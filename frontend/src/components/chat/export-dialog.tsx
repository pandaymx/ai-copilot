"use client";

import { Brain, Check, Download, FileJson, FileText, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { ChatMessage } from "@/components/chat/message-bubble";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  type ExportFormat,
  exportConversation,
} from "@/lib/export-conversation";

interface ExportDialogProps {
  open: boolean;
  messages: ChatMessage[];
  title: string;
  onClose: () => void;
}

const FORMAT_OPTIONS: {
  id: ExportFormat;
  label: string;
  desc: string;
  icon: React.ElementType;
  color: string;
}[] = [
  {
    id: "markdown",
    label: "Markdown",
    desc: ".md · 含格式标记，适合 Obsidian / Notion",
    icon: FileText,
    color:
      "from-indigo-500 to-violet-600 ring-indigo-500/30 dark:ring-indigo-500/40",
  },
  {
    id: "text",
    label: "纯文本",
    desc: ".txt · 无格式，通用兼容",
    icon: FileText,
    color:
      "from-zinc-500 to-zinc-600 ring-zinc-400/30 dark:ring-zinc-500/40",
  },
  {
    id: "json",
    label: "JSON",
    desc: ".json · 结构化数据，可二次处理",
    icon: FileJson,
    color:
      "from-amber-500 to-orange-500 ring-amber-500/30 dark:ring-amber-500/40",
  },
];

export function ExportDialog({
  open,
  messages,
  title,
  onClose,
}: ExportDialogProps) {
  const [format, setFormat] = useState<ExportFormat>("markdown");
  const [includeThinking, setIncludeThinking] = useState(true);
  const [done, setDone] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  // 检查是否有思考过程可供导出
  const hasThinking = messages.some((m) => m.thinking?.trim());

  // 点击外部关闭
  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (dialogRef.current && !dialogRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    // 延迟绑定，避免触发打开该面板的同一次点击事件
    const tid = setTimeout(
      () => document.addEventListener("mousedown", handleClick),
      50,
    );
    return () => {
      clearTimeout(tid);
      document.removeEventListener("mousedown", handleClick);
    };
  }, [open, onClose]);

  // ESC 关闭
  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, onClose]);

  // 关闭时重置 done 状态
  useEffect(() => {
    if (!open) setDone(false);
  }, [open]);

  const handleExport = () => {
    exportConversation(messages, { format, includeThinking, title });
    setDone(true);
    setTimeout(onClose, 1200);
  };

  if (!open) return null;

  return (
    /* 全屏遮罩 */
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-in fade-in duration-150">
      {/* 面板 */}
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="导出对话"
        className="w-full max-w-sm rounded-2xl border border-zinc-200/80 bg-white/95 shadow-2xl shadow-indigo-500/10 backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/95 dark:shadow-none animate-in zoom-in-95 fade-in duration-200"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-zinc-100 px-5 py-4 dark:border-zinc-800/60">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-md shadow-indigo-500/20">
              <Download className="size-4" />
            </div>
            <div>
              <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                导出对话
              </p>
              <p className="text-[11px] text-zinc-400 dark:text-zinc-500">
                {messages.length} 条消息
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="flex size-7 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Body */}
        <div className="space-y-4 p-5">
          {/* 格式选择 */}
          <div className="space-y-2">
            <p className="text-xs font-semibold text-zinc-500 uppercase tracking-wider dark:text-zinc-400">
              导出格式
            </p>
            <div className="flex flex-col gap-2">
              {FORMAT_OPTIONS.map((opt) => {
                const Icon = opt.icon;
                const selected = format === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    id={`export-format-${opt.id}`}
                    onClick={() => setFormat(opt.id)}
                    className={cn(
                      "group flex items-center gap-3 rounded-xl border px-3.5 py-2.5 text-left transition-all duration-150",
                      selected
                        ? "border-indigo-500/40 bg-indigo-50/60 ring-1 ring-indigo-500/20 dark:border-indigo-500/30 dark:bg-indigo-950/30 dark:ring-indigo-500/20"
                        : "border-zinc-200/70 bg-zinc-50/60 hover:border-zinc-300/80 hover:bg-zinc-100/60 dark:border-zinc-800/70 dark:bg-zinc-800/30 dark:hover:border-zinc-700/80",
                    )}
                  >
                    <span
                      className={cn(
                        "flex size-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white text-xs shadow-sm transition-all",
                        opt.color,
                        selected ? "ring-2" : "",
                      )}
                    >
                      <Icon className="size-3.5" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          "block text-xs font-semibold leading-tight transition-colors",
                          selected
                            ? "text-indigo-700 dark:text-indigo-300"
                            : "text-zinc-800 dark:text-zinc-200",
                        )}
                      >
                        {opt.label}
                      </span>
                      <span className="block text-[11px] text-zinc-400 dark:text-zinc-500 mt-0.5">
                        {opt.desc}
                      </span>
                    </span>
                    {selected && (
                      <Check className="size-4 shrink-0 text-indigo-600 dark:text-indigo-400" />
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 思考过程开关 */}
          {hasThinking && (
            <div className="rounded-xl border border-zinc-200/70 bg-zinc-50/60 px-3.5 py-3 dark:border-zinc-800/60 dark:bg-zinc-800/30">
              <label
                htmlFor="export-include-thinking"
                className="flex cursor-pointer items-center gap-3"
              >
                <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-indigo-600 text-white shadow-sm">
                  <Brain className="size-3.5" />
                </span>
                <span className="flex-1 min-w-0">
                  <span className="block text-xs font-semibold text-zinc-800 dark:text-zinc-200">
                    包含思考过程
                  </span>
                  <span className="block text-[11px] text-zinc-400 dark:text-zinc-500 mt-0.5">
                    将推理链一并导出（推理型模型）
                  </span>
                </span>
                {/* Toggle 开关 */}
                <div className="relative shrink-0">
                  <input
                    id="export-include-thinking"
                    type="checkbox"
                    checked={includeThinking}
                    onChange={(e) => setIncludeThinking(e.target.checked)}
                    className="sr-only"
                  />
                  <div
                    onClick={() => setIncludeThinking((v) => !v)}
                    className={cn(
                      "relative h-5 w-9 cursor-pointer rounded-full transition-colors duration-200",
                      includeThinking
                        ? "bg-indigo-600 dark:bg-indigo-500"
                        : "bg-zinc-300 dark:bg-zinc-600",
                    )}
                  >
                    <span
                      className={cn(
                        "absolute top-0.5 left-0.5 size-4 rounded-full bg-white shadow-sm transition-transform duration-200",
                        includeThinking ? "translate-x-4" : "translate-x-0",
                      )}
                    />
                  </div>
                </div>
              </label>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 border-t border-zinc-100 px-5 py-4 dark:border-zinc-800/60">
          <Button
            variant="ghost"
            size="sm"
            onClick={onClose}
            className="text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-100"
          >
            取消
          </Button>
          <button
            type="button"
            id="export-confirm-btn"
            onClick={handleExport}
            disabled={done}
            className={cn(
              "flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-md transition-all duration-200",
              done
                ? "bg-emerald-500 shadow-emerald-500/20"
                : "bg-gradient-to-r from-indigo-600 to-purple-600 shadow-indigo-500/20 hover:shadow-indigo-500/35 hover:scale-[1.02]",
            )}
          >
            {done ? (
              <>
                <Check className="size-3.5" />
                已导出
              </>
            ) : (
              <>
                <Download className="size-3.5" />
                导出
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
