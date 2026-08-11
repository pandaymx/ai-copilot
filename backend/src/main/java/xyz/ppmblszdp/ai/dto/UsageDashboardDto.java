package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板聚合数据响应 DTO (GET /api/usage/dashboard)。
 *
 * @param monthKey            查询月份 (yyyy-MM)
 * @param totalTokens         当月总 Token 数量
 * @param totalCost           当月总费用 RMB
 * @param totalRequests       当月总请求数
 * @param activeUsers         活跃用户数
 * @param activeModels        使用模型数
 * @param byUser              按用户汇总排行榜
 * @param byModel             按模型汇总明细
 * @param dailyTrend          按日趋势统计
 * @param quotaConfig         当前生效配额阈值配置
 * @param quotaAlertTriggered 是否已触发告警阈值
 */
public record UsageDashboardDto(
		String monthKey,
		long totalTokens,
		BigDecimal totalCost,
		long totalRequests,
		long activeUsers,
		long activeModels,
		List<UsageUserSummary> byUser,
		List<UsageModelDetailSummary> byModel,
		List<UsageDailySummary> dailyTrend,
		QuotaConfigDto quotaConfig,
		boolean quotaAlertTriggered
) {
}
