package xyz.ppmblszdp.ai.factory;

import java.time.Duration;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.ProviderProtocol;

/**
 * OpenAI 兼容协议工厂。
 *
 * <p>覆盖绝大多数厂商：通义千问 DashScope 兼容模式、百度千帆 v2、智谱、月之暗面等。
 * 这些厂商提供与 OpenAI 一致的 chat completions 端点，仅 baseUrl 与 apiKey 不同。
 *
 * <p>Spring AI 2.0 将连接参数（baseUrl / apiKey / timeout / maxRetries）下沉到
 * {@link OpenAiChatOptions}，因此此处通过 options 注入连接参数，再由
 * {@link OpenAiChatModel} 自动据此构建底层 OpenAIClient，无需手动构造客户端。
 */
public class OpenAiCompatibleChatModelFactory implements ChatModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.OPENAI;
    }

    @Override
    public ChatModel create(AiProviderProperties.SecondClassConfig config) {
        String defaultModel = config.firstEnabledModelName();
        OpenAiChatOptions.Builder optsBuilder = OpenAiChatOptions.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .timeout(DEFAULT_TIMEOUT);
        if (config.maxRetriesOrNull() != null) {
            optsBuilder.maxRetries(config.maxRetriesOrNull());
        }
        if (defaultModel != null) {
            optsBuilder.model(defaultModel);
        }
        return OpenAiChatModel.builder()
                .options(optsBuilder.build())
                .httpClientBuilderCustomizer(
                        b -> b.dispatcherExecutorService(Executors.newVirtualThreadPerTaskExecutor()))
                .build();
    }
}
