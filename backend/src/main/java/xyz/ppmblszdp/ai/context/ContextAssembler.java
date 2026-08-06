package xyz.ppmblszdp.ai.context;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatMessageDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 上下文组装器：本方案核心算法。
 *
 * <p>
 * 职责：
 * <ol>
 * <li><b>System Prompt 保底注入</b>：优先级 请求内 system > provider 级 >
 * 全局，且永不参与历史裁剪；</li>
 * <li><b>历史去重</b>：前端 {@code history} 末尾通常已包含本轮 {@code message}（前端 send 时先 push
 * 再发），
 * 需识别并去重，否则用户消息会被发送两遍；</li>
 * <li><b>Token 预算</b>：可用预算 = 上下文窗口 × historyRatio − systemTokens −
 * reserveOutputTokens；</li>
 * <li><b>反向滑动窗口</b>：从最新消息向前累加，超预算即止，O(n) 单趟；</li>
 * <li><b>轮次成对对齐</b>：以 user/assistant 轮次为单位，避免裁出「有 assistant 无 user」的孤儿消息；</li>
 * <li><b>类型转换</b>：转为 Spring AI 的 {@link Message}
 * 列表（System/User/Assistant）。</li>
 * </ol>
 */
public class ContextAssembler {

	private final AiProviderProperties properties;
	private final TokenEstimator estimator;

	public ContextAssembler(AiProviderProperties properties, TokenEstimator estimator) {
		this.properties = properties;
		this.estimator = estimator;
	}

	/**
	 * 组装消息列表。
	 *
	 * @param message          当前用户消息（必填）
	 * @param history          历史消息（可能已含当前消息，需去重）
	 * @param requestSystem    请求级 system prompt 覆盖（可空）
	 * @param providerSystem   供应商级 system prompt（可空）
	 * @param maxContextTokens 模型上下文窗口大小
	 * @return 可直接用于 {@code new Prompt(messages, options)} 的消息列表
	 */
	public List<Message> assemble(String message, List<ChatMessageDto> history,
			String requestSystem, String providerSystem, int maxContextTokens) {
		String system = resolveSystem(requestSystem, providerSystem);
		int systemTokens = (system != null) ? estimator.estimate(system) : 0;

		int reserve = properties.resolveContext().resolveReserveOutputTokens();
		double ratio = properties.resolveContext().resolveHistoryRatio();
		int budget = (int) (maxContextTokens * ratio) - systemTokens - reserve;
		if (budget < 0) {
			budget = 0;
		}

		// 1) 取出历史中的非系统消息，并去重当前消息
		List<ChatMessageDto> deduped = dedupeCurrentMessage(history, message);

		// 2) 反向滑窗裁剪（按轮次成对对齐）
		List<ChatMessageDto> kept = slidingWindow(deduped, budget);

		// 3) 组装最终消息：system 保底在首
		List<Message> result = new ArrayList<>();
		if (system != null && !system.isBlank()) {
			result.add(new SystemMessage(system));
		}
		for (ChatMessageDto dto : kept) {
			result.add(toMessage(dto));
		}
		// 当前用户消息一定在末尾（去重后追加，确保一定送达）
		result.add(new UserMessage(message));
		return result;
	}

	private String resolveSystem(String requestSystem, String providerSystem) {
		if (requestSystem != null && !requestSystem.isBlank()) {
			return requestSystem;
		}
		if (providerSystem != null && !providerSystem.isBlank()) {
			return providerSystem;
		}
		String global = properties.systemPrompt();
		return (global != null && !global.isBlank()) ? global : null;
	}

	/** 暴露全局/默认系统提示词（记忆路径中作为 ChatClient system 兜底）。 */
	public String defaultSystemPrompt() {
		String global = properties.systemPrompt();
		return (global != null && !global.isBlank()) ? global : "你是一个专业、友好且可靠的 AI 助手。";
	}

	/**
	 * 去重：若 history 非空，且最后一条是 user 且内容与当前 message 相同，
	 * 则去掉该条（它会被作为当前消息重新追加），避免重复发送。
	 */
	private List<ChatMessageDto> dedupeCurrentMessage(List<ChatMessageDto> history, String message) {
		List<ChatMessageDto> src = (history == null) ? List.of() : history;
		if (src.isEmpty()) {
			return List.of();
		}
		ChatMessageDto last = src.get(src.size() - 1);
		List<ChatMessageDto> out = new ArrayList<>(src);
		if ("user".equalsIgnoreCase(last.role()) && message != null && message.equals(last.content())) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	/**
	 * 反向滑动窗口：从最新消息向前累加 token，超预算即止，且以 user/assistant 轮次成对保留。
	 */
	private List<ChatMessageDto> slidingWindow(List<ChatMessageDto> messages, int budget) {
		List<ChatMessageDto> kept = new ArrayList<>();
		int used = 0;
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessageDto msg = messages.get(i);
			if (msg == null || msg.content() == null) {
				continue;
			}
			// 系统消息不计入历史预算（已单独保底）
			if ("system".equalsIgnoreCase(msg.role())) {
				continue;
			}
			int cost = estimator.estimate(msg.content());
			// 成对对齐：仅当本消息与其「成对伙伴」（前一条）都能放入时才保留
			boolean pairOk = true;
			if (i > 0) {
				ChatMessageDto prev = messages.get(i - 1);
				if (prev != null && prev.content() != null && isPaired(msg.role(), prev.role())) {
					int pairCost = cost + estimator.estimate(prev.content());
					if (used + pairCost > budget) {
						pairOk = false;
					}
				}
			}
			if (used + cost > budget || !pairOk) {
				break;
			}
			used += cost;
			kept.add(msg);
		}
		// kept 是反向收集的，需反转回正序
		Collections.reverse(kept);
		return kept;
	}

	private boolean isPaired(String role, String prevRole) {
		// user 与 assistant 为完整一轮；system 已被排除
		if ("user".equalsIgnoreCase(role) && "assistant".equalsIgnoreCase(prevRole)) {
			return true;
		}
		if ("assistant".equalsIgnoreCase(role) && "user".equalsIgnoreCase(prevRole)) {
			return true;
		}
		return false;
	}

	private Message toMessage(ChatMessageDto dto) {
		String content = (dto.content() == null) ? "" : dto.content();
		String role = (dto.role() == null) ? "user" : dto.role();
		return switch (role.toLowerCase()) {
			case "system" -> new SystemMessage(content);
			case "assistant" -> new AssistantMessage(content);
			default -> new UserMessage(content);
		};
	}
}
