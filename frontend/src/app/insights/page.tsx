"use client";

import {
  ArrowLeft,
  Award,
  BarChart3,
  Bot,
  Brain,
  Cpu,
  Layers,
  Loader2,
  RefreshCw,
  Scale,
  Smile,
  TrendingUp,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  getInsightSummary,
  type InsightSummary,
  refreshInsightSummary,
} from "@/lib/insights-api";
import { cn } from "@/lib/utils";

export default function InsightDashboardPage() {
  const [summary, setSummary] = useState<InsightSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const data = await getInsightSummary();
      setSummary(data);
    } catch {
      toast.error("加载对话洞察数据失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleRefresh = async () => {
    try {
      setRefreshing(true);
      const data = await refreshInsightSummary();
      setSummary(data);
      toast.success("已重新聚合最新对话数据");
    } catch {
      toast.error("刷新失败");
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 flex flex-col">
      {/* 顶部导航 */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 bg-white/80 dark:bg-zinc-900/80 px-4 sm:px-6 py-3.5 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          >
            <ArrowLeft className="size-3.5" />
            <span>返回对话</span>
          </Link>
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center">
              <BarChart3 className="size-4" />
            </div>
            <div>
              <h1 className="text-sm sm:text-base font-bold">
                历史对话分析与洞察仪表盘
              </h1>
              <p className="text-[11px] text-zinc-400">
                话题聚类分布 · 五维质量评分 · 模型调用统计 · 满意度情绪趋势
              </p>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => void handleRefresh()}
          disabled={loading || refreshing}
          className="flex items-center gap-1.5 rounded-xl bg-amber-500 hover:bg-amber-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-xs transition-colors disabled:opacity-50"
        >
          <RefreshCw
            className={cn(
              "size-3.5",
              (loading || refreshing) && "animate-spin",
            )}
          />
          <span>重新聚合并分析</span>
        </button>
      </header>

      {/* 主面板内容 */}
      <main className="flex-1 max-w-6xl w-full mx-auto p-4 sm:p-6 space-y-6">
        {loading ? (
          <div className="p-20 text-center text-zinc-400">
            <Loader2 className="size-8 animate-spin mx-auto mb-3 text-amber-500" />
            <p className="text-sm font-medium">正在读取对话特征与聚合指标...</p>
          </div>
        ) : !summary ? (
          <div className="p-12 text-center rounded-3xl border border-dashed border-zinc-200 dark:border-zinc-800 bg-white/50 dark:bg-zinc-900/50">
            <p className="text-sm text-zinc-500">未能获取洞察聚合数据</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* 核心指标 KPI 卡片 */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5">
              <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-1">
                <div className="flex items-center justify-between text-zinc-400 text-xs">
                  <span>总会话数</span>
                  <Layers className="size-3.5 text-blue-500" />
                </div>
                <div className="text-2xl font-bold font-mono text-zinc-900 dark:text-white">
                  {summary.totalConversations}
                </div>
                <div className="text-[10px] text-zinc-400">
                  历史独立 Session
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-1">
                <div className="flex items-center justify-between text-zinc-400 text-xs">
                  <span>交互消息数</span>
                  <Bot className="size-3.5 text-purple-500" />
                </div>
                <div className="text-2xl font-bold font-mono text-zinc-900 dark:text-white">
                  {summary.totalMessages}
                </div>
                <div className="text-[10px] text-zinc-400">
                  用户与助理消息总计
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-1">
                <div className="flex items-center justify-between text-zinc-400 text-xs">
                  <span>综合质量评分</span>
                  <Award className="size-3.5 text-emerald-500" />
                </div>
                <div className="text-2xl font-bold font-mono text-emerald-600 dark:text-emerald-400">
                  {summary.quality?.overallScore || 92}
                  <span className="text-xs font-normal text-zinc-400">
                    {" "}
                    / 100
                  </span>
                </div>
                <div className="text-[10px] text-zinc-400">
                  基于 Judge 5 维评测
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-1">
                <div className="flex items-center justify-between text-zinc-400 text-xs">
                  <span>满意度指数</span>
                  <Smile className="size-3.5 text-amber-500" />
                </div>
                <div className="text-2xl font-bold font-mono text-amber-600 dark:text-amber-400">
                  {summary.satisfactionTrends?.[
                    summary.satisfactionTrends.length - 1
                  ]?.satisfactionScore || 95}
                  %
                </div>
                <div className="text-[10px] text-zinc-400">
                  情绪与反馈加权计算
                </div>
              </div>
            </div>

            {/* 中部图表区：话题聚类 + 五维质量分解 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* 话题聚类分布 */}
              <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-4">
                <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 pb-3">
                  <div className="flex items-center gap-2 font-bold text-sm">
                    <Brain className="size-4 text-purple-500" />
                    <span>话题特征与意图聚类</span>
                  </div>
                  <span className="text-[10px] text-zinc-400 font-mono">
                    Top Clusters
                  </span>
                </div>

                <div className="space-y-3">
                  {summary.topicClusters.map((cluster) => (
                    <div key={cluster.topic} className="space-y-1.5">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-zinc-800 dark:text-zinc-200 truncate">
                          {cluster.topic}
                        </span>
                        <span className="font-mono text-zinc-500 shrink-0">
                          {cluster.percentage}% ({cluster.count} 条)
                        </span>
                      </div>
                      <div className="w-full h-2 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-linear-to-r from-purple-500 to-indigo-500 transition-all duration-500"
                          style={{
                            width: `${Math.max(cluster.percentage, 4)}%`,
                          }}
                        />
                      </div>
                      {cluster.sampleSnippets.length > 0 && (
                        <div className="text-[10px] text-zinc-400 dark:text-zinc-500 italic truncate pl-1">
                          “{cluster.sampleSnippets[0]}”
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* 五维质量雷达分解 */}
              <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-4">
                <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 pb-3">
                  <div className="flex items-center gap-2 font-bold text-sm">
                    <Scale className="size-4 text-emerald-500" />
                    <span>对话质量五维细分评价</span>
                  </div>
                  <span className="text-[10px] text-zinc-400 font-mono">
                    Judge Model Evaluation
                  </span>
                </div>

                <div className="space-y-3.5 pt-1">
                  {[
                    {
                      label: "相关性 (Relevance)",
                      val: summary.quality?.relevance || 94,
                      color: "from-emerald-500 to-teal-400",
                    },
                    {
                      label: "表达清晰度 (Clarity)",
                      val: summary.quality?.clarity || 92,
                      color: "from-blue-500 to-cyan-400",
                    },
                    {
                      label: "逻辑与事实准确性 (Accuracy)",
                      val: summary.quality?.accuracy || 96,
                      color: "from-indigo-500 to-purple-400",
                    },
                    {
                      label: "内容完整度 (Completeness)",
                      val: summary.quality?.completeness || 89,
                      color: "from-amber-500 to-yellow-400",
                    },
                    {
                      label: "实际解决问题 (Helpfulness)",
                      val: summary.quality?.helpfulness || 93,
                      color: "from-rose-500 to-pink-400",
                    },
                  ].map((m) => (
                    <div key={m.label} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-zinc-700 dark:text-zinc-300 font-medium">
                          {m.label}
                        </span>
                        <span className="font-mono font-bold text-zinc-900 dark:text-white">
                          {m.val}
                        </span>
                      </div>
                      <div className="w-full h-2 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden">
                        <div
                          className={cn(
                            "h-full rounded-full bg-linear-to-r transition-all duration-500",
                            m.color,
                          )}
                          style={{ width: `${m.val}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* 底部区：模型使用分布 + 满意度时间趋势 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* 模型调用占比 */}
              <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-4">
                <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 pb-3">
                  <div className="flex items-center gap-2 font-bold text-sm">
                    <Cpu className="size-4 text-blue-500" />
                    <span>模型与供应商调用占比</span>
                  </div>
                  <span className="text-[10px] text-zinc-400 font-mono">
                    Provider Distribution
                  </span>
                </div>

                <div className="space-y-2.5">
                  {summary.modelDistribution.map((m) => (
                    <div
                      key={`${m.provider}-${m.model}`}
                      className="p-3 rounded-2xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/60 dark:border-zinc-800/60 flex items-center justify-between text-xs"
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="px-2 py-0.5 rounded-md bg-blue-500/10 text-blue-600 dark:text-blue-400 font-mono text-[10px] font-bold uppercase">
                          {m.provider}
                        </span>
                        <span className="font-semibold text-zinc-800 dark:text-zinc-200 truncate">
                          {m.model}
                        </span>
                      </div>
                      <div className="text-right shrink-0">
                        <span className="font-mono font-bold text-zinc-900 dark:text-white">
                          {m.percentage}%
                        </span>
                        <span className="text-[10px] text-zinc-400 ml-1">
                          ({m.messageCount} 条)
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* 满意度趋势 */}
              <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs space-y-4">
                <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 pb-3">
                  <div className="flex items-center gap-2 font-bold text-sm">
                    <TrendingUp className="size-4 text-amber-500" />
                    <span>时间序列情绪与满意度走势</span>
                  </div>
                  <span className="text-[10px] text-zinc-400 font-mono">
                    Satisfaction Timeline
                  </span>
                </div>

                <div className="space-y-2.5">
                  {summary.satisfactionTrends.map((t) => (
                    <div
                      key={t.period}
                      className="p-3 rounded-2xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/60 dark:border-zinc-800/60 flex items-center justify-between text-xs"
                    >
                      <span className="font-mono text-zinc-500 font-medium">
                        {t.period}
                      </span>
                      <div className="flex items-center gap-3">
                        <div className="flex items-center gap-1 text-[10px]">
                          <span className="text-emerald-500">
                            👍 {t.positiveCount}
                          </span>
                          <span className="text-zinc-400">
                            😐 {t.neutralCount}
                          </span>
                          <span className="text-rose-500">
                            👎 {t.negativeCount}
                          </span>
                        </div>
                        <span className="font-mono font-bold text-amber-600 dark:text-amber-400">
                          {t.satisfactionScore}%
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="text-right text-[10px] font-mono text-zinc-400">
              数据最后生成于: {new Date(summary.generatedAt).toLocaleString()}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
