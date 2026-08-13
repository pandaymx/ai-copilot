package xyz.ppmblszdp.ai.memory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * 会话记忆配置：以底层 {@link ChatMemoryRepository}（JDBC 存储库 = Ground Truth）为基础，
 * 用 {@link RedisCachingChatMemory} 装饰器缓存最近 N 条热消息加速高频会话读取。
 *
 * <p>
 * 当引入 {@code spring-ai-starter-model-chat-memory-repository-jdbc} 时，Spring AI
 * 自动配置 {@code JdbcChatMemoryRepository}。本配置通过构造 {@link MessageWindowChatMemory} 并在其上叠加 Redis
 * 热缓存，并将装饰器标记为 {@code @Primary}，使业务层注入带缓存的版本。
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
            ObjectProvider<ChatMemoryRepository> chatMemoryRepositoryProvider,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            AiProviderProperties properties) {
        ChatMemoryRepository repository =
                chatMemoryRepositoryProvider.getIfAvailable(InMemoryChatMemoryRepository::new);
        // 取消硬编码的 20 条盲截断（设为 1000 软上限），将完整会话持久化在 JDBC/Redis 中，
        // 具体的 Token 预算滑动窗口统一由 ContextAssembler 按模型 maxContextTokens 动态裁切。
        ChatMemory delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(1000)
                .build();

        int hotCacheSize = properties.resolveMemory().resolveHotCacheSize();
        int ttlDays = properties.resolveMemory().resolveConversationTtlDays();
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            log.warn("未检测到 Redis（StringRedisTemplate 不可用），会话记忆回退为纯持久化模式（无热缓存）");
            return delegate;
        }
        log.info(
                "会话记忆装配完成：持久化(Repository={}) + Redis 热缓存(hotCacheSize={}, ttlDays={})",
                repository.getClass().getSimpleName(),
                hotCacheSize,
                ttlDays);
        return new RedisCachingChatMemory(delegate, redis, hotCacheSize, Duration.ofDays(ttlDays));
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
                out.add(
                        switch (type) {
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
