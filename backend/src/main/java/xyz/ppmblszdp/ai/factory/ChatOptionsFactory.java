package xyz.ppmblszdp.ai.factory;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * ChatOptions 构建工厂。
 * 集中管理各 LLM Provider 对应的 ChatOptions 实例化逻辑，避免在服务层分散重复。
 */
public final class ChatOptionsFactory {

    private ChatOptionsFactory() {
        // 工具类禁止实例化
    }

    /**
     * 根据 ResolvedModel 构建对应的 ChatOptions。
     *
     * @param resolved 模型解析信息
     * @param temperature 采样温度 (若为 null 则不设置)
     * @return 供应商特定的 ChatOptions 实例
     */
    public static ChatOptions forProvider(ResolvedModel resolved, Double temperature) {
        if (resolved == null || resolved.provider() == null || resolved.model() == null) {
            return forProvider(null, null, temperature);
        }
        return forProvider(resolved.provider().providerId(), resolved.model().modelName(), temperature);
    }

    /**
     * 根据 providerId、modelName 和 temperature 构建对应的 ChatOptions。
     *
     * @param providerId 供应商 ID（如 "deepseek", "openai", "google", "anthropic", "ollama"）
     * @param modelName 模型名称
     * @param temperature 采样温度 (若为 null 则不设置)
     * @return 供应商特定的 ChatOptions 实例
     */
    public static ChatOptions forProvider(String providerId, String modelName, Double temperature) {
        // 业务中的 providerId 为复合形式（如 "google-gemini"、"anthropic-claude"、"ollama-local"），
        // 因此按前缀匹配各供应商的合法别名（小写）。相较于 contains() 子串匹配，startsWith()
        // 可避免自定义供应商 ID 中间夹带 "openai" 等子串导致的误匹配，同时正确命中复合前缀 ID。
        String pid = providerId != null ? providerId.toLowerCase() : "";

        if (pid.startsWith("deepseek")) {
            DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder().model(modelName);
            if (temperature != null) {
                builder.temperature(temperature);
            }
            return builder.build();
        }
        if (pid.startsWith("openai")) {
            OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(modelName);
            if (temperature != null) {
                builder.temperature(temperature);
            }
            return builder.build();
        }
        if (pid.startsWith("google") || pid.startsWith("gemini")) {
            GoogleGenAiChatOptions.Builder builder =
                    GoogleGenAiChatOptions.builder().model(modelName);
            if (temperature != null) {
                builder.temperature(temperature);
            }
            return builder.build();
        }
        if (pid.startsWith("anthropic") || pid.startsWith("claude")) {
            AnthropicChatOptions.Builder builder =
                    AnthropicChatOptions.builder().model(modelName);
            if (temperature != null) {
                builder.temperature(temperature);
            }
            return builder.build();
        }
        if (pid.startsWith("ollama")) {
            OllamaChatOptions.Builder builder = OllamaChatOptions.builder().model(modelName);
            if (temperature != null) {
                builder.temperature(temperature);
            }
            return builder.build();
        }

        // 默认回退至 OpenAiChatOptions
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(modelName);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        return builder.build();
    }
}
