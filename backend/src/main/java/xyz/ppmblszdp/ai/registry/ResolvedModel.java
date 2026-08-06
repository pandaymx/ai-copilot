package xyz.ppmblszdp.ai.registry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 路由结果记录：一次聊天请求解析出的调用所需全部信息。
 *
 * <p>一等公民与二等公民在此完全归一，Service 层拿到它后即可无差别调用，无法区分其来源。
 */
public record ResolvedModel(
		ChatModel chatModel,
		ProviderDescriptor provider,
		ModelDescriptor model
) {

	/** 记忆路径专用：委托到供应商描述符预构建的 {@link ChatClient}。 */
	public ChatClient chatClient() {
		return provider.chatClient();
	}
}
