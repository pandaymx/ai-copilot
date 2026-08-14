package xyz.ppmblszdp.ai.rag.embedding.dto;

import java.util.List;
import java.util.Map;

/**
 * 向量健康检测报告 DTO。
 *
 * @param totalVectors            向量总条数
 * @param healthyVectors          正常健康向量数
 * @param emptyOrZeroVectors      空或全零向量数
 * @param dimensionMismatchCount  维度不一致异常数
 * @param modelMismatchCount      模型标识失配数
 * @param staleVectorsCount       冷数据死向量数（30天+零命中）
 * @param activeModelName         当前系统活跃 Embedding 模型名称
 * @param activeModelDimensions   当前模型预期向量维度
 * @param healthScore             综合健康分（0~100）
 * @param status                  健康级别 (HEALTHY / WARNING / CRITICAL)
 * @param dimensionDistribution   各维度向量分布统计
 * @param issues                  异常明细条目列表
 */
public record EmbeddingHealthDto(
        long totalVectors,
        long healthyVectors,
        long emptyOrZeroVectors,
        long dimensionMismatchCount,
        long modelMismatchCount,
        long staleVectorsCount,
        String activeModelName,
        int activeModelDimensions,
        int healthScore,
        String status,
        Map<String, Long> dimensionDistribution,
        List<HealthIssue> issues) {

    public record HealthIssue(
            String documentId, String fileName, String issueType, String description, String severity) {}
}
