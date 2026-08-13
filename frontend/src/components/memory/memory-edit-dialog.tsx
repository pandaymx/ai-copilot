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
  onConfirm: (
    id: string,
    content: string,
    category: string | null,
    priority: number,
    archived: boolean,
  ) => void;
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
  const [priority, setPriority] = useState<number>(1.0);
  const [archived, setArchived] = useState<boolean>(false);

  useEffect(() => {
    if (item) {
      setContent(item.content);
      setCategory(item.category ?? "");
      setPriority(item.priority ?? 1.0);
      setArchived(item.archived ?? false);
    } else {
      setContent("");
      setCategory("");
      setPriority(1.0);
      setArchived(false);
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
              rows={3}
              placeholder="输入原子化、无代词的陈述句…"
              className="w-full resize-none rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2 text-xs text-zinc-700 outline-none transition-colors placeholder:text-zinc-400 focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-200"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
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

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="memory-priority"
                className="text-[11px] font-medium text-zinc-500 dark:text-zinc-400 flex justify-between"
              >
                <span>基础优先级权重</span>
                <span className="font-mono text-indigo-600 dark:text-indigo-400">
                  {priority.toFixed(1)}
                </span>
              </label>
              <input
                id="memory-priority"
                type="range"
                min="0.1"
                max="2.0"
                step="0.1"
                value={priority}
                onChange={(e) => setPriority(Number.parseFloat(e.target.value))}
                className="accent-indigo-600 dark:accent-indigo-400 h-8 cursor-pointer"
              />
            </div>
          </div>

          <div className="flex items-center gap-2 pt-1">
            <input
              id="memory-archived"
              type="checkbox"
              checked={archived}
              onChange={(e) => setArchived(e.target.checked)}
              className="size-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
            />
            <label
              htmlFor="memory-archived"
              className="text-xs text-zinc-700 dark:text-zinc-300 cursor-pointer select-none"
            >
              已归档（归档后暂不注入对话 Prompt，但可随时恢复）
            </label>
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
            onClick={() =>
              onConfirm(
                item.id,
                content.trim(),
                category || null,
                priority,
                archived,
              )
            }
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
