package xyz.ppmblszdp.ai.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 评测大盘聚合统计数据 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationSummaryDto(
        long totalEvaluations,
        long totalAbTests,
        double averageScore,
        EvaluationMetrics dimensionAverages,
        List<ModelLeaderboardEntry> leaderboard,
        Map<String, Integer> categoryDistribution,
        List<EvaluationResultDto> recentResults,
        List<AbTestResultDto> recentAbTests) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelLeaderboardEntry(
            String modelKey,
            String provider,
            String model,
            long count,
            double averageScore,
            double averageLatencyMs,
            EvaluationMetrics metrics) {}
}
