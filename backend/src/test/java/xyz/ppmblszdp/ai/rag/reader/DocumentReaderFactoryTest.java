package xyz.ppmblszdp.ai.rag.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.metadata.RagMetadataEnricher;

/**
 * 验证 JsoupHtmlCleaningReader 不存在（已合并入 DocumentReaderFactory 内部），
 * 此处重点测试多源解析中的元数据注入与 SourceType 路由。
 */
class DocumentReaderFactoryTest {

    private final RagProperties properties = new RagProperties(
            true,
            4,
            900,
            180,
            "CL100K_BASE",
            "ai_rag_documents",
            true,
            false,
            true,
            60,
            3,
            new RagProperties.SsrfConfig(5, 10_485_760L));

    private final DocumentReaderFactory factory = new DocumentReaderFactory(properties);

    @Test
    void readText_shouldReturnSingleDocumentWithCorrectContent() {
        String text = "Hello, RAG pipeline testing.";
        List<Document> docs = factory.read(SourceType.TEXT, text, "test.txt");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).isEqualTo(text);
    }

    @Test
    void readText_shouldReturnEmptyForBlankSource() {
        List<Document> docs = factory.read(SourceType.TEXT, "", "empty.txt");
        assertThat(docs).isEmpty();
    }

    @Test
    void metadataEnricher_shouldPreservePageNumberAsString() {
        // 模拟 PDF 解析后带 pageNumber 的文档（pageNumber 已在 DocumentReaderFactory 中以 String 写入）
        Document pdfDoc = new Document("pdf-1", "PDF page content", Map.of("pageNumber", "3")); // String
        List<Document> docs = new ArrayList<>(List.of(pdfDoc));
        RagMetadataEnricher.enrich(docs, "PDF", "/test.pdf", "test.pdf", null, "Test PDF", "user-abc");

        assertThat(docs).hasSize(1);
        Document enriched = docs.get(0);
        // pageNumber 应保持为 String（回应风险3）
        assertThat(enriched.getMetadata().get("pageNumber"))
                .isInstanceOf(String.class)
                .isEqualTo("3");
        assertThat(enriched.getMetadata().get("sourceType")).isEqualTo("PDF");
        assertThat(enriched.getMetadata().get("userId")).isEqualTo("user-abc");
    }

    @Test
    void metadataEnricher_shouldSetTitleAndUrlWhenProvided() {
        Document doc = new Document("url-1", "URL content", Map.of());
        List<Document> docs = List.of(doc);
        RagMetadataEnricher.enrich(
                docs,
                "URL",
                "https://example.com/article",
                "",
                "https://example.com/article",
                "Example Article Title",
                "user-url");

        Document enriched = docs.get(0);
        assertThat(enriched.getMetadata().get("url")).isEqualTo("https://example.com/article");
        assertThat(enriched.getMetadata().get("title")).isEqualTo("Example Article Title");
        assertThat(enriched.getMetadata().get("sourceType")).isEqualTo("URL");
    }

    @Test
    void metadataEnricher_shouldDefaultUserIdToSystem() {
        Document doc = new Document("anon-1", "Anonymous content", Map.of());
        List<Document> docs = List.of(doc);
        RagMetadataEnricher.enrich(docs, "TEXT", "inline", "anon.txt", null, null, null);

        Document enriched = docs.get(0);
        // userId 为 null 时默认 "system"
        assertThat(enriched.getMetadata().get("userId")).isEqualTo("system");
    }
}
