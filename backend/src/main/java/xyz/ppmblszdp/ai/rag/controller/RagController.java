package xyz.ppmblszdp.ai.rag.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.rag.dto.RagDocumentMeta;
import xyz.ppmblszdp.ai.rag.dto.RagExtractRequest;
import xyz.ppmblszdp.ai.rag.dto.RagListResponse;
import xyz.ppmblszdp.ai.rag.dto.StructuredKnowledge;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.security.SsrfBlockedException;
import xyz.ppmblszdp.ai.rag.service.RagExtractionService;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;
import xyz.ppmblszdp.ai.rag.service.RagQueryService;

/**
 * RAG 文档入库、检索与结构化抽取 REST 接口。
 *
 * <ul>
 * <li>POST /api/rag/ingest — 多源文档入库（支持 SKIP / OVERWRITE / FORCE_ADD 冲突策略）</li>
 * <li>GET /api/rag/search — 混合检索（向量 + 全文双路召回 + RRF 融合）</li>
 * <li>POST /api/rag/extract — 结构化实体与知识提取（BeanOutputConverter 绑定）</li>
 * <li>GET /api/rag/documents — 已入库文档列表（分页/过滤聚合）</li>
 * <li>DELETE /api/rag/documents — 按 source / contentHash + 用户精确删除文档</li>
 * <li>POST /api/rag/reingest — 覆盖更新（先删后写）</li>
 * <li>GET /api/rag/status — 向量库状态统计</li>
 * </ul>
 *
 * <p>
 * 仅在 {@code app.ai.rag.enabled=true} 时暴露。
 */
@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagIngestionService ingestionService;
    private final RagQueryService queryService;
    private final RagExtractionService extractionService;
    private final AuthProperties authProperties;

    public RagController(
            RagIngestionService ingestionService,
            RagQueryService queryService,
            RagExtractionService extractionService,
            AuthProperties authProperties) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.extractionService = extractionService;
        this.authProperties = authProperties;
    }

    /**
     * 多源文档入库（支持 ConflictPolicy 冲突策略）。
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody IngestRequest request, ServerWebExchange exchange) {

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(request.sourceType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "未知的 sourceType: " + request.sourceType(), "valid", SourceType.values()));
        }

        String source = "";
        switch (sourceType) {
            case TEXT -> {
                if (request.rawText() == null || request.rawText().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "TEXT 类型需提供 rawText"));
                }
                source = request.rawText();
            }
            case URL -> {
                if (request.targetUrl() == null || request.targetUrl().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "URL 类型需提供 targetUrl"));
                }
                source = request.targetUrl();
            }
            case PDF, TIKA, MARKDOWN -> {
                if (request.fileStoragePath() == null
                        || request.fileStoragePath().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", sourceType + " 类型需提供 fileStoragePath"));
                }
                source = request.fileStoragePath();
            }
        }

        String fileName = (request.fileName() != null) ? request.fileName() : source;
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);

        try {
            RagIngestionService.IngestResult result =
                    ingestionService.ingest(sourceType, source, fileName, userId, request.conflictPolicy());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("sourceType", sourceType.name());
            body.put("source", source);
            body.put(
                    "conflictPolicy",
                    request.conflictPolicy() != null ? request.conflictPolicy().name() : "SKIP");
            body.put("ingested", result.ingested());
            body.put("skipped", result.skipped());
            return ResponseEntity.ok(body);
        } catch (SsrfBlockedException e) {
            log.warn("SSRF 拦截: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "SSRF 拦截", "detail", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("入库异常: sourceType={} source={}", sourceType, source, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", "入库异常");
            error.put("detail", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 结构化知识提取接口。
     */
    @PostMapping("/extract")
    public ResponseEntity<StructuredKnowledge> extract(
            @RequestBody RagExtractRequest request, ServerWebExchange exchange) {
        String resolvedUser = UserIdentityFilter.resolveIdentity(exchange, request.userId(), authProperties);
        RagExtractRequest effectiveRequest = new RagExtractRequest(
                request.query(), request.rawText(), resolvedUser, request.sourceType(), request.topK());
        StructuredKnowledge result = extractionService.extract(effectiveRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * 已入库文档列表（聚合视图）。
     */
    @GetMapping("/documents")
    public ResponseEntity<RagListResponse> listDocuments(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "50") int limit,
            ServerWebExchange exchange) {

        String resolvedUser = UserIdentityFilter.resolveIdentity(exchange, userId, authProperties);
        List<?> items = queryService.listDocuments(resolvedUser, sourceType, limit);
        long total = items.size();
        Map<String, Long> sourceTypeCounts = items.stream()
                .map(o -> ((RagDocumentMeta) o))
                .collect(Collectors.groupingBy(RagDocumentMeta::sourceType, Collectors.counting()));
        RagListResponse response = new RagListResponse(
                items.stream().map(o -> (RagDocumentMeta) o).collect(Collectors.toList()), total, sourceTypeCounts);
        return ResponseEntity.ok(response);
    }

    /**
     * 按 source 或 contentHash 精确删除文档片段。
     */
    @DeleteMapping("/documents")
    public ResponseEntity<Map<String, Object>> deleteDocuments(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String contentHash,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {

        if ((source == null || source.isBlank()) && (contentHash == null || contentHash.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 和 contentHash 不能同时为空"));
        }
        String resolvedUser = UserIdentityFilter.resolveIdentity(exchange, userId, authProperties);
        try {
            int removed;
            if (contentHash != null && !contentHash.isBlank()) {
                removed = ingestionService.deleteByContentHash(contentHash, resolvedUser);
            } else {
                removed = ingestionService.deleteBySourceAndUser(source, sourceType, resolvedUser);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("source", source);
            body.put("contentHash", contentHash);
            body.put("userId", resolvedUser);
            body.put("removed", removed);
            return ResponseEntity.ok(body);
        } catch (RuntimeException e) {
            log.error("删除文档异常: source={} userId={}", source, resolvedUser, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", "删除失败");
            error.put("detail", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 覆盖更新（重新入库）。
     */
    @PostMapping("/reingest")
    public ResponseEntity<Map<String, Object>> reingest(
            @RequestBody IngestRequest request, ServerWebExchange exchange) {

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(request.sourceType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "未知的 sourceType: " + request.sourceType(), "valid", SourceType.values()));
        }

        String source = "";
        switch (sourceType) {
            case TEXT -> {
                if (request.rawText() == null || request.rawText().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "TEXT 类型需提供 rawText"));
                }
                source = request.rawText();
            }
            case URL -> {
                if (request.targetUrl() == null || request.targetUrl().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "URL 类型需提供 targetUrl"));
                }
                source = request.targetUrl();
            }
            case PDF, TIKA, MARKDOWN -> {
                if (request.fileStoragePath() == null
                        || request.fileStoragePath().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", sourceType + " 类型需提供 fileStoragePath"));
                }
                source = request.fileStoragePath();
            }
        }

        String fileName = (request.fileName() != null) ? request.fileName() : source;
        String resolvedUser = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);

        try {
            RagIngestionService.ReingestResult result =
                    ingestionService.reingest(sourceType, source, fileName, resolvedUser);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("sourceType", sourceType.name());
            body.put("source", source);
            body.put("removed", result.removed());
            body.put("ingested", result.ingested());
            body.put("skipped", result.skipped());
            return ResponseEntity.ok(body);
        } catch (SsrfBlockedException e) {
            log.warn("SSRF 拦截: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "SSRF 拦截", "detail", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("重新入库异常: sourceType={} source={}", sourceType, source, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", "重新入库失败");
            error.put("detail", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 向量库状态统计。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(queryService.collectionStats());
    }

    /**
     * 语义相似检索。
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = UserIdentityFilter.DEFAULT_USER_ID) String userId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "0") int topK) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query 不能为空"));
        }

        try {
            var results = queryService.search(query, userId, sourceType, topK);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("userId", userId);
            body.put("sourceType", sourceType);
            body.put("count", results.size());
            body.put(
                    "results",
                    results.stream()
                            .map(doc -> {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("id", doc.getId());
                                item.put(
                                        "text",
                                        doc.getText().length() > 500
                                                ? doc.getText().substring(0, 500) + "..."
                                                : doc.getText());
                                item.put("metadata", doc.getMetadata());
                                return item;
                            })
                            .toList());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("检索异常: query=...", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "检索异常", "detail", e.getMessage()));
        }
    }
}
