package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.ApiKeyDto;
import xyz.ppmblszdp.ai.dto.ApiKeySaveRequest;
import xyz.ppmblszdp.ai.dto.ApiKeyTestResultDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ApiKeyManagementService;

/**
 * API Key 运行时管理 REST 端点。
 */
@RestController
@RequestMapping("/api/settings/api-keys")
@CrossOrigin(origins = "*")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyManagementService apiKeyService;
    private final AuthProperties authProperties;

    public ApiKeyController(ApiKeyManagementService apiKeyService, AuthProperties authProperties) {
        this.apiKeyService = apiKeyService;
        this.authProperties = authProperties;
    }

    @GetMapping
    public Mono<ResponseEntity<List<ApiKeyDto>>> listKeys(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    log.debug("获取 API Key 列表: userId={}", userId);
                    return ResponseEntity.ok(apiKeyService.list(userId));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, String>>> saveKey(
            @RequestBody ApiKeySaveRequest request, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    log.info("保存 API Key: userId={}, provider={}", userId, request.provider());
                    String id = apiKeyService.save(userId, request.provider(), request.apiKey());
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "saved"));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteKey(@PathVariable("id") String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    log.info("删除 API Key: userId={}, keyId={}", userId, id);
                    boolean deleted = apiKeyService.delete(id, userId);
                    return deleted
                            ? ResponseEntity.noContent().<Void>build()
                            : ResponseEntity.notFound().<Void>build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/test")
    public Mono<ResponseEntity<ApiKeyTestResultDto>> testKey(
            @PathVariable("id") String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    log.info("测试 API Key 连通性: userId={}, keyId={}", userId, id);
                    ApiKeyTestResultDto res = apiKeyService.test(id, userId);
                    return ResponseEntity.ok(res);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveUserId(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }
}
