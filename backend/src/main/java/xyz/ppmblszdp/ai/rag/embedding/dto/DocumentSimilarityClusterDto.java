package xyz.ppmblszdp.ai.rag.embedding.dto;

/**
 * 相似文档聚类 / 冲突簇 DTO。
 *
 * @param clusterId       冲突簇标识
 * @param similarityScore 两者余弦相似度（0~1.0）
 * @param docAId          文档 A 的 ID
 * @param docAName        文档 A 文件名
 * @param docAExcerpt     文档 A 内容摘要
 * @param docBId          文档 B 的 ID
 * @param docBName        文档 B 文件名
 * @param docBExcerpt     文档 B 内容摘要
 * @param conflictType    冲突类型 (INTRA_DOC_OVERLAP / CROSS_DOC_DUPLICATE / SEMANTIC_CONFLICT)
 * @param suggestedAction 建议处理动作 (KEEP_BOTH / MERGE / DELETE_OLDER / DELETE_DOC_B)
 */
public record DocumentSimilarityClusterDto(
        String clusterId,
        double similarityScore,
        String docAId,
        String docAName,
        String docAExcerpt,
        String docBId,
        String docBName,
        String docBExcerpt,
        String conflictType,
        String suggestedAction) {}
