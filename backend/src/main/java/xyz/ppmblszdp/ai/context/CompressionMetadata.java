package xyz.ppmblszdp.ai.context;

/**
 * 上下文压缩元数据，作为压缩结果的附属信息返回给调用层，供前端折叠标记渲染。
 *
 * <p>元数据不持久化到数据库，随每次请求的 history[] 携带，天然支持无状态横向扩展。
 *
 * @param compressedTurnCount 被压缩的轮次数（一对 user/assistant 为 1 轮）
 * @param originalTokens      压缩前这些轮次的 Token 总数（估算值）
 * @param compressedTokens    压缩后摘要消息的 Token 数（估算值）
 * @param level               实际采用的压缩等级
 * @param summarySnippet      压缩摘要前 200 字（供前端折叠标记预览区显示）
 * @param fallback            是否因 LLM 调用超时/失败而退化为硬删除（true = 未实际压缩）
 */
public record CompressionMetadata(
        int compressedTurnCount,
        int originalTokens,
        int compressedTokens,
        ContextCompressor.Level level,
        String summarySnippet,
        boolean fallback) {

    /** 压缩率（0~1），越小表示压缩越彻底。 */
    public double compressionRatio() {
        if (originalTokens <= 0) return 1.0;
        return (double) compressedTokens / originalTokens;
    }

    /** 节省的 Token 数。 */
    public int savedTokens() {
        return Math.max(0, originalTokens - compressedTokens);
    }

    /** 生成硬删除降级的标记元数据（LLM 失败时使用）。 */
    public static CompressionMetadata fallback(int turnCount, int originalTokens) {
        return new CompressionMetadata(turnCount, originalTokens, 0, ContextCompressor.Level.KEYWORDS, "", true);
    }
}
