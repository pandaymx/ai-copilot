package xyz.ppmblszdp.ai.rag.controller;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.rag.dto.DocChatDocResponse;
import xyz.ppmblszdp.ai.rag.dto.DocChatIngestRequest;
import xyz.ppmblszdp.ai.rag.dto.DocChunkResponse;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.DocumentChatService;

/**
 * 文档对话 REST 接口控制器（Chat with Document）。
 *
 * <ul>
 *   <li>POST /api/rag/doc-chat/ingest — 上传并挂载文档到当前会话</li>
 *   <li>GET /api/rag/doc-chat/documents — 获取会话挂载的所有文档</li>
 *   <li>DELETE /api/rag/doc-chat/documents/{docId} — 从会话中移除指定文档</li>
 *   <li>GET /api/rag/doc-chat/chunks/{docId} — 查询文档切片段落原文（用于前端原文对照高亮）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rag/doc-chat")
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class DocumentChatController {

    private static final Logger log = LoggerFactory.getLogger(DocumentChatController.class);

    private final DocumentChatService documentChatService;
    private final AuthProperties authProperties;

    public DocumentChatController(DocumentChatService documentChatService, AuthProperties authProperties) {
        this.documentChatService = documentChatService;
        this.authProperties = authProperties;
    }

    private String resolveUser(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }

    /**
     * 会话文档入库。
     */
    @PostMapping("/ingest")
    public ResponseEntity<DocChatDocResponse> ingest(
            @RequestBody DocChatIngestRequest request, ServerWebExchange exchange) {
        if (request == null
                || request.conversationId() == null
                || request.conversationId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String userId = resolveUser(exchange);
        log.info(
                "会话文档上传入库请求: convId={} file={} type={} user={}",
                request.conversationId(),
                request.fileName(),
                request.sourceType(),
                userId);
        SourceType sourceType;
        try {
            sourceType =
                    SourceType.valueOf((request.sourceType() != null ? request.sourceType() : "TEXT").toUpperCase());
        } catch (IllegalArgumentException e) {
            sourceType = SourceType.TEXT;
        }

        String source = "";
        switch (sourceType) {
            case TEXT, CONVERSATION_SUMMARY -> {
                source = request.rawText() != null ? request.rawText() : "";
            }
            case URL -> {
                source = request.targetUrl() != null ? request.targetUrl() : "";
            }
            case PDF, TIKA, MARKDOWN -> {
                source = (request.fileStoragePath() != null
                                && !request.fileStoragePath().isBlank())
                        ? request.fileStoragePath()
                        : (request.rawText() != null ? request.rawText() : "");
            }
        }

        if (source.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        DocChatDocResponse response = documentChatService.ingestSessionDocument(
                request.conversationId(), sourceType, source, request.fileName(), userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定会话挂载的文档列表。
     */
    @GetMapping("/documents")
    public ResponseEntity<List<DocChatDocResponse>> getDocuments(
            @RequestParam("conversationId") String conversationId, ServerWebExchange exchange) {
        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        String userId = resolveUser(exchange);
        List<DocChatDocResponse> docs = documentChatService.getSessionDocuments(conversationId, userId);
        return ResponseEntity.ok(docs);
    }

    /**
     * 删除会话挂载的某份文档。
     */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable("docId") String docId,
            @RequestParam("conversationId") String conversationId,
            ServerWebExchange exchange) {
        if (docId == null || docId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "参数缺失"));
        }
        String userId = resolveUser(exchange);
        boolean success = documentChatService.deleteSessionDocument(docId, conversationId, userId);
        return ResponseEntity.ok(Map.of("success", success, "docId", docId));
    }

    /**
     * 获取文档切片列表（供前端原文对照抽屉渲染与高亮）。
     */
    @GetMapping("/chunks/{docId}")
    public ResponseEntity<List<DocChunkResponse>> getChunks(
            @PathVariable("docId") String docId,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            ServerWebExchange exchange) {
        if (docId == null || docId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String userId = resolveUser(exchange);
        List<DocChunkResponse> chunks = documentChatService.getDocumentChunks(docId, conversationId, userId);
        return ResponseEntity.ok(chunks);
    }
}
