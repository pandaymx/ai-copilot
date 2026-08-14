package xyz.ppmblszdp.ai.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 反馈驱动质量闭环 — 大盘聚合分析 DTO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackAnalyticsDto(
        /** 过去 N 天每日点赞 / 点踩趋势 */
        List<DayBucket> satisfactionTrend,
        /** 按模型分组的满意度排名 */
        List<ModelSatisfactionEntry> modelSatisfaction,
        /** 按意图分组的健康度（含告警状态） */
        List<IntentHealthEntry> intentHealth,
        /** 最近 N 条点踩低分案例（含 Reflection 分析） */
        List<LowScoreCase> lowScoreCases,
        /** 全局统计摘要 */
        GlobalStats globalStats) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DayBucket(String date, long thumbsUp, long thumbsDown, double satisfactionRate) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelSatisfactionEntry(
            String modelId, long thumbsUp, long thumbsDown, long total, double satisfactionRate) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IntentHealthEntry(
            String intent, String label, long thumbsUp, long thumbsDown, boolean alerting, double thumbsDownRate) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LowScoreCase(
            String messageId,
            String conversationId,
            String modelId,
            String intent,
            String userPrompt,
            String comment,
            String reflectionAnalysis,
            String reflectionCategory,
            long createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GlobalStats(
            long totalFeedback,
            long totalThumbsUp,
            long totalThumbsDown,
            double overallSatisfactionRate,
            long alertingIntentCount) {}
}
