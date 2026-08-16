package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.WebhookDto;
import xyz.ppmblszdp.ai.event.DomainEvent;
import xyz.ppmblszdp.ai.repository.WebhookRepository;

/**
 * Webhook 核心业务服务（WebhookService）：处理事件监听、HMAC 签名、异步推送与重试记录。
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final WebhookRepository webhookRepository;

    public WebhookService(WebhookRepository webhookRepository) {
        this.webhookRepository = webhookRepository;
    }

    public WebhookDto.WebhookSubscriptionDto createSubscription(String userId, WebhookDto.WebhookCreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Webhook 名称不能为空");
        }
        if (req.url() == null || req.url().isBlank()) {
            throw new IllegalArgumentException("Webhook URL 不能为空");
        }
        String eventType = (req.eventType() != null && !req.eventType().isBlank())
                ? req.eventType().trim()
                : "*";

        String secret =
                (req.secret() != null && !req.secret().isBlank()) ? req.secret().trim() : generateSecret();

        String id = "wh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        var sub = new WebhookDto.WebhookSubscriptionDto(
                id, userId, req.name().trim(), req.url().trim(), eventType, secret, true, null, null, now);

        webhookRepository.saveSubscription(sub);
        log.info("已创建 Webhook 订阅: id={}, user={}, url={}", id, userId, req.url());
        return sub;
    }

    public List<WebhookDto.WebhookSubscriptionDto> listSubscriptions(String userId) {
        return webhookRepository.findSubscriptionsByUserId(userId);
    }

    public void updateSubscription(String id, String userId, WebhookDto.WebhookUpdateRequest req) {
        var existing = webhookRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook 订阅不存在"));

        String name = (req.name() != null && !req.name().isBlank()) ? req.name().trim() : existing.name();
        String url = (req.url() != null && !req.url().isBlank()) ? req.url().trim() : existing.url();
        String eventType = (req.eventType() != null && !req.eventType().isBlank())
                ? req.eventType().trim()
                : existing.eventType();
        boolean enabled = req.enabled() != null ? req.enabled() : existing.enabled();

        webhookRepository.updateSubscription(id, userId, name, url, eventType, enabled);
    }

    public void deleteSubscription(String id, String userId) {
        int deleted = webhookRepository.deleteSubscription(id, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("未找到可删除的 Webhook 订阅");
        }
    }

    public List<WebhookDto.WebhookDeliveryDto> listDeliveries(String subscriptionId, String userId) {
        return webhookRepository.findDeliveriesBySubscriptionId(subscriptionId, userId, 50);
    }

    @Async
    @EventListener
    public void onDomainEvent(DomainEvent event) {
        if (event == null || event.userId() == null) return;
        List<WebhookDto.WebhookSubscriptionDto> subs =
                webhookRepository.findActiveByUserIdAndEventType(event.userId(), event.eventType());

        for (WebhookDto.WebhookSubscriptionDto sub : subs) {
            CompletableFuture.runAsync(() -> deliverEvent(sub, event));
        }
    }

    public WebhookDto.WebhookTestResult testSubscription(String id, String userId) {
        var sub = webhookRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook 订阅不存在"));

        var testEvent = DomainEvent.of("webhook.test_ping", userId, Map.of("message", "AI-Copilot Webhook 连通性测试"));
        return deliverEvent(sub, testEvent);
    }

    private WebhookDto.WebhookTestResult deliverEvent(WebhookDto.WebhookSubscriptionDto sub, DomainEvent event) {
        long start = System.currentTimeMillis();
        String deliveryId =
                "del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(Map.of(
                    "eventId", event.eventId(),
                    "eventType", event.eventType(),
                    "userId", event.userId(),
                    "timestamp", event.timestamp(),
                    "data", event.data()));
        } catch (Exception e) {
            payloadJson = "{}";
        }

        String signature = sign(sub.secret(), String.valueOf(event.timestamp()) + "." + payloadJson);

        int responseCode = 0;
        String responseBody = "";
        boolean success = false;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sub.url()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "AI-Copilot-Webhook/2.0")
                    .header("X-Webhook-Event", event.eventType())
                    .header("X-Webhook-Timestamp", String.valueOf(event.timestamp()))
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            responseCode = response.statusCode();
            responseBody = response.body();
            success = (responseCode >= 200 && responseCode < 300);
        } catch (Exception e) {
            responseCode = 500;
            responseBody = "推送异常: " + e.getMessage();
            success = false;
        }

        long duration = System.currentTimeMillis() - start;
        String status = success ? "SUCCESS" : "FAILED";

        var delivery = new WebhookDto.WebhookDeliveryDto(
                deliveryId,
                sub.id(),
                sub.userId(),
                event.eventType(),
                payloadJson,
                responseCode,
                responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody,
                success,
                duration,
                System.currentTimeMillis());

        webhookRepository.recordDelivery(delivery);
        webhookRepository.updateSubscriptionStatus(sub.id(), status, System.currentTimeMillis());

        return new WebhookDto.WebhookTestResult(success, responseCode, responseBody, duration);
    }

    private String sign(String secret, String content) {
        if (secret == null || secret.isBlank()) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacData = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacData);
        } catch (Exception e) {
            return "";
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
