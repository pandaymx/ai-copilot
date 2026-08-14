"use client";

import {
  FileText,
  Link2,
  Loader2,
  Trash2,
  Type,
  UploadCloud,
  X,
} from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { type RagIngestResult, ragReingestApi, ragUploadApi } from "@/lib/api";
import { cn } from "@/lib/utils";

type Tab = "text" | "url" | "file";

const TABS: {
  key: Tab;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}[] = [
  { key: "text", label: "文本", icon: Type },
  { key: "url", label: "网页 URL", icon: Link2 },
  { key: "file", label: "批量文件", icon: FileText },
];

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface KnowledgeUploadProps {
  onSuccess: (result: RagIngestResult) => void;
}

export function KnowledgeUpload({ onSuccess }: KnowledgeUploadProps) {
  const [tab, setTab] = useState<Tab>("text");
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [fileName, setFileName] = useState("");
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [batchProgress, setBatchProgress] = useState<{
    current: number;
    total: number;
  } | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const newFiles: File[] = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.size > 10 * 1024 * 1024) {
        toast.error(`文件 "${file.name}" 超过 10MB 限制`);
        continue;
      }
      // 防止文件列表中已有完全相同的名字和大小
      newFiles.push(file);
    }

    setSelectedFiles((prev) => {
      const existingKeys = new Set(
        prev.map((f) => `${f.name}-${f.size}-${f.lastModified}`),
      );
      const uniqueNew = newFiles.filter(
        (f) => !existingKeys.has(`${f.name}-${f.size}-${f.lastModified}`),
      );
      return [...prev, ...uniqueNew];
    });

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const removeFile = (index: number) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const clearFiles = () => {
    setSelectedFiles([]);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    handleFiles(e.dataTransfer.files);
  };

  const handleSubmit = async () => {
    setSubmitting(true);

    try {
      if (tab === "text") {
        if (!text.trim()) return;
        const result = await ragUploadApi({
          sourceType: "TEXT",
          rawText: text,
          fileName: fileName.trim() || undefined,
        });
        if (result) {
          onSuccess(result);
          setText("");
          setFileName("");
        }
      } else if (tab === "url") {
        if (!url.trim()) return;
        const result = await ragUploadApi({
          sourceType: "URL",
          targetUrl: url.trim(),
          fileName: fileName.trim() || undefined,
        });
        if (result) {
          onSuccess(result);
          setUrl("");
          setFileName("");
        }
      } else if (tab === "file") {
        if (selectedFiles.length === 0) return;

        let totalIngested = 0;
        let totalSkipped = 0;
        let failCount = 0;

        setBatchProgress({ current: 0, total: selectedFiles.length });

        for (let i = 0; i < selectedFiles.length; i++) {
          const file = selectedFiles[i];
          setBatchProgress({ current: i + 1, total: selectedFiles.length });

          try {
            const content = await file.text();
            // 单文件重命名覆盖仅当选定单文件且输入框有值时生效；多文件使用原文件名
            const effectiveName =
              selectedFiles.length === 1 && fileName.trim()
                ? fileName.trim()
                : file.name;

            const res = await ragUploadApi({
              sourceType: "TEXT",
              rawText: content,
              fileName: effectiveName,
            });

            if (res?.success) {
              totalIngested += res.ingested;
              totalSkipped += res.skipped;
            } else {
              failCount++;
            }
          } catch (err) {
            console.error(`读取/上传文件 ${file.name} 失败:`, err);
            failCount++;
          }
        }

        const overallSuccess = failCount === 0;
        onSuccess({
          success: overallSuccess,
          sourceType: "TEXT",
          source: `${selectedFiles.length} 个文件`,
          ingested: totalIngested,
          skipped: totalSkipped,
          error:
            failCount > 0
              ? `${failCount}/${selectedFiles.length} 个文件上传失败`
              : undefined,
        });

        setSelectedFiles([]);
        setFileName("");
      }
    } finally {
      setSubmitting(false);
      setBatchProgress(null);
    }
  };

  const handleReingest = async () => {
    if (tab !== "text" || !text.trim()) return;
    setSubmitting(true);
    try {
      const result = await ragReingestApi({
        sourceType: "TEXT",
        rawText: text,
        fileName: fileName || undefined,
      });
      if (result) onSuccess(result);
    } finally {
      setSubmitting(false);
    }
  };

  const totalFilesSize = selectedFiles.reduce((acc, f) => acc + f.size, 0);

  return (
    <Card className="border-zinc-200/70 bg-white/70 p-5 shadow-xs backdrop-blur-xl dark:border-zinc-800/70 dark:bg-zinc-900/60">
      <div className="mb-4 flex items-center gap-2">
        <span className="flex size-8 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 text-white shadow-md shadow-indigo-500/20">
          <UploadCloud className="size-4" />
        </span>
        <div className="leading-tight">
          <h3 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100">
            上传入库
          </h3>
          <p className="text-[11px] text-zinc-400 dark:text-zinc-500">
            支持多文件批量上传，自动去重跳过重复切片
          </p>
        </div>
      </div>

      {/* 选项卡 */}
      <div className="mb-4 flex gap-1 rounded-xl border border-zinc-200/70 bg-zinc-100/60 p-1 dark:border-zinc-800/70 dark:bg-zinc-800/40">
        {TABS.map((t) => {
          const Icon = t.icon;
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={cn(
                "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition-all duration-200",
                active
                  ? "bg-white text-indigo-600 shadow-xs dark:bg-zinc-900 dark:text-indigo-400"
                  : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              <Icon className="size-3.5" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === "text" && (
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={6}
          placeholder="粘贴或输入需要入库的文本内容…"
          className="w-full resize-none rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2.5 text-xs text-zinc-800 outline-none transition-colors placeholder:text-zinc-400 focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-100 dark:placeholder:text-zinc-500"
        />
      )}

      {tab === "url" && (
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/docs/article"
          className="w-full rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2.5 text-xs text-zinc-800 outline-none transition-colors placeholder:text-zinc-400 focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-100 dark:placeholder:text-zinc-500"
        />
      )}

      {tab === "file" && (
        <div className="space-y-3">
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept=".txt,.md,.json,.js,.ts,.java,.py,.csv,.log,.xml,.yaml,.yml"
            onChange={(e) => handleFiles(e.target.files)}
            className="hidden"
          />

          {/* biome-ignore lint/a11y/useSemanticElements: dropzone container requires div for drag & drop layout */}
          <div
            role="button"
            tabIndex={0}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                fileInputRef.current?.click();
              }
            }}
            className={cn(
              "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border border-dashed px-4 py-6 text-center text-xs transition-colors",
              isDragging
                ? "border-indigo-500 bg-indigo-50/60 dark:border-indigo-400 dark:bg-indigo-500/10"
                : "border-zinc-300/80 bg-zinc-50/60 hover:border-indigo-500/60 hover:bg-indigo-50/40 dark:border-zinc-700/80 dark:bg-zinc-900/40 dark:hover:border-indigo-500/50 dark:hover:bg-indigo-500/5",
            )}
          >
            <FileText className="size-6 text-indigo-500" />
            <div className="space-y-0.5">
              <p className="font-medium text-zinc-700 dark:text-zinc-200">
                点击或拖拽文件到此处（支持多选批量上传）
              </p>
              <p className="text-[11px] text-zinc-400 dark:text-zinc-500">
                支持 .txt, .md, .json, .js, .py, .java 等文本/代码文件
              </p>
            </div>
          </div>

          {selectedFiles.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-[11px] text-zinc-500 dark:text-zinc-400">
                <span>
                  已选择{" "}
                  <strong className="font-semibold text-zinc-700 dark:text-zinc-200">
                    {selectedFiles.length}
                  </strong>{" "}
                  个文件（共 {formatFileSize(totalFilesSize)}）
                </span>
                <button
                  type="button"
                  onClick={clearFiles}
                  className="flex items-center gap-1 text-rose-500 hover:text-rose-600 dark:text-rose-400 dark:hover:text-rose-300"
                >
                  <Trash2 className="size-3" />
                  清空列表
                </button>
              </div>

              <div className="max-h-40 space-y-1.5 overflow-y-auto pr-1">
                {selectedFiles.map((file, idx) => (
                  <div
                    key={`${file.name}-${idx}`}
                    className="flex items-center justify-between gap-2 rounded-lg bg-zinc-100/70 px-3 py-1.5 text-[11px] text-zinc-700 dark:bg-zinc-800/50 dark:text-zinc-200"
                  >
                    <div className="flex items-center gap-2 truncate">
                      <FileText className="size-3.5 shrink-0 text-indigo-500" />
                      <span className="truncate font-medium">{file.name}</span>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span className="text-[10px] text-zinc-400">
                        {formatFileSize(file.size)}
                      </span>
                      <button
                        type="button"
                        onClick={() => removeFile(idx)}
                        className="rounded p-0.5 text-zinc-400 hover:bg-zinc-200 hover:text-zinc-600 dark:hover:bg-zinc-700 dark:hover:text-zinc-200"
                      >
                        <X className="size-3" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {batchProgress && (
        <div className="mt-3 flex items-center justify-between rounded-lg bg-indigo-50/70 px-3 py-1.5 text-xs text-indigo-600 dark:bg-indigo-950/40 dark:text-indigo-300">
          <span className="flex items-center gap-2">
            <Loader2 className="size-3.5 animate-spin" />
            正在批量入库 ({batchProgress.current} / {batchProgress.total})
          </span>
          <span className="font-mono text-[11px]">
            {Math.round((batchProgress.current / batchProgress.total) * 100)}%
          </span>
        </div>
      )}

      <div className="mt-4 flex items-center gap-2">
        <Input
          value={fileName}
          onChange={(e) => setFileName(e.target.value)}
          placeholder={
            tab === "file" && selectedFiles.length > 1
              ? "多文件模式下自动使用原文件名"
              : "文件名 / 标题（可选）"
          }
          disabled={tab === "file" && selectedFiles.length > 1}
          className="flex-1"
        />
        <Button
          variant="gradient"
          size="sm"
          onClick={handleSubmit}
          disabled={
            submitting ||
            (tab === "text" && !text.trim()) ||
            (tab === "url" && !url.trim()) ||
            (tab === "file" && selectedFiles.length === 0)
          }
        >
          {submitting && <Loader2 className="size-3.5 animate-spin" />}
          {tab === "file" && selectedFiles.length > 1
            ? `批量入库 (${selectedFiles.length})`
            : "入库"}
        </Button>
        {tab === "text" && text.trim() && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleReingest}
            disabled={submitting}
            title="先删除旧版本再覆盖写入"
          >
            重新入库
          </Button>
        )}
      </div>
    </Card>
  );
}
