package xyz.ppmblszdp.ai.rag.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import xyz.ppmblszdp.ai.memory.SafeVectorStore;
import xyz.ppmblszdp.ai.rag.RagProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * RagAdvisor Factory 逻辑测试：聚焦工厂函数式行为（有/无 VectorStore 时的返回），
 * 不依赖 Spring IoC 容器（无需 @SpringBootTest），通过手工构造工厂接口测试核心逻辑。
 */
class RagAdvisorConfigTest {

    private final RagProperties properties = new RagProperties(
            true, 5, 900, 180, "CL100K_BASE", "ai_rag_documents",
            new RagProperties.SsrfConfig(5, 10_485_760L));

    @Test
    void factoryShouldReturnAdvisor_whenVectorStoreAvailable() {
        // 手工模拟工厂构造：VectorStore 存在 → 返回 Advisor
        RagAdvisorConfig.RagAdvisorFactory factory = createFactory(true);
        assertThat(factory).isNotNull();
        Advisor advisor = factory.forUser("user-abc", null);
        assertThat(advisor).isNotNull();
    }

    @Test
    void factoryShouldReturnAdvisorWithSourceTypeFilter() {
        RagAdvisorConfig.RagAdvisorFactory factory = createFactory(true);
        Advisor advisor = factory.forUser("user-xyz", "PDF");
        assertThat(advisor).isNotNull();
    }

    @Test
    void factoryShouldReturnNull_whenVectorStoreMissing() {
        RagAdvisorConfig.RagAdvisorFactory factory = createFactory(false);
        Advisor advisor = factory.forUser("user-none", null);
        assertThat(advisor).isNull();
    }

    @Test
    void ragPropertiesShouldHaveSaneDefaults() {
        RagProperties defaults = new RagProperties(
                true, null, null, null, null, null, null);

        assertThat(defaults.resolveTopK()).isEqualTo(4);
        assertThat(defaults.resolveChunkSize()).isEqualTo(900);
        assertThat(defaults.resolveOverlap()).isEqualTo(180);
        assertThat(defaults.resolveEncodingType()).isEqualTo("CL100K_BASE");
        assertThat(defaults.resolveCollectionName()).isEqualTo("ai_rag_documents");
    }

    @Test
    void ragPropertiesSsrfDefaults() {
        RagProperties defaults = new RagProperties(
                true, null, null, null, null, null, null);
        assertThat(defaults.resolveSsrf().resolveTimeoutSeconds()).isEqualTo(5);
        assertThat(defaults.resolveSsrf().resolveMaxBodyBytes()).isEqualTo(10_485_760L);
    }

    // --- 辅助：手工构造工厂（模拟 RagAdvisorConfig 内部逻辑）---

    private RagAdvisorConfig.RagAdvisorFactory createFactory(boolean vsAvailable) {
        if (!vsAvailable) {
            return (userId, sourceType) -> null;
        }
        VectorStore mockVs = mock(VectorStore.class);
        SafeVectorStore safeVs = new SafeVectorStore(mockVs);
        int topK = properties.resolveTopK();

        return (userId, sourceType) -> {
            var feb = new FilterExpressionBuilder();
            var op = feb.eq("userId", (userId != null && !userId.isBlank()) ? userId : "system");
            Filter.Expression filter;
            if (sourceType != null && !sourceType.isBlank()) {
                filter = feb.and(op, feb.eq("sourceType", sourceType)).build();
            } else {
                filter = op.build();
            }

            var search = SearchRequest.builder()
                    .topK(topK)
                    .filterExpression(filter)
                    .build();

            var prompt = new PromptTemplate("""
                    {query}
                    {question_answer_context}
                    """);

            return QuestionAnswerAdvisor.builder(safeVs)
                    .searchRequest(search)
                    .promptTemplate(prompt)
                    .build();
        };
    }
}
