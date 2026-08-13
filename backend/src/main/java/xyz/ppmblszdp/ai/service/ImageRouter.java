package xyz.ppmblszdp.ai.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ImageGenerationRequestDto;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 图像意图识别与图像生成流式路由服务。
 *
 * <p>单向无环依赖叶子服务，不依赖 ChatService / ChatOrchestrator。
 */
@Service
public class ImageRouter {

    private static final Logger log = LoggerFactory.getLogger(ImageRouter.class);

    private final ObjectProvider<ImageGenerationService> imageGenerationServiceProvider;
    private final AiProviderProperties properties;

    public ImageRouter(
            ObjectProvider<ImageGenerationService> imageGenerationServiceProvider, AiProviderProperties properties) {
        this.imageGenerationServiceProvider = imageGenerationServiceProvider;
        this.properties = properties;
    }

    /** 图像意图判定与提炼出的提示词 DTO 记录。 */
    public record ImageIntentResult(boolean isImage, String prompt) {}

    public ImageGenerationService getAvailableImageService() {
        return imageGenerationServiceProvider != null ? imageGenerationServiceProvider.getIfAvailable() : null;
    }

    /**
     * 判断请求是否具有图像生成意图。
     *
     * <ol>
     *   <li>快速斜杠命令检测 (`/image`, `/img`)</li>
     *   <li>轻量关键字信号拦截 (无绘图信号词则瞬间返回 false，不浪费 LLM 资源)</li>
     *   <li>LLM 智能分类与提示词提炼</li>
     * </ol>
     */
    public ImageIntentResult detectImageIntent(ChatRequest request, ResolvedModel resolved) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return new ImageIntentResult(false, "");
        }
        String msg = request.message().trim();

        // 1. 显式命令直接触发快速响应 (/image ... 或 /img ...)
        if (msg.startsWith("/image ") || msg.startsWith("/img ")) {
            return new ImageIntentResult(true, extractImagePrompt(request));
        }
        if (msg.startsWith("/image") || msg.startsWith("/img")) {
            return new ImageIntentResult(true, extractImagePrompt(request));
        }

        // 2. 判定文本中是否带有图片创作相关的信号词（避免无相关信号词的普通问答额外调用 LLM）
        List<String> keywords = properties != null && properties.resolveImage() != null
                ? properties.resolveImage().resolveKeywords()
                : AiProviderProperties.ImageConfig.defaults().resolveKeywords();
        String lower = msg.toLowerCase();
        boolean hasSignal = false;
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase())) {
                hasSignal = true;
                break;
            }
        }

        if (!hasSignal) {
            return new ImageIntentResult(false, "");
        }

        // 3. 调用 LLM 进行智能意图识别与提示词提炼
        try {
            if (resolved != null && resolved.chatModel() != null) {
                String systemPrompt = """
						你是一个意图识别助手。判断用户的文本请求是否表达了【希望 AI 绘图/生成图片/生成海报/生成插画/生成照片】的意图。
						注意：若用户仅要求画架构图、流程图、代码、文字描述等，请判定为 false。
						请严格返回如下 JSON 格式，不要加入 ```json 标记：
						{"isImage": true, "prompt": "提炼出的生成图片的提示词"}
						或
						{"isImage": false, "prompt": ""}
						""";
                Prompt prompt = new Prompt(
                        List.of(new SystemMessage(systemPrompt), new UserMessage(msg)),
                        ChatOptionsFactory.forProvider(resolved, 0.1));

                ChatResponse response = resolved.chatModel().call(prompt);
                if (response != null
                        && response.getResult() != null
                        && response.getResult().getOutput() != null) {
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isBlank()) {
                        text = text.trim();
                        if (text.startsWith("```json")) {
                            text = text.substring(7);
                        }
                        if (text.startsWith("```")) {
                            text = text.substring(3);
                        }
                        if (text.endsWith("```")) {
                            text = text.substring(0, text.length() - 3);
                        }
                        text = text.trim();

                        boolean isImage = text.contains("\"isImage\": true") || text.contains("\"isImage\":true");
                        String extractedPrompt = msg;
                        int promptIdx = text.indexOf("\"prompt\"");
                        if (promptIdx != -1) {
                            int colonIdx = text.indexOf(':', promptIdx);
                            if (colonIdx != -1) {
                                int firstQuote = text.indexOf('"', colonIdx);
                                if (firstQuote != -1) {
                                    int secondQuote = text.indexOf('"', firstQuote + 1);
                                    if (secondQuote != -1) {
                                        extractedPrompt = text.substring(firstQuote + 1, secondQuote)
                                                .trim();
                                    }
                                }
                            }
                        }
                        if (isImage) {
                            return new ImageIntentResult(
                                    true, extractedPrompt.isBlank() ? extractImagePrompt(request) : extractedPrompt);
                        }
                        return new ImageIntentResult(false, "");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM 图像意图识别失败，回退至规则校验: {}", e.getMessage());
        }

        // 4. LLM 异常时的降级规则校验
        boolean isReq = isImageGenerationRequestByRule(msg, keywords);
        return new ImageIntentResult(isReq, isReq ? extractImagePrompt(request) : "");
    }

    public boolean isImageGenerationRequestByRule(String msg, List<String> keywords) {
        String lower = msg.toLowerCase();
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && lower.startsWith(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public String extractImagePrompt(ChatRequest request) {
        String msg = request.message().trim();
        if (msg.startsWith("/image ")) {
            return msg.substring(7).trim();
        }
        if (msg.startsWith("/img ")) {
            return msg.substring(5).trim();
        }
        if (msg.startsWith("/image") || msg.startsWith("/img")) {
            int spaceIdx = msg.indexOf(' ');
            return spaceIdx > 0 ? msg.substring(spaceIdx + 1).trim() : msg;
        }
        List<String> keywords = properties != null && properties.resolveImage() != null
                ? properties.resolveImage().resolveKeywords()
                : AiProviderProperties.ImageConfig.defaults().resolveKeywords();
        String lower = msg.toLowerCase();
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank()) {
                String kwLower = kw.toLowerCase();
                if (lower.startsWith(kwLower)) {
                    String extracted = msg.substring(kw.length()).trim();
                    if (extracted.startsWith(":") || extracted.startsWith("：")) {
                        extracted = extracted.substring(1).trim();
                    }
                    return extracted.isBlank() ? msg : extracted;
                }
            }
        }
        return msg;
    }

    /** 执行图像生成并输出 SSE Chunk 流。 */
    public Flux<ChatChunkDto> streamImageGeneration(
            ChatRequest request, String userId, ImageGenerationService imgService, String customPrompt) {
        String rawPrompt =
                (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : extractImagePrompt(request);
        final String prompt = rawPrompt.isBlank() ? "一只可爱的卡通小猫" : rawPrompt;
        String artifactId = "img-" + UUID.randomUUID().toString().substring(0, 8);

        ChatChunkDto textChunk = ChatChunkDto.content("正在为你生成图片：" + prompt + "\n\n");
        ChatChunkDto processingChunk = ChatChunkDto.artifact(
                artifactId, "image", "image", "正在生成图片: " + prompt, null, "processing", "image/png");

        ImageGenerationRequestDto genReq =
                new ImageGenerationRequestDto(prompt, request.provider(), request.model(), null, null, null, null);

        return Flux.just(textChunk, processingChunk)
                .concatWith(imgService
                        .generateImage(genReq)
                        .map(res -> ChatChunkDto.artifact(
                                artifactId, "image", "image", prompt, res.payload(), "complete", res.mimeType()))
                        .onErrorResume(
                                ex -> Mono.just(ChatChunkDto.error("IMAGE_GEN_FAILED", "图片生成失败: " + ex.getMessage()))));
    }
}
