package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 聊天请求体。向后兼容前端现有 {@code {message, history}} 契约。
 *
 * <ul>
 *   <li>{@code message} 必填；</li>
 *   <li>{@code history} 可选，缺省为空（前端已把当前消息 push 进 history 的情形由 ContextAssembler 去重）；</li>
 *   <li>{@code provider} / {@code model} 可选，缺省回落全局默认，确保前端不改动即可跑通；</li>
 *   <li>{@code systemPrompt} 可选，可用于本次请求覆盖系统提示词。</li>
 * </ul>
 *
 * @param message     当前用户输入（必填）
 * @param history     历史对话
 * @param provider    供应商 id（可选）
 * @param model       模型 id（可选）
 * @param systemPrompt 本次请求覆盖的系统提示词（可选）
 */
public record ChatRequest(
		String message,
		List<ChatMessageDto> history,
		String provider,
		String model,
		String systemPrompt
) {
	public List<ChatMessageDto> history() {
		return history == null ? List.of() : history;
	}

	public String message() {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message 不能为空");
		}
		return message;
	}
}
