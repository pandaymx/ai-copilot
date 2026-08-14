"use client";

import {
  ArrowLeft,
  Award,
  BarChart3,
  Edit3,
  Layers,
  Loader2,
  Play,
  Plus,
  RefreshCw,
  ShieldCheck,
  Swords,
  Trash2,
  Trophy,
  Zap,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import {
  type AbTestResultDto,
  addBenchmarkApi,
  annotateEvaluationResultApi,
  type BenchmarkCase,
  deleteBenchmarkApi,
  type EvaluationSummaryDto,
  fetchBenchmarksApi,
  fetchEvaluationSummaryApi,
  runAbTestApi,
  runBatchEvaluationApi,
} from "@/lib/api";

export default function EvaluationPage() {
  const [activeTab, setActiveTab] = useState<
    "dashboard" | "ab-arena" | "benchmarks" | "history"
  >("dashboard");

  // 大盘数据
  const [summary, setSummary] = useState<EvaluationSummaryDto | null>(null);
  const [loadingSummary, setLoadingSummary] = useState(false);

  // 基准测试集
  const [benchmarks, setBenchmarks] = useState<BenchmarkCase[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [_loadingBenchmarks, setLoadingBenchmarks] = useState(false);

  // A/B 盲测状态
  const [abPrompt, setAbPrompt] = useState("");
  const [abContext, setAbContext] = useState("");
  const [modelA, setModelA] = useState<{ provider: string; model: string }>({
    provider: "deepseek",
    model: "deepseek-chat",
  });
  const [modelB, setModelB] = useState<{ provider: string; model: string }>({
    provider: "openai",
    model: "gpt-4o",
  });
  const [judgeModel, setJudgeModel] = useState<{
    provider: string;
    model: string;
  }>({
    provider: "deepseek",
    model: "deepseek-chat",
  });
  const [abLoading, setAbLoading] = useState(false);
  const [abResult, setAbResult] = useState<AbTestResultDto | null>(null);

  // 批量评测
  const [batchTarget, _setBatchTarget] = useState<{
    provider: string;
    model: string;
  }>({
    provider: "deepseek",
    model: "deepseek-chat",
  });
  const [runningBatch, setRunningBatch] = useState(false);

  // 新建基准用例模态框
  const [showAddModal, setShowAddModal] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newCategory, setNewCategory] = useState("RAG 检索问答");
  const [newPrompt, setNewPrompt] = useState("");
  const [newExpected, setNewExpected] = useState("");
  const [newContext, setNewContext] = useState("");

  // 人工标注
  const [editingResultId, setEditingResultId] = useState<string | null>(null);
  const [humanScoreInput, setHumanScoreInput] = useState<string>("0.95");
  const [humanAnnotationInput, setHumanAnnotationInput] = useState<string>("");
  const [savingAnnotation, setSavingAnnotation] = useState(false);

  const loadSummary = useCallback(async () => {
    setLoadingSummary(true);
    try {
      const data = await fetchEvaluationSummaryApi();
      if (data) setSummary(data);
    } catch {
      toast.error("加载大盘评测数据失败");
    } finally {
      setLoadingSummary(false);
    }
  }, []);

  const loadBenchmarks = useCallback(async () => {
    setLoadingBenchmarks(true);
    try {
      const cat = selectedCategory === "ALL" ? undefined : selectedCategory;
      const list = await fetchBenchmarksApi(cat);
      setBenchmarks(list);
    } catch {
      toast.error("加载基准测试集失败");
    } finally {
      setLoadingBenchmarks(false);
    }
  }, [selectedCategory]);

  useEffect(() => {
    void loadSummary();
    void loadBenchmarks();
  }, [loadSummary, loadBenchmarks]);

  const handleRunAbTest = async () => {
    if (!abPrompt.trim()) {
      toast.error("请输入待评测的 Prompt 问题");
      return;
    }
    setAbLoading(true);
    setAbResult(null);
    try {
      const res = await runAbTestApi(
        abPrompt,
        modelA.provider,
        modelA.model,
        modelB.provider,
        modelB.model,
        abContext || undefined,
        undefined,
        judgeModel.provider,
        judgeModel.model,
      );
      if (res) {
        setAbResult(res);
        toast.success("A/B 盲测裁判打分完成！");
        void loadSummary();
      } else {
        toast.error("A/B 评测执行失败");
      }
    } catch {
      toast.error("请求失败，请稍后重试");
    } finally {
      setAbLoading(false);
    }
  };

  const handleRunBatch = async () => {
    setRunningBatch(true);
    try {
      const results = await runBatchEvaluationApi(
        batchTarget.provider,
        batchTarget.model,
        judgeModel.provider,
        judgeModel.model,
      );
      if (results.length > 0) {
        toast.success(`成功完成 ${results.length} 项基准用例自动化评测！`);
        void loadSummary();
        setActiveTab("history");
      } else {
        toast.error("未找到可评测的基准用例");
      }
    } catch {
      toast.error("批量评测执行失败");
    } finally {
      setRunningBatch(false);
    }
  };

  const handleAddBenchmark = async () => {
    if (!newTitle.trim() || !newPrompt.trim()) {
      toast.error("标题与 Prompt 为必填项");
      return;
    }
    const created = await addBenchmarkApi({
      title: newTitle.trim(),
      category: newCategory,
      prompt: newPrompt.trim(),
      expectedOutput: newExpected.trim(),
      context: newContext.trim() || undefined,
      tags: [newCategory],
    });
    if (created) {
      toast.success("基准测试用例已添加");
      setShowAddModal(false);
      setNewTitle("");
      setNewPrompt("");
      setNewExpected("");
      setNewContext("");
      void loadBenchmarks();
    } else {
      toast.error("添加用例失败");
    }
  };

  const handleDeleteBenchmark = async (id: string) => {
    const ok = await deleteBenchmarkApi(id);
    if (ok) {
      toast.success("用例已删除");
      void loadBenchmarks();
    } else {
      toast.error("删除用例失败");
    }
  };

  const handleSaveAnnotation = async (id: string) => {
    const scoreNum = Number.parseFloat(humanScoreInput);
    if (Number.isNaN(scoreNum) || scoreNum < 0 || scoreNum > 1) {
      toast.error("人工分值须在 0.0 ~ 1.0 之间");
      return;
    }
    setSavingAnnotation(true);
    const updated = await annotateEvaluationResultApi(
      id,
      scoreNum,
      humanAnnotationInput.trim(),
    );
    setSavingAnnotation(false);
    if (updated) {
      toast.success("人工标注已保存");
      setEditingResultId(null);
      void loadSummary();
    } else {
      toast.error("保存标注失败");
    }
  };

  return (
    <div className="flex flex-col min-h-screen bg-zinc-50/50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 font-sans">
      {/* 顶部导航 Header */}
      <header className="sticky top-0 z-40 flex items-center justify-between border-b border-zinc-200/80 dark:border-zinc-800/80 bg-white/80 dark:bg-zinc-900/80 px-6 py-3.5 backdrop-blur-xl">
        <div className="flex items-center gap-4">
          <Link
            href="/"
            className="flex items-center gap-1.5 text-xs font-semibold text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors"
          >
            <ArrowLeft className="size-4" />
            <span>返回对话</span>
          </Link>
          <div className="h-4 w-px bg-zinc-200 dark:bg-zinc-800" />
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-gradient-to-tr from-amber-500/10 via-indigo-500/10 to-violet-500/10 border border-amber-500/20 text-amber-600 dark:text-amber-400">
              <Award className="size-5" />
            </div>
            <div>
              <h1 className="text-base font-bold tracking-tight text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                AI 评测与评估体系 (Evaluation Arena)
                <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-100/70 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200/60 dark:border-amber-800/50">
                  LLM-as-Judge & A/B 盲测
                </span>
              </h1>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => {
              void loadSummary();
              void loadBenchmarks();
              toast.success("评测数据已刷新");
            }}
            title="刷新数据"
          >
            <RefreshCw
              className={`size-4 ${loadingSummary ? "animate-spin" : ""}`}
            />
          </Button>
          <ThemeToggle />
        </div>
      </header>

      {/* 选项卡 Tab Header */}
      <div className="border-b border-zinc-200/80 dark:border-zinc-800/80 bg-white/40 dark:bg-zinc-900/40 px-6 backdrop-blur-md">
        <div className="flex gap-2 -mb-px">
          {[
            { id: "dashboard", label: "大盘概览", icon: BarChart3 },
            { id: "ab-arena", label: "A/B 盲测竞技场", icon: Swords },
            { id: "benchmarks", label: "基准测试集", icon: Layers },
            { id: "history", label: "评测记录与标注", icon: Trophy },
          ].map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as typeof activeTab)}
                className={`flex items-center gap-2 px-4 py-3 text-xs font-semibold border-b-2 transition-all duration-200 ${
                  active
                    ? "border-amber-500 text-amber-600 dark:text-amber-400 bg-amber-50/40 dark:bg-amber-950/20"
                    : "border-transparent text-zinc-500 dark:text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-200 hover:border-zinc-300"
                }`}
              >
                <Icon className="size-4" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* 主体内容 */}
      <main className="flex-1 p-6 max-w-7xl w-full mx-auto space-y-6">
        {/* ====================== TAB 1: 大盘概览 ====================== */}
        {activeTab === "dashboard" && (
          <div className="space-y-6">
            {/* 4 个核心 KPI 卡片 */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="p-4 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-zinc-500">
                    自动化评测总轮次
                  </span>
                  <div className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-500">
                    <Zap className="size-4" />
                  </div>
                </div>
                <div className="mt-2 text-2xl font-bold tracking-tight">
                  {summary?.totalEvaluations ?? 0}
                </div>
                <p className="mt-1 text-[11px] text-zinc-400">
                  涵盖 RAG/代码/逻辑等多维度用例
                </p>
              </div>

              <div className="p-4 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-zinc-500">
                    A/B 盲测对比次数
                  </span>
                  <div className="p-1.5 rounded-lg bg-amber-500/10 text-amber-500">
                    <Swords className="size-4" />
                  </div>
                </div>
                <div className="mt-2 text-2xl font-bold tracking-tight">
                  {summary?.totalAbTests ?? 0}
                </div>
                <p className="mt-1 text-[11px] text-zinc-400">
                  双模型并发无偏见盲测对决
                </p>
              </div>

              <div className="p-4 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-zinc-500">
                    综合加权均分
                  </span>
                  <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-500">
                    <Trophy className="size-4" />
                  </div>
                </div>
                <div className="mt-2 text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400">
                  {summary?.averageScore
                    ? `${(summary.averageScore * 100).toFixed(1)}分`
                    : "--"}
                </div>
                <p className="mt-1 text-[11px] text-zinc-400">
                  5 维度综合加权总体得分
                </p>
              </div>

              <div className="p-4 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-zinc-500">
                    黄金基准用例数
                  </span>
                  <div className="p-1.5 rounded-lg bg-violet-500/10 text-violet-500">
                    <Layers className="size-4" />
                  </div>
                </div>
                <div className="mt-2 text-2xl font-bold tracking-tight">
                  {benchmarks.length}
                </div>
                <p className="mt-1 text-[11px] text-zinc-400">
                  预置及自定义测试用例集合
                </p>
              </div>
            </div>

            {/* 5 维度核心能力评分条 */}
            <div className="p-6 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm space-y-4">
              <h2 className="text-sm font-bold tracking-tight text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                <ShieldCheck className="size-4 text-emerald-500" />5
                维度能力画像均分 (Five Core Dimensions)
              </h2>

              <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                {[
                  {
                    name: "相关性 (Relevance)",
                    score: summary?.dimensionAverages?.relevance ?? 0.9,
                    color: "bg-blue-500",
                  },
                  {
                    name: "准确性 (Accuracy)",
                    score: summary?.dimensionAverages?.accuracy ?? 0.92,
                    color: "bg-emerald-500",
                  },
                  {
                    name: "完整性 (Completeness)",
                    score: summary?.dimensionAverages?.completeness ?? 0.88,
                    color: "bg-indigo-500",
                  },
                  {
                    name: "流畅度 (Fluency)",
                    score: summary?.dimensionAverages?.fluency ?? 0.94,
                    color: "bg-purple-500",
                  },
                  {
                    name: "安全性 (Safety)",
                    score: summary?.dimensionAverages?.safety ?? 1.0,
                    color: "bg-amber-500",
                  },
                ].map((dim) => (
                  <div
                    key={dim.name}
                    className="p-3.5 rounded-xl border border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-950/40 space-y-2"
                  >
                    <div className="flex justify-between text-xs font-semibold">
                      <span className="text-zinc-600 dark:text-zinc-300">
                        {dim.name.split(" ")[0]}
                      </span>
                      <span className="text-zinc-900 dark:text-zinc-100">
                        {(dim.score * 100).toFixed(0)}%
                      </span>
                    </div>
                    <div className="h-2 w-full rounded-full bg-zinc-200 dark:bg-zinc-800 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${dim.color}`}
                        style={{ width: `${Math.min(100, dim.score * 100)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 模型排行榜 (Model Leaderboard) */}
            <div className="p-6 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-bold tracking-tight text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                  <Trophy className="size-4 text-amber-500" />
                  模型能力排行榜 (Model Leaderboard)
                </h2>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleRunBatch}
                  disabled={runningBatch}
                  className="gap-1.5 text-xs border-indigo-500/30 text-indigo-600 dark:text-indigo-400"
                >
                  {runningBatch ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <Play className="size-3.5" />
                  )}
                  {runningBatch ? "正在全量评测中..." : "一键评测 DeepSeek"}
                </Button>
              </div>

              {summary?.leaderboard && summary.leaderboard.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="border-b border-zinc-200 dark:border-zinc-800 text-zinc-500 font-medium">
                        <th className="pb-3 pl-2">排名</th>
                        <th className="pb-3">模型名称</th>
                        <th className="pb-3">供应商</th>
                        <th className="pb-3">评测样本数</th>
                        <th className="pb-3">平均综合得分</th>
                        <th className="pb-3">平均延迟</th>
                        <th className="pb-3">准确度 / 安全性</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/60">
                      {summary.leaderboard.map((item, idx) => (
                        <tr
                          key={item.modelKey}
                          className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30"
                        >
                          <td className="py-3.5 pl-2 font-bold">
                            {idx === 0 ? (
                              <span className="text-amber-500 flex items-center gap-1">
                                🥇 1
                              </span>
                            ) : idx === 1 ? (
                              <span className="text-zinc-400 flex items-center gap-1">
                                🥈 2
                              </span>
                            ) : idx === 2 ? (
                              <span className="text-amber-700 flex items-center gap-1">
                                🥉 3
                              </span>
                            ) : (
                              `#${idx + 1}`
                            )}
                          </td>
                          <td className="py-3.5 font-semibold text-zinc-900 dark:text-zinc-100">
                            {item.model}
                          </td>
                          <td className="py-3.5 text-zinc-500">
                            <span className="px-2 py-0.5 rounded-md text-[11px] bg-zinc-100 dark:bg-zinc-800">
                              {item.provider}
                            </span>
                          </td>
                          <td className="py-3.5">{item.count} 次</td>
                          <td className="py-3.5 font-bold text-emerald-600 dark:text-emerald-400">
                            {(item.averageScore * 100).toFixed(1)}分
                          </td>
                          <td className="py-3.5 text-zinc-500">
                            {item.averageLatencyMs} ms
                          </td>
                          <td className="py-3.5">
                            <span className="text-emerald-500 font-medium">
                              {(item.metrics.accuracy * 100).toFixed(0)}%
                            </span>{" "}
                            /{" "}
                            <span className="text-amber-500 font-medium">
                              {(item.metrics.safety * 100).toFixed(0)}%
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="py-8 text-center text-xs text-zinc-500">
                  暂无排行榜数据，请点击上方「一键评测」或在基准测试集发测评测。
                </div>
              )}
            </div>
          </div>
        )}

        {/* ====================== TAB 2: A/B 盲测竞技场 ====================== */}
        {activeTab === "ab-arena" && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold tracking-tight text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                    <Swords className="size-5 text-amber-500" />
                    A/B 盲测对比竞技场 (A/B Arena)
                  </h2>
                  <p className="text-xs text-zinc-500 mt-0.5">
                    双模型并发调用，并在匿名去偏见模式下由 LLM 裁判直接裁定胜负
                    🏆
                  </p>
                </div>
              </div>

              {/* 模型选择器 */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
                <div className="p-3.5 rounded-xl bg-indigo-50/40 dark:bg-indigo-950/20 border border-indigo-200/50 dark:border-indigo-800/40 space-y-2">
                  <span className="text-xs font-bold text-indigo-600 dark:text-indigo-400">
                    Model A (对决方 A)
                  </span>
                  <select
                    className="w-full text-xs rounded-lg border border-indigo-200 bg-white px-2.5 py-1.5 dark:border-indigo-800 dark:bg-zinc-900"
                    value={`${modelA.provider}::${modelA.model}`}
                    onChange={(e) => {
                      const [p, m] = e.target.value.split("::");
                      setModelA({ provider: p, model: m });
                    }}
                  >
                    <option value="deepseek::deepseek-chat">
                      DeepSeek Chat
                    </option>
                    <option value="openai::gpt-4o">OpenAI GPT-4o</option>
                    <option value="openai::gpt-4o-mini">
                      OpenAI GPT-4o-mini
                    </option>
                    <option value="google::gemini-1.5-pro">
                      Google Gemini 1.5 Pro
                    </option>
                    <option value="ollama::qwen2.5">Ollama Qwen 2.5</option>
                  </select>
                </div>

                <div className="p-3.5 rounded-xl bg-purple-50/40 dark:bg-purple-950/20 border border-purple-200/50 dark:border-purple-800/40 space-y-2">
                  <span className="text-xs font-bold text-purple-600 dark:text-purple-400">
                    Model B (对决方 B)
                  </span>
                  <select
                    className="w-full text-xs rounded-lg border border-purple-200 bg-white px-2.5 py-1.5 dark:border-purple-800 dark:bg-zinc-900"
                    value={`${modelB.provider}::${modelB.model}`}
                    onChange={(e) => {
                      const [p, m] = e.target.value.split("::");
                      setModelB({ provider: p, model: m });
                    }}
                  >
                    <option value="openai::gpt-4o">OpenAI GPT-4o</option>
                    <option value="deepseek::deepseek-chat">
                      DeepSeek Chat
                    </option>
                    <option value="openai::gpt-4o-mini">
                      OpenAI GPT-4o-mini
                    </option>
                    <option value="google::gemini-1.5-pro">
                      Google Gemini 1.5 Pro
                    </option>
                    <option value="ollama::qwen2.5">Ollama Qwen 2.5</option>
                  </select>
                </div>

                <div className="p-3.5 rounded-xl bg-amber-50/40 dark:bg-amber-950/20 border border-amber-200/50 dark:border-amber-800/40 space-y-2">
                  <span className="text-xs font-bold text-amber-600 dark:text-amber-400">
                    裁判模型 (LLM-as-Judge)
                  </span>
                  <select
                    className="w-full text-xs rounded-lg border border-amber-200 bg-white px-2.5 py-1.5 dark:border-amber-800 dark:bg-zinc-900"
                    value={`${judgeModel.provider}::${judgeModel.model}`}
                    onChange={(e) => {
                      const [p, m] = e.target.value.split("::");
                      setJudgeModel({ provider: p, model: m });
                    }}
                  >
                    <option value="deepseek::deepseek-chat">
                      DeepSeek Chat (裁判)
                    </option>
                    <option value="openai::gpt-4o">GPT-4o (裁判)</option>
                  </select>
                </div>
              </div>

              {/* 输入区域 */}
              <div className="space-y-3 pt-2">
                <div>
                  <label
                    htmlFor="ab-prompt-input"
                    className="text-xs font-semibold text-zinc-700 dark:text-zinc-300"
                  >
                    评测提示词 / 用户问题 (Prompt)
                  </label>
                  <textarea
                    id="ab-prompt-input"
                    value={abPrompt}
                    onChange={(e) => setAbPrompt(e.target.value)}
                    placeholder="例如：请编写一个使用 Java 25 虚拟线程与 StructuredTaskScope 处理并发超时的示例..."
                    className="mt-1 w-full h-24 text-xs rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-3 focus:outline-hidden focus:ring-2 focus:ring-amber-500/20"
                  />
                </div>

                {/* 快速选择基准用例 */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[11px] text-zinc-400">
                    填入预置用例:
                  </span>
                  {benchmarks.slice(0, 4).map((b) => (
                    <button
                      key={b.id}
                      type="button"
                      onClick={() => {
                        setAbPrompt(b.prompt);
                        setAbContext(b.context || "");
                      }}
                      className="px-2 py-0.5 rounded-md text-[11px] bg-zinc-100 hover:bg-zinc-200 dark:bg-zinc-800 dark:hover:bg-zinc-700 text-zinc-600 dark:text-zinc-300 transition-colors"
                    >
                      {b.title}
                    </button>
                  ))}
                </div>

                <Button
                  onClick={handleRunAbTest}
                  disabled={abLoading || !abPrompt.trim()}
                  className="w-full bg-gradient-to-r from-amber-500 to-indigo-600 hover:from-amber-600 hover:to-indigo-700 text-white font-semibold text-xs py-2.5 rounded-xl shadow-md shadow-amber-500/10"
                >
                  {abLoading ? (
                    <Loader2 className="size-4 animate-spin mr-2" />
                  ) : (
                    <Swords className="size-4 mr-2" />
                  )}
                  {abLoading
                    ? "正在并发调用两路模型并由裁判打分..."
                    : "发起 A/B 盲测对比评测"}
                </Button>
              </div>
            </div>

            {/* A/B 盲测裁判结果卡片 */}
            {abResult && (
              <div className="p-6 rounded-2xl border border-amber-500/30 bg-amber-50/20 dark:bg-amber-950/10 shadow-lg space-y-6 animate-in fade-in duration-300">
                {/* 胜负 Badge */}
                <div className="flex items-center justify-between border-b border-amber-200/60 dark:border-amber-800/40 pb-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-xl bg-amber-500 text-white shadow-md shadow-amber-500/20">
                      <Trophy className="size-6" />
                    </div>
                    <div>
                      <div className="text-xs font-semibold text-amber-600 dark:text-amber-400 uppercase tracking-wider">
                        裁判判定结果 (Winner)
                      </div>
                      <div className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                        {abResult.winner === "MODEL_A" ? (
                          <span className="text-indigo-600 dark:text-indigo-400">
                            🏆 Model A ({abResult.modelA}) 胜出
                          </span>
                        ) : abResult.winner === "MODEL_B" ? (
                          <span className="text-purple-600 dark:text-purple-400">
                            🏆 Model B ({abResult.modelB}) 胜出
                          </span>
                        ) : (
                          <span className="text-zinc-600 dark:text-zinc-300">
                            🤝 两者平局 (TIE)
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  <span className="text-xs text-zinc-500">
                    裁判模型: {abResult.judgeModel}
                  </span>
                </div>

                {/* 裁决理由 */}
                <div className="p-4 rounded-xl bg-white/80 dark:bg-zinc-900/80 border border-zinc-200/70 dark:border-zinc-800 text-xs text-zinc-700 dark:text-zinc-300 leading-relaxed">
                  <strong className="text-zinc-900 dark:text-zinc-100">
                    裁判深度评语:
                  </strong>{" "}
                  {abResult.comparisonReason}
                </div>

                {/* 并排对比 */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Model A 回答 */}
                  <div className="p-4 rounded-xl border border-indigo-200/80 dark:border-indigo-800/60 bg-white/90 dark:bg-zinc-900/90 space-y-3">
                    <div className="flex justify-between items-center border-b border-zinc-100 dark:border-zinc-800 pb-2">
                      <span className="text-xs font-bold text-indigo-600 dark:text-indigo-400">
                        Model A: {abResult.modelA} ({abResult.latencyMsA}ms)
                      </span>
                      <span className="text-xs font-bold text-indigo-600">
                        均分:{" "}
                        {(abResult.metricsA.overallScore * 100).toFixed(0)}分
                      </span>
                    </div>
                    <div className="text-xs text-zinc-700 dark:text-zinc-300 max-h-72 overflow-y-auto whitespace-pre-wrap font-mono leading-relaxed p-2 rounded-lg bg-zinc-50 dark:bg-zinc-950">
                      {abResult.responseA}
                    </div>
                  </div>

                  {/* Model B 回答 */}
                  <div className="p-4 rounded-xl border border-purple-200/80 dark:border-purple-800/60 bg-white/90 dark:bg-zinc-900/90 space-y-3">
                    <div className="flex justify-between items-center border-b border-zinc-100 dark:border-zinc-800 pb-2">
                      <span className="text-xs font-bold text-purple-600 dark:text-purple-400">
                        Model B: {abResult.modelB} ({abResult.latencyMsB}ms)
                      </span>
                      <span className="text-xs font-bold text-purple-600">
                        均分:{" "}
                        {(abResult.metricsB.overallScore * 100).toFixed(0)}分
                      </span>
                    </div>
                    <div className="text-xs text-zinc-700 dark:text-zinc-300 max-h-72 overflow-y-auto whitespace-pre-wrap font-mono leading-relaxed p-2 rounded-lg bg-zinc-50 dark:bg-zinc-950">
                      {abResult.responseB}
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ====================== TAB 3: 基准测试集 ====================== */}
        {activeTab === "benchmarks" && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
                  黄金基准测试集 (Golden Benchmark Suites)
                </h2>
                <p className="text-xs text-zinc-500">
                  预置工业级评测用例，支持一键自动化批量跑分
                </p>
              </div>
              <Button
                onClick={() => setShowAddModal(true)}
                size="sm"
                className="gap-1.5 text-xs bg-indigo-600 hover:bg-indigo-700 text-white"
              >
                <Plus className="size-3.5" />
                新建基准用例
              </Button>
            </div>

            {/* 分类过滤器 */}
            <div className="flex items-center gap-1.5 flex-wrap">
              {[
                "ALL",
                "RAG 检索问答",
                "代码生成与优化",
                "逻辑推理",
                "安全对抗",
                "提炼总结",
              ].map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-3 py-1 rounded-full text-xs font-semibold transition-colors ${
                    selectedCategory === cat
                      ? "bg-indigo-600 text-white shadow-xs"
                      : "bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200 dark:hover:bg-zinc-700"
                  }`}
                >
                  {cat === "ALL" ? "全部用例" : cat}
                </button>
              ))}
            </div>

            {/* 用例列表 */}
            <div className="grid grid-cols-1 gap-4">
              {benchmarks.map((bCase) => (
                <div
                  key={bCase.id}
                  className="p-5 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-100/70 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border border-indigo-200/60 dark:border-indigo-800/40">
                        {bCase.category}
                      </span>
                      <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                        {bCase.title}
                      </h3>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => handleDeleteBenchmark(bCase.id)}
                      className="text-zinc-400 hover:text-rose-600"
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>

                  <div className="space-y-1.5">
                    <div className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">
                      提示词 (Prompt):
                    </div>
                    <div className="text-xs text-zinc-800 dark:text-zinc-200 p-3 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/60 dark:border-zinc-800/60">
                      {bCase.prompt}
                    </div>
                  </div>

                  {bCase.expectedOutput && (
                    <div className="text-[11px] text-zinc-500 dark:text-zinc-400 flex items-start gap-1.5">
                      <strong className="text-zinc-700 dark:text-zinc-300 shrink-0">
                        参考标准:
                      </strong>
                      <span>{bCase.expectedOutput}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ====================== TAB 4: 历史记录与人工标注 ====================== */}
        {activeTab === "history" && (
          <div className="space-y-6">
            <div>
              <h2 className="text-base font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
                评测记录与专家标注 (Evaluation Results & Annotation)
              </h2>
              <p className="text-xs text-zinc-500">
                查看历史评测结果、LLM 裁判判词并支持人工打分微调
              </p>
            </div>

            {summary?.recentResults && summary.recentResults.length > 0 ? (
              <div className="space-y-4">
                {summary.recentResults.map((item) => {
                  const isEditing = editingResultId === item.id;
                  return (
                    <div
                      key={item.id}
                      className="p-5 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 shadow-xs backdrop-blur-sm space-y-4"
                    >
                      <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800/80 pb-3">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-xs text-zinc-900 dark:text-zinc-100">
                            {item.benchmarkTitle || "即时单条评测"}
                          </span>
                          <span className="text-[11px] px-2 py-0.5 rounded-md bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400">
                            模型: {item.model}
                          </span>
                          <span className="text-[11px] text-zinc-400">
                            ({item.latencyMs} ms)
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400">
                            裁判分:{" "}
                            {(item.metrics.overallScore * 100).toFixed(1)}分
                          </span>
                          {item.humanScore != null && (
                            <span className="text-xs font-bold text-indigo-600 dark:text-indigo-400">
                              人工分: {(item.humanScore * 100).toFixed(1)}分
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Prompt & 回答节选 */}
                      <div className="space-y-2">
                        <div className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">
                          Prompt: {item.prompt}
                        </div>
                        <div className="text-xs text-zinc-800 dark:text-zinc-200 max-h-40 overflow-y-auto p-3 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/60 dark:border-zinc-800/60 whitespace-pre-wrap">
                          {item.responseText}
                        </div>
                      </div>

                      {/* 裁判评语 */}
                      <div className="p-3 rounded-xl bg-indigo-50/30 dark:bg-indigo-950/20 border border-indigo-200/40 dark:border-indigo-800/30 text-xs text-zinc-700 dark:text-zinc-300">
                        <strong className="text-indigo-600 dark:text-indigo-400">
                          裁判反馈:
                        </strong>{" "}
                        {item.judgeFeedback}
                      </div>

                      {/* 人工标注区 */}
                      {isEditing ? (
                        <div className="p-3 rounded-xl bg-amber-50/30 dark:bg-amber-950/20 border border-amber-200/40 dark:border-amber-800/30 space-y-2">
                          <div className="flex items-center gap-3">
                            <label
                              htmlFor={`score-input-${item.id}`}
                              className="text-xs font-semibold text-amber-700 dark:text-amber-300"
                            >
                              人工评分 (0.0 ~ 1.0):
                            </label>
                            <input
                              id={`score-input-${item.id}`}
                              type="number"
                              step="0.01"
                              min="0"
                              max="1"
                              className="w-24 px-2 py-1 text-xs rounded-md border border-amber-300 bg-white dark:bg-zinc-900"
                              value={humanScoreInput}
                              onChange={(e) =>
                                setHumanScoreInput(e.target.value)
                              }
                            />
                          </div>
                          <div>
                            <input
                              type="text"
                              placeholder="填写人工复核批注与修改建议..."
                              className="w-full px-2 py-1.5 text-xs rounded-md border border-amber-300 bg-white dark:bg-zinc-900"
                              value={humanAnnotationInput}
                              onChange={(e) =>
                                setHumanAnnotationInput(e.target.value)
                              }
                            />
                          </div>
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => setEditingResultId(null)}
                            >
                              取消
                            </Button>
                            <Button
                              size="sm"
                              onClick={() => handleSaveAnnotation(item.id)}
                              disabled={savingAnnotation}
                              className="bg-amber-600 hover:bg-amber-700 text-white text-xs"
                            >
                              {savingAnnotation ? "保存中..." : "保存人工标注"}
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center justify-between pt-1">
                          <div className="text-[11px] text-zinc-500">
                            {item.humanAnnotation
                              ? `人工批注: ${item.humanAnnotation}`
                              : "暂无人工标注"}
                          </div>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setEditingResultId(item.id);
                              setHumanScoreInput(
                                (item.humanScore ?? 0.95).toString(),
                              );
                              setHumanAnnotationInput(
                                item.humanAnnotation ?? "",
                              );
                            }}
                            className="gap-1 text-xs"
                          >
                            <Edit3 className="size-3" />
                            人工标注覆盖
                          </Button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="py-12 text-center text-xs text-zinc-500">
                暂无评测记录，请在「基准测试集」或「A/B 盲测」发测评测。
              </div>
            )}
          </div>
        )}
      </main>

      {/* 新建基准用例模态框 */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="flex flex-col w-full max-w-lg rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-6 shadow-2xl space-y-4">
            <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
              新建基准测试用例
            </h2>

            <div className="space-y-3 text-xs">
              <div>
                <label
                  htmlFor="benchmark-title-input"
                  className="font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  用例名称
                </label>
                <input
                  id="benchmark-title-input"
                  type="text"
                  placeholder="例如：Spring AI PgVector 过滤检索"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  className="mt-1 w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950"
                />
              </div>

              <div>
                <label
                  htmlFor="benchmark-category-select"
                  className="font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  用例分类
                </label>
                <select
                  id="benchmark-category-select"
                  value={newCategory}
                  onChange={(e) => setNewCategory(e.target.value)}
                  className="mt-1 w-full px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950"
                >
                  <option value="RAG 检索问答">RAG 检索问答</option>
                  <option value="代码生成与优化">代码生成与优化</option>
                  <option value="逻辑推理">逻辑推理</option>
                  <option value="安全对抗">安全对抗</option>
                  <option value="提炼总结">提炼总结</option>
                </select>
              </div>

              <div>
                <label
                  htmlFor="benchmark-prompt-input"
                  className="font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  提示词 (Prompt)
                </label>
                <textarea
                  id="benchmark-prompt-input"
                  placeholder="输入测试问题或指令..."
                  value={newPrompt}
                  onChange={(e) => setNewPrompt(e.target.value)}
                  className="mt-1 w-full h-20 px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950"
                />
              </div>

              <div>
                <label
                  htmlFor="benchmark-expected-input"
                  className="font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  参考标准答案 (Expected Output)
                </label>
                <textarea
                  id="benchmark-expected-input"
                  placeholder="输入预期的标准答案或关键点..."
                  value={newExpected}
                  onChange={(e) => setNewExpected(e.target.value)}
                  className="mt-1 w-full h-16 px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowAddModal(false)}
              >
                取消
              </Button>
              <Button
                size="sm"
                onClick={handleAddBenchmark}
                className="bg-indigo-600 hover:bg-indigo-700 text-white text-xs"
              >
                保存基准用例
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
