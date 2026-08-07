package xyz.ppmblszdp.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.dto.TitleRequest;
import xyz.ppmblszdp.ai.dto.TitleResponse;
import xyz.ppmblszdp.ai.exception.AiException;
import xyz.ppmblszdp.ai.service.ChatService;
import xyz.ppmblszdp.ai.service.SessionService;
import xyz.ppmblszdp.ai.service.TitleService;

/**
 * 聊天接口控制器。
 *
 * <ul>
 *   <li>{@code POST /api/chat}：非流式，返回完整回复；</li>
 *   <li>{@code POST /api/chat/stream}：结构化 SSE 流式，由框架自动进行类型安全的 JSON 序列化与分帧，
 *       流末补发 done 帧；中途异常转成 error SSE 事件后正常 complete，避免前端只见网络错误。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	private final ChatService chatService;
	private final TitleService titleService;
	private final SessionService sessionService;

	public ChatController(ChatService chatService, TitleService titleService, SessionService sessionService) {
		this.chatService = chatService;
		this.titleService = titleService;
		this.sessionService = sessionService;
	}

	@PostMapping
	public Mono<ChatResponseDto> chat(@RequestBody ChatRequest request) {
		return chatService.chat(request)
				.onErrorResume(AiException.class, ex -> Mono.error(ex));
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ChatChunkDto>> stream(@RequestBody ChatRequest request) {
		// 记忆路径：先把后端最终使用的 conversationId 以元数据帧透传给前端，便于多轮串联
		String conversationId = chatService.resolveConversationId(request);
		boolean emitConversation = conversationId != null && !conversationId.isBlank();
		Flux<ServerSentEvent<ChatChunkDto>> head = emitConversation
				? Flux.just(ServerSentEvent.builder(ChatChunkDto.conversation(conversationId)).build())
				: Flux.empty();
		return head.concatWith(chatService.streamChat(request)
						.map(chunk -> ServerSentEvent.builder(ChatChunkDto.content(chunk)).build()))
				.concatWithValues(ServerSentEvent.builder(ChatChunkDto.done()).build())
				.onErrorResume(AiException.class, ex ->
						Flux.just(ServerSentEvent.builder(ChatChunkDto.error(ex.getErrorCode(), ex.getMessage())).build()))
				.onErrorResume(Exception.class, ex -> {
					log.warn("流式未预期异常: {}", ex.getMessage());
					return Flux.just(ServerSentEvent.builder(ChatChunkDto.error("UPSTREAM_ERROR", ex.getMessage())).build());
				});
	}

	@PostMapping(value = "/title", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<TitleResponse> title(@RequestBody TitleRequest request) {
		return titleService
				.generateTitle(request.message(), request.answer(), request.provider(), request.model())
				.doOnNext(generatedTitle -> {
					if (generatedTitle != null && !generatedTitle.isBlank() && request.conversationId() != null && !request.conversationId().isBlank()) {
						sessionService.renameSession(request.conversationId(), generatedTitle);
					}
				})
				.map(TitleResponse::new)
				.defaultIfEmpty(new TitleResponse(""));
	}
}

