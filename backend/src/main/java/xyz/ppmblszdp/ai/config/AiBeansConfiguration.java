package xyz.ppmblszdp.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.context.HeuristicTokenEstimator;
import xyz.ppmblszdp.ai.context.JTokkitTokenEstimator;
import xyz.ppmblszdp.ai.context.TokenEstimator;
import xyz.ppmblszdp.ai.factory.AnthropicCompatibleChatModelFactory;
import xyz.ppmblszdp.ai.factory.ChatModelFactory;
import xyz.ppmblszdp.ai.factory.CustomChatModelFactory;
import xyz.ppmblszdp.ai.factory.OpenAiCompatibleChatModelFactory;
import xyz.ppmblszdp.ai.memory.NoOpEmbeddingModel;
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;
import xyz.ppmblszdp.ai.registry.FirstClassProviderRegistrar;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.SecondClassProviderRegistrar;
import xyz.ppmblszdp.ai.spi.CustomChatModelSupplier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一抽象层的 Bean 装配。
 *
 * <p>注意本类刻意不直接 new 出 ChatModel：一等公民 ChatModel 由官方 starter 自动装配，
 * 二等公民 ChatModel 由其对应的 {@link ChatModelFactory} 在注册阶段构造。此处只负责把
 * 两个 Registrar 的产出合成一个不可变 {@link ProviderRegistry}。
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiBeansConfiguration {

	@Bean
	public OpenAiCompatibleChatModelFactory openAiCompatibleChatModelFactory() {
		return new OpenAiCompatibleChatModelFactory();
	}

	@Bean
	public AnthropicCompatibleChatModelFactory anthropicCompatibleChatModelFactory() {
		return new AnthropicCompatibleChatModelFactory();
	}

	@Bean
	public CustomChatModelFactory customChatModelFactory(Map<String, CustomChatModelSupplier> suppliers) {
		return new CustomChatModelFactory(suppliers);
	}

	@Bean
	@Primary
	public EmbeddingModel primaryEmbeddingModel(
			@Qualifier("openAiEmbeddingModel") ObjectProvider<EmbeddingModel> openAiEmbeddingModel,
			@Qualifier("ollamaEmbeddingModel") ObjectProvider<EmbeddingModel> ollamaEmbeddingModel,
			@Value("${spring.ai.openai.api-key:}") String openaiApiKey) {
		Logger log = LoggerFactory.getLogger(AiBeansConfiguration.class);
		boolean openaiKeyValid = ApiKeyValidator.isValid(openaiApiKey);
		if (openaiKeyValid) {
			EmbeddingModel openAi = openAiEmbeddingModel.getIfAvailable();
			if (openAi != null) {
				log.info("EmbeddingModel 选择: OpenAI (已包装 SafeEmbeddingModel)");
				return new SafeEmbeddingModel(openAi);
			}
		}
		EmbeddingModel ollama = ollamaEmbeddingModel.getIfAvailable();
		if (ollama != null) {
			log.info("EmbeddingModel 选择: Ollama (已包装 SafeEmbeddingModel)");
			return new SafeEmbeddingModel(ollama);
		}
		EmbeddingModel openAi = openAiEmbeddingModel.getIfAvailable();
		if (openAi != null) {
			log.warn("OpenAI API Key 未配置或无效，且未检测到 Ollama Embedding，启用 SafeEmbeddingModel(OpenAI) 容错静默降级");
			return new SafeEmbeddingModel(openAi);
		}
		log.warn("未检测到可用的 EmbeddingModel Bean，启用 NoOpEmbeddingModel 容错降级");
		return new SafeEmbeddingModel(new NoOpEmbeddingModel());
	}

	@Bean
	public TokenEstimator tokenEstimator() {
		// 优先使用 JTokkit (o200k_base) 精确 Tokenizer，异常或缺失时自动降级至启发式估算 (1.1x 安全系数)
		return new JTokkitTokenEstimator(new HeuristicTokenEstimator(1.1d));
	}

	@Bean
	public ContextAssembler contextAssembler(AiProviderProperties properties, TokenEstimator estimator) {
		return new ContextAssembler(properties, estimator);
	}

	@Bean
	public ProviderRegistry providerRegistry(
			FirstClassProviderRegistrar firstClass,
			SecondClassProviderRegistrar secondClass,
			AiProviderProperties properties) {
		Map<String, ProviderDescriptor> all = new LinkedHashMap<>();
		all.putAll(firstClass.register());
		all.putAll(secondClass.register());

		ProviderRegistry.Builder builder = ProviderRegistry.builder();
		all.values().forEach(builder::register);
		String defProvider = (properties.defaultProvider() != null && !properties.defaultProvider().isBlank())
				? properties.defaultProvider() : null;
		String defModel = (properties.defaultModel() != null && !properties.defaultModel().isBlank())
				? properties.defaultModel() : null;
		builder.defaultProviderId(defProvider).defaultModelId(defModel);
		ProviderRegistry registry = builder.build();
		logRegistry(registry);
		return registry;
	}

	private void logRegistry(ProviderRegistry registry) {
		Logger log = LoggerFactory.getLogger(AiBeansConfiguration.class);
		log.info("Provider 注册表构建完成，供应商数={}，默认供应商={}，默认模型={}",
				registry.providers().size(), registry.defaultProviderId(), registry.defaultModelId());
	}
}
