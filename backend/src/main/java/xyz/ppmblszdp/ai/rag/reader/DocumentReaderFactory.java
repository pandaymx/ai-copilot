package xyz.ppmblszdp.ai.rag.reader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.security.SsrfGuard;

/**
 * 多源文档读取器工厂：按 {@link SourceType} 选择对应 Reader 构造并读取文档。
 *
 * <ul>
 * <li>PDF → {@code PagePdfDocumentReader}</li>
 * <li>TIKA → {@code TikaDocumentReader}（Office / 异构文档）</li>
 * <li>MARKDOWN → {@code MarkdownDocumentReader}</li>
 * <li>URL → {@link JsoupHtmlCleaningReader}（自实现，前置 SSRF 校验 + HTML 噪音清洗）</li>
 * <li>TEXT → 直接构造单篇 {@link Document}</li>
 * </ul>
 */
@Component
public class DocumentReaderFactory {

    private static final Logger log = LoggerFactory.getLogger(DocumentReaderFactory.class);

    private final RagProperties properties;

    /** 常见的 HTML 噪音标签与属性 */
    private static final String[] NOISE_SELECTORS = {
        "script",
        "style",
        "nav",
        "header",
        "footer",
        "aside",
        "noscript",
        "iframe",
        "svg",
        "form",
        "button",
        "[role=complementary]",
        "[role=navigation]",
        "[role=banner]",
        ".sidebar",
        ".nav",
        ".menu",
        ".footer",
        ".header",
        ".ads",
        ".ad",
        ".advertisement",
        ".banner",
        ".social",
        ".comments",
        ".share",
        "[aria-hidden=true]"
    };

    public DocumentReaderFactory(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 按源类型与来源字符串读取文档列表。
     *
     * @param sourceType 文档源类型
     * @param source     来源字符串（文件路径 / URL / 原始文本）
     * @param fileName   文件名（用于元数据记录），可为空
     * @return 解析后的 {@link Document} 列表
     */
    public List<Document> read(SourceType sourceType, String source, String fileName) {
        return switch (sourceType) {
            case PDF -> readPdf(source, fileName);
            case TIKA -> readTika(source, fileName);
            case MARKDOWN -> readMarkdown(source, fileName);
            case URL -> readUrl(source);
            case TEXT -> readText(source, fileName);
        };
    }

    private List<Document> readPdf(String filePath, String fileName) {
        try {
            // Spring AI 2.0: PagePdfDocumentReader 按页解析
            PagePdfDocumentReader reader = new PagePdfDocumentReader(new FileSystemResource(filePath));
            List<Document> docs = reader.get();
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                // pageNumber 以 String 写入（回应风险3：统一类型避免 PgVector 过滤失败）
                doc.getMetadata().put("pageNumber", String.valueOf(i + 1));
            }
            log.info("PDF 解析完成: file={} pages={}", fileName, docs.size());
            return docs;
        } catch (Exception e) {
            log.error("PDF 解析失败: file={} error={}", filePath, e.getMessage(), e);
            throw new RuntimeException("PDF 解析失败: " + filePath, e);
        }
    }

    private List<Document> readTika(String filePath, String fileName) {
        try {
            TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
            List<Document> docs = reader.get();
            log.info("Tika 解析完成: file={} chunks={}", fileName, docs.size());
            return docs;
        } catch (Exception e) {
            log.error("Tika 解析失败: file={} error={}", filePath, e.getMessage(), e);
            throw new RuntimeException("Tika 解析失败: " + filePath, e);
        }
    }

    private List<Document> readMarkdown(String filePath, String fileName) {
        try {
            // 读取 Markdown 源文本
            String content = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            // Spring AI 2.0: MarkdownDocumentReader 支持配置是否包含代码块与引用块
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(true)
                    .withIncludeBlockquote(true)
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(content, config);
            List<Document> docs = reader.get();
            log.info("Markdown 解析完成: file={} chunks={}", fileName, docs.size());
            return docs;
        } catch (Exception e) {
            log.error("Markdown 解析失败: file={} error={}", filePath, e.getMessage(), e);
            throw new RuntimeException("Markdown 解析失败: " + filePath, e);
        }
    }

    private List<Document> readUrl(String url) {
        // 前置 SSRF 防护：仅校验通过才能发起请求
        SsrfGuard.validate(url);

        RagProperties.SsrfConfig ssrf = properties.resolveSsrf();
        int timeout = ssrf.resolveTimeoutSeconds();
        long maxBodyBytes = ssrf.resolveMaxBodyBytes();

        try {
            Connection conn = Jsoup.connect(url)
                    .timeout(timeout * 1000)
                    .maxBodySize(Math.toIntExact(maxBodyBytes))
                    .userAgent("Mozilla/5.0 (compatible; AiCopilotRagBot/1.0)")
                    .followRedirects(true)
                    .ignoreContentType(false);

            org.jsoup.nodes.Document htmlDoc = conn.get();

            // 清洗 HTML：移除 script/style/nav/header/footer/aside/广告等噪音节点
            for (String selector : NOISE_SELECTORS) {
                htmlDoc.select(selector).remove();
            }

            // 尝试提取正文容器，兜底取 body 文本
            Element mainContent = htmlDoc.selectFirst("article, main, [role=main]");
            if (mainContent == null) {
                mainContent = htmlDoc.body();
            }
            String text = (mainContent != null) ? mainContent.text() : htmlDoc.text();

            if (text.isBlank()) {
                log.warn("URL 抓取后未提取到有效正文: url={}", url);
                return Collections.emptyList();
            }

            // 构建 Document（TEXT 类型发送至此直接构造单篇文档）
            Document doc = new Document(UUID.randomUUID().toString(), text, new HashMap<>());
            String title = extractTitle(htmlDoc);
            doc.getMetadata().put("title", title != null ? title : "");
            doc.getMetadata().put("url", url);
            log.info("URL 解析完成: url={} title={} bodyLength={}", url, title, text.length());
            return List.of(doc);
        } catch (Exception e) {
            log.warn("URL 抓取失败: url={} error={}", url, e.getMessage());
            throw new RuntimeException("URL 抓取失败: " + url, e);
        }
    }

    private List<Document> readText(String rawText, String fileName) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }
        Document doc = new Document(UUID.randomUUID().toString(), rawText, new HashMap<>());
        log.info("TEXT 构造完成: source={} length={}", fileName, rawText.length());
        return List.of(doc);
    }

    private String extractTitle(org.jsoup.nodes.Document htmlDoc) {
        Element titleEl = htmlDoc.selectFirst("head title");
        if (titleEl != null && !titleEl.text().isBlank()) {
            return titleEl.text().trim();
        }
        Element h1 = htmlDoc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }
        return null;
    }
}
