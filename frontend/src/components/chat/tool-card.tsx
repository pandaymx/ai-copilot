"use client";

import {
  Brain,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Loader2,
  Sparkles,
  Wrench,
  XCircle,
} from "lucide-react";
import { useState } from "react";

import type { ToolCallItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";
import { ToolResultRenderer } from "./tool-renderers";

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
): { thought: string; cleanArgsText: string; taskText: string } {
  let thought = itemThought ?? "";
  let cleanArgsObj: Record<string, unknown> | null = null;
  let taskText = "";

  if (rawArgs) {
    try {
      const obj = JSON.parse(rawArgs);
      if (typeof obj === "object" && obj !== null && !Array.isArray(obj)) {
        if (!thought && typeof obj.innerThought === "string") {
          thought = obj.innerThought;
        }
        if (typeof obj.task === "string") {
          taskText = obj.task;
        }
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {
          innerThought: _i,
          task: _t,
          ...rest
        } = obj as Record<string, unknown>;
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

  return { thought, cleanArgsText, taskText };
}

const SUB_AGENT_LABELS: Record<string, string> = {
  analysis: "数据分析",
  code: "代码生成",
  summary: "摘要整合",
};

function resolveAgentLabel(name: string): string {
  const type = name.split(":")[1] ?? "";
  return SUB_AGENT_LABELS[type] ?? type;
}

function extractOutputText(resultJson: string | undefined): string {
  if (!resultJson) return "";
  try {
    const obj = JSON.parse(resultJson);
    if (typeof obj?.output === "string") return obj.output;
    return JSON.stringify(obj, null, 2);
  } catch {
    return resultJson;
  }
}

/**
 * 单个工具调用卡片。
 *
 * 支持两种渲染模式：
 * - 普通工具：扳手图标 + 工具名 + 参数/结果折叠展示
 * - 子代理（toolName 以 sub_agent: 开头）：🤖 紫色渐变徽章 + 任务描述 + 折叠结果卡片
 *   呈现效果：「🤖 子代理 · 数据分析：分析过去 30 天用户增长趋势（思考中…）」
 */
export function ToolCard({ item }: { item: ToolCallItem }) {
  const isSubAgent = item.name.startsWith("sub_agent:");
  const agentLabel = isSubAgent ? resolveAgentLabel(item.name) : null;

  const [expanded, setExpanded] = useState(
    item.status === "calling" || item.status === "error",
  );

  const isCalling = item.status === "calling";
  const isError = item.status === "error";
  const isSuccess = item.status === "success";

  const { thought, cleanArgsText, taskText } = extractThoughtAndCleanArgs(
    item.arguments,
    item.innerThought,
  );

  // ---- Sub-Agent Card ----
  if (isSubAgent) {
    const resultOutput = extractOutputText(item.result);

    return (
      <div
        className={cn(
          "rounded-xl border backdrop-blur-md transition-all duration-300",
          isError
            ? "border-rose-300/70 bg-rose-500/5 shadow-sm shadow-rose-500/10 dark:border-rose-500/40"
            : "border-purple-300/50 bg-gradient-to-br from-purple-500/8 to-fuchsia-500/8 dark:border-purple-500/30 dark:from-purple-950/20 dark:to-fuchsia-950/20",
          isSuccess && !expanded && "py-1",
        )}
      >
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left outline-none transition-colors hover:bg-purple-500/8 dark:hover:bg-purple-500/10"
        >
          {/* Robot emoji badge */}
          <span
            className={cn(
              "flex size-6 shrink-0 items-center justify-center rounded-lg text-sm",
              isError
                ? "bg-rose-500/15"
                : "bg-gradient-to-br from-purple-500/20 to-fuchsia-500/20",
            )}
          >
            🤖
          </span>

          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="flex items-center gap-1.5 flex-wrap">
              {/* Agent type chip */}
              <span
                className={cn(
                  "inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] font-semibold",
                  isError
                    ? "bg-rose-500/15 text-rose-600 dark:text-rose-400"
                    : "bg-gradient-to-r from-purple-500/20 to-fuchsia-500/20 text-purple-700 dark:text-purple-300",
                )}
              >
                <Sparkles className="size-2.5" />
                子代理 · {agentLabel}
              </span>
              {isCalling && (
                <span className="text-[10px] text-muted-foreground animate-pulse">
                  思考中…
                </span>
              )}
              {isSuccess && (
                <span className="text-[10px] text-emerald-600 dark:text-emerald-400">
                  已完成
                </span>
              )}
              {isError && (
                <span className="text-[10px] text-rose-500">执行失败</span>
              )}
            </span>
            {/* Task description in header */}
            {taskText && (
              <span
                className={cn(
                  "truncate text-xs leading-snug",
                  isCalling
                    ? "text-purple-800 dark:text-purple-200"
                    : "text-muted-foreground",
                )}
                title={taskText}
              >
                {taskText}
              </span>
            )}
          </span>

          <span className="flex items-center gap-1 shrink-0">
            {isCalling && (
              <Loader2 className="size-3.5 animate-spin text-purple-500" />
            )}
            {isSuccess && (
              <CheckCircle2 className="size-3.5 text-emerald-500" />
            )}
            {isError && <XCircle className="size-3.5 text-rose-500" />}
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
              <div className="rounded-lg border border-purple-500/20 bg-gradient-to-r from-purple-500/10 via-indigo-500/10 to-blue-500/10 p-2.5 dark:border-purple-500/30">
                <div className="mb-1 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wide text-purple-700 dark:text-purple-300">
                  <Brain className="size-3" />
                  思考过程
                </div>
                <p className="whitespace-pre-wrap text-xs leading-relaxed text-purple-950 dark:text-purple-200 font-sans">
                  {thought}
                </p>
              </div>
            )}
            {taskText && (
              <div>
                <div className="mb-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                  子任务指令
                </div>
                <p className="rounded-lg bg-purple-950/10 px-2.5 py-2 text-xs leading-relaxed text-foreground dark:bg-purple-950/30 whitespace-pre-wrap">
                  {taskText}
                </p>
              </div>
            )}
            {resultOutput && (
              <div>
                <div className="mb-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                  {isError ? "错误原因" : "子代理回复"}
                </div>
                <pre
                  className={cn(
                    "max-h-64 overflow-auto rounded-lg p-2.5 text-[11px] leading-relaxed whitespace-pre-wrap",
                    isError
                      ? "bg-rose-950/40 text-rose-200"
                      : "bg-zinc-900/90 text-zinc-100 dark:bg-black/40",
                  )}
                >
                  {resultOutput}
                </pre>
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  // ---- Regular Tool Card ----
  const statusMeta = isCalling
    ? {
        icon: <Loader2 className="size-3.5 animate-spin text-violet-500" />,
        label: "正在执行工具...",
      }
    : isError
      ? {
          icon: <XCircle className="size-3.5 text-rose-500" />,
          label: "调用失败",
        }
      : {
          icon: <CheckCircle2 className="size-3.5 text-emerald-500" />,
          label: "调用完成",
        };

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl border bg-white/60 backdrop-blur-md transition-all duration-300 dark:bg-white/5",
        isCalling &&
          "border-violet-500/40 bg-violet-500/5 shadow-md shadow-violet-500/10 dark:border-violet-500/40",
        isError &&
          "border-rose-300/70 shadow-sm shadow-rose-500/10 dark:border-rose-500/40",
        isSuccess &&
          !expanded &&
          "py-1.5 border-emerald-500/20 dark:border-emerald-500/20",
      )}
    >
      {/* 流式/调用中顶部微光进度条 */}
      {isCalling && (
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r from-violet-500 via-indigo-500 to-fuchsia-500 animate-pulse" />
      )}

      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left outline-none transition-colors hover:bg-black/5 dark:hover:bg-white/5"
      >
        <span
          className={cn(
            "flex size-6 shrink-0 items-center justify-center rounded-lg text-violet-600 dark:text-violet-300 transition-all",
            isCalling
              ? "bg-violet-500/20 animate-pulse"
              : "bg-gradient-to-br from-indigo-500/15 to-fuchsia-500/15",
          )}
        >
          <Wrench className="size-3.5" />
        </span>
        <span className="flex min-w-0 flex-1 items-center gap-2">
          <span
            className={cn(
              "text-[11px]",
              isCalling
                ? "text-violet-600 font-medium dark:text-violet-400 animate-pulse"
                : isError
                  ? "text-rose-500"
                  : "text-muted-foreground",
            )}
          >
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
        <div className="space-y-2.5 px-3 pb-3">
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
              <pre className="max-h-48 overflow-auto rounded-lg bg-zinc-900/90 p-2.5 text-[11px] leading-relaxed text-zinc-100 dark:bg-black/40 font-mono">
                {cleanArgsText}
              </pre>
            </div>
          )}
          {item.result && (
            <div>
              <div className="mb-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                {isError ? "错误原因" : "结构化返回结果"}
              </div>
              <ToolResultRenderer
                toolName={item.name}
                argsJson={item.arguments}
                resultJson={item.result}
                isError={isError}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
