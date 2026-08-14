"use client";

import {
  AlertTriangle,
  Code2,
  Cpu,
  Download,
  FileText,
  Layers,
  PanelLeftOpen,
  Paperclip,
  RotateCcw,
  Search,
  Send,
  Sparkles,
  Square,
  UploadCloud,
  Wand2,
  X,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import useSWR from "swr";
import { ExportDialog } from "@/components/chat/export-dialog";
import {
  type AttachmentItem,
  type ChatMessage,
  LiveMessageBubble,
  MessageBubble,
} from "@/components/chat/message-bubble";
import {
  type BackendProviderEntry,
  isVisionModel,
  ModelSelector,
  type SelectedModel,
} from "@/components/chat/model-selector";
import { SearchDialog } from "@/components/chat/search-dialog";
import { type ChatSession, Sidebar } from "@/components/chat/sidebar";
import { VisionScenarioPills } from "@/components/chat/vision-scenario-pills";
import { VoiceRecorderButton } from "@/components/chat/voice-recorder-button";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { useSpringAiStream } from "@/hooks/useSpringAiStream";
import { useVoiceRecorder } from "@/hooks/useVoiceRecorder";
import {
  deleteSessionApi,
  fetchSessionDetailApi,
  fetchSessionsApi,
  renameSessionApi,
} from "@/lib/api";
import { compressImage } from "@/lib/image-compressor";
import { fetchTitle } from "@/lib/title";
import { cn } from "@/lib/utils";
import { transcribe } from "@/lib/voice";

const ACTIVE_KEY = "ai-copilot-active";
const MODEL_STORAGE_KEY = "ai-copilot-selected-model";
const SESSIONS_STORAGE_KEY = "ai-copilot-sessions";

const nextId = () => `msg-${crypto.randomUUID()}`;
const nextSessionId = () => `sess-${crypto.randomUUID()}`;

const SUGGESTED_PROMPTS = [
  {
    icon: Code2,
    category: "代码开发",
    text: "用 Spring Boot 4.x 写一个 Reactive WebFlux SSE 流式控制器",
    gradient: "from-blue-500 to-cyan-500",
  },
  {
    icon: Cpu,
    category: "性能调优",
    text: "对比分析 Java 25 Virtual Threads 与 Kotlin 协程在 IO 密集场景的差异",
    gradient: "from-emerald-500 to-teal-500",
  },
  {
    icon: Layers,
    category: "架构设计",
    text: "设计一个高并发、低延迟的分布式 AI Agent 状态流转模型",
    gradient: "from-purple-500 to-indigo-500",
  },
  {
    icon: Wand2,
    category: "前端工程",
    text: "编写一个支持 Server-Sent Events 流式打字机效果的 React Hook",
    gradient: "from-amber-500 to-orange-500",
  },
];

/** 从 localStorage 读取上次使用的模型配置 */
function loadSavedModel(): SelectedModel {
  if (typeof window === "undefined") {
    return { provider: "deepseek", model: "deepseek-chat" };
  }
  try {
    const raw = localStorage.getItem(MODEL_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as SelectedModel;
      if (parsed?.provider && parsed?.model) {
        return parsed;
      }
    }
  } catch {
    // 忽略解析错误
  }
  return { provider: "deepseek", model: "deepseek-chat" };
}

export default function Home() {
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

  const { loading, error, send, stop, streamStore } = useSpringAiStream({
    endpoint: "/api/chat/stream",
    onConversationId: (serverConvId) => {
      if (!serverConvId) return;
      // 首次发送时 activeId 为 null，必须无条件设置；
      // 后续仅在会话 ID 变更时更新（避免不必要的重渲染）。
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
    onFinish: (finalContent, finalThinking, finalUsage) => {
      const liveId = liveIdRef.current;
      if (!liveId || !activeId) return;
      liveIdRef.current = null;
      const question = liveUserTextRef.current;
      liveUserTextRef.current = "";

      setMessages((prev) =>
        prev.map((m) =>
          m.id === liveId
            ? {
                ...m,
                content: finalContent,
                thinking: finalThinking || m.thinking,
                usage: finalUsage ?? m.usage,
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
                  }
                : m,
            );
            return { ...s, messages: updatedMessages, updatedAt: Date.now() };
          }),
        false,
      );

      // 仅当标题仍为自动生成（未被用户重命名、也未被 AI 改写）时才更新标题
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

  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [imageMode, setImageMode] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [showExport, setShowExport] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  // 全局 ⌘K / Ctrl+K 快捷键唤起全盘全文检索
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        // 强制打开搜索框，避免 toggle 在多测试/多快捷键下状态翻转导致的不确定行为
        setSearchOpen(true);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);
  const [model, setModel] = useState<SelectedModel>({
    provider: "deepseek",
    model: "deepseek-chat",
  });
  const [catalog, setCatalog] = useState<BackendProviderEntry[]>([]);

  // 计算当前选中的模型是否支持视觉图片处理
  const currentProviderObj = catalog.find((p) => p.id === model.provider);
  const currentModelObj = currentProviderObj?.models.find(
    (m) => m.id === model.model,
  );
  const currentSupportsVision = currentModelObj
    ? isVisionModel(currentModelObj)
    : model.provider === "openai" ||
      model.provider === "google" ||
      model.model.includes("gpt-4") ||
      model.model.includes("gemini");

  const [attachments, setAttachments] = useState<AttachmentItem[]>([]);
  const [agentEnabled, setAgentEnabled] = useState<boolean>(false);
  const [isDraggingOver, setIsDraggingOver] = useState<boolean>(false);
  const dragCounterRef = useRef<number>(0);
  const lastPastedRef = useRef<{ time: number; key: string }>({
    time: 0,
    key: "",
  });
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 语音录制：录音停止后自动上传转写并回填输入框
  const recorder = useVoiceRecorder();
  const handleVoiceStop = useCallback(async () => {
    const result = await recorder.stop();
    if (!result) return;
    try {
      const text = await transcribe(result.base64, result.mimeType);
      if (text) setInput((prev) => (prev ? `${prev} ${text}` : text).trim());
    } catch (err) {
      console.error("语音识别失败:", err);
    }
  }, [recorder]);

  const isStreaming = loading;
  const hasError = Boolean(error);

  const processFiles = useCallback(
    async (files: FileList | File[]) => {
      const fileList = Array.from(files);
      if (fileList.length === 0) return;

      const newAttachments: AttachmentItem[] = [];
      for (const file of fileList) {
        if (file.size > 10 * 1024 * 1024) {
          toast.error(`文件 "${file.name}" 超过 10MB 限制`);
          continue;
        }

        if (file.type.startsWith("image/")) {
          if (!currentSupportsVision) {
            toast.error(
              "当前模型不支持图片，请切换到支持图片的模型 (如 GPT-4o, Gemini 等)",
            );
            continue;
          }
          try {
            const compressed = await compressImage(file);
            newAttachments.push({
              id: `att-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
              name: compressed.name,
              type: "image",
              mimeType: compressed.mimeType,
              url: compressed.dataUrl,
              size: compressed.size,
            });
          } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "图片处理失败");
          }
        } else {
          // 非图片文件：读取文本内容，存储为 AttachmentItem
          const textContent = await file.text();
          newAttachments.push({
            id: `att-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
            name: file.name,
            type: "file",
            mimeType: file.type || "text/plain",
            url: "",
            size: file.size,
            textContent,
          });
        }
      }

      if (newAttachments.length > 0) {
        setAttachments((prev) => [...prev, ...newAttachments].slice(0, 4));
      }
    },
    [currentSupportsVision],
  );

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      void processFiles(e.target.files);
      e.target.value = "";
    }
  };

  const removeAttachment = (id: string) => {
    setAttachments((prev) => prev.filter((a) => a.id !== id));
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (e.clipboardData.files && e.clipboardData.files.length > 0) {
      const files = Array.from(e.clipboardData.files);
      const pasteKey = files.map((f) => `${f.name}-${f.size}`).join(",");
      const now = Date.now();
      if (
        now - lastPastedRef.current.time < 500 &&
        lastPastedRef.current.key === pasteKey
      ) {
        e.preventDefault();
        return;
      }
      lastPastedRef.current = { time: now, key: pasteKey };

      const imageFiles = files.filter((f) => f.type.startsWith("image/"));
      if (imageFiles.length > 0) {
        e.preventDefault();
        if (!currentSupportsVision) {
          toast.error(
            "当前模型不支持图片，请切换到支持图片的模型 (如 GPT-4o, Gemini 等)",
          );
          return;
        }
        void processFiles(imageFiles);
      }
    }
  };

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current += 1;
    if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
      setIsDraggingOver(true);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current -= 1;
    if (dragCounterRef.current <= 0) {
      dragCounterRef.current = 0;
      setIsDraggingOver(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current = 0;
    setIsDraggingOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      void processFiles(e.dataTransfer.files);
    }
  };

  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const liveIdRef = useRef<string | null>(null);
  // 当前流式轮次对应的用户问题文本，用于流结束后生成标题
  const liveUserTextRef = useRef<string>("");
  // 最新 sessions 的引用，供 onFinish 闭包读取最新标题状态，避免闭包捕获过期值
  const sessionsRef = useRef<ChatSession[]>([]);
  useEffect(() => {
    sessionsRef.current = sessions;
  }, [sessions]);

  const handlePickPrompt = (text: string) => {
    setInput(text);
    if (!isStreaming) handleSend(text);
  };

  const goToRootDraft = useCallback(() => {
    stop();
    liveIdRef.current = null;
    setActiveId(null);
    setMessages([]);
    setInput("");
    if (typeof window !== "undefined") {
      localStorage.removeItem(ACTIVE_KEY);
      if (window.innerWidth < 768) {
        setCollapsed(true);
      }
    }
  }, [stop]);

  // 快捷键 Cmd + B / Ctrl + B 切换侧边栏
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "b") {
        e.preventDefault();
        setCollapsed((prev) => !prev);
      }
    };
    window.addEventListener("keydown", handleGlobalKeyDown);
    return () => window.removeEventListener("keydown", handleGlobalKeyDown);
  }, []);

  // 初始化：模型选择与激活会话联动
  useEffect(() => {
    const savedModel = loadSavedModel();
    setModel(savedModel);
  }, []);

  useEffect(() => {
    if (loadingSessions) return;
    // 流式生成中或存在 live message 时，本地会话尚未同步到 dbSessions，
    // 此时不能因会话列表为空就清空 activeId/messages，否则会丢失进行中的回复。
    if (isStreaming || liveIdRef.current) return;
    const activeRaw =
      typeof window !== "undefined" ? localStorage.getItem(ACTIVE_KEY) : null;
    const currentSessions = dbSessions ?? [];
    const targetId =
      activeRaw && currentSessions.some((s) => s.id === activeRaw)
        ? activeRaw
        : (currentSessions[0]?.id ?? null);

    if (targetId) {
      if (targetId !== activeId) {
        setActiveId(targetId);
        if (typeof window !== "undefined") {
          localStorage.setItem(ACTIVE_KEY, targetId);
        }
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
      // 仅在本地没有任何消息时才清空：本地新建的会话尚未同步到 dbSessions，
      // 列表为空不代表会话已删除，不能误清空进行中的对话。
      setActiveId(null);
      setMessages([]);
      if (typeof window !== "undefined") {
        localStorage.removeItem(ACTIVE_KEY);
      }
    }
  }, [dbSessions, loadingSessions, activeId, isStreaming, messages]);

  // 模型选择持久化
  useEffect(() => {
    if (typeof window !== "undefined" && model?.provider && model?.model) {
      localStorage.setItem(MODEL_STORAGE_KEY, JSON.stringify(model));
    }
  }, [model]);

  // 会话列表本地持久化：500ms 防抖，流式传输过程中跳过序列化以提升 UI 性能
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

  // 自动滚动到底部
  // biome-ignore lint/correctness/useExhaustiveDependencies: 副作用触发滚动
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // 自适应文本框高度
  // biome-ignore lint/correctness/useExhaustiveDependencies: 高度随 input 重新计算
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  }, [input]);

  function scrollToMessage(targetMessageId: string | number) {
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
  }

  async function selectSession(id: string, targetMessageId?: string | number) {
    if (id === activeId) {
      if (typeof window !== "undefined" && window.innerWidth < 768) {
        setCollapsed(true);
      }
      if (targetMessageId !== undefined && targetMessageId !== null) {
        scrollToMessage(targetMessageId);
      }
      return;
    }
    setActiveId(id);
    liveIdRef.current = null;
    stop();
    if (typeof window !== "undefined") {
      localStorage.setItem(ACTIVE_KEY, id);
      if (window.innerWidth < 768) {
        setCollapsed(true);
      }
    }
    const detail = await fetchSessionDetailApi(id);
    if (detail?.messages && detail.messages.length > 0) {
      setMessages(detail.messages);
    } else {
      const target = sessions.find((s) => s.id === id);
      setMessages(target?.messages ?? []);
    }

    if (targetMessageId !== undefined && targetMessageId !== null) {
      scrollToMessage(targetMessageId);
    }
  }

  function deleteSession(id: string) {
    void (async () => {
      await deleteSessionApi(id);
      void mutateSessions();
    })();
    void mutateSessions(
      (prev) => (prev ?? []).filter((s) => s.id !== id),
      false,
    );
    if (id === activeId) {
      goToRootDraft();
    }
  }

  function renameSession(id: string, newTitle: string) {
    void (async () => {
      await renameSessionApi(id, newTitle);
      void mutateSessions();
    })();
    void mutateSessions(
      (prev) =>
        (prev ?? []).map((s) =>
          s.id === id ? { ...s, title: newTitle, isDefaultTitle: false } : s,
        ),
      false,
    );
  }

  const handleSend = useCallback(
    (textOverride?: string) => {
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

      // 将非图片文件的文本内容拼接为上下文前缀，确保后端能收到文件内容
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

      // 界面显示的消息文本（不含文件内容，保持 UI 简洁）
      const userMsgText =
        text || (currentAttachments.length > 0 ? "[附件]" : "");

      // 实际发送给后端的消息文本（包含文件上下文）
      const sendText = fileContextPrefix
        ? `${fileContextPrefix}\n\n${text}`
        : userMsgText;

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
        // 处于根目录草稿状态时，发起对话才创建并保存会话
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

      send(payloadText, {
        provider: model.provider,
        model: model.model,
        conversationId: currentConvId,
        history: historyPayload,
        media: mediaPayload.length > 0 ? mediaPayload : undefined,
        agentEnabled,
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
      activeId,
      currentSupportsVision,
      imageMode,
      mutateSessions,
    ],
  );

  const handleRegenerate = useCallback(() => {
    setMessages((prev) => {
      const targetUserMsg = prev[prev.length - 2];
      if (targetUserMsg && targetUserMsg.role === "user") {
        setTimeout(() => handleSend(targetUserMsg.content), 0);
      }
      return prev;
    });
  }, [handleSend]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSend();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleReset = () => {
    goToRootDraft();
  };

  return (
    <div className="relative flex h-dvh overflow-hidden bg-ambient-mesh bg-zinc-50 dark:bg-zinc-950">
      <Sidebar
        sessions={sessions}
        activeId={activeId}
        collapsed={collapsed}
        loadingSessions={loadingSessions}
        isOfflineFallback={isOfflineFallback}
        onSelect={selectSession}
        onNew={goToRootDraft}
        onDelete={(id) => setDeleteTarget(id)}
        onRename={renameSession}
        onToggleCollapsed={() => setCollapsed((c) => !c)}
        onOpenSearch={() => setSearchOpen(true)}
      />

      {/* 移动端遮罩 */}
      <button
        type="button"
        className={cn(
          "fixed inset-0 z-20 bg-black/40 backdrop-blur-xs transition-opacity duration-300 md:hidden",
          collapsed
            ? "opacity-0 pointer-events-none"
            : "opacity-100 pointer-events-auto",
        )}
        onClick={() => setCollapsed(true)}
        aria-label="关闭侧边栏"
      />

      {/* biome-ignore lint/a11y/noStaticElementInteractions: 页面级拖拽文件上传容器 */}
      <div
        className="relative flex h-full min-h-0 min-w-0 flex-1 flex-col bg-transparent"
        onDragEnter={handleDragEnter}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {/* 拖拽上传覆盖层 */}
        {isDraggingOver && (
          <div className="pointer-events-none absolute inset-0 z-50 flex flex-col items-center justify-center gap-3 bg-indigo-950/70 backdrop-blur-sm border-2 border-dashed border-indigo-400 text-white animate-in fade-in duration-200">
            <div className="flex size-16 items-center justify-center rounded-2xl bg-indigo-600/90 shadow-xl ring-4 ring-indigo-400/40 animate-bounce">
              <UploadCloud className="size-8 text-white" />
            </div>
            <div className="text-center">
              <p className="text-base font-semibold">
                释放图片以添加至视觉对话
              </p>
              <p className="text-xs text-indigo-200 mt-1">
                支持 JPG / PNG / WebP / GIF，客户端自动保真压缩 (≤ 4MB)
              </p>
            </div>
          </div>
        )}

        {/* 顶部 Header */}
        <header className="shrink-0 z-10 border-b border-zinc-200/60 bg-white/70 backdrop-blur-xl dark:border-zinc-800/60 dark:bg-zinc-950/70">
          <div className="flex w-full items-center justify-between px-4 py-3 sm:px-6">
            <div className="flex items-center gap-3">
              {collapsed && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors"
                  onClick={() => setCollapsed(false)}
                  aria-label="展开侧边栏"
                  title="展开侧边栏 (⌘B)"
                >
                  <PanelLeftOpen className="size-4" />
                </Button>
              )}
              <div className="flex items-center gap-2">
                <span className="relative flex size-2.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                  <span className="relative inline-flex size-2.5 rounded-full bg-emerald-500" />
                </span>
                <h1 className="font-heading text-sm font-bold tracking-tight text-zinc-800 dark:text-zinc-100">
                  Spring AI Copilot
                </h1>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <ThemeToggle />
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => setSearchOpen(true)}
                className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 transition-colors"
                aria-label="搜索历史消息 (⌘K)"
                title="搜索历史消息 (⌘K)"
              >
                <Search className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => setShowExport(true)}
                disabled={messages.length === 0}
                className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 transition-colors"
                aria-label="导出对话"
                title="导出对话"
              >
                <Download className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setConfirmClear(true)}
                disabled={isStreaming || messages.length === 0}
                className="gap-1.5 text-xs text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              >
                <RotateCcw className="size-3.5" />
                清空
              </Button>
              {confirmClear && (
                <div className="flex items-center gap-1.5 rounded-md border border-zinc-200 bg-white px-2 py-1 text-xs shadow-sm dark:border-zinc-700 dark:bg-zinc-900">
                  <span className="text-zinc-500 dark:text-zinc-400">
                    确认清空？
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setConfirmClear(false);
                      handleReset();
                    }}
                    className="h-6 px-2 text-rose-600 hover:text-rose-700 dark:text-rose-400"
                  >
                    确认清空
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setConfirmClear(false)}
                    className="h-6 px-2"
                  >
                    取消
                  </Button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* 错误提示卡片 */}
        {hasError && (
          <div className="mx-auto mt-4 w-full max-w-3xl shrink-0 px-4 sm:px-6">
            <div className="flex items-start gap-2.5 rounded-2xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-600 dark:text-rose-400 shadow-sm backdrop-blur-md">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <span>
                服务连接受阻：{error?.message ?? "后端未能即时响应"}。请确保后端
                Spring AI 服务已正常启动。
              </span>
            </div>
          </div>
        )}

        {/* 删除会话二次确认（破坏性操作保护） */}
        {deleteTarget && (
          <div className="fixed inset-0 z-60 flex items-center justify-center bg-zinc-950/60 px-4 backdrop-blur-sm">
            <div
              role="alertdialog"
              aria-modal="true"
              aria-labelledby="delete-dialog-title"
              className="w-full max-w-sm rounded-2xl border border-zinc-200 bg-white p-5 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900"
            >
              <h2
                id="delete-dialog-title"
                className="text-sm font-semibold text-zinc-900 dark:text-zinc-100"
              >
                删除会话
              </h2>
              <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
                确定删除该会话吗？此操作不可撤销，会话内的全部消息将被永久删除。
              </p>
              <div className="mt-4 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setDeleteTarget(null)}
                >
                  取消
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => {
                    const id = deleteTarget;
                    setDeleteTarget(null);
                    deleteSession(id);
                  }}
                >
                  确认删除
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* 主消息列表区 */}
        <main
          className="flex min-h-0 flex-1 flex-col overflow-y-auto scroll-smooth scrollbar-hidden"
          aria-live="polite"
        >
          {messages.length === 0 ? (
            <EmptyState onPickPrompt={handlePickPrompt} />
          ) : (
            <div className="mx-auto w-full max-w-3xl py-4">
              {messages.map((m, index) => {
                const isLive = m.id === liveIdRef.current && isStreaming;
                if (isLive) {
                  return (
                    <LiveMessageBubble
                      key={m.id}
                      message={m}
                      streamStore={streamStore}
                      conversationId={activeId || undefined}
                    />
                  );
                }
                const isLastAssistant =
                  m.role === "assistant" && index === messages.length - 1;
                return (
                  <MessageBubble
                    key={m.id}
                    message={m}
                    conversationId={activeId || undefined}
                    onRegenerate={
                      isLastAssistant ? handleRegenerate : undefined
                    }
                  />
                );
              })}
              <div ref={bottomRef} className="h-6" />
            </div>
          )}
        </main>

        {/* 底部悬浮发光输入框 */}
        <div className="sticky bottom-0 z-10 bg-gradient-to-t from-zinc-50 via-zinc-50/90 to-transparent pb-4 pt-2 dark:from-zinc-950 dark:via-zinc-950/90 px-4 sm:px-6">
          <form
            onSubmit={handleSubmit}
            className="mx-auto flex w-full max-w-3xl flex-col gap-2 rounded-2xl border border-zinc-200/80 bg-white/90 p-3 shadow-2xl shadow-indigo-500/10 backdrop-blur-xl transition-all duration-200 focus-within:border-indigo-500/60 focus-within:ring-2 focus-within:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/90 dark:shadow-none"
          >
            {/* 隐藏的原生文件上传 Input */}
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept="image/jpeg,image/png,image/webp,image/gif,text/*,.txt,.md,.json,.js,.ts,.tsx,.java,.py,.go,.rs"
              multiple
              className="hidden"
            />

            {/* 视觉快捷场景胶囊栏：在有图片附件或多模态模式下动态展示 */}
            {attachments.some((att) => att.type === "image") && (
              <div className="px-2 pt-1.5 pb-1 border-b border-zinc-100 dark:border-zinc-800/60 bg-zinc-50/40 dark:bg-zinc-900/40 rounded-t-xl">
                <VisionScenarioPills
                  onSelect={(prompt) => {
                    setInput(prompt);
                    textareaRef.current?.focus();
                  }}
                />
              </div>
            )}

            {/* 待发送附件预览栏 */}
            {attachments.length > 0 && (
              <div className="flex flex-wrap gap-2 px-1 pt-1 pb-1.5 border-b border-zinc-100 dark:border-zinc-800/60">
                {attachments.map((att) => (
                  <div
                    key={att.id}
                    className="group relative flex items-center gap-2 rounded-xl border border-zinc-200/80 bg-zinc-50/80 p-1.5 dark:border-zinc-800/80 dark:bg-zinc-800/60 text-xs shadow-xs"
                  >
                    {att.type === "image" ? (
                      <div className="relative size-9 overflow-hidden rounded-lg">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={att.url}
                          alt={att.name}
                          className="size-full object-cover"
                        />
                      </div>
                    ) : (
                      <FileText className="size-4 text-indigo-500 shrink-0" />
                    )}
                    <span className="max-w-[120px] truncate font-medium text-zinc-700 dark:text-zinc-300">
                      {att.name}
                    </span>
                    {att.size && (
                      <span className="text-[10px] text-zinc-400 font-mono">
                        {(att.size / 1024).toFixed(0)}KB
                      </span>
                    )}
                    <button
                      type="button"
                      onClick={() => removeAttachment(att.id)}
                      className="flex size-4.5 items-center justify-center rounded-full bg-zinc-200/80 text-zinc-500 hover:bg-rose-500 hover:text-white dark:bg-zinc-700 dark:text-zinc-400 dark:hover:bg-rose-600 transition-colors"
                      title="移除附件"
                    >
                      <X className="size-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="flex items-end gap-2">
              <div className="flex h-9 shrink-0 items-center rounded-xl border border-zinc-200/80 bg-zinc-50/60 px-2.5 transition-colors dark:border-zinc-800/80 dark:bg-zinc-800/50">
                <Switch
                  checked={agentEnabled}
                  onCheckedChange={setAgentEnabled}
                  label="Agent"
                  badge="Agent"
                  id="agent-mode-switch"
                />
              </div>
              <textarea
                ref={textareaRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                onPaste={handlePaste}
                rows={1}
                placeholder={
                  currentSupportsVision
                    ? "给 Spring AI 发送指令、问答或拖拽/粘贴图片..."
                    : "给 Spring AI 发送指令、问答或文本文件..."
                }
                className="max-h-36 min-h-9 flex-1 resize-none bg-transparent px-2 py-1 text-sm text-zinc-900 caret-indigo-500 outline-none placeholder:text-zinc-400 dark:text-zinc-100 dark:caret-indigo-400 dark:placeholder:text-zinc-500 leading-relaxed"
              />
              {isStreaming ? (
                <Button
                  type="button"
                  variant="destructive"
                  size="icon"
                  onClick={stop}
                  aria-label="停止生成"
                  className="rounded-xl shadow-sm"
                >
                  <Square className="size-4" />
                </Button>
              ) : (
                <button
                  type="submit"
                  disabled={
                    (!input.trim() && attachments.length === 0) ||
                    recorder.recording
                  }
                  aria-label="发送"
                  className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-md shadow-indigo-500/25 transition-all duration-200 hover:scale-105 hover:shadow-indigo-500/40 disabled:opacity-40 disabled:hover:scale-100 disabled:shadow-none"
                >
                  <Send className="size-4" />
                </button>
              )}
            </div>

            {/* 底部工具栏 */}
            <div className="flex items-center justify-between border-t border-zinc-100 px-1 pt-2 dark:border-zinc-800/60">
              <div className="flex items-center gap-2">
                <VoiceRecorderButton
                  recording={recorder.recording}
                  seconds={recorder.seconds}
                  disabled={recorder.unsupported || isStreaming}
                  onStart={() => void recorder.start()}
                  onStop={() => void handleVoiceStop()}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  onClick={() => fileInputRef.current?.click()}
                  className={cn(
                    "text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 transition-colors",
                    !currentSupportsVision && "opacity-80",
                  )}
                  aria-label="添加文件"
                  title={
                    currentSupportsVision
                      ? "上传图片或代码/文本文件"
                      : "当前模型不支持图片处理，仅可上传代码/文本文件"
                  }
                >
                  <Paperclip className="size-4" />
                </Button>
                <Button
                  type="button"
                  variant={imageMode ? "default" : "ghost"}
                  size="icon-sm"
                  onClick={() => setImageMode((prev) => !prev)}
                  className={cn(
                    "transition-colors rounded-lg",
                    imageMode
                      ? "bg-purple-600 text-white hover:bg-purple-700 dark:bg-purple-600 dark:text-white"
                      : "text-zinc-400 hover:text-purple-600 dark:hover:text-purple-400",
                  )}
                  aria-label="生成图片模式"
                  title={
                    imageMode
                      ? "生成图片模式已开启 (提示词将触发 AI 绘图)"
                      : "切换为生成图片模式"
                  }
                >
                  <Sparkles className="size-4" />
                </Button>
                <ModelSelector
                  value={model}
                  onChange={setModel}
                  onCatalogChange={setCatalog}
                />
              </div>
              <span className="select-none font-mono text-[11px] text-zinc-400 dark:text-zinc-500">
                Enter 发送 / Shift+Enter 换行
              </span>
            </div>
          </form>
        </div>
      </div>

      {/* 导出对话弹窗 */}
      <ExportDialog
        open={showExport}
        messages={messages}
        title={
          sessions.find((s) => s.id === activeId)?.title ?? "AI-Copilot-对话"
        }
        onClose={() => setShowExport(false)}
      />
      {/* 历史消息全文检索弹窗 */}
      <SearchDialog
        open={searchOpen}
        onOpenChange={setSearchOpen}
        sessions={sessions}
        onSelectResult={(sessionId, messageId) =>
          selectSession(sessionId, messageId)
        }
      />
    </div>
  );
}

/** 衍生会话标题 */
function deriveTitle(text: string): string {
  const firstLine = text.trim().split("\n")[0].trim();
  if (!firstLine) return "新会话";
  return firstLine.length > 18 ? `${firstLine.slice(0, 18)}…` : firstLine;
}

/** 沉浸式欢迎页与场景推荐卡片 */
function EmptyState({
  onPickPrompt,
}: {
  onPickPrompt: (text: string) => void;
}) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-8 px-6 py-12 text-center">
      {/* 极光 Header Icon */}
      <div className="relative">
        <div className="absolute -inset-1 rounded-3xl bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 opacity-30 blur-lg animate-pulse" />
        <div className="relative flex size-16 items-center justify-center rounded-2xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 text-white shadow-xl shadow-indigo-500/25">
          <Sparkles className="size-8" />
        </div>
      </div>

      <div className="max-w-md space-y-2">
        <h2 className="font-heading text-2xl font-bold tracking-tight bg-gradient-to-r from-zinc-900 via-zinc-700 to-zinc-900 bg-clip-text text-transparent dark:from-white dark:via-zinc-200 dark:to-white">
          今天想与 AI 创造什么？
        </h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
          基于 Spring AI
          企业级核心架构，支持高并发流式计算、代码实时构建与多维度推理。
        </p>
      </div>

      {/* 预设场景 Prompt 推荐卡片 */}
      <div className="grid w-full max-w-2xl grid-cols-1 sm:grid-cols-2 gap-3.5">
        {SUGGESTED_PROMPTS.map((p) => {
          const Icon = p.icon;
          return (
            <button
              key={p.text}
              type="button"
              onClick={() => onPickPrompt(p.text)}
              className="group flex flex-col items-start justify-between rounded-2xl border border-zinc-200/80 bg-white/80 p-4 text-left shadow-xs backdrop-blur-md transition-all duration-200 hover:border-indigo-500/40 hover:bg-white hover:shadow-lg hover:shadow-indigo-500/5 dark:border-zinc-800/80 dark:bg-zinc-900/60 dark:hover:border-indigo-500/50 dark:hover:bg-zinc-900"
            >
              <div className="flex w-full items-center justify-between gap-2">
                <span
                  className={`flex size-8 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-xs ${p.gradient}`}
                >
                  <Icon className="size-4" />
                </span>
                <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-[10px] font-semibold text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
                  {p.category}
                </span>
              </div>
              <p className="mt-3 text-xs font-medium text-zinc-800 dark:text-zinc-200 leading-relaxed group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                {p.text}
              </p>
            </button>
          );
        })}
      </div>
    </div>
  );
}
