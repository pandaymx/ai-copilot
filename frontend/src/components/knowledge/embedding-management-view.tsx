"use client";

import {
  AlertCircle,
  AlertTriangle,
  Archive,
  CheckCircle2,
  Cpu,
  Layers,
  Pause,
  Play,
  RefreshCw,
  Sparkles,
  Trash2,
  XCircle,
  Zap,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  type DocumentSimilarityClusterDto,
  type EmbeddingHealthDto,
  type EmbeddingReindexTaskDto,
  embeddingArchiveStaleApi,
  embeddingHealthApi,
  embeddingPurgeStaleApi,
  embeddingReembedPauseApi,
  embeddingReembedResumeApi,
  embeddingReembedStartApi,
  embeddingReembedStatusApi,
  embeddingSimilarityClustersApi,
  embeddingStaleVectorsApi,
  type StaleVectorDto,
} from "@/lib/api";
import { cn } from "@/lib/utils";

export function EmbeddingManagementView() {
  const [health, setHealth] = useState<EmbeddingHealthDto | null>(null);
  const [task, setTask] = useState<EmbeddingReindexTaskDto | null>(null);
  const [clusters, setClusters] = useState<DocumentSimilarityClusterDto[]>([]);
  const [staleVectors, setStaleVectors] = useState<StaleVectorDto[]>([]);
  const [selectedStaleIds, setSelectedStaleIds] = useState<Set<string>>(
    new Set(),
  );

  const [loading, setLoading] = useState(false);
  const [activeConflictFilter, setActiveConflictFilter] =
    useState<string>("ALL");
  const [actionBusy, setActionBusy] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const fetchAllData = useCallback(async () => {
    setLoading(true);
    const [hData, tData, cData, sData] = await Promise.all([
      embeddingHealthApi(),
      embeddingReembedStatusApi(),
      embeddingSimilarityClustersApi(0.88, 50),
      embeddingStaleVectorsApi(30, 50),
    ]);
    if (hData) setHealth(hData);
    if (tData) setTask(tData);
    if (cData) setClusters(cData);
    if (sData) setStaleVectors(sData);
    setLoading(false);
  }, []);

  useEffect(() => {
    void fetchAllData();
  }, [fetchAllData]);

  // 轮询任务状态（当处于 running 时每 2 秒刷新）
  useEffect(() => {
    if (task?.isRunning) {
      pollTimerRef.current = setInterval(async () => {
        const updated = await embeddingReembedStatusApi();
        if (updated) {
          setTask(updated);
          if (!updated.isRunning) {
            void fetchAllData();
          }
        }
      }, 2000);
    } else if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
    }
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [task?.isRunning, fetchAllData]);

  const handleStartReembedding = async () => {
    setActionBusy(true);
    const res = await embeddingReembedStartApi(false);
    if (res) {
      setTask(res);
      showToast("已启动批量重新向量化任务");
    }
    setActionBusy(false);
  };

  const handlePause = async () => {
    const ok = await embeddingReembedPauseApi();
    if (ok) {
      setTask((prev) => (prev ? { ...prev, isPaused: true } : null));
      showToast("任务已暂停");
    }
  };

  const handleResume = async () => {
    const ok = await embeddingReembedResumeApi();
    if (ok) {
      setTask((prev) => (prev ? { ...prev, isPaused: false } : null));
      showToast("任务已恢复执行");
    }
  };

  const toggleSelectStale = (id: string) => {
    const next = new Set(selectedStaleIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedStaleIds(next);
  };

  const toggleSelectAllStale = () => {
    if (selectedStaleIds.size === staleVectors.length) {
      setSelectedStaleIds(new Set());
    } else {
      setSelectedStaleIds(new Set(staleVectors.map((s) => s.id)));
    }
  };

  const handleArchiveSelected = async () => {
    if (selectedStaleIds.size === 0) return;
    setActionBusy(true);
    const res = await embeddingArchiveStaleApi(Array.from(selectedStaleIds));
    if (res?.success) {
      showToast(`成功软归档 ${res.archivedCount} 条死向量`);
      setSelectedStaleIds(new Set());
      void fetchAllData();
    }
    setActionBusy(false);
  };

  const handlePurgeSelected = async () => {
    if (selectedStaleIds.size === 0) return;
    setActionBusy(true);
    const res = await embeddingPurgeStaleApi(Array.from(selectedStaleIds));
    if (res?.success) {
      showToast(`成功物理清理 ${res.purgedCount} 条死向量`);
      setSelectedStaleIds(new Set());
      void fetchAllData();
    }
    setActionBusy(false);
  };

  const filteredClusters = clusters.filter(
    (c) =>
      activeConflictFilter === "ALL" || c.conflictType === activeConflictFilter,
  );

  const getHealthBadge = (status: string) => {
    switch (status) {
      case "HEALTHY":
        return (
          <span className="flex items-center gap-1 text-xs font-semibold text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/30">
            <CheckCircle2 className="size-3.5" /> 状态健康 (Healthy)
          </span>
        );
      case "WARNING":
        return (
          <span className="flex items-center gap-1 text-xs font-semibold text-amber-600 dark:text-amber-400 bg-amber-500/10 px-2.5 py-1 rounded-full border border-amber-500/30">
            <AlertTriangle className="size-3.5" /> 需关注 (Warning)
          </span>
        );
      default:
        return (
          <span className="flex items-center gap-1 text-xs font-semibold text-rose-600 dark:text-rose-400 bg-rose-500/10 px-2.5 py-1 rounded-full border border-rose-500/30">
            <XCircle className="size-3.5" /> 异常阻断 (Critical)
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* 顶部健康总览大盘 */}
      <div className="rounded-2xl border border-zinc-200/80 bg-white/90 p-5 shadow-xs dark:border-zinc-800/80 dark:bg-zinc-900/90 backdrop-blur-xl">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-100 pb-4 dark:border-zinc-800">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
              <Cpu className="size-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                <span>Embedding 向量生命周期大盘</span>
                {health && getHealthBadge(health.status)}
              </h2>
              <p className="text-xs text-zinc-500 mt-0.5">
                当前活跃模型：
                <span className="font-mono font-semibold text-indigo-600 dark:text-indigo-400">
                  {health?.activeModelName ?? "text-embedding-3-small"}
                </span>{" "}
                ({health?.activeModelDimensions ?? 1536} 维)
              </p>
            </div>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={fetchAllData}
            disabled={loading}
            className="text-xs gap-1.5 h-8"
          >
            <RefreshCw
              className={cn(
                "size-3.5",
                loading && "animate-spin text-indigo-500",
              )}
            />
            <span>刷新大盘</span>
          </Button>
        </div>

        {/* 核心 KPI 卡片组 */}
        <div className="grid grid-cols-2 gap-3 pt-4 sm:grid-cols-3 lg:grid-cols-6">
          <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-3 dark:border-zinc-800/60 dark:bg-zinc-800/40">
            <span className="text-[11px] text-zinc-400 font-medium">
              综合健康评分
            </span>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="text-2xl font-black text-zinc-900 dark:text-zinc-100">
                {health?.healthScore ?? 100}
              </span>
              <span className="text-xs text-zinc-400">/100</span>
            </div>
          </div>

          <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-3 dark:border-zinc-800/60 dark:bg-zinc-800/40">
            <span className="text-[11px] text-zinc-400 font-medium">
              总向量切片数
            </span>
            <div className="mt-1 text-2xl font-black text-zinc-900 dark:text-zinc-100">
              {health?.totalVectors ?? 0}
            </div>
          </div>

          <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-3 dark:border-zinc-800/60 dark:bg-zinc-800/40">
            <span className="text-[11px] text-zinc-400 font-medium">
              健康就绪向量
            </span>
            <div className="mt-1 text-2xl font-black text-emerald-600 dark:text-emerald-400">
              {health?.healthyVectors ?? 0}
            </div>
          </div>

          <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-3 dark:border-zinc-800/60 dark:bg-zinc-800/40">
            <span className="text-[11px] text-zinc-400 font-medium">
              空/全零向量
            </span>
            <div
              className={cn(
                "mt-1 text-2xl font-black",
                health?.emptyOrZeroVectors
                  ? "text-rose-600 dark:text-rose-400"
                  : "text-zinc-900 dark:text-zinc-100",
              )}
            >
              {health?.emptyOrZeroVectors ?? 0}
            </div>
          </div>

          <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-3 dark:border-zinc-800/60 dark:bg-zinc-800/40">
            <span className="text-[11px] text-zinc-400 font-medium">
              模型失配数
            </span>
            <div
              className={cn(
                "mt-1 text-2xl font-black",
                health?.modelMismatchCount
                  ? "text-amber-600 dark:text-amber-400"
                  : "text-zinc-900 dark:text-zinc-100",
              )}
            >
              {health?.modelMismatchCount ?? 0}
            </div>
          </div>

          <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-3 dark:border-zinc-800/60 dark:bg-zinc-800/40">
            <span className="text-[11px] text-zinc-400 font-medium">
              30天+ 零命中死向量
            </span>
            <div className="mt-1 text-2xl font-black text-zinc-500">
              {health?.staleVectorsCount ?? 0}
            </div>
          </div>
        </div>

        {/* 异常警示条目 */}
        {health?.issues && health.issues.length > 0 && (
          <div className="mt-4 space-y-2">
            {health.issues.map((issue, idx) => (
              <div
                key={`${issue.issueType}-${issue.documentId}-${idx}`}
                className={cn(
                  "flex items-start gap-2.5 rounded-xl p-2.5 text-xs border",
                  issue.severity === "CRITICAL"
                    ? "bg-rose-500/10 border-rose-500/30 text-rose-700 dark:text-rose-300"
                    : issue.severity === "WARNING"
                      ? "bg-amber-500/10 border-amber-500/30 text-amber-700 dark:text-amber-300"
                      : "bg-zinc-100 border-zinc-200 text-zinc-700 dark:bg-zinc-800/50 dark:border-zinc-700 dark:text-zinc-300",
                )}
              >
                <AlertCircle className="size-4 shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span className="font-semibold mr-1.5">
                    [{issue.issueType}]
                  </span>
                  <span>{issue.description}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 批量重新向量化管道控制卡片 */}
      <div className="rounded-2xl border border-zinc-200/80 bg-white/90 p-5 shadow-xs dark:border-zinc-800/80 dark:bg-zinc-900/90 backdrop-blur-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-100 pb-3 dark:border-zinc-800">
          <div>
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Zap className="size-4 text-indigo-600" />
              <span>模型切换与批量重嵌入管道 (Batch Re-embedding)</span>
            </h3>
            <p className="text-xs text-zinc-500 mt-0.5">
              当切换 Embedding
              模型时，一键异步重新生成所有存量切片向量。具备批次独立事务、断点续传（Checkpoint）与平滑限流保护。
            </p>
          </div>

          <div className="flex items-center gap-2">
            {task?.isRunning ? (
              task.isPaused ? (
                <Button
                  size="sm"
                  onClick={handleResume}
                  className="h-8 text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <Play className="size-3.5" />
                  <span>恢复任务</span>
                </Button>
              ) : (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handlePause}
                  className="h-8 text-xs gap-1.5 text-amber-600 border-amber-300 dark:border-amber-700 hover:bg-amber-50"
                >
                  <Pause className="size-3.5" />
                  <span>暂停任务</span>
                </Button>
              )
            ) : (
              <Button
                size="sm"
                onClick={handleStartReembedding}
                disabled={actionBusy}
                className="h-8 text-xs gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs"
              >
                <Sparkles className="size-3.5" />
                <span>一键全量重新向量化</span>
              </Button>
            )}
          </div>
        </div>

        {/* 任务进度条 */}
        {task && (task.isRunning || task.processed > 0) && (
          <div className="mt-4 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium text-zinc-700 dark:text-zinc-300 flex items-center gap-1.5">
                {task.isRunning ? (
                  <span className="relative flex size-2">
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-indigo-400 opacity-75" />
                    <span className="relative inline-flex size-2 rounded-full bg-indigo-500" />
                  </span>
                ) : (
                  <CheckCircle2 className="size-3.5 text-emerald-500" />
                )}
                <span>
                  {task.isRunning
                    ? task.isPaused
                      ? "任务已暂停"
                      : "重嵌入处理中..."
                    : "重嵌入已完成"}
                </span>
                <span className="text-zinc-400 font-mono text-[11px]">
                  ({task.processed} / {task.total} 切片)
                </span>
              </span>
              <span className="font-bold text-indigo-600 dark:text-indigo-400 font-mono">
                {task.total > 0
                  ? Math.round((task.processed / task.total) * 100)
                  : 100}
                %
              </span>
            </div>

            <div className="h-2 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
              <div
                className="h-full rounded-full bg-linear-to-r from-indigo-500 to-purple-600 transition-all duration-500"
                style={{
                  width: `${task.total > 0 ? (task.processed / task.total) * 100 : 100}%`,
                }}
              />
            </div>

            <div className="flex items-center justify-between text-[11px] text-zinc-400 pt-1">
              <span>
                成功:{" "}
                <strong className="text-emerald-600 font-mono">
                  {task.successCount}
                </strong>{" "}
                · 失败:{" "}
                <strong className="text-rose-600 font-mono">
                  {task.failedCount}
                </strong>
              </span>
              {task.lastProcessedId && (
                <span className="font-mono">
                  断点 ID: {task.lastProcessedId}
                </span>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 相似度地图与重复/冲突切片聚类 */}
      <div className="rounded-2xl border border-zinc-200/80 bg-white/90 p-5 shadow-xs dark:border-zinc-800/80 dark:bg-zinc-900/90 backdrop-blur-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-100 pb-3 dark:border-zinc-800">
          <div>
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Layers className="size-4 text-purple-600" />
              <span>向量相似度地图与冲突聚类 (ANN 冲突挖掘)</span>
            </h3>
            <p className="text-xs text-zinc-500 mt-0.5">
              利用 pgvector HNSW
              局部近邻自关联，智能探测高相似度（&gt;0.88）重复或语义冲突切片，避免
              O(N²) 笛卡尔积开销。
            </p>
          </div>

          {/* 冲突类型筛选胶囊 */}
          <div className="flex items-center rounded-xl border border-zinc-200/80 bg-zinc-100/80 p-0.5 text-xs dark:border-zinc-800 dark:bg-zinc-900/80">
            {[
              { id: "ALL", label: "全部冲突" },
              { id: "CROSS_DOC_DUPLICATE", label: "跨文件重复" },
              { id: "INTRA_DOC_OVERLAP", label: "内部切片重叠" },
              { id: "SEMANTIC_CONFLICT", label: "语义冲突" },
            ].map((f) => (
              <button
                key={f.id}
                type="button"
                onClick={() => setActiveConflictFilter(f.id)}
                className={cn(
                  "px-2.5 py-1 rounded-lg font-medium transition-all text-[11px]",
                  activeConflictFilter === f.id
                    ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-800 dark:text-white"
                    : "text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100",
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* 冲突簇卡片列表 */}
        <div className="mt-4 space-y-3">
          {filteredClusters.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-center text-zinc-400">
              <CheckCircle2 className="size-8 text-emerald-500 mb-2" />
              <p className="text-xs">
                未检测到严重语义冲突或重复切片，知识库质量良好。
              </p>
            </div>
          ) : (
            filteredClusters.map((cluster) => (
              <div
                key={cluster.clusterId}
                className="rounded-xl border border-zinc-200/70 bg-zinc-50/50 p-3.5 text-xs dark:border-zinc-800/70 dark:bg-zinc-800/30 space-y-2.5"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className={cn(
                        "rounded-full px-2 py-0.5 text-[10px] font-bold border",
                        cluster.conflictType === "CROSS_DOC_DUPLICATE"
                          ? "bg-rose-500/10 border-rose-500/30 text-rose-600 dark:text-rose-400"
                          : cluster.conflictType === "INTRA_DOC_OVERLAP"
                            ? "bg-blue-500/10 border-blue-500/30 text-blue-600 dark:text-blue-400"
                            : "bg-amber-500/10 border-amber-500/30 text-amber-600 dark:text-amber-400",
                      )}
                    >
                      {cluster.conflictType}
                    </span>
                    <span className="font-mono text-zinc-400 text-[11px]">
                      相似度：{(cluster.similarityScore * 100).toFixed(1)}%
                    </span>
                  </div>

                  <span className="rounded-md bg-indigo-50 dark:bg-indigo-950/60 px-2 py-0.5 text-[10px] font-medium text-indigo-600 dark:text-indigo-300 border border-indigo-200/60 dark:border-indigo-800/60">
                    建议：{cluster.suggestedAction}
                  </span>
                </div>

                {/* 左右切片并排对比 */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5 pt-1">
                  <div className="rounded-lg bg-white p-2.5 border border-zinc-200/60 dark:bg-zinc-900/60 dark:border-zinc-800/60">
                    <div className="font-semibold text-zinc-800 dark:text-zinc-200 truncate mb-1">
                      📄 {cluster.docAName}
                    </div>
                    <p className="text-zinc-600 dark:text-zinc-400 text-[11px] leading-relaxed line-clamp-3">
                      {cluster.docAExcerpt}
                    </p>
                  </div>
                  <div className="rounded-lg bg-white p-2.5 border border-zinc-200/60 dark:bg-zinc-900/60 dark:border-zinc-800/60">
                    <div className="font-semibold text-zinc-800 dark:text-zinc-200 truncate mb-1">
                      📄 {cluster.docBName}
                    </div>
                    <p className="text-zinc-600 dark:text-zinc-400 text-[11px] leading-relaxed line-clamp-3">
                      {cluster.docBExcerpt}
                    </p>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 冷数据与死向量管理（30天+零命中软归档与清理） */}
      <div className="rounded-2xl border border-zinc-200/80 bg-white/90 p-5 shadow-xs dark:border-zinc-800/80 dark:bg-zinc-900/90 backdrop-blur-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-100 pb-3 dark:border-zinc-800">
          <div>
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Archive className="size-4 text-emerald-600" />
              <span>冷数据死向量管理 (Stale Vectors)</span>
            </h3>
            <p className="text-xs text-zinc-500 mt-0.5">
              入库超过 30
              天且检索零命中的长尾向量。可软归档（检索排除但可还原）或彻底删除以释放
              PostgreSQL 存储与索引内存。
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={handleArchiveSelected}
              disabled={selectedStaleIds.size === 0 || actionBusy}
              className="h-8 text-xs gap-1.5 border-emerald-300 dark:border-emerald-700 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-50"
            >
              <Archive className="size-3.5" />
              <span>软归档已选 ({selectedStaleIds.size})</span>
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={handlePurgeSelected}
              disabled={selectedStaleIds.size === 0 || actionBusy}
              className="h-8 text-xs gap-1.5 border-rose-300 dark:border-rose-700 text-rose-600 dark:text-rose-400 hover:bg-rose-50"
            >
              <Trash2 className="size-3.5" />
              <span>物理清理已选</span>
            </Button>
          </div>
        </div>

        {/* 死向量表格 */}
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-zinc-200/80 text-zinc-400 dark:border-zinc-800">
                <th className="py-2.5 px-3 w-8">
                  <input
                    type="checkbox"
                    checked={
                      staleVectors.length > 0 &&
                      selectedStaleIds.size === staleVectors.length
                    }
                    onChange={toggleSelectAllStale}
                    className="rounded-sm border-zinc-300"
                  />
                </th>
                <th className="py-2.5 px-3">文件名 / 来源</th>
                <th className="py-2.5 px-3">内容摘要</th>
                <th className="py-2.5 px-3">入库时间</th>
                <th className="py-2.5 px-3">命中次数</th>
                <th className="py-2.5 px-3">状态</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/60">
              {staleVectors.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className="py-8 text-center text-zinc-400 text-xs"
                  >
                    暂无 30 天+ 的零命中死向量记录
                  </td>
                </tr>
              ) : (
                staleVectors.map((s) => (
                  <tr
                    key={s.id}
                    className="hover:bg-zinc-50/60 dark:hover:bg-zinc-800/30 transition-colors"
                  >
                    <td className="py-2.5 px-3">
                      <input
                        type="checkbox"
                        checked={selectedStaleIds.has(s.id)}
                        onChange={() => toggleSelectStale(s.id)}
                        className="rounded-sm border-zinc-300"
                      />
                    </td>
                    <td className="py-2.5 px-3 font-medium text-zinc-800 dark:text-zinc-200 truncate max-w-[160px]">
                      {s.fileName}
                    </td>
                    <td className="py-2.5 px-3 text-zinc-500 truncate max-w-[240px]">
                      {s.content}
                    </td>
                    <td className="py-2.5 px-3 text-zinc-400 text-[11px]">
                      {new Date(s.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-zinc-600 dark:text-zinc-300">
                      {s.hitCount}
                    </td>
                    <td className="py-2.5 px-3">
                      {s.isArchived ? (
                        <span className="rounded-sm bg-zinc-200 dark:bg-zinc-700 px-1.5 py-0.5 text-[10px] text-zinc-700 dark:text-zinc-300">
                          已归档
                        </span>
                      ) : (
                        <span className="rounded-sm bg-amber-500/10 text-amber-600 px-1.5 py-0.5 text-[10px]">
                          活跃冷数据
                        </span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 浮动 Toast 提示 */}
      {toastMessage && (
        <div className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2">
          <div className="flex items-center gap-2 rounded-2xl border border-indigo-500/30 bg-indigo-500/10 px-4 py-2.5 text-xs font-medium text-indigo-700 dark:text-indigo-300 shadow-2xl backdrop-blur-xl">
            <CheckCircle2 className="size-4" />
            {toastMessage}
          </div>
        </div>
      )}
    </div>
  );
}
