"use client";

import {
  Brain,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock,
  Compass,
  Search,
  Sparkles,
  Target,
  Zap,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { cn } from "@/lib/utils";

export interface ReasoningStep {
  id: string;
  stepIndex: number;
  title: string;
  category: "Goal" | "Analysis" | "Decision" | "Action" | "Summary";
  isDecision: boolean;
  content: string;
}

const DECISION_KEYWORDS = [
  "决策",
  "决定",
  "选择",
  "判定",
  "关键",
  "方案",
  "策略",
  "因此",
  "结论",
  "假设",
  "推导",
  "decide",
  "decision",
  "therefore",
  "choose",
  "assume",
  "conclude",
  "option",
  "choice",
];

/** 将原始思考文本智能解析为结构化多步推理节点 */
export function parseThinkingToSteps(rawThinking: string): ReasoningStep[] {
  if (!rawThinking || !rawThinking.trim()) return [];

  // 按照双换行、编号列表 (1. 2. 3.) 或 Markdown 标题/标记分块
  const rawChunks = rawThinking
    .split(
      /(?:\r?\n){2,}|(?=\n\s*(?:[0-9]+\.|\*|-|Step\s*\d+|Phase\s*\d+|Goal|Analysis|Decision|Conclusion|\[|#))/i,
    )
    .map((c) => c.trim())
    .filter(Boolean);

  if (rawChunks.length === 0) {
    return [
      {
        id: "step-1",
        stepIndex: 1,
        title: "逻辑推导过程",
        category: "Action",
        isDecision: false,
        content: rawThinking.trim(),
      },
    ];
  }

  return rawChunks.map((chunk, idx) => {
    const lines = chunk
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean);
    const rawTitleLine = lines[0] || `推理步骤 ${idx + 1}`;

    // 清理前端编号或 Markdown 标记
    const titleClean = rawTitleLine
      .replace(/^(?:[0-9]+\.|\*|-|#+)\s*/, "")
      .replace(/^\[(?:Step|Phase|\d+)\]\s*/i, "");

    const title =
      titleClean.length > 50 ? `${titleClean.slice(0, 47)}...` : titleClean;
    const lowerChunk = chunk.toLowerCase();

    // 关键字与决策点识别
    const isDecision = DECISION_KEYWORDS.some((kw) =>
      lowerChunk.includes(kw.toLowerCase()),
    );

    let category: ReasoningStep["category"] = "Action";
    if (/总结|结论|综上|summary|conclusion/i.test(lowerChunk)) {
      category = "Summary";
    } else if (/目标|需求|意图|goal|intent/i.test(lowerChunk)) {
      category = "Goal";
    } else if (/分析|研究|观察|analyse|analyze|observe/i.test(lowerChunk)) {
      category = "Analysis";
    } else if (isDecision || /决策|决定|选择|decision/i.test(lowerChunk)) {
      category = "Decision";
    }

    return {
      id: `step-${idx + 1}`,
      stepIndex: idx + 1,
      title,
      category,
      isDecision,
      content: chunk,
    };
  });
}

/** 格式化用时（毫秒 -> 秒，如 3.2s） */
function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export interface ReasoningViewProps {
  thinking: string;
  streaming?: boolean;
  durationMs?: number;
  className?: string;
}

export function ReasoningView({
  thinking,
  streaming = false,
  durationMs,
  className,
}: ReasoningViewProps) {
  const [expanded, setExpanded] = useState(true);
  const [elapsedMs, setElapsedMs] = useState(0);

  // 流式推理时的实时秒表计时器
  useEffect(() => {
    if (!streaming) return;
    const start = Date.now();
    const timer = setInterval(() => {
      setElapsedMs(Date.now() - start);
    }, 100);

    return () => clearInterval(timer);
  }, [streaming]);

  const steps = useMemo(() => parseThinkingToSteps(thinking), [thinking]);
  const decisionCount = useMemo(
    () => steps.filter((s) => s.isDecision).length,
    [steps],
  );

  if (!thinking || !thinking.trim()) return null;

  const displayTime = durationMs
    ? formatDuration(durationMs)
    : streaming
      ? formatDuration(elapsedMs)
      : null;

  return (
    <div
      className={cn(
        "my-2 w-full min-w-0 overflow-hidden rounded-2xl border backdrop-blur-md transition-all duration-300",
        streaming
          ? "border-purple-500/40 bg-gradient-to-br from-purple-500/10 via-indigo-500/5 to-fuchsia-500/10 shadow-lg shadow-purple-500/10"
          : "border-purple-300/40 bg-purple-50/40 dark:border-purple-900/40 dark:bg-purple-950/20",
        className,
      )}
    >
      {/* 顶栏控制条 Header */}
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center justify-between px-3.5 py-2.5 outline-none transition-colors hover:bg-purple-500/10 dark:hover:bg-purple-500/15"
      >
        <div className="flex items-center gap-2 min-w-0">
          <span
            className={cn(
              "flex size-6 shrink-0 items-center justify-center rounded-lg text-purple-600 dark:text-purple-300 transition-all",
              streaming
                ? "bg-purple-500/20 animate-pulse ring-2 ring-purple-500/30"
                : "bg-purple-500/15 dark:bg-purple-900/40",
            )}
          >
            <Brain className={cn("size-3.5", streaming && "animate-pulse")} />
          </span>

          <span className="text-xs font-semibold text-purple-900 dark:text-purple-200">
            思维链推理过程
          </span>

          {/* 实时秒表计时器 / 思考用时 Badge */}
          {displayTime && (
            <span
              className={cn(
                "inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-mono text-[10px] font-medium transition-all",
                streaming
                  ? "bg-purple-500/20 text-purple-600 dark:text-purple-300 animate-pulse border border-purple-500/30"
                  : "bg-purple-950/20 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300",
              )}
            >
              <Clock className="size-3" />
              <span>
                {streaming ? `思考中... ${displayTime}` : `用时 ${displayTime}`}
              </span>
            </span>
          )}

          {/* 步骤计数 */}
          {steps.length > 0 && (
            <span className="hidden sm:inline-flex items-center rounded-md bg-purple-900/10 dark:bg-purple-900/40 px-1.5 py-0.5 text-[10px] font-mono text-purple-700 dark:text-purple-300">
              {steps.length} 个步骤
            </span>
          )}

          {/* 决策点 Badge */}
          {decisionCount > 0 && (
            <span className="inline-flex items-center gap-0.5 rounded-md bg-amber-500/15 border border-amber-500/30 px-1.5 py-0.5 text-[10px] font-semibold text-amber-600 dark:text-amber-300">
              <Sparkles className="size-2.5" />
              <span>{decisionCount} 处决策点</span>
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 shrink-0 text-purple-600 dark:text-purple-400">
          {expanded ? (
            <ChevronDown className="size-4" />
          ) : (
            <ChevronRight className="size-4" />
          )}
        </div>
      </button>

      {/* 展开内容：多步推理树与连线 */}
      {expanded && (
        <div className="border-t border-purple-500/20 p-3.5 space-y-3 dark:border-purple-900/30">
          <div className="relative pl-6 space-y-3">
            {/* 左侧垂直主轴连线 */}
            <div className="absolute left-2.5 top-3 bottom-3 w-0.5 bg-gradient-to-b from-purple-500/40 via-indigo-500/30 to-purple-500/10 rounded-full" />

            {steps.map((step) => {
              const categoryConfig = {
                Goal: {
                  label: "目标分析",
                  icon: Target,
                  color:
                    "bg-cyan-500/15 text-cyan-700 border-cyan-500/30 dark:text-cyan-300",
                },
                Analysis: {
                  label: "深入探究",
                  icon: Search,
                  color:
                    "bg-blue-500/15 text-blue-700 border-blue-500/30 dark:text-blue-300",
                },
                Decision: {
                  label: "关键决策点",
                  icon: Compass,
                  color:
                    "bg-gradient-to-r from-purple-500/20 to-amber-500/20 text-purple-700 border-purple-500/40 font-bold dark:text-purple-200 shadow-sm shadow-purple-500/20",
                },
                Action: {
                  label: "逻辑推导",
                  icon: Zap,
                  color:
                    "bg-indigo-500/15 text-indigo-700 border-indigo-500/30 dark:text-indigo-300",
                },
                Summary: {
                  label: "推导结论",
                  icon: CheckCircle2,
                  color:
                    "bg-emerald-500/15 text-emerald-700 border-emerald-500/30 dark:text-emerald-300",
                },
              }[step.category];

              const Icon = categoryConfig.icon;

              return (
                <div key={step.id} className="relative group/step">
                  {/* 节点轴上的数字 Badge */}
                  <span
                    className={cn(
                      "absolute -left-6 top-1 flex size-5 items-center justify-center rounded-full font-mono text-[10px] font-bold shadow-xs transition-all",
                      step.isDecision
                        ? "bg-gradient-to-br from-purple-500 to-amber-500 text-white ring-2 ring-purple-500/30 scale-110"
                        : "bg-purple-900/20 text-purple-700 dark:bg-purple-950 dark:text-purple-300 border border-purple-500/30",
                    )}
                  >
                    {step.stepIndex}
                  </span>

                  {/* 节点步骤卡片 */}
                  <div
                    className={cn(
                      "rounded-xl border p-3 text-xs transition-all duration-200",
                      step.isDecision
                        ? "border-purple-500/40 bg-gradient-to-br from-purple-950/20 via-amber-950/10 to-purple-950/30 shadow-md shadow-purple-500/10 dark:border-purple-500/50"
                        : "border-purple-500/15 bg-white/40 dark:bg-black/30 dark:border-purple-900/30 hover:border-purple-500/30",
                    )}
                  >
                    {/* 卡片 Header: 类别 Tag + 步骤标题 */}
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-purple-500/10 pb-1.5 mb-2">
                      <div className="flex items-center gap-1.5 min-w-0">
                        <span
                          className={cn(
                            "inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-semibold border",
                            categoryConfig.color,
                          )}
                        >
                          <Icon className="size-2.5" />
                          <span>{categoryConfig.label}</span>
                        </span>

                        {step.isDecision && (
                          <span className="inline-flex items-center gap-0.5 rounded-full bg-amber-500/20 px-1.5 py-0.5 text-[9px] font-bold text-amber-500 border border-amber-500/30">
                            ★ 决策点
                          </span>
                        )}
                      </div>

                      <span className="truncate font-medium text-[11px] text-purple-950 dark:text-purple-200">
                        {step.title}
                      </span>
                    </div>

                    {/* 正文文本 */}
                    <p className="whitespace-pre-wrap text-[11px] leading-relaxed text-zinc-700 font-sans dark:text-zinc-300">
                      {step.content}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
