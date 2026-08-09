package xyz.ppmblszdp.ai.dto;

/**
 * 文本转语音（TTS）请求体。
 *
 * @param text  待合成的文本（必填，非空）
 * @param voice 语音名（可选；缺省回落服务端默认 voice）。保留给兼容 OpenAI 的供应商，
 *              例如 {@code alloy}/{@code echo}/{@code fable}；对接 Gemini TTS 时若模型不识别则被忽略。
 */
public record TtsRequest(
		String text,
		String voice
) {
	public String text() {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("text 不能为空");
		}
		return text.trim();
	}

	public String voice() {
		return (voice != null && !voice.isBlank()) ? voice.trim() : null;
	}
}
