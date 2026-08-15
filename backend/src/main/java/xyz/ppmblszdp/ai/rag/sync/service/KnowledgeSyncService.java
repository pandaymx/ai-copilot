package xyz.ppmblszdp.ai.rag.sync.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;
import xyz.ppmblszdp.ai.rag.sync.connector.KnowledgeConnector;
import xyz.ppmblszdp.ai.rag.sync.dto.*;

/**
 * 知识库自动同步核心服务 (KnowledgeSyncService)：
 * 负责外部多源连接器分发、SHA-256 增量内容哈希比对（零 Token 消耗跳过未变文档）、
 * 过期与已删除文档清理（避免幽灵数据）、并发状态锁防护与周期性 Cron 调度。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class KnowledgeSyncService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncService.class);
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final RagIngestionService ragIngestionService;
    private final List<KnowledgeConnector> connectors;

    /** 内存知识源配置缓存，支持并发读写 */
    private final ConcurrentHashMap<String, KnowledgeSourceDto> sources = new ConcurrentHashMap<>();

    /** 并发任务状态锁：防止同一数据源在未完成前被 Cron 重入或并发重复触发 */
    private final ConcurrentHashMap<String, Boolean> syncLocks = new ConcurrentHashMap<>();

    /** 记录最近各知识源的同步历史日志 */
    private final ConcurrentHashMap<String, List<KnowledgeSyncResultDto>> syncLogs = new ConcurrentHashMap<>();

    public KnowledgeSyncService(
            RagIngestionService ragIngestionService, ObjectProvider<List<KnowledgeConnector>> connectorsProvider) {
        this.ragIngestionService = ragIngestionService;
        this.connectors = connectorsProvider.getIfAvailable(ArrayList::new);

        // 初始化默认内置样例知识源
        initDefaultSampleSources();
    }

    private void initDefaultSampleSources() {
        KnowledgeSourceDto githubSample = KnowledgeSourceDto.create(
                "src-gh-copilot",
                "AI Copilot 官方架构与设计文档",
                "GITHUB",
                Map.of(
                        "repo", "ppmblszdp/ai-copilot",
                        "branch", "main",
                        "path", "docs/"),
                "0 0 */2 * * ?", // 每2小时自动同步
                true);
        sources.put(githubSample.id(), githubSample);
    }

    /**
     * 获取所有知识源列表（已脱敏）。
     */
    public List<KnowledgeSourceDto> listSources() {
        return sources.values().stream()
                .map(KnowledgeSourceDto::masked)
                .sorted(Comparator.comparing(KnowledgeSourceDto::id))
                .toList();
    }

    /**
     * 获取单个知识源详情。
     */
    public Optional<KnowledgeSourceDto> getSource(String id) {
        KnowledgeSourceDto dto = sources.get(id);
        return dto != null ? Optional.of(dto.masked()) : Optional.empty();
    }

    /**
     * 创建新知识源。
     */
    public KnowledgeSourceDto createSource(CreateSourceReq req) {
        String id = "src-" + UUID.randomUUID().toString().substring(0, 8);
        KnowledgeSourceDto source = KnowledgeSourceDto.create(
                id,
                req.name(),
                req.sourceType(),
                req.config(),
                req.cronExpression(),
                req.enabled() != null ? req.enabled() : true);

        sources.put(id, source);
        log.info("创建知识源成功: id={}, name={}, type={}", id, req.name(), req.sourceType());
        return source.masked();
    }

    /**
     * 更新知识源配置。
     */
    public Optional<KnowledgeSourceDto> updateSource(String id, UpdateSourceReq req) {
        KnowledgeSourceDto existing = sources.get(id);
        if (existing == null) {
            return Optional.empty();
        }

        KnowledgeSourceDto updated = existing.withConfig(req.name(), req.config(), req.cronExpression(), req.enabled());
        sources.put(id, updated);
        log.info("更新知识源配置: id={}, name={}", id, updated.name());
        return Optional.of(updated.masked());
    }

    /**
     * 删除知识源，并清理其关联的全部向量文档（避免幽灵数据残留）。
     */
    public boolean deleteSource(String id) {
        KnowledgeSourceDto removed = sources.remove(id);
        if (removed == null) {
            return false;
        }

        // 异步级联清理 VectorStore 中该知识源的所有文档
        VIRTUAL_THREAD_POOL.submit(() -> {
            try {
                for (String docUri : removed.contentHashes().keySet()) {
                    ragIngestionService.deleteBySourceAndUser(docUri, "TEXT", "system");
                }
                log.info(
                        "已完成知识源级联物理删除: id={}, 清理文档数={}",
                        id,
                        removed.contentHashes().size());
            } catch (Exception e) {
                log.warn("级联清理向量库失败: id={}, error={}", id, e.getMessage());
            }
        });

        syncLocks.remove(id);
        syncLogs.remove(id);
        return true;
    }

    /**
     * 执行指定知识源的增量同步。
     *
     * @param sourceId 知识源 ID
     * @param force    是否强制全量覆盖更新（忽略 Hash 一致性）
     * @return 同步结果统计
     */
    public KnowledgeSyncResultDto syncSource(String sourceId, boolean force) {
        KnowledgeSourceDto source = sources.get(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("未找到对应的知识源: " + sourceId);
        }

        // 1. 并发状态锁检查：若当前知识源正处于同步中，拒绝并发重入
        if (syncLocks.putIfAbsent(sourceId, Boolean.TRUE) != null) {
            log.warn("知识源 [{}] 正在同步中，拒绝并发重入请求", sourceId);
            return KnowledgeSyncResultDto.failed(sourceId, source.name(), "该数据源正在同步中，请勿重复触发", 0);
        }

        long start = System.currentTimeMillis();
        sources.put(sourceId, source.withStatus("SYNCING", "正在同步远端数据...", 0, source.documentCount(), null));

        try {
            // 2. 匹配连接器
            KnowledgeConnector connector = connectors.stream()
                    .filter(c -> c.supports(source.sourceType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("不支持的数据源类型连接器: " + source.sourceType()));

            // 3. 拉取远端文档
            List<RemoteKnowledgeDoc> remoteDocs = connector.fetchDocuments(source);

            // 4. 增量比对与入库
            Map<String, String> currentHashes = new HashMap<>(source.contentHashes());
            Set<String> remoteUris = new HashSet<>();

            int added = 0;
            int updated = 0;
            int skipped = 0;
            int deleted = 0;

            for (RemoteKnowledgeDoc doc : remoteDocs) {
                remoteUris.add(doc.uri());
                String prevHash = currentHashes.get(doc.uri());

                // 第一道防线：基于 contentHash 的低开销比对，若内容完全一致且未指定强制覆盖，则直接跳过
                if (!force && prevHash != null && prevHash.equals(doc.hash())) {
                    skipped++;
                    continue;
                }

                // 新增或更新文档入库
                boolean isNew = (prevHash == null);
                if (isNew) {
                    added++;
                } else {
                    updated++;
                }

                Map<String, Object> extraMetadata = new HashMap<>(doc.metadata() != null ? doc.metadata() : Map.of());
                extraMetadata.put("knowledgeSourceId", source.id());
                extraMetadata.put("sourceType", source.sourceType());
                extraMetadata.put("originalUri", doc.uri());
                extraMetadata.put("contentHash", doc.hash());

                ragIngestionService.ingest(
                        SourceType.TEXT, doc.content(), doc.title(), "system", ConflictPolicy.OVERWRITE, extraMetadata);

                currentHashes.put(doc.uri(), doc.hash());
            }

            // 5. 旧数据与已删除文档清理 (Stale / Deleted Document Eviction)
            // 识别本地已存在但远端已被删除的文档，彻底清理向量切片与图谱节点
            Set<String> localUris = new HashSet<>(source.contentHashes().keySet());
            localUris.removeAll(remoteUris);

            for (String deletedUri : localUris) {
                try {
                    ragIngestionService.deleteBySourceAndUser(deletedUri, "TEXT", "system");
                    currentHashes.remove(deletedUri);
                    deleted++;
                    log.info("知识库同步成功清理远端已删除文档: uri={}", deletedUri);
                } catch (Exception e) {
                    log.warn("清理过期文档异常: uri={}, error={}", deletedUri, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - start;
            int finalDocCount = currentHashes.size();

            KnowledgeSyncResultDto result = KnowledgeSyncResultDto.success(
                    sourceId, source.name(), remoteDocs.size(), added, updated, skipped, deleted, duration);

            // 更新知识源持久化状态
            sources.put(
                    sourceId, source.withStatus("SUCCESS", result.message(), duration, finalDocCount, currentHashes));
            recordSyncLog(sourceId, result);

            log.info("知识源 [{}] 同步完成: {}", sourceId, result.message());
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("知识源 [{}] 同步失败", sourceId, e);
            KnowledgeSyncResultDto failedResult =
                    KnowledgeSyncResultDto.failed(sourceId, source.name(), "同步异常: " + e.getMessage(), duration);
            sources.put(
                    sourceId,
                    source.withStatus("FAILED", "同步失败: " + e.getMessage(), duration, source.documentCount(), null));
            recordSyncLog(sourceId, failedResult);
            return failedResult;
        } finally {
            syncLocks.remove(sourceId);
        }
    }

    /**
     * 定时扫描启用的知识源并触发周期性自动增量同步。
     */
    @Scheduled(fixedDelay = 60000) // 每分钟检查一次
    public void schedulePeriodicSync() {
        for (KnowledgeSourceDto src : sources.values()) {
            if (!src.enabled()) {
                continue;
            }
            // 若超过 2 小时未同步且当前未在同步中，自动触发增量同步
            long now = System.currentTimeMillis();
            if (src.lastSyncAtMs() == null || (now - src.lastSyncAtMs() > 7200000L)) {
                VIRTUAL_THREAD_POOL.submit(() -> syncSource(src.id(), false));
            }
        }
    }

    public List<KnowledgeSyncResultDto> getSyncLogs(String sourceId) {
        return syncLogs.getOrDefault(sourceId, List.of());
    }

    private void recordSyncLog(String sourceId, KnowledgeSyncResultDto result) {
        syncLogs.compute(sourceId, (k, v) -> {
            List<KnowledgeSyncResultDto> list = (v != null) ? new ArrayList<>(v) : new ArrayList<>();
            list.add(0, result);
            if (list.size() > 20) {
                return list.subList(0, 20);
            }
            return list;
        });
    }
}
