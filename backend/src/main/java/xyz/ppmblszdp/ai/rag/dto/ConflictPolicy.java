package xyz.ppmblszdp.ai.rag.dto;

/**
 * RAG 文档入库冲突解决策略。
 */
public enum ConflictPolicy {
    /**
     * 默认策略：内容重复（contentHash 匹配）时自动跳过对应的 chunk。
     */
    SKIP,

    /**
     * 覆盖模式：入库前在事务内按 (userId, sourceType, source) 清理已有文档，然后重新录入。
     */
    OVERWRITE,

    /**
     * 强制写入：跳过内容 Hash 重复预检，一律强行新增切片。
     */
    FORCE_ADD
}
