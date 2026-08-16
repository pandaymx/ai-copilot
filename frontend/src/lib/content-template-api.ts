import { getStoredToken } from "./auth-api";

export interface TemplateField {
  name: string;
  label: string;
  placeholder: string;
  required: boolean;
  type: string;
}

export interface ContentTemplateMetadata {
  id: string;
  name: string;
  description: string;
  category: string;
  icon: string;
  fields: TemplateField[];
}

export interface GenerateContentRequest {
  templateId: string;
  title: string;
  inputs: Record<string, string>;
  customPrompt?: string;
}

export interface GenerateContentResponse {
  id: string;
  templateId: string;
  title: string;
  markdownContent: string;
  structuredSections: Record<string, unknown>;
  createdAt: number;
}

export interface ContentGenerationHistoryItem {
  id: string;
  userId: string;
  templateId: string;
  title: string;
  markdownContent: string;
  createdAt: number;
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

export async function listContentTemplates(): Promise<
  ContentTemplateMetadata[]
> {
  const res = await fetch("/api/content/templates", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return [];
  return res.json();
}

export async function generateContent(
  payload: GenerateContentRequest,
): Promise<GenerateContentResponse> {
  const res = await fetch("/api/content/generate", {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error("AI 结构化内容生成失败");
  }
  return res.json();
}

export async function listContentHistory(): Promise<
  ContentGenerationHistoryItem[]
> {
  const res = await fetch("/api/content/history", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return [];
  return res.json();
}

export async function deleteContentHistory(id: string): Promise<void> {
  const res = await fetch(`/api/content/history/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    throw new Error("删除历史记录失败");
  }
}
