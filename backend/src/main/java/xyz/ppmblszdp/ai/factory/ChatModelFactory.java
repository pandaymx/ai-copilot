package xyz.ppmblszdp.ai.factory;

import org.springframework.ai.chat.model.ChatModel;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.ProviderProtocol;

/**
 * 协议适配工厂接口（策略模式）。
 *
 * <p>每种 {@link ProviderProtocol} 对应一个实现 Bean。新增一种协议只需新增一个实现，
 * 由 {@code SecondClassProviderRegistrar} 按 {@code protocol} 字段分派，符合开闭原则。
 */
public interface ChatModelFactory {

	/** 是否支持该协议。 */
	boolean supports(ProviderProtocol protocol);

	/** 根据配置构建一个 {@link ChatModel} 实例。 */
	ChatModel create(AiProviderProperties.SecondClassConfig config);
}
