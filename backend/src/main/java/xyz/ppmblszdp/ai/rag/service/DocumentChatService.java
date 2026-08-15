package xyz.ppmblszdp.ai.rag.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.chunker.RagTextSplitter;
import xyz.ppmblszdp.ai.rag.chunker.TokenBasedRagTextSplitter;
import xyz.ppmblszdp.ai.rag.dto.DocChatDocResponse;
import xyz.ppmblszdp.ai.rag.dto.DocChunkResponse;
import xyz.ppmblszdp.ai.rag.dto.DocumentCitationDto;
import xyz.ppmblszdp.ai.rag.reader.DocumentReaderFactory;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.rerank.RagReranker;

/**
 * 文档对话核心服务（Chat with Document）。
 *
 * <p>提供基于会话级隔离与文档级范围限定的专属文档问答能力：
 * <ul>
 *   <li>会话专属文档切片与临时向量索引（注入 conversationId、docId、pageNumber、paragraphIndex 等元数据）；</li>
 *   <li>多文档挂载与交叉比对检索；</li>
 *   <li>严格事实锚定检索与超范围自动拒答提示注入；</li>
 *   <li>精准页码/段落引用提取与结构化 Citation 构造；</li>
 *   <li>文档原文切片段落对照读取（支持前端点击跳转高亮）。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class DocumentChatService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChatService.class);
    private static final int MAX_FETCH_CHUNKS = 1000;

    private final DocumentReaderFactory readerFactory;
    private final RagTextSplitter splitter;
    private final VectorStore ragVectorStore;
    private final RagProperties properties;
    private final RagReranker reranker;

    public DocumentChatService(
            DocumentReaderFactory readerFactory,
            TokenBasedRagTextSplitter splitter,
            @Qualifier("ragVectorStore") VectorStore ragVectorStore,
            RagProperties properties,
            ObjectProvider<RagReranker> rerankerProvider) {
        this.readerFactory = readerFactory;
        this.splitter = splitter;
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
        this.reranker = rerankerProvider != null ? rerankerProvider.getIfAvailable() : null;
    }

    /** 构造器重载（方便测试与非 Provider 场景注入） */
    public DocumentChatService(
            DocumentReaderFactory readerFactory,
            RagTextSplitter splitter,
            VectorStore ragVectorStore,
            RagProperties properties,
            RagReranker reranker) {
        this.readerFactory = readerFactory;
        this.splitter = splitter;
        this.ragVectorStore = ragVectorStore;
        this.properties = properties;
        this.reranker = reranker;
    }

    /**
     * 文档对话检索上下文与引用结果封装。
     */
    public record DocumentChatContext(
            String formattedContext,
            List<DocumentCitationDto> citations,
            List<Document> matchedChunks,
            boolean hasContext) {
        public static DocumentChatContext empty() {
            return new DocumentChatContext("", List.of(), List.of(), false);
        }
    }

    /**
     * 将文档上传并挂载至指定会话（会话级向量切片入库）。
     *
     * @param conversationId 会话 ID（必填）
     * @param sourceType 文档类型
     * @param source 来源路径/文本/URL
     * @param fileName 文件名
     * @param userId 用户 ID
     * @return 挂载结果响应
     */
    @Transactional
    public DocChatDocResponse ingestSessionDocument(
            String conversationId, SourceType sourceType, String source, String fileName, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : UserIdentityFilter.DEFAULT_USER_ID;
        String effectiveFileName = (fileName != null && !fileName.isBlank()) ? fileName : "未命名文档";

        // 1. 读取原始文档
        List<Document> rawDocs = readerFactory.read(sourceType, source, effectiveFileName);
        if (rawDocs == null || rawDocs.isEmpty()) {
            log.warn("文档读取为空: conversationId={} source={}", conversationId, source);
            return new DocChatDocResponse(
                    "",
                    conversationId,
                    effectiveFileName,
                    sourceType.name(),
                    0,
                    Instant.now().toString());
        }

        // 2. 切片分块
        List<Document> chunks = splitter.apply(rawDocs);
        if (chunks == null || chunks.isEmpty()) {
            chunks = rawDocs;
        }

        // 3. 分配唯一 docId 并注入会话隔离与精准位置元数据
        String docId = "doc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String timestamp = Instant.now().toString();

        List<Document> enrichedChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> meta = new HashMap<>(chunk.getMetadata());

            meta.put("docId", docId);
            meta.put("conversationId", conversationId);
            meta.put("userId", effectiveUser);
            meta.put("fileName", effectiveFileName);
            meta.put("sourceType", sourceType.name());
            meta.put("source", (source != null) ? source : "inline");
            meta.put("ingestedAt", timestamp);
            meta.put("paragraphIndex", String.valueOf(i + 1));
            meta.put("chunkIndex", String.valueOf(i + 1));
            if (!meta.containsKey("pageNumber")) {
                meta.put("pageNumber", "1");
            } else {
                meta.put("pageNumber", String.valueOf(meta.get("pageNumber")));
            }
            meta.put("contentHash", sha256Hex(chunk.getText()));

            String chunkId = docId + "_p" + (i + 1);
            enrichedChunks.add(new Document(chunkId, chunk.getText(), meta));
        }

        // 4. 写入向量库
        try {
            ragVectorStore.accept(enrichedChunks);
            log.info(
                    "会话文档入库完成: convId={} docId={} file={} chunks={}",
                    conversationId,
                    docId,
                    effectiveFileName,
                    enrichedChunks.size());
        } catch (Exception e) {
            log.error("会话文档写入向量库失败: convId={} docId={} error={}", conversationId, docId, e.getMessage(), e);
            throw new RuntimeException("会话文档入库失败: " + e.getMessage(), e);
        }

        return new DocChatDocResponse(
                docId, conversationId, effectiveFileName, sourceType.name(), enrichedChunks.size(), timestamp);
    }

    /**
     * 查询指定会话挂载的所有文档列表。
     */
    public List<DocChatDocResponse> getSessionDocuments(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : UserIdentityFilter.DEFAULT_USER_ID;

        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        Filter.Expression filter = feb.and(feb.eq("conversationId", conversationId), feb.eq("userId", effectiveUser))
                .build();

        SearchRequest request = SearchRequest.builder()
                .query("")
                .topK(MAX_FETCH_CHUNKS)
                .filterExpression(filter)
                .build();

        List<Document> records;
        try {
            records = ragVectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.warn("查询会话文档记录异常: convId={} userId={} error={}", conversationId, effectiveUser, e.getMessage());
            return Collections.emptyList();
        }

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, DocChatDocResponse> docMap = new LinkedHashMap<>();
        for (Document doc : records) {
            Map<String, Object> m = doc.getMetadata();
            String docId = String.valueOf(m.getOrDefault("docId", ""));
            if (docId.isBlank()) continue;

            String fileName = String.valueOf(m.getOrDefault("fileName", ""));
            String sourceType = String.valueOf(m.getOrDefault("sourceType", "TEXT"));
            String ingestedAt = String.valueOf(m.getOrDefault("ingestedAt", ""));

            DocChatDocResponse existing = docMap.get(docId);
            if (existing == null) {
                docMap.put(docId, new DocChatDocResponse(docId, conversationId, fileName, sourceType, 1, ingestedAt));
            } else {
                docMap.put(
                        docId,
                        new DocChatDocResponse(
                                docId,
                                conversationId,
                                fileName,
                                sourceType,
                                existing.chunkCount() + 1,
                                existing.ingestedAt()));
            }
        }

        List<DocChatDocResponse> result = new ArrayList<>(docMap.values());
        result.sort(Comparator.comparing(DocChatDocResponse::ingestedAt).reversed());
        return result;
    }

    /**
     * 删除指定会话中的某份文档。
     */
    @Transactional
    public boolean deleteSessionDocument(String docId, String conversationId, String userId) {
        if (docId == null || docId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return false;
        }
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : UserIdentityFilter.DEFAULT_USER_ID;

        try {
            FilterExpressionBuilder feb = new FilterExpressionBuilder();
            Filter.Expression filter = feb.and(
                            feb.eq("docId", docId),
                            feb.and(feb.eq("conversationId", conversationId), feb.eq("userId", effectiveUser)))
                    .build();

            ragVectorStore.delete(filter);
            log.info("会话文档删除成功: docId={} convId={} userId={}", docId, conversationId, effectiveUser);
            return true;
        } catch (Exception e) {
            log.error("会话文档删除异常: docId={} convId={} error={}", docId, conversationId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取指定文档的所有切片段落（用于前端原文对照抽屉高亮）。
     */
    public List<DocChunkResponse> getDocumentChunks(String docId, String conversationId, String userId) {
        if (docId == null || docId.isBlank()) {
            return Collections.emptyList();
        }
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : UserIdentityFilter.DEFAULT_USER_ID;

        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        var convFilter =
                (conversationId != null && !conversationId.isBlank()) ? feb.eq("conversationId", conversationId) : null;
        var userFilter = feb.eq("userId", effectiveUser);
        var docFilter = feb.eq("docId", docId);

        Filter.Expression filter;
        if (convFilter != null) {
            filter = feb.and(docFilter, feb.and(convFilter, userFilter)).build();
        } else {
            filter = feb.and(docFilter, userFilter).build();
        }

        SearchRequest request = SearchRequest.builder()
                .query("")
                .topK(MAX_FETCH_CHUNKS)
                .filterExpression(filter)
                .build();

        List<Document> records;
        try {
            records = ragVectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.warn("获取文档切片失败: docId={} error={}", docId, e.getMessage());
            return Collections.emptyList();
        }

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<DocChunkResponse> chunks = new ArrayList<>(records.size());
        for (Document doc : records) {
            Map<String, Object> m = doc.getMetadata();
            String chunkId = doc.getId();
            String dId = String.valueOf(m.getOrDefault("docId", docId));
            String fileName = String.valueOf(m.getOrDefault("fileName", ""));
            String pageNumber = String.valueOf(m.getOrDefault("pageNumber", "1"));
            String paragraphIndex = String.valueOf(m.getOrDefault("paragraphIndex", "1"));
            chunks.add(new DocChunkResponse(chunkId, dId, fileName, pageNumber, paragraphIndex, doc.getText()));
        }

        chunks.sort(Comparator.comparingInt(c -> {
            try {
                return Integer.parseInt(c.paragraphIndex());
            } catch (Exception ignored) {
                return 0;
            }
        }));

        return chunks;
    }

    /**
     * 执行会话/文档限定的严格上下文检索与引用提取。
     *
     * @param query 用户问题
     * @param conversationId 会话 ID
     * @param docIds 指定限定的文档 ID 列表（为空则限定当前会话的所有文档）
     * @param userId 用户 ID
     * @param topK 召回数量
     * @return 严格检索结果（包含格式化上下文与结构化 Citation 列表）
     */
    public DocumentChatContext retrieveStrictContext(
            String query, String conversationId, List<String> docIds, String userId, int topK) {
        if (query == null || query.isBlank() || conversationId == null || conversationId.isBlank()) {
            return DocumentChatContext.empty();
        }
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : UserIdentityFilter.DEFAULT_USER_ID;
        int targetTopK = topK > 0 ? topK : properties.resolveTopK();

        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        Filter.Expression filter = feb.and(feb.eq("conversationId", conversationId), feb.eq("userId", effectiveUser))
                .build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(targetTopK * 2, 10))
                .filterExpression(filter)
                .build();

        List<Document> candidates;
        try {
            candidates = ragVectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.warn("文档对话向量检索异常: convId={} query={} error={}", conversationId, query, e.getMessage());
            return DocumentChatContext.empty();
        }

        if (candidates == null || candidates.isEmpty()) {
            return DocumentChatContext.empty();
        }

        // 若指定了具体 docIds，做内存精确过滤
        if (docIds != null && !docIds.isEmpty()) {
            candidates = candidates.stream()
                    .filter(c -> {
                        Object dId = c.getMetadata().get("docId");
                        return dId != null && docIds.contains(dId.toString());
                    })
                    .toList();
        }

        if (candidates.isEmpty()) {
            return DocumentChatContext.empty();
        }

        // 可选 Rerank 重排序
        if (properties.isRerankEnabled() && reranker != null) {
            candidates = reranker.rerank(query, candidates, targetTopK);
        } else if (candidates.size() > targetTopK) {
            candidates = candidates.subList(0, targetTopK);
        }

        // 构建引用与格式化提示词上下文
        List<DocumentCitationDto> citations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < candidates.size(); i++) {
            Document chunk = candidates.get(i);
            Map<String, Object> meta = chunk.getMetadata();
            String citationId = String.valueOf(i + 1);
            String docId = String.valueOf(meta.getOrDefault("docId", ""));
            String fileName = String.valueOf(meta.getOrDefault("fileName", "未知文档"));
            String pageNumber = String.valueOf(meta.getOrDefault("pageNumber", "1"));
            String paragraphIndex = String.valueOf(meta.getOrDefault("paragraphIndex", String.valueOf(i + 1)));
            String rawText = chunk.getText();
            String snippet = (rawText.length() > 180) ? rawText.substring(0, 180) + "..." : rawText;
            Double score = null;
            if (chunk.getScore() != null) {
                score = chunk.getScore();
            }

            citations.add(
                    new DocumentCitationDto(citationId, docId, fileName, pageNumber, paragraphIndex, snippet, score));

            contextBuilder.append(
                    String.format("[引用 %s | %s (第 %s 页 / 段落 %s)]\n", citationId, fileName, pageNumber, paragraphIndex));
            contextBuilder.append(rawText.trim());
            contextBuilder.append("\n\n");
        }

        return new DocumentChatContext(contextBuilder.toString().trim(), citations, candidates, true);
    }

    /**
     * 生成文档对话严格约束系统提示词。
     */
    public String buildStrictSystemPrompt(String customPrompt) {
        String baseStrictPrompt = """
                你是一名极其严谨的专业文档分析专家（专注于合同审查、学术论文研读与技术文档问答）。

                【核心回答准则与铁律】：
                1. 【严格限定事实】：你必须严格且仅根据提供的【📄 会话专属文档上下文】中的事实回答问题，严禁编造、猜测或引入任何未在文档中记载的外部知识。
                2. 【自动拒答机制】：若提供的文档上下文中不包含回答问题所需的事实，或无法从文档得出确定性结论，你必须明确拒答，标准拒答语格式为：“根据所提供的文档内容，无法找到与您问题相关的依据。当前处于【文档对话严格模式】，仅支持基于已上传文档的事实进行解答。”，严禁强行作答。
                3. 【精准引用标注】：你回答中的每一个关键结论、事实数据或条款陈述，必须在句末插入引用标记，格式为 `[引用 序号: 文档名 (第X页/段落Y)]`（例如 `[引用 1: 采购合同.pdf (第3页/段落2)]`）。
                4. 【多文档交叉比对】：当涉及多份文档时，须清晰对比各自文档在对应条款/论点上的异同。
                """;

        if (customPrompt != null && !customPrompt.isBlank()) {
            return baseStrictPrompt + "\n【用户附加指令】：\n" + customPrompt;
        }
        return baseStrictPrompt;
    }

    private String sha256Hex(String text) {
        if (text == null) text = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            int h = text.hashCode();
            return String.format("%064x", h & 0xFFFFFFFFL);
        }
    }
}
