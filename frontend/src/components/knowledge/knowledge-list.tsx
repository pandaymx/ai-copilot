"use client";

import {
  AlertTriangle,
  Loader2,
  RefreshCw,
  Search,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import type { RagDocumentMeta, RagListResponse } from "@/lib/api";
import { cn } from "@/lib/utils";

interface KnowledgeListProps {
  data: RagListResponse | null;
  loading?: boolean;
  userIdFilter: string;
  sourceTypeFilter: string;
  onUserIdFilterChange: (v: string) => void;
  onSourceTypeFilterChange: (v: string) => void;
  onRefresh: () => void;
  onDelete: (source: string, fileName: string) => void;
  onReingest: (doc: RagDocumentMeta) => void;
}

const SOURCE_TYPES = ["", "PDF", "TIKA", "MARKDOWN", "URL", "TEXT"];

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

function formatTime(iso: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { hour12: false });
}

export function KnowledgeList({
  data,
  loading,
  userIdFilter,
  sourceTypeFilter,
  onUserIdFilterChange,
  onSourceTypeFilterChange,
  onRefresh,
  onDelete,
  onReingest,
}: KnowledgeListProps) {
  const items = data?.items ?? [];

  return (
    <Card className="border-zinc-200/70 bg-white/70 shadow-xs backdrop-blur-xl dark:border-zinc-800/70 dark:bg-zinc-900/60">
      <div className="flex flex-col gap-3 border-b border-zinc-200/60 p-4 dark:border-zinc-800/60 sm:flex-row sm:items-center">
        <div className="flex items-center gap-2">
          <h3 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100">
            已入库文档
          </h3>
          <Badge tone="bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/15 dark:text-indigo-400">
            {data?.total ?? 0} 篇
          </Badge>
        </div>

        <div className="flex flex-1 flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[140px]">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-zinc-400" />
            <Input
              value={userIdFilter}
              onChange={(e) => onUserIdFilterChange(e.target.value)}
              placeholder="按用户 ID 过滤"
              className="pl-8"
            />
          </div>
          <select
            value={sourceTypeFilter}
            onChange={(e) => onSourceTypeFilterChange(e.target.value)}
            className="rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2 text-xs text-zinc-700 outline-none transition-colors focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-200"
          >
            {SOURCE_TYPES.map((t) => (
              <option key={t} value={t}>
                {t === "" ? "全部类型" : t}
              </option>
            ))}
          </select>
          <Button
            variant="outline"
            size="icon-sm"
            onClick={onRefresh}
            disabled={loading}
            aria-label="刷新列表"
          >
            <RefreshCw className={cn("size-4", loading && "animate-spin")} />
          </Button>
        </div>
      </div>

      {loading && items.length === 0 ? (
        <div className="flex items-center justify-center gap-2 py-16 text-xs text-zinc-400">
          <Loader2 className="size-4 animate-spin" />
          加载中…
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-gradient-to-tr from-indigo-600/20 to-purple-600/20">
            <Search className="size-5 text-indigo-500" />
          </div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            暂无已入库文档，使用上方「上传入库」添加知识。
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-zinc-200/60 text-[10px] uppercase tracking-wide text-zinc-400 dark:border-zinc-800/60">
                <th className="px-4 py-2.5 font-semibold">来源 / 文件</th>
                <th className="px-3 py-2.5 font-semibold">类型</th>
                <th className="px-3 py-2.5 font-semibold">用户</th>
                <th className="px-3 py-2.5 text-right font-semibold">切片</th>
                <th className="px-3 py-2.5 font-semibold">入库时间</th>
                <th className="px-3 py-2.5 text-right font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((doc) => (
                <tr
                  key={doc.docId}
                  className="border-b border-zinc-100/70 transition-colors hover:bg-indigo-50/40 dark:border-zinc-800/40 dark:hover:bg-indigo-500/5"
                >
                  <td className="max-w-[240px] px-4 py-3">
                    <div className="truncate font-medium text-zinc-800 dark:text-zinc-100">
                      {doc.fileName || doc.source}
                    </div>
                    {doc.fileName && (
                      <div className="truncate text-[10px] text-zinc-400">
                        {doc.source}
                      </div>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <Badge tone="bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
                      {doc.sourceType || "—"}
                    </Badge>
                  </td>
                  <td className="px-3 py-3">
                    <span className="truncate text-zinc-500 dark:text-zinc-400">
                      {doc.userId}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-right font-mono text-zinc-600 dark:text-zinc-300">
                    {doc.chunkCount}
                  </td>
                  <td className="px-3 py-3 text-zinc-500 dark:text-zinc-400">
                    {formatTime(doc.ingestedAt)}
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex items-center justify-end gap-1.5">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        title="重新入库（覆盖更新）"
                        onClick={() => onReingest(doc)}
                      >
                        <RefreshCw className="size-3.5 text-indigo-500" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        title="删除文档"
                        onClick={() =>
                          onDelete(doc.source, doc.fileName || doc.source)
                        }
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
  fileName: string;
  onCancel: () => void;
  onConfirm: () => void;
}

export function DeleteDialog({
  open,
  fileName,
  onCancel,
  onConfirm,
}: DeleteDialogProps) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-2xl border border-zinc-200/70 bg-white p-5 shadow-2xl dark:border-zinc-800/70 dark:bg-zinc-900">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-rose-500/10">
            <AlertTriangle className="size-5 text-rose-500" />
          </div>
          <div>
            <h4 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100">
              删除文档
            </h4>
            <p className="text-[11px] text-zinc-400">
              此操作将移除对应向量记录。
            </p>
          </div>
        </div>
        <p className="mt-4 truncate rounded-lg bg-zinc-100/70 px-3 py-2 text-xs text-zinc-600 dark:bg-zinc-800/50 dark:text-zinc-300">
          {fileName}
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
