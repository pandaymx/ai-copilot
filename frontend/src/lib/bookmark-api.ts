import { getStoredToken } from "./auth-api";

export interface MessageBookmark {
  id: string;
  userId: string;
  sessionId: string;
  messageId: string;
  role: string;
  content: string;
  tags: string[];
  pinned: boolean;
  bookmarked: boolean;
  createdAt: number;
}

export interface MessageStatusResponse {
  pinned: boolean;
  bookmarked: boolean;
  tags: string[];
}

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  const token = getStoredToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

export async function toggleBookmark(
  messageId: string,
  payload: {
    sessionId: string;
    role: string;
    content: string;
    tags?: string[];
  },
): Promise<MessageStatusResponse> {
  const res = await fetch(
    `/api/chat/messages/${encodeURIComponent(messageId)}/bookmark`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(payload),
    },
  );
  if (!res.ok) {
    throw new Error("切换收藏状态失败");
  }
  return res.json();
}

export async function togglePin(
  messageId: string,
  payload: { sessionId: string; role: string; content: string },
): Promise<MessageStatusResponse> {
  const res = await fetch(
    `/api/chat/messages/${encodeURIComponent(messageId)}/pin`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(payload),
    },
  );
  if (!res.ok) {
    throw new Error("切换置顶固定状态失败");
  }
  return res.json();
}

export async function updateMessageTags(
  messageId: string,
  tags: string[],
): Promise<void> {
  const res = await fetch(
    `/api/chat/messages/${encodeURIComponent(messageId)}/tags`,
    {
      method: "PUT",
      headers: getAuthHeaders(),
      body: JSON.stringify({ tags }),
    },
  );
  if (!res.ok) {
    throw new Error("更新标签失败");
  }
}

export async function listBookmarks(): Promise<MessageBookmark[]> {
  const res = await fetch("/api/chat/bookmarks", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return [];
  return res.json();
}

export async function listPinnedMessages(
  sessionId: string,
): Promise<MessageBookmark[]> {
  if (!sessionId) return [];
  const res = await fetch(
    `/api/chat/sessions/${encodeURIComponent(sessionId)}/pinned`,
    {
      headers: getAuthHeaders(),
    },
  );
  if (!res.ok) return [];
  return res.json();
}

export async function getMessageMeta(
  messageId: string,
): Promise<MessageBookmark | null> {
  const res = await fetch(
    `/api/chat/messages/${encodeURIComponent(messageId)}/meta`,
    {
      headers: getAuthHeaders(),
    },
  );
  if (!res.ok) return null;
  return res.json();
}
