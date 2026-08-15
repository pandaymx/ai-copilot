"use client";

import {
  BookOpen,
  FileCode,
  FileSpreadsheet,
  FileText,
  Loader2,
  Plus,
  ShieldCheck,
  UploadCloud,
  X,
} from "lucide-react";
import { useCallback, useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  type DocChatDocItem,
  deleteDocChatDocumentApi,
  ingestDocChatDocumentApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

interface DocumentChatBarProps {
  enabled: boolean;
  onToggleEnabled: (enabled: boolean) => void;
  conversationId?: string;
  documents: DocChatDocItem[];
  selectedDocIds: string[];
  onSelectDocIds: (docIds: string[]) => void;
  onDocumentsChange: () => void;
}

function getFileIcon(fileName: string) {
  const ext = fileName.split(".").pop()?.toLowerCase() || "";
  if (ext === "pdf") return FileText;
  if (["doc", "docx"].includes(ext)) return FileSpreadsheet;
  if (["md", "markdown", "txt"].includes(ext)) return BookOpen;
  if (["js", "ts", "py", "java", "json"].includes(ext)) return FileCode;
  return FileText;
}

export function DocumentChatBar({
  enabled,
  onToggleEnabled,
  conversationId,
  documents,
  selectedDocIds,
  onSelectDocIds,
  onDocumentsChange,
}: DocumentChatBarProps) {
  const [uploading, setUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    if (!conversationId) {
      toast.error("请先开始对话以挂载专属文档");
      return;
    }

    setUploading(true);
    let successCount = 0;

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.size > 20 * 1024 * 1024) {
        toast.error(`文件 "${file.name}" 超过 20MB 上限`);
        continue;
      }

      try {
        const textContent = await file.text();
        const ext = file.name.split(".").pop()?.toUpperCase() || "TEXT";
        let sourceType = "TEXT";
        if (ext === "PDF") sourceType = "PDF";
        else if (["DOC", "DOCX"].includes(ext)) sourceType = "TIKA";
        else if (["MD", "MARKDOWN"].includes(ext)) sourceType = "MARKDOWN";

        const res = await ingestDocChatDocumentApi({
          conversationId,
          sourceType,
          fileName: file.name,
          rawText: textContent,
        });

        if (res && res.docId) {
          successCount++;
        } else {
          toast.error(`文档 "${file.name}" 解析入库失败`);
        }
      } catch (err) {
        console.error("文件上传处理失败:", err);
        toast.error(`上传 "${file.name}" 失败`);
      }
    }

    setUploading(false);
    if (fileInputRef.current) fileInputRef.current.value = "";

    if (successCount > 0) {
      toast.success(`成功挂载 ${successCount} 份文档至当前会话`);
      if (!enabled) {
        onToggleEnabled(true);
      }
      onDocumentsChange();
    }
  };

  const handleDelete = async (docId: string, fileName: string) => {
    if (!conversationId) return;
    try {
      const res = await deleteDocChatDocumentApi(docId, conversationId);
      if (res?.success) {
        toast.success(`已从会话移除 "${fileName}"`);
        onDocumentsChange();
        // 更新选中列表
        onSelectDocIds(selectedDocIds.filter((id) => id !== docId));
      } else {
        toast.error("删除失败");
      }
    } catch {
      toast.error("删除异常");
    }
  };

  const toggleDocSelection = (docId: string) => {
    if (selectedDocIds.includes(docId)) {
      onSelectDocIds(selectedDocIds.filter((id) => id !== docId));
    } else {
      onSelectDocIds([...selectedDocIds, docId]);
    }
  };

  return (
    // biome-ignore lint/a11y/noStaticElementInteractions: 文件拖拽区域容器
    <div
      className={cn(
        "relative rounded-2xl border transition-all duration-200 p-3 shadow-xs",
        enabled
          ? "border-indigo-500/40 bg-indigo-50/40 dark:border-indigo-500/30 dark:bg-indigo-950/20 backdrop-blur-xs"
          : "border-zinc-200/70 bg-white/60 dark:border-zinc-800/70 dark:bg-zinc-900/40",
      )}
      onDragOver={(e) => {
        e.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={(e) => {
        e.preventDefault();
        setIsDragging(false);
        void handleFileUpload(e.dataTransfer.files);
      }}
    >
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".pdf,.docx,.doc,.txt,.md,.json,.js,.ts,.py,.java"
        className="hidden"
        onChange={(e) => void handleFileUpload(e.target.files)}
      />

      {/* 头部控制区 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onToggleEnabled(!enabled)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-xl px-2.5 py-1 text-xs font-semibold transition-all duration-200",
              enabled
                ? "bg-indigo-600 text-white shadow-xs dark:bg-indigo-500"
                : "bg-zinc-200/70 text-zinc-700 hover:bg-zinc-300 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700",
            )}
          >
            <BookOpen className="size-3.5" />
            <span>文档对话模式</span>
            <span
              className={cn(
                "ml-1 rounded-full px-1.5 py-0.2 text-[10px] font-bold uppercase",
                enabled
                  ? "bg-white/20 text-white"
                  : "bg-zinc-300 text-zinc-600 dark:bg-zinc-700 dark:text-zinc-400",
              )}
            >
              {enabled ? "ON" : "OFF"}
            </span>
          </button>

          {enabled && (
            <span className="hidden sm:inline-flex items-center gap-1 text-[11px] font-medium text-indigo-600 dark:text-indigo-400">
              <ShieldCheck className="size-3.5" />
              <span>严格限定事实 · 附页码引用 · 超范围自动拒答</span>
            </span>
          )}
        </div>

        {/* 右侧上传按钮 */}
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={uploading}
            onClick={() => fileInputRef.current?.click()}
            className="h-7.5 gap-1.5 rounded-xl text-xs border-indigo-200 bg-white/80 dark:border-indigo-900 dark:bg-zinc-900/80 hover:bg-indigo-50 dark:hover:bg-indigo-950/40"
          >
            {uploading ? (
              <>
                <Loader2 className="size-3.5 animate-spin text-indigo-500" />
                <span>切片入库中...</span>
              </>
            ) : (
              <>
                <Plus className="size-3.5 text-indigo-500" />
                <span>挂载文档</span>
              </>
            )}
          </Button>
        </div>
      </div>

      {/* 挂载的文档列表 / 胶囊区域 */}
      {documents.length > 0 ? (
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <span className="text-[11px] font-medium text-zinc-400 dark:text-zinc-500">
            已挂载文档 ({documents.length}):
          </span>
          {documents.map((doc) => {
            const Icon = getFileIcon(doc.fileName);
            const isSelected =
              selectedDocIds.length === 0 || selectedDocIds.includes(doc.docId);

            return (
              <div
                key={doc.docId}
                className={cn(
                  "group inline-flex items-center gap-2 rounded-xl border px-2.5 py-1 text-xs transition-all duration-200",
                  isSelected
                    ? "border-indigo-400/80 bg-white/95 text-zinc-800 shadow-xs dark:border-indigo-500/80 dark:bg-zinc-900/90 dark:text-zinc-200"
                    : "border-zinc-200 bg-white/40 opacity-60 text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/40 dark:text-zinc-500",
                )}
              >
                <button
                  type="button"
                  onClick={() => toggleDocSelection(doc.docId)}
                  className="flex items-center gap-1.5 max-w-[180px] truncate"
                  title="点击切换是否参与检索比对"
                >
                  <Icon className="size-3.5 text-indigo-500 shrink-0" />
                  <span className="truncate font-medium">{doc.fileName}</span>
                  <span className="rounded bg-zinc-100 px-1 py-0.2 text-[10px] text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400 shrink-0">
                    {doc.chunkCount} 切片
                  </span>
                </button>

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    void handleDelete(doc.docId, doc.fileName);
                  }}
                  className="rounded p-0.5 text-zinc-400 hover:bg-rose-50 hover:text-rose-500 dark:hover:bg-rose-950/50 transition-colors"
                  aria-label={`移除 ${doc.fileName}`}
                >
                  <X className="size-3" />
                </button>
              </div>
            );
          })}
        </div>
      ) : (
        enabled && (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className={cn(
              "mt-2.5 flex w-full items-center justify-center gap-2 rounded-xl border border-dashed p-3 text-xs text-zinc-500 cursor-pointer transition-colors dark:text-zinc-400",
              isDragging
                ? "border-indigo-500 bg-indigo-500/10 text-indigo-600 dark:text-indigo-400"
                : "border-zinc-300 hover:border-indigo-400 hover:bg-white/60 dark:border-zinc-800 dark:hover:bg-zinc-900/60",
            )}
          >
            <UploadCloud className="size-4 text-indigo-500" />
            <span>
              拖拽合同/论文/技术文档（PDF, Word, TXT, MD）至此处，或点击上传
            </span>
          </button>
        )
      )}
    </div>
  );
}
