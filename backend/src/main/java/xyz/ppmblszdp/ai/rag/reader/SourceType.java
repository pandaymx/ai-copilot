package xyz.ppmblszdp.ai.rag.reader;

/**
 * RAG 多源文档类型标识。
 */
public enum SourceType {
    /** PDF 文档（使用 PagePdfDocumentReader 按页解析） */
    PDF,
    /** Office / 文本 / 异构文档（使用 TikaDocumentReader 提取内容） */
    TIKA,
    /** Markdown 文档（使用 MarkdownDocumentReader 解析） */
    MARKDOWN,
    /** 网页 URL（基于 Jsoup 抓取 + SsrfGuard 安全校验，清洗 HTML 噪音） */
    URL,
    /** 纯文本（直接构造 Document，不经过读取器） */
    TEXT,
    /** 会话结构化摘要与知识沉淀 */
    CONVERSATION_SUMMARY
}
