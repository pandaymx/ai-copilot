package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;

/**
 * 按用户汇总的 Token 与费用明细。
 *
 * @param userId           用户 ID
 * @param promptTokens     Prompt Token 数量
 * @param completionTokens Completion Token 数量
 * @param totalTokens      总 Token 数量
 * @param totalCost        总费用 RMB
 * @param requestCount     请求数
 */
public record UsageUserSummary(
        String userId,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal totalCost,
        long requestCount) {}
