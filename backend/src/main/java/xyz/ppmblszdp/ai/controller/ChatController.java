package xyz.ppmblszdp.ai.controller;

import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.dto.TtsRequest;
import xyz.ppmblszdp.ai.dto.TitleRequest;
import xyz.ppmblszdp.ai.dto.TitleResponse;
import xyz.ppmblszdp.ai.exception.AiException;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ChatService;
import xyz.ppmblszdp.ai.service.FeedbackService;
import xyz.ppmblszdp.ai.service.SessionService;
import xyz.ppmblszdp.ai.service.TitleService;

/**
 * 聊天接口控制器。
 *
 * <ul>
 * <li>{@code POST /api/chat}：非流式，返回完整回复；</li>
 * <li>{@code POST /api/chat/stream}：结构化 SSE 流式，由框架自动进行类型安全的 JSON 序列化与分帧，
 * 流末补发 done 帧；中途异常转成 error SSE 事件后正常 complete，避免前端只见网络错误。</li>
 * </ul>
 *
 * <p>所有端点均从受信任 {@code X-User-Id} Header 解析身份（strict 缺 Header 抛 401），
 * 用于限流 / 长期记忆 / 会话归属隔离。请求体中的 userId 仅作 dev 模式 fallback。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	private final ChatService chatService;
	private final TitleService titleService;
	private final SessionService sessionService;
	private final FeedbackService feedbackService;
	private final AuthProperties authProperties;

	public ChatController(ChatService chatService, TitleService titleService, SessionService sessionService,
			xyz.ppmblszdp.ai.service.FeedbackService feedbackService, AuthProperties authProperties) {
		this.chatService = chatService;
		this.titleService = titleService;
		this.sessionService = sessionService;
		this.feedbackService = feedbackService;
		this.authProperties = authProperties;
	}

	/** 从请求交换解析当前用户身份（dev 模式可 fallback 到请求体 userId） */
	private String resolveIdentity(ServerWebExchange exchange, ChatRequest request) {
		return UserIdentityFilter.resolveIdentity(exchange, request == null ? null : request.userId(), authProperties);
	}

	/**
	 * 文本转语音（TTS）：合成并返回 mp3 音频二进制。复用 X-User-Id 网关鉴权，
	 * AUTH_MODE=strict 缺头返回 401（由 UserIdentityFilter.resolveIdentity 抛出）。
	 */
	@PostMapping(value = "tts", produces = "audio/mpeg")
	public Mono<ResponseEntity<byte[]>> tts(@RequestBody TtsRequest request, ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange, (ChatRequest) null);
		return chatService.synthesizeSpeech(request.text(), request.voice(), userId)
				.map(audio -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType("audio/mpeg"))
						.body(audio))
				.onErrorResume(e -> Mono.error(new ResponseStatusException(
						HttpStatus.BAD_GATEWAY, "语音合成失败：" + e.getMessage(), e)));
	}

	/**
	 * 语音转文本（STT）：接收前端 base64 音频，复用 Gemini 多模态能力精准转录为文本。
	 * 复用 X-User-Id 网关鉴权，AUTH_MODE=strict 缺头返回 401。
	 */
	@PostMapping(value = "transcribe", produces = MediaType.TEXT_PLAIN_VALUE)
	public Mono<String> transcribe(@RequestBody MediaDto audio, ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange, (ChatRequest) null);
		String base64 = audio.data();
		if (base64 == null || base64.isBlank()) {
			return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频数据为空"));
		}
		String mime = (audio.mimeType() != null && !audio.mimeType().isBlank())
				? audio.mimeType() : "audio/webm";
		byte[] bytes;
		try {
			String b64 = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
			bytes = Base64.getDecoder().decode(b64.trim());
		} catch (IllegalArgumentException e) {
			return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频 base64 解析失败"));
		}
		return chatService.transcribeAudio(bytes, mime, userId)
				.onErrorResume(e -> Mono.error(new ResponseStatusException(
						HttpStatus.BAD_GATEWAY, "语音识别失败：" + e.getMessage(), e)));
	}

	@PostMapping
	public Mono<ChatResponseDto> chat(@RequestBody ChatRequest request, ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange, request);
		return chatService.chat(request, userId)
				.onErrorResume(AiException.class, ex -> Mono.error(ex));
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ChatChunkDto>> stream(@RequestBody ChatRequest request, ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange, request);
		String conversationId = chatService.resolveConversationId(request, userId);
		boolean emitConversation = conversationId != null && !conversationId.isBlank();
		Flux<ServerSentEvent<ChatChunkDto>> head = emitConversation
				? Flux.just(ServerSentEvent.builder(ChatChunkDto.conversation(conversationId)).build())
				: Flux.empty();
		return head.concatWith(chatService.streamChatChunks(request, userId)
				.map(chunk -> ServerSentEvent.builder(chunk).build()))
				.concatWithValues(ServerSentEvent.builder(ChatChunkDto.done()).build())
				.onErrorResume(AiException.class,
						ex -> Flux.just(ServerSentEvent.builder(ChatChunkDto.error(ex.getErrorCode(), ex.getMessage()))
								.build()))
				.onErrorResume(Exception.class, ex -> {
					log.warn("流式未预期异常: {}", ex.getMessage());
					return Flux.just(
							ServerSentEvent.builder(ChatChunkDto.error("UPSTREAM_ERROR", ex.getMessage())).build());
				});
	}

	@PostMapping(value = "/title", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<TitleResponse> title(@RequestBody TitleRequest request, ServerWebExchange exchange) {
		String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
		final String conversationId = request.conversationId();
		// 归属校验：会话不存在或不属于当前用户 → 404，防止越权重命名
		if (conversationId == null || conversationId.isBlank()
				|| sessionService.findSession(conversationId, userId).isEmpty()) {
			return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或无权访问"));
		}
		return titleService
				.generateTitle(request.message(), request.answer(), request.provider(), request.model())
				.doOnNext(generatedTitle -> {
					if (generatedTitle != null && !generatedTitle.isBlank()) {
						sessionService.renameSession(conversationId, userId, generatedTitle);
					}
				})
				.map(TitleResponse::new)
				.defaultIfEmpty(new TitleResponse(""));
	}

	@PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Map<String, Object>> feedback(@RequestBody ChatFeedbackRequest request, ServerWebExchange exchange) {
		String userId = UserIdentityFilter.resolveIdentity(exchange, request == null ? null : request.userId(), authProperties);
		final String resolvedUserId = userId;
		return Mono.fromRunnable(() -> feedbackService.saveFeedback(resolvedUserId, request))
				.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
				.thenReturn(Map.of("success", true));
	}
}
