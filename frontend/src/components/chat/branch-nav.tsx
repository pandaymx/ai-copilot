"use client";

import { ChevronDown, Columns, GitBranch, Plus, Workflow } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  type BranchSummary,
  createBranch,
  fetchBranches,
} from "@/lib/branch-api";
import { cn } from "@/lib/utils";
import { BranchDiffModal } from "./branch-diff-modal";
import { BranchTreeModal } from "./branch-tree-modal";

interface BranchNavProps {
  sessionId: string;
  activeBranchId: string;
  onSelectBranch: (branchId: string) => void;
  className?: string;
}

export function BranchNav({
  sessionId,
  activeBranchId,
  onSelectBranch,
  className,
}: BranchNavProps) {
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [treeModalOpen, setTreeModalOpen] = useState(false);
  const [diffModalOpen, setDiffModalOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  const loadBranches = async () => {
    if (!sessionId) return;
    try {
      const data = await fetchBranches(sessionId);
      setBranches(data);
    } catch {
      // 容错处理
    }
  };

  useEffect(() => {
    let isMounted = true;
    if (!sessionId) return;
    fetchBranches(sessionId)
      .then((data) => {
        if (isMounted) setBranches(data);
      })
      .catch(() => {});
    return () => {
      isMounted = false;
    };
  }, [sessionId]);

  const activeBranch = useMemo(() => {
    return (
      branches.find((b) => b.branchId === activeBranchId) ||
      branches[0] || {
        branchId: "main",
        branchLabel: "主线",
        messageCount: 0,
      }
    );
  }, [branches, activeBranchId]);

  const handleQuickFork = async () => {
    try {
      const label = `探索分支 ${branches.length + 1}`;
      const res = await createBranch(sessionId, undefined, label);
      toast.success(`已新建分支: ${res.branchLabel}`);
      await loadBranches();
      onSelectBranch(res.branchId);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "创建分支失败");
    }
  };

  if (!sessionId) return null;

  return (
    <div className={cn("relative inline-flex items-center gap-1.5", className)}>
      {/* 当前分支下拉菜单触发器 */}
      <div className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-zinc-100 dark:bg-zinc-800/80 hover:bg-zinc-200 dark:hover:bg-zinc-800 text-xs font-semibold text-zinc-700 dark:text-zinc-300 border border-zinc-200/80 dark:border-zinc-700/60 shadow-2xs transition-colors"
          title="切换探索分支"
        >
          <GitBranch className="size-3.5 text-indigo-500" />
          <span className="max-w-28 truncate">{activeBranch.branchLabel}</span>
          <ChevronDown className="size-3 text-zinc-400" />
        </button>

        {menuOpen && (
          <>
            <button
              type="button"
              aria-label="关闭分支菜单"
              className="fixed inset-0 z-40 cursor-default bg-transparent border-0"
              onClick={() => setMenuOpen(false)}
            />
            <div className="absolute left-0 mt-1.5 w-56 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-xl z-50 p-1.5 text-xs space-y-1 animate-in fade-in">
              <div className="px-2 py-1 text-[10px] font-bold text-zinc-400 uppercase tracking-wider">
                对话分支列表
              </div>
              <div className="max-h-48 overflow-y-auto space-y-0.5">
                {branches.map((b) => (
                  <button
                    key={b.branchId}
                    type="button"
                    onClick={() => {
                      onSelectBranch(b.branchId);
                      setMenuOpen(false);
                    }}
                    className={cn(
                      "w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg text-left transition-colors",
                      b.branchId === activeBranchId
                        ? "bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 font-semibold"
                        : "hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-700 dark:text-zinc-300",
                    )}
                  >
                    <div className="flex items-center gap-1.5 truncate">
                      <GitBranch className="size-3 shrink-0" />
                      <span className="truncate">{b.branchLabel}</span>
                    </div>
                    <span className="text-[10px] text-zinc-400">
                      {b.messageCount}条
                    </span>
                  </button>
                ))}
              </div>

              <div className="border-t border-zinc-100 dark:border-zinc-800 pt-1 space-y-0.5">
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    void handleQuickFork();
                  }}
                  className="w-full flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-800 text-indigo-600 dark:text-indigo-400 font-medium"
                >
                  <Plus className="size-3.5" />
                  <span>新建空白分支</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    setTreeModalOpen(true);
                  }}
                  className="w-full flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-600 dark:text-zinc-300"
                >
                  <Workflow className="size-3.5" />
                  <span>打开版本树视图</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    setDiffModalOpen(true);
                  }}
                  className="w-full flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-600 dark:text-zinc-300"
                >
                  <Columns className="size-3.5" />
                  <span>分支差异对比 (Diff)</span>
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* 快捷树形视图按钮 */}
      <button
        type="button"
        onClick={() => setTreeModalOpen(true)}
        className="p-1 rounded-lg text-zinc-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
        title="打开分支版本树"
      >
        <Workflow className="size-3.5" />
      </button>

      {/* 分支树弹窗 */}
      <BranchTreeModal
        open={treeModalOpen}
        onClose={() => setTreeModalOpen(false)}
        sessionId={sessionId}
        activeBranchId={activeBranchId}
        onSelectBranch={(bId) => {
          onSelectBranch(bId);
          void loadBranches();
        }}
      />

      {/* 分支对比弹窗 */}
      <BranchDiffModal
        open={diffModalOpen}
        onClose={() => setDiffModalOpen(false)}
        sessionId={sessionId}
        branches={branches}
        initialBranchA={activeBranchId}
        onMergeSuccess={() => void loadBranches()}
      />
    </div>
  );
}
