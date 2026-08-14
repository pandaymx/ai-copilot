package xyz.ppmblszdp.ai.rag.embedding.dto;

import java.util.List;

/**
 * 批量重新向量化（Re-embedding）任务状态 DTO。
 *
 * @param taskId          任务唯一标识
 * @param total           待处理总文档切片数
 * @param processed       已处理切片数
 * @param successCount    成功重嵌入数
 * @param failedCount     失败切片数
 * @param lastProcessedId 上一次成功处理的文档 ID（断点游标）
 * @param targetModel     目标 Embedding 模型
 * @param targetDimension 目标维度
 * @param isRunning       是否正在运行
 * @param isPaused        是否处于暂停状态
 * @param startedAt       任务启动时间戳
 * @param finishedAt      任务完成时间戳（未完成为 null）
 * @param errorSummary    错误概要列表（截断前 10 条）
 */
public record EmbeddingReindexTaskDto(
        String taskId,
        long total,
        long processed,
        long successCount,
        long failedCount,
        String lastProcessedId,
        String targetModel,
        int targetDimension,
        boolean isRunning,
        boolean isPaused,
        long startedAt,
        Long finishedAt,
        List<String> errorSummary) {

    public double getProgressPercentage() {
        if (total <= 0) return 100.0;
        return Math.min(100.0, Math.round((double) processed / total * 1000.0) / 10.0);
    }
}
