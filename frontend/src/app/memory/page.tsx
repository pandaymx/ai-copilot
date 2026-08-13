"use client";

import { ArrowLeft, CheckCircle2, X } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { MemoryEditDialog } from "@/components/memory/memory-edit-dialog";
import {
  MemoryDeleteDialog,
  MemoryList,
  type MemoryStatusFilter,
} from "@/components/memory/memory-list";
import { ThemeToggle } from "@/components/theme-toggle";
import {
  type MemoryItem,
  memoryCompressApi,
  memoryDecayApi,
  memoryDeleteApi,
  memoryListApi,
  memoryResolveConflictsApi,
  memoryUpdateApi,
} from "@/lib/api";

interface Toast {
  kind: "success" | "error";
  message: string;
}

export default function MemoryPage() {
  const [items, setItems] = useState<MemoryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<MemoryStatusFilter>("active");
  const [toast, setToast] = useState<Toast | null>(null);

  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [editTarget, setEditTarget] = useState<MemoryItem | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<MemoryItem | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((kind: Toast["kind"], message: string) => {
    setToast({ kind, message });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  const fetchMemories = useCallback(async () => {
    setLoading(true);
    const data = await memoryListApi(keyword || undefined, status, 200, 0);
    if (data) {
      setItems(data.items);
      setTotal(data.total);
    }
    setLoading(false);
  }, [keyword, status]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      fetchMemories();
    }, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [fetchMemories]);

  const openEdit = (item: MemoryItem) => {
    setEditTarget(item);
    setEditOpen(true);
  };

  const openDelete = (item: MemoryItem) => {
    setDeleteTarget(item);
    setDeleteOpen(true);
  };

  const handleEditConfirm = async (
    id: string,
    content: string,
    category: string | null,
    priority: number,
    archived: boolean,
  ) => {
    setSavingEdit(true);
    const updated = await memoryUpdateApi(
      id,
      content,
      category,
      priority,
      archived,
    );
    setSavingEdit(false);
    if (updated) {
      setEditOpen(false);
      setEditTarget(null);
      showToast("success", "记忆已更新，后续对话实时生效。");
      fetchMemories();
    } else {
      showToast("error", "更新失败，请重试。");
    }
  };

  const handleToggleArchive = async (item: MemoryItem) => {
    const newArchived = !(item.archived ?? false);
    const updated = await memoryUpdateApi(
      item.id,
      item.content,
      item.category,
      item.priority ?? 1.0,
      newArchived,
    );
    if (updated) {
      showToast("success", newArchived ? "记忆已归档。" : "记忆已从归档恢复。");
      fetchMemories();
    } else {
      showToast("error", "操作失败，请重试。");
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    const ok = await memoryDeleteApi(deleteTarget.id);
    setDeleteOpen(false);
    setDeleteTarget(null);
    if (ok) {
      showToast("success", "记忆已删除。");
      fetchMemories();
    } else {
      showToast("error", "删除失败，请重试。");
    }
  };

  const handleTriggerDecay = async () => {
    setActionLoading("decay");
    const res = await memoryDecayApi();
    setActionLoading(null);
    if (res) {
      showToast(
        "success",
        `衰减清理完成：自动归档 ${res.archived} 条，清理过期 ${res.deleted} 条。`,
      );
      fetchMemories();
    } else {
      showToast("error", "衰减清理执行失败。");
    }
  };

  const handleTriggerCompress = async () => {
    setActionLoading("compress");
    const res = await memoryCompressApi();
    setActionLoading(null);
    if (res) {
      showToast(
        "success",
        `记忆压缩完成：共成功压缩提炼 ${res.compressedCategories} 个分类下的细粒度记忆。`,
      );
      fetchMemories();
    } else {
      showToast("error", "记忆压缩执行失败。");
    }
  };

  const handleTriggerResolveConflicts = async () => {
    setActionLoading("conflicts");
    const res = await memoryResolveConflictsApi();
    setActionLoading(null);
    if (res) {
      showToast(
        "success",
        `冲突清理完成：智能检测并解决/合并了 ${res.resolvedConflicts} 处记忆矛盾。`,
      );
      fetchMemories();
    } else {
      showToast("error", "冲突清理执行失败。");
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-zinc-50 to-white dark:from-zinc-950 dark:to-zinc-900">
      <header className="sticky top-0 z-30 border-b border-zinc-200/60 bg-white/70 backdrop-blur-xl dark:border-zinc-800/60 dark:bg-zinc-950/70">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-3">
            <Link href="/">
              <ArrowLeft className="size-5 cursor-pointer text-zinc-500 transition-colors hover:text-zinc-800 dark:hover:text-zinc-200" />
            </Link>
            <div>
              <h1 className="font-heading text-base font-bold text-zinc-800 dark:text-zinc-100">
                长期记忆与遗忘管理
              </h1>
              <p className="text-[11px] text-zinc-400">
                查看、编辑、优化记忆优先级，衰减清理过期记忆或智能判定矛盾冲突。
              </p>
            </div>
          </div>
          <ThemeToggle />
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6">
        <MemoryList
          items={items}
          total={total}
          loading={loading}
          keyword={keyword}
          onKeywordChange={setKeyword}
          status={status}
          onStatusChange={setStatus}
          onRefresh={fetchMemories}
          onEdit={openEdit}
          onDelete={openDelete}
          onToggleArchive={handleToggleArchive}
          onTriggerDecay={handleTriggerDecay}
          onTriggerCompress={handleTriggerCompress}
          onTriggerResolveConflicts={handleTriggerResolveConflicts}
          actionLoading={actionLoading}
        />
      </main>

      <MemoryEditDialog
        open={editOpen}
        item={editTarget}
        saving={savingEdit}
        onCancel={() => {
          setEditOpen(false);
          setEditTarget(null);
        }}
        onConfirm={handleEditConfirm}
      />

      <MemoryDeleteDialog
        open={deleteOpen}
        item={deleteTarget}
        onCancel={() => {
          setDeleteOpen(false);
          setDeleteTarget(null);
        }}
        onConfirm={handleDeleteConfirm}
      />

      {toast && (
        <div className="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-2 rounded-xl border border-zinc-200/70 bg-white px-4 py-2.5 text-xs shadow-xl dark:border-zinc-800/70 dark:bg-zinc-900">
          <CheckCircle2
            className={
              toast.kind === "success"
                ? "size-4 text-emerald-500"
                : "size-4 text-rose-500"
            }
          />
          <span className="text-zinc-700 dark:text-zinc-200">
            {toast.message}
          </span>
          <button
            type="button"
            onClick={() => setToast(null)}
            aria-label="关闭提示"
          >
            <X className="size-3.5 cursor-pointer text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300" />
          </button>
        </div>
      )}
    </div>
  );
}
