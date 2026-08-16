import { getStoredToken } from "./auth-api";

export interface WebhookSubscription {
  id: string;
  userId: string;
  name: string;
  url: string;
  eventType: string;
  secret: string;
  enabled: boolean;
  lastStatus: string | null;
  lastDeliveredAt: number | null;
  createdAt: number;
}

export interface WebhookCreateRequest {
  name: string;
  url: string;
  eventType: string;
  secret?: string;
}

export interface WebhookUpdateRequest {
  name?: string;
  url?: string;
  eventType?: string;
  enabled?: boolean;
}

export interface WebhookDelivery {
  id: string;
  subscriptionId: string;
  userId: string;
  eventType: string;
  payloadJson: string;
  responseStatus: number;
  responseBody: string;
  success: boolean;
  durationMs: number;
  createdAt: number;
}

export interface WebhookTestResult {
  success: boolean;
  statusCode: number;
  message: string;
  durationMs: number;
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

export async function listWebhooks(): Promise<WebhookSubscription[]> {
  const res = await fetch("/api/settings/webhooks", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return [];
  return res.json();
}

export async function createWebhook(
  req: WebhookCreateRequest,
): Promise<WebhookSubscription> {
  const res = await fetch("/api/settings/webhooks", {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "创建 Webhook 失败" }));
    throw new Error(err.error || "创建 Webhook 失败");
  }
  return res.json();
}

export async function updateWebhook(
  id: string,
  req: WebhookUpdateRequest,
): Promise<void> {
  const res = await fetch(`/api/settings/webhooks/${id}`, {
    method: "PUT",
    headers: getAuthHeaders(),
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "更新 Webhook 失败" }));
    throw new Error(err.error || "更新 Webhook 失败");
  }
}

export async function deleteWebhook(id: string): Promise<void> {
  const res = await fetch(`/api/settings/webhooks/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "删除 Webhook 失败" }));
    throw new Error(err.error || "删除 Webhook 失败");
  }
}

export async function testWebhook(id: string): Promise<WebhookTestResult> {
  const res = await fetch(`/api/settings/webhooks/${id}/test`, {
    method: "POST",
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "测试发送失败" }));
    throw new Error(err.error || "测试发送失败");
  }
  return res.json();
}

export async function listWebhookDeliveries(
  id: string,
): Promise<WebhookDelivery[]> {
  const res = await fetch(`/api/settings/webhooks/${id}/deliveries`, {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return [];
  return res.json();
}
