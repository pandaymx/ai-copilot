package xyz.ppmblszdp.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import xyz.ppmblszdp.ai.memory.SafeVectorStore;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.dto.RagDocumentMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档相似检索服务。
 *
 * <p>
 * <b>所有过滤字段强制 String 类型（回应风险3）</b>：
 * {@code userId} / {@code sourceType} 在 {@code FilterExpressionBuilder} 中统一以
 * String 构建，
 * 确保与 {@link RagMetadataEnricher} 写入的类型一致，避免 PgVector JSONB 过滤因 Long/String
 * 失配失效。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    /** 列表全量拉取上限，避免超大集合拖垮前端（回应性能考量）。 */
    private static final int LIST_FETCH_LIMIT = 1000;

    private final VectorStore ragVectorStore;
    private final RagProperties properties;

    public RagQueryService(@Qualifier("ragVectorStore") VectorStore ragVectorStore,
            RagProperties properties) {
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
    }

    /**
     * 列出已入库文档：拉取满足过滤条件的全部向量记录，按 {@code source} 内存聚合为文档视图。
     *
     * <p>
     * 聚合粒度：同一 {@code source} 下的多个 chunk 合并为一条 {@link RagDocumentMeta}，
     * chunkCount 为该 source 的向量数，ingestedAt 取最新一条，contentHash 取首条。
     *
     * @param userId     用户 ID 过滤（null/空表示不过滤，由上游按身份解析）
     * @param sourceType 来源类型过滤（null/空表示不过滤）
     * @param limit      最多聚合多少文档（source）
     * @return 聚合后的文档列表（按最新入库时间降序）
     */
    public List<RagDocumentMeta> listDocuments(String userId, String sourceType, int limit) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query("") // 列表仅用 filter，不需要语义相似
                .topK(LIST_FETCH_LIMIT);

        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        Filter.Expression filter = null;
        boolean hasUser = userId != null && !userId.isBlank();
        boolean hasType = sourceType != null && !sourceType.isBlank();

        if (hasUser && hasType) {
            filter = feb.and(feb.eq("userId", userId), feb.eq("sourceType", sourceType)).build();
        } else if (hasUser) {
            filter = feb.eq("userId", userId).build();
        } else if (hasType) {
            filter = feb.eq("sourceType", sourceType).build();
        }
        if (filter != null) {
            builder.filterExpression(filter);
        }

        List<Document> records;
        try {
            records = ragVectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("RAG 列表检索异常（已降级为空）: userId={} sourceType={} error={}",
                    userId, sourceType, e.getMessage());
            return Collections.emptyList();
        }

        // 内存按 source 聚合（同 source 可能跨 chunk，需保留首条 contentHash / 最新 ingestedAt）
        Map<String, RagDocumentMeta> bySource = new LinkedHashMap<>();
        for (Document doc : records) {
            Map<String, Object> m = doc.getMetadata();
            String source = String.valueOf(m.getOrDefault("source", "unknown"));
            String uid = String.valueOf(m.getOrDefault("userId", "system"));
            RagDocumentMeta existing = bySource.get(source);
            if (existing == null) {
                bySource.put(source, toMeta(source, uid, m));
            } else {
                int chunkCount = existing.chunkCount() + 1;
                String ingestedAt = latest(existing.ingestedAt(),
                        String.valueOf(m.getOrDefault("ingestedAt", "")));
                bySource.put(source, new RagDocumentMeta(
                        existing.docId(), existing.source(), existing.sourceType(),
                        existing.fileName(), existing.title(), existing.userId(),
                        chunkCount, ingestedAt, existing.contentHash()));
            }
        }

        List<RagDocumentMeta> items = new ArrayList<>(bySource.values());
        items.sort(Comparator.comparing(RagDocumentMeta::ingestedAt).reversed());
        int cap = (limit > 0) ? Math.min(limit, LIST_FETCH_LIMIT) : LIST_FETCH_LIMIT;
        if (items.size() > cap) {
            items = items.subList(0, cap);
        }
        return items;
    }

    /**
     * 向量库可用性与统计：enabled 来自配置，available 来自底层 VectorStore 装配状态，
     * estimatedCount 为最佳努力的文档（source）估算。
     */
    public Map<String, Object> collectionStats() {
        List<RagDocumentMeta> all = listDocuments(null, null, LIST_FETCH_LIMIT);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("enabled", properties.isEnabled());
        stats.put("available", ragVectorStore instanceof SafeVectorStore
                ? ((SafeVectorStore) ragVectorStore).isAvailable()
                : true);
        stats.put("collectionName", properties.resolveCollectionName());
        stats.put("documentCount", (long) all.size());
        stats.put("vectorCount", all.stream().mapToLong(RagDocumentMeta::chunkCount).sum());
        return stats;
    }

    private RagDocumentMeta toMeta(String source, String userId, Map<String, Object> m) {
        String hash = String.valueOf(m.getOrDefault("contentHash", ""));
        String docId = source + "#" + hash;
        return new RagDocumentMeta(
                docId,
                source,
                String.valueOf(m.getOrDefault("sourceType", "")),
                String.valueOf(m.getOrDefault("fileName", "")),
                String.valueOf(m.getOrDefault("title", "")),
                userId,
                1,
                String.valueOf(m.getOrDefault("ingestedAt", "")),
                hash);
    }

    private String latest(String a, String b) {
        if (a == null || a.isBlank())
            return b;
        if (b == null || b.isBlank())
            return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * 按查询文本和用户 ID 检索相似文档片段。
     *
     * @param query  查询文本
     * @param userId 用户 ID（用于隔离，String 类型）
     * @return 相似文档列表（按相似度降序）
     */
    public List<Document> search(String query, String userId) {
        return search(query, userId, properties.resolveTopK());
    }

    /**
     * 按查询文本、用户 ID 和来源类型检索相似文档片段。
     *
     * @param query      查询文本
     * @param userId     用户 ID（String）
     * @param sourceType 来源类型过滤，可为 null 表示不过滤
     * @param topK       Top-K
     * @return 相似文档列表
     */
    public List<Document> search(String query, String userId, String sourceType, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK > 0 ? topK : properties.resolveTopK());

        // 构建过滤表达式（全部 String 比较）
        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        Filter.Expression filter = null;

        // 先用 Op 收集筛选条件，最后统一构建 Expression
        if (userId != null && !userId.isBlank()) {
            filter = feb.eq("userId", userId).build();
        }

        if (sourceType != null && !sourceType.isBlank()) {
            var srcOp = feb.eq("sourceType", sourceType);
            if (filter != null) {
                // 重新构造 and compound；feb.and() 返回 Op → .build() 得到 Expression
                var userOp = feb.eq("userId", userId);
                filter = feb.and(userOp, srcOp).build();
            } else {
                filter = srcOp.build();
            }
        }

        if (filter != null) {
            builder.filterExpression(filter);
        }

        try {
            SearchRequest request = builder.build();
            List<Document> results = ragVectorStore.similaritySearch(request);
            log.debug("RAG 检索完成: query=... userId={} sourceType={} topK={} hits={}",
                    userId, sourceType, topK, results.size());
            return results;
        } catch (Exception e) {
            log.warn("RAG 检索异常（已降级为空结果）: query=... error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 基础检索（按查询文本 + userId）。
     */
    public List<Document> search(String query, String userId, int topK) {
        return search(query, userId, null, topK);
    }
}
