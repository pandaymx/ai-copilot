package xyz.ppmblszdp.ai.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工业级模型流式性能监控与 P50/P90 延迟统计器 (ModelPerformanceTracker)。
 *
 * <p>特性：
 * <ul>
 *   <li><b>无锁环形采样</b>：每个模型维护固定容量 (默认 300) 的环形样本队列；</li>
 *   <li><b>读写分离与快照缓存</b>：写操作更新脏标记，读操作复用快照或按需重新计算，避免高频查询开销；</li>
 *   <li><b>准确分位数计算</b>：支持首字延迟 (TTFT) 与总耗时的 P50、P90、Avg、Min、Max 计算；</li>
 *   <li><b>冷启动低置信度标记</b>：样本量低于阈值 (如 &lt; 5) 时标记 {@code lowSampleWarning=true}；</li>
 *   <li><b>纯生成速率公式</b>：排除首字延迟与工具调用耗时干扰，准确统计 Token 输出速度。</li>
 * </ul>
 */
@Component
public class ModelPerformanceTracker {

    private static final Logger log = LoggerFactory.getLogger(ModelPerformanceTracker.class);

    private static final int RING_BUFFER_CAPACITY = 300;
    private static final int LOW_SAMPLE_THRESHOLD = 5;

    private final ConcurrentHashMap<String, ModelSampleBuffer> buffers = new ConcurrentHashMap<>();

    public record PerformanceSample(
            long timeToFirstTokenMs,
            double tokensPerSecond,
            long totalDurationMs,
            long toolCallDurationMs,
            int completionTokens,
            boolean isEstimated,
            long timestamp) {}

    public record ModelPerformanceSummaryDto(
            String providerId,
            String modelId,
            long sampleCount,
            double p50TtftMs,
            double p90TtftMs,
            double avgTtftMs,
            double minTtftMs,
            double maxTtftMs,
            double p50TotalDurationMs,
            double p90TotalDurationMs,
            double avgTotalDurationMs,
            double avgTokensPerSecond,
            double maxTokensPerSecond,
            double avgToolCallDurationMs,
            boolean lowSampleWarning) {}

    /**
     * 计算纯生成速率 (Tokens/s)。
     * 公式：Tokens / ((TotalDuration - TTFT - ToolCallDuration) / 1000.0)
     */
    public static double calculateTokensPerSecond(
            int completionTokens, long totalDurationMs, long ttftMs, long toolCallDurationMs) {
        if (completionTokens <= 0) {
            return 0.0;
        }
        long pureGenDurationMs = totalDurationMs - Math.max(0, ttftMs) - Math.max(0, toolCallDurationMs);
        if (pureGenDurationMs <= 10) {
            // 如果生成时间极短或仅 1 个 token，以总耗时为分母保底，避免除以 0 或异常膨胀
            pureGenDurationMs = Math.max(1, totalDurationMs);
        }
        double rate = (completionTokens * 1000.0) / pureGenDurationMs;
        // 保留一位小数
        return Math.round(rate * 10.0) / 10.0;
    }

    /**
     * 记录一次流式调用的性能指标。
     */
    public void record(
            String providerId,
            String modelId,
            long timeToFirstTokenMs,
            double tokensPerSecond,
            long totalDurationMs,
            long toolCallDurationMs,
            int completionTokens,
            boolean isEstimated) {
        String key = buildKey(providerId, modelId);
        ModelSampleBuffer buffer = buffers.computeIfAbsent(key, k -> new ModelSampleBuffer(providerId, modelId));
        PerformanceSample sample = new PerformanceSample(
                Math.max(0, timeToFirstTokenMs),
                Math.max(0.0, tokensPerSecond),
                Math.max(0, totalDurationMs),
                Math.max(0, toolCallDurationMs),
                Math.max(0, completionTokens),
                isEstimated,
                System.currentTimeMillis());
        buffer.add(sample);
        log.debug(
                "⚡ [ModelPerformanceTracker] 记录模型 [{}] 性能: TTFT={}ms, Rate={:.1f}t/s, Total={}ms, Tool={}ms",
                key,
                timeToFirstTokenMs,
                tokensPerSecond,
                totalDurationMs,
                toolCallDurationMs);
    }

    /**
     * 获取指定模型的性能统计摘要。
     */
    public ModelPerformanceSummaryDto getSummary(String providerId, String modelId) {
        String key = buildKey(providerId, modelId);
        ModelSampleBuffer buffer = buffers.get(key);
        if (buffer == null) {
            return new ModelPerformanceSummaryDto(providerId, modelId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
        }
        return buffer.getSummary();
    }

    /**
     * 获取所有已知模型的性能统计大盘列表。
     */
    public List<ModelPerformanceSummaryDto> getAllSummaries() {
        List<ModelPerformanceSummaryDto> list = new ArrayList<>();
        for (ModelSampleBuffer buffer : buffers.values()) {
            list.add(buffer.getSummary());
        }
        return list;
    }

    /**
     * 重置某模型或全部统计（用于测试或管理员清空）。
     */
    public void resetAll() {
        buffers.clear();
    }

    private String buildKey(String providerId, String modelId) {
        return (providerId != null ? providerId.trim().toLowerCase() : "default") + ":"
                + (modelId != null ? modelId.trim().toLowerCase() : "default");
    }

    /**
     * 单模型性能采样环形缓冲区与快照缓存。
     */
    private static final class ModelSampleBuffer {
        private final String providerId;
        private final String modelId;
        private final PerformanceSample[] ring = new PerformanceSample[RING_BUFFER_CAPACITY];
        private final AtomicInteger head = new AtomicInteger(0);
        private final AtomicInteger totalCount = new AtomicInteger(0);
        private final AtomicReference<ModelPerformanceSummaryDto> cachedSummary = new AtomicReference<>();
        private volatile boolean dirty = true;

        public ModelSampleBuffer(String providerId, String modelId) {
            this.providerId = providerId;
            this.modelId = modelId;
        }

        public void add(PerformanceSample sample) {
            int idx = head.getAndUpdate(i -> (i + 1) % RING_BUFFER_CAPACITY);
            ring[idx] = sample;
            totalCount.incrementAndGet();
            dirty = true;
        }

        public ModelPerformanceSummaryDto getSummary() {
            if (!dirty && cachedSummary.get() != null) {
                return cachedSummary.get();
            }

            // 快照复制当前采样
            List<PerformanceSample> snapshot = new ArrayList<>(RING_BUFFER_CAPACITY);
            for (int i = 0; i < RING_BUFFER_CAPACITY; i++) {
                PerformanceSample s = ring[i];
                if (s != null) {
                    snapshot.add(s);
                }
            }

            int count = snapshot.size();
            if (count == 0) {
                ModelPerformanceSummaryDto empty =
                        new ModelPerformanceSummaryDto(providerId, modelId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
                cachedSummary.set(empty);
                dirty = false;
                return empty;
            }

            double[] ttfts = new double[count];
            double[] totals = new double[count];
            double[] rates = new double[count];
            double[] tools = new double[count];

            double ttftSum = 0;
            double totalSum = 0;
            double rateSum = 0;
            double toolSum = 0;
            double minTtft = Double.MAX_VALUE;
            double maxTtft = 0;
            double maxRate = 0;

            for (int i = 0; i < count; i++) {
                PerformanceSample s = snapshot.get(i);
                ttfts[i] = s.timeToFirstTokenMs;
                totals[i] = s.totalDurationMs;
                rates[i] = s.tokensPerSecond;
                tools[i] = s.toolCallDurationMs;

                ttftSum += s.timeToFirstTokenMs;
                totalSum += s.totalDurationMs;
                rateSum += s.tokensPerSecond;
                toolSum += s.toolCallDurationMs;

                if (s.timeToFirstTokenMs < minTtft) minTtft = s.timeToFirstTokenMs;
                if (s.timeToFirstTokenMs > maxTtft) maxTtft = s.timeToFirstTokenMs;
                if (s.tokensPerSecond > maxRate) maxRate = s.tokensPerSecond;
            }

            Arrays.sort(ttfts);
            Arrays.sort(totals);
            Arrays.sort(rates);

            double p50Ttft = getPercentile(ttfts, 0.50);
            double p90Ttft = getPercentile(ttfts, 0.90);
            double p50Total = getPercentile(totals, 0.50);
            double p90Total = getPercentile(totals, 0.90);

            double avgTtft = Math.round((ttftSum / count) * 10.0) / 10.0;
            double avgTotal = Math.round((totalSum / count) * 10.0) / 10.0;
            double avgRate = Math.round((rateSum / count) * 10.0) / 10.0;
            double avgTool = Math.round((toolSum / count) * 10.0) / 10.0;
            if (minTtft == Double.MAX_VALUE) minTtft = 0;

            ModelPerformanceSummaryDto summary = new ModelPerformanceSummaryDto(
                    providerId,
                    modelId,
                    totalCount.get(),
                    p50Ttft,
                    p90Ttft,
                    avgTtft,
                    minTtft,
                    maxTtft,
                    p50Total,
                    p90Total,
                    avgTotal,
                    avgRate,
                    maxRate,
                    avgTool,
                    count < LOW_SAMPLE_THRESHOLD);

            cachedSummary.set(summary);
            dirty = false;
            return summary;
        }

        private static double getPercentile(double[] sortedArray, double percentile) {
            if (sortedArray == null || sortedArray.length == 0) return 0;
            if (sortedArray.length == 1) return sortedArray[0];
            int index = (int) Math.round(percentile * (sortedArray.length - 1));
            index = Math.max(0, Math.min(index, sortedArray.length - 1));
            return sortedArray[index];
        }
    }
}
