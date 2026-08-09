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
import xyz.ppmblszdp.ai.rag.RagProperties;

import java.util.Collections;
import java.util.List;

/**
 * RAG 文档相似检索服务。
 *
 * <p><b>所有过滤字段强制 String 类型（回应风险3）</b>：
 * {@code userId} / {@code sourceType} 在 {@code FilterExpressionBuilder} 中统一以 String 构建，
 * 确保与 {@link RagMetadataEnricher} 写入的类型一致，避免 PgVector JSONB 过滤因 Long/String 失配失效。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final VectorStore ragVectorStore;
    private final RagProperties properties;

    public RagQueryService(@Qualifier("ragVectorStore") VectorStore ragVectorStore,
                           RagProperties properties) {
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
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
