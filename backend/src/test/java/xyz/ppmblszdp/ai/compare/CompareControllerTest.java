package xyz.ppmblszdp.ai.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.compare.controller.CompareController;
import xyz.ppmblszdp.ai.compare.dto.CompareChunkDto;
import xyz.ppmblszdp.ai.compare.dto.CompareRequest;
import xyz.ppmblszdp.ai.compare.dto.CompareResponseDto;
import xyz.ppmblszdp.ai.compare.service.ModelCompareService;
import xyz.ppmblszdp.ai.identity.AuthProperties;

class CompareControllerTest {

    private ModelCompareService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(ModelCompareService.class);
        AuthProperties auth = new AuthProperties("dev", "X-User-Id", java.util.Set.of("admin"));
        CompareController controller = new CompareController(service, auth);
        client = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("POST /api/chat/compare - 并行非流式多模型比对返回结果")
    void testCompareNonStreaming() {
        CompareResponseDto.ModelCompareResult res1 = new CompareResponseDto.ModelCompareResult(
                0, "openai", "gpt-4o", "回答 1", null, 200L, 800L, 35.0, 28, null);
        CompareResponseDto.ModelCompareResult res2 = new CompareResponseDto.ModelCompareResult(
                1, "deepseek", "deepseek-chat", "回答 2", null, 150L, 600L, 50.0, 30, null);

        CompareResponseDto responseDto =
                new CompareResponseDto("请解释并发模型", System.currentTimeMillis(), List.of(res1, res2));

        when(service.compare(any(), anyString())).thenReturn(Mono.just(responseDto));

        CompareRequest req = new CompareRequest(
                "请解释并发模型",
                List.of(
                        new CompareRequest.ModelTarget("openai", "gpt-4o"),
                        new CompareRequest.ModelTarget("deepseek", "deepseek-chat")),
                null,
                null,
                null);

        client.post()
                .uri("/api/chat/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CompareResponseDto.class)
                .value(res -> {
                    assertThat(res.prompt()).isEqualTo("请解释并发模型");
                    assertThat(res.results()).hasSize(2);
                    assertThat(res.results().get(0).provider()).isEqualTo("openai");
                    assertThat(res.results().get(1).provider()).isEqualTo("deepseek");
                });
    }

    @Test
    @DisplayName("POST /api/chat/compare/stream - SSE 多路复用并行流式下发分帧")
    void testCompareStreaming() {
        CompareChunkDto chunk1 = CompareChunkDto.text(0, "openai", "gpt-4o", "Hello from GPT");
        CompareChunkDto chunk2 = CompareChunkDto.text(1, "deepseek", "deepseek-chat", "Hello from DeepSeek");
        CompareChunkDto metric1 = CompareChunkDto.metrics(0, "openai", "gpt-4o", 180L, 500L, 40.0, 20);

        when(service.streamCompare(any(), anyString())).thenReturn(Flux.just(chunk1, chunk2, metric1));

        CompareRequest req = new CompareRequest(
                "测试流式",
                List.of(
                        new CompareRequest.ModelTarget("openai", "gpt-4o"),
                        new CompareRequest.ModelTarget("deepseek", "deepseek-chat")),
                null,
                null,
                null);

        client.post()
                .uri("/api/chat/compare/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }
}
