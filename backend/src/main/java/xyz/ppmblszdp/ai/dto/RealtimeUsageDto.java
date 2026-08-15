package xyz.ppmblszdp.ai.dto;

/**
 * 实时 Token 配额与已用量 DTO（基于 Redis 实时数据，不查 DB）。
 *
 * @param month                 当前月份（yyyy-MM）
 * @param usedTokens            当月已消耗 Token 总数
 * @param quotaTokens           当月配额上限（0 表示无限制）
 * @param remainingTokens       当月剩余可用 Token 数（0 表示已耗尽）
 * @param usedPercent           已用百分比（0.0 ~ 100.0）
 * @param alertThresholdPercent 预警阈值百分比（如 80.0）
 */
public record RealtimeUsageDto(
        String month,
        long usedTokens,
        long quotaTokens,
        long remainingTokens,
        double usedPercent,
        double alertThresholdPercent) {}
