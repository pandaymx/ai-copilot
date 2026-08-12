"use client";

import {
  Bot,
  Clock,
  Loader2,
  MessageSquare,
  Search,
  User,
  X,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatSession } from "@/components/chat/sidebar";
import { type SearchResultItem, searchChatHistoryApi } from "@/lib/api";
import { cn } from "@/lib/utils";

/**
 * XSS 净化与高亮片段转换函数。
 * 建议用正则把所有 HTML 标签转义，但保留并转化 <b> 与 </b>，
 * 增强高亮单词在 UI 上的视效（配合 Tailwind 配色）。
 */
function sanitizeSnippet(snippet: string): string {
  if (!snippet) return "";
  // 1. 用占位符保护 <b> 与 </b>
  const tokenized = snippet
    .replace(/<b>/g, "___B_OPEN___")
    .replace(/<\/b>/g, "___B_CLOSE___");
  // 2. 转义常规 HTML 特殊字符，防范 XSS
  const escaped = tokenized
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
  // 3. 还原 <b> 为带精美高亮类名的 HTML
  return escaped
    .replace(
      /___B_OPEN___/g,
      '<b class="text-indigo-600 dark:text-indigo-400 font-semibold bg-indigo-500/10 dark:bg-indigo-400/20 px-1 py-0.5 rounded">',
    )
    .replace(/___B_CLOSE___/g, "</b>");
}

function formatTimestamp(ts: number): string {
  if (!ts) return "";
  const date = new Date(ts);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  return date.toLocaleDateString("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface SearchDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  sessions: ChatSession[];
  onSelectResult: (sessionId: string, messageId: number | string) => void;
}

export function SearchDialog({
  open,
  onOpenChange,
  sessions,
  onSelectResult,
}: SearchDialogProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [hasSearched, setHasSearched] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  // 打开弹窗时自动聚焦输入框（同步聚焦，去除不确定的 setTimeout 延迟）
  useEffect(() => {
    if (open) {
      inputRef.current?.focus();
    } else {
      setQuery("");
      setResults([]);
      setIsLoading(false);
      setHasSearched(false);
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    }
  }, [open]);

  // 防抖 + AbortController 竞态控制
  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setResults([]);
      setIsLoading(false);
      setHasSearched(false);
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
      return;
    }

    setIsLoading(true);
    const timer = setTimeout(() => {
      // 取消上一次未完成的请求
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      const controller = new AbortController();
      abortControllerRef.current = controller;

      searchChatHistoryApi(trimmed, 50, controller.signal)
        .then((res) => {
          if (controller.signal.aborted) return;
          setResults(res?.results ?? []);
          setSelectedIndex(0);
          setHasSearched(true);
        })
        .catch(() => {
          if (controller.signal.aborted) return;
          setResults([]);
          setHasSearched(true);
        })
        .finally(() => {
          if (!controller.signal.aborted) {
            setIsLoading(false);
          }
        });
    }, 300);

    return () => {
      clearTimeout(timer);
    };
  }, [query]);

  // 键盘快捷键监听（上下键切换选中项、Enter 选择、Esc 关闭）
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Escape") {
        onOpenChange(false);
        return;
      }
      if (results.length === 0) return;

      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % results.length);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedIndex(
          (prev) => (prev - 1 + results.length) % results.length,
        );
      } else if (e.key === "Enter") {
        e.preventDefault();
        // 焦点在结果按钮上时，交由按钮原生的 Enter/Space 触发 onClick，避免重复选中
        const active = document.activeElement as HTMLElement | null;
        if (active?.tagName === "BUTTON" && active.dataset.result) return;
        const target = results[selectedIndex];
        if (target) {
          onSelectResult(target.sessionId, target.messageId);
          onOpenChange(false);
        }
      }
    },
    [results, selectedIndex, onSelectResult, onOpenChange],
  );

  if (!open) return null;

  // 根据 sessionId 获取会话标题
  const getSessionTitle = (sessionId: string) => {
    const match = sessions.find((s) => s.id === sessionId);
    return match ? match.title : `会话 #${sessionId.slice(0, 8)}`;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-16 sm:pt-24 px-4 bg-zinc-950/60 backdrop-blur-sm animate-in fade-in duration-200">
      {/* 遮罩背景点击关闭 */}
      <div
        className="fixed inset-0"
        onClick={() => onOpenChange(false)}
        aria-hidden="true"
      />

      {/* 搜索框浮层卡片 */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label="搜索聊天记录"
        className="relative z-10 w-full max-w-2xl overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-2xl transition-all dark:border-zinc-800/80 dark:bg-zinc-900"
        onKeyDown={handleKeyDown}
      >
        {/* 顶部搜索输入 Header */}
        <div className="flex items-center px-4 py-3.5 border-b border-zinc-100 dark:border-zinc-800/80 gap-3">
          <Search className="size-5 shrink-0 text-zinc-400 dark:text-zinc-500" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索历史消息内容..."
            className="w-full bg-transparent text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none"
          />
          {isLoading && (
            <Loader2 className="size-4 shrink-0 animate-spin text-indigo-500" />
          )}
          {query && !isLoading && (
            <button
              type="button"
              onClick={() => setQuery("")}
              className="p-1 rounded-md text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
            >
              <X className="size-4" />
            </button>
          )}
          <kbd className="hidden sm:inline-flex items-center gap-1 rounded border border-zinc-200 bg-zinc-50 px-1.5 py-0.5 text-[10px] font-medium text-zinc-500 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-400">
            ESC
          </kbd>
        </div>

        {/* 结果展示区 */}
        <div className="max-h-[60vh] overflow-y-auto p-2 scrollbar-hidden">
          {query.trim() === "" ? (
            <div className="py-12 text-center text-xs text-zinc-400 dark:text-zinc-500">
              <Search className="mx-auto size-8 mb-2 stroke-1 opacity-50" />
              输入关键字搜索聊天记录
            </div>
          ) : isLoading && results.length === 0 ? (
            <div className="py-12 text-center text-xs text-zinc-400 dark:text-zinc-500">
              <Loader2 className="mx-auto size-6 mb-2 animate-spin text-indigo-500 opacity-80" />
              搜索中...
            </div>
          ) : hasSearched && results.length === 0 ? (
            <div className="py-12 text-center text-xs text-zinc-400 dark:text-zinc-500">
              未找到与关键字 &quot;{query}&quot; 相关的匹配记录
            </div>
          ) : (
            <div className="space-y-1">
              <div className="px-3 py-1.5 text-[11px] font-medium text-zinc-400 dark:text-zinc-500">
                匹配结果 ({results.length} 条)
              </div>
              {results.map((item, idx) => {
                const isSelected = idx === selectedIndex;
                const isUser = item.role.toUpperCase() === "USER";
                const sanitizedHtml = sanitizeSnippet(item.snippet);

                return (
                  <button
                    type="button"
                    data-result
                    key={`${item.sessionId}-${item.messageId}-${idx}`}
                    onClick={() => {
                      onSelectResult(item.sessionId, item.messageId);
                      onOpenChange(false);
                    }}
                    onMouseEnter={() => setSelectedIndex(idx)}
                    className={cn(
                      "group flex w-full flex-col gap-1.5 rounded-xl border border-transparent bg-transparent p-3 text-left text-xs transition-colors",
                      isSelected
                        ? "bg-indigo-50/80 border-indigo-200/80 dark:bg-indigo-950/40 dark:border-indigo-800/50"
                        : "hover:bg-zinc-100/70 dark:hover:bg-zinc-800/50",
                    )}
                  >
                    {/* 会话信息标头与角色 */}
                    <div className="flex items-center justify-between text-[11px] text-zinc-500 dark:text-zinc-400">
                      <div className="flex items-center gap-1.5 truncate max-w-[70%]">
                        <MessageSquare className="size-3.5 shrink-0 text-indigo-500" />
                        <span className="font-medium text-zinc-700 dark:text-zinc-300 truncate">
                          {getSessionTitle(item.sessionId)}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span
                          className={cn(
                            "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium",
                            isUser
                              ? "bg-blue-500/10 text-blue-600 dark:text-blue-400"
                              : "bg-purple-500/10 text-purple-600 dark:text-purple-400",
                          )}
                        >
                          {isUser ? (
                            <User className="size-2.5" />
                          ) : (
                            <Bot className="size-2.5" />
                          )}
                          {isUser ? "提问" : "回答"}
                        </span>
                        {item.timestamp > 0 && (
                          <span className="inline-flex items-center gap-1 text-[10px] text-zinc-400">
                            <Clock className="size-2.5" />
                            {formatTimestamp(item.timestamp)}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* 高亮片段 Content */}
                    <div
                      className="line-clamp-3 text-zinc-600 dark:text-zinc-300 leading-relaxed font-sans text-left"
                      // biome-ignore lint/security/noDangerouslySetInnerHtml: sanitized snippet via sanitizeSnippet
                      dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
                    />
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 底部快捷键提示 */}
        <div className="flex items-center justify-between px-4 py-2 border-t border-zinc-100 dark:border-zinc-800/80 text-[11px] text-zinc-400 dark:text-zinc-500 bg-zinc-50/50 dark:bg-zinc-900/50">
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-zinc-200 bg-white px-1 py-0.5 text-[9px] dark:border-zinc-800 dark:bg-zinc-800">
                ↑
              </kbd>
              <kbd className="rounded border border-zinc-200 bg-white px-1 py-0.5 text-[9px] dark:border-zinc-800 dark:bg-zinc-800">
                ↓
              </kbd>
              切换选中
            </span>
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-zinc-200 bg-white px-1 py-0.5 text-[9px] dark:border-zinc-800 dark:bg-zinc-800">
                ↵
              </kbd>
              跳转匹配消息
            </span>
          </div>
          <span>按 ESC 关闭</span>
        </div>
      </div>
    </div>
  );
}
