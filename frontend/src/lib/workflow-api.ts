"use client";

export interface WorkflowInputField {
  key: string;
  label: string;
  type: "string" | "text" | "number" | "boolean" | "select";
  defaultValue?: string;
  placeholder?: string;
  options?: string[];
  required?: boolean;
}

export type WorkflowNodeType =
  | "INPUT"
  | "LLM"
  | "TOOL"
  | "CONDITION"
  | "PARALLEL"
  | "OUTPUT";

export type WorkflowNodeStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "SKIPPED";

export interface WorkflowNode {
  id: string;
  name: string;
  type: WorkflowNodeType;
  config: Record<string, unknown>;
  position: { x: number; y: number };
}

export interface WorkflowEdge {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  sourceHandle?: string; // "true" | "false" | "default" | "out"
  targetHandle?: string;
  label?: string;
}

export interface WorkflowDefinition {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  version?: string;
  inputSchema?: WorkflowInputField[];
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  defaultInputs?: Record<string, unknown>;
  createdAt?: number;
  updatedAt?: number;
}

export interface WorkflowEvent {
  type:
    | "workflow_started"
    | "node_started"
    | "node_chunk"
    | "node_finished"
    | "node_skipped"
    | "node_failed"
    | "workflow_completed"
    | "workflow_failed";
  executionId?: string;
  workflowId?: string;
  nodeId?: string;
  nodeName?: string;
  nodeType?: string;
  delta?: string;
  output?: unknown;
  error?: string;
  skipReason?: string;
  durationMs?: number;
  tokenUsage?: number;
  finalOutputs?: Record<string, unknown>;
}

export interface NodeExecutionSnapshot {
  nodeId: string;
  nodeName: string;
  nodeType: WorkflowNodeType;
  status: WorkflowNodeStatus;
  inputState?: unknown;
  outputState?: unknown;
  error?: string;
  skipReason?: string;
  durationMs?: number;
  tokenUsage?: number;
}

export interface WorkflowExecutionRecord {
  executionId: string;
  workflowId: string;
  workflowName: string;
  status: "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
  startTime: number;
  endTime?: number;
  durationMs?: number;
  totalTokens?: number;
  inputs?: Record<string, unknown>;
  outputs?: Record<string, unknown>;
  error?: string;
  nodeSnapshots?: Record<string, NodeExecutionSnapshot>;
}

// -------------------------------------------------------------
// API Client Functions
// -------------------------------------------------------------

export async function fetchWorkflows(): Promise<WorkflowDefinition[]> {
  const res = await fetch("/api/workflows");
  if (!res.ok) throw new Error(`获取工作流列表失败: ${res.statusText}`);
  return res.json();
}

export async function fetchWorkflow(id: string): Promise<WorkflowDefinition> {
  const res = await fetch(`/api/workflows/${id}`);
  if (!res.ok) throw new Error(`获取工作流详情失败: ${res.statusText}`);
  return res.json();
}

export async function saveWorkflow(
  def: WorkflowDefinition,
): Promise<WorkflowDefinition> {
  const res = await fetch("/api/workflows", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(def),
  });
  if (!res.ok) throw new Error(`保存工作流失败: ${res.statusText}`);
  return res.json();
}

export async function deleteWorkflow(id: string): Promise<boolean> {
  const res = await fetch(`/api/workflows/${id}`, {
    method: "DELETE",
  });
  return res.ok;
}

export async function fetchWorkflowExecutions(
  workflowId?: string,
): Promise<WorkflowExecutionRecord[]> {
  const url = workflowId
    ? `/api/workflows/executions?workflowId=${encodeURIComponent(workflowId)}`
    : "/api/workflows/executions";
  const res = await fetch(url);
  if (!res.ok) throw new Error(`获取执行记录失败: ${res.statusText}`);
  return res.json();
}

/**
 * 通过 SSE 流式执行工作流并分发事件。
 */
export async function executeWorkflowStream(
  workflowId: string,
  inputs: Record<string, unknown>,
  onEvent: (event: WorkflowEvent) => void,
  onError?: (err: Error) => void,
  onComplete?: () => void,
): Promise<void> {
  try {
    const res = await fetch(`/api/workflows/${workflowId}/execute`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify(inputs),
    });

    if (!res.ok) {
      throw new Error(`执行失败 (${res.status}): ${res.statusText}`);
    }

    if (!res.body) {
      throw new Error("响应体为空，无法读取 SSE 流");
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith(":")) continue;

        if (trimmed.startsWith("data:")) {
          const jsonStr = trimmed.slice(5).trim();
          if (!jsonStr) continue;
          try {
            const event: WorkflowEvent = JSON.parse(jsonStr);
            onEvent(event);
          } catch (e) {
            console.error("解析工作流 SSE 帧失败:", jsonStr, e);
          }
        }
      }
    }

    if (buffer.trim().startsWith("data:")) {
      try {
        const event = JSON.parse(buffer.trim().slice(5).trim());
        onEvent(event);
      } catch {}
    }

    onComplete?.();
  } catch (err: unknown) {
    console.error("执行工作流异常:", err);
    onError?.(err instanceof Error ? err : new Error(String(err)));
  }
}
