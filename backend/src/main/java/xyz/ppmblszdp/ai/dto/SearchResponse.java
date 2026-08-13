package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 聊天历史全文检索响应。
 *
 * <p>{@code query} 为回显的原始查询关键字；{@code results} 为命中的消息条目，
 * 按相关度（ts_rank）降序排列。
 */
public record SearchResponse(String query, List<SearchResultItem> results) {

    /**
     * 单条命中结果。
     *
     * @param sessionId 所属会话 ID（对应 spring_ai_chat_memory.conversation_id）
     * @param messageId 消息序号（对应 sequence_id，会话内唯一，可作为消息定位键）
     * @param role      消息角色/类型（USER / ASSISTANT / SYSTEM / TOOL）
     * @param snippet   经 ts_headline 生成的高亮片段，匹配词以 {@code <b>…</b>} 包裹
     * @param timestamp 消息时间戳（epoch millis）
     */
    public record SearchResultItem(String sessionId, long messageId, String role, String snippet, long timestamp) {}
}
