"use client";

import { Award, Cpu, TrendingUp, UserCheck, Zap } from "lucide-react";
import { useState } from "react";
import type {
  UsageDailySummary,
  UsageModelDetailSummary,
  UsageUserSummary,
} from "@/lib/api";

interface UsageChartProps {
  dailyTrend: UsageDailySummary[];
  byUser: UsageUserSummary[];
  byModel: UsageModelDetailSummary[];
}

function formatTokens(tokens: number): string {
  if (tokens >= 1_000_000) {
    return `${(tokens / 1_000_000).toFixed(2)} M`;
  }
  if (tokens >= 1_000) {
    return `${(tokens / 1_000).toFixed(1)} K`;
  }
  return tokens.toLocaleString();
}

function formatCost(cost: number): string {
  return `¥${Number(cost || 0).toFixed(4)}`;
}

export function UsageChart({ dailyTrend, byUser, byModel }: UsageChartProps) {
  const [metricMode, setMetricMode] = useState<"tokens" | "cost">("tokens");
  const [hoveredDayIndex, setHoveredDayIndex] = useState<number | null>(null);

  const maxVal = Math.max(
    1,
    ...dailyTrend.map((d) =>
      metricMode === "tokens" ? d.totalTokens : d.totalCost,
    ),
  );

  const totalPromptTokens = byUser.reduce((acc, u) => acc + u.promptTokens, 0);
  const totalCompletionTokens = byUser.reduce(
    (acc, u) => acc + u.completionTokens,
    0,
  );
  const totalTokensSum = totalPromptTokens + totalCompletionTokens || 1;

  const promptPercent = ((totalPromptTokens / totalTokensSum) * 100).toFixed(1);
  const completionPercent = (
    (totalCompletionTokens / totalTokensSum) *
    100
  ).toFixed(1);

  return (
    <div className="space-y-6">
      {/* 1. 日度用量与成本趋势图 (SVG Interactive Chart) */}
      <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-5 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
              <TrendingUp className="size-4" />
            </div>
            <div>
              <h2 className="font-heading text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                用量与成本日趋势
              </h2>
              <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                实时统计每日消耗 Token 与 RMB 计费趋势
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 rounded-xl border border-zinc-200/80 bg-zinc-100/70 p-1 dark:border-zinc-800/80 dark:bg-zinc-950/60">
            <button
              type="button"
              onClick={() => setMetricMode("tokens")}
              className={`rounded-lg px-3 py-1 text-xs font-medium transition-all ${
                metricMode === "tokens"
                  ? "bg-white text-indigo-600 shadow-xs dark:bg-zinc-800 dark:text-indigo-400"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              }`}
            >
              Token 数量
            </button>
            <button
              type="button"
              onClick={() => setMetricMode("cost")}
              className={`rounded-lg px-3 py-1 text-xs font-medium transition-all ${
                metricMode === "cost"
                  ? "bg-white text-emerald-600 shadow-xs dark:bg-zinc-800 dark:text-emerald-400"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              }`}
            >
              RMB 费用
            </button>
          </div>
        </div>

        {dailyTrend.length === 0 ? (
          <div className="flex h-48 items-center justify-center text-xs text-zinc-400">
            当月暂无日用量记录
          </div>
        ) : (
          <div className="mt-6">
            {/* SVG 柱状/趋势图 */}
            <div className="relative flex h-52 items-end gap-1.5 pt-6 pb-2 px-2">
              {dailyTrend.map((item, idx) => {
                const val =
                  metricMode === "tokens" ? item.totalTokens : item.totalCost;
                const heightPercent = Math.max(
                  8,
                  Math.min(100, (val / maxVal) * 100),
                );
                const isHovered = hoveredDayIndex === idx;

                return (
                  <section
                    key={item.day}
                    aria-label={`Daily usage for ${item.day}`}
                    onMouseEnter={() => setHoveredDayIndex(idx)}
                    onMouseLeave={() => setHoveredDayIndex(null)}
                    className="group relative flex flex-1 flex-col items-center h-full justify-end"
                  >
                    {/* Hover Tooltip */}
                    {isHovered && (
                      <div className="absolute bottom-full mb-2 z-20 flex flex-col items-center rounded-xl border border-zinc-800 bg-zinc-900/95 px-3 py-2 text-[11px] text-white shadow-xl backdrop-blur-md">
                        <span className="font-semibold text-zinc-300">
                          {item.day}
                        </span>
                        <div className="mt-1 flex items-center gap-2">
                          <span className="text-indigo-400">Token:</span>
                          <span className="font-mono font-medium">
                            {formatTokens(item.totalTokens)}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-emerald-400">费用:</span>
                          <span className="font-mono font-medium">
                            {formatCost(item.totalCost)}
                          </span>
                        </div>
                        <div className="text-[10px] text-zinc-400">
                          {item.requestCount} 次请求
                        </div>
                      </div>
                    )}

                    {/* Bar */}
                    <div
                      style={{ height: `${heightPercent}%` }}
                      className={`w-full rounded-t-md transition-all duration-200 ${
                        metricMode === "tokens"
                          ? "bg-gradient-to-t from-indigo-600 via-indigo-500 to-purple-500 hover:from-indigo-500 hover:to-pink-500"
                          : "bg-gradient-to-t from-emerald-600 via-emerald-500 to-teal-400 hover:from-emerald-500 hover:to-cyan-400"
                      } ${isHovered ? "scale-105 shadow-md" : "opacity-90"}`}
                    />
                  </section>
                );
              })}
            </div>

            {/* X-axis Days label */}
            <div className="mt-2 flex justify-between px-2 text-[10px] text-zinc-400">
              <span>{dailyTrend[0]?.day}</span>
              {dailyTrend.length > 2 && (
                <span>
                  {dailyTrend[Math.floor(dailyTrend.length / 2)]?.day}
                </span>
              )}
              <span>{dailyTrend[dailyTrend.length - 1]?.day}</span>
            </div>
          </div>
        )}
      </div>

      {/* 2. 中间区域：用户排行榜 + Prompt vs Completion 占比 */}
      <div className="grid gap-6 lg:grid-cols-5">
        {/* 用户 Token 与费用排行榜 */}
        <div className="lg:col-span-3 rounded-2xl border border-zinc-200/80 bg-white/70 p-5 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
          <div className="flex items-center justify-between pb-4 border-b border-zinc-100 dark:border-zinc-800/60">
            <div className="flex items-center gap-2">
              <div className="flex size-7 items-center justify-center rounded-xl bg-purple-500/10 text-purple-600 dark:bg-purple-500/20 dark:text-purple-400">
                <UserCheck className="size-4" />
              </div>
              <h3 className="font-heading text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                用户消耗排行榜
              </h3>
            </div>
            <span className="text-[11px] text-zinc-400">
              共 {byUser.length} 位活跃用户
            </span>
          </div>

          {byUser.length === 0 ? (
            <div className="flex h-40 items-center justify-center text-xs text-zinc-400">
              当月暂无用户使用记录
            </div>
          ) : (
            <div className="mt-4 divide-y divide-zinc-100 dark:divide-zinc-800/40">
              {byUser.slice(0, 10).map((u, idx) => {
                const maxUserTokens = byUser[0]?.totalTokens || 1;
                const percent = Math.min(
                  100,
                  Math.max(5, (u.totalTokens / maxUserTokens) * 100),
                );

                return (
                  <div key={u.userId} className="py-3 flex flex-col gap-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <span
                          className={`flex size-5 items-center justify-center rounded-full text-[10px] font-bold ${
                            idx === 0
                              ? "bg-amber-500 text-white"
                              : idx === 1
                                ? "bg-zinc-300 text-zinc-800 dark:bg-zinc-700 dark:text-zinc-200"
                                : idx === 2
                                  ? "bg-amber-700/60 text-white"
                                  : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400"
                          }`}
                        >
                          {idx + 1}
                        </span>
                        <span className="font-mono font-medium text-zinc-800 dark:text-zinc-200">
                          {u.userId}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 text-right">
                        <span className="font-mono font-semibold text-indigo-600 dark:text-indigo-400">
                          {formatTokens(u.totalTokens)}
                        </span>
                        <span className="font-mono text-zinc-500 dark:text-zinc-400">
                          {formatCost(u.totalCost)}
                        </span>
                      </div>
                    </div>

                    {/* User usage progress bar */}
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                      <div
                        style={{ width: `${percent}%` }}
                        className="h-full rounded-full bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 transition-all duration-300"
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Prompt vs Completion Token 占比拆分 */}
        <div className="lg:col-span-2 rounded-2xl border border-zinc-200/80 bg-white/70 p-5 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 pb-4 border-b border-zinc-100 dark:border-zinc-800/60">
              <div className="flex size-7 items-center justify-center rounded-xl bg-pink-500/10 text-pink-600 dark:bg-pink-500/20 dark:text-pink-400">
                <Zap className="size-4" />
              </div>
              <h3 className="font-heading text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                Token 构成分解
              </h3>
            </div>

            <div className="mt-6 space-y-4">
              <div>
                <div className="flex justify-between text-xs mb-1.5 font-medium">
                  <span className="text-indigo-600 dark:text-indigo-400">
                    Prompt Tokens (输入)
                  </span>
                  <span className="font-mono text-zinc-700 dark:text-zinc-300">
                    {formatTokens(totalPromptTokens)} ({promptPercent}%)
                  </span>
                </div>
                <div className="h-2.5 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                  <div
                    style={{ width: `${promptPercent}%` }}
                    className="h-full bg-gradient-to-r from-indigo-600 to-indigo-400 rounded-full"
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-xs mb-1.5 font-medium">
                  <span className="text-purple-600 dark:text-purple-400">
                    Completion Tokens (生成)
                  </span>
                  <span className="font-mono text-zinc-700 dark:text-zinc-300">
                    {formatTokens(totalCompletionTokens)} ({completionPercent}%)
                  </span>
                </div>
                <div className="h-2.5 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                  <div
                    style={{ width: `${completionPercent}%` }}
                    className="h-full bg-gradient-to-r from-purple-600 to-pink-500 rounded-full"
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 rounded-xl border border-indigo-500/10 bg-indigo-50/40 p-3.5 dark:border-indigo-500/20 dark:bg-indigo-950/30">
            <div className="flex items-center gap-2 text-xs font-semibold text-indigo-700 dark:text-indigo-300">
              <Award className="size-4" />
              <span>智能优化提示</span>
            </div>
            <p className="mt-1 text-[11px] leading-relaxed text-zinc-600 dark:text-zinc-400">
              Prompt 输入占比较高时，推荐结合 RAG
              切块与长期记忆摘要优化，减少冗余上下文消耗。
            </p>
          </div>
        </div>
      </div>

      {/* 3. 模型维度消耗拆分 (Model Breakdown Cards) */}
      <div className="rounded-2xl border border-zinc-200/80 bg-white/70 p-5 shadow-xs backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/60">
        <div className="flex items-center justify-between pb-4 border-b border-zinc-100 dark:border-zinc-800/60">
          <div className="flex items-center gap-2">
            <div className="flex size-7 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-600 dark:bg-cyan-500/20 dark:text-cyan-400">
              <Cpu className="size-4" />
            </div>
            <h3 className="font-heading text-sm font-semibold text-zinc-900 dark:text-zinc-100">
              模型与供应商成本分布
            </h3>
          </div>
          <span className="text-[11px] text-zinc-400">
            涵盖 {byModel.length} 个 AI 模型
          </span>
        </div>

        {byModel.length === 0 ? (
          <div className="flex h-36 items-center justify-center text-xs text-zinc-400">
            当月暂无模型调用数据
          </div>
        ) : (
          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {byModel.map((m) => (
              <div
                key={`${m.providerId}-${m.modelId}`}
                className="group relative overflow-hidden rounded-xl border border-zinc-200/60 bg-zinc-50/50 p-4 transition-all duration-200 hover:border-indigo-500/40 hover:bg-white hover:shadow-md dark:border-zinc-800/60 dark:bg-zinc-950/40 dark:hover:border-indigo-500/40 dark:hover:bg-zinc-900"
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono text-xs font-bold text-zinc-900 dark:text-zinc-100 truncate">
                    {m.modelId}
                  </span>
                  <span className="rounded-md bg-zinc-200/60 px-2 py-0.5 text-[10px] font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
                    {m.providerId}
                  </span>
                </div>

                <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[10px] text-zinc-400 block">
                      Token 消耗
                    </span>
                    <span className="font-mono font-semibold text-indigo-600 dark:text-indigo-400">
                      {formatTokens(m.totalTokens)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-zinc-400 block">
                      产生费用
                    </span>
                    <span className="font-mono font-semibold text-emerald-600 dark:text-emerald-400">
                      {formatCost(m.totalCost)}
                    </span>
                  </div>
                </div>

                <div className="mt-2.5 flex items-center justify-between text-[11px] text-zinc-400 border-t border-zinc-200/40 pt-2 dark:border-zinc-800/40">
                  <span>调用次数: {m.requestCount} 次</span>
                  <span>
                    In/Out: {formatTokens(m.promptTokens)} /{" "}
                    {formatTokens(m.completionTokens)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
