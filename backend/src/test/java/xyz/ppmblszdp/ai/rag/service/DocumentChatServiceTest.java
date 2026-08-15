package xyz.ppmblszdp.ai.rag.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.chunker.RagTextSplitter;
import xyz.ppmblszdp.ai.rag.dto.DocChatDocResponse;
import xyz.ppmblszdp.ai.rag.dto.DocChunkResponse;
import xyz.ppmblszdp.ai.rag.reader.DocumentReaderFactory;
import xyz.ppmblszdp.ai.rag.reader.SourceType;

class DocumentChatServiceTest {

    private DocumentReaderFactory mockReaderFactory;
    private RagTextSplitter mockSplitter;
    private VectorStore mockVectorStore;
    private RagProperties properties;
    private DocumentChatService documentChatService;

    @BeforeEach
    void setUp() {
        mockReaderFactory = mock(DocumentReaderFactory.class);
        mockSplitter = mock(RagTextSplitter.class);
        mockVectorStore = mock(VectorStore.class);
        properties = new RagProperties(true, null, null, 4, null, null, null, null, null, null, null, null, null);

        documentChatService =
                new DocumentChatService(mockReaderFactory, mockSplitter, mockVectorStore, properties, null);
    }

    @Test
    @DisplayName("会话文档入库：自动分块、提取页码与段落并写入向量库")
    void testIngestSessionDocument() {
        String convId = "conv-101";
        String userId = "user-alice";
        String fileName = "采购合同.pdf";
        String rawContent = "第一条：本合同由甲乙双方签署...\n第二条：付款期限为30天。";

        Document rawDoc = new Document("raw-1", rawContent, new HashMap<>(Map.of("pageNumber", "1")));
        when(mockReaderFactory.read(SourceType.PDF, "contract.pdf", fileName)).thenReturn(List.of(rawDoc));

        Document chunk1 = new Document("c1", "第一条：本合同由甲乙双方签署...", new HashMap<>(Map.of("pageNumber", "1")));
        Document chunk2 = new Document("c2", "第二条：付款期限为30天。", new HashMap<>(Map.of("pageNumber", "2")));
        when(mockSplitter.apply(List.of(rawDoc))).thenReturn(List.of(chunk1, chunk2));

        DocChatDocResponse response =
                documentChatService.ingestSessionDocument(convId, SourceType.PDF, "contract.pdf", fileName, userId);

        assertNotNull(response);
        assertNotNull(response.docId());
        assertTrue(response.docId().startsWith("doc-"));
        assertEquals(convId, response.conversationId());
        assertEquals(fileName, response.fileName());
        assertEquals(2, response.chunkCount());

        verify(mockVectorStore, times(1)).accept(any());
    }

    @Test
    @DisplayName("查询会话挂载文档列表：按 docId 聚合")
    void testGetSessionDocuments() {
        String convId = "conv-102";
        String userId = "user-bob";

        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("docId", "doc-aaa");
        meta1.put("conversationId", convId);
        meta1.put("fileName", "合同A.docx");
        meta1.put("sourceType", "TIKA");
        meta1.put("ingestedAt", "2026-08-15T12:00:00Z");

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("docId", "doc-aaa");
        meta2.put("conversationId", convId);
        meta2.put("fileName", "合同A.docx");
        meta2.put("sourceType", "TIKA");
        meta2.put("ingestedAt", "2026-08-15T12:00:00Z");

        Map<String, Object> meta3 = new HashMap<>();
        meta3.put("docId", "doc-bbb");
        meta3.put("conversationId", convId);
        meta3.put("fileName", "论文B.pdf");
        meta3.put("sourceType", "PDF");
        meta3.put("ingestedAt", "2026-08-15T12:05:00Z");

        Document doc1 = new Document("id-1", "chunk 1", meta1);
        Document doc2 = new Document("id-2", "chunk 2", meta2);
        Document doc3 = new Document("id-3", "chunk 3", meta3);

        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2, doc3));

        List<DocChatDocResponse> docs = documentChatService.getSessionDocuments(convId, userId);

        assertEquals(2, docs.size());
        assertEquals("doc-bbb", docs.get(0).docId()); // latest ingested first
        assertEquals(1, docs.get(0).chunkCount());
        assertEquals("doc-aaa", docs.get(1).docId());
        assertEquals(2, docs.get(1).chunkCount());
    }

    @Test
    @DisplayName("删除会话挂载文档")
    void testDeleteSessionDocument() {
        String docId = "doc-aaa";
        String convId = "conv-103";
        String userId = "user-carol";

        boolean success = documentChatService.deleteSessionDocument(docId, convId, userId);
        assertTrue(success);
        verify(mockVectorStore, times(1)).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("获取文档切片段落：供前端原文对照高亮")
    void testGetDocumentChunks() {
        String docId = "doc-123";
        String convId = "conv-104";
        String userId = "user-dave";

        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("docId", docId);
        meta1.put("fileName", "技术规范.md");
        meta1.put("pageNumber", "1");
        meta1.put("paragraphIndex", "2");

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("docId", docId);
        meta2.put("fileName", "技术规范.md");
        meta2.put("pageNumber", "1");
        meta2.put("paragraphIndex", "1");

        Document d1 = new Document("c2", "这是第二段内容", meta1);
        Document d2 = new Document("c1", "这是第一段内容", meta2);

        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(d1, d2));

        List<DocChunkResponse> chunks = documentChatService.getDocumentChunks(docId, convId, userId);

        assertEquals(2, chunks.size());
        // Should be sorted by paragraphIndex ascending (1, 2)
        assertEquals("1", chunks.get(0).paragraphIndex());
        assertEquals("这是第一段内容", chunks.get(0).content());
        assertEquals("2", chunks.get(1).paragraphIndex());
        assertEquals("这是第二段内容", chunks.get(1).content());
    }

    @Test
    @DisplayName("执行严格文档检索：多文档交叉引用与 Citation 构造")
    void testRetrieveStrictContext() {
        String convId = "conv-105";
        String userId = "user-eve";
        String query = "双方在违约责任上的约定是什么？";

        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("docId", "doc-contract-1");
        meta1.put("fileName", "采购合同.pdf");
        meta1.put("pageNumber", "3");
        meta1.put("paragraphIndex", "2");

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("docId", "doc-contract-2");
        meta2.put("fileName", "补充协议.pdf");
        meta2.put("pageNumber", "1");
        meta2.put("paragraphIndex", "4");

        Document d1 = new Document("c1", "甲方逾期付款需支付每日万分之五违约金。", meta1);
        Document d2 = new Document("c2", "补充协议约定违约金上限为总金额的10%。", meta2);

        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(d1, d2));

        DocumentChatService.DocumentChatContext context = documentChatService.retrieveStrictContext(
                query, convId, List.of("doc-contract-1", "doc-contract-2"), userId, 4);

        assertTrue(context.hasContext());
        assertEquals(2, context.citations().size());

        // Verify Citation 1
        assertEquals("1", context.citations().get(0).citationId());
        assertEquals("采购合同.pdf", context.citations().get(0).fileName());
        assertEquals("3", context.citations().get(0).pageNumber());
        assertEquals("2", context.citations().get(0).paragraphIndex());

        // Verify Citation 2
        assertEquals("2", context.citations().get(1).citationId());
        assertEquals("补充协议.pdf", context.citations().get(1).fileName());
        assertEquals("1", context.citations().get(1).pageNumber());

        // Verify formatted context contains both references
        assertTrue(context.formattedContext().contains("采购合同.pdf (第 3 页 / 段落 2)"));
        assertTrue(context.formattedContext().contains("补充协议.pdf (第 1 页 / 段落 4)"));
    }

    @Test
    @DisplayName("构建严格约束 System Prompt：必须包含拒答指令与引用规范")
    void testBuildStrictSystemPrompt() {
        String prompt = documentChatService.buildStrictSystemPrompt("请特别关注违约条款");
        assertNotNull(prompt);
        assertTrue(prompt.contains("自动拒答机制"));
        assertTrue(prompt.contains("严格限定事实"));
        assertTrue(prompt.contains("精准引用标注"));
        assertTrue(prompt.contains("请特别关注违约条款"));
    }
}
