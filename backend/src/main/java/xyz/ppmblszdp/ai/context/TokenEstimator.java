package xyz.ppmblszdp.ai.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Token 估算接口。
 *
 * <p>抽成接口，便于后续替换为基于 jtokkit 的精确 tokenizer（如仅面向 OpenAI 系模型时）。
 * 默认实现 {@link HeuristicTokenEstimator} 使用字符权重启发式估算。
 */
public interface TokenEstimator {

	/** 估算一段文本的 token 数。 */
	int estimate(String text);

	/** 估算多条消息的 token 数之和。 */
	int estimate(List<Message> messages);
}
