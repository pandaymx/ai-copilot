"use client";

import { Brain, Check, ChevronDown, Cpu, Sparkles, Zap } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export type ModelId = "spring-ai" | "gpt-4o" | "claude-3-5" | "deepseek-r1";

export interface ModelOption {
  id: ModelId;
  name: string;
  badge: string;
  description: string;
  icon: typeof Sparkles;
  speed: "极速" | "深度推理" | "代码专家";
  accent: string;
}

export const MODELS: ModelOption[] = [
  {
    id: "spring-ai",
    name: "Spring AI Core",
    badge: "默认",
    description: "原生 Spring AI 企业级流式服务，高并发高性能",
    icon: Zap,
    speed: "极速",
    accent: "from-emerald-500 to-teal-500",
  },
  {
    id: "deepseek-r1",
    name: "DeepSeek R1",
    badge: "推理",
    description: "深度思考与长文本逻辑推演专家",
    icon: Brain,
    speed: "深度推理",
    accent: "from-indigo-500 to-purple-500",
  },
  {
    id: "gpt-4o",
    name: "GPT-4o Omnis",
    badge: "全能",
    description: "多模态理解与多语言逻辑全能旗舰",
    icon: Sparkles,
    speed: "极速",
    accent: "from-blue-500 to-cyan-500",
  },
  {
    id: "claude-3-5",
    name: "Claude 3.5 Sonnet",
    badge: "架构",
    description: "卓越的代码生成、重构与架构设计",
    icon: Cpu,
    speed: "代码专家",
    accent: "from-amber-500 to-orange-500",
  },
];

interface ModelSelectorProps {
  value: ModelId;
  onChange: (id: ModelId) => void;
}

export function ModelSelector({ value, onChange }: ModelSelectorProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedModel = MODELS.find((m) => m.id === value) ?? MODELS[0];
  const IconComponent = selectedModel.icon;

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="relative inline-block text-left" ref={containerRef}>
      {/* 选中的模型按钮 */}
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className={cn(
          "group flex items-center gap-2 rounded-xl border border-zinc-200/80 bg-white/80 px-2.5 py-1.5 text-xs font-medium text-zinc-700 shadow-xs backdrop-blur transition-all duration-200 hover:border-indigo-500/40 hover:bg-white hover:shadow-sm dark:border-zinc-800/80 dark:bg-zinc-900/60 dark:text-zinc-200 dark:hover:border-indigo-500/50 dark:hover:bg-zinc-900",
          open &&
            "border-indigo-500 ring-2 ring-indigo-500/20 dark:border-indigo-500",
        )}
      >
        <span
          className={cn(
            "flex size-5 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-xs",
            selectedModel.accent,
          )}
        >
          <IconComponent className="size-3" />
        </span>
        <span className="font-semibold">{selectedModel.name}</span>
        <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-[10px] font-normal text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
          {selectedModel.badge}
        </span>
        <ChevronDown
          className={cn(
            "size-3.5 text-zinc-400 transition-transform duration-200",
            open && "rotate-180 text-zinc-700 dark:text-zinc-200",
          )}
        />
      </button>

      {/* 下拉面板 */}
      {open && (
        <div className="absolute left-0 bottom-full mb-2 z-50 w-72 origin-bottom-left rounded-2xl border border-zinc-200/80 bg-white/95 p-1.5 shadow-2xl shadow-indigo-500/10 backdrop-blur-xl transition-all dark:border-zinc-800/90 dark:bg-zinc-900/95 dark:shadow-none animate-in fade-in slide-in-from-bottom-2 duration-150">
          <div className="px-3 py-2 text-[11px] font-semibold tracking-wider text-zinc-400 uppercase">
            选择 AI 模型
          </div>
          <div className="space-y-1">
            {MODELS.map((m) => {
              const isSelected = m.id === value;
              const Icon = m.icon;
              return (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => {
                    onChange(m.id);
                    setOpen(false);
                  }}
                  className={cn(
                    "group flex w-full items-start gap-2.5 rounded-xl p-2.5 text-left transition-all duration-150",
                    isSelected
                      ? "bg-indigo-50/80 text-indigo-950 dark:bg-indigo-950/40 dark:text-indigo-100"
                      : "hover:bg-zinc-100/80 dark:hover:bg-zinc-800/60 text-zinc-700 dark:text-zinc-300",
                  )}
                >
                  <span
                    className={cn(
                      "mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-xs transition-transform group-hover:scale-105",
                      m.accent,
                    )}
                  >
                    <Icon className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-1">
                      <span className="font-semibold text-xs text-zinc-900 dark:text-zinc-100">
                        {m.name}
                      </span>
                      <span className="rounded-full bg-zinc-200/60 px-1.5 py-0.5 text-[10px] text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
                        {m.speed}
                      </span>
                    </div>
                    <p className="mt-0.5 line-clamp-2 text-[11px] leading-relaxed text-zinc-500 dark:text-zinc-400">
                      {m.description}
                    </p>
                  </div>
                  {isSelected && (
                    <Check className="mt-1 size-4 shrink-0 text-indigo-600 dark:text-indigo-400" />
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
