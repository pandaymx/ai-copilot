"use client";

import { useCallback, useRef, useState } from "react";
import { toast } from "sonner";
import type { KeyedMutator } from "swr";
import type {
  AttachmentItem,
  ChatMessage,
} from "@/components/chat/message-bubble";
import type { SelectedModel } from "@/components/chat/model-selector";
import type { ChatSession } from "@/components/chat/sidebar";
import { useTokenBudget } from "@/context/token-budget-context";
import {
  type ModelRecommendation,
  type StreamStore,
  useSpringAiStream,
} from "@/hooks/useSpringAiStream";
import type { DocChatDocItem } from "@/lib/api";
import { renameSessionApi } from "@/lib/api";
import { fetchTitle } from "@/lib/title";
import { ACTIVE_KEY, nextSessionId } from "./useChatSession";

const nextId = () => `msg-${crypto.randomUUID()}`;

export function deriveTitle(text: string): string {
  const firstLine = text.trim().split("\n")[0].trim();
  if (!firstLine) return "新会话";
  return firstLine.length > 18 ? `${firstLine.slice(0, 18)}…` : firstLine;
}

export interface UseChatStreamingOptions {
  activeId: string | null;
  setActiveId: (id: string | null) => void;
  messages: ChatMessage[];
  setMessages: React.Dispatch<React.SetStateAction<ChatMessage[]>>;
  mutateSessions: KeyedMutator<ChatSession[] | null>;
  sessionsRef: React.RefObject<ChatSession[]>;
  model: SelectedModel;
  currentSupportsVision: boolean;
  attachments: AttachmentItem[];
  setAttachments: React.Dispatch<React.SetStateAction<AttachmentItem[]>>;
  input: string;
  setInput: React.Dispatch<React.SetStateAction<string>>;
  imageMode: boolean;
  agentEnabled: boolean;
  documentChatEnabled: boolean;
  docChatDocuments: DocChatDocItem[];
  selectedDocIds: string[];
  personaId?: string;
}

export interface UseChatStreamingResult {
  loading: boolean;
  isStreaming: boolean;
  error: Error | null;
  stop: () => void;
  streamStore: StreamStore;
  recommendation: ModelRecommendation | null;
  setRecommendation: (rec: ModelRecommendation | null) => void;
  handleSend: (
    textOverride?: string,
    modelOverride?: { provider: string; model: string },
  ) => Promise<void>;
  handleRegenerate: (
    messageIndex?: number,
    modelOverride?: { provider: string; model: string },
  ) => void;
  handleEditAndResend: (messageIndex: number, newText: string) => void;
  liveIdRef: React.RefObject<string | null>;
}

export function useChatStreaming({
  activeId,
  setActiveId,
  messages,
  setMessages,
  mutateSessions,
  sessionsRef,
  model,
  currentSupportsVision,
  attachments,
  setAttachments,
  input,
  setInput,
  imageMode,
  agentEnabled,
  documentChatEnabled,
  docChatDocuments,
  selectedDocIds,
  personaId,
}: UseChatStreamingOptions): UseChatStreamingResult {
  const liveIdRef = useRef<string | null>(null);
  const liveUserTextRef = useRef<string>("");
  const { updateFromSseUsage } = useTokenBudget();
  const [recommendation, setRecommendation] =
    useState<ModelRecommendation | null>(null);

  const { loading, error, send, stop, streamStore } = useSpringAiStream({
    endpoint: "/api/chat/stream",
    onUsage: (u) => {
      updateFromSseUsage(u);
    },
    onRecommendation: (rec) => {
      setRecommendation(rec);
    },
    onConversationId: (serverConvId) => {
      if (!serverConvId) return;
      if (!activeId || serverConvId !== activeId) {
        setActiveId(serverConvId);
        void mutateSessions(
          (prev) =>
            (prev ?? []).map((s) =>
              s.id === activeId ? { ...s, id: serverConvId } : s,
            ),
          false,
        );
        if (typeof window !== "undefined") {
          localStorage.setItem(ACTIVE_KEY, serverConvId);
        }
      }
    },
    onIntent: (intent, intentLabel) => {
      const liveId = liveIdRef.current;
      if (!liveId) return;
      setMessages((prev) =>
        prev.map((m) => (m.id === liveId ? { ...m, intent, intentLabel } : m)),
      );
    },
    onContextCompression: (metadata) => {
      const liveId = liveIdRef.current;
      if (!liveId) return;
      setMessages((prev) =>
        prev.map((m) =>
          m.id === liveId ? { ...m, compressionMetadata: metadata } : m,
        ),
      );
    },
    onCitations: (citations) => {
      const liveId = liveIdRef.current;
      if (!liveId) return;
      setMessages((prev) =>
        prev.map((m) => (m.id === liveId ? { ...m, citations } : m)),
      );
    },
    onFinish: (finalContent, finalThinking, finalUsage, finalMetrics) => {
      if (finalUsage) {
        updateFromSseUsage(finalUsage);
      }
      const liveId = liveIdRef.current;
      if (!liveId || !activeId) return;
      liveIdRef.current = null;
      const question = liveUserTextRef.current;
      liveUserTextRef.current = "";

      const snap = streamStore.getSnapshot();
      const resolvedMetrics = finalMetrics ?? snap.metrics;
      setMessages((prev) =>
        prev.map((m) =>
          m.id === liveId
            ? {
                ...m,
                content: finalContent,
                thinking: finalThinking || m.thinking,
                usage: finalUsage ?? m.usage,
                metrics: resolvedMetrics ?? m.metrics,
                interaction: snap.interaction ?? m.interaction,
                compressionMetadata:
                  snap.contextCompression ?? m.compressionMetadata,
                citations:
                  snap.citations && snap.citations.length > 0
                    ? snap.citations
                    : m.citations,
              }
            : m,
        ),
      );

      void mutateSessions(
        (prev) =>
          (prev ?? []).map((s) => {
            if (s.id !== activeId) return s;
            const updatedMessages = (s.messages ?? []).map((m) =>
              m.id === liveId
                ? {
                    ...m,
                    content: finalContent,
                    thinking: finalThinking || m.thinking,
                    usage: finalUsage ?? m.usage,
                    metrics: resolvedMetrics ?? m.metrics,
                    interaction: snap.interaction ?? m.interaction,
                    compressionMetadata:
                      snap.contextCompression ?? m.compressionMetadata,
                    citations:
                      snap.citations && snap.citations.length > 0
                        ? snap.citations
                        : m.citations,
                  }
                : m,
            );
            return { ...s, messages: updatedMessages, updatedAt: Date.now() };
          }),
        false,
      );

      // 仅当标题仍为自动生成时才调用 AI 生成标题
      const target = sessionsRef.current.find((s) => s.id === activeId);
      if (!target || target.isDefaultTitle !== true) return;

      void (async () => {
        const aiTitle = await fetchTitle({
          message: question,
          answer: finalContent,
          provider: model.provider,
          model: model.model,
          conversationId: activeId,
        });
        const newTitle = aiTitle ?? deriveTitle(finalContent);
        await renameSessionApi(activeId, newTitle);
        void mutateSessions();
      })();
    },
  });

  const isStreaming = loading;

  const handleSend = useCallback(
    async (
      textOverride?: string,
      modelOverride?: { provider: string; model: string },
    ) => {
      const text = (textOverride ?? input).trim();
      if ((!text && attachments.length === 0) || isStreaming) return;

      const liveId = nextId();
      liveIdRef.current = liveId;

      const currentAttachments = [...attachments];
      const mediaPayload = currentAttachments
        .filter((att) => att.type === "image")
        .map((att) => ({ mimeType: att.mimeType, data: att.url }));

      if (mediaPayload.length > 0 && !currentSupportsVision) {
        toast.error("当前模型不支持图片，请切换到支持图片的模型");
        return;
      }

      // 将非图片文件的文本内容拼接为上下文前缀
      const fileAttachments = currentAttachments.filter(
        (att) => att.type === "file" && att.textContent,
      );
      const fileContextPrefix = fileAttachments
        .map(
          (att) =>
            `【附加上下文文件 ${att.name}】\n\`\`\`\n${att.textContent}\n\`\`\``,
        )
        .join("\n\n");

      const isRegenerate = Boolean(textOverride);
      const historySource = isRegenerate ? messages.slice(0, -2) : messages;
      const historyPayload = historySource
        .filter((m) => m.content.trim() !== "")
        .map((m) => ({ role: m.role, content: m.content }));

      const sendText = fileContextPrefix
        ? `${fileContextPrefix}\n\n${text}`
        : text;

      const userMsgText = text || (mediaPayload.length > 0 ? "[图片]" : "");

      const next: ChatMessage[] = [
        ...historySource,
        {
          id: nextId(),
          role: "user",
          content: userMsgText,
          attachments:
            currentAttachments.length > 0 ? currentAttachments : undefined,
        },
        { id: liveId, role: "assistant", content: "" },
      ];

      let currentConvId = activeId;

      if (!currentConvId) {
        currentConvId = nextSessionId();
        const newSession: ChatSession = {
          id: currentConvId,
          title: deriveTitle(userMsgText),
          updatedAt: Date.now(),
          messages: next,
          isDefaultTitle: true,
        };
        setActiveId(currentConvId);
        void mutateSessions((prev) => [newSession, ...(prev ?? [])], false);
        if (typeof window !== "undefined") {
          localStorage.setItem(ACTIVE_KEY, currentConvId);
        }
      } else {
        void mutateSessions(
          (prev) =>
            (prev ?? []).map((s) =>
              s.id === currentConvId
                ? { ...s, messages: next, updatedAt: Date.now() }
                : s,
            ),
          false,
        );
      }

      setMessages(next);
      setInput("");
      setAttachments([]);
      liveUserTextRef.current = userMsgText;

      let payloadText = sendText;
      if (
        imageMode &&
        !payloadText.startsWith("/image ") &&
        !payloadText.startsWith("/img ")
      ) {
        payloadText = `/image ${payloadText}`;
      }

      const activeProvider = modelOverride?.provider || model.provider;
      const activeModel = modelOverride?.model || model.model;

      send(payloadText, {
        provider: activeProvider,
        model: activeModel,
        conversationId: currentConvId,
        history: historyPayload,
        media: mediaPayload.length > 0 ? mediaPayload : undefined,
        agentEnabled,
        documentChatEnabled: documentChatEnabled || docChatDocuments.length > 0,
        docIds: selectedDocIds.length > 0 ? selectedDocIds : undefined,
        personaId: personaId || undefined,
      });
    },
    [
      attachments,
      input,
      isStreaming,
      messages,
      model.model,
      model.provider,
      send,
      agentEnabled,
      documentChatEnabled,
      docChatDocuments.length,
      selectedDocIds,
      personaId,
      activeId,
      currentSupportsVision,
      imageMode,
      mutateSessions,
      setActiveId,
      setAttachments,
      setInput,
      setMessages,
    ],
  );

  const handleRegenerate = useCallback(
    (
      messageIndex?: number,
      modelOverride?: { provider: string; model: string },
    ) => {
      if (isStreaming) return;
      let userMsgIdx = -1;
      if (typeof messageIndex === "number" && messageIndex >= 0) {
        if (messages[messageIndex]?.role === "user") {
          userMsgIdx = messageIndex;
        } else if (
          messages[messageIndex]?.role === "assistant" &&
          messageIndex > 0
        ) {
          userMsgIdx = messageIndex - 1;
        }
      } else {
        userMsgIdx = messages.length - 2;
      }

      if (userMsgIdx >= 0 && messages[userMsgIdx]?.role === "user") {
        const targetUserMsg = messages[userMsgIdx];
        const historyBefore = messages.slice(0, userMsgIdx);
        setMessages(historyBefore);
        void handleSend(targetUserMsg.content, modelOverride);
      }
    },
    [messages, isStreaming, handleSend, setMessages],
  );

  const handleEditAndResend = useCallback(
    (messageIndex: number, newText: string) => {
      if (isStreaming || !newText.trim()) return;
      const historyBefore = messages.slice(0, messageIndex);
      setMessages(historyBefore);
      void handleSend(newText.trim());
    },
    [messages, isStreaming, handleSend, setMessages],
  );

  return {
    loading,
    isStreaming,
    error,
    stop,
    streamStore,
    recommendation,
    setRecommendation,
    handleSend,
    handleRegenerate,
    handleEditAndResend,
    liveIdRef,
  };
}
