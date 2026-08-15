"use client";

import {
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  Calendar,
  CheckCircle2,
  Coins,
  Cpu,
  Layers,
  Loader2,
  RefreshCw,
  Settings,
  ShieldAlert,
  Users,
} from "lucide-react";
import Link from "next/link";

import { useCallback, useEffect, useRef, useState } from "react";
import { ModelPerformanceModal } from "@/components/chat/model-performance-modal";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { QuotaConfigDialog } from "@/components/usage/quota-config-dialog";
import { UsageChart } from "@/components/usage/usage-chart";
import {
  fetchUsageDashboardApi,
  type QuotaConfig,
  type UsageDashboardData,
  updateQuotaConfigApi,
} from "@/lib/api";

interface Toast {
  kind: "success" | "error";
  message: string;
}

function getCurrentMonthStr(): string {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  return `${yyyy}-${mm}`;
}

export default function UsageDashboardPage() {
  const [selectedMonth, setSelectedMonth] = useState<string>(
    getCurrentMonthStr(),
  );
  const [data, setData] = useState<UsageDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [configOpen, setConfigOpen] = useState(false);
  const [showPerformanceModal, setShowPerformanceModal] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [toast, setToast] = useState<Toast | null>(null);

  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((kind: Toast["kind"], message: string) => {
    setToast({ kind, message });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  const fetchDashboardData = useCallback(async () => {
    setLoading(true);
    const result = await fetchUsageDashboardApi(selectedMonth);
    setData(result);
    setLoading(false);
  }, [selectedMonth]);

  useEffect(() => {
    void fetchDashboardData();
  }, [fetchDashboardData]);

  useEffect(() => {
    return () => {
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, []);

  const handleSaveConfig = async (newConfig: QuotaConfig) => {
    setSavingConfig(true);
    const updated = await updateQuotaConfigApi(newConfig);
    setSavingConfig(false);
    if (updated) {
      setConfigOpen(false);
      showToast("success", "配额与告警阈值配置已保存，实时生效！");
      void fetchDashboardData();
    } else {
      showToast("error", "保存配置失败，请重试");
    }
  };

  const quota = data?.quotaConfig;
  const totalTokens = data?.totalTokens || 0;
  const quotaTokens = quota?.monthlyTokenQuota || 0;
  const usedPercent =
    quotaTokens > 0 ? Math.min(100, (totalTokens / quotaTokens) * 100) : 0;

  return (
    <div className="relative min-h-screen bg-ambient-mesh bg-zinc-50 dark:bg-zinc-950">
      {/* 顶部 Header */}
      <header className="sticky top-0 z-30 border-b border-zinc-200/60 bg-white/70 backdrop-blur-xl dark:border-zinc-800/60 dark:bg-zinc-950/70">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <Link href="/">
              <Button
                variant="ghost"
                size="icon-sm"
                className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              >
                <ArrowLeft className="size-4" />
              </Button>
            </Link>
            <div className="flex items-center gap-2">
              <div className="flex size-7 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 text-white shadow-md shadow-indigo-500/20">
                <Coins className="size-4" />
              </div>
              <div>
                <h1 className="font-heading text-sm font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
                  成本看板与配额管理
                </h1>
                <p className="text-[10px] text-zinc-500 dark:text-zinc-400">
                  可视化 Token 消耗、模型费用分布与告警阈值管控
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            {/* 月份选择器 */}
            <div className="flex items-center gap-1.5 rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-1.5 text-xs text-zinc-700 shadow-2xs dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-200">
              <Calendar className="size-3.5 text-zinc-400" />
              <input
                type="month"
                value={selectedMonth}
                onChange={(e) =>
                  e.target.value && setSelectedMonth(e.target.value)
                }
                className="bg-transparent font-mono outline-none cursor-pointer"
              />
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={() => void fetchDashboardData()}
              disabled={loading}
              className="gap-1 text-xs"
            >
              <RefreshCw
                className={`size-3.5 ${loading ? "animate-spin" : ""}`}
              />
              <span>刷新</span>
            </Button>

            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowPerformanceModal(true)}
              className="gap-1.5 text-xs text-indigo-600 dark:text-indigo-400 border-indigo-200/80 dark:border-indigo-800/80 hover:bg-indigo-50 dark:hover:bg-indigo-950/50 cursor-pointer"
            >
              <BarChart3 className="size-3.5" />
              <span>流式性能大盘</span>
            </Button>

            <Button
              size="sm"
              onClick={() => setConfigOpen(true)}
              className="gap-1.5 bg-gradient-to-r from-indigo-600 to-purple-600 text-xs font-semibold text-white shadow-md hover:from-indigo-500 hover:to-purple-500 cursor-pointer"
            >
              <Settings className="size-3.5" />
              <span>设置告警阈值</span>
            </Button>

            <ThemeToggle />
          </div>
        </div>
      </header>

      {/* 主面板内容 */}
      <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-6 sm:px-6">
        {/* 告警 Banner */}
        {data?.quotaAlertTriggered && (
          <div className="flex items-center justify-between rounded-2xl border border-amber-500/30 bg-gradient-to-r from-amber-500/15 via-orange-500/10 to-rose-500/15 p-4 shadow-md text-amber-900 dark:text-amber-200">
            <div className="flex items-center gap-3">
              <div className="flex size-9 items-center justify-center rounded-xl bg-amber-500 text-white shadow-sm shadow-amber-500/40">
                <ShieldAlert className="size-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold tracking-tight">
                  ⚠️ 警告：当前用量已触发配额告警阈值！
                </h3>
                <p className="mt-0.5 text-[11px] opacity-90">
                  当前月度 Token 消耗量 ({totalTokens.toLocaleString()})
                  已达到配额 ({quotaTokens.toLocaleString()}) 的{" "}
                  <span className="font-bold underline">
                    {usedPercent.toFixed(1)}%
                  </span>
                  ，已超过预警阈值 ({quota?.alertThresholdPercent}%)。
                </p>
              </div>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setConfigOpen(true)}
              className="shrink-0 border-amber-500/40 bg-white/80 text-xs font-semibold text-amber-900 hover:bg-white dark:bg-zinc-900 dark:text-amber-300"
            >
              调高配额
            </Button>
          </div>
        )}

        {/* 4 大核心指标 Summary Cards */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* 1. 当月 Token 消耗 */}
          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-4 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">当月 Token 总消耗</span>
              <Coins className="size-4 text-indigo-500" />
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
                {totalTokens.toLocaleString()}
              </span>
            </div>
            <div className="mt-3">
              <div className="flex justify-between text-[11px] text-zinc-400 mb-1">
                <span>配额配比 ({usedPercent.toFixed(1)}%)</span>
                <span>
                  {quotaTokens > 0 ? quotaTokens.toLocaleString() : "无限制"}
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                <div
                  style={{ width: `${usedPercent}%` }}
                  className={`h-full rounded-full transition-all duration-300 ${
                    usedPercent >= (quota?.alertThresholdPercent || 80)
                      ? "bg-amber-500"
                      : "bg-indigo-600"
                  }`}
                />
              </div>
            </div>
          </div>

          {/* 2. 当月预估费用 */}
          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-4 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">当月预估费用 (RMB)</span>
              <Layers className="size-4 text-emerald-500" />
            </div>
            <div className="mt-2 flex items-baseline gap-1 text-emerald-600 dark:text-emerald-400">
              <span className="text-sm font-semibold">¥</span>
              <span className="font-mono text-2xl font-bold tracking-tight">
                {Number(data?.totalCost || 0).toFixed(4)}
              </span>
            </div>
            <div className="mt-3 text-[11px] text-zinc-400">
              费用上限:{" "}
              {quota?.monthlyCostQuotaRmb
                ? `¥${quota.monthlyCostQuotaRmb}`
                : "未设定"}
            </div>
          </div>

          {/* 3. 总请求数 & 活跃用户 */}
          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-4 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">总请求数 / 活跃用户</span>
              <Users className="size-4 text-purple-500" />
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
                {data?.totalRequests || 0}
              </span>
              <span className="text-xs text-zinc-400">次请求</span>
            </div>
            <div className="mt-3 text-[11px] text-zinc-400">
              活跃独立用户:{" "}
              <span className="font-semibold text-zinc-700 dark:text-zinc-200">
                {data?.activeUsers || 0}
              </span>{" "}
              人
            </div>
          </div>

          {/* 4. 模型调用分布 */}
          <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-4 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">使用的 AI 模型数</span>
              <Cpu className="size-4 text-cyan-500" />
            </div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
                {data?.activeModels || 0}
              </span>
              <span className="text-xs text-zinc-400">个模型</span>
            </div>
            <div className="mt-3 text-[11px] text-zinc-400">
              告警阈值:{" "}
              <span className="font-semibold text-amber-600 dark:text-amber-400">
                {quota?.alertThresholdPercent || 80}%
              </span>
            </div>
          </div>
        </div>

        {/* 可视化图表区 */}
        {loading ? (
          <div className="flex h-64 flex-col items-center justify-center rounded-2xl border border-zinc-200/80 bg-white/50 dark:border-zinc-800/80 dark:bg-zinc-900/50">
            <Loader2 className="size-6 animate-spin text-indigo-500" />
            <span className="mt-2 text-xs font-medium text-zinc-500">
              正在聚合成本与用量数据…
            </span>
          </div>
        ) : (
          <UsageChart
            dailyTrend={data?.dailyTrend || []}
            byUser={data?.byUser || []}
            byModel={data?.byModel || []}
          />
        )}
      </main>

      {/* 配额阈值设置弹窗 */}
      <QuotaConfigDialog
        open={configOpen}
        config={quota || null}
        saving={savingConfig}
        onClose={() => setConfigOpen(false)}
        onSave={handleSaveConfig}
      />

      {/* 流式性能对比与 P50/P90 延迟大盘弹窗 */}
      <ModelPerformanceModal
        isOpen={showPerformanceModal}
        onClose={() => setShowPerformanceModal(false)}
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
              <AlertTriangle className="size-4" />
            )}
            {toast.message}
          </div>
        </div>
      )}
    </div>
  );
}
