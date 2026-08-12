/**
 * Mock 后端接口构造器。
 *
 * 严格匹配 frontend 既有契约：
 * - SSE 事件（useSpringAiStream.ts）：{type:"conversation",conversationId}、
 *   {type:"reasoning",reasoning}、{type:"usage",usage}、{content}（增量）、
 *   [DONE]（结束）、{type:"error",message}（错误卡片）。
 * - REST（api.ts）：GET /api/chat/sessions → ChatSession[]（非 2xx → null 降级）；
 *   GET /api/chat/sessions/:id → {session,messages}；
 *   GET /api/chat/search?q=&limit= → {query,results}；
 *   DELETE/PUT /api/chat/sessions/:id → bool。
 */

export interface MockSession {
  id: string;
  title: string;
  updatedAt: number;
  isDefaultTitle?: boolean;
}

export interface MockMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  media?: { mimeType: string; data: string }[];
}

export interface SearchResultItem {
  sessionId: string;
  messageId: number;
  role: string;
  snippet: string;
  timestamp: number;
}

/** 构造会话列表响应体。 */
export function buildSessions(sessions: MockSession[]): MockSession[] {
  return sessions;
}

/** 构造会话详情响应体（含消息列表）。 */
export function buildSessionDetail(session: MockSession, messages: MockMessage[]) {
  return {
    id: session.id,
    title: session.title,
    updatedAt: session.updatedAt,
    isDefaultTitle: session.isDefaultTitle,
    messages: messages.map((m) => ({
      id: m.id,
      role: m.role,
      content: m.content,
      ...(m.media ? { media: m.media } : {}),
    })),
  };
}

/**
 * 将文本按固定步长切片，构造标准 SSE 分帧体数组。
 * 每个 content 帧使用 {content} 结构，前端 defaultParseChunk 才能正确累加。
 * 返回数组（而非拼接字符串），便于在 route 中按 delay 逐帧 flush，
 * 从而在测试期间保持 isStreaming=true，使“停止生成”按钮持续可见。
 */
export function buildStreamFrames(
  text: string,
  opts: { conversationId?: string; reasoning?: string; step?: number } = {},
): string[] {
  const { conversationId = "sess-mock-1", reasoning, step = 4 } = opts;
  const safeText = text || " ";
  const chunks = safeText.match(new RegExp(`.{1,${step}}`, "g")) ?? [safeText];

  const frames: string[] = [
    `data: ${JSON.stringify({ type: "conversation", conversationId })}\n\n`,
  ];

  if (reasoning) {
    frames.push(
      `data: ${JSON.stringify({ type: "reasoning", reasoning })}\n\n`,
    );
  }

  for (const c of chunks) {
    frames.push(`data: ${JSON.stringify({ content: c })}\n\n`);
  }

  frames.push(
    `data: ${JSON.stringify({
      type: "usage",
      usage: { promptTokens: 12, completionTokens: 8, totalTokens: 20 },
    })}\n\n`,
  );
  frames.push(`data: [DONE]\n\n`);

  return frames;
}

/**
 * 构造错误帧（业务级 SSE error 帧）。
 * 前端 useSpringAiStream 会将其识别为 type:"error" 并调用 setError，
 * 从而渲染“服务连接受阻”错误卡片（而非作为正文文本追加）。
 */
export function buildErrorFrames(message = "Mock 服务异常"): string[] {
  return [
    `data: ${JSON.stringify({ type: "conversation", conversationId: "sess-mock-err" })}\n\n`,
    `data: ${JSON.stringify({ type: "error", message })}\n\n`,
    `data: [DONE]\n\n`,
  ];
}

/** 构造 500 文本错误响应（fetchEventSource onerror 抛出）。 */
export function buildInternalErrorBody(message = "Mock Internal Error"): string {
  return JSON.stringify({ error: true, message });
}

/** 构造搜索响应体。 */
export function buildSearchResponse(
  query: string,
  results: SearchResultItem[] = [],
): { query: string; results: SearchResultItem[] } {
  return { query, results };
}
