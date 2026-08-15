"use client";

import {
  AlertTriangle,
  Clock,
  Hourglass,
  RefreshCw,
  ShieldAlert,
  ZapOff,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { fetchRateLimitStatusApi, type RateLimitStatus } from "@/lib/api";
import { cn } from "@/lib/utils";

interface RateLimitIndicatorProps {
  /** 外部传入的最新状态（例如从 SSE error 帧或 chat 响应中捕获） */
  externalStatus?: RateLimitStatus | null;
  /** 手动触发状态刷新的回调引用 */
  onRefreshNeeded?: (refresher: () => Promise<void>) => void;
  className?: string;
}

export function RateLimitIndicator({
  externalStatus,
  onRefreshNeeded,
  className,
}: RateLimitIndicatorProps) {
  const [status, setStatus] = useState<RateLimitStatus | null>(
    externalStatus || null,
  );
  const [countdown, setCountdown] = useState<number>(
    externalStatus?.resetAfterSeconds || 0,
  );
  const [loading, setLoading] = useState<boolean>(false);

  const loadStatus = useCallback(async () => {
    setLoading(true);
    const res = await fetchRateLimitStatusApi();
    if (res) {
      setStatus(res);
      setCountdown(res.resetAfterSeconds || 0);
    }
    setLoading(false);
  }, []);

  // 初始加载与周期性轻量轮询（每 30 秒轮询一次）
  useEffect(() => {
    if (!externalStatus) {
      void loadStatus();
    }
    const timer = setInterval(() => {
      if (!externalStatus) {
        void loadStatus();
      }
    }, 30000);
    return () => clearInterval(timer);
  }, [loadStatus, externalStatus]);

  // 当外部状态更新时同步（如收到 RATE_LIMITED 错误帧）
  useEffect(() => {
    if (externalStatus) {
      setStatus(externalStatus);
      setCountdown(externalStatus.resetAfterSeconds || 0);
    }
  }, [externalStatus]);

  // 注册刷新方法供父组件（如发送消息后）主动调用
  useEffect(() => {
    if (onRefreshNeeded) {
      onRefreshNeeded(loadStatus);
    }
  }, [onRefreshNeeded, loadStatus]);

  // 本地 1 秒倒计时递减
  useEffect(() => {
    if (countdown <= 0) return;
    const tick = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          void loadStatus();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(tick);
  }, [countdown, loadStatus]);

  if (!status) return null;

  const isRateLimited =
    status.isRateLimited || (countdown > 0 && status.remainingRequests <= 0);
  const isQuotaExhausted =
    status.isQuotaExhausted ||
    (status.monthlyQuotaTokens > 0 && status.monthlyRemainingTokens <= 0);
  const isNearRateLimit =
    !isRateLimited && status.remainingRequests <= 5 && status.capacity > 0;
  const isNearQuota = !isQuotaExhausted && status.monthlyUsedPercent >= 90;

  // 如果一切充裕且未受限，渲染极简的健康指示器或在低调模式下隐藏
  if (!isRateLimited && !isQuotaExhausted && !isNearRateLimit && !isNearQuota) {
    return (
      <div
        className={cn(
          "flex items-center gap-1.5 px-2 py-0.5 text-[11px] text-zinc-400 dark:text-zinc-500 font-mono transition-opacity select-none",
          className,
        )}
      >
        <span className="size-1.5 rounded-full bg-emerald-500/80 animate-pulse" />
        <span>
          请求余量: {status.remainingRequests}/{status.capacity}
        </span>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "flex flex-wrap items-center justify-between gap-2 rounded-xl px-3 py-2 text-xs transition-all animate-in fade-in slide-in-from-bottom-1 shadow-2xs",
        isRateLimited
          ? "border border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/50 dark:text-rose-200"
          : isQuotaExhausted
            ? "border border-red-300 bg-red-50 text-red-900 dark:border-red-900/80 dark:bg-red-950/60 dark:text-red-200"
            : isNearRateLimit
              ? "border border-amber-200 bg-amber-50/90 text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200"
              : "border border-amber-200 bg-amber-50/80 text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-200",
        className,
      )}
    >
      <div className="flex items-center gap-2">
        {isRateLimited ? (
          <ZapOff className="size-4 shrink-0 text-rose-600 dark:text-rose-400 animate-bounce" />
        ) : isQuotaExhausted ? (
          <ShieldAlert className="size-4 shrink-0 text-red-600 dark:text-red-400" />
        ) : isNearRateLimit ? (
          <Hourglass className="size-4 shrink-0 text-amber-600 dark:text-amber-400" />
        ) : (
          <AlertTriangle className="size-4 shrink-0 text-amber-600 dark:text-amber-400" />
        )}

        <div className="flex flex-col sm:flex-row sm:items-center sm:gap-2">
          <span className="font-bold">
            {isRateLimited
              ? "请求过于频繁（已触发速率限制）"
              : isQuotaExhausted
                ? "月度 Token 配额已耗尽"
                : isNearRateLimit
                  ? "短时请求频率接近上限"
                  : "月度 Token 配额即将耗尽"}
          </span>

          <span className="text-[11px] opacity-90">
            {isRateLimited ? (
              countdown > 0 ? (
                <>
                  请等待{" "}
                  <strong className="font-mono text-rose-700 dark:text-rose-300">
                    {countdown}s
                  </strong>{" "}
                  后自动重置
                </>
              ) : (
                "正在解除限流，请稍后刷新重试"
              )
            ) : isQuotaExhausted ? (
              "本月额度已用尽，请下月再试或联系管理员增加配额"
            ) : isNearRateLimit ? (
              <>
                当前窗口仅剩{" "}
                <strong className="font-mono text-amber-700 dark:text-amber-300">
                  {status.remainingRequests}/{status.capacity}
                </strong>{" "}
                次请求{countdown > 0 ? ` (${countdown}s 后重置)` : ""}
              </>
            ) : (
              <>
                已消耗{" "}
                <strong className="font-mono">
                  {status.monthlyUsedPercent.toFixed(1)}%
                </strong>
                ，剩余{" "}
                <strong className="font-mono">
                  {status.monthlyRemainingTokens.toLocaleString()}
                </strong>{" "}
                Tokens
              </>
            )}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        {countdown > 0 && isRateLimited && (
          <div className="flex items-center gap-1 font-mono text-[11px] rounded-lg bg-rose-100/80 px-2 py-0.5 text-rose-800 dark:bg-rose-900/60 dark:text-rose-200">
            <Clock className="size-3" />
            <span>{countdown}s</span>
          </div>
        )}

        <button
          type="button"
          onClick={() => void loadStatus()}
          disabled={loading}
          className="rounded-lg p-1 text-zinc-500 hover:bg-black/5 hover:text-zinc-700 dark:text-zinc-400 dark:hover:bg-white/5 dark:hover:text-zinc-200 transition-colors cursor-pointer"
          title="刷新限流状态"
        >
          <RefreshCw className={cn("size-3.5", loading && "animate-spin")} />
        </button>
      </div>
    </div>
  );
}
