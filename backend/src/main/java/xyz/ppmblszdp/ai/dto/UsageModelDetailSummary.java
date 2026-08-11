package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;

/**
 * 按模型与供应商详细汇总的 Token 与费用明细。
 *
 * @param modelId          模型 ID
 * @param providerId       供应商 ID
 * @param promptTokens     Prompt Token 数量
 * @param completionTokens Completion Token 数量
 * @param totalTokens      总 Token 数量
 * @param totalCost        总费用 RMB
 * @param requestCount     请求数
 */
public record UsageModelDetailSummary(
		String modelId,
		String providerId,
		long promptTokens,
		long completionTokens,
		long totalTokens,
		BigDecimal totalCost,
		long requestCount
) {
}
