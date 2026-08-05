package xyz.ppmblszdp.ai.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
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
 * <p>用 {@link ObjectProvider} 安全获取容器中官方 starter 自动装配的 ChatModel Bean
 * （Bean 缺失不报错，仅跳过）。结合 {@code app.ai.first-class} 配置补充模型清单与展示元数据；
 * 通过 {@link ApiKeyValidator} 过滤未配置密钥的供应商（Ollama 等本地模型豁免）。
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

	public FirstClassProviderRegistrar(
			ObjectProvider<DeepSeekChatModel> deepseek,
			ObjectProvider<OpenAiChatModel> openai,
			ObjectProvider<GoogleGenAiChatModel> google,
			ObjectProvider<OllamaChatModel> ollama,
			ObjectProvider<AnthropicChatModel> anthropic,
			AiProviderProperties properties) {
		this.deepseek = deepseek;
		this.openai = openai;
		this.google = google;
		this.ollama = ollama;
		this.anthropic = anthropic;
		this.properties = properties;
	}

	/** 收集所有可用的一等公民供应商描述符。 */
	public Map<String, ProviderDescriptor> register() {
		Map<String, ProviderDescriptor> result = new LinkedHashMap<>();
		registerOne(result, "deepseek", deepseek.getIfAvailable(), properties.resolveFirstClass().get("deepseek"));
		registerOne(result, "openai", openai.getIfAvailable(), properties.resolveFirstClass().get("openai"));
		registerOne(result, "google", google.getIfAvailable(), properties.resolveFirstClass().get("google"));
		registerOne(result, "ollama", ollama.getIfAvailable(), properties.resolveFirstClass().get("ollama"));
		registerOne(result, "anthropic", anthropic.getIfAvailable(), properties.resolveFirstClass().get("anthropic"));
		return result;
	}

	private void registerOne(Map<String, ProviderDescriptor> result, String providerId,
							 ChatModel model, AiProviderProperties.FirstClassConfig cfg) {
		if (model == null) {
			log.debug("一等公民供应商 '{}' 的 ChatModel Bean 不存在，跳过", providerId);
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
}
