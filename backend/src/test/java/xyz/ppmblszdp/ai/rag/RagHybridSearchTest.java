package xyz.ppmblszdp.ai.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import xyz.ppmblszdp.ai.rag.repository.RagSearchRepository;
import xyz.ppmblszdp.ai.rag.rerank.RagReranker;
import xyz.ppmblszdp.ai.rag.service.RagQueryService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagHybridSearchTest {

    private VectorStore mockVectorStore;
    private RagSearchRepository mockSearchRepository;
    private RagProperties properties;
    private RagQueryService queryService;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        mockSearchRepository = mock(RagSearchRepository.class);

        properties = new RagProperties(
                true, 4, 900, 180, "CL100K_BASE", "ai_rag_documents",
                true, false, 60, 3,
                new RagProperties.SsrfConfig(5, 10_485_760L));

        queryService = new RagQueryService(
                mockVectorStore,
                properties,
                mockSearchRepository,
                new RagReranker.DefaultRagReranker()
        );
    }

    @Test
    void search_shouldPerformHybridRrfFusion_whenBothVectorAndFullTextReturnResults() {
        Document docVectorOnly = new Document("doc-vec", "向量召回独有文本内容", Map.of("contentHash", "h-vec"));
        Document docFullTextOnly = new Document("doc-fts", "全文召回独有文本内容", Map.of("contentHash", "h-fts"));
        Document docBothMatch = new Document("doc-both", "双路共同命中的高相关度文档", Map.of("contentHash", "h-both"));

        // 模拟向量召回：docBothMatch 排名 1，docVectorOnly 排名 2
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docBothMatch, docVectorOnly));

        // 模拟全文召回：docFullTextOnly 排名 1，docBothMatch 排名 2
        when(mockSearchRepository.searchFullText(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of(docFullTextOnly, docBothMatch));

        List<Document> results = queryService.search("测试查询", "user-123", 4);

        assertThat(results).isNotEmpty();
        // 双路共同命中的文档得分应最高，在顶部
        assertThat(results.get(0).getId()).isEqualTo("doc-both");
        assertThat(results.get(0).getMetadata()).containsKey("rrfScore");
        assertThat(results.get(0).getMetadata()).containsKey("vectorRank");
        assertThat(results.get(0).getMetadata()).containsKey("fullTextRank");

        // 候选池扩充数量断言
        verify(mockSearchRepository).searchFullText(eq("测试查询"), eq("user-123"), any(), eq(20));
    }

    @Test
    void search_shouldFallbackToVectorOnly_whenHybridSearchDisabled() {
        RagProperties disabledProperties = new RagProperties(
                true, 4, 900, 180, "CL100K_BASE", "ai_rag_documents",
                false, false, 60, 3,
                new RagProperties.SsrfConfig(5, 10_485_760L));

        RagQueryService fallbackQueryService = new RagQueryService(
                mockVectorStore,
                disabledProperties
        );

        Document docVec = new Document("doc-1", "纯向量结果", Map.of());
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docVec));

        List<Document> results = fallbackQueryService.search("查询", "user-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("doc-1");
        verifyNoInteractions(mockSearchRepository);
    }
}
