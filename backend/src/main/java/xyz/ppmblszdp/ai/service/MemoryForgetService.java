package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 记忆遗忘与优化算法服务：包含优先级时间衰减计算、LLM 记忆冲突检测/合并判定、以及细粒度记忆高层摘要压缩。
 */
@Service
public class MemoryForgetService {

    private static final Logger log = LoggerFactory.getLogger(MemoryForgetService.class);

    /** 默认时间衰减常数 (半衰期约为 14 天) */
    private static final double DECAY_LAMBDA = 0.05;

    /** LLM 冲突判定 Prompt Template */
    private static final String CONFLICT_DETECTION_SYSTEM_PROMPT = """
			你是一个记忆冲突检测与重构专家。
			请对比一条【新提取的记忆】与【现有的同类旧记忆】，判断它们是否存在逻辑矛盾或事实更替（例如：“用户使用 Java 17”与“用户已升级到 Java 25”）。

			判定规则：
			- RETAIN_NEW: 新记忆完全覆盖/替代了旧记忆（例如技术栈升级、计划变更），应删除旧记忆。
			- RETAIN_OLD: 旧记忆更准确完整，新记忆为误提取或无用重复，弃用新记忆。
			- MERGE: 新旧记忆互补，需合并为一条更加精准的综合陈述句。
			- NO_CONFLICT: 两者属于平行事实，不冲突。

			请严格输出 JSON 对象，包含字段：
			- action: 字符串，取值范围 ["RETAIN_NEW", "RETAIN_OLD", "MERGE", "NO_CONFLICT"]
			- mergedContent: 字符串，仅当 action 为 MERGE 时填入合并后的完整陈述句，否则填 null
			- reason: 字符串，简要说明判定依据
			""";

    /** LLM 摘要压缩 System Prompt */
    private static final String COMPRESSION_SYSTEM_PROMPT = """
			你是一个记忆压缩与提炼专家。
			给定一组相同分类下的细粒度长期记忆条目，请将它们合并提炼为 1 到 2 条高层次、概括性强且无上下文依赖的独立陈述句。

			规则：
			1. 必须保留用户的关键核心偏好、技术背景与重要决策。
			2. 剔除冗余的重复修饰词。
			3. 输出必须是 JSON 字符串数组，如：["用户主流技术栈为 Java 25 与 Next.js 16 全栈架构", "用户项目具备 Docker 与 WebSockets 实时通信要求"]。
			""";

    private final ProviderRegistry providerRegistry;

    public MemoryForgetService(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * 计算结合时间衰减与访问频次后的综合优先级得分。
     *
     * <p>
     * 公式：score = (basePriority + 0.1 * min(accessCount, 20)) * e^(-0.05 * daysElapsed)
     *
     * @param basePriority   基础优先级 (0.1 ~ 2.0，默认 1.0)
     * @param accessCount    访问/命中累计次数
     * @param lastAccessedAt 最近访问时间戳 (ISO-8601)
     * @param updatedAt      更新时间戳 (ISO-8601)
     * @return 衰减后的综合得分 (保留 2 位小数)
     */
    public double calculatePriorityScore(
            Double basePriority, Integer accessCount, String lastAccessedAt, String updatedAt) {
        double p = (basePriority != null && basePriority > 0.0) ? basePriority : 1.0;
        int acc = (accessCount != null && accessCount >= 0) ? accessCount : 0;

        Instant refTime = parseInstant(lastAccessedAt);
        if (refTime == null) {
            refTime = parseInstant(updatedAt);
        }
        if (refTime == null) {
            refTime = Instant.now();
        }

        long daysElapsed = Math.max(0L, Duration.between(refTime, Instant.now()).toDays());
        double score = (p + 0.1 * Math.min(acc, 20)) * Math.exp(-DECAY_LAMBDA * daysElapsed);
        return Math.round(score * 100.0) / 100.0;
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    /** LLM 冲突判定解析 DTO */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConflictDecision {
        @JsonProperty("action")
        private String action;

        @JsonProperty("mergedContent")
        private String mergedContent;

        @JsonProperty("reason")
        private String reason;

        public ConflictDecision() {}

        public ConflictDecision(String action, String mergedContent, String reason) {
            this.action = action;
            this.mergedContent = mergedContent;
            this.reason = reason;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getMergedContent() {
            return mergedContent;
        }

        public void setMergedContent(String mergedContent) {
            this.mergedContent = mergedContent;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 使用 LLM 检测新旧记忆是否存在矛盾，并返回合并/覆盖策略。
     */
    public ConflictDecision evaluateConflict(String newContent, String existingContent) {
        if (providerRegistry == null || newContent == null || existingContent == null) {
            return new ConflictDecision("NO_CONFLICT", null, "模型注册表不可用或内容为空");
        }

        try {
            ResolvedModel resolved = providerRegistry.resolve(null, null);
            ChatClient chatClient = resolved.chatClient();

            BeanOutputConverter<ConflictDecision> converter = new BeanOutputConverter<>(ConflictDecision.class);
            String formatInstruction = converter.getFormat();
            String systemPrompt = CONFLICT_DETECTION_SYSTEM_PROMPT + "\n" + formatInstruction;
            String userPrompt = "【新记忆】: " + newContent + "\n【现有旧记忆】: " + existingContent;

            String response = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return new ConflictDecision("NO_CONFLICT", null, "LLM 返回为空");
            }

            ConflictDecision decision = converter.convert(response);
            if (decision != null && decision.getAction() != null) {
                return decision;
            }
        } catch (Exception e) {
            log.warn("记忆冲突 LLM 判定过程异常（降级按不冲突处理）: {}", e.getMessage());
        }

        return new ConflictDecision("NO_CONFLICT", null, "解析降级");
    }

    /**
     * 使用 LLM 将一组同分类细粒度记忆压缩提炼为高层摘要陈述句列表。
     */
    public List<String> compressMemories(String category, List<String> fineGrainedMemories) {
        if (providerRegistry == null || fineGrainedMemories == null || fineGrainedMemories.isEmpty()) {
            return Collections.emptyList();
        }
        if (fineGrainedMemories.size() == 1) {
            return fineGrainedMemories;
        }

        try {
            ResolvedModel resolved = providerRegistry.resolve(null, null);
            ChatClient chatClient = resolved.chatClient();

            BeanOutputConverter<List<String>> converter = new BeanOutputConverter<>(
                    new org.springframework.core.ParameterizedTypeReference<List<String>>() {});
            String formatInstruction = converter.getFormat();
            String systemPrompt = COMPRESSION_SYSTEM_PROMPT + "\n" + formatInstruction;

            StringBuilder userContent = new StringBuilder();
            userContent
                    .append("分类: ")
                    .append(category != null ? category : "未分类")
                    .append("\n");
            userContent.append("细粒度记忆条目:\n");
            for (int i = 0; i < fineGrainedMemories.size(); i++) {
                userContent
                        .append(i + 1)
                        .append(". ")
                        .append(fineGrainedMemories.get(i))
                        .append("\n");
            }

            String response = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userContent.toString())
                    .call()
                    .content();

            if (response != null && !response.isBlank()) {
                List<String> summaries = converter.convert(response);
                if (summaries != null && !summaries.isEmpty()) {
                    log.info(
                            "记忆摘要压缩完成: 分类={}, 原始条数={}, 压缩后条数={}",
                            category,
                            fineGrainedMemories.size(),
                            summaries.size());
                    return summaries;
                }
            }
        } catch (Exception e) {
            log.warn("记忆摘要压缩 LLM 过程异常（降级保持原样）: {}", e.getMessage());
        }

        return Collections.emptyList();
    }
}
