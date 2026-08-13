package xyz.ppmblszdp.ai.dto;

/**
 * 标题生成响应。{@code title} 为生成后的会话标题，失败/降级时为空字符串。
 */
public record TitleResponse(String title) {}
