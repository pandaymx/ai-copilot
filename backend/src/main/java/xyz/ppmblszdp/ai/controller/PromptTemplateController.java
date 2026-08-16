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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.PromptTemplateDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.PromptTemplateService;

/**
 * Prompt 模板管理 REST 控制器。
 */
@RestController
@RequestMapping("/api/prompt-templates")
@CrossOrigin(origins = "*")
public class PromptTemplateController {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateController.class);

    private final PromptTemplateService promptTemplateService;
    private final AuthProperties authProperties;

    public PromptTemplateController(PromptTemplateService promptTemplateService, AuthProperties authProperties) {
        this.promptTemplateService = promptTemplateService;
        this.authProperties = authProperties;
    }

    @GetMapping
    public Mono<ResponseEntity<List<PromptTemplateDto>>> listTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    return ResponseEntity.ok(promptTemplateService.list(userId, category, keyword));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<PromptTemplateDto>> getTemplate(@PathVariable String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    return ResponseEntity.ok(promptTemplateService.get(id, userId));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, String>>> createTemplate(
            @RequestBody PromptTemplateDto dto, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    String id = promptTemplateService.create(dto, userId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "message", "模板创建成功"));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, String>>> updateTemplate(
            @PathVariable String id, @RequestBody PromptTemplateDto dto, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    PromptTemplateDto merged = new PromptTemplateDto(
                            id,
                            userId,
                            dto.title(),
                            dto.description(),
                            dto.category(),
                            dto.body(),
                            dto.variables(),
                            dto.rating(),
                            dto.favorite(),
                            false,
                            dto.createdAt(),
                            System.currentTimeMillis());
                    promptTemplateService.update(merged, userId);
                    return ResponseEntity.ok(Map.of("message", "模板更新成功"));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteTemplate(@PathVariable String id, ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> {
                    String userId = resolveUserId(exchange);
                    promptTemplateService.delete(id, userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/render")
    public Mono<ResponseEntity<Map<String, String>>> renderTemplate(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> variables,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    String rendered =
                            promptTemplateService.render(id, userId, variables != null ? variables : Map.of());
                    return ResponseEntity.ok(Map.of("renderedText", rendered));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/favorite")
    public Mono<ResponseEntity<Void>> toggleFavorite(@PathVariable String id, ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> {
                    String userId = resolveUserId(exchange);
                    promptTemplateService.toggleFavorite(id, userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.ok().build());
    }

    @PostMapping("/{id}/rate")
    public Mono<ResponseEntity<Void>> rateTemplate(
            @PathVariable String id, @RequestBody Map<String, Integer> req, ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> {
                    String userId = resolveUserId(exchange);
                    int rating = req.getOrDefault("rating", 5);
                    promptTemplateService.rate(id, userId, rating);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.ok().build());
    }

    @PostMapping("/{id}/smart-fill")
    public Mono<ResponseEntity<Map<String, String>>> smartFillTemplate(
            @PathVariable String id, @RequestBody Map<String, String> req, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    String context = req.getOrDefault("context", "");
                    Map<String, String> filledVars = promptTemplateService.smartFill(id, userId, context);
                    return ResponseEntity.ok(filledVars);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveUserId(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }
}
