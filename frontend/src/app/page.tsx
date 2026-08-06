"use client";

import {
  AlertTriangle,
  Code2,
  Cpu,
  Layers,
  PanelLeftOpen,
  Paperclip,
  RotateCcw,
  Send,
  Sparkles,
  Square,
  Wand2,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  type ChatMessage,
  MessageBubble,
} from "@/components/chat/message-bubble";
import { type SelectedModel, ModelSelector } from "@/components/chat/model-selector";
import { type ChatSession, Sidebar } from "@/components/chat/sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { useSpringAiStream } from "@/hooks/useSpringAiStream";

const STORAGE_KEY = "ai-copilot-sessions";
const ACTIVE_KEY = "ai-copilot-active";

let idCounter = 0;
const nextId = () => `msg-${Date.now()}-${++idCounter}`;

let sessionIdCounter = 0;
const nextSessionId = () =>
  `sess-${Date.now()}-${++sessionIdCounter}-${Math.random().toString(36).substring(2, 7)}`;

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

/** 从 localStorage 读取会话列表 */
function loadSessions(): ChatSession[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as ChatSession[];
    const seen = new Set<string>();
    const sanitized: ChatSession[] = [];
    for (const item of parsed) {
      if (!item.id || seen.has(item.id)) {
        item.id = nextSessionId();
      }
      seen.add(item.id);
      if (Array.isArray(item.messages)) {
        const msgSeen = new Set<string>();
        item.messages = item.messages.map((m) => {
          if (!m.id || m.id === "assistant-live" || msgSeen.has(m.id)) {
            m.id = nextId();
          }
          msgSeen.add(m.id);
          return m;
        });
      }
      sanitized.push(item);
    }
    return sanitized;
  } catch {
    return [];
  }
}

export default function Home() {
  const { content, loading, error, send, stop } = useSpringAiStream({
    endpoint: "/api/chat/stream",
  });

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [collapsed, setCollapsed] = useState(false);
  const [model, setModel] = useState<SelectedModel>({
    provider: "deepseek",
    model: "deepseek-chat",
  });

  const isStreaming = loading;
  const hasError = Boolean(error);

  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const liveIdRef = useRef<string | null>(null);

  const handlePickPrompt = (text: string) => {
    setInput(text);
    if (!isStreaming) handleSend(text);
  };

  const createSession = useCallback(() => {
    const id = nextSessionId();
    const session: ChatSession = {
      id,
      title: "新会话",
      updatedAt: Date.now(),
      messages: [],
    };
    setSessions((prev) => [session, ...prev]);
    setActiveId(id);
    setMessages([]);
    localStorage.setItem(ACTIVE_KEY, id);
  }, []);

  // 初始化：恢复会话或新建首个会话
  useEffect(() => {
    const restored = loadSessions();
    const activeRaw =
      typeof window !== "undefined" ? localStorage.getItem(ACTIVE_KEY) : null;
    if (restored.length > 0) {
      setSessions(restored);
      const aid = activeRaw ?? restored[0].id;
      setActiveId(aid);
      const active = restored.find((s) => s.id === aid);
      if (active) setMessages(active.messages ?? []);
    } else {
      createSession();
    }
  }, [createSession]);

  // 会话持久化
  useEffect(() => {
    if (sessions.length > 0) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions));
    }
  }, [sessions]);

  // 自动滚动到底部
  // biome-ignore lint/correctness/useExhaustiveDependencies: 副作用触发滚动
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, content]);

  // 流式内容实时写回 DOM
  useEffect(() => {
    if (!liveIdRef.current) return;
    const liveId = liveIdRef.current;
    setMessages((prev) =>
      prev.map((m) => (m.id === liveId ? { ...m, content } : m)),
    );
  }, [content]);

  // 流式完成后持久化同步会话列表
  useEffect(() => {
    if (!isStreaming && liveIdRef.current) {
      const liveId = liveIdRef.current;
      liveIdRef.current = null;
      setSessions((prev) =>
        prev.map((s) => {
          if (s.id !== activeId) return s;
          const updatedMessages = (s.messages ?? []).map((m) =>
            m.id === liveId ? { ...m, content } : m,
          );
          const newTitle =
            s.title === "新会话" ? deriveTitle(content) : s.title;
          return {
            ...s,
            title: newTitle,
            messages: updatedMessages,
            updatedAt: Date.now(),
          };
        }),
      );
    }
  }, [isStreaming, content, activeId]);

  // 自适应文本框高度
  // biome-ignore lint/correctness/useExhaustiveDependencies: 高度随 input 重新计算
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  }, [input]);

  function selectSession(id: string) {
    if (id === activeId) {
      setCollapsed(true);
      return;
    }
    setActiveId(id);
    const target = sessions.find((s) => s.id === id);
    setMessages(target?.messages ?? []);
    liveIdRef.current = null;
    stop();
    localStorage.setItem(ACTIVE_KEY, id);
  }

  function deleteSession(id: string) {
    setSessions((prev) => {
      const next = prev.filter((s) => s.id !== id);
      if (next.length === 0) {
        const newSession: ChatSession = {
          id: nextSessionId(),
          title: "新会话",
          updatedAt: Date.now(),
          messages: [],
        };
        setActiveId(newSession.id);
        setMessages([]);
        localStorage.setItem(ACTIVE_KEY, newSession.id);
        return [newSession];
      }
      if (id === activeId) {
        setActiveId(next[0].id);
        setMessages(next[0].messages ?? []);
        localStorage.setItem(ACTIVE_KEY, next[0].id);
      }
      return next;
    });
  }

  function renameSession(id: string, newTitle: string) {
    setSessions((prev) =>
      prev.map((s) => (s.id === id ? { ...s, title: newTitle } : s)),
    );
  }

  const handleSend = (textOverride?: string) => {
    const text = (textOverride ?? input).trim();
    if (!text || isStreaming) return;
    const liveId = nextId();
    liveIdRef.current = liveId;
    const next: ChatMessage[] = [
      ...messages,
      { id: nextId(), role: "user", content: text },
      { id: liveId, role: "assistant", content: "" },
    ];
    setMessages(next);
    setSessions((prev) =>
      prev.map((s) =>
        s.id === activeId ? { ...s, messages: next, updatedAt: Date.now() } : s,
      ),
    );
    setInput("");
    send(text, { provider: model.provider, model: model.model });
  };

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
    stop();
    liveIdRef.current = null;
    setMessages([]);
    setInput("");
  };

  return (
    <div className="relative flex h-dvh overflow-hidden bg-ambient-mesh bg-zinc-50 dark:bg-zinc-950">
      <Sidebar
        sessions={sessions}
        activeId={activeId}
        collapsed={collapsed}
        onSelect={selectSession}
        onNew={createSession}
        onDelete={deleteSession}
        onRename={renameSession}
        onToggleCollapsed={() => setCollapsed((c) => !c)}
      />

      {/* 移动端遮罩 */}
      {!collapsed && (
        <button
          type="button"
          className="fixed inset-0 z-20 bg-black/40 backdrop-blur-xs md:hidden"
          onClick={() => setCollapsed(true)}
          aria-label="关闭侧边栏"
        />
      )}

      <div className="flex min-w-0 flex-1 flex-col bg-transparent">
        {/* 顶部 Header */}
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-zinc-200/60 bg-white/70 px-4 py-3 backdrop-blur-xl dark:border-zinc-800/60 dark:bg-zinc-950/70 sm:px-6">
          <div className="flex items-center gap-3">
            {collapsed && (
              <Button
                variant="ghost"
                size="icon-sm"
                className="md:hidden text-zinc-600 dark:text-zinc-300"
                onClick={() => setCollapsed(false)}
                aria-label="打开侧边栏"
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
              size="sm"
              onClick={handleReset}
              disabled={isStreaming || messages.length === 0}
              className="gap-1.5 text-xs text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            >
              <RotateCcw className="size-3.5" />
              清空
            </Button>
          </div>
        </header>

        {/* 错误提示卡片 */}
        {hasError && (
          <div className="mx-auto mt-4 w-full max-w-3xl px-4 sm:px-6">
            <div className="flex items-start gap-2.5 rounded-2xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-600 dark:text-rose-400 shadow-sm backdrop-blur-md">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <span>
                服务连接受阻：{error?.message ?? "后端未能即时响应"}。请确保后端
                Spring AI 服务已正常启动。
              </span>
            </div>
          </div>
        )}

        {/* 主消息列表区 */}
        <main
          className="flex flex-1 flex-col overflow-y-auto scroll-smooth scrollbar-hidden"
          aria-live="polite"
        >
          {messages.length === 0 ? (
            <EmptyState onPickPrompt={handlePickPrompt} />
          ) : (
            <div className="py-4">
              {messages.map((m) => (
                <MessageBubble
                  key={m.id}
                  message={m}
                  streaming={m.id === liveIdRef.current && isStreaming}
                  onRegenerate={() =>
                    handleSend(messages[messages.length - 2]?.content)
                  }
                />
              ))}
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
            <div className="flex items-end gap-2">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={1}
                placeholder="给 Spring AI 发发送指令或问题..."
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
                  disabled={!input.trim()}
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
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200"
                  aria-label="添加文件"
                >
                  <Paperclip className="size-4" />
                </Button>
                <ModelSelector value={model} onChange={setModel} />
              </div>
              <span className="select-none font-mono text-[11px] text-zinc-400 dark:text-zinc-500">
                ⌘ + Enter 发送
              </span>
            </div>
          </form>
        </div>
      </div>
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
