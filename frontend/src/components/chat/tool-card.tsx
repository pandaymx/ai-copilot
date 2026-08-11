"use client";

import {
  Brain,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Loader2,
  Wrench,
  XCircle,
} from "lucide-react";
import { useState } from "react";

import type { ToolCallItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

function tryFormatJson(raw: string): { text: string; isJson: boolean } {
  if (!raw) return { text: "", isJson: false };
  try {
    const obj = JSON.parse(raw);
    return { text: JSON.stringify(obj, null, 2), isJson: true };
  } catch {
    return { text: raw, isJson: false };
  }
}

function extractThoughtAndCleanArgs(
  rawArgs: string,
  itemThought?: string,
): { thought: string; cleanArgsText: string } {
  let thought = itemThought ?? "";
  let cleanArgsObj: Record<string, unknown> | null = null;

  if (rawArgs) {
    try {
      const obj = JSON.parse(rawArgs);
      if (typeof obj === "object" && obj !== null && !Array.isArray(obj)) {
        if (!thought && typeof obj.innerThought === "string") {
          thought = obj.innerThought;
        }
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { innerThought: _, ...rest } = obj as Record<string, unknown>;
        if (Object.keys(rest).length > 0) {
          cleanArgsObj = rest;
        }
      }
    } catch {
      if (!thought) {
        const match = /"innerThought"\s*:\s*"((?:[^"\\]|\\.)*)/.exec(rawArgs);
        if (match?.[1]) {
          try {
            thought = JSON.parse(`"${match[1]}"`);
          } catch {
            thought = match[1];
          }
        }
      }
    }
  }

  let cleanArgsText = "";
  if (cleanArgsObj) {
    cleanArgsText = JSON.stringify(cleanArgsObj, null, 2);
  } else if (rawArgs) {
    cleanArgsText = tryFormatJson(rawArgs).text;
  }

  return { thought, cleanArgsText };
}

/**
 * 单个工具调用卡片。
 *
 * 智能折叠 UX：
 * - calling（执行中）：默认展开，展示正在传入的参数与思考过程；
 * - success（成功）：默认收起为微缩状态，点击可二次展开查看思考过程与结果；
 * - error（失败）：默认展开并用红框高亮错误原因。
 * UI 层以 callId 作为唯一 key（见 message-bubble），保证并行多工具调用不闪烁/不顺序颠倒。
 */
export function ToolCard({ item }: { item: ToolCallItem }) {
  const [expanded, setExpanded] = useState(
    item.status === "calling" || item.status === "error",
  );

  const isCalling = item.status === "calling";
  const isError = item.status === "error";
  const isSuccess = item.status === "success";

  const { thought, cleanArgsText } = extractThoughtAndCleanArgs(
    item.arguments,
    item.innerThought,
  );
  const result = tryFormatJson(item.result ?? "");

  const statusMeta = isCalling
    ? {
        icon: <Loader2 className="size-3.5 animate-spin text-violet-500" />,
        label: "正在调用工具",
        badge: "calling",
      }
    : isError
      ? {
          icon: <XCircle className="size-3.5 text-rose-500" />,
          label: "调用失败",
          badge: "error",
        }
      : {
          icon: <CheckCircle2 className="size-3.5 text-emerald-500" />,
          label: "调用完成",
          badge: "success",
        };

  return (
    <div
      className={cn(
        "rounded-xl border bg-white/60 backdrop-blur-md transition-all duration-300 dark:bg-white/5",
        isError
          ? "border-rose-300/70 shadow-sm shadow-rose-500/10 dark:border-rose-500/40"
          : "border-white/60 dark:border-white/10",
        isSuccess && !expanded && "py-1.5",
      )}
    >
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left outline-none transition-colors hover:bg-black/5 dark:hover:bg-white/5"
      >
        <span className="flex size-6 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500/15 to-fuchsia-500/15 text-violet-600 dark:text-violet-300">
          <Wrench className="size-3.5" />
        </span>
        <span className="flex min-w-0 flex-1 items-center gap-2">
          <span className="text-[11px] text-muted-foreground">
            {statusMeta.label}：
          </span>
          <span className="truncate text-xs font-medium text-foreground">
            {item.name}
          </span>
        </span>
        <span className="flex items-center gap-1.5">
          {statusMeta.icon}
          {expanded ? (
            <ChevronDown className="size-3.5 text-muted-foreground" />
          ) : (
            <ChevronRight className="size-3.5 text-muted-foreground" />
          )}
        </span>
      </button>

      {expanded && (
        <div className="space-y-2 px-3 pb-3">
          {thought && (
            <div className="rounded-lg border border-purple-500/20 bg-gradient-to-r from-purple-500/10 via-indigo-500/10 to-blue-500/10 p-2.5 dark:border-purple-500/30 dark:from-purple-950/30 dark:via-indigo-950/30 dark:to-blue-950/30">
              <div className="mb-1 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wide text-purple-700 dark:text-purple-300">
                <Brain className="size-3 text-purple-600 dark:text-purple-400" />
                思考过程
              </div>
              <p className="whitespace-pre-wrap text-xs leading-relaxed text-purple-950 dark:text-purple-200 font-sans">
                {thought}
              </p>
            </div>
          )}
          {cleanArgsText && (
            <div>
              <div className="mb-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                参数
              </div>
              <pre className="max-h-48 overflow-auto rounded-lg bg-zinc-900/90 p-2.5 text-[11px] leading-relaxed text-zinc-100 dark:bg-black/40">
                {cleanArgsText}
              </pre>
            </div>
          )}
          {result.text && (
            <div>
              <div className="mb-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                {isError ? "错误原因" : "返回结果"}
              </div>
              <pre
                className={cn(
                  "max-h-48 overflow-auto rounded-lg p-2.5 text-[11px] leading-relaxed",
                  isError
                    ? "bg-rose-950/40 text-rose-200"
                    : "bg-zinc-900/90 text-zinc-100 dark:bg-black/40",
                )}
              >
                {result.text}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
