package xyz.ppmblszdp.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 会话记忆配置：以 Spring AI 自动配置的 {@code ChatMemory}（底层 JDBC 存储库 = Ground Truth）为基础，
 * 用 {@link RedisCachingChatMemory} 装饰器缓存最近 N 条热消息加速高频会话读取。
 *
 * <p>
 * Spring AI 在引入 {@code spring-ai-starter-model-chat-memory-repository-jdbc}
 * 后自动配置名为
 * {@code chatMemory} 的 {@link ChatMemory} bean（PostgreSQL 持久化）。本配置在其上叠加 Redis
 * 热缓存，
 * 并把装饰器标记为 {@code @Primary}，使业务层注入的是带缓存的版本。
 *
 * <p>
 * 仅在 {@code app.ai.memory.enabled=true} 时装配。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.memory", name = "enabled", havingValue = "true")
public class ChatMemoryConfig {

	private static final Logger log = LoggerFactory.getLogger(ChatMemoryConfig.class);

	private static final String HOT_KEY_PREFIX = "memory:hot:";

	@Bean
	@Primary
	public ChatMemory sessionChatMemory(
			@Qualifier("chatMemory") ChatMemory jdbcDelegate,
			ObjectProvider<StringRedisTemplate> redisTemplate,
			AiProviderProperties properties) {
		int hotCacheSize = properties.resolveMemory().resolveHotCacheSize();
		int ttlDays = properties.resolveMemory().resolveConversationTtlDays();
		StringRedisTemplate redis = redisTemplate.getIfAvailable();
		if (redis == null) {
			log.warn("未检测到 Redis（StringRedisTemplate 不可用），会话记忆回退为纯 JDBC 模式（无热缓存）");
			return jdbcDelegate;
		}
		log.info("会话记忆装配完成：JDBC 持久化 + Redis 热缓存(hotCacheSize={}, ttlDays={})", hotCacheSize, ttlDays);
		return new RedisCachingChatMemory(jdbcDelegate, redis, hotCacheSize, Duration.ofDays(ttlDays));
	}

	/**
	 * 装饰器：在官方 {@link ChatMemory}（JDBC Ground Truth）之上叠加 Redis 热缓存。
	 *
	 * <p>
	 * 读路径：先查 Redis List，命中且条数满足 hotCacheSize 直接返回；否则回源 JDBC，
	 * 取最近 N 条反向填入 Redis 并设置 TTL，防止冷数据常驻。
	 * 写路径：{@code add} 先写 JDBC，再写 Redis 并更新 TTL（Spring AI 在流式结束后自动回调 add）。
	 */
	static final class RedisCachingChatMemory implements ChatMemory {

		private final ChatMemory delegate;
		private final StringRedisTemplate redis;
		private final int hotCacheSize;
		private final Duration ttl;

		RedisCachingChatMemory(ChatMemory delegate, StringRedisTemplate redis, int hotCacheSize, Duration ttl) {
			this.delegate = delegate;
			this.redis = redis;
			this.hotCacheSize = hotCacheSize;
			this.ttl = ttl;
		}

		@Override
		public List<Message> get(String conversationId) {
			try {
				String key = HOT_KEY_PREFIX + conversationId;
				List<String> cached = redis.opsForList().range(key, 0, -1);
				if (cached != null && cached.size() >= hotCacheSize) {
					List<Message> messages = deserialize(cached);
					if (messages.size() >= hotCacheSize) {
						log.debug("会话 '{}' 命中 Redis 热缓存，条数={}", conversationId, messages.size());
						return messages;
					}
				}
			} catch (RuntimeException ex) {
				log.warn("会话 '{}' 读 Redis 热缓存失败，回源 JDBC：{}", conversationId, ex.getMessage());
			}
			List<Message> fromJdbc = safeGetJdbc(conversationId);
			try {
				String key = HOT_KEY_PREFIX + conversationId;
				redis.delete(key);
				List<String> tail = serializeTail(fromJdbc, hotCacheSize);
				if (!tail.isEmpty()) {
					redis.opsForList().rightPushAll(key, tail);
					redis.expire(key, ttl);
				}
			} catch (RuntimeException ex) {
				log.warn("会话 '{}' 回填 Redis 热缓存失败（不影响持久化）：{}", conversationId, ex.getMessage());
			}
			return fromJdbc;
		}

		@Override
		public void add(String conversationId, List<Message> messages) {
			delegate.add(conversationId, messages);
			try {
				String key = HOT_KEY_PREFIX + conversationId;
				redis.delete(key);
				List<String> tail = serializeTail(messages, hotCacheSize);
				if (!tail.isEmpty()) {
					redis.opsForList().rightPushAll(key, tail);
					redis.expire(key, ttl);
				}
			} catch (RuntimeException ex) {
				log.warn("会话 '{}' 写 Redis 热缓存失败（不影响持久化）：{}", conversationId, ex.getMessage());
			}
		}

		@Override
		public void clear(String conversationId) {
			delegate.clear(conversationId);
			try {
				redis.delete(HOT_KEY_PREFIX + conversationId);
			} catch (RuntimeException ex) {
				log.warn("会话 '{}' 清除 Redis 热缓存失败：{}", conversationId, ex.getMessage());
			}
		}

		private List<Message> safeGetJdbc(String conversationId) {
			try {
				return delegate.get(conversationId);
			} catch (RuntimeException ex) {
				log.warn("会话 '{}' 读 JDBC 记忆失败，降级为空上下文：{}", conversationId, ex.getMessage());
				return new ArrayList<>();
			}
		}

		private List<String> serializeTail(List<Message> messages, int n) {
			if (messages == null || messages.isEmpty()) {
				return List.of();
			}
			int from = Math.max(0, messages.size() - n);
			List<String> out = new ArrayList<>(messages.size() - from);
			for (Message m : messages.subList(from, messages.size())) {
				out.add(serialize(m));
			}
			return out;
		}

		private String serialize(Message m) {
			return m.getMessageType().name() + "\u0001" + m.getText();
		}

		private List<Message> deserialize(List<String> raw) {
			List<Message> out = new LinkedList<>();
			for (String s : raw) {
				int idx = s.indexOf('\u0001');
				if (idx < 0) {
					continue;
				}
				String type = s.substring(0, idx);
				String text = s.substring(idx + 1);
				out.add(switch (type) {
					case "USER" -> new UserMessage(text);
					case "ASSISTANT" -> new AssistantMessage(text);
					case "SYSTEM" -> new SystemMessage(text);
					default -> new UserMessage(text);
				});
			}
			return out;
		}
	}
}
