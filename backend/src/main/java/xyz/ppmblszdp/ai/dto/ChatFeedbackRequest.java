package xyz.ppmblszdp.ai.dto;

/**
 * 点赞/点踩反馈请求 DTO。
 *
 * @param conversationId  会话 ID（可空）
 * @param messageId       消息 ID（可空）
 * @param rating          反馈类型 ("THUMBS_UP" | "THUMBS_DOWN")
 * @param comment         可选评论说明
 * @param userId          用户标识（默认 "default_user"）
 * @param modelId         被评价消息使用的模型 ID（可空，用于满意度排名）
 * @param intent          意图类型字符串（来自 IntentClassifier，可空）
 * @param userPrompt      用户原始提问（前端截断至 2,000 字符，后端以 TEXT 无损存储）
 * @param assistantReply  AI 助手回答摘要（前端截断至 2,000 字符，后端以 TEXT 无损存储）
 */
public record ChatFeedbackRequest(
        String conversationId,
        String messageId,
        String rating,
        String comment,
        String userId,
        String modelId,
        String intent,
        String userPrompt,
        String assistantReply) {
    /**
     * 解析当前用户 id。
     *
     * @deprecated 服务端身份已从受信任 {@code X-User-Id} Header 解析。仅保留作 dev 模式 fallback。
     */
    @Deprecated(since = "auth-refactor", forRemoval = false)
    public String resolveUserId() {
        return (userId != null && !userId.isBlank()) ? userId : "default_user";
    }
}
