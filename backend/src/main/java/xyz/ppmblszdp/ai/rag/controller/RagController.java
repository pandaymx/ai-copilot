package xyz.ppmblszdp.ai.rag.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.rag.dto.RagDocumentMeta;
import xyz.ppmblszdp.ai.rag.dto.RagListResponse;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.security.SsrfBlockedException;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;
import xyz.ppmblszdp.ai.rag.service.RagQueryService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 文档入库与检索 REST 接口。
 *
 * <ul>
 * <li>POST /api/rag/ingest — 多源文档入库（联合 DTO：rawText / targetUrl /
 * fileStoragePath），内置内容去重</li>
 * <li>GET /api/rag/search — 语义相似检索</li>
 * <li>GET /api/rag/documents — 已入库文档列表（分页/过滤聚合）</li>
 * <li>DELETE /api/rag/documents — 按 source + 用户删除文档</li>
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
    private final AuthProperties authProperties;

    public RagController(RagIngestionService ingestionService, RagQueryService queryService,
            AuthProperties authProperties) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.authProperties = authProperties;
    }

    /**
     * 多源文档入库。
     *
     * <p>
     * 三选一/多选一联合请求：rawText（纯文本）、targetUrl（网页）、fileStoragePath（文件路径）。
     * 入库链路内置内容级去重（contentHash），重复内容自动跳过，响应返回新增/跳过计数。
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody IngestRequest request, ServerWebExchange exchange) {

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(request.sourceType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "未知的 sourceType: " + request.sourceType(),
                            "valid", SourceType.values()));
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
                if (request.fileStoragePath() == null || request.fileStoragePath().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            sourceType + " 类型需提供 fileStoragePath"));
                }
                source = request.fileStoragePath();
            }
        }
        ;

        String fileName = (request.fileName() != null) ? request.fileName() : source;
        // 取真实身份（与 ChatController 检索口径一致），缺真实身份时回退到 DEFAULT_USER_ID
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);

        try {
            RagIngestionService.IngestResult result = ingestionService.ingest(
                    sourceType, source, fileName, userId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("sourceType", sourceType.name());
            body.put("source", source);
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
     * 已入库文档列表（聚合视图）。
     *
     * @param userId     过滤用户 ID（可选；为空时按调用者真实身份隔离）
     * @param sourceType 来源类型过滤（可选）
     * @param limit      最多返回文档数（默认 50，最大 1000）
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
                .collect(Collectors.groupingBy(
                        RagDocumentMeta::sourceType,
                        Collectors.counting()));
        RagListResponse response = new RagListResponse(
                items.stream()
                        .map(o -> (RagDocumentMeta) o)
                        .collect(Collectors.toList()),
                total, sourceTypeCounts);
        return ResponseEntity.ok(response);
    }

    /**
     * 按 source 删除文档（幂等：目标不存在时返回 0 不报错）。
     *
     * <p>
     * 删除键为 {@code source + 真实 userId}，保证多租户隔离，前端传入的 userId 仅用于身份解析上下文。
     */
    @DeleteMapping("/documents")
    public ResponseEntity<Map<String, Object>> deleteDocuments(
            @RequestParam String source,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {

        if (source == null || source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 不能为空"));
        }
        String resolvedUser = UserIdentityFilter.resolveIdentity(exchange, userId, authProperties);
        try {
            int removed = ingestionService.deleteBySourceAndUser(source, resolvedUser);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("source", source);
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
     * 覆盖更新（重新入库）：先删后写，返回删除旧数与新增/跳过数。
     */
    @PostMapping("/reingest")
    public ResponseEntity<Map<String, Object>> reingest(
            @RequestBody IngestRequest request, ServerWebExchange exchange) {

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(request.sourceType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "未知的 sourceType: " + request.sourceType(),
                            "valid", SourceType.values()));
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
                if (request.fileStoragePath() == null || request.fileStoragePath().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            sourceType + " 类型需提供 fileStoragePath"));
                }
                source = request.fileStoragePath();
            }
        }
        ;

        String fileName = (request.fileName() != null) ? request.fileName() : source;
        String resolvedUser = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);

        try {
            RagIngestionService.ReingestResult result = ingestionService.reingest(
                    sourceType, source, fileName, resolvedUser);
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
     * 向量库状态统计：enabled / available / 集合名 / 文档与向量数。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(queryService.collectionStats());
    }

    /**
     * 语义相似检索。
     *
     * @param query      查询文本（必填）
     * @param userId     用户 ID（可选，默认 {@code UserIdentityFilter.DEFAULT_USER_ID}）
     * @param sourceType 来源类型过滤（可选）
     * @param topK       Top-K（可选，默认使用配置值）
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
            body.put("results", results.stream().map(doc -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", doc.getId());
                item.put("text", doc.getText().length() > 500
                        ? doc.getText().substring(0, 500) + "..."
                        : doc.getText());
                item.put("metadata", doc.getMetadata());
                return item;
            }).toList());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("检索异常: query=...", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "检索异常", "detail", e.getMessage()));
        }
    }
}
