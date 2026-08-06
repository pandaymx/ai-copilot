import type { ChatMessage } from "@/components/chat/message-bubble";
import type { ChatSession } from "@/components/chat/sidebar";

export async function fetchSessionsApi(): Promise<ChatSession[] | null> {
  try {
    const res = await fetch("/api/chat/sessions");
    if (!res.ok) return null;
    const data = (await res.json()) as ChatSession[];
    return data;
  } catch {
    return null;
  }
}

export async function fetchSessionDetailApi(
  id: string,
): Promise<{ session: ChatSession; messages: ChatMessage[] } | null> {
  try {
    const res = await fetch(`/api/chat/sessions/${id}`);
    if (!res.ok) return null;
    const data = (await res.json()) as {
      id: string;
      title: string;
      updatedAt: number;
      isDefaultTitle?: boolean;
      messages: {
        id: string;
        role: "user" | "assistant" | "system";
        content: string;
      }[];
    };
    return {
      session: {
        id: data.id,
        title: data.title,
        updatedAt: data.updatedAt,
        isDefaultTitle: data.isDefaultTitle,
      },
      messages: data.messages
        .filter((m) => m.role === "user" || m.role === "assistant")
        .map((m) => ({
          id: m.id,
          role: m.role as "user" | "assistant",
          content: m.content,
        })),
    };
  } catch {
    return null;
  }
}

export async function renameSessionApi(
  id: string,
  newTitle: string,
): Promise<boolean> {
  try {
    const res = await fetch(`/api/chat/sessions/${id}/title`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: newTitle }),
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function deleteSessionApi(id: string): Promise<boolean> {
  try {
    const res = await fetch(`/api/chat/sessions/${id}`, {
      method: "DELETE",
    });
    return res.ok;
  } catch {
    return false;
  }
}
