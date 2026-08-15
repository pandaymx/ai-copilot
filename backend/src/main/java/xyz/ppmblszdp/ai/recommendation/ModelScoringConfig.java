package xyz.ppmblszdp.ai.recommendation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import xyz.ppmblszdp.ai.intent.IntentType;

/**
 * 模型推荐评分配置：意图→标签映射、维度权重、归一化参数与冷启动基准。
 *
 * <p>所有常量在类加载时即确定，运行期只读、无锁安全。
 */
public final class ModelScoringConfig {

    private ModelScoringConfig() {}

    // ====================== 1. 维度权重（总和 = 1.0）======================

    /** 意图适配权重：IntentType → 模型标签匹配度 */
    public static final double WEIGHT_INTENT_FIT = 0.35;

    /** 历史质量权重：EvaluationService 评测大盘 averageScore */
    public static final double WEIGHT_QUALITY = 0.25;

    /** 用户满意度权重：点赞/点踩反馈 */
    public static final double WEIGHT_SATISFACTION = 0.15;

    /** 性能表现权重：P50 TTFT + Tokens/s */
    public static final double WEIGHT_PERFORMANCE = 0.15;

    /** 成本效率权重：价格越低分越高 */
    public static final double WEIGHT_COST = 0.10;

    // ====================== 2. 冷启动基准 ======================

    /** 无评测数据时的默认质量基准分（中位数） */
    public static final double COLD_START_QUALITY_BASELINE = 0.5;

    /** 无满意度数据时的默认满意度基准分 */
    public static final double COLD_START_SATISFACTION_BASELINE = 0.5;

    /** 无性能数据时的默认性能基准分 */
    public static final double COLD_START_PERFORMANCE_BASELINE = 0.5;

    /**
     * 置信度衰减因子阈值：样本量低于此值时，评分按样本量/阈值比例向基准分衰减。
     * 例：阈值=10、样本量=3 → 分数 = baseline + (实际分 - baseline) × (3/10)
     */
    public static final int CONFIDENCE_SAMPLE_THRESHOLD = 10;

    // ====================== 3. 降级与惩罚 ======================

    /** 全候选模型得分低于此阈值时，直接返回默认兜底模型 */
    public static final double MIN_RECOMMENDATION_THRESHOLD = 0.20;

    /** DEGRADED 模型（高延迟抖动/接近熔断阈值）惩罚系数（乘以此系数降权） */
    public static final double DEGRADED_PENALTY_FACTOR = 0.60;

    // ====================== 4. 归一化参数（Min-Max 上下限）======================

    /** TTFT 归一化下限 (ms)：低于此值视为满分 */
    public static final double TTFT_MIN_MS = 50.0;

    /** TTFT 归一化上限 (ms)：高于此值视为 0 分 */
    public static final double TTFT_MAX_MS = 5000.0;

    /** Tokens/s 归一化下限：低于此值视为 0 分 */
    public static final double TPS_MIN = 5.0;

    /** Tokens/s 归一化上限：高于此值视为满分 */
    public static final double TPS_MAX = 200.0;

    /** 成本归一化下限 (每千 Token 总价 RMB)：低于此值视为满分 */
    public static final BigDecimal COST_MIN = new BigDecimal("0.001");

    /** 成本归一化上限 (每千 Token 总价 RMB)：高于此值视为 0 分 */
    public static final BigDecimal COST_MAX = new BigDecimal("0.200");

    // ====================== 5. Token 预估参数 ======================

    /** 字符→Token 的平均折算系数（中英文混合） */
    public static final double CHARS_PER_TOKEN = 2.0;

    /** 各意图类型的历史平均输出/输入 Token 比例（用于成本预估） */
    public static final Map<IntentType, Double> INTENT_OUTPUT_RATIO = Map.ofEntries(
            Map.entry(IntentType.CHAT, 2.0),
            Map.entry(IntentType.CODE, 4.0),
            Map.entry(IntentType.TRANSLATION, 1.5),
            Map.entry(IntentType.WRITING, 5.0),
            Map.entry(IntentType.ANALYSIS, 4.0),
            Map.entry(IntentType.SEARCH, 3.0),
            Map.entry(IntentType.MATH, 3.5),
            Map.entry(IntentType.MULTIMODAL, 2.0),
            Map.entry(IntentType.IMAGE, 1.0));

    // ====================== 6. 意图→模型标签偏好映射 ======================

    /**
     * 各意图类型偏好的模型能力标签集合。
     * 模型的 tags 字段若包含偏好标签，则获得意图适配加分。
     */
    public static final Map<IntentType, Set<String>> INTENT_PREFERRED_TAGS = Map.ofEntries(
            Map.entry(IntentType.CODE, Set.of("reasoning", "code", "coding")),
            Map.entry(IntentType.MATH, Set.of("reasoning", "math", "logic")),
            Map.entry(IntentType.ANALYSIS, Set.of("reasoning", "analysis", "deep-thinking")),
            Map.entry(IntentType.WRITING, Set.of("creative", "writing", "long-context")),
            Map.entry(IntentType.SEARCH, Set.of("search", "realtime", "tool-use")),
            Map.entry(IntentType.TRANSLATION, Set.of("multilingual", "translation")),
            Map.entry(IntentType.MULTIMODAL, Set.of("vision", "multimodal")),
            Map.entry(IntentType.IMAGE, Set.of("image-generation", "creative")),
            Map.entry(IntentType.CHAT, Set.of("fast", "chat", "general")));

    /**
     * 各意图类型偏好的模型"级别"：high → 优先选高端推理模型，low → 优先选低成本快速模型。
     */
    public static final Map<IntentType, ModelTier> INTENT_MODEL_TIER = Map.ofEntries(
            Map.entry(IntentType.CODE, ModelTier.HIGH),
            Map.entry(IntentType.MATH, ModelTier.HIGH),
            Map.entry(IntentType.ANALYSIS, ModelTier.HIGH),
            Map.entry(IntentType.WRITING, ModelTier.MEDIUM),
            Map.entry(IntentType.SEARCH, ModelTier.MEDIUM),
            Map.entry(IntentType.TRANSLATION, ModelTier.MEDIUM),
            Map.entry(IntentType.MULTIMODAL, ModelTier.MEDIUM),
            Map.entry(IntentType.IMAGE, ModelTier.LOW),
            Map.entry(IntentType.CHAT, ModelTier.LOW));

    public enum ModelTier {
        /** 高端推理模型：精度优先 */
        HIGH,
        /** 中等模型：平衡精度与成本 */
        MEDIUM,
        /** 低成本快速模型：速度/成本优先 */
        LOW
    }

    // ====================== 7. 缓存配置 ======================

    /** 基准分缓存刷新间隔（秒） */
    public static final long CACHE_REFRESH_INTERVAL_SECONDS = 60;

    // ====================== 工具方法 ======================

    /**
     * Min-Max 归一化到 [0, 1]。
     *
     * @param value     原始值
     * @param min       下限（映射为 0 或 1，取决于 lowerIsBetter）
     * @param max       上限
     * @param lowerIsBetter 是否越低越好（如 TTFT、成本）
     * @return 归一化后的 0.0 ~ 1.0 分值
     */
    public static double normalize(double value, double min, double max, boolean lowerIsBetter) {
        if (max <= min) return 0.5;
        double clamped = Math.max(min, Math.min(max, value));
        double ratio = (clamped - min) / (max - min);
        return lowerIsBetter ? (1.0 - ratio) : ratio;
    }

    /**
     * 应用冷启动置信度衰减。
     *
     * @param actualScore 实际计算得分
     * @param baseline    冷启动基准分
     * @param sampleCount 实际样本量
     * @return 衰减后的分值
     */
    public static double applyConfidenceDecay(double actualScore, double baseline, long sampleCount) {
        if (sampleCount >= CONFIDENCE_SAMPLE_THRESHOLD) {
            return actualScore;
        }
        if (sampleCount <= 0) {
            return baseline;
        }
        double confidence = (double) sampleCount / CONFIDENCE_SAMPLE_THRESHOLD;
        return baseline + (actualScore - baseline) * confidence;
    }
}
