package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 结构化 SSE 传输帧 DTO。
 *
 * @param type           帧类型："conversation" | "content" | "reasoning" | "usage" | "error" | "done"
 * @param conversationId 会话 ID（仅 conversation 类型有）
 * @param content        增量文本内容（仅 content 类型有）
 * @param reasoning      推理解析/思考过程增量（仅 reasoning 类型有）
 * @param usage          Token 用量及费用信息（仅 usage / done 类型有）
 * @param code           错误代码（仅 error 类型有）
 * @param message        错误信息（仅 error 类型有）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatChunkDto(
		String type,
		String conversationId,
		String content,
		String reasoning,
		UsageDto usage,
		String code,
		String message
) {
	public record UsageDto(
			int promptTokens,
			int completionTokens,
			int totalTokens,
			Double estimatedCostRmb
	) {}

	public static ChatChunkDto conversation(String conversationId) {
		return new ChatChunkDto("conversation", conversationId, null, null, null, null, null);
	}

	public static ChatChunkDto content(String content) {
		return new ChatChunkDto("content", null, content, null, null, null, null);
	}

	public static ChatChunkDto reasoning(String reasoning) {
		return new ChatChunkDto("reasoning", null, null, reasoning, null, null, null);
	}

	public static ChatChunkDto usage(UsageDto usage) {
		return new ChatChunkDto("usage", null, null, null, usage, null, null);
	}

	public static ChatChunkDto error(String code, String message) {
		return new ChatChunkDto("error", null, null, null, null, code, message);
	}

	public static ChatChunkDto done() {
		return new ChatChunkDto("done", null, null, null, null, null, null);
	}

	public static ChatChunkDto done(UsageDto usage) {
		return new ChatChunkDto("done", null, null, null, usage, null, null);
	}
}
