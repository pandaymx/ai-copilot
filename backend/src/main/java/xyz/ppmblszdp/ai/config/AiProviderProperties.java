package xyz.ppmblszdp.ai.config;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import java.util.List;
import java.util.Map;

/**
 * 多模型供应商抽象层的根配置，绑定 {@code app.ai.*}。
 *
 * <p>刻意与 Spring AI 自身的 {@code spring.ai.*} 命名空间分离：{@code spring.ai.*} 由官方
 * starter 消费用于自动装配「一等公民」，本配置只负责在其之上补充统一抽象所需的元数据
 * （模型清单、展示信息、上下文预算），以及完整描述「二等公民」。这样即使本模块被移除，
 * 一等公民的原生行为也不受影响。
 *
 * <p>使用 record 做构造器绑定，天然不可变且对 GraalVM Native 友好。
 *
 * @param defaultProvider 全局默认供应商 id；请求未指定 provider 时使用
 * @param defaultModel    全局默认模型 id；请求未指定 model 时使用
 * @param systemPrompt    全局系统提示词，会被保底注入且不参与历史裁剪
 * @param context         上下文与 Token 预算相关配置
 * @param firstClass      一等公民的补充配置，key 为供应商 id（如 {@code openai}、{@code deepseek}）
 * @param secondClass     二等公民供应商列表，纯配置驱动
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProviderProperties(
		@Nullable String defaultProvider,
		@Nullable String defaultModel,
		@Nullable String fallbackProvider,
		@Nullable String fallbackModel,
		@Nullable String systemPrompt,
		@Nullable ContextConfig context,
		@Nullable MemoryConfig memory,
		@Nullable Map<String, FirstClassConfig> firstClass,
		@Nullable List<SecondClassConfig> secondClass
) {

	/** 兜底的上下文窗口大小，未在任何层级配置时使用。 */
	public static final int FALLBACK_MAX_CONTEXT_TOKENS = 32768;

	public ContextConfig resolveContext() {
		return context != null ? context : ContextConfig.defaults();
	}

	public Map<String, FirstClassConfig> resolveFirstClass() {
		return firstClass == null ? Map.of() : firstClass;
	}

	public List<SecondClassConfig> resolveSecondClass() {
		return secondClass == null ? List.of() : secondClass;
	}

	/** 记忆子系统配置（绑定 {@code app.ai.memory.*}）。 */
	public MemoryConfig resolveMemory() {
		return memory != null ? memory : MemoryConfig.defaults();
	}

	/**
	 * 上下文组装与 Token 预算配置。
	 *
	 * @param reserveOutputTokens     为模型输出预留的 token 数，从上下文窗口中扣除，
	 *                                避免「输入刚好塞满窗口导致无法生成回复」
	 * @param historyRatio            历史消息可占用的预算比例（0~1]
	 * @param defaultMaxContextTokens 模型未声明 maxContextTokens 时的兜底值
	 * @param safetyFactor            Token 估算安全系数，>1 表示高估，宁可少发也不超窗
	 */
	public record ContextConfig(
			@Nullable Integer reserveOutputTokens,
			@Nullable Double historyRatio,
			@Nullable Integer defaultMaxContextTokens,
			@Nullable Double safetyFactor
	) {

		public static ContextConfig defaults() {
			return new ContextConfig(null, null, null, null);
		}

		public int resolveReserveOutputTokens() {
			return (reserveOutputTokens != null && reserveOutputTokens >= 0) ? reserveOutputTokens : 2048;
		}

		public double resolveHistoryRatio() {
			if (historyRatio == null || historyRatio <= 0 || historyRatio > 1) {
				return 0.7d;
			}
			return historyRatio;
		}

		public int resolveDefaultMaxContextTokens() {
			return (defaultMaxContextTokens != null && defaultMaxContextTokens > 0)
					? defaultMaxContextTokens
					: FALLBACK_MAX_CONTEXT_TOKENS;
		}

		public double resolveSafetyFactor() {
			return (safetyFactor != null && safetyFactor >= 1.0d) ? safetyFactor : 1.1d;
		}
	}

	/**
	 * 记忆子系统配置。
	 *
	 * @param enabled                        记忆路径总开关；false 时 ChatService 退化为旧 history 模式
	 * @param hotCacheSize                   Redis 热缓存保留最近 N 条；同时作为会话记忆 RETRIEVE_SIZE 上限
	 * @param longTermTopK                   长期记忆向量检索 Top-K
	 * @param longTermDedupEnabled           长期记忆落库前是否执行相似度去重
	 * @param longTermSimilarityThreshold    长期记忆去重判定相似度阈值（0~1]
	 * @param longTermMinContentLength       写入/抽取的最小字符长度（前置硬规则）
	 * @param longTermSummarizeEnabled       是否开启会话周期性与会话结束摘要抽取
	 * @param longTermSummarizeTurnInterval 会话轮次间隔触发值
	 * @param conversationTtlDays             会话热缓存在 Redis 的 TTL（天），防止冷数据常驻
	 * @param rateLimit                      基于 Redis 的对话限流配置
	 * @param usageQuota                     用户级月度 Token 总量配额配置（基于 Redis 累计）
	 */
	public record MemoryConfig(
			@Nullable Boolean enabled,
			@Nullable Integer hotCacheSize,
			@Name("long-term-top-k") @Nullable Integer longTermTopK,
			@Name("long-term-dedup-enabled") @Nullable Boolean longTermDedupEnabled,
			@Name("long-term-similarity-threshold") @Nullable Double longTermSimilarityThreshold,
			@Name("long-term-min-content-length") @Nullable Integer longTermMinContentLength,
			@Name("long-term-summarize-enabled") @Nullable Boolean longTermSummarizeEnabled,
			@Name("long-term-summarize-turn-interval") @Nullable Integer longTermSummarizeTurnInterval,
			@Nullable Integer conversationTtlDays,
			@Nullable RateLimitConfig rateLimit,
			@Name("usage-quota") @Nullable UsageQuotaConfig usageQuota
	) {

		public static MemoryConfig defaults() {
			return new MemoryConfig(false, 20, 5, true, 0.85d, 15, true, 5, 14, null, null);
		}

		public boolean isEnabled() {
			return enabled != null && enabled;
		}

		public int resolveHotCacheSize() {
			return (hotCacheSize != null && hotCacheSize > 0) ? hotCacheSize : 20;
		}

		public int resolveLongTermTopK() {
			return (longTermTopK != null && longTermTopK > 0) ? longTermTopK : 5;
		}

		public boolean isLongTermDedupEnabled() {
			return longTermDedupEnabled == null || longTermDedupEnabled;
		}

		public double resolveLongTermSimilarityThreshold() {
			return (longTermSimilarityThreshold != null && longTermSimilarityThreshold > 0 && longTermSimilarityThreshold <= 1.0d)
					? longTermSimilarityThreshold
					: 0.85d;
		}

		public int resolveLongTermMinContentLength() {
			return (longTermMinContentLength != null && longTermMinContentLength > 0) ? longTermMinContentLength : 15;
		}

		public boolean isLongTermSummarizeEnabled() {
			return longTermSummarizeEnabled == null || longTermSummarizeEnabled;
		}

		public int resolveLongTermSummarizeTurnInterval() {
			return (longTermSummarizeTurnInterval != null && longTermSummarizeTurnInterval > 0) ? longTermSummarizeTurnInterval : 5;
		}

		public int resolveConversationTtlDays() {
			return (conversationTtlDays != null && conversationTtlDays > 0) ? conversationTtlDays : 14;
		}

		public RateLimitConfig resolveRateLimit() {
			return rateLimit != null ? rateLimit : RateLimitConfig.defaults();
		}

		public UsageQuotaConfig resolveUsageQuota() {
			return usageQuota != null ? usageQuota : UsageQuotaConfig.defaults();
		}
	}

	/**
	 * 用户级月度 Token 总量配额配置（基于 Redis 按月累计，保护上游月度成本）。
	 *
	 * <p>与 {@code rate-limit} 共享 {@code app.ai.memory.rate-limit.enabled} 开关：
	 * 限流与配额均在该开关下启用。月度配额默认值 1,000,000 tokens，预扣基础值用于
	 * 请求发起时无法预知真实 token 数的场景。所有字段支持 {@code ${ENV:默认}} 回退。
	 *
	 * @param monthlyTokenQuota 月度 token 上限（≤0 表示无上限）；回退 {@code AI_USAGE_MONTHLY_QUOTA}
	 * @param reserveTokens     预扣基础 token 数（请求发起时占用，事后校准）；回退 {@code AI_USAGE_RESERVE_TOKENS}
	 */
	public record UsageQuotaConfig(
			@Name("monthly-token-quota") @Nullable Long monthlyTokenQuota,
			@Name("reserve-tokens") @Nullable Long reserveTokens
	) {

		public static UsageQuotaConfig defaults() {
			return new UsageQuotaConfig(1_000_000L, 2000L);
		}

		public long resolveMonthlyTokenQuota() {
			return (monthlyTokenQuota != null && monthlyTokenQuota >= 0) ? monthlyTokenQuota : 1_000_000L;
		}

		public long resolveReserveTokens() {
			return (reserveTokens != null && reserveTokens >= 0) ? reserveTokens : 2000L;
		}
	}

	/**
	 * 基于 Redis 的对话限流配置（保护上游 API 配额）；Redis 不可用时降级放行。
	 *
	 * @param enabled         是否启用限流
	 * @param capacity        窗口内最大请求数
	 * @param refillSeconds   限流窗口（秒）
	 */
	public record RateLimitConfig(
			@Nullable Boolean enabled,
			@Nullable Integer capacity,
			@Nullable Integer refillSeconds
	) {

		public static RateLimitConfig defaults() {
			return new RateLimitConfig(false, 20, 60);
		}

		public boolean isEnabled() {
			return enabled != null && enabled;
		}

		public int resolveCapacity() {
			return (capacity != null && capacity > 0) ? capacity : 20;
		}

		public int resolveRefillSeconds() {
			return (refillSeconds != null && refillSeconds > 0) ? refillSeconds : 60;
		}
	}

	/**
	 * 一等公民的补充配置。
	 *
	 * <p>一等公民的连接参数（apiKey / baseUrl）仍由 {@code spring.ai.*} 与官方 starter 负责，
	 * 这里只声明「对外暴露哪些模型」及其展示元数据，用于填充 {@code GET /api/models}。
	 *
	 * @param enabled     是否启用；缺省为启用
	 * @param displayName 供应商展示名
	 * @param models      该供应商下暴露的模型清单（1:N 的 N 端）
	 */
	public record FirstClassConfig(
			@Nullable Boolean enabled,
			@Nullable String displayName,
			@Nullable List<ModelConfig> models
	) {

		public boolean isEnabled() {
			return enabled == null || enabled;
		}

		public List<ModelConfig> resolveModels() {
			return models == null ? List.of() : models;
		}
	}

	/**
	 * 二等公民供应商配置，完全由 YAML 驱动。
	 *
	 * @param id             供应商唯一标识，例如 {@code qwen}
	 * @param displayName    供应商展示名，例如「通义千问」
	 * @param protocol       接入协议：{@code openai} / {@code anthropic} / {@code custom}
	 * @param baseUrl        API 基础地址
	 * @param apiKey         API 密钥，建议以 {@code ${ENV_VAR:占位}} 形式引用环境变量
	 * @param supplier       仅 {@code custom} 协议使用，指向 {@code CustomChatModelSupplier} 的 Bean 名
	 * @param systemPrompt   供应商级系统提示词，优先级高于全局
	 * @param enabled        是否启用；缺省为启用
	 * @param requiresApiKey 是否强制校验密钥；本地模型（如自建 Ollama 网关）可置为 false
	 * @param timeoutSeconds 请求超时秒数
	 * @param maxRetries     最大重试次数
	 * @param models         模型清单（1:N 的 N 端）
	 */
	public record SecondClassConfig(
			@Nullable String id,
			@Nullable String displayName,
			@Nullable String protocol,
			@Nullable String baseUrl,
			@Nullable String apiKey,
			@Nullable String supplier,
			@Nullable String systemPrompt,
			@Nullable Boolean enabled,
			@Nullable Boolean requiresApiKey,
			@Nullable Integer timeoutSeconds,
			@Nullable Integer maxRetries,
			@Nullable List<ModelConfig> models
	) {

		public boolean isEnabled() {
			return enabled == null || enabled;
		}

		public boolean isApiKeyRequired() {
			// 自定义协议的鉴权方式由 supplier 自行决定（如百度原生 AK/SK 换 token），不强制校验
			if (requiresApiKey != null) {
				return requiresApiKey;
			}
			return resolveProtocol() != ProviderProtocol.CUSTOM;
		}

		public ProviderProtocol resolveProtocol() {
			return ProviderProtocol.fromString(protocol);
		}

		public String resolveDisplayName() {
			return (displayName != null && !displayName.isBlank()) ? displayName.trim() : resolveId();
		}

		public String resolveId() {
			if (id == null || id.isBlank()) {
				throw new IllegalArgumentException("二等公民供应商配置缺少必填的 id");
			}
			return id.trim();
		}

		public List<ModelConfig> resolveModels() {
			return models == null ? List.of() : models;
		}

		/** 第一个启用模型的对外 id（用于构造 ChatModel 时的默认 model 名）。 */
		public String firstEnabledModelName() {
			return resolveModels().stream()
					.filter(m -> m != null && m.isEnabled())
					.map(m -> m.resolveName())
					.findFirst()
					.orElse(null);
		}

		/** 超时秒数，未配置则回落 60。 */
		public int resolveTimeoutSeconds() {
			return (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 60;
		}

		/** 最大重试次数，可为 null（交给底层默认值）。 */
		@Nullable
		public Integer maxRetriesOrNull() {
			return maxRetries;
		}
	}
}
