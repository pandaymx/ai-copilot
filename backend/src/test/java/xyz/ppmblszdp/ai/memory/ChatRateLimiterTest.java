package xyz.ppmblszdp.ai.memory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRateLimiterTest {

	@Test
	void testNoopRateLimiterAlwaysAcquires() {
		ChatRateLimiter config = new ChatRateLimiter();
		ChatRateLimiter.RateLimiter limiter = config.noopRateLimiter();

		assertTrue(limiter.tryAcquire("user1"));
		assertTrue(limiter.tryAcquire("user1"));
	}

	@Test
	void testRedisRateLimiterAcquireAndLimit() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		// 模拟第一次请求放行 (返回 1L)，第二次请求被限制 (返回 0L)
		when(redis.execute(
				ArgumentMatchers.<RedisScript<Long>>any(),
				ArgumentMatchers.<List<String>>any(),
				any(), any(), any(), any())).thenReturn(1L).thenReturn(0L);

		ChatRateLimiter.RedisRateLimiter limiter = new ChatRateLimiter.RedisRateLimiter(redis, 1,
				Duration.ofSeconds(60));

		assertTrue(limiter.tryAcquire("user1"), "First request within capacity should be allowed");
		assertFalse(limiter.tryAcquire("user1"), "Second request exceeding capacity should be blocked");
	}

	@Test
	void testRedisRateLimiterFallbackOnException() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.execute(
				ArgumentMatchers.<RedisScript<Long>>any(),
				ArgumentMatchers.<List<String>>any(),
				any(), any(), any(), any())).thenThrow(new RuntimeException("Redis connection error"));

		ChatRateLimiter.RedisRateLimiter limiter = new ChatRateLimiter.RedisRateLimiter(redis, 1,
				Duration.ofSeconds(60));

		assertTrue(limiter.tryAcquire("user1"), "On Redis exception, it should fallback to true (allow)");
	}
}
