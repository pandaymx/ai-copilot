package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 结构化 SSE 传输帧 DTO。
 *
 * @param type           帧类型："conversation" | "content" | "error" | "done"
 * @param conversationId 会话 ID（仅 conversation 类型有）
 * @param content        增量文本内容（仅 content 类型有）
 * @param code           错误代码（仅 error 类型有）
 * @param message        错误信息（仅 error 类型有）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatChunkDto(
		String type,
		String conversationId,
		String content,
		String code,
		String message
) {
	public static ChatChunkDto conversation(String conversationId) {
		return new ChatChunkDto("conversation", conversationId, null, null, null);
	}

	public static ChatChunkDto content(String content) {
		return new ChatChunkDto("content", null, content, null, null);
	}

	public static ChatChunkDto error(String code, String message) {
		return new ChatChunkDto("error", null, null, code, message);
	}

	public static ChatChunkDto done() {
		return new ChatChunkDto("done", null, null, null, null);
	}
}
