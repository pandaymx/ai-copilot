package xyz.ppmblszdp.ai.collab;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.concurrent.Queues;

/**
 * 会话级协作广播总线（进程内内存实现，单实例协作）。
 *
 * <p>设计要点（修正点 3/4）：
 * <ul>
 *   <li>瞬态事件（cursor/typing）与持久事件共用同一总线，但瞬态帧采用
 *       {@code onBackpressureBuffer(SMALL_BUFFER_SIZE, false)} 丢弃策略：
 *       慢消费者（如弱网协作者）缓冲溢出时丢弃<b>旧</b>帧而非报错关闭；</li>
 *   <li>瞬态事件不持久化，presence/message 帧也仅向<b>当前在线</b>连接投递；</li>
 *   <li>总线本身不依赖 Redis，遵循优雅降级原则（Redis 不可用也不影响协作）。</li>
 * </ul>
 *
 * <p>扩展横向多实例时，可在本类外层包一层 Redis pub/sub 转发即可，对外接口不变。
 */
public class CollaborationBus {

    private static final Logger log = LoggerFactory.getLogger(CollaborationBus.class);

    /** 每个会话一个多播 Sink。 */
    private final Map<String, Many<CollabEvent>> sessionSinks = new ConcurrentHashMap<>();

    /** 每个会话的在线成员（userId -> 角色）。 */
    private final Map<String, Map<String, String>> sessionMembers = new ConcurrentHashMap<>();

    /** 会话级生成锁（修正点 2）：sessionId -> 触发者 userId。 */
    private final Map<String, String> generatingSessions = new ConcurrentHashMap<>();

    private Many<CollabEvent> sinkFor(String sessionId) {
        return sessionSinks.computeIfAbsent(
                sessionId, id -> Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false));
    }

    /** 成员加入（幂等），返回当前在线成员快照。 */
    public synchronized java.util.List<String> join(String sessionId, String userId, String role) {
        sessionMembers
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(userId, role);
        return onlineOf(sessionId);
    }

    /** 成员离开。 */
    public synchronized void leave(String sessionId, String userId) {
        Map<String, String> members = sessionMembers.get(sessionId);
        if (members != null) {
            members.remove(userId);
            if (members.isEmpty()) {
                sessionMembers.remove(sessionId);
                sessionSinks.remove(sessionId);
            }
        }
    }

    /** 当前在线成员 userId 列表。 */
    public synchronized java.util.List<String> onlineOf(String sessionId) {
        Map<String, String> members = sessionMembers.get(sessionId);
        return members == null ? java.util.List.of() : java.util.List.copyOf(members.keySet());
    }

    /** 向会话广播事件（仅投递给当前在线连接）。 */
    public void broadcast(String sessionId, CollabEvent event) {
        Many<CollabEvent> sink = sessionSinks.get(sessionId);
        if (sink == null) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("会话 {} 广播事件失败（缓冲溢出/无订阅者）: {}", sessionId, result);
        }
    }

    /** 订阅会话事件流。 */
    public Flux<CollabEvent> stream(String sessionId) {
        return sinkFor(sessionId).asFlux();
    }

    // ---- 会话级生成锁（修正点 2）----

    /** 尝试获取生成锁；已被他人持有则返回 false。 */
    public synchronized boolean tryAcquireGeneratingLock(String sessionId, String userId) {
        String holder = generatingSessions.get(sessionId);
        if (holder != null && !holder.equals(userId)) {
            return false;
        }
        generatingSessions.put(sessionId, userId);
        return true;
    }

    public boolean isGenerating(String sessionId) {
        return generatingSessions.containsKey(sessionId);
    }

    /** 释放生成锁（仅持有者或强制释放）。 */
    public synchronized void releaseGeneratingLock(String sessionId, String userId, boolean force) {
        if (force || userId.equals(generatingSessions.get(sessionId))) {
            generatingSessions.remove(sessionId);
        }
    }

    // ---- 广播辅助（供 ChatOrchestrator / REST 编辑删除接口调用）----

    public void broadcastMessageUpdated(
            String sessionId, String messageId, String role, String content, String editorId) {
        broadcast(
                sessionId,
                new CollabEvent.MessageUpdated(
                        sessionId, messageId, role, content, editorId, System.currentTimeMillis()));
    }

    public void broadcastMessageDeleted(String sessionId, String messageId, String deleterId) {
        broadcast(
                sessionId, new CollabEvent.MessageDeleted(sessionId, messageId, deleterId, System.currentTimeMillis()));
    }

    public void broadcastSessionStatus(String sessionId, String status, String triggeredBy) {
        broadcast(sessionId, new CollabEvent.SessionStatus(sessionId, status, triggeredBy, System.currentTimeMillis()));
    }
}
