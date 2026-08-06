"use client";

import { fetchEventSource } from "@microsoft/fetch-event-source";
import { useCallback, useEffect, useRef, useState } from "react";

export interface SpringAiStreamMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

export interface UseSpringAiStreamOptions {
  /** 后端流式接口地址，默认复用 Spring AI 的 SSE 端点。 */
  endpoint?: string;
  /** 自定义请求头，会与 Accept: text/event-stream 合并。 */
  headers?: Record<string, string>;
  /** 自定义请求体构造，便于适配不同模型的入参格式。 */
  buildBody?: (
    input: string,
    history: SpringAiStreamMessage[],
    extraBody?: Record<string, unknown>,
  ) => unknown;
  /**
   * 从单个 SSE data 字段（已剥离前缀、保留内部空白）中解析出增量文本。
   * 返回 null 表示无有效内容（如 [DONE] 或心跳）。
   * JSON 解析失败时应返回 null，不要回退为原文追加，以免半截 JSON 造成乱码。
   */
  parseChunk?: (data: string) => string | null;
  /** 在收到后端返回的会话 ID 时回调 */
  onConversationId?: (conversationId: string) => void;
  /** 在请求前对消息历史做处理（如裁剪）。 */
  onBeforeSend?: (history: SpringAiStreamMessage[]) => SpringAiStreamMessage[];
  /** 流完整结束后回调（成功完成或异常均触发），参数为最终累计文本。 */
  onFinish?: (finalContent: string) => void;
}

export interface UseSpringAiStreamResult {
  /** 当前已累计的助手回复文本。 */
  content: string;
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
}

const DEFAULT_ENDPOINT = "/api/chat/stream";

/**
 * 判断字符串是否"看起来像不完整 JSON 片段"（如网络分包导致的半截 JSON）。
 * 启发式：以 { 或 [ 开头、且整体并未闭合/可解析时，视为片段而非纯文本。
 */
function looksLikeIncompleteJson(data: string): boolean {
  const head = data.trimStart()[0];
  if (head !== "{" && head !== "[") return false;
  // 已能完整解析的不会走到这里；开头是括号且已抛错，基本可判定为片段。
  return true;
}

function defaultParseChunk(data: string): string | null {
  if (!data || data === "[DONE]") return null;
  try {
    const parsed = JSON.parse(data);
    if (typeof parsed === "string") return parsed;

    if (parsed?.type === "conversation") {
      return null;
    }

    if (parsed?.error) {
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

function defaultBuildBody(
  input: string,
  history: SpringAiStreamMessage[],
  extraBody?: Record<string, unknown>,
) {
  return { message: input, history, ...extraBody };
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
    onBeforeSend,
    onFinish,
  } = options;

  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const contentRef = useRef("");
  const historyRef = useRef<SpringAiStreamMessage[]>([]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    contentRef.current = "";
    setContent("");
    setLoading(false);
    setError(null);
  }, []);

  const stop = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setLoading(false);
  }, []);

  const send = useCallback(
    (input: string, extraBody?: Record<string, unknown>) => {
      const message = input.trim();
      if (!message || loading) return;

      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      historyRef.current = [
        ...historyRef.current,
        { role: "user", content: message },
      ];
      const payload = onBeforeSend
        ? onBeforeSend(historyRef.current)
        : historyRef.current;

      contentRef.current = "";
      setContent("");
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
            body: JSON.stringify(buildBody(message, payload, extraBody)),

            signal: controller.signal,
            // 用户切换标签页时保持连接，避免流被浏览器挂起或重连异常。
            openWhenHidden: true,
            // 多行 data 字段（SSE 允许用多行 data: 拼接）合并为单个 data 字符串，
            // 再交由 parseChunk 处理，保留内部空白。
            onmessage(ev) {
              if (!ev.data) return;
              try {
                const parsed = JSON.parse(ev.data);
                if (parsed?.type === "conversation" && parsed.conversationId) {
                  onConversationId?.(parsed.conversationId);
                  return;
                }
              } catch {
                // 非 JSON 分帧忽略 JSON.parse 错误
              }

              const delta = parseChunk(ev.data);
              if (delta) {
                contentRef.current += delta;
                setContent(contentRef.current);
              }
            },
            onerror(err) {
              // 显式抛出以终止 fetch-event-source 的自动重连：
              // 对 HTTP 4xx/5xx 及网络错误不应无限重试，否则会重复触发流。
              // 注意：返回（不抛出）才会触发库的重试逻辑；这里一律抛出停止重试。
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
            setLoading(false);
            onFinish?.(contentRef.current);
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
      onBeforeSend,
      onFinish,
      loading,
    ],
  );

  return { content, loading, error, send, stop, reset };
}
