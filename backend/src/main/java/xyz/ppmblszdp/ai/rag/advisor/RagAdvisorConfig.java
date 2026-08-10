package xyz.ppmblszdp.ai.rag.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.memory.SafeVectorStore;
import xyz.ppmblszdp.ai.rag.RagProperties;

/**
 * RAG 检索 Advisor 配置：提供 {@link QuestionAnswerAdvisor} 工厂，按 userId/sourceType metadata
 * 过滤后可挂接到 {@code ChatClient} 的 call/stream 链路。
 *
 * <p><b>过滤类型安全（回应风险3）</b>：所有 {@code FilterExpressionBuilder} 的过滤键
 * 一律以 String 比较（{@code eq("userId", "...")}），确保与 {@code RagMetadataEnricher}
 * 写入的 String 类型一致，避免 PgVector JSONB 过滤失效。
 *
 * <p>仅在 {@code app.ai.rag.enabled=true} 时装配，与 {@link LongTermMemoryAdvisorFactory} 模式对齐。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagAdvisorConfig {

    private static final Logger log = LoggerFactory.getLogger(RagAdvisorConfig.class);

    @Bean
    public RagAdvisorFactory ragAdvisorFactory(
            @Qualifier("ragVectorStore") ObjectProvider<VectorStore> ragVectorStore,
            RagProperties properties) {

        VectorStore vs = ragVectorStore.getIfAvailable();
        int topK = properties.resolveTopK();

        if (vs == null) {
            log.warn("未检测到 RAG VectorStore，RAG Advisor 降级为空（不注入文档检索）");
            return (userId, sourceType) -> null;
        }

        VectorStore safeVs = vs instanceof SafeVectorStore ? vs : new SafeVectorStore(vs);
        log.info("RAG Advisor 工厂装配完成: SafeVectorStore, TopK={}", topK);

        return (userId, sourceType) -> {
            FilterExpressionBuilder feb = new FilterExpressionBuilder();
            var op = feb.eq("userId", (userId != null && !userId.isBlank()) ? userId : UserIdentityFilter.DEFAULT_USER_ID);
            Filter.Expression filter;
            if (sourceType != null && !sourceType.isBlank()) {
                filter = feb.and(op, feb.eq("sourceType", sourceType)).build();
            } else {
                filter = op.build();
            }

            SearchRequest search = SearchRequest.builder()
                    .topK(topK)
                    .filterExpression(filter)
                    .build();

            PromptTemplate promptTemplate = new PromptTemplate("""
                    {query}

                    [📄 RAG 文档检索上下文]
                    ---------------------
                    {question_answer_context}
                    ---------------------

                    注意：以上为从你上传/录入的文档中检索到的相关片段。若其中不包含与问题相关的信息，
                    请直接利用你的通用知识库正常回答，勿拒绝回答。""");

            return QuestionAnswerAdvisor.builder(safeVs)
                    .searchRequest(search)
                    .promptTemplate(promptTemplate)
                    .build();
        };
    }

    /** 按 userId 和可选的 sourceType 动态构造带过滤的 RAG 检索 Advisor（每次请求独立，避免并发串线）。 */
    @FunctionalInterface
    public interface RagAdvisorFactory {
        Advisor forUser(String userId, String sourceType);
    }
}
