package xyz.ppmblszdp.ai.rag.sync.dto;

/**
 * 知识库单次同步操作的详细指标与结果总结。
 *
 * @param sourceId       知识源 ID
 * @param sourceName     知识源名称
 * @param totalRemoteDocs 远端扫描到的总文档数
 * @param addedCount     本次新增入库文档数
 * @param updatedCount   本次内容变更更新文档数
 * @param skippedCount   本次未变更跳过文档数 (零 Token 消耗)
 * @param deletedCount   远端已删除并在本地清理的幽灵文档数
 * @param durationMs     本次同步耗时 (毫秒)
 * @param success        是否成功
 * @param message        结果描述或错误信息
 */
public record KnowledgeSyncResultDto(
        String sourceId,
        String sourceName,
        int totalRemoteDocs,
        int addedCount,
        int updatedCount,
        int skippedCount,
        int deletedCount,
        long durationMs,
        boolean success,
        String message) {

    public static KnowledgeSyncResultDto success(
            String sourceId,
            String sourceName,
            int total,
            int added,
            int updated,
            int skipped,
            int deleted,
            long durationMs) {
        String msg = String.format("同步成功：新增 %d 篇，更新 %d 篇，跳过 %d 篇，清理过期 %d 篇", added, updated, skipped, deleted);
        return new KnowledgeSyncResultDto(
                sourceId, sourceName, total, added, updated, skipped, deleted, durationMs, true, msg);
    }

    public static KnowledgeSyncResultDto failed(String sourceId, String sourceName, String errorMsg, long durationMs) {
        return new KnowledgeSyncResultDto(sourceId, sourceName, 0, 0, 0, 0, 0, durationMs, false, errorMsg);
    }
}
