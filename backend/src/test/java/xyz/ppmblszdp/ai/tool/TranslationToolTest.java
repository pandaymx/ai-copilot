package xyz.ppmblszdp.ai.tool;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import xyz.ppmblszdp.ai.dto.TranslateRequestDto;
import xyz.ppmblszdp.ai.dto.TranslateResponseDto;
import xyz.ppmblszdp.ai.service.TranslationService;

class TranslationToolTest {

    private TranslationService translationService;
    private TranslationTool translationTool;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        translationService = mock(TranslationService.class);
        translationTool = new TranslationTool(translationService);

        xyz.ppmblszdp.ai.config.AiProviderProperties props = mock(xyz.ppmblszdp.ai.config.AiProviderProperties.class);
        xyz.ppmblszdp.ai.config.AiProviderProperties.AgentConfig agentConfig =
                mock(xyz.ppmblszdp.ai.config.AiProviderProperties.AgentConfig.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

        ToolEventEmitter emitter = new ToolEventEmitter(props);
        var sink = emitter.newSink();

        java.util.Map<String, Object> ctxMap = new java.util.HashMap<>();
        ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
        ctxMap.put("eventSink", sink);
        toolContext = new ToolContext(ctxMap);
    }

    @Test
    @DisplayName("TranslationTool 工具调用与 JSON 结果解析")
    void testToolExecution() {
        when(translationService.translate(any(TranslateRequestDto.class)))
                .thenReturn(new TranslateResponseDto("Hello world", "en", "ja", "en", "こんにちは、世界", 1, 25));

        String result = translationTool.translate("Hello world", "ja", "en", "{\"Hello\":\"こんにちは\"}", toolContext);

        assertNotNull(result);
        assertTrue(result.contains("こんにちは、世界"));
        assertTrue(result.contains("\"targetLang\":\"ja\""));
        assertTrue(result.contains("\"sourceLang\":\"en\""));
    }
}
