package xyz.ppmblszdp.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.chunker.RagTextSplitter;
import xyz.ppmblszdp.ai.rag.chunker.TokenBasedRagTextSplitter;
import xyz.ppmblszdp.ai.rag.metadata.RagMetadataEnricher;
import xyz.ppmblszdp.ai.rag.reader.DocumentReaderFactory;
import xyz.ppmblszdp.ai.rag.reader.SourceType;

import java.util.List;

/**
 * RAG 文档入库编排服务：Reader → Splitter → Metadata → VectorStore 端到端管道。
 *
 * <p>
 * 将文档解析、切片（含 overlap）、元数据注入、批量写入串联为单一编排入口。
 * 异常受控（记录日志并上报），不静默吞关键错误。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final DocumentReaderFactory readerFactory;
    private final RagTextSplitter splitter;
    private final VectorStore ragVectorStore;

    public RagIngestionService(DocumentReaderFactory readerFactory,
            TokenBasedRagTextSplitter splitter,
            @Qualifier("ragVectorStore") VectorStore ragVectorStore,
            RagProperties properties) {
        this.readerFactory = readerFactory;
        this.splitter = splitter;
        this.ragVectorStore = ragVectorStore;
    }

    /**
     * 入库入口：读取 → 切片 → 注入元数据 → 批量写入 VectorStore。
     *
     * @param sourceType 文档源类型
     * @param source     来源字符串（文件路径 / URL / 原始文本）
     * @param fileName   文件名标识
     * @param userId     关联用户 ID（String）
     * @return 入库总 chunk 数（用于控制器响应）
     */
    public int ingest(SourceType sourceType, String source, String fileName, String userId) {
        if (source == null || source.isBlank()) {
            log.warn("入库源为空，跳过: sourceType={} fileName={}", sourceType, fileName);
            return 0;
        }

        // 1. 多源解析
        log.info("RAG 入库开始: sourceType={} source={} fileName={} userId={}",
                sourceType, source, fileName, userId);
        List<Document> docs = readerFactory.read(sourceType, source, fileName);

        if (docs.isEmpty()) {
            log.warn("解析后文档为空: sourceType={} source={}", sourceType, source);
            return 0;
        }
        log.info("解析完成: 文档数={}", docs.size());

        // 2. 切片（含 overlap）
        List<Document> chunks = splitter.apply(docs);
        if (chunks.isEmpty()) {
            log.warn("切片后为空: sourceType={} source={}", sourceType, source);
            return 0;
        }
        log.info("切片完成: chunk数={}", chunks.size());

        // 3. 注入元数据（userId / sourceType 等均以 String 写入，回应风险3）
        String url = (sourceType == SourceType.URL) ? source : null;
        String title = null;
        for (Document d : chunks) {
            Object t = d.getMetadata().get("title");
            if (t != null) {
                title = t.toString();
                break;
            }
        }
        RagMetadataEnricher.enrich(chunks,
                sourceType.name(), source, fileName, url, title, userId);

        // 4. 批量写入 VectorStore
        try {
            ragVectorStore.add(chunks);
            log.info("RAG 入库完成: sourceType={} source={} chunks={} userId={}",
                    sourceType, source, chunks.size(), userId);
        } catch (Exception e) {
            log.error("RAG 入库写入 VectorStore 异常: sourceType={} source={} chunks={} error={}",
                    sourceType, source, chunks.size(), e.getMessage(), e);
            throw new RuntimeException("RAG 向量库写入失败: " + source, e);
        }

        return chunks.size();
    }
}
