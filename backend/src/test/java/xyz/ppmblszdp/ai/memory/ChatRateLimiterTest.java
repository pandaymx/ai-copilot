package xyz.ppmblszdp.ai.memory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ChatRateLimiterTest {

    @Test
    void testNoopRateLimiterAlwaysAcquiresAndReturnsFullQuota() {
        ChatRateLimiter config = new ChatRateLimiter();
        ChatRateLimiter.RateLimiter limiter = config.noopRateLimiter();

        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));

        ChatRateLimiter.WindowQuotaDto status = limiter.getQuotaStatus("user1");
        assertNotNull(status);
        assertEquals(20, status.capacity());
        assertEquals(20, status.remaining());
        assertEquals(60, status.windowSeconds());
        assertEquals(0, status.resetAfterSeconds());
    }

    @Test
    void testRedisRateLimiterAcquireAndLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        // 模拟第一次请求放行 (返回 1L)，第二次请求被限制 (返回 0L)
        when(redis.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(),
                        ArgumentMatchers.<List<String>>any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(1L)
                .thenReturn(0L);

        ChatRateLimiter.RedisRateLimiter limiter =
                new ChatRateLimiter.RedisRateLimiter(redis, 1, Duration.ofSeconds(60));

        assertTrue(limiter.tryAcquire("user1"), "First request within capacity should be allowed");
        assertFalse(limiter.tryAcquire("user1"), "Second request exceeding capacity should be blocked");
    }

    @Test
    void testRedisRateLimiterGetQuotaStatus() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        long now = System.currentTimeMillis();
        // 模拟已用 2 次，最旧时间戳为 10 秒前
        when(redis.execute(
                        ArgumentMatchers.<RedisScript<List<Object>>>any(),
                        ArgumentMatchers.<List<String>>any(),
                        any(),
                        any()))
                .thenReturn(List.of(2L, now - 10000L));

        ChatRateLimiter.RedisRateLimiter limiter =
                new ChatRateLimiter.RedisRateLimiter(redis, 5, Duration.ofSeconds(60));

        ChatRateLimiter.WindowQuotaDto status = limiter.getQuotaStatus("user1");
        assertNotNull(status);
        assertEquals(5, status.capacity());
        assertEquals(3, status.remaining());
        assertEquals(60, status.windowSeconds());
        assertTrue(status.resetAfterSeconds() > 0 && status.resetAfterSeconds() <= 51);
    }

    @Test
    void testRedisRateLimiterFallbackOnException() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(),
                        ArgumentMatchers.<List<String>>any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenThrow(new RuntimeException("Redis connection error"));

        ChatRateLimiter.RedisRateLimiter limiter =
                new ChatRateLimiter.RedisRateLimiter(redis, 1, Duration.ofSeconds(60));

        assertTrue(limiter.tryAcquire("user1"), "On Redis exception, it should fallback to true (allow)");
    }
}
