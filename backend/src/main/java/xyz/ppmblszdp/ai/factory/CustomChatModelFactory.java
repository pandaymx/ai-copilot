package xyz.ppmblszdp.ai.factory;

import org.springframework.ai.chat.model.ChatModel;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.ProviderProtocol;
import xyz.ppmblszdp.ai.spi.CustomChatModelSupplier;

import java.util.Map;
import java.util.Optional;

/**
 * 自定义 SPI 工厂。
 *
 * <p>按配置中的 {@code supplier} 字段（Spring Bean 名）查找用户手写的
 * {@link CustomChatModelSupplier} 实现，调用其 {@code supply(config)} 获得 ChatModel 实例。
 * 查找不到时抛出明确异常，提示需注册对应名称的 Bean。
 */
public class CustomChatModelFactory implements ChatModelFactory {

	private final Map<String, CustomChatModelSupplier> suppliers;

	public CustomChatModelFactory(Map<String, CustomChatModelSupplier> suppliers) {
		this.suppliers = suppliers;
	}

	@Override
	public boolean supports(ProviderProtocol protocol) {
		return protocol == ProviderProtocol.CUSTOM;
	}

	@Override
	public ChatModel create(AiProviderProperties.SecondClassConfig config) {
		String supplierName = (config.supplier() == null || config.supplier().isBlank())
				? "default" : config.supplier();
		CustomChatModelSupplier supplier = Optional.ofNullable(suppliers.get(supplierName))
				.orElseThrow(() -> new IllegalStateException(
						"协议为 custom 的供应商 '%s' 需要名为 '%s' 的 CustomChatModelSupplier Bean，"
								+ "但未找到。请实现该接口并以该名称注册为 Spring Bean。".formatted(config.id(), supplierName)));
		return supplier.supply(config);
	}
}
