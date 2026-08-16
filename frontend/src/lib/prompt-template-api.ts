export interface PromptTemplate {
  id: string;
  userId: string;
  title: string;
  description: string;
  category: string;
  body: string;
  variables: string[];
  rating: number;
  favorite: boolean;
  isSystem: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface PromptTemplateCreateRequest {
  id?: string;
  title: string;
  description?: string;
  category: string;
  body: string;
  rating?: number;
  favorite?: boolean;
}

export async function fetchPromptTemplates(
  category?: string,
  keyword?: string,
): Promise<PromptTemplate[]> {
  const params = new URLSearchParams();
  if (category && category !== "all") {
    params.set("category", category);
  }
  if (keyword) {
    params.set("keyword", keyword);
  }

  const url = `/api/prompt-templates${params.toString() ? `?${params.toString()}` : ""}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`获取 Prompt 模板列表失败: ${res.statusText}`);
  }
  return res.json();
}

export async function createPromptTemplate(
  data: PromptTemplateCreateRequest,
): Promise<{ id: string; message: string }> {
  const res = await fetch("/api/prompt-templates", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const errorText = await res.text();
    throw new Error(errorText || "创建 Prompt 模板失败");
  }
  return res.json();
}

export async function updatePromptTemplate(
  id: string,
  data: Partial<PromptTemplateCreateRequest>,
): Promise<{ message: string }> {
  const res = await fetch(`/api/prompt-templates/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const errorText = await res.text();
    throw new Error(errorText || "更新 Prompt 模板失败");
  }
  return res.json();
}

export async function deletePromptTemplate(id: string): Promise<void> {
  const res = await fetch(`/api/prompt-templates/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    throw new Error("删除 Prompt 模板失败");
  }
}

export async function toggleFavoritePromptTemplate(id: string): Promise<void> {
  const res = await fetch(
    `/api/prompt-templates/${encodeURIComponent(id)}/favorite`,
    {
      method: "POST",
    },
  );
  if (!res.ok) {
    throw new Error("收藏状态切换失败");
  }
}

export async function ratePromptTemplate(
  id: string,
  rating: number,
): Promise<void> {
  const res = await fetch(
    `/api/prompt-templates/${encodeURIComponent(id)}/rate`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rating }),
    },
  );
  if (!res.ok) {
    throw new Error("评分失败");
  }
}

export async function renderPromptTemplate(
  id: string,
  variables: Record<string, string>,
): Promise<string> {
  const res = await fetch(
    `/api/prompt-templates/${encodeURIComponent(id)}/render`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(variables),
    },
  );
  if (!res.ok) {
    throw new Error("模板渲染失败");
  }
  const data = (await res.json()) as { renderedText: string };
  return data.renderedText;
}

export async function smartFillPromptTemplate(
  id: string,
  context: string,
): Promise<Record<string, string>> {
  const res = await fetch(
    `/api/prompt-templates/${encodeURIComponent(id)}/smart-fill`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ context }),
    },
  );
  if (!res.ok) {
    throw new Error("智能填充失败");
  }
  return res.json();
}
