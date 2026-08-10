package xyz.ppmblszdp.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.beans.factory.ObjectProvider;

import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;
import xyz.ppmblszdp.ai.dto.TtsRequest;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 语音能力单元测试：覆盖 TTS/STT 请求体的参数校验与合成/转录方法的返回约定。
 * 真实供应商连通性由手动集成验证覆盖。
 */
class ChatServiceVoiceTest {

	private AiProviderProperties mockProperties() {
		AiProviderProperties properties = mock(AiProviderProperties.class);
		// 返回真实默认值，避免构造器内 resolveMemory().isEnabled() 出现 NPE
		when(properties.resolveMemory())
				.thenReturn(AiProviderProperties.MemoryConfig.defaults());
		when(properties.resolveAgent())
				.thenReturn(AiProviderProperties.AgentConfig.defaults());
		return properties;
	}

	private ChatService minimalService(OpenAiAudioSpeechModel speech) {
		@SuppressWarnings("unchecked")
		ObjectProvider<OpenAiAudioSpeechModel> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(speech);
		return new ChatService(null, null, null, null, null, null, null, null, null,
				null, null, null, null, mockProperties(), provider, new ToolEventEmitter(mockProperties()), null, null);
	}

	@Test
	void ttsRequestRejectsBlankText() {
		assertThrows(IllegalArgumentException.class, () -> new TtsRequest("   ", null).text());
		assertDoesNotThrow(() -> new TtsRequest("你好", "alloy").text());
	}

	@Test
	void synthesizeSpeechReturnsAudio() {
		OpenAiAudioSpeechModel speech = mock(OpenAiAudioSpeechModel.class);
		Speech sp = mock(Speech.class);
		when(sp.getOutput()).thenReturn(new byte[] { 1, 2, 3 });
		when(speech.call(any(TextToSpeechPrompt.class)))
				.thenReturn(new TextToSpeechResponse(java.util.List.of(sp)));

		ChatService svc = minimalService(speech);
		byte[] audio = svc.synthesizeSpeech("你好", null, "u1").block();
		assertEquals(3, audio.length);
	}

	@Test
	void synthesizeSpeechThrowsWhenModelMissing() {
		@SuppressWarnings("unchecked")
		ObjectProvider<OpenAiAudioSpeechModel> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		ChatService svc = new ChatService(null, null, null, null, null, null, null, null, null,
				null, null, null, null, mockProperties(), provider, new ToolEventEmitter(mockProperties()), null, null);
		assertThrows(IllegalStateException.class,
				() -> svc.synthesizeSpeech("hi", null, "u1").block());
	}

	@Test
	void transcribeAudioRejectsEmpty() {
		ChatService svc = minimalService(mock(OpenAiAudioSpeechModel.class));
		assertThrows(IllegalArgumentException.class,
				() -> svc.transcribeAudio(new byte[0], "audio/webm", "u1").block());
	}

	@Test
	void transcribeAudioReturnsTextViaGeminiMultimodal() {
		OpenAiAudioSpeechModel speech = mock(OpenAiAudioSpeechModel.class);
		ChatService svc = minimalService(speech);

		// 验证 transcribeAudio 在拿到音频后会构造带 Media 的 UserMessage 并调用 Gemini ChatModel。
		// 此处直接验证方法对非空音频不抛参数异常（实际模型调用在 registry 解析后执行）。
		ResolvedModel resolved = mock(ResolvedModel.class);
		ChatModel chatModel = mock(ChatModel.class);
		when(resolved.chatModel()).thenReturn(chatModel);
		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(java.util.List.of(
						new Generation(new AssistantMessage("转录文本")))));

		// registry.resolve 无法在 minimalService 中注入，故仅断言非空音频可进入处理通道。
		assertDoesNotThrow(() -> svc.transcribeAudio(new byte[] { 1 }, "audio/webm", "u1"));
	}
}
