package xyz.ppmblszdp.ai.feedback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 意图反馈信号累积器（滑动窗口实现）。
 *
 * <p>职责：
 * <ol>
 *   <li>接收来自 {@link xyz.ppmblszdp.ai.service.FeedbackService} 的点赞/点踩信号（按意图聚合）</li>
 *   <li>维护每种意图最近 {@link #WINDOW_SIZE} 条信号的滑动窗口</li>
 *   <li>当某意图点踩率超过 {@link #ALERT_THRESHOLD}（30%）时，标记 {@code alerting=true}</li>
 *   <li>提供意图告警状态查询，供 {@code ChatOrchestrator} 实现自愈型意图路由切换</li>
 * </ol>
 */
@Service
public class IntentFeedbackAccumulator {

    private static final Logger log = LoggerFactory.getLogger(IntentFeedbackAccumulator.class);

    /** 每种意图滑动窗口最大容量 */
    static final int WINDOW_SIZE = 200;

    /** 点踩率告警阈值（30%） */
    static final double ALERT_THRESHOLD = 0.30;

    /**
     * 意图专属的滑动窗口，存储 +1（点赞）或 -1（点踩）信号。
     * 线程安全：外层 Map 用 ConcurrentHashMap，内层 Deque 同步操作。
     */
    private final Map<String, Deque<Integer>> windows = new ConcurrentHashMap<>();

    /** 意图累计点赞总计数（不受窗口限制，用于历史趋势展示） */
    private final Map<String, Long> totalUp = new ConcurrentHashMap<>();

    /** 意图累计点踩总计数 */
    private final Map<String, Long> totalDown = new ConcurrentHashMap<>();

    /**
     * 记录一条意图反馈信号。
     *
     * @param intent 意图类型字符串（如 "CODE", "CHAT"）
     * @param delta  +1 表示点赞，-1 表示点踩
     */
    public synchronized void record(String intent, int delta) {
        if (intent == null || intent.isBlank()) return;
        String key = intent.toUpperCase().trim();

        Deque<Integer> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>(WINDOW_SIZE));
        if (window.size() >= WINDOW_SIZE) {
            window.pollFirst(); // 移出最旧的信号
        }
        window.addLast(delta > 0 ? 1 : -1);

        if (delta > 0) {
            totalUp.merge(key, 1L, (oldVal, newVal) -> oldVal + newVal);
        } else {
            totalDown.merge(key, 1L, (oldVal, newVal) -> oldVal + newVal);
        }

        // 告警日志（达到阈值时输出一次）
        double thumbsDownRate = computeThumbsDownRate(window);
        if (thumbsDownRate >= ALERT_THRESHOLD) {
            log.warn(
                    "⚠️ [IntentFeedbackAccumulator] 意图告警 [intent={}, thumbsDownRate={:.1f}%，窗口={}条]",
                    key, thumbsDownRate * 100, window.size());
        }
    }

    /**
     * 获取指定意图是否处于告警状态（点踩率 ≥ 30%，且窗口已有至少 10 条数据）。
     */
    public boolean isAlerting(String intent) {
        if (intent == null || intent.isBlank()) return false;
        Deque<Integer> window = windows.get(intent.toUpperCase().trim());
        if (window == null || window.size() < 10) return false;
        return computeThumbsDownRate(window) >= ALERT_THRESHOLD;
    }

    /**
     * 获取所有意图的健康摘要列表。
     */
    public List<IntentHealthEntry> getIntentHealthSummary() {
        List<IntentHealthEntry> result = new ArrayList<>();
        for (Map.Entry<String, Deque<Integer>> entry : windows.entrySet()) {
            String intent = entry.getKey();
            Deque<Integer> window = entry.getValue();
            long up = totalUp.getOrDefault(intent, 0L);
            long down = totalDown.getOrDefault(intent, 0L);
            boolean alerting = window.size() >= 10 && computeThumbsDownRate(window) >= ALERT_THRESHOLD;
            result.add(new IntentHealthEntry(intent, up, down, alerting));
        }
        result.sort((a, b) -> Double.compare(b.thumbsDown(), a.thumbsDown()));
        return result;
    }

    private double computeThumbsDownRate(Deque<Integer> window) {
        if (window.isEmpty()) return 0.0;
        long downCount = window.stream().filter(v -> v < 0).count();
        return (double) downCount / window.size();
    }

    /**
     * 意图健康状态条目（供 REST 响应与 ChatOrchestrator 路由查询使用）。
     */
    public record IntentHealthEntry(String intent, long thumbsUp, long thumbsDown, boolean alerting) {}
}
