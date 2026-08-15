package xyz.ppmblszdp.ai.context;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatMessageDto;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 智能上下文压缩器（Smart Context Compressor）。
 *
 * <p>算法核心：
 * <ol>
 *   <li><b>保留最新 N 轮 Short-term Working Memory</b>：最后 {@link #PROTECTED_TURNS} 轮对话
 *       完整保留，确保代词/指代关系不受压缩影响。</li>
 *   <li><b>仅压缩超出预算的旧消息</b>：将需要裁剪的旧轮次送 LLM 压缩为单条摘要消息，
 *       替换原始多条消息，节省 Token。</li>
 *   <li><b>三档压缩等级自动升级</b>：LIGHT → DEEP → KEYWORDS，压缩比不达标自动升级。</li>
 *   <li><b>10s 超时熔断</b>：LLM 压缩调用失败/超时时静默退化为硬删除，保持原有行为。</li>
 *   <li><b>专用低成本压缩模型</b>：通过 {@code app.ai.context.compression.provider} 和
 *       {@code model} 配置独立的压缩模型（如 Gemini Flash / DeepSeek），
 *       不占用主链路昂贵模型的 quota。</li>
 * </ol>
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    /** 压缩等级：定义 LLM 摘要的详细程度。 */
    public enum Level {
        /** 轻度压缩：保留所有实体、决策、代码关键行；删除寒暄，目标压缩至约 50%。 */
        LIGHT,
        /** 深度压缩：保留核心决策/代码片段；删去大段说明，目标压缩至约 25%。 */
        DEEP,
        /** 关键词提取：仅提取关键词/实体/操作列表，目标压缩至约 10%。 */
        KEYWORDS
    }

    /** 压缩结果：压缩后的消息列表 + 元数据。 */
    public record CompressResult(List<ChatMessageDto> messages, CompressionMetadata metadata) {}

    /** 默认保留最近 N 轮不参与压缩（Short-term Working Memory，1 轮 = 1 对 user/assistant 消息）。 */
    public static final int DEFAULT_PROTECTED_TURNS = 3;

    /** 默认超时时间：LLM 压缩调用超过此时间则退化为硬删除。 */
    public static final long DEFAULT_TIMEOUT_MS = 10_000L;

    /**
     * 目标压缩率阈值：压缩后 Token 数 > 原始的 85% 则认为压缩效果不佳，自动升级等级。
     * （LLM 有时对短文本几乎无法压缩，需要 escalate。）
     */
    static final double INEFFECTIVE_COMPRESSION_RATIO = 0.85;

    private final TokenEstimator estimator;
    private final AiProviderProperties properties;
    private final ObjectProvider<ProviderRegistry> registryProvider;
    private final ObjectProvider<ChatClient> compressionClientProvider;

    public ContextCompressor(
            TokenEstimator estimator,
            AiProviderProperties properties,
            ObjectProvider<ProviderRegistry> registryProvider,
            ObjectProvider<ChatClient> compressionClientProvider) {
        this.estimator = estimator;
        this.properties = properties;
        this.registryProvider = registryProvider;
        this.compressionClientProvider = compressionClientProvider;
    }

    /**
     * 对 history 中超出 budget 的旧轮次执行 LLM 摘要压缩。
     *
     * <p>保证：最后 {@code protectedTurns} 轮不参与压缩，始终以原始文本保留。
     *
     * @param history      完整历史（已去重当前消息、不含 system）
     * @param budget       可用 Token 预算
     * @param defaultLevel 默认压缩等级（可自动 escalate）
     * @return 压缩后的消息列表 + 元数据；若 LLM 失败则退化返回截断后的硬删除结果
     */
    public CompressResult compress(List<ChatMessageDto> history, int budget, Level defaultLevel) {
        if (history == null || history.isEmpty()) {
            return new CompressResult(List.of(), null);
        }

        int protectedTurns = properties.resolveContext().resolveCompression().resolveProtectedTurns();
        if (protectedTurns <= 0) {
            protectedTurns = DEFAULT_PROTECTED_TURNS;
        }

        // 分区：保护区（最新 protectedTurns 轮 = 最后 2*protectedTurns 条消息）+ 候压区
        int protectCount = Math.min(history.size(), protectedTurns * 2);
        int compressableEnd = history.size() - protectCount;

        if (compressableEnd <= 0) {
            // 历史不足保护阈值，不压缩直接返回
            return new CompressResult(new ArrayList<>(history), null);
        }

        List<ChatMessageDto> compressableChunk = history.subList(0, compressableEnd);
        List<ChatMessageDto> protectedChunk = history.subList(compressableEnd, history.size());

        int originalTokens = estimateTokens(compressableChunk);

        // 尝试 LLM 压缩，自动 escalate
        Level[] levels = Level.values();
        int levelIdx = Math.max(0, defaultLevel != null ? defaultLevel.ordinal() : 0);

        for (int attempt = levelIdx; attempt < levels.length; attempt++) {
            Level level = levels[attempt];
            CompressResult result = tryLlmCompress(compressableChunk, protectedChunk, budget, level, originalTokens);
            if (result != null) {
                return result;
            }
            if (attempt < levels.length - 1) {
                log.info("[ContextCompressor] 压缩比不达标，升级等级: {} → {}", level, levels[attempt + 1]);
            }
        }

        // 所有等级均失败或无效：降级为硬删除（返回保护区消息）
        log.warn("[ContextCompressor] LLM 压缩全部失败，退化为硬删除（仅保留最近 {} 轮）", protectedTurns);
        int deletedTokens = estimateTokens(compressableChunk);
        return new CompressResult(
                new ArrayList<>(protectedChunk),
                CompressionMetadata.fallback(countTurns(compressableChunk), deletedTokens));
    }

    // ─── 私有方法 ─────────────────────────────────────────────────────────────

    private CompressResult tryLlmCompress(
            List<ChatMessageDto> chunk,
            List<ChatMessageDto> protectedChunk,
            int budget,
            Level level,
            int originalTokens) {
        ChatClient client = resolveCompressionClient();
        if (client == null) {
            // 无压缩模型可用：退化
            return null;
        }

        String prompt = buildCompressionPrompt(chunk, level);
        long timeoutMs = properties.resolveContext().resolveCompression().resolveTimeoutMs();

        try {
            ChatResponse response = Mono.fromCallable(() -> client.prompt()
                            .user(prompt)
                            .call()
                            .chatResponse())
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response == null
                    || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                log.warn("[ContextCompressor] LLM 压缩返回空响应（level={}）", level);
                return null;
            }

            String summary = response.getResult().getOutput().getText();
            if (summary == null || summary.isBlank()) {
                return null;
            }

            int compressedTokens = estimator.estimate(summary);

            // 检查压缩是否有效：若摘要 Token 仍 > 原始的 INEFFECTIVE_COMPRESSION_RATIO，escalate
            if (originalTokens > 100 && (double) compressedTokens / originalTokens > INEFFECTIVE_COMPRESSION_RATIO) {
                log.debug(
                        "[ContextCompressor] 压缩效果不佳（level={}, ratio={:.2f}），考虑升级",
                        level,
                        (double) compressedTokens / originalTokens);
                return null; // 触发 escalate
            }

            // 构建摘要消息（作为单条 assistant 消息，标记为 [COMPRESSED:N turns]）
            int turns = countTurns(chunk);
            String tag = "[COMPRESSED:" + turns + " turns] " + summary;
            ChatMessageDto summaryMsg = ChatMessageDto.assistant(tag);

            // 组装最终消息：摘要 + 保护区
            List<ChatMessageDto> finalMessages = new ArrayList<>();
            finalMessages.add(summaryMsg);
            finalMessages.addAll(protectedChunk);

            // 验证总 Token 是否在预算内（保护区 + 摘要）
            int totalTokens = compressedTokens + estimateTokens(protectedChunk);
            if (totalTokens > budget && budget > 0) {
                // 摘要 + 保护区仍超预算（极端情况），降级：仅保留保护区
                log.warn("[ContextCompressor] 压缩后仍超预算（{} > {}），仅保留保护区", totalTokens, budget);
                return new CompressResult(
                        new ArrayList<>(protectedChunk),
                        CompressionMetadata.fallback(turns, originalTokens));
            }

            String snippet = summary.length() > 200 ? summary.substring(0, 200) + "…" : summary;
            CompressionMetadata metadata =
                    new CompressionMetadata(turns, originalTokens, compressedTokens, level, snippet, false);

            log.info(
                    "[ContextCompressor] ✅ 压缩完成 level={}, turns={}, tokens: {} → {} (节省 {})",
                    level,
                    turns,
                    originalTokens,
                    compressedTokens,
                    originalTokens - compressedTokens);
            return new CompressResult(finalMessages, metadata);

        } catch (Exception e) {
            log.warn("[ContextCompressor] LLM 压缩失败或超时（level={}）: {}", level, e.getMessage());
            return null;
        }
    }

    private ChatClient resolveCompressionClient() {
        // 1. 检查是否显式注入了 compressionChatClient
        ChatClient injected = compressionClientProvider.getIfAvailable();
        if (injected != null) {
            return injected;
        }

        // 2. 从 ProviderRegistry 根据配置解析低成本专用压缩模型
        ProviderRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return null;
        }

        var compressionConfig = properties.resolveContext().resolveCompression();
        String configuredProvider = compressionConfig.provider();
        String configuredModel = compressionConfig.model();

        try {
            ResolvedModel resolved = registry.resolve(configuredProvider, configuredModel);
            return resolved.chatClient();
        } catch (Exception ex) {
            log.warn(
                    "[ContextCompressor] 无法解析配置的压缩模型 provider={}, model={}: {}, 回退到默认模型",
                    configuredProvider,
                    configuredModel,
                    ex.getMessage());
            try {
                return registry.resolve(null, null).chatClient();
            } catch (Exception fallbackEx) {
                log.error("[ContextCompressor] 默认模型解析失败: {}", fallbackEx.getMessage());
                return null;
            }
        }
    }

    private String buildCompressionPrompt(List<ChatMessageDto> messages, Level level) {
        String systemInstruction =
                switch (level) {
                    case LIGHT ->
                            """
                            你是一个上下文压缩助手。请将以下对话历史压缩为一段简洁的摘要。
                            要求：
                            - 保留所有重要实体（人名、变量名、文件名、API名称）
                            - 保留所有决策和结论
                            - 保留关键代码片段（可缩减为伪代码）
                            - 删去寒暄、重复内容和冗余解释
                            - 使用第三人称客观描述（"用户提问了...""助手回答了..."）
                            - 目标：原文的 50% 以内
                            直接输出摘要，不要加任何前缀或说明。
                            """;
                    case DEEP ->
                            """
                            你是一个深度上下文压缩助手。请将以下对话历史极度压缩。
                            要求：
                            - 仅保留核心决策、关键结论和必要代码片段（最多 3 行）
                            - 删去所有过渡性解释和详细说明
                            - 使用 bullet point 列表格式
                            - 目标：原文的 25% 以内
                            直接输出压缩结果，不要加前缀。
                            """;
                    case KEYWORDS ->
                            """
                            你是一个关键词提取助手。请从以下对话历史中仅提取关键信息。
                            输出格式（JSON 格式）：
                            {"entities":["实体1","实体2"],"decisions":["决策1"],"code_refs":["文件或函数名"]}
                            目标：极简关键信息，原文的 10% 以内。直接输出 JSON，不要加前缀。
                            """;
                };

        StringBuilder sb = new StringBuilder();
        sb.append(systemInstruction).append("\n\n【对话历史】:\n");
        for (ChatMessageDto msg : messages) {
            String role = "user".equalsIgnoreCase(msg.role()) ? "用户" : "助手";
            sb.append(role).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    private int estimateTokens(List<ChatMessageDto> messages) {
        return messages.stream()
                .mapToInt(m -> m.content() != null ? estimator.estimate(m.content()) : 0)
                .sum();
    }

    private static int countTurns(List<ChatMessageDto> messages) {
        long userCount = messages.stream()
                .filter(m -> "user".equalsIgnoreCase(m.role()))
                .count();
        return (int) Math.max(1, userCount);
    }

    /** 判断消息是否是 LLM 插入的压缩摘要标记。 */
    public static boolean isCompressedMarker(ChatMessageDto msg) {
        return msg != null
                && "assistant".equalsIgnoreCase(msg.role())
                && msg.content() != null
                && msg.content().startsWith("[COMPRESSED:");
    }
}
