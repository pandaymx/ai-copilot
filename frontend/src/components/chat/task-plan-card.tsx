"use client";

import { useState } from "react";
import type { TaskPlanState, TaskStepItem } from "@/hooks/useSpringAiStream";
import {
  CheckCircle2,
  CircleDashed,
  AlertTriangle,
  RefreshCw,
  Clock,
  ChevronDown,
  ChevronRight,
  Workflow,
  Wrench,
  Sparkles,
  Terminal,
  XCircle,
} from "lucide-react";

interface TaskPlanCardProps {
  plan: TaskPlanState;
}

export function TaskPlanCard({ plan }: TaskPlanCardProps) {
  const [isExpanded, setIsExpanded] = useState(true);
  const [openStepIds, setOpenStepIds] = useState<Record<number, boolean>>({});

  const toggleStep = (stepId: number) => {
    setOpenStepIds((prev) => ({ ...prev, [stepId]: !prev[stepId] }));
  };

  const totalSteps = plan.steps?.length || plan.totalSteps || 1;
  const completedCount =
    plan.steps?.filter((s) => s.status === "COMPLETED" || s.status === "SKIPPED")
      .length || 0;
  const progressPercent = Math.min(
    100,
    Math.round((completedCount / totalSteps) * 100),
  );

  const isExecuting = plan.status === "EXECUTING" || plan.status === "PLANNING" || plan.status === "REPLANNING";
  const isCompleted = plan.status === "COMPLETED";
  const isCancelled = plan.status === "CANCELLED";

  return (
    <div className="my-3 overflow-hidden rounded-2xl border border-blue-500/20 bg-gradient-to-b from-blue-950/20 via-background/80 to-background/95 backdrop-blur-md shadow-lg shadow-blue-500/5 transition-all">
      {/* Header Bar */}
      <div className="flex items-center justify-between p-3.5 border-b border-border/40 bg-muted/20">
        <div className="flex items-center gap-2.5">
          <div className="relative flex h-8 w-8 items-center justify-center rounded-lg bg-blue-500/10 text-blue-500 dark:text-blue-400">
            <Workflow className="h-4 w-4" />
            {isExecuting && (
              <span className="absolute -top-0.5 -right-0.5 flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500" />
              </span>
            )}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-blue-500 dark:text-blue-400">
                ReAct 多步任务规划
              </span>
              <span
                className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${
                  isCompleted
                    ? "bg-emerald-500/10 text-emerald-500 border-emerald-500/20"
                    : isCancelled
                      ? "bg-amber-500/10 text-amber-500 border-amber-500/20"
                      : "bg-blue-500/10 text-blue-500 border-blue-500/20 animate-pulse"
                }`}
              >
                {isCompleted
                  ? "已全部完成"
                  : isCancelled
                    ? "已取消"
                    : plan.status === "REPLANNING"
                      ? "自适应重规划中..."
                      : `执行中 (${completedCount}/${totalSteps})`}
              </span>
            </div>
            <h4 className="text-sm font-semibold text-foreground truncate max-w-[280px] sm:max-w-md">
              {plan.title || plan.goal}
            </h4>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setIsExpanded(!isExpanded)}
          className="flex h-7 w-7 items-center justify-center rounded-lg hover:bg-muted/60 text-muted-foreground transition-colors"
          title={isExpanded ? "收起规划面板" : "展开规划面板"}
        >
          {isExpanded ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          )}
        </button>
      </div>

      {/* Progress Bar */}
      <div className="h-1 w-full bg-muted/40 overflow-hidden">
        <div
          className="h-full bg-gradient-to-r from-blue-500 via-indigo-500 to-emerald-500 transition-all duration-500 ease-out"
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      {/* Collapsible Steps Timeline */}
      {isExpanded && (
        <div className="p-3.5 space-y-3">
          {plan.steps?.map((step) => {
            const isStepOpen = openStepIds[step.stepId] ?? (step.status === "RUNNING" || step.status === "REPLANNING");

            return (
              <div
                key={step.stepId}
                className={`rounded-xl border transition-all ${
                  step.status === "RUNNING"
                    ? "border-blue-500/40 bg-blue-500/5 shadow-sm"
                    : step.status === "REPLANNING"
                      ? "border-amber-500/40 bg-amber-500/5"
                      : step.status === "COMPLETED"
                        ? "border-emerald-500/20 bg-muted/10"
                        : step.status === "FAILED"
                          ? "border-rose-500/30 bg-rose-500/5"
                          : "border-border/40 bg-muted/5 opacity-80"
                }`}
              >
                {/* Step Header */}
                <button
                  type="button"
                  className="flex w-full items-center justify-between p-2.5 cursor-pointer select-none text-left bg-transparent border-0"
                  onClick={() => toggleStep(step.stepId)}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <StepStatusIcon status={step.status} />
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono font-medium text-muted-foreground">
                          Step {step.stepId}
                        </span>
                        <span className="text-xs font-semibold text-foreground truncate">
                          {step.title}
                        </span>
                        {step.toolName && (
                          <span className="inline-flex items-center gap-1 rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground">
                            <Wrench className="h-2.5 w-2.5" />
                            {step.toolName}
                          </span>
                        )}
                        {(step.replanCount ?? 0) > 0 && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-1.5 py-0.2 text-[9px] font-medium text-amber-500 border border-amber-500/20">
                            <RefreshCw className="h-2 w-2" /> 重试×{step.replanCount}
                          </span>
                        )}
                      </div>
                      {step.description && (
                        <p className="text-[11px] text-muted-foreground truncate max-w-sm sm:max-w-md">
                          {step.description}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-1 text-muted-foreground">
                    {isStepOpen ? (
                      <ChevronDown className="h-3.5 w-3.5" />
                    ) : (
                      <ChevronRight className="h-3.5 w-3.5" />
                    )}
                  </div>
                </button>

                {/* Step Details */}
                {isStepOpen && (
                  <div className="px-3 pb-3 pt-1 space-y-2 border-t border-border/30 text-xs">
                    {/* Thought / Reason */}
                    {step.thought && (
                      <div className="rounded-lg bg-blue-500/5 p-2.5 border border-blue-500/15">
                        <div className="flex items-center gap-1 text-[11px] font-medium text-blue-500 mb-1">
                          <Sparkles className="h-3 w-3" />
                          <span>推理思考 (Thought)</span>
                        </div>
                        <p className="text-[11px] text-foreground/80 leading-relaxed whitespace-pre-wrap">
                          {step.thought}
                        </p>
                      </div>
                    )}

                    {/* Action Arguments */}
                    {step.actionArgs && step.actionArgs !== "{}" && (
                      <div className="rounded-lg bg-muted/40 p-2 border border-border/40 font-mono text-[11px]">
                        <div className="flex items-center gap-1 text-[10px] text-muted-foreground mb-1">
                          <Terminal className="h-2.5 w-2.5" />
                          <span>工具参数 (Action Args)</span>
                        </div>
                        <pre className="text-foreground/90 overflow-x-auto text-[10px]">
                          {step.actionArgs}
                        </pre>
                      </div>
                    )}

                    {/* Observation Result */}
                    {step.observation && (
                      <div className="rounded-lg bg-muted/20 p-2.5 border border-border/40">
                        <div className="flex items-center gap-1 text-[11px] font-medium text-muted-foreground mb-1">
                          <Clock className="h-3 w-3" />
                          <span>观察产物 (Observation)</span>
                        </div>
                        <p className="text-[11px] text-muted-foreground/90 leading-relaxed line-clamp-6 whitespace-pre-wrap font-mono">
                          {step.observation}
                        </p>
                      </div>
                    )}

                    {/* Error Message */}
                    {step.errorMessage && (
                      <div className="rounded-lg bg-rose-500/10 p-2 border border-rose-500/20 text-rose-500 text-[11px] flex items-start gap-1.5">
                        <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                        <span>{step.errorMessage}</span>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function StepStatusIcon({ status }: { status: TaskStepItem["status"] }) {
  switch (status) {
    case "COMPLETED":
      return <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />;
    case "RUNNING":
      return <RefreshCw className="h-4 w-4 text-blue-500 animate-spin shrink-0" />;
    case "REPLANNING":
      return <RefreshCw className="h-4 w-4 text-amber-500 animate-spin shrink-0" />;
    case "FAILED":
      return <AlertTriangle className="h-4 w-4 text-rose-500 shrink-0" />;
    case "SKIPPED":
      return <XCircle className="h-4 w-4 text-muted-foreground shrink-0" />;
    default:
      return <CircleDashed className="h-4 w-4 text-muted-foreground/60 shrink-0" />;
  }
}
