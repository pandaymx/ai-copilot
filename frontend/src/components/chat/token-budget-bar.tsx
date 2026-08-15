"use client";

import {
  AlertCircle,
  AlertTriangle,
  ArrowUpRight,
  CheckCircle2,
  Coins,
  RefreshCw,
  Sparkles,
  Zap,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useTokenBudget } from "@/context/token-budget-context";
import { cn } from "@/lib/utils";

interface TokenBudgetBarProps {
  /** 是否以极简紧凑模式展示（如嵌入在顶部导航栏或紧凑容器中） */
  compact?: boolean;
  className?: string;
}

function formatNumber(num: number): string {
  if (num >= 1_000_000) {
    return `${(num / 1_000_000).toFixed(2)}M`;
  }
  if (num >= 1_000) {
    return `${(num / 1_000).toFixed(1)}k`;
  }
  return num.toLocaleString();
}

export function TokenBudgetBar({
  compact = false,
  className,
}: TokenBudgetBarProps) {
  const {
    realtimeUsage,
    estimatedDeltaTokens,
    estimatedCostRmb,
    isOverBudget,
    alertLevel,
    refreshRealtimeUsage,
  } = useTokenBudget();

  const [popoverOpen, setPopoverOpen] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // 点击外部自动收起 Popover
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setPopoverOpen(false);
      }
    }
    if (popoverOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [popoverOpen]);

  const handleRefresh = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsRefreshing(true);
    await refreshRealtimeUsage();
    setIsRefreshing(false);
  };

  const used = realtimeUsage?.usedTokens ?? 0;
  const quota = realtimeUsage?.quotaTokens ?? 0;
  const remaining = realtimeUsage?.remainingTokens ?? 0;
  const usedPercent = quota > 0 ? Math.min(100, (used / quota) * 100) : 0;

  // 加上草稿预估后的投影百分比
  const projectedPercent =
    quota > 0
      ? Math.min(100, ((used + estimatedDeltaTokens) / quota) * 100)
      : usedPercent;
  const deltaPercent = Math.max(0, projectedPercent - usedPercent);

  // 配额分级色彩定义
  const colorMap = {
    normal: {
      bar: "bg-gradient-to-r from-emerald-500 to-teal-500",
      deltaBar: "bg-emerald-400/50",
      text: "text-emerald-700 dark:text-emerald-400",
      badge:
        "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300",
      border: "border-emerald-200/80 dark:border-emerald-900/60",
      ring: "ring-emerald-500/20",
      icon: CheckCircle2,
    },
    warning: {
      bar: "bg-gradient-to-r from-amber-500 to-orange-500",
      deltaBar: "bg-amber-400/50",
      text: "text-amber-700 dark:text-amber-400",
      badge:
        "bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300",
      border: "border-amber-200/80 dark:border-amber-900/60",
      ring: "ring-amber-500/20",
      icon: AlertTriangle,
    },
    danger: {
      bar: "bg-gradient-to-r from-rose-500 to-red-600",
      deltaBar: "bg-rose-400/50",
      text: "text-rose-700 dark:text-rose-400",
      badge: "bg-rose-100 text-rose-800 dark:bg-rose-950/60 dark:text-rose-300",
      border: "border-rose-200/80 dark:border-rose-900/60",
      ring: "ring-rose-500/20",
      icon: AlertCircle,
    },
    exceeded: {
      bar: "bg-gradient-to-r from-red-600 to-rose-700 animate-pulse",
      deltaBar: "bg-rose-500/60",
      text: "text-rose-700 dark:text-rose-400 font-bold",
      badge: "bg-rose-500 text-white font-bold animate-pulse",
      border: "border-rose-400 dark:border-rose-800",
      ring: "ring-rose-500/30",
      icon: AlertCircle,
    },
  };

  const currentTheme = colorMap[alertLevel];

  if (compact) {
    return (
      <div
        ref={containerRef}
        className={cn("relative inline-flex items-center", className)}
      >
        <button
          type="button"
          onClick={() => setPopoverOpen((prev) => !prev)}
          className={cn(
            "group flex items-center gap-1.5 rounded-lg border px-2 py-1 text-[11px] font-medium transition-all shadow-2xs hover:shadow-xs",
            currentTheme.border,
            "bg-white/80 dark:bg-zinc-900/80 backdrop-blur",
            isOverBudget && "ring-2 ring-rose-500/40",
          )}
          title={`本月配额已用 ${usedPercent.toFixed(1)}%（${formatNumber(used)} / ${formatNumber(quota)} Tokens）`}
        >
          <Coins className={cn("size-3.5 shrink-0", currentTheme.text)} />
          <div className="flex items-center gap-1">
            <span
              className={cn("tabular-nums font-semibold", currentTheme.text)}
            >
              {usedPercent.toFixed(0)}%
            </span>
            {estimatedDeltaTokens > 0 && (
              <span className="text-[10px] text-zinc-400 dark:text-zinc-500">
                (+{formatNumber(estimatedDeltaTokens)})
              </span>
            )}
          </div>
          {/* 微型进度指示条 */}
          <div className="h-1.5 w-10 overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800 relative">
            <div
              className={cn(
                "h-full transition-all duration-300",
                currentTheme.bar,
              )}
              style={{ width: `${usedPercent}%` }}
            />
            {deltaPercent > 0 && (
              <div
                className={cn(
                  "absolute top-0 bottom-0 transition-all duration-300",
                  currentTheme.deltaBar,
                )}
                style={{
                  left: `${usedPercent}%`,
                  width: `${deltaPercent}%`,
                }}
              />
            )}
          </div>
        </button>

        {/* 紧凑模式悬浮 Popover */}
        {popoverOpen && (
          <BudgetDetailPopover
            realtimeUsage={realtimeUsage}
            estimatedDeltaTokens={estimatedDeltaTokens}
            estimatedCostRmb={estimatedCostRmb}
            isRefreshing={isRefreshing}
            onRefresh={handleRefresh}
            onClose={() => setPopoverOpen(false)}
          />
        )}
      </div>
    );
  }

  // 完整标准模式（用于 Sidebar 等主区域）
  return (
    <div ref={containerRef} className={cn("relative w-full", className)}>
      <button
        type="button"
        onClick={() => setPopoverOpen((prev) => !prev)}
        className={cn(
          "group block w-full rounded-xl border p-2.5 transition-all text-left cursor-pointer outline-none",
          "bg-white/70 shadow-2xs hover:bg-white hover:shadow-md dark:bg-zinc-900/50 dark:hover:bg-zinc-900/80 backdrop-blur",
          currentTheme.border,
          popoverOpen && `ring-2 ${currentTheme.ring}`,
        )}
      >
        {/* 顶部标题与百分比 */}
        <div className="flex items-center justify-between gap-1 mb-1.5">
          <div className="flex items-center gap-1.5 min-w-0">
            <Coins className={cn("size-3.5 shrink-0", currentTheme.text)} />
            <span className="truncate text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              月度 Token 配额
            </span>
          </div>

          <div className="flex items-center gap-1">
            <span
              className={cn(
                "rounded px-1.5 py-0.5 text-[10px] font-bold tabular-nums",
                currentTheme.badge,
              )}
            >
              {usedPercent.toFixed(1)}%
            </span>
          </div>
        </div>

        {/* 进度条轨道 */}
        <div className="relative h-2 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800/80">
          {/* 已消耗主进度 */}
          <div
            className={cn(
              "h-full rounded-full transition-all duration-500",
              currentTheme.bar,
            )}
            style={{ width: `${usedPercent}%` }}
          />
          {/* 实时输入草稿投影预览增量条 */}
          {deltaPercent > 0 && (
            <div
              className={cn(
                "absolute top-0 bottom-0 transition-all duration-300 animate-pulse",
                currentTheme.deltaBar,
              )}
              style={{
                left: `${usedPercent}%`,
                width: `${deltaPercent}%`,
              }}
            />
          )}
        </div>

        {/* 底部小字说明与草稿预估 */}
        <div className="mt-1.5 flex items-center justify-between text-[11px] text-zinc-500 dark:text-zinc-400">
          <span className="tabular-nums">
            {formatNumber(used)} / {quota > 0 ? formatNumber(quota) : "不限"}
          </span>

          {estimatedDeltaTokens > 0 ? (
            <span className="flex items-center gap-0.5 text-indigo-600 dark:text-indigo-400 font-medium">
              <Zap className="size-3" />
              +~{formatNumber(estimatedDeltaTokens)}
            </span>
          ) : (
            <span className="text-zinc-400 dark:text-zinc-500">
              剩 {formatNumber(remaining)}
            </span>
          )}
        </div>

        {/* 超额高亮警告条目 */}
        {isOverBudget && (
          <div className="mt-2 flex items-center gap-1.5 rounded-lg bg-rose-50 px-2 py-1 text-[11px] font-medium text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border border-rose-200 dark:border-rose-900/60">
            <AlertCircle className="size-3.5 shrink-0" />
            <span className="truncate">本月配额已耗尽，已触发保护</span>
          </div>
        )}
      </button>

      {/* 详细浮层 Popover */}
      {popoverOpen && (
        <BudgetDetailPopover
          realtimeUsage={realtimeUsage}
          estimatedDeltaTokens={estimatedDeltaTokens}
          estimatedCostRmb={estimatedCostRmb}
          isRefreshing={isRefreshing}
          onRefresh={handleRefresh}
          onClose={() => setPopoverOpen(false)}
        />
      )}
    </div>
  );
}

interface BudgetDetailPopoverProps {
  realtimeUsage: ReturnType<typeof useTokenBudget>["realtimeUsage"];
  estimatedDeltaTokens: number;
  estimatedCostRmb: number;
  isRefreshing: boolean;
  onRefresh: (e: React.MouseEvent) => void;
  onClose: () => void;
}

function BudgetDetailPopover({
  realtimeUsage,
  estimatedDeltaTokens,
  estimatedCostRmb,
  isRefreshing,
  onRefresh,
  onClose,
}: BudgetDetailPopoverProps) {
  const used = realtimeUsage?.usedTokens ?? 0;
  const quota = realtimeUsage?.quotaTokens ?? 0;
  const remaining = realtimeUsage?.remainingTokens ?? 0;
  const threshold = realtimeUsage?.alertThresholdPercent ?? 80.0;
  const month = realtimeUsage?.month ?? "";

  return (
    <div
      className={cn(
        "absolute left-0 top-full mt-2 z-50 w-72 rounded-2xl border border-zinc-200/80 bg-white/95 p-3.5 shadow-xl backdrop-blur-xl dark:border-zinc-800 dark:bg-zinc-900/95 animate-in fade-in zoom-in-95 duration-150",
      )}
    >
      {/* 浮层头部 */}
      <div className="flex items-center justify-between border-b border-zinc-100 pb-2.5 dark:border-zinc-800">
        <div className="flex items-center gap-1.5">
          <div className="flex size-6 items-center justify-center rounded-lg bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400">
            <Coins className="size-3.5" />
          </div>
          <div>
            <h4 className="text-xs font-bold text-zinc-900 dark:text-zinc-100">
              实时 Token 预算明细
            </h4>
            <p className="text-[10px] text-zinc-400 dark:text-zinc-500">
              {month ? `${month} 账单周期` : "当月计量"} · Redis 实时同步
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={onRefresh}
          className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-300 transition-colors"
          title="刷新实时用量"
        >
          <RefreshCw
            className={cn(
              "size-3.5",
              isRefreshing && "animate-spin text-indigo-500",
            )}
          />
        </button>
      </div>

      {/* 统计指标卡片网格 */}
      <div className="grid grid-cols-2 gap-2 py-2.5 text-xs">
        <div className="rounded-xl bg-zinc-50/80 p-2 dark:bg-zinc-800/50">
          <span className="text-[10px] text-zinc-400 dark:text-zinc-500">
            本月已用
          </span>
          <p className="mt-0.5 font-bold text-zinc-800 dark:text-zinc-200 tabular-nums">
            {used.toLocaleString()}
          </p>
        </div>
        <div className="rounded-xl bg-zinc-50/80 p-2 dark:bg-zinc-800/50">
          <span className="text-[10px] text-zinc-400 dark:text-zinc-500">
            剩余额度
          </span>
          <p className="mt-0.5 font-bold text-emerald-600 dark:text-emerald-400 tabular-nums">
            {remaining.toLocaleString()}
          </p>
        </div>
        <div className="rounded-xl bg-zinc-50/80 p-2 dark:bg-zinc-800/50">
          <span className="text-[10px] text-zinc-400 dark:text-zinc-500">
            月度上限
          </span>
          <p className="mt-0.5 font-semibold text-zinc-700 dark:text-zinc-300 tabular-nums">
            {quota > 0 ? quota.toLocaleString() : "无限制"}
          </p>
        </div>
        <div className="rounded-xl bg-zinc-50/80 p-2 dark:bg-zinc-800/50">
          <span className="text-[10px] text-zinc-400 dark:text-zinc-500">
            预警阈值
          </span>
          <p className="mt-0.5 font-semibold text-amber-600 dark:text-amber-400 tabular-nums">
            {threshold}%
          </p>
        </div>
      </div>

      {/* 草稿预估提示 */}
      {estimatedDeltaTokens > 0 && (
        <div className="mb-2.5 rounded-xl border border-indigo-100 bg-indigo-50/70 p-2 text-xs text-indigo-900 dark:border-indigo-900/50 dark:bg-indigo-950/40 dark:text-indigo-300">
          <div className="flex items-center gap-1 font-semibold text-[11px]">
            <Sparkles className="size-3 text-indigo-500" />
            <span>输入草稿预估消耗</span>
          </div>
          <div className="mt-1 flex items-center justify-between text-[11px]">
            <span className="tabular-nums font-mono">
              ~{estimatedDeltaTokens.toLocaleString()} Tokens
            </span>
            <span className="text-[10px] text-indigo-600 dark:text-indigo-400">
              约 ¥{estimatedCostRmb.toFixed(4)}
            </span>
          </div>
        </div>
      )}

      {/* 底部跳转入口 */}
      <div className="border-t border-zinc-100 pt-2 dark:border-zinc-800">
        <Link
          href="/usage"
          onClick={onClose}
          className="flex items-center justify-between rounded-xl px-2.5 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-indigo-50 hover:text-indigo-600 dark:text-zinc-300 dark:hover:bg-indigo-950/50 dark:hover:text-indigo-400 transition-colors"
        >
          <span>进入成本看板总览</span>
          <ArrowUpRight className="size-3.5" />
        </Link>
      </div>
    </div>
  );
}
