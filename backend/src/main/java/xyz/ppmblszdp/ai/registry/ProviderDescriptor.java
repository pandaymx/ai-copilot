package xyz.ppmblszdp.ai.registry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 注册在 {@link ProviderRegistry} 中的供应商描述符（不可变）。
 *
 * <p>它是「一等公民」与「二等公民」在注册表中的统一载体：两者在路由阶段完全不可区分。
 * 内部持有该供应商对应的唯一 {@link ChatModel} 实例（一套连接池），以及其下 N 个模型的索引
 * （1:N 的 N 端）。运行时按 {@code (providerId, modelId)} 定位后，通过 {@code ChatOptions.model()}
 * 在同一实例上切换具体模型名，因此无需为每个模型单独建实例。
 */
public final class ProviderDescriptor {

	private final String providerId;
	private final String displayName;
	private final String protocol;
	private final Tier tier;
	private final ChatModel chatModel;
	private final ChatClient chatClient;
	private final Map<String, ModelDescriptor> models;
	private final String defaultModelId;

	private ProviderDescriptor(Builder b) {
		this.providerId = b.providerId;
		this.displayName = b.displayName;
		this.protocol = b.protocol;
		this.tier = b.tier;
		this.chatModel = b.chatModel;
		this.chatClient = (b.chatClient != null) ? b.chatClient : ChatClient.builder(b.chatModel).build();
		this.models = Map.copyOf(b.models);
		this.defaultModelId = b.defaultModelId;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String providerId() {
		return providerId;
	}

	public String displayName() {
		return displayName;
	}

	public String protocol() {
		return protocol;
	}

	public Tier tier() {
		return tier;
	}

	public ChatModel chatModel() {
		return chatModel;
	}

	/**
	 * 记忆路径专用：预构建的 {@link ChatClient}（由 {@code ChatClient.builder(chatModel)} 零成本构造）。
	 * 业务层挂载 Advisor 时复用此实例，避免每次请求重建。
	 */
	public ChatClient chatClient() {
		return chatClient;
	}

	/** 该供应商下已注册的模型索引（modelId -> 描述符），永不为 null。 */
	public Map<String, ModelDescriptor> models() {
		return models;
	}

	public String defaultModelId() {
		return defaultModelId;
	}

	public static final class Builder {
		private String providerId;
		private String displayName;
		private String protocol = "openai";
		private Tier tier = Tier.SECOND_CLASS;
		private ChatModel chatModel;
		private ChatClient chatClient;
		private Map<String, ModelDescriptor> models = Map.of();
		private String defaultModelId;

		public Builder providerId(String v) {
			this.providerId = v;
			return this;
		}

		public Builder displayName(String v) {
			this.displayName = v;
			return this;
		}

		public Builder protocol(String v) {
			if (v != null && !v.isBlank()) {
				this.protocol = v;
			}
			return this;
		}

		public Builder tier(Tier v) {
			this.tier = v;
			return this;
		}

		public Builder chatModel(ChatModel v) {
			this.chatModel = v;
			return this;
		}

		public Builder chatClient(ChatClient v) {
			this.chatClient = v;
			return this;
		}

		public Builder models(Map<String, ModelDescriptor> v) {
			this.models = v;
			return this;
		}

		public Builder defaultModelId(String v) {
			this.defaultModelId = v;
			return this;
		}

		public ProviderDescriptor build() {
			if (providerId == null || providerId.isBlank()) {
				throw new IllegalStateException("ProviderDescriptor 缺少 providerId");
			}
			if (chatModel == null) {
				throw new IllegalStateException("ProviderDescriptor 缺少 chatModel");
			}
			if (defaultModelId == null && !models.isEmpty()) {
				defaultModelId = models.keySet().iterator().next();
			}
			return new ProviderDescriptor(this);
		}
	}

	/** 供应商层级：决定其来源与是否由官方 starter 自动装配。 */
	public enum Tier {
		FIRST_CLASS,
		SECOND_CLASS
	}
}
