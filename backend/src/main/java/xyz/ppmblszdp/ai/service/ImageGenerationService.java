package xyz.ppmblszdp.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.ImageGenerationRequestDto;
import xyz.ppmblszdp.ai.dto.ImageGenerationResultDto;
import xyz.ppmblszdp.ai.exception.AiException;
import xyz.ppmblszdp.ai.exception.ImageGenerationException;
import xyz.ppmblszdp.ai.registry.ImageModelRegistry;

import java.util.Base64;
import java.util.UUID;

/**
 * 图像生成服务。
 *
 * <p>封装 ImageModel 的分发与调用，并自动将供应商返回的临时 HTTP URL
 * 转换为持久化 / 自包含的 Base64 Data URI。
 */
@Service
public class ImageGenerationService {

	private static final Logger log = LoggerFactory.getLogger(ImageGenerationService.class);

	private final ImageModelRegistry registry;
	private final WebClient webClient;

	public ImageGenerationService(ImageModelRegistry registry, WebClient.Builder webClientBuilder) {
		this.registry = registry;
		this.webClient = webClientBuilder.build();
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
				provider, request.model(), request.width(), request.height(), request.quality(), request.style()
		);

		log.info("开始生成图片, prompt: '{}', provider: '{}', model: '{}'", request.prompt(), provider, options != null ? options.getModel() : null);

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
					options != null ? options.getModel() : null
			);
		})
		.onErrorMap(e -> !(e instanceof AiException),
				e -> new ImageGenerationException("IMAGE_GEN_FAILED", "图像生成失败: " + e.getMessage()));
	}

	private Mono<String> processImageResponse(ImageResponse response) {
		if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
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

	private Mono<String> downloadAndConvertToBase64(String url) {
		return webClient.get()
				.uri(url)
				.retrieve()
				.bodyToMono(byte[].class)
				.map(bytes -> "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes))
				.onErrorResume(e -> {
					log.warn("从临时 URL 下载图像失败，回落为原始 URL: {}", e.getMessage());
					return Mono.just(url);
				});
	}
}
