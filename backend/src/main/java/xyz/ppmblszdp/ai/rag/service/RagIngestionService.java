package xyz.ppmblszdp.ai.rag.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.chunker.RagTextSplitter;
import xyz.ppmblszdp.ai.rag.chunker.TokenBasedRagTextSplitter;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;
import xyz.ppmblszdp.ai.rag.dto.RagExtractRequest;
import xyz.ppmblszdp.ai.rag.dto.StructuredKnowledge;
import xyz.ppmblszdp.ai.rag.metadata.RagMetadataEnricher;
import xyz.ppmblszdp.ai.rag.reader.DocumentReaderFactory;
import xyz.ppmblszdp.ai.rag.reader.SourceType;

/**
 * RAG 文档入库编排服务：Reader → Splitter → Metadata → Extraction → VectorStore 端到端管道。
 *
 * <p>
 * 将文档解析、切片（含 overlap）、元数据注入、结构化知识抽取、批量写入串联为单一编排入口。
 * 支持三种冲突处理策略（SKIP 去重、OVERWRITE 先删后写、FORCE_ADD 强制新增）。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final DocumentReaderFactory readerFactory;
    private final RagTextSplitter splitter;
    private final VectorStore ragVectorStore;
    private final RagProperties properties;
    private final RagExtractionService extractionService;

    public RagIngestionService(
            DocumentReaderFactory readerFactory,
            TokenBasedRagTextSplitter splitter,
            @Qualifier("ragVectorStore") VectorStore ragVectorStore,
            RagProperties properties,
            ObjectProvider<RagExtractionService> extractionServiceProvider) {
        this(
                readerFactory,
                splitter,
                ragVectorStore,
                properties,
                extractionServiceProvider != null ? extractionServiceProvider.getIfAvailable() : null);
    }

    public RagIngestionService(
            DocumentReaderFactory readerFactory,
            TokenBasedRagTextSplitter splitter,
            VectorStore ragVectorStore,
            RagProperties properties,
            RagExtractionService extractionService) {
        this.readerFactory = readerFactory;
        this.splitter = splitter;
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
        this.extractionService = extractionService;
    }

    public RagIngestionService(
            DocumentReaderFactory readerFactory,
            TokenBasedRagTextSplitter splitter,
            VectorStore ragVectorStore,
            RagProperties properties) {
        this(readerFactory, splitter, ragVectorStore, properties, (RagExtractionService) null);
    }

    /**
     * 入库结果：区分实际新增与去重跳过的 chunk 数。
     */
    public record IngestResult(int ingested, int skipped) {}

    /**
     * 默认入库入口（使用 SKIP 策略去重）。
     */
    public IngestResult ingest(SourceType sourceType, String source, String fileName, String userId) {
        return ingest(sourceType, source, fileName, userId, ConflictPolicy.SKIP);
    }

    /**
     * 带冲突策略的入库入口：读取 → 切片 → 注入元数据 → 结构化抽取 → 策略处理（SKIP/OVERWRITE/FORCE_ADD） → 写入。
     *
     * @param sourceType     文档源类型
     * @param source         来源字符串
     * @param fileName       文件名标识
     * @param userId         关联用户 ID
     * @param conflictPolicy 冲突解决策略（null 默认 SKIP）
     * @return 入库结果（新增 / 跳过计数）
     */
    @Transactional
    public IngestResult ingest(
            SourceType sourceType, String source, String fileName, String userId, ConflictPolicy conflictPolicy) {
        if (source == null || source.isBlank()) {
            log.warn("入库源为空，跳过: sourceType={} fileName={}", sourceType, fileName);
            return new IngestResult(0, 0);
        }

        ConflictPolicy policy = conflictPolicy != null ? conflictPolicy : ConflictPolicy.SKIP;
        log.info(
                "RAG 入库开始: sourceType={} source={} fileName={} userId={} policy={}",
                sourceType,
                source,
                fileName,
                userId,
                policy);

        // 如果是 OVERWRITE 模式，在事务内先严格清理旧数据
        if (policy == ConflictPolicy.OVERWRITE) {
            deleteBySourceAndUser(source, sourceType.name(), userId);
        }

        // 1. 多源解析
        List<Document> docs = readerFactory.read(sourceType, source, fileName);
        if (docs.isEmpty()) {
            log.warn("解析后文档为空: sourceType={} source={}", sourceType, source);
            return new IngestResult(0, 0);
        }

        // 2. 切片
        List<Document> chunks = splitter.apply(docs);
        if (chunks.isEmpty()) {
            log.warn("切片后为空: sourceType={} source={}", sourceType, source);
            return new IngestResult(0, 0);
        }

        // 3. 注入元数据
        String url = (sourceType == SourceType.URL) ? source : null;
        String title = null;
        for (Document d : chunks) {
            Object t = d.getMetadata().get("title");
            if (t != null) {
                title = t.toString();
                break;
            }
        }
        RagMetadataEnricher.enrich(chunks, sourceType.name(), source, fileName, url, title, userId);

        // 3.1 结构化知识抽取 (若开启 extraction-enabled 且 extractionService 可用)
        if (properties.isExtractionEnabled() && extractionService != null) {
            for (Document chunk : chunks) {
                try {
                    String chunkContent = chunk.getText();
                    if (chunkContent != null && !chunkContent.isBlank()) {
                        RagExtractRequest request =
                                new RagExtractRequest(null, chunkContent, userId, sourceType.name(), null);
                        StructuredKnowledge knowledge = extractionService.extract(request);
                        if (knowledge != null) {
                            chunk.getMetadata().put("structuredKnowledge", knowledge);
                        }
                    }
                } catch (Exception e) {
                    log.warn("RAG 结构化抽取降级（不阻断入库）: source={} error={}", source, e.getMessage());
                }
            }
        }

        // 4. 根据冲突策略筛选写入列表
        List<Document> toWrite;
        if (policy == ConflictPolicy.SKIP) {
            toWrite = dedupeByContentHash(chunks, userId);
        } else {
            // OVERWRITE 或 FORCE_ADD 模式下全量写入
            toWrite = chunks;
        }

        int ingested = toWrite.size();
        int skipped = chunks.size() - ingested;
        if (ingested > 0) {
            try {
                ragVectorStore.add(toWrite);
                log.info(
                        "RAG 入库完成: sourceType={} source={} ingested={} skipped={} policy={} userId={}",
                        sourceType,
                        source,
                        ingested,
                        skipped,
                        policy,
                        userId);
            } catch (Exception e) {
                log.error(
                        "RAG 入库写入 VectorStore 异常: sourceType={} source={} chunks={} error={}",
                        sourceType,
                        source,
                        ingested,
                        e.getMessage(),
                        e);
                throw new RuntimeException("RAG 向量库写入失败: " + source, e);
            }
        } else {
            log.info(
                    "RAG 入库全部去重跳过: sourceType={} source={} chunks={} userId={}",
                    sourceType,
                    source,
                    chunks.size(),
                    userId);
        }

        return new IngestResult(ingested, skipped);
    }

    /**
     * 重新入库：先删后写（内部调用 OVERWRITE 策略入库）。
     */
    @Transactional
    public ReingestResult reingest(SourceType sourceType, String source, String fileName, String userId) {
        int removed = deleteBySourceAndUser(source, sourceType.name(), userId);
        IngestResult ingestResult = ingest(sourceType, source, fileName, userId, ConflictPolicy.FORCE_ADD);
        return new ReingestResult(removed, ingestResult.ingested(), ingestResult.skipped());
    }

    public record ReingestResult(int removed, int ingested, int skipped) {}

    /**
     * 按 source + userId 删除。
     */
    @Transactional
    public int deleteBySourceAndUser(String source, String userId) {
        return deleteBySourceAndUser(source, null, userId);
    }

    /**
     * 按 (userId, sourceType, source) 三元组严格删除对应向量，保证多租户隔离与精准物理清理。
     *
     * @param source     来源标识
     * @param sourceType 来源类型（可选）
     * @param userId     用户 ID
     * @return 删除状态标志 (1 表示已执行成功)
     */
    @Transactional
    public int deleteBySourceAndUser(String source, String sourceType, String userId) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        String uid = (userId != null) ? userId : "system";
        try {
            FilterExpressionBuilder feb = new FilterExpressionBuilder();
            Filter.Expression filter;
            if (sourceType != null && !sourceType.isBlank()) {
                filter = feb.and(
                                feb.eq("source", source),
                                feb.and(feb.eq("userId", uid), feb.eq("sourceType", sourceType)))
                        .build();
            } else {
                filter =
                        feb.and(feb.eq("source", source), feb.eq("userId", uid)).build();
            }
            ragVectorStore.delete(filter);
            log.info("RAG 精确删除完成: source={} sourceType={} userId={}", source, sourceType, uid);
            return 1;
        } catch (Exception e) {
            log.error(
                    "RAG 删除异常: source={} sourceType={} userId={} error={}", source, sourceType, uid, e.getMessage(), e);
            throw new RuntimeException("RAG 删除失败: " + source, e);
        }
    }

    /**
     * 按 contentHash + userId 细粒度删除切片。
     */
    @Transactional
    public int deleteByContentHash(String contentHash, String userId) {
        if (contentHash == null || contentHash.isBlank()) {
            return 0;
        }
        String uid = (userId != null) ? userId : "system";
        try {
            FilterExpressionBuilder feb = new FilterExpressionBuilder();
            Filter.Expression filter = feb.and(feb.eq("contentHash", contentHash), feb.eq("userId", uid))
                    .build();
            ragVectorStore.delete(filter);
            log.info("RAG contentHash 删除完成: hash={} userId={}", contentHash, uid);
            return 1;
        } catch (Exception e) {
            log.error("RAG contentHash 删除异常: hash={} userId={} error={}", contentHash, uid, e.getMessage(), e);
            throw new RuntimeException("RAG 删除失败: " + contentHash, e);
        }
    }

    private List<Document> dedupeByContentHash(List<Document> chunks, String userId) {
        String uid = (userId != null) ? userId : "system";
        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        List<Document> result = new java.util.ArrayList<>(chunks.size());
        for (Document chunk : chunks) {
            Object hashObj = chunk.getMetadata().get("contentHash");
            if (hashObj == null) {
                result.add(chunk);
                continue;
            }
            String hash = hashObj.toString();
            Filter.Expression filter =
                    feb.and(feb.eq("contentHash", hash), feb.eq("userId", uid)).build();
            SearchRequest probe = SearchRequest.builder()
                    .query("")
                    .topK(1)
                    .filterExpression(filter)
                    .build();
            boolean exists;
            try {
                exists = !ragVectorStore.similaritySearch(probe).isEmpty();
            } catch (Exception e) {
                log.warn("RAG 去重预检失败（按未重复处理）: hash={} error={}", hash, e.getMessage());
                exists = false;
            }
            if (!exists) {
                result.add(chunk);
            }
        }
        return result;
    }
}
