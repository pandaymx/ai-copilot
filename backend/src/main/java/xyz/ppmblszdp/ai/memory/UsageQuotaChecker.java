package xyz.ppmblszdp.ai.memory;

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

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 基于 Redis 的用户级月度 Token 总量配额检查（保护上游月度成本）。
 *
 * <p>与 {@link ChatRateLimiter} 的短时窗口限流互补：窗口限流防上游突发，
 * 本组件按月累计用户已用 token 数并拦截超额请求。
 *
 * <h2>预扣 + 事后校准（关键设计）</h2>
 * 请求发起时模型尚未生成内容，无法预知真实 token 数。因此：
 * <ul>
 *   <li>{@link UsageQuota#tryReserve(String)} 仅做“当前已用量 + 预扣基础值 &gt; 上限”的拦截，
 *       不接收真实 token 数；</li>
 *   <li>响应结束取到真实 {@code totalTokens} 后，调用
 *       {@link UsageQuota#consumeActual(String, long)} 执行 {@code INCRBY(real - 预扣值)}
 *       净校准，使 Redis 中的累计值最终等于真实消耗。</li>
 * </ul>
 * 单次对话的落库与额度校准由上游 {@code AtomicBoolean} 保证仅执行一次。
 *
 * <p>Key 为 {@code usagequota:{userId}:{yyyy-MM}}，首次建 key 时通过 Lua 脚本原子地
 * {@code INCRBY + EXPIRE 35d}，跨月自动释放，避免历史用户 key 在 Redis 中永久堆积。
 *
 * <p>Redis 不可用或配额开关关闭时均降级放行，绝不阻断对话主链路（遵循 AGENTS.md）。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.memory", name = "enabled", havingValue = "true")
public class UsageQuotaChecker {

	private static final Logger log = LoggerFactory.getLogger(UsageQuotaChecker.class);

	private static final String KEY_PREFIX = "usagequota:";
	private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
	private static final Duration KEY_TTL = Duration.ofDays(35);

	@Bean
	@ConditionalOnProperty(prefix = "app.ai.memory.rate-limit", name = "enabled", havingValue = "true")
	public UsageQuota usageQuota(
			ObjectProvider<StringRedisTemplate> redisTemplate,
			AiProviderProperties properties) {
		StringRedisTemplate redis = redisTemplate.getIfAvailable();
		long monthlyQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
		long reserveTokens = properties.resolveMemory().resolveUsageQuota().resolveReserveTokens();
		if (redis == null) {
			log.warn("月度配额启用但 Redis 不可用，降级为放行（不限制）");
			return new NoopUsageQuota();
		}
		log.info("月度 Token 配额装配完成：monthlyQuota={}, reserveTokens={}", monthlyQuota, reserveTokens);
		return new RedisUsageQuota(redis, monthlyQuota, reserveTokens);
	}

	@Bean
	@ConditionalOnProperty(prefix = "app.ai.memory.rate-limit", name = "enabled", havingValue = "false", matchIfMissing = true)
	public UsageQuota noopUsageQuota() {
		return new NoopUsageQuota();
	}

	/** 当月月份 key（yyyy-MM），由服务端按当前日期计算。 */
	public static String currentMonthKey() {
		return LocalDate.now().format(MONTH_FMT);
	}

	/**
	 * 月度用量配额接口。
	 */
	public interface UsageQuota {
		/**
		 * 预扣拦截：检查“当前已用量 + 预扣基础值”是否超过上限。
		 *
		 * @param userId 受信任用户身份
		 * @return true 放行，false 超额拒绝；Redis 异常时降级放行
		 */
		boolean tryReserve(String userId);

		/**
		 * 事后校准：按真实 token 数与预扣值的净增量写入 Redis 累计值。
		 * 无论对话是正常完成、被用户取消还是异常结束，最多调用一次。
		 *
		 * @param userId     受信任用户身份
		 * @param realTokens 本次对话真实产生的 token 数（≥0）
		 */
		void consumeActual(String userId, long realTokens);
	}

	static final class NoopUsageQuota implements UsageQuota {
		@Override
		public boolean tryReserve(String userId) {
			return true;
		}

		@Override
		public void consumeActual(String userId, long realTokens) {
			// 未启用配额：不计入 Redis
		}
	}

	static final class RedisUsageQuota implements UsageQuota {

		/**
		 * 原子脚本：INCRBY 累计 delta，并在 key 为新创建（TTL 缺失）时设置 35d 过期。
		 * KEYS[1]=quotaKey, ARGV[1]=delta, ARGV[2]=ttlSeconds
		 * 返回当前累计值（仅用于 tryReserve 的超额判定，单位 token）。
		 */
		private static final RedisScript<Long> INCRBY_WITH_TTL_LUA = new DefaultRedisScript<>(
				"local key = KEYS[1]\n" +
				"local delta = tonumber(ARGV[1])\n" +
				"local ttl = tonumber(ARGV[2])\n" +
				"local newVal = redis.call('INCRBY', key, delta)\n" +
				"if redis.call('TTL', key) < 0 then\n" +
				"    redis.call('EXPIRE', key, ttl)\n" +
				"end\n" +
				"return newVal\n",
				Long.class
		);

		private final StringRedisTemplate redis;
		private final long monthlyQuota;
		private final long reserveTokens;

		RedisUsageQuota(StringRedisTemplate redis, long monthlyQuota, long reserveTokens) {
			this.redis = redis;
			this.monthlyQuota = monthlyQuota;
			this.reserveTokens = reserveTokens;
		}

		@Override
		public boolean tryReserve(String userId) {
			if (userId == null || userId.isBlank()) {
				return true; // 匿名用户不纳入配额
			}
			if (monthlyQuota <= 0) {
				return true; // 0 表示无上限
			}
			String redisKey = KEY_PREFIX + userId + ":" + currentMonthKey();
			try {
				Long current = redis.opsForValue().increment(redisKey, reserveTokens);
				if (current == null) {
					return true;
				}
				// 新 key 立即补 TTL，避免堆积
				if (Boolean.FALSE.equals(redis.hasKey(redisKey)) || redis.getExpire(redisKey) < 0) {
					redis.expire(redisKey, KEY_TTL);
				}
				if (current > monthlyQuota) {
					log.info("用户月度配额不足 [user={}, used={}, quota={}]", userId, current, monthlyQuota);
					return false;
				}
				return true;
			} catch (RuntimeException ex) {
				log.warn("月度配额预扣判定异常，降级放行：{}", ex.getMessage());
				return true;
			}
		}

		@Override
		public void consumeActual(String userId, long realTokens) {
			if (userId == null || userId.isBlank() || monthlyQuota <= 0) {
				return;
			}
			// 净增量 = 真实消耗 - 预扣基础值（可能为负，表示实际少于预扣）
			long delta = realTokens - reserveTokens;
			String redisKey = KEY_PREFIX + userId + ":" + currentMonthKey();
			try {
				redis.execute(
						INCRBY_WITH_TTL_LUA,
						Collections.singletonList(redisKey),
						String.valueOf(delta),
						String.valueOf(KEY_TTL.getSeconds())
				);
			} catch (RuntimeException ex) {
				log.warn("月度配额校准异常，跳过（不影响对话）：{}", ex.getMessage());
			}
		}
	}
}
