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
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig;
import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天业务服务。
 *
 * <p>三种路径：
 * <ol>
 *   <li><b>记忆驱动</b>（有 conversationId 且 app.ai.memory.enabled=true）：走 ChatClient + Advisor
 *       （MessageChatMemoryAdvisor 会话记忆 + 长期记忆 Advisor），conversationId 在每次请求时动态注入，
 *       多线程并发不会串线；</li>
 *   <li><b>旧 history 模式</b>（无 conversationId）：沿用 ContextAssembler 组装历史，完全向后兼容；</li>
 *   <li><b>单轮</b>（无 conversationId 且无 history）：仅当前消息。</li>
 * </ol>
 *
 * <p>记忆相关异常降级为「无记忆单次对话」，不向上抛 5xx；ContextAssembler 的 Token 预算裁剪在旧路径生效，
 * 记忆路径由 MessageChatMemoryAdvisor 的 RETRIEVE_SIZE 控制提取条数。
 */
@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

	private final ProviderRegistry registry;
	private final ContextAssembler contextAssembler;
	private final ObjectProvider<ChatMemory> sessionChatMemory;
	private final ObjectProvider<LongTermMemoryConfig.LongTermMemoryAdvisorFactory> longTermFactory;
	private final ObjectProvider<LongTermMemoryConfig.LongTermMemoryWriter> longTermWriter;
	private final ObjectProvider<LongTermMemoryProcessor> longTermProcessor;
	private final ObjectProvider<ChatRateLimiter.RateLimiter> rateLimiter;
	private final SessionService sessionService;
	private final boolean memoryEnabled;

	public ChatService(
			ProviderRegistry registry,
			ContextAssembler contextAssembler,
			ObjectProvider<ChatMemory> sessionChatMemory,
			ObjectProvider<LongTermMemoryConfig.LongTermMemoryAdvisorFactory> longTermFactory,
			ObjectProvider<LongTermMemoryConfig.LongTermMemoryWriter> longTermWriter,
			ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
			ObjectProvider<ChatRateLimiter.RateLimiter> rateLimiter,
			SessionService sessionService,
			xyz.ppmblszdp.ai.config.AiProviderProperties properties) {
		this.registry = registry;
		this.contextAssembler = contextAssembler;
		this.sessionChatMemory = sessionChatMemory;
		this.longTermFactory = longTermFactory;
		this.longTermWriter = longTermWriter;
		this.longTermProcessor = longTermProcessor;
		this.rateLimiter = rateLimiter;
		this.sessionService = sessionService;
		this.memoryEnabled = properties.resolveMemory().isEnabled();
	}

	/** 非流式：一次性返回完整回复。 */
	public Mono<ChatResponseDto> chat(ChatRequest request) {
		ResolvedModel resolved = registry.resolve(request.provider(), request.model());
		ChatRateLimiter.RateLimiter limiter = rateLimiter.getIfAvailable();
		if (limiter != null && !limiter.tryAcquire(request.resolveUserId())) {
			log.warn("非流式请求被限流 → 用户={}", request.resolveUserId());
			return Mono.just(new ChatResponseDto(
					"请求过于频繁，请稍后再试。", resolved.provider().providerId(),
					resolved.model().id(), request.conversationId(), null, null));
		}
		log.info("非流式请求 → 供应商={}, 模型={}, 记忆路径={}",
				resolved.provider().providerId(), resolved.model().id(), useMemory(request));

		boolean memoryPath = useMemory(request);
		ChatOptions options = buildChatOptions(resolved);

		if (memoryPath) {
			ChatMemory memory = sessionChatMemory.getIfAvailable();
			if (memory == null) {
				log.warn("记忆路径已选但 ChatMemory 不可用，降级为单轮");
				return callWithoutMemory(resolved, request, options);
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
					.advisors(a -> applyLongTermAdvisor(a, req))
					.options(options.mutate())
					.call();
			return Mono.fromCallable(() -> spec.chatResponse())
					.map(resp -> {
						String replyText = extractText(resp);
						recordLongTermMemoryAsync(req.resolveUserId(), req.conversationId(), req.message(), replyText);
						return new ChatResponseDto(
								replyText,
								resolved.provider().providerId(),
								resolved.model().id(),
								req.conversationId(),
								null,
								null);
					})
					.subscribeOn(Schedulers.boundedElastic());
		}
		return callWithoutMemory(resolved, request, options);
	}

	/** 流式：结构化 ChatChunkDto Flux（包含思考过程 reasoning 与 token 用量）。 */
	public Flux<ChatChunkDto> streamChatChunks(ChatRequest request) {
		ResolvedModel resolved = registry.resolve(request.provider(), request.model());
		ChatRateLimiter.RateLimiter limiter = rateLimiter.getIfAvailable();
		if (limiter != null && !limiter.tryAcquire(request.resolveUserId())) {
			log.warn("流式请求被限流 → 用户={}", request.resolveUserId());
			return Flux.just(ChatChunkDto.error("RATE_LIMIT", "请求过于频繁，请稍后再试。"));
		}

		boolean memoryPath = useMemory(request);
		ChatOptions options = buildChatOptions(resolved);

		if (memoryPath) {
			ChatMemory memory = sessionChatMemory.getIfAvailable();
			if (memory == null) {
				return streamChunksWithoutMemory(resolved, request, options);
			}
			ChatRequest req = ensureConversation(request);
			ChatClient client = resolved.chatClient();
			MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
			StringBuilder fullContent = new StringBuilder();
			List<Media> mediaList = convertMediaList(req.media());

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
					.advisors(a -> applyLongTermAdvisor(a, req))
					.options(options.mutate())
					.stream()
					.chatResponse()
					.timeout(STREAM_TIMEOUT)
					.concatMap(resp -> processChatResponseToChunks(resp, fullContent))
					.doOnComplete(() -> recordLongTermMemoryAsync(req.resolveUserId(), req.conversationId(), req.message(), fullContent.toString()))
					.doOnCancel(() -> {
						if (fullContent.length() > 0) {
							recordLongTermMemoryAsync(req.resolveUserId(), req.conversationId(), req.message(), fullContent.toString());
						}
					});
		}
		return streamChunksWithoutMemory(resolved, request, options);
	}

	public Flux<String> streamChat(ChatRequest request) {
		return streamChatChunks(request)
				.filter(c -> "content".equals(c.type()) && c.content() != null)
				.map(ChatChunkDto::content);
	}

	private Flux<ChatChunkDto> streamChunksWithoutMemory(ResolvedModel resolved, ChatRequest request, ChatOptions options) {
		List<Media> mediaList = convertMediaList(request.media());
		List<Message> messages = contextAssembler.assemble(
				request.message(), request.history(), request.systemPrompt(),
				null, resolved.model().maxContextTokens(), mediaList);
		Prompt prompt = new Prompt(messages, options);
		StringBuilder fullContent = new StringBuilder();

		return resolved.chatModel().stream(prompt)
				.timeout(STREAM_TIMEOUT)
				.concatMap(resp -> processChatResponseToChunks(resp, fullContent));
	}

	private Flux<ChatChunkDto> processChatResponseToChunks(ChatResponse resp, StringBuilder fullContent) {
		if (resp == null) return Flux.empty();
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
		ChatChunkDto.UsageDto usageDto = extractUsageDto(resp);
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

	private ChatChunkDto.UsageDto extractUsageDto(ChatResponse resp) {
		if (resp == null || resp.getMetadata() == null || resp.getMetadata().getUsage() == null) return null;
		var u = resp.getMetadata().getUsage();
		int prompt = u.getPromptTokens() != null ? u.getPromptTokens().intValue() : 0;
		int completion = u.getCompletionTokens() != null ? u.getCompletionTokens().intValue() : 0;
		int total = u.getTotalTokens() != null ? u.getTotalTokens().intValue() : (prompt + completion);
		if (total == 0) return null;

		// 预估费用：按 DeepSeek/OpenAI 通用均价 0.002元/千 Token 估算 RMB
		double cost = (prompt * 0.001 + completion * 0.002) / 1000.0 * 7.2;
		return new ChatChunkDto.UsageDto(prompt, completion, total, Math.round(cost * 10000.0) / 10000.0);
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

	/** 供 Controller 透传：返回本次请求最终使用的 conversationId（已回填生成值）。 */
	public String resolveConversationId(ChatRequest request) {
		if (!useMemory(request)) {
			return request.conversationId();
		}
		String cid = ensureConversation(request).conversationId();
		if (cid != null && !cid.isBlank()) {
			// 副作用异步化：将 JDBC 数据库更新下沉至 boundedElastic 调度器，避免阻塞 WebFlux 事件循环并缩短首包延迟 (TTFB)
			Mono.fromRunnable(() -> sessionService.touchSession(cid, deriveDefaultTitle(request.message())))
					.subscribeOn(Schedulers.boundedElastic())
					.subscribe();
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

	private Mono<ChatResponseDto> callWithoutMemory(ResolvedModel resolved, ChatRequest request, ChatOptions options) {
		List<Media> mediaList = convertMediaList(request.media());
		List<Message> messages = contextAssembler.assemble(
				request.message(), request.history(), request.systemPrompt(),
				null, resolved.model().maxContextTokens(), mediaList);
		Prompt prompt =
				new Prompt(messages, options);
		return Mono.fromCallable(() -> resolved.chatModel().call(prompt))
				.map(resp -> new ChatResponseDto(
						extractText(resp), resolved.provider().providerId(), resolved.model().id(),
						request.conversationId(), null, null))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private Flux<String> streamWithoutMemory(ResolvedModel resolved, ChatRequest request, ChatOptions options) {
		List<Media> mediaList = convertMediaList(request.media());
		List<Message> messages = contextAssembler.assemble(
				request.message(), request.history(), request.systemPrompt(),
				null, resolved.model().maxContextTokens(), mediaList);
		Prompt prompt =
				new Prompt(messages, options);
		return resolved.chatModel().stream(prompt)
				.timeout(STREAM_TIMEOUT)
				.map(resp -> extractText(resp))
				.filter(text -> text != null && !text.isEmpty())
				.doOnComplete(() -> log.info("流式请求结束(旧路径) → 供应商={}, 模型={}",
						resolved.provider().providerId(), resolved.model().id()))
				.doOnCancel(() -> log.info("流式请求被客户端取消(旧路径) → 供应商={}, 模型={}",
						resolved.provider().providerId(), resolved.model().id()))
				.doOnError(err -> log.warn("流式请求异常(旧路径) → 供应商={}, 模型={}: {}",
						resolved.provider().providerId(), resolved.model().id(), err.getMessage()));
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

	private void recordLongTermMemoryAsync(String userId, String conversationId, String userMessage, String assistantReply) {
		if (!memoryEnabled || userId == null || userId.isBlank() || userMessage == null || userMessage.isBlank()) {
			return;
		}
		LongTermMemoryProcessor processor = longTermProcessor.getIfAvailable();
		if (processor != null) {
			Mono.fromRunnable(() -> processor.processTurn(userId, conversationId, userMessage, assistantReply))
					.subscribeOn(Schedulers.boundedElastic())
					.subscribe();
			return;
		}
		LongTermMemoryConfig.LongTermMemoryWriter writer = longTermWriter.getIfAvailable();
		if (writer == null) {
			return;
		}
		String content = "【用户提问】: " + userMessage + "\n【AI回复】: " + (assistantReply != null ? assistantReply : "");
		Mono.fromRunnable(() -> writer.write(userId, content))
				.subscribeOn(Schedulers.boundedElastic())
				.subscribe();
	}

	private void applyLongTermAdvisor(
			ChatClient.AdvisorSpec a, ChatRequest request) {
		LongTermMemoryConfig.LongTermMemoryAdvisorFactory factory =
				longTermFactory.getIfAvailable();
		if (factory == null) {
			return;
		}
		// 按 userId 维度隔离长期记忆检索，避免跨用户污染；每次请求独立构造 advisor，不串线
		Advisor advisor = factory.forUser(request.resolveUserId());
		if (advisor == null) {
			return;
		}
		a.advisors(advisor);
	}

	private ChatOptions buildChatOptions(ResolvedModel resolved) {
		String modelName = resolved.model().modelName();
		String providerId = resolved.provider().providerId().toLowerCase();

		// 全局默认采样温度 0.2：偏向确定性、稳定的回复
		if (providerId.contains("deepseek")) {
			return DeepSeekChatOptions.builder()
					.model(modelName)
					.temperature(0.2)
					.build();
		}
		if (providerId.contains("openai")) {
			return OpenAiChatOptions.builder()
					.model(modelName)
					.temperature(0.2)
					.build();
		}
		if (providerId.contains("google") || providerId.contains("gemini")) {
			return GoogleGenAiChatOptions.builder()
					.model(modelName)
					.temperature(0.2)
					.build();
		}
		if (providerId.contains("anthropic") || providerId.contains("claude")) {
			return AnthropicChatOptions.builder()
					.model(modelName)
					.temperature(0.2)
					.build();
		}
		if (providerId.contains("ollama")) {
			return OllamaChatOptions.builder()
					.model(modelName)
					.temperature(0.2)
					.build();
		}
		return OpenAiChatOptions.builder()
				.model(modelName)
				.temperature(0.2)
				.build();
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
}
