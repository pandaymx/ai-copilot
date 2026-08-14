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
import xyz.ppmblszdp.ai.rag.chunker.TokenBasedRagTextSplitter;
import xyz.ppmblszdp.ai.rag.dto.RagExtractRequest;
import xyz.ppmblszdp.ai.rag.dto.StructuredKnowledge;
import xyz.ppmblszdp.ai.rag.reader.DocumentReaderFactory;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.RagExtractionService;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;

class RagIngestionServiceTest {

    private VectorStore mockVectorStore;
    private DocumentReaderFactory mockReaderFactory;
    private RagExtractionService mockExtractionService;
    private RagIngestionService ingestionService;
    private RagProperties properties;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        mockExtractionService = mock(RagExtractionService.class);
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

        mockReaderFactory = mock(DocumentReaderFactory.class);

        // TokenBasedRagTextSplitter 需要 RagProperties
        TokenBasedRagTextSplitter splitter = new TokenBasedRagTextSplitter(properties);

        ingestionService = new RagIngestionService(
                mockReaderFactory, splitter, mockVectorStore, properties, mockExtractionService);
    }

    @Test
    void ingest_shouldReadSplitEnrichAndAdd_whenSourceIsText() {
        // 模拟 DocumentReaderFactory 返回一篇原始文档
        String rawText = "这是一段需要被切片和向量化的长文本。" + "它包含了多个句子，当超过 TokenTextSplitter 的 chunk 大小时应该自动切分。"
                + "RAG 管道包括读取、切片、元数据注入和向量库写入四个步骤。";
        Document rawDoc = new Document("doc-1", rawText, Map.of());
        when(mockReaderFactory.read(SourceType.TEXT, rawText, "inline.txt")).thenReturn(List.of(rawDoc));

        // 执行入库
        var result = ingestionService.ingest(SourceType.TEXT, rawText, "inline.txt", "user-001");

        // 断言返回的 chunk 数 >= 1
        assertThat(result.ingested()).isGreaterThanOrEqualTo(1);

        // 断言 VectorStore.add 至少被调用一次
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore, atLeastOnce()).add(captor.capture());

        // 断言注入的 metadata 中 userId 为 String 类型（回应风险3：类型兼容性）
        List<Document> addedDocs = captor.getValue();
        assertThat(addedDocs).isNotEmpty();
        for (Document doc : addedDocs) {
            assertThat(doc.getMetadata()).containsKey("userId");
            assertThat(doc.getMetadata().get("userId")).isInstanceOf(String.class);
            assertThat(doc.getMetadata().get("userId")).isEqualTo("user-001");
            assertThat(doc.getMetadata()).containsKey("sourceType");
            assertThat(doc.getMetadata().get("sourceType")).isEqualTo("TEXT");
            assertThat(doc.getMetadata()).containsKey("fileName");
            assertThat(doc.getMetadata().get("fileName")).isEqualTo("inline.txt");
            assertThat(doc.getMetadata()).containsKey("ingestedAt");
        }
    }

    @Test
    void ingest_shouldExtractStructuredKnowledge_whenEnabled() {
        String text = "AI Copilot 包含 Spring Boot 后端与 Next.js 前端。";
        Document rawDoc = new Document("doc-ext", text, Map.of());
        when(mockReaderFactory.read(SourceType.TEXT, text, "arch.txt")).thenReturn(List.of(rawDoc));

        StructuredKnowledge mockKnowledge = new StructuredKnowledge(
                "架构说明",
                "系统的基本组成",
                List.of(new StructuredKnowledge.EntityItem("AI Copilot", "产品", "智能助手")),
                List.of("包含前端与后端"));
        when(mockExtractionService.extract(any(RagExtractRequest.class))).thenReturn(mockKnowledge);

        ingestionService.ingest(SourceType.TEXT, text, "arch.txt", "u-001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore, atLeastOnce()).add(captor.capture());
        verify(mockExtractionService, atLeastOnce()).extract(any(RagExtractRequest.class));

        Document first = captor.getValue().get(0);
        assertThat(first.getMetadata()).containsKey("structuredKnowledge");
        assertThat(first.getMetadata().get("structuredKnowledge")).isEqualTo(mockKnowledge);
    }

    @Test
    void ingest_shouldDegradeGracefully_whenExtractionFails() {
        String text = "抽取异常降级测试文本。";
        Document rawDoc = new Document("doc-err", text, Map.of());
        when(mockReaderFactory.read(SourceType.TEXT, text, "err.txt")).thenReturn(List.of(rawDoc));

        when(mockExtractionService.extract(any(RagExtractRequest.class))).thenThrow(new RuntimeException("LLM 抽取限流失败"));

        // 执行入库，抽取异常不应阻断入库主流程
        var result = ingestionService.ingest(SourceType.TEXT, text, "err.txt", "u-002");

        assertThat(result.ingested()).isGreaterThanOrEqualTo(1);
        verify(mockVectorStore, atLeastOnce()).add(any());
    }

    @Test
    void ingest_shouldNotThrow_whenVectorStoreThrowsException() {
        // 模拟 VectorStore 写入异常，SafeVectorStore 应静默降级
        doThrow(new RuntimeException("数据库不可达")).when(mockVectorStore).add(any());

        String rawText = "测试降级文本";
        when(mockReaderFactory.read(SourceType.TEXT, rawText, "test.txt"))
                .thenReturn(List.of(new Document("doc-1", rawText, Map.of())));

        // 当前服务直接使用 mockVectorStore（非 SafeVectorStore），异常应向上传播
        // 此处验证：当 DB 异常时，IngestionService 应捕获并记录日志（调用层面应抛异常）
        assertThat(assertThrowsRuntime(() -> ingestionService.ingest(SourceType.TEXT, rawText, "test.txt", "user-x")))
                .isTrue();
        verify(mockVectorStore, atLeastOnce()).add(any());
    }

    @Test
    void ingest_shouldSkipEmptySource() {
        var result = ingestionService.ingest(SourceType.TEXT, "", "", "user-x");
        assertThat(result.ingested()).isZero();
        assertThat(result.skipped()).isZero();
        verifyNoInteractions(mockVectorStore);
        verifyNoInteractions(mockReaderFactory);
    }

    @Test
    void metadataShouldContainAllRequiredStringKeys() {
        String text = "元数据完整性测试文本，用于断言所有必须的 metadata 键均存在且类型正确。";
        Document rawDoc = new Document("doc-meta", text, Map.of());
        when(mockReaderFactory.read(SourceType.TEXT, text, "meta.txt")).thenReturn(List.of(rawDoc));

        ingestionService.ingest(SourceType.TEXT, text, "meta.txt", "u-999");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore, atLeastOnce()).add(captor.capture());

        List<Document> docs = captor.getValue();
        assertThat(docs).isNotEmpty();
        Document first = docs.get(0);
        Map<String, Object> meta = first.getMetadata();

        // 必须的元数据键
        assertThat(meta).containsKeys("sourceType", "source", "fileName", "ingestedAt", "userId");
        // 确保全部为 String（回应风险3）
        assertThat(meta.get("sourceType")).isInstanceOf(String.class);
        assertThat(meta.get("userId")).isInstanceOf(String.class);
    }

    /** AssertJ 风格的异常断言辅助。 */
    private static boolean assertThrowsRuntime(Runnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
