package xyz.ppmblszdp.ai.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 ImageModel 注册表与供应商治理中心。
 *
 * <p>支持多供应商（OpenAI DALL·E 3、ZhiPuAI CogView、Azure、Stability AI）图像生成模型的
 * 归一解析与 ImageOptions 动态映射。
 */
@Component
public class ImageModelRegistry {

	private static final Logger log = LoggerFactory.getLogger(ImageModelRegistry.class);

	private final ImageModel defaultImageModel;
	private final AiProviderProperties properties;
	private final Map<String, ImageModel> providerModelCache = new ConcurrentHashMap<>();

	public ImageModelRegistry(ObjectProvider<ImageModel> imageModelProvider, AiProviderProperties properties) {
		this.defaultImageModel = imageModelProvider.getIfAvailable();
		this.properties = properties;
		if (this.defaultImageModel != null) {
			log.info("已成功装配全局默认 ImageModel: {}", this.defaultImageModel.getClass().getSimpleName());
		} else {
			log.warn("未检测到原生 ImageModel Bean，将根据 YAML 配置按需延迟初始化");
		}
	}

	/**
	 * 根据 providerId 解析目标 ImageModel，若指定 provider 未单独配置连接参数则回落全局默认模型。
	 */
	public ImageModel resolve(String providerId) {
		String targetProvider = (providerId != null && !providerId.isBlank())
				? providerId.toLowerCase().trim()
				: properties.resolveImage().resolveDefaultProvider();

		return providerModelCache.computeIfAbsent(targetProvider, key -> {
			ImageModel custom = createCustomProviderImageModel(key);
			if (custom != null) {
				return custom;
			}
			return defaultImageModel;
		});
	}

	/**
	 * 构建特定供应商的 ImageOptions 参数。
	 *
	 * @param providerId 供应商 ID
	 * @param modelId    模型 ID（如 dall-e-3, cogview-3-plus）
	 * @param width      图片宽度
	 * @param height     图片高度
	 * @param quality    生成质量（standard/hd）
	 * @param style      生成风格（vivid/natural）
	 * @return 构造完成的 ImageOptions
	 */
	public ImageOptions buildOptions(String providerId, String modelId, Integer width, Integer height, String quality, String style) {
		AiProviderProperties.ImageConfig imgConfig = properties.resolveImage();
		String effectiveModel = (modelId != null && !modelId.isBlank()) ? modelId : imgConfig.resolveDefaultModel();
		int effectiveWidth = (width != null && width > 0) ? width : imgConfig.resolveWidth();
		int effectiveHeight = (height != null && height > 0) ? height : imgConfig.resolveHeight();
		String effectiveQuality = (quality != null && !quality.isBlank()) ? quality : imgConfig.resolveQuality();
		String effectiveStyle = (style != null && !style.isBlank()) ? style : imgConfig.resolveStyle();

		String pId = (providerId != null && !providerId.isBlank()) ? providerId.toLowerCase().trim() : imgConfig.resolveDefaultProvider();

		// OpenAI / ZhiPuAI / Azure 等 OpenAI 协议兼容端点
		if ("openai".equalsIgnoreCase(pId) || "zhipu".equalsIgnoreCase(pId) || "azure".equalsIgnoreCase(pId)) {
			return OpenAiImageOptions.builder()
					.model(effectiveModel)
					.width(effectiveWidth)
					.height(effectiveHeight)
					.quality(effectiveQuality)
					.style(effectiveStyle)
					.responseFormat("b64_json") // 优先请求 base64，提高性能并避免临时 URL 过期
					.build();
		}

		// 通用 ImageOptions 回退
		return ImageOptionsBuilder.builder()
				.model(effectiveModel)
				.width(effectiveWidth)
				.height(effectiveHeight)
				.build();
	}

	private ImageModel createCustomProviderImageModel(String providerId) {
		// 检查二等公民或 YAML 配置中是否有该供应商的专属 apiKey & baseUrl (如 ZhiPuAI / Azure)
		for (AiProviderProperties.SecondClassConfig sc : properties.resolveSecondClass()) {
			if (sc.resolveId().equalsIgnoreCase(providerId)) {
				String apiKey = sc.apiKey();
				String baseUrl = sc.baseUrl();
				if (apiKey != null && !apiKey.isBlank() && !apiKey.contains("your_")) {
					String effectiveBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "https://api.openai.com";
					log.info("为供应商 {} 动态装配 OpenAiImageModel (baseUrl={})", providerId, effectiveBaseUrl);
					OpenAiImageOptions options = OpenAiImageOptions.builder()
							.baseUrl(effectiveBaseUrl)
							.apiKey(apiKey)
							.build();
					return new OpenAiImageModel(options);
				}
			}
		}
		return null;
	}
}
