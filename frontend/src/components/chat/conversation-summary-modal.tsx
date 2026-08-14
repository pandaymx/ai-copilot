"use client";

import {
  BookOpen,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  HelpCircle,
  Lightbulb,
  ListTodo,
  Loader2,
  RefreshCw,
  Sparkles,
  Tag,
  X,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  type ConversationSummary,
  generateSessionSummaryApi,
  type KnowledgeCaptureResult,
  saveSessionToKnowledgeApi,
} from "@/lib/api";

interface ConversationSummaryModalProps {
  isOpen: boolean;
  onClose: () => void;
  sessionId: string;
  provider?: string;
  model?: string;
}

export function ConversationSummaryModal({
  isOpen,
  onClose,
  sessionId,
  provider,
  model,
}: ConversationSummaryModalProps) {
  const [summary, setSummary] = useState<ConversationSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [savingKnowledge, setSavingKnowledge] = useState<boolean>(false);
  const [savedKnowledge, setSavedKnowledge] =
    useState<KnowledgeCaptureResult | null>(null);
  const [copied, setCopied] = useState<boolean>(false);

  const fetchSummary = useCallback(
    async (forceRegenerate = false) => {
      if (!sessionId) return;
      setLoading(true);
      setSavedKnowledge(null);
      try {
        const data = await generateSessionSummaryApi(
          sessionId,
          provider,
          model,
        );
        if (data) {
          setSummary(data);
          if (forceRegenerate) {
            toast.success("结构化摘要已重新生成");
          }
        } else {
          toast.error("生成摘要失败，请确保当前会话包含对话内容");
        }
      } catch (err) {
        console.error("生成摘要失败:", err);
        toast.error("请求失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    },
    [sessionId, provider, model],
  );

  useEffect(() => {
    if (isOpen && sessionId) {
      void fetchSummary();
    } else if (!isOpen) {
      setSummary(null);
      setSavedKnowledge(null);
      setCopied(false);
    }
  }, [isOpen, sessionId, fetchSummary]);

  const handleSaveToKnowledge = async () => {
    if (!summary || !sessionId) return;
    setSavingKnowledge(true);
    try {
      const result = await saveSessionToKnowledgeApi(sessionId, summary);
      if (result?.success) {
        setSavedKnowledge(result);
        toast.success(`已沉淀至个人知识库: ${result.fileName}`);
      } else {
        toast.error(result?.error || "知识库沉淀失败");
      }
    } catch (err) {
      console.error("存入知识库失败:", err);
      toast.error("存入知识库失败");
    } finally {
      setSavingKnowledge(false);
    }
  };

  const handleCopyMarkdown = () => {
    if (!summary) return;
    const md = [
      `# 会话知识归档: ${summary.title}`,
      `- 会话编号: ${summary.conversationId}`,
      `- 标签: ${summary.tags.map((t) => `#${t}`).join(" ")}`,
      "",
      "## 核心概述",
      summary.summary,
      "",
      "## 关键决策与结论",
      ...summary.keyDecisions.map((d) => `- 📌 ${d}`),
      "",
      "## 待办清单与行动项",
      ...summary.todos.map((t) => `- [ ] ${t}`),
      "",
      "## 参考资料与关键技术",
      ...summary.references.map((r) => `- 📚 ${r}`),
      "",
      "## 未决问题与后续探索",
      ...summary.openIssues.map((o) => `- ❓ ${o}`),
    ].join("\n");

    navigator.clipboard.writeText(md).then(() => {
      setCopied(true);
      toast.success("Markdown 摘要已复制到剪贴板");
      setTimeout(() => setCopied(false), 2500);
    });
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="relative flex flex-col w-full max-w-3xl max-h-[85vh] rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/95 dark:bg-zinc-900/95 shadow-2xl backdrop-blur-xl overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="summary-title"
      >
        {/* 顶部 Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-200/80 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-950/30">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-gradient-to-tr from-amber-500/10 via-indigo-500/10 to-violet-500/10 border border-indigo-500/20 text-indigo-600 dark:text-indigo-400">
              <Sparkles className="size-5" />
            </div>
            <div>
              <h2
                id="summary-title"
                className="text-base font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2"
              >
                会话摘要与知识沉淀
                {summary && (
                  <span className="text-xs font-normal px-2 py-0.5 rounded-full bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400">
                    {summary.messageCount} 轮对话提炼
                  </span>
                )}
              </h2>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                结构化梳理核心决策、行动项与知识点，并可一键归档至 RAG
                向量知识库
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => fetchSummary(true)}
              disabled={loading}
              title="重新生成摘要"
              className="text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
            >
              <RefreshCw
                className={`size-4 ${loading ? "animate-spin" : ""}`}
              />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={onClose}
              className="text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
            >
              <X className="size-4" />
            </Button>
          </div>
        </div>

        {/* 内容主体 */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20 space-y-3">
              <Loader2 className="size-8 animate-spin text-indigo-500" />
              <p className="text-sm text-zinc-500 dark:text-zinc-400">
                正在深度提炼会话结构化要点与知识决策...
              </p>
            </div>
          ) : summary ? (
            <>
              {/* 会话标题 & 标签 */}
              <div className="p-4 rounded-xl bg-indigo-50/40 dark:bg-indigo-950/20 border border-indigo-200/50 dark:border-indigo-800/40">
                <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
                  {summary.title}
                </h3>
                {summary.tags && summary.tags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mt-2.5">
                    {summary.tags.map((tag) => (
                      <span
                        key={tag}
                        className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-xs font-medium bg-indigo-100/70 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 border border-indigo-200/60 dark:border-indigo-800/50"
                      >
                        <Tag className="size-3" />
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              {/* 1. 核心概述 */}
              <div className="space-y-2">
                <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  <BookOpen className="size-3.5 text-indigo-500" />
                  核心概述 (Executive Summary)
                </div>
                <div className="p-4 rounded-xl bg-zinc-50 dark:bg-zinc-800/40 border border-zinc-200/60 dark:border-zinc-800 text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">
                  {summary.summary}
                </div>
              </div>

              {/* 2 & 3: 关键决策 & 待办清单 双列网格 */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* 关键决策 */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                    <Lightbulb className="size-3.5" />
                    关键决策与结论 (Key Decisions)
                  </div>
                  <div className="p-4 rounded-xl bg-emerald-50/40 dark:bg-emerald-950/20 border border-emerald-200/50 dark:border-emerald-800/30 space-y-2.5 min-h-[140px]">
                    {summary.keyDecisions && summary.keyDecisions.length > 0 ? (
                      summary.keyDecisions.map((decision) => (
                        <div
                          key={decision}
                          className="flex items-start gap-2 text-xs leading-relaxed text-zinc-700 dark:text-zinc-300"
                        >
                          <span className="text-emerald-500 font-bold mt-0.5">
                            •
                          </span>
                          <span>{decision}</span>
                        </div>
                      ))
                    ) : (
                      <p className="text-xs text-zinc-400 italic">
                        本次对话未形成显式技术决策
                      </p>
                    )}
                  </div>
                </div>

                {/* 待办清单 */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-sky-600 dark:text-sky-400">
                    <ListTodo className="size-3.5" />
                    待办与行动项 (Action Items)
                  </div>
                  <div className="p-4 rounded-xl bg-sky-50/40 dark:bg-sky-950/20 border border-sky-200/50 dark:border-sky-800/30 space-y-2.5 min-h-[140px]">
                    {summary.todos && summary.todos.length > 0 ? (
                      summary.todos.map((todo) => (
                        <div
                          key={todo}
                          className="flex items-start gap-2 text-xs leading-relaxed text-zinc-700 dark:text-zinc-300"
                        >
                          <span className="size-3.5 rounded border border-sky-400 dark:border-sky-500 flex items-center justify-center shrink-0 mt-0.5" />
                          <span>{todo}</span>
                        </div>
                      ))
                    ) : (
                      <p className="text-xs text-zinc-400 italic">
                        暂无后续待办
                      </p>
                    )}
                  </div>
                </div>
              </div>

              {/* 4 & 5: 参考资料 & 未决问题 */}
              {(summary.references?.length > 0 ||
                summary.openIssues?.length > 0) && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {summary.references && summary.references.length > 0 && (
                    <div className="space-y-2">
                      <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-violet-600 dark:text-violet-400">
                        <BookOpen className="size-3.5" />
                        参考资料与框架 (References)
                      </div>
                      <div className="p-3.5 rounded-xl bg-violet-50/40 dark:bg-violet-950/20 border border-violet-200/50 dark:border-violet-800/30 space-y-2">
                        {summary.references.map((ref) => (
                          <div
                            key={ref}
                            className="flex items-center gap-1.5 text-xs text-zinc-700 dark:text-zinc-300"
                          >
                            <span className="text-violet-500">•</span>
                            <span>{ref}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {summary.openIssues && summary.openIssues.length > 0 && (
                    <div className="space-y-2">
                      <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-amber-600 dark:text-amber-400">
                        <HelpCircle className="size-3.5" />
                        未决问题与后续探索 (Open Questions)
                      </div>
                      <div className="p-3.5 rounded-xl bg-amber-50/40 dark:bg-amber-950/20 border border-amber-200/50 dark:border-amber-800/30 space-y-2">
                        {summary.openIssues.map((issue) => (
                          <div
                            key={issue}
                            className="flex items-center gap-1.5 text-xs text-zinc-700 dark:text-zinc-300"
                          >
                            <span className="text-amber-500">•</span>
                            <span>{issue}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </>
          ) : (
            <div className="py-12 text-center text-zinc-500">
              暂无摘要数据，请点击右上角刷新按钮重新提炼
            </div>
          )}
        </div>

        {/* 底部操作区 */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-zinc-200/80 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-950/30">
          <Button
            variant="outline"
            size="sm"
            onClick={handleCopyMarkdown}
            disabled={!summary || loading}
            className="gap-1.5 text-xs"
          >
            {copied ? (
              <Check className="size-3.5 text-emerald-500" />
            ) : (
              <Copy className="size-3.5" />
            )}
            {copied ? "已复制 Markdown" : "复制 Markdown"}
          </Button>

          <div className="flex items-center gap-2">
            {savedKnowledge ? (
              <Link
                href="/knowledge"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/20 transition-colors"
              >
                <CheckCircle2 className="size-3.5" />
                已存入知识库 ✓ (前往查看)
                <ExternalLink className="size-3" />
              </Link>
            ) : (
              <Button
                variant="default"
                size="sm"
                onClick={handleSaveToKnowledge}
                disabled={!summary || loading || savingKnowledge}
                className="gap-1.5 text-xs bg-indigo-600 hover:bg-indigo-700 text-white"
              >
                {savingKnowledge ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <Sparkles className="size-3.5" />
                )}
                {savingKnowledge ? "正在向量化写入..." : "存入 RAG 知识库"}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
