package xyz.ppmblszdp.ai.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.ApiKeyValidator;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.ModelConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一等公民注册器。
 *
 * <p>结合 {@link ApiKeyValidator} 过滤未配置密钥的供应商；
 * 用 {@link ObjectProvider} 安全获取容器中官方 starter 自动装配的 ChatModel Bean
 * （Bean 缺失或初始化失败不报错，仅跳过）。
 */
@Component
public class FirstClassProviderRegistrar {

	private static final Logger log = LoggerFactory.getLogger(FirstClassProviderRegistrar.class);

	private final ObjectProvider<DeepSeekChatModel> deepseek;
	private final ObjectProvider<OpenAiChatModel> openai;
	private final ObjectProvider<GoogleGenAiChatModel> google;
	private final ObjectProvider<OllamaChatModel> ollama;
	private final ObjectProvider<AnthropicChatModel> anthropic;
	private final AiProviderProperties properties;
	private final ObjectProvider<xyz.ppmblszdp.ai.identity.AuthProperties> authPropertiesProvider;

	@Value("${spring.ai.deepseek.api-key:}")
	private String deepseekApiKey;

	@Value("${spring.ai.openai.api-key:}")
	private String openaiApiKey;

	@Value("${spring.ai.google.genai.api-key:}")
	private String googleApiKey;

	@Value("${spring.ai.anthropic.api-key:}")
	private String anthropicApiKey;

	public FirstClassProviderRegistrar(
			ObjectProvider<DeepSeekChatModel> deepseek,
			ObjectProvider<OpenAiChatModel> openai,
			ObjectProvider<GoogleGenAiChatModel> google,
			ObjectProvider<OllamaChatModel> ollama,
			ObjectProvider<AnthropicChatModel> anthropic,
			AiProviderProperties properties,
			ObjectProvider<xyz.ppmblszdp.ai.identity.AuthProperties> authPropertiesProvider) {
		this.deepseek = deepseek;
		this.openai = openai;
		this.google = google;
		this.ollama = ollama;
		this.anthropic = anthropic;
		this.properties = properties;
		this.authPropertiesProvider = authPropertiesProvider;
	}

	/** 收集所有可用的一等公民供应商描述符。 */
	public Map<String, ProviderDescriptor> register() {
		Map<String, ProviderDescriptor> result = new LinkedHashMap<>();
		registerOne(result, "deepseek", deepseekApiKey, true, deepseek, properties.resolveFirstClass().get("deepseek"));
		registerOne(result, "openai", openaiApiKey, true, openai, properties.resolveFirstClass().get("openai"));
		registerOne(result, "google", googleApiKey, true, google, properties.resolveFirstClass().get("google"));
		registerOne(result, "ollama", null, false, ollama, properties.resolveFirstClass().get("ollama"));
		registerOne(result, "anthropic", anthropicApiKey, true, anthropic, properties.resolveFirstClass().get("anthropic"));
		return result;
	}

	private <T extends ChatModel> void registerOne(
			Map<String, ProviderDescriptor> result,
			String providerId,
			String apiKey,
			boolean requiresApiKey,
			ObjectProvider<T> provider,
			AiProviderProperties.FirstClassConfig cfg) {

		if (cfg != null && !cfg.isEnabled()) {
			log.info("一等公民供应商 '{}' 已配置为禁用，跳过注册", providerId);
			return;
		}

		if (requiresApiKey && ApiKeyValidator.isPlaceholder(apiKey)) {
			xyz.ppmblszdp.ai.identity.AuthProperties authProps = authPropertiesProvider != null ? authPropertiesProvider.getIfAvailable() : null;
			if (authProps != null && authProps.isStrict()) {
				throw new IllegalStateException(String.format(
						"【FAIL-FAST】系统运行在 strict 认证模式 (app.auth.mode=strict)，但供应商 '%s' 的 API Key 使用了默认占位符 [%s]！"
								+ " 请在 .env 中配置真实 API 密钥，或在开发环境中设置 AUTH_MODE=dev。",
						providerId, apiKey.trim()
				));
			}
			log.warn("一等公民供应商 '{}' 未配置有效密钥（占位值或空白），跳过注册", providerId);
			return;
		}

		if (requiresApiKey && !ApiKeyValidator.isValid(apiKey)) {
			log.warn("一等公民供应商 '{}' 未配置有效密钥（占位值或空白），跳过注册", providerId);
			return;
		}

		ChatModel model = safeGetModel(provider, providerId);
		if (model == null) {
			log.debug("一等公民供应商 '{}' 的 ChatModel Bean 不可用，跳过注册", providerId);
			return;
		}
		if (cfg != null && !cfg.isEnabled()) {
			log.info("一等公民供应商 '{}' 已配置为禁用，跳过注册", providerId);
			return;
		}

		List<ModelConfig> modelCfgs = (cfg != null) ? cfg.resolveModels() : List.of();
		Map<String, ModelDescriptor> models = buildModelIndex(modelCfgs, properties.resolveContext().resolveDefaultMaxContextTokens());
		if (models.isEmpty()) {
			log.warn("一等公民供应商 '{}' 未声明任何可用模型，跳过注册", providerId);
			return;
		}
		String displayName = (cfg != null && cfg.displayName() != null && !cfg.displayName().isBlank())
				? cfg.displayName() : providerId;
		String defaultModelId = models.values().stream()
				.filter(m -> m != null && m.isDefault())
				.map(m -> m.id())
				.findFirst()
				.orElse(null);
		ProviderDescriptor descriptor = ProviderDescriptor.builder()
				.providerId(providerId)
				.displayName(displayName)
				.protocol("openai")
				.tier(ProviderDescriptor.Tier.FIRST_CLASS)
				.chatModel(model)
				.models(models)
				.defaultModelId(defaultModelId)
				.build();
		result.put(providerId, descriptor);
		log.info("已注册一等公民供应商 '{}' (协议 openai, 模型数 {})", providerId, models.size());
	}

	private Map<String, ModelDescriptor> buildModelIndex(List<ModelConfig> cfgs, int fallbackMax) {
		Map<String, ModelDescriptor> index = new LinkedHashMap<>();
		for (ModelConfig cfg : cfgs) {
			if (!cfg.isEnabled()) {
				continue;
			}
			ModelDescriptor md = ModelDescriptor.from(cfg, fallbackMax);
			index.put(md.id(), md);
		}
		return index;
	}

	private <T extends ChatModel> ChatModel safeGetModel(ObjectProvider<T> provider, String providerId) {
		try {
			return provider.getIfAvailable();
		} catch (Throwable ex) {
			log.warn("获取一等公民供应商 '{}' 的 ChatModel Bean 失败，已容错跳过: {}", providerId, ex.getMessage());
			return null;
		}
	}
}
