package xyz.ppmblszdp.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 标题生成服务。
 *
 * <p>根据「用户问题 + AI 回答」调用 LLM 生成不超过 20 字的中文会话标题。
 * 该调用与主对话链路完全解耦：独立、幂等、无副作用（不写记忆/存储），失败降级返回 {@code null}，
 * 由前端回退到本地截取，绝不向上抛 5xx 影响主对话。
 *
 * <p>可靠性要点：
 * <ul>
 *   <li><b>入参截断</b>：问题截取前 200 字、回答截取前 500 字，避免长篇 Markdown/长文浪费 Token 并拖慢延迟；</li>
 *   <li><b>严格清洗</b>：去除模型惯用前缀（如「标题：」「生成的标题为：」）、Markdown 强调符号、首尾引号/书名号；</li>
 *   <li><b>显式超时</b>：基于 WebFlux 的 {@code Mono.timeout}，硬超时 8s，防止供应商慢响应阻塞线程池或前端长挂起。</li>
 * </ul>
 */
@Service
public class TitleService {

	private static final Logger log = LoggerFactory.getLogger(TitleService.class);

	/** 标题生成的硬超时（兼容 WebFlux，避免阻塞事件循环）。 */
	private static final Duration TITLE_TIMEOUT = Duration.ofSeconds(8);

	/** 入参截断长度：提问前 200 字、回答前 500 字已足以提炼主题。 */
	private static final int MAX_QUESTION_CHARS = 200;
	private static final int MAX_ANSWER_CHARS = 500;

	/** 标题硬上限，超出截断。 */
	private static final int MAX_TITLE_CHARS = 30;

	// 去除前缀：标题：/标题:/生成的标题为：/总结：/主题：/「」
	private static final Pattern PREFIX_PATTERN =
			Pattern.compile("^\\s*(标题|主题|总结|概括|简述|session title|title)\\s*[:：]\\s*", Pattern.CASE_INSENSITIVE);
	// 去除 Markdown 强调：**标题**、__标题__、*标题*、# 标题
	private static final Pattern MARKDOWN_PATTERN =
			Pattern.compile("^[#*_\\-\\s>]+|[#*_\\-\\s>]+$");
	// 去除首尾引号/书名号/括号
	private static final Pattern WRAP_PATTERN =
			Pattern.compile("^[\\\"'\"'「『（(【\\[]+|[\\\"'\"'」』）)】\\]]+$");

	private final ProviderRegistry registry;

	public TitleService(ProviderRegistry registry) {
		this.registry = registry;
	}

	/**
	 * 生成会话标题。
	 *
	 * @return 清洗后的标题；任何失败/降级场景返回 {@code null}（前端兜底）
	 */
	public Mono<String> generateTitle(String userMessage, String answer, String provider, String model) {
		String question = truncate(userMessage, MAX_QUESTION_CHARS);
		String content = truncate(answer, MAX_ANSWER_CHARS);
		if (question.isBlank() && content.isBlank()) {
			return Mono.just(null);
		}

		ResolvedModel resolved;
		try {
			resolved = registry.resolve(provider, model);
		} catch (Exception ex) {
			log.warn("标题生成：模型解析失败 → {}", ex.getMessage());
			return Mono.just(null);
		}

		ChatClient client = resolved.chatClient();
		String userPrompt = "用户问题：\n" + question + "\n\nAI 回答：\n" + content;
		ChatOptions options = buildOptions(resolved);

		return Mono.fromCallable(() -> {
					Prompt prompt = new Prompt(
							List.of(
									new SystemMessage(SYSTEM_PROMPT),
									new UserMessage(userPrompt)),
							options);
					ChatResponse resp = client.prompt(prompt).call().chatResponse();
					if (resp == null || resp.getResult() == null
							|| resp.getResult().getOutput() == null) {
						return null;
					}
					return resp.getResult().getOutput().getText();
				})
				.map(this::cleanTitle)
				.filter(t -> t != null && !t.isBlank())
				.timeout(TITLE_TIMEOUT)
				.onErrorResume(ex -> {
					log.warn("标题生成失败 → 供应商={}, 模型={}: {}",
							resolved.provider().providerId(), resolved.model().id(), ex.getMessage());
					return Mono.just(null);
				});
	}

	/** 去除模型惯用前缀、Markdown 符号、首尾包裹符，并做长度硬截断。 */
	private String cleanTitle(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		// 去掉思考链（若存在 <think:6124c78e>...</think:6124c78e> 残留）
		int thinkEnd = t.lastIndexOf("</think:6124c78e>");
		if (thinkEnd >= 0) {
			t = t.substring(thinkEnd + "</think:6124c78e>".length()).trim();
		}
		// 去掉常见前缀
		t = PREFIX_PATTERN.matcher(t).replaceFirst("");
		// 逐层去除 Markdown 强调与包裹符（循环处理嵌套）
		for (int i = 0; i < 3; i++) {
			String prev = t;
			t = MARKDOWN_PATTERN.matcher(t).replaceFirst("").replaceAll("$", "");
			t = MARKDOWN_PATTERN.matcher(t).replaceFirst("").trim();
			if (t.equals(prev)) {
				break;
			}
		}
		t = WRAP_PATTERN.matcher(t).replaceFirst("").replaceAll("$", "");
		t = t.trim();
		// 长度硬截断
		if (t.length() > MAX_TITLE_CHARS) {
			t = t.substring(0, MAX_TITLE_CHARS).trim();
		}
		return t.isBlank() ? null : t;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		String trimmed = s.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	/** 标题生成的采样温度固定 0.2，保证标题稳定、确定。 */
	private ChatOptions buildOptions(ResolvedModel resolved) {
		String modelName = resolved.model().modelName();
		String providerId = resolved.provider().providerId().toLowerCase();
		if (providerId.contains("deepseek")) {
			return DeepSeekChatOptions.builder().model(modelName).temperature(0.2).build();
		}
		if (providerId.contains("openai")) {
			return OpenAiChatOptions.builder().model(modelName).temperature(0.2).build();
		}
		if (providerId.contains("google") || providerId.contains("gemini")) {
			return GoogleGenAiChatOptions.builder().model(modelName).temperature(0.2).build();
		}
		if (providerId.contains("anthropic") || providerId.contains("claude")) {
			return AnthropicChatOptions.builder().model(modelName).temperature(0.2).build();
		}
		if (providerId.contains("ollama")) {
			return OllamaChatOptions.builder().model(modelName).temperature(0.2).build();
		}
		return OpenAiChatOptions.builder().model(modelName).temperature(0.2).build();
	}

	private static final String SYSTEM_PROMPT =
			"你是一个会话标题生成器。请根据下面的「用户问题」和「AI 回答」，提炼一句简洁的会话标题。"
			+ "要求：\n"
			+ "1. 不超过 20 个汉字（或 30 个字符）；\n"
			+ "2. 只输出标题本身，不要任何前缀（如「标题：」「总结：」）；\n"
			+ "3. 不要使用引号、书名号、Markdown 强调符号（** 或 *）或代码块；\n"
			+ "4. 不要结尾标点；\n"
			+ "5. 直接给出标题，不要解释、不要换行。";
}
