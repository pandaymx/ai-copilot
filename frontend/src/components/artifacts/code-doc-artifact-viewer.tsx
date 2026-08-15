"use client";

import {
  Check,
  Copy,
  Download,
  FileCode,
  FileText,
  WrapText,
} from "lucide-react";
import { useState } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

interface CodeDocArtifactViewerProps {
  artifact: ArtifactItem;
  className?: string;
}

export function CodeDocArtifactViewer({
  artifact,
  className,
}: CodeDocArtifactViewerProps) {
  const [copied, setCopied] = useState<boolean>(false);
  const [wordWrap, setWordWrap] = useState<boolean>(true);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(artifact.content || "");
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  const handleDownload = () => {
    if (!artifact.content) return;
    const blob = new Blob([artifact.content], {
      type: "text/plain;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const ext = artifact.language ? `.${artifact.language}` : ".txt";
    a.href = url;
    a.download = `${artifact.title || "artifact"}-${artifact.artifactId || Date.now()}${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div
      className={cn(
        "group relative my-3 overflow-hidden rounded-2xl border border-indigo-200/70 bg-gradient-to-br from-indigo-50/40 via-white to-purple-50/30 p-4 shadow-sm transition-all duration-300 dark:border-indigo-900/60 dark:from-zinc-950 dark:via-zinc-900 dark:to-indigo-950/30 backdrop-blur-md",
        className,
      )}
    >
      {/* Header */}
      <div className="mb-3 flex items-center justify-between gap-2 border-b border-indigo-100/60 pb-2.5 dark:border-indigo-900/40">
        <div className="flex items-center gap-2 min-w-0">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
            {artifact.artifactType === "document" ? (
              <FileText className="size-4" />
            ) : (
              <FileCode className="size-4" />
            )}
          </div>
          <div className="min-w-0">
            <h4 className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
              {artifact.title ||
                (artifact.artifactType === "document"
                  ? "文档产物"
                  : "代码产物")}
            </h4>
            <span className="font-mono text-[10px] uppercase text-zinc-400">
              {artifact.language || artifact.artifactType || "text"}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setWordWrap(!wordWrap)}
            className={cn(
              "flex items-center gap-1 rounded-lg border px-2 py-1 text-[11px] font-medium transition-colors",
              wordWrap
                ? "border-indigo-300 bg-indigo-50 text-indigo-700 dark:border-indigo-800 dark:bg-indigo-950 dark:text-indigo-300"
                : "border-zinc-200 bg-white text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300",
            )}
            title="自动折行切换"
          >
            <WrapText className="size-3" />
            <span className="hidden sm:inline">折行</span>
          </button>

          <button
            type="button"
            onClick={handleCopy}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="复制代码/文本"
          >
            {copied ? (
              <Check className="size-3 text-emerald-500" />
            ) : (
              <Copy className="size-3" />
            )}
            <span>复制</span>
          </button>

          <button
            type="button"
            onClick={handleDownload}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="下载文件"
          >
            <Download className="size-3" />
          </button>
        </div>
      </div>

      {/* Code body */}
      <div className="rounded-xl bg-zinc-950 p-4 font-mono text-xs text-zinc-200">
        <pre
          className={cn(
            "max-h-[360px] overflow-y-auto",
            wordWrap
              ? "whitespace-pre-wrap break-all"
              : "whitespace-pre overflow-x-auto",
          )}
        >
          {artifact.content}
        </pre>
      </div>
    </div>
  );
}
