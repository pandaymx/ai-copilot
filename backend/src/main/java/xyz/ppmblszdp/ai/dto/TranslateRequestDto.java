package xyz.ppmblszdp.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 多语言翻译请求 DTO。
 *
 * @param text               待翻译文本
 * @param targetLang         目标语言代码（如 zh-CN, en, ja, ko, fr, de, es, ru 等）
 * @param sourceLang         源语言代码（可选，默认 auto 自动检测）
 * @param glossary           专用术语表字典（可选，如 {"Spring AI": "Spring AI 框架", "LLM": "大语言模型"}）
 * @param provider           指定模型供应商（可选）
 * @param model              指定模型标识（可选）
 * @param preserveFormatting 是否开启格式保护（默认 true，提取并保留代码块与 LaTeX 公式）
 */
public record TranslateRequestDto(
        @NotBlank(message = "待翻译文本不能为空") String text,
        @NotBlank(message = "目标语言不能为空") String targetLang,
        String sourceLang,
        Map<String, String> glossary,
        String provider,
        String model,
        Boolean preserveFormatting) {}
