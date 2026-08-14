package xyz.ppmblszdp.ai.rag.embedding.controller;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.rag.embedding.dto.DocumentSimilarityClusterDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingHealthDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingReindexTaskDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.StaleVectorDto;
import xyz.ppmblszdp.ai.rag.embedding.service.EmbeddingManagementService;

/**
 * 向量生命周期管理 REST 控制器。
 */
@RestController
@RequestMapping("/api/rag/embeddings")
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class EmbeddingManagementController {

    private final EmbeddingManagementService embeddingService;
    private final AuthProperties authProperties;

    public EmbeddingManagementController(EmbeddingManagementService embeddingService, AuthProperties authProperties) {
        this.embeddingService = embeddingService;
        this.authProperties = authProperties;
    }

    @GetMapping("/health")
    public ResponseEntity<EmbeddingHealthDto> getHealth(
            @RequestParam(required = false) String userId, ServerWebExchange exchange) {
        String resolvedUser = resolveUser(userId, exchange);
        return ResponseEntity.ok(embeddingService.detectHealth(resolvedUser));
    }

    @PostMapping("/reembed/start")
    public ResponseEntity<EmbeddingReindexTaskDto> startReembedding(
            @RequestParam(required = false, defaultValue = "false") boolean force,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {
        String resolvedUser = resolveUser(userId, exchange);
        return ResponseEntity.ok(embeddingService.startReembedding(resolvedUser, force));
    }

    @GetMapping("/reembed/status")
    public ResponseEntity<EmbeddingReindexTaskDto> getReembedStatus() {
        return ResponseEntity.ok(embeddingService.getReindexTaskStatus());
    }

    @PostMapping("/reembed/pause")
    public ResponseEntity<Map<String, Object>> pauseReembedding() {
        embeddingService.pauseReembedding();
        return ResponseEntity.ok(Map.of("success", true, "message", "批量重嵌入任务已暂停"));
    }

    @PostMapping("/reembed/resume")
    public ResponseEntity<Map<String, Object>> resumeReembedding() {
        embeddingService.resumeReembedding();
        return ResponseEntity.ok(Map.of("success", true, "message", "批量重嵌入任务已恢复"));
    }

    @GetMapping("/similarity-clusters")
    public ResponseEntity<List<DocumentSimilarityClusterDto>> getSimilarityClusters(
            @RequestParam(required = false, defaultValue = "0.88") double minSimilarity,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {
        String resolvedUser = resolveUser(userId, exchange);
        return ResponseEntity.ok(embeddingService.findSimilarityClusters(resolvedUser, minSimilarity, limit));
    }

    @GetMapping("/stale")
    public ResponseEntity<List<StaleVectorDto>> getStaleVectors(
            @RequestParam(required = false, defaultValue = "30") int retentionDays,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {
        String resolvedUser = resolveUser(userId, exchange);
        return ResponseEntity.ok(embeddingService.findStaleVectors(resolvedUser, retentionDays, limit));
    }

    public record BatchDocIdsRequest(List<String> docIds, String userId) {}

    @PostMapping("/stale/archive")
    public ResponseEntity<Map<String, Object>> archiveStaleVectors(
            @RequestBody BatchDocIdsRequest req, ServerWebExchange exchange) {
        String resolvedUser = resolveUser(req.userId(), exchange);
        boolean success = embeddingService.archiveStaleVectors(req.docIds(), resolvedUser);
        return ResponseEntity.ok(Map.of(
                "success",
                success,
                "archivedCount",
                req.docIds() != null ? req.docIds().size() : 0));
    }

    @DeleteMapping("/stale/purge")
    public ResponseEntity<Map<String, Object>> purgeStaleVectors(
            @RequestBody BatchDocIdsRequest req, ServerWebExchange exchange) {
        String resolvedUser = resolveUser(req.userId(), exchange);
        boolean success = embeddingService.purgeStaleVectors(req.docIds(), resolvedUser);
        return ResponseEntity.ok(Map.of(
                "success",
                success,
                "purgedCount",
                req.docIds() != null ? req.docIds().size() : 0));
    }

    private String resolveUser(String requestedUser, ServerWebExchange exchange) {
        if (exchange == null) {
            return (requestedUser != null && !requestedUser.isBlank())
                    ? requestedUser
                    : UserIdentityFilter.DEFAULT_USER_ID;
        }
        return UserIdentityFilter.resolveIdentity(exchange, requestedUser, authProperties);
    }
}
