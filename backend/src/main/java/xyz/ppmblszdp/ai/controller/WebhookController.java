package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.WebhookDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.WebhookService;

/**
 * Webhook 订阅与投递日志 REST 控制器（WebhookController）。
 */
@RestController
@RequestMapping("/api/settings/webhooks")
public class WebhookController {

    private final WebhookService webhookService;
    private final AuthProperties authProperties;

    public WebhookController(WebhookService webhookService, AuthProperties authProperties) {
        this.webhookService = webhookService;
        this.authProperties = authProperties;
    }

    @GetMapping
    public ResponseEntity<List<WebhookDto.WebhookSubscriptionDto>> listSubscriptions(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(webhookService.listSubscriptions(userId));
    }

    @PostMapping
    public ResponseEntity<?> createSubscription(
            @RequestBody WebhookDto.WebhookCreateRequest req, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            var sub = webhookService.createSubscription(userId, req);
            return ResponseEntity.status(HttpStatus.CREATED).body(sub);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSubscription(
            @PathVariable String id, @RequestBody WebhookDto.WebhookUpdateRequest req, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            webhookService.updateSubscription(id, userId, req);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubscription(@PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            webhookService.deleteSubscription(id, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testSubscription(@PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            var result = webhookService.testSubscription(id, userId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/deliveries")
    public ResponseEntity<List<WebhookDto.WebhookDeliveryDto>> listDeliveries(
            @PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(webhookService.listDeliveries(id, userId));
    }
}
