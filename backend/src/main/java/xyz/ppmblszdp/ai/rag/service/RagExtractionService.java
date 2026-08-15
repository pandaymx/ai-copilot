package xyz.ppmblszdp.ai.rag.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.rag.dto.RagExtractRequest;
import xyz.ppmblszdp.ai.rag.dto.StructuredKnowledge;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * RAG 结构化抽取服务（利用 Spring AI {@link BeanOutputConverter} 强类型提取知识对象与结构化实体）。
 */
@Service
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagExtractionService {

    private static final Logger log = LoggerFactory.getLogger(RagExtractionService.class);

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            你是一个专业的文档分析与知识结构化抽取专家。
            请分析所提供的文档上下文内容，从中提炼出核心主题标题(title)、整体摘要(summary)、关键实体列表(entities，含实体名称、类型、详细描述)以及关键事实结论(keyFacts)。
            你必须严格按照下述指定的 JSON 格式输出，不要包含 Markdown 标记之外的其他解释文本。
            """;

    private final RagQueryService queryService;
    private final ProviderRegistry providerRegistry;

    public RagExtractionService(RagQueryService queryService, ObjectProvider<ProviderRegistry> providerRegistry) {
        this(queryService, providerRegistry != null ? providerRegistry.getIfAvailable() : null);
    }

    public RagExtractionService(RagQueryService queryService, ProviderRegistry providerRegistry) {
        this.queryService = queryService;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 执行结构化抽取：获取相关 RAG 上下文或原始文本 -> 构造格式指令 -> LLM 提取 -> BeanOutputConverter 解析。
     *
     * @param request 抽取请求参数
     * @return 强类型结构化知识对象
     */
    public StructuredKnowledge extract(RagExtractRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ExtractRequest 不能为空");
        }

        String contextText = "";
        if (request.rawText() != null && !request.rawText().isBlank()) {
            contextText = request.rawText();
        } else if (request.query() != null && !request.query().isBlank()) {
            int topK = (request.topK() != null && request.topK() > 0) ? request.topK() : 4;
            List<Document> docs = queryService.search(request.query(), request.userId(), request.sourceType(), topK);
            if (docs.isEmpty()) {
                log.info("未找到相关 RAG 片段，结构化抽取返回空知识库对象: query={}", request.query());
                return new StructuredKnowledge(
                        request.query(), "未找到相关文档内容", Collections.emptyList(), Collections.emptyList());
            }
            contextText = docs.stream().map(d -> d.getText()).collect(Collectors.joining("\n---\n"));
        } else {
            throw new IllegalArgumentException("query 和 rawText 不能同时为空");
        }

        if (providerRegistry == null) {
            log.warn("未注册 Model ProviderRegistry，结构化抽取退化为基础模型模式");
            return new StructuredKnowledge("未就绪", "模型注册表未初始化", Collections.emptyList(), Collections.emptyList());
        }

        try {
            ResolvedModel resolved = providerRegistry.resolve(null, null);
            ChatClient chatClient = resolved.chatClient();

            BeanOutputConverter<StructuredKnowledge> converter = new BeanOutputConverter<>(StructuredKnowledge.class);
            String formatInstructions = converter.getFormat();

            String fullSystemPrompt = EXTRACTION_SYSTEM_PROMPT + "\n" + formatInstructions;

            String responseContent = chatClient
                    .prompt()
                    .system(fullSystemPrompt)
                    .user("待强类型抽取的内容如下：\n---\n" + contextText + "\n---")
                    .call()
                    .content();

            if (responseContent == null || responseContent.isBlank()) {
                log.warn("LLM 返回结构化抽取结果为空");
                return new StructuredKnowledge("未知主题", "抽取结果为空", Collections.emptyList(), Collections.emptyList());
            }

            StructuredKnowledge knowledge = converter.convert(responseContent);
            log.info(
                    "RAG 结构化抽取成功: title={} entitiesCount={}",
                    knowledge.title(),
                    knowledge.entities() != null ? knowledge.entities().size() : 0);
            return knowledge;
        } catch (Exception e) {
            log.error("RAG 结构化抽取解析异常: error={}", e.getMessage(), e);
            return new StructuredKnowledge(
                    "抽取失败", "解析出现异常: " + e.getMessage(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
