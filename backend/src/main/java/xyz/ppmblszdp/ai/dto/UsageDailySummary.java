package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;

/**
 * 按日分布的 Token 与费用趋势。
 *
 * @param day          日期字符串 (yyyy-MM-dd)
 * @param totalTokens  当日总 Token 数量
 * @param totalCost    当日总费用 RMB
 * @param requestCount 当日请求数
 */
public record UsageDailySummary(
		String day,
		long totalTokens,
		BigDecimal totalCost,
		long requestCount
) {
}
