package xyz.ppmblszdp.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.ImportContextRequest;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.ImportContextResponse;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.InheritedContext;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ContextInheritanceService;

/**
 * 跨会话上下文继承 REST 控制器。
 *
 * <p>提供：
 * <ul>
 *   <li>POST /api/chat/sessions/{id}/export-context - 导出源会话结构化上下文</li>
 *   <li>POST /api/chat/sessions/{id}/import-context - 导入上下文到目标会话</li>
 *   <li>POST /api/sessions/{id}/export-context (别名)</li>
 *   <li>POST /api/sessions/{id}/import-context (别名)</li>
 * </ul>
 */
@RestController
@RequestMapping({"/api/chat/sessions", "/api/sessions"})
public class ContextInheritanceController {

    private static final Logger log = LoggerFactory.getLogger(ContextInheritanceController.class);

    private final ContextInheritanceService inheritanceService;
    private final AuthProperties authProperties;

    public ContextInheritanceController(ContextInheritanceService inheritanceService, AuthProperties authProperties) {
        this.inheritanceService = inheritanceService;
        this.authProperties = authProperties;
    }

    private String resolveIdentity(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }

    /**
     * 导出指定源会话的结构化上下文。
     */
    @PostMapping("/{id}/export-context")
    public Mono<ResponseEntity<InheritedContext>> exportContext(
            @PathVariable("id") String sourceSessionId,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "model", required = false) String model,
            ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        log.info("用户 '{}' 请求导出源会话 '{}' 的上下文 (provider: {}, model: {})", userId, sourceSessionId, provider, model);

        return inheritanceService
                .exportContext(sourceSessionId, userId, provider, model)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e -> {
                    log.warn("导出上下文失败 (源会话不存在或无权访问): {}", e.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("导出上下文内部错误: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * 导入结构化上下文到指定目标会话。
     */
    @PostMapping("/{id}/import-context")
    public ResponseEntity<ImportContextResponse> importContext(
            @PathVariable("id") String targetSessionId,
            @RequestBody ImportContextRequest request,
            ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        log.info("用户 '{}' 请求向目标会话 '{}' 导入上下文", userId, targetSessionId);

        if (request == null || request.context() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ImportContextResponse response = inheritanceService.importContext(targetSessionId, userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("导入上下文参数异常: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("导入上下文内部异常: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
