package xyz.ppmblszdp.ai.config;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

/**
 * 多模型供应商抽象层的根配置，绑定 {@code app.ai.*}。
 *
 * <p>
 * 刻意与 Spring AI 自身的 {@code spring.ai.*} 命名空间分离：{@code spring.ai.*} 由官方
 * starter 消费用于自动装配「一等公民」，本配置只负责在其之上补充统一抽象所需的元数据
 * （模型清单、展示信息、上下文预算），以及完整描述「二等公民」。这样即使本模块被移除，
 * 一等公民的原生行为也不受影响。
 *
 * <p>
 * 使用 record 做构造器绑定，天然不可变且对 GraalVM Native 友好。
 *
 * @param defaultProvider 全局默认供应商 id；请求未指定 provider 时使用
 * @param defaultModel    全局默认模型 id；请求未指定 model 时使用
 * @param systemPrompt    全局系统提示词，会被保底注入且不参与历史裁剪
 * @param context         上下文与 Token 预算相关配置
 * @param firstClass      一等公民的补充配置，key 为供应商 id（如
 *                        {@code openai}、{@code deepseek}）
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
        @Nullable RagConfig rag,
        @Nullable AgentConfig agent,
        @Nullable ImageConfig image,
        @Nullable Map<String, FirstClassConfig> firstClass,
        @Nullable List<SecondClassConfig> secondClass) {

    /** 兜底的上下文窗口大小，未在任何层级配置时使用。 */
    public static final int FALLBACK_MAX_CONTEXT_TOKENS = 32768;

    public ImageConfig resolveImage() {
        return image != null ? image : ImageConfig.defaults();
    }

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

    /** RAG 文档多源解析与检索管道配置（绑定 {@code app.ai.rag.*}）。 */
    public RagConfig resolveRag() {
        return rag != null ? rag : RagConfig.defaults();
    }

    /** Agent 工具调用子系统配置（绑定 {@code app.ai.agent.*}）。 */
    public AgentConfig resolveAgent() {
        return agent != null ? agent : AgentConfig.defaults();
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
            @Nullable Double safetyFactor,
            @Nullable ContextCompressionConfig compression) {

        public static ContextConfig defaults() {
            return new ContextConfig(null, null, null, null, null);
        }

        public ContextCompressionConfig resolveCompression() {
            return compression != null ? compression : ContextCompressionConfig.defaults();
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
     * 智能上下文压缩配置（绑定 {@code app.ai.context.compression.*}）。
     *
     * @param enabled        总开关（默认 true）
     * @param provider       压缩专用低成本供应商 ID（如 google/deepseek，为空则使用系统默认）
     * @param model          压缩专用低成本模型 ID（如 gemini-3.6-flash/deepseek-chat，为空则使用供应商默认）
     * @param defaultLevel   默认压缩等级：LIGHT / DEEP / KEYWORDS
     * @param protectedTurns 保留不参与压缩的最新轮次数（默认 3 轮，保证 Short-term Working Memory 完整）
     * @param timeoutMs      LLM 压缩调用超时（毫秒，默认 10000ms，超时退化为硬删除）
     */
    public record ContextCompressionConfig(
            @Nullable Boolean enabled,
            @Nullable String provider,
            @Nullable String model,
            @Nullable String defaultLevel,
            @Nullable Integer protectedTurns,
            @Nullable Long timeoutMs) {

        public static ContextCompressionConfig defaults() {
            return new ContextCompressionConfig(true, null, null, "LIGHT", 3, 10000L);
        }

        public boolean resolveEnabled() {
            return enabled == null || enabled;
        }

        public String resolveDefaultLevel() {
            return (defaultLevel != null && !defaultLevel.isBlank()) ? defaultLevel : "LIGHT";
        }

        public int resolveProtectedTurns() {
            return (protectedTurns != null && protectedTurns >= 0) ? protectedTurns : 3;
        }

        public long resolveTimeoutMs() {
            return (timeoutMs != null && timeoutMs > 0) ? timeoutMs : 10000L;
        }
    }

    /**
     * 记忆子系统配置。
     *
     * @param enabled                       记忆路径总开关；false 时 ChatService 退化为旧 history
     *                                      模式
     * @param hotCacheSize                  Redis 热缓存保留最近 N 条；同时作为会话记忆 RETRIEVE_SIZE
     *                                      上限
     * @param longTermTopK                  长期记忆向量检索 Top-K
     * @param longTermDedupEnabled          长期记忆落库前是否执行相似度去重
     * @param longTermSimilarityThreshold   长期记忆去重判定相似度阈值（0~1]
     * @param longTermMinContentLength      写入/抽取的最小字符长度（前置硬规则）
     * @param longTermSummarizeEnabled      是否开启会话周期性与会话结束摘要抽取
     * @param longTermSummarizeTurnInterval 会话轮次间隔触发值
     * @param conversationTtlDays           会话热缓存在 Redis 的 TTL（天），防止冷数据常驻
     * @param rateLimit                     基于 Redis 的对话限流配置
     * @param usageQuota                    用户级月度 Token 总量配额配置（基于 Redis 累计）
     */
    public record MemoryConfig(
            @Nullable Boolean enabled,
            @Nullable Integer hotCacheSize,
            @Name("long-term-top-k") @Nullable Integer longTermTopK,
            @Name("long-term-dedup-enabled") @Nullable Boolean longTermDedupEnabled,

            @Name("long-term-similarity-threshold") @Nullable
            Double longTermSimilarityThreshold,

            @Name("long-term-min-content-length") @Nullable Integer longTermMinContentLength,
            @Name("long-term-summarize-enabled") @Nullable Boolean longTermSummarizeEnabled,

            @Name("long-term-summarize-turn-interval") @Nullable
            Integer longTermSummarizeTurnInterval,

            @Nullable Integer conversationTtlDays,
            @Nullable RateLimitConfig rateLimit,
            @Name("usage-quota") @Nullable UsageQuotaConfig usageQuota) {

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
            return (longTermSimilarityThreshold != null
                            && longTermSimilarityThreshold > 0
                            && longTermSimilarityThreshold <= 1.0d)
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
            return (longTermSummarizeTurnInterval != null && longTermSummarizeTurnInterval > 0)
                    ? longTermSummarizeTurnInterval
                    : 5;
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
     * Agent 工具调用子系统配置（绑定 {@code app.ai.agent.*}）。
     *
     * @param enabled             Agent 模式服务端总开关；false 时即使前端开启 agentEnabled 也不装配工具
     * @param maxToolCalls        单次请求内允许连续调用工具的最大次数（防止 LLM 死循环消耗 Token）
     * @param timeoutSeconds      单次工具执行的超时上限（秒）
     * @param augmentMcpTools     是否对 MCP 远程工具也注入 innerThought 增强
     * @param toolSearchAdvisor   渐进式工具披露 / 工具检索 Advisor 配置
     * @param orchestratorEnabled 调度者-工作者模式总开关；true 时向 Agent 工具集注入 SubAgentTool
     * @param workerProvider      Worker ChatClient 使用的供应商 id；空则复用主模型供应商
     * @param workerModel         Worker ChatClient 使用的模型 id；空则复用主模型
     * @param workerMaxTokens     Worker 单次推理最大输出 token 数（防止失控输出）
     * @param maxWorkerDepth      Worker 最大嵌套深度上限（默认 1：仅允许 Orchestrator→Worker 单层派发）
     * @param codeReview          代码审查工具（code_review）配置；默认开启，与代码执行沙箱同属开发类工具
     */
    public record AgentConfig(
            @Nullable Boolean enabled,
            @Name("max-tool-calls") @Nullable Integer maxToolCalls,
            @Name("timeout-seconds") @Nullable Integer timeoutSeconds,
            @Name("augment-mcp-tools") @Nullable Boolean augmentMcpTools,
            @Name("tool-search-advisor") @Nullable ToolSearchAdvisorPropertiesConfig toolSearchAdvisor,
            @Name("orchestrator-enabled") @Nullable Boolean orchestratorEnabled,
            @Name("worker-provider") @Nullable String workerProvider,
            @Name("worker-model") @Nullable String workerModel,
            @Name("worker-max-tokens") @Nullable Integer workerMaxTokens,
            @Name("max-worker-depth") @Nullable Integer maxWorkerDepth,
            @Name("code-sandbox") @Nullable CodeSandboxConfig codeSandbox,
            @Name("code-review") @Nullable CodeReviewConfig codeReview,
            @Name("calendar-task-enabled") @Nullable Boolean calendarTaskEnabled,
            @Name("web-search-enabled") @Nullable Boolean webSearchEnabled,
            @Name("web-search") @Nullable WebSearchConfig webSearch,
            @Name("db-query-enabled") @Nullable Boolean dbQueryEnabled,
            @Name("db-query") @Nullable DbQueryConfig dbQuery,
            @Name("email-enabled") @Nullable Boolean emailEnabled) {

        public AgentConfig(
                @Nullable Boolean enabled,
                @Nullable Integer maxToolCalls,
                @Nullable Integer timeoutSeconds,
                @Nullable Boolean augmentMcpTools,
                @Nullable ToolSearchAdvisorPropertiesConfig toolSearchAdvisor,
                @Nullable Boolean orchestratorEnabled,
                @Nullable String workerProvider,
                @Nullable String workerModel,
                @Nullable Integer workerMaxTokens,
                @Nullable Integer maxWorkerDepth,
                @Nullable CodeSandboxConfig codeSandbox,
                @Nullable CodeReviewConfig codeReview,
                @Nullable Boolean calendarTaskEnabled) {
            this(
                    enabled,
                    maxToolCalls,
                    timeoutSeconds,
                    augmentMcpTools,
                    toolSearchAdvisor,
                    orchestratorEnabled,
                    workerProvider,
                    workerModel,
                    workerMaxTokens,
                    maxWorkerDepth,
                    codeSandbox,
                    codeReview,
                    calendarTaskEnabled,
                    false,
                    WebSearchConfig.defaults(),
                    false,
                    DbQueryConfig.defaults(),
                    false);
        }

        public static AgentConfig defaults() {
            return new AgentConfig(
                    true,
                    5,
                    30,
                    false,
                    ToolSearchAdvisorPropertiesConfig.defaults(),
                    false,
                    null,
                    null,
                    2048,
                    1,
                    CodeSandboxConfig.defaults(),
                    CodeReviewConfig.defaults(),
                    false,
                    false,
                    WebSearchConfig.defaults(),
                    false,
                    DbQueryConfig.defaults(),
                    false);
        }

        /** 邮件工具（EmailTool）是否开启，默认关闭。 */
        public boolean isEmailEnabled() {
            return emailEnabled != null && emailEnabled;
        }

        /** 代码审查工具是否开启（默认 true）。 */
        public boolean isCodeReviewEnabled() {
            return codeReview == null || codeReview.isEnabled();
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public int resolveMaxToolCalls() {
            return (maxToolCalls != null && maxToolCalls > 0) ? maxToolCalls : 5;
        }

        public int resolveTimeoutSeconds() {
            return (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 30;
        }

        public boolean isAugmentMcpTools() {
            return augmentMcpTools != null && augmentMcpTools;
        }

        public ToolSearchAdvisorPropertiesConfig resolveToolSearchAdvisor() {
            return toolSearchAdvisor != null ? toolSearchAdvisor : ToolSearchAdvisorPropertiesConfig.defaults();
        }

        public CodeSandboxConfig resolveCodeSandbox() {
            return codeSandbox != null ? codeSandbox : CodeSandboxConfig.defaults();
        }

        /** 调度者-工作者模式是否开启。 */
        public boolean isOrchestratorEnabled() {
            return orchestratorEnabled != null && orchestratorEnabled;
        }

        /** 日历与任务工具（CalendarTool / TaskTool）是否开启，默认关闭。 */
        public boolean isCalendarTaskEnabled() {
            return calendarTaskEnabled != null && calendarTaskEnabled;
        }

        /** 网络搜索工具（WebSearchTool）是否开启，默认关闭。 */
        public boolean isWebSearchEnabled() {
            return webSearchEnabled != null && webSearchEnabled;
        }

        public WebSearchConfig resolveWebSearch() {
            return webSearch != null ? webSearch : WebSearchConfig.defaults();
        }

        /** 数据库只读查询工具（DatabaseQueryTool）是否开启，默认关闭。 */
        public boolean isDbQueryEnabled() {
            return dbQueryEnabled != null && dbQueryEnabled;
        }

        public DbQueryConfig resolveDbQuery() {
            return dbQuery != null ? dbQuery : DbQueryConfig.defaults();
        }

        /** Worker 使用的供应商；null 表示复用主模型供应商。 */
        @Nullable
        public String resolveWorkerProvider() {
            return (workerProvider != null && !workerProvider.isBlank()) ? workerProvider.trim() : null;
        }

        /** Worker 使用的模型 id；null 表示复用主模型。 */
        @Nullable
        public String resolveWorkerModel() {
            return (workerModel != null && !workerModel.isBlank()) ? workerModel.trim() : null;
        }

        /** Worker 单次最大输出 token（防失控，默认 2048）。 */
        public int resolveWorkerMaxTokens() {
            return (workerMaxTokens != null && workerMaxTokens > 0) ? workerMaxTokens : 2048;
        }

        /** 允许的最大 Worker 嵌套深度（默认 1，只允许 Orchestrator→Worker 单层）。 */
        public int resolveMaxWorkerDepth() {
            return (maxWorkerDepth != null && maxWorkerDepth >= 0) ? maxWorkerDepth : 1;
        }
    }

    /** 代码审查工具配置（绑定 {@code app.ai.agent.code-review.*}）。 */
    public record CodeReviewConfig(
            @Name("enabled") @Nullable Boolean enabled) {
        public static CodeReviewConfig defaults() {
            return new CodeReviewConfig(true);
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }
    }

    /** 网络搜索工具配置（绑定 {@code app.ai.agent.web-search.*}）。 */
    public record WebSearchConfig(
            @Name("provider") @Nullable String provider,
            @Name("api-key") @Nullable String apiKey,
            @Name("base-url") @Nullable String baseUrl,
            @Name("timeout-seconds") @Nullable Integer timeoutSeconds) {

        public static WebSearchConfig defaults() {
            return new WebSearchConfig("tavily", null, null, 15);
        }

        public String resolveProvider() {
            return (provider != null && !provider.isBlank()) ? provider.trim().toLowerCase() : "tavily";
        }

        public int resolveTimeoutSeconds() {
            return (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 15;
        }
    }

    /** 数据库查询工具配置（绑定 {@code app.ai.agent.db-query.*}）。 */
    public record DbQueryConfig(
            @Name("allowed-tables") @Nullable List<String> allowedTables,
            @Name("max-rows") @Nullable Integer maxRows,
            @Name("timeout-seconds") @Nullable Integer timeoutSeconds) {

        public static DbQueryConfig defaults() {
            return new DbQueryConfig(List.of(), 200, 15);
        }

        public List<String> resolveAllowedTables() {
            return allowedTables != null ? allowedTables : List.of();
        }

        public int resolveMaxRows() {
            return (maxRows != null && maxRows > 0) ? maxRows : 200;
        }

        public int resolveTimeoutSeconds() {
            return (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 15;
        }
    }

    /**
     * 代码执行沙箱配置（绑定 {@code app.ai.agent.code-sandbox.*}）。
     *
     * @param enabled            是否开启代码沙箱工具
     * @param timeoutSeconds     代码执行超时（秒），默认 15s
     * @param maxOutputLength    标准输出/错误截断上限（字符数），默认 65536
     * @param dockerEnabled      是否优先采用 Docker 隔离容器运行，默认 true
     * @param allowLocalFallback 当 Docker 不可用时是否允许回退为本地安全进程沙箱，默认 true
     * @param pythonImage        Python Docker 镜像名称，默认 "python:3.11-slim"
     * @param nodeImage          Node.js Docker 镜像名称，默认 "node:20-alpine"
     * @param memoryLimit        Docker 内存限制，默认 "256m"
     * @param cpuLimit           Docker CPU 限制，默认 "1.0"
     */
    public record CodeSandboxConfig(
            @Nullable Boolean enabled,
            @Name("timeout-seconds") @Nullable Integer timeoutSeconds,
            @Name("max-output-length") @Nullable Integer maxOutputLength,
            @Name("docker-enabled") @Nullable Boolean dockerEnabled,
            @Name("allow-local-fallback") @Nullable Boolean allowLocalFallback,
            @Name("python-image") @Nullable String pythonImage,
            @Name("node-image") @Nullable String nodeImage,
            @Name("memory-limit") @Nullable String memoryLimit,
            @Name("cpu-limit") @Nullable String cpuLimit) {

        public static CodeSandboxConfig defaults() {
            return new CodeSandboxConfig(
                    true, 15, 65536, true, true, "python:3.11-slim", "node:20-alpine", "256m", "1.0");
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public int resolveTimeoutSeconds() {
            return (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 15;
        }

        public int resolveMaxOutputLength() {
            return (maxOutputLength != null && maxOutputLength > 0) ? maxOutputLength : 65536;
        }

        public boolean isDockerEnabled() {
            return dockerEnabled == null || dockerEnabled;
        }

        public boolean isAllowLocalFallback() {
            return allowLocalFallback == null || allowLocalFallback;
        }

        public String resolvePythonImage() {
            return (pythonImage != null && !pythonImage.isBlank()) ? pythonImage.trim() : "python:3.11-slim";
        }

        public String resolveNodeImage() {
            return (nodeImage != null && !nodeImage.isBlank()) ? nodeImage.trim() : "node:20-alpine";
        }

        public String resolveMemoryLimit() {
            return (memoryLimit != null && !memoryLimit.isBlank()) ? memoryLimit.trim() : "256m";
        }

        public String resolveCpuLimit() {
            return (cpuLimit != null && !cpuLimit.isBlank()) ? cpuLimit.trim() : "1.0";
        }
    }

    /**
     * 渐进式工具披露 Advisor 配置（绑定 {@code app.ai.agent.tool-search-advisor.*}）。
     *
     * @param enabled           是否开启 ToolSearchAdvisor 渐进式工具披露
     * @param toolIndexType     工具索引类型：regex | lucene | vector（默认 regex）
     * @param minToolsThreshold 工具检索触发阈值：全量工具（本地+MCP）大于等于此值时激活过滤
     */
    public record ToolSearchAdvisorPropertiesConfig(
            @Nullable Boolean enabled,
            @Name("tool-index-type") @Nullable String toolIndexType,
            @Name("min-tools-threshold") @Nullable Integer minToolsThreshold) {

        public static ToolSearchAdvisorPropertiesConfig defaults() {
            return new ToolSearchAdvisorPropertiesConfig(false, "regex", 30);
        }

        public boolean isEnabled() {
            return enabled != null && enabled;
        }

        public String resolveToolIndexType() {
            return (toolIndexType != null && !toolIndexType.isBlank())
                    ? toolIndexType.toLowerCase().trim()
                    : "regex";
        }

        public int resolveMinToolsThreshold() {
            return (minToolsThreshold != null && minToolsThreshold > 0) ? minToolsThreshold : 30;
        }
    }

    /**
     * 用户级月度 Token 总量配额配置（基于 Redis 按月累计，保护上游月度成本）。
     *
     * <p>
     * 与 {@code rate-limit} 共享 {@code app.ai.memory.rate-limit.enabled} 开关：
     * 限流与配额均在该开关下启用。月度配额默认值 1,000,000 tokens，预扣基础值用于
     * 请求发起时无法预知真实 token 数的场景。所有字段支持 {@code ${ENV:默认}} 回退。
     *
     * @param monthlyTokenQuota 月度 token 上限（≤0 表示无上限）；回退
     *                          {@code AI_USAGE_MONTHLY_QUOTA}
     * @param reserveTokens     预扣基础 token 数（请求发起时占用，事后校准）；回退
     *                          {@code AI_USAGE_RESERVE_TOKENS}
     */
    public record UsageQuotaConfig(
            @Name("monthly-token-quota") @Nullable Long monthlyTokenQuota,
            @Name("reserve-tokens") @Nullable Long reserveTokens) {

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
     * @param enabled       是否启用限流
     * @param capacity      窗口内最大请求数
     * @param refillSeconds 限流窗口（秒）
     */
    public record RateLimitConfig(
            @Nullable Boolean enabled,
            @Nullable Integer capacity,
            @Nullable Integer refillSeconds) {

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
     * <p>
     * 一等公民的连接参数（apiKey / baseUrl）仍由 {@code spring.ai.*} 与官方 starter 负责，
     * 这里只声明「对外暴露哪些模型」及其展示元数据，用于填充 {@code GET /api/models}。
     *
     * @param enabled     是否启用；缺省为启用
     * @param displayName 供应商展示名
     * @param models      该供应商下暴露的模型清单（1:N 的 N 端）
     */
    public record FirstClassConfig(
            @Nullable Boolean enabled,
            @Nullable String displayName,
            @Nullable List<ModelConfig> models) {

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
     * @param protocol       接入协议：{@code openai} / {@code anthropic} /
     *                       {@code custom}
     * @param baseUrl        API 基础地址
     * @param apiKey         API 密钥，建议以 {@code ${ENV_VAR:占位}} 形式引用环境变量
     * @param supplier       仅 {@code custom} 协议使用，指向
     *                       {@code CustomChatModelSupplier} 的 Bean 名
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
            @Nullable List<ModelConfig> models) {

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

    /**
     * RAG 文档多源解析与检索管道配置（绑定 {@code app.ai.rag.*}）。
     *
     * <p>
     * 独立 pgvector 表 {@code ai_rag_documents}，与长期记忆物理隔离。
     *
     * @param enabled        RAG 总开关
     * @param topK           文档相似检索 Top-K
     * @param chunkSize      TokenTextSplitter 每片 Token 数
     * @param overlap        相邻切片重叠 Token 数
     * @param encodingType   TokenTextSplitter 分词编码
     * @param collectionName 独立 pgvector 表名
     */
    public record RagConfig(
            @Nullable Boolean enabled,
            @Name("top-k") @Nullable Integer topK,
            @Name("chunk-size") @Nullable Integer chunkSize,
            @Nullable Integer overlap,
            @Name("encoding-type") @Nullable String encodingType,
            @Name("collection-name") @Nullable String collectionName) {
        public static RagConfig defaults() {
            return new RagConfig(false, 4, 900, 180, "CL100K_BASE", "ai_rag_documents");
        }

        public boolean isEnabled() {
            return enabled != null && enabled;
        }

        public int resolveTopK() {
            return (topK != null && topK > 0) ? topK : 4;
        }

        public int resolveChunkSize() {
            return (chunkSize != null && chunkSize > 0) ? chunkSize : 900;
        }

        public int resolveOverlap() {
            return (overlap != null && overlap >= 0) ? overlap : 180;
        }

        public String resolveEncodingType() {
            return (encodingType != null && !encodingType.isBlank()) ? encodingType.trim() : "CL100K_BASE";
        }

        public String resolveCollectionName() {
            return (collectionName != null && !collectionName.isBlank()) ? collectionName.trim() : "ai_rag_documents";
        }
    }

    /**
     * 图像生成服务配置（绑定 {@code app.ai.image.*}）。
     *
     * @param defaultProvider 默认图像生成供应商（如 openai / zhipu / stability / azure）
     * @param defaultModel    默认图像生成模型（如 dall-e-3 / cogview-3-plus）
     * @param width           生成图片宽度
     * @param height          生成图片高度
     * @param quality         生成质量（standard / hd）
     * @param style           生成风格（vivid / natural）
     * @param responseFormat  返回格式（b64_json / url）
     */
    public record ImageConfig(
            @Name("default-provider") @Nullable String defaultProvider,
            @Name("default-model") @Nullable String defaultModel,
            @Nullable Integer width,
            @Nullable Integer height,
            @Nullable String quality,
            @Nullable String style,
            @Name("response-format") @Nullable String responseFormat,
            @Nullable List<String> keywords) {
        public ImageConfig(
                String defaultProvider,
                String defaultModel,
                Integer width,
                Integer height,
                String quality,
                String style,
                String responseFormat) {
            this(defaultProvider, defaultModel, width, height, quality, style, responseFormat, null);
        }

        public static ImageConfig defaults() {
            return new ImageConfig("openai", "dall-e-3", 1024, 1024, "standard", "vivid", "b64_json", null);
        }

        public String resolveDefaultProvider() {
            return (defaultProvider != null && !defaultProvider.isBlank()) ? defaultProvider.trim() : "openai";
        }

        public String resolveDefaultModel() {
            return (defaultModel != null && !defaultModel.isBlank()) ? defaultModel.trim() : "dall-e-3";
        }

        public int resolveWidth() {
            return (width != null && width > 0) ? width : 1024;
        }

        public int resolveHeight() {
            return (height != null && height > 0) ? height : 1024;
        }

        public String resolveQuality() {
            return (quality != null && !quality.isBlank()) ? quality.trim() : "standard";
        }

        public String resolveStyle() {
            return (style != null && !style.isBlank()) ? style.trim() : "vivid";
        }

        public String resolveResponseFormat() {
            return (responseFormat != null && !responseFormat.isBlank()) ? responseFormat.trim() : "b64_json";
        }

        public List<String> resolveKeywords() {
            if (keywords != null && !keywords.isEmpty()) {
                return keywords;
            }
            return List.of(
                    "/image",
                    "/img",
                    "画一只",
                    "画一个",
                    "画一张",
                    "画成",
                    "生成图片",
                    "生成一张图片",
                    "帮我画",
                    "画图",
                    "绘制",
                    "generate image",
                    "draw a",
                    "draw an",
                    "draw ");
        }
    }
}
