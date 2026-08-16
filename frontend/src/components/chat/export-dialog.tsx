"use client";

import {
  Brain,
  Check,
  Copy,
  Download,
  ExternalLink,
  FileJson,
  FileText,
  KeyRound,
  Loader2,
  Lock,
  Share2,
  Sparkles,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import type { ChatMessage } from "@/components/chat/message-bubble";
import { Button } from "@/components/ui/button";
import {
  type ExportFormat,
  exportConversation,
} from "@/lib/export-conversation";
import { createSessionShare, type ShareMeta } from "@/lib/share-api";
import { cn } from "@/lib/utils";

interface ExportDialogProps {
  open: boolean;
  messages: ChatMessage[];
  title: string;
  sessionId?: string;
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
    color: "from-zinc-500 to-zinc-600 ring-zinc-400/30 dark:ring-zinc-500/40",
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
  sessionId = "current-session",
  onClose,
}: ExportDialogProps) {
  const [tab, setTab] = useState<"export" | "share">("export");
  const [format, setFormat] = useState<ExportFormat>("markdown");
  const [includeThinking, setIncludeThinking] = useState(true);
  const [done, setDone] = useState(false);
  const [sharePassword, setSharePassword] = useState("");
  const [sharing, setSharing] = useState(false);
  const [shareResult, setShareResult] = useState<ShareMeta | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);

  const dialogRef = useRef<HTMLDivElement>(null);
  const hasThinking = messages.some((m) => m.thinking?.trim());

  // 点击外部关闭
  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (dialogRef.current && !dialogRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
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

  // 关闭时重置状态
  useEffect(() => {
    if (!open) {
      setDone(false);
      setShareResult(null);
      setSharePassword("");
    }
  }, [open]);

  const handleExport = () => {
    exportConversation(messages, {
      format,
      includeThinking,
      title,
    });
    setDone(true);
    setTimeout(() => {
      setDone(false);
      onClose();
    }, 1200);
  };

  const handleCreateShare = async () => {
    try {
      setSharing(true);
      const snapshotMessages = messages.map((m) => ({
        id: m.id,
        role: m.role,
        content: m.content,
        timestamp: Date.now(),
      }));

      const meta = await createSessionShare(sessionId, {
        title: title || "AI 对话分享",
        messagesJson: JSON.stringify(snapshotMessages),
        password: sharePassword.trim() || undefined,
      });

      setShareResult(meta);
      toast.success("分享快照已成功创建");
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "创建分享失败");
    } finally {
      setSharing(false);
    }
  };

  const shareUrl = shareResult
    ? `${typeof window !== "undefined" ? window.location.origin : ""}/s/${shareResult.token}`
    : "";

  const handleCopyShareLink = async () => {
    if (!shareUrl) return;
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopiedLink(true);
      toast.success("分享短链接已复制到剪贴板");
      setTimeout(() => setCopiedLink(false), 2000);
    } catch {
      toast.error("复制失败");
    }
  };

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="export-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in duration-200"
    >
      <div
        ref={dialogRef}
        className="relative w-full max-w-md rounded-2xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-900 animate-in zoom-in-95 duration-200 overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-zinc-100 px-5 py-4 dark:border-zinc-800/60">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 text-white shadow-sm">
              <Share2 className="size-4" />
            </div>
            <div>
              <h3
                id="export-dialog-title"
                className="text-sm font-bold text-zinc-900 dark:text-zinc-100"
              >
                导出与在线分享
              </h3>
              <p className="text-[11px] text-zinc-400 dark:text-zinc-500">
                共 {messages.length} 条消息
              </p>
            </div>
          </div>
          <button
            type="button"
            id="export-close-btn"
            onClick={onClose}
            className="flex size-7 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-300 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="flex border-b border-zinc-100 dark:border-zinc-800/60 px-5 pt-2">
          <button
            type="button"
            onClick={() => setTab("export")}
            className={cn(
              "flex-1 pb-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center justify-center gap-1.5",
              tab === "export"
                ? "border-indigo-600 text-indigo-600 dark:text-indigo-400 dark:border-indigo-400"
                : "border-transparent text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300",
            )}
          >
            <Download className="size-3.5" />
            <span>本地文件导出</span>
          </button>
          <button
            type="button"
            onClick={() => setTab("share")}
            className={cn(
              "flex-1 pb-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center justify-center gap-1.5",
              tab === "share"
                ? "border-indigo-600 text-indigo-600 dark:text-indigo-400 dark:border-indigo-400"
                : "border-transparent text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300",
            )}
          >
            <Sparkles className="size-3.5" />
            <span>在线只读分享链接</span>
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4">
          {tab === "export" ? (
            <>
              {/* 格式选择 */}
              <div className="space-y-2">
                <p className="text-xs font-medium text-zinc-600 dark:text-zinc-400">
                  选择导出格式
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
                            "flex size-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white text-xs shadow-xs transition-all",
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
                    <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-indigo-600 text-white shadow-xs">
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
                    <div className="relative shrink-0">
                      <input
                        id="export-include-thinking"
                        type="checkbox"
                        checked={includeThinking}
                        onChange={(e) => setIncludeThinking(e.target.checked)}
                        className="sr-only"
                      />
                      <div
                        className={cn(
                          "relative h-5 w-9 cursor-pointer rounded-full transition-colors duration-200",
                          includeThinking
                            ? "bg-indigo-600 dark:bg-indigo-500"
                            : "bg-zinc-300 dark:bg-zinc-600",
                        )}
                      >
                        <span
                          className={cn(
                            "absolute top-0.5 left-0.5 size-4 rounded-full bg-white shadow-xs transition-transform duration-200",
                            includeThinking ? "translate-x-4" : "translate-x-0",
                          )}
                        />
                      </div>
                    </div>
                  </label>
                </div>
              )}
            </>
          ) : (
            <div className="space-y-4">
              {!shareResult ? (
                <>
                  <div className="p-3.5 rounded-xl bg-indigo-50/60 dark:bg-indigo-950/30 border border-indigo-200/60 dark:border-indigo-800/40 text-xs text-indigo-900 dark:text-indigo-200 space-y-1">
                    <p className="font-semibold">关于在线快照分享</p>
                    <p className="text-[11px] text-indigo-700 dark:text-indigo-300 leading-relaxed">
                      将生成当前会话全部消息的静态物化快照，免登录即可阅读，支持设置访问保护密码。
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <label
                      htmlFor="share-pwd-input"
                      className="block text-xs font-medium text-zinc-700 dark:text-zinc-300"
                    >
                      访问密码（可选）
                    </label>
                    <div className="relative">
                      <Lock className="size-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
                      <input
                        id="share-pwd-input"
                        type="text"
                        value={sharePassword}
                        onChange={(e) => setSharePassword(e.target.value)}
                        placeholder="留空则所有人可通过链接免密访问"
                        className="w-full pl-8.5 pr-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-zinc-50/60 dark:bg-zinc-800/40 text-xs text-zinc-900 dark:text-white placeholder:text-zinc-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/50"
                      />
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => void handleCreateShare()}
                    disabled={sharing}
                    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-white text-xs font-semibold shadow-md shadow-indigo-500/20 disabled:opacity-50 transition-all hover:scale-[1.01]"
                  >
                    {sharing ? (
                      <>
                        <Loader2 className="size-3.5 animate-spin" />
                        <span>正在生成快照链接...</span>
                      </>
                    ) : (
                      <>
                        <Share2 className="size-3.5" />
                        <span>生成在线分享短链接</span>
                      </>
                    )}
                  </button>
                </>
              ) : (
                <div className="space-y-3 animate-in fade-in">
                  <div className="flex items-center gap-2 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                    <Check className="size-4" />
                    <span>在线分享链接已就绪！</span>
                  </div>

                  <div className="p-3 rounded-xl bg-zinc-50 dark:bg-zinc-800/60 border border-zinc-200 dark:border-zinc-700 space-y-2">
                    <div className="text-[11px] font-mono text-indigo-600 dark:text-indigo-400 break-all select-all">
                      {shareUrl}
                    </div>
                    {shareResult.hasPassword && (
                      <div className="text-[10px] text-amber-600 dark:text-amber-400 flex items-center gap-1">
                        <KeyRound className="size-3" />
                        <span>该链接已设置访问密码</span>
                      </div>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => void handleCopyShareLink()}
                      className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition-colors"
                    >
                      {copiedLink ? (
                        <Check className="size-3.5" />
                      ) : (
                        <Copy className="size-3.5" />
                      )}
                      <span>复制链接</span>
                    </button>
                    <a
                      href={shareUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xs font-medium text-zinc-700 dark:text-zinc-300 transition-colors"
                    >
                      <ExternalLink className="size-3.5" />
                      <span>直接打开</span>
                    </a>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer for Local Export */}
        {tab === "export" && (
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
        )}
      </div>
    </div>
  );
}
