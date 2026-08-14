package xyz.ppmblszdp.ai.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单次/批量评测结果 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationResultDto(
        String id,
        String benchmarkId,
        String benchmarkTitle,
        String provider,
        String model,
        String judgeProvider,
        String judgeModel,
        String prompt,
        String responseText,
        String expectedOutput,
        EvaluationMetrics metrics,
        String judgeFeedback,
        Long latencyMs,
        Integer totalTokens,
        Double humanScore,
        String humanAnnotation,
        Long evaluatedAt) {

    public EvaluationResultDto withHumanAnnotation(Double score, String annotation) {
        return new EvaluationResultDto(
                id,
                benchmarkId,
                benchmarkTitle,
                provider,
                model,
                judgeProvider,
                judgeModel,
                prompt,
                responseText,
                expectedOutput,
                metrics,
                judgeFeedback,
                latencyMs,
                totalTokens,
                score,
                annotation,
                evaluatedAt);
    }
}
