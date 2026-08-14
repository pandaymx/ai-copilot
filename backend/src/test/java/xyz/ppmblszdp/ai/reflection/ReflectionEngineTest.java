package xyz.ppmblszdp.ai.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class ReflectionEngineTest {

    private ProviderRegistry registry;
    private ChatModel mockChatModel;
    private ReflectionProperties properties;
    private ReflectionEngine engine;

    @BeforeEach
    void setUp() {
        registry = mock(ProviderRegistry.class);
        mockChatModel = mock(ChatModel.class);
        when(mockChatModel.getOptions())
                .thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .build());

        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("deepseek")
                .chatModel(mockChatModel)
                .build();
        ModelDescriptor model = ModelDescriptor.builder()
                .id("deepseek-chat")
                .modelName("deepseek-chat")
                .build();
        ResolvedModel resolved = new ResolvedModel(mockChatModel, provider, model);

        when(registry.resolve(any(), any())).thenReturn(resolved);

        properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setMinContentLength(20);
        properties.setTimeoutMs(3000);

        engine = new ReflectionEngine(registry, properties);
    }

    @Test
    @DisplayName("短文本应跳过反思直接返回 passed")
    void testShortTextSkipsReflection() {
        ReflectionAssessment res = engine.evaluate("你好", "你好！很高兴为你提供服务。", null);
        assertThat(res.passed()).isTrue();
    }

    @Test
    @DisplayName("回答自检无误时返回 passed=true")
    void testReflectionPassed() {
        String mockPassedJson = """
				```json
				{
				  "passed": true,
				  "factualityScore": 0.98,
				  "completenessScore": 0.95,
				  "issues": []
				}
				```
				""";

        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockPassedJson)))));

        ReflectionAssessment assessment = engine.evaluate(
                "如何使用 Spring AI 2.0 实现 RAG？",
                "使用 VectorStore 和 FilterExpressionBuilder 即可构建高可用的向量检索知识库问答系统...",
                "Spring AI 官方文档");

        assertThat(assessment.passed()).isTrue();
    }

    @Test
    @DisplayName("回答存在事实或逻辑遗漏时触发纠偏 passed=false 并提取纠偏内容")
    void testReflectionNeedsCorrection() {
        String mockCorrectionJson = """
				```json
				{
				  "passed": false,
				  "factualityScore": 0.65,
				  "completenessScore": 0.70,
				  "issues": ["遗漏了 Java 25 StructuredTaskScope 的超时处理机制", "混淆了线程池关闭方式"],
				  "correctionExplanation": "原回答未涵盖超时熔断保护，容易造成任务死锁",
				  "supplementalCorrection": "推荐使用 scope.joinUntil(Instant.now().plusSeconds(5)) 进行精确超时控制。"
				}
				```
				""";

        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockCorrectionJson)))));

        ReflectionAssessment assessment = engine.evaluate(
                "请展示 Java 25 结构化并发中的超时控制", "使用 StructuredTaskScope.open() 启动任务并使用 join() 等待即可...", null);

        assertThat(assessment.passed()).isFalse();
        assertThat(assessment.issues()).hasSize(2);
        assertThat(assessment.correctionExplanation()).contains("超时熔断");
        assertThat(assessment.supplementalCorrection()).contains("joinUntil");
    }
}
