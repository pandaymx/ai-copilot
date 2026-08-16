package xyz.ppmblszdp.ai.dto;

import jakarta.annotation.Nullable;

/**
 * 添加或更新 API Key 请求体。
 *
 * @param provider 供应商标识（如 openai / deepseek / anthropic / google / qwen）
 * @param apiKey   明文 API Key（若传入已脱敏 Key 则不更新密文）
 */
public record ApiKeySaveRequest(
        String provider, String apiKey, @Nullable String baseUrl) {}
