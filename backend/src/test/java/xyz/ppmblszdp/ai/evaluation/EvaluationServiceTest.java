package xyz.ppmblszdp.ai.evaluation;

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
import xyz.ppmblszdp.ai.evaluation.dto.AbTestResultDto;
import xyz.ppmblszdp.ai.evaluation.dto.BenchmarkCase;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationRequests;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationResultDto;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationSummaryDto;
import xyz.ppmblszdp.ai.evaluation.service.EvaluationService;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class EvaluationServiceTest {

    private ProviderRegistry registry;
    private ChatModel mockChatModel;
    private EvaluationService evaluationService;

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

        evaluationService = new EvaluationService(registry);
    }

    @Test
    @DisplayName("基准测试集查询与动态添加测试")
    void testBenchmarksCrud() {
        List<BenchmarkCase> all = evaluationService.listBenchmarks(null);
        assertThat(all).hasSizeGreaterThanOrEqualTo(5);

        BenchmarkCase custom = new BenchmarkCase("custom-1", "测试标题", "自定义", "问题是什么？", "标准答案", "上下文", List.of("test"));
        BenchmarkCase created = evaluationService.addBenchmark(custom);
        assertThat(created.id()).isEqualTo("custom-1");

        assertThat(evaluationService.getBenchmark("custom-1")).isPresent();
        assertThat(evaluationService.deleteBenchmark("custom-1")).isTrue();
        assertThat(evaluationService.getBenchmark("custom-1")).isEmpty();
    }

    @Test
    @DisplayName("单条 LLM-as-Judge 裁判评分与 5 维度解析测试")
    void testJudgeSingle() {
        String mockJudgeJson = """
				```json
				{
				  "relevance": 0.95,
				  "accuracy": 0.92,
				  "completeness": 0.88,
				  "fluency": 0.90,
				  "safety": 1.0,
				  "feedback": "回答切中技术要害，代码逻辑严密无幻觉。"
				}
				```
				""";

        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockJudgeJson)))));

        EvaluationRequests.SingleJudgeRequest req = new EvaluationRequests.SingleJudgeRequest(
                "如何使用 Spring AI 实现向量检索？",
                "使用 VectorStore 和 FilterExpressionBuilder 进行带租户过滤的查询...",
                "Spring AI 官方参考",
                "应使用 VectorStore",
                "deepseek",
                "deepseek-chat");

        EvaluationResultDto result = evaluationService.judgeSingle(req);

        assertThat(result).isNotNull();
        assertThat(result.metrics().getRelevance()).isEqualTo(0.95);
        assertThat(result.metrics().getAccuracy()).isEqualTo(0.92);
        assertThat(result.metrics().getCompleteness()).isEqualTo(0.88);
        assertThat(result.metrics().getSafety()).isEqualTo(1.0);
        assertThat(result.judgeFeedback()).contains("代码逻辑严密");
        assertThat(result.metrics().getOverallScore()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("A/B 盲测对比评测与胜负判定测试")
    void testRunAbTest() {
        String mockAbJudgeJson = """
				```json
				{
				  "winner": "MODEL_A",
				  "reason": "Model A 在并发控制与超时熔断方面提供了更完善的代码示例。",
				  "metricsA": { "relevance": 0.95, "accuracy": 0.95, "completeness": 0.9, "fluency": 0.9, "safety": 1.0 },
				  "metricsB": { "relevance": 0.8, "accuracy": 0.8, "completeness": 0.7, "fluency": 0.85, "safety": 1.0 }
				}
				```
				""";

        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockAbJudgeJson)))));

        EvaluationRequests.AbRequest abReq = new EvaluationRequests.AbRequest(
                "请编写 Java 25 虚拟线程示例",
                "Java 25 特性",
                "需包含 StructuredTaskScope",
                "deepseek",
                "deepseek-chat",
                "openai",
                "gpt-4o",
                "deepseek",
                "deepseek-chat");

        AbTestResultDto abResult = evaluationService.runAbTest(abReq);

        assertThat(abResult).isNotNull();
        assertThat(abResult.winner()).isEqualTo("MODEL_A");
        assertThat(abResult.comparisonReason()).contains("Model A");
        assertThat(abResult.metricsA().getAccuracy()).isEqualTo(0.95);
        assertThat(abResult.metricsB().getAccuracy()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("大盘聚合概览与人工标注覆盖测试")
    void testSummaryAndAnnotation() {
        String mockJudgeJson = """
				{
				  "relevance": 0.9,
				  "accuracy": 0.9,
				  "completeness": 0.9,
				  "fluency": 0.9,
				  "safety": 1.0,
				  "feedback": "表现优秀"
				}
				""";

        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockJudgeJson)))));

        EvaluationResultDto res = evaluationService.judgeSingle(new EvaluationRequests.SingleJudgeRequest(
                "测试 Prompt", "测试回答", null, null, "deepseek", "deepseek-chat"));

        // 人工标注
        var annotated = evaluationService.annotateResult(res.id(), 0.98, "人工复核：优秀");
        assertThat(annotated).isPresent();
        assertThat(annotated.get().humanScore()).isEqualTo(0.98);
        assertThat(annotated.get().humanAnnotation()).isEqualTo("人工复核：优秀");

        EvaluationSummaryDto summary = evaluationService.getSummary();
        assertThat(summary.totalEvaluations()).isGreaterThanOrEqualTo(1);
        assertThat(summary.leaderboard()).isNotEmpty();
        assertThat(summary.dimensionAverages().getSafety()).isEqualTo(1.0);
    }
}
