package xyz.ppmblszdp.ai.rag.metadata;

import org.springframework.ai.document.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档元数据统一注入器。
 *
 * <p><b>关键约束（回应风险3：Metadata 类型兼容性）</b>：
 * 所有用于 PgVector {@code FilterExpressionBuilder} 过滤的字段（userId / sourceType / pageNumber 等）
 * 强制以 <b>String</b> 类型写入，确保过滤表达式 {@code eq("userId", "123")} 不会因 Long/String 失配失效。
 */
public class RagMetadataEnricher {

    private RagMetadataEnricher() {
    }

    /**
     * 为文档列表统一注入 RAG 元数据：sourceType、source、fileName、url、title、timestamp、userId。
     * PDF 文档额外保留 {@code pageNumber}（String）。
     *
     * @param documents  待注入文档
     * @param sourceType 源类型（PDF / TIKA / MARKDOWN / URL / TEXT），以 String 写入
     * @param source     源标识（文件路径 / URL / "inline"）
     * @param fileName   原始文件名
     * @param url        源 URL（可为 null）
     * @param title      文档标题（可为 null）
     * @param userId     关联用户 ID，以 String 写入
     * @return 注入元数据后的文档列表（原地修改，但返回同一列表以便链式调用）
     */
    public static List<Document> enrich(List<Document> documents, String sourceType,
                                        String source, String fileName, String url,
                                        String title, String userId) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        String timestamp = Instant.now().toString();

        for (Document doc : documents) {
            Map<String, Object> meta = new HashMap<>(doc.getMetadata());
            // 所有字段强制 String 类型（回应风险3）
            meta.put("sourceType", sourceType);
            meta.put("source", (source != null) ? source : "unknown");
            meta.put("fileName", (fileName != null) ? fileName : "");
            if (url != null && !url.isBlank()) {
                meta.put("url", url);
            }
            if (title != null && !title.isBlank()) {
                meta.put("title", title);
            }
            meta.put("ingestedAt", timestamp);
            meta.put("userId", (userId != null) ? userId : "system");
            // PDF 的 pageNumber 已在 DocumentReaderFactory 中以 String 写入，此处不覆盖
            if (doc.getMetadata().containsKey("pageNumber")) {
                meta.put("pageNumber", String.valueOf(doc.getMetadata().get("pageNumber")));
            }
            // 就地替换：重新构造含新 metadata 的 Document
            doc.getMetadata().clear();
            doc.getMetadata().putAll(meta);
        }
        return documents;
    }
}
