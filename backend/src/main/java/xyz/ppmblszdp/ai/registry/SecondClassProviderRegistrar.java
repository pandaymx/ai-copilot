package xyz.ppmblszdp.ai.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.ApiKeyValidator;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.ModelConfig;
import xyz.ppmblszdp.ai.config.ProviderProtocol;
import xyz.ppmblszdp.ai.factory.ChatModelFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 二等公民注册器。
 *
 * <p>遍历 {@code app.ai.second-class} 配置，校验必填项与密钥有效性后，按 {@code protocol}
 * 选取对应 {@link ChatModelFactory} 构建 ChatModel，生成 {@link ProviderDescriptor} 注册。
 *
 * <p>单个 supplier 构建失败仅 WARN 跳过，不得影响其他 supplier 与应用启动
 * （例如某厂商密钥无效或网络不通不应阻断整个应用）。
 */
@Component
public class SecondClassProviderRegistrar {

	private static final Logger log = LoggerFactory.getLogger(SecondClassProviderRegistrar.class);

	private final List<ChatModelFactory> factories;
	private final AiProviderProperties properties;

	public SecondClassProviderRegistrar(List<ChatModelFactory> factories, AiProviderProperties properties) {
		this.factories = factories;
		this.properties = properties;
	}

	public Map<String, ProviderDescriptor> register() {
		Map<String, ProviderDescriptor> result = new LinkedHashMap<>();
		for (AiProviderProperties.SecondClassConfig cfg : properties.resolveSecondClass()) {
			try {
				ProviderDescriptor descriptor = registerOne(cfg);
				if (descriptor != null) {
					result.put(descriptor.providerId(), descriptor);
					log.info("已注册二等公民供应商 '{}' (协议 {}, 模型数 {})",
							descriptor.providerId(), descriptor.protocol(), descriptor.models().size());
				}
			}
			catch (RuntimeException ex) {
				log.warn("二等公民供应商 '{}' 注册失败，已跳过: {}", safeId(cfg), ex.getMessage());
			}
		}
		return result;
	}

	private ProviderDescriptor registerOne(AiProviderProperties.SecondClassConfig cfg) {
		String providerId = cfg.resolveId();
		if (!cfg.isEnabled()) {
			log.info("二等公民供应商 '{}' 已配置为禁用，跳过注册", providerId);
			return null;
		}
		ProviderProtocol protocol = cfg.resolveProtocol();
		if (cfg.baseUrl() == null || cfg.baseUrl().isBlank()) {
			throw new IllegalArgumentException("缺少必填的 base-url");
		}
		if (cfg.isApiKeyRequired() && !ApiKeyValidator.isValid(cfg.apiKey())) {
			log.warn("二等公民供应商 '{}' 未配置有效密钥（占位值或空白），跳过注册", providerId);
			return null;
		}

		ChatModelFactory factory = factories.stream()
				.filter(f -> f.supports(protocol))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"找不到支持协议 '%s' 的 ChatModelFactory".formatted(protocol)));

		ChatModel model = factory.create(cfg);

		List<ModelConfig> modelCfgs = cfg.resolveModels();
		Map<String, ModelDescriptor> models = new LinkedHashMap<>();
		for (ModelConfig mc : modelCfgs) {
			if (!mc.isEnabled()) {
				continue;
			}
			ModelDescriptor md = ModelDescriptor.from(mc, properties.resolveContext().resolveDefaultMaxContextTokens());
			models.put(md.id(), md);
		}
		if (models.isEmpty()) {
			throw new IllegalArgumentException("未声明任何可用模型");
		}
		String defaultModelId = models.values().stream().filter(ModelDescriptor::isDefault)
				.map(ModelDescriptor::id).findFirst().orElse(null);
		return ProviderDescriptor.builder()
				.providerId(providerId)
				.displayName(cfg.resolveDisplayName())
				.protocol(protocol.name().toLowerCase())
				.tier(ProviderDescriptor.Tier.SECOND_CLASS)
				.chatModel(model)
				.models(models)
				.defaultModelId(defaultModelId)
				.build();
	}

	private String safeId(AiProviderProperties.SecondClassConfig cfg) {
		try {
			return cfg.resolveId();
		}
		catch (RuntimeException ex) {
			return "<unknown>";
		}
	}
}
