package xyz.ppmblszdp.ai.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A/B 盲测对比评测结果 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AbTestResultDto(
        String id,
        String prompt,
        String context,
        // Model A 信息与输出
        String providerA,
        String modelA,
        String responseA,
        Long latencyMsA,
        EvaluationMetrics metricsA,
        // Model B 信息与输出
        String providerB,
        String modelB,
        String responseB,
        Long latencyMsB,
        EvaluationMetrics metricsB,
        // 裁判结论
        String judgeProvider,
        String judgeModel,
        String winner, // "MODEL_A" | "MODEL_B" | "TIE"
        String comparisonReason,
        Long executedAt) {}
