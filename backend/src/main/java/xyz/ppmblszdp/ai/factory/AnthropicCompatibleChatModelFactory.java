package xyz.ppmblszdp.ai.factory;

import java.time.Duration;
import java.util.concurrent.Executors;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.ProviderProtocol;

/**
 * Anthropic / Claude 兼容协议工厂。
 *
 * <p>用于对接 Claude 官方，或任何提供 Anthropic 兼容协议的网关（自定义 baseUrl 即可）。
 * 与 OpenAI 工厂同理，2.0 将连接参数下沉到 {@link AnthropicChatOptions}。
 */
public class AnthropicCompatibleChatModelFactory implements ChatModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.ANTHROPIC;
    }

    @Override
    public ChatModel create(AiProviderProperties.SecondClassConfig config) {
        String defaultModel = config.firstEnabledModelName();
        AnthropicChatOptions.Builder optsBuilder = AnthropicChatOptions.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .timeout(DEFAULT_TIMEOUT);
        if (config.maxRetriesOrNull() != null) {
            optsBuilder.maxRetries(config.maxRetriesOrNull());
        }
        if (defaultModel != null) {
            optsBuilder.model(defaultModel);
        }
        return AnthropicChatModel.builder()
                .options(optsBuilder.build())
                .dispatcherExecutor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }
}
