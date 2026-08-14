package xyz.ppmblszdp.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import xyz.ppmblszdp.ai.rag.chunker.TokenBasedRagTextSplitter;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;
import xyz.ppmblszdp.ai.rag.reader.DocumentReaderFactory;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;

class RagIngestionConflictTest {

    private VectorStore mockVectorStore;
    private DocumentReaderFactory mockReaderFactory;
    private RagIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        mockReaderFactory = mock(DocumentReaderFactory.class);
        RagProperties properties = new RagProperties(
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

        TokenBasedRagTextSplitter splitter = new TokenBasedRagTextSplitter(properties);
        ingestionService = new RagIngestionService(mockReaderFactory, splitter, mockVectorStore, properties);
    }

    @Test
    void ingest_shouldExecuteDeleteFirst_whenConflictPolicyIsOverwrite() {
        String rawText = "覆盖模式测试文本，验证先删后写";
        Document rawDoc = new Document("doc-1", rawText, Map.of());
        when(mockReaderFactory.read(SourceType.TEXT, rawText, "overwrite.txt")).thenReturn(List.of(rawDoc));

        var result = ingestionService.ingest(
                SourceType.TEXT, rawText, "overwrite.txt", "user-001", ConflictPolicy.OVERWRITE);

        assertThat(result.ingested()).isGreaterThanOrEqualTo(1);

        // 验证物理删除 vectorStore.delete 被精确调用
        verify(mockVectorStore, times(1)).delete(any(Filter.Expression.class));

        // 验证写入被调用
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore, atLeastOnce()).add(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void ingest_shouldForceAddAll_whenConflictPolicyIsForceAdd() {
        String rawText = "强制新增模式测试文本";
        Document rawDoc = new Document("doc-2", rawText, Map.of());
        when(mockReaderFactory.read(SourceType.TEXT, rawText, "force.txt")).thenReturn(List.of(rawDoc));

        var result =
                ingestionService.ingest(SourceType.TEXT, rawText, "force.txt", "user-002", ConflictPolicy.FORCE_ADD);

        assertThat(result.ingested()).isGreaterThanOrEqualTo(1);
        assertThat(result.skipped()).isZero();
        verify(mockVectorStore, never()).delete(any(Filter.Expression.class));
    }
}
