"use client";

import { fetchEventSource } from "@microsoft/fetch-event-source";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";

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
  /** 收到 Reasoning/Thinking 思考过程增量时的回调 */
  onReasoning?: (reasoningDelta: string) => void;
  /** 收到 Token 用量统计时的回调 */
  onUsage?: (usage: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  }) => void;
  /** 收到 tool_call 帧（工具开始执行）时的回调 */
  onToolCall?: (item: ToolCallItem) => void;
  /** 收到 tool_result 帧（工具执行完成/失败）时的回调 */
  onToolResult?: (item: ToolCallItem) => void;
  /** 流完整结束后回调（成功完成或异常均触发），参数为最终累计文本、思考过程与 Token 用量。 */
  onFinish?: (
    finalContent: string,
    finalThinking?: string,
    finalUsage?: {
      promptTokens: number;
      completionTokens: number;
      totalTokens: number;
      estimatedCostRmb?: number;
    } | null,
  ) => void;
}

export interface UseSpringAiStreamResult {
  /** 当前已累计的助手回复文本。 */
  content: string;
  /** 当前累计的思考过程文本。 */
  thinking: string;
  /** 当前统计的 Usage 信息。 */
  usage: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  } | null;
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

export interface StreamData {
  content: string;
  thinking: string;
  usage: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  } | null;
  /** 工具调用列表，以 callId 为唯一 key（Map 结构用普通对象表达以保证快照不可变）。 */
  toolCalls: Record<string, ToolCallItem>;
}

export class StreamStore {
  private data: StreamData = {
    content: "",
    thinking: "",
    usage: null,
    toolCalls: {},
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

  update(content: string, thinking: string, usage: StreamData["usage"]) {
    this.data = { ...this.data, content, thinking, usage };
    for (const listener of this.listeners) {
      listener();
    }
  }

  /** 以 callId 为 key 增量更新某个工具调用项（保证并行多 tool_call 不互相覆盖、不顺序颠倒）。 */
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

  reset() {
    this.data = { content: "", thinking: "", usage: null, toolCalls: {} };
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

    if (parsed?.type === "error" || parsed?.error) {
      const msg = parsed.message || parsed.code || "后端响应错误";
      return `\n\n⚠️ [服务异常]: ${msg}`;
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
    onReasoning,
    onUsage,
    onToolCall,
    onToolResult,
    onFinish,
  } = options;

  const [streamData, setStreamData] = useState({ content: "", thinking: "" });
  const [usage, setUsage] = useState<{
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const streamStoreRef = useRef(new StreamStore());
  const contentRef = useRef("");
  const thinkingRef = useRef("");
  const usageRef = useRef<{
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  } | null>(null);
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
    );
    setStreamData({
      content: contentRef.current,
      thinking: thinkingRef.current,
    });
  }, []);

  const scheduleUpdate = useCallback(() => {
    if (rafRef.current !== null) return;
    rafRef.current = requestAnimationFrame(() => {
      rafRef.current = null;
      streamStoreRef.current.update(
        contentRef.current,
        thinkingRef.current,
        usageRef.current,
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
      onFinish?.(currentContent, currentThinking, currentUsage);
    }
  }, [flushState, onFinish]);

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
                if (parsed?.type === "conversation" && parsed.conversationId) {
                  onConversationId?.(parsed.conversationId);
                  return;
                }
                if (parsed?.type === "reasoning" && parsed.reasoning) {
                  thinkingRef.current += parsed.reasoning;
                  scheduleUpdate();
                  onReasoning?.(parsed.reasoning);
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
                  onToolCall?.(item);
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
                  onToolResult?.(item);
                  return;
                }
                if (parsed?.type === "usage" && parsed.usage) {
                  usageRef.current = parsed.usage;
                  setUsage(parsed.usage);
                  onUsage?.(parsed.usage);
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
            onFinish?.(
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
      onConversationId,
      onReasoning,
      onUsage,
      onToolCall,
      onToolResult,
      onFinish,
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
