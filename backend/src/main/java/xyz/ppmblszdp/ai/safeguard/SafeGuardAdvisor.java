package xyz.ppmblszdp.ai.safeguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 2.0.0 标准 SafeGuardAdvisor 安全防护 Advisor。
 *
 * <p>
 * 拦截责任链核心职责：
 * <ul>
 * <li><b>前置审查 (Around-Request)</b>：拦截用户输入的 Prompt。进行 Prompt Injection 攻击检测、手机号/身份证/Email 隐私泄露拦截及敏感词检测。BLOCK 模式下在本地直接切断模型调用，节省 Token。</li>
 * <li><b>后置审查 (Around-Response)</b>：拦截模型 Call/Stream 回复内容，通过滑动窗口算法（{@link StreamWindowSanitizer}）解决跨 Chunk 敏感词打码脱敏或阻断替换。</li>
 * </ul>
 */
public class SafeGuardAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Logger log = LoggerFactory.getLogger(SafeGuardAdvisor.class);

	private final SafeGuardEngine engine;
	private final SafeGuardProperties properties;
	private final int order;

	public SafeGuardAdvisor(SafeGuardEngine engine, SafeGuardProperties properties) {
		this(engine, properties, Ordered.HIGHEST_PRECEDENCE);
	}

	public SafeGuardAdvisor(SafeGuardEngine engine, SafeGuardProperties properties, int order) {
		this.engine = engine;
		this.properties = properties;
		this.order = order;
	}

	@Override
	public String getName() {
		return "SafeGuardAdvisor";
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		if (!properties.isEnabled()) {
			return chain.nextCall(request);
		}

		String userText = extractUserText(request);
		ActionPolicy reqPolicy = properties.getRequestPolicy();

		// 1. 前置 Request 检查
		SafeGuardCheckResult reqCheck = engine.inspectRequest(userText, reqPolicy);
		if (reqCheck.isTriggered()) {
			if (reqPolicy == ActionPolicy.BLOCK) {
				log.warn("🛡️ [SafeGuardAdvisor] 前置拦截用户输入 → 原因: {}, 触发规则: {}", reqCheck.getTriggerType(), reqCheck.getMatchedRule());
				return buildBlockedResponse(request, properties.getBlockMessage());
			}
			if (reqPolicy == ActionPolicy.MASK) {
				request = mutateUserText(request, reqCheck.getProcessedText());
			}
		}

		// 2. 执行大模型调用
		ChatClientResponse response = chain.nextCall(request);

		// 3. 后置 Response 检查与脱敏
		ActionPolicy respPolicy = properties.getResponsePolicy();
		String outputText = extractResponseText(response);

		SafeGuardCheckResult respCheck = engine.inspectResponse(outputText, respPolicy);
		if (respCheck.isTriggered()) {
			if (respPolicy == ActionPolicy.BLOCK) {
				log.warn("🛡️ [SafeGuardAdvisor] 后置拦截大模型回复 → 原因: {}, 触发规则: {}", respCheck.getTriggerType(), respCheck.getMatchedRule());
				return buildBlockedResponse(request, properties.getBlockMessage());
			}
			if (respPolicy == ActionPolicy.MASK) {
				return buildSanitizedResponse(response, respCheck.getProcessedText());
			}
		}

		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		if (!properties.isEnabled()) {
			return chain.nextStream(request);
		}

		String userText = extractUserText(request);
		ActionPolicy reqPolicy = properties.getRequestPolicy();

		// 1. 前置 Request 检查
		SafeGuardCheckResult reqCheck = engine.inspectRequest(userText, reqPolicy);
		ChatClientRequest finalRequest = request;
		if (reqCheck.isTriggered()) {
			if (reqPolicy == ActionPolicy.BLOCK) {
				log.warn("🛡️ [SafeGuardAdvisor-Stream] 前置流式拦截用户输入 → 原因: {}, 触发规则: {}", reqCheck.getTriggerType(), reqCheck.getMatchedRule());
				return Flux.just(buildBlockedResponse(request, properties.getBlockMessage()));
			}
			if (reqPolicy == ActionPolicy.MASK) {
				finalRequest = mutateUserText(request, reqCheck.getProcessedText());
			}
		}

		ActionPolicy respPolicy = properties.getResponsePolicy();
		if (respPolicy == ActionPolicy.LOG_ONLY) {
			return chain.nextStream(finalRequest);
		}

		// 2. 流式响应拦截：使用 StreamWindowSanitizer 进行跨 Chunk 滑动窗口打码
		StreamWindowSanitizer sanitizer = new StreamWindowSanitizer(engine, respPolicy);
		final ChatClientRequest effectiveRequest = finalRequest;

		return chain.nextStream(effectiveRequest)
				.concatMap(resp -> {
					String chunkText = extractResponseText(resp);
					if (respPolicy == ActionPolicy.BLOCK) {
						SafeGuardCheckResult check = engine.inspectResponse(chunkText, ActionPolicy.BLOCK);
						if (check.isTriggered()) {
							log.warn("🛡️ [SafeGuardAdvisor-Stream] 后置流式中途熔断触发");
							return Flux.just(buildBlockedResponse(effectiveRequest, properties.getBlockMessage()));
						}
					}
					String sanitizedChunk = sanitizer.processChunk(chunkText);
					if (sanitizedChunk.isEmpty()) {
						return Flux.empty();
					}
					return Flux.just(buildSanitizedResponse(resp, sanitizedChunk));
				})
				.concatWith(Flux.defer(() -> {
					String remaining = sanitizer.flush();
					if (remaining.isEmpty()) {
						return Flux.empty();
					}
					ChatClientResponse dummyResp = new ChatClientResponse(
							new ChatResponse(List.of(new Generation(new AssistantMessage(remaining)))),
							effectiveRequest.context()
					);
					return Flux.just(dummyResp);
				}));
	}

	// ─────────────────────────────────────────────
	// 辅助构造与解包方法
	// ─────────────────────────────────────────────

	private String extractUserText(ChatClientRequest request) {
		if (request == null || request.prompt() == null) {
			return "";
		}
		List<Message> messages = request.prompt().getInstructions();
		if (messages == null || messages.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Message m : messages) {
			if (m instanceof UserMessage um) {
				if (sb.length() > 0) sb.append("\n");
				sb.append(um.getText());
			}
		}
		return sb.toString();
	}

	private ChatClientRequest mutateUserText(ChatClientRequest request, String newText) {
		if (request == null || request.prompt() == null) {
			return request;
		}
		List<Message> original = request.prompt().getInstructions();
		List<Message> updated = new ArrayList<>();
		boolean mutated = false;
		for (Message m : original) {
			if (m instanceof UserMessage && !mutated) {
				updated.add(new UserMessage(newText));
				mutated = true;
			} else {
				updated.add(m);
			}
		}
		Prompt newPrompt = new Prompt(updated, request.prompt().getOptions());
		return request.mutate().prompt(newPrompt).build();
	}

	private String extractResponseText(ChatClientResponse response) {
		if (response == null || response.chatResponse() == null) {
			return "";
		}
		ChatResponse chatResponse = response.chatResponse();
		if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
			return chatResponse.getResult().getOutput().getText();
		}
		return "";
	}

	private ChatClientResponse buildBlockedResponse(ChatClientRequest request, String blockMsg) {
		AssistantMessage assistantMessage = new AssistantMessage(blockMsg);
		Generation generation = new Generation(assistantMessage);
		ChatResponse blockedChatResponse = new ChatResponse(List.of(generation));
		return new ChatClientResponse(blockedChatResponse, request != null ? request.context() : java.util.Map.of());
	}

	private ChatClientResponse buildSanitizedResponse(ChatClientResponse original, String newText) {
		AssistantMessage assistantMessage = new AssistantMessage(newText);
		Generation generation = new Generation(assistantMessage);
		ChatResponse sanitizedChatResponse = new ChatResponse(List.of(generation));
		return new ChatClientResponse(sanitizedChatResponse, original != null ? original.context() : java.util.Map.of());
	}
}
