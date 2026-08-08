package xyz.ppmblszdp.ai.dto;

/**
 * 点赞/点踩反馈请求 DTO。
 *
 * @param conversationId 会话 ID（可空）
 * @param messageId      消息 ID（可空）
 * @param rating         反馈类型 ("THUMBS_UP" | "THUMBS_DOWN")
 * @param comment        可选评论说明
 * @param userId         用户标识（默认 "default_user"）
 */
public record ChatFeedbackRequest(
		String conversationId,
		String messageId,
		String rating,
		String comment,
		String userId
) {
	public String resolveUserId() {
		return (userId != null && !userId.isBlank()) ? userId : "default_user";
	}
}
