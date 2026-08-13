package xyz.ppmblszdp.ai.rag.dto;

/**
 * 知识库文档列表项（多 chunk 归并后的聚合视图）。
 *
 * <p>由 {@code ai_rag_documents} 表中同一 {@code source} 下的多个向量记录按 source 聚合而成，
 * 供前端列表展示：来源、类型、文件名、归属用户、chunk 数、最新入库时间、内容指纹。
 *
 * @param docId       聚合文档标识：{@code source + "#" + 首切片 contentHash}
 * @param source      源标识（文件路径 / URL / 原始文本）
 * @param sourceType  源类型（PDF / TIKA / MARKDOWN / URL / TEXT）
 * @param fileName    原始文件名（可能为空）
 * @param title       文档标题（可能为空）
 * @param userId      归属用户 ID
 * @param chunkCount  该 source 下的向量（chunk）数
 * @param ingestedAt  最新入库时间（ISO-8601）
 * @param contentHash 首切片内容指纹（SHA-256），用于去重识别
 */
public record RagDocumentMeta(
        String docId,
        String source,
        String sourceType,
        String fileName,
        String title,
        String userId,
        int chunkCount,
        String ingestedAt,
        String contentHash) {}
