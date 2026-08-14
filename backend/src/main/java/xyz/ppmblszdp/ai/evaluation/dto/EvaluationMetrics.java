package xyz.ppmblszdp.ai.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 5 维度核心评测指标与综合评分。
 * 分值范围 0.0 ~ 1.0 (或百分制 0 ~ 100)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationMetrics {

    /** 相关性 (Relevance): 回答与 Prompt 意图的契合度 */
    private Double relevance;

    /** 准确性 (Accuracy / Factuality): 事实与技术逻辑准确性，有无幻觉 */
    private Double accuracy;

    /** 完整性 (Completeness): 是否覆盖所有约束与问题要点 */
    private Double completeness;

    /** 流畅度 (Fluency / Clarity): 表达通顺度、逻辑连贯性与格式规范 */
    private Double fluency;

    /** 安全性 (Safety): 无提示词注入、恶意越狱或隐私泄漏风险 */
    private Double safety;

    /** 综合加权得分 0.0 ~ 1.0 */
    private Double overallScore;

    public EvaluationMetrics() {}

    public EvaluationMetrics(Double relevance, Double accuracy, Double completeness, Double fluency, Double safety) {
        this.relevance = relevance;
        this.accuracy = accuracy;
        this.completeness = completeness;
        this.fluency = fluency;
        this.safety = safety;
        this.overallScore = calculateOverall(relevance, accuracy, completeness, fluency, safety);
    }

    public static double calculateOverall(Double rel, Double acc, Double comp, Double flu, Double safe) {
        double r = (rel != null) ? rel : 0.8;
        double a = (acc != null) ? acc : 0.8;
        double c = (comp != null) ? comp : 0.8;
        double f = (flu != null) ? flu : 0.8;
        double s = (safe != null) ? safe : 1.0;
        // 权重: 准确性 30%, 相关性 25%, 完整性 20%, 流畅度 15%, 安全性 10%
        double score = a * 0.30 + r * 0.25 + c * 0.20 + f * 0.15 + s * 0.10;
        return Math.round(score * 100.0) / 100.0;
    }

    public Double getRelevance() {
        return relevance;
    }

    public void setRelevance(Double relevance) {
        this.relevance = relevance;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Double getCompleteness() {
        return completeness;
    }

    public void setCompleteness(Double completeness) {
        this.completeness = completeness;
    }

    public Double getFluency() {
        return fluency;
    }

    public void setFluency(Double fluency) {
        this.fluency = fluency;
    }

    public Double getSafety() {
        return safety;
    }

    public void setSafety(Double safety) {
        this.safety = safety;
    }

    public Double getOverallScore() {
        if (overallScore == null) {
            overallScore = calculateOverall(relevance, accuracy, completeness, fluency, safety);
        }
        return overallScore;
    }

    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }
}
