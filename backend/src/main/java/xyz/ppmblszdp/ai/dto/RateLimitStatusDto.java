package xyz.ppmblszdp.ai.dto;

/**
 * 客户端请求限流与月度配额状态摘要 DTO（用于前端聊天区实时预警与友好提示）。
 *
 * @param remainingRequests     当前短时滑动窗口内剩余可用请求数
 * @param capacity              短时滑动窗口请求总容量
 * @param windowSeconds         滑动窗口大小（秒）
 * @param resetAfterSeconds     当前滑动窗口重置剩余倒计时（秒）
 * @param resetAtMs             窗口重置时间戳（毫秒）
 * @param monthlyRemainingTokens 当月剩余可用 Token 数
 * @param monthlyQuotaTokens    当月总 Token 配额
 * @param monthlyUsedPercent    当月已用 Token 百分比 (0.0 ~ 100.0)
 * @param isRateLimited         是否已触发短时高频限流
 * @param isQuotaExhausted      是否已耗尽月度 Token 配额
 */
public record RateLimitStatusDto(
        int remainingRequests,
        int capacity,
        int windowSeconds,
        int resetAfterSeconds,
        long resetAtMs,
        long monthlyRemainingTokens,
        long monthlyQuotaTokens,
        double monthlyUsedPercent,
        boolean isRateLimited,
        boolean isQuotaExhausted) {

    public static RateLimitStatusDto of(
            int remaining,
            int capacity,
            int windowSeconds,
            int resetAfterSeconds,
            long resetAtMs,
            long monthlyRemaining,
            long monthlyQuota,
            double monthlyUsedPercent) {
        boolean rateLimited = (remaining <= 0);
        boolean quotaExhausted = (monthlyQuota > 0 && monthlyRemaining <= 0);
        return new RateLimitStatusDto(
                remaining,
                capacity,
                windowSeconds,
                resetAfterSeconds,
                resetAtMs,
                monthlyRemaining,
                monthlyQuota,
                monthlyUsedPercent,
                rateLimited,
                quotaExhausted);
    }
}
