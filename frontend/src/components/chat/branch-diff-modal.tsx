"use client";

import {
  ArrowRight,
  Columns,
  GitBranch,
  GitMerge,
  Loader2,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  type BranchDiff,
  type BranchSummary,
  diffBranches,
  mergeBranches,
} from "@/lib/branch-api";
import { cn } from "@/lib/utils";

interface BranchDiffModalProps {
  open: boolean;
  onClose: () => void;
  sessionId: string;
  branches: BranchSummary[];
  initialBranchA?: string;
  initialBranchB?: string;
  onMergeSuccess?: () => void;
}

export function BranchDiffModal({
  open,
  onClose,
  sessionId,
  branches,
  initialBranchA,
  initialBranchB,
  onMergeSuccess,
}: BranchDiffModalProps) {
  const [branchA, setBranchA] = useState(
    initialBranchA || (branches[0] ? branches[0].branchId : ""),
  );
  const [branchB, setBranchB] = useState(
    initialBranchB || (branches[1] ? branches[1].branchId : ""),
  );
  const [diff, setDiff] = useState<BranchDiff | null>(null);
  const [loading, setLoading] = useState(false);
  const [merging, setMerging] = useState(false);

  useEffect(() => {
    if (!open || !sessionId || !branchA || !branchB || branchA === branchB) {
      setDiff(null);
      return;
    }
    let isMounted = true;
    setLoading(true);
    diffBranches(sessionId, branchA, branchB)
      .then((data) => {
        if (isMounted) setDiff(data);
      })
      .catch((e: unknown) => {
        toast.error(e instanceof Error ? e.message : "获取分支差异失败");
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, [open, sessionId, branchA, branchB]);

  const handleMerge = async () => {
    if (!branchA || !branchB) return;
    try {
      setMerging(true);
      const res = await mergeBranches(sessionId, branchB, branchA);
      if (res.success) {
        toast.success(res.message || "分支合并成功！");
        onMergeSuccess?.();
        onClose();
      } else {
        toast.error(res.message || "合并失败");
      }
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "合并异常");
    } finally {
      setMerging(false);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="relative flex flex-col w-full max-w-5xl h-[700px] max-h-[92vh] rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden">
        {/* 顶部栏 */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-950/40">
          <div className="flex items-center gap-2">
            <Columns className="size-4.5 text-indigo-500" />
            <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
              分支并排对比与合并 (Branch Diff & Merge)
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* 分支选择栏 */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-100/50 dark:bg-zinc-900/50 gap-4 text-xs">
          <div className="flex items-center gap-2 flex-1">
            <span className="font-semibold text-zinc-500">基准分支 (A):</span>
            <select
              value={branchA}
              onChange={(e) => setBranchA(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 text-xs focus:outline-hidden font-medium"
            >
              {branches.map((b) => (
                <option key={b.branchId} value={b.branchId}>
                  {b.branchLabel} ({b.messageCount} 条消息)
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-1.5 text-zinc-400">
            <ArrowRight className="size-4" />
          </div>

          <div className="flex items-center gap-2 flex-1 justify-end">
            <span className="font-semibold text-zinc-500">对比分支 (B):</span>
            <select
              value={branchB}
              onChange={(e) => setBranchB(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 text-xs focus:outline-hidden font-medium"
            >
              {branches.map((b) => (
                <option key={b.branchId} value={b.branchId}>
                  {b.branchLabel} ({b.messageCount} 条消息)
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* 对比主体分栏 */}
        <div className="flex-1 flex min-h-0 divide-x divide-zinc-200 dark:divide-zinc-800 overflow-hidden">
          {loading ? (
            <div className="w-full flex flex-col items-center justify-center py-20 gap-2 text-zinc-400 text-xs">
              <Loader2 className="size-6 animate-spin text-indigo-500" />
              <span>正在计算分支消息差异...</span>
            </div>
          ) : branchA === branchB ? (
            <div className="w-full flex flex-col items-center justify-center py-20 gap-2 text-zinc-400 text-xs">
              <GitBranch className="size-8 text-zinc-300 dark:text-zinc-700" />
              <span>请选择两个不同的分支以查看差异</span>
            </div>
          ) : (
            <>
              {/* 分支 A 消息列表 */}
              <div className="flex-1 flex flex-col min-h-0 overflow-y-auto p-4 space-y-3 bg-zinc-50/30 dark:bg-zinc-950/20">
                <div className="text-xs font-semibold text-zinc-500 sticky top-0 bg-white/90 dark:bg-zinc-900/90 py-1 backdrop-blur-xs">
                  分支 A 消息 ({diff?.branchAMessages.length || 0})
                </div>
                {diff?.branchAMessages.map((m) => (
                  <div
                    key={m.id}
                    className={cn(
                      "p-3 rounded-xl border text-xs space-y-1 transition-all",
                      m.diffStatus === "MODIFIED"
                        ? "border-amber-300 dark:border-amber-700 bg-amber-50/40 dark:bg-amber-950/20"
                        : "border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900",
                    )}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-[11px] uppercase tracking-wider text-zinc-400">
                        {m.role}
                      </span>
                      {m.diffStatus === "MODIFIED" && (
                        <span className="px-1.5 py-0.2 rounded text-[10px] bg-amber-100 dark:bg-amber-950 text-amber-700 dark:text-amber-300 font-medium">
                          独有/不同
                        </span>
                      )}
                    </div>
                    <p className="whitespace-pre-wrap font-mono text-[11px] leading-relaxed text-zinc-700 dark:text-zinc-300">
                      {m.content}
                    </p>
                  </div>
                ))}
              </div>

              {/* 分支 B 消息列表 */}
              <div className="flex-1 flex flex-col min-h-0 overflow-y-auto p-4 space-y-3 bg-zinc-50/30 dark:bg-zinc-950/20">
                <div className="text-xs font-semibold text-zinc-500 sticky top-0 bg-white/90 dark:bg-zinc-900/90 py-1 backdrop-blur-xs">
                  分支 B 消息 ({diff?.branchBMessages.length || 0})
                </div>
                {diff?.branchBMessages.map((m) => (
                  <div
                    key={m.id}
                    className={cn(
                      "p-3 rounded-xl border text-xs space-y-1 transition-all",
                      m.diffStatus === "ADDED"
                        ? "border-emerald-300 dark:border-emerald-700 bg-emerald-50/40 dark:bg-emerald-950/20"
                        : "border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900",
                    )}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-[11px] uppercase tracking-wider text-zinc-400">
                        {m.role}
                      </span>
                      {m.diffStatus === "ADDED" && (
                        <span className="px-1.5 py-0.2 rounded text-[10px] bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300 font-medium">
                          新增
                        </span>
                      )}
                    </div>
                    <p className="whitespace-pre-wrap font-mono text-[11px] leading-relaxed text-zinc-700 dark:text-zinc-300">
                      {m.content}
                    </p>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {/* 底部合并操作栏 */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-950/40 text-xs">
          <div className="text-zinc-500">
            合并将把{" "}
            <span className="font-semibold text-indigo-500">分支 B</span>{" "}
            的独有增量消息复制并追加至{" "}
            <span className="font-semibold text-indigo-500">分支 A</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 text-xs font-medium hover:bg-zinc-100 dark:hover:bg-zinc-800"
            >
              关闭
            </button>
            <button
              type="button"
              disabled={merging || branchA === branchB}
              onClick={handleMerge}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 disabled:opacity-40 shadow-sm"
            >
              {merging ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <GitMerge className="size-3.5" />
              )}
              <span>将 B 合并至 A</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
