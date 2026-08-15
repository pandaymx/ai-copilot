package xyz.ppmblszdp.ai.recommendation;

/**
 * 模型推荐结果 DTO。
 *
 * @param providerId       推荐供应商 ID
 * @param modelId          推荐模型 ID
 * @param displayName      推荐模型显示名
 * @param reason           推荐理由（中文）
 * @param score            综合得分 0.0~1.0
 * @param estimatedCostRmb 预估成本（RMB，可选）
 */
public record ModelRecommendation(
        String providerId, String modelId, String displayName, String reason, double score, Double estimatedCostRmb) {}
