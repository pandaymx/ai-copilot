package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.context.HeuristicTokenEstimator;
import xyz.ppmblszdp.ai.context.TokenEstimator;
import xyz.ppmblszdp.ai.dto.ConversationSummaryDto;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class ConversationSummaryServiceTest {

    private ProviderRegistry registry;
    private SessionService sessionService;
    private TokenEstimator tokenEstimator;
    private RagIngestionService ragIngestionService;
    private ObjectProvider<RagIngestionService> ragProvider;
    private ChatModel chatModel;
    private ConversationSummaryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = mock(ProviderRegistry.class);
        sessionService = mock(SessionService.class);
        tokenEstimator = new HeuristicTokenEstimator(1.1);
        ragIngestionService = mock(RagIngestionService.class);
        ragProvider = mock(ObjectProvider.class);
        when(ragProvider.getIfAvailable()).thenReturn(ragIngestionService);

        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions())
                .thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .build());

        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("deepseek")
                .chatModel(chatModel)
                .build();
        ModelDescriptor model = ModelDescriptor.builder()
                .id("deepseek-chat")
                .modelName("deepseek-chat")
                .build();
        ResolvedModel resolved = new ResolvedModel(chatModel, provider, model);

        when(registry.resolve(any(), any())).thenReturn(resolved);

        service = new ConversationSummaryService(registry, sessionService, tokenEstimator, ragProvider);
    }

    @Test
    @DisplayName("结构化摘要生成与 JSON 健壮解析测试")
    void testGenerateSummarySuccess() {
        String sessionId = "sess-123";
        String userId = "user-1";

        List<SessionDto.MessageItem> messages = List.of(
                new SessionDto.MessageItem("msg-1", "user", "请问如何用 Spring Boot 4 和 WebFlux 实现 SSE 流式推送？"),
                new SessionDto.MessageItem("msg-2", "assistant", "可以使用 Flux<ServerSentEvent<String>> 构建控制器端点..."),
                new SessionDto.MessageItem("msg-3", "user", "那如何保障长连接断开时的资源释放？"),
                new SessionDto.MessageItem("msg-4", "assistant", "通过 doOnCancel 和 Sinks.Many 管理订阅生命周期即可。"));

        when(sessionService.getSessionDetail(sessionId, userId))
                .thenReturn(Optional.of(
                        new SessionDto.SessionDetail(sessionId, "原标题", System.currentTimeMillis(), false, messages)));

        String mockLlmJson = """
				```json
				{
				  "title": "Spring WebFlux SSE 流式推送与连接生命周期",
				  "summary": "本次对话深入探讨了基于 Spring Boot 4 与 WebFlux 的 SSE 流式响应实现方案，重点分析了 Flux 响应构造与 doOnCancel 资源回收机制。",
				  "keyDecisions": [
				    "使用 Flux<ServerSentEvent> 代替传统 SseEmitter",
				    "通过 doOnCancel 钩子安全释放客户端断开时的资源"
				  ],
				  "todos": [
				    "在业务层添加心跳 ping 帧防止网关超时断连",
				    "配置 WebFlux 编解码器内存上限"
				  ],
				  "references": [
				    "Spring WebFlux 官方文档",
				    "RFC 6202 SSE 规范"
				  ],
				  "openIssues": [
				    "反向代理（Nginx） proxy_buffering 配置需验证"
				  ],
				  "tags": [
				    "Spring Boot",
				    "WebFlux",
				    "SSE",
				    "Reactive"
				  ]
				}
				```
				""";

        ChatResponse mockResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(mockLlmJson))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        ConversationSummaryDto summary = service.generateSummary(sessionId, userId, "deepseek", "deepseek-chat")
                .block();

        assertThat(summary).isNotNull();
        assertThat(summary.conversationId()).isEqualTo(sessionId);
        assertThat(summary.title()).isEqualTo("Spring WebFlux SSE 流式推送与连接生命周期");
        assertThat(summary.summary()).contains("Spring Boot 4 与 WebFlux");
        assertThat(summary.keyDecisions()).hasSize(2);
        assertThat(summary.todos()).hasSize(2);
        assertThat(summary.references()).hasSize(2);
        assertThat(summary.openIssues()).hasSize(1);
        assertThat(summary.tags()).contains("Spring Boot", "WebFlux", "SSE");
        assertThat(summary.messageCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("沉淀至 RAG 个人知识库 - 验证元数据增强与 Markdown 文档生成")
    void testSaveToKnowledgeBase() {
        String sessionId = "sess-888";
        String userId = "user-vip";

        ConversationSummaryDto summary = new ConversationSummaryDto(
                sessionId,
                "Docker 容器安全加固指南",
                "梳理了 Docker 容器运行时的只读文件系统、capabilities 丢弃与资源限额策略。",
                List.of("禁用 --privileged 提权模式", "配置 --read-only 根文件系统"),
                List.of("编写 Dockerfile non-root 用户配置"),
                List.of("CIS Docker Benchmark"),
                List.of(),
                List.of("Docker", "Security", "DevOps"),
                6,
                System.currentTimeMillis());

        when(sessionService.getSessionDetail(sessionId, userId))
                .thenReturn(Optional.of(new SessionDto.SessionDetail(
                        sessionId,
                        "Docker 安全",
                        System.currentTimeMillis(),
                        false,
                        List.of(
                                new SessionDto.MessageItem("1", "user", "如何防止容器逃逸？"),
                                new SessionDto.MessageItem("2", "assistant", "限制 capabilities 并启用只读根系统。")))));

        when(ragIngestionService.ingest(
                        eq(SourceType.CONVERSATION_SUMMARY),
                        anyString(),
                        eq("会话沉淀-Docker 容器安全加固指南.md"),
                        eq(userId),
                        eq(ConflictPolicy.OVERWRITE),
                        any()))
                .thenReturn(new RagIngestionService.IngestResult(3, 0));

        Map<String, Object> result = service.saveToKnowledgeBase(sessionId, userId, summary, null);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("fileName")).isEqualTo("会话沉淀-Docker 容器安全加固指南.md");
        assertThat(result.get("ingestedChunks")).isEqualTo(3);
        assertThat(result.get("sourceType")).isEqualTo("CONVERSATION_SUMMARY");

        // 验证 metadata 中包含 sessionId, topicTags, summaryCreatedAt
        verify(ragIngestionService)
                .ingest(
                        eq(SourceType.CONVERSATION_SUMMARY),
                        anyString(),
                        eq("会话沉淀-Docker 容器安全加固指南.md"),
                        eq(userId),
                        eq(ConflictPolicy.OVERWRITE),
                        org.mockito.ArgumentMatchers.argThat(
                                (Map<String, ?> meta) -> sessionId.equals(meta.get("sessionId"))
                                        && meta.containsKey("topicTags")
                                        && meta.containsKey("summaryCreatedAt")));
    }
}
