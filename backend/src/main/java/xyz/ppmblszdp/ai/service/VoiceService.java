package xyz.ppmblszdp.ai.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 语音处理专用服务（TTS 语音合成与 STT 语音识别转写）。
 *
 * <p>单向无环依赖叶子服务，不依赖 ChatService / ChatOrchestrator。
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private final ObjectProvider<OpenAiAudioSpeechModel> speechModelProvider;
    private final ProviderRegistry registry;

    public VoiceService(ObjectProvider<OpenAiAudioSpeechModel> speechModelProvider, ProviderRegistry registry) {
        this.speechModelProvider = speechModelProvider;
        this.registry = registry;
    }

    /** 文本转语音（TTS）。 */
    public Mono<byte[]> synthesizeSpeech(String text, String voice, String userId) {
        OpenAiAudioSpeechModel speechModel = speechModelProvider.getIfAvailable();
        if (speechModel == null) {
            return Mono.error(new IllegalStateException("TTS 模型不可用：未启用 spring.ai.model.audio.speech=openai"));
        }
        return Mono.fromCallable(() -> {
                    OpenAiAudioSpeechOptions.Builder optsBuilder = OpenAiAudioSpeechOptions.builder()
                            .responseFormat("mp3")
                            .speed(1.0);
                    if (voice != null && !voice.isBlank()) {
                        optsBuilder.voice(voice.trim());
                    }
                    TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, optsBuilder.build());
                    byte[] audio = speechModel.call(prompt).getResult().getOutput();
                    if (audio == null || audio.length == 0) {
                        throw new IllegalStateException("TTS 合成返回空音频");
                    }
                    log.info("TTS 合成完成 → 用户={}, 字符数={}, 字节数={}", userId, text.length(), audio.length);
                    return audio;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 语音转文本（STT）：复用 Gemini 多模态 ChatModel 精准转录。 */
    public Mono<String> transcribeAudio(byte[] audioBytes, String mimeType, String userId) {
        if (audioBytes == null || audioBytes.length == 0) {
            return Mono.error(new IllegalArgumentException("音频数据为空"));
        }
        final String rawMime = (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : "audio/webm";

        return Mono.fromCallable(() -> {
                    ResolvedModel resolved = registry.resolve(null, null);
                    if (resolved == null || resolved.chatModel() == null) {
                        throw new IllegalStateException("无可用 ChatModel 进行语音识别");
                    }

                    org.springframework.util.MimeType resolvedMime = MimeTypeUtils.parseMimeType(rawMime);
                    Media audioMedia = new Media(resolvedMime, new ByteArrayResource(audioBytes));
                    UserMessage userMsg = UserMessage.builder()
                            .text("请将这段音频精准转写为文本。不要添加任何引言、解释、标点修饰或总结，仅输出转写后的文字内容本身。")
                            .media(List.of(audioMedia))
                            .build();
                    Prompt prompt = new Prompt(List.of(userMsg), ChatOptionsFactory.forProvider(resolved, 0.0));

                    ChatResponse response = resolved.chatModel().call(prompt);
                    if (response == null
                            || response.getResult() == null
                            || response.getResult().getOutput() == null) {
                        throw new IllegalStateException("语音识别服务未能生成有效文本");
                    }
                    String text = response.getResult().getOutput().getText();
                    String cleaned = text != null ? text.trim() : "";
                    log.info(
                            "语音识别完成 → 用户={}, 字节数={}, MIME={}, 结果文本长度={}",
                            userId,
                            audioBytes.length,
                            rawMime,
                            cleaned.length());
                    return cleaned;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
