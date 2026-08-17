package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.MemoryDto;
import xyz.ppmblszdp.ai.dto.MemoryDto.ListResponse;
import xyz.ppmblszdp.ai.memory.SafeVectorStore;

/**
 * 长期记忆管理业务层。
 *
 * <p>
 * 列出走 {@code JdbcTemplate} 直查 pgvector 表（PgVectorStore 不支持按 metadata 全量列出）；
 * 编辑/删除走 {@link SafeVectorStore}（容错降级，不抛 5xx）。
 * 结合 {@link MemoryForgetService} 实现时间衰减、自动归档清理、冲突检测与摘要压缩。
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 与 Spring AI pgvector autoconfigure 实际建表名保持一致（默认集合名为 vector_store）。
    // 长期记忆专用，与 RAG 独立表 ai_rag_documents 物理隔离。
    private static final String MEMORY_TABLE = "vector_store";

    private final JdbcTemplate jdbcTemplate;
    private final SafeVectorStore vectorStore;
    private final MemoryForgetService forgetService;

    public MemoryService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<VectorStore> vectorStoreProvider,
            MemoryForgetService forgetService) {
        this.jdbcTemplate = jdbcTemplate;
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        this.vectorStore = (vs != null) ? new SafeVectorStore(vs) : new SafeVectorStore(null);
        this.forgetService = forgetService;
    }

    private Map<String, Object> parseMetadata(Object metaObj) {
        if (metaObj == null) {
            return Collections.emptyMap();
        }
        if (metaObj instanceof PGobject pg) {
            try {
                String value = pg.getValue();
                if (value == null || value.isBlank()) {
                    return Collections.emptyMap();
                }
                Map<String, Object> map = MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {});
                return (map != null) ? map : Collections.emptyMap();
            } catch (Exception e) {
                log.warn("解析记忆 metadata(jsonb)失败，降级空 Map: {}", e.getMessage());
                return Collections.emptyMap();
            }
        }
        if (metaObj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Collections.emptyMap();
    }

    /**
     * 列出用户长期记忆（支持按状态过滤、关键字搜索、分页）。
     *
     * @param userId  当前用户 ID
     * @param keyword 检索关键字
     * @param status  状态过滤 ("active", "archived", "all")
     * @param limit   分页 limit
     * @param offset  分页 offset
     */
    public ListResponse listMemories(String userId, String keyword, String status, int limit, int offset) {
        StringBuilder where = new StringBuilder("WHERE metadata->>'userId' = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND content ILIKE ?");
            args.add("%" + keyword.trim() + "%");
        }

        if ("archived".equalsIgnoreCase(status)) {
            where.append(" AND metadata->>'archived' = 'true'");
        } else if (!"all".equalsIgnoreCase(status)) {
            // 默认仅查活跃记忆 (archived != true)
            where.append(" AND (metadata->>'archived' IS NULL OR metadata->>'archived' = 'false')");
        }

        String countSql = "SELECT COUNT(*) FROM " + MEMORY_TABLE + " " + where;
        long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        String listSql = "SELECT id, content, metadata FROM " + MEMORY_TABLE + " " + where
                + " ORDER BY (metadata->>'updated_at') DESC NULLS LAST LIMIT ? OFFSET ?";
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(limit);
        listArgs.add(offset);

        RowMapper<MemoryDto> mapper = (rs, rowNum) -> {
            String id = rs.getString("id");
            String content = rs.getString("content");
            Map<String, Object> meta = parseMetadata(rs.getObject("metadata"));

            String category = (meta.get("category") instanceof String c) ? c : null;
            Double confidence = (meta.get("confidence") instanceof Number n) ? n.doubleValue() : null;
            String updatedAt = (meta.get("updated_at") instanceof String u) ? u : null;

            Double priority = (meta.get("priority") instanceof Number n) ? n.doubleValue() : 1.0;
            Integer accessCount = (meta.get("access_count") instanceof Number n) ? n.intValue() : 0;
            String lastAccessedAt = (meta.get("last_accessed_at") instanceof String a) ? a : updatedAt;
            Boolean archived = Boolean.TRUE.equals(meta.get("archived"))
                    || "true".equalsIgnoreCase(String.valueOf(meta.get("archived")));

            double priorityScore = (forgetService != null)
                    ? forgetService.calculatePriorityScore(priority, accessCount, lastAccessedAt, updatedAt)
                    : priority;

            return new MemoryDto(
                    id,
                    content,
                    category,
                    confidence,
                    updatedAt,
                    priority,
                    accessCount,
                    lastAccessedAt,
                    priorityScore,
                    archived);
        };

        List<MemoryDto> items = jdbcTemplate.query(listSql, mapper, listArgs.toArray());
        return new MemoryDto.ListResponse(items, total);
    }

    public ListResponse listMemories(String userId, String keyword, int limit, int offset) {
        return listMemories(userId, keyword, "active", limit, offset);
    }

    /**
     * 归属校验：查询指定 id 的记忆。
     */
    public Optional<MemoryDto> findByIdAndUser(String id, String userId) {
        String sql = "SELECT id, content, metadata FROM " + MEMORY_TABLE + " WHERE id = ? AND metadata->>'userId' = ?";
        List<MemoryDto> list = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String content = rs.getString("content");
                    Map<String, Object> meta = parseMetadata(rs.getObject("metadata"));
                    String category = (meta.get("category") instanceof String c) ? c : null;
                    Double confidence = (meta.get("confidence") instanceof Number n) ? n.doubleValue() : null;
                    String updatedAt = (meta.get("updated_at") instanceof String u) ? u : null;
                    Double priority = (meta.get("priority") instanceof Number n) ? n.doubleValue() : 1.0;
                    Integer accessCount = (meta.get("access_count") instanceof Number n) ? n.intValue() : 0;
                    String lastAccessedAt = (meta.get("last_accessed_at") instanceof String a) ? a : updatedAt;
                    Boolean archived = Boolean.TRUE.equals(meta.get("archived"))
                            || "true".equalsIgnoreCase(String.valueOf(meta.get("archived")));

                    double priorityScore = (forgetService != null)
                            ? forgetService.calculatePriorityScore(priority, accessCount, lastAccessedAt, updatedAt)
                            : priority;

                    return new MemoryDto(
                            rs.getString("id"),
                            content,
                            category,
                            confidence,
                            updatedAt,
                            priority,
                            accessCount,
                            lastAccessedAt,
                            priorityScore,
                            archived);
                },
                id,
                userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 编辑记忆（更新内容、分类、优先级权重、归档状态）。
     */
    public Optional<MemoryDto> updateMemory(
            String id, String userId, String content, String category, Double priority, Boolean archived) {
        Optional<MemoryDto> existingOpt = findByIdAndUser(id, userId);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        MemoryDto existing = existingOpt.get();

        String newContent = (content != null && !content.isBlank()) ? content.trim() : existing.getContent();
        String newCategory = (category != null) ? category.trim() : existing.getCategory();
        Double newPriority = (priority != null && priority > 0.0) ? priority : existing.getPriority();
        Boolean newArchived = (archived != null) ? archived : existing.getArchived();

        try {
            vectorStore.delete(List.of(id));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", userId);
            metadata.put("updated_at", Instant.now().toString());
            metadata.put("sourceType", "long_term_memory");
            if (newCategory != null && !newCategory.isBlank()) {
                metadata.put("category", newCategory);
            }
            if (existing.getConfidence() != null) {
                metadata.put("confidence", existing.getConfidence());
            }
            metadata.put("priority", newPriority);
            metadata.put("access_count", existing.getAccessCount() != null ? existing.getAccessCount() : 0);
            if (existing.getLastAccessedAt() != null) {
                metadata.put("last_accessed_at", existing.getLastAccessedAt());
            }
            metadata.put("archived", newArchived);

            Document updatedDoc = new Document(id, newContent, metadata);
            vectorStore.add(List.of(updatedDoc));
            log.info("长期记忆已更新: id={}, userId={}, priority={}, archived={}", id, userId, newPriority, newArchived);

            double priorityScore = (forgetService != null)
                    ? forgetService.calculatePriorityScore(
                            newPriority,
                            existing.getAccessCount(),
                            existing.getLastAccessedAt(),
                            Instant.now().toString())
                    : newPriority;

            return Optional.of(new MemoryDto(
                    id,
                    newContent,
                    newCategory,
                    existing.getConfidence(),
                    Instant.now().toString(),
                    newPriority,
                    existing.getAccessCount(),
                    existing.getLastAccessedAt(),
                    priorityScore,
                    newArchived));
        } catch (Exception e) {
            log.warn("更新长期记忆失败（已降级）: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<MemoryDto> updateMemory(String id, String userId, String content, String category) {
        return updateMemory(id, userId, content, category, null, null);
    }

    /**
     * 删除记忆。
     */
    public boolean deleteMemory(String id, String userId) {
        if (findByIdAndUser(id, userId).isEmpty()) {
            return false;
        }
        try {
            vectorStore.delete(List.of(id));
            log.info("长期记忆已删除: id={}, userId={}", id, userId);
            return true;
        } catch (Exception e) {
            log.warn("删除长期记忆失败（已降级）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 异步记录记忆命中的访问频次与最近访问时间。
     */
    public void recordAccess(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            String nowIso = Instant.now().toString();
            for (String id : ids) {
                String sql = "UPDATE " + MEMORY_TABLE
                        + " SET metadata = jsonb_set(jsonb_set(metadata, '{access_count}', to_jsonb(COALESCE((metadata->>'access_count')::int, 0) + 1)), '{last_accessed_at}', to_jsonb(?::text))"
                        + " WHERE id = ?";
                jdbcTemplate.update(sql, nowIso, id);
            }
        } catch (Exception e) {
            log.warn("更新记忆访问频次异常（已降级）: {}", e.getMessage());
        }
    }

    /**
     * 记忆优先级衰减扫描：自动归档得分 < 0.3 的记忆，物理删除得分 < 0.1 的低权重记忆。
     *
     * @return 结果统计 Map ("archived", "deleted")
     */
    public Map<String, Integer> decayMemories(String userId) {
        ListResponse resp = listMemories(userId, null, "active", 500, 0);
        int archivedCount = 0;
        int deletedCount = 0;

        for (MemoryDto item : resp.getItems()) {
            double score = (item.getPriorityScore() != null) ? item.getPriorityScore() : 1.0;
            if (score < 0.1 && (item.getPriority() == null || item.getPriority() <= 1.0)) {
                deleteMemory(item.getId(), userId);
                deletedCount++;
            } else if (score < 0.3 && !Boolean.TRUE.equals(item.getArchived())) {
                updateMemory(item.getId(), userId, item.getContent(), item.getCategory(), item.getPriority(), true);
                archivedCount++;
            }
        }

        log.info("记忆衰减优化执行完成: userId={}, 自动归档={}, 自动清理={}", userId, archivedCount, deletedCount);
        return Map.of("archived", archivedCount, "deleted", deletedCount);
    }

    /**
     * 记忆摘要压缩：将同一分类下细粒度记忆条数 >= 5 的总结压缩为 1~2 条高层陈述句。
     */
    public int compressMemories(String userId) {
        if (forgetService == null) {
            return 0;
        }
        ListResponse resp = listMemories(userId, null, "active", 500, 0);
        Map<String, List<MemoryDto>> categoryGroups = resp.getItems().stream()
                .collect(Collectors.groupingBy(m -> m.getCategory() != null ? m.getCategory() : "通用"));

        int compressedCategories = 0;
        for (Map.Entry<String, List<MemoryDto>> entry : categoryGroups.entrySet()) {
            List<MemoryDto> group = entry.getValue();
            if (group.size() >= 5) {
                String category = entry.getKey();
                List<String> contents = group.stream().map(m -> m.getContent()).toList();
                List<String> summaries = forgetService.compressMemories(category, contents);

                if (summaries != null && !summaries.isEmpty()) {
                    // 替换：删除原有细粒度记忆，插入压缩后的总结
                    for (MemoryDto old : group) {
                        deleteMemory(old.getId(), userId);
                    }
                    for (String summary : summaries) {
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("userId", userId);
                        meta.put("updated_at", Instant.now().toString());
                        meta.put("sourceType", "long_term_memory_summary");
                        meta.put("category", category);
                        meta.put("priority", 1.2); // 摘要提高初始权重
                        meta.put("access_count", 0);
                        meta.put("archived", false);

                        Document doc = new Document(summary, meta);
                        vectorStore.add(List.of(doc));
                    }
                    compressedCategories++;
                }
            }
        }

        log.info("记忆摘要压缩执行完成: userId={}, 成功压缩分类数={}", userId, compressedCategories);
        return compressedCategories;
    }

    /**
     * 冲突检测与合并：检测同用户记忆中的矛盾并自动判定保留/合并。
     */
    public int resolveConflicts(String userId) {
        if (forgetService == null) {
            return 0;
        }
        ListResponse resp = listMemories(userId, null, "active", 100, 0);
        List<MemoryDto> items = resp.getItems();
        int resolvedCount = 0;

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                MemoryDto m1 = items.get(i);
                MemoryDto m2 = items.get(j);

                // 仅检测同分类或相似主题
                if (m1.getCategory() != null && m1.getCategory().equals(m2.getCategory())) {
                    MemoryForgetService.ConflictDecision decision =
                            forgetService.evaluateConflict(m1.getContent(), m2.getContent());
                    if ("RETAIN_NEW".equalsIgnoreCase(decision.getAction())) {
                        deleteMemory(m2.getId(), userId);
                        resolvedCount++;
                    } else if ("RETAIN_OLD".equalsIgnoreCase(decision.getAction())) {
                        deleteMemory(m1.getId(), userId);
                        resolvedCount++;
                        break;
                    } else if ("MERGE".equalsIgnoreCase(decision.getAction()) && decision.getMergedContent() != null) {
                        updateMemory(m1.getId(), userId, decision.getMergedContent(), m1.getCategory());
                        deleteMemory(m2.getId(), userId);
                        resolvedCount++;
                        break;
                    }
                }
            }
        }

        log.info("记忆冲突清理执行完成: userId={}, 解决冲突数={}", userId, resolvedCount);
        return resolvedCount;
    }
}
