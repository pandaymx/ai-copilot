import { getStoredToken } from "./auth-api";

export interface ShareMeta {
  token: string;
  sessionId: string;
  userId: string;
  title: string;
  expireAt: number | null;
  hasPassword: boolean;
  viewCount: number;
  createdAt: number;
}

export interface ShareSnapshotView {
  token: string;
  title: string;
  messagesJson: string;
  createdAt: number;
  viewCount: number;
}

export interface CreateShareParams {
  title?: string;
  messagesJson: string;
  expireAt?: number;
  password?: string;
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

export async function createSessionShare(
  sessionId: string,
  params: CreateShareParams,
): Promise<ShareMeta> {
  const res = await fetch(`/api/sessions/${sessionId}/share`, {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(params),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "生成分享链接失败" }));
    throw new Error(err.error || "生成分享链接失败");
  }
  return res.json();
}

export async function listSessionShares(
  sessionId: string,
): Promise<ShareMeta[]> {
  const res = await fetch(`/api/sessions/${sessionId}/shares`, {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return [];
  return res.json();
}

export async function revokeShare(token: string): Promise<void> {
  const res = await fetch(`/api/shares/${token}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "撤销分享失败" }));
    throw new Error(err.error || "撤销分享失败");
  }
}

export async function checkShare(
  token: string,
): Promise<{ token: string; requiresPassword: boolean }> {
  const res = await fetch(`/api/public/shares/${token}/check`);
  if (!res.ok) {
    throw new Error("分享链接不存在或已失效");
  }
  return res.json();
}

export async function resolveShare(
  token: string,
  password?: string,
): Promise<ShareSnapshotView> {
  const res = await fetch(`/api/public/shares/${token}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "访问分享失败" }));
    throw new Error(err.error || "访问分享失败");
  }
  return res.json();
}
