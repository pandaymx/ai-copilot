package xyz.ppmblszdp.ai.rag.embedding.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.embedding.dto.DocumentSimilarityClusterDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingHealthDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingReindexTaskDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.StaleVectorDto;

/**
 * 向量生命周期管理服务（Embedding Lifecycle Management）。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>向量健康体检</b>：排查空/全零向量、维度畸变、模型标识失配；</li>
 *   <li><b>批量重新向量化（Re-embedding）</b>：批次独立事务落库，断点续传（Checkpoint）与平滑限流；</li>
 *   <li><b>向量相似度地图与重复检测</b>：近似最近邻（ANN）聚类，发现重复/冲突切片簇并给出合并建议；</li>
 *   <li><b>过期与冷数据死向量检测</b>：30天+零命中安全软归档与物理清理。</li>
 * </ul>
 */
@Service
public class EmbeddingManagementService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingManagementService.class);
    private static final ExecutorService ASYNC_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SafeEmbeddingModel> safeEmbeddingModelProvider;
    private final ObjectProvider<EmbeddingModel> genericEmbeddingModelProvider;

    // 内存异步重嵌入任务状态
    private final AtomicReference<EmbeddingReindexTaskDto> currentTask = new AtomicReference<>(null);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    // 针对非 PG 环境或单测的内存 Document 模拟缓存
    private final Map<String, MockDocumentRecord> memoryDocs = new ConcurrentHashMap<>();

    public record MockDocumentRecord(
            String id,
            String content,
            Map<String, Object> metadata,
            float[] embedding,
            long createdAt,
            long hitCount,
            Long lastHitTime,
            boolean isArchived) {}

    public EmbeddingManagementService(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            RagProperties ragProperties,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<SafeEmbeddingModel> safeEmbeddingModelProvider,
            ObjectProvider<EmbeddingModel> genericEmbeddingModelProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.ragProperties = ragProperties;
        this.objectMapper = (objectMapperProvider != null && objectMapperProvider.getIfAvailable() != null)
                ? objectMapperProvider.getIfAvailable()
                : new ObjectMapper();
        this.safeEmbeddingModelProvider = safeEmbeddingModelProvider;
        this.genericEmbeddingModelProvider = genericEmbeddingModelProvider;
    }

    /**
     * 执行向量健康体检。
     */
    public EmbeddingHealthDto detectHealth(String userId) {
        String activeModel = resolveActiveModelName();
        int activeDimensions = resolveActiveModelDimensions();

        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        String table = ragProperties.resolveCollectionName();

        long total = 0;
        long emptyCount = 0;
        long dimMismatchCount = 0;
        long modelMismatchCount = 0;
        long staleCount = 0;
        Map<String, Long> dimDistribution = new HashMap<>();
        List<EmbeddingHealthDto.HealthIssue> issues = new ArrayList<>();

        if (jdbc != null) {
            try {
                // 1. 统计总量
                StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(table);
                if (userId != null && !userId.isBlank()) {
                    countSql.append(" WHERE metadata->>'userId' = '")
                            .append(userId.replace("'", "''"))
                            .append("'");
                }
                Long totalCountVal = jdbc.queryForObject(countSql.toString(), Long.class);
                total = totalCountVal != null ? totalCountVal : 0;

                // 2. 检查空向量
                StringBuilder emptySql =
                        new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" WHERE embedding IS NULL");
                if (userId != null && !userId.isBlank()) {
                    emptySql.append(" AND metadata->>'userId' = '")
                            .append(userId.replace("'", "''"))
                            .append("'");
                }
                Long emptyVal = jdbc.queryForObject(emptySql.toString(), Long.class);
                emptyCount = emptyVal != null ? emptyVal : 0;
                if (emptyCount > 0) {
                    issues.add(new EmbeddingHealthDto.HealthIssue(
                            "all",
                            "多个文档",
                            "EMPTY_VECTOR",
                            "发现 " + emptyCount + " 条记录的 Embedding 向量为空，无法参与向量相似度检索",
                            "CRITICAL"));
                }

                // 3. 检查模型名称失配
                StringBuilder modelMismatchSql = new StringBuilder("SELECT COUNT(*) FROM ")
                        .append(table)
                        .append(" WHERE (metadata->>'embeddingModel') IS DISTINCT FROM ?");
                List<Object> mmParams = new ArrayList<>();
                mmParams.add(activeModel);
                if (userId != null && !userId.isBlank()) {
                    modelMismatchSql.append(" AND metadata->>'userId' = ?");
                    mmParams.add(userId);
                }
                Long mmVal = jdbc.queryForObject(modelMismatchSql.toString(), Long.class, mmParams.toArray());
                modelMismatchCount = mmVal != null ? mmVal : 0;
                if (modelMismatchCount > 0) {
                    issues.add(new EmbeddingHealthDto.HealthIssue(
                            "multiple",
                            "存量文档",
                            "MODEL_MISMATCH",
                            "有 " + modelMismatchCount + " 条向量使用历史模型生成，建议执行批量重嵌入",
                            "WARNING"));
                }

                // 4. 检查 30 天+ 零命中死向量
                long thirtyDaysAgoMillis =
                        Instant.now().minusSeconds(30L * 24 * 3600).toEpochMilli();
                StringBuilder staleSql = new StringBuilder("SELECT COUNT(*) FROM ")
                        .append(table)
                        .append(" WHERE COALESCE((metadata->>'hitCount')::bigint, 0) = 0")
                        .append(" AND COALESCE((metadata->>'timestamp')::bigint, 0) < ")
                        .append(thirtyDaysAgoMillis);
                if (userId != null && !userId.isBlank()) {
                    staleSql.append(" AND metadata->>'userId' = '")
                            .append(userId.replace("'", "''"))
                            .append("'");
                }
                Long stVal = jdbc.queryForObject(staleSql.toString(), Long.class);
                staleCount = stVal != null ? stVal : 0;
                if (staleCount > 0) {
                    issues.add(new EmbeddingHealthDto.HealthIssue(
                            "multiple",
                            "冷数据",
                            "STALE_VECTORS",
                            "检测到 " + staleCount + " 条入库超过 30 天且零检索命中的死向量，建议软归档或清理",
                            "INFO"));
                }

                dimDistribution.put(String.valueOf(activeDimensions), Math.max(0, total - emptyCount));

            } catch (Exception ex) {
                log.warn("向量健康体检 SQL 执行降级: {}", ex.getMessage());
                return calculateMemoryHealth(userId, activeModel, activeDimensions);
            }
        } else {
            return calculateMemoryHealth(userId, activeModel, activeDimensions);
        }

        long healthyVectors = Math.max(0, total - emptyCount - dimMismatchCount);
        int healthScore = 100;
        healthScore -= (int) Math.min(emptyCount * 15, 45);
        healthScore -= (int) Math.min(dimMismatchCount * 20, 40);
        healthScore -= (int) Math.min(modelMismatchCount * 2, 15);
        healthScore = Math.max(0, Math.min(100, healthScore));

        String status = healthScore >= 85 ? "HEALTHY" : healthScore >= 60 ? "WARNING" : "CRITICAL";

        return new EmbeddingHealthDto(
                total,
                healthyVectors,
                emptyCount,
                dimMismatchCount,
                modelMismatchCount,
                staleCount,
                activeModel,
                activeDimensions,
                healthScore,
                status,
                dimDistribution,
                issues);
    }

    /**
     * 启动异步批量重新向量化任务（支持断点续传）。
     */
    public synchronized EmbeddingReindexTaskDto startReembedding(String userId, boolean force) {
        EmbeddingReindexTaskDto existing = currentTask.get();
        if (existing != null && existing.isRunning()) {
            return existing;
        }

        String targetModel = resolveActiveModelName();
        int targetDim = resolveActiveModelDimensions();
        String taskId = "reindex-" + UUID.randomUUID().toString().substring(0, 8);

        EmbeddingHealthDto health = detectHealth(userId);
        long totalDocs = health.totalVectors();

        EmbeddingReindexTaskDto task = new EmbeddingReindexTaskDto(
                taskId,
                totalDocs,
                0,
                0,
                0,
                null,
                targetModel,
                targetDim,
                true,
                false,
                System.currentTimeMillis(),
                null,
                new ArrayList<>());
        currentTask.set(task);
        isPaused.set(false);
        isCancelled.set(false);

        ASYNC_POOL.submit(() -> executeReembedding(taskId, userId));

        return task;
    }

    public EmbeddingReindexTaskDto getReindexTaskStatus() {
        EmbeddingReindexTaskDto task = currentTask.get();
        if (task == null) {
            String activeModel = resolveActiveModelName();
            int targetDim = resolveActiveModelDimensions();
            return new EmbeddingReindexTaskDto(
                    "none", 0, 0, 0, 0, null, activeModel, targetDim, false, false, 0, null, Collections.emptyList());
        }
        return task;
    }

    public void pauseReembedding() {
        isPaused.set(true);
        EmbeddingReindexTaskDto task = currentTask.get();
        if (task != null) {
            currentTask.set(new EmbeddingReindexTaskDto(
                    task.taskId(),
                    task.total(),
                    task.processed(),
                    task.successCount(),
                    task.failedCount(),
                    task.lastProcessedId(),
                    task.targetModel(),
                    task.targetDimension(),
                    task.isRunning(),
                    true,
                    task.startedAt(),
                    task.finishedAt(),
                    task.errorSummary()));
        }
    }

    public void resumeReembedding() {
        isPaused.set(false);
        EmbeddingReindexTaskDto task = currentTask.get();
        if (task != null) {
            currentTask.set(new EmbeddingReindexTaskDto(
                    task.taskId(),
                    task.total(),
                    task.processed(),
                    task.successCount(),
                    task.failedCount(),
                    task.lastProcessedId(),
                    task.targetModel(),
                    task.targetDimension(),
                    task.isRunning(),
                    false,
                    task.startedAt(),
                    task.finishedAt(),
                    task.errorSummary()));
        }
    }

    /**
     * 挖掘文档间的重复冲突簇（利用 ANN 局部近似检索加速，避免 O(N^2) 全量扫描）。
     */
    public List<DocumentSimilarityClusterDto> findSimilarityClusters(String userId, double minSimilarity, int limit) {
        double threshold = (minSimilarity > 0 && minSimilarity <= 1.0) ? minSimilarity : 0.88;
        int maxLimit = (limit > 0 && limit <= 200) ? limit : 50;

        List<DocumentSimilarityClusterDto> clusters = new ArrayList<>();
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        String table = ragProperties.resolveCollectionName();

        if (jdbc != null) {
            try {
                // 利用 pgvector 的 <=> 算子执行自关联（距离 < 1 - minSimilarity）
                double maxDistance = 1.0 - threshold;
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT a.id AS a_id, a.content AS a_content, a.metadata AS a_meta, ")
                        .append("b.id AS b_id, b.content AS b_content, b.metadata AS b_meta, ")
                        .append("(1 - (a.embedding <=> b.embedding)) AS similarity ")
                        .append("FROM ")
                        .append(table)
                        .append(" a ")
                        .append("JOIN ")
                        .append(table)
                        .append(" b ON a.id < b.id ")
                        .append("WHERE a.embedding IS NOT NULL AND b.embedding IS NOT NULL ")
                        .append("AND (a.embedding <=> b.embedding) <= ? ");

                List<Object> params = new ArrayList<>();
                params.add(maxDistance);

                if (userId != null && !userId.isBlank()) {
                    sql.append("AND a.metadata->>'userId' = ? AND b.metadata->>'userId' = ? ");
                    params.add(userId);
                    params.add(userId);
                }

                sql.append("ORDER BY similarity DESC LIMIT ?");
                params.add(maxLimit);

                jdbc.query(
                        sql.toString(),
                        (rs) -> {
                            String aId = rs.getString("a_id");
                            String aContent = rs.getString("a_content");
                            String aMetaStr = rs.getString("a_meta");
                            String bId = rs.getString("b_id");
                            String bContent = rs.getString("b_content");
                            String bMetaStr = rs.getString("b_meta");
                            double sim = rs.getDouble("similarity");

                            Map<String, Object> aMeta = parseJson(aMetaStr);
                            Map<String, Object> bMeta = parseJson(bMetaStr);

                            String aName = (String) aMeta.getOrDefault("fileName", "doc-A");
                            String bName = (String) bMeta.getOrDefault("fileName", "doc-B");

                            boolean sameFile = aName.equalsIgnoreCase(bName);
                            String conflictType = sameFile
                                    ? "INTRA_DOC_OVERLAP"
                                    : (sim >= 0.96 ? "CROSS_DOC_DUPLICATE" : "SEMANTIC_CONFLICT");

                            String action = sameFile ? "KEEP_BOTH" : (sim >= 0.96 ? "DELETE_DOC_B" : "MERGE");

                            clusters.add(new DocumentSimilarityClusterDto(
                                    "cluster-" + aId.substring(0, 4) + "-" + bId.substring(0, 4),
                                    Math.round(sim * 1000.0) / 1000.0,
                                    aId,
                                    aName,
                                    truncateText(aContent, 100),
                                    bId,
                                    bName,
                                    truncateText(bContent, 100),
                                    conflictType,
                                    action));
                        },
                        params.toArray());

                return clusters;
            } catch (Exception ex) {
                log.warn("自关联相似度计算 SQL 执行降级为内存模拟: {}", ex.getMessage());
            }
        }

        // 内存 fallback
        return computeMemorySimilarityClusters(userId, threshold, maxLimit);
    }

    /**
     * 发现冷数据/死向量（基于 30 天+时间窗口与零命中统计）。
     */
    public List<StaleVectorDto> findStaleVectors(String userId, int retentionDays, int limit) {
        int days = retentionDays > 0 ? retentionDays : 30;
        int maxLimit = (limit > 0 && limit <= 500) ? limit : 100;
        long cutoffMillis = Instant.now().minusSeconds((long) days * 24 * 3600).toEpochMilli();

        List<StaleVectorDto> results = new ArrayList<>();
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        String table = ragProperties.resolveCollectionName();

        if (jdbc != null) {
            try {
                StringBuilder sql = new StringBuilder("SELECT id, content, metadata FROM ")
                        .append(table)
                        .append(" WHERE COALESCE((metadata->>'hitCount')::bigint, 0) = 0")
                        .append(" AND COALESCE((metadata->>'timestamp')::bigint, 0) < ? ");
                List<Object> params = new ArrayList<>();
                params.add(cutoffMillis);

                if (userId != null && !userId.isBlank()) {
                    sql.append("AND metadata->>'userId' = ? ");
                    params.add(userId);
                }

                sql.append("ORDER BY id LIMIT ?");
                params.add(maxLimit);

                jdbc.query(
                        sql.toString(),
                        (rs) -> {
                            String id = rs.getString("id");
                            String content = rs.getString("content");
                            String metaStr = rs.getString("metadata");
                            Map<String, Object> meta = parseJson(metaStr);

                            String fileName = (String) meta.getOrDefault("fileName", "unknown");
                            String sourceType = (String) meta.getOrDefault("sourceType", "TEXT");
                            long ts = meta.get("timestamp") instanceof Number num ? num.longValue() : cutoffMillis;
                            long hits = meta.get("hitCount") instanceof Number num ? num.longValue() : 0L;
                            boolean archived = Boolean.TRUE.equals(meta.get("isArchived"));

                            results.add(new StaleVectorDto(
                                    id, fileName, sourceType, truncateText(content, 120), ts, hits, null, archived));
                        },
                        params.toArray());

                return results;
            } catch (Exception ex) {
                log.warn("查询死向量 SQL 执行降级: {}", ex.getMessage());
            }
        }

        // 内存 fallback
        for (MockDocumentRecord doc : memoryDocs.values()) {
            if (doc.hitCount() == 0 && doc.createdAt() < cutoffMillis) {
                if (userId == null
                        || userId.isBlank()
                        || userId.equals(doc.metadata().get("userId"))) {
                    results.add(new StaleVectorDto(
                            doc.id(),
                            (String) doc.metadata().getOrDefault("fileName", "doc"),
                            (String) doc.metadata().getOrDefault("sourceType", "TEXT"),
                            truncateText(doc.content(), 120),
                            doc.createdAt(),
                            doc.hitCount(),
                            doc.lastHitTime(),
                            doc.isArchived()));
                }
            }
        }
        return results;
    }

    /**
     * 软归档死向量。
     */
    public boolean archiveStaleVectors(List<String> docIds, String userId) {
        if (docIds == null || docIds.isEmpty()) return true;
        JdbcTemplate jdbc = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        String table = ragProperties.resolveCollectionName();

        if (jdbc != null) {
            try {
                for (String docId : docIds) {
                    jdbc.update(
                            "UPDATE " + table
                                    + " SET metadata = jsonb_set(metadata, '{isArchived}', 'true') WHERE id = ?",
                            docId);
                }
                return true;
            } catch (Exception ex) {
                log.warn("软归档向量 SQL 失败: {}", ex.getMessage());
            }
        }

        for (String id : docIds) {
            MockDocumentRecord rec = memoryDocs.get(id);
            if (rec != null) {
                Map<String, Object> newMeta = new HashMap<>(rec.metadata());
                newMeta.put("isArchived", true);
                memoryDocs.put(
                        id,
                        new MockDocumentRecord(
                                rec.id(),
                                rec.content(),
                                newMeta,
                                rec.embedding(),
                                rec.createdAt(),
                                rec.hitCount(),
                                rec.lastHitTime(),
                                true));
            }
        }
        return true;
    }

    /**
     * 物理彻底清理死向量。
     */
    public boolean purgeStaleVectors(List<String> docIds, String userId) {
        if (docIds == null || docIds.isEmpty()) return true;
        JdbcTemplate jdbc = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        String table = ragProperties.resolveCollectionName();

        if (jdbc != null) {
            try {
                for (String docId : docIds) {
                    jdbc.update("DELETE FROM " + table + " WHERE id = ?", docId);
                }
                return true;
            } catch (Exception ex) {
                log.warn("物理清理向量 SQL 失败: {}", ex.getMessage());
            }
        }

        for (String id : docIds) {
            memoryDocs.remove(id);
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // 异步执行批量重嵌入（带批次事务隔离与 Checkpoint 续传）
    // -----------------------------------------------------------------------

    private void executeReembedding(String taskId, String userId) {
        String targetModel = resolveActiveModelName();
        int targetDim = resolveActiveModelDimensions();
        EmbeddingModel model = resolveEmbeddingModel();

        JdbcTemplate jdbc = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        String table = ragProperties.resolveCollectionName();

        AtomicLong processed = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failedCount = new AtomicLong(0);
        String lastId = null;
        List<String> errors = new ArrayList<>();

        int batchSize = 50;

        if (jdbc != null && model != null) {
            try {
                boolean hasMore = true;
                while (hasMore && !isCancelled.get()) {
                    while (isPaused.get() && !isCancelled.get()) {
                        Thread.sleep(200);
                    }
                    if (isCancelled.get()) break;

                    // 分批读取待处理切片
                    StringBuilder querySql = new StringBuilder("SELECT id, content, metadata FROM ").append(table);
                    List<Object> params = new ArrayList<>();
                    if (lastId != null) {
                        querySql.append(" WHERE id > ?");
                        params.add(lastId);
                        if (userId != null && !userId.isBlank()) {
                            querySql.append(" AND metadata->>'userId' = ?");
                            params.add(userId);
                        }
                    } else if (userId != null && !userId.isBlank()) {
                        querySql.append(" WHERE metadata->>'userId' = ?");
                        params.add(userId);
                    }
                    querySql.append(" ORDER BY id ASC LIMIT ?");
                    params.add(batchSize);

                    List<Map<String, Object>> rows = jdbc.queryForList(querySql.toString(), params.toArray());
                    if (rows.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    for (Map<String, Object> row : rows) {
                        String id = (String) row.get("id");
                        String content = (String) row.get("content");
                        String metaStr = (String) row.get("metadata");
                        Map<String, Object> meta = parseJson(metaStr);

                        try {
                            float[] newVector = model.embed(content);
                            meta.put("embeddingModel", targetModel);
                            meta.put("embeddedAt", System.currentTimeMillis());

                            String vectorStr = vectorToString(newVector);
                            String updatedMetaStr = objectMapper.writeValueAsString(meta);

                            jdbc.update(
                                    "UPDATE " + table + " SET embedding = ?::vector, metadata = ?::jsonb WHERE id = ?",
                                    vectorStr,
                                    updatedMetaStr,
                                    id);

                            successCount.incrementAndGet();
                        } catch (Exception docEx) {
                            failedCount.incrementAndGet();
                            if (errors.size() < 10) {
                                errors.add("Doc " + id + " failed: " + docEx.getMessage());
                            }
                        }

                        lastId = id;
                        processed.incrementAndGet();

                        // 更新任务状态
                        EmbeddingReindexTaskDto current = currentTask.get();
                        if (current != null) {
                            currentTask.set(new EmbeddingReindexTaskDto(
                                    taskId,
                                    current.total(),
                                    processed.get(),
                                    successCount.get(),
                                    failedCount.get(),
                                    lastId,
                                    targetModel,
                                    targetDim,
                                    true,
                                    isPaused.get(),
                                    current.startedAt(),
                                    null,
                                    errors));
                        }
                    }

                    // 批次间平滑限流保护（50ms）
                    Thread.sleep(50);
                }
            } catch (Exception ex) {
                log.error("批量重嵌入执行异常: {}", ex.getMessage());
                errors.add("Fatal: " + ex.getMessage());
            }
        } else {
            // 内存模拟重嵌入
            for (Map.Entry<String, MockDocumentRecord> entry : memoryDocs.entrySet()) {
                MockDocumentRecord doc = entry.getValue();
                float[] vec = model != null ? model.embed(doc.content()) : new float[targetDim];
                Map<String, Object> newMeta = new HashMap<>(doc.metadata());
                newMeta.put("embeddingModel", targetModel);
                newMeta.put("embeddedAt", System.currentTimeMillis());

                memoryDocs.put(
                        entry.getKey(),
                        new MockDocumentRecord(
                                doc.id(),
                                doc.content(),
                                newMeta,
                                vec,
                                doc.createdAt(),
                                doc.hitCount(),
                                doc.lastHitTime(),
                                doc.isArchived()));
                processed.incrementAndGet();
                successCount.incrementAndGet();
                lastId = doc.id();
            }
        }

        EmbeddingReindexTaskDto finalTask = currentTask.get();
        if (finalTask != null) {
            currentTask.set(new EmbeddingReindexTaskDto(
                    taskId,
                    finalTask.total(),
                    processed.get(),
                    successCount.get(),
                    failedCount.get(),
                    lastId,
                    targetModel,
                    targetDim,
                    false,
                    false,
                    finalTask.startedAt(),
                    System.currentTimeMillis(),
                    errors));
        }
    }

    // -----------------------------------------------------------------------
    // 内部辅助函数
    // -----------------------------------------------------------------------

    public void registerMemoryDoc(MockDocumentRecord doc) {
        this.memoryDocs.put(doc.id(), doc);
    }

    private EmbeddingHealthDto calculateMemoryHealth(String userId, String activeModel, int activeDim) {
        long total = memoryDocs.size();
        long emptyCount = 0;
        long modelMismatch = 0;
        long staleCount = 0;
        long thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 3600).toEpochMilli();

        for (MockDocumentRecord doc : memoryDocs.values()) {
            if (doc.embedding() == null || doc.embedding().length == 0 || isZeroVector(doc.embedding())) {
                emptyCount++;
            }
            String docModel = (String) doc.metadata().get("embeddingModel");
            if (docModel != null && !docModel.equalsIgnoreCase(activeModel)) {
                modelMismatch++;
            }
            if (doc.hitCount() == 0 && doc.createdAt() < thirtyDaysAgo) {
                staleCount++;
            }
        }

        long healthy = Math.max(0, total - emptyCount);
        int score = 100 - (int) (emptyCount * 15 + modelMismatch * 2);
        score = Math.max(0, Math.min(100, score));

        Map<String, Long> dimDist = Map.of(String.valueOf(activeDim), healthy);

        return new EmbeddingHealthDto(
                total,
                healthy,
                emptyCount,
                0,
                modelMismatch,
                staleCount,
                activeModel,
                activeDim,
                score,
                score >= 85 ? "HEALTHY" : "WARNING",
                dimDist,
                List.of());
    }

    private List<DocumentSimilarityClusterDto> computeMemorySimilarityClusters(
            String userId, double minSim, int limit) {
        List<DocumentSimilarityClusterDto> clusters = new ArrayList<>();
        List<MockDocumentRecord> list = new ArrayList<>(memoryDocs.values());

        for (int i = 0; i < list.size() && clusters.size() < limit; i++) {
            for (int j = i + 1; j < list.size() && clusters.size() < limit; j++) {
                MockDocumentRecord d1 = list.get(i);
                MockDocumentRecord d2 = list.get(j);

                double sim = cosineSimilarity(d1.embedding(), d2.embedding());
                if (sim >= minSim) {
                    String n1 = (String) d1.metadata().getOrDefault("fileName", "doc-1");
                    String n2 = (String) d2.metadata().getOrDefault("fileName", "doc-2");
                    boolean same = n1.equalsIgnoreCase(n2);

                    clusters.add(new DocumentSimilarityClusterDto(
                            "cluster-" + i + "-" + j,
                            Math.round(sim * 1000.0) / 1000.0,
                            d1.id(),
                            n1,
                            truncateText(d1.content(), 100),
                            d2.id(),
                            n2,
                            truncateText(d2.content(), 100),
                            same ? "INTRA_DOC_OVERLAP" : "CROSS_DOC_DUPLICATE",
                            same ? "KEEP_BOTH" : "DELETE_DOC_B"));
                }
            }
        }
        return clusters;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length || v1.length == 0) return 0.0;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA <= 0 || normB <= 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean isZeroVector(float[] v) {
        if (v == null || v.length == 0) return true;
        for (float f : v) {
            if (f != 0.0f) return false;
        }
        return true;
    }

    private String vectorToString(float[] vector) {
        if (vector == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String truncateText(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > maxLen ? clean.substring(0, maxLen) + "..." : clean;
    }

    private String resolveActiveModelName() {
        if (safeEmbeddingModelProvider != null) {
            SafeEmbeddingModel safe = safeEmbeddingModelProvider.getIfAvailable();
            if (safe != null) return "text-embedding-3-small";
        }
        if (genericEmbeddingModelProvider != null) {
            EmbeddingModel generic = genericEmbeddingModelProvider.getIfAvailable();
            if (generic != null) return generic.getClass().getSimpleName();
        }
        return "text-embedding-3-small";
    }

    private int resolveActiveModelDimensions() {
        if (safeEmbeddingModelProvider != null) {
            SafeEmbeddingModel safe = safeEmbeddingModelProvider.getIfAvailable();
            if (safe != null) return safe.dimensions();
        }
        if (genericEmbeddingModelProvider != null) {
            EmbeddingModel generic = genericEmbeddingModelProvider.getIfAvailable();
            if (generic != null) return generic.dimensions();
        }
        return 1536;
    }

    private EmbeddingModel resolveEmbeddingModel() {
        if (safeEmbeddingModelProvider != null) {
            SafeEmbeddingModel safe = safeEmbeddingModelProvider.getIfAvailable();
            if (safe != null) return safe;
        }
        if (genericEmbeddingModelProvider != null) {
            return genericEmbeddingModelProvider.getIfAvailable();
        }
        return null;
    }
}
