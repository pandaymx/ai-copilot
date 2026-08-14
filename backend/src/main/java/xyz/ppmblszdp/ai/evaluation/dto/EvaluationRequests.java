package xyz.ppmblszdp.ai.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 评测请求体集合
 */
public class EvaluationRequests {

    /** 批量基准测试集运行请求 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunRequest(
            String provider,
            String model,
            String judgeProvider,
            String judgeModel,
            List<String> benchmarkIds,
            String category) {}

    /** A/B 盲测对比请求 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AbRequest(
            String prompt,
            String context,
            String expectedOutput,
            String providerA,
            String modelA,
            String providerB,
            String modelB,
            String judgeProvider,
            String judgeModel) {}

    /** 单条问答即时裁判评分请求 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SingleJudgeRequest(
            String prompt,
            String responseText,
            String context,
            String expectedOutput,
            String judgeProvider,
            String judgeModel) {}

    /** 人工标注打分更新请求 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HumanAnnotationRequest(Double humanScore, String humanAnnotation) {}
}
