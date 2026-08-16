"use client";

import { useSyncExternalStore } from "react";
import type {
  AckEvent,
  CollabClientFrame,
  CollaborationState,
  CollabServerEvent,
  CursorEvent,
  ErrorEvent,
  MessageDeletedEvent,
  MessageUpdatedEvent,
  ParticipantRole,
  PresenceEvent,
  SessionStatusEvent,
  TypingEvent,
} from "@/lib/collab-types";

// 协作状态单例 store（仿 StreamStore 用 useSyncExternalStore 高频渲染）
let state: CollaborationState = {
  connected: false,
  online: [],
  role: null,
  generating: false,
  generatingBy: null,
  typingUsers: [],
};

const listeners = new Set<() => void>();
const typingTimers = new Map<string, ReturnType<typeof setTimeout>>();

function emit() {
  for (const l of listeners) l();
}

function setState(patch: Partial<CollaborationState>) {
  state = { ...state, ...patch };
  emit();
}

let socket: WebSocket | null = null;
let activeSessionId: string | null = null;
let activeUserId: string | null = null;

function clearTyping(userId: string) {
  const t = typingTimers.get(userId);
  if (t) {
    clearTimeout(t);
    typingTimers.delete(userId);
  }
  const next = state.typingUsers.filter((u) => u !== userId);
  if (next.length !== state.typingUsers.length) {
    setState({ typingUsers: next });
  }
}

function handleServerEvent(evt: CollabServerEvent) {
  switch (evt.type) {
    case "ack": {
      const ack = evt as AckEvent;
      setState({
        connected: true,
        online: ack.online,
        role: ack.role as ParticipantRole,
      });
      break;
    }
    case "presence": {
      const p = evt as PresenceEvent;
      if (p.status === "leave" || p.status === "removed") {
        setState({ online: state.online.filter((u) => u !== p.userId) });
        clearTyping(p.userId);
      } else if (p.status === "join" || p.status === "invited") {
        if (p.userId && !state.online.includes(p.userId)) {
          setState({ online: [...state.online, p.userId] });
        }
      }
      break;
    }
    case "typing": {
      const t = evt as TypingEvent;
      if (t.active) {
        // 修正点 3：3s 无更新自动清除，避免连接断开导致永久"正在输入"
        clearTyping(t.userId);
        if (!state.typingUsers.includes(t.userId)) {
          setState({ typingUsers: [...state.typingUsers, t.userId] });
        }
        typingTimers.set(
          t.userId,
          setTimeout(() => clearTyping(t.userId), 3000),
        );
      } else {
        clearTyping(t.userId);
      }
      break;
    }
    case "session.status": {
      const s = evt as SessionStatusEvent;
      setState({
        generating: s.status === "generating",
        generatingBy: s.triggeredBy ?? null,
      });
      break;
    }
    case "cursor": {
      const c = evt as CursorEvent;
      // 光标位置由 message-bubble 层消费，这里仅保留透传钩子
      cursorListeners.forEach((cb) => {
        cb(c);
      });
      break;
    }
    case "message.updated": {
      const m = evt as MessageUpdatedEvent;
      // 发送者本人已拥有该消息（流式或本地编辑），跳过避免重复
      if (m.editorId && m.editorId === activeUserId) break;
      messageListeners.forEach((cb) => {
        cb(m);
      });
      break;
    }
    case "message.deleted": {
      const m = evt as MessageDeletedEvent;
      if (m.deleterId && m.deleterId === activeUserId) break;
      messageListeners.forEach((cb) => {
        cb(m);
      });
      break;
    }
    case "error": {
      const err = evt as ErrorEvent;
      console.error("[collab] server error:", err.message);
      break;
    }
  }
}

const cursorListeners = new Set<(e: CursorEvent) => void>();
const messageListeners = new Set<
  (e: MessageUpdatedEvent | MessageDeletedEvent) => void
>();

function send(frame: CollabClientFrame) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(frame));
  }
}

export const collaborationStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  getSnapshot() {
    return state;
  },
  onCursor(cb: (e: CursorEvent) => void) {
    cursorListeners.add(cb);
    return () => cursorListeners.delete(cb);
  },
  onMessage(cb: (e: MessageUpdatedEvent | MessageDeletedEvent) => void) {
    messageListeners.add(cb);
    return () => messageListeners.delete(cb);
  },
  connect(sessionId: string, userId: string) {
    if (socket && activeSessionId === sessionId && activeUserId === userId) {
      return;
    }
    this.disconnect();
    activeSessionId = sessionId;
    activeUserId = userId;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const url = `${proto}://${location.host}/api/collab?sessionId=${encodeURIComponent(
      sessionId,
    )}&userId=${encodeURIComponent(userId)}`;
    socket = new WebSocket(url);
    socket.onopen = () => {
      setState({ connected: true });
      // 心跳，保持在线状态
      const ping = setInterval(() => {
        if (socket?.readyState === WebSocket.OPEN) send({ type: "ping" });
        else clearInterval(ping);
      }, 25000);
    };
    socket.onmessage = (e) => {
      try {
        const evt = JSON.parse(e.data) as CollabServerEvent;
        handleServerEvent(evt);
      } catch {
        /* ignore */
      }
    };
    socket.onclose = () => {
      setState({
        connected: false,
        online: [],
        generating: false,
        typingUsers: [],
      });
      socket = null;
    };
    socket.onerror = () => {
      socket?.close();
    };
  },
  disconnect() {
    if (socket) {
      socket.close();
      socket = null;
    }
    activeSessionId = null;
    activeUserId = null;
    for (const t of typingTimers.values()) clearTimeout(t);
    typingTimers.clear();
    setState({
      connected: false,
      online: [],
      role: null,
      generating: false,
      generatingBy: null,
      typingUsers: [],
    });
  },
  // 高频事件（前端节流后上行）
  sendCursor(messageId: string | undefined, caret: number) {
    send({ type: "cursor", messageId, caret });
  },
  sendTyping(active: boolean) {
    send({ type: "typing", active });
  },
  sendMessageUpdated(messageId: string, role: string, content: string) {
    send({ type: "message.updated", messageId, role, content });
  },
  sendMessageDeleted(messageId: string) {
    send({ type: "message.deleted", messageId });
  },
  acquireLock() {
    send({ type: "session.lock" });
  },
  releaseLock() {
    send({ type: "session.unlock" });
  },
};

/** 订阅协作状态（组件渲染用）。 */
export function useCollaboration(): CollaborationState {
  return useSyncExternalStore(
    collaborationStore.subscribe,
    collaborationStore.getSnapshot,
  );
}
