package xyz.ppmblszdp.ai.dto;

/**
 * 标题生成请求。
 *
 * <p>{@code message} 为用户问题，{@code answer} 为 AI 回答；provider/model 可空，空时回落到默认模型。
 */
public record TitleRequest(String message, String answer, String provider, String model, String conversationId) {}
