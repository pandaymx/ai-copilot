package xyz.ppmblszdp.ai.event;

import java.util.UUID;

/**
 * 系统领域事件对象，用于驱动 Webhook 异步推送与事件通知总线。
 */
public record DomainEvent(String eventId, String eventType, String userId, Object data, long timestamp) {

    public static DomainEvent of(String eventType, String userId, Object data) {
        return new DomainEvent(
                "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                eventType,
                userId,
                data,
                System.currentTimeMillis());
    }
}
