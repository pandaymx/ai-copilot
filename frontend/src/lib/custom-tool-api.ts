/**
 * 用户自定义工具（Custom Tool DSL）前端 API 客户端与类型定义。
 */

export type CustomToolType = "HTTP" | "SCRIPT" | "PROMPT";

export interface HttpConfig {
  url: string;
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  headers?: Record<string, string>;
  queryParams?: Record<string, string>;
  bodyTemplate?: string;
  authType?: "NONE" | "BEARER" | "API_KEY" | "BASIC";
  authHeader?: string;
  authToken?: string;
  timeoutSeconds?: number;
}

export interface ScriptConfig {
  language: "python" | "javascript";
  scriptCode: string;
}

export interface PromptConfig {
  systemPrompt?: string;
  promptTemplate: string;
  targetProvider?: string;
  targetModel?: string;
}

export interface CustomToolItem {
  id?: string;
  name: string;
  displayName: string;
  description: string;
  type: CustomToolType;
  enabled?: boolean;
  parametersSchema: string;
  httpConfig?: HttpConfig;
  scriptConfig?: ScriptConfig;
  promptConfig?: PromptConfig;
  createdAt?: number;
  updatedAt?: number;
}

export interface ToolTestRequest {
  tool: CustomToolItem;
  inputParameters: Record<string, unknown>;
}

export interface ToolTestResponse {
  status: "SUCCESS" | "FAILURE";
  output?: string;
  executionTimeMs: number;
  isTruncated: boolean;
  errorMessage?: string;
}

/** 获取自定义工具列表 */
export async function listCustomTools(): Promise<CustomToolItem[]> {
  const res = await fetch("/api/custom-tools", {
    headers: { "Content-Type": "application/json" },
  });
  if (!res.ok) {
    throw new Error(`获取自定义工具列表失败: ${res.statusText}`);
  }
  return res.json();
}

/** 获取单个自定义工具 */
export async function getCustomTool(id: string): Promise<CustomToolItem> {
  const res = await fetch(`/api/custom-tools/${id}`);
  if (!res.ok) {
    throw new Error(`获取工具详情失败: ${res.statusText}`);
  }
  return res.json();
}

/** 创建自定义工具 */
export async function createCustomTool(
  tool: CustomToolItem,
): Promise<CustomToolItem> {
  const res = await fetch("/api/custom-tools", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(tool),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || `创建工具失败: ${res.statusText}`);
  }
  return res.json();
}

/** 更新自定义工具 */
export async function updateCustomTool(
  id: string,
  tool: CustomToolItem,
): Promise<CustomToolItem> {
  const res = await fetch(`/api/custom-tools/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(tool),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || `更新工具失败: ${res.statusText}`);
  }
  return res.json();
}

/** 切换工具启用状态 */
export async function toggleCustomTool(id: string): Promise<boolean> {
  const res = await fetch(`/api/custom-tools/${id}/toggle`, {
    method: "POST",
  });
  if (!res.ok) {
    throw new Error(`切换状态失败: ${res.statusText}`);
  }
  const data = await res.json();
  return !!data.success;
}

/** 删除自定义工具 */
export async function deleteCustomTool(id: string): Promise<boolean> {
  const res = await fetch(`/api/custom-tools/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    throw new Error(`删除工具失败: ${res.statusText}`);
  }
  const data = await res.json();
  return !!data.deleted;
}

/** 在线单次试运行测试 */
export async function testCustomTool(
  req: ToolTestRequest,
): Promise<ToolTestResponse> {
  const res = await fetch("/api/custom-tools/test", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || `测试运行失败: ${res.statusText}`);
  }
  return res.json();
}
