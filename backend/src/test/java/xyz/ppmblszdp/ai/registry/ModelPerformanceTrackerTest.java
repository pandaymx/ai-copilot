package xyz.ppmblszdp.ai.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModelPerformanceTracker 性能指标与 P50/P90 延迟统计单元测试")
class ModelPerformanceTrackerTest {

    private ModelPerformanceTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ModelPerformanceTracker();
    }

    @Test
    @DisplayName("纯 Token 速率计算应正确扣除 TTFT 与工具调用耗时")
    void testCalculateTokensPerSecond() {
        // total: 2000ms, ttft: 200ms, tool: 800ms -> pure gen: 1000ms. 40 tokens / 1.0s = 40.0 t/s
        double rate = ModelPerformanceTracker.calculateTokensPerSecond(40, 2000, 200, 800);
        assertEquals(40.0, rate, 0.1);

        // 0 tokens
        assertEquals(0.0, ModelPerformanceTracker.calculateTokensPerSecond(0, 2000, 200, 800), 0.01);

        // 极短时间保底
        double fastRate = ModelPerformanceTracker.calculateTokensPerSecond(5, 5, 2, 0);
        assertTrue(fastRate > 0);
    }

    @Test
    @DisplayName("冷启动时样本数 < 5 应标记 lowSampleWarning=true")
    void testLowSampleWarning() {
        tracker.record("deepseek", "deepseek-chat", 250, 45.0, 1500, 0, 50, false);
        tracker.record("deepseek", "deepseek-chat", 300, 42.0, 1600, 0, 55, false);

        var summary = tracker.getSummary("deepseek", "deepseek-chat");
        assertEquals(2, summary.sampleCount());
        assertTrue(summary.lowSampleWarning());
    }

    @Test
    @DisplayName("样本充足时应正确计算 P50, P90, Avg, Min, Max 指标")
    void testPercentilesAndAverages() {
        // 记录 10 次样本，TTFT 从 100 到 1000
        for (int i = 1; i <= 10; i++) {
            long ttft = i * 100L; // 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000
            long total = i * 200L;
            double rate = 30.0 + i;
            tracker.record("openai", "gpt-4o", ttft, rate, total, 0, 30, false);
        }

        var summary = tracker.getSummary("openai", "gpt-4o");
        assertEquals(10, summary.sampleCount());
        assertFalse(summary.lowSampleWarning());

        // P50 TTFT (index = round(0.50 * 9) = 5 -> sortedArray[5] = 600)
        assertEquals(600.0, summary.p50TtftMs(), 1.0);
        // P90 TTFT (index = round(0.90 * 9) = 8 -> sortedArray[8] = 900)
        assertEquals(900.0, summary.p90TtftMs(), 1.0);

        assertEquals(100.0, summary.minTtftMs(), 0.1);
        assertEquals(1000.0, summary.maxTtftMs(), 0.1);
        assertEquals(550.0, summary.avgTtftMs(), 1.0);

        List<ModelPerformanceTracker.ModelPerformanceSummaryDto> all = tracker.getAllSummaries();
        assertEquals(1, all.size());
        assertEquals("gpt-4o", all.get(0).modelId());
    }

    @Test
    @DisplayName("多线程高并发记录无死锁且统计正确")
    void testConcurrentRecording() throws InterruptedException {
        int threads = 10;
        int recordsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < recordsPerThread; i++) {
                        tracker.record("anthropic", "claude-3-5-sonnet", 200, 50.0, 1000, 100, 40, false);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        var summary = tracker.getSummary("anthropic", "claude-3-5-sonnet");
        assertNotNull(summary);
        assertEquals(500, summary.sampleCount());
        assertFalse(summary.lowSampleWarning());
        assertEquals(200.0, summary.p50TtftMs(), 0.1);
    }
}
