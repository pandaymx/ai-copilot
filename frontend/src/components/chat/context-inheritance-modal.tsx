"use client";

import {
  Check,
  Code2,
  FileText,
  GitFork,
  HelpCircle,
  Layers,
  Lightbulb,
  Loader2,
  Scale,
  Search,
  Sparkles,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  exportSessionContextApi,
  type ImportContextResponse,
  type InheritedContext,
  importSessionContextApi,
} from "@/lib/api";
import type { ChatSession } from "./sidebar";

interface ContextInheritanceModalProps {
  isOpen: boolean;
  onClose: () => void;
  sessions: ChatSession[];
  currentSessionId?: string | null;
  initialSourceSessionId?: string | null;
  onSuccess: (response: ImportContextResponse) => void;
}

export function ContextInheritanceModal({
  isOpen,
  onClose,
  sessions,
  currentSessionId,
  initialSourceSessionId,
  onSuccess,
}: ContextInheritanceModalProps) {
  const [sourceSessionId, setSourceSessionId] = useState<string>(
    initialSourceSessionId || "",
  );
  const [search, setSearch] = useState("");
  const [loadingExtract, setLoadingExtract] = useState(false);
  const [context, setContext] = useState<InheritedContext | null>(null);

  // 模块勾选状态
  const [selectedModules, setSelectedModules] = useState<string[]>([
    "summary",
    "decisions",
    "code",
    "files",
    "questions",
    "entities",
  ]);

  // 导入目标： "NEW" (新建会话) 或 "CURRENT" (注入当前会话)
  const [destination, setDestination] = useState<"NEW" | "CURRENT">("NEW");
  const [customNote, setCustomNote] = useState("");
  const [targetTitle, setTargetTitle] = useState("");
  const [importing, setImporting] = useState(false);

  // 初始化 sourceSessionId
  useEffect(() => {
    if (initialSourceSessionId) {
      setSourceSessionId(initialSourceSessionId);
    } else if (!sourceSessionId && sessions.length > 0) {
      // 默认选第一个有非当前会话
      const other = sessions.find((s) => s.id !== currentSessionId);
      if (other) setSourceSessionId(other.id);
    }
  }, [initialSourceSessionId, sessions, currentSessionId, sourceSessionId]);

  // 当选择源会话后，触发提取
  const handleExtract = useCallback(async (srcId: string) => {
    if (!srcId) return;
    setLoadingExtract(true);
    setContext(null);
    try {
      const data = await exportSessionContextApi(srcId);
      if (data) {
        setContext(data);
        if (data.sourceSessionTitle) {
          setTargetTitle(`继承: ${data.sourceSessionTitle}`);
        }
      } else {
        toast.error("提取源会话上下文失败，该会话可能无有效对话记录");
      }
    } catch (_e) {
      toast.error("提取上下文请求发生异常");
    } finally {
      setLoadingExtract(false);
    }
  }, []);

  // 选中源会话时自动提取
  useEffect(() => {
    if (isOpen && sourceSessionId) {
      handleExtract(sourceSessionId);
    }
  }, [isOpen, sourceSessionId, handleExtract]);

  const filteredSessions = useMemo(() => {
    return sessions.filter(
      (s) =>
        s.id !== currentSessionId &&
        (s.title || "").toLowerCase().includes(search.toLowerCase()),
    );
  }, [sessions, currentSessionId, search]);

  const toggleModule = (mod: string) => {
    setSelectedModules((prev) =>
      prev.includes(mod) ? prev.filter((m) => m !== mod) : [...prev, mod],
    );
  };

  const handleConfirmImport = async () => {
    if (!context) {
      toast.error("请先提取源会话上下文");
      return;
    }

    setImporting(true);
    try {
      const targetId =
        destination === "CURRENT" && currentSessionId
          ? currentSessionId
          : `session-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

      const resp = await importSessionContextApi(targetId, {
        context,
        selectedModules,
        customNote: customNote.trim() || undefined,
        targetTitle: targetTitle.trim() || undefined,
      });

      if (resp?.success) {
        toast.success(
          destination === "CURRENT"
            ? "已成功注入上下文到当前会话！"
            : "已成功创建并继承上下文新会话！",
        );
        onSuccess(resp);
        onClose();
      } else {
        toast.error("导入上下文失败");
      }
    } catch {
      toast.error("导入上下文请求异常");
    } finally {
      setImporting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-3xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
        {/* 头部 */}
        <div className="flex items-center justify-between border-b border-zinc-200/80 px-6 py-4 dark:border-zinc-800">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white shadow-md shadow-indigo-500/20">
              <GitFork className="size-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                  跨会话上下文继承 (Context Inheritance)
                </h3>
                <span className="rounded-md bg-indigo-500/10 px-2 py-0.5 text-[10px] font-bold text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
                  5维结构化
                </span>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                将前序会话的关键决策、核心代码、文件引用与待办无缝迁移
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
          >
            <X className="size-5" />
          </button>
        </div>

        {/* 主体两栏布局 */}
        <div className="grid flex-1 grid-cols-1 overflow-y-auto md:grid-cols-12 divide-y md:divide-y-0 md:divide-x divide-zinc-200/80 dark:divide-zinc-800">
          {/* 左栏：源会话选择 (4列) */}
          <div className="p-4 md:col-span-4 space-y-3 bg-zinc-50/50 dark:bg-zinc-900/30">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-zinc-800 dark:text-zinc-200">
                1. 选择源会话
              </span>
              <span className="text-[10px] text-zinc-400">
                {filteredSessions.length} 个可选
              </span>
            </div>

            <div className="relative">
              <Search className="absolute left-2.5 top-2.5 size-3.5 text-zinc-400" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="搜索历史会话..."
                className="w-full rounded-xl border border-zinc-200 bg-white py-1.5 pl-8 pr-3 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
              />
            </div>

            <div className="max-h-64 md:max-h-[460px] space-y-1.5 overflow-y-auto pr-1 scrollbar-hidden">
              {filteredSessions.length === 0 ? (
                <div className="py-8 text-center text-xs text-zinc-400">
                  未找到其他历史会话
                </div>
              ) : (
                filteredSessions.map((s) => {
                  const selected = s.id === sourceSessionId;
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => {
                        setSourceSessionId(s.id);
                        handleExtract(s.id);
                      }}
                      className={`group flex w-full items-center justify-between rounded-xl p-2.5 text-left text-xs transition-all ${
                        selected
                          ? "bg-indigo-500 text-white shadow-xs"
                          : "bg-white hover:bg-zinc-100 dark:bg-zinc-800/80 dark:hover:bg-zinc-800 text-zinc-700 dark:text-zinc-300"
                      }`}
                    >
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold">
                          {s.title || "未命名会话"}
                        </p>
                        <p
                          className={`text-[10px] truncate ${
                            selected
                              ? "text-indigo-100"
                              : "text-zinc-400 dark:text-zinc-500"
                          }`}
                        >
                          {new Date(s.updatedAt).toLocaleDateString()}
                        </p>
                      </div>
                      {selected && <Check className="size-4 shrink-0" />}
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* 右栏：结构化预览与勾选 (8列) */}
          <div className="p-5 md:col-span-8 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-zinc-800 dark:text-zinc-200">
                  2. 结构化上下文预览与按需勾选
                </span>
                {context && (
                  <span className="rounded bg-emerald-500/10 px-2 py-0.5 text-[10px] font-bold text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400">
                    预估 ~{context.estimatedTokens} Tokens
                  </span>
                )}
              </div>

              {context && (
                <button
                  type="button"
                  onClick={() => {
                    if (selectedModules.length === 6) {
                      setSelectedModules([]);
                    } else {
                      setSelectedModules([
                        "summary",
                        "decisions",
                        "code",
                        "files",
                        "questions",
                        "entities",
                      ]);
                    }
                  }}
                  className="text-[11px] font-medium text-indigo-600 hover:underline dark:text-indigo-400"
                >
                  {selectedModules.length === 6 ? "取消全选" : "全选所有模块"}
                </button>
              )}
            </div>

            {loadingExtract ? (
              <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="size-8 animate-spin text-indigo-500" />
                <p className="mt-3 text-xs font-medium text-zinc-600 dark:text-zinc-400">
                  正在进行双层结构化提炼 (5维抽取)...
                </p>
                <p className="mt-1 text-[11px] text-zinc-400">
                  自动隔离 Prompt 注入并压缩会话噪音
                </p>
              </div>
            ) : !context ? (
              <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-zinc-200 py-16 text-center dark:border-zinc-800">
                <Layers className="size-8 text-zinc-300 dark:text-zinc-600" />
                <p className="mt-2 text-xs text-zinc-500">
                  请在左侧选择需要继承的源会话
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {/* 1. 主旨概述 */}
                <div
                  className={`rounded-xl border p-3 transition-all ${
                    selectedModules.includes("summary")
                      ? "border-indigo-300 bg-indigo-50/30 dark:border-indigo-800 dark:bg-indigo-950/20"
                      : "border-zinc-200 opacity-60 dark:border-zinc-800"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-xs font-bold text-zinc-900 dark:text-zinc-100">
                      <Lightbulb className="size-4 text-amber-500" />
                      <span>核心主旨概述</span>
                    </span>
                    <input
                      type="checkbox"
                      checked={selectedModules.includes("summary")}
                      onChange={() => toggleModule("summary")}
                      className="rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                    />
                  </div>
                  <p className="mt-1.5 text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
                    {context.contextSummary || "无概述"}
                  </p>
                </div>

                {/* 2. 关键决策 */}
                {context.keyDecisions && context.keyDecisions.length > 0 && (
                  <div
                    className={`rounded-xl border p-3 transition-all ${
                      selectedModules.includes("decisions")
                        ? "border-indigo-300 bg-indigo-50/30 dark:border-indigo-800 dark:bg-indigo-950/20"
                        : "border-zinc-200 opacity-60 dark:border-zinc-800"
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-xs font-bold text-zinc-900 dark:text-zinc-100">
                        <Scale className="size-4 text-indigo-500" />
                        <span>
                          关键架构决策 ({context.keyDecisions.length})
                        </span>
                      </span>
                      <input
                        type="checkbox"
                        checked={selectedModules.includes("decisions")}
                        onChange={() => toggleModule("decisions")}
                        className="rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                      />
                    </div>
                    <div className="mt-2 space-y-1">
                      {context.keyDecisions.map((kd) => (
                        <div
                          key={kd.decision}
                          className="flex items-start gap-2 text-xs text-zinc-700 dark:text-zinc-300"
                        >
                          <span className="size-1.5 shrink-0 rounded-full bg-indigo-500 mt-1.5" />
                          <span>
                            <strong>{kd.decision}</strong>
                            {kd.rationale ? ` (${kd.rationale})` : ""}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 3. 核心代码 */}
                {context.codeSnippets && context.codeSnippets.length > 0 && (
                  <div
                    className={`rounded-xl border p-3 transition-all ${
                      selectedModules.includes("code")
                        ? "border-indigo-300 bg-indigo-50/30 dark:border-indigo-800 dark:bg-indigo-950/20"
                        : "border-zinc-200 opacity-60 dark:border-zinc-800"
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-xs font-bold text-zinc-900 dark:text-zinc-100">
                        <Code2 className="size-4 text-emerald-500" />
                        <span>
                          核心代码片段 ({context.codeSnippets.length})
                        </span>
                      </span>
                      <input
                        type="checkbox"
                        checked={selectedModules.includes("code")}
                        onChange={() => toggleModule("code")}
                        className="rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                      />
                    </div>
                    <div className="mt-2 space-y-1.5 max-h-36 overflow-y-auto">
                      {context.codeSnippets.map((cs, idx) => (
                        <div
                          key={`${cs.language}-${idx}`}
                          className="rounded-lg bg-zinc-950 p-2 font-mono text-[11px] text-emerald-300"
                        >
                          <div className="text-[9px] text-zinc-500">
                            {cs.description || cs.language}
                          </div>
                          <pre className="truncate">
                            {cs.code.slice(0, 100)}
                          </pre>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 4. 文件与未决问题 */}
                <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                  {context.fileReferences &&
                    context.fileReferences.length > 0 && (
                      <div
                        className={`rounded-xl border p-3 transition-all ${
                          selectedModules.includes("files")
                            ? "border-indigo-300 bg-indigo-50/30 dark:border-indigo-800 dark:bg-indigo-950/20"
                            : "border-zinc-200 opacity-60 dark:border-zinc-800"
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="flex items-center gap-1.5 text-xs font-bold text-zinc-900 dark:text-zinc-100">
                            <FileText className="size-4 text-blue-500" />
                            <span>
                              文件引用 ({context.fileReferences.length})
                            </span>
                          </span>
                          <input
                            type="checkbox"
                            checked={selectedModules.includes("files")}
                            onChange={() => toggleModule("files")}
                            className="rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                          />
                        </div>
                        <ul className="mt-1.5 space-y-1 text-[11px] text-zinc-600 dark:text-zinc-400 font-mono">
                          {context.fileReferences.slice(0, 3).map((f) => (
                            <li key={f.fileName} className="truncate">
                              • {f.fileName}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                  {context.pendingQuestions &&
                    context.pendingQuestions.length > 0 && (
                      <div
                        className={`rounded-xl border p-3 transition-all ${
                          selectedModules.includes("questions")
                            ? "border-indigo-300 bg-indigo-50/30 dark:border-indigo-800 dark:bg-indigo-950/20"
                            : "border-zinc-200 opacity-60 dark:border-zinc-800"
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="flex items-center gap-1.5 text-xs font-bold text-zinc-900 dark:text-zinc-100">
                            <HelpCircle className="size-4 text-purple-500" />
                            <span>
                              待办跟进 ({context.pendingQuestions.length})
                            </span>
                          </span>
                          <input
                            type="checkbox"
                            checked={selectedModules.includes("questions")}
                            onChange={() => toggleModule("questions")}
                            className="rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                          />
                        </div>
                        <ul className="mt-1.5 space-y-1 text-[11px] text-zinc-600 dark:text-zinc-400">
                          {context.pendingQuestions.slice(0, 3).map((q) => (
                            <li key={q.question} className="truncate">
                              • {q.question}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                </div>

                {/* 3. 目标配置与自定义附加说明 */}
                <div className="rounded-xl border border-zinc-200 bg-zinc-50/60 p-3.5 space-y-3 dark:border-zinc-800 dark:bg-zinc-900/50">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="text-xs font-bold text-zinc-800 dark:text-zinc-200">
                      3. 导入目标与附加备忘
                    </span>
                    <div className="flex rounded-lg bg-zinc-200/70 p-0.5 dark:bg-zinc-800">
                      <button
                        type="button"
                        onClick={() => setDestination("NEW")}
                        className={`rounded-md px-2.5 py-1 text-xs font-semibold transition-all ${
                          destination === "NEW"
                            ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                            : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400"
                        }`}
                      >
                        创建全新会话
                      </button>
                      {currentSessionId && (
                        <button
                          type="button"
                          onClick={() => setDestination("CURRENT")}
                          className={`rounded-md px-2.5 py-1 text-xs font-semibold transition-all ${
                            destination === "CURRENT"
                              ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                              : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400"
                          }`}
                        >
                          注入当前会话
                        </button>
                      )}
                    </div>
                  </div>

                  {destination === "NEW" && (
                    <input
                      type="text"
                      value={targetTitle}
                      onChange={(e) => setTargetTitle(e.target.value)}
                      placeholder="新会话标题 (如 继承: xxx)"
                      className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                    />
                  )}

                  <textarea
                    value={customNote}
                    onChange={(e) => setCustomNote(e.target.value)}
                    rows={2}
                    placeholder="可选附加约束与备忘（如：请基于上述方案继续进行第 2 阶段接口实现）"
                    className="w-full rounded-lg border border-zinc-200 bg-white p-2.5 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  />
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between border-t border-zinc-200/80 bg-zinc-50/50 px-6 py-3.5 dark:border-zinc-800 dark:bg-zinc-900/50">
          <div className="flex items-center gap-1.5 text-xs text-zinc-500">
            <span className="size-2 rounded-full bg-emerald-500" />
            <span>ChatMemory 置顶安全注入</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-zinc-200 bg-white px-4 py-2 text-xs font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700 transition-colors"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleConfirmImport}
              disabled={importing || !context || selectedModules.length === 0}
              className="flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 px-5 py-2 text-xs font-bold text-white shadow-md shadow-indigo-500/20 hover:from-indigo-600 hover:to-purple-700 transition-all disabled:opacity-50"
            >
              {importing ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  <span>正在注入并创建...</span>
                </>
              ) : (
                <>
                  <Sparkles className="size-3.5" />
                  <span>确认继承并导入</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
