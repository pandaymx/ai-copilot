"use client";

import { fetchEventSource } from "@microsoft/fetch-event-source";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import type { CompressionMetadata, DocumentCitationItem } from "@/lib/api";

export interface SpringAiStreamMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

export function useStreamData(store: StreamStore): StreamData {
  return useSyncExternalStore(
    store.subscribe,
    store.getSnapshot,
    store.getSnapshot,
  );
}

/** 单个工具调用的状态（前端侧，用于渲染工具卡片）。 */
export interface ToolCallItem {
  callId: string;
  name: string;
  /** 工具入参，已序列化的 JSON 字符串（渲染时再做安全解析展示）。 */
  arguments: string;
  /** 思考过程 / 推理逻辑（由 AugmentedToolCallback 注入并推送）。 */
  innerThought?: string;
  /** 工具返回结果，已序列化的 JSON 字符串；status 为 calling 时为空。 */
  result?: string;
  status: "calling" | "success" | "error";
}

export interface UsageInfo {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCostRmb?: number;
  monthlyUsed?: number;
  monthlyQuota?: number;
  monthlyPercent?: number;
}

export interface UseSpringAiStreamOptions {
  /** 后端流式接口地址，默认复用 Spring AI 的 SSE 端点。 */
  endpoint?: string;
  /** 自定义请求头，会与 Accept: text/event-stream 合并。 */
  headers?: Record<string, string>;
  /**
   * 自定义请求体构造，便于适配不同模型的入参格式。
   * 历史消息由调用方通过 extraBody 传入，hook 不维护内部历史。
   */
  buildBody?: (input: string, extraBody?: Record<string, unknown>) => unknown;
  /**
   * 从单个 SSE data 字段（已剥离前缀、保留内部空白）中解析出增量文本。
   * 返回 null 表示无有效内容（如 [DONE] 或心跳）。
   * JSON 解析失败时应返回 null，不要回退为原文追加，以免半截 JSON 造成乱码。
   */
  parseChunk?: (data: string) => string | null;
  /** 在收到后端返回的会话 ID 时回调 */
  onConversationId?: (conversationId: string) => void;
  /** 在收到后端识别的意图与意图中文标签时回调 */
  onIntent?: (intent: string, intentLabel: string) => void;
  /** 收到 Reasoning/Thinking 思考过程增量时的回调 */
  onReasoning?: (reasoningDelta: string) => void;
  /** 收到 Token 用量统计时的回调 */
  onUsage?: (usage: UsageInfo) => void;
  /** 收到 artifact 帧（可渲染产物）时的回调 */
  onArtifact?: (item: ArtifactItem) => void;
  /** 收到 task_plan 帧（多步任务规划总览）时的回调 */
  onTaskPlan?: (plan: TaskPlanState) => void;
  /** 收到 task_step 帧（多步任务单步执行更新）时的回调 */
  onTaskStep?: (step: TaskStepItem) => void;
  /** 收到 tool_call 帧（工具开始执行）时的回调 */
  onToolCall?: (item: ToolCallItem) => void;
  /** 收到 tool_result 帧（工具执行完成/失败）时的回调 */
  onToolResult?: (item: ToolCallItem) => void;
  /** 收到 context_compression 帧（上下文智能压缩元数据）时的回调 */
  onContextCompression?: (metadata: CompressionMetadata) => void;
  /** 收到文档对话精准引用时的回调 */
  onCitations?: (citations: DocumentCitationItem[]) => void;
  /** 流完整结束后回调（成功完成或异常均触发），参数为最终累计文本、思考过程与 Token 用量。 */
  onFinish?: (
    finalContent: string,
    finalThinking?: string,
    finalUsage?: UsageInfo | null,
  ) => void;
}

export interface UseSpringAiStreamResult {
  /** 当前已累计的助手回复文本。 */
  content: string;
  /** 当前累计的思考过程文本。 */
  thinking: string;
  /** 当前统计的 Usage 信息。 */
  usage: UsageInfo | null;
  /** 是否正在流式接收。 */
  loading: boolean;
  /** 最近一次错误信息。 */
  error: Error | null;
  /** 发送一条消息并开始流式接收。 */
  send: (input: string, extraBody?: Record<string, unknown>) => void;
  /** 中断当前流。 */
  stop: () => void;
  /** 清空已接收内容与历史。 */
  reset: () => void;
  /** 高频流数据 Store，供 LiveMessage 订阅以隔离全屏重渲染 */
  streamStore: StreamStore;
}

export interface ArtifactItem {
  artifactId: string;
  artifactType: "image" | "code" | "html" | "svg" | string;
  title?: string;
  content?: string;
  mimeType?: string;
  language?: string;
  status?: "processing" | "complete" | "drafting" | "streaming" | "final";
}

export interface TaskStepItem {
  stepId: number;
  title: string;
  description: string;
  toolName?: string;
  expectedOutput?: string;
  thought?: string;
  actionArgs?: string;
  observation?: string;
  status:
    | "PENDING"
    | "RUNNING"
    | "COMPLETED"
    | "FAILED"
    | "REPLANNING"
    | "SKIPPED";
  replanCount?: number;
  errorMessage?: string;
}

export interface TaskPlanState {
  planId: string;
  title: string;
  goal: string;
  status:
    | "PLANNING"
    | "EXECUTING"
    | "COMPLETED"
    | "FAILED"
    | "REPLANNING"
    | "CANCELLED";
  currentStep: number;
  totalSteps: number;
  steps: TaskStepItem[];
  summary?: string;
}

export interface StreamData {
  content: string;
  thinking: string;
  reasoningDurationMs?: number;
  usage: UsageInfo | null;
  /** 工具调用列表，以 callId 为唯一 key（Map 结构用普通对象表达以保证快照不可变）。 */
  toolCalls: Record<string, ToolCallItem>;
  /** 可渲染产物列表，以 artifactId 为唯一 key。 */
  artifacts: Record<string, ArtifactItem>;
  /** 结构化多步任务规划状态（若当前任务开启了 ReAct / Planning 模式）。 */
  taskPlan?: TaskPlanState | null;
  /** 智能上下文压缩元数据（若触发了历史消息摘要压缩）。 */
  contextCompression?: CompressionMetadata | null;
  /** 文档对话精准引用列表（若开启了文档对话模式）。 */
  citations?: DocumentCitationItem[];
}

export class StreamStore {
  private data: StreamData = {
    content: "",
    thinking: "",
    usage: null,
    toolCalls: {},
    artifacts: {},
    taskPlan: null,
    contextCompression: null,
    citations: [],
  };
  private listeners = new Set<() => void>();

  getSnapshot = (): StreamData => {
    return this.data;
  };

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  update(
    content: string,
    thinking: string,
    usage: StreamData["usage"],
    reasoningDurationMs?: number,
  ) {
    this.data = { ...this.data, content, thinking, usage, reasoningDurationMs };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 更新文档对话精准引用列表 */
  updateCitations(citations: DocumentCitationItem[]) {
    this.data = { ...this.data, citations };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 更新上下文压缩元数据 */
  updateContextCompression(meta: CompressionMetadata) {
    this.data = { ...this.data, contextCompression: meta };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 更新多步任务计划总览 */
  updateTaskPlan(plan: TaskPlanState) {
    this.data = { ...this.data, taskPlan: plan };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 增量更新多步任务单一步骤 */
  updateTaskStep(step: TaskStepItem) {
    if (!this.data.taskPlan) return;
    const currentSteps = [...this.data.taskPlan.steps];
    const idx = currentSteps.findIndex((s) => s.stepId === step.stepId);
    if (idx >= 0) {
      currentSteps[idx] = { ...currentSteps[idx], ...step };
    } else {
      currentSteps.push(step);
    }
    const updatedPlan: TaskPlanState = {
      ...this.data.taskPlan,
      steps: currentSteps,
      currentStep: step.stepId,
    };
    this.data = { ...this.data, taskPlan: updatedPlan };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 以 callId 为 key 增量更新某个工具调用项。 */
  updateToolCall(callId: string, patch: Partial<ToolCallItem>) {
    const prev = this.data.toolCalls[callId] ?? {
      callId,
      name: "",
      arguments: "",
      status: "calling" as const,
    };
    const next = { ...prev, ...patch, callId };
    this.data = {
      ...this.data,
      toolCalls: { ...this.data.toolCalls, [callId]: next },
    };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 以 artifactId 为 key 增量更新某个产物项。 */
  updateArtifact(artifactId: string, patch: Partial<ArtifactItem>) {
    const prev = this.data.artifacts[artifactId] ?? {
      artifactId,
      artifactType: "image",
      content: "",
      status: "processing" as const,
    };
    const next = { ...prev, ...patch, artifactId };
    this.data = {
      ...this.data,
      artifacts: { ...this.data.artifacts, [artifactId]: next },
    };
    for (const listener of this.listeners) {
      listener();
    }
  }

  reset() {
    this.data = {
      content: "",
      thinking: "",
      usage: null,
      toolCalls: {},
      artifacts: {},
      taskPlan: null,
      contextCompression: null,
      citations: [],
    };
    for (const listener of this.listeners) {
      listener();
    }
  }
}

const DEFAULT_ENDPOINT = "/api/chat/stream";

/**
 * 判断字符串是否为"由于网络截断导致的未完成结构化 JSON SSE 事件"。
 * 仅当以 { 或 [ 开头、且包含标准 API 键名模式（如 "type":, "content":, "choices": 等）、但未能成功解析时，
 * 才判定为半截 JSON；反之，若只是以 { 开头的普通代码或文本（如 { foo: bar }），回退为增量文本返回，防止吞字。
 */
function looksLikeIncompleteJson(data: string): boolean {
  const trimmed = data.trimStart();
  const head = trimmed[0];
  if (head !== "{" && head !== "[") return false;
  return /"?(type|content|result|choices|error|reasoning|usage)"?\s*:/i.test(
    trimmed,
  );
}

function defaultParseChunk(data: string): string | null {
  if (!data || data === "[DONE]") return null;
  try {
    const parsed = JSON.parse(data);
    if (typeof parsed === "string") return parsed;

    if (parsed?.type === "conversation" || parsed?.type === "done") {
      return null;
    }

    // 业务级错误帧（type:"error" / 携带 error 字段）不应作为正文文本追加，
    // 交由调用方 onmessage 统一通过 setError 处理，以触发错误卡片与重试联动。
    if (parsed?.type === "error" || parsed?.error) {
      return null;
    }

    const nested =
      parsed?.content ??
      parsed?.result?.output?.text ??
      parsed?.choices?.[0]?.delta?.content ??
      parsed?.choices?.[0]?.message?.content ??
      parsed?.text;
    return typeof nested === "string" ? nested : null;
  } catch {
    if (looksLikeIncompleteJson(data)) return null;
    return data;
  }
}

function defaultBuildBody(input: string, extraBody?: Record<string, unknown>) {
  return { message: input, ...extraBody };
}

/**
 * 与 Spring AI 响应式（WebFlux SSE）流式接口配合的 Hook。
 * 基于 @microsoft/fetch-event-source 进行标准 SSE 分帧，
 * 既能正确处理多行 data 拼接，也避免手动解析流时的分包/空白被吞问题。
 */
export function useSpringAiStream(
  options: UseSpringAiStreamOptions = {},
): UseSpringAiStreamResult {
  const {
    endpoint = DEFAULT_ENDPOINT,
    headers,
    buildBody = defaultBuildBody,
    parseChunk = defaultParseChunk,
    onConversationId,
    onIntent,
    onReasoning,
    onUsage,
    onArtifact,
    onTaskPlan,
    onTaskStep,
    onToolCall,
    onToolResult,
    onContextCompression,
    onCitations,
    onFinish,
  } = options;

  // 回调 ref：流一旦启动就会跨多个渲染周期运行，必须用 ref 避免捕获旧闭包，
  // 否则 onFinish 等回调会读到调用 send 瞬间的过期状态（如 activeId=null）。
  const onConversationIdRef = useRef(onConversationId);
  const onIntentRef = useRef(onIntent);
  const onReasoningRef = useRef(onReasoning);
  const onUsageRef = useRef(onUsage);
  const onArtifactRef = useRef(onArtifact);
  const onTaskPlanRef = useRef(onTaskPlan);
  const onTaskStepRef = useRef(onTaskStep);
  const onToolCallRef = useRef(onToolCall);
  const onToolResultRef = useRef(onToolResult);
  const onContextCompressionRef = useRef(onContextCompression);
  const onCitationsRef = useRef(onCitations);
  const onFinishRef = useRef(onFinish);
  useEffect(() => {
    onConversationIdRef.current = onConversationId;
    onIntentRef.current = onIntent;
    onReasoningRef.current = onReasoning;
    onUsageRef.current = onUsage;
    onArtifactRef.current = onArtifact;
    onTaskPlanRef.current = onTaskPlan;
    onTaskStepRef.current = onTaskStep;
    onToolCallRef.current = onToolCall;
    onToolResultRef.current = onToolResult;
    onContextCompressionRef.current = onContextCompression;
    onCitationsRef.current = onCitations;
    onFinishRef.current = onFinish;
  }, [
    onConversationId,
    onIntent,
    onReasoning,
    onUsage,
    onArtifact,
    onTaskPlan,
    onTaskStep,
    onToolCall,
    onToolResult,
    onContextCompression,
    onCitations,
    onFinish,
  ]);

  const [streamData, setStreamData] = useState({ content: "", thinking: "" });
  const [usage, setUsage] = useState<UsageInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const streamStoreRef = useRef(new StreamStore());
  const contentRef = useRef("");
  const thinkingRef = useRef("");
  const reasoningStartTimeRef = useRef<number | null>(null);
  const reasoningDurationMsRef = useRef<number | null>(null);
  const usageRef = useRef<UsageInfo | null>(null);
  const rafRef = useRef<number | null>(null);

  const flushState = useCallback(() => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    }
    streamStoreRef.current.update(
      contentRef.current,
      thinkingRef.current,
      usageRef.current,
      reasoningDurationMsRef.current ?? undefined,
    );
    setStreamData({
      content: contentRef.current,
      thinking: thinkingRef.current,
    });
  }, []);

  const scheduleUpdate = useCallback(() => {
    if (rafRef.current !== null) return;
    // 用 microtask 批处理增量刷新，而非 requestAnimationFrame：
    // headless/后台标签页下 rAF 会被节流甚至暂停，导致流式内容无法刷新到
    // streamStore（进而气泡内容永远为空）。microtask 在所有环境下都会执行，
    // 同时仍能把同一轮同步 deltas 合并到一次刷新。
    rafRef.current = 1 as unknown as number;
    queueMicrotask(() => {
      rafRef.current = null;
      streamStoreRef.current.update(
        contentRef.current,
        thinkingRef.current,
        usageRef.current,
        reasoningDurationMsRef.current ?? undefined,
      );
      setStreamData({
        content: contentRef.current,
        thinking: thinkingRef.current,
      });
    });
  }, []);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    }
    contentRef.current = "";
    thinkingRef.current = "";
    reasoningStartTimeRef.current = null;
    reasoningDurationMsRef.current = null;
    usageRef.current = null;
    streamStoreRef.current.reset();
    setStreamData({ content: "", thinking: "" });
    setUsage(null);
    setLoading(false);
    setError(null);
  }, []);

  const stop = useCallback(() => {
    if (abortRef.current) {
      const currentContent = contentRef.current;
      const currentThinking = thinkingRef.current;
      const currentUsage = usageRef.current;
      abortRef.current.abort();
      abortRef.current = null;
      flushState();
      setLoading(false);
      onFinishRef.current?.(currentContent, currentThinking, currentUsage);
    }
  }, [flushState]);

  const send = useCallback(
    (input: string, extraBody?: Record<string, unknown>) => {
      const message = input.trim();
      if (!message || loading) return;

      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
      contentRef.current = "";
      thinkingRef.current = "";
      reasoningStartTimeRef.current = null;
      reasoningDurationMsRef.current = null;
      usageRef.current = null;
      streamStoreRef.current.reset();
      setStreamData({ content: "", thinking: "" });
      setUsage(null);
      setError(null);
      setLoading(true);

      const run = async () => {
        try {
          await fetchEventSource(endpoint, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Accept: "text/event-stream",
              ...headers,
            },
            body: JSON.stringify(buildBody(message, extraBody)),

            signal: controller.signal,
            openWhenHidden: true,
            onmessage(ev) {
              if (!ev.data) return;
              try {
                const parsed = JSON.parse(ev.data);
                if (parsed?.type === "conversation") {
                  if (parsed.conversationId) {
                    onConversationIdRef.current?.(parsed.conversationId);
                  }
                  if (parsed.intent && parsed.intentLabel) {
                    onIntentRef.current?.(parsed.intent, parsed.intentLabel);
                  }
                  return;
                }
                if (
                  parsed?.type === "citations" ||
                  (Array.isArray(parsed?.citations) &&
                    parsed.citations.length > 0)
                ) {
                  const citationsList = Array.isArray(parsed?.citations)
                    ? parsed.citations
                    : Array.isArray(parsed?.data)
                      ? parsed.data
                      : [];
                  if (citationsList.length > 0) {
                    streamStoreRef.current.updateCitations(citationsList);
                    onCitationsRef.current?.(citationsList);
                  }
                  return;
                }
                if (parsed?.type === "context_compression") {
                  try {
                    const rawContent = parsed.content ?? parsed;
                    const meta: CompressionMetadata =
                      typeof rawContent === "string"
                        ? JSON.parse(rawContent)
                        : rawContent;
                    streamStoreRef.current.updateContextCompression(meta);
                    onContextCompressionRef.current?.(meta);
                  } catch {}
                  return;
                }
                // 业务级错误帧：统一置位 error，渲染错误卡片并终止后续增量处理。
                if (parsed?.type === "error" || parsed?.error) {
                  const msg = parsed?.message || parsed?.code || "后端响应错误";
                  setError(new Error(msg));
                  return;
                }
                if (parsed?.type === "reasoning" && parsed.reasoning) {
                  if (reasoningStartTimeRef.current === null) {
                    reasoningStartTimeRef.current = Date.now();
                  }
                  reasoningDurationMsRef.current =
                    Date.now() - reasoningStartTimeRef.current;
                  thinkingRef.current += parsed.reasoning;
                  scheduleUpdate();
                  onReasoningRef.current?.(parsed.reasoning);
                  return;
                }
                if (parsed?.type === "artifact") {
                  const item: ArtifactItem = {
                    artifactId: parsed.artifactId || `art-${Date.now()}`,
                    artifactType: parsed.artifactType || "image",
                    title: parsed.title,
                    content:
                      parsed.html || parsed.content || parsed.payload || "",
                    mimeType: parsed.mimeType || "image/png",
                    language: parsed.language,
                    status: parsed.status || "complete",
                  };
                  streamStoreRef.current.updateArtifact(item.artifactId, item);
                  onArtifactRef.current?.(item);
                  return;
                }
                if (parsed?.type === "task_plan") {
                  try {
                    const raw = parsed.content || parsed.arguments || "{}";
                    const plan: TaskPlanState = JSON.parse(raw);
                    streamStoreRef.current.updateTaskPlan(plan);
                    onTaskPlanRef.current?.(plan);
                  } catch {}
                  return;
                }
                if (parsed?.type === "task_step") {
                  try {
                    const raw = parsed.content || parsed.arguments || "{}";
                    const step: TaskStepItem = JSON.parse(raw);
                    streamStoreRef.current.updateTaskStep(step);
                    onTaskStepRef.current?.(step);
                  } catch {}
                  return;
                }
                if (parsed?.type === "tool_call") {
                  const rawArgs = parsed.arguments ?? "";
                  let innerThought: string | undefined;
                  if (rawArgs) {
                    try {
                      const obj = JSON.parse(rawArgs);
                      if (
                        typeof obj?.innerThought === "string" &&
                        obj.innerThought.trim()
                      ) {
                        innerThought = obj.innerThought;
                      }
                    } catch {
                      const match =
                        /"innerThought"\s*:\s*"((?:[^"\\]|\\.)*)/.exec(rawArgs);
                      if (match?.[1]) {
                        try {
                          innerThought = JSON.parse(`"${match[1]}"`);
                        } catch {
                          innerThought = match[1];
                        }
                      }
                    }
                  }

                  const item: ToolCallItem = {
                    callId: parsed.toolCallId,
                    name: parsed.toolName ?? "tool",
                    arguments: rawArgs,
                    innerThought,
                    status: "calling",
                  };
                  streamStoreRef.current.updateToolCall(item.callId, item);
                  onToolCallRef.current?.(item);
                  return;
                }
                if (parsed?.type === "tool_result") {
                  const item: ToolCallItem = {
                    callId: parsed.toolCallId,
                    name: parsed.toolName ?? "tool",
                    arguments: "",
                    result: parsed.result ?? "",
                    status: parsed.isError ? "error" : "success",
                  };
                  streamStoreRef.current.updateToolCall(item.callId, item);
                  onToolResultRef.current?.(item);
                  return;
                }
                if (parsed?.type === "usage" && parsed.usage) {
                  usageRef.current = parsed.usage;
                  setUsage(parsed.usage);
                  onUsageRef.current?.(parsed.usage);
                  return;
                }
              } catch {
                // 非 JSON 分帧忽略 JSON.parse 错误
              }

              const delta = parseChunk(ev.data);
              if (delta) {
                contentRef.current += delta;
                scheduleUpdate();
              }
            },
            onerror(err) {
              throw err instanceof Error ? err : new Error(String(err));
            },
          });
        } catch (err) {
          if ((err as Error).name === "AbortError") {
            return;
          }
          setError(err instanceof Error ? err : new Error(String(err)));
        } finally {
          if (abortRef.current === controller) {
            abortRef.current = null;
            flushState();
            setLoading(false);
            onFinishRef.current?.(
              contentRef.current,
              thinkingRef.current,
              usageRef.current,
            );
          }
        }
      };

      void run();
    },
    [
      endpoint,
      headers,
      buildBody,
      parseChunk,
      flushState,
      scheduleUpdate,
      loading,
    ],
  );

  return {
    content: streamData.content,
    thinking: streamData.thinking,
    usage,
    loading,
    error,
    send,
    stop,
    reset,
    streamStore: streamStoreRef.current,
  };
}
