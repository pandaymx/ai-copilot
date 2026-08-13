package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;

/**
 * 用户指定月份的用量与费用聚合（不按模型拆分）。
 *
 * @param totalTokens 累计 token 数
 * @param totalCost   累计费用（元）
 */
public record UsageMonthlySummary(long totalTokens, BigDecimal totalCost) {}
