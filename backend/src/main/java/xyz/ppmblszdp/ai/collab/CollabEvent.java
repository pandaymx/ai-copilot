package xyz.ppmblszdp.ai.collab;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 协作 WebSocket 事件帧。
 *
 * <p>帧类型分两类：
 * <ul>
 *   <li>瞬态（transient）：{@code cursor}/{@code typing}/{@code presence} —— 仅内存广播，不持久化；</li>
 *   <li>持久（durable）：{@code message.updated}/{@code message.deleted}/{@code session.status}
 *       —— 表示消息或会话状态变更，必须可靠送达。</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CollabEvent.Presence.class, name = "presence"),
    @JsonSubTypes.Type(value = CollabEvent.Cursor.class, name = "cursor"),
    @JsonSubTypes.Type(value = CollabEvent.Typing.class, name = "typing"),
    @JsonSubTypes.Type(value = CollabEvent.MessageUpdated.class, name = "message.updated"),
    @JsonSubTypes.Type(value = CollabEvent.MessageDeleted.class, name = "message.deleted"),
    @JsonSubTypes.Type(value = CollabEvent.SessionStatus.class, name = "session.status"),
    @JsonSubTypes.Type(value = CollabEvent.Ack.class, name = "ack"),
    @JsonSubTypes.Type(value = CollabEvent.Error.class, name = "error")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface CollabEvent
        permits CollabEvent.Presence,
                CollabEvent.Cursor,
                CollabEvent.Typing,
                CollabEvent.MessageUpdated,
                CollabEvent.MessageDeleted,
                CollabEvent.SessionStatus,
                CollabEvent.Ack,
                CollabEvent.Error {

    String type();

    /** 在线状态变化（join/leave）。 */
    record Presence(String type, String sessionId, String userId, String role, String status, long ts)
            implements CollabEvent {
        public Presence {
            type = "presence";
        }
    }

    /** 光标位置同步（瞬态，可丢弃旧帧）。 */
    record Cursor(String sessionId, String userId, String messageId, int caret, long ts) implements CollabEvent {
        @Override
        public String type() {
            return "cursor";
        }
    }

    /** 正在输入提示（瞬态，接收端 3s 无更新自动清除）。 */
    record Typing(String sessionId, String userId, boolean active, long ts) implements CollabEvent {
        @Override
        public String type() {
            return "typing";
        }
    }

    /** 消息编辑后广播（持久）。 */
    record MessageUpdated(String sessionId, String messageId, String role, String content, String editorId, long ts)
            implements CollabEvent {
        @Override
        public String type() {
            return "message.updated";
        }
    }

    /** 消息删除后广播（持久）。 */
    record MessageDeleted(String sessionId, String messageId, String deleterId, long ts) implements CollabEvent {
        @Override
        public String type() {
            return "message.deleted";
        }
    }

    /** 会话生成状态（generating / idle），用于锁定其他用户发送（修正点 2）。 */
    record SessionStatus(String sessionId, String status, String triggeredBy, long ts) implements CollabEvent {
        @Override
        public String type() {
            return "session.status";
        }
    }

    /** 握手/加入确认。 */
    record Ack(String sessionId, String userId, String role, java.util.List<String> online, long ts)
            implements CollabEvent {
        @Override
        public String type() {
            return "ack";
        }
    }

    /** 错误（如越权）。 */
    record Error(String message) implements CollabEvent {
        @Override
        public String type() {
            return "error";
        }
    }
}
