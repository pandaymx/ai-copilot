package xyz.ppmblszdp.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

import java.time.Duration;
import java.util.List;

/**
 * 聊天业务服务。
 *
 * <p>流程：resolve 路由 → ContextAssembler 组装消息 → 构造 {@link Prompt}（含具体模型名 options）
 * → 调用 ChatModel 的 call / stream。流式加超时保护，异常向上抛给 Controller 转 SSE 错误事件。
 */
@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

	private final ProviderRegistry registry;
	private final ContextAssembler contextAssembler;

	public ChatService(ProviderRegistry registry, ContextAssembler contextAssembler) {
		this.registry = registry;
		this.contextAssembler = contextAssembler;
	}

	/** 非流式：一次性返回完整回复。 */
	public Mono<ChatResponseDto> chat(ChatRequest request) {
		ResolvedModel resolved = registry.resolve(request.provider(), request.model());
		List<org.springframework.ai.chat.messages.Message> messages = contextAssembler.assemble(
				request.message(),
				request.history(),
				request.systemPrompt(),
				null,
				resolved.model().maxContextTokens());
		ChatOptions options = ChatOptions.builder().model(resolved.model().modelName()).build();
		Prompt prompt = new Prompt(messages, options);
		log.info("非流式请求 → 供应商={}, 模型={}", resolved.provider().providerId(), resolved.model().id());

		return Mono.fromCallable(() -> resolved.chatModel().call(prompt))
				.map(resp -> extractText(resp))
				.map(text -> new ChatResponseDto(text, resolved.provider().providerId(), resolved.model().id(), null, null));
	}

	/** 流式：增量文本 Flux。 */
	public Flux<String> streamChat(ChatRequest request) {
		ResolvedModel resolved = registry.resolve(request.provider(), request.model());
		List<org.springframework.ai.chat.messages.Message> messages = contextAssembler.assemble(
				request.message(),
				request.history(),
				request.systemPrompt(),
				null,
				resolved.model().maxContextTokens());
		ChatOptions options = ChatOptions.builder().model(resolved.model().modelName()).build();
		Prompt prompt = new Prompt(messages, options);
		log.info("流式请求开始 → 供应商={}, 模型={}", resolved.provider().providerId(), resolved.model().id());

		return resolved.chatModel().stream(prompt)
				.timeout(STREAM_TIMEOUT)
				.map(resp -> extractText(resp))
				.filter(text -> text != null && !text.isEmpty())
				.doOnComplete(() -> log.info("流式请求结束 → 供应商={}, 模型={}",
						resolved.provider().providerId(), resolved.model().id()))
				.doOnError(err -> log.warn("流式请求异常 → 供应商={}, 模型={}: {}",
						resolved.provider().providerId(), resolved.model().id(), err.getMessage()));
	}

	private String extractText(org.springframework.ai.chat.model.ChatResponse resp) {
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
