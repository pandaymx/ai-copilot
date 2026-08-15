package xyz.ppmblszdp.ai.recommendation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationSummaryDto;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationSummaryDto.ModelLeaderboardEntry;
import xyz.ppmblszdp.ai.evaluation.service.EvaluationService;
import xyz.ppmblszdp.ai.intent.IntentResult;
import xyz.ppmblszdp.ai.intent.IntentType;
import xyz.ppmblszdp.ai.recommendation.ModelScoringConfig.ModelTier;
import xyz.ppmblszdp.ai.registry.HealthStatus;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ModelPerformanceTracker;
import xyz.ppmblszdp.ai.registry.ModelPerformanceTracker.ModelPerformanceSummaryDto;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 智能模型推荐引擎：多维度加权评分、Min-Max 归一化、冷启动置信度衰减、
 * 内存基准分缓存（60s 刷新）、DEGRADED 惩罚降权与全候选低分优雅降级。
 *
 * <p>设计目标：在线计算耗时 &lt; 5ms（缓存命中路径），不阻塞 SSE 首帧渲染。
 */
@Service
public class ModelRecommender implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ModelRecommender.class);

    private final ProviderRegistry providerRegistry;
    private final ModelHealthTracker healthTracker;
    private final ModelPerformanceTracker performanceTracker;
    private final EvaluationService evaluationService;

    /** 基准分缓存：key = "providerId:modelId" → 预计算的 quality/satisfaction/performance/cost 分值 */
    private final AtomicReference<Map<String, CachedBaseScores>> baseScoreCache = new AtomicReference<>(Map.of());

    private ScheduledExecutorService refreshScheduler;

    public ModelRecommender(
            ProviderRegistry providerRegistry,
            ModelHealthTracker healthTracker,
            ModelPerformanceTracker performanceTracker,
            EvaluationService evaluationService) {
        this.providerRegistry = providerRegistry;
        this.healthTracker = healthTracker;
        this.performanceTracker = performanceTracker;
        this.evaluationService = evaluationService;
    }

    // ====================== 生命周期 ======================

    @Override
    public void afterPropertiesSet() {
        refreshBaseScoreCache();
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "model-recommender-cache-refresh");
            t.setDaemon(true);
            return t;
        });
        refreshScheduler.scheduleAtFixedRate(
                this::refreshBaseScoreCacheSafe,
                ModelScoringConfig.CACHE_REFRESH_INTERVAL_SECONDS,
                ModelScoringConfig.CACHE_REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        log.info("✅ [ModelRecommender] 初始化完成，缓存刷新间隔={}s", ModelScoringConfig.CACHE_REFRESH_INTERVAL_SECONDS);
    }

    @Override
    public void destroy() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdownNow();
        }
    }

    // ====================== 公开 API ======================

    /**
     * 基于意图识别结果与当前已解析模型，计算推荐模型并丰富 {@link IntentResult}。
     *
     * @param intentResult 意图分类结果（recommendedModel 可能为 null）
     * @param currentResolved 用户当前选择的模型
     * @param inputMessage 用户当前输入文本（用于成本预估）
     * @return 丰富后的 IntentResult（填充 recommendedModel/recommendedProvider/recommendationReason）
     */
    public IntentResult enrich(IntentResult intentResult, ResolvedModel currentResolved, String inputMessage) {
        if (intentResult == null) {
            return intentResult;
        }
        try {
            ModelRecommendation recommendation = recommend(intentResult.intent(), currentResolved, inputMessage);
            if (recommendation == null) {
                return intentResult;
            }
            // 若推荐模型与当前模型相同，不填充推荐字段
            if (recommendation.modelId().equals(currentResolved.model().id())
                    && recommendation
                            .providerId()
                            .equals(currentResolved.provider().providerId())) {
                return intentResult;
            }
            return new IntentResult(
                    intentResult.intent(),
                    intentResult.label(),
                    recommendation.modelId(),
                    recommendation.providerId(),
                    recommendation.reason(),
                    intentResult.enableRag(),
                    intentResult.enableTools(),
                    intentResult.systemPromptTemplate());
        } catch (Exception e) {
            log.warn("[ModelRecommender] 推荐计算异常，降级返回原始 IntentResult: {}", e.getMessage());
            return intentResult;
        }
    }

    /**
     * 核心推荐算法：遍历全部候选模型，5 维度加权评分取 Top-1。
     */
    public ModelRecommendation recommend(IntentType intentType, ResolvedModel currentResolved, String inputMessage) {
        Map<String, CachedBaseScores> cache = baseScoreCache.get();
        List<ScoredCandidate> candidates = new ArrayList<>();

        for (ProviderDescriptor provider : providerRegistry.providers().values()) {
            for (ModelDescriptor model : provider.models().values()) {
                // 1. 硬过滤：DOWN 状态直接排除
                HealthStatus status = healthTracker.getStatus(provider.providerId(), model.id());
                if (status == HealthStatus.DOWN) {
                    continue;
                }

                String key = buildKey(provider.providerId(), model.id());
                CachedBaseScores baseScores = cache.getOrDefault(key, CachedBaseScores.COLD_START);

                // 2. 在线计算意图适配分
                double intentFitScore = computeIntentFitScore(intentType, model);

                // 3. 加权求和
                double totalScore = ModelScoringConfig.WEIGHT_INTENT_FIT * intentFitScore
                        + ModelScoringConfig.WEIGHT_QUALITY * baseScores.qualityScore()
                        + ModelScoringConfig.WEIGHT_SATISFACTION * baseScores.satisfactionScore()
                        + ModelScoringConfig.WEIGHT_PERFORMANCE * baseScores.performanceScore()
                        + ModelScoringConfig.WEIGHT_COST * baseScores.costScore();

                // 4. DEGRADED 惩罚（HALF_OPEN 状态视为 DEGRADED）
                if (status == HealthStatus.HALF_OPEN) {
                    totalScore *= ModelScoringConfig.DEGRADED_PENALTY_FACTOR;
                }

                // 5. 高延迟抖动惩罚
                if (baseScores.isHighLatencyJitter()) {
                    totalScore *= ModelScoringConfig.DEGRADED_PENALTY_FACTOR;
                }

                candidates.add(new ScoredCandidate(
                        provider.providerId(),
                        model.id(),
                        model.displayName(),
                        totalScore,
                        intentFitScore,
                        baseScores,
                        model));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 排序取 Top-1
        candidates.sort(Comparator.comparingDouble(ScoredCandidate::totalScore).reversed());
        ScoredCandidate best = candidates.get(0);

        // 全候选低于阈值 → 优雅降级到默认模型
        if (best.totalScore() < ModelScoringConfig.MIN_RECOMMENDATION_THRESHOLD) {
            log.debug(
                    "[ModelRecommender] 全候选得分 ({}) < 阈值 ({})，降级返回默认模型",
                    best.totalScore(),
                    ModelScoringConfig.MIN_RECOMMENDATION_THRESHOLD);
            return buildDefaultFallbackRecommendation(intentType);
        }

        // 生成推荐理由
        String reason = buildRecommendationReason(best, intentType);

        // 预估成本
        Double estimatedCost = estimateCost(best.model(), intentType, inputMessage);

        return new ModelRecommendation(
                best.providerId(), best.modelId(), best.displayName(), reason, best.totalScore(), estimatedCost);
    }

    // ====================== 缓存刷新 ======================

    private void refreshBaseScoreCacheSafe() {
        try {
            refreshBaseScoreCache();
        } catch (Exception e) {
            log.warn("[ModelRecommender] 基准分缓存刷新异常: {}", e.getMessage());
        }
    }

    /**
     * 全量刷新基准分缓存。
     * 从 EvaluationService、ModelPerformanceTracker、ModelDescriptor 抽取并归一化各维度基准分。
     */
    void refreshBaseScoreCache() {
        Map<String, CachedBaseScores> newCache = new ConcurrentHashMap<>();

        // 1. 评测质量数据
        EvaluationSummaryDto summary = null;
        try {
            summary = evaluationService.getSummary();
        } catch (Exception e) {
            log.debug("[ModelRecommender] 获取评测大盘异常: {}", e.getMessage());
        }
        Map<String, ModelLeaderboardEntry> leaderboardMap = new ConcurrentHashMap<>();
        if (summary != null && summary.leaderboard() != null) {
            for (ModelLeaderboardEntry entry : summary.leaderboard()) {
                leaderboardMap.put(buildKey(entry.provider(), entry.model()), entry);
            }
        }

        // 2. 满意度数据
        Map<String, Double> satisfactionMap = Collections.emptyMap();
        try {
            satisfactionMap = evaluationService.getFeedbackSatisfactionByModel();
        } catch (Exception e) {
            log.debug("[ModelRecommender] 获取满意度数据异常: {}", e.getMessage());
        }

        // 3. 性能数据
        List<ModelPerformanceSummaryDto> perfSummaries = Collections.emptyList();
        try {
            perfSummaries = performanceTracker.getAllSummaries();
        } catch (Exception e) {
            log.debug("[ModelRecommender] 获取性能数据异常: {}", e.getMessage());
        }
        Map<String, ModelPerformanceSummaryDto> perfMap = new ConcurrentHashMap<>();
        for (ModelPerformanceSummaryDto perf : perfSummaries) {
            perfMap.put(buildKey(perf.providerId(), perf.modelId()), perf);
        }

        // 4. 遍历所有模型计算基准分
        for (ProviderDescriptor provider : providerRegistry.providers().values()) {
            for (ModelDescriptor model : provider.models().values()) {
                String key = buildKey(provider.providerId(), model.id());

                // Quality
                double qualityScore;
                long qualitySamples;
                ModelLeaderboardEntry le = leaderboardMap.get(key);
                if (le != null && le.count() > 0) {
                    qualityScore = le.averageScore();
                    qualitySamples = le.count();
                } else {
                    qualityScore = ModelScoringConfig.COLD_START_QUALITY_BASELINE;
                    qualitySamples = 0;
                }
                qualityScore = ModelScoringConfig.applyConfidenceDecay(
                        qualityScore, ModelScoringConfig.COLD_START_QUALITY_BASELINE, qualitySamples);

                // Satisfaction
                double satisfactionScore;
                Double satVal = satisfactionMap.get(model.id());
                long satSamples = satVal != null ? 1 : 0; // 简化：有数据记为可用
                if (satVal != null) {
                    satisfactionScore = satVal;
                    // 粗略估算样本量（满意度 map 不直接暴露样本数）
                    satSamples = 5; // 保守估算
                } else {
                    satisfactionScore = ModelScoringConfig.COLD_START_SATISFACTION_BASELINE;
                }
                satisfactionScore = ModelScoringConfig.applyConfidenceDecay(
                        satisfactionScore, ModelScoringConfig.COLD_START_SATISFACTION_BASELINE, satSamples);

                // Performance
                ModelPerformanceSummaryDto perf = perfMap.get(key);
                double performanceScore;
                long perfSamples;
                boolean highLatencyJitter = false;
                if (perf != null && perf.sampleCount() > 0) {
                    // 性能分 = 50% TTFT 分 + 50% TPS 分
                    double ttftScore = ModelScoringConfig.normalize(
                            perf.p50TtftMs(), ModelScoringConfig.TTFT_MIN_MS, ModelScoringConfig.TTFT_MAX_MS, true);
                    double tpsScore = ModelScoringConfig.normalize(
                            perf.avgTokensPerSecond(), ModelScoringConfig.TPS_MIN, ModelScoringConfig.TPS_MAX, false);
                    performanceScore = 0.5 * ttftScore + 0.5 * tpsScore;
                    perfSamples = perf.sampleCount();

                    // 高延迟抖动检测：P90/P50 > 3.0 视为抖动
                    if (perf.p50TtftMs() > 0 && perf.p90TtftMs() / perf.p50TtftMs() > 3.0) {
                        highLatencyJitter = true;
                    }
                } else {
                    performanceScore = ModelScoringConfig.COLD_START_PERFORMANCE_BASELINE;
                    perfSamples = 0;
                }
                performanceScore = ModelScoringConfig.applyConfidenceDecay(
                        performanceScore, ModelScoringConfig.COLD_START_PERFORMANCE_BASELINE, perfSamples);

                // Cost
                BigDecimal totalCostPerK = model.inputPricePerK().add(model.outputPricePerK());
                double costScore = ModelScoringConfig.normalize(
                        totalCostPerK.doubleValue(),
                        ModelScoringConfig.COST_MIN.doubleValue(),
                        ModelScoringConfig.COST_MAX.doubleValue(),
                        true);

                newCache.put(
                        key,
                        new CachedBaseScores(
                                qualityScore,
                                satisfactionScore,
                                performanceScore,
                                costScore,
                                highLatencyJitter,
                                qualitySamples,
                                perfSamples));
            }
        }

        baseScoreCache.set(newCache);
        log.debug("[ModelRecommender] 基准分缓存刷新完成，模型数={}", newCache.size());
    }

    // ====================== 意图适配分（在线计算）======================

    /**
     * 计算意图适配分 (0.0 ~ 1.0)。
     * 基于标签匹配与模型级别偏好。
     */
    double computeIntentFitScore(IntentType intentType, ModelDescriptor model) {
        double score = 0.3; // 基础分：所有模型至少有 0.3 的适配度

        if (intentType == null) {
            return score;
        }

        // 标签匹配：每命中一个偏好标签 +0.15，上限 0.45
        Set<String> preferredTags = ModelScoringConfig.INTENT_PREFERRED_TAGS.getOrDefault(intentType, Set.of());
        List<String> modelTags = model.tags();
        if (modelTags != null && !preferredTags.isEmpty()) {
            int matchCount = 0;
            for (String tag : modelTags) {
                if (preferredTags.contains(tag.toLowerCase())) {
                    matchCount++;
                }
            }
            score += Math.min(0.45, matchCount * 0.15);
        }

        // 模型级别匹配
        ModelTier preferredTier = ModelScoringConfig.INTENT_MODEL_TIER.getOrDefault(intentType, ModelTier.MEDIUM);
        BigDecimal totalCost = model.inputPricePerK().add(model.outputPricePerK());
        double costValue = totalCost.doubleValue();

        switch (preferredTier) {
            case HIGH -> {
                // 高端偏好：贵模型+0.25，便宜模型不加分
                if (costValue > 0.05) score += 0.25;
                else if (costValue > 0.02) score += 0.10;
            }
            case LOW -> {
                // 低成本偏好：便宜模型+0.25
                if (costValue < 0.01) score += 0.25;
                else if (costValue < 0.03) score += 0.15;
            }
            case MEDIUM -> {
                // 中等偏好：中等价位+0.15
                score += 0.10;
            }
        }

        // 特殊能力要求
        if (intentType == IntentType.MULTIMODAL && model.supportsVision()) {
            score += 0.20;
        }

        return Math.min(1.0, score);
    }

    // ====================== 推荐理由生成 ======================

    private String buildRecommendationReason(ScoredCandidate best, IntentType intentType) {
        StringBuilder sb = new StringBuilder();
        sb.append("推荐 ").append(best.displayName());

        String intentLabel = intentType != null ? intentType.getLabel() : "通用";
        sb.append("（").append(intentLabel).append("任务适配度 ");
        sb.append(String.format("%.0f%%", best.intentFitScore() * 100));
        sb.append("）");

        CachedBaseScores base = best.baseScores();
        if (base.qualitySamples() > 0) {
            sb.append("，评测质量 ").append(String.format("%.0f%%", base.qualityScore() * 100));
        }
        if (base.perfSamples() > 0) {
            sb.append("，性能 ").append(String.format("%.0f%%", base.performanceScore() * 100));
        }

        return sb.toString();
    }

    // ====================== 成本预估 ======================

    /**
     * 分层估算预估成本（RMB）。
     * 1. 输入 Token = 输入文本字符数 / CHARS_PER_TOKEN
     * 2. 输出 Token = 输入 Token × 意图输出比例
     * 3. 成本 = (inputTokens × inputPricePerK + outputTokens × outputPricePerK) / 1000
     */
    Double estimateCost(ModelDescriptor model, IntentType intentType, String inputMessage) {
        if (model == null || inputMessage == null || inputMessage.isBlank()) {
            return null;
        }
        double inputTokens = inputMessage.length() / ModelScoringConfig.CHARS_PER_TOKEN;
        double outputRatio = ModelScoringConfig.INTENT_OUTPUT_RATIO.getOrDefault(
                intentType != null ? intentType : IntentType.CHAT, 2.0);
        double outputTokens = inputTokens * outputRatio;

        double costRmb = (inputTokens * model.inputPricePerK().doubleValue()
                        + outputTokens * model.outputPricePerK().doubleValue())
                / 1000.0;

        return Math.round(costRmb * 10000.0) / 10000.0;
    }

    // ====================== 优雅降级 ======================

    private ModelRecommendation buildDefaultFallbackRecommendation(IntentType intentType) {
        String defaultProvider = providerRegistry.defaultProviderId();
        String defaultModel = providerRegistry.defaultModelId();
        if (defaultProvider == null || defaultModel == null) {
            return null;
        }
        return new ModelRecommendation(defaultProvider, defaultModel, defaultModel, "所有模型评分较低，推荐使用默认模型", 0.5, null);
    }

    // ====================== 内部数据结构 ======================

    private String buildKey(String providerId, String modelId) {
        return (providerId != null ? providerId.trim().toLowerCase() : "default") + ":"
                + (modelId != null ? modelId.trim().toLowerCase() : "default");
    }

    /** 缓存的预计算基准分（不含意图适配维度，那是在线计算的） */
    record CachedBaseScores(
            double qualityScore,
            double satisfactionScore,
            double performanceScore,
            double costScore,
            boolean isHighLatencyJitter,
            long qualitySamples,
            long perfSamples) {

        static final CachedBaseScores COLD_START = new CachedBaseScores(
                ModelScoringConfig.COLD_START_QUALITY_BASELINE,
                ModelScoringConfig.COLD_START_SATISFACTION_BASELINE,
                ModelScoringConfig.COLD_START_PERFORMANCE_BASELINE,
                0.5,
                false,
                0,
                0);
    }

    record ScoredCandidate(
            String providerId,
            String modelId,
            String displayName,
            double totalScore,
            double intentFitScore,
            CachedBaseScores baseScores,
            ModelDescriptor model) {}
}
