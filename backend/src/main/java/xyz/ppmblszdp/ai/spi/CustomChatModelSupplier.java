package xyz.ppmblszdp.ai.spi;

import org.springframework.ai.chat.model.ChatModel;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * 自定义协议扩展点（SPI）。
 *
 * <p>当某个厂商无法用 OpenAI / Anthropic 兼容协议接入（例如百度原生 AK/SK 换 token、
 * 或需要自定义签名头）时，使用者实现本接口并注册为 Spring Bean，即可在不改动框架代码的前提下接入。
 *
 * <p>框架通过配置中的 {@code supplier} 字段（Bean 名）查找对应实现。
 */
@FunctionalInterface
public interface CustomChatModelSupplier {

	/**
	 * 根据二等公民配置构建一个 {@link ChatModel} 实例。
	 *
	 * @param config 该供应商的 YAML 配置（含 baseUrl / apiKey / 自定义字段等）
	 * @return 可直接调用的 ChatModel 实例
	 */
	ChatModel supply(AiProviderProperties.SecondClassConfig config);
}
