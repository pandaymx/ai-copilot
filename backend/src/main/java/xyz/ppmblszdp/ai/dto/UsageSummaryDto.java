package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用量查询响应 DTO（GET /api/usage）。
 *
 * <p>返回当前用户当月的累计 token、费用、配额上限、剩余量与使用百分比，以及按模型分组的明细。
 *
 * @param monthKey   查询月份，格式 {@code yyyy-MM}
 * @param totalTokens 当月累计 token 数
 * @param totalCost  当月累计费用（元）
 * @param quotaTokens 月度 token 配额上限（0 表示无上限/未开启）
 * @param remainingTokens 剩余可用 token 数（quotaTokens 为 0 时等于 totalTokens）
 * @param usedPercent 使用百分比（0~100，quotaTokens 为 0 时为 0）
 * @param byModel    按模型分组的明细
 */
public record UsageSummaryDto(
        String monthKey,
        long totalTokens,
        BigDecimal totalCost,
        long quotaTokens,
        long remainingTokens,
        double usedPercent,
        List<UsageModelSummary> byModel) {}
