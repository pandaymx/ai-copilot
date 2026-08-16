package xyz.ppmblszdp.ai.dto;

/**
 * Webhook 订阅与事件投递数据传输对象（Webhook DTOs）。
 */
public class WebhookDto {

    public record WebhookSubscriptionDto(
            String id,
            String userId,
            String name,
            String url,
            String eventType,
            String secret,
            boolean enabled,
            String lastStatus,
            Long lastDeliveredAt,
            long createdAt) {}

    public record WebhookCreateRequest(String name, String url, String eventType, String secret) {}

    public record WebhookUpdateRequest(String name, String url, String eventType, Boolean enabled) {}

    public record WebhookDeliveryDto(
            String id,
            String subscriptionId,
            String userId,
            String eventType,
            String payloadJson,
            int responseStatus,
            String responseBody,
            boolean success,
            long durationMs,
            long createdAt) {}

    public record WebhookTestResult(boolean success, int statusCode, String message, long durationMs) {}
}
