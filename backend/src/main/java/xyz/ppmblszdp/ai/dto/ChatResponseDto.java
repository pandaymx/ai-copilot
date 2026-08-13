package xyz.ppmblszdp.ai.dto;

/**
 * 非流式聊天响应。
 *
 * @param content       完整回复文本
 * @param provider      实际命中的供应商 id
 * @param model         实际命中的模型 id
 * @param conversationId 会话 id（记忆驱动时由后端生成/透传，便于前端串联多轮）
 * @param usage         token 用量统计（可选，可空）
 * @param finishReason  结束原因（可选，可空）
 */
public record ChatResponseDto(
		String content,
		String provider,
		String model,
		String conversationId,
		Object usage,
		String finishReason,
		Boolean isFallback,
		String intent,
		String intentLabel
) {
	public ChatResponseDto(
			String content,
			String provider,
			String model,
			String conversationId,
			Object usage,
			String finishReason
	) {
		this(content, provider, model, conversationId, usage, finishReason, false, null, null);
	}

	public ChatResponseDto(
			String content,
			String provider,
			String model,
			String conversationId,
			Object usage,
			String finishReason,
			Boolean isFallback
	) {
		this(content, provider, model, conversationId, usage, finishReason, isFallback, null, null);
	}
}
