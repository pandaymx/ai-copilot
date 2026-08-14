package xyz.ppmblszdp.ai.memory;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

/**
 * 长期记忆配置：用户画像/偏好经 embedding 存入 pgvector，每次请求按 userId 维度向量检索后注入。
 *
 * <p>
 * 与会话记忆职责分离：会话记忆=短期上下文，长期记忆=跨会话个性化。
 * 检索隔离通过 {@code metadata.userId} +
 * {@code FilterExpressionBuilder.eq("userId", ...)} 实现，
 * 确保用户 A 不会检索到用户 B 的偏好。
 *
 * <p>
 * 仅在 {@code app.ai.memory.enabled=true} 时装配；PgVectorStore 由
 * spring-ai-starter-vector-store-pgvector
 * 自动装配（复用同一 PostgreSQL 实例）。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.memory", name = "enabled", havingValue = "true")
public class LongTermMemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryConfig.class);

    @Bean
    public LongTermMemoryAdvisorFactory longTermMemoryAdvisorFactory(
            ObjectProvider<VectorStore> vectorStore, AiProviderProperties properties) {
        VectorStore vs = vectorStore.getIfAvailable();
        int topK = properties.resolveMemory().resolveLongTermTopK();
        if (vs == null) {
            log.warn("未检测到 VectorStore（pgvector 未装配），长期记忆降级为空（不注入长期记忆）");
            return (userId) -> null;
        }
        VectorStore safeVs = new SafeVectorStore(vs);
        log.info("长期记忆工厂装配完成：SafeVectorStore (pgvector 检索 TopK={})", topK);
        return (userId) -> {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            var filter = b.and(b.eq("userId", userId), b.ne("archived", true)).build();
            SearchRequest search =
                    SearchRequest.builder().topK(topK).filterExpression(filter).build();
            PromptTemplate customPromptTemplate = new PromptTemplate("""
					{query}

					[参考信息/用户偏好]
					---------------------
					{question_answer_context}
					---------------------

					注意：以上上下文信息仅供参考。若参考信息中未包含相关答案，请直接利用你的通用知识库正常解答用户的问题，切勿拒绝回答。
					""");
            return QuestionAnswerAdvisor.builder(safeVs)
                    .searchRequest(search)
                    .promptTemplate(customPromptTemplate)
                    .build();
        };
    }

    @Bean
    public LongTermMemoryProcessor longTermMemoryProcessor(
            ObjectProvider<VectorStore> vectorStore,
            ObjectProvider<ProviderRegistry> providerRegistry,
            ObjectProvider<xyz.ppmblszdp.ai.service.MemoryForgetService> forgetServiceProvider,
            AiProviderProperties properties) {
        VectorStore vs = vectorStore.getIfAvailable();
        ProviderRegistry registry = providerRegistry.getIfAvailable();
        xyz.ppmblszdp.ai.service.MemoryForgetService forgetService = forgetServiceProvider.getIfAvailable();
        log.info("长期记忆核心处理器 (LongTermMemoryProcessor) 装配完成 (包含冲突判定与衰减支持)");
        return new LongTermMemoryProcessor(vs, registry, forgetService, properties);
    }

    @Bean
    public LongTermMemoryWriter longTermMemoryWriter(LongTermMemoryProcessor processor) {
        return (userId, content) -> {
            if (userId == null || userId.isBlank() || content == null || content.isBlank()) {
                return;
            }
            try {
                processor.dedupAndUpsert(userId, content, null, null);
            } catch (Exception e) {
                log.warn("写入长期记忆向量库异常: {}", e.getMessage());
            }
        };
    }

    /** 按 userId 动态构造带过滤的长期记忆 Advisor（每次请求独立，避免并发串线）。 */
    @FunctionalInterface
    public interface LongTermMemoryAdvisorFactory {
        Advisor forUser(String userId);
    }

    /** 写入长期记忆的执行器（包含以 userId 为维度的向量存储写入）。 */
    @FunctionalInterface
    public interface LongTermMemoryWriter {
        void write(String userId, String content);
    }

    /** 写入长期记忆的辅助方法：所有文档必须带 {@code userId} metadata。 */
    public static Document withUserId(Document doc, String userId) {
        Map<String, Object> meta = new HashMap<>(doc.getMetadata());
        meta.put("userId", userId);
        return new Document(doc.getId(), doc.getText(), meta);
    }
}
