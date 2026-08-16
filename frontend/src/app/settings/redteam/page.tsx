"use client";

import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  History,
  Loader2,
  Play,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Zap,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  listRedTeamRuns,
  type RedTeamReport,
  type RedTeamRunHistoryItem,
  runRedTeamEvaluation,
} from "@/lib/redteam-api";

export default function RedTeamPage() {
  const [report, setReport] = useState<RedTeamReport | null>(null);
  const [history, setHistory] = useState<RedTeamRunHistoryItem[]>([]);
  const [running, setRunning] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const loadHistory = useCallback(async () => {
    try {
      setLoadingHistory(true);
      const data = await listRedTeamRuns(10);
      setHistory(data);
      if (data.length > 0 && data[0]?.detailsJson) {
        try {
          const parsed = JSON.parse(data[0].detailsJson) as RedTeamReport;
          setReport((prev) => prev ?? parsed);
        } catch {}
      }
    } catch {
      toast.error("加载红队演练历史记录失败");
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  const handleRun = async () => {
    try {
      setRunning(true);
      const data = await runRedTeamEvaluation(5);
      setReport(data);
      toast.success("红队演练完成！已生成安全审计评估报告");
      void loadHistory();
    } catch {
      toast.error("运行红队演练失败");
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 p-4 md:p-8">
      <div className="mx-auto max-w-5xl space-y-6">
        {/* 顶部导航与操作栏 */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="p-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-colors"
            >
              <ArrowLeft className="size-4" />
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <div className="size-8 rounded-xl bg-rose-500/10 text-rose-600 flex items-center justify-center">
                  <ShieldAlert className="size-4" />
                </div>
                <h1 className="text-xl font-bold">
                  AI 安全对抗演练 (Adversarial Red Team)
                </h1>
              </div>
              <p className="text-xs text-zinc-500 mt-0.5">
                基于自动化红队攻击载荷验证 SafeGuard 语义与正则级防御拦截能力
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleRun}
              disabled={running}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-rose-600 to-indigo-600 hover:from-rose-500 hover:to-indigo-500 text-white text-xs font-semibold shadow-md transition-all disabled:opacity-50 cursor-pointer"
            >
              {running ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Play className="size-3.5" />
              )}
              <span>
                {running ? "正在执行红队攻击..." : "发起红队对抗演练"}
              </span>
            </button>
          </div>
        </div>

        {/* 核心指标看板 */}
        {report ? (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs">
              <span className="text-[11px] text-zinc-400 font-medium">
                综合防御命中率
              </span>
              <div className="mt-1 flex items-baseline gap-1">
                <span className="text-2xl font-black text-rose-600 dark:text-rose-400">
                  {report.hitRatePct}%
                </span>
              </div>
              <p className="mt-1 text-[10px] text-zinc-400">
                {report.hitRatePct >= 90
                  ? "防御水平: 极强"
                  : report.hitRatePct >= 70
                    ? "防御水平: 良好"
                    : "建议增强语义安全规则"}
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs">
              <span className="text-[11px] text-zinc-400 font-medium">
                总测试载荷
              </span>
              <div className="mt-1 text-2xl font-black text-zinc-900 dark:text-white">
                {report.totalTests}
              </div>
              <p className="mt-1 text-[10px] text-zinc-400">
                含对抗攻击与良性对照样本
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs">
              <span className="text-[11px] text-zinc-400 font-medium">
                成功拦截攻击
              </span>
              <div className="mt-1 flex items-baseline gap-1">
                <span className="text-2xl font-black text-emerald-600 dark:text-emerald-400">
                  {report.blockedCount}
                </span>
                <span className="text-xs text-zinc-400">次</span>
              </div>
              <p className="mt-1 text-[10px] text-zinc-400">
                触发 SafeGuard 正则/语义阻断
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs">
              <span className="text-[11px] text-zinc-400 font-medium">
                攻击绕过风险
              </span>
              <div className="mt-1 flex items-baseline gap-1">
                <span className="text-2xl font-black text-amber-600 dark:text-amber-400">
                  {report.bypassCount}
                </span>
                <span className="text-xs text-zinc-400">次</span>
              </div>
              <p className="mt-1 text-[10px] text-zinc-400">
                未被阻断的攻击请求
              </p>
            </div>
          </div>
        ) : (
          <div className="p-12 text-center rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 space-y-3">
            <ShieldCheck className="size-12 mx-auto text-rose-500 opacity-60" />
            <h3 className="text-sm font-bold">暂无红队演练报告</h3>
            <p className="text-xs text-zinc-400 max-w-md mx-auto">
              点击右上角「发起红队对抗演练」以自动生成 DAN
              变体、越狱与提取攻击载荷，验证安全防御效果。
            </p>
          </div>
        )}

        {/* 攻击类别防御分布 */}
        {report?.categoryBreakdown && (
          <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 space-y-4 shadow-xs">
            <div className="flex items-center gap-2">
              <Zap className="size-4 text-amber-500" />
              <h3 className="text-sm font-bold">攻击向量防御覆盖率</h3>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {Object.entries(report.categoryBreakdown).map(
                ([category, stat]) => (
                  <div
                    key={category}
                    className="p-3.5 rounded-2xl bg-zinc-50 dark:bg-zinc-950/60 border border-zinc-200/50 dark:border-zinc-800/50 space-y-2"
                  >
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-mono font-bold">{category}</span>
                      <span className="font-bold text-rose-600 dark:text-rose-400">
                        {stat.blockRatePct}% 拦截
                      </span>
                    </div>
                    <div className="w-full h-2 rounded-full bg-zinc-200 dark:bg-zinc-800 overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-rose-500 to-indigo-500 transition-all duration-500 rounded-full"
                        style={{ width: `${stat.blockRatePct}%` }}
                      />
                    </div>
                    <div className="flex items-center justify-between text-[10px] text-zinc-400 font-mono">
                      <span>已拦截: {stat.blocked}</span>
                      <span>已绕过: {stat.bypassed}</span>
                      <span>总计: {stat.total}</span>
                    </div>
                  </div>
                ),
              )}
            </div>
          </div>
        )}

        {/* 详细测试用例列表 */}
        {report?.testResults && report.testResults.length > 0 && (
          <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 space-y-4 shadow-xs">
            <h3 className="text-sm font-bold">测试载荷执行明细</h3>
            <div className="space-y-2.5 max-h-96 overflow-y-auto pr-1">
              {report.testResults.map((tc) => (
                <div
                  key={tc.id}
                  className="p-3 rounded-2xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/60 dark:border-zinc-800/60 text-xs space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded-md bg-zinc-200 dark:bg-zinc-800 text-[10px] font-mono font-bold uppercase">
                        {tc.category}
                      </span>
                      {tc.passed ? (
                        <span className="flex items-center gap-1 text-[10px] text-emerald-600 font-bold">
                          <CheckCircle2 className="size-3" />
                          <span>测试通过</span>
                        </span>
                      ) : (
                        <span className="flex items-center gap-1 text-[10px] text-rose-600 font-bold">
                          <AlertTriangle className="size-3" />
                          <span>存在绕过风险</span>
                        </span>
                      )}
                    </div>

                    <span className="text-[10px] text-zinc-400 font-mono">
                      触发规则: {tc.matchedRule}
                    </span>
                  </div>

                  <div className="p-2 rounded-xl bg-white dark:bg-zinc-900 font-mono text-[11px] text-zinc-700 dark:text-zinc-300">
                    {tc.prompt}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 历史演练记录 */}
        {history.length > 0 && (
          <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 space-y-3 shadow-xs">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <History className="size-4 text-zinc-400" />
                <h3 className="text-sm font-bold">历史演练记录</h3>
              </div>
              <button
                type="button"
                onClick={() => void loadHistory()}
                className="p-1 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
              >
                <RefreshCw
                  className={`size-3.5 ${loadingHistory ? "animate-spin" : ""}`}
                />
              </button>
            </div>

            <div className="divide-y divide-zinc-100 dark:divide-zinc-800 text-xs">
              {history.map((h) => (
                <div
                  key={h.id}
                  className="py-2.5 flex items-center justify-between gap-2"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[11px] font-bold text-rose-600 dark:text-rose-400">
                      {h.hitRatePct}% 命中
                    </span>
                    <span className="text-[10px] text-zinc-400 font-mono">
                      {new Date(h.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <span className="text-[10px] text-zinc-400">
                    总用例: {h.totalTests} · 拦截: {h.blockedCount} · 绕过:{" "}
                    {h.bypassCount}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
