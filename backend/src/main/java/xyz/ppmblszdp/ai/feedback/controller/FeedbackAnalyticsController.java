package xyz.ppmblszdp.ai.feedback.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator;
import xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator.IntentHealthEntry;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto;
import xyz.ppmblszdp.ai.feedback.dto.FeedbackAnalyticsDto.LowScoreCase;
import xyz.ppmblszdp.ai.service.FeedbackService;

/**
 * 反馈驱动质量闭环 REST 控制器。
 *
 * <ul>
 *   <li>{@code GET /api/feedback/analytics} — 完整大盘聚合（趋势 + 模型 + 意图 + 低分案例）</li>
 *   <li>{@code GET /api/feedback/intent-health} — 仅返回意图健康状态（含实时告警）</li>
 *   <li>{@code GET /api/feedback/reflections} — 返回最近点踩低分案例（含 Reflection 分析）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackAnalyticsController {

    private final FeedbackService feedbackService;
    private final IntentFeedbackAccumulator intentFeedbackAccumulator;

    public FeedbackAnalyticsController(
            FeedbackService feedbackService, IntentFeedbackAccumulator intentFeedbackAccumulator) {
        this.feedbackService = feedbackService;
        this.intentFeedbackAccumulator = intentFeedbackAccumulator;
    }

    /**
     * 获取完整的反馈分析大盘数据。
     *
     * @param trendDays     趋势天数（默认 14 天）
     * @param lowScoreLimit 低分案例上限（默认 20 条）
     */
    @GetMapping("/analytics")
    public ResponseEntity<FeedbackAnalyticsDto> getAnalytics(
            @RequestParam(value = "trendDays", required = false, defaultValue = "14") int trendDays,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int lowScoreLimit) {
        if (trendDays < 1 || trendDays > 90) trendDays = 14;
        if (lowScoreLimit < 1 || lowScoreLimit > 100) lowScoreLimit = 20;
        return ResponseEntity.ok(feedbackService.getAnalytics(trendDays, lowScoreLimit));
    }

    /**
     * 获取各意图健康状态列表（含实时内存滑动窗口告警状态）。
     */
    @GetMapping("/intent-health")
    public ResponseEntity<List<IntentHealthEntry>> getIntentHealth() {
        return ResponseEntity.ok(intentFeedbackAccumulator.getIntentHealthSummary());
    }

    /**
     * 获取最近点踩低分案例列表（含 ReflectionEngine 异步分析结果）。
     *
     * @param limit 返回条数上限（默认 20）
     */
    @GetMapping("/reflections")
    public ResponseEntity<List<LowScoreCase>> getReflections(
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) limit = 20;
        FeedbackAnalyticsDto dto = feedbackService.getAnalytics(14, limit);
        return ResponseEntity.ok(dto.lowScoreCases());
    }
}
