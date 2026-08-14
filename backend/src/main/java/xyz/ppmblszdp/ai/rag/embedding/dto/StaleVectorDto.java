package xyz.ppmblszdp.ai.rag.embedding.dto;

/**
 * 冷数据死向量 DTO。
 *
 * @param id          文档切片 ID
 * @param fileName    来源文件名
 * @param sourceType  文档来源类型
 * @param content     内容摘要片段
 * @param createdAt   入库时间戳
 * @param hitCount    累计检索命中次数
 * @param lastHitTime 最后一次被检索命中时间戳（可为 null）
 * @param isArchived  是否已软归档
 */
public record StaleVectorDto(
        String id,
        String fileName,
        String sourceType,
        String content,
        long createdAt,
        long hitCount,
        Long lastHitTime,
        boolean isArchived) {}
