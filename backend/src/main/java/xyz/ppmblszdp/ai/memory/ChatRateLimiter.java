package xyz.ppmblszdp.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

import java.time.Duration;

/**
 * 基于 Redis 的对话限流组件（保护上游 API 配额）。
 *
 * <p>采用固定窗口计数：{@code INCR + EXPIRE}。Redis 不可用时降级为放行（不阻断业务）。
 * 仅在 {@code app.ai.memory.enabled=true} 且 {@code app.ai.memory.rate-limit.enabled=true} 时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.memory", name = "enabled", havingValue = "true")
public class ChatRateLimiter {

	private static final Logger log = LoggerFactory.getLogger(ChatRateLimiter.class);

	private static final String KEY_PREFIX = "ratelimit:chat:";

	@Bean
	@ConditionalOnProperty(prefix = "app.ai.memory.rate-limit", name = "enabled", havingValue = "true")
	public RateLimiter rateLimiter(
			ObjectProvider<StringRedisTemplate> redisTemplate,
			AiProviderProperties properties) {
		StringRedisTemplate redis = redisTemplate.getIfAvailable();
		int capacity = properties.resolveMemory().resolveRateLimit().resolveCapacity();
		int refillSeconds = properties.resolveMemory().resolveRateLimit().resolveRefillSeconds();
		if (redis == null) {
			log.warn("限流启用但 Redis 不可用，降级为放行（不限制）");
			return (key) -> true;
		}
		log.info("对话限流装配完成：capacity={}, window={}s", capacity, refillSeconds);
		return new RedisRateLimiter(redis, capacity, Duration.ofSeconds(refillSeconds));
	}

	@Bean
	@ConditionalOnProperty(prefix = "app.ai.memory.rate-limit", name = "enabled", havingValue = "false", matchIfMissing = true)
	public RateLimiter noopRateLimiter() {
		return (key) -> true;
	}

	/** 限流判定：返回 true 表示放行，false 表示被限流。 */
	public interface RateLimiter {
		boolean tryAcquire(String key);
	}

	static final class RedisRateLimiter implements RateLimiter {

		private final StringRedisTemplate redis;
		private final int capacity;
		private final Duration window;

		RedisRateLimiter(StringRedisTemplate redis, int capacity, Duration window) {
			this.redis = redis;
			this.capacity = capacity;
			this.window = window;
		}

		@Override
		public boolean tryAcquire(String key) {
			String redisKey = KEY_PREFIX + key;
			try {
				Long count = redis.opsForValue().increment(redisKey);
				if (count != null && count == 1) {
					redis.expire(redisKey, window);
				}
				return count != null && count <= capacity;
			} catch (RuntimeException ex) {
				log.warn("限流判定异常，降级放行：{}", ex.getMessage());
				return true;
			}
		}
	}
}
