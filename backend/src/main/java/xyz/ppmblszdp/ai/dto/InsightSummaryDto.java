package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 对话洞察与分析聚合数据传输对象（InsightSummaryDto）。
 */
public record InsightSummaryDto(
        String userId,
        int totalConversations,
        int totalMessages,
        List<TopicCluster> topicClusters,
        QualityMetric quality,
        List<ModelDistribution> modelDistribution,
        List<SatisfactionTrend> satisfactionTrends,
        long generatedAt) {

    public record TopicCluster(String topic, int count, double percentage, List<String> sampleSnippets) {}

    public record QualityMetric(
            double overallScore,
            double relevance,
            double clarity,
            double accuracy,
            double completeness,
            double helpfulness) {}

    public record ModelDistribution(String provider, String model, int messageCount, double percentage) {}

    public record SatisfactionTrend(
            String period, double satisfactionScore, int positiveCount, int neutralCount, int negativeCount) {}
}
