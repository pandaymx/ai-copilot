"use client";

import {
  AlertTriangle,
  Check,
  Copy,
  Lightbulb,
  ListChecks,
  Sparkles,
  Wand2,
  X,
} from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

/** 与后端 PromptOptimizeResult 对齐的强类型定义。 */
export interface PromptOptimizeResult {
  score: number;
  missing: string[];
  issues: string[];
  optimized: string;
  fewShot: string[];
}

const MISSING_LABELS: Record<string, string> = {
  ROLE: "角色设定",
  CONTEXT: "上下文",
  FORMAT: "输出格式",
  CONSTRAINTS: "边界约束",
  EXAMPLES: "示例",
  AUDIENCE: "目标受众",
  TONE: "语气风格",
};

interface PromptOptimizerCardProps {
  result: PromptOptimizeResult | null;
  originalPrompt: string;
  loading: boolean;
  error: string | null;
  onApply: (optimized: string) => void;
  onClose: () => void;
}

function scoreColor(score: number): string {
  if (score >= 80) return "text-emerald-500";
  if (score >= 50) return "text-amber-500";
  return "text-rose-500";
}

function scoreBarColor(score: number): string {
  if (score >= 80) return "bg-emerald-500";
  if (score >= 50) return "bg-amber-500";
  return "bg-rose-500";
}

export function PromptOptimizerCard({
  result,
  originalPrompt,
  loading,
  error,
  onApply,
  onClose,
}: PromptOptimizerCardProps) {
  const [copied, setCopied] = useState(false);
  const [showDiff, setShowDiff] = useState(false);

  if (loading) {
    return (
      <div className="rounded-xl border border-indigo-200/60 bg-indigo-50/50 p-3 text-sm text-indigo-600 dark:border-indigo-500/30 dark:bg-indigo-500/10 dark:text-indigo-300">
        <div className="flex items-center gap-2">
          <Sparkles className="size-4 animate-pulse" />
          正在分析并优化你的 Prompt…
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-rose-200/60 bg-rose-50/50 p-3 text-sm text-rose-600 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300">
        <div className="flex items-start gap-2">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" />
          <div className="flex-1">
            <p>暂时无法优化 Prompt：{error}</p>
            <p className="mt-1 text-xs opacity-70">
              你可以继续直接发送原始内容。
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="shrink-0 rounded-full p-1 text-rose-400 hover:bg-rose-500/10"
            title="关闭"
          >
            <X className="size-3.5" />
          </button>
        </div>
      </div>
    );
  }

  if (!result) return null;

  // 降级结果：score < 0 表示后端调用失败，仅回退原始 Prompt。
  const degraded = result.score < 0;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(result.optimized);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* 忽略剪贴板异常 */
    }
  };

  return (
    <div className="rounded-xl border border-indigo-200/60 bg-white/70 p-3 text-sm shadow-sm dark:border-indigo-500/30 dark:bg-zinc-900/60">
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-1.5 font-medium text-indigo-600 dark:text-indigo-300">
          <Wand2 className="size-4" />
          Prompt 优化建议
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-full p-1 text-zinc-400 hover:bg-zinc-500/10 hover:text-zinc-600 dark:hover:text-zinc-200"
          title="关闭"
        >
          <X className="size-3.5" />
        </button>
      </div>

      {degraded ? (
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          优化服务暂不可用，已为你保留原始内容，可直接发送。
        </p>
      ) : (
        <>
          {/* 评分 + 缺失项 */}
          <div className="mb-3 flex items-center gap-4">
            <div className="flex items-baseline gap-1">
              <span
                className={cn(
                  "text-2xl font-bold tabular-nums",
                  scoreColor(result.score),
                )}
              >
                {result.score}
              </span>
              <span className="text-xs text-zinc-400">/100</span>
            </div>
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-700">
              <div
                className={cn(
                  "h-full rounded-full transition-all",
                  scoreBarColor(result.score),
                )}
                style={{
                  width: `${Math.max(0, Math.min(100, result.score))}%`,
                }}
              />
            </div>
          </div>

          {result.missing.length > 0 && (
            <div className="mb-3">
              <div className="mb-1 flex items-center gap-1 text-xs font-medium text-zinc-500 dark:text-zinc-400">
                <ListChecks className="size-3.5" />
                缺失项
              </div>
              <div className="flex flex-wrap gap-1.5">
                {result.missing.map((m) => (
                  <span
                    key={m}
                    className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"
                  >
                    {MISSING_LABELS[m] ?? m}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* 修改建议 */}
          {result.issues.length > 0 && (
            <div className="mb-3">
              <div className="mb-1 flex items-center gap-1 text-xs font-medium text-zinc-500 dark:text-zinc-400">
                <Lightbulb className="size-3.5" />
                改进建议
              </div>
              <ul className="list-disc space-y-0.5 pl-5 text-zinc-600 dark:text-zinc-300">
                {result.issues.map((issue) => (
                  <li key={issue}>{issue}</li>
                ))}
              </ul>
            </div>
          )}

          {/* 优化版 Prompt + Diff */}
          <div className="mb-2">
            <div className="mb-1 flex items-center justify-between">
              <span className="text-xs font-medium text-zinc-500 dark:text-zinc-400">
                优化后的 Prompt
              </span>
              {originalPrompt.trim() &&
                result.optimized.trim() &&
                originalPrompt.trim() !== result.optimized.trim() && (
                  <button
                    type="button"
                    onClick={() => setShowDiff((v) => !v)}
                    className="text-xs text-indigo-500 hover:underline dark:text-indigo-400"
                  >
                    {showDiff ? "隐藏对比" : "对比原 Prompt"}
                  </button>
                )}
            </div>
            <textarea
              readOnly
              value={result.optimized}
              rows={Math.min(
                10,
                Math.max(3, result.optimized.split("\n").length),
              )}
              className="w-full resize-none rounded-lg border border-zinc-200 bg-zinc-50 p-2 font-mono text-xs leading-relaxed text-zinc-800 focus:outline-hidden dark:border-zinc-700 dark:bg-zinc-800/60 dark:text-zinc-200"
            />
            {showDiff && (
              <div className="mt-2">
                <div className="mb-1 text-xs text-zinc-400">原始 Prompt</div>
                <div className="max-h-32 overflow-auto whitespace-pre-wrap rounded-lg border border-zinc-200 bg-zinc-50 p-2 font-mono text-xs text-zinc-500 dark:border-zinc-700 dark:bg-zinc-800/40 dark:text-zinc-400">
                  {originalPrompt}
                </div>
              </div>
            )}
          </div>

          {/* Few-shot */}
          {result.fewShot.length > 0 && (
            <div className="mb-3">
              <div className="mb-1 text-xs font-medium text-zinc-500 dark:text-zinc-400">
                Few-shot 示例
              </div>
              <div className="space-y-1.5">
                {result.fewShot.map((ex) => (
                  <div
                    key={ex}
                    className="whitespace-pre-wrap rounded-lg border border-zinc-200 bg-zinc-50 p-2 font-mono text-xs text-zinc-600 dark:border-zinc-700 dark:bg-zinc-800/40 dark:text-zinc-300"
                  >
                    {ex}
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onApply(result.optimized)}
          className="flex items-center gap-1 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-700 dark:bg-indigo-500 dark:hover:bg-indigo-600"
        >
          <Wand2 className="size-3.5" />
          一键应用
        </button>
        <button
          type="button"
          onClick={handleCopy}
          className="flex items-center gap-1 rounded-lg border border-zinc-200 px-3 py-1.5 text-xs font-medium text-zinc-600 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
        >
          {copied ? (
            <Check className="size-3.5" />
          ) : (
            <Copy className="size-3.5" />
          )}
          {copied ? "已复制" : "复制"}
        </button>
      </div>
    </div>
  );
}
