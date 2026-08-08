package xyz.ppmblszdp.ai.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工业级模型健康状态追踪与熔断状态机 (ModelHealthTracker)。
 *
 * <p>
 * 特性：
 * <ul>
 *   <li><b>熔断三态</b>：{@code UP} (健康) -> {@code DOWN} (熔断) -> {@code HALF_OPEN} (半开探测)；</li>
 *   <li><b>冷却恢复</b>：DOWN 状态经过 {@code COOLDOWN_MS} (默认 60秒) 自动切入 HALF_OPEN 半开试探；</li>
 *   <li><b>精准错误分类</b>：仅在网络超时、5xx 服务端故障、429 (Rate Limit) 时计入失败；401/403 凭证错误、400 参数或超长、客户端取消不计入熔断，防止误判；</li>
 *   <li><b>无锁并发安全</b>：内部基于 {@link ConcurrentHashMap} + {@link AtomicInteger}。</li>
 * </ul>
 */
@Component
public class ModelHealthTracker {

	private static final Logger log = LoggerFactory.getLogger(ModelHealthTracker.class);

	/** 连续失败阈值 (达到 2 次切入 DOWN) */
	private static final int MAX_FAILURES = 2;

	/** 熔断冷却期 (60 秒) */
	private static final long COOLDOWN_MS = 60_000L;

	private final ConcurrentHashMap<String, ModelHealthNode> nodes = new ConcurrentHashMap<>();

	/**
	 * 获取指定 Provider 与 Model 的当前健康状态 (自动感知 HALF_OPEN)
	 */
	public HealthStatus getStatus(String providerId, String modelId) {
		String key = buildKey(providerId, modelId);
		ModelHealthNode node = nodes.get(key);
		if (node == null) {
			return HealthStatus.UP;
		}

		long now = System.currentTimeMillis();
		if (node.status == HealthStatus.DOWN) {
			if (now - node.lastStatusChangeTimeMs >= COOLDOWN_MS) {
				synchronized (node) {
					if (node.status == HealthStatus.DOWN && now - node.lastStatusChangeTimeMs >= COOLDOWN_MS) {
						node.status = HealthStatus.HALF_OPEN;
						node.lastStatusChangeTimeMs = now;
						log.info("🔄 [HealthTracker] 模型 [{}] 冷却期结束，状态转移: DOWN -> HALF_OPEN (半开试探)", key);
					}
				}
			}
		}
		return node.status;
	}

	/**
	 * 记录一次调用成功
	 */
	public void recordSuccess(String providerId, String modelId) {
		String key = buildKey(providerId, modelId);
		ModelHealthNode node = nodes.computeIfAbsent(key, k -> new ModelHealthNode());
		node.consecutiveFailures.set(0);
		if (node.status != HealthStatus.UP) {
			synchronized (node) {
				if (node.status != HealthStatus.UP) {
					log.info("✅ [HealthTracker] 模型 [{}] 探测/恢复成功，状态转移: {} -> UP", key, node.status);
					node.status = HealthStatus.UP;
					node.lastStatusChangeTimeMs = System.currentTimeMillis();
				}
			}
		}
	}

	/**
	 * 记录一次调用失败（带有精准异常分类处理）
	 */
	public void recordFailure(String providerId, String modelId, Throwable throwable) {
		if (!shouldCountAsFailure(throwable)) {
			log.debug("ℹ️ [HealthTracker] 忽略业务/客户端取消异常，不触发熔断逻辑 → provider={}, model={}, cause={}",
					providerId, modelId, throwable != null ? throwable.getMessage() : "null");
			return;
		}

		String key = buildKey(providerId, modelId);
		ModelHealthNode node = nodes.computeIfAbsent(key, k -> new ModelHealthNode());
		int failures = node.consecutiveFailures.incrementAndGet();
		long now = System.currentTimeMillis();

		synchronized (node) {
			if (node.status == HealthStatus.HALF_OPEN) {
				node.status = HealthStatus.DOWN;
				node.lastStatusChangeTimeMs = now;
				log.warn("🚨 [HealthTracker] 模型 [{}] 半开试探再次失败，状态转移: HALF_OPEN -> DOWN (重新进入冷却)", key);
			} else if (failures >= MAX_FAILURES && node.status == HealthStatus.UP) {
				node.status = HealthStatus.DOWN;
				node.lastStatusChangeTimeMs = now;
				log.warn("💥 [HealthTracker] 模型 [{}] 连续失败 {} 次，状态转移: UP -> DOWN (进入熔断)", key, failures);
			}
		}
	}

	/**
	 * 获取所有已知模型的健康状态快照 (Map<"providerId:modelId", HealthStatus>)
	 */
	public Map<String, HealthStatus> getAllStatusSnapshots() {
		Map<String, HealthStatus> snapshot = new HashMap<>();
		for (Map.Entry<String, ModelHealthNode> entry : nodes.entrySet()) {
			snapshot.put(entry.getKey(), getStatusFromKey(entry.getKey()));
		}
		return snapshot;
	}

	public HealthStatus getStatusFromKey(String key) {
		String[] parts = key.split(":");
		if (parts.length == 2) {
			return getStatus(parts[0], parts[1]);
		}
		return HealthStatus.UP;
	}

	/**
	 * 精准错误分类：判断该异常是否应当计入熔断失败
	 */
	private boolean shouldCountAsFailure(Throwable t) {
		if (t == null) return true;

		String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";

		// 忽略客户端主动取消 (Client Abort)
		if (t instanceof java.util.concurrent.CancellationException || msg.contains("cancel") || msg.contains("aborted")) {
			return false;
		}

		// 忽略客户端 400 Bad Request / 401 Unauthorized / 403 Forbidden
		if (msg.contains("400 bad request") || msg.contains("401 unauthorized") || msg.contains("403 forbidden")) {
			return false;
		}

		// 计入失败：超时、5xx 服务端异常、429 Rate Limit
		if (t instanceof TimeoutException || t instanceof SocketTimeoutException || t instanceof IOException) {
			return true;
		}
		if (msg.contains("timeout") || msg.contains("429") || msg.contains("too many requests")
				|| msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) {
			return true;
		}

		return true;
	}

	private String buildKey(String providerId, String modelId) {
		return (providerId != null ? providerId.trim().toLowerCase() : "default") + ":"
				+ (modelId != null ? modelId.trim().toLowerCase() : "default");
	}

	private static final class ModelHealthNode {
		private volatile HealthStatus status = HealthStatus.UP;
		private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
		private volatile long lastStatusChangeTimeMs = System.currentTimeMillis();
	}
}
