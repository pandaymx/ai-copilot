"use client";

import {
  Check,
  ChevronDown,
  ChevronUp,
  Code2,
  Copy,
  FileText,
  GitFork,
  HelpCircle,
  Lightbulb,
  Scale,
} from "lucide-react";
import { useMemo, useState } from "react";
import type { InheritedContext } from "@/lib/api";

interface InheritedContextBannerProps {
  inheritedContextJson?: string | null;
  parentSessionId?: string | null;
  onSelectSourceSession?: (sessionId: string) => void;
}

export function InheritedContextBanner({
  inheritedContextJson,
  parentSessionId,
  onSelectSourceSession,
}: InheritedContextBannerProps) {
  const [expanded, setExpanded] = useState(false);
  const [copied, setCopied] = useState(false);

  const context: InheritedContext | null = useMemo(() => {
    if (!inheritedContextJson) return null;
    try {
      return JSON.parse(inheritedContextJson) as InheritedContext;
    } catch {
      return null;
    }
  }, [inheritedContextJson]);

  if (!context) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(JSON.stringify(context, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="mx-auto my-3 max-w-4xl px-4">
      <div className="overflow-hidden rounded-2xl border border-indigo-200/80 bg-gradient-to-r from-indigo-50/80 via-purple-50/60 to-white/90 shadow-sm backdrop-blur-xs dark:border-indigo-900/60 dark:from-indigo-950/30 dark:via-purple-950/20 dark:to-zinc-900/60 transition-all">
        {/* Banner 头部 */}
        <div className="flex flex-wrap items-center justify-between gap-2 p-3 sm:px-4">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="flex size-7 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-600 dark:bg-indigo-500/25 dark:text-indigo-400">
              <GitFork className="size-4" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-zinc-900 dark:text-zinc-100">
                  跨会话继承上下文
                </span>
                <span className="rounded bg-indigo-500/10 px-1.5 py-0.2 text-[10px] font-semibold text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
                  {context.extractionMode === "LLM" ? "深度提炼" : "规则提取"}
                </span>
              </div>
              <p className="truncate text-[11px] text-zinc-500 dark:text-zinc-400">
                源自会话：
                {parentSessionId && onSelectSourceSession ? (
                  <button
                    type="button"
                    onClick={() => onSelectSourceSession(parentSessionId)}
                    className="font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                  >
                    {context.sourceSessionTitle || parentSessionId}
                  </button>
                ) : (
                  <span className="font-medium text-zinc-700 dark:text-zinc-300">
                    {context.sourceSessionTitle || "历史会话"}
                  </span>
                )}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white/80 px-2 py-1 text-[11px] font-medium text-zinc-600 shadow-2xs hover:bg-white dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700 transition-colors"
              title="复制上下文 JSON"
            >
              {copied ? (
                <>
                  <Check className="size-3 text-emerald-500" />
                  <span className="text-emerald-500">已复制</span>
                </>
              ) : (
                <>
                  <Copy className="size-3 text-zinc-400" />
                  <span>复制 JSON</span>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={() => setExpanded(!expanded)}
              className="flex items-center gap-1 rounded-lg bg-indigo-500/10 px-2.5 py-1 text-[11px] font-semibold text-indigo-600 hover:bg-indigo-500/20 dark:bg-indigo-500/20 dark:text-indigo-300 transition-colors"
            >
              <span>{expanded ? "收起详情" : "展开详情"}</span>
              {expanded ? (
                <ChevronUp className="size-3.5" />
              ) : (
                <ChevronDown className="size-3.5" />
              )}
            </button>
          </div>
        </div>

        {/* 主旨概要简述（未展开时单行展示） */}
        {!expanded && context.contextSummary && (
          <div className="border-t border-indigo-100/60 bg-white/40 px-4 py-2 text-xs text-zinc-600 dark:border-indigo-900/30 dark:bg-zinc-900/40 dark:text-zinc-400">
            <span className="font-semibold text-zinc-800 dark:text-zinc-200">
              主旨概述：
            </span>
            <span className="line-clamp-1">{context.contextSummary}</span>
          </div>
        )}

        {/* 展开后的 5 维详细面板 */}
        {expanded && (
          <div className="space-y-3.5 border-t border-indigo-100/80 bg-white/60 p-4 dark:border-indigo-900/40 dark:bg-zinc-900/60">
            {/* 核心主旨 */}
            {context.contextSummary && (
              <div className="space-y-1">
                <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800 dark:text-zinc-200">
                  <Lightbulb className="size-3.5 text-amber-500" />
                  <span>背景与核心主旨概述</span>
                </div>
                <p className="rounded-xl bg-white/80 p-2.5 text-xs leading-relaxed text-zinc-700 shadow-2xs dark:bg-zinc-800/80 dark:text-zinc-300 border border-zinc-200/60 dark:border-zinc-700/60">
                  {context.contextSummary}
                </p>
              </div>
            )}

            {/* 关键决策 */}
            {context.keyDecisions && context.keyDecisions.length > 0 && (
              <div className="space-y-1.5">
                <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800 dark:text-zinc-200">
                  <Scale className="size-3.5 text-indigo-500" />
                  <span>
                    关键架构与设计决策 ({context.keyDecisions.length})
                  </span>
                </div>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  {context.keyDecisions.map((kd) => (
                    <div
                      key={kd.decision}
                      className="rounded-xl border border-zinc-200/70 bg-white/80 p-2.5 text-xs shadow-2xs dark:border-zinc-700/60 dark:bg-zinc-800/70"
                    >
                      <div className="flex items-center gap-1.5 font-semibold text-zinc-900 dark:text-zinc-100">
                        {kd.category && (
                          <span className="rounded bg-indigo-50 px-1.5 py-0.5 text-[10px] font-bold text-indigo-600 dark:bg-indigo-950 dark:text-indigo-300">
                            {kd.category}
                          </span>
                        )}
                        <span className="truncate">{kd.decision}</span>
                      </div>
                      {kd.rationale && (
                        <p className="mt-1 text-[11px] text-zinc-500 dark:text-zinc-400">
                          理由：{kd.rationale}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 核心代码片段 */}
            {context.codeSnippets && context.codeSnippets.length > 0 && (
              <div className="space-y-1.5">
                <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800 dark:text-zinc-200">
                  <Code2 className="size-3.5 text-emerald-500" />
                  <span>核心代码片段 ({context.codeSnippets.length})</span>
                </div>
                <div className="space-y-2">
                  {context.codeSnippets.map((cs, idx) => (
                    <div
                      key={`${cs.language}-${idx}`}
                      className="overflow-hidden rounded-xl border border-zinc-200/80 bg-zinc-950 dark:border-zinc-800"
                    >
                      <div className="flex items-center justify-between border-b border-zinc-800 bg-zinc-900 px-3 py-1 text-[11px] text-zinc-400">
                        <span>
                          {cs.description || cs.filePath || "代码片段"}
                        </span>
                        <span className="font-mono text-[10px] uppercase text-emerald-400">
                          {cs.language}
                        </span>
                      </div>
                      <pre className="max-h-40 overflow-y-auto p-3 font-mono text-xs text-emerald-300">
                        {cs.code}
                      </pre>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 关联文件与未决问题 */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {context.fileReferences && context.fileReferences.length > 0 && (
                <div className="space-y-1">
                  <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800 dark:text-zinc-200">
                    <FileText className="size-3.5 text-blue-500" />
                    <span>涉及文件与引用</span>
                  </div>
                  <ul className="space-y-1 rounded-xl border border-zinc-200/70 bg-white/80 p-2.5 text-xs dark:border-zinc-700/60 dark:bg-zinc-800/70">
                    {context.fileReferences.map((fr) => (
                      <li
                        key={fr.fileName}
                        className="flex items-center justify-between text-zinc-600 dark:text-zinc-300 font-mono text-[11px]"
                      >
                        <span className="truncate">{fr.fileName}</span>
                        {fr.description && (
                          <span className="text-[10px] text-zinc-400 font-sans">
                            {fr.description}
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {context.pendingQuestions &&
                context.pendingQuestions.length > 0 && (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800 dark:text-zinc-200">
                      <HelpCircle className="size-3.5 text-purple-500" />
                      <span>未决问题与待办</span>
                    </div>
                    <ul className="space-y-1 rounded-xl border border-zinc-200/70 bg-white/80 p-2.5 text-xs dark:border-zinc-700/60 dark:bg-zinc-800/70">
                      {context.pendingQuestions.map((pq) => (
                        <li
                          key={pq.question}
                          className="flex items-center gap-1.5 text-zinc-700 dark:text-zinc-300"
                        >
                          <span className="size-1.5 rounded-full bg-purple-500" />
                          <span className="truncate flex-1">{pq.question}</span>
                          {pq.priority && (
                            <span
                              className={`rounded px-1 text-[9px] font-bold ${
                                pq.priority === "HIGH"
                                  ? "bg-rose-50 text-rose-600 dark:bg-rose-950 dark:text-rose-400"
                                  : "bg-zinc-100 text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300"
                              }`}
                            >
                              {pq.priority}
                            </span>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
