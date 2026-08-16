package xyz.ppmblszdp.ai.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import xyz.ppmblszdp.ai.clarification.ClarificationAdvisor;
import xyz.ppmblszdp.ai.collab.CollaborationBus;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.intent.IntentResult;
import xyz.ppmblszdp.ai.interaction.InteractionAnalysis;
import xyz.ppmblszdp.ai.interaction.InteractionAnalyzer;
import xyz.ppmblszdp.ai.interaction.InteractionPromptPolicy;
import xyz.ppmblszdp.ai.interaction.InteractionState;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter.RateLimiter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryAdvisorFactory;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryWriter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;
import xyz.ppmblszdp.ai.rag.advisor.RagAdvisorConfig.RagAdvisorFactory;
import xyz.ppmblszdp.ai.rag.service.DocumentChatService;
import xyz.ppmblszdp.ai.rag.service.DocumentChatService.DocumentChatContext;
import xyz.ppmblszdp.ai.reflection.ReflectionAdvisor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ModelPerformanceTracker;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.safeguard.SafeGuardAdvisor;
import xyz.ppmblszdp.ai.tool.AugmentedToolCallbackProvider;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;
import xyz.ppmblszdp.ai.tool.ToolSearchAdvisorConfig.ToolSearchAdvisorFactory;

/**
 * 核心对话与 Agent 编排服务。
 *
 * <p>负责记忆路径、Agent 工具链与 MCP 聚合、上下文组装、多模型路由与降级熔断编排。
 */
@Service
public class ChatOrchestrator implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    private final ProviderRegistry registry;
    private final ContextAssembler contextAssembler;
    private final ObjectProvider<ChatMemory> sessionChatMemory;
    private final ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory;
    private final ObjectProvider<LongTermMemoryWriter> longTermWriter;
    private final ObjectProvider<LongTermMemoryProcessor> longTermProcessor;
    private final ObjectProvider<RateLimiter> rateLimiter;
    private final UsageRecorder usageRecorder;
    private final ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor;
    private final ObjectProvider<ClarificationAdvisor> clarificationAdvisor;
    private final ObjectProvider<ReflectionAdvisor> reflectionAdvisor;
    private final ObjectProvider<RagAdvisorFactory> ragAdvisorFactory;
    private final ModelHealthTracker healthTracker;
    private final ModelPerformanceTracker performanceTracker;
    private final SessionService sessionService;
    private final AiProviderProperties properties;
    private final boolean memoryEnabled;
    private final boolean agentEnabled;
    private final ToolEventEmitter toolEventEmitter;
    private final ToolCallback[] toolCallbacks;
    private final ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider;
    private final ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory;
    private final AugmentedToolCallbackProvider augmentedToolCallbackProvider;
    private final ImageRouter imageRouter;
    private final IntentClassifier intentClassifier;
    private final InteractionAnalyzer interactionAnalyzer;
    private final VisionService visionService;
    private final ObjectProvider<xyz.ppmblszdp.ai.agent.plan.ReActAgent> reActAgentProvider;
    private final ObjectProvider<xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator> intentFeedbackAccumulatorProvider;
    private final ObjectProvider<xyz.ppmblszdp.ai.context.ContextCompressor> contextCompressorProvider;
    private final ObjectProvider<DocumentChatService> documentChatServiceProvider;
    private final ObjectProvider<xyz.ppmblszdp.ai.customtool.service.CustomToolService> customToolServiceProvider;
    private final ObjectProvider<xyz.ppmblszdp.ai.persona.service.PersonaStoreService> personaStoreServiceProvider;

    private final ConcurrentLinkedQueue<Disposable> fireAndForgetSubscriptions = new ConcurrentLinkedQueue<>();

    @Autowired
    private CollaborationBus collabBus;

    @Autowired(required = false)
    private xyz.ppmblszdp.ai.recommendation.ModelRecommender modelRecommender;

    public void setModelRecommender(xyz.ppmblszdp.ai.recommendation.ModelRecommender modelRecommender) {
        this.modelRecommender = modelRecommender;
    }

    @Autowired
    public ChatOrchestrator(
            ProviderRegistry registry,
            ContextAssembler contextAssembler,
            ObjectProvider<ChatMemory> sessionChatMemory,
            ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory,
            ObjectProvider<LongTermMemoryWriter> longTermWriter,
            ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
            ObjectProvider<RateLimiter> rateLimiter,
            UsageRecorder usageRecorder,
            ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
            ObjectProvider<ClarificationAdvisor> clarificationAdvisor,
            ObjectProvider<ReflectionAdvisor> reflectionAdvisor,
            ObjectProvider<RagAdvisorFactory> ragAdvisorFactory,
            ModelHealthTracker healthTracker,
            SessionService sessionService,
            AiProviderProperties properties,
            ToolEventEmitter toolEventEmitter,
            @Qualifier("agentToolCallbacks") ToolCallback[] toolCallbacks,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider,
            ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory,
            ObjectProvider<AugmentedToolCallbackProvider> augmentedToolProvider,
            ImageRouter imageRouter,
            IntentClassifier intentClassifier,
            VisionService visionService,
            ObjectProvider<xyz.ppmblszdp.ai.agent.plan.ReActAgent> reActAgentProvider,
            ObjectProvider<xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator> intentFeedbackAccumulatorProvider,
            ObjectProvider<xyz.ppmblszdp.ai.context.ContextCompressor> contextCompressorProvider,
            ObjectProvider<DocumentChatService> documentChatServiceProvider,
            ObjectProvider<ModelPerformanceTracker> performanceTrackerProvider,
            ObjectProvider<InteractionAnalyzer> interactionAnalyzerProvider,
            ObjectProvider<xyz.ppmblszdp.ai.customtool.service.CustomToolService> customToolServiceProvider,
            ObjectProvider<xyz.ppmblszdp.ai.persona.service.PersonaStoreService> personaStoreServiceProvider) {
        this.registry = registry;
        this.contextAssembler = contextAssembler;
        this.sessionChatMemory = sessionChatMemory;
        this.longTermFactory = longTermFactory;
        this.longTermWriter = longTermWriter;
        this.longTermProcessor = longTermProcessor;
        this.rateLimiter = rateLimiter;
        this.usageRecorder = usageRecorder;
        this.safeGuardAdvisor = safeGuardAdvisor;
        this.clarificationAdvisor = clarificationAdvisor;
        this.reflectionAdvisor = reflectionAdvisor;
        this.ragAdvisorFactory = ragAdvisorFactory;
        this.healthTracker = healthTracker;
        this.performanceTracker =
                (performanceTrackerProvider != null && performanceTrackerProvider.getIfAvailable() != null)
                        ? performanceTrackerProvider.getIfAvailable()
                        : new ModelPerformanceTracker();
        this.sessionService = sessionService;
        this.properties = properties;
        this.memoryEnabled = properties.resolveMemory().isEnabled();
        this.agentEnabled = properties.resolveAgent().isEnabled();
        this.toolEventEmitter = toolEventEmitter;
        this.toolCallbacks = toolCallbacks;
        this.mcpToolProvider = mcpToolProvider;
        this.toolSearchFactory = toolSearchFactory;
        this.augmentedToolCallbackProvider =
                (augmentedToolProvider != null && augmentedToolProvider.getIfAvailable() != null)
                        ? augmentedToolProvider.getIfAvailable()
                        : new AugmentedToolCallbackProvider();
        this.imageRouter = imageRouter;
        this.intentClassifier = intentClassifier;
        this.interactionAnalyzer =
                (interactionAnalyzerProvider != null && interactionAnalyzerProvider.getIfAvailable() != null)
                        ? interactionAnalyzerProvider.getIfAvailable()
                        : new InteractionAnalyzer();
        this.visionService = (visionService != null) ? visionService : new VisionService();
        this.reActAgentProvider = reActAgentProvider;
        this.intentFeedbackAccumulatorProvider = intentFeedbackAccumulatorProvider;
        this.contextCompressorProvider = contextCompressorProvider;
        this.documentChatServiceProvider = documentChatServiceProvider;
        this.customToolServiceProvider = customToolServiceProvider;
        this.personaStoreServiceProvider = personaStoreServiceProvider;
    }

    public ChatOrchestrator(
            ProviderRegistry registry,
            ContextAssembler contextAssembler,
            ObjectProvider<ChatMemory> sessionChatMemory,
            ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory,
            ObjectProvider<LongTermMemoryWriter> longTermWriter,
            ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
            ObjectProvider<RateLimiter> rateLimiter,
            UsageRecorder usageRecorder,
            ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
            ObjectProvider<ClarificationAdvisor> clarificationAdvisor,
            ObjectProvider<ReflectionAdvisor> reflectionAdvisor,
            ObjectProvider<RagAdvisorFactory> ragAdvisorFactory,
            ModelHealthTracker healthTracker,
            SessionService sessionService,
            AiProviderProperties properties,
            ToolEventEmitter toolEventEmitter,
            @Qualifier("agentToolCallbacks") ToolCallback[] toolCallbacks,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider,
            ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory,
            ObjectProvider<AugmentedToolCallbackProvider> augmentedToolProvider,
            ImageRouter imageRouter,
            IntentClassifier intentClassifier,
            VisionService visionService,
            ObjectProvider<xyz.ppmblszdp.ai.agent.plan.ReActAgent> reActAgentProvider,
            ObjectProvider<xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator> intentFeedbackAccumulatorProvider,
            ObjectProvider<xyz.ppmblszdp.ai.context.ContextCompressor> contextCompressorProvider,
            ObjectProvider<DocumentChatService> documentChatServiceProvider,
            ObjectProvider<ModelPerformanceTracker> performanceTrackerProvider) {
        this(
                registry,
                contextAssembler,
                sessionChatMemory,
                longTermFactory,
                longTermWriter,
                longTermProcessor,
                rateLimiter,
                usageRecorder,
                safeGuardAdvisor,
                clarificationAdvisor,
                reflectionAdvisor,
                ragAdvisorFactory,
                healthTracker,
                sessionService,
                properties,
                toolEventEmitter,
                toolCallbacks,
                mcpToolProvider,
                toolSearchFactory,
                augmentedToolProvider,
                imageRouter,
                intentClassifier,
                visionService,
                reActAgentProvider,
                intentFeedbackAccumulatorProvider,
                contextCompressorProvider,
                documentChatServiceProvider,
                performanceTrackerProvider,
                null,
                null,
                null);
    }

    public ChatOrchestrator(
            ProviderRegistry registry,
            ContextAssembler contextAssembler,
            ObjectProvider<ChatMemory> sessionChatMemory,
            ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory,
            ObjectProvider<LongTermMemoryWriter> longTermWriter,
            ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
            ObjectProvider<RateLimiter> rateLimiter,
            UsageRecorder usageRecorder,
            ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
            ObjectProvider<ClarificationAdvisor> clarificationAdvisor,
            ObjectProvider<ReflectionAdvisor> reflectionAdvisor,
            ObjectProvider<RagAdvisorFactory> ragAdvisorFactory,
            ModelHealthTracker healthTracker,
            SessionService sessionService,
            AiProviderProperties properties,
            ToolEventEmitter toolEventEmitter,
            @Qualifier("agentToolCallbacks") ToolCallback[] toolCallbacks,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider,
            ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory,
            ObjectProvider<AugmentedToolCallbackProvider> augmentedToolProvider,
            ImageRouter imageRouter,
            IntentClassifier intentClassifier,
            VisionService visionService,
            ObjectProvider<xyz.ppmblszdp.ai.agent.plan.ReActAgent> reActAgentProvider,
            ObjectProvider<xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator> intentFeedbackAccumulatorProvider,
            ObjectProvider<xyz.ppmblszdp.ai.context.ContextCompressor> contextCompressorProvider,
            ObjectProvider<DocumentChatService> documentChatServiceProvider) {
        this(
                registry,
                contextAssembler,
                sessionChatMemory,
                longTermFactory,
                longTermWriter,
                longTermProcessor,
                rateLimiter,
                usageRecorder,
                safeGuardAdvisor,
                clarificationAdvisor,
                reflectionAdvisor,
                ragAdvisorFactory,
                healthTracker,
                sessionService,
                properties,
                toolEventEmitter,
                toolCallbacks,
                mcpToolProvider,
                toolSearchFactory,
                augmentedToolProvider,
                imageRouter,
                intentClassifier,
                visionService,
                reActAgentProvider,
                intentFeedbackAccumulatorProvider,
                contextCompressorProvider,
                documentChatServiceProvider,
                null);
    }

    /** 兼容 26 参数旧构造函数 */
    public ChatOrchestrator(
            ProviderRegistry registry,
            ContextAssembler contextAssembler,
            ObjectProvider<ChatMemory> sessionChatMemory,
            ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory,
            ObjectProvider<LongTermMemoryWriter> longTermWriter,
            ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
            ObjectProvider<RateLimiter> rateLimiter,
            UsageRecorder usageRecorder,
            ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
            ObjectProvider<ClarificationAdvisor> clarificationAdvisor,
            ObjectProvider<ReflectionAdvisor> reflectionAdvisor,
            ObjectProvider<RagAdvisorFactory> ragAdvisorFactory,
            ModelHealthTracker healthTracker,
            SessionService sessionService,
            AiProviderProperties properties,
            ToolEventEmitter toolEventEmitter,
            @Qualifier("agentToolCallbacks") ToolCallback[] toolCallbacks,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider,
            ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory,
            ObjectProvider<AugmentedToolCallbackProvider> augmentedToolProvider,
            ImageRouter imageRouter,
            IntentClassifier intentClassifier,
            VisionService visionService,
            ObjectProvider<xyz.ppmblszdp.ai.agent.plan.ReActAgent> reActAgentProvider,
            ObjectProvider<xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator> intentFeedbackAccumulatorProvider,
            ObjectProvider<xyz.ppmblszdp.ai.context.ContextCompressor> contextCompressorProvider) {
        this(
                registry,
                contextAssembler,
                sessionChatMemory,
                longTermFactory,
                longTermWriter,
                longTermProcessor,
                rateLimiter,
                usageRecorder,
                safeGuardAdvisor,
                clarificationAdvisor,
                reflectionAdvisor,
                ragAdvisorFactory,
                healthTracker,
                sessionService,
                properties,
                toolEventEmitter,
                toolCallbacks,
                mcpToolProvider,
                toolSearchFactory,
                augmentedToolProvider,
                imageRouter,
                intentClassifier,
                visionService,
                reActAgentProvider,
                intentFeedbackAccumulatorProvider,
                contextCompressorProvider,
                null);
    }

    @Override
    public void destroy() {
        Disposable d;
        int released = 0;
        while ((d = fireAndForgetSubscriptions.poll()) != null) {
            if (!d.isDisposed()) {
                d.dispose();
                released++;
            }
        }
        if (released > 0) {
            log.info("ChatOrchestrator 销毁：已释放 {} 个未完成的即发即弃订阅", released);
        }
    }

    public String resolveConversationId(ChatRequest request, String userId) {
        if (request != null && request.hasConversation()) {
            return request.conversationId();
        }
        if (properties.resolveMemory().isEnabled()) {
            return "conv-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        return null;
    }

    /** 非流式：一次性返回完整回复。 */
    public Mono<ChatResponseDto> chat(ChatRequest request, String userId) {
        ResolvedModel resolved = registry.resolve(request.provider(), request.model());
        if (hasMedia(request) && !resolved.model().supportsVision()) {
            log.warn("模型 [{}] 不支持图片 (Vision)，拦截并返回明确提示", resolved.model().id());
            return Mono.just(new ChatResponseDto(
                    "当前模型不支持图片，请切换到支持图片的模型",
                    resolved.provider().providerId(),
                    resolved.model().id(),
                    request.conversationId(),
                    null,
                    null,
                    false));
        }
        RateLimiter limiter = rateLimiter.getIfAvailable();
        if (limiter != null && !limiter.tryAcquire(userId)) {
            log.warn("非流式请求被限流 → 用户={}", userId);
            return Mono.just(new ChatResponseDto(
                    "请求过于频繁，请稍后再试。",
                    resolved.provider().providerId(),
                    resolved.model().id(),
                    request.conversationId(),
                    null,
                    null,
                    false));
        }
        if (!usageRecorder.tryReserveMonthlyQuota(userId, resolved)) {
            return Mono.just(usageRecorder.buildMonthlyQuotaExhaustedDto(resolved, request));
        }
        log.info(
                "非流式请求 → 供应商={}, 模型={}, 记忆路径={}",
                resolved.provider().providerId(),
                resolved.model().id(),
                useMemory(request));

        IntentResult intentResult = intentClassifier.classify(request, resolved);
        InteractionAnalysis interactionAnalysis = interactionAnalyzer.analyze(request.message());

        boolean memoryPath = useMemory(request);
        ChatOptions options = buildChatOptions(resolved);

        if (memoryPath) {
            ChatMemory memory = sessionChatMemory.getIfAvailable();
            if (memory == null) {
                log.warn("记忆路径已选但 ChatMemory 不可用，降级为单轮");
                return callWithoutMemory(resolved, request, options, userId, intentResult, interactionAnalysis);
            }
            ChatRequest req = ensureConversation(request);
            DocumentChatContext docContext = resolveDocumentChatContext(req, req.conversationId(), userId);
            ChatClient client = resolved.chatClient();
            MessageChatMemoryAdvisor memoryAdvisor =
                    MessageChatMemoryAdvisor.builder(memory).build();
            List<Media> mediaList = extractMedia(req);
            CallResponseSpec spec = client.prompt()
                    .system(sp -> sp.text(resolveSystemPrompt(req, intentResult, interactionAnalysis, docContext)))
                    .user(u -> {
                        u.text(req.message());
                        if (!mediaList.isEmpty()) {
                            u.media(mediaList.toArray(new Media[0]));
                        }
                    })
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, req.conversationId()))
                    .advisors(memoryAdvisor)
                    .advisors(a -> applyLongTermAdvisor(a, userId))
                    .advisors(a -> applySafeGuardAdvisor(a))
                    .advisors(a -> applyClarificationAdvisor(a, req, false))
                    .advisors(a -> applyReflectionAdvisor(a))
                    .advisors(a -> {
                        if (docContext == null) {
                            applyRagAdvisor(a, userId);
                        }
                    })
                    .options(options.mutate())
                    .call();
            return Mono.fromCallable(() -> spec.chatResponse())
                    .map(resp -> {
                        String replyText = extractText(resp);
                        healthTracker.recordSuccess(
                                resolved.provider().providerId(),
                                resolved.model().id());
                        touchSessionAsync(userId, req.conversationId(), null);
                        recordLongTermMemoryAsync(userId, req.conversationId(), req.message(), replyText);
                        ChatChunkDto.UsageDto usageDto = usageRecorder.extractUsageDto(resp, resolved, userId);
                        usageRecorder.settleUsage(
                                userId, resolved, req.conversationId(), usageDto, fireAndForgetSubscriptions);
                        return new ChatResponseDto(
                                replyText,
                                resolved.provider().providerId(),
                                resolved.model().id(),
                                req.conversationId(),
                                usageDto,
                                null,
                                false,
                                intentResult.intent().name(),
                                intentResult.label());
                    })
                    .timeout(CALL_TIMEOUT)
                    .onErrorResume(ex -> {
                        log.warn(
                                "记忆路径异常，降级为非记忆调用 → 供应商={}, 模型={}: {}",
                                resolved.provider().providerId(),
                                resolved.model().id(),
                                ex.getMessage());
                        return callWithoutMemory(resolved, request, options, userId, intentResult);
                    })
                    .onErrorResume(ex -> {
                        log.warn(
                                "主供应商请求失败 (记忆路径) → 供应商={}, 模型={}: {}",
                                resolved.provider().providerId(),
                                resolved.model().id(),
                                ex.getMessage());
                        healthTracker.recordFailure(
                                resolved.provider().providerId(),
                                resolved.model().id(),
                                ex);
                        return callWithFallback(resolved, request, ex, userId);
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        }

        return callWithoutMemory(resolved, request, options, userId, intentResult);
    }

    /** 流式：结构化 ChatChunkDto Flux。 */
    public Flux<ChatChunkDto> streamChatChunks(ChatRequest request, String userId) {
        ResolvedModel resolved = registry.resolve(request.provider(), request.model());

        ImageGenerationService imgService = imageRouter.getAvailableImageService();
        if (imgService != null) {
            ImageRouter.ImageIntentResult intent = imageRouter.detectImageIntent(request, resolved);
            if (intent.isImage()) {
                return imageRouter.streamImageGeneration(request, userId, imgService, intent.prompt());
            }
        }

        if (hasMedia(request) && !resolved.model().supportsVision()) {
            log.warn("模型 [{}] 不支持图片 (Vision)，流式拦截并返回明确提示", resolved.model().id());
            return Flux.just(ChatChunkDto.error("INVALID_ARGUMENT", "当前模型不支持图片，请切换到支持图片的模型"));
        }
        RateLimiter limiter = rateLimiter.getIfAvailable();
        if (limiter != null && !limiter.tryAcquire(userId)) {
            log.warn("流式请求被限流 → 用户={}", userId);
            ChatRateLimiter.WindowQuotaDto status = limiter.getQuotaStatus(userId);
            String retryHint =
                    status.resetAfterSeconds() > 0 ? "，请等待 " + status.resetAfterSeconds() + " 秒后重试。" : "，请稍后再试。";
            return Flux.just(ChatChunkDto.error("RATE_LIMITED", "请求过于频繁" + retryHint));
        }
        if (!usageRecorder.tryReserveMonthlyQuota(userId, resolved)) {
            return Flux.just(ChatChunkDto.error("QUOTA_EXHAUSTED", "本月对话 Token 额度已用尽，请下月再试或联系管理员提升配额。"));
        }

        IntentResult intentResult = intentClassifier.classify(request, resolved);
        InteractionAnalysis interactionAnalysis = interactionAnalyzer.analyze(request.message());

        // 意图告警自愈路由：若当前意图点踩率 > 30%，自动切换至默认模型
        xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator intentAccumulator =
                intentFeedbackAccumulatorProvider != null ? intentFeedbackAccumulatorProvider.getIfAvailable() : null;
        final ResolvedModel effectiveResolved;
        if (intentAccumulator != null
                && intentResult != null
                && intentAccumulator.isAlerting(intentResult.intent().name())) {
            ResolvedModel fallback = registry.resolve(null, null);
            if (!fallback.model().id().equals(resolved.model().id())) {
                log.warn(
                        "⚠️ [AutoHeal] 意图 [{}] 告警（点踩率 ≥ 30%），自动切换模型: {} → {}",
                        intentResult.intent().name(),
                        resolved.model().id(),
                        fallback.model().id());
                effectiveResolved = fallback;
            } else {
                effectiveResolved = resolved;
            }
        } else {
            effectiveResolved = resolved;
        }

        if (Boolean.TRUE.equals(request.reactEnabled())
                && reActAgentProvider != null
                && reActAgentProvider.getIfAvailable() != null) {
            return streamReAct(effectiveResolved, request, userId, intentResult);
        }

        // 共享会话：防止多 EDITOR 并发触发流式生成（修正点 2）
        String convId = request.conversationId();
        if (convId != null && !collabBus.tryAcquireGeneratingLock(convId, userId)) {
            return Flux.just(ChatChunkDto.error("SESSION_BUSY", "该共享会话正在生成回复，请等待当前回复完成后再发送。"));
        }

        boolean memoryPath = useMemory(request);
        ChatOptions options = buildChatOptions(effectiveResolved);
        boolean agentPath = useAgent(request, intentResult);

        if (memoryPath) {
            ChatMemory memory = sessionChatMemory.getIfAvailable();
            if (memory == null) {
                return streamChunksWithoutMemory(
                        effectiveResolved,
                        request,
                        options,
                        new AtomicReference<>(),
                        userId,
                        intentResult,
                        interactionAnalysis);
            }
            long streamStartTime = System.currentTimeMillis();
            AtomicLong firstTokenTime = new AtomicLong(-1);
            AtomicLong toolDuration = new AtomicLong(0);

            ChatRequest req = ensureConversation(request);
            DocumentChatContext docContext = resolveDocumentChatContext(req, req.conversationId(), userId);
            ChatClient client = effectiveResolved.chatClient();
            MessageChatMemoryAdvisor memoryAdvisor =
                    MessageChatMemoryAdvisor.builder(memory).build();
            StringBuilder fullContent = new StringBuilder();
            List<Media> mediaList = extractMedia(req);

            boolean[] hasEmittedFirstChunk = new boolean[] {false};
            AtomicReference<ChatChunkDto.UsageDto> lastUsage = new AtomicReference<>();
            AtomicBoolean usageSettled = new AtomicBoolean(false);

            Many<ChatChunkDto> toolSink = agentPath ? toolEventEmitter.newSink() : null;
            ChatClientRequestSpec requestSpec = client.prompt()
                    .system(sp -> sp.text(resolveSystemPrompt(req, intentResult, interactionAnalysis, docContext)))
                    .user(u -> {
                        u.text(req.message());
                        if (!mediaList.isEmpty()) {
                            u.media(mediaList.toArray(new Media[0]));
                        }
                    })
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, req.conversationId()))
                    .advisors(memoryAdvisor)
                    .advisors(a -> applyLongTermAdvisor(a, userId))
                    .advisors(a -> applySafeGuardAdvisor(a))
                    .advisors(a -> applyClarificationAdvisor(a, req, agentPath))
                    .advisors(a -> applyReflectionAdvisor(a))
                    .advisors(a -> {
                        if (docContext == null) {
                            applyRagAdvisor(a, userId);
                        }
                    })
                    .options(options.mutate());
            if (agentPath) {
                ToolCallback[] agentTools = prepareAgentTools(req.conversationId(), userId);
                requestSpec = requestSpec
                        .tools((Object[]) agentTools)
                        .toolContext(Map.of(
                                "eventSink",
                                toolSink,
                                ToolEventEmitter.CTX_EMITTER,
                                toolEventEmitter,
                                ToolEventEmitter.CTX_USER_ID,
                                userId,
                                ToolEventEmitter.CTX_TOOL_DURATION,
                                toolDuration));
            }
            Flux<ChatChunkDto> contentFlux = requestSpec.stream()
                    .chatResponse()
                    .timeout(STREAM_TIMEOUT)
                    .concatMap(resp -> {
                        boolean isFirst = !hasEmittedFirstChunk[0];
                        hasEmittedFirstChunk[0] = true;
                        healthTracker.recordSuccess(
                                effectiveResolved.provider().providerId(),
                                effectiveResolved.model().id());
                        Flux<ChatChunkDto> chunkFlux = usageRecorder.accumulateUsage(
                                processChatResponseToChunks(
                                        resp, fullContent, effectiveResolved, userId, firstTokenTime),
                                lastUsage);
                        if (isFirst) {
                            ChatChunkDto initChunk = ChatChunkDto.conversation(
                                    req.conversationId(),
                                    effectiveResolved.provider().providerId(),
                                    effectiveResolved.model().id(),
                                    false,
                                    intentResult != null ? intentResult.intent().name() : null,
                                    intentResult != null ? intentResult.label() : null,
                                    buildInteractionDto(interactionAnalysis));
                            List<ChatChunkDto> headChunks = new ArrayList<>();
                            headChunks.add(initChunk);
                            ChatChunkDto.ModelRecommendationDto recDto =
                                    computeRecommendation(intentResult, effectiveResolved, req.message());
                            if (recDto != null) {
                                headChunks.add(ChatChunkDto.recommendation(recDto));
                            }
                            if (docContext != null
                                    && docContext.citations() != null
                                    && !docContext.citations().isEmpty()) {
                                headChunks.add(ChatChunkDto.citations(docContext.citations()));
                            }
                            return Flux.concat(Flux.fromIterable(headChunks), chunkFlux);
                        }
                        return chunkFlux;
                    })
                    .concatWith(createMetricsChunk(
                            streamStartTime, firstTokenTime, toolDuration, lastUsage, fullContent, effectiveResolved))
                    .doOnComplete(() -> recordLongTermMemoryAsync(
                            userId, req.conversationId(), req.message(), fullContent.toString()))
                    .doOnCancel(() -> {
                        if (fullContent.length() > 0) {
                            recordLongTermMemoryAsync(
                                    userId, req.conversationId(), req.message(), fullContent.toString());
                        }
                    })
                    .doFinally(signalType -> {
                        log.debug(
                                "流式记忆路径订阅结束 (signal={}) → 供应商={}, 模型={}",
                                signalType,
                                effectiveResolved.provider().providerId(),
                                effectiveResolved.model().id());
                        if (usageSettled.compareAndSet(false, true)) {
                            touchSessionAsync(userId, req.conversationId(), null);
                            usageRecorder.settleUsage(
                                    userId,
                                    effectiveResolved,
                                    req.conversationId(),
                                    lastUsage.get(),
                                    fireAndForgetSubscriptions);
                            // 释放共享会话生成锁并广播最终助手消息给协作者（修正点 2）
                            collabBus.releaseGeneratingLock(req.conversationId(), userId, false);
                            collabBus.broadcastSessionStatus(req.conversationId(), "idle", userId);
                            if (fullContent.length() > 0) {
                                String msgId = "assistant-" + req.conversationId() + "-" + System.currentTimeMillis();
                                collabBus.broadcastMessageUpdated(
                                        req.conversationId(), msgId, "assistant", fullContent.toString(), userId);
                            }
                        }
                    })
                    .onErrorResume(ex -> {
                        log.warn(
                                "流式主供应商请求失败 (记忆路径) → 供应商={}, 模型={}, 首帧下发状态={}: {}",
                                effectiveResolved.provider().providerId(),
                                effectiveResolved.model().id(),
                                hasEmittedFirstChunk[0],
                                ex.getMessage());
                        healthTracker.recordFailure(
                                effectiveResolved.provider().providerId(),
                                effectiveResolved.model().id(),
                                ex);
                        if (!hasEmittedFirstChunk[0]) {
                            return streamChunksWithoutMemory(
                                    effectiveResolved,
                                    req,
                                    options,
                                    lastUsage,
                                    userId,
                                    intentResult,
                                    interactionAnalysis);
                        }
                        return Flux.just(ChatChunkDto.error("STREAM_ERROR", "输出中断：" + ex.getMessage()));
                    });

            if (agentPath && toolSink != null) {
                return Flux.merge(contentFlux, toolSink.asFlux());
            }
            return contentFlux;
        }

        return streamChunksWithoutMemory(
                effectiveResolved,
                request,
                options,
                new AtomicReference<>(),
                userId,
                intentResult,
                interactionAnalysis);
    }

    private Mono<ChatResponseDto> callWithoutMemory(
            ResolvedModel primaryResolved,
            ChatRequest request,
            ChatOptions options,
            String userId,
            IntentResult intentResult) {
        return callWithoutMemory(
                primaryResolved,
                request,
                options,
                userId,
                intentResult,
                (request != null && request.message() != null) ? interactionAnalyzer.analyze(request.message()) : null);
    }

    private Mono<ChatResponseDto> callWithoutMemory(
            ResolvedModel primaryResolved,
            ChatRequest request,
            ChatOptions options,
            String userId,
            IntentResult intentResult,
            InteractionAnalysis interactionAnalysis) {
        List<Media> mediaList = extractMedia(request);
        DocumentChatContext docContext = resolveDocumentChatContext(request, request.conversationId(), userId);
        xyz.ppmblszdp.ai.context.ContextCompressor compressor =
                contextCompressorProvider != null ? contextCompressorProvider.getIfAvailable() : null;
        xyz.ppmblszdp.ai.context.AssembleResult assembleResult = contextAssembler.assembleWithResult(
                request.message(),
                request.history(),
                resolveSystemPrompt(request, intentResult, interactionAnalysis, docContext),
                null,
                primaryResolved.model().maxContextTokens(),
                mediaList,
                compressor);
        Prompt prompt = new Prompt(assembleResult.messages(), options);

        return Mono.fromCallable(() -> {
                    ChatResponse response = primaryResolved.chatModel().call(prompt);
                    healthTracker.recordSuccess(
                            primaryResolved.provider().providerId(),
                            primaryResolved.model().id());
                    ChatChunkDto.UsageDto usageDto = usageRecorder.extractUsageDto(response, primaryResolved, userId);
                    usageRecorder.settleUsage(
                            userId, primaryResolved, request.conversationId(), usageDto, fireAndForgetSubscriptions);
                    return new ChatResponseDto(
                            extractText(response),
                            primaryResolved.provider().providerId(),
                            primaryResolved.model().id(),
                            request.conversationId(),
                            usageDto,
                            null,
                            false,
                            intentResult != null ? intentResult.intent().name() : null,
                            intentResult != null ? intentResult.label() : null);
                })
                .timeout(CALL_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn(
                            "主供应商请求失败 → 供应商={}, 模型={}: {}",
                            primaryResolved.provider().providerId(),
                            primaryResolved.model().id(),
                            ex.getMessage());
                    healthTracker.recordFailure(
                            primaryResolved.provider().providerId(),
                            primaryResolved.model().id(),
                            ex);
                    return callWithFallback(primaryResolved, request, ex, userId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ChatChunkDto> streamChunksWithoutMemory(
            ResolvedModel primaryResolved,
            ChatRequest request,
            ChatOptions options,
            AtomicReference<ChatChunkDto.UsageDto> sharedUsage,
            String userId,
            IntentResult intentResult) {
        return streamChunksWithoutMemory(
                primaryResolved,
                request,
                options,
                sharedUsage,
                userId,
                intentResult,
                (request != null && request.message() != null) ? interactionAnalyzer.analyze(request.message()) : null);
    }

    private Flux<ChatChunkDto> streamChunksWithoutMemory(
            ResolvedModel primaryResolved,
            ChatRequest request,
            ChatOptions options,
            AtomicReference<ChatChunkDto.UsageDto> sharedUsage,
            String userId,
            IntentResult intentResult,
            InteractionAnalysis interactionAnalysis) {
        long streamStartTime = System.currentTimeMillis();
        AtomicLong firstTokenTime = new AtomicLong(-1);
        AtomicLong toolDuration = new AtomicLong(0);

        List<Media> mediaList = extractMedia(request);
        DocumentChatContext docContext = resolveDocumentChatContext(request, request.conversationId(), userId);
        xyz.ppmblszdp.ai.context.ContextCompressor compressor =
                contextCompressorProvider != null ? contextCompressorProvider.getIfAvailable() : null;
        xyz.ppmblszdp.ai.context.AssembleResult assembleResult = contextAssembler.assembleWithResult(
                request.message(),
                request.history(),
                resolveSystemPrompt(request, intentResult, interactionAnalysis, docContext),
                null,
                primaryResolved.model().maxContextTokens(),
                mediaList,
                compressor);
        Prompt prompt = new Prompt(assembleResult.messages(), options);

        StringBuilder fullContent = new StringBuilder();
        AtomicReference<ChatChunkDto.UsageDto> usageAccum =
                (sharedUsage != null) ? sharedUsage : new AtomicReference<>();
        AtomicBoolean settled = new AtomicBoolean(false);

        boolean agentPath = useAgent(request, intentResult);
        Many<ChatChunkDto> toolSink = agentPath ? toolEventEmitter.newSink() : null;

        ChatClientRequestSpec requestSpec = primaryResolved.chatClient().prompt(prompt);
        if (agentPath) {
            ToolCallback[] agentTools = prepareAgentTools(request.conversationId(), userId);
            requestSpec = requestSpec
                    .tools((Object[]) agentTools)
                    .toolContext(Map.of(
                            "eventSink",
                            toolSink,
                            ToolEventEmitter.CTX_EMITTER,
                            toolEventEmitter,
                            ToolEventEmitter.CTX_USER_ID,
                            userId,
                            ToolEventEmitter.CTX_TOOL_DURATION,
                            toolDuration));
        }

        ChatChunkDto initChunk = ChatChunkDto.conversation(
                request.conversationId(),
                primaryResolved.provider().providerId(),
                primaryResolved.model().id(),
                false,
                intentResult != null ? intentResult.intent().name() : null,
                intentResult != null ? intentResult.label() : null,
                buildInteractionDto(interactionAnalysis));

        List<ChatChunkDto> headerChunks = new ArrayList<>();
        headerChunks.add(initChunk);
        ChatChunkDto.ModelRecommendationDto recDto =
                computeRecommendation(intentResult, primaryResolved, request.message());
        if (recDto != null) {
            headerChunks.add(ChatChunkDto.recommendation(recDto));
        }
        if (docContext != null
                && docContext.citations() != null
                && !docContext.citations().isEmpty()) {
            headerChunks.add(ChatChunkDto.citations(docContext.citations()));
        }
        if (assembleResult.hasCompression()) {
            var meta = assembleResult.compressionMetadata();
            String snippet = meta.summarySnippet() != null
                    ? meta.summarySnippet().replace("\"", "\\\"").replace("\n", "\\n")
                    : "";
            String metaJson = String.format(
                    "{\"compressedTurnCount\":%d,\"originalTokens\":%d,\"compressedTokens\":%d,\"level\":\"%s\",\"summarySnippet\":\"%s\",\"fallback\":%b}",
                    meta.compressedTurnCount(),
                    meta.originalTokens(),
                    meta.compressedTokens(),
                    meta.level() != null ? meta.level().name() : "LIGHT",
                    snippet,
                    meta.fallback());
            headerChunks.add(ChatChunkDto.contextCompression(metaJson));
        }

        Flux<ChatChunkDto> contentFlux = Flux.concat(
                        Flux.fromIterable(headerChunks),
                        requestSpec.stream()
                                .chatResponse()
                                .timeout(STREAM_TIMEOUT)
                                .concatMap(resp -> usageRecorder.accumulateUsage(
                                        processChatResponseToChunks(
                                                resp, fullContent, primaryResolved, userId, firstTokenTime),
                                        usageAccum)))
                .concatWith(createMetricsChunk(
                        streamStartTime, firstTokenTime, toolDuration, usageAccum, fullContent, primaryResolved));

        if (agentPath && toolSink != null) {
            Flux<ChatChunkDto> merged = Flux.merge(contentFlux, toolSink.asFlux());
            return merged.doFinally(sig -> {
                toolSink.tryEmitComplete();
                if (settled.compareAndSet(false, true)) {
                    usageRecorder.settleUsage(
                            userId,
                            primaryResolved,
                            request.conversationId(),
                            usageAccum.get(),
                            fireAndForgetSubscriptions);
                }
            });
        }
        return contentFlux.doFinally(signalType -> {
            if (settled.compareAndSet(false, true)) {
                usageRecorder.settleUsage(
                        userId,
                        primaryResolved,
                        request.conversationId(),
                        usageAccum.get(),
                        fireAndForgetSubscriptions);
            }
        });
    }

    private Mono<ChatChunkDto> createMetricsChunk(
            long streamStartTime,
            AtomicLong firstTokenTime,
            AtomicLong toolDuration,
            AtomicReference<ChatChunkDto.UsageDto> lastUsage,
            StringBuilder fullContent,
            ResolvedModel resolved) {
        return Mono.fromSupplier(() -> {
            long totalDuration = Math.max(1, System.currentTimeMillis() - streamStartTime);
            long ftTime = firstTokenTime.get();
            long ttft = (ftTime > 0) ? Math.max(0, ftTime - streamStartTime) : totalDuration;
            long toolDur = toolDuration != null ? toolDuration.get() : 0;
            ChatChunkDto.UsageDto usage = lastUsage != null ? lastUsage.get() : null;
            int completionTokens;
            boolean isEstimated;
            if (usage != null && usage.completionTokens() > 0) {
                completionTokens = usage.completionTokens();
                isEstimated = false;
            } else {
                completionTokens = Math.max(1, (int) Math.ceil(fullContent.length() / 3.5));
                isEstimated = true;
            }
            double tokensPerSecond =
                    ModelPerformanceTracker.calculateTokensPerSecond(completionTokens, totalDuration, ttft, toolDur);
            performanceTracker.record(
                    resolved.provider().providerId(),
                    resolved.model().id(),
                    ttft,
                    tokensPerSecond,
                    totalDuration,
                    toolDur,
                    completionTokens,
                    isEstimated);
            return ChatChunkDto.metrics(ttft, tokensPerSecond, totalDuration, toolDur, isEstimated);
        });
    }

    private Mono<ChatResponseDto> callWithFallback(
            ResolvedModel primaryResolved, ChatRequest request, Throwable primaryEx, String userId) {
        String fbProvider = properties != null ? properties.fallbackProvider() : null;
        String fbModel = properties != null ? properties.fallbackModel() : null;
        ResolvedModel fallbackResolved =
                registry.resolveFallback(primaryResolved.provider().providerId(), fbProvider, fbModel);
        if (fallbackResolved != null) {
            log.info(
                    "触发自动降级熔断机制: [{}] -> [{}]",
                    primaryResolved.provider().providerId(),
                    fallbackResolved.provider().providerId());

            List<Media> mediaList = extractMedia(request);
            ChatOptions fallbackOpts = ChatOptionsFactory.forProvider(fallbackResolved, 0.2);
            List<Message> messages = contextAssembler.assemble(
                    request.message(),
                    request.history(),
                    request.systemPrompt(),
                    null,
                    fallbackResolved.model().maxContextTokens(),
                    mediaList);
            Prompt prompt = new Prompt(messages, fallbackOpts);

            return Mono.fromCallable(() -> {
                        ChatResponse resp = fallbackResolved.chatModel().call(prompt);
                        healthTracker.recordSuccess(
                                fallbackResolved.provider().providerId(),
                                fallbackResolved.model().id());
                        ChatChunkDto.UsageDto usageDto = usageRecorder.extractUsageDto(resp, fallbackResolved, userId);
                        usageRecorder.settleUsage(
                                userId,
                                fallbackResolved,
                                request.conversationId(),
                                usageDto,
                                fireAndForgetSubscriptions);
                        return new ChatResponseDto(
                                extractText(resp),
                                fallbackResolved.provider().providerId(),
                                fallbackResolved.model().id(),
                                request.conversationId(),
                                usageDto,
                                null,
                                true);
                    })
                    .timeout(CALL_TIMEOUT)
                    .onErrorResume(fbEx -> {
                        log.warn(
                                "备用供应商 [{}] 调用亦失败: {}",
                                fallbackResolved.provider().providerId(),
                                fbEx.getMessage());
                        return Mono.just(new ChatResponseDto(
                                "上游供应商响应超时/异常，请稍后再试。",
                                primaryResolved.provider().providerId(),
                                primaryResolved.model().id(),
                                request.conversationId(),
                                null,
                                null,
                                false));
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.just(new ChatResponseDto(
                "上游供应商响应超时/异常，请稍后再试。",
                primaryResolved.provider().providerId(),
                primaryResolved.model().id(),
                request.conversationId(),
                null,
                null,
                false));
    }

    private Flux<ChatChunkDto> processChatResponseToChunks(
            ChatResponse resp,
            StringBuilder fullContent,
            ResolvedModel resolved,
            String userId,
            AtomicLong firstTokenTime) {
        if (resp == null) return Flux.empty();
        List<ChatChunkDto> chunks = new ArrayList<>();

        String reasoning = extractReasoning(resp);
        if (reasoning != null && !reasoning.isEmpty()) {
            if (firstTokenTime != null) {
                firstTokenTime.compareAndSet(-1, System.currentTimeMillis());
            }
            chunks.add(ChatChunkDto.reasoning(reasoning));
        }

        String text = extractText(resp);
        if (text != null && !text.isEmpty()) {
            if (firstTokenTime != null) {
                firstTokenTime.compareAndSet(-1, System.currentTimeMillis());
            }
            fullContent.append(text);
            chunks.add(ChatChunkDto.content(text));
        }

        ChatChunkDto.UsageDto usageDto = usageRecorder.extractUsageDto(resp, resolved, userId);
        if (usageDto != null) {
            chunks.add(ChatChunkDto.usage(usageDto));
        }

        return Flux.fromIterable(chunks);
    }

    private String extractReasoning(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) return null;
        Map<String, Object> metadata = resp.getResult().getOutput().getMetadata();
        if (metadata != null) {
            Object r = metadata.get("reasoning_content");
            if (r == null) r = metadata.get("reasoning");
            if (r == null) r = metadata.get("thinking");
            if (r instanceof String s && !s.isEmpty()) return s;
        }
        return null;
    }

    private String extractText(ChatResponse result) {
        if (result == null || result.getResult() == null) return "";
        var output = result.getResult().getOutput();
        if (output == null) return "";
        String text = output.getText();
        return (text == null) ? "" : text;
    }

    private ToolCallback[] resolveMcpToolCallbacks() {
        SyncMcpToolCallbackProvider provider = mcpToolProvider != null ? mcpToolProvider.getIfAvailable() : null;
        if (provider == null) return new ToolCallback[0];
        ToolCallback[] mcpTools = provider.getToolCallbacks();
        if (mcpTools == null || mcpTools.length == 0) return new ToolCallback[0];
        log.info("已加载远程 MCP 工具 {} 个", mcpTools.length);
        return mcpTools;
    }

    private ToolCallback[] resolveCustomToolCallbacks(String userId) {
        xyz.ppmblszdp.ai.customtool.service.CustomToolService service =
                customToolServiceProvider != null ? customToolServiceProvider.getIfAvailable() : null;
        if (service == null) return new ToolCallback[0];
        List<ToolCallback> list = service.getCompiledTools(userId);
        return list != null ? list.toArray(new ToolCallback[0]) : new ToolCallback[0];
    }

    private ToolCallback[] prepareAgentTools(String conversationId, String userId) {
        ToolCallback[] local = toolCallbacks != null ? toolCallbacks : new ToolCallback[0];
        ToolCallback[] custom = resolveCustomToolCallbacks(userId);
        ToolCallback[] remote = resolveMcpToolCallbacks();

        // 合并本地原生工具与用户自定义工具
        ToolCallback[] combinedLocal;
        if (custom.length == 0) {
            combinedLocal = local;
        } else if (local.length == 0) {
            combinedLocal = custom;
        } else {
            combinedLocal = new ToolCallback[local.length + custom.length];
            System.arraycopy(local, 0, combinedLocal, 0, local.length);
            System.arraycopy(custom, 0, combinedLocal, local.length, custom.length);
        }

        boolean augmentMcp = properties != null && properties.resolveAgent().isAugmentMcpTools();
        ToolCallback[] merged = augmentedToolCallbackProvider.wrapTools(combinedLocal, remote, augmentMcp);
        ToolSearchAdvisorFactory factory = toolSearchFactory != null ? toolSearchFactory.getIfAvailable() : null;
        if (factory != null && factory.shouldApply(merged, combinedLocal.length, remote.length)) {
            return factory.processTools(merged, combinedLocal.length, remote.length, conversationId);
        }
        return merged;
    }

    private boolean useAgent(ChatRequest request, IntentResult intentResult) {
        if (request != null && request.agentEnabled() != null) {
            return agentEnabled && request.agentEnabled();
        }
        return agentEnabled && (intentResult != null && intentResult.enableTools());
    }

    private boolean useMemory(ChatRequest request) {
        return memoryEnabled && request.hasConversation();
    }

    private ChatRequest ensureConversation(ChatRequest request) {
        if (request.hasConversation()) return request;
        String convId = "conv-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        return request.withConversationId(convId);
    }

    private boolean hasMedia(ChatRequest request) {
        return request != null && request.hasMedia();
    }

    private List<Media> extractMedia(ChatRequest request) {
        if (request == null || !request.hasMedia()) return List.of();
        return visionService.extractMedia(request.media(), request.mediaUrls());
    }

    private DocumentChatContext resolveDocumentChatContext(ChatRequest request, String conversationId, String userId) {
        if (documentChatServiceProvider == null || request == null) return null;
        DocumentChatService docChatService = documentChatServiceProvider.getIfAvailable();
        if (docChatService == null) return null;

        boolean shouldQuery = request.isDocumentChat();
        if (!shouldQuery && conversationId != null && !conversationId.isBlank()) {
            try {
                var attached = docChatService.getSessionDocuments(conversationId, userId);
                shouldQuery = (attached != null && !attached.isEmpty());
            } catch (Exception ignored) {
                // ignore
            }
        }

        if (shouldQuery && conversationId != null && !conversationId.isBlank()) {
            return docChatService.retrieveStrictContext(request.message(), conversationId, request.docIds(), userId, 6);
        }
        return null;
    }

    private ChatChunkDto.InteractionMetadataDto buildInteractionDto(InteractionAnalysis analysis) {
        if (analysis == null || analysis.state() == InteractionState.NEUTRAL) {
            return null;
        }
        return new ChatChunkDto.InteractionMetadataDto(
                analysis.state().name(),
                analysis.state().getLabel(),
                analysis.signals().stream().map(s -> s.name()).toList(),
                analysis.strategies().stream().map(s -> s.name()).toList());
    }

    private String resolveSystemPrompt(
            ChatRequest request,
            IntentResult intentResult,
            InteractionAnalysis interactionAnalysis,
            DocumentChatContext docContext) {
        String personaBlock = "";
        if (request != null
                && request.personaId() != null
                && !request.personaId().isBlank()) {
            xyz.ppmblszdp.ai.persona.service.PersonaStoreService personaStore =
                    personaStoreServiceProvider != null ? personaStoreServiceProvider.getIfAvailable() : null;
            if (personaStore != null) {
                xyz.ppmblszdp.ai.persona.dto.PersonaDto persona = personaStore.getPersona(request.personaId(), null);
                if (persona != null
                        && persona.systemPrompt() != null
                        && !persona.systemPrompt().isBlank()) {
                    personaBlock = "【🎭 当前智能体角色设定: " + persona.name() + " (" + persona.category() + ")】\n"
                            + persona.systemPrompt() + "\n\n";
                }
            }
        }

        if (docContext != null) {
            DocumentChatService docChatService =
                    documentChatServiceProvider != null ? documentChatServiceProvider.getIfAvailable() : null;
            if (docChatService != null) {
                String baseStrict =
                        docChatService.buildStrictSystemPrompt(request != null ? request.systemPrompt() : null);
                String docBlock = docContext.hasContext()
                        ? "\n\n【📄 会话专属文档上下文】:\n" + docContext.formattedContext()
                        : "\n\n【📄 会话专属文档上下文】:\n(当前会话文档中未检索到与用户问题相关的任何事实依据。请严格按照【自动拒答机制】直接拒答，切勿编造。)";
                String promptWithDoc = personaBlock + baseStrict + docBlock;
                String interactionPolicy = InteractionPromptPolicy.buildSystemPromptPolicy(interactionAnalysis);
                return (interactionPolicy != null && !interactionPolicy.isBlank())
                        ? promptWithDoc + "\n\n" + interactionPolicy
                        : promptWithDoc;
            }
        }

        String base = (request != null
                        && request.systemPrompt() != null
                        && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : contextAssembler.defaultSystemPrompt();
        String combined = personaBlock + base;
        if (intentResult != null
                && intentResult.systemPromptTemplate() != null
                && !intentResult.systemPromptTemplate().isBlank()) {
            return combined + "\n\n" + intentResult.systemPromptTemplate();
        }
        return combined;
    }

    private void recordLongTermMemoryAsync(
            String userId, String conversationId, String userMessage, String assistantReply) {
        if (!memoryEnabled || userId == null || userId.isBlank() || userMessage == null || userMessage.isBlank())
            return;

        LongTermMemoryProcessor processor = longTermProcessor.getIfAvailable();
        if (processor != null) {
            Disposable d1 = Mono.fromRunnable(
                            () -> processor.processTurn(userId, conversationId, userMessage, assistantReply))
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
                    .doOnError(ex -> log.warn(
                            "长期记忆处理写入失败 [userId={}, conversationId={}]: {}", userId, conversationId, ex.getMessage()))
                    .onErrorComplete()
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
            fireAndForgetSubscriptions.add(d1);
            return;
        }

        LongTermMemoryWriter writer = longTermWriter.getIfAvailable();
        if (writer == null) return;

        String content = "【用户提问】: " + userMessage + "\n【AI回复】: " + (assistantReply != null ? assistantReply : "");
        Disposable d2 = Mono.fromRunnable(() -> writer.write(userId, content))
                .timeout(Duration.ofSeconds(10))
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
                .doOnError(ex -> log.warn("长期记忆写入失败 [userId={}]: {}", userId, ex.getMessage()))
                .onErrorComplete()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        fireAndForgetSubscriptions.add(d2);
    }

    private void touchSessionAsync(String userId, String conversationId, String fallbackTitle) {
        if (sessionService != null && conversationId != null && !conversationId.isBlank()) {
            Disposable sub = Mono.fromRunnable(() -> sessionService.touchSession(conversationId, userId, fallbackTitle))
                    .doOnError(ex -> log.warn("更新会话活跃时间失败 [conversationId={}]: {}", conversationId, ex.getMessage()))
                    .onErrorComplete()
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
            fireAndForgetSubscriptions.add(sub);
        }
    }

    private void applyLongTermAdvisor(ChatClient.AdvisorSpec advisorSpec, String userId) {
        LongTermMemoryAdvisorFactory factory = longTermFactory.getIfAvailable();
        if (factory != null) {
            Advisor advisor = factory.forUser(userId);
            if (advisor != null) {
                advisorSpec.advisors(advisor);
            }
        }
    }

    private void applySafeGuardAdvisor(ChatClient.AdvisorSpec advisorSpec) {
        SafeGuardAdvisor advisor = safeGuardAdvisor.getIfAvailable();
        if (advisor != null) {
            advisorSpec.advisors(advisor);
        }
    }

    private void applyClarificationAdvisor(ChatClient.AdvisorSpec advisorSpec, ChatRequest req, boolean isAgent) {
        ClarificationAdvisor advisor = clarificationAdvisor != null ? clarificationAdvisor.getIfAvailable() : null;
        if (advisor != null) {
            if (req != null && req.clarificationMode() != null) {
                advisorSpec.param(ClarificationAdvisor.CTX_CLARIFICATION_MODE, req.clarificationMode());
            }
            advisorSpec.param(ClarificationAdvisor.CTX_IS_AGENT, isAgent);
            advisorSpec.advisors(advisor);
        }
    }

    private void applyReflectionAdvisor(ChatClient.AdvisorSpec advisorSpec) {
        ReflectionAdvisor advisor = reflectionAdvisor != null ? reflectionAdvisor.getIfAvailable() : null;
        if (advisor != null) {
            advisorSpec.advisors(advisor);
        }
    }

    private void applyRagAdvisor(ChatClient.AdvisorSpec advisorSpec, String userId) {
        RagAdvisorFactory factory = ragAdvisorFactory.getIfAvailable();
        if (factory != null) {
            Advisor advisor = factory.forUser(userId, null);
            if (advisor != null) {
                advisorSpec.advisors(advisor);
            }
        }
    }

    private ChatOptions buildChatOptions(ResolvedModel resolved) {
        return ChatOptionsFactory.forProvider(resolved, 0.2);
    }

    /**
     * ReAct 闭环任务规划流式执行器。
     */
    public Flux<ChatChunkDto> streamReAct(
            ResolvedModel resolved, ChatRequest request, String userId, IntentResult intentResult) {

        xyz.ppmblszdp.ai.agent.plan.ReActAgent agent =
                reActAgentProvider != null ? reActAgentProvider.getIfAvailable() : null;
        if (agent == null) {
            return Flux.just(ChatChunkDto.error("REACT_UNAVAILABLE", "ReAct 规划引擎未初始化"));
        }

        ChatRequest req = ensureConversation(request);
        Many<ChatChunkDto> sink =
                reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        AtomicBoolean isAborted = new AtomicBoolean(false);

        ChatChunkDto initChunk = ChatChunkDto.conversation(
                req.conversationId(),
                resolved.provider().providerId(),
                resolved.model().id(),
                false,
                intentResult != null ? intentResult.intent().name() : "PLANNING",
                intentResult != null ? intentResult.label() : "多步任务规划");
        sink.tryEmitNext(initChunk);

        ToolCallback[] allTools = prepareAgentTools(req.conversationId(), userId);
        List<ToolCallback> toolList = allTools != null ? java.util.Arrays.asList(allTools) : List.of();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                agent.run(req.message(), req.systemPrompt(), toolList, resolved.chatClient(), sink, isAborted, 8);
            } catch (Exception e) {
                log.error("ReAct 执行异常: {}", e.getMessage(), e);
                sink.tryEmitNext(ChatChunkDto.error("REACT_ERROR", "任务规划执行异常: " + e.getMessage()));
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux().doOnCancel(() -> {
            log.info("客户端取消 ReAct 连接 (convId={})", req.conversationId());
            isAborted.set(true);
        });
    }

    private ChatChunkDto.ModelRecommendationDto computeRecommendation(
            IntentResult intentResult, xyz.ppmblszdp.ai.registry.ResolvedModel resolved, String userMessage) {
        if (modelRecommender == null || intentResult == null) {
            return null;
        }
        try {
            var rec = modelRecommender.recommend(intentResult.intent(), resolved, userMessage);
            if (rec != null) {
                return new ChatChunkDto.ModelRecommendationDto(
                        rec.providerId(), rec.modelId(), rec.displayName(), rec.reason(), rec.estimatedCostRmb());
            }
        } catch (Exception e) {
            log.debug("计算模型推荐异常（已降级忽略）: {}", e.getMessage());
        }
        return null;
    }
}
