"use client";

import {
  Activity,
  AlertTriangle,
  BarChart3,
  Clock,
  Gauge,
  Layers,
  RefreshCw,
  Sparkles,
  X,
  Zap,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchModelsMetricsApi, type ModelsMetricsResponse } from "@/lib/api";

interface ModelPerformanceModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialProvider?: string;
  initialModel?: string;
}

type ChartMode = "ttft" | "totalDuration" | "tokensPerSecond";

export function ModelPerformanceModal({
  isOpen,
  onClose,
  initialProvider,
  initialModel,
}: ModelPerformanceModalProps) {
  const [data, setData] = useState<ModelsMetricsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<string>("ALL");
  const [chartMode, setChartMode] = useState<ChartMode>("ttft");

  const loadData = useCallback(async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await fetchModelsMetricsApi();
      if (res) {
        setData(res);
      } else {
        setErrorMessage("无法获取模型流式性能数据，请确认后端服务正常运行。");
      }
    } catch {
      setErrorMessage("请求流式性能指标异常。");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      void loadData();
      if (initialProvider) {
        setSelectedProvider(initialProvider);
      }
    }
  }, [isOpen, initialProvider, loadData]);

  // 按供应商过滤后的模型列表
  const filteredModels = useMemo(() => {
    if (!data || !data.models) return [];
    if (selectedProvider === "ALL") return data.models;
    return data.models.filter((m) => m.providerId === selectedProvider);
  }, [data, selectedProvider]);

  // 所有可用供应商列表
  const providerList = useMemo(() => {
    if (!data || !data.models) return [];
    const set = new Set(data.models.map((m) => m.providerId));
    return Array.from(set);
  }, [data]);

  // 关键 KPI 计算
  const stats = useMemo(() => {
    if (!filteredModels.length) {
      return {
        topSpeedModel: "-",
        topSpeedRate: 0,
        lowestTtftModel: "-",
        lowestTtftP50: 0,
        totalSamples: 0,
        overallAvgTtft: 0,
      };
    }

    let topSpeed = filteredModels[0];
    let lowestTtft = filteredModels[0];
    let totalSamples = 0;
    let sumTtft = 0;

    for (const m of filteredModels) {
      totalSamples += m.sampleCount;
      sumTtft += m.avgTtftMs * m.sampleCount;
      if (m.avgTokensPerSecond > topSpeed.avgTokensPerSecond) {
        topSpeed = m;
      }
      if (
        m.p50TtftMs > 0 &&
        (lowestTtft.p50TtftMs === 0 || m.p50TtftMs < lowestTtft.p50TtftMs)
      ) {
        lowestTtft = m;
      }
    }

    return {
      topSpeedModel: topSpeed.modelId,
      topSpeedRate: Math.round(topSpeed.avgTokensPerSecond * 10) / 10,
      lowestTtftModel: lowestTtft.modelId,
      lowestTtftP50: Math.round(lowestTtft.p50TtftMs),
      totalSamples,
      overallAvgTtft: totalSamples > 0 ? Math.round(sumTtft / totalSamples) : 0,
    };
  }, [filteredModels]);

  if (!isOpen) return null;

  // 图表最大值计算
  const maxChartValue = Math.max(
    ...filteredModels.map((m) => {
      if (chartMode === "ttft") return Math.max(m.p90TtftMs, m.p50TtftMs, 100);
      if (chartMode === "totalDuration")
        return Math.max(m.p90TotalDurationMs, m.p50TotalDurationMs, 500);
      return Math.max(m.avgTokensPerSecond, m.maxTokensPerSecond, 20);
    }),
    10,
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/60 backdrop-blur-md animate-in fade-in duration-200">
      <div
        className="relative flex flex-col w-full max-w-5xl max-h-[90vh] rounded-2xl bg-white/95 dark:bg-zinc-900/95 border border-zinc-200/80 dark:border-zinc-800/80 shadow-2xl overflow-hidden backdrop-blur-xl transition-all"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-200/60 dark:border-zinc-800/60 bg-zinc-50/50 dark:bg-zinc-900/50">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white shadow-md shadow-indigo-500/20">
              <BarChart3 className="size-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
                  模型流式性能大盘 & P50/P90 延迟对比
                </h2>
                <span className="rounded-full bg-indigo-50 dark:bg-indigo-950/60 px-2 py-0.5 text-xs font-semibold text-indigo-600 dark:text-indigo-400 border border-indigo-200/40 dark:border-indigo-800/40">
                  实时统计
                </span>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                基于真实请求环形缓冲区无锁统计，展示首字延迟
                (TTFT)、生成速率与工具执行耗时
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={loadData}
              disabled={loading}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer border border-zinc-200/60 dark:border-zinc-800/60"
              title="刷新性能数据"
            >
              <RefreshCw
                className={`size-3.5 ${loading ? "animate-spin text-indigo-500" : ""}`}
              />
              <span>刷新</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              className="flex size-8 items-center justify-center rounded-lg text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* 异常提示 */}
          {errorMessage && (
            <div className="flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/50 dark:text-rose-300">
              <AlertTriangle className="size-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* KPI Summary Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5">
            <div className="rounded-xl border border-indigo-200/60 dark:border-indigo-900/40 bg-gradient-to-br from-indigo-50/50 to-white dark:from-indigo-950/20 dark:to-zinc-900 p-4 shadow-xs">
              <div className="flex items-center justify-between text-indigo-600 dark:text-indigo-400 mb-1.5">
                <span className="text-xs font-medium">最快首字响应 (P50)</span>
                <Zap className="size-4" />
              </div>
              <div className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">
                {stats.lowestTtftP50 > 0 ? `${stats.lowestTtftP50} ms` : "-"}
              </div>
              <div
                className="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1 truncate"
                title={stats.lowestTtftModel}
              >
                模型: {stats.lowestTtftModel}
              </div>
            </div>

            <div className="rounded-xl border border-purple-200/60 dark:border-purple-900/40 bg-gradient-to-br from-purple-50/50 to-white dark:from-purple-950/20 dark:to-zinc-900 p-4 shadow-xs">
              <div className="flex items-center justify-between text-purple-600 dark:text-purple-400 mb-1.5">
                <span className="text-xs font-medium">最高纯生成速率</span>
                <Gauge className="size-4" />
              </div>
              <div className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">
                {stats.topSpeedRate > 0 ? `${stats.topSpeedRate} t/s` : "-"}
              </div>
              <div
                className="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1 truncate"
                title={stats.topSpeedModel}
              >
                模型: {stats.topSpeedModel}
              </div>
            </div>

            <div className="rounded-xl border border-emerald-200/60 dark:border-emerald-900/40 bg-gradient-to-br from-emerald-50/50 to-white dark:from-emerald-950/20 dark:to-zinc-900 p-4 shadow-xs">
              <div className="flex items-center justify-between text-emerald-600 dark:text-emerald-400 mb-1.5">
                <span className="text-xs font-medium">综合平均 TTFT</span>
                <Clock className="size-4" />
              </div>
              <div className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">
                {stats.overallAvgTtft > 0 ? `${stats.overallAvgTtft} ms` : "-"}
              </div>
              <div className="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">
                扣除工具与网络建连
              </div>
            </div>

            <div className="rounded-xl border border-amber-200/60 dark:border-amber-900/40 bg-gradient-to-br from-amber-50/50 to-white dark:from-amber-950/20 dark:to-zinc-900 p-4 shadow-xs">
              <div className="flex items-center justify-between text-amber-600 dark:text-amber-400 mb-1.5">
                <span className="text-xs font-medium">已采集有效样本数</span>
                <Activity className="size-4" />
              </div>
              <div className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">
                {stats.totalSamples} 次
              </div>
              <div className="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">
                最近 100 轮请求滚动
              </div>
            </div>
          </div>

          {/* Controls: Provider Filter & Chart Mode */}
          <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
            {/* Provider Filter Tabs */}
            <div className="flex items-center gap-1.5 overflow-x-auto py-1">
              <button
                type="button"
                onClick={() => setSelectedProvider("ALL")}
                className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                  selectedProvider === "ALL"
                    ? "bg-indigo-600 text-white shadow-xs"
                    : "bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200 dark:hover:bg-zinc-700"
                }`}
              >
                全部供应商 ({data?.models?.length ?? 0})
              </button>
              {providerList.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setSelectedProvider(p)}
                  className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                    selectedProvider === p
                      ? "bg-indigo-600 text-white shadow-xs"
                      : "bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200 dark:hover:bg-zinc-700"
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>

            {/* Chart Mode Switcher */}
            <div className="flex items-center gap-1 bg-zinc-100 dark:bg-zinc-800/80 p-0.5 rounded-lg border border-zinc-200/60 dark:border-zinc-700/60">
              <button
                type="button"
                onClick={() => setChartMode("ttft")}
                className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all cursor-pointer ${
                  chartMode === "ttft"
                    ? "bg-white dark:bg-zinc-900 text-indigo-600 dark:text-indigo-400 shadow-2xs font-semibold"
                    : "text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
                }`}
              >
                ⚡ 首字延迟 (P50/P90)
              </button>
              <button
                type="button"
                onClick={() => setChartMode("totalDuration")}
                className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all cursor-pointer ${
                  chartMode === "totalDuration"
                    ? "bg-white dark:bg-zinc-900 text-indigo-600 dark:text-indigo-400 shadow-2xs font-semibold"
                    : "text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
                }`}
              >
                ⏱️ 总耗时 (P50/P90)
              </button>
              <button
                type="button"
                onClick={() => setChartMode("tokensPerSecond")}
                className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all cursor-pointer ${
                  chartMode === "tokensPerSecond"
                    ? "bg-white dark:bg-zinc-900 text-indigo-600 dark:text-indigo-400 shadow-2xs font-semibold"
                    : "text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
                }`}
              >
                🚀 纯生成速率 (Tokens/s)
              </button>
            </div>
          </div>

          {/* Interactive Visual Latency Comparison Chart */}
          <div className="rounded-xl border border-zinc-200/80 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-900/40 p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                  {chartMode === "ttft" &&
                    "首字延迟 (TTFT) 对比（毫秒越低越快）"}
                  {chartMode === "totalDuration" &&
                    "单次会话总耗时对比（毫秒）"}
                  {chartMode === "tokensPerSecond" &&
                    "纯文本生成速率对比（Tokens/秒越高越快）"}
                </span>
                <span className="text-xs text-zinc-400">
                  {chartMode === "tokensPerSecond"
                    ? "(均值 vs 峰值)"
                    : "(P50 典型 vs P90 尾部)"}
                </span>
              </div>
              <div className="flex items-center gap-4 text-xs font-medium">
                {chartMode === "tokensPerSecond" ? (
                  <>
                    <span className="flex items-center gap-1.5 text-indigo-600 dark:text-indigo-400">
                      <span className="size-2.5 rounded-xs bg-indigo-500" />
                      平均生成速率
                    </span>
                    <span className="flex items-center gap-1.5 text-purple-600 dark:text-purple-400">
                      <span className="size-2.5 rounded-xs bg-purple-500" />
                      峰值生成速率
                    </span>
                  </>
                ) : (
                  <>
                    <span className="flex items-center gap-1.5 text-indigo-600 dark:text-indigo-400">
                      <span className="size-2.5 rounded-xs bg-indigo-500" />
                      P50 (中位数)
                    </span>
                    <span className="flex items-center gap-1.5 text-amber-600 dark:text-amber-400">
                      <span className="size-2.5 rounded-xs bg-amber-500" />
                      P90 (90% 分位)
                    </span>
                  </>
                )}
              </div>
            </div>

            {filteredModels.length === 0 ? (
              <div className="py-12 text-center text-zinc-400 text-xs">
                暂无模型性能样本数据，请发起对话后查看。
              </div>
            ) : (
              <div className="space-y-3">
                {filteredModels.map((m) => {
                  let val1 = 0;
                  let val2 = 0;
                  let unit = "ms";
                  let label1 = "P50";
                  let label2 = "P90";

                  if (chartMode === "ttft") {
                    val1 = m.p50TtftMs;
                    val2 = m.p90TtftMs;
                    unit = "ms";
                  } else if (chartMode === "totalDuration") {
                    val1 = m.p50TotalDurationMs;
                    val2 = m.p90TotalDurationMs;
                    unit = "ms";
                  } else {
                    val1 = m.avgTokensPerSecond;
                    val2 = m.maxTokensPerSecond;
                    label1 = "Avg";
                    label2 = "Max";
                    unit = "t/s";
                  }

                  const pct1 = Math.min(
                    100,
                    Math.max(3, (val1 / maxChartValue) * 100),
                  );
                  const pct2 = Math.min(
                    100,
                    Math.max(3, (val2 / maxChartValue) * 100),
                  );
                  const isCurrentModel =
                    initialModel && m.modelId === initialModel;

                  return (
                    <div
                      key={`${m.providerId}-${m.modelId}`}
                      className={`group rounded-lg p-2.5 transition-all border ${
                        isCurrentModel
                          ? "bg-indigo-50/50 dark:bg-indigo-950/30 border-indigo-300 dark:border-indigo-700 shadow-2xs"
                          : "border-zinc-200/50 dark:border-zinc-800/50 hover:bg-white dark:hover:bg-zinc-800/60 hover:border-zinc-300 dark:hover:border-zinc-700"
                      }`}
                    >
                      <div className="flex items-center justify-between text-xs mb-1.5">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-zinc-900 dark:text-zinc-100">
                            {m.modelId}
                          </span>
                          <span className="rounded px-1.5 py-0.2 bg-zinc-200/60 dark:bg-zinc-800 text-[10px] font-mono text-zinc-500">
                            {m.providerId}
                          </span>
                          {isCurrentModel && (
                            <span className="rounded bg-indigo-100 dark:bg-indigo-900/60 px-1.5 py-0.2 text-[10px] font-medium text-indigo-700 dark:text-indigo-300">
                              当前模型
                            </span>
                          )}
                          {m.lowSampleWarning && (
                            <span
                              className="flex items-center gap-1 rounded bg-amber-50 dark:bg-amber-950/60 px-1.5 py-0.2 text-[10px] font-medium text-amber-600 dark:text-amber-400 border border-amber-200/50"
                              title="样本数小于 5 次，统计置信度较低"
                            >
                              <AlertTriangle className="size-2.5" />
                              低样本 ({m.sampleCount})
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-xs font-mono">
                          <span className="text-indigo-600 dark:text-indigo-400 font-medium">
                            {label1}: {val1} {unit}
                          </span>
                          <span className="text-amber-600 dark:text-amber-400 font-medium">
                            {label2}: {val2} {unit}
                          </span>
                        </div>
                      </div>

                      {/* Grouped Bar Graphic */}
                      <div className="space-y-1">
                        {/* Bar 1 */}
                        <div className="h-2.5 w-full bg-zinc-200/50 dark:bg-zinc-800 rounded-full overflow-hidden flex">
                          <div
                            className="h-full bg-gradient-to-r from-indigo-500 to-indigo-600 rounded-full transition-all duration-500"
                            style={{ width: `${pct1}%` }}
                          />
                        </div>
                        {/* Bar 2 */}
                        <div className="h-2.5 w-full bg-zinc-200/50 dark:bg-zinc-800 rounded-full overflow-hidden flex">
                          <div
                            className={`h-full rounded-full transition-all duration-500 ${
                              chartMode === "tokensPerSecond"
                                ? "bg-gradient-to-r from-purple-500 to-purple-600"
                                : "bg-gradient-to-r from-amber-500 to-amber-600"
                            }`}
                            style={{ width: `${pct2}%` }}
                          />
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Model Performance Benchmark Table */}
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Layers className="size-4 text-indigo-500" />
              <span>全量指标明细列表</span>
            </h3>

            <div className="overflow-x-auto rounded-xl border border-zinc-200/80 dark:border-zinc-800/80 shadow-2xs">
              <table className="w-full text-left text-xs">
                <thead className="bg-zinc-100/70 dark:bg-zinc-800/60 text-zinc-600 dark:text-zinc-400 font-semibold border-b border-zinc-200/60 dark:border-zinc-800/60">
                  <tr>
                    <th className="py-2.5 px-3.5">模型名称</th>
                    <th className="py-2.5 px-3">样本量</th>
                    <th className="py-2.5 px-3">TTFT (P50 / P90)</th>
                    <th className="py-2.5 px-3">TTFT (均值 / 极值)</th>
                    <th className="py-2.5 px-3">生成速率 (均值 / 峰值)</th>
                    <th className="py-2.5 px-3">总耗时 (P50 / P90)</th>
                    <th className="py-2.5 px-3">工具耗时</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200/40 dark:divide-zinc-800/40">
                  {filteredModels.map((m) => (
                    <tr
                      key={`${m.providerId}-${m.modelId}`}
                      className="hover:bg-zinc-50/70 dark:hover:bg-zinc-800/40 transition-colors"
                    >
                      <td className="py-2.5 px-3.5 font-medium text-zinc-900 dark:text-zinc-100">
                        <div className="flex items-center gap-1.5">
                          <span>{m.modelId}</span>
                          <span className="text-[10px] text-zinc-400 font-mono">
                            ({m.providerId})
                          </span>
                        </div>
                      </td>
                      <td className="py-2.5 px-3">
                        <span
                          className={`inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-mono ${
                            m.lowSampleWarning
                              ? "bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200/60"
                              : "bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300"
                          }`}
                        >
                          {m.sampleCount} 次
                        </span>
                      </td>
                      <td className="py-2.5 px-3 font-mono font-medium text-indigo-600 dark:text-indigo-400">
                        {m.p50TtftMs} ms / {m.p90TtftMs} ms
                      </td>
                      <td className="py-2.5 px-3 font-mono text-zinc-500 dark:text-zinc-400">
                        {Math.round(m.avgTtftMs)} ms ({m.minTtftMs}~
                        {m.maxTtftMs})
                      </td>
                      <td className="py-2.5 px-3 font-mono font-medium text-purple-600 dark:text-purple-400">
                        {m.avgTokensPerSecond} t/s / {m.maxTokensPerSecond} t/s
                      </td>
                      <td className="py-2.5 px-3 font-mono text-zinc-600 dark:text-zinc-300">
                        {m.p50TotalDurationMs} ms / {m.p90TotalDurationMs} ms
                      </td>
                      <td className="py-2.5 px-3 font-mono text-zinc-500 dark:text-zinc-400">
                        {m.avgToolCallDurationMs > 0
                          ? `${Math.round(m.avgToolCallDurationMs)} ms`
                          : "-"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-3 border-t border-zinc-200/60 dark:border-zinc-800/60 bg-zinc-50/50 dark:bg-zinc-900/50 text-xs text-zinc-500 dark:text-zinc-400">
          <div className="flex items-center gap-1.5">
            <Sparkles className="size-3.5 text-indigo-500" />
            <span>纯文本生成速率已排除首字延迟与工具调用执行耗时。</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 font-medium hover:opacity-90 transition-opacity cursor-pointer"
          >
            完成
          </button>
        </div>
      </div>
    </div>
  );
}
