package xyz.ppmblszdp.ai.service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.evaluation.service.EvaluationService;
import xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto.DayBucket;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto.GlobalStats;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto.IntentHealthEntry;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto.LowScoreCase;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto.ModelSatisfactionEntry;
import xyz.ppmblszdp.ai.intent.IntentType;
import xyz.ppmblszdp.ai.reflection.ReflectionAssessment;
import xyz.ppmblszdp.ai.reflection.ReflectionEngine;
import xyz.ppmblszdp.ai.repository.FeedbackRepository;

/**
 * 消息评价反馈服务。
 *
 * <p>在保存反馈后，异步触发三条质量闭环管道：
 * <ol>
 *   <li><b>管道 1 → EvaluationService</b>：点踩时自动注入评测队列，贡献模型满意度维度分</li>
 *   <li><b>管道 2 → IntentFeedbackAccumulator</b>：累积意图维度信号，超阈值时触发告警</li>
 *   <li><b>管道 3 → ReflectionEngine</b>：点踩时异步回溯反思，分析失败原因并持久化</li>
 * </ol>
 *
 * <p><b>限流策略</b>：管道 3（Reflection LLM 调用）通过 JDK {@link Semaphore} +
 * {@link ScheduledExecutorService} 实现滑动窗口限流，每分钟最多 10 次 LLM 反思调用，
 * 防止恶意连续点踩导致 API 配额耗尽。
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /** Reflection 限流：每分钟最多触发 10 次 LLM 调用 */
    private static final int REFLECTION_RATE_PER_MINUTE = 10;

    private final FeedbackRepository feedbackRepository;
    private final EvaluationService evaluationService;
    private final IntentFeedbackAccumulator intentFeedbackAccumulator;
    private final ReflectionEngine reflectionEngine;

    /** 异步管道线程池（虚拟线程，轻量无阻塞） */
    private final ExecutorService asyncPool = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 滑动窗口限流实现：Semaphore 控制当前窗口内 Reflection 调用次数，
     * ScheduledExecutorService 每分钟重置许可数。
     */
    private final Semaphore reflectionRateLimiter = new Semaphore(REFLECTION_RATE_PER_MINUTE, true);

    private final AtomicInteger reflectionCallsThisWindow = new AtomicInteger(0);
    private final ScheduledExecutorService rateLimiterReset = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofVirtual().name("feedback-rate-reset").unstarted(r));

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            EvaluationService evaluationService,
            IntentFeedbackAccumulator intentFeedbackAccumulator,
            ReflectionEngine reflectionEngine) {
        this.feedbackRepository = feedbackRepository;
        this.evaluationService = evaluationService;
        this.intentFeedbackAccumulator = intentFeedbackAccumulator;
        this.reflectionEngine = reflectionEngine;

        // 每分钟重置 Reflection 限流 Semaphore（滑动窗口重置）
        rateLimiterReset.scheduleAtFixedRate(
                () -> {
                    int used = reflectionCallsThisWindow.getAndSet(0);
                    int toRelease = REFLECTION_RATE_PER_MINUTE - reflectionRateLimiter.availablePermits();
                    if (toRelease > 0) {
                        reflectionRateLimiter.release(toRelease);
                    }
                    if (used > 0) {
                        log.debug("[FeedbackService] Reflection 限流窗口重置，本窗口共消耗 {} 次", used);
                    }
                },
                1,
                1,
                TimeUnit.MINUTES);
    }

    /**
     * 保存点赞/点踩反馈，并异步触发三条质量闭环管道。
     * userId 来自服务端受信任身份，不再信任请求体。
     */
    public void saveFeedback(String userId, ChatFeedbackRequest request) {
        // 同步落库（主路径）
        feedbackRepository.saveFeedback(userId, request);

        // 异步三管道（各自独立，任意管道失败不影响其他）
        triggerPipeline1EvaluationAsync(request);
        triggerPipeline2IntentAsync(request);
        triggerPipeline3ReflectionAsync(request);
    }

    // ====================== 管道 1：→ EvaluationService ======================

    private void triggerPipeline1EvaluationAsync(ChatFeedbackRequest request) {
        if (request.rating() == null) return;
        CompletableFuture.runAsync(
                () -> {
                    try {
                        evaluationService.ingestFeedbackCase(request);
                    } catch (Exception e) {
                        log.warn("[Pipeline1-Eval] 反馈注入评测队列失败（静默降级）: {}", e.getMessage());
                    }
                },
                asyncPool);
    }

    // ====================== 管道 2：→ IntentFeedbackAccumulator ======================

    private void triggerPipeline2IntentAsync(ChatFeedbackRequest request) {
        String intent = request.intent();
        if (intent == null || intent.isBlank()) return;
        String rating = request.rating();
        if (rating == null) return;

        CompletableFuture.runAsync(
                () -> {
                    try {
                        int delta = "THUMBS_UP".equalsIgnoreCase(rating) ? 1 : -1;
                        intentFeedbackAccumulator.record(intent, delta);
                    } catch (Exception e) {
                        log.warn("[Pipeline2-Intent] 意图信号累积失败（静默降级）: {}", e.getMessage());
                    }
                },
                asyncPool);
    }

    // ====================== 管道 3：→ ReflectionEngine ======================

    private void triggerPipeline3ReflectionAsync(ChatFeedbackRequest request) {
        // 仅在点踩时触发
        if (!"THUMBS_DOWN".equalsIgnoreCase(request.rating())) return;

        String userPrompt = request.userPrompt();
        String assistantReply = request.assistantReply();

        // 内容门槛：assistantReply 至少 50 字符才有分析价值
        if (userPrompt == null || userPrompt.isBlank()) return;
        if (assistantReply == null || assistantReply.length() < 50) return;

        String messageId = request.messageId();

        CompletableFuture.runAsync(
                () -> {
                    try {
                        // 幂等守卫：若已分析则跳过
                        if (messageId != null
                                && !messageId.isBlank()
                                && feedbackRepository.isReflectionDone(messageId)) {
                            log.debug("[Pipeline3-Reflection] messageId={} 已完成反思，跳过", messageId);
                            return;
                        }

                        // 限流检查（非阻塞 tryAcquire）
                        if (!reflectionRateLimiter.tryAcquire()) {
                            log.warn("[Pipeline3-Reflection] 限流触发（每分钟最多 {} 次），跳过本次反思", REFLECTION_RATE_PER_MINUTE);
                            return;
                        }
                        reflectionCallsThisWindow.incrementAndGet();

                        // 调用 ReflectionEngine 回溯分析
                        log.debug("[Pipeline3-Reflection] 开始回溯反思 [messageId={}]", messageId);
                        ReflectionAssessment assessment = reflectionEngine.evaluate(userPrompt, assistantReply, null);

                        // 提取分类标签
                        String category = categorizeReflection(assessment);

                        // 持久化回 PostgreSQL（幂等更新）
                        String analysisText = buildAnalysisText(assessment);
                        if (messageId != null && !messageId.isBlank()) {
                            feedbackRepository.updateReflection(messageId, analysisText, category);
                        }
                        log.info(
                                "[Pipeline3-Reflection] 反思完成 [messageId={}, passed={}, category={}]",
                                messageId,
                                assessment.passed(),
                                category);

                    } catch (Exception e) {
                        log.warn("[Pipeline3-Reflection] 反思分析失败（静默降级）: {}", e.getMessage());
                    }
                },
                asyncPool);
    }

    private String categorizeReflection(ReflectionAssessment assessment) {
        if (assessment == null || assessment.passed()) return "PASSED";
        Double factuality = assessment.factualityScore();
        Double completeness = assessment.completenessScore();
        if (factuality != null && factuality < 0.5) return "FACTUAL_ERROR";
        if (completeness != null && completeness < 0.5) return "INCOMPLETE";
        return "QUALITY_ISSUE";
    }

    private String buildAnalysisText(ReflectionAssessment assessment) {
        if (assessment == null) return "反思分析失败";
        if (assessment.passed()) return "质量检查通过，无明显问题";
        StringBuilder sb = new StringBuilder();
        if (assessment.correctionExplanation() != null) {
            sb.append("【问题分析】").append(assessment.correctionExplanation()).append("\n");
        }
        if (assessment.issues() != null && !assessment.issues().isEmpty()) {
            sb.append("【具体问题】").append(String.join("；", assessment.issues())).append("\n");
        }
        if (assessment.supplementalCorrection() != null) {
            sb.append("【修正建议】").append(assessment.supplementalCorrection());
        }
        return sb.toString().trim();
    }

    // ====================== 聚合分析 API ======================

    /**
     * 获取完整的反馈分析大盘数据。
     */
    public FeedbackAnalyticsDto getAnalytics(int trendDays, int lowScoreLimit) {
        // 1. 趋势
        List<DayBucket> trend = buildTrend(trendDays);

        // 2. 模型满意度
        List<ModelSatisfactionEntry> modelSatisfaction = buildModelSatisfaction();

        // 3. 意图健康度（结合 DB 聚合 + 内存滑动窗口告警）
        List<IntentHealthEntry> intentHealth = buildIntentHealth();

        // 4. 低分案例
        List<LowScoreCase> lowScoreCases = buildLowScoreCases(lowScoreLimit);

        // 5. 全局摘要
        GlobalStats globalStats = buildGlobalStats(trend, intentHealth);

        return new FeedbackAnalyticsDto(trend, modelSatisfaction, intentHealth, lowScoreCases, globalStats);
    }

    private List<DayBucket> buildTrend(int days) {
        List<Map<String, Object>> rows = feedbackRepository.queryDailyTrend(days);
        return rows.stream()
                .map(row -> {
                    String date = String.valueOf(row.getOrDefault("date", ""));
                    long up = toLong(row.get("thumbs_up"));
                    long down = toLong(row.get("thumbs_down"));
                    long total = up + down;
                    double rate = total > 0 ? Math.round((double) up / total * 1000.0) / 10.0 : 0.0;
                    return new DayBucket(date, up, down, rate);
                })
                .toList();
    }

    private List<ModelSatisfactionEntry> buildModelSatisfaction() {
        List<Map<String, Object>> rows = feedbackRepository.queryModelSatisfaction();
        return rows.stream()
                .map(row -> {
                    String modelId = String.valueOf(row.getOrDefault("model_id", "unknown"));
                    long up = toLong(row.get("thumbs_up"));
                    long down = toLong(row.get("thumbs_down"));
                    long total = toLong(row.get("total"));
                    double rate = total > 0 ? Math.round((double) up / total * 1000.0) / 10.0 : 0.0;
                    return new ModelSatisfactionEntry(modelId, up, down, total, rate);
                })
                .toList();
    }

    private List<IntentHealthEntry> buildIntentHealth() {
        // DB 聚合数据
        List<Map<String, Object>> dbRows = feedbackRepository.queryIntentSatisfaction();
        return dbRows.stream()
                .map(row -> {
                    String intent = String.valueOf(row.getOrDefault("intent", "UNKNOWN"));
                    long up = toLong(row.get("thumbs_up"));
                    long down = toLong(row.get("thumbs_down"));
                    long total = up + down;
                    double downRate = total > 0 ? Math.round((double) down / total * 1000.0) / 10.0 : 0.0;
                    // 告警状态结合内存滑动窗口（更实时）
                    boolean alerting = intentFeedbackAccumulator.isAlerting(intent);
                    // 获取人类可读标签
                    String label = resolveIntentLabel(intent);
                    return new IntentHealthEntry(intent, label, up, down, alerting, downRate);
                })
                .toList();
    }

    private List<LowScoreCase> buildLowScoreCases(int limit) {
        List<Map<String, Object>> rows = feedbackRepository.queryLowScoreCases(limit);
        return rows.stream()
                .map(row -> new LowScoreCase(
                        String.valueOf(row.getOrDefault("message_id", "")),
                        String.valueOf(row.getOrDefault("conversation_id", "")),
                        row.get("model_id") != null ? String.valueOf(row.get("model_id")) : null,
                        row.get("intent") != null ? String.valueOf(row.get("intent")) : null,
                        row.get("user_prompt") != null ? String.valueOf(row.get("user_prompt")) : null,
                        row.get("comment") != null ? String.valueOf(row.get("comment")) : null,
                        row.get("reflection_analysis") != null ? String.valueOf(row.get("reflection_analysis")) : null,
                        row.get("reflection_category") != null ? String.valueOf(row.get("reflection_category")) : null,
                        toLong(row.get("created_at"))))
                .toList();
    }

    private GlobalStats buildGlobalStats(List<DayBucket> trend, List<IntentHealthEntry> intentHealth) {
        long totalUp = trend.stream().mapToLong(b -> b.thumbsUp()).sum();
        long totalDown = trend.stream().mapToLong(b -> b.thumbsDown()).sum();
        long total = totalUp + totalDown;
        double overallRate = total > 0 ? Math.round((double) totalUp / total * 1000.0) / 10.0 : 0.0;
        long alertingCount = intentHealth.stream().filter(e -> e.alerting()).count();
        return new GlobalStats(total, totalUp, totalDown, overallRate, alertingCount);
    }

    private String resolveIntentLabel(String intent) {
        try {
            return IntentType.valueOf(intent.toUpperCase()).getLabel();
        } catch (Exception e) {
            return intent;
        }
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    @PreDestroy
    public void shutdown() {
        asyncPool.shutdown();
        rateLimiterReset.shutdown();
    }
}
