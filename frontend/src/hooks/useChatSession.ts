"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import useSWR, { type KeyedMutator } from "swr";
import type { ChatMessage } from "@/components/chat/message-bubble";
import type { ChatSession } from "@/components/chat/sidebar";
import {
  deleteSessionApi,
  fetchSessionDetailApi,
  fetchSessionsApi,
  renameSessionApi,
} from "@/lib/api";

export const ACTIVE_KEY = "ai-copilot-active";
export const SESSIONS_STORAGE_KEY = "ai-copilot-sessions";

export const nextSessionId = () => `sess-${crypto.randomUUID()}`;

export interface UseChatSessionOptions {
  isStreaming?: boolean;
  onSelectSessionCallback?: (id: string) => void;
}

export interface UseChatSessionResult {
  sessions: ChatSession[];
  activeId: string | null;
  setActiveId: (id: string | null) => void;
  activeSession: ChatSession | undefined;
  messages: ChatMessage[];
  setMessages: React.Dispatch<React.SetStateAction<ChatMessage[]>>;
  loadingSessions: boolean;
  isOfflineFallback: boolean;
  mutateSessions: KeyedMutator<ChatSession[] | null>;
  setStreaming: (value: boolean) => void;
  selectSession: (
    id: string,
    targetMessageId?: string | number,
  ) => Promise<void>;
  deleteSession: (id: string) => Promise<void>;
  renameSession: (id: string, newTitle: string) => Promise<void>;
  newSession: () => void;
  sessionsRef: React.RefObject<ChatSession[]>;
  scrollToMessage: (targetMessageId: string | number) => void;
}

export function useChatSession(
  options?: UseChatSessionOptions,
): UseChatSessionResult {
  const [externalStreaming, setExternalStreaming] = useState(false);
  const isStreaming = Boolean(options?.isStreaming) || externalStreaming;
  const onSelectSessionCallback = options?.onSelectSessionCallback;

  const setStreaming = useCallback((value: boolean) => {
    setExternalStreaming(value);
  }, []);

  const {
    data: dbSessions,
    error: sessionsError,
    isLoading: loadingSessions,
    mutate: mutateSessions,
  } = useSWR<ChatSession[] | null>("/api/chat/sessions", fetchSessionsApi, {
    revalidateOnFocus: true,
    dedupingInterval: 2000,
  });

  const isOfflineFallback = Boolean(sessionsError || dbSessions === null);
  const [offlineSessions, setOfflineSessions] = useState<ChatSession[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  // 离线会话恢复
  useEffect(() => {
    if (isOfflineFallback && typeof window !== "undefined") {
      try {
        const raw = localStorage.getItem(SESSIONS_STORAGE_KEY);
        if (raw) {
          setOfflineSessions(JSON.parse(raw) as ChatSession[]);
        }
      } catch {
        // 忽略解析错误
      }
    }
  }, [isOfflineFallback]);

  const sessions = dbSessions ?? offlineSessions;
  const sessionsRef = useRef(sessions);
  useEffect(() => {
    sessionsRef.current = sessions;
  }, [sessions]);

  const activeSession = sessions.find((s) => s.id === activeId);

  // 会话列表本地持久化：500ms 防抖
  useEffect(() => {
    if (
      typeof window === "undefined" ||
      isStreaming ||
      !sessions ||
      sessions.length === 0
    ) {
      return;
    }
    const timer = setTimeout(() => {
      try {
        localStorage.setItem(SESSIONS_STORAGE_KEY, JSON.stringify(sessions));
      } catch (err) {
        console.error("Failed to persist sessions to localStorage:", err);
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [sessions, isStreaming]);

  const scrollToMessage = useCallback((targetMessageId: string | number) => {
    setTimeout(() => {
      const targetEl =
        document.getElementById(`msg-${targetMessageId}`) ||
        document.querySelector(`[data-message-id="${targetMessageId}"]`);
      if (targetEl) {
        targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
        targetEl.classList.add(
          "ring-2",
          "ring-indigo-500",
          "bg-indigo-500/10",
          "dark:bg-indigo-500/20",
        );
        setTimeout(() => {
          targetEl.classList.remove(
            "ring-2",
            "ring-indigo-500",
            "bg-indigo-500/10",
            "dark:bg-indigo-500/20",
          );
        }, 2500);
      }
    }, 150);
  }, []);

  const selectSession = useCallback(
    async (id: string, targetMessageId?: string | number) => {
      if (id === activeId) {
        onSelectSessionCallback?.(id);
        if (targetMessageId !== undefined && targetMessageId !== null) {
          scrollToMessage(targetMessageId);
        }
        return;
      }

      setActiveId(id);
      if (typeof window !== "undefined") {
        localStorage.setItem(ACTIVE_KEY, id);
      }
      onSelectSessionCallback?.(id);

      const detail = await fetchSessionDetailApi(id);
      if (detail?.messages && detail.messages.length > 0) {
        setMessages(detail.messages);
      } else {
        const fallback = sessionsRef.current.find((s) => s.id === id);
        setMessages(fallback?.messages ?? []);
      }

      if (targetMessageId !== undefined && targetMessageId !== null) {
        scrollToMessage(targetMessageId);
      }
    },
    [activeId, onSelectSessionCallback, scrollToMessage],
  );

  const deleteSession = useCallback(
    async (id: string) => {
      await deleteSessionApi(id);
      void mutateSessions(
        (prev) => (prev ?? []).filter((s) => s.id !== id),
        false,
      );
      setOfflineSessions((prev) => prev.filter((s) => s.id !== id));

      if (activeId === id) {
        const remaining = sessions.filter((s) => s.id !== id);
        if (remaining.length > 0) {
          void selectSession(remaining[0].id);
        } else {
          setActiveId(null);
          setMessages([]);
          if (typeof window !== "undefined") {
            localStorage.removeItem(ACTIVE_KEY);
          }
        }
      }
    },
    [activeId, mutateSessions, selectSession, sessions],
  );

  const renameSession = useCallback(
    async (id: string, newTitle: string) => {
      await renameSessionApi(id, newTitle);
      void mutateSessions(
        (prev) =>
          (prev ?? []).map((s) =>
            s.id === id ? { ...s, title: newTitle, isDefaultTitle: false } : s,
          ),
        false,
      );
      setOfflineSessions((prev) =>
        prev.map((s) =>
          s.id === id ? { ...s, title: newTitle, isDefaultTitle: false } : s,
        ),
      );
    },
    [mutateSessions],
  );

  const newSession = useCallback(() => {
    setActiveId(null);
    setMessages([]);
    if (typeof window !== "undefined") {
      localStorage.removeItem(ACTIVE_KEY);
    }
  }, []);

  // 恢复上次激活的会话
  useEffect(() => {
    if (loadingSessions || isStreaming) return;
    const currentSessions = sessions;
    if (currentSessions.length === 0) return;

    if (activeId === null) {
      const savedActiveId =
        typeof window !== "undefined" ? localStorage.getItem(ACTIVE_KEY) : null;
      const targetId =
        savedActiveId && currentSessions.some((s) => s.id === savedActiveId)
          ? savedActiveId
          : currentSessions[0].id;

      setActiveId(targetId);
      if (typeof window !== "undefined") {
        localStorage.setItem(ACTIVE_KEY, targetId);
      }

      if (messages.length === 0) {
        void (async () => {
          const detail = await fetchSessionDetailApi(targetId);
          if (detail?.messages && detail.messages.length > 0) {
            setMessages(detail.messages);
          } else {
            const fallback = currentSessions.find((s) => s.id === targetId);
            setMessages(fallback?.messages ?? []);
          }
        })();
      }
    } else if (
      activeId !== null &&
      currentSessions.length === 0 &&
      messages.length === 0
    ) {
      setActiveId(null);
      setMessages([]);
      if (typeof window !== "undefined") {
        localStorage.removeItem(ACTIVE_KEY);
      }
    }
  }, [loadingSessions, isStreaming, sessions, activeId, messages.length]);

  return {
    sessions,
    activeId,
    setActiveId,
    activeSession,
    messages,
    setMessages,
    loadingSessions,
    isOfflineFallback,
    mutateSessions,
    setStreaming,
    selectSession,
    deleteSession,
    renameSession,
    newSession,
    sessionsRef,
    scrollToMessage,
  };
}
