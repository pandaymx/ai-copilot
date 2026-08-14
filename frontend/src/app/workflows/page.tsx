"use client";

import {
  ArrowLeft,
  Check,
  CheckCircle2,
  Copy,
  GitBranch,
  History,
  Layers,
  Loader2,
  Play,
  RotateCcw,
  Save,
  Settings,
  Sparkles,
  Terminal,
  Workflow,
  X,
  XCircle,
  Zap,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  executeWorkflowStream,
  fetchWorkflowExecutions,
  fetchWorkflows,
  type NodeExecutionSnapshot,
  saveWorkflow,
  type WorkflowDefinition,
  type WorkflowEvent,
  type WorkflowExecutionRecord,
  type WorkflowNodeType,
} from "@/lib/workflow-api";

// 节点类型视觉元数据
const NODE_META: Record<
  WorkflowNodeType,
  {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    color: string;
    bg: string;
    border: string;
    glow: string;
  }
> = {
  INPUT: {
    label: "输入参数",
    icon: Layers,
    color: "text-emerald-500 dark:text-emerald-400",
    bg: "bg-emerald-500/10",
    border: "border-emerald-500/30",
    glow: "shadow-emerald-500/20",
  },
  LLM: {
    label: "LLM 推理",
    icon: Sparkles,
    color: "text-indigo-500 dark:text-indigo-400",
    bg: "bg-indigo-500/10",
    border: "border-indigo-500/30",
    glow: "shadow-indigo-500/20",
  },
  TOOL: {
    label: "工具调用",
    icon: Terminal,
    color: "text-amber-500 dark:text-amber-400",
    bg: "bg-amber-500/10",
    border: "border-amber-500/30",
    glow: "shadow-amber-500/20",
  },
  CONDITION: {
    label: "条件分支",
    icon: GitBranch,
    color: "text-purple-500 dark:text-purple-400",
    bg: "bg-purple-500/10",
    border: "border-purple-500/30",
    glow: "shadow-purple-500/20",
  },
  PARALLEL: {
    label: "并行聚合",
    icon: Zap,
    color: "text-sky-500 dark:text-sky-400",
    bg: "bg-sky-500/10",
    border: "border-sky-500/30",
    glow: "shadow-sky-500/20",
  },
  OUTPUT: {
    label: "终稿产物",
    icon: CheckCircle2,
    color: "text-rose-500 dark:text-rose-400",
    bg: "bg-rose-500/10",
    border: "border-rose-500/30",
    glow: "shadow-rose-500/20",
  },
};

export default function WorkflowsPage() {
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [currentWorkflow, setCurrentWorkflow] =
    useState<WorkflowDefinition | null>(null);
  const [_isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  // 执行状态与流式快照
  const [isExecuting, setIsExecuting] = useState(false);
  const [executionInputs, setExecutionInputs] = useState<
    Record<string, unknown>
  >({});
  const [nodeSnapshots, setNodeSnapshots] = useState<
    Record<string, NodeExecutionSnapshot>
  >({});
  const [activeConsoleTab, setActiveConsoleTab] = useState<
    "form" | "timeline" | "output" | "history"
  >("form");
  const [finalOutputText, setFinalOutputText] = useState<string>("");
  const [executionHistory, setExecutionHistory] = useState<
    WorkflowExecutionRecord[]
  >([]);
  const [executingMetrics, setExecutingMetrics] = useState<{
    durationMs: number;
    totalTokens: number;
  }>({
    durationMs: 0,
    totalTokens: 0,
  });

  // 画布平移与缩放
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 40, y: 40 });
  const [isDraggingCanvas, setIsDraggingCanvas] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [copiedOutput, setCopiedOutput] = useState(false);

  // 加载工作流列表
  const loadWorkflows = async () => {
    setIsLoading(true);
    try {
      const list = await fetchWorkflows();
      setWorkflows(list);
      if (list.length > 0 && !currentWorkflow) {
        selectWorkflow(list[0]);
      }
    } catch (e) {
      console.error("加载工作流列表失败", e);
    } finally {
      setIsLoading(false);
    }
  };

  // biome-ignore lint/correctness/useExhaustiveDependencies: load templates on mount
  useEffect(() => {
    loadWorkflows();
  }, []);

  const selectWorkflow = (wf: WorkflowDefinition) => {
    setCurrentWorkflow(wf);
    setSelectedNodeId(null);
    setNodeSnapshots({});
    setFinalOutputText("");
    setExecutionInputs(wf.defaultInputs ? { ...wf.defaultInputs } : {});
    loadHistory(wf.id);
  };

  const loadHistory = async (workflowId: string) => {
    try {
      const records = await fetchWorkflowExecutions(workflowId);
      setExecutionHistory(records);
    } catch (e) {
      console.error("加载历史记录失败", e);
    }
  };

  // 保存工作流
  const handleSaveWorkflow = async () => {
    if (!currentWorkflow) return;
    setIsSaving(true);
    try {
      const updated = await saveWorkflow(currentWorkflow);
      setCurrentWorkflow(updated);
      setWorkflows((prev) =>
        prev.map((w) => (w.id === updated.id ? updated : w)),
      );
    } catch (e) {
      console.error("保存失败", e);
    } finally {
      setIsSaving(false);
    }
  };

  // 执行工作流
  const handleRunWorkflow = async () => {
    if (!currentWorkflow || isExecuting) return;
    setIsExecuting(true);
    setActiveConsoleTab("timeline");
    setFinalOutputText("");
    setExecutingMetrics({ durationMs: 0, totalTokens: 0 });

    // 初始化节点状态为 PENDING
    const initialSnapshots: Record<string, NodeExecutionSnapshot> = {};
    for (const node of currentWorkflow.nodes) {
      initialSnapshots[node.id] = {
        nodeId: node.id,
        nodeName: node.name,
        nodeType: node.type,
        status: "PENDING",
      };
    }
    setNodeSnapshots(initialSnapshots);

    await executeWorkflowStream(
      currentWorkflow.id,
      executionInputs,
      (event: WorkflowEvent) => {
        const nodeId = event.nodeId;
        if (event.type === "node_started" && nodeId) {
          setNodeSnapshots((prev) => ({
            ...prev,
            [nodeId]: {
              ...(prev[nodeId] || {
                nodeId,
                nodeName: event.nodeName || "",
                nodeType: (event.nodeType as WorkflowNodeType) || "LLM",
              }),
              status: "RUNNING",
            },
          }));
        } else if (event.type === "node_finished" && nodeId) {
          setNodeSnapshots((prev) => ({
            ...prev,
            [nodeId]: {
              ...prev[nodeId],
              status: "COMPLETED",
              outputState: event.output,
              durationMs: event.durationMs,
              tokenUsage: event.tokenUsage,
            },
          }));
          if (event.tokenUsage) {
            setExecutingMetrics((prev) => ({
              ...prev,
              totalTokens: prev.totalTokens + (event.tokenUsage || 0),
            }));
          }
        } else if (event.type === "node_skipped" && nodeId) {
          setNodeSnapshots((prev) => ({
            ...prev,
            [nodeId]: {
              ...prev[nodeId],
              status: "SKIPPED",
              skipReason: event.skipReason,
            },
          }));
        } else if (event.type === "node_failed" && nodeId) {
          setNodeSnapshots((prev) => ({
            ...prev,
            [nodeId]: {
              ...prev[nodeId],
              status: "FAILED",
              error: event.error,
              durationMs: event.durationMs,
            },
          }));
        } else if (event.type === "workflow_completed") {
          if (event.durationMs) {
            setExecutingMetrics((prev) => ({
              ...prev,
              durationMs: event.durationMs || 0,
            }));
          }
          if (event.finalOutputs) {
            const outStr =
              typeof event.finalOutputs.output === "string"
                ? event.finalOutputs.output
                : JSON.stringify(event.finalOutputs, null, 2);
            setFinalOutputText(outStr);
            setActiveConsoleTab("output");
          }
          loadHistory(currentWorkflow.id);
        }
      },
      (err) => {
        console.error("执行流异常", err);
        setIsExecuting(false);
      },
      () => {
        setIsExecuting(false);
      },
    );
  };

  const handleCopyOutput = async () => {
    if (!finalOutputText) return;
    try {
      await navigator.clipboard.writeText(finalOutputText);
      setCopiedOutput(true);
      setTimeout(() => setCopiedOutput(false), 2000);
    } catch {}
  };

  const selectedNode = currentWorkflow?.nodes.find(
    (n) => n.id === selectedNodeId,
  );

  // 画布鼠标拖拽平移事件
  const handleMouseDown = (e: React.MouseEvent) => {
    if (
      e.target === e.currentTarget ||
      (e.target as HTMLElement).tagName === "svg"
    ) {
      setIsDraggingCanvas(true);
      setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (isDraggingCanvas) {
      setPan({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y });
    }
  };

  const handleMouseUp = () => {
    setIsDraggingCanvas(false);
  };

  return (
    <div className="flex h-screen w-screen flex-col overflow-hidden bg-zinc-950 text-zinc-100 font-sans select-none">
      {/* 顶部专业控制栏 */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-zinc-800/80 bg-zinc-900/90 px-4 backdrop-blur-xl z-20">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors"
          >
            <ArrowLeft className="size-3.5" />
            <span>返回助手</span>
          </Link>
          <div className="h-4 w-px bg-zinc-800" />
          <div className="flex items-center gap-2">
            <span className="flex size-7 items-center justify-center rounded-lg bg-gradient-to-tr from-purple-600 to-indigo-600 text-white shadow-md shadow-purple-500/20">
              <Workflow className="size-4" />
            </span>
            <div>
              <span className="font-heading text-sm font-bold tracking-tight bg-gradient-to-r from-white via-zinc-200 to-zinc-400 bg-clip-text text-transparent">
                AI Workflow Studio
              </span>
              <span className="ml-2 rounded-full bg-purple-500/20 px-2 py-0.2 text-[10px] font-medium text-purple-300 border border-purple-500/30">
                DAG 编排引擎
              </span>
            </div>
          </div>
        </div>

        {/* 预置模板选择器 */}
        <div className="flex items-center gap-2">
          <div className="relative flex items-center rounded-xl border border-zinc-800 bg-zinc-950 px-2 py-1 text-xs">
            <span className="text-[11px] text-zinc-500 mr-2">当前工作流:</span>
            <select
              value={currentWorkflow?.id || ""}
              onChange={(e) => {
                const found = workflows.find((w) => w.id === e.target.value);
                if (found) selectWorkflow(found);
              }}
              className="bg-transparent text-xs font-semibold text-zinc-200 focus:outline-none cursor-pointer"
            >
              {workflows.map((w) => (
                <option
                  key={w.id}
                  value={w.id}
                  className="bg-zinc-900 text-zinc-200"
                >
                  {w.name} (v{w.version || "1.0"})
                </option>
              ))}
            </select>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={handleSaveWorkflow}
            disabled={isSaving || !currentWorkflow}
            className="gap-1.5 border-zinc-800 bg-zinc-900/80 text-xs text-zinc-300 hover:bg-zinc-800 hover:text-white"
          >
            {isSaving ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Save className="size-3.5" />
            )}
            保存
          </Button>

          <Button
            size="sm"
            onClick={handleRunWorkflow}
            disabled={isExecuting || !currentWorkflow}
            className="gap-1.5 bg-gradient-to-r from-purple-600 to-indigo-600 text-xs font-semibold text-white shadow-md shadow-purple-500/20 hover:from-purple-500 hover:to-indigo-500 active:scale-98 transition-all"
          >
            {isExecuting ? (
              <>
                <Loader2 className="size-3.5 animate-spin" />
                正在流式执行...
              </>
            ) : (
              <>
                <Play className="size-3.5 fill-current" />
                立即运行
              </>
            )}
          </Button>

          <ThemeToggle />
        </div>
      </header>

      {/* 主工作区：左侧 DAG 画布 + 右侧属性与执行控制台 */}
      <div className="flex flex-1 overflow-hidden relative">
        {/* DAG 交互式画布 */}
        <section
          aria-label="工作流 DAG 画布"
          className="flex-1 relative overflow-hidden bg-zinc-950 cursor-grab active:cursor-grabbing"
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
        >
          {/* 背景网格点阵 */}
          <div
            className="absolute inset-0 pointer-events-none opacity-20"
            style={{
              backgroundImage: `radial-gradient(circle, #6366f1 1px, transparent 1px)`,
              backgroundSize: "24px 24px",
              transform: `translate(${pan.x % 24}px, ${pan.y % 24}px)`,
            }}
          />

          {/* 画布浮动缩放控制面板 */}
          <div className="absolute bottom-4 left-4 z-10 flex items-center gap-1 rounded-xl border border-zinc-800/80 bg-zinc-900/90 p-1 backdrop-blur-md shadow-xl text-zinc-400">
            <button
              type="button"
              onClick={() => setZoom((z) => Math.min(1.8, z + 0.1))}
              className="flex size-7 items-center justify-center rounded-lg hover:bg-zinc-800 hover:text-white"
              title="放大"
            >
              +
            </button>
            <span className="px-1 text-[11px] font-mono text-zinc-300 font-medium">
              {Math.round(zoom * 100)}%
            </span>
            <button
              type="button"
              onClick={() => setZoom((z) => Math.max(0.4, z - 0.1))}
              className="flex size-7 items-center justify-center rounded-lg hover:bg-zinc-800 hover:text-white"
              title="缩小"
            >
              -
            </button>
            <button
              type="button"
              onClick={() => {
                setZoom(1);
                setPan({ x: 40, y: 40 });
              }}
              className="flex size-7 items-center justify-center rounded-lg hover:bg-zinc-800 hover:text-white text-xs"
              title="复位画布"
            >
              <RotateCcw className="size-3" />
            </button>
          </div>

          {/* 状态统计指示胶囊 */}
          <div className="absolute top-4 left-4 z-10 flex items-center gap-2 rounded-xl border border-zinc-800/80 bg-zinc-900/90 px-3 py-1.5 backdrop-blur-md shadow-xl text-xs">
            <span className="flex items-center gap-1.5 text-zinc-300 font-medium">
              <Layers className="size-3.5 text-purple-400" />
              {currentWorkflow?.nodes.length || 0} 个节点
            </span>
            <span className="text-zinc-600">/</span>
            <span className="flex items-center gap-1.5 text-zinc-300 font-medium">
              <GitBranch className="size-3.5 text-indigo-400" />
              {currentWorkflow?.edges.length || 0} 条依赖连线
            </span>
            {isExecuting && (
              <>
                <span className="text-zinc-600">/</span>
                <span className="flex items-center gap-1.5 text-purple-400 animate-pulse font-medium">
                  <Loader2 className="size-3 animate-spin" />
                  执行中
                </span>
              </>
            )}
          </div>

          {/* 渲染 DAG 节点与连线容器 */}
          <div
            className="absolute origin-top-left transition-transform duration-75 ease-out"
            style={{
              transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
            }}
          >
            {/* SVG 连线层 */}
            <svg
              role="img"
              aria-label="工作流连线图"
              className="absolute inset-0 overflow-visible pointer-events-none"
              style={{ width: "3000px", height: "2000px" }}
            >
              <title>工作流依赖关系图</title>
              <defs>
                <marker
                  id="arrow"
                  viewBox="0 0 10 10"
                  refX="6"
                  refY="5"
                  markerWidth="6"
                  markerHeight="6"
                  orient="auto-start-reverse"
                >
                  <path d="M 0 1 L 8 5 L 0 9 z" fill="#6366f1" />
                </marker>
                <marker
                  id="arrow-skipped"
                  viewBox="0 0 10 10"
                  refX="6"
                  refY="5"
                  markerWidth="6"
                  markerHeight="6"
                  orient="auto-start-reverse"
                >
                  <path d="M 0 1 L 8 5 L 0 9 z" fill="#52525b" />
                </marker>
              </defs>

              {currentWorkflow?.edges.map((edge) => {
                const source = currentWorkflow.nodes.find(
                  (n) => n.id === edge.sourceNodeId,
                );
                const target = currentWorkflow.nodes.find(
                  (n) => n.id === edge.targetNodeId,
                );
                if (!source || !target) return null;

                const sx = source.position.x + 200;
                const sy = source.position.y + 45;
                const tx = target.position.x;
                const ty = target.position.y + 45;
                const dx = Math.max(40, (tx - sx) * 0.5);

                const sourceStatus = nodeSnapshots[source.id]?.status;
                const isSkipped = sourceStatus === "SKIPPED";
                const isRunning = sourceStatus === "RUNNING";

                const pathData = `M ${sx} ${sy} C ${sx + dx} ${sy}, ${tx - dx} ${ty}, ${tx} ${ty}`;

                return (
                  <g key={edge.id}>
                    <path
                      d={pathData}
                      fill="none"
                      stroke={
                        isSkipped
                          ? "#3f3f46"
                          : isRunning
                            ? "#a855f7"
                            : "#6366f1"
                      }
                      strokeWidth={isRunning ? 2.5 : 2}
                      strokeDasharray={
                        isSkipped ? "4 4" : isRunning ? "6 6" : undefined
                      }
                      className={isRunning ? "animate-pulse" : ""}
                      markerEnd={
                        isSkipped ? "url(#arrow-skipped)" : "url(#arrow)"
                      }
                    />
                    {edge.label && (
                      <text
                        x={(sx + tx) / 2}
                        y={(sy + ty) / 2 - 8}
                        fill="#a1a1aa"
                        fontSize="10"
                        textAnchor="middle"
                        className="font-mono bg-zinc-950"
                      >
                        {edge.label}
                      </text>
                    )}
                  </g>
                );
              })}
            </svg>

            {/* 节点 DOM 卡片层 */}
            {currentWorkflow?.nodes.map((node) => {
              const meta = NODE_META[node.type] || NODE_META.LLM;
              const Icon = meta.icon;
              const snapshot = nodeSnapshots[node.id];
              const status = snapshot?.status || "PENDING";
              const isSelected = selectedNodeId === node.id;

              return (
                <button
                  type="button"
                  key={node.id}
                  onClick={() => setSelectedNodeId(node.id)}
                  style={{
                    transform: `translate(${node.position.x}px, ${node.position.y}px)`,
                    width: "200px",
                  }}
                  className={cn(
                    "absolute text-left cursor-pointer rounded-xl border p-3 transition-all duration-200 backdrop-blur-xl shadow-lg",
                    isSelected
                      ? "ring-2 ring-purple-500 shadow-purple-500/30"
                      : "hover:border-zinc-700 hover:scale-102",
                    status === "RUNNING" &&
                      "border-purple-500 bg-purple-950/40 animate-pulse ring-2 ring-purple-500/50 shadow-xl shadow-purple-500/25",
                    status === "COMPLETED" &&
                      "border-emerald-500/40 bg-zinc-900/90 shadow-emerald-500/10",
                    status === "SKIPPED" &&
                      "border-zinc-800 bg-zinc-950/60 opacity-40 grayscale",
                    status === "FAILED" &&
                      "border-rose-500 bg-rose-950/30 ring-2 ring-rose-500/40",
                    status === "PENDING" && "border-zinc-800/80 bg-zinc-900/80",
                  )}
                >
                  {/* 节点头部 */}
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-1.5 min-w-0">
                      <span
                        className={cn(
                          "flex size-5.5 items-center justify-center rounded-md",
                          meta.bg,
                          meta.color,
                        )}
                      >
                        <Icon className="size-3" />
                      </span>
                      <span className="truncate text-xs font-semibold text-zinc-100">
                        {node.name}
                      </span>
                    </div>

                    {/* 状态指示 Badge */}
                    {status === "RUNNING" && (
                      <Loader2 className="size-3.5 text-purple-400 animate-spin shrink-0" />
                    )}
                    {status === "COMPLETED" && (
                      <span className="flex size-4 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400">
                        <Check className="size-2.5" />
                      </span>
                    )}
                    {status === "SKIPPED" && (
                      <span className="text-[9px] font-mono text-zinc-500 bg-zinc-800 px-1 rounded">
                        跳过
                      </span>
                    )}
                    {status === "FAILED" && (
                      <XCircle className="size-3.5 text-rose-400 shrink-0" />
                    )}
                  </div>

                  {/* 节点核心摘要预览 */}
                  <div className="rounded-lg bg-black/40 p-1.5 text-[10px] font-mono text-zinc-400 truncate">
                    {node.type === "LLM" &&
                      String(node.config?.promptTemplate || "提示词模板")}
                    {node.type === "TOOL" &&
                      `工具: ${String(node.config?.toolName || "web_search")}`}
                    {node.type === "CONDITION" &&
                      String(node.config?.expression || "条件判断")}
                    {node.type === "INPUT" && "接收用户入参"}
                    {node.type === "OUTPUT" && "生成终稿产物"}
                  </div>

                  {/* 运行耗时与 Token 标识 */}
                  {snapshot?.durationMs !== undefined &&
                    status === "COMPLETED" && (
                      <div className="mt-2 flex items-center justify-between text-[9px] text-zinc-500 border-t border-white/5 pt-1.5">
                        <span>⏱️ {snapshot.durationMs}ms</span>
                        {snapshot.tokenUsage ? (
                          <span>🪙 {snapshot.tokenUsage} tok</span>
                        ) : null}
                      </div>
                    )}
                </button>
              );
            })}
          </div>
        </section>

        {/* 右侧边栏：节点属性面板 / 执行控制台切换 */}
        <aside className="w-96 shrink-0 border-l border-zinc-800/80 bg-zinc-900/95 backdrop-blur-2xl flex flex-col z-10 shadow-2xl">
          {/* Tab 导航 */}
          <div className="flex border-b border-zinc-800 px-3 pt-2 gap-1 text-xs">
            <button
              type="button"
              onClick={() => setActiveConsoleTab("form")}
              className={cn(
                "flex items-center gap-1.5 px-3 py-2 font-medium border-b-2 transition-colors",
                activeConsoleTab === "form"
                  ? "border-purple-500 text-purple-400"
                  : "border-transparent text-zinc-400 hover:text-zinc-200",
              )}
            >
              <Play className="size-3" />
              <span>运行输入</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveConsoleTab("timeline")}
              className={cn(
                "flex items-center gap-1.5 px-3 py-2 font-medium border-b-2 transition-colors",
                activeConsoleTab === "timeline"
                  ? "border-purple-500 text-purple-400"
                  : "border-transparent text-zinc-400 hover:text-zinc-200",
              )}
            >
              <Terminal className="size-3" />
              <span>执行日志</span>
              {isExecuting && (
                <span className="size-1.5 rounded-full bg-purple-400 animate-ping" />
              )}
            </button>
            <button
              type="button"
              onClick={() => setActiveConsoleTab("output")}
              className={cn(
                "flex items-center gap-1.5 px-3 py-2 font-medium border-b-2 transition-colors",
                activeConsoleTab === "output"
                  ? "border-purple-500 text-purple-400"
                  : "border-transparent text-zinc-400 hover:text-zinc-200",
              )}
            >
              <CheckCircle2 className="size-3" />
              <span>最终产物</span>
              {finalOutputText && (
                <span className="size-1.5 rounded-full bg-emerald-400" />
              )}
            </button>
            <button
              type="button"
              onClick={() => setActiveConsoleTab("history")}
              className={cn(
                "flex items-center gap-1.5 px-3 py-2 font-medium border-b-2 transition-colors",
                activeConsoleTab === "history"
                  ? "border-purple-500 text-purple-400"
                  : "border-transparent text-zinc-400 hover:text-zinc-200",
              )}
            >
              <History className="size-3" />
              <span>历史</span>
            </button>
          </div>

          {/* 属性与控制台内容容器 */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {/* Tab 1: 运行入参表单 */}
            {activeConsoleTab === "form" && (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-zinc-200 uppercase tracking-wider">
                    工作流入参配置
                  </h3>
                  {currentWorkflow?.defaultInputs && (
                    <button
                      type="button"
                      onClick={() =>
                        setExecutionInputs({ ...currentWorkflow.defaultInputs })
                      }
                      className="text-[11px] text-purple-400 hover:text-purple-300 transition-colors"
                    >
                      一键填入预置数据
                    </button>
                  )}
                </div>

                {currentWorkflow?.inputSchema?.map((field) => (
                  <div key={field.key} className="space-y-1.5">
                    <div className="text-xs font-semibold text-zinc-300 flex items-center justify-between">
                      <span>{field.label}</span>
                      {field.required && (
                        <span className="text-[10px] text-rose-400">必填</span>
                      )}
                    </div>
                    {field.type === "text" ? (
                      <textarea
                        rows={3}
                        value={String(executionInputs[field.key] ?? "")}
                        onChange={(e) =>
                          setExecutionInputs((prev) => ({
                            ...prev,
                            [field.key]: e.target.value,
                          }))
                        }
                        placeholder={field.placeholder}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-950 p-2 text-xs text-zinc-200 focus:border-purple-500 focus:outline-none"
                      />
                    ) : field.type === "select" ? (
                      <select
                        value={String(
                          executionInputs[field.key] ??
                            field.defaultValue ??
                            "",
                        )}
                        onChange={(e) =>
                          setExecutionInputs((prev) => ({
                            ...prev,
                            [field.key]: e.target.value,
                          }))
                        }
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-950 p-2 text-xs text-zinc-200 focus:border-purple-500 focus:outline-none"
                      >
                        {field.options?.map((opt) => (
                          <option key={opt} value={opt}>
                            {opt}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input
                        type="text"
                        value={String(executionInputs[field.key] ?? "")}
                        onChange={(e) =>
                          setExecutionInputs((prev) => ({
                            ...prev,
                            [field.key]: e.target.value,
                          }))
                        }
                        placeholder={field.placeholder}
                        className="w-full rounded-lg border border-zinc-800 bg-zinc-950 p-2 text-xs text-zinc-200 focus:border-purple-500 focus:outline-none"
                      />
                    )}
                  </div>
                ))}

                <Button
                  onClick={handleRunWorkflow}
                  disabled={isExecuting}
                  className="w-full gap-2 bg-gradient-to-r from-purple-600 to-indigo-600 py-2.5 text-xs font-semibold shadow-lg shadow-purple-500/25 hover:from-purple-500 hover:to-indigo-500"
                >
                  {isExecuting ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Play className="size-4 fill-current" />
                  )}
                  执行当前工作流
                </Button>
              </div>
            )}

            {/* Tab 2: 实时节点执行时间轴 */}
            {activeConsoleTab === "timeline" && (
              <div className="space-y-3">
                <div className="flex items-center justify-between text-xs text-zinc-400 border-b border-zinc-800 pb-2">
                  <span>节点执行追踪</span>
                  <div className="flex items-center gap-2 font-mono text-[11px]">
                    <span>⏱️ {executingMetrics.durationMs}ms</span>
                    <span>🪙 {executingMetrics.totalTokens} tok</span>
                  </div>
                </div>

                <div className="space-y-2">
                  {currentWorkflow?.nodes.map((node) => {
                    const snap = nodeSnapshots[node.id];
                    const status = snap?.status || "PENDING";
                    const meta = NODE_META[node.type] || NODE_META.LLM;

                    return (
                      <div
                        key={node.id}
                        className={cn(
                          "rounded-xl border p-2.5 text-xs transition-all",
                          status === "RUNNING" &&
                            "border-purple-500/60 bg-purple-950/20",
                          status === "COMPLETED" &&
                            "border-zinc-800 bg-zinc-950/60",
                          status === "SKIPPED" &&
                            "border-zinc-800/40 bg-zinc-950/30 opacity-50",
                          status === "FAILED" &&
                            "border-rose-500/60 bg-rose-950/20",
                          status === "PENDING" &&
                            "border-zinc-800/40 bg-zinc-950/40 text-zinc-500",
                        )}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-1.5 font-semibold text-zinc-200">
                            <meta.icon className={cn("size-3.5", meta.color)} />
                            <span>{node.name}</span>
                          </div>
                          <span
                            className={cn(
                              "rounded-md px-1.5 py-0.2 text-[10px] font-mono",
                              status === "COMPLETED" &&
                                "bg-emerald-500/15 text-emerald-400",
                              status === "RUNNING" &&
                                "bg-purple-500/20 text-purple-300 animate-pulse",
                              status === "SKIPPED" &&
                                "bg-zinc-800 text-zinc-400",
                              status === "FAILED" &&
                                "bg-rose-500/20 text-rose-400",
                            )}
                          >
                            {status}
                          </span>
                        </div>

                        {snap?.outputState !== undefined &&
                          snap?.outputState !== null && (
                            <div className="mt-2 rounded-lg bg-black/60 p-2 font-mono text-[11px] text-zinc-300 max-h-28 overflow-y-auto whitespace-pre-wrap select-text">
                              {typeof snap.outputState === "string"
                                ? snap.outputState
                                : JSON.stringify(snap.outputState, null, 2)}
                            </div>
                          )}

                        {snap?.skipReason && (
                          <div className="mt-1 text-[10px] text-amber-400/80 font-mono">
                            跳过原因: {snap.skipReason}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Tab 3: 最终产物输出 */}
            {activeConsoleTab === "output" && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-zinc-200 uppercase tracking-wider">
                    最终产物结果
                  </h3>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={handleCopyOutput}
                      className="h-7 gap-1 px-2 text-[11px] text-zinc-400 hover:text-white"
                    >
                      {copiedOutput ? (
                        <Check className="size-3 text-emerald-400" />
                      ) : (
                        <Copy className="size-3" />
                      )}
                      <span>{copiedOutput ? "已复制" : "复制"}</span>
                    </Button>
                  </div>
                </div>

                {finalOutputText ? (
                  <div className="rounded-xl border border-zinc-800 bg-zinc-950 p-3 shadow-inner">
                    <pre className="font-mono text-xs leading-relaxed text-zinc-200 whitespace-pre-wrap select-text">
                      {finalOutputText}
                    </pre>
                  </div>
                ) : (
                  <div className="py-12 text-center text-xs text-zinc-500 font-mono">
                    (执行完毕后在此展示最终产物)
                  </div>
                )}
              </div>
            )}

            {/* Tab 4: 历史记录 */}
            {activeConsoleTab === "history" && (
              <div className="space-y-2">
                <h3 className="text-xs font-bold text-zinc-200 uppercase tracking-wider mb-2">
                  历史执行快照
                </h3>
                {executionHistory.length === 0 ? (
                  <div className="py-8 text-center text-xs text-zinc-500">
                    暂无历史执行记录
                  </div>
                ) : (
                  executionHistory.map((rec) => (
                    <div
                      key={rec.executionId}
                      className="rounded-xl border border-zinc-800/80 bg-zinc-950/80 p-2.5 text-xs hover:border-zinc-700 transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-[10px] text-zinc-400">
                          {new Date(rec.startTime).toLocaleTimeString()}
                        </span>
                        <span
                          className={cn(
                            "rounded-md px-1.5 py-0.2 text-[10px] font-mono",
                            rec.status === "COMPLETED"
                              ? "bg-emerald-500/15 text-emerald-400"
                              : "bg-rose-500/20 text-rose-400",
                          )}
                        >
                          {rec.status}
                        </span>
                      </div>
                      <div className="mt-1 flex items-center justify-between text-[10px] text-zinc-500">
                        <span>⏱️ {rec.durationMs || 0}ms</span>
                        <span>🪙 {rec.totalTokens || 0} tok</span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {/* 选中节点属性抽屉预览 */}
            {selectedNode && (
              <div className="mt-4 border-t border-zinc-800 pt-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <Settings className="size-3.5 text-purple-400" />
                    <span className="text-xs font-bold text-zinc-200">
                      节点属性配置: {selectedNode.name}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedNodeId(null)}
                    className="text-zinc-500 hover:text-zinc-300"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>

                <div className="rounded-xl border border-zinc-800/80 bg-zinc-950 p-2.5 space-y-2 text-xs">
                  <div>
                    <div className="text-[10px] text-zinc-500 font-mono">
                      节点 ID
                    </div>
                    <div className="font-mono text-zinc-300">
                      {selectedNode.id}
                    </div>
                  </div>

                  <div>
                    <div className="text-[10px] text-zinc-500 font-mono">
                      节点类型
                    </div>
                    <div className="font-semibold text-purple-400">
                      {selectedNode.type}
                    </div>
                  </div>

                  {selectedNode.type === "LLM" && (
                    <div className="space-y-1">
                      <div className="text-[10px] text-zinc-500 font-mono">
                        提示词模板
                      </div>
                      <textarea
                        rows={3}
                        value={String(
                          selectedNode.config?.promptTemplate || "",
                        )}
                        onChange={(e) => {
                          const val = e.target.value;
                          setCurrentWorkflow((prev) => {
                            if (!prev) return prev;
                            return {
                              ...prev,
                              nodes: prev.nodes.map((n) =>
                                n.id === selectedNode.id
                                  ? {
                                      ...n,
                                      config: {
                                        ...n.config,
                                        promptTemplate: val,
                                      },
                                    }
                                  : n,
                              ),
                            };
                          });
                        }}
                        className="w-full rounded-md border border-zinc-800 bg-black/60 p-2 font-mono text-[11px] text-zinc-200 focus:outline-none"
                      />
                    </div>
                  )}

                  {selectedNode.type === "CONDITION" && (
                    <div className="space-y-1">
                      <div className="text-[10px] text-zinc-500 font-mono">
                        判断表达式
                      </div>
                      <input
                        type="text"
                        value={String(selectedNode.config?.expression || "")}
                        onChange={(e) => {
                          const val = e.target.value;
                          setCurrentWorkflow((prev) => {
                            if (!prev) return prev;
                            return {
                              ...prev,
                              nodes: prev.nodes.map((n) =>
                                n.id === selectedNode.id
                                  ? {
                                      ...n,
                                      config: { ...n.config, expression: val },
                                    }
                                  : n,
                              ),
                            };
                          });
                        }}
                        className="w-full rounded-md border border-zinc-800 bg-black/60 p-2 font-mono text-[11px] text-zinc-200 focus:outline-none"
                      />
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
