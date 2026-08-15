package xyz.ppmblszdp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import xyz.ppmblszdp.ai.dto.TranslateRequestDto;
import xyz.ppmblszdp.ai.dto.TranslateResponseDto;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class TranslationServiceTest {

    private ProviderRegistry providerRegistry;
    private ChatModel chatModel;
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        providerRegistry = mock(ProviderRegistry.class);
        chatModel = mock(ChatModel.class);
        translationService = new TranslationService(providerRegistry);

        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("mock-provider")
                .displayName("Mock Provider")
                .chatModel(chatModel)
                .defaultModelId("mock-model")
                .build();
        ModelDescriptor model = ModelDescriptor.builder()
                .id("mock-model")
                .modelName("mock-model")
                .build();
        ResolvedModel resolved = new ResolvedModel(chatModel, provider, model);
        when(providerRegistry.resolve(any(), any())).thenReturn(resolved);
    }

    @Test
    @DisplayName("语种智能探测：准确识别 CJK中文、日文假名、韩文谚文、西里尔俄文与拉丁英文")
    void testDetectLanguage() {
        assertEquals("zh-CN", translationService.detectLanguage("这是一个关于人工智能与大模型微调的专业技术报告。"));
        assertEquals("ja", translationService.detectLanguage("こんにちは、最新のシステムアーキテクチャについて教えてください。"));
        assertEquals("ko", translationService.detectLanguage("안녕하세요, 스프링 AI 프레임워크를 활용한 개발 가이드입니다."));
        assertEquals(
                "ru",
                translationService.detectLanguage("Привет, это руководство по разработке на Java и Spring Boot."));
        assertEquals(
                "en",
                translationService.detectLanguage(
                        "Hello world, this is a comprehensive guide to building AI copilots."));
        assertEquals("unknown", translationService.detectLanguage(""));
        assertEquals("unknown", translationService.detectLanguage("   \n\t "));
    }

    @Test
    @DisplayName("格式保护与占位符提取与还原：代码块、行内代码与 LaTeX 公式无损隔离")
    void testFormatPreservation() {
        String complexDoc = """
                # 架构设计说明

                请查看核心方法：
                ```java
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                ```

                执行命令为 `bun run build`，损失函数定义为 $$ \\mathcal{L} = \\sum_{i=1}^n (y_i - \\hat{y}_i)^2 $$，其中学习率参数为 $\\alpha = 0.001$。
                """;

        var protectedContent = translationService.extractProtectedPlaceholders(complexDoc);
        String processed = protectedContent.processedText();

        // 验证原代码与公式已被替换为安全占位符
        assertFalse(processed.contains("public static void main"));
        assertFalse(processed.contains("\\mathcal{L}"));
        assertFalse(processed.contains("bun run build"));
        assertTrue(processed.contains("__PROTECTED_CODE_BLOCK_0__"));
        assertTrue(processed.contains("__PROTECTED_MATH_DISPLAY_1__"));
        assertTrue(processed.contains("__PROTECTED_MATH_INLINE_2__"));
        assertTrue(processed.contains("__PROTECTED_INLINE_CODE_3__"));

        // 模拟 LLM 翻译（占位符保持不动）
        String mockTranslated = """
                # Architecture Design Document

                Please check the core method:
                __PROTECTED_CODE_BLOCK_0__

                The execution command is __PROTECTED_INLINE_CODE_3__, and the loss function is defined as __PROTECTED_MATH_DISPLAY_1__, where the learning rate parameter is __PROTECTED_MATH_INLINE_2__.
                """;

        String restored =
                translationService.restoreProtectedPlaceholders(mockTranslated, protectedContent.placeholders());

        // 验证回填后原代码与数学公式完全恢复
        assertTrue(restored.contains("public static void main"));
        assertTrue(restored.contains("bun run build"));
        assertTrue(restored.contains("$$ \\mathcal{L} = \\sum_{i=1}^n (y_i - \\hat{y}_i)^2 $$"));
        assertTrue(restored.contains("$\\alpha = 0.001$"));
    }

    @Test
    @DisplayName("翻译请求：源语言与目标语言相同时快速短路返回")
    void testTranslateFastPathSameLanguage() {
        TranslateRequestDto req = new TranslateRequestDto("Hello world", "en", "en", Map.of(), null, null, true);

        TranslateResponseDto resp = translationService.translate(req);
        assertEquals("Hello world", resp.translatedText());
        assertEquals("en", resp.sourceLang());
        assertEquals("en", resp.targetLang());
        assertEquals(0, resp.glossaryAppliedCount());
    }

    @Test
    @DisplayName("完整翻译流程：调用 LLM、注入术语表并统计命中数")
    void testTranslateFullFlowWithGlossary() {
        String input = "Spring AI is an awesome framework for building intelligent LLM applications.";
        String mockLlmOutput = "Spring AI 框架 是构建智能 大语言模型 应用的卓越框架。";

        ChatResponse mockResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(mockLlmOutput))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        Map<String, String> glossary = Map.of(
                "Spring AI", "Spring AI 框架",
                "LLM", "大语言模型");

        TranslateRequestDto req =
                new TranslateRequestDto(input, "zh-CN", "auto", glossary, "mock-provider", "mock-model", true);

        TranslateResponseDto resp = translationService.translate(req);

        assertEquals("zh-CN", resp.targetLang());
        assertEquals("en", resp.detectedLang());
        assertEquals("en", resp.sourceLang());
        assertEquals(mockLlmOutput, resp.translatedText());
        assertEquals(2, resp.glossaryAppliedCount());
        assertTrue(resp.latencyMs() >= 0);
    }

    @Test
    @DisplayName("获取标准支持语言列表")
    void testGetSupportedLanguages() {
        var languages = translationService.getSupportedLanguages();
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertTrue(languages.stream().anyMatch(l -> "zh-CN".equals(l.get("code"))));
        assertTrue(languages.stream().anyMatch(l -> "en".equals(l.get("code"))));
        assertTrue(languages.stream().anyMatch(l -> "ja".equals(l.get("code"))));
    }
}
