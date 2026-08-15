package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.dto.TranslateRequestDto;
import xyz.ppmblszdp.ai.dto.TranslateResponseDto;
import xyz.ppmblszdp.ai.service.TranslationService;

/**
 * Agent 智能体多语言翻译工具：
 * 提供代码保留、Markdown/LaTeX 结构无损恢复及专业术语表映射的多语种翻译能力。
 */
@Component
public class TranslationTool {

    private static final Logger log = LoggerFactory.getLogger(TranslationTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TranslationService translationService;

    public TranslationTool(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Tool(description = "多语言翻译引擎：自动检测源语言并高保真翻译为目标语言，完整保留 Markdown 格式、代码块与 LaTeX 公式，支持行业术语表字典映射")
    public String translate(
            @ToolParam(description = "待翻译的文本内容") String text,
            @ToolParam(
                            description =
                                    "目标语言代码，例如 zh-CN(简体中文), en(英语), ja(日语), ko(韩语), fr(法语), de(德语), es(西班牙语), ru(俄语) 等")
                    String targetLang,
            @ToolParam(description = "源语言代码（可选，默认 auto 自动检测），例如 auto, en, zh-CN, ja 等", required = false)
                    String sourceLang,
            @ToolParam(
                            description =
                                    "专用术语表映射 JSON 字符串（可选，例如 {\"LLM\": \"大语言模型\", \"Spring AI\": \"Spring AI 框架\"}）",
                            required = false)
                    String glossaryJson,
            ToolContext toolContext) {

        String argsJson = buildArgsJson(text, targetLang, sourceLang, glossaryJson);

        return ToolEventEmitter.from(toolContext).executeWithEvent("translation", argsJson, toolContext, () -> {
            Map<String, String> glossary = parseGlossary(glossaryJson);
            TranslateRequestDto request =
                    new TranslateRequestDto(text, targetLang, sourceLang, glossary, null, null, true);

            TranslateResponseDto response = translationService.translate(request);
            try {
                return MAPPER.writeValueAsString(Map.of(
                        "sourceLang", response.sourceLang(),
                        "targetLang", response.targetLang(),
                        "detectedLang", response.detectedLang(),
                        "translatedText", response.translatedText(),
                        "glossaryAppliedCount", response.glossaryAppliedCount(),
                        "latencyMs", response.latencyMs()));
            } catch (Exception e) {
                return "{\"translatedText\":\"" + response.translatedText().replace("\"", "\\\"") + "\"}";
            }
        });
    }

    private Map<String, String> parseGlossary(String glossaryJson) {
        if (glossaryJson == null || glossaryJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(glossaryJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("解析术语表 JSON 失败，忽略术语约束: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String buildArgsJson(String text, String targetLang, String sourceLang, String glossaryJson) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "text", text != null ? text : "",
                    "targetLang", targetLang != null ? targetLang : "zh-CN",
                    "sourceLang", sourceLang != null ? sourceLang : "auto",
                    "glossaryJson", glossaryJson != null ? glossaryJson : ""));
        } catch (Exception e) {
            return "{}";
        }
    }
}
