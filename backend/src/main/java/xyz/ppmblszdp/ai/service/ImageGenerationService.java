package xyz.ppmblszdp.ai.service;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import xyz.ppmblszdp.ai.dto.ImageGenerationRequestDto;
import xyz.ppmblszdp.ai.dto.ImageGenerationResultDto;
import xyz.ppmblszdp.ai.exception.AiException;
import xyz.ppmblszdp.ai.exception.ImageGenerationException;
import xyz.ppmblszdp.ai.rag.security.SsrfBlockedException;
import xyz.ppmblszdp.ai.rag.security.SsrfGuard;
import xyz.ppmblszdp.ai.registry.ImageModelRegistry;

/**
 * 图像生成服务。
 *
 * <p>封装 ImageModel 的分发与调用，并自动将供应商返回的临时 HTTP URL
 * 转换为持久化 / 自包含的 Base64 Data URI。
 *
 * <p>安全措施：
 * <ul>
 *   <li>SSRF 防护：下载供应商返回的 URL 前调用 {@link SsrfGuard#validate}，
 *       拦截内网/回环/元数据地址；</li>
 *   <li>超时控制：WebClient 配置 30s 响应超时 + 10s 连接超时，
 *       防止上游 URL 无响应时连接池耗尽。</li>
 * </ul>
 */
@Service
public class ImageGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationService.class);

    /** 图像下载响应超时 */
    private static final Duration DOWNLOAD_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    /** 图像下载连接超时 */
    private static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 图像下载最大体积（1MB） */
    private static final int MAX_IMAGE_BYTES = 1_048_576;

    private final ImageModelRegistry registry;
    private final WebClient webClient;

    public ImageGenerationService(ImageModelRegistry registry, WebClient.Builder webClientBuilder) {
        this.registry = registry;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(DOWNLOAD_RESPONSE_TIMEOUT)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)
                        DOWNLOAD_CONNECT_TIMEOUT.toMillis());
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 异步生成图片并返回 Base64 格式结果。
     */
    public Mono<ImageGenerationResultDto> generateImage(ImageGenerationRequestDto request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            return Mono.error(new ImageGenerationException("INVALID_ARGUMENT", "生成提示词 (prompt) 不能为空"));
        }

        String provider = request.provider();
        ImageModel model = registry.resolve(provider);
        if (model == null) {
            return Mono.error(new ImageGenerationException("PROVIDER_NOT_FOUND", "未找到可用的图像生成模型供应商: " + provider));
        }

        ImageOptions options = registry.buildOptions(
                provider, request.model(), request.width(), request.height(), request.quality(), request.style());

        log.info(
                "开始生成图片, prompt: '{}', provider: '{}', model: '{}'",
                request.prompt(),
                provider,
                options != null ? options.getModel() : null);

        return Mono.fromCallable(() -> {
                    ImagePrompt imagePrompt = new ImagePrompt(request.prompt(), options);
                    return model.call(imagePrompt);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::processImageResponse)
                .map(payload -> {
                    String artifactId = "img-" + UUID.randomUUID().toString().substring(0, 8);
                    return new ImageGenerationResultDto(
                            artifactId,
                            request.prompt(),
                            payload,
                            "image/png",
                            provider,
                            options != null ? options.getModel() : null);
                })
                .onErrorMap(
                        e -> !(e instanceof AiException),
                        e -> new ImageGenerationException("IMAGE_GEN_FAILED", "图像生成失败: " + e.getMessage()));
    }

    private Mono<String> processImageResponse(ImageResponse response) {
        if (response == null
                || response.getResults() == null
                || response.getResults().isEmpty()) {
            return Mono.error(new ImageGenerationException("IMAGE_GEN_FAILED", "上游供应商未返回图像数据"));
        }

        ImageGeneration gen = response.getResult();
        if (gen == null || gen.getOutput() == null) {
            return Mono.error(new ImageGenerationException("IMAGE_GEN_FAILED", "图像生成结果为空"));
        }

        String b64Json = gen.getOutput().getB64Json();
        if (b64Json != null && !b64Json.isBlank()) {
            if (b64Json.startsWith("data:")) {
                return Mono.just(b64Json);
            }
            return Mono.just("data:image/png;base64," + b64Json.trim());
        }

        String url = gen.getOutput().getUrl();
        if (url != null && !url.isBlank()) {
            log.info("检测到图像临时 URL，开始转换为 Base64 Data URI: {}", url);
            return downloadAndConvertToBase64(url);
        }

        return Mono.error(new ImageGenerationException("IMAGE_GEN_FAILED", "无法解析图像 Base64 或 URL 数据"));
    }

    /**
     * 下载供应商返回的临时图像 URL 并转换为 Base64 Data URI。
     *
     * <p>安全措施：
     * <ol>
     *   <li>SSRF 校验：调用 {@link SsrfGuard#validate} 拦截内网/回环/元数据地址；</li>
     *   <li>超时控制：WebClient 已配置 30s 响应超时 + 10s 连接超时；</li>
     *   <li>体积上限：限制下载内容不超过 1MB，防止内存耗尽。</li>
     * </ol>
     */
    private Mono<String> downloadAndConvertToBase64(String url) {
        try {
            SsrfGuard.validate(url);
        } catch (SsrfBlockedException e) {
            log.warn("[SSRF 防护] 拦截图像下载请求: url={} reason={}", url, e.getMessage());
            return Mono.error(new ImageGenerationException("SSRF_BLOCKED", "图像 URL 被安全策略拦截: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return Mono.error(new ImageGenerationException("INVALID_URL", "图像 URL 格式不合法: " + e.getMessage()));
        }

        return webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    if (bytes.length > MAX_IMAGE_BYTES) {
                        throw new ImageGenerationException(
                                "IMAGE_TOO_LARGE", "图像体积超过上限 (" + MAX_IMAGE_BYTES + " bytes), 实际: " + bytes.length);
                    }
                    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                })
                .onErrorResume(e -> {
                    log.warn("从临时 URL 下载图像失败，回落为原始 URL: {}", e.getMessage());
                    return Mono.just(url);
                });
    }
}
