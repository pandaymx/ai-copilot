package xyz.ppmblszdp.ai.rag.sync.dto;

/**
 * 远程数据源拉取到的原始文档结构。
 *
 * @param uri          文档唯一资源标识符 (如 "https://github.com/org/repo/blob/main/docs/arch.md" 或 "https://site.com/docs/intro")
 * @param title        文档标题
 * @param content      文档纯文本或 Markdown 正文
 * @param hash         远端哈希或 Git Blob SHA (用于第一道增量比对防线)
 * @param lastModifiedMs 远端最后修改时间戳 (若可用)
 * @param metadata     额外元数据属性 (如 author, tags, commitSha)
 */
public record RemoteKnowledgeDoc(
        String uri,
        String title,
        String content,
        String hash,
        Long lastModifiedMs,
        java.util.Map<String, Object> metadata) {

    public static RemoteKnowledgeDoc of(String uri, String title, String content, String hash) {
        return new RemoteKnowledgeDoc(uri, title, content, hash, System.currentTimeMillis(), java.util.Map.of());
    }
}
