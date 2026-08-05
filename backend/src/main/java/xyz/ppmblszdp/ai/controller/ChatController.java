package xyz.ppmblszdp.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.exception.AiException;
import xyz.ppmblszdp.ai.service.ChatService;

/**
 * 聊天接口控制器。
 *
 * <ul>
 *   <li>{@code POST /api/chat}：非流式，返回完整回复；</li>
 *   <li>{@code POST /api/chat/stream}：SSE 流式，每帧输出 {@code {"content":"..."}} 紧凑 JSON，
 *       流末补发 {@code [DONE]}；中途异常转成错误 SSE 事件后正常 complete，避免前端只见网络错误。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	public Mono<ChatResponseDto> chat(@RequestBody ChatRequest request) {
		return chatService.chat(request)
				.onErrorResume(AiException.class, ex -> Mono.error(ex));
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> stream(@RequestBody ChatRequest request) {
		return chatService.streamChat(request)
				.map(chunk -> "{\"content\":" + escapeJson(chunk) + "}")
				.concatWithValues("[DONE]")
				.onErrorResume(AiException.class, ex ->
						Flux.just("{\"error\":true,\"code\":\"" + ex.getErrorCode() + "\",\"message\":"
								+ escapeJson(ex.getMessage()) + "}"))
				.onErrorResume(Exception.class, ex -> {
					log.warn("流式未预期异常: {}", ex.getMessage());
					return Flux.just("{\"error\":true,\"code\":\"UPSTREAM_ERROR\",\"message\":"
							+ escapeJson(ex.getMessage()) + "}");
				});
	}

	private static String escapeJson(String s) {
		if (s == null) {
			return "\"\"";
		}
		StringBuilder sb = new StringBuilder(s.length() + 8);
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
		return sb.toString();
	}
}
