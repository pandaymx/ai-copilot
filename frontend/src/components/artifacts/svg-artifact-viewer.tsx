"use client";

import {
  Check,
  Code2,
  Copy,
  Download,
  Eye,
  Grid,
  Sparkles,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useMemo, useRef, useState } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

interface SvgArtifactViewerProps {
  artifact: ArtifactItem;
  className?: string;
}

/** 清理并格式化 SVG 字符串（防 XSS 恶意脚本注入） */
function sanitizeSvg(content?: string): string {
  if (!content || !content.trim()) return "";

  let clean = content.trim();
  if (
    clean.startsWith("```xml") ||
    clean.startsWith("```svg") ||
    clean.startsWith("```html")
  ) {
    clean = clean
      .replace(/^```\w*/, "")
      .replace(/```$/, "")
      .trim();
  } else if (clean.startsWith("```")) {
    clean = clean
      .replace(/^```\w*/, "")
      .replace(/```$/, "")
      .trim();
  }

  // 确保包含 <svg> 标签
  const svgMatch = clean.match(/<svg[\s\S]*<\/svg>/i);
  if (svgMatch) {
    clean = svgMatch[0];
  }

  // 过滤掉 <script> 标签与 on* 事件处理器
  clean = clean.replace(/<script[\s\S]*?<\/script>/gi, "");
  clean = clean.replace(/\son\w+\s*=\s*(['"]).*?\1/gi, "");
  clean = clean.replace(/\son\w+\s*=\s*[^>\s]+/gi, "");
  clean = clean.replace(/javascript\s*:/gi, "disabled:");

  return clean;
}

export function SvgArtifactViewer({
  artifact,
  className,
}: SvgArtifactViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [activeTab, setActiveTab] = useState<"preview" | "code">("preview");
  const [zoom, setZoom] = useState<number>(100);
  const [showGrid, setShowGrid] = useState<boolean>(true);
  const [copied, setCopied] = useState<boolean>(false);
  const [position, setPosition] = useState<{ x: number; y: number }>({
    x: 0,
    y: 0,
  });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const dragStartRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

  const cleanSvg = useMemo(
    () => sanitizeSvg(artifact.content),
    [artifact.content],
  );

  const handleZoomIn = () => setZoom((z) => Math.min(400, z + 25));
  const handleZoomOut = () => setZoom((z) => Math.max(25, z - 25));
  const handleResetZoom = () => {
    setZoom(100);
    setPosition({ x: 0, y: 0 });
  };

  const handleCopyCode = async () => {
    try {
      await navigator.clipboard.writeText(cleanSvg || artifact.content || "");
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  const handleDownloadSvg = () => {
    if (!cleanSvg) return;
    const blob = new Blob([cleanSvg], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `vector-graphic-${artifact.artifactId || Date.now()}.svg`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExportPng = () => {
    if (!cleanSvg) return;
    const img = new Image();
    const svgBlob = new Blob([cleanSvg], {
      type: "image/svg+xml;charset=utf-8",
    });
    const url = URL.createObjectURL(svgBlob);

    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = (img.naturalWidth || 800) * 2;
      canvas.height = (img.naturalHeight || 600) * 2;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      const pngUrl = canvas.toDataURL("image/png");
      const a = document.createElement("a");
      a.href = pngUrl;
      a.download = `vector-graphic-${artifact.artifactId || Date.now()}.png`;
      a.click();
      URL.revokeObjectURL(url);
    };
    img.src = url;
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (activeTab !== "preview") return;
    setIsDragging(true);
    dragStartRef.current = {
      x: e.clientX - position.x,
      y: e.clientY - position.y,
    };
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    setPosition({
      x: e.clientX - dragStartRef.current.x,
      y: e.clientY - dragStartRef.current.y,
    });
  };

  const handleMouseUp = () => setIsDragging(false);

  return (
    <div
      className={cn(
        "group relative my-3 overflow-hidden rounded-2xl border border-indigo-200/70 bg-gradient-to-br from-indigo-50/40 via-white to-purple-50/30 p-4 shadow-sm transition-all duration-300 dark:border-indigo-900/60 dark:from-zinc-950 dark:via-zinc-900 dark:to-indigo-950/30 backdrop-blur-md",
        className,
      )}
    >
      {/* Header */}
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-indigo-100/60 pb-2.5 dark:border-indigo-900/40">
        <div className="flex items-center gap-2 min-w-0">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
            <Sparkles className="size-4" />
          </div>
          <div className="min-w-0">
            <h4 className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
              {artifact.title || "SVG 矢量图形产物"}
            </h4>
            <span className="text-[10px] text-zinc-400">
              矢量矢量图 · 缩放率 {zoom}%
            </span>
          </div>
        </div>

        {/* 控制工具条 */}
        <div className="flex items-center gap-1.5">
          {/* Tab 切换 */}
          <div className="flex items-center rounded-lg bg-zinc-100 p-0.5 dark:bg-zinc-800">
            <button
              type="button"
              onClick={() => setActiveTab("preview")}
              className={cn(
                "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                activeTab === "preview"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <Eye className="size-3" />
              <span>预览</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("code")}
              className={cn(
                "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                activeTab === "code"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <Code2 className="size-3" />
              <span>源码</span>
            </button>
          </div>

          {activeTab === "preview" && (
            <>
              {/* 棋盘格背景切换 */}
              <button
                type="button"
                onClick={() => setShowGrid(!showGrid)}
                className={cn(
                  "rounded-lg border p-1 text-xs transition-colors",
                  showGrid
                    ? "border-indigo-300 bg-indigo-50 text-indigo-600 dark:border-indigo-800 dark:bg-indigo-950 dark:text-indigo-300"
                    : "border-zinc-200 bg-white text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300",
                )}
                title="切换透明棋盘格背景"
              >
                <Grid className="size-3.5" />
              </button>

              {/* 缩放控制器 */}
              <div className="flex items-center rounded-lg border border-zinc-200 bg-white px-1 dark:border-zinc-800 dark:bg-zinc-800">
                <button
                  type="button"
                  onClick={handleZoomOut}
                  className="p-1 text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200"
                  title="缩小"
                >
                  <ZoomOut className="size-3" />
                </button>
                <button
                  type="button"
                  onClick={handleResetZoom}
                  className="px-1 font-mono text-[10px] font-semibold text-zinc-700 hover:text-indigo-600 dark:text-zinc-300"
                  title="重置缩放"
                >
                  {zoom}%
                </button>
                <button
                  type="button"
                  onClick={handleZoomIn}
                  className="p-1 text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200"
                  title="放大"
                >
                  <ZoomIn className="size-3" />
                </button>
              </div>
            </>
          )}

          <button
            type="button"
            onClick={handleCopyCode}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="复制 SVG 代码"
          >
            {copied ? (
              <Check className="size-3 text-emerald-500" />
            ) : (
              <Copy className="size-3" />
            )}
          </button>

          <button
            type="button"
            onClick={handleDownloadSvg}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="下载 SVG 矢量文件"
          >
            <Download className="size-3" />
            <span className="hidden sm:inline">.SVG</span>
          </button>

          <button
            type="button"
            onClick={handleExportPng}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="导出为 PNG"
          >
            <Download className="size-3" />
            <span className="hidden sm:inline">.PNG</span>
          </button>
        </div>
      </div>

      {/* 主视图区 */}
      <div className="relative overflow-hidden rounded-xl border border-zinc-200 bg-zinc-950/5 dark:border-zinc-800 dark:bg-zinc-900/40 min-h-[280px]">
        {activeTab === "preview" ? (
          // biome-ignore lint/a11y/noStaticElementInteractions: 矢量图画布平移拖拽容器
          <div
            ref={containerRef}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            className={cn(
              "flex min-h-[300px] w-full items-center justify-center p-6 select-none overflow-hidden",
              isDragging ? "cursor-grabbing" : "cursor-grab",
              showGrid &&
                "bg-[linear-gradient(45deg,#f1f5f9_25%,transparent_25%),linear-gradient(-45deg,#f1f5f9_25%,transparent_25%),linear-gradient(45deg,transparent_75%,#f1f5f9_75%),linear-gradient(-45deg,transparent_75%,#f1f5f9_75%)] dark:bg-[linear-gradient(45deg,#18181b_25%,transparent_25%),linear-gradient(-45deg,#18181b_25%,transparent_25%),linear-gradient(45deg,transparent_75%,#18181b_75%),linear-gradient(-45deg,transparent_75%,#18181b_75%)] bg-[size:16px_16px] bg-[position:0_0,0_8px,8px_-8px,-8px_0]",
            )}
          >
            <div
              style={{
                transform: `translate(${position.x}px, ${position.y}px) scale(${zoom / 100})`,
                transformOrigin: "center center",
                transition: isDragging ? "none" : "transform 0.15s ease-out",
              }}
              className="flex items-center justify-center [&_svg]:max-h-[360px] [&_svg]:max-w-full [&_svg]:h-auto drop-shadow-md"
              // biome-ignore lint/security/noDangerouslySetInnerHtml: 已清洗防 XSS 的安全 SVG 矢量图
              dangerouslySetInnerHTML={{ __html: cleanSvg }}
            />
          </div>
        ) : (
          <div className="rounded-xl bg-zinc-950 p-4 font-mono text-xs text-emerald-300">
            <pre className="max-h-[360px] overflow-y-auto whitespace-pre-wrap">
              {cleanSvg}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
}
