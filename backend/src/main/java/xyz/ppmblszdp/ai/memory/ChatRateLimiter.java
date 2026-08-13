package xyz.ppmblszdp.ai.memory;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * 基于 Redis Lua 脚本滑动窗口（Sliding Window Log）的对话限流组件（保护上游 API 配额）。
 *
 * <p>基于 Redis ZSET + Lua 脚本实现精准滑动窗口计数：
 * <ul>
 *   <li>消除固定窗口在交界处的 2 倍突发问题（即 2 秒内发 2 倍配额）；</li>
 *   <li>单段 Lua 脚本原子化执行，从根源上杜绝非原子 {@code EXPIRE} 导致的永久锁死隐患；</li>
 *   <li>Redis 不可用或判定异常时降级为放行（不阻断业务）。</li>
 * </ul>
 * 仅在 {@code app.ai.memory.enabled=true} 且 {@code app.ai.memory.rate-limit.enabled=true} 时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.memory", name = "enabled", havingValue = "true")
public class ChatRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimiter.class);

    private static final String KEY_PREFIX = "ratelimit:chat:";

    @Bean
    @ConditionalOnProperty(prefix = "app.ai.memory.rate-limit", name = "enabled", havingValue = "true")
    public RateLimiter rateLimiter(ObjectProvider<StringRedisTemplate> redisTemplate, AiProviderProperties properties) {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        int capacity = properties.resolveMemory().resolveRateLimit().resolveCapacity();
        int refillSeconds = properties.resolveMemory().resolveRateLimit().resolveRefillSeconds();
        if (redis == null) {
            log.warn("限流启用但 Redis 不可用，降级为放行（不限制）");
            return (key) -> true;
        }
        log.info("对话滑动窗口限流装配完成：capacity={}, window={}s", capacity, refillSeconds);
        return new RedisRateLimiter(redis, capacity, Duration.ofSeconds(refillSeconds));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.ai.memory.rate-limit",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    public RateLimiter noopRateLimiter() {
        return (key) -> true;
    }

    /** 限流判定：返回 true 表示放行，false 表示被限流。 */
    public interface RateLimiter {
        boolean tryAcquire(String key);
    }

    static final class RedisRateLimiter implements RateLimiter {

        private static final RedisScript<Long> SLIDING_WINDOW_LUA_SCRIPT = new DefaultRedisScript<>(
                "local key = KEYS[1]\n" + "local now = tonumber(ARGV[1])\n"
                        + "local windowMs = tonumber(ARGV[2])\n"
                        + "local capacity = tonumber(ARGV[3])\n"
                        + "local memberId = ARGV[4]\n"
                        + "local clearBefore = now - windowMs\n"
                        + "redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)\n"
                        + "local currentCount = redis.call('ZCARD', key)\n"
                        + "if currentCount < capacity then\n"
                        + "    redis.call('ZADD', key, now, memberId)\n"
                        + "    local ttlSeconds = math.ceil(windowMs / 1000)\n"
                        + "    redis.call('EXPIRE', key, ttlSeconds)\n"
                        + "    return 1\n"
                        + "else\n"
                        + "    return 0\n"
                        + "end\n",
                Long.class);

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
            long now = System.currentTimeMillis();
            long windowMs = window.toMillis();
            String memberId = now + ":" + UUID.randomUUID().toString();
            try {
                Long result = redis.execute(
                        SLIDING_WINDOW_LUA_SCRIPT,
                        Collections.singletonList(redisKey),
                        String.valueOf(now),
                        String.valueOf(windowMs),
                        String.valueOf(capacity),
                        memberId);
                return result != null && result == 1L;
            } catch (RuntimeException ex) {
                log.warn("限流判定异常，降级放行：{}", ex.getMessage());
                return true;
            }
        }
    }
}
