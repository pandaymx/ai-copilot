package xyz.ppmblszdp.ai.rag.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto;
import xyz.ppmblszdp.ai.rag.graph.repository.KnowledgeGraphRepository;
import xyz.ppmblszdp.ai.rag.graph.service.GraphRagService;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class GraphRagServiceTest {

    private KnowledgeGraphRepository repository;
    private ProviderRegistry providerRegistry;
    private ChatModel mockChatModel;
    private GraphRagService graphRagService;

    @BeforeEach
    void setUp() {
        repository = new KnowledgeGraphRepository();
        providerRegistry = mock(ProviderRegistry.class);
        mockChatModel = mock(ChatModel.class);
        when(mockChatModel.getOptions())
                .thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .build());

        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("deepseek")
                .chatModel(mockChatModel)
                .build();
        ModelDescriptor model = ModelDescriptor.builder()
                .id("deepseek-chat")
                .modelName("deepseek-chat")
                .build();
        ResolvedModel resolved = new ResolvedModel(mockChatModel, provider, model);

        when(providerRegistry.resolve(any(), any())).thenReturn(resolved);

        graphRagService = new GraphRagService(repository, providerRegistry);
    }

    @Test
    @DisplayName("测试 LLM 抽取实体与关系三元组并持久化")
    void testExtractAndIndex() {
        String mockJson = """
				```json
				{
				  "entities": [
				    {"name": "Apache AGE", "type": "TECHNOLOGY", "description": "PostgreSQL 图数据库扩展插件"},
				    {"name": "PostgreSQL", "type": "TECHNOLOGY", "description": "关系型与向量数据库"}
				  ],
				  "relations": [
				    {"source": "Apache AGE", "relation": "EXTENDS_TO", "target": "PostgreSQL", "description": "AGE 为 PG 扩展图查询 Cypher 能力"}
				  ]
				}
				```
				""";

        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockJson)))));

        KnowledgeGraphDto res = graphRagService.extractAndIndex(
                "Apache AGE 是针对 PostgreSQL 的图数据库扩展插件，支持 openCypher 查询语言。", "doc-pg-age", "user-test");

        assertThat(res.nodes()).hasSize(2);
        assertThat(res.edges()).hasSize(1);
        assertThat(res.edges().get(0).relation()).isEqualTo("EXTENDS_TO");

        // 验证已存入 repository
        assertThat(repository.listEntities("user-test", "doc-pg-age")).hasSize(2);
    }

    @Test
    @DisplayName("测试从 Query 检索多跳拓扑生成虚拟 Document")
    void testRetrieveGraphDocuments() {
        List<Document> docs = graphRagService.retrieveGraphDocuments("我想了解 Spring AI 与 PgVectorStore 的协同关系", null, 2);
        assertThat(docs).isNotEmpty();
        Document doc = docs.get(0);
        assertThat(doc.getText()).contains("Spring AI");
        assertThat(doc.getText()).contains("PgVectorStore");
        assertThat(doc.getMetadata().get("sourceType")).isEqualTo("KNOWLEDGE_GRAPH");
    }
}
