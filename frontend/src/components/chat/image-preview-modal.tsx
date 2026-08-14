"use client";

import {
  Check,
  Copy,
  Download,
  RotateCw,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useEffect, useState } from "react";

interface ImagePreviewModalProps {
  src: string | null;
  alt?: string;
  onClose: () => void;
}

export function ImagePreviewModal({
  src,
  alt = "预览图片",
  onClose,
}: ImagePreviewModalProps) {
  const [scale, setScale] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };
    if (src) {
      window.addEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "hidden";
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "unset";
    };
  }, [src, onClose]);

  // 重置状态
  useEffect(() => {
    if (src) {
      setScale(1);
      setRotation(0);
      setCopied(false);
    }
  }, [src]);

  if (!src) return null;

  const handleZoomIn = (e: React.MouseEvent) => {
    e.stopPropagation();
    setScale((prev) => Math.min(prev + 0.25, 3));
  };

  const handleZoomOut = (e: React.MouseEvent) => {
    e.stopPropagation();
    setScale((prev) => Math.max(prev - 0.25, 0.5));
  };

  const handleRotate = (e: React.MouseEvent) => {
    e.stopPropagation();
    setRotation((prev) => (prev + 90) % 360);
  };

  const handleDownload = (e: React.MouseEvent) => {
    e.stopPropagation();
    const link = document.createElement("a");
    link.href = src;
    link.download = `vision-image-${Date.now()}.${
      src.startsWith("data:image/png")
        ? "png"
        : src.startsWith("data:image/webp")
          ? "webp"
          : "jpg"
    }`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      if (src.startsWith("data:image/")) {
        const res = await fetch(src);
        const blob = await res.blob();
        await navigator.clipboard.write([
          new ClipboardItem({ [blob.type]: blob }),
        ]);
      } else {
        await navigator.clipboard.writeText(src);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      await navigator.clipboard.writeText(src);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="图片全屏预览"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md transition-all duration-300 animate-in fade-in"
    >
      {/* 点击背景关闭遮罩 */}
      <button
        type="button"
        className="absolute inset-0 size-full cursor-default border-none bg-transparent"
        onClick={onClose}
        aria-label="关闭预览背景"
      />

      {/* 顶部操作工具栏 */}
      <div className="absolute top-4 right-4 z-10 flex items-center gap-2 rounded-2xl border border-white/10 bg-zinc-900/80 px-3 py-1.5 shadow-2xl backdrop-blur-xl pointer-events-auto">
        <button
          type="button"
          onClick={handleZoomIn}
          className="flex size-8 items-center justify-center rounded-lg text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
          title="放大"
        >
          <ZoomIn className="size-4.5" />
        </button>
        <button
          type="button"
          onClick={handleZoomOut}
          className="flex size-8 items-center justify-center rounded-lg text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
          title="缩小"
        >
          <ZoomOut className="size-4.5" />
        </button>
        <button
          type="button"
          onClick={handleRotate}
          className="flex size-8 items-center justify-center rounded-lg text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
          title="旋转"
        >
          <RotateCw className="size-4.5" />
        </button>
        <button
          type="button"
          onClick={handleCopy}
          className="flex size-8 items-center justify-center rounded-lg text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
          title="复制图片或链接"
        >
          {copied ? (
            <Check className="size-4.5 text-emerald-400" />
          ) : (
            <Copy className="size-4.5" />
          )}
        </button>
        <button
          type="button"
          onClick={handleDownload}
          className="flex size-8 items-center justify-center rounded-lg text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
          title="下载图片"
        >
          <Download className="size-4.5" />
        </button>
        <div className="mx-0.5 h-4 w-px bg-white/20" />
        <button
          type="button"
          onClick={onClose}
          className="flex size-8 items-center justify-center rounded-lg text-zinc-400 transition-colors hover:bg-rose-500/20 hover:text-rose-400"
          title="关闭 (Esc)"
        >
          <X className="size-5" />
        </button>
      </div>

      {/* 图片容器 */}
      <div
        className="relative max-h-[90vh] max-w-[90vw] overflow-hidden select-none transition-transform duration-200 pointer-events-auto"
        style={{
          transform: `scale(${scale}) rotate(${rotation}deg)`,
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={src}
          alt={alt}
          className="max-h-[85vh] max-w-[85vw] rounded-xl object-contain shadow-2xl ring-1 ring-white/10"
        />
      </div>
    </div>
  );
}
