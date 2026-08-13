package xyz.ppmblszdp.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.ParameterizedTypeReference;

import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 长期记忆核心处理器：负责双重触发摘要、前置硬规则链过滤、LLM 原子化事实提取以及 pgvector 向量去重/Upsert 更新。
 */
public class LongTermMemoryProcessor {

	private static final Logger log = LoggerFactory.getLogger(LongTermMemoryProcessor.class);

	/** 高频问候语/闲聊/无意义词正则拦截链 */
	private static final Pattern NOISE_PATTERN = Pattern.compile(
			"^(你好|在吗|谢谢|收到|好的|OK|ok|Thanks|thanks|hello|Hello|hi|Hi|thx|3q|整好了|好的呢|嗯嗯|好的吧|知道了|不用了)[\\s!！~.~]*$",
			Pattern.CASE_INSENSITIVE
	);

	/** 原子化记忆抽取 System Prompt（结构化 JSON 输出，供 BeanOutputConverter 绑定） */
	private static final String FACT_EXTRACTION_SYSTEM_PROMPT = """
			你是一个无状态的个人信息与偏好抽取助手。
			请分析给定的用户对话历史，提取用户显性表达或暗示的【持久个人偏好】、【技术栈/背景】、【关键决策/约束】或【项目状态】。

			规则：
			1. 每一条提取记录必须是【原子化、无上下文依赖、无代词】的独立陈述句（例如：“用户技术栈偏好：Java 25。”、“用户项目需求：全栈 AI 聊天应用”）。
			2. 严禁提取任何临时对话问候、过渡短语或次要细节（例如：“用户说了你好”）。
			3. 如果对话中未包含任何有价值的持久信息或偏好，请直接且仅输出：[NONE]。
			4. 输出必须是 JSON 数组，每个元素包含字段：
			   - category: 字符串，取值如 "技术栈偏好" / "项目状态" / "关键决策" / "个人背景" / "其他"
			   - content: 字符串，原子化陈述句
			   - confidence: 数值，0.0~1.0，表示本条抽取的置信度
			5. 禁止在 JSON 外输出任何解释性文字。
			""";

	private final VectorStore vectorStore;
	private final ProviderRegistry providerRegistry;
	private final AiProviderProperties properties;

	/** 会话轮次计数器：conversationId -> atomic turn count */
	private final Map<String, AtomicInteger> sessionTurnCounters = new ConcurrentHashMap<>();

	public LongTermMemoryProcessor(
			VectorStore vectorStore,
			ProviderRegistry providerRegistry,
			AiProviderProperties properties) {
		this.vectorStore = (vectorStore != null) ? new SafeVectorStore(vectorStore) : null;
		this.providerRegistry = providerRegistry;
		this.properties = properties;
	}

	/**
	 * 1. 前置硬规则链过滤（Hard Rules Filter Pipeline）
	 *
	 * @param content 待校验内容
	 * @return true 表示为无意义/噪音短语，应拦截；false 表示符合进一步处理规则
	 */
	public boolean isTrivialOrNoise(String content) {
		if (content == null || content.isBlank()) {
			return true;
		}
		String trimmed = content.trim();
		int minLen = properties.resolveMemory().resolveLongTermMinContentLength();
		if (trimmed.length() < minLen) {
			log.debug("硬规则链拦截：内容长度 ({}) < 最小要求 ({}) -> '{}'", trimmed.length(), minLen, trimmed);
			return true;
		}
		if (NOISE_PATTERN.matcher(trimmed).matches()) {
			log.debug("硬规则链拦截：命中高频问候语/无意义正则 -> '{}'", trimmed);
			return true;
		}
		return false;
	}

	/**
	 * 2. 轮次触发处理：每当会话增加一轮对话时调用
	 */
	public void processTurn(String userId, String conversationId, String userMessage, String assistantReply) {
		if (!properties.resolveMemory().isLongTermSummarizeEnabled()) {
			// 若摘要未开启，降级为直写模式（含硬规则+去重）
			recordDirectMemory(userId, userMessage, assistantReply);
			return;
		}

		if (isTrivialOrNoise(userMessage)) {
			return;
		}

		AtomicInteger counter = sessionTurnCounters.computeIfAbsent(conversationId, k -> new AtomicInteger(0));
		int currentTurn = counter.incrementAndGet();
		int interval = properties.resolveMemory().resolveLongTermSummarizeTurnInterval();

		if (currentTurn % interval == 0) {
			log.info("触发长期记忆【轮次摘要】→ 会话={}, 当前轮次={}", conversationId, currentTurn);
			extractAndRecordFactsAsync(userId, conversationId, userMessage, assistantReply);
		}
	}

	/**
	 * 3. 会话结束/显式 Touch 触发处理：补齐尾部未满 5 轮的抽取
	 */
	public void processSessionClose(String userId, String conversationId, String lastUserMessage, String lastAssistantReply) {
		if (!properties.resolveMemory().isLongTermSummarizeEnabled()) {
			return;
		}
		AtomicInteger counter = sessionTurnCounters.remove(conversationId);
		int currentTurn = (counter != null) ? counter.get() : 0;
		int interval = properties.resolveMemory().resolveLongTermSummarizeTurnInterval();

		// 如果存在尾部未凑满 interval 轮的对话，执行尾部补齐抽取
		if (currentTurn % interval != 0 && lastUserMessage != null && !isTrivialOrNoise(lastUserMessage)) {
			log.info("触发长期记忆【会话结束补齐摘要】→ 会话={}, 尾部轮次={}", conversationId, currentTurn % interval);
			extractAndRecordFactsAsync(userId, conversationId, lastUserMessage, lastAssistantReply);
		}
	}

	/**
	 * 4. 利用 LLM 进行原子化事实抽取，并写入向量库
	 */
	private void extractAndRecordFactsAsync(String userId, String conversationId, String userMessage, String assistantReply) {
		if (providerRegistry == null) {
			return;
		}
		try {
			ResolvedModel resolved = providerRegistry.resolve(null, null);
			ChatClient chatClient = resolved.chatClient();
			String inputContent = "【用户】: " + userMessage + "\n【助手】: " + (assistantReply != null ? assistantReply : "");

			// 强类型结构化输出：使用 ParameterizedTypeReference 规避 List.class 的类型擦除退化
			BeanOutputConverter<List<MemoryFact>> converter =
					new BeanOutputConverter<>(new ParameterizedTypeReference<List<MemoryFact>>() {});
			String formatInstruction = converter.getFormat();
			String systemPrompt = FACT_EXTRACTION_SYSTEM_PROMPT + "\n" + formatInstruction;

			String extracted = chatClient.prompt()
					.system(systemPrompt)
					.user(inputContent)
					.call()
					.content();

			if (extracted == null || extracted.isBlank() || extracted.contains("[NONE]")) {
				log.debug("LLM 抽取完成：未发现有价值的原子化长期记忆/偏好 -> 会话={}", conversationId);
				return;
			}

			List<MemoryFact> facts;
			try {
				facts = converter.convert(extracted);
			} catch (Exception e) {
				log.warn("LLM 结构化抽取解析失败（降级忽略）: {}", e.getMessage());
				return;
			}

			if (facts == null || facts.isEmpty()) {
				return;
			}

			for (MemoryFact fact : facts) {
				String content = (fact.getContent() != null) ? fact.getContent().trim() : "";
				if (!content.isBlank() && !content.contains("[NONE]") && !isTrivialOrNoise(content)) {
					dedupAndUpsert(userId, content, fact.getCategory(), fact.getConfidence());
				}
			}
		} catch (Exception e) {
			log.warn("长期记忆 LLM 原子事实抽取过程异常（静默降级）: {}", e.getMessage());
		}
	}

	/**
	 * 直写降级模式
	 */
	private void recordDirectMemory(String userId, String userMessage, String assistantReply) {
		if (isTrivialOrNoise(userMessage)) {
			return;
		}
		String content = "用户偏好/问答: " + userMessage;
		dedupAndUpsert(userId, content, null, null);
	}

	/**
	 * 5. 向量去重与 Upsert / 时间戳刷新逻辑
	 *
	 * @param category   事实分类（可空，用于结构化编辑/去重；旧纯文本路径传 null）
	 * @param confidence 抽取置信度（可空）
	 */
	public void dedupAndUpsert(String userId, String content, String category, Double confidence) {
		if (vectorStore == null || userId == null || userId.isBlank() || content == null || content.isBlank()) {
			return;
		}

		if (isTrivialOrNoise(content)) {
			return;
		}

		boolean dedupEnabled = properties.resolveMemory().isLongTermDedupEnabled();
		double threshold = properties.resolveMemory().resolveLongTermSimilarityThreshold();

		try {
			if (dedupEnabled) {
				FilterExpressionBuilder b = new FilterExpressionBuilder();
				var filter = b.eq("userId", userId).build();
				SearchRequest request = SearchRequest.builder()
						.query(content)
						.topK(1)
						.filterExpression(filter)
						.similarityThreshold(threshold)
						.build();

				List<Document> similarDocs = vectorStore.similaritySearch(request);
				if (!similarDocs.isEmpty()) {
					Document existing = similarDocs.get(0);
					log.info("向量去重命中（相似度 >= {}）-> 删除老记录以执行 Upsert 时间戳刷新: id={}, content='{}'",
							threshold, existing.getId(), content);
					vectorStore.delete(List.of(existing.getId()));
				}
			}

			// 构建带有 userId、updated_at、priority、access_count 与结构化字段的全新 Document
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("userId", userId);
			String nowStr = Instant.now().toString();
			metadata.put("updated_at", nowStr);
			metadata.put("sourceType", "long_term_memory");
			metadata.put("priority", 1.0);
			metadata.put("access_count", 0);
			metadata.put("last_accessed_at", nowStr);
			metadata.put("archived", false);

			if (category != null && !category.isBlank()) {
				metadata.put("category", category);
			}
			if (confidence != null) {
				metadata.put("confidence", confidence);
			}

			Document newDoc = new Document(content, metadata);
			vectorStore.add(List.of(newDoc));
			log.info("长期记忆已成功写入 pgvector (Upsert) → userId={}, category={}, content='{}'", userId, category, content);

		} catch (Exception e) {
			log.warn("长期记忆向量去重/写入异常（已降级）: {}", e.getMessage());
		}
	}

	public void clearSessionCounter(String conversationId) {
		if (conversationId != null) {
			sessionTurnCounters.remove(conversationId);
		}
	}
}
