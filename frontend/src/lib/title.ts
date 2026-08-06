// 会话标题生成：调用后端 /api/chat/title，根据「用户问题 + AI 回答」让 LLM 生成标题。
// 失败时返回 null，由调用方回退到本地 deriveTitle。

export interface FetchTitleInput {
  message: string;
  answer: string;
  provider?: string | null;
  model?: string | null;
  conversationId?: string | null;
}

export async function fetchTitle(
  input: FetchTitleInput,
): Promise<string | null> {
  try {
    const res = await fetch("/api/chat/title", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message: input.message ?? "",
        answer: input.answer ?? "",
        provider: input.provider ?? null,
        model: input.model ?? null,
        conversationId: input.conversationId ?? null,
      }),
    });
    if (!res.ok) {
      return null;
    }
    const data = (await res.json()) as { title?: string | null };
    const title = data?.title?.trim();
    return title ? title : null;
  } catch {
    return null;
  }
}
