package xyz.ppmblszdp.ai.rag.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.memory.SafeVectorStore;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.dto.RagDocumentMeta;
import xyz.ppmblszdp.ai.rag.graph.service.GraphRagService;
import xyz.ppmblszdp.ai.rag.repository.RagSearchRepository;
import xyz.ppmblszdp.ai.rag.rerank.RagReranker;

/**
 * RAG 文档相似检索服务（支持双路召回 + RRF 倒数排名融合 + 可选 Rerank 精排）。
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
    private final RagSearchRepository searchRepository;
    private final RagReranker reranker;
    private final GraphRagService graphRagService;

    public RagQueryService(
            @Qualifier("ragVectorStore") VectorStore ragVectorStore,
            RagProperties properties,
            ObjectProvider<RagSearchRepository> searchRepositoryProvider,
            ObjectProvider<RagReranker> rerankerProvider,
            ObjectProvider<GraphRagService> graphRagServiceProvider) {
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
        this.searchRepository = searchRepositoryProvider != null ? searchRepositoryProvider.getIfAvailable() : null;
        this.reranker = rerankerProvider != null ? rerankerProvider.getIfAvailable() : null;
        this.graphRagService = graphRagServiceProvider != null ? graphRagServiceProvider.getIfAvailable() : null;
    }

    public RagQueryService(
            VectorStore ragVectorStore,
            RagProperties properties,
            RagSearchRepository searchRepository,
            RagReranker reranker,
            GraphRagService graphRagService) {
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
        this.searchRepository = searchRepository;
        this.reranker = reranker;
        this.graphRagService = graphRagService;
    }

    public RagQueryService(
            VectorStore ragVectorStore,
            RagProperties properties,
            RagSearchRepository searchRepository,
            RagReranker reranker) {
        this(ragVectorStore, properties, searchRepository, reranker, (GraphRagService) null);
    }

    public RagQueryService(VectorStore ragVectorStore, RagProperties properties) {
        this(ragVectorStore, properties, (RagSearchRepository) null, (RagReranker) null, (GraphRagService) null);
    }

    /**
     * 列出已入库文档：拉取满足过滤条件的全部向量记录，按 {@code source} 内存聚合为文档视图。
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
            filter = feb.and(feb.eq("userId", userId), feb.eq("sourceType", sourceType))
                    .build();
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
            log.warn("RAG 列表检索异常（已降级为空）: userId={} sourceType={} error={}", userId, sourceType, e.getMessage());
            return Collections.emptyList();
        }

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
                String ingestedAt = latest(existing.ingestedAt(), String.valueOf(m.getOrDefault("ingestedAt", "")));
                bySource.put(
                        source,
                        new RagDocumentMeta(
                                existing.docId(),
                                existing.source(),
                                existing.sourceType(),
                                existing.fileName(),
                                existing.title(),
                                existing.userId(),
                                chunkCount,
                                ingestedAt,
                                existing.contentHash()));
            }
        }

        List<RagDocumentMeta> items = new ArrayList<>(bySource.values());
        items.sort(Comparator.comparing((RagDocumentMeta m) -> m.ingestedAt()).reversed());
        int cap = (limit > 0) ? Math.min(limit, LIST_FETCH_LIMIT) : LIST_FETCH_LIMIT;
        if (items.size() > cap) {
            items = items.subList(0, cap);
        }
        return items;
    }

    /**
     * 向量库可用性与统计。
     */
    public Map<String, Object> collectionStats() {
        List<RagDocumentMeta> all = listDocuments(null, null, LIST_FETCH_LIMIT);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("enabled", properties.isEnabled());
        stats.put("hybridSearchEnabled", properties.isHybridSearchEnabled());
        stats.put("rerankEnabled", properties.isRerankEnabled());
        stats.put(
                "available",
                ragVectorStore instanceof SafeVectorStore ? ((SafeVectorStore) ragVectorStore).isAvailable() : true);
        stats.put("collectionName", properties.resolveCollectionName());
        stats.put("documentCount", (long) all.size());
        stats.put("vectorCount", all.stream().mapToLong(m -> m.chunkCount()).sum());
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
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    public List<Document> search(String query, String userId) {
        return search(query, userId, properties.resolveTopK());
    }

    public List<Document> search(String query, String userId, int topK) {
        return search(query, userId, null, topK);
    }

    /**
     * 按查询文本、用户 ID 和来源类型检索文档片段（根据配置自动支持单向量 / 双路 RRF 混合检索与精排）。
     *
     * @param query      查询文本
     * @param userId     用户 ID（String）
     * @param sourceType 来源类型过滤，可为 null 表示不过滤
     * @param topK       Top-K
     * @return 匹配文档列表
     */
    public List<Document> search(String query, String userId, String sourceType, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int targetTopK = topK > 0 ? topK : properties.resolveTopK();

        // 0. 优先进行结构化知识查询探查 (若开启 extraction-enabled 且 searchRepository 可用)
        if (properties.isExtractionEnabled() && searchRepository != null) {
            try {
                List<Document> structuredDocs =
                        searchRepository.searchStructuredKnowledge(query, userId, sourceType, targetTopK);
                if (structuredDocs != null && !structuredDocs.isEmpty()) {
                    log.info("RAG 结构化查询路由命中: query={} count={} userId={}", query, structuredDocs.size(), userId);
                    return structuredDocs;
                }
            } catch (Exception e) {
                log.warn("RAG 结构化查询探查失败（平滑降级至混合/向量检索）: query={} error={}", query, e.getMessage());
            }
        }

        List<Document> baseResults;
        // 仅走单路向量检索逻辑（若关闭混合检索或未注入 RagSearchRepository）
        if (!properties.isHybridSearchEnabled() || searchRepository == null) {
            baseResults = searchVectorOnly(query, userId, sourceType, targetTopK);
        } else {
            // 双路召回 + RRF 融合逻辑
            baseResults = searchHybridRrf(query, userId, sourceType, targetTopK);
        }

        // 1. GraphRAG 知识图谱拓扑关系与多跳实体联合召回 (若开启 graph-rag-enabled 且 graphRagService 可用)
        if (properties.isGraphRagEnabled() && graphRagService != null) {
            try {
                List<Document> graphDocs = graphRagService.retrieveGraphDocuments(query, userId, 2);
                if (graphDocs != null && !graphDocs.isEmpty()) {
                    log.info("GraphRAG 实体拓扑关联召回成功: query={} count={} userId={}", query, graphDocs.size(), userId);
                    List<Document> combined = new ArrayList<>(graphDocs);
                    combined.addAll(baseResults);
                    return combined;
                }
            } catch (Exception e) {
                log.warn("GraphRAG 检索探查异常（降级至常规检索）: query={} error={}", query, e.getMessage());
            }
        }

        return baseResults;
    }

    private List<Document> searchVectorOnly(String query, String userId, String sourceType, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(topK);

        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        Filter.Expression filter = buildFilter(feb, userId, sourceType);
        if (filter != null) {
            builder.filterExpression(filter);
        }

        try {
            return ragVectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("RAG 向量检索异常（已降级为空）: query=... error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Document> searchHybridRrf(String query, String userId, String sourceType, int topK) {
        int candidateLimit = Math.max(topK * properties.resolveCandidatePoolMultiplier(), 20);

        // 1. 向量召回
        List<Document> vectorDocs = searchVectorOnly(query, userId, sourceType, candidateLimit);

        // 2. 全文与模糊召回
        List<Document> fullTextDocs;
        try {
            fullTextDocs = searchRepository.searchFullText(query, userId, sourceType, candidateLimit);
        } catch (Exception e) {
            log.warn("RAG 全文召回异常（已降级为仅向量）: error={}", e.getMessage());
            fullTextDocs = Collections.emptyList();
        }

        if (vectorDocs.isEmpty() && fullTextDocs.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. RRF (Reciprocal Rank Fusion) 融合
        int rrfK = properties.resolveRrfK();
        Map<String, DocumentRrfScore> scoreMap = new LinkedHashMap<>();

        // 累计向量排名
        for (int rank = 0; rank < vectorDocs.size(); rank++) {
            Document doc = vectorDocs.get(rank);
            String docKey = resolveDocKey(doc);
            double score = 1.0 / (rrfK + (rank + 1));
            DocumentRrfScore entry = scoreMap.computeIfAbsent(docKey, k -> new DocumentRrfScore(doc));
            entry.addVectorScore(score, rank + 1);
        }

        // 累计全文排名
        for (int rank = 0; rank < fullTextDocs.size(); rank++) {
            Document doc = fullTextDocs.get(rank);
            String docKey = resolveDocKey(doc);
            double score = 1.0 / (rrfK + (rank + 1));
            DocumentRrfScore entry = scoreMap.computeIfAbsent(docKey, k -> new DocumentRrfScore(doc));
            entry.addFullTextScore(score, rank + 1);
        }

        List<Document> rrfCandidates = new ArrayList<>();
        for (DocumentRrfScore entry : scoreMap.values()) {
            Document orig = entry.doc;
            Map<String, Object> meta = new HashMap<>(orig.getMetadata());
            meta.put("rrfScore", entry.rrfScore);
            if (entry.vectorRank > 0) meta.put("vectorRank", entry.vectorRank);
            if (entry.fullTextRank > 0) meta.put("fullTextRank", entry.fullTextRank);
            rrfCandidates.add(new Document(orig.getId(), orig.getText(), meta));
        }

        rrfCandidates.sort((d1, d2) -> Double.compare((double) d2.getMetadata().getOrDefault("rrfScore", 0.0), (double)
                d1.getMetadata().getOrDefault("rrfScore", 0.0)));

        // 4. 可选 Rerank 精排
        if (properties.isRerankEnabled() && reranker != null) {
            return reranker.rerank(query, rrfCandidates, topK);
        }

        return rrfCandidates.size() > topK ? rrfCandidates.subList(0, topK) : rrfCandidates;
    }

    private String resolveDocKey(Document doc) {
        if (doc.getId() != null && !doc.getId().isBlank()) {
            return doc.getId();
        }
        Object hash = doc.getMetadata().get("contentHash");
        if (hash != null) {
            return hash.toString();
        }
        return String.valueOf(doc.getText().hashCode());
    }

    private Filter.Expression buildFilter(FilterExpressionBuilder feb, String userId, String sourceType) {
        Filter.Expression filter = null;
        if (userId != null && !userId.isBlank()) {
            filter = feb.eq("userId", userId).build();
        }
        if (sourceType != null && !sourceType.isBlank()) {
            var srcOp = feb.eq("sourceType", sourceType);
            if (filter != null) {
                var userOp = feb.eq("userId", userId);
                filter = feb.and(userOp, srcOp).build();
            } else {
                filter = srcOp.build();
            }
        }
        return filter;
    }

    private static class DocumentRrfScore {
        final Document doc;
        double rrfScore = 0.0;
        int vectorRank = -1;
        int fullTextRank = -1;

        DocumentRrfScore(Document doc) {
            this.doc = doc;
        }

        void addVectorScore(double score, int rank) {
            this.rrfScore += score;
            this.vectorRank = rank;
        }

        void addFullTextScore(double score, int rank) {
            this.rrfScore += score;
            this.fullTextRank = rank;
        }
    }
}
