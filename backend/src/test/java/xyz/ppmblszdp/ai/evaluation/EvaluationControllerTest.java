package xyz.ppmblszdp.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import xyz.ppmblszdp.ai.evaluation.controller.EvaluationController;
import xyz.ppmblszdp.ai.evaluation.dto.AbTestResultDto;
import xyz.ppmblszdp.ai.evaluation.dto.BenchmarkCase;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationMetrics;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationRequests;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationResultDto;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationSummaryDto;
import xyz.ppmblszdp.ai.evaluation.service.EvaluationService;

class EvaluationControllerTest {

    private EvaluationService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(EvaluationService.class);
        EvaluationController controller = new EvaluationController(service);
        client = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/evaluation/benchmarks - 返回基准用例列表")
    void testListBenchmarks() {
        BenchmarkCase b = new BenchmarkCase("b-1", "标题", "分类", "问题", "答案", "上下文", List.of("tag"));
        when(service.listBenchmarks(any())).thenReturn(List.of(b));

        client.get()
                .uri("/api/evaluation/benchmarks")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(BenchmarkCase.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("POST /api/evaluation/ab-test - 触发 A/B 盲测评测")
    void testRunAbTest() {
        AbTestResultDto abResult = new AbTestResultDto(
                "ab-1",
                "Prompt",
                "Context",
                "deepseek",
                "deepseek-chat",
                "Ans A",
                300L,
                new EvaluationMetrics(0.9, 0.9, 0.9, 0.9, 1.0),
                "openai",
                "gpt-4o",
                "Ans B",
                400L,
                new EvaluationMetrics(0.8, 0.8, 0.8, 0.8, 1.0),
                "deepseek",
                "deepseek-chat",
                "MODEL_A",
                "Model A 更好",
                System.currentTimeMillis());

        when(service.runAbTest(any())).thenReturn(abResult);

        EvaluationRequests.AbRequest req = new EvaluationRequests.AbRequest(
                "Prompt",
                "Context",
                "Expected",
                "deepseek",
                "deepseek-chat",
                "openai",
                "gpt-4o",
                "deepseek",
                "deepseek-chat");

        client.post()
                .uri("/api/evaluation/ab-test")
                .bodyValue(req)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AbTestResultDto.class)
                .value(r -> assertThat(r.winner()).isEqualTo("MODEL_A"));
    }

    @Test
    @DisplayName("GET /api/evaluation/summary - 获取大盘统计")
    void testGetSummary() {
        EvaluationSummaryDto summary = new EvaluationSummaryDto(
                10,
                5,
                0.91,
                new EvaluationMetrics(0.9, 0.9, 0.9, 0.9, 1.0),
                List.of(),
                Map.of("RAG", 3),
                List.of(),
                List.of());

        when(service.getSummary()).thenReturn(summary);

        client.get()
                .uri("/api/evaluation/summary")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EvaluationSummaryDto.class)
                .value(s -> assertThat(s.totalEvaluations()).isEqualTo(10));
    }

    @Test
    @DisplayName("PUT /api/evaluation/results/{id}/annotate - 人工标注打分")
    void testAnnotateResult() {
        EvaluationResultDto updated = new EvaluationResultDto(
                "res-1",
                "bench-1",
                "标题",
                "p",
                "m",
                "jp",
                "jm",
                "prompt",
                "resp",
                "exp",
                new EvaluationMetrics(0.8, 0.8, 0.8, 0.8, 1.0),
                "fb",
                200L,
                100,
                0.95,
                "人工复核",
                System.currentTimeMillis());

        when(service.annotateResult("res-1", 0.95, "人工复核")).thenReturn(Optional.of(updated));

        client.put()
                .uri("/api/evaluation/results/res-1/annotate")
                .bodyValue(new EvaluationRequests.HumanAnnotationRequest(0.95, "人工复核"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(EvaluationResultDto.class)
                .value(r -> assertThat(r.humanScore()).isEqualTo(0.95));
    }
}
