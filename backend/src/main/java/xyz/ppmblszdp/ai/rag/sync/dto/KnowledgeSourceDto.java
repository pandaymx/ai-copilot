package xyz.ppmblszdp.ai.rag.sync.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识源数据传输对象，支持敏感 Token 脱敏展示。
 */
public record KnowledgeSourceDto(
        String id,
        String name,
        String sourceType,
        Map<String, Object> config,
        String cronExpression,
        boolean enabled,
        String status,
        Long lastSyncAtMs,
        String lastSyncStatus,
        int documentCount,
        long lastSyncDurationMs,
        Map<String, String> contentHashes) {

    public static KnowledgeSourceDto create(
            String id,
            String name,
            String sourceType,
            Map<String, Object> config,
            String cronExpression,
            boolean enabled) {
        return new KnowledgeSourceDto(
                id,
                name,
                sourceType != null ? sourceType.toUpperCase() : "CUSTOM",
                config != null ? new HashMap<>(config) : new HashMap<>(),
                cronExpression != null && !cronExpression.isBlank() ? cronExpression : "0 0 * * * ?",
                enabled,
                "IDLE",
                null,
                "尚未同步",
                0,
                0L,
                new HashMap<>());
    }

    public KnowledgeSourceDto withStatus(
            String newStatus, String syncMsg, long durationMs, int docCount, Map<String, String> newHashes) {
        return new KnowledgeSourceDto(
                id,
                name,
                sourceType,
                config,
                cronExpression,
                enabled,
                newStatus,
                System.currentTimeMillis(),
                syncMsg,
                docCount,
                durationMs,
                newHashes != null ? new HashMap<>(newHashes) : this.contentHashes);
    }

    public KnowledgeSourceDto withConfig(
            String newName, Map<String, Object> newConfig, String newCron, Boolean newEnabled) {
        return new KnowledgeSourceDto(
                id,
                newName != null ? newName : this.name,
                sourceType,
                newConfig != null ? new HashMap<>(newConfig) : this.config,
                newCron != null ? newCron : this.cronExpression,
                newEnabled != null ? newEnabled : this.enabled,
                status,
                lastSyncAtMs,
                lastSyncStatus,
                documentCount,
                lastSyncDurationMs,
                contentHashes);
    }

    /**
     * 返回脱敏后的知识源视图，防止 GitHub PAT、Notion Token、Confluence Key 明文泄露给前端。
     */
    public KnowledgeSourceDto masked() {
        if (config == null || config.isEmpty()) {
            return this;
        }
        Map<String, Object> maskedConfig = new HashMap<>(config);
        for (String key : config.keySet()) {
            String lowerKey = key.toLowerCase();
            if (lowerKey.contains("token")
                    || lowerKey.contains("key")
                    || lowerKey.contains("secret")
                    || lowerKey.contains("password")) {
                Object val = config.get(key);
                if (val instanceof String s && s.length() > 6) {
                    maskedConfig.put(key, s.substring(0, 3) + "****" + s.substring(s.length() - 3));
                } else if (val != null) {
                    maskedConfig.put(key, "******");
                }
            }
        }
        return new KnowledgeSourceDto(
                id,
                name,
                sourceType,
                maskedConfig,
                cronExpression,
                enabled,
                status,
                lastSyncAtMs,
                lastSyncStatus,
                documentCount,
                lastSyncDurationMs,
                null); // 列表或详情接口无需返回全量内部 Hash Map
    }
}
