export interface ApiKeyItem {
  id: string;
  userId: string;
  provider: string;
  maskedKey: string;
  status: "ACTIVE" | "INVALID" | "UNTESTED";
  balance?: string;
  errorMessage?: string;
  createdAt: number;
  updatedAt: number;
}

export interface ApiKeyTestResult {
  valid: boolean;
  status: string;
  message: string;
  balance?: string;
}

export async function fetchApiKeys(): Promise<ApiKeyItem[]> {
  const res = await fetch("/api/settings/api-keys", {
    headers: { "Content-Type": "application/json" },
  });
  if (!res.ok) {
    throw new Error(`获取 API Key 列表失败 (${res.status})`);
  }
  return res.json();
}

export async function saveApiKey(
  provider: string,
  apiKey: string,
  baseUrl?: string,
): Promise<{ id: string; status: string }> {
  const res = await fetch("/api/settings/api-keys", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ provider, apiKey, baseUrl }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `保存 API Key 失败 (${res.status})`);
  }
  return res.json();
}

export async function deleteApiKey(id: string): Promise<void> {
  const res = await fetch(`/api/settings/api-keys/${id}`, {
    method: "DELETE",
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`删除 API Key 失败 (${res.status})`);
  }
}

export async function testApiKey(id: string): Promise<ApiKeyTestResult> {
  const res = await fetch(`/api/settings/api-keys/${id}/test`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `测试 API Key 失败 (${res.status})`);
  }
  return res.json();
}
