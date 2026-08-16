package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.PromptOptimizeRequest;
import xyz.ppmblszdp.ai.dto.PromptOptimizeResult;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * Prompt 优化服务。
 *
 * <p>对用户输入的 Prompt 做单次结构化分析：清晰度评分、缺失项检测、优化重写与
 * 按需 Few-shot 生成。与主对话链路完全解耦，无状态、无副作用（不写库、不读记忆），
 * 任何失败均降级返回原始 Prompt（{@code score=-1, optimized=originalPrompt}），
 * 绝不向上抛 5xx 阻断前端输入。
 *
 * <p>可靠性（对齐 {@code TitleService}）：
 * <ul>
 *   <li>LLM 调用经 {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}
 *       包装，避免阻塞 Netty EventLoop 线程；</li>
 *   <li>{@code .timeout(15s)} 硬超时 + {@code onErrorResume} 降级；</li>
 *   <li>低成本模型（T2）用于普通优化，深度优化回退用户模型（T3）。</li>
 * </ul>
 */
@Service
public class PromptOptimizer {

    private static final Logger log = LoggerFactory.getLogger(PromptOptimizer.class);

    /** 优化分析硬超时（15s）。 */
    private static final Duration OPTIMIZE_TIMEOUT = Duration.ofSeconds(15);

    /** 允许分析的最大 Prompt 长度，超出截断，足够语义评估。 */
    private static final int MAX_PROMPT_CHARS = 4000;

    /** 缺失项受限枚举，约束 LLM 输出，避免发散自然语言。 */
    private static final String MISSING_ENUM = "ROLE（缺少角色/人设设定）, CONTEXT（缺少背景或上下文）, FORMAT（缺少输出格式要求）, "
            + "CONSTRAINTS（缺少边界/约束条件）, EXAMPLES（缺少示例/Few-shot）, "
            + "AUDIENCE（缺少目标受众）, TONE（缺少语气/风格要求）";

    private final ProviderRegistry registry;

    public PromptOptimizer(ProviderRegistry registry) {
        this.registry = registry;
    }

    /**
     * 优化用户 Prompt。
     *
     * @param request 含原始 prompt 与可选 depth 开关
     * @param provider 用户当前选择的供应商（用于定位低成本模型与深度优化回退）
     * @param model 用户当前选择的模型
     * @return 结构化优化结果；任何失败降级为原始 Prompt
     */
    public Mono<PromptOptimizeResult> optimize(PromptOptimizeRequest request, String provider, String model) {
        String original = request.getPrompt() == null ? "" : request.getPrompt();
        boolean depth = Boolean.TRUE.equals(request.getDepth());

        ResolvedModel userResolved;
        try {
            userResolved = registry.resolve(provider, model);
        } catch (Exception ex) {
            log.warn("Prompt 优化：模型解析失败 → {}", ex.getMessage());
            return Mono.just(fallback(original));
        }

        // 深度优化走用户所选模型（更强推理），普通优化走最便宜的低成本模型。
        ResolvedModel resolved = depth ? userResolved : selectLowCostModel(userResolved);
        log.debug(
                "Prompt 优化：depth={}, 用户模型={}/{}, 实际模型={}/{}",
                depth,
                userResolved.provider().providerId(),
                userResolved.model().id(),
                resolved.provider().providerId(),
                resolved.model().id());

        BeanOutputConverter<OptimizeDecision> converter = new BeanOutputConverter<>(OptimizeDecision.class);
        String format = converter.getFormat();
        String analyzed = truncate(original, MAX_PROMPT_CHARS);
        String userPrompt = "【待优化的原始 Prompt】：\n" + analyzed;

        return Mono.fromCallable(() -> {
                    ChatClient client = resolved.chatClient();
                    String content = client.prompt()
                            .system(SYSTEM_PROMPT + "\n\n" + format)
                            .user(userPrompt)
                            .call()
                            .content();
                    OptimizeDecision decision = converter.convert(content);
                    return toResult(decision, original);
                })
                .timeout(OPTIMIZE_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn(
                            "Prompt 优化失败 → 供应商={}, 模型={}: {}",
                            resolved.provider().providerId(),
                            resolved.model().id(),
                            ex.getMessage());
                    return Mono.just(fallback(original));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private PromptOptimizeResult toResult(OptimizeDecision decision, String original) {
        if (decision == null) {
            return fallback(original);
        }
        PromptOptimizeResult result = new PromptOptimizeResult();
        result.setScore(decision.getScore());
        result.setMissing(decision.getMissing());
        result.setIssues(decision.getIssues());
        String optimized = decision.getOptimized();
        result.setOptimized(optimized != null && !optimized.isBlank() ? optimized : original);
        result.setFewShot(decision.getFewShot());
        return result;
    }

    /** 降级结果：评分置 -1，优化版回退为原始 Prompt。 */
    private PromptOptimizeResult fallback(String original) {
        PromptOptimizeResult result = new PromptOptimizeResult();
        result.setScore(-1);
        result.setOptimized(original);
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * 在用户所选供应商下挑选成本最低的模型用于普通优化。
     *
     * <p>普通优化为轻量文本任务，使用 {@code (inputPricePerK + outputPricePerK)} 最小模型即可，
     * 显著降低开销；若供应商未登记任何模型则回退用户传入模型。
     */
    private ResolvedModel selectLowCostModel(ResolvedModel userResolved) {
        ProviderDescriptor provider = userResolved.provider();
        Map<String, ModelDescriptor> models = provider.models();
        if (models == null || models.isEmpty()) {
            return userResolved;
        }
        ModelDescriptor cheapest = null;
        BigDecimal lowestCost = null;
        for (ModelDescriptor md : models.values()) {
            BigDecimal cost = md.inputPricePerK().add(md.outputPricePerK());
            if (lowestCost == null || cost.compareTo(lowestCost) < 0) {
                lowestCost = cost;
                cheapest = md;
            }
        }
        if (cheapest == null) {
            return userResolved;
        }
        return new ResolvedModel(provider.chatModel(), provider, cheapest);
    }

    // ========================================================================
    // 内部强类型 DTO（BeanOutputConverter 反序列化目标）
    // ========================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OptimizeDecision {
        @JsonProperty("score")
        private int score;

        @JsonProperty("missing")
        private List<String> missing;

        @JsonProperty("issues")
        private List<String> issues;

        @JsonProperty("optimized")
        private String optimized;

        @JsonProperty("fewShot")
        private List<String> fewShot;

        int getScore() {
            return score;
        }

        List<String> getMissing() {
            return missing;
        }

        List<String> getIssues() {
            return issues;
        }

        String getOptimized() {
            return optimized;
        }

        List<String> getFewShot() {
            return fewShot;
        }
    }

    // ========================================================================
    // System Prompt（含用户要求的模板约束：保留核心诉求 + 语言一致）
    // ========================================================================

    private static final String SYSTEM_PROMPT = """
			你是一名资深的 Prompt 工程专家，擅长将粗糙、含糊的用户指令改写为结构清晰、可执行、易于模型理解的优质 Prompt。

			【分析任务】
			针对用户给出的原始 Prompt，完成以下评估与重写：
			1. score：对原始 Prompt 的「清晰度与可执行性」打分（0-100，越高越清晰）。
			2. missing：检测原始 Prompt 缺失的关键要素，仅能是下列受限枚举的子集，不得臆造其他值：
			   ROLE（缺少角色/人设设定）, CONTEXT（缺少背景或上下文）, FORMAT（缺少输出格式要求）,
			   CONSTRAINTS（缺少边界/约束条件）, EXAMPLES（缺少示例/Few-shot）,
			   AUDIENCE（缺少目标受众）, TONE（缺少语气/风格要求）。
			   若各项齐备则输出空数组 []。
			3. issues：给出 1-5 条具体、可操作的修改建议（每条一句精炼短句，中文输出）。
			4. optimized：产出优化重写后的完整 Prompt。必须遵循下方【铁律】。
			5. fewShot：若原始 Prompt 适合示例驱动（如分类、抽取、格式转换），生成 1-3 个 Few-shot 示例；
			   否则输出空数组 []。

			【铁律 —— 必须严格遵守】
			- 保留核心诉求：优化版必须完整保留用户的真实意图与业务逻辑，严禁擅自变更主题、偷换概念或添加用户未要求的任务。
			- 语言一致：若原始 Prompt 为「中文」，则 optimized 与 fewShot 一律使用中文；若原始 Prompt 为「英文」，则一律使用英文；不要中英混排。
			- 结构增强：优化版应显式补充缺失的角色设定、上下文、输出格式与约束，使其可直接复制使用。
			- 仅输出结构化 JSON，不要任何额外解释、前缀或 Markdown 代码块包裹。
			""";
}
