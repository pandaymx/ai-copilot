package xyz.ppmblszdp.ai.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.dto.PromptOptimizeRequest;
import xyz.ppmblszdp.ai.dto.PromptOptimizeResult;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.PromptOptimizer;

/**
 * Prompt 优化 REST 端点。
 *
 * <p>接收用户原始 Prompt，返回结构化优化结果。userId 取自服务端受信任身份链路，
 * 仅用于审计/限额，不持久化任何用户 Prompt（无状态分析，无数据库表）。
 */
@RestController
@RequestMapping("/api/prompt")
@CrossOrigin(origins = "*")
public class PromptOptimizerController {

    private static final Logger log = LoggerFactory.getLogger(PromptOptimizerController.class);

    private final PromptOptimizer optimizer;
    private final AuthProperties authProperties;

    public PromptOptimizerController(PromptOptimizer optimizer, AuthProperties authProperties) {
        this.optimizer = optimizer;
        this.authProperties = authProperties;
    }

    /**
     * 优化用户 Prompt。
     *
     * @param request 请求体（prompt + 可选 depth）
     * @param exchange 用于解析受信任 userId
     * @return 结构化优化结果
     */
    @PostMapping("/optimize")
    public Mono<ResponseEntity<PromptOptimizeResult>> optimize(
            @Valid @RequestBody PromptOptimizeRequest request, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);

        // userId 仅用于审计日志；不读取用户业务数据，不建表。
        log.info("Prompt 优化请求：userId={}, depth={}, len={}", userId, request.getDepth(), promptLen(request.getPrompt()));

        // 供应商/模型沿用用户当前上下文：以 null 交由 ProviderRegistry 解析默认路由。
        return optimizer.optimize(request, null, null).map(ResponseEntity::ok);
    }

    private static int promptLen(String s) {
        return s == null ? 0 : s.length();
    }
}
