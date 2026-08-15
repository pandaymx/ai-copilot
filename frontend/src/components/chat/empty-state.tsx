"use client";

import { Code2, Cpu, Layers, Sparkles, Wand2 } from "lucide-react";

export const SUGGESTED_PROMPTS = [
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

interface EmptyStateProps {
  onPickPrompt: (text: string) => void;
}

/** 沉浸式欢迎页与场景推荐卡片 */
export function EmptyState({ onPickPrompt }: EmptyStateProps) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-8 px-6 py-12 text-center">
      {/* 极光 Header Icon */}
      <div className="relative">
        <div className="absolute -inset-1 rounded-3xl bg-linear-to-r from-indigo-500 via-purple-500 to-pink-500 opacity-30 blur-lg animate-pulse" />
        <div className="relative flex size-16 items-center justify-center rounded-2xl bg-linear-to-tr from-indigo-600 via-purple-600 to-pink-500 text-white shadow-xl shadow-indigo-500/25">
          <Sparkles className="size-8" />
        </div>
      </div>

      <div className="max-w-md space-y-2">
        <h2 className="font-heading text-2xl font-bold tracking-tight bg-linear-to-r from-zinc-900 via-zinc-700 to-zinc-900 bg-clip-text text-transparent dark:from-white dark:via-zinc-200 dark:to-white">
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
                  className={`flex size-8 items-center justify-center rounded-xl bg-linear-to-br text-white shadow-xs ${p.gradient}`}
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
