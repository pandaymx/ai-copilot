package xyz.ppmblszdp.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import java.time.Duration;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig;
import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.repository.UsageRepository;
import xyz.ppmblszdp.ai.safeguard.SafeGuardAdvisor;
import xyz.ppmblszdp.ai.rag.advisor.RagAdvisorConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天业务服务。
 *
 * <p>
 * 三种路径：
 * <ol>
 * <li><b>记忆驱动</b>（有 conversationId 且 app.ai.memory.enabled=true）：走 ChatClient +
 * Advisor
 * （MessageChatMemoryAdvisor 会话记忆 + 长期记忆 Advisor），conversationId 在每次请求时动态注入，
 * 多线程并发不会串线；</li>
 * <li><b>旧 history 模式</b>（无 conversationId）：沿用 ContextAssembler
 * 组装历史，完全向后兼容；</li>
 * <li><b>单轮</b>（无 conversationId 且无 history）：仅当前消息。</li>
 * </ol>
 *
 * <p>
 * 记忆相关异常降级为「无记忆单次对话」，不向上抛 5xx；ContextAssembler 的 Token 预算裁剪在旧路径生效，
 * 记忆路径由 MessageChatMemoryAdvisor 的 RETRIEVE_SIZE 控制提取条数。
 */
@Service
public class ChatService implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
	private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

	private final ProviderRegistry registry;
	private final ContextAssembler contextAssembler;
	private final ObjectProvider<ChatMemory> sessionChatMemory;
	private final ObjectProvider<LongTermMemoryConfig.LongTermMemoryAdvisorFactory> longTermFactory;
	private final ObjectProvider<LongTermMemoryConfig.LongTermMemoryWriter> longTermWriter;
	private final ObjectProvider<LongTermMemoryProcessor> longTermProcessor;
	private final ObjectProvider<ChatRateLimiter.RateLimiter> rateLimiter;
	private final ObjectProvider<UsageQuotaChecker.UsageQuota> usageQuota;
	private final UsageRepository usageRepository;
	private final ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor;
	private final ObjectProvider<RagAdvisorConfig.RagAdvisorFactory> ragAdvisorFactory;
	private final ModelHealthTracker healthTracker;
	private final SessionService sessionService;
	private final AiProviderProperties properties;
	private final boolean memoryEnabled;

	/**
	 * 持有所有「即发即弃」订阅（touchSession / 长期记忆写入等）返回的 Disposable，
	 * 便于在 Bean 销毁时统一 dispose，避免应用关闭/上下文销毁时订阅空转或泄漏。
	 * 使用并发队列，订阅在 complete 后自动置为 disposed，cleanup 时跳过即可。
	 */
	private final ConcurrentLinkedQueue<reactor.core.Disposable> fireAndForgetSubscriptions =
			new ConcurrentLinkedQueue<>();

	public ChatService(
			ProviderRegistry registry,
			ContextAssembler contextAssembler,
			ObjectProvider<ChatMemory> sessionChatMemory,
			ObjectProvider<LongTermMemoryConfig.LongTermMemoryAdvisorFactory> longTermFactory,
			ObjectProvider<LongTermMemoryConfig.LongTermMemoryWriter> longTermWriter,
			ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
			ObjectProvider<ChatRateLimiter.RateLimiter> rateLimiter,
			ObjectProvider<UsageQuotaChecker.UsageQuota> usageQuota,
			UsageRepository usageRepository,
			ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
			ObjectProvider<RagAdvisorConfig.RagAdvisorFactory> ragAdvisorFactory,
			ModelHealthTracker healthTracker,
			SessionService sessionService,
			AiProviderProperties properties) {
		this.registry = registry;
		this.contextAssembler = contextAssembler;
		this.sessionChatMemory = sessionChatMemory;
		this.longTermFactory = longTermFactory;
		this.longTermWriter = longTermWriter;
		this.longTermProcessor = longTermProcessor;
		this.rateLimiter = rateLimiter;
		this.usageQuota = usageQuota;
		this.usageRepository = usageRepository;
		this.safeGuardAdvisor = safeGuardAdvisor;
		this.ragAdvisorFactory = ragAdvisorFactory;
		this.healthTracker = healthTracker;
		this.sessionService = sessionService;
		this.properties = properties;
		this.memoryEnabled = properties.resolveMemory().isEnabled();
	}

	/** 非流式：一次性返回完整回复。userId 来自服务端受信任身份，用于限流/记忆隔离。 */
	public Mono<ChatResponseDto> chat(ChatRequest request, String userId) {
		ResolvedModel resolved = registry.resolve(request.provider(), request.model());
		ChatRateLimiter.RateLimiter limiter = rateLimiter.getIfAvailable();
		if (limiter != null && !limiter.tryAcquire(userId)) {
			log.warn("非流式请求被限流 → 用户={}", userId);
			return Mono.just(new ChatResponseDto(
					"请求过于频繁，请稍后再试。", resolved.provider().providerId(),
					resolved.model().id(), request.conversationId(), null, null));
		}
		// 用户级月度 Token 配额预扣（请求发起时无法预知真实 token 数，仅做预扣拦截）
		if (!tryReserveMonthlyQuota(userId, resolved)) {
			return Mono.just(buildMonthlyQuotaExhaustedDto(resolved, request));
		}
		log.info("非流式请求 → 供应商={}, 模型={}, 记忆路径={}",
				resolved.provider().providerId(), resolved.model().id(), useMemory(request));

		boolean memoryPath = useMemory(request);
		ChatOptions options = buildChatOptions(resolved);

		if (memoryPath) {
			ChatMemory memory = sessionChatMemory.getIfAvailable();
			if (memory == null) {
				log.warn("记忆路径已选但 ChatMemory 不可用，降级为单轮");
				return callWithoutMemory(resolved, request, options, userId);
			}
			ChatRequest req = ensureConversation(request);
			ChatClient client = resolved.chatClient();
			MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
			List<Media> mediaList = convertMediaList(req.media());
			ChatClient.CallResponseSpec spec = client.prompt()
					.system(sp -> sp.text(resolveSystemPrompt(req)))
					.user(u -> {
						u.text(req.message());
						if (!mediaList.isEmpty()) {
							u.media(mediaList.toArray(new Media[0]));
						}
					})
					.advisors(a -> a
							.param(ChatMemory.CONVERSATION_ID, req.conversationId()))
					.advisors(memoryAdvisor)
					.advisors(a -> applyLongTermAdvisor(a, userId))
					.advisors(a -> applySafeGuardAdvisor(a))
					.advisors(a -> applyRagAdvisor(a, userId))
					.options(options.mutate())
					.call();
			return Mono.fromCallable(() -> spec.chatResponse())
					.map(resp -> {
						String replyText = extractText(resp);
						meterUsageAsync(userId, resolved, req.conversationId(), extractUsageDto(resp, resolved));
						recordLongTermMemoryAsync(userId, req.conversationId(), req.message(), replyText);
						healthTracker.recordSuccess(resolved.provider().providerId(), resolved.model().id());
						return new ChatResponseDto(
								replyText,
								resolved.provider().providerId(),
								resolved.model().id(),
								req.conversationId(),
								null,
								null,
								false);
					})
					.onErrorResume(ex -> {
						log.warn("主供应商请求失败 (记忆路径) → 供应商={}, 模型={}: {}",
								resolved.provider().providerId(), resolved.model().id(), ex.getMessage());
						healthTracker.recordFailure(resolved.provider().providerId(), resolved.model().id(), ex);
						return callWithFallback(resolved, request, ex, userId);
					})
					.subscribeOn(Schedulers.boundedElastic());
		}
		return callWithoutMemory(resolved, request, options, userId);
	}

	/** 流式：结构化 ChatChunkDto Flux（包含思考过程 reasoning 与 token 用量）。userId 来自服务端受信任身份。 */
	public Flux<ChatChunkDto> streamChatChunks(ChatRequest request, String userId) {
		ResolvedModel resolved = registry.resolve(request.provider(), request.model());
		ChatRateLimiter.RateLimiter limiter = rateLimiter.getIfAvailable();
		if (limiter != null && !limiter.tryAcquire(userId)) {
			log.warn("流式请求被限流 → 用户={}", userId);
			return Flux.just(ChatChunkDto.error("RATE_LIMIT", "请求过于频繁，请稍后再试。"));
		}
		// 用户级月度 Token 配额预扣（请求发起时无法预知真实 token 数，仅做预扣拦截）
		if (!tryReserveMonthlyQuota(userId, resolved)) {
			return Flux.just(ChatChunkDto.error("RATE_LIMIT", "本月对话额度已用尽，请下月再试或联系管理员提升配额。"));
		}

		boolean memoryPath = useMemory(request);
		ChatOptions options = buildChatOptions(resolved);

		if (memoryPath) {
			ChatMemory memory = sessionChatMemory.getIfAvailable();
			if (memory == null) {
				return streamChunksWithoutMemory(resolved, request, options, new AtomicReference<>(), userId);
			}
			ChatRequest req = ensureConversation(request);
			ChatClient client = resolved.chatClient();
			MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
			StringBuilder fullContent = new StringBuilder();
			List<Media> mediaList = convertMediaList(req.media());

			boolean[] hasEmittedFirstChunk = new boolean[] { false };
			// 流式用量累加（跨主/备用供应商共享），doFinally 统一落库一次
			AtomicReference<ChatChunkDto.UsageDto> lastUsage = new AtomicReference<>();
			AtomicBoolean usageSettled = new AtomicBoolean(false);

			return client.prompt()
					.system(sp -> sp.text(resolveSystemPrompt(req)))
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
					.advisors(a -> applyRagAdvisor(a, userId))
					.options(options.mutate())
					.stream()
					.chatResponse()
					.timeout(STREAM_TIMEOUT)
					.concatMap(resp -> {
						boolean isFirst = !hasEmittedFirstChunk[0];
						hasEmittedFirstChunk[0] = true;
						healthTracker.recordSuccess(resolved.provider().providerId(), resolved.model().id());
						Flux<ChatChunkDto> chunkFlux = accumulateUsage(
								processChatResponseToChunks(resp, fullContent, resolved), lastUsage);
						if (isFirst) {
							ChatChunkDto initChunk = ChatChunkDto.conversation(
									req.conversationId(),
									resolved.provider().providerId(),
									resolved.model().id(),
									false);
							return Flux.concat(Flux.just(initChunk), chunkFlux);
						}
						return chunkFlux;
					})
					.doOnComplete(() -> recordLongTermMemoryAsync(userId, req.conversationId(),
							req.message(), fullContent.toString()))
					.doOnCancel(() -> {
						if (fullContent.length() > 0) {
							recordLongTermMemoryAsync(userId, req.conversationId(), req.message(),
									fullContent.toString());
						}
					})
					// doFinally 作为兜底：无论正常完成、客户端取消还是异常，均确保底层订阅被释放，
					// 并触发用量落库 + 月度配额校准（AtomicBoolean 保证每次请求仅一次）。
					.doFinally(signalType -> {
						log.debug("流式记忆路径订阅结束 (signal={}) → 供应商={}, 模型={}",
								signalType, resolved.provider().providerId(), resolved.model().id());
						if (usageSettled.compareAndSet(false, true)) {
							settleUsage(userId, resolved, req.conversationId(), lastUsage.get());
						}
					})
					.onErrorResume(ex -> {
						log.warn("流式主供应商请求失败 (记忆路径) → 供应商={}, 模型={}, 首帧下发状态={}: {}",
								resolved.provider().providerId(), resolved.model().id(), hasEmittedFirstChunk[0],
								ex.getMessage());
						healthTracker.recordFailure(resolved.provider().providerId(), resolved.model().id(), ex);

						// 首帧后 (Mid-stream)：如果已经输出了部分内容，不透明重试，直接抛出 error Chunk 停止
						if (hasEmittedFirstChunk[0]) {
							return Flux.just(ChatChunkDto.error("STREAM_INTERRUPTED", "连接异常断开，回复受阻。"));
						}

						// 首帧前 (Pre-flight)：在输出之前发生异常，可以透明无感切 Fallback 模型
						ResolvedModel fallbackResolved = registry.resolveFallback(
								resolved.provider().providerId(),
								properties.fallbackProvider(),
								properties.fallbackModel());
						if (fallbackResolved != null) {
							log.warn("流式主供应商 [{}] 首帧前失败，透明降级切换至备用供应商 [{}]",
									resolved.provider().providerId(), fallbackResolved.provider().providerId());
							ChatOptions fallbackOpts = ChatOptionsFactory.forProvider(fallbackResolved, 0.2);
							// 独立引用 + 内部 doFinally，避免与记忆路径主路径 doFinally 双重落库
							return streamChunksWithoutMemory(fallbackResolved, request, fallbackOpts, new AtomicReference<>(), userId);
						}
						return Flux.just(ChatChunkDto.error("UPSTREAM_ERROR", "上游供应商响应异常，请稍后再试。"));
					});
		}
		return streamChunksWithoutMemory(resolved, request, options, new AtomicReference<>(), userId);
	}

	public Flux<String> streamChat(ChatRequest request, String userId) {
		return streamChatChunks(request, userId)
				.filter(c -> "content".equals(c.type()) && c.content() != null)
				.map(ChatChunkDto::content);
	}

	private Flux<ChatChunkDto> streamChunksWithoutMemory(ResolvedModel resolved, ChatRequest request,
			ChatOptions options, AtomicReference<ChatChunkDto.UsageDto> usageAccum, String userId) {
		List<Media> mediaList = convertMediaList(request.media());
		List<Message> messages = contextAssembler.assemble(
				request.message(), request.history(), request.systemPrompt(),
				null, resolved.model().maxContextTokens(), mediaList);
		Prompt prompt = new Prompt(messages, options);
		StringBuilder fullContent = new StringBuilder();

		// 每条流式路径独立的 doFinally + AtomicBoolean，确保用量落库与配额校准仅执行一次，
		// 无论正常完成、客户端取消还是异常结束（与记忆路径的 doFinally 互不干扰，避免双重落库）
		AtomicBoolean settled = new AtomicBoolean(false);
		return resolved.chatModel().stream(prompt)
				.timeout(STREAM_TIMEOUT)
				.concatMap(resp -> accumulateUsage(
						processChatResponseToChunks(resp, fullContent, resolved), usageAccum))
				.doFinally(signalType -> {
					if (settled.compareAndSet(false, true)) {
						settleUsage(userId, resolved, request.conversationId(), usageAccum.get());
					}
				});
	}

	private Flux<ChatChunkDto> processChatResponseToChunks(ChatResponse resp, StringBuilder fullContent,
			ResolvedModel resolved) {
		if (resp == null)
			return Flux.empty();
		List<ChatChunkDto> chunks = new ArrayList<>();

		// 1. 提取推理/思考文本 (DeepSeek R1 / Qwen Reasoning / Spring AI Output)
		String reasoning = extractReasoning(resp);
		if (reasoning != null && !reasoning.isEmpty()) {
			chunks.add(ChatChunkDto.reasoning(reasoning));
		}

		// 2. 提取正文增量内容
		String text = extractText(resp);
		if (text != null && !text.isEmpty()) {
			fullContent.append(text);
			chunks.add(ChatChunkDto.content(text));
		}

		// 3. 提取 Usage (Prompt / Completion / Total Tokens 及预估费用)
		ChatChunkDto.UsageDto usageDto = extractUsageDto(resp, resolved);
		if (usageDto != null) {
			chunks.add(ChatChunkDto.usage(usageDto));
		}

		return Flux.fromIterable(chunks);
	}

	private String extractReasoning(ChatResponse resp) {
		if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null)
			return null;
		Map<String, Object> metadata = resp.getResult().getOutput().getMetadata();
		if (metadata != null) {
			Object r = metadata.get("reasoning_content");
			if (r == null)
				r = metadata.get("reasoning");
			if (r == null)
				r = metadata.get("thinking");
			if (r instanceof String s && !s.isEmpty())
				return s;
		}
		return null;
	}

	private ChatChunkDto.UsageDto extractUsageDto(ChatResponse resp, ResolvedModel resolved) {
		if (resp == null || resp.getMetadata() == null || resp.getMetadata().getUsage() == null) {
			log.trace("LLM 响应未包含 Usage 元数据");
			return null;
		}
		var u = resp.getMetadata().getUsage();
		int prompt = u.getPromptTokens() != null ? u.getPromptTokens().intValue() : 0;
		int completion = u.getCompletionTokens() != null ? u.getCompletionTokens().intValue() : 0;
		int total = u.getTotalTokens() != null ? u.getTotalTokens().intValue() : (prompt + completion);
		if (total == 0) {
			log.debug("LLM 响应包含 Usage 元数据但 Token 用量全 0 (首包/中间块)，跳过生成 UsageDto");
			return null;
		}

		ModelDescriptor descriptor = (resolved != null) ? resolved.model() : null;
		BigDecimal inputPrice = (descriptor != null && descriptor.inputPricePerK() != null)
				? descriptor.inputPricePerK()
				: ModelDescriptor.DEFAULT_INPUT_PRICE;
		BigDecimal outputPrice = (descriptor != null && descriptor.outputPricePerK() != null)
				? descriptor.outputPricePerK()
				: ModelDescriptor.DEFAULT_OUTPUT_PRICE;

		BigDecimal promptCost = BigDecimal.valueOf(prompt)
				.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
				.multiply(inputPrice);

		BigDecimal completionCost = BigDecimal.valueOf(completion)
				.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
				.multiply(outputPrice);

		BigDecimal totalCostRmb = promptCost.add(completionCost).setScale(4, RoundingMode.HALF_UP);
		return new ChatChunkDto.UsageDto(prompt, completion, total, totalCostRmb.doubleValue());
	}

	// ====================== Token 用量计量与月度配额 ======================

	/** 在 chunk 流上累加 usage（取末次非零 total 的 UsageDto），供 doFinally 统一落库。 */
	private Flux<ChatChunkDto> accumulateUsage(Flux<ChatChunkDto> chunkFlux, AtomicReference<ChatChunkDto.UsageDto> accum) {
		return chunkFlux.doOnNext(c -> {
			if (c != null && "usage".equals(c.type()) && c.usage() != null && c.usage().totalTokens() > 0) {
				accum.set(c.usage());
			}
		});
	}

	/** 月度配额预扣：请求发起时无法预知真实 token 数，仅做“已用量 + 预扣值 > 上限”拦截。 */
	private boolean tryReserveMonthlyQuota(String userId, ResolvedModel resolved) {
		UsageQuotaChecker.UsageQuota quota = usageQuota.getIfAvailable();
		if (quota == null) {
			return true;
		}
		return quota.tryReserve(userId);
	}

	/** 月度配额耗尽时返回的统一错误响应（非流式）。 */
	private ChatResponseDto buildMonthlyQuotaExhaustedDto(ResolvedModel resolved, ChatRequest request) {
		log.warn("月度 Token 配额耗尽，拒绝请求 → 供应商={}, 模型={}",
				resolved.provider().providerId(), resolved.model().id());
		return new ChatResponseDto(
				"本月对话额度已用尽，请下月再试或联系管理员提升配额。",
				resolved.provider().providerId(),
				resolved.model().id(),
				request.conversationId(),
				null,
				null);
	}

	/** 非流式落库：从单次 UsageDto 异步落库并校准月度配额。 */
	private void meterUsageAsync(String userId, ResolvedModel resolved, String conversationId, ChatChunkDto.UsageDto usage) {
		settleUsage(userId, resolved, conversationId, usage);
	}

	/**
	 * 用量落库 + 月度配额校准（核心）。异步执行，不阻塞主链路。
	 * 仅在真实 token 数 &gt; 0 时落库；cost 为 NULL 时兜底 ZERO。
	 */
	private void settleUsage(String userId, ResolvedModel resolved, String conversationId, ChatChunkDto.UsageDto usage) {
		if (usage == null || usage.totalTokens() <= 0) {
			return;
		}
		String monthKey = UsageQuotaChecker.currentMonthKey();
		String providerId = resolved.provider().providerId();
		String modelId = resolved.model().id();
		int promptTokens = usage.promptTokens() > 0 ? usage.promptTokens() : 0;
		int completionTokens = usage.completionTokens() > 0 ? usage.completionTokens() : 0;
		int totalTokens = usage.totalTokens();
		BigDecimal costRmb = (usage.estimatedCostRmb() != null) ? BigDecimal.valueOf(usage.estimatedCostRmb()) : BigDecimal.ZERO;

		// 1) 异步落库用量（失败仅告警）
		var saveSub = Mono.fromRunnable(() -> usageRepository.saveUsage(
						userId, providerId, modelId, conversationId,
						promptTokens, completionTokens, totalTokens, costRmb, monthKey))
				.onErrorComplete(ex -> {
					log.warn("用量落库失败 [user={}, model={}]: {}", userId, modelId, ex.getMessage());
					return true;
				})
				.subscribeOn(Schedulers.boundedElastic())
				.subscribe();
		fireAndForgetSubscriptions.add(saveSub);

		// 2) 事后校准月度配额（净增量 = 真实 - 预扣值）
		var calibrateSub = Mono.fromRunnable(() -> {
					UsageQuotaChecker.UsageQuota quota = usageQuota.getIfAvailable();
					if (quota != null) {
						quota.consumeActual(userId, totalTokens);
					}
				})
				.onErrorComplete(ex -> {
					log.warn("月度配额校准失败 [user={}]: {}", userId, ex.getMessage());
					return true;
				})
				.subscribeOn(Schedulers.boundedElastic())
				.subscribe();
		fireAndForgetSubscriptions.add(calibrateSub);
	}

	private boolean useMemory(ChatRequest request) {
		return memoryEnabled && request.hasConversation();
	}

	/** 记忆路径下保证 conversationId 存在：前端未传则后端生成 UUID，便于多轮串联。 */
	private ChatRequest ensureConversation(ChatRequest request) {
		if (request.hasConversation()) {
			return request;
		}
		return request.withConversationId(java.util.UUID.randomUUID().toString());
	}

	/** 供 Controller 透传：返回本次请求最终使用的 conversationId（已回填生成值）。userId 用于会话归属绑定。 */
	public String resolveConversationId(ChatRequest request, String userId) {
		if (!useMemory(request)) {
			return request.conversationId();
		}
		String cid = ensureConversation(request).conversationId();
		if (cid != null && !cid.isBlank()) {
			// 副作用异步化：将 JDBC 会话更新（touchSession/标题兜底）下沉至 boundedElastic 调度器，
			// 避免阻塞 WebFlux 主事件循环并缩短首包延迟 (TTFB)；高并发极端场景后续可扩展 Redis 节流/合并写。
			reactor.core.Disposable disposable = Mono.fromRunnable(() -> sessionService.touchSession(cid, userId, deriveDefaultTitle(request.message())))
					.doOnError(ex -> log.warn("异步更新会话状态(touchSession)失败 [cid={}]: {}", cid, ex.getMessage()))
					.onErrorComplete()
					.subscribeOn(Schedulers.boundedElastic())
					.subscribe();
			fireAndForgetSubscriptions.add(disposable);
		}
		return cid;
	}

	private static String deriveDefaultTitle(String text) {
		if (text == null || text.isBlank()) {
			return "新会话";
		}
		String firstLine = text.trim().split("\n")[0].trim();
		return firstLine.length() > 18 ? firstLine.substring(0, 18) + "…" : firstLine;
	}

	private Mono<ChatResponseDto> callWithoutMemory(ResolvedModel resolved, ChatRequest request, ChatOptions options, String userId) {
		List<Media> mediaList = convertMediaList(request.media());
		List<Message> messages = contextAssembler.assemble(
				request.message(), request.history(), request.systemPrompt(),
				null, resolved.model().maxContextTokens(), mediaList);
		Prompt prompt = new Prompt(messages, options);
		return Mono.fromCallable(() -> resolved.chatModel().call(prompt))
				.map(resp -> {
					meterUsageAsync(userId, resolved, request.conversationId(), extractUsageDto(resp, resolved));
					return new ChatResponseDto(
							extractText(resp), resolved.provider().providerId(), resolved.model().id(),
							request.conversationId(), null, null);
				})
				.timeout(CALL_TIMEOUT)
				.onErrorResume(ex -> {
					log.warn("主供应商请求失败 (单轮/旧历史) → 供应商={}, 模型={}: {}",
							resolved.provider().providerId(), resolved.model().id(), ex.getMessage());
					return callWithFallback(resolved, request, ex, userId);
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<ChatResponseDto> callWithFallback(ResolvedModel primaryResolved, ChatRequest request,
			Throwable error, String userId) {
		ResolvedModel fallbackResolved = registry.resolveFallback(
				primaryResolved.provider().providerId(),
				properties.fallbackProvider(),
				properties.fallbackModel());
		if (fallbackResolved != null) {
			log.warn("主供应商 [{}] 调用失败 ({})，无缝降级切换至备用供应商 [{}], 模型 [{}]",
					primaryResolved.provider().providerId(), error.getMessage(),
					fallbackResolved.provider().providerId(), fallbackResolved.model().id());
			ChatOptions fallbackOptions = ChatOptionsFactory.forProvider(fallbackResolved, 0.2);
			List<Media> mediaList = convertMediaList(request.media());
			List<Message> messages = contextAssembler.assemble(
					request.message(), request.history(), request.systemPrompt(),
					null, fallbackResolved.model().maxContextTokens(), mediaList);
			Prompt prompt = new Prompt(messages, fallbackOptions);
			return Mono.fromCallable(() -> fallbackResolved.chatModel().call(prompt))
					.map(resp -> {
						meterUsageAsync(userId, fallbackResolved, request.conversationId(), extractUsageDto(resp, fallbackResolved));
						return new ChatResponseDto(
								extractText(resp), fallbackResolved.provider().providerId(), fallbackResolved.model().id(),
								request.conversationId(), null, null);
					})
					.timeout(CALL_TIMEOUT)
					.onErrorResume(fbEx -> {
						log.warn("备用供应商 [{}] 调用亦失败: {}", fallbackResolved.provider().providerId(), fbEx.getMessage());
						return Mono.just(new ChatResponseDto(
								"上游供应商响应超时/异常，请稍后再试。",
								primaryResolved.provider().providerId(),
								primaryResolved.model().id(),
								request.conversationId(),
								null,
								null));
					})
					.subscribeOn(Schedulers.boundedElastic());
		}
		return Mono.just(new ChatResponseDto(
				"上游供应商响应超时/异常，请稍后再试。",
				primaryResolved.provider().providerId(),
				primaryResolved.model().id(),
				request.conversationId(),
				null,
				null));
	}

	private List<Media> convertMediaList(List<MediaDto> dtos) {
		if (dtos == null || dtos.isEmpty()) {
			return List.of();
		}
		return dtos.stream()
				.map(this::toMedia)
				.filter(Objects::nonNull)
				.toList();
	}

	private Media toMedia(MediaDto dto) {
		if (dto == null || dto.data() == null || dto.data().isBlank()) {
			return null;
		}
		try {
			String dataStr = dto.data().trim();
			String base64Data = dataStr;
			String mimeTypeStr = dto.mimeType();

			if (dataStr.contains(",") && dataStr.startsWith("data:")) {
				String[] parts = dataStr.split(",", 2);
				base64Data = parts[1];
				String header = parts[0];
				if (header.contains(":") && header.contains(";")) {
					mimeTypeStr = header.substring(header.indexOf(":") + 1, header.indexOf(";"));
				}
			}

			byte[] bytes = Base64.getDecoder().decode(base64Data.trim());
			return new Media(MimeTypeUtils.parseMimeType(mimeTypeStr), new ByteArrayResource(bytes));
		} catch (Exception e) {
			log.warn("多模态媒体 (Media) 数据解析异常: {}", e.getMessage());
			return null;
		}
	}

	private String resolveSystemPrompt(ChatRequest request) {
		return (request.systemPrompt() != null && !request.systemPrompt().isBlank())
				? request.systemPrompt()
				: contextAssembler.defaultSystemPrompt();
	}

	private void recordLongTermMemoryAsync(String userId, String conversationId, String userMessage,
			String assistantReply) {
		if (!memoryEnabled || userId == null || userId.isBlank() || userMessage == null || userMessage.isBlank()) {
			return;
		}
		LongTermMemoryProcessor processor = longTermProcessor.getIfAvailable();
		if (processor != null) {
			reactor.core.Disposable d1 = Mono.fromRunnable(() -> processor.processTurn(userId, conversationId, userMessage, assistantReply))
					.timeout(Duration.ofSeconds(10))
					.retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
					.doOnError(ex -> log.warn("长期记忆处理(processTurn)写入失败 [userId={}, conversationId={}]: {}", userId,
							conversationId, ex.getMessage()))
					.onErrorComplete()
					.subscribeOn(Schedulers.boundedElastic())
					.subscribe();
			fireAndForgetSubscriptions.add(d1);
			return;
		}
		LongTermMemoryConfig.LongTermMemoryWriter writer = longTermWriter.getIfAvailable();
		if (writer == null) {
			return;
		}
		String content = "【用户提问】: " + userMessage + "\n【AI回复】: " + (assistantReply != null ? assistantReply : "");
		reactor.core.Disposable d2 = Mono.fromRunnable(() -> writer.write(userId, content))
				.timeout(Duration.ofSeconds(10))
				.retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
				.doOnError(ex -> log.warn("长期记忆(writer)写入失败 [userId={}]: {}", userId, ex.getMessage()))
				.onErrorComplete()
				.subscribeOn(Schedulers.boundedElastic())
				.subscribe();
		fireAndForgetSubscriptions.add(d2);
	}

	private void applyLongTermAdvisor(ChatClient.AdvisorSpec advisorSpec, String userId) {
		LongTermMemoryConfig.LongTermMemoryAdvisorFactory factory = longTermFactory.getIfAvailable();
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

	private void applyRagAdvisor(ChatClient.AdvisorSpec advisorSpec, String userId) {
		RagAdvisorConfig.RagAdvisorFactory factory = ragAdvisorFactory.getIfAvailable();
		if (factory != null) {
			// sourceType 暂不传递（全局检索），后续可按请求粒度扩展过滤
			Advisor advisor = factory.forUser(userId, null);
			if (advisor != null) {
				advisorSpec.advisors(advisor);
			}
		}
	}

	private ChatOptions buildChatOptions(ResolvedModel resolved) {
		// 全局默认采样温度 0.2：偏向确定性、稳定的回复
		return ChatOptionsFactory.forProvider(resolved, 0.2);
	}

	private String extractText(ChatResponse resp) {
		if (resp == null) {
			return "";
		}
		var result = resp.getResult();
		if (result == null) {
			return "";
		}
		var output = result.getOutput();
		if (output == null) {
			return "";
		}
		String text = output.getText();
		return (text == null) ? "" : text;
	}

	/**
	 * Bean 销毁时统一释放所有「即发即弃」订阅，防止应用关闭/上下文销毁后订阅空转或泄漏。
	 * 已自然完成的订阅 dispose 为幂等操作，安全。
	 */
	@Override
	public void destroy() {
		reactor.core.Disposable d;
		int released = 0;
		while ((d = fireAndForgetSubscriptions.poll()) != null) {
			if (!d.isDisposed()) {
				d.dispose();
				released++;
			}
		}
		if (released > 0) {
			log.info("ChatService 销毁：已释放 {} 个未完成的即发即弃订阅", released);
		}
	}
}
