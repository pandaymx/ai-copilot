"use client";

import { Loader2, Pencil } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import type { MemoryItem } from "@/lib/api";

const CATEGORIES = ["技术栈偏好", "项目状态", "关键决策", "个人背景", "其他"];

interface MemoryEditDialogProps {
  open: boolean;
  item: MemoryItem | null;
  onCancel: () => void;
  onConfirm: (id: string, content: string, category: string | null) => void;
  saving?: boolean;
}

export function MemoryEditDialog({
  open,
  item,
  onCancel,
  onConfirm,
  saving,
}: MemoryEditDialogProps) {
  const [content, setContent] = useState("");
  const [category, setCategory] = useState<string>("");

  useEffect(() => {
    if (item) {
      setContent(item.content);
      setCategory(item.category ?? "");
    } else {
      setContent("");
      setCategory("");
    }
  }, [item]);

  if (!open || !item) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-lg rounded-2xl border border-zinc-200/70 bg-white p-5 shadow-2xl dark:border-zinc-800/70 dark:bg-zinc-900">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-indigo-500/10">
            <Pencil className="size-5 text-indigo-500" />
          </div>
          <div>
            <h4 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100">
              编辑记忆
            </h4>
            <p className="text-[11px] text-zinc-400">
              保存后实时对后续对话生效。
            </p>
          </div>
        </div>

        <div className="mt-4 flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="memory-content"
              className="text-[11px] font-medium text-zinc-500 dark:text-zinc-400"
            >
              记忆内容
            </label>
            <textarea
              id="memory-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={4}
              placeholder="输入原子化、无代词的陈述句…"
              className="w-full resize-none rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2 text-xs text-zinc-700 outline-none transition-colors placeholder:text-zinc-400 focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-200"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="memory-category"
              className="text-[11px] font-medium text-zinc-500 dark:text-zinc-400"
            >
              分类
            </label>
            <select
              id="memory-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2 text-xs text-zinc-700 outline-none transition-colors focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-200"
            >
              <option value="">未分类</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={onCancel}
            disabled={saving}
          >
            取消
          </Button>
          <Button
            variant="default"
            size="sm"
            onClick={() => onConfirm(item.id, content.trim(), category || null)}
            disabled={saving || content.trim().length === 0}
          >
            {saving && <Loader2 className="size-3.5 animate-spin" />}
            保存
          </Button>
        </div>
      </div>
    </div>
  );
}
