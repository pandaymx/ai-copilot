"use client";

import { BarChart2, Clock, Gauge, Wrench, Zap } from "lucide-react";
import { useEffect, useState } from "react";
import type { StreamMetrics } from "@/hooks/useSpringAiStream";
import { ModelPerformanceModal } from "./model-performance-modal";

interface StreamingMetricsBarProps {
  metrics?: StreamMetrics | null;
  streaming?: boolean;
  contentLength?: number;
  providerId?: string;
  modelId?: string;
  toolCallsCount?: number;
}

export function StreamingMetricsBar({
  metrics,
  streaming = false,
  contentLength = 0,
  providerId,
  modelId,
  toolCallsCount = 0,
}: StreamingMetricsBarProps) {
  const [showModal, setShowModal] = useState(false);
  const [liveElapsedMs, setLiveElapsedMs] = useState<number>(0);
  const [streamStartTime] = useState<number>(() => Date.now());

  // 流式生成中，以 200ms 轻量节流定时器跳动已耗时与估算速率，避免过度触发重渲染
  useEffect(() => {
    if (!streaming) return;
    const interval = setInterval(() => {
      setLiveElapsedMs(Date.now() - streamStartTime);
    }, 200);
    return () => clearInterval(interval);
  }, [streaming, streamStartTime]);

  // 如果没有指标且非流式状态，不渲染
  if (!streaming && !metrics) {
    return null;
  }

  // 首字延迟
  const ttft = metrics?.timeToFirstToken;
  // 最终或实时计算的生成速率
  let tokensPerSec = metrics?.tokensPerSecond;
  if (streaming && !tokensPerSec && liveElapsedMs > 0 && contentLength > 0) {
    const pureGenTime = ttft
      ? Math.max(100, liveElapsedMs - ttft)
      : liveElapsedMs;
    const estTokens = Math.max(1, Math.round(contentLength / 3.5));
    tokensPerSec = Math.round(((estTokens * 1000) / pureGenTime) * 10) / 10;
  }

  // 总耗时
  const totalDuration =
    metrics?.totalDuration ?? (streaming ? liveElapsedMs : 0);
  const toolDuration = metrics?.toolCallDuration;

  return (
    <>
      <div className="flex flex-wrap items-center gap-1.5 pt-1.5 text-[11px] font-mono text-zinc-500 dark:text-zinc-400 select-none animate-in fade-in duration-300">
        {/* 指标胶囊容器 */}
        <button
          type="button"
          onClick={() => setShowModal(true)}
          className={`inline-flex items-center gap-2 rounded-lg px-2.5 py-1 border transition-all cursor-pointer ${
            streaming
              ? "bg-indigo-50/80 dark:bg-indigo-950/40 border-indigo-200/60 dark:border-indigo-800/60 text-indigo-700 dark:text-indigo-300 shadow-2xs"
              : "bg-zinc-50/90 dark:bg-zinc-900/60 border-zinc-200/60 dark:border-zinc-800/60 hover:border-indigo-300 dark:hover:border-indigo-700 hover:bg-zinc-100/80 dark:hover:bg-zinc-800/80 text-zinc-600 dark:text-zinc-400 shadow-2xs"
          }`}
          title="点击查看模型性能对比 (P50/P90 延迟与历史速率)"
        >
          {/* 状态动画小圆点 / 闪电图标 */}
          {streaming ? (
            <span className="flex items-center gap-1 text-indigo-600 dark:text-indigo-400 font-semibold">
              <span className="relative flex size-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75" />
                <span className="relative inline-flex rounded-full size-2 bg-indigo-500" />
              </span>
              <span className="text-[10px] uppercase font-sans tracking-wide">
                Stream
              </span>
            </span>
          ) : (
            <Zap className="size-3 text-amber-500 dark:text-amber-400" />
          )}

          {/* TTFT 首字延迟 */}
          {ttft !== undefined && ttft > 0 && (
            <span
              className="flex items-center gap-1"
              title={`首字延迟 (TTFT): ${ttft}ms`}
            >
              <span className="text-zinc-400 dark:text-zinc-500">TTFT</span>
              <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                {ttft}ms
              </span>
            </span>
          )}

          {/* 生成速率 (Tokens/s) */}
          {tokensPerSec !== undefined && tokensPerSec > 0 && (
            <>
              <span className="text-zinc-300 dark:text-zinc-700">·</span>
              <span
                className="flex items-center gap-1"
                title={`纯文本生成速率: ${tokensPerSec} tokens/s ${
                  metrics?.isEstimated ? "(估算)" : ""
                }`}
              >
                <Gauge className="size-3 text-indigo-500" />
                <span className="font-semibold text-indigo-600 dark:text-indigo-400">
                  {tokensPerSec}
                  <span className="text-[9px] font-normal text-zinc-400 ml-0.5">
                    t/s
                  </span>
                </span>
              </span>
            </>
          )}

          {/* 总耗时 */}
          {totalDuration > 0 && (
            <>
              <span className="text-zinc-300 dark:text-zinc-700">·</span>
              <span
                className="flex items-center gap-1"
                title={`总耗时: ${(totalDuration / 1000).toFixed(2)}s`}
              >
                <Clock className="size-3 text-zinc-400" />
                <span className="font-medium text-zinc-700 dark:text-zinc-300">
                  {(totalDuration / 1000).toFixed(1)}s
                </span>
              </span>
            </>
          )}

          {/* 工具调用耗时 (若有) */}
          {toolDuration !== undefined && toolDuration > 0 && (
            <>
              <span className="text-zinc-300 dark:text-zinc-700">·</span>
              <span
                className="flex items-center gap-1 text-purple-600 dark:text-purple-400"
                title={`工具执行耗时 (${toolCallsCount}个工具): ${toolDuration}ms`}
              >
                <Wrench className="size-3 text-purple-500" />
                <span className="font-medium">{toolDuration}ms</span>
              </span>
            </>
          )}

          {/* 图表小图标（提示可交互） */}
          {!streaming && (
            <span className="ml-0.5 flex items-center text-zinc-400 hover:text-indigo-500 transition-colors">
              <BarChart2 className="size-3" />
            </span>
          )}
        </button>
      </div>

      {/* 模型流式性能大盘模态框 */}
      {showModal && (
        <ModelPerformanceModal
          isOpen={showModal}
          onClose={() => setShowModal(false)}
          initialProvider={providerId}
          initialModel={modelId}
        />
      )}
    </>
  );
}
