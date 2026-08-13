package xyz.ppmblszdp.ai.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class UsageQuotaCheckerTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private UsageQuotaChecker.UsageQuota redisQuota;

    private static final long QUOTA = 1000L;
    private static final long RESERVE = 200L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        // 避免 hasKey/getExpire 默认 null 触发 NPE 分支
        when(redis.hasKey(any())).thenReturn(true);
        when(redis.getExpire(any())).thenReturn(100L);
        redisQuota = new UsageQuotaChecker.RedisUsageQuota(redis, QUOTA, RESERVE);
    }

    @Test
    void tryReserveAllowsWhenWithinQuota() {
        // 预扣后累计 200 < 1000 上限
        when(valueOps.increment(any(), eq(RESERVE))).thenReturn(RESERVE);
        assertTrue(redisQuota.tryReserve("user-1"));
    }

    @Test
    void tryReserveRejectsWhenExceedsQuota() {
        // 预扣后累计 1200 > 1000 上限（模拟已有用量 + 预扣）
        when(valueOps.increment(any(), eq(RESERVE))).thenReturn(1200L);
        assertFalse(redisQuota.tryReserve("user-1"), "已超月度配额应被拒绝");
    }

    @Test
    void tryReserveFallsBackToAllowOnRedisException() {
        when(valueOps.increment(any(), anyLong())).thenThrow(new RuntimeException("Redis down"));
        assertTrue(redisQuota.tryReserve("user-1"), "Redis 异常应降级放行");
    }

    @Test
    void consumeActualCalibratesByNetDelta() {
        // 真实消耗 500，预扣 200 → 净增量 INCRBY +300
        redisQuota.consumeActual("user-1", 500L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> d1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> d2 = ArgumentCaptor.forClass(Object.class);
        verify(redis).execute(any(), keysCaptor.capture(), d1.capture(), d2.capture());

        assertEquals(String.valueOf(500L - RESERVE), String.valueOf(d1.getValue()));
        // 第二个参数为 TTL 秒，应为正整数
        assertTrue(Long.parseLong(String.valueOf(d2.getValue())) > 0);
    }

    @Test
    void consumeActualCalledOnlyOncePerRequest() {
        // 模拟上游 AtomicBoolean 防重：单次请求仅调用一次
        redisQuota.consumeActual("user-1", 800L);
        verify(redis, times(1)).execute(any(), anyList(), any(), any());
    }

    @Test
    void tryReserveWithZeroQuotaMeansUnlimited() {
        UsageQuotaChecker.UsageQuota unlimited = new UsageQuotaChecker.RedisUsageQuota(redis, 0L, RESERVE);
        when(valueOps.increment(any(), anyLong())).thenReturn(Long.MAX_VALUE);
        assertTrue(unlimited.tryReserve("user-1"), "quota<=0 应视为无上限");
    }

    @Test
    void noopQuotaAlwaysAllowsAndNoopsConsume() {
        UsageQuotaChecker.UsageQuota noop = new UsageQuotaChecker.NoopUsageQuota();
        assertTrue(noop.tryReserve("user-1"));
        noop.consumeActual("user-1", 9999L);
        // noop 不应触碰 redis
        verify(redis, times(0)).execute(any(), anyList(), any(), any());
    }
}
