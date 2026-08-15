package xyz.ppmblszdp.ai.rag.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.rag.dto.DocChatDocResponse;
import xyz.ppmblszdp.ai.rag.dto.DocChatIngestRequest;
import xyz.ppmblszdp.ai.rag.dto.DocChunkResponse;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.DocumentChatService;

class DocumentChatControllerTest {

    private DocumentChatService mockDocumentChatService;
    private AuthProperties authProperties;
    private DocumentChatController controller;

    @BeforeEach
    void setUp() {
        mockDocumentChatService = mock(DocumentChatService.class);
        authProperties = new AuthProperties("dev", "X-User-Id", java.util.Set.of("admin"));
        controller = new DocumentChatController(mockDocumentChatService, authProperties);
    }

    @Test
    @DisplayName("POST /api/rag/doc-chat/ingest: 成功入库文档")
    void testIngestSuccess() {
        DocChatIngestRequest request =
                new DocChatIngestRequest("conv-201", "TEXT", "技术方案.md", "# 系统设计架构说明...", null, null);

        DocChatDocResponse mockResp =
                new DocChatDocResponse("doc-999", "conv-201", "技术方案.md", "TEXT", 3, "2026-08-15T12:00:00Z");

        when(mockDocumentChatService.ingestSessionDocument(
                        eq("conv-201"), eq(SourceType.TEXT), eq("# 系统设计架构说明..."), eq("技术方案.md"), any()))
                .thenReturn(mockResp);

        MockServerHttpRequest req = MockServerHttpRequest.post("/api/rag/doc-chat/ingest")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        ResponseEntity<DocChatDocResponse> response = controller.ingest(request, exchange);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("doc-999", response.getBody().docId());
        assertEquals(3, response.getBody().chunkCount());
    }

    @Test
    @DisplayName("POST /api/rag/doc-chat/ingest: 缺少 conversationId 返回 400")
    void testIngestMissingConvId() {
        DocChatIngestRequest request = new DocChatIngestRequest("", "TEXT", "file.txt", "some content", null, null);

        MockServerHttpRequest req =
                MockServerHttpRequest.post("/api/rag/doc-chat/ingest").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        ResponseEntity<DocChatDocResponse> response = controller.ingest(request, exchange);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/rag/doc-chat/documents: 获取会话挂载文档列表")
    void testGetDocuments() {
        DocChatDocResponse doc1 =
                new DocChatDocResponse("doc-1", "conv-202", "合同.pdf", "PDF", 4, "2026-08-15T10:00:00Z");
        when(mockDocumentChatService.getSessionDocuments(eq("conv-202"), any())).thenReturn(List.of(doc1));

        MockServerHttpRequest req = MockServerHttpRequest.get("/api/rag/doc-chat/documents?conversationId=conv-202")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        ResponseEntity<List<DocChatDocResponse>> response = controller.getDocuments("conv-202", exchange);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("doc-1", response.getBody().get(0).docId());
    }

    @Test
    @DisplayName("DELETE /api/rag/doc-chat/documents/{docId}: 删除挂载文档")
    void testDeleteDocument() {
        when(mockDocumentChatService.deleteSessionDocument(eq("doc-1"), eq("conv-203"), any()))
                .thenReturn(true);

        MockServerHttpRequest req = MockServerHttpRequest.delete(
                        "/api/rag/doc-chat/documents/doc-1?conversationId=conv-203")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        ResponseEntity<Map<String, Object>> response = controller.deleteDocument("doc-1", "conv-203", exchange);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("doc-1", response.getBody().get("docId"));
    }

    @Test
    @DisplayName("GET /api/rag/doc-chat/chunks/{docId}: 查询切片段落原文")
    void testGetChunks() {
        DocChunkResponse chunk1 = new DocChunkResponse("c1", "doc-1", "论文.pdf", "1", "1", "引言内容");
        when(mockDocumentChatService.getDocumentChunks(eq("doc-1"), eq("conv-204"), any()))
                .thenReturn(List.of(chunk1));

        MockServerHttpRequest req = MockServerHttpRequest.get("/api/rag/doc-chat/chunks/doc-1?conversationId=conv-204")
                .header("X-User-Id", "user-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        ResponseEntity<List<DocChunkResponse>> response = controller.getChunks("doc-1", "conv-204", exchange);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("引言内容", response.getBody().get(0).content());
    }
}
