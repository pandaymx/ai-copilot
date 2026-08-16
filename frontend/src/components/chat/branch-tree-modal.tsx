"use client";

import { Columns, GitBranch, Loader2, Plus, Workflow, X } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  type ConversationTree,
  createBranch,
  fetchConversationTree,
  type TreeNode,
} from "@/lib/branch-api";
import { cn } from "@/lib/utils";
import { BranchDiffModal } from "./branch-diff-modal";

interface BranchTreeModalProps {
  open: boolean;
  onClose: () => void;
  sessionId: string;
  activeBranchId: string;
  onSelectBranch: (branchId: string) => void;
}

export function BranchTreeModal({
  open,
  onClose,
  sessionId,
  activeBranchId,
  onSelectBranch,
}: BranchTreeModalProps) {
  const [tree, setTree] = useState<ConversationTree | null>(null);
  const [loading, setLoading] = useState(true);
  const [diffOpen, setDiffOpen] = useState(false);
  const [newBranchLabel, setNewBranchLabel] = useState("");
  const [creatingBranch, setCreatingBranch] = useState(false);

  const loadTree = async () => {
    if (!sessionId) return;
    try {
      setLoading(true);
      const data = await fetchConversationTree(sessionId, activeBranchId);
      setTree(data);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "加载分支版本树失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let isMounted = true;
    if (!open || !sessionId) return;
    setLoading(true);
    fetchConversationTree(sessionId, activeBranchId)
      .then((data) => {
        if (isMounted) setTree(data);
      })
      .catch((e: unknown) => {
        toast.error(e instanceof Error ? e.message : "加载分支版本树失败");
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, [open, sessionId, activeBranchId]);

  const handleCreateNewBranch = async () => {
    if (!newBranchLabel.trim()) {
      toast.error("请输入新分支名称");
      return;
    }
    try {
      setCreatingBranch(true);
      const res = await createBranch(
        sessionId,
        undefined,
        newBranchLabel.trim(),
      );
      toast.success(`已创建分支: ${res.branchLabel}`);
      setNewBranchLabel("");
      await loadTree();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "创建分支失败");
    } finally {
      setCreatingBranch(false);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="relative flex flex-col w-full max-w-5xl h-[720px] max-h-[92vh] rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden">
        {/* 顶部标题栏 */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-950/40">
          <div className="flex items-center gap-2">
            <GitBranch className="size-4.5 text-indigo-500" />
            <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
              对话分支与版本树 (Conversation Version Tree)
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setDiffOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xs font-semibold text-zinc-700 dark:text-zinc-300 transition-colors shadow-2xs"
            >
              <Columns className="size-3.5 text-indigo-500" />
              <span>并排对比分支 (Diff)</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              className="p-1 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        {/* 主体分栏 */}
        <div className="flex-1 flex min-h-0 divide-x divide-zinc-200 dark:divide-zinc-800 overflow-hidden">
          {/* 左侧分支列表与新建 */}
          <div className="w-1/3 flex flex-col min-h-0 bg-zinc-50/40 dark:bg-zinc-950/20 p-4 space-y-4">
            {/* 新建分支输入 */}
            <div className="space-y-1.5">
              <label
                htmlFor="new-branch-name"
                className="text-xs font-semibold text-zinc-600 dark:text-zinc-400"
              >
                新建探索分支:
              </label>
              <div className="flex items-center gap-2">
                <input
                  id="new-branch-name"
                  type="text"
                  placeholder="如：方案 B (微服务架构)..."
                  value={newBranchLabel}
                  onChange={(e) => setNewBranchLabel(e.target.value)}
                  className="flex-1 px-3 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-xs focus:outline-hidden"
                />
                <button
                  type="button"
                  disabled={creatingBranch || !newBranchLabel.trim()}
                  onClick={handleCreateNewBranch}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-700 disabled:opacity-40"
                >
                  {creatingBranch ? (
                    <Loader2 className="size-3 animate-spin" />
                  ) : (
                    <Plus className="size-3.5" />
                  )}
                  <span>创建</span>
                </button>
              </div>
            </div>

            {/* 分支列表 */}
            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              <div className="text-xs font-semibold text-zinc-500">
                全部活跃分支 ({tree?.branches.length || 0})
              </div>
              {tree?.branches.map((b) => {
                const isActive = b.branchId === activeBranchId;
                return (
                  <div
                    key={b.branchId}
                    className={cn(
                      "p-3 rounded-2xl border transition-all space-y-1.5",
                      isActive
                        ? "border-indigo-500 bg-indigo-50/60 dark:bg-indigo-950/40 shadow-xs"
                        : "border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 hover:border-zinc-300",
                    )}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <GitBranch
                          className={cn(
                            "size-3.5",
                            isActive ? "text-indigo-600" : "text-zinc-400",
                          )}
                        />
                        <span className="text-xs font-bold text-zinc-900 dark:text-zinc-100">
                          {b.branchLabel}
                        </span>
                      </div>
                      <span className="text-[10px] text-zinc-400">
                        {b.messageCount} 条消息
                      </span>
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      {isActive ? (
                        <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-indigo-600 text-white">
                          当前使用中
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            onSelectBranch(b.branchId);
                            onClose();
                          }}
                          className="px-2 py-0.5 rounded-md text-[11px] font-medium text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 dark:hover:bg-indigo-950/60 transition-colors"
                        >
                          切换至此分支 →
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* 右侧树形节点流水展示 */}
          <div className="flex-1 flex flex-col min-h-0 overflow-y-auto p-5 space-y-4 bg-zinc-50/20 dark:bg-zinc-950/10">
            <div className="text-xs font-semibold text-zinc-500 flex items-center gap-1.5">
              <Workflow className="size-4 text-indigo-500" />
              <span>对话有向树节点全景</span>
            </div>

            {loading ? (
              <div className="py-20 flex flex-col items-center justify-center gap-2 text-zinc-400 text-xs">
                <Loader2 className="size-6 animate-spin text-indigo-500" />
                <span>正在加载会话节点树...</span>
              </div>
            ) : tree?.rootNodes.length === 0 ? (
              <div className="py-20 text-center text-xs text-zinc-400">
                暂无分支节点数据
              </div>
            ) : (
              <div className="space-y-3">
                {tree?.rootNodes.map((node) => (
                  <TreeNodeRenderer
                    key={node.id}
                    node={node}
                    activeBranchId={activeBranchId}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 分支并排对比弹窗 */}
      {tree && (
        <BranchDiffModal
          open={diffOpen}
          onClose={() => setDiffOpen(false)}
          sessionId={sessionId}
          branches={tree.branches}
          initialBranchA={activeBranchId}
          onMergeSuccess={() => void loadTree()}
        />
      )}
    </div>
  );
}

function TreeNodeRenderer({
  node,
  activeBranchId,
  depth = 0,
}: {
  node: TreeNode;
  activeBranchId: string;
  depth?: number;
}) {
  const isSelfActive = node.branchId === activeBranchId;

  return (
    <div
      className={cn(
        "relative pl-4 space-y-2",
        depth > 0 &&
          "border-l-2 border-indigo-200 dark:border-indigo-900/60 ml-2",
      )}
    >
      <div
        className={cn(
          "p-3 rounded-xl border text-xs space-y-1 transition-all",
          isSelfActive
            ? "border-indigo-300 dark:border-indigo-700 bg-white dark:bg-zinc-900 shadow-xs"
            : "border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-950/50 opacity-80",
        )}
      >
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1.5">
            <span className="px-1.5 py-0.2 rounded text-[10px] uppercase font-bold bg-zinc-100 dark:bg-zinc-800 text-zinc-500">
              {node.role}
            </span>
            <span className="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400">
              [{node.branchLabel}]
            </span>
          </div>
          <span className="text-[10px] text-zinc-400">
            {new Date(node.createdAt).toLocaleTimeString()}
          </span>
        </div>
        <p className="whitespace-pre-wrap font-mono text-[11px] text-zinc-700 dark:text-zinc-300 line-clamp-2">
          {node.content}
        </p>
      </div>

      {node.children && node.children.length > 0 && (
        <div className="space-y-2 pt-1">
          {node.children.map((c) => (
            <TreeNodeRenderer
              key={c.id}
              node={c}
              activeBranchId={activeBranchId}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}
