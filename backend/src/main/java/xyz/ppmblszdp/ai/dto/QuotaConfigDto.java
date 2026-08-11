package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;

/**
 * 月度 Token 配额与告警阈值配置 DTO。
 *
 * @param monthlyTokenQuota     月度 Token 上限（0 表示无限制）
 * @param alertThresholdPercent  告警阈值百分比（如 80.0 表示达到配额 80% 时告警）
 * @param monthlyCostQuotaRmb   月度预算费用上限 RMB（0 表示无限制）
 */
public record QuotaConfigDto(
		long monthlyTokenQuota,
		double alertThresholdPercent,
		BigDecimal monthlyCostQuotaRmb
) {
}
