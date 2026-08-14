package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.context.TokenEstimator;
import xyz.ppmblszdp.ai.dto.ConversationSummaryDto;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 会话结构化摘要与知识沉淀服务。
 *
 * <p>负责：
 * <ul>
 *   <li>长会话 Token 预算防溢出采样与压缩；</li>
 *   <li>LLM 结构化提炼（总体概述、关键决策、待办事项、参考资料、未决问题与主题标签）；</li>
 *   <li>健壮 JSON 解析与容错降级；</li>
 *   <li>一键生成标准化 Markdown 知识文档并写入 RAG 向量知识库（携带丰富检索元数据）。</li>
 * </ul>
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_INPUT_TOKENS_BUDGET = 8000;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private final ProviderRegistry registry;
    private final SessionService sessionService;
    private final TokenEstimator tokenEstimator;
    private final ObjectProvider<RagIngestionService> ragIngestionServiceProvider;

    public ConversationSummaryService(
            ProviderRegistry registry,
            SessionService sessionService,
            TokenEstimator tokenEstimator,
            ObjectProvider<RagIngestionService> ragIngestionServiceProvider) {
        this.registry = registry;
        this.sessionService = sessionService;
        this.tokenEstimator = tokenEstimator;
        this.ragIngestionServiceProvider = ragIngestionServiceProvider;
    }

    /**
     * 生成指定会话的结构化摘要。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param provider       可选模型供应商
     * @param model          可选模型 ID
     * @return 结构化摘要 DTO
     */
    public Mono<ConversationSummaryDto> generateSummary(
            String conversationId, String userId, String provider, String model) {
        return Mono.fromCallable(() -> {
                    Optional<SessionDto.SessionDetail> detailOpt =
                            sessionService.getSessionDetail(conversationId, userId);
                    if (detailOpt.isEmpty() || detailOpt.get().messages().isEmpty()) {
                        throw new IllegalArgumentException("会话不存在或暂无对话记录，无法生成摘要");
                    }

                    List<SessionDto.MessageItem> messages = detailOpt.get().messages();
                    List<SessionDto.MessageItem> sampled = sampleMessagesWithinBudget(messages);

                    ResolvedModel resolved = registry.resolve(provider, model);
                    ChatOptions options = ChatOptionsFactory.forProvider(resolved, 0.3);

                    String formattedHistory = buildFormattedHistory(sampled);
                    Prompt prompt = new Prompt(
                            List.of(
                                    new SystemMessage(SUMMARY_SYSTEM_PROMPT),
                                    new UserMessage("【待分析提炼的完整对话历史记录】：\n\n" + formattedHistory)),
                            options);

                    ChatResponse resp = resolved.chatModel().call(prompt);
                    if (resp == null
                            || resp.getResult() == null
                            || resp.getResult().getOutput() == null) {
                        throw new IllegalStateException("LLM 摘要生成响应为空");
                    }

                    String rawOutput = resp.getResult().getOutput().getText();
                    return parseSummaryJson(conversationId, rawOutput, messages.size());
                })
                .timeout(SUMMARY_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将会话摘要与精选对话沉淀至 RAG 个人知识库。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param summary        已生成的结构化摘要
     * @param customTitle    用户自定义标题（可为空，空则取 summary.title）
     * @return 沉淀入库结果
     */
    public Map<String, Object> saveToKnowledgeBase(
            String conversationId, String userId, ConversationSummaryDto summary, String customTitle) {
        RagIngestionService ingestionService = ragIngestionServiceProvider.getIfAvailable();
        if (ingestionService == null) {
            throw new IllegalStateException("RAG 知识库功能未启用 (app.ai.rag.enabled=false)");
        }

        String finalTitle = (customTitle != null && !customTitle.isBlank())
                ? customTitle.trim()
                : ((summary.title() != null && !summary.title().isBlank())
                        ? summary.title().trim()
                        : "会话知识沉淀");

        // 获取历史消息做附录
        List<SessionDto.MessageItem> history = sessionService
                .getSessionDetail(conversationId, userId)
                .map(SessionDto.SessionDetail::messages)
                .orElse(List.of());

        String markdown = buildKnowledgeMarkdown(summary, finalTitle, history);
        String fileName = "会话沉淀-" + finalTitle + ".md";

        // 构造丰富元数据便于后续检索与过滤
        Map<String, Object> extraMetadata = new HashMap<>();
        extraMetadata.put("sessionId", conversationId);
        extraMetadata.put("topicTags", String.join(", ", summary.tags()));
        extraMetadata.put("summaryCreatedAt", String.valueOf(summary.createdAt()));
        extraMetadata.put("summaryTitle", finalTitle);

        RagIngestionService.IngestResult result = ingestionService.ingest(
                SourceType.CONVERSATION_SUMMARY, markdown, fileName, userId, ConflictPolicy.OVERWRITE, extraMetadata);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("fileName", fileName);
        resp.put("title", finalTitle);
        resp.put("ingestedChunks", result.ingested());
        resp.put("skippedChunks", result.skipped());
        resp.put("sourceType", SourceType.CONVERSATION_SUMMARY.name());
        return resp;
    }

    /**
     * 构建适合 Embedding 检索与人类阅读的高结构化 Markdown 文档。
     */
    private String buildKnowledgeMarkdown(
            ConversationSummaryDto summary, String title, List<SessionDto.MessageItem> history) {
        StringBuilder sb = new StringBuilder();
        String formattedDate = DATE_FORMATTER.format(Instant.ofEpochMilli(summary.createdAt()));

        sb.append("# 会话知识归档: ").append(title).append("\n\n");
        sb.append("- **归档时间**: ").append(formattedDate).append("\n");
        sb.append("- **会话编号**: `").append(summary.conversationId()).append("`\n");
        if (!summary.tags().isEmpty()) {
            sb.append("- **知识标签**: ");
            for (String tag : summary.tags()) {
                sb.append("`#").append(tag).append("` ");
            }
            sb.append("\n");
        }
        sb.append("\n---\n\n");

        sb.append("## 1. 核心概述 (Executive Summary)\n");
        sb.append(summary.summary()).append("\n\n");

        if (!summary.keyDecisions().isEmpty()) {
            sb.append("## 2. 关键决策与核心结论 (Key Decisions)\n");
            for (String decision : summary.keyDecisions()) {
                sb.append("- 📌 ").append(decision).append("\n");
            }
            sb.append("\n");
        }

        if (!summary.todos().isEmpty()) {
            sb.append("## 3. 待办清单与后续行动项 (Action Items)\n");
            for (String todo : summary.todos()) {
                sb.append("- [ ] ").append(todo).append("\n");
            }
            sb.append("\n");
        }

        if (!summary.references().isEmpty()) {
            sb.append("## 4. 参考资料与关键技术 (References & Tech)\n");
            for (String ref : summary.references()) {
                sb.append("- 📚 ").append(ref).append("\n");
            }
            sb.append("\n");
        }

        if (!summary.openIssues().isEmpty()) {
            sb.append("## 5. 未决问题与待探讨议题 (Open Questions)\n");
            for (String issue : summary.openIssues()) {
                sb.append("- ❓ ").append(issue).append("\n");
            }
            sb.append("\n");
        }

        if (!history.isEmpty()) {
            sb.append("---\n\n## 附录：关键对话节选记录 (Selected Transcript)\n\n");
            int count = 0;
            for (SessionDto.MessageItem item : history) {
                if (item.content() != null && !item.content().isBlank()) {
                    sb.append("### [").append(item.role().toUpperCase()).append("]\n");
                    sb.append(item.content()).append("\n\n");
                    count++;
                    if (count >= 15) {
                        sb.append("> *(后续多轮对话细节已收敛于上方核心摘要)*\n\n");
                        break;
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * 会话过长时的 Token 截断防护与采样机制：
     * 若会话超长，保留首轮（背景设定）+ 尾部轮次（最终结论）+ 中间均匀采样。
     */
    private List<SessionDto.MessageItem> sampleMessagesWithinBudget(List<SessionDto.MessageItem> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder fullText = new StringBuilder();
        for (SessionDto.MessageItem m : messages) {
            fullText.append(m.role()).append(": ").append(m.content()).append("\n");
        }

        int totalTokens = tokenEstimator.estimate(fullText.toString());
        if (totalTokens <= MAX_INPUT_TOKENS_BUDGET) {
            return messages;
        }

        log.info("会话超出摘要 Token 预算 ({} > {})，触发均匀采样裁剪", totalTokens, MAX_INPUT_TOKENS_BUDGET);

        // 如果消息数较少但单条极长，直接截断单条文本
        if (messages.size() <= 8) {
            List<SessionDto.MessageItem> truncated = new ArrayList<>();
            for (SessionDto.MessageItem m : messages) {
                String t = m.content();
                if (t != null && t.length() > 2000) {
                    t = t.substring(0, 2000) + "... [内容过长截断]";
                }
                truncated.add(new SessionDto.MessageItem(m.id(), m.role(), t, m.media()));
            }
            return truncated;
        }

        List<SessionDto.MessageItem> sampled = new ArrayList<>();
        // 保留前 2 轮
        sampled.add(messages.get(0));
        sampled.add(messages.get(1));

        // 中间抽取 4 轮
        int middleCount = messages.size() - 6;
        if (middleCount > 0) {
            int step = Math.max(1, middleCount / 4);
            for (int i = 2; i < messages.size() - 4; i += step) {
                sampled.add(messages.get(i));
                if (sampled.size() >= 8) break;
            }
        }

        // 保留最后 4 轮
        for (int i = Math.max(2, messages.size() - 4); i < messages.size(); i++) {
            sampled.add(messages.get(i));
        }

        return sampled;
    }

    private String buildFormattedHistory(List<SessionDto.MessageItem> messages) {
        StringBuilder sb = new StringBuilder();
        for (SessionDto.MessageItem m : messages) {
            String roleName = "user".equalsIgnoreCase(m.role()) ? "用户" : "AI 助手";
            sb.append("【").append(roleName).append("】：\n");
            sb.append(m.content() != null ? m.content().trim() : "").append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 输出的 JSON 结构，若模型输出包含 Markdown 代码块或有脏字符，做自动清洗抽取。
     */
    private ConversationSummaryDto parseSummaryJson(String conversationId, String raw, int messageCount) {
        String cleanJson = raw.trim();

        Matcher matcher = JSON_BLOCK_PATTERN.matcher(cleanJson);
        if (matcher.find()) {
            cleanJson = matcher.group(1).trim();
        } else {
            int firstBrace = cleanJson.indexOf('{');
            int lastBrace = cleanJson.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                cleanJson = cleanJson.substring(firstBrace, lastBrace + 1).trim();
            }
        }

        try {
            JsonNode root = MAPPER.readTree(cleanJson);
            String title = root.path("title").asText("会话总结");
            String summary = root.path("summary").asText("");
            List<String> keyDecisions = extractStringList(root.path("keyDecisions"));
            List<String> todos = extractStringList(root.path("todos"));
            List<String> references = extractStringList(root.path("references"));
            List<String> openIssues = extractStringList(root.path("openIssues"));
            List<String> tags = extractStringList(root.path("tags"));

            return new ConversationSummaryDto(
                    conversationId,
                    title,
                    summary,
                    keyDecisions,
                    todos,
                    references,
                    openIssues,
                    tags,
                    messageCount,
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("JSON 摘要解析异常，退回文本提取兜底: {}", e.getMessage());
            return new ConversationSummaryDto(
                    conversationId,
                    "会话提炼总结",
                    raw.length() > 500 ? raw.substring(0, 500) + "..." : raw,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("会话归档"),
                    messageCount,
                    System.currentTimeMillis());
        }
    }

    private List<String> extractStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText();
            if (text != null && !text.isBlank()) {
                list.add(text.trim());
            }
        }
        return list;
    }

    private static final String SUMMARY_SYSTEM_PROMPT = """
			你是一个专业的 AI 知识工程与对话提炼专家。
			你的任务是：深度分析给定的完整多轮对话历史，提炼出结构化的专业会话摘要，便于沉淀为长期的个人知识库。

			请严格输出合法的 JSON 格式，包含以下字段（禁止输出任何 JSON 之外的问候或废话）：
			```json
			{
			  "title": "高度精炼的会话核心主题（4~15 字）",
			  "summary": "全面详实的总体业务/技术概述（100~300 字，梳理对话背景、核心探讨议题与最终达成方案）",
			  "keyDecisions": [
			    "关键决策或核心结论 1",
			    "关键决策或核心结论 2"
			  ],
			  "todos": [
			    "具体可执行的待办事项/行动项 1",
			    "具体可执行的待办事项/行动项 2"
			  ],
			  "references": [
			    "涉及的关键库、框架、协议、命令或规范链接"
			  ],
			  "openIssues": [
			    "尚未完全解决的问题、待验证事项或后续可深入探索点（若无则为空数组）"
			  ],
			  "tags": [
			    "技术/业务主题标签1",
			    "技术/业务主题标签2"
			  ]
			}
			```

			【提炼准则】：
			1. 事实求是：仅从对话中提取实际讨论过的观点与方案，绝不无中生有；
			2. 结构清晰：条目简明扼要，突出技术点与核心决策逻辑；
			3. 严格 JSON：确保生成的 JSON 语法完全合法，避免未转义引号导致解析失败。
			""";
}
