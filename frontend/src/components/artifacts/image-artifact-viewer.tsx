"use client";

import {
  Download,
  Expand,
  ImageIcon,
  Loader2,
  Sparkles,
  X,
} from "lucide-react";
import { useState } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

interface ImageArtifactViewerProps {
  artifact: ArtifactItem;
  className?: string;
}

export function ImageArtifactViewer({
  artifact,
  className,
}: ImageArtifactViewerProps) {
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const isProcessing = artifact.status === "processing" || !artifact.content;

  const handleDownload = () => {
    if (!artifact.content) return;
    const a = document.createElement("a");
    a.href = artifact.content;
    a.download = `generated-image-${artifact.artifactId || Date.now()}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  return (
    <div
      className={cn(
        "group relative my-3 overflow-hidden rounded-2xl border border-indigo-200/70 bg-gradient-to-br from-indigo-50/50 via-white to-purple-50/50 p-4 shadow-sm transition-all duration-300 dark:border-indigo-900/60 dark:from-zinc-900/90 dark:via-zinc-900/70 dark:to-indigo-950/40 backdrop-blur-md",
        className,
      )}
    >
      {/* Header bar */}
      <div className="mb-3 flex items-center justify-between gap-2 border-b border-indigo-100/60 pb-2.5 dark:border-indigo-900/40">
        <div className="flex items-center gap-2">
          <div className="flex size-7 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
            <ImageIcon className="size-4" />
          </div>
          <span className="font-semibold text-xs text-zinc-800 dark:text-zinc-200 max-w-[260px] truncate">
            {artifact.title || "AI 图像生成产物"}
          </span>
        </div>
        <span
          className={cn(
            "flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[10px] font-semibold border transition-colors",
            isProcessing
              ? "bg-amber-50 text-amber-700 border-amber-200/60 dark:bg-amber-950/50 dark:text-amber-300 dark:border-amber-800/50"
              : "bg-emerald-50 text-emerald-700 border-emerald-200/60 dark:bg-emerald-950/50 dark:text-emerald-300 dark:border-emerald-800/50",
          )}
        >
          {isProcessing ? (
            <>
              <Loader2 className="size-3 animate-spin text-amber-500" />
              <span>生成中...</span>
            </>
          ) : (
            <>
              <Sparkles className="size-3 text-emerald-500" />
              <span>已完成</span>
            </>
          )}
        </span>
      </div>

      {/* Main Image Body / Loading Skeleton */}
      {isProcessing ? (
        <div className="relative flex aspect-square sm:aspect-video w-full flex-col items-center justify-center rounded-xl border border-dashed border-indigo-200 bg-indigo-50/30 p-6 dark:border-indigo-900/60 dark:bg-indigo-950/20">
          <div className="relative mb-3 flex size-12 items-center justify-center rounded-full bg-indigo-500/10 dark:bg-indigo-500/20">
            <Loader2 className="size-6 animate-spin text-indigo-600 dark:text-indigo-400" />
            <Sparkles className="absolute -top-1 -right-1 size-4 animate-bounce text-purple-500" />
          </div>
          <p className="text-xs font-medium text-zinc-700 dark:text-zinc-300 animate-pulse">
            AI 正在绘制生成画面中，请稍候...
          </p>
          <span className="mt-1 text-[11px] text-zinc-400 dark:text-zinc-500">
            {artifact.title || "正在进行高质量图像建模与渲染"}
          </span>
        </div>
      ) : (
        <div className="relative overflow-hidden rounded-xl group/img border border-zinc-200/60 dark:border-zinc-800/60 bg-black/5 dark:bg-black/20">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={artifact.content}
            alt={artifact.title || "AI Generated Image"}
            className="w-full max-h-[420px] object-contain transition-transform duration-500 group-hover/img:scale-[1.02]"
          />
          {/* Hover Overlay Action Bar */}
          <div className="absolute inset-0 flex items-center justify-center gap-3 bg-zinc-950/40 opacity-0 backdrop-blur-xs transition-opacity duration-300 group-hover/img:opacity-100">
            <button
              type="button"
              onClick={() => setIsPreviewOpen(true)}
              className="flex items-center gap-1.5 rounded-lg bg-white/90 px-3 py-1.5 text-xs font-medium text-zinc-900 shadow-md hover:bg-white transition-all transform hover:scale-105"
            >
              <Expand className="size-3.5" />
              <span>放大预览</span>
            </button>
            <button
              type="button"
              onClick={handleDownload}
              className="flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white shadow-md hover:bg-indigo-700 transition-all transform hover:scale-105"
            >
              <Download className="size-3.5" />
              <span>高清下载</span>
            </button>
          </div>
        </div>
      )}

      {/* Fullscreen Preview Modal */}
      {isPreviewOpen && artifact.content && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-md animate-in fade-in duration-200">
          <div className="relative max-h-[90vh] max-w-[90vw] overflow-hidden rounded-2xl bg-zinc-900 border border-zinc-800 p-2 shadow-2xl">
            <div className="mb-2 flex items-center justify-between px-3 pt-2">
              <span className="text-xs font-medium text-zinc-300 truncate max-w-[70vw]">
                {artifact.title || "生成高清原图"}
              </span>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleDownload}
                  className="flex items-center gap-1 rounded-lg bg-indigo-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-indigo-500 transition-colors"
                >
                  <Download className="size-3.5" />
                  <span>下载</span>
                </button>
                <button
                  type="button"
                  onClick={() => setIsPreviewOpen(false)}
                  className="flex size-7 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-800 hover:text-white transition-colors"
                >
                  <X className="size-4" />
                </button>
              </div>
            </div>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={artifact.content}
              alt={artifact.title || "Full preview"}
              className="max-h-[80vh] max-w-[85vw] rounded-xl object-contain"
            />
          </div>
        </div>
      )}
    </div>
  );
}
