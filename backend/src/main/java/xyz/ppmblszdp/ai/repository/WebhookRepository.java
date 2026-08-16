package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.WebhookDto;

/**
 * Webhook 订阅与投递日志持久化仓储（WebhookRepository）。
 */
@Repository
public class WebhookRepository {

    private static final Logger log = LoggerFactory.getLogger(WebhookRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<WebhookDto.WebhookSubscriptionDto> subRowMapper =
            (rs, rowNum) -> new WebhookDto.WebhookSubscriptionDto(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("name"),
                    rs.getString("url"),
                    rs.getString("event_type"),
                    rs.getString("secret"),
                    rs.getBoolean("enabled"),
                    rs.getString("last_status"),
                    rs.getObject("last_delivered_at") != null ? rs.getLong("last_delivered_at") : null,
                    rs.getLong("created_at"));

    private final RowMapper<WebhookDto.WebhookDeliveryDto> delRowMapper =
            (rs, rowNum) -> new WebhookDto.WebhookDeliveryDto(
                    rs.getString("id"),
                    rs.getString("subscription_id"),
                    rs.getString("user_id"),
                    rs.getString("event_type"),
                    rs.getString("payload_json"),
                    rs.getInt("response_status"),
                    rs.getString("response_body"),
                    rs.getBoolean("success"),
                    rs.getLong("duration_ms"),
                    rs.getLong("created_at"));

    public WebhookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS webhook_subscriptions (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(128) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    url VARCHAR(512) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    secret VARCHAR(256),
                    enabled BOOLEAN NOT NULL DEFAULT true,
                    last_status VARCHAR(32),
                    last_delivered_at BIGINT,
                    created_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_webhook_user_event ON webhook_subscriptions(user_id, event_type);

                CREATE TABLE IF NOT EXISTS webhook_deliveries (
                    id VARCHAR(64) PRIMARY KEY,
                    subscription_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(128) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload_json TEXT,
                    response_status INT,
                    response_body TEXT,
                    success BOOLEAN NOT NULL DEFAULT false,
                    duration_ms BIGINT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_sub ON webhook_deliveries(subscription_id, created_at DESC);
            """);
        } catch (Exception e) {
            log.warn("初始化 Webhook 表结构失败: {}", e.getMessage());
        }
    }

    public void saveSubscription(WebhookDto.WebhookSubscriptionDto sub) {
        jdbcTemplate.update(
                """
            INSERT INTO webhook_subscriptions (id, user_id, name, url, event_type, secret, enabled, last_status, last_delivered_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
                sub.id(),
                sub.userId(),
                sub.name(),
                sub.url(),
                sub.eventType(),
                sub.secret(),
                sub.enabled(),
                sub.lastStatus(),
                sub.lastDeliveredAt(),
                sub.createdAt());
    }

    public List<WebhookDto.WebhookSubscriptionDto> findSubscriptionsByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, name, url, event_type, secret, enabled, last_status, last_delivered_at, created_at FROM webhook_subscriptions WHERE user_id = ? ORDER BY created_at DESC",
                subRowMapper,
                userId);
    }

    public List<WebhookDto.WebhookSubscriptionDto> findActiveByUserIdAndEventType(String userId, String eventType) {
        return jdbcTemplate.query(
                "SELECT id, user_id, name, url, event_type, secret, enabled, last_status, last_delivered_at, created_at FROM webhook_subscriptions WHERE user_id = ? AND enabled = true AND (event_type = ? OR event_type = '*')",
                subRowMapper,
                userId,
                eventType);
    }

    public Optional<WebhookDto.WebhookSubscriptionDto> findByIdAndUserId(String id, String userId) {
        List<WebhookDto.WebhookSubscriptionDto> list = jdbcTemplate.query(
                "SELECT id, user_id, name, url, event_type, secret, enabled, last_status, last_delivered_at, created_at FROM webhook_subscriptions WHERE id = ? AND user_id = ?",
                subRowMapper,
                id,
                userId);
        return list.stream().findFirst();
    }

    public void updateSubscriptionStatus(String id, String lastStatus, long deliveredAt) {
        jdbcTemplate.update(
                "UPDATE webhook_subscriptions SET last_status = ?, last_delivered_at = ? WHERE id = ?",
                lastStatus,
                deliveredAt,
                id);
    }

    public void updateSubscription(
            String id, String userId, String name, String url, String eventType, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE webhook_subscriptions SET name = ?, url = ?, event_type = ?, enabled = ? WHERE id = ? AND user_id = ?",
                name,
                url,
                eventType,
                enabled,
                id,
                userId);
    }

    public int deleteSubscription(String id, String userId) {
        jdbcTemplate.update("DELETE FROM webhook_deliveries WHERE subscription_id = ? AND user_id = ?", id, userId);
        return jdbcTemplate.update("DELETE FROM webhook_subscriptions WHERE id = ? AND user_id = ?", id, userId);
    }

    public void recordDelivery(WebhookDto.WebhookDeliveryDto del) {
        jdbcTemplate.update(
                """
            INSERT INTO webhook_deliveries (id, subscription_id, user_id, event_type, payload_json, response_status, response_body, success, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
                del.id(),
                del.subscriptionId(),
                del.userId(),
                del.eventType(),
                del.payloadJson(),
                del.responseStatus(),
                del.responseBody(),
                del.success(),
                del.durationMs(),
                del.createdAt());
    }

    public List<WebhookDto.WebhookDeliveryDto> findDeliveriesBySubscriptionId(
            String subscriptionId, String userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, subscription_id, user_id, event_type, payload_json, response_status, response_body, success, duration_ms, created_at FROM webhook_deliveries WHERE subscription_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT ?",
                delRowMapper,
                subscriptionId,
                userId,
                limit > 0 ? limit : 50);
    }
}
