"use client";

import { AlertOctagon, Home, RefreshCw, Terminal } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";

interface ErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalError({ error, reset }: ErrorProps) {
  const [showDetails, setShowDetails] = useState(false);

  useEffect(() => {
    // 可以在此处接入 Sentry / OpenTelemetry 等全局日志监控服务
    console.error("[Global Error Boundary caught exception]:", error);
  }, [error]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 px-4 py-12 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100 transition-colors duration-200">
      {/* 极光模糊背景卡片 */}
      <div className="relative w-full max-w-lg overflow-hidden rounded-3xl border border-rose-500/20 bg-white/80 p-8 shadow-2xl backdrop-blur-xl dark:border-rose-500/30 dark:bg-zinc-900/90 sm:p-10">
        <div className="absolute -top-24 -left-24 size-48 rounded-full bg-rose-500/20 blur-3xl" />
        <div className="absolute -bottom-24 -right-24 size-48 rounded-full bg-indigo-500/20 blur-3xl" />

        <div className="relative flex flex-col items-center text-center">
          {/* Error Icon Badge */}
          <div className="mb-6 flex size-16 items-center justify-center rounded-2xl bg-rose-500/10 text-rose-600 ring-8 ring-rose-500/5 dark:bg-rose-500/20 dark:text-rose-400">
            <AlertOctagon className="size-8 animate-bounce stroke-[1.75]" />
          </div>

          {/* Error Title & Subtitle */}
          <h1 className="font-heading text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50 sm:text-3xl">
            应用遇到非预期错误
          </h1>
          <p className="mt-3 text-xs leading-relaxed text-zinc-600 dark:text-zinc-400 sm:text-sm">
            抱歉，前端组件在渲染或交互过程中抛出了未捕获异常。你可以尝试刷新或重新恢复应用程序状态。
          </p>

          {/* Digest Tag */}
          {error.digest && (
            <div className="mt-3 inline-flex items-center gap-1.5 rounded-full border border-zinc-200 bg-zinc-100 px-3 py-1 text-[11px] font-mono text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/80 dark:text-zinc-400">
              <span>Digest:</span>
              <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                {error.digest}
              </span>
            </div>
          )}

          {/* Operational Buttons */}
          <div className="mt-8 flex w-full flex-col gap-3 sm:flex-row sm:items-center sm:justify-center">
            <Button
              type="button"
              onClick={() => reset()}
              className="group relative flex h-10 min-w-[140px] items-center justify-center gap-2 overflow-hidden rounded-xl bg-gradient-to-r from-indigo-600 via-purple-600 to-rose-600 text-xs font-semibold text-white shadow-lg shadow-indigo-500/25 transition-all duration-200 hover:scale-[1.02] hover:shadow-indigo-500/40 active:scale-[0.98]"
            >
              <RefreshCw className="size-3.5 transition-transform duration-300 group-hover:rotate-180" />
              重新尝试加载
            </Button>

            <Button
              type="button"
              variant="outline"
              onClick={() => {
                window.location.href = "/";
              }}
              className="flex h-10 min-w-[120px] items-center justify-center gap-2 rounded-xl border border-zinc-200 bg-white text-xs font-semibold text-zinc-700 hover:bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800"
            >
              <Home className="size-3.5" />
              返回首页
            </Button>
          </div>

          {/* Toggle Stack Details for Developers */}
          <div className="mt-6 w-full text-left">
            <button
              type="button"
              onClick={() => setShowDetails((prev) => !prev)}
              className="inline-flex items-center gap-1.5 text-[11px] font-medium text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200 transition-colors"
            >
              <Terminal className="size-3" />
              {showDetails
                ? "隐藏错误堆栈详情"
                : "查看技术排查细节 (Stack Trace)"}
            </button>

            {showDetails && (
              <div className="mt-2 max-h-48 overflow-y-auto rounded-xl border border-zinc-200 bg-zinc-900/90 p-3 font-mono text-[11px] text-zinc-300 shadow-inner dark:border-zinc-800 scrollbar-hidden">
                <p className="font-semibold text-rose-400">
                  {error.name}: {error.message}
                </p>
                {error.stack && (
                  <pre className="mt-2 whitespace-pre-wrap text-[10px] leading-relaxed text-zinc-400">
                    {error.stack}
                  </pre>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
