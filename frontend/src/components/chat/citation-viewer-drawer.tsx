"use client";

import {
  BookOpen,
  Check,
  Copy,
  FileCode,
  FileSpreadsheet,
  FileText,
  Layers,
  Loader2,
  Sparkles,
  X,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  type DocChunkItem,
  type DocumentCitationItem,
  fetchDocChatChunksApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

interface CitationViewerDrawerProps {
  open: boolean;
  onClose: () => void;
  citations: DocumentCitationItem[];
  activeCitationId?: string;
  onSelectCitation?: (citationId: string) => void;
  conversationId?: string;
}

function getFileIcon(fileName: string) {
  const ext = fileName.split(".").pop()?.toLowerCase() || "";
  if (ext === "pdf") return FileText;
  if (["doc", "docx"].includes(ext)) return FileSpreadsheet;
  if (["md", "markdown", "txt"].includes(ext)) return BookOpen;
  if (["js", "ts", "py", "java", "json"].includes(ext)) return FileCode;
  return FileText;
}

export function CitationViewerDrawer({
  open,
  onClose,
  citations,
  activeCitationId,
  onSelectCitation,
  conversationId,
}: CitationViewerDrawerProps) {
  const [selectedId, setSelectedId] = useState<string>(
    activeCitationId || (citations[0]?.citationId ?? "1"),
  );
  const [copied, setCopied] = useState(false);
  const [chunks, setChunks] = useState<DocChunkItem[]>([]);
  const [loadingChunks, setLoadingChunks] = useState(false);

  useEffect(() => {
    if (activeCitationId) {
      setSelectedId(activeCitationId);
    } else if (citations.length > 0 && !selectedId) {
      setSelectedId(citations[0].citationId);
    }
  }, [activeCitationId, citations, selectedId]);

  const activeCitation =
    citations.find((c) => c.citationId === selectedId) || citations[0];

  const loadChunks = useCallback(async (docId: string, convId?: string) => {
    if (!docId) {
      setChunks([]);
      return;
    }
    setLoadingChunks(true);
    try {
      const data = await fetchDocChatChunksApi(docId, convId);
      setChunks(data);
    } catch {
      setChunks([]);
    } finally {
      setLoadingChunks(false);
    }
  }, []);

  useEffect(() => {
    if (open && activeCitation?.docId) {
      void loadChunks(activeCitation.docId, conversationId);
    }
  }, [open, activeCitation?.docId, conversationId, loadChunks]);

  const handleCopyQuote = (text?: string) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopied(true);
    toast.success("已复制引用原文");
    setTimeout(() => setCopied(false), 2000);
  };

  if (!open || !activeCitation) return null;

  const IconComp = getFileIcon(activeCitation.fileName || "");

  return (
    <div className="fixed inset-0 z-50 flex justify-end animate-in fade-in duration-200">
      {/* 遮罩背景 */}
      <button
        type="button"
        className="fixed inset-0 bg-black/40 backdrop-blur-xs transition-opacity border-0 cursor-default"
        onClick={onClose}
        aria-label="关闭抽屉"
      />

      {/* 侧边滑出抽屉 */}
      <aside className="relative z-10 flex h-full w-full max-w-xl flex-col border-l border-zinc-200/80 bg-white/95 shadow-2xl backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-950/95 transition-transform duration-300">
        {/* 抽屉顶部头部 */}
        <div className="flex items-center justify-between border-b border-zinc-200/80 px-5 py-4 dark:border-zinc-800/80">
          <div className="flex items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
              <BookOpen className="size-5" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                <span>原文引用对照</span>
                <span className="rounded-md bg-indigo-500/10 px-2 py-0.5 text-[11px] font-medium text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-300">
                  Strict Grounding
                </span>
              </h2>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                定位模型回答依据的真实文档段落
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="flex size-8 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
            aria-label="关闭"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* 多引用标签切换器 */}
        {citations.length > 1 && (
          <div className="flex items-center gap-1.5 overflow-x-auto border-b border-zinc-200/60 bg-zinc-50/50 px-5 py-2.5 dark:border-zinc-800/60 dark:bg-zinc-900/30 scrollbar-none">
            <span className="text-[11px] font-medium text-zinc-400 shrink-0">
              引用切片:
            </span>
            {citations.map((cite) => {
              const active = cite.citationId === activeCitation.citationId;
              return (
                <button
                  key={cite.citationId}
                  type="button"
                  onClick={() => {
                    setSelectedId(cite.citationId);
                    onSelectCitation?.(cite.citationId);
                  }}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium transition-all shrink-0",
                    active
                      ? "bg-indigo-600 text-white shadow-xs dark:bg-indigo-500"
                      : "bg-white/80 text-zinc-600 hover:bg-zinc-200/70 dark:bg-zinc-800/80 dark:text-zinc-300 dark:hover:bg-zinc-700",
                  )}
                >
                  <span>[{cite.citationId}]</span>
                  <span className="max-w-[100px] truncate">
                    {cite.fileName}
                  </span>
                  {cite.pageNumber && (
                    <span className="opacity-75 text-[10px]">
                      p.{cite.pageNumber}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}

        {/* 抽屉内容滚动区 */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {/* 当前引用卡片 */}
          <div className="rounded-2xl border border-indigo-500/20 bg-linear-to-br from-indigo-50/60 via-white to-purple-50/40 p-4.5 shadow-xs dark:border-indigo-500/30 dark:from-indigo-950/20 dark:via-zinc-900/40 dark:to-purple-950/10">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="flex size-8 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-xs shrink-0">
                  <IconComp className="size-4" />
                </div>
                <div className="min-w-0">
                  <h3 className="truncate text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {activeCitation.fileName}
                  </h3>
                  <div className="flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                    {activeCitation.pageNumber && (
                      <span className="rounded-md bg-zinc-200/70 px-1.5 py-0.5 font-mono text-[10px] text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                        第 {activeCitation.pageNumber} 页
                      </span>
                    )}
                    {activeCitation.paragraphIndex && (
                      <span className="rounded-md bg-zinc-200/70 px-1.5 py-0.5 font-mono text-[10px] text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                        段落 #{activeCitation.paragraphIndex}
                      </span>
                    )}
                    {activeCitation.similarityScore !== undefined && (
                      <span className="text-[11px] text-emerald-600 dark:text-emerald-400 font-medium">
                        匹配度{" "}
                        {(activeCitation.similarityScore * 100).toFixed(0)}%
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <Button
                size="sm"
                variant="outline"
                onClick={() => handleCopyQuote(activeCitation.snippet)}
                className="h-8 gap-1.5 rounded-lg text-xs shrink-0 border-indigo-200 hover:bg-indigo-50 dark:border-indigo-800 dark:hover:bg-indigo-950/50"
              >
                {copied ? (
                  <>
                    <Check className="size-3.5 text-emerald-500" />
                    <span>已复制</span>
                  </>
                ) : (
                  <>
                    <Copy className="size-3.5 text-indigo-500" />
                    <span>复制摘录</span>
                  </>
                )}
              </Button>
            </div>

            {/* 引用摘录高亮展示 */}
            {activeCitation.snippet && (
              <div className="mt-3.5 rounded-xl border border-indigo-200/70 bg-white/90 p-3.5 shadow-inner dark:border-indigo-900/50 dark:bg-zinc-900/90">
                <div className="flex items-center gap-1.5 text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 mb-1.5">
                  <Sparkles className="size-3.5" />
                  <span>核心回答依据 (Cited Excerpt)</span>
                </div>
                <p className="text-xs leading-relaxed text-zinc-700 dark:text-zinc-300 whitespace-pre-wrap font-sans">
                  {activeCitation.snippet}
                </p>
              </div>
            )}
          </div>

          {/* 完整段落上下文列表 */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                <Layers className="size-4 text-indigo-500" />
                <span>文档完整段落上下文</span>
              </div>
              {chunks.length > 0 && (
                <span className="text-[11px] text-zinc-400">
                  共 {chunks.length} 个切片
                </span>
              )}
            </div>

            {loadingChunks ? (
              <div className="flex flex-col items-center justify-center gap-2 py-12 text-zinc-400 text-xs font-mono">
                <Loader2 className="size-5 animate-spin text-indigo-500" />
                <span>正在加载文档完整段落...</span>
              </div>
            ) : chunks.length === 0 ? (
              <div className="rounded-xl border border-dashed border-zinc-300 p-6 text-center text-xs text-zinc-400 dark:border-zinc-800">
                暂无更多段落上下文，已展示检索摘录
              </div>
            ) : (
              <div className="space-y-2.5">
                {chunks.map((chk) => {
                  const isCurrentTarget =
                    chk.paragraphIndex === activeCitation.paragraphIndex ||
                    (activeCitation.snippet &&
                      chk.content.includes(
                        activeCitation.snippet.slice(0, 30),
                      ));

                  return (
                    <div
                      key={chk.chunkId}
                      className={cn(
                        "group relative rounded-xl border p-3.5 text-xs transition-all duration-200",
                        isCurrentTarget
                          ? "border-indigo-500/80 bg-indigo-50/70 shadow-sm ring-2 ring-indigo-500/20 dark:border-indigo-500/80 dark:bg-indigo-950/30"
                          : "border-zinc-200/80 bg-white/60 hover:bg-zinc-50 dark:border-zinc-800/80 dark:bg-zinc-900/40 dark:hover:bg-zinc-900/70",
                      )}
                    >
                      <div className="flex items-center justify-between text-[10px] text-zinc-400 mb-1.5">
                        <div className="flex items-center gap-1.5 font-mono">
                          <span
                            className={cn(
                              "rounded px-1.5 py-0.5 font-semibold",
                              isCurrentTarget
                                ? "bg-indigo-600 text-white dark:bg-indigo-500"
                                : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400",
                            )}
                          >
                            段落 #{chk.paragraphIndex}
                          </span>
                          {chk.pageNumber && <span>p.{chk.pageNumber}</span>}
                        </div>

                        {isCurrentTarget && (
                          <span className="flex items-center gap-1 font-medium text-indigo-600 dark:text-indigo-400 text-[10px]">
                            <span className="size-1.5 rounded-full bg-indigo-500 animate-ping" />
                            当前引用定位
                          </span>
                        )}
                      </div>

                      <p
                        className={cn(
                          "leading-relaxed whitespace-pre-wrap",
                          isCurrentTarget
                            ? "text-zinc-900 font-medium dark:text-zinc-100"
                            : "text-zinc-600 dark:text-zinc-400",
                        )}
                      >
                        {chk.content}
                      </p>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* 抽屉底部 */}
        <div className="border-t border-zinc-200/80 p-4 bg-zinc-50/80 dark:border-zinc-800/80 dark:bg-zinc-900/50 flex items-center justify-between">
          <p className="text-[11px] text-zinc-400">
            文档对话模式提供 100% 事实溯源与防幻觉验证
          </p>
          <Button
            size="sm"
            variant="ghost"
            onClick={onClose}
            className="text-xs"
          >
            关闭预览
          </Button>
        </div>
      </aside>
    </div>
  );
}
