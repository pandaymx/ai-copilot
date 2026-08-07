package xyz.ppmblszdp.ai.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Token 估算接口。
 *
 * <p>核心实现包含基于 JTokkit (Tiktoken) 的精确估算器 {@link JTokkitTokenEstimator}
 * 与字符权重启发式估算器 {@link HeuristicTokenEstimator}。
 */
public interface TokenEstimator {

	/** 估算一段文本的 token 数。 */
	int estimate(String text);

	/** 估算多条消息的 token 数之和。 */
	int estimate(List<Message> messages);
}
