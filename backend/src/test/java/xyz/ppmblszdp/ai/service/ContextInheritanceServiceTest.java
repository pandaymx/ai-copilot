package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.context.HeuristicTokenEstimator;
import xyz.ppmblszdp.ai.context.TokenEstimator;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.CodeSnippet;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.ImportContextRequest;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.ImportContextResponse;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.InheritedContext;
import xyz.ppmblszdp.ai.dto.ContextInheritanceDto.KeyDecision;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class ContextInheritanceServiceTest {

    private ProviderRegistry registry;
    private SessionService sessionService;
    private TokenEstimator tokenEstimator;
    private ChatMemory chatMemory;
    private ObjectProvider<ChatMemory> chatMemoryProvider;
    private ChatModel chatModel;
    private ContextInheritanceService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = mock(ProviderRegistry.class);
        sessionService = mock(SessionService.class);
        tokenEstimator = new HeuristicTokenEstimator(1.1);
        chatMemory = mock(ChatMemory.class);
        chatMemoryProvider = mock(ObjectProvider.class);
        when(chatMemoryProvider.getIfAvailable()).thenReturn(chatMemory);

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

        service = new ContextInheritanceService(registry, sessionService, tokenEstimator, chatMemoryProvider);
    }

    @Test
    @DisplayName("规则快速提取：准确识别代码块、文件引用与待办")
    void testExtractContextByRules() {
        List<SessionDto.MessageItem> messages = List.of(
                new SessionDto.MessageItem("m1", "user", "我们需要重构 src/main/java/xyz/App.java，请给出示例"),
                new SessionDto.MessageItem(
                        "m2",
                        "assistant",
                        "```java\npublic class App { public static void main(String[] args) {} }\n```\n- [ ] 还需要添加单元测试？"));

        InheritedContext ctx = service.extractContextByRules("s-1", "重构任务", messages);

        assertThat(ctx.sourceSessionId()).isEqualTo("s-1");
        assertThat(ctx.sourceSessionTitle()).isEqualTo("重构任务");
        assertThat(ctx.extractionMode()).isEqualTo("RULE_FALLBACK");
        assertThat(ctx.codeSnippets()).hasSize(1);
        assertThat(ctx.codeSnippets().get(0).language()).isEqualTo("java");
        assertThat(ctx.codeSnippets().get(0).code()).contains("public class App");
        assertThat(ctx.fileReferences()).isNotEmpty();
        assertThat(ctx.pendingQuestions()).hasSize(1);
    }

    @Test
    @DisplayName("LLM 语义结构化提炼：解析完整 5 维结构")
    void testExportContextWithLlm() {
        String sessionId = "s-llm-1";
        String userId = "u-123";

        List<SessionDto.MessageItem> messages = List.of(
                new SessionDto.MessageItem("m1", "user", "请帮我实现一个分布式缓存，采用 Redis 双写还是 Cache-Aside？"),
                new SessionDto.MessageItem("m2", "assistant", "建议使用 Cache-Aside 模式，避免双写一致性问题。"));

        SessionDto.SessionDetail detail =
                new SessionDto.SessionDetail(sessionId, "缓存方案选型", System.currentTimeMillis(), false, messages);

        when(sessionService.getSessionDetail(sessionId, userId)).thenReturn(Optional.of(detail));

        String llmJson = """
                {
                  "contextSummary": "探讨了分布式缓存方案选型，确定采用 Cache-Aside 模式",
                  "keyDecisions": [
                    {
                      "decision": "选用 Cache-Aside 模式",
                      "rationale": "避免双写一致性并发异常",
                      "category": "架构"
                    }
                  ],
                  "codeSnippets": [],
                  "fileReferences": [
                    {
                      "fileName": "RedisConfig.java",
                      "fileType": "java",
                      "description": "缓存配置类"
                    }
                  ],
                  "pendingQuestions": [
                    {
                      "question": "是否引入本地 Caffeine 缓存做多级缓存？",
                      "context": "评估热点 Key 流量",
                      "priority": "HIGH"
                    }
                  ],
                  "entityRelations": [
                    {
                      "subject": "AppServer",
                      "relation": "queries",
                      "object": "RedisCluster",
                      "description": "读取热数据"
                    }
                  ]
                }
                """;

        ChatResponse mockResp = new ChatResponse(
                List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(llmJson))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResp);

        InheritedContext ctx =
                service.exportContext(sessionId, userId, null, null).block();

        assertThat(ctx).isNotNull();
        assertThat(ctx.extractionMode()).isEqualTo("LLM");
        assertThat(ctx.contextSummary()).contains("Cache-Aside");
        assertThat(ctx.keyDecisions()).hasSize(1);
        assertThat(ctx.keyDecisions().get(0).decision()).isEqualTo("选用 Cache-Aside 模式");
        assertThat(ctx.fileReferences()).hasSize(1);
        assertThat(ctx.pendingQuestions()).hasSize(1);
        assertThat(ctx.pendingQuestions().get(0).priority()).isEqualTo("HIGH");
        assertThat(ctx.entityRelations()).hasSize(1);
    }

    @Test
    @DisplayName("上下文导入：结构化注入 ChatMemory 并记录关联元数据")
    void testImportContext() {
        String targetSessionId = "s-target-1";
        String userId = "u-test";

        InheritedContext ctx = new InheritedContext(
                "s-source-1",
                "源会话项目",
                "项目核心目标是重构支付网关",
                List.of(new KeyDecision("使用 AES-256 加密凭据", "提升安全性", "安全", System.currentTimeMillis())),
                List.of(new CodeSnippet("java", "String key = \"secret\";", "密钥生成", "KeyGen.java")),
                List.of(),
                List.of(),
                List.of(),
                System.currentTimeMillis(),
                120,
                "LLM");

        ImportContextRequest req =
                new ImportContextRequest(ctx, List.of("summary", "decisions", "code"), "注意：需支持多租户隔离", "继承测试会话");

        ImportContextResponse resp = service.importContext(targetSessionId, userId, req);

        assertThat(resp.success()).isTrue();
        assertThat(resp.targetSessionId()).isEqualTo(targetSessionId);
        assertThat(resp.targetTitle()).isEqualTo("继承测试会话");
        assertThat(resp.formattedContextPreview()).contains("### 📝 背景与核心主旨概述");
        assertThat(resp.formattedContextPreview()).contains("### ⚖️ 关键设计与架构决策");
        assertThat(resp.formattedContextPreview()).contains("### 💡 用户附加备忘与约束");

        // 验证 ChatMemory 注入
        verify(chatMemory).add(eq(targetSessionId), any(List.class));

        // 验证 SessionService 记录关联
        verify(sessionService)
                .recordSessionWithInheritance(
                        eq(targetSessionId), eq(userId), eq("继承测试会话"), eq(false), eq("s-source-1"), any());
    }
}
