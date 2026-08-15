"use client";

import {
  AlertTriangle,
  Bot,
  CheckCircle2,
  Clock,
  Code2,
  FileCheck,
  FileSearch,
  GitBranch,
  Layers,
  Loader2,
  Play,
  Scale,
  Sparkles,
  UserCheck,
  Users,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Markdown } from "@/components/chat/markdown";
import {
  type ConflictItem,
  type MultiAgentEvent,
  type MultiAgentPlan,
  orchestrateMultiAgentApi,
  resolveConflictApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

interface MultiAgentModalProps {
  open: boolean;
  onClose: () => void;
  initialGoal?: string;
  conversationId?: string;
  onAdopt?: (synthesisResult: string) => void;
}

const PRESET_GOALS = [
  "对比 Quarkus, Spring Boot 3 与 Micronaut 的性能与开发体验",
  "设计并实现支持 JWT 与 OAuth2 的全栈用户鉴权系统",
  "深度评测主流开源 AI Agent 框架（LangChain, AutoGen, CrewAI）",
];

const AGENT_ROLES_META: Record<
  string,
  { name: string; color: string; icon: typeof Bot }
> = {
  research: {
    name: "深度调研代理",
    color:
      "text-blue-600 bg-blue-50 dark:bg-blue-950/50 dark:text-blue-400 border-blue-200 dark:border-blue-800",
    icon: FileSearch,
  },
  code: {
    name: "架构工程代理",
    color:
      "text-emerald-600 bg-emerald-50 dark:bg-emerald-950/50 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800",
    icon: Code2,
  },
  analysis: {
    name: "质量审查代理",
    color:
      "text-purple-600 bg-purple-50 dark:bg-purple-950/50 dark:text-purple-400 border-purple-200 dark:border-purple-800",
    icon: Scale,
  },
  review: {
    name: "复核审查代理",
    color:
      "text-amber-600 bg-amber-50 dark:bg-amber-950/50 dark:text-amber-400 border-amber-200 dark:border-amber-800",
    icon: FileCheck,
  },
  synthesis: {
    name: "综合汇总代理",
    color:
      "text-indigo-600 bg-indigo-50 dark:bg-indigo-950/50 dark:text-indigo-400 border-indigo-200 dark:border-indigo-800",
    icon: Sparkles,
  },
};

export function MultiAgentModal({
  open,
  onClose,
  initialGoal = "",
  conversationId,
  onAdopt,
}: MultiAgentModalProps) {
  const [goal, setGoal] = useState(initialGoal);
  const [interactiveHITL, setInteractiveHITL] = useState(true);
  const [selectedProvider, setSelectedProvider] = useState("openai");
  const [selectedModel, setSelectedModel] = useState("gpt-4o");

  const [running, setRunning] = useState(false);
  const [plan, setPlan] = useState<MultiAgentPlan | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"dag" | "synthesis">("dag");

  // 人机裁决输入状态
  const [userDecisionText, setUserDecisionText] = useState("");
  const [resolvingConflict, setResolvingConflict] = useState(false);

  const abortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (initialGoal) {
      setGoal(initialGoal);
    }
  }, [initialGoal]);

  if (!open) return null;

  const handleStartOrchestration = async () => {
    if (!goal.trim() || running) return;

    setRunning(true);
    setPlan(null);
    setSelectedNodeId(null);
    setActiveTab("dag");

    abortControllerRef.current = new AbortController();

    try {
      // 优先尝试通过 SSE 流式接收多 Agent 事件
      const res = await fetch("/api/agents/orchestrate/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          goal: goal.trim(),
          provider: selectedProvider,
          model: selectedModel,
          interactiveConflictResolution: interactiveHITL,
          conversationId,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!res.ok || !res.body) {
        throw new Error("流式端点不可用，回退至非流式");
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed.startsWith("data:")) continue;
          const jsonStr = trimmed.slice(5).trim();
          if (!jsonStr) continue;

          try {
            const event = JSON.parse(jsonStr) as MultiAgentEvent;
            handleStreamEvent(event);
          } catch {
            // ignore malformed SSE
          }
        }
      }
    } catch (err: unknown) {
      if ((err as Error)?.name === "AbortError") {
        toast.info("多 Agent 协同流程已手动终止");
      } else {
        // 回退调用非流式接口
        try {
          const syncRes = await orchestrateMultiAgentApi({
            goal: goal.trim(),
            provider: selectedProvider,
            model: selectedModel,
            interactiveConflictResolution: interactiveHITL,
            conversationId,
          });
          if (syncRes?.plan) {
            setPlan(syncRes.plan);
            toast.success("多 Agent 协同分析完成！");
          }
        } catch {
          toast.error("多 Agent 协同执行失败，请检查模型连接");
        }
      }
    } finally {
      setRunning(false);
    }
  };

  const handleStreamEvent = (event: MultiAgentEvent) => {
    if (event.eventType === "plan_created" && event.plan) {
      setPlan(event.plan);
    } else if (event.eventType === "agent_started" && event.nodeId) {
      setPlan((prev) => {
        if (!prev) return prev;
        const updatedNodes = prev.nodes.map((n) =>
          n.id === event.nodeId ? { ...n, status: "RUNNING" as const } : n,
        );
        return { ...prev, status: "EXECUTING", nodes: updatedNodes };
      });
    } else if (event.eventType === "agent_completed" && event.nodeId) {
      setPlan((prev) => {
        if (!prev) return prev;
        const updatedNodes = prev.nodes.map((n) =>
          n.id === event.nodeId
            ? {
                ...n,
                status: "COMPLETED" as const,
                output: event.content,
                durationMs: event.durationMs,
              }
            : n,
        );
        return { ...prev, nodes: updatedNodes };
      });
    } else if (event.eventType === "agent_failed" && event.nodeId) {
      setPlan((prev) => {
        if (!prev) return prev;
        const updatedNodes = prev.nodes.map((n) =>
          n.id === event.nodeId
            ? {
                ...n,
                status: "FAILED" as const,
                errorMessage: event.content,
                durationMs: event.durationMs,
              }
            : n,
        );
        return { ...prev, nodes: updatedNodes };
      });
    } else if (event.eventType === "conflict_detected" && event.conflict) {
      setPlan((prev) => {
        if (!prev) return prev;
        const exists = prev.conflicts.some(
          (c) => c.conflictId === event.conflict?.conflictId,
        );
        const conflicts = exists
          ? prev.conflicts
          : [...prev.conflicts, event.conflict as ConflictItem];
        return { ...prev, conflicts };
      });
      toast.warning(`检测到事实分歧：${event.conflict.topic}`);
    } else if (event.eventType === "conflict_waiting_user") {
      if (event.plan) setPlan(event.plan);
      setRunning(false);
      toast.info("已检测到关键分歧，工作流已挂起等待您的裁决");
    } else if (
      event.eventType === "synthesis_chunk" ||
      event.eventType === "workflow_completed"
    ) {
      if (event.plan) {
        setPlan(event.plan);
      } else if (event.content) {
        setPlan((prev) =>
          prev ? { ...prev, synthesisResult: event.content } : prev,
        );
      }
      if (event.eventType === "workflow_completed") {
        toast.success("多 Agent 协同汇总报告已生成完毕！");
      }
    }
  };

  const handleResolveConflict = async (
    conflictId: string,
    decisionOverride?: string,
  ) => {
    if (!plan) return;
    const finalDecision = (
      decisionOverride ||
      userDecisionText ||
      "采纳合理平衡方案"
    ).trim();

    setResolvingConflict(true);
    try {
      const updated = await resolveConflictApi({
        planId: plan.planId,
        conflictId,
        decision: finalDecision,
      });
      if (updated) {
        setPlan(updated);
        toast.success("已提交裁决，综合代理已完成最终汇总！");
        setActiveTab("synthesis");
      }
    } catch {
      toast.error("提交裁决失败，请重试");
    } finally {
      setResolvingConflict(false);
    }
  };

  const selectedNode = plan?.nodes.find((n) => n.id === selectedNodeId);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-md p-4 sm:p-6 animate-in fade-in duration-200">
      <div className="flex h-[92vh] w-full max-w-6xl flex-col rounded-3xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
        {/* 顶部 Header */}
        <div className="flex items-center justify-between border-b border-zinc-200 px-6 py-4 dark:border-zinc-800 bg-gradient-to-r from-indigo-50/50 via-white to-purple-50/50 dark:from-indigo-950/20 dark:via-zinc-950 dark:to-purple-950/20">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-2xl bg-indigo-600 text-white shadow-md shadow-indigo-500/20">
              <Users className="size-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                  多 Agent 协同研讨与执行工作台
                </h3>
                <span className="rounded-full border border-indigo-200 bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-700 dark:border-indigo-900 dark:bg-indigo-950 dark:text-indigo-300">
                  DAG 拓扑调度
                </span>
                {plan?.status && (
                  <span
                    className={cn(
                      "rounded-full px-2 py-0.5 text-[11px] font-bold",
                      plan.status === "WAITING_USER"
                        ? "bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300 animate-pulse"
                        : plan.status === "EXECUTING"
                          ? "bg-indigo-100 text-indigo-800 dark:bg-indigo-950/60 dark:text-indigo-300"
                          : plan.status === "COMPLETED"
                            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300"
                            : "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
                    )}
                  >
                    {plan.status === "WAITING_USER"
                      ? "⏸️ 等待人工裁决 (HITL)"
                      : plan.status === "EXECUTING"
                        ? "⚡ 并发执行中"
                        : plan.status === "COMPLETED"
                          ? "✅ 协同完成"
                          : plan.status}
                  </span>
                )}
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                分解复杂目标为多子代理并行研究与串行综合，支持成果共享与事实分歧智能检测
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="flex size-8 items-center justify-center rounded-xl text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        {/* 目标输入与控制栏 */}
        <div className="border-b border-zinc-200 bg-zinc-50/50 p-4 dark:border-zinc-800 dark:bg-zinc-900/30">
          <div className="space-y-3">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <div className="relative flex-1">
                <input
                  type="text"
                  value={goal}
                  onChange={(e) => setGoal(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      void handleStartOrchestration();
                    }
                  }}
                  placeholder="输入需要多 Agent 协同研讨与分析的复杂目标（如：对比 3 个框架性能并输出选型方案）..."
                  className="w-full rounded-2xl border border-zinc-200 bg-white px-4 py-2.5 text-xs text-zinc-900 placeholder:text-zinc-400 focus:border-indigo-500 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100 shadow-2xs"
                />
              </div>

              <div className="flex items-center gap-2 flex-wrap">
                <select
                  value={`${selectedProvider}::${selectedModel}`}
                  onChange={(e) => {
                    const [p, m] = e.target.value.split("::");
                    setSelectedProvider(p);
                    setSelectedModel(m);
                  }}
                  aria-label="选择协作主模型"
                  className="rounded-xl border border-zinc-200 bg-white px-3 py-2 text-xs font-medium text-zinc-700 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-200 outline-none shadow-2xs"
                >
                  <option value="openai::gpt-4o">OpenAI GPT-4o</option>
                  <option value="anthropic::claude-3-5-sonnet">
                    Claude 3.5 Sonnet
                  </option>
                  <option value="deepseek::deepseek-chat">DeepSeek V3</option>
                  <option value="deepseek::deepseek-reasoner">
                    DeepSeek R1
                  </option>
                  <option value="google::gemini-2.5-flash">
                    Gemini 2.5 Flash
                  </option>
                  <option value="ollama::qwen2.5:7b">Qwen 2.5 (本地)</option>
                </select>

                <label className="flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-3 py-2 text-xs font-medium text-zinc-700 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-200 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={interactiveHITL}
                    onChange={(e) => setInteractiveHITL(e.target.checked)}
                    className="size-3.5 rounded text-indigo-600 focus:ring-indigo-500"
                  />
                  <span>开启人工裁决 (HITL)</span>
                </label>

                <button
                  type="button"
                  onClick={() => {
                    if (running) {
                      abortControllerRef.current?.abort();
                      setRunning(false);
                    } else {
                      void handleStartOrchestration();
                    }
                  }}
                  disabled={!goal.trim()}
                  className={cn(
                    "flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-bold text-white shadow-md transition-all cursor-pointer",
                    running
                      ? "bg-rose-600 hover:bg-rose-700 shadow-rose-500/20"
                      : "bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 shadow-indigo-500/20 disabled:opacity-50",
                  )}
                >
                  {running ? (
                    <>
                      <Loader2 className="size-3.5 animate-spin" />
                      <span>终止协同</span>
                    </>
                  ) : (
                    <>
                      <Play className="size-3.5 fill-current" />
                      <span>发起多 Agent 协同</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* 预设目标快捷 Pill */}
            {!plan && (
              <div className="flex items-center gap-2 flex-wrap text-xs text-zinc-500">
                <span className="font-semibold">示例场景：</span>
                {PRESET_GOALS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => setGoal(preset)}
                    className="rounded-lg border border-zinc-200 bg-white px-2.5 py-1 text-xs text-zinc-600 hover:border-indigo-400 hover:bg-indigo-50 hover:text-indigo-600 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 transition-colors shadow-2xs"
                  >
                    {preset}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* 中间主要工作区（DAG 拓扑与综合报告 Tabs） */}
        <div className="flex flex-1 overflow-hidden">
          {/* 左侧主展示区 */}
          <div className="flex flex-1 flex-col overflow-hidden border-r border-zinc-200 dark:border-zinc-800">
            {/* 模式切换 Tab Bar */}
            <div className="flex items-center justify-between border-b border-zinc-200 bg-zinc-50/30 px-6 py-2 dark:border-zinc-800 dark:bg-zinc-900/20">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setActiveTab("dag")}
                  className={cn(
                    "flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold transition-colors",
                    activeTab === "dag"
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800",
                  )}
                >
                  <Layers className="size-3.5" />
                  <span>DAG 拓扑执行泳道</span>
                  {plan?.nodes && (
                    <span className="ml-1 rounded-full bg-white/20 px-1.5 py-0.2 text-[10px]">
                      {plan.nodes.length}
                    </span>
                  )}
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab("synthesis")}
                  className={cn(
                    "flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold transition-colors",
                    activeTab === "synthesis"
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800",
                  )}
                >
                  <Sparkles className="size-3.5" />
                  <span>综合报告交付</span>
                  {plan?.synthesisResult && (
                    <span className="ml-1 size-2 rounded-full bg-emerald-400" />
                  )}
                </button>
              </div>

              {plan?.conflicts && plan.conflicts.length > 0 && (
                <div className="flex items-center gap-1.5 rounded-xl border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-800 dark:border-amber-900 dark:bg-amber-950/60 dark:text-amber-300">
                  <AlertTriangle className="size-3.5 text-amber-500" />
                  <span>识别到 {plan.conflicts.length} 处事实分歧</span>
                </div>
              )}
            </div>

            {/* 内容区 */}
            <div className="flex-1 overflow-y-auto p-6">
              {!plan ? (
                <div className="flex h-full flex-col items-center justify-center text-center">
                  <div className="flex size-16 items-center justify-center rounded-3xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400 mb-4 shadow-inner">
                    <Users className="size-8" />
                  </div>
                  <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-200">
                    等待发起多 Agent 协同研讨
                  </h4>
                  <p className="mt-1.5 max-w-sm text-xs text-zinc-500">
                    输入目标并点击「发起协同」，系统将自动构建有向无环图（DAG），调度各专业代理协同工作。
                  </p>
                </div>
              ) : activeTab === "dag" ? (
                <div className="space-y-6">
                  {/* 人工裁决挂起卡片 (HITL Alert) */}
                  {plan.status === "WAITING_USER" &&
                    plan.conflicts.length > 0 && (
                      <div className="rounded-3xl border border-amber-300 bg-amber-50/80 p-5 dark:border-amber-900/60 dark:bg-amber-950/40 shadow-sm animate-in fade-in-50 zoom-in-95">
                        <div className="flex items-start gap-3">
                          <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-amber-500 text-white shadow-sm">
                            <UserCheck className="size-5" />
                          </div>
                          <div className="flex-1 space-y-3">
                            <div>
                              <h4 className="text-sm font-bold text-amber-950 dark:text-amber-200">
                                冲突裁决请求 (Human-in-the-Loop)
                              </h4>
                              <p className="text-xs text-amber-800 dark:text-amber-300 mt-0.5">
                                子代理之间在以下核心事实或指标上存在明显分歧，流水线已挂起，请您选择或输入裁决指导：
                              </p>
                            </div>

                            {plan.conflicts.map((conflict) => (
                              <div
                                key={conflict.conflictId}
                                className="rounded-2xl border border-amber-200 bg-white/90 p-3.5 dark:border-amber-900/60 dark:bg-zinc-900/90 space-y-2.5 shadow-2xs"
                              >
                                <div className="flex items-center justify-between">
                                  <span className="font-bold text-xs text-zinc-900 dark:text-zinc-100">
                                    争议点：{conflict.topic}
                                  </span>
                                  <span className="rounded-md bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                                    {conflict.resolutionStatus}
                                  </span>
                                </div>
                                <p className="text-xs text-zinc-600 dark:text-zinc-400">
                                  {conflict.description}
                                </p>

                                <div className="flex flex-wrap gap-2 pt-1">
                                  <button
                                    type="button"
                                    onClick={() =>
                                      handleResolveConflict(
                                        conflict.conflictId,
                                        `采纳 ${conflict.agentA} 的分析主张`,
                                      )
                                    }
                                    disabled={resolvingConflict}
                                    className="rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:border-indigo-500 hover:bg-indigo-50 hover:text-indigo-600 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-200 transition-colors cursor-pointer"
                                  >
                                    倾向于 {conflict.agentA}
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() =>
                                      handleResolveConflict(
                                        conflict.conflictId,
                                        `采纳 ${conflict.agentB} 的分析主张`,
                                      )
                                    }
                                    disabled={resolvingConflict}
                                    className="rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:border-indigo-500 hover:bg-indigo-50 hover:text-indigo-600 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-200 transition-colors cursor-pointer"
                                  >
                                    倾向于 {conflict.agentB}
                                  </button>
                                </div>
                              </div>
                            ))}

                            <div className="flex gap-2">
                              <input
                                type="text"
                                value={userDecisionText}
                                onChange={(e) =>
                                  setUserDecisionText(e.target.value)
                                }
                                placeholder="或者输入您的自定义指导原则（如：以同等硬件规格下的吞吐量为准）..."
                                className="flex-1 rounded-xl border border-amber-300 bg-white px-3 py-2 text-xs text-zinc-900 focus:outline-none dark:border-amber-800 dark:bg-zinc-900 dark:text-zinc-100"
                              />
                              <button
                                type="button"
                                onClick={() =>
                                  handleResolveConflict(
                                    plan.conflicts[0].conflictId,
                                  )
                                }
                                disabled={resolvingConflict}
                                className="rounded-xl bg-amber-600 px-4 py-2 text-xs font-bold text-white hover:bg-amber-700 shadow-md shadow-amber-600/20 disabled:opacity-50 cursor-pointer"
                              >
                                {resolvingConflict ? (
                                  <Loader2 className="size-3.5 animate-spin" />
                                ) : (
                                  "提交裁决并继续"
                                )}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}

                  {/* DAG 节点拓扑与状态泳道 */}
                  <div className="space-y-4">
                    <h4 className="text-xs font-bold text-zinc-500 uppercase tracking-wider">
                      子任务执行拓扑图与状态流转
                    </h4>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                      {plan.nodes.map((node) => {
                        const roleMeta = AGENT_ROLES_META[node.role] || {
                          name: node.role,
                          color: "text-zinc-600 bg-zinc-100 border-zinc-200",
                          icon: Bot,
                        };
                        const Icon = roleMeta.icon;
                        const isSelected = selectedNodeId === node.id;

                        return (
                          <button
                            type="button"
                            key={node.id}
                            onClick={() => setSelectedNodeId(node.id)}
                            className={cn(
                              "flex flex-col justify-between rounded-2xl border p-4 text-left transition-all cursor-pointer",
                              isSelected
                                ? "border-indigo-500 bg-indigo-50/40 dark:border-indigo-600 dark:bg-indigo-950/30 shadow-md"
                                : "border-zinc-200 bg-white hover:border-zinc-300 dark:border-zinc-800 dark:bg-zinc-900/60",
                            )}
                          >
                            <div className="space-y-2.5 w-full">
                              {/* 顶部角色与状态 */}
                              <div className="flex items-center justify-between">
                                <div className="flex items-center gap-1.5">
                                  <span
                                    className={cn(
                                      "flex items-center gap-1 rounded-lg border px-2 py-0.5 text-[10px] font-bold",
                                      roleMeta.color,
                                    )}
                                  >
                                    <Icon className="size-3" />
                                    <span>{roleMeta.name}</span>
                                  </span>
                                  <span className="font-mono text-[10px] text-zinc-400">
                                    {node.id}
                                  </span>
                                </div>

                                <div className="flex items-center gap-1">
                                  {node.status === "RUNNING" ? (
                                    <span className="flex items-center gap-1 text-[11px] font-bold text-indigo-600 dark:text-indigo-400">
                                      <Loader2 className="size-3 animate-spin" />
                                      <span>运行中</span>
                                    </span>
                                  ) : node.status === "COMPLETED" ? (
                                    <span className="flex items-center gap-1 text-[11px] font-bold text-emerald-600 dark:text-emerald-400">
                                      <CheckCircle2 className="size-3" />
                                      <span>已完成</span>
                                    </span>
                                  ) : node.status === "FAILED" ? (
                                    <span className="flex items-center gap-1 text-[11px] font-bold text-rose-600 dark:text-rose-400">
                                      <AlertTriangle className="size-3" />
                                      <span>已降级</span>
                                    </span>
                                  ) : (
                                    <span className="text-[11px] font-medium text-zinc-400">
                                      等待就绪
                                    </span>
                                  )}
                                </div>
                              </div>

                              {/* 标题与描述 */}
                              <div>
                                <h5 className="text-xs font-bold text-zinc-900 dark:text-zinc-100">
                                  {node.title}
                                </h5>
                                <p className="text-[11px] text-zinc-500 line-clamp-2 mt-1">
                                  {node.description}
                                </p>
                              </div>
                            </div>

                            {/* 底部依赖与耗时 */}
                            <div className="mt-3 flex items-center justify-between border-t border-zinc-100 pt-2.5 text-[10px] text-zinc-400 dark:border-zinc-800 w-full">
                              <div className="flex items-center gap-1">
                                <GitBranch className="size-3" />
                                <span>
                                  依赖:{" "}
                                  {node.dependencies.length > 0
                                    ? node.dependencies.join(", ")
                                    : "无 (并行启动)"}
                                </span>
                              </div>
                              {node.durationMs ? (
                                <div className="flex items-center gap-0.5 font-mono">
                                  <Clock className="size-3" />
                                  <span>{node.durationMs}ms</span>
                                </div>
                              ) : null}
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>
              ) : (
                /* 综合报告展示区 */
                <div className="space-y-4">
                  <div className="flex items-center justify-between border-b border-zinc-200 pb-3 dark:border-zinc-800">
                    <div className="flex items-center gap-2">
                      <div className="flex size-7 items-center justify-center rounded-lg bg-indigo-600 text-white">
                        <Sparkles className="size-4" />
                      </div>
                      <h4 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                        多 Agent 综合汇总交付报告
                      </h4>
                    </div>

                    <div className="flex items-center gap-2">
                      {plan.synthesisResult && onAdopt && (
                        <button
                          type="button"
                          onClick={() => {
                            onAdopt(plan.synthesisResult || "");
                            toast.success("已采纳综合报告并写入对话！");
                            onClose();
                          }}
                          className="flex items-center gap-1.5 rounded-xl bg-indigo-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-indigo-700 shadow-md shadow-indigo-600/20 cursor-pointer"
                        >
                          <CheckCircle2 className="size-3.5" />
                          <span>采纳并写入会话</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {plan.synthesisResult ? (
                    <div className="rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900 shadow-sm">
                      <Markdown content={plan.synthesisResult} />
                    </div>
                  ) : (
                    <div className="flex h-64 flex-col items-center justify-center text-center">
                      <Loader2 className="size-8 animate-spin text-indigo-500 mb-2" />
                      <p className="text-xs font-medium text-zinc-500">
                        综合代理正在汇聚所有子任务结论并起草报告...
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* 右侧节点详情抽屉 / Inspector */}
          {selectedNode && (
            <div className="w-96 overflow-y-auto border-l border-zinc-200 bg-zinc-50/40 p-5 dark:border-zinc-800 dark:bg-zinc-900/40 space-y-4">
              <div className="flex items-center justify-between border-b border-zinc-200 pb-3 dark:border-zinc-800">
                <div className="flex items-center gap-2">
                  <Bot className="size-4 text-indigo-600" />
                  <h4 className="text-xs font-bold text-zinc-900 dark:text-zinc-100">
                    子代理详情 ({selectedNode.id})
                  </h4>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedNodeId(null)}
                  className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
                >
                  <X className="size-3.5" />
                </button>
              </div>

              <div className="space-y-3 text-xs">
                <div>
                  <span className="text-[10px] font-bold uppercase text-zinc-400">
                    任务标题
                  </span>
                  <p className="font-bold text-zinc-800 dark:text-zinc-200 mt-0.5">
                    {selectedNode.title}
                  </p>
                </div>

                <div>
                  <span className="text-[10px] font-bold uppercase text-zinc-400">
                    角色与指令
                  </span>
                  <p className="text-zinc-600 dark:text-zinc-400 mt-0.5">
                    {selectedNode.description}
                  </p>
                </div>

                <div>
                  <span className="text-[10px] font-bold uppercase text-zinc-400">
                    执行状态与耗时
                  </span>
                  <p className="font-mono text-zinc-700 dark:text-zinc-300 mt-0.5">
                    {selectedNode.status} (
                    {selectedNode.durationMs
                      ? `${selectedNode.durationMs}ms`
                      : "-"}
                    )
                  </p>
                </div>

                <div>
                  <span className="text-[10px] font-bold uppercase text-zinc-400">
                    交付成果与输出
                  </span>
                  {selectedNode.output ? (
                    <div className="mt-1 rounded-xl border border-zinc-200 bg-white p-3 text-xs dark:border-zinc-800 dark:bg-zinc-900 leading-relaxed max-h-96 overflow-y-auto">
                      <Markdown content={selectedNode.output} />
                    </div>
                  ) : selectedNode.errorMessage ? (
                    <div className="mt-1 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40">
                      {selectedNode.errorMessage}
                    </div>
                  ) : (
                    <p className="text-zinc-400 mt-1 italic">
                      该子代理正在执行中或等待前置依赖就绪...
                    </p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
