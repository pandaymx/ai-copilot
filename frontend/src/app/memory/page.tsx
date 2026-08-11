"use client";

import { ArrowLeft, CheckCircle2, X } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { MemoryEditDialog } from "@/components/memory/memory-edit-dialog";
import {
  MemoryDeleteDialog,
  MemoryList,
} from "@/components/memory/memory-list";
import { ThemeToggle } from "@/components/theme-toggle";
import {
  type MemoryItem,
  memoryDeleteApi,
  memoryListApi,
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
  const [toast, setToast] = useState<Toast | null>(null);

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
    const data = await memoryListApi(keyword || undefined, 200, 0);
    if (data) {
      setItems(data.items);
      setTotal(data.total);
    }
    setLoading(false);
  }, [keyword]);

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
  ) => {
    setSavingEdit(true);
    const updated = await memoryUpdateApi(id, content, category);
    setSavingEdit(false);
    if (updated) {
      setEditOpen(false);
      setEditTarget(null);
      showToast("success", "记忆已更新，后续对话立即生效。");
      fetchMemories();
    } else {
      showToast("error", "更新失败，请重试。");
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
                长期记忆
              </h1>
              <p className="text-[11px] text-zinc-400">
                查看、编辑或删除 AI 记住的你的偏好与背景。
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
          onRefresh={fetchMemories}
          onEdit={openEdit}
          onDelete={openDelete}
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
