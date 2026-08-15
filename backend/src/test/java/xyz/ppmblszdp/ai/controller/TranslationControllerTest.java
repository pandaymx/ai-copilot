package xyz.ppmblszdp.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import xyz.ppmblszdp.ai.dto.TranslateRequestDto;
import xyz.ppmblszdp.ai.dto.TranslateResponseDto;
import xyz.ppmblszdp.ai.service.TranslationService;

class TranslationControllerTest {

    private TranslationService translationService;
    private TranslationController controller;
    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        translationService = mock(TranslationService.class);
        controller = new TranslationController(translationService);
        webClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("POST /api/translate 成功执行并返回翻译响应")
    void testTranslateEndpoint() {
        when(translationService.translate(any(TranslateRequestDto.class)))
                .thenReturn(new TranslateResponseDto("Hello world", "en", "zh-CN", "en", "你好，世界", 0, 30));

        webClient
                .post()
                .uri("/api/translate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"Hello world\",\"targetLang\":\"zh-CN\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.translatedText")
                .isEqualTo("你好，世界")
                .jsonPath("$.sourceLang")
                .isEqualTo("en")
                .jsonPath("$.targetLang")
                .isEqualTo("zh-CN")
                .jsonPath("$.detectedLang")
                .isEqualTo("en");
    }

    @Test
    @DisplayName("GET /api/translate/languages 返回支持语种字典列表")
    void testGetSupportedLanguagesEndpoint() {
        when(translationService.getSupportedLanguages())
                .thenReturn(List.of(
                        Map.of("code", "zh-CN", "name", "简体中文", "nativeName", "简体中文"),
                        Map.of("code", "en", "name", "英语", "nativeName", "English")));

        webClient
                .get()
                .uri("/api/translate/languages")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].code")
                .isEqualTo("zh-CN")
                .jsonPath("$[1].code")
                .isEqualTo("en");
    }
}
