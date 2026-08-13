"use client";

import {
  AlertTriangle,
  Archive,
  ArchiveRestore,
  Brain,
  Clock,
  GitMerge,
  Loader2,
  Pencil,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
  Zap,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import type { MemoryItem } from "@/lib/api";
import { cn } from "@/lib/utils";

export type MemoryStatusFilter = "active" | "archived" | "all";

interface MemoryListProps {
  items: MemoryItem[];
  total: number;
  loading?: boolean;
  keyword: string;
  onKeywordChange: (v: string) => void;
  status: MemoryStatusFilter;
  onStatusChange: (status: MemoryStatusFilter) => void;
  onRefresh: () => void;
  onEdit: (item: MemoryItem) => void;
  onDelete: (item: MemoryItem) => void;
  onToggleArchive: (item: MemoryItem) => void;
  onTriggerDecay: () => void;
  onTriggerCompress: () => void;
  onTriggerResolveConflicts: () => void;
  actionLoading?: string | null;
}

function Badge({
  children,
  tone,
}: {
  children: React.ReactNode;
  tone: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold",
        tone,
      )}
    >
      {children}
    </span>
  );
}

function formatTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { hour12: false });
}

export function MemoryList({
  items,
  total,
  loading,
  keyword,
  onKeywordChange,
  status,
  onStatusChange,
  onRefresh,
  onEdit,
  onDelete,
  onToggleArchive,
  onTriggerDecay,
  onTriggerCompress,
  onTriggerResolveConflicts,
  actionLoading,
}: MemoryListProps) {
  return (
    <Card className="border-zinc-200/70 bg-white/70 shadow-xs backdrop-blur-xl dark:border-zinc-800/70 dark:bg-zinc-900/60">
      {/* 顶部工具栏与高阶操作按钮 */}
      <div className="flex flex-col gap-3 border-b border-zinc-200/60 p-4 dark:border-zinc-800/60">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <h3 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100 flex items-center gap-1.5">
              <Brain className="size-4 text-indigo-500" />
              长期记忆库
            </h3>
            <Badge tone="bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/15 dark:text-indigo-400">
              {total} 条
            </Badge>
          </div>

          {/* 快捷遗忘/压缩/清理工具链 */}
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="xs"
              onClick={onTriggerDecay}
              disabled={!!actionLoading}
              title="按时间衰减与访问频次，自动归档低频/过期记忆"
              className="text-[11px]"
            >
              {actionLoading === "decay" ? (
                <Loader2 className="size-3 animate-spin text-amber-500" />
              ) : (
                <Zap className="size-3 text-amber-500" />
              )}
              衰减清理
            </Button>
            <Button
              variant="outline"
              size="xs"
              onClick={onTriggerCompress}
              disabled={!!actionLoading}
              title="将同一分类下的多条细粒度记忆提炼为高层摘要"
              className="text-[11px]"
            >
              {actionLoading === "compress" ? (
                <Loader2 className="size-3 animate-spin text-purple-500" />
              ) : (
                <Sparkles className="size-3 text-purple-500" />
              )}
              摘要压缩
            </Button>
            <Button
              variant="outline"
              size="xs"
              onClick={onTriggerResolveConflicts}
              disabled={!!actionLoading}
              title="使用 LLM 检测矛盾记忆并自动合并覆盖"
              className="text-[11px]"
            >
              {actionLoading === "conflicts" ? (
                <Loader2 className="size-3 animate-spin text-sky-500" />
              ) : (
                <GitMerge className="size-3 text-sky-500" />
              )}
              冲突合并
            </Button>
          </div>
        </div>

        {/* 状态 Tab 切换与搜索栏 */}
        <div className="flex flex-col gap-2.5 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex rounded-lg bg-zinc-100/80 p-0.5 dark:bg-zinc-800/60 w-fit">
            <button
              type="button"
              onClick={() => onStatusChange("active")}
              className={cn(
                "rounded-md px-3 py-1 text-xs font-medium transition-all",
                status === "active"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-900 dark:text-zinc-100"
                  : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              活跃中
            </button>
            <button
              type="button"
              onClick={() => onStatusChange("archived")}
              className={cn(
                "rounded-md px-3 py-1 text-xs font-medium transition-all",
                status === "archived"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-900 dark:text-zinc-100"
                  : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              已归档
            </button>
            <button
              type="button"
              onClick={() => onStatusChange("all")}
              className={cn(
                "rounded-md px-3 py-1 text-xs font-medium transition-all",
                status === "all"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-900 dark:text-zinc-100"
                  : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              全部记忆
            </button>
          </div>

          <div className="flex flex-1 items-center gap-2 max-w-sm">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-zinc-400" />
              <Input
                value={keyword}
                onChange={(e) => onKeywordChange(e.target.value)}
                placeholder="搜索记忆内容…"
                className="pl-8 text-xs"
              />
            </div>
            <Button
              variant="outline"
              size="icon-sm"
              onClick={onRefresh}
              disabled={loading}
              aria-label="刷新列表"
            >
              <RefreshCw
                className={cn("size-3.5", loading && "animate-spin")}
              />
            </Button>
          </div>
        </div>
      </div>

      {loading && items.length === 0 ? (
        <div className="flex items-center justify-center gap-2 py-16 text-xs text-zinc-400">
          <Loader2 className="size-4 animate-spin" />
          加载记忆中…
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-gradient-to-tr from-indigo-600/20 to-purple-600/20">
            <Brain className="size-5 text-indigo-500" />
          </div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            {status === "archived"
              ? "暂无已归档记忆。"
              : "暂无记忆。继续对话，系统会自动抽取你的偏好与背景。"}
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-zinc-200/60 text-[10px] uppercase tracking-wide text-zinc-400 dark:border-zinc-800/60">
                <th className="px-4 py-2.5 font-semibold">记忆内容</th>
                <th className="px-3 py-2.5 font-semibold">分类</th>
                <th className="px-3 py-2.5 font-semibold">权重/得分</th>
                <th className="px-3 py-2.5 font-semibold">访问/时间</th>
                <th className="px-3 py-2.5 font-semibold">状态</th>
                <th className="px-3 py-2.5 text-right font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((mem) => (
                <tr
                  key={mem.id}
                  className="border-b border-zinc-100/70 transition-colors hover:bg-indigo-50/40 dark:border-zinc-800/40 dark:hover:bg-indigo-500/5"
                >
                  <td className="max-w-[360px] px-4 py-3">
                    <div className="line-clamp-2 font-medium text-zinc-800 dark:text-zinc-100">
                      {mem.content}
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    {mem.category ? (
                      <Badge tone="bg-violet-500/10 text-violet-600 dark:bg-violet-500/15 dark:text-violet-400">
                        {mem.category}
                      </Badge>
                    ) : (
                      <Badge tone="bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
                        未分类
                      </Badge>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex flex-col gap-0.5">
                      <span className="font-mono text-zinc-700 dark:text-zinc-200">
                        权重: {mem.priority?.toFixed(1) ?? "1.0"}
                      </span>
                      {mem.priorityScore != null && (
                        <span className="text-[10px] text-indigo-600 dark:text-indigo-400 font-mono">
                          得分: {mem.priorityScore.toFixed(2)}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-3 py-3 text-zinc-500 dark:text-zinc-400 text-[11px]">
                    <div className="flex items-center gap-1 text-zinc-600 dark:text-zinc-300">
                      <Clock className="size-3 text-zinc-400" />
                      <span>{mem.accessCount ?? 0} 次命中</span>
                    </div>
                    <div className="text-[10px] text-zinc-400">
                      {formatTime(mem.updatedAt)}
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    {mem.archived ? (
                      <Badge tone="bg-amber-500/10 text-amber-600 dark:bg-amber-500/15 dark:text-amber-400">
                        已归档
                      </Badge>
                    ) : (
                      <Badge tone="bg-emerald-500/10 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-400">
                        活跃
                      </Badge>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        title={mem.archived ? "恢复解档" : "移至归档"}
                        onClick={() => onToggleArchive(mem)}
                      >
                        {mem.archived ? (
                          <ArchiveRestore className="size-3.5 text-amber-500" />
                        ) : (
                          <Archive className="size-3.5 text-zinc-400 hover:text-amber-500" />
                        )}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        title="编辑记忆"
                        onClick={() => onEdit(mem)}
                      >
                        <Pencil className="size-3.5 text-indigo-500" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        title="删除记忆"
                        onClick={() => onDelete(mem)}
                      >
                        <Trash2 className="size-3.5 text-rose-500" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

interface DeleteDialogProps {
  open: boolean;
  item: MemoryItem | null;
  onCancel: () => void;
  onConfirm: () => void;
}

export function MemoryDeleteDialog({
  open,
  item,
  onCancel,
  onConfirm,
}: DeleteDialogProps) {
  if (!open || !item) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-2xl border border-zinc-200/70 bg-white p-5 shadow-2xl dark:border-zinc-800/70 dark:bg-zinc-900">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-rose-500/10">
            <AlertTriangle className="size-5 text-rose-500" />
          </div>
          <div>
            <h4 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100">
              删除记忆
            </h4>
            <p className="text-[11px] text-zinc-400">
              删除后将从对话上下文中永久移除。
            </p>
          </div>
        </div>
        <p className="mt-4 line-clamp-3 rounded-lg bg-zinc-100/70 px-3 py-2 text-xs text-zinc-600 dark:bg-zinc-800/50 dark:text-zinc-300">
          {item.content}
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onCancel}>
            取消
          </Button>
          <Button variant="destructive" size="sm" onClick={onConfirm}>
            <Trash2 className="size-3.5" />
            确认删除
          </Button>
        </div>
      </div>
    </div>
  );
}
