package xyz.ppmblszdp.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import xyz.ppmblszdp.ai.rag.repository.RagSearchRepository;
import xyz.ppmblszdp.ai.rag.rerank.RagReranker;
import xyz.ppmblszdp.ai.rag.service.RagQueryService;

class RagStructuredQueryTest {

    private VectorStore mockVectorStore;
    private RagSearchRepository mockSearchRepository;
    private RagProperties properties;
    private RagQueryService queryService;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        mockSearchRepository = mock(RagSearchRepository.class);

        properties = new RagProperties(
                true,
                4,
                900,
                180,
                "CL100K_BASE",
                "ai_rag_documents",
                true,
                false,
                true,
                true,
                60,
                3,
                new RagProperties.SsrfConfig(5, 10_485_760L));

        queryService = new RagQueryService(
                mockVectorStore, properties, mockSearchRepository, new RagReranker.DefaultRagReranker());
    }

    @Test
    void search_shouldReturnStructuredKnowledgeHits_directlyWhenMatched() {
        Document structuredDoc =
                new Document("struct-1", "结构化知识命中片段", Map.of("structuredKnowledge", Map.of("title", "架构描述")));

        when(mockSearchRepository.searchStructuredKnowledge(eq("架构"), eq("user-100"), any(), eq(4)))
                .thenReturn(List.of(structuredDoc));

        List<Document> results = queryService.search("架构", "user-100", 4);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("struct-1");

        // 验证结构化命中时无需调用纯向量或全文召回
        verify(mockSearchRepository).searchStructuredKnowledge(eq("架构"), eq("user-100"), any(), eq(4));
        verify(mockSearchRepository, never()).searchFullText(anyString(), anyString(), any(), anyInt());
        verify(mockVectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void search_shouldFallbackToHybridSearch_whenStructuredKnowledgeMisses() {
        Document vectorDoc = new Document("vec-1", "向量召回片段", Map.of());
        when(mockSearchRepository.searchStructuredKnowledge(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc));

        List<Document> results = queryService.search("普通问题", "user-100", 4);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getId()).isEqualTo("vec-1");

        verify(mockSearchRepository).searchStructuredKnowledge(eq("普通问题"), eq("user-100"), any(), eq(4));
        verify(mockVectorStore, atLeastOnce()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void search_shouldSkipStructuredQuery_whenExtractionDisabled() {
        RagProperties disabledExtProps = new RagProperties(
                true,
                4,
                900,
                180,
                "CL100K_BASE",
                "ai_rag_documents",
                true,
                false,
                false,
                false,
                60,
                3,
                new RagProperties.SsrfConfig(5, 10_485_760L));

        RagQueryService queryServiceDisabledExt =
                new RagQueryService(mockVectorStore, disabledExtProps, mockSearchRepository, null);

        Document vectorDoc = new Document("vec-2", "向量结果", Map.of());
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc));

        List<Document> results = queryServiceDisabledExt.search("知识库", "user-100", 4);

        assertThat(results).isNotEmpty();
        verify(mockSearchRepository, never()).searchStructuredKnowledge(anyString(), anyString(), any(), anyInt());
    }
}
