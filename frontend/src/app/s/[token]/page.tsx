"use client";

import {
  Bot,
  Check,
  Clock,
  Copy,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Lock,
  Share2,
  Sparkles,
  User,
} from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Markdown } from "@/components/chat/markdown";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  checkShare,
  resolveShare,
  type ShareSnapshotView,
} from "@/lib/share-api";
import { cn } from "@/lib/utils";

interface SnapshotMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp?: number;
}

export default function PublicShareViewPage() {
  const params = useParams();
  const token = (params?.token as string) || "";

  const [checking, setChecking] = useState(true);
  const [requiresPassword, setRequiresPassword] = useState(false);
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [snapshot, setSnapshot] = useState<ShareSnapshotView | null>(null);
  const [resolving, setResolving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const doResolve = useCallback(
    async (pwd?: string) => {
      try {
        setResolving(true);
        setErrorMsg(null);
        const res = await resolveShare(token, pwd);
        setSnapshot(res);
        setRequiresPassword(false);
      } catch (err: unknown) {
        setErrorMsg(err instanceof Error ? err.message : "获取分享内容失败");
      } finally {
        setResolving(false);
      }
    },
    [token],
  );

  useEffect(() => {
    if (!token) return;
    let isMounted = true;
    setChecking(true);
    checkShare(token)
      .then((data) => {
        if (!isMounted) return;
        if (data.requiresPassword) {
          setRequiresPassword(true);
        } else {
          void doResolve("");
        }
      })
      .catch((err: unknown) => {
        if (!isMounted) return;
        setErrorMsg(
          err instanceof Error ? err.message : "分享链接无效或已失效",
        );
      })
      .finally(() => {
        if (isMounted) setChecking(false);
      });
    return () => {
      isMounted = false;
    };
  }, [token, doResolve]);

  const messages: SnapshotMessage[] = useMemo(() => {
    if (!snapshot?.messagesJson) return [];
    try {
      return JSON.parse(snapshot.messagesJson);
    } catch {
      return [];
    }
  }, [snapshot]);

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      toast.success("分享链接已复制");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("复制失败");
    }
  };

  if (checking) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-zinc-50 dark:bg-zinc-950 text-zinc-600 dark:text-zinc-400">
        <div className="flex items-center gap-2">
          <Loader2 className="size-5 animate-spin text-indigo-600" />
          <span className="text-sm font-medium">正在验证分享状态...</span>
        </div>
      </div>
    );
  }

  if (errorMsg && !requiresPassword) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-4 bg-zinc-50 dark:bg-zinc-950 text-center">
        <div className="w-full max-w-sm p-6 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-xl space-y-4">
          <div className="size-12 rounded-2xl bg-rose-500/10 text-rose-600 flex items-center justify-center mx-auto">
            <Lock className="size-6" />
          </div>
          <h2 className="text-base font-bold text-zinc-900 dark:text-white">
            无法访问分享
          </h2>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">{errorMsg}</p>
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 transition-colors"
          >
            <span>返回首页</span>
          </Link>
        </div>
      </div>
    );
  }

  if (requiresPassword) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-4 bg-gradient-to-br from-indigo-50/70 via-white to-violet-50/70 dark:from-zinc-950 dark:via-zinc-900 dark:to-indigo-950/40">
        <div className="w-full max-w-sm rounded-3xl bg-white/80 dark:bg-zinc-900/80 backdrop-blur-xl border border-zinc-200/80 dark:border-zinc-800/80 shadow-2xl p-6 sm:p-8 space-y-5">
          <div className="text-center space-y-1.5">
            <div className="size-10 rounded-2xl bg-amber-500/10 text-amber-600 flex items-center justify-center mx-auto mb-2">
              <KeyRound className="size-5" />
            </div>
            <h2 className="text-base font-bold text-zinc-900 dark:text-white">
              受保护的 AI 对话
            </h2>
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              发起者已为该分享设置了访问密码，请输入密码查看
            </p>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              void doResolve(password);
            }}
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <label
                htmlFor="share-password"
                className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
              >
                访问密码
              </label>
              <div className="relative">
                <Lock className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
                <input
                  id="share-password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入访问密码"
                  required
                  className="w-full pl-9 pr-10 py-2.5 rounded-xl border border-zinc-200 dark:border-zinc-700/80 bg-white/70 dark:bg-zinc-800/60 text-xs text-zinc-900 dark:text-white placeholder:text-zinc-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/50"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 transition-colors"
                >
                  {showPassword ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
            </div>

            {errorMsg && (
              <div className="p-2 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-600 text-xs text-center">
                {errorMsg}
              </div>
            )}

            <button
              type="submit"
              disabled={resolving || !password.trim()}
              className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold shadow-md shadow-indigo-500/20 disabled:opacity-50 transition-all"
            >
              {resolving ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  <span>解锁中...</span>
                </>
              ) : (
                <span>解锁并查看对话</span>
              )}
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 flex flex-col">
      {/* 顶部标题栏 */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 bg-white/80 dark:bg-zinc-900/80 px-4 sm:px-6 py-3.5 backdrop-blur-md">
        <div className="flex items-center gap-3 min-w-0">
          <div className="size-8 rounded-xl bg-gradient-to-tr from-indigo-500 to-violet-600 text-white flex items-center justify-center shrink-0">
            <Share2 className="size-4" />
          </div>
          <div className="min-w-0">
            <h1 className="text-sm sm:text-base font-bold truncate">
              {snapshot?.title || "AI 对话分享"}
            </h1>
            <div className="flex items-center gap-3 text-[11px] text-zinc-400">
              <span className="flex items-center gap-1">
                <Clock className="size-3" />
                {snapshot
                  ? new Date(snapshot.createdAt).toLocaleString("zh-CN")
                  : ""}
              </span>
              <span className="flex items-center gap-1">
                <Eye className="size-3" />
                {snapshot?.viewCount || 1} 次浏览
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => void handleCopyLink()}
            className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors shadow-2xs"
          >
            {copied ? (
              <Check className="size-3.5 text-emerald-500" />
            ) : (
              <Copy className="size-3.5" />
            )}
            <span className="hidden sm:inline">复制链接</span>
          </button>
          <Link
            href="/"
            className="flex items-center gap-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 px-3.5 py-1.5 text-xs font-semibold text-white shadow-xs transition-colors"
          >
            <Sparkles className="size-3.5" />
            <span>开启新对话</span>
          </Link>
        </div>
      </header>

      {/* 消息对话列表 */}
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 sm:p-6 space-y-5">
        {messages.map((m) => {
          const isUser = m.role === "user";
          return (
            <div
              key={m.id}
              className={cn(
                "flex gap-3 items-start",
                isUser ? "flex-row-reverse" : "flex-row",
              )}
            >
              <Avatar size="sm" className="shrink-0 ring-2 ring-indigo-500/20">
                <AvatarFallback
                  className={cn(
                    "text-xs font-semibold",
                    isUser
                      ? "bg-zinc-800 text-white"
                      : "bg-gradient-to-tr from-indigo-600 to-violet-600 text-white",
                  )}
                >
                  {isUser ? (
                    <User className="size-3.5" />
                  ) : (
                    <Bot className="size-3.5" />
                  )}
                </AvatarFallback>
              </Avatar>

              <div
                className={cn(
                  "max-w-[85%] rounded-2xl p-4 text-xs leading-relaxed space-y-2 shadow-2xs",
                  isUser
                    ? "bg-indigo-600 text-white rounded-tr-none"
                    : "bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 text-zinc-900 dark:text-zinc-100 rounded-tl-none",
                )}
              >
                {isUser ? (
                  <p className="whitespace-pre-wrap">{m.content}</p>
                ) : (
                  <Markdown content={m.content} />
                )}
              </div>
            </div>
          );
        })}

        {/* 底部只读水印 */}
        <div className="pt-8 pb-4 text-center text-xs text-zinc-400 space-y-1">
          <div className="inline-flex items-center gap-1.5 font-medium text-zinc-500 dark:text-zinc-400">
            <Sparkles className="size-3.5 text-indigo-500" />
            <span>由 AI Copilot 智能企业助手生成并在线托管</span>
          </div>
          <p className="text-[10px] text-zinc-400">
            该页面为只读快照视图，数据已安全加密存档
          </p>
        </div>
      </main>
    </div>
  );
}
