package xyz.ppmblszdp.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.collab.CollabEvent;
import xyz.ppmblszdp.ai.collab.CollaborationBus;
import xyz.ppmblszdp.ai.repository.SessionParticipant;
import xyz.ppmblszdp.ai.service.ParticipantAuthService;

/**
 * 共享会话协作 WebSocket 处理器（WebFlux 响应式）。
 *
 * <p>握手鉴权（修正点 1）：浏览器原生 WebSocket 不支持自定义 Header，故身份从
 * 查询参数 {@code ?sessionId=xxx&userId=yyy} 提取，并在服务端用
 * {@link ParticipantAuthService} 二次校验是否为该会话参与者（修正点 5）。
 *
 * <p>会话级生成锁（修正点 2）：当用户触发流式生成时广播 {@code session.status=generating}，
 * 其他 EDITOR/VIEWER 的发送框被前端锁定，直到落盘完成广播 {@code session.status=idle}。
 */
@Component
public class CollabController implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CollabController.class);

    private final CollaborationBus bus;
    private final ParticipantAuthService authService;
    private final ObjectMapper objectMapper;

    public CollabController(CollaborationBus bus, ParticipantAuthService authService, ObjectMapper objectMapper) {
        this.bus = bus;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        String sessionId = queryParam(uri, "sessionId");
        String userId = queryParam(uri, "userId");

        if (sessionId == null || userId == null) {
            return session.send(
                            Mono.just(session.textMessage(toJson(new CollabEvent.Error("缺少 sessionId 或 userId 参数")))))
                    .then(session.close());
        }

        return authService
                .isParticipant(sessionId, userId)
                .flatMap(participant -> {
                    if (!participant) {
                        return session.send(Mono.just(session.textMessage(toJson(new CollabEvent.Error("无权访问该共享会话")))))
                                .then(session.close());
                    }
                    return establish(session, sessionId, userId);
                })
                .onErrorResume(e -> {
                    log.warn("协作握手鉴权异常 session={} user={}: {}", sessionId, userId, e.getMessage());
                    return session.close();
                });
    }

    private Mono<Void> establish(WebSocketSession session, String sessionId, String userId) {
        return authService
                .roleOf(sessionId, userId)
                .map(role -> role != null ? role : SessionParticipant.Role.VIEWER)
                .flatMap(role -> doEstablish(session, sessionId, userId, role));
    }

    private Mono<Void> doEstablish(
            WebSocketSession session, String sessionId, String userId, SessionParticipant.Role role) {
        List<String> online = bus.join(sessionId, userId, role.name());
        long now = System.currentTimeMillis();
        bus.broadcast(sessionId, new CollabEvent.Presence("presence", sessionId, userId, role.name(), "join", now));
        // 向自己确认，并回传当前在线成员
        Mono<Void> outgoingHello = session.send(Mono.just(
                session.textMessage(toJson(new CollabEvent.Ack(sessionId, userId, role.name(), online, now)))));

        // 下行：总线事件 -> WebSocket 文本帧
        Flux<WebSocketMessage> downstream = bus.stream(sessionId)
                .filter(evt -> isVisibleTo(evt, userId))
                .map(evt -> session.textMessage(toJson(evt)));

        // 上行：客户端帧 -> 总线广播 / 会话锁 / 回执
        Flux<WebSocketMessage> upstream = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> handleClientFrame(session, sessionId, userId, role, text))
                .filter(e -> e != null)
                .map(e -> session.textMessage(toJson(e)));

        Mono<Void> sessionFlow = session.send(Flux.merge(downstream, upstream))
                .then()
                .doFinally(sig -> {
                    bus.leave(sessionId, userId);
                    bus.broadcast(
                            sessionId,
                            new CollabEvent.Presence("presence", sessionId, userId, role.name(), "leave", now));
                    // 若离开者是生成锁持有者，释放锁
                    if (bus.isGenerating(sessionId)) {
                        bus.releaseGeneratingLock(sessionId, userId, true);
                        bus.broadcastSessionStatus(sessionId, "idle", userId);
                    }
                });

        return Mono.when(outgoingHello, sessionFlow);
    }

    /** 单个用户不应收到自己发出的 cursor/typing 回显（presence/message/status 需全部接收）。 */
    private boolean isVisibleTo(CollabEvent evt, String userId) {
        if (evt instanceof CollabEvent.Cursor c) {
            return !c.userId().equals(userId);
        }
        if (evt instanceof CollabEvent.Typing t) {
            return !t.userId().equals(userId);
        }
        return true;
    }

    private Mono<CollabEvent> handleClientFrame(
            WebSocketSession session, String sessionId, String userId, SessionParticipant.Role role, String text) {
        try {
            // 轻量解析 type，避免完整反序列化开销
            String type = extractType(text);
            long now = System.currentTimeMillis();
            switch (type) {
                case "cursor" -> {
                    CollabEvent.Cursor c = parse(text, CollabEvent.Cursor.class);
                    if (c != null) {
                        bus.broadcast(
                                sessionId, new CollabEvent.Cursor(sessionId, userId, c.messageId(), c.caret(), now));
                    }
                }
                case "typing" -> {
                    CollabEvent.Typing t = parse(text, CollabEvent.Typing.class);
                    if (t != null) {
                        bus.broadcast(sessionId, new CollabEvent.Typing(sessionId, userId, t.active(), now));
                    }
                }
                case "session.lock" -> {
                    // 仅 EDITOR/OWNER 可请求生成锁
                    if (role.atLeast(SessionParticipant.Role.EDITOR)) {
                        if (bus.tryAcquireGeneratingLock(sessionId, userId)) {
                            bus.broadcastSessionStatus(sessionId, "generating", userId);
                        }
                    }
                }
                case "session.unlock" -> {
                    if (bus.isGenerating(sessionId)) {
                        bus.releaseGeneratingLock(sessionId, userId, role == SessionParticipant.Role.OWNER);
                        bus.broadcastSessionStatus(sessionId, "idle", userId);
                    }
                }
                case "ping" -> {
                    return Mono.just(new CollabEvent.Ack(sessionId, userId, role.name(), bus.onlineOf(sessionId), now));
                }
                default -> {
                    // 忽略未知帧
                }
            }
        } catch (Exception e) {
            log.debug("解析协作上行帧失败: {}", e.getMessage());
        }
        return Mono.empty();
    }

    /** 是否处于生成锁（供 ChatController 入口校验，避免并发触发）。 */
    public boolean isGenerating(String sessionId) {
        return bus.isGenerating(sessionId);
    }

    private static String queryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }

    private static String extractType(String text) {
        int idx = text.indexOf("\"type\"");
        if (idx < 0) {
            return "";
        }
        int colon = text.indexOf(':', idx);
        int q1 = text.indexOf('"', colon);
        int q2 = text.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return "";
        }
        return text.substring(q1 + 1, q2);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }

    private <T> T parse(String text, Class<T> type) {
        try {
            return objectMapper.readValue(text, type);
        } catch (Exception e) {
            return null;
        }
    }
}
