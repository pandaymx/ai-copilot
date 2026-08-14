"use client";

import { ArrowLeft, CheckCircle2, Loader2, X } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { EmbeddingManagementView } from "@/components/knowledge/embedding-management-view";
import { KnowledgeGraphViewer } from "@/components/knowledge/knowledge-graph-viewer";
import {
  DeleteDialog,
  KnowledgeList,
} from "@/components/knowledge/knowledge-list";
import { KnowledgeStatus } from "@/components/knowledge/knowledge-status";
import { KnowledgeUpload } from "@/components/knowledge/knowledge-upload";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import {
  type RagDocumentMeta,
  type RagListResponse,
  type RagStatus,
  ragDeleteApi,
  ragListApi,
  ragReingestApi,
  ragStatusApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

interface Toast {
  kind: "success" | "error";
  message: string;
}

export default function KnowledgePage() {
  const [list, setList] = useState<RagListResponse | null>(null);
  const [status, setStatus] = useState<RagStatus | null>(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingStatus, setLoadingStatus] = useState(false);
  const [userIdFilter, setUserIdFilter] = useState("");
  const [sourceTypeFilter, setSourceTypeFilter] = useState("");
  const [toast, setToast] = useState<Toast | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{
    source: string;
    name: string;
  } | null>(null);
  const [busy, setBusy] = useState(false);

  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((kind: Toast["kind"], message: string) => {
    setToast({ kind, message });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  const fetchDocuments = useCallback(async () => {
    setLoadingList(true);
    const data = await ragListApi(
      userIdFilter || undefined,
      sourceTypeFilter || undefined,
      100,
    );
    setList(data);
    setLoadingList(false);
  }, [userIdFilter, sourceTypeFilter]);

  const fetchStatus = useCallback(async () => {
    setLoadingStatus(true);
    const data = await ragStatusApi();
    setStatus(data);
    setLoadingStatus(false);
  }, []);

  useEffect(() => {
    void fetchDocuments();
  }, [fetchDocuments]);

  useEffect(() => {
    void fetchStatus();
  }, [fetchStatus]);

  useEffect(() => {
    return () => {
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, []);

  const handleUploadSuccess = () => {
    showToast("success", "文档入库成功");
    void fetchDocuments();
    void fetchStatus();
  };

  const handleDelete = async (source: string, fileName: string) => {
    setBusy(true);
    const res = await ragDeleteApi(source);
    setBusy(false);
    setDeleteTarget(null);
    if (res?.success) {
      showToast("success", `已删除：${fileName}`);
    } else {
      showToast("error", `删除失败：${res?.error ?? "未知错误"}`);
    }
    void fetchDocuments();
    void fetchStatus();
  };

  const handleReingest = useCallback(
    async (doc: RagDocumentMeta) => {
      let payload: {
        sourceType: "URL" | "TEXT";
        targetUrl?: string;
        rawText?: string;
        fileName: string;
      } | null = null;
      if (doc.sourceType === "URL") {
        payload = {
          sourceType: "URL",
          targetUrl: doc.source,
          fileName: doc.fileName,
        };
      } else if (doc.sourceType === "TEXT") {
        payload = {
          sourceType: "TEXT",
          rawText: doc.source,
          fileName: doc.fileName,
        };
      } else {
        showToast(
          "error",
          "文件类文档需后端文件路径，浏览器端暂不支持重新入库",
        );
        return;
      }
      setBusy(true);
      const res = await ragReingestApi(payload);
      setBusy(false);
      if (res?.success) {
        showToast(
          "success",
          `重新入库完成：移除 ${res.removed}，新增 ${res.ingested}，跳过 ${res.skipped}`,
        );
      } else {
        showToast("error", `重新入库失败：${res?.error ?? "未知错误"}`);
      }
      void fetchDocuments();
    },
    [fetchDocuments, showToast],
  );

  const [activeTab, setActiveTab] = useState<"docs" | "graph" | "embedding">(
    "docs",
  );

  return (
    <div className="relative min-h-dvh bg-ambient-mesh bg-zinc-50 dark:bg-zinc-950">
      <header className="sticky top-0 z-30 border-b border-zinc-200/60 bg-white/70 backdrop-blur-xl dark:border-zinc-800/60 dark:bg-zinc-950/70">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <span className="relative flex size-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex size-2.5 rounded-full bg-emerald-500" />
            </span>
            <h1 className="font-heading text-sm font-bold tracking-tight text-zinc-800 dark:text-zinc-100">
              知识库与图谱管理
            </h1>

            <div className="flex items-center rounded-xl border border-zinc-200/80 bg-zinc-100/80 p-0.5 text-xs dark:border-zinc-800 dark:bg-zinc-900/80 ml-4">
              <button
                type="button"
                onClick={() => setActiveTab("docs")}
                className={cn(
                  "flex items-center gap-1.5 px-3 py-1 rounded-lg font-medium transition-all",
                  activeTab === "docs"
                    ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-800 dark:text-white"
                    : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100",
                )}
              >
                <span>📚 知识库文档</span>
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("graph")}
                className={cn(
                  "flex items-center gap-1.5 px-3 py-1 rounded-lg font-medium transition-all",
                  activeTab === "graph"
                    ? "bg-indigo-600 text-white shadow-xs"
                    : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100",
                )}
              >
                <span>🕸️ 知识图谱 (GraphRAG)</span>
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("embedding")}
                className={cn(
                  "flex items-center gap-1.5 px-3 py-1 rounded-lg font-medium transition-all",
                  activeTab === "embedding"
                    ? "bg-purple-600 text-white shadow-xs"
                    : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100",
                )}
              >
                <span>🧬 向量生命周期</span>
              </button>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link href="/">
              <Button
                variant="ghost"
                size="sm"
                className="gap-1.5 text-xs text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              >
                <ArrowLeft className="size-3.5" />
                返回对话
              </Button>
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl space-y-5 px-4 py-6 sm:px-6">
        {activeTab === "docs" ? (
          <>
            <KnowledgeStatus status={status} loading={loadingStatus} />
            <div className="grid gap-5 lg:grid-cols-5">
              <div className="lg:col-span-2">
                <KnowledgeUpload onSuccess={handleUploadSuccess} />
              </div>
              <div className="lg:col-span-3">
                <KnowledgeList
                  data={list}
                  loading={loadingList}
                  userIdFilter={userIdFilter}
                  sourceTypeFilter={sourceTypeFilter}
                  onUserIdFilterChange={setUserIdFilter}
                  onSourceTypeFilterChange={setSourceTypeFilter}
                  onRefresh={() => void fetchDocuments()}
                  onDelete={(source, name) => setDeleteTarget({ source, name })}
                  onReingest={handleReingest}
                />
              </div>
            </div>
          </>
        ) : activeTab === "graph" ? (
          <div className="space-y-4">
            <KnowledgeGraphViewer />
          </div>
        ) : (
          <div className="space-y-4">
            <EmbeddingManagementView />
          </div>
        )}
      </main>

      <DeleteDialog
        open={deleteTarget !== null}
        fileName={deleteTarget?.name ?? ""}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() =>
          deleteTarget &&
          void handleDelete(deleteTarget.source, deleteTarget.name)
        }
      />

      {/* Toast */}
      {toast && (
        <div className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2">
          <div
            className={`flex items-center gap-2 rounded-2xl border px-4 py-2.5 text-xs font-medium shadow-2xl backdrop-blur-xl ${
              toast.kind === "success"
                ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                : "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300"
            }`}
          >
            {toast.kind === "success" ? (
              <CheckCircle2 className="size-4" />
            ) : (
              <X className="size-4" />
            )}
            {toast.message}
          </div>
        </div>
      )}

      {busy && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/20 backdrop-blur-xs">
          <div className="flex items-center gap-2 rounded-2xl bg-white/90 px-4 py-3 text-xs font-medium text-zinc-700 shadow-xl dark:bg-zinc-900/90 dark:text-zinc-200">
            <Loader2 className="size-4 animate-spin" />
            处理中…
          </div>
        </div>
      )}
    </div>
  );
}
