package xyz.ppmblszdp.ai.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModelHealthTracker 熔断三态与精准错误分类单元测试")
class ModelHealthTrackerTest {

    private ModelHealthTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ModelHealthTracker();
    }

    @Test
    @DisplayName("1. 初始状态为 UP，成功调用维持 UP")
    void testInitialStateUp() {
        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.UP);
        tracker.recordSuccess("openai", "gpt-4o");
        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.UP);
    }

    @Test
    @DisplayName("2. 精准错误分类：客户端 400/401/403 及取消不触发熔断")
    void testIgnoredExceptions() {
        tracker.recordFailure("openai", "gpt-4o", new RuntimeException("401 Unauthorized"));
        tracker.recordFailure("openai", "gpt-4o", new RuntimeException("400 Bad Request"));
        tracker.recordFailure("openai", "gpt-4o", new java.util.concurrent.CancellationException("Client Abort"));

        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.UP);
    }

    @Test
    @DisplayName("3. 连续 2 次超时/5xx 错误触发 UP -> DOWN 熔断")
    void testCircuitBreakerTriggerDown() {
        tracker.recordFailure("openai", "gpt-4o", new SocketTimeoutException("Read timed out"));
        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.UP);

        tracker.recordFailure("openai", "gpt-4o", new RuntimeException("503 Service Unavailable"));
        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    @DisplayName("4. 探测成功：HALF_OPEN/DOWN 恢复为 UP 并重置失败计数")
    void testRecoveryToUp() {
        // 触发出 DOWN
        tracker.recordFailure("openai", "gpt-4o", new SocketTimeoutException("Timeout 1"));
        tracker.recordFailure("openai", "gpt-4o", new SocketTimeoutException("Timeout 2"));
        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.DOWN);

        // 记录成功回复，立马切回 UP
        tracker.recordSuccess("openai", "gpt-4o");
        assertThat(tracker.getStatus("openai", "gpt-4o")).isEqualTo(HealthStatus.UP);
    }
}
