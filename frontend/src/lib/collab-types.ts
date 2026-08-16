// 共享会话协作协议类型（与后端 CollabEvent 对齐）

export type ParticipantRole = "OWNER" | "EDITOR" | "VIEWER";

export interface Participant {
  userId: string;
  role: ParticipantRole;
}

// 后端下行的事件帧（仅消费字段）
export interface PresenceEvent {
  type: "presence";
  sessionId: string;
  userId: string;
  role?: string;
  status: "join" | "leave" | "invited" | "removed";
  ts: number;
}

export interface CursorEvent {
  type: "cursor";
  sessionId: string;
  userId: string;
  messageId?: string;
  caret: number;
  ts: number;
}

export interface TypingEvent {
  type: "typing";
  sessionId: string;
  userId: string;
  active: boolean;
  ts: number;
}

export interface MessageUpdatedEvent {
  type: "message.updated";
  sessionId: string;
  messageId: string;
  role: string;
  content: string;
  editorId: string;
  ts: number;
}

export interface MessageDeletedEvent {
  type: "message.deleted";
  sessionId: string;
  messageId: string;
  deleterId: string;
  ts: number;
}

export interface SessionStatusEvent {
  type: "session.status";
  sessionId: string;
  status: "generating" | "idle";
  triggeredBy?: string | null;
  ts: number;
}

export interface AckEvent {
  type: "ack";
  sessionId: string;
  userId: string;
  role: string;
  online: string[];
  ts: number;
}

export interface ErrorEvent {
  type: "error";
  message: string;
}

export type CollabServerEvent =
  | PresenceEvent
  | CursorEvent
  | TypingEvent
  | MessageUpdatedEvent
  | MessageDeletedEvent
  | SessionStatusEvent
  | AckEvent
  | ErrorEvent;

// 前端上行帧
export interface CursorFrame {
  type: "cursor";
  messageId?: string;
  caret: number;
}

export interface TypingFrame {
  type: "typing";
  active: boolean;
}

export interface MessageUpdatedFrame {
  type: "message.updated";
  messageId: string;
  role: string;
  content: string;
}

export interface MessageDeletedFrame {
  type: "message.deleted";
  messageId: string;
}

export type CollabClientFrame =
  | CursorFrame
  | TypingFrame
  | MessageUpdatedFrame
  | MessageDeletedFrame
  | { type: "session.lock" }
  | { type: "session.unlock" }
  | { type: "ping" };

// 前端协作本地状态（供 useSyncExternalStore 订阅）
export interface CollaborationState {
  connected: boolean;
  online: string[];
  role: ParticipantRole | null;
  generating: boolean;
  generatingBy: string | null;
  typingUsers: string[];
}
