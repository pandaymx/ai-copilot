"use client";

import {
  AlertTriangle,
  ChevronDown,
  Paperclip,
  PanelLeftOpen,
  RotateCcw,
  Send,
  Sparkles,
  Square,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  type ChatMessage,
  MessageBubble,
} from "@/components/chat/message-bubble";
import { type ChatSession, Sidebar } from "@/components/chat/sidebar";
import { Button } from "@/components/ui/button";
import { useSpringAiStream } from "@/hooks/useSpringAiStream";

const STORAGE_KEY = "ai-copilot-sessions";
const ACTIVE_KEY = "ai-copilot-active";

let idCounter = 0;
const nextId = () => `msg-${Date.now()}-${++idCounter}`;

type ModelId = "spring-ai" | "gpt" | "claude";

const MODELS: { id: ModelId; label: string }[] = [
  { id: "spring-ai", label: "Spring AI" },
  { id: "gpt", label: "GPT-4o" },
  { id: "claude", label: "Claude 3.5" },
];

const SUGGESTED_PROMPTS: { icon: string; text: string }[] = [
  {
    icon: "💡",
    text: "用 Spring Boot 4.x 写一个 WebFlux SSE 控制器",
  },
  {
    icon: "🚀",
    text: "解释 Java 25 Virtual Threads 与协程的区别",
  },
  {
    icon: "🛠️",
    text: "编写一个 React + TypeScript 流式 Hook",
  },
];

/** 从 localStorage 读取会话列表（首次进入提供空态）。 */
function loadSessions(): ChatSession[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as ChatSession[]) : [];
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
  const [model, setModel] = useState<ModelId>("spring-ai");

  /** 预设 Prompt 卡片点击：直接发送。 */
  const handlePickPrompt = (text: string) => {
    setInput(text);
    if (!isStreaming) handleSend(text);
  };

  const isStreaming = loading;
  const hasError = Boolean(error);

  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  // 记录当前会话中"正在生成"的占位消息 id，便于把流式 content 写回。
  const liveIdRef = useRef<string | null>(null);

  const createSession = useCallback(() => {
    const id = `sess-${Date.now()}`;
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

  // 初始化：恢复会话或新建首个会话。
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

  // 会话列表（含消息历史）持久化。
  useEffect(() => {
    if (sessions.length > 0) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions));
    }
  }, [sessions]);

  // 自动滚动到底部（AI 逐字输出时钉住）。
  // biome-ignore lint/correctness/useExhaustiveDependencies: 依赖用于触发滚动副作用。
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, content]);

  // 流式内容写回当前会话的最后一条（live）助手消息，并同步持久化。
  // biome-ignore lint/correctness/useExhaustiveDependencies: 依赖 content/loading 触发写回。
  useEffect(() => {
    if (liveIdRef.current) {
      setMessages((prev) => {
        const next = prev.map((m) =>
          m.id === liveIdRef.current ? { ...m, content } : m,
        );
        setSessions((list) =>
          list.map((s) =>
            s.id === activeId
              ? { ...s, messages: next, updatedAt: Date.now() }
              : s,
          ),
        );
        return next;
      });
    }
    if (!isStreaming && liveIdRef.current) {
      liveIdRef.current = null;
      // 流结束：用首句生成会话标题。
      setSessions((prev) =>
        prev.map((s) =>
          s.id === activeId && s.title === "新会话"
            ? { ...s, title: deriveTitle(content), updatedAt: Date.now() }
            : s.id === activeId
              ? { ...s, updatedAt: Date.now() }
              : s,
        ),
      );
    }
  }, [content, isStreaming, activeId]);

  // 自适应文本框高度。
  // biome-ignore lint/correctness/useExhaustiveDependencies: 依赖 input 以重算高度。
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
      if (id === activeId) {
        if (next.length > 0) {
          setActiveId(next[0].id);
          setMessages(next[0].messages ?? []);
          localStorage.setItem(ACTIVE_KEY, next[0].id);
        } else {
          setActiveId(null);
          setMessages([]);
          localStorage.removeItem(ACTIVE_KEY);
        }
      }
      if (next.length === 0) createSession();
      return next;
    });
  }

  const handleSend = (textOverride?: string) => {
    const text = (textOverride ?? input).trim();
    if (!text || isStreaming) return;
    const liveId = "assistant-live";
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
    send(text);
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
    <div className="relative flex h-dvh overflow-hidden bg-zinc-50 dark:bg-zinc-950">
      <Sidebar
        sessions={sessions}
        activeId={activeId}
        collapsed={collapsed}
        onSelect={selectSession}
        onNew={createSession}
        onDelete={deleteSession}
        onToggleCollapsed={() => setCollapsed((c) => !c)}
      />

      {/* 移动端：展开侧边栏时的遮罩 */}
      {!collapsed && (
        <button
          type="button"
          className="fixed inset-0 z-20 bg-black/40 md:hidden"
          onClick={() => setCollapsed(true)}
          aria-label="关闭侧边栏"
        />
      )}

      <div className="flex min-w-0 flex-1 flex-col bg-background">
        {/* 顶栏 */}
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-border bg-background/80 px-4 py-3 backdrop-blur sm:px-6">
          <div className="flex items-center gap-2">
            {collapsed && (
              <Button
                variant="ghost"
                size="icon"
                className="md:hidden"
                onClick={() => setCollapsed(false)}
                aria-label="打开侧边栏"
              >
                <PanelLeftOpen className="size-4" />
              </Button>
            )}
            <span className="size-2 rounded-full bg-emerald-500" />
            <h1 className="font-heading text-base font-semibold">AI Copilot</h1>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleReset}
            disabled={isStreaming || messages.length === 0}
          >
            <RotateCcw className="size-4" />
            清空
          </Button>
        </header>

        {/* 错误提示（居中卡片） */}
        {hasError && (
          <div className="mx-auto mt-3 w-full max-w-3xl px-4 sm:px-6">
            <div className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <span>
                连接失败：{error?.message ?? "未知错误"}。请检查 Spring AI
                后端服务 是否可用后重试。
              </span>
            </div>
          </div>
        )}

        {/* 消息列表（居中，最大宽度限制） */}
        <main
          className="flex flex-1 flex-col overflow-y-auto scroll-smooth scrollbar-hidden"
          aria-live="polite"
        >
          {messages.length === 0 ? (
            <EmptyState onPickPrompt={handlePickPrompt} />
          ) : (
            <>
              {messages.map((m) => (
                <MessageBubble
                  key={m.id}
                  message={m}
                  streaming={m.id === "assistant-live" && isStreaming}
                />
              ))}
              <div ref={bottomRef} className="h-6" />
            </>
          )}
        </main>

        {/* 输入区：相对对话区居中、悬浮吸底、自动撑高的卡片 */}
        <div className="sticky bottom-0 z-10 bg-linear-to-t from-background via-background/95 to-transparent px-4 pb-3 pt-3 sm:px-6">
          <form
            onSubmit={handleSubmit}
            className="mx-auto flex w-full max-w-3xl flex-col gap-1.5 rounded-2xl border border-zinc-200/80 bg-white p-2.5 shadow-lg shadow-zinc-200/50 backdrop-blur transition-shadow focus-within:border-emerald-500 focus-within:ring-2 focus-within:ring-emerald-500/20 dark:border-zinc-800/80 dark:bg-zinc-950 dark:shadow-none"
          >
            <div className="flex items-end gap-2">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={1}
                placeholder="给 AI Copilot 发消息…"
                className="max-h-32 min-h-9 flex-1 resize-none bg-transparent px-1.5 py-2 text-sm text-zinc-900 caret-emerald-500 outline-none placeholder:text-zinc-400 dark:text-zinc-100 dark:caret-emerald-400 dark:placeholder:text-zinc-500"
              />
              {isStreaming ? (
                <Button
                  type="button"
                  variant="destructive"
                  size="icon"
                  onClick={stop}
                  aria-label="停止生成"
                >
                  <Square className="size-4" />
                </Button>
              ) : (
                <Button
                  type="submit"
                  size="icon"
                  disabled={!input.trim()}
                  aria-label="发送"
                >
                  <Send className="size-4" />
                </Button>
              )}
            </div>

            {/* 工具栏：附件 / 模型选择 / 快捷键提示 */}
            <div className="flex items-center justify-between px-1 pt-0.5">
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="text-muted-foreground"
                  aria-label="上传附件"
                >
                  <Paperclip className="size-4" />
                </Button>
                <ModelSelect value={model} onChange={setModel} />
              </div>
              <span className="select-none text-[11px] text-muted-foreground/70">
                ⌘ + Enter 发送
              </span>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

/** 用首句（最多 18 字）作为会话标题。 */
function deriveTitle(text: string): string {
  const firstLine = text.trim().split("\n")[0].trim();
  if (!firstLine) return "新会话";
  return firstLine.length > 18 ? `${firstLine.slice(0, 18)}…` : firstLine;
}

function EmptyState({ onPickPrompt }: { onPickPrompt: (text: string) => void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-6 px-6 text-center">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-emerald-600/10 text-emerald-600">
        <Send className="size-5" />
      </div>
      <div className="space-y-1.5">
        <p className="font-heading text-lg font-semibold text-foreground">
          开始与 AI Copilot 对话
        </p>
        <p className="max-w-sm text-sm text-muted-foreground">
          基于 Spring AI 流式响应，支持 Markdown 渲染、代码高亮与实时增量输出。
        </p>
      </div>

      {/* 预设 Prompt 推荐卡片 */}
      <div className="grid w-full max-w-xl grid-cols-2 gap-3">
        {SUGGESTED_PROMPTS.map((p) => (
          <button
            key={p.text}
            type="button"
            onClick={() => onPickPrompt(p.text)}
            className="group flex items-center gap-2.5 rounded-xl border border-zinc-200/80 bg-white px-3 py-2.5 text-left text-sm text-zinc-900 shadow-lg shadow-zinc-200/50 transition-all hover:border-emerald-500/50 hover:bg-emerald-500/5 hover:shadow-md dark:border-zinc-800/80 dark:bg-zinc-950 dark:text-zinc-100 dark:shadow-none"
          >
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-base">
              {p.icon}
            </span>
            <span className="min-w-0 flex-1 truncate">{p.text}</span>
            <Sparkles className="size-4 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          </button>
        ))}
      </div>
    </div>
  );
}

/** 模型选择器（轻量下拉，基于原生 select 样式化）。 */
function ModelSelect({
  value,
  onChange,
}: {
  value: ModelId;
  onChange: (id: ModelId) => void;
}) {
  const label = MODELS.find((m) => m.id === value)?.label ?? "模型";
  return (
    <div className="relative">
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as ModelId)}
        aria-label="选择模型"
        className="h-8 cursor-pointer appearance-none rounded-lg border border-zinc-200/80 bg-transparent pl-2.5 pr-7 text-xs text-muted-foreground outline-none transition-colors hover:border-emerald-500/50 hover:text-foreground focus-visible:border-ring dark:border-zinc-700/60"
      >
        {MODELS.map((m) => (
          <option key={m.id} value={m.id} className="bg-background text-foreground">
            {m.label}
          </option>
        ))}
      </select>
      <ChevronDown className="pointer-events-none absolute right-1.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
      <span className="sr-only">{label}</span>
    </div>
  );
}
