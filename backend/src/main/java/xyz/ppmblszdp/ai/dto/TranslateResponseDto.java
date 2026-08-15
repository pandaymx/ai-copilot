package xyz.ppmblszdp.ai.dto;

/**
 * 多语言翻译响应 DTO。
 *
 * @param originalText         原始输入文本
 * @param sourceLang           请求指定的源语言（如 auto 或 en）
 * @param targetLang           请求的目标语言（如 zh-CN）
 * @param detectedLang         智能探测出的实际源语言（如 en, zh-CN, ja, ko 等）
 * @param translatedText       最终翻译后的文本
 * @param glossaryAppliedCount 命中的术语表词条数量
 * @param latencyMs            翻译总耗时（毫秒）
 */
public record TranslateResponseDto(
        String originalText,
        String sourceLang,
        String targetLang,
        String detectedLang,
        String translatedText,
        int glossaryAppliedCount,
        long latencyMs) {}
