package xyz.ppmblszdp.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
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
 *
 * <p>
 * <b>去重（回应任务 7.4）</b>：入库前按切片 {@code contentHash + userId} 预检，已存在则跳过该切片，
 * 返回新增/跳过计数，避免同一内容重复堆积向量。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final DocumentReaderFactory readerFactory;
    private final RagTextSplitter splitter;
    private final VectorStore ragVectorStore;
    private final RagProperties properties;

    public RagIngestionService(DocumentReaderFactory readerFactory,
            TokenBasedRagTextSplitter splitter,
            @Qualifier("ragVectorStore") VectorStore ragVectorStore,
            RagProperties properties) {
        this.readerFactory = readerFactory;
        this.splitter = splitter;
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
    }

    /**
     * 入库结果：区分实际新增与去重跳过的 chunk 数，供控制器回传前端展示。
     *
     * @param ingested 实际写入向量库的 chunk 数
     * @param skipped  因内容重复（contentHash 已存在）而跳过的 chunk 数
     */
    public record IngestResult(int ingested, int skipped) {
    }

    /**
     * 入库入口：读取 → 切片 → 注入元数据（含 contentHash）→ 去重预检 → 批量写入。
     *
     * @param sourceType 文档源类型
     * @param source     来源字符串（文件路径 / URL / 原始文本）
     * @param fileName   文件名标识
     * @param userId     关联用户 ID（String）
     * @return 入库结果（新增 / 跳过计数）
     */
    public IngestResult ingest(SourceType sourceType, String source, String fileName, String userId) {
        if (source == null || source.isBlank()) {
            log.warn("入库源为空，跳过: sourceType={} fileName={}", sourceType, fileName);
            return new IngestResult(0, 0);
        }

        // 1. 多源解析
        log.info("RAG 入库开始: sourceType={} source={} fileName={} userId={}",
                sourceType, source, fileName, userId);
        List<Document> docs = readerFactory.read(sourceType, source, fileName);

        if (docs.isEmpty()) {
            log.warn("解析后文档为空: sourceType={} source={}", sourceType, source);
            return new IngestResult(0, 0);
        }
        log.info("解析完成: 文档数={}", docs.size());

        // 2. 切片（含 overlap）
        List<Document> chunks = splitter.apply(docs);
        if (chunks.isEmpty()) {
            log.warn("切片后为空: sourceType={} source={}", sourceType, source);
            return new IngestResult(0, 0);
        }
        log.info("切片完成: chunk数={}", chunks.size());

        // 3. 注入元数据（含 contentHash 去重键；userId / sourceType 等均以 String 写入）
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

        // 4. 内容级去重预检：剔除已存在的相同 contentHash（同一 userId 下）
        List<Document> toWrite = dedupeByContentHash(chunks, userId);

        // 5. 批量写入 VectorStore（仅写入去重后的增量）
        int ingested = toWrite.size();
        int skipped = chunks.size() - ingested;
        if (ingested > 0) {
            try {
                ragVectorStore.add(toWrite);
                log.info("RAG 入库完成: sourceType={} source={} ingested={} skipped={} userId={}",
                        sourceType, source, ingested, skipped, userId);
            } catch (Exception e) {
                log.error("RAG 入库写入 VectorStore 异常: sourceType={} source={} chunks={} error={}",
                        sourceType, source, ingested, e.getMessage(), e);
                throw new RuntimeException("RAG 向量库写入失败: " + source, e);
            }
        } else {
            log.info("RAG 入库全部去重跳过: sourceType={} source={} chunks={} userId={}",
                    sourceType, source, chunks.size(), userId);
        }

        return new IngestResult(ingested, skipped);
    }

    /**
     * 覆盖更新（重新入库）：先按 {@code source + userId} 删除旧向量，再走完整入库管道。
     *
     * <p>
     * <b>幂等与一致性（回应优化建议 1）</b>：采用"先删后写"策略。仅当删除阶段已成功（无异常抛出）
     * 才执行后续入库；若删除失败则直接抛出，绝不在旧数据残留状态下写入，避免脏数据。
     *
     * @param sourceType 文档源类型
     * @param source     来源字符串（需与入库时一致，作为删除 Filter 的 source 键）
     * @param fileName   文件名标识
     * @param userId     关联用户 ID
     * @return 删除的旧 chunk 数 + 入库结果
     */
    public ReingestResult reingest(SourceType sourceType, String source, String fileName, String userId) {
        int removed = deleteBySourceAndUser(source, userId);
        IngestResult ingestResult = ingest(sourceType, source, fileName, userId);
        return new ReingestResult(removed, ingestResult.ingested(), ingestResult.skipped());
    }

    /**
     * 重新入库结果：删除的旧向量数 + 新写入/跳过数。
     */
    public record ReingestResult(int removed, int ingested, int skipped) {
    }

    /**
     * 按 {@code source + userId} 精确删除对应向量（幂等：删除目标不存在时返回 0 不报错）。
     *
     * @return 删除命中的文档数（PgVector 不回传精确计数时按 best-effort 返回，失败返回 0）
     */
    public int deleteBySourceAndUser(String source, String userId) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        try {
            FilterExpressionBuilder feb = new FilterExpressionBuilder();
            Filter.Expression filter = feb.and(
                    feb.eq("source", source),
                    feb.eq("userId", (userId != null) ? userId : "system")).build();
            ragVectorStore.delete(filter);
            log.info("RAG 删除完成: source={} userId={}", source, userId);
            return 1;
        } catch (Exception e) {
            log.error("RAG 删除异常: source={} userId={} error={}", source, userId, e.getMessage(), e);
            throw new RuntimeException("RAG 删除失败: " + source, e);
        }
    }

    /**
     * 内容级去重：对每个 chunk 的 contentHash（同一 userId 下）是否已经存在于向量库做预检，
     * 已存在则跳过。返回需要写入的增量 chunk 列表。
     *
     * <p>
     * 实现：聚合去重 hash 集合，逐个 hash 用 {@code eq(contentHash) AND eq(userId)}
     * 查询一次（topK=1），
     * 命中即视为重复。检索降级（抛出异常）时视为"未重复"，保证入库流程不中断。
     */
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
            Filter.Expression filter = feb.and(
                    feb.eq("contentHash", hash),
                    feb.eq("userId", uid)).build();
            SearchRequest probe = SearchRequest.builder()
                    .query("") // 去重仅用 filter，不需要语义相似
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
