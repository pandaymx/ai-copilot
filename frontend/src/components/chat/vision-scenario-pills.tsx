"use client";

import { Bug, FileCode, Leaf, LineChart, Sparkles } from "lucide-react";
import type { ComponentType } from "react";
import { cn } from "@/lib/utils";

export interface VisionScenario {
  id: string;
  icon: ComponentType<{ className?: string }>;
  title: string;
  badge: string;
  prompt: string;
  gradient: string;
}

export const VISION_SCENARIOS: VisionScenario[] = [
  {
    id: "bug",
    icon: Bug,
    title: "截图问 Bug",
    badge: "代码排错",
    prompt:
      "请帮我分析截图中的代码或错误日志，指出可能导致 Bug 的根本原因，并给出具体的修复代码与优化建议。",
    gradient:
      "from-rose-500/15 via-red-500/10 to-orange-500/15 text-rose-600 dark:text-rose-400 border-rose-200/80 dark:border-rose-900/50 hover:border-rose-400 dark:hover:border-rose-700",
  },
  {
    id: "plant",
    icon: Leaf,
    title: "植物病害识别",
    badge: "动植物识别",
    prompt:
      "请帮我识别图片中的植物种类及其健康状况，若存在病虫害或缺素等异常，请详细说明病害特征、成因及科学防治方法。",
    gradient:
      "from-emerald-500/15 via-teal-500/10 to-green-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-200/80 dark:border-emerald-900/50 hover:border-emerald-400 dark:hover:border-emerald-700",
  },
  {
    id: "latex",
    icon: FileCode,
    title: "公式转 LaTeX",
    badge: "公式提取",
    prompt:
      "请识别图片中的所有数学或物理公式，将它们转换为标准、严谨的 LaTeX 语法，并简要解释各符号的物理/数学含义。",
    gradient:
      "from-blue-500/15 via-indigo-500/10 to-cyan-500/15 text-blue-600 dark:text-blue-400 border-blue-200/80 dark:border-blue-900/50 hover:border-blue-400 dark:hover:border-blue-700",
  },
  {
    id: "chart",
    icon: LineChart,
    title: "图表数据提取",
    badge: "数据分析",
    prompt:
      "请识别图表中的坐标轴、图例、数据趋势与关键极值点，将核心数据提取整理为 Markdown 表格，并输出 3 点核心洞察结论。",
    gradient:
      "from-purple-500/15 via-fuchsia-500/10 to-pink-500/15 text-purple-600 dark:text-purple-400 border-purple-200/80 dark:border-purple-900/50 hover:border-purple-400 dark:hover:border-purple-700",
  },
];

interface VisionScenarioPillsProps {
  onSelect: (prompt: string) => void;
  className?: string;
}

export function VisionScenarioPills({
  onSelect,
  className,
}: VisionScenarioPillsProps) {
  return (
    <div
      className={cn(
        "flex items-center gap-1.5 overflow-x-auto pb-1.5 pt-0.5 scrollbar-none animate-in fade-in slide-in-from-bottom-2 duration-300",
        className,
      )}
    >
      <div className="flex shrink-0 items-center gap-1 text-[11px] font-medium text-zinc-400 dark:text-zinc-500 px-1">
        <Sparkles className="size-3.5 text-indigo-500 dark:text-indigo-400 animate-pulse" />
        <span>视觉快捷指令:</span>
      </div>
      {VISION_SCENARIOS.map((sc) => {
        const Icon = sc.icon;
        return (
          <button
            key={sc.id}
            type="button"
            onClick={() => onSelect(sc.prompt)}
            className={cn(
              "group relative flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium backdrop-blur-xs transition-all duration-200 hover:scale-105 active:scale-95 shadow-2xs hover:shadow-xs",
              sc.gradient,
            )}
            title={sc.prompt}
          >
            <Icon className="size-3.5 shrink-0 transition-transform group-hover:scale-110" />
            <span className="font-medium">{sc.title}</span>
            <span className="opacity-60 text-[10px] hidden sm:inline">
              · {sc.badge}
            </span>
          </button>
        );
      })}
    </div>
  );
}
