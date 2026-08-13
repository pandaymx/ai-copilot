package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;

/**
 * 用户指定月份按模型分组的用量与费用明细。
 *
 * @param modelId     模型 id（缺失时记为 {@code unknown}）
 * @param tokens      该模型累计 token 数
 * @param cost        该模型累计费用（元）
 */
public record UsageModelSummary(String modelId, long tokens, BigDecimal cost) {}
