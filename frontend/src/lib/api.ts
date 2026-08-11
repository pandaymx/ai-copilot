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
        media?: { mimeType: string; data: string }[];
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
          attachments:
            m.media && m.media.length > 0
              ? m.media.map((att, idx) => ({
                  id: `att-${m.id}-${idx}`,
                  name: `图片附件 ${idx + 1}`,
                  type: "image" as const,
                  mimeType: att.mimeType,
                  url: att.data.startsWith("data:")
                    ? att.data
                    : `data:${att.mimeType};base64,${att.data}`,
                }))
              : undefined,
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

export interface SearchResultItem {
  sessionId: string;
  messageId: number;
  role: string;
  snippet: string;
  timestamp: number;
}

export interface SearchResponse {
  query: string;
  results: SearchResultItem[];
}

export async function searchChatHistoryApi(
  query: string,
  limit = 50,
  signal?: AbortSignal,
): Promise<SearchResponse | null> {
  if (!query.trim()) return null;
  try {
    const res = await fetch(
      `/api/chat/search?q=${encodeURIComponent(query.trim())}&limit=${limit}`,
      { signal },
    );
    if (!res.ok) return null;
    return (await res.json()) as SearchResponse;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") {
      return null;
    }
    return null;
  }
}

// ====================== 知识库（RAG）管理 API ======================

export interface RagDocumentMeta {
  docId: string;
  source: string;
  sourceType: string;
  fileName: string;
  title: string;
  userId: string;
  chunkCount: number;
  ingestedAt: string;
  contentHash: string;
}

export interface RagListResponse {
  items: RagDocumentMeta[];
  total: number;
  sourceTypeCounts: Record<string, number>;
}

export interface RagStatus {
  enabled: boolean;
  available: boolean;
  collectionName: string;
  documentCount: number;
  vectorCount: number;
}

export interface RagIngestResult {
  success: boolean;
  sourceType: string;
  source: string;
  ingested: number;
  skipped: number;
  /** 重新入库（reingest）时返回，表示被移除的旧向量条数。 */
  removed?: number;
  error?: string;
  detail?: string;
}

export interface RagDeleteResult {
  success: boolean;
  source: string;
  userId: string;
  removed: number;
  error?: string;
  detail?: string;
}

/** 拉取已入库文档列表（按 source 聚合）。 */
export async function ragListApi(
  userId?: string,
  sourceType?: string,
  limit = 50,
  signal?: AbortSignal,
): Promise<RagListResponse | null> {
  const params = new URLSearchParams();
  if (userId) params.set("userId", userId);
  if (sourceType) params.set("sourceType", sourceType);
  params.set("limit", String(limit));
  try {
    const res = await fetch(`/api/rag/documents?${params.toString()}`, {
      signal,
    });
    if (!res.ok) return null;
    return (await res.json()) as RagListResponse;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

/** 上传入库（多源联合 DTO）。 */
export async function ragUploadApi(payload: {
  sourceType: string;
  rawText?: string;
  targetUrl?: string;
  fileStoragePath?: string;
  fileName?: string;
}): Promise<RagIngestResult | null> {
  try {
    const res = await fetch("/api/rag/ingest", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = (await res.json()) as RagIngestResult;
    if (!res.ok) {
      return {
        success: false,
        sourceType: payload.sourceType,
        source: "",
        ingested: 0,
        skipped: 0,
        error: data.error,
        detail: data.detail,
      };
    }
    return data;
  } catch {
    return null;
  }
}

/** 覆盖更新（重新入库）。 */
export async function ragReingestApi(payload: {
  sourceType: string;
  rawText?: string;
  targetUrl?: string;
  fileStoragePath?: string;
  fileName?: string;
}): Promise<RagIngestResult | null> {
  try {
    const res = await fetch("/api/rag/reingest", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = (await res.json()) as RagIngestResult;
    if (!res.ok) {
      return {
        success: false,
        sourceType: payload.sourceType,
        source: "",
        ingested: 0,
        skipped: 0,
        error: data.error,
        detail: data.detail,
      };
    }
    return data;
  } catch {
    return null;
  }
}

/** 按 source 删除文档。 */
export async function ragDeleteApi(
  source: string,
  userId?: string,
): Promise<RagDeleteResult | null> {
  const params = new URLSearchParams({ source });
  if (userId) params.set("userId", userId);
  try {
    const res = await fetch(`/api/rag/documents?${params.toString()}`, {
      method: "DELETE",
    });
    const data = (await res.json()) as RagDeleteResult;
    if (!res.ok) {
      return {
        success: false,
        source,
        userId: userId ?? "",
        removed: 0,
        error: data.error,
        detail: data.detail,
      };
    }
    return data;
  } catch {
    return null;
  }
}

/** 向量库状态统计。 */
export async function ragStatusApi(
  signal?: AbortSignal,
): Promise<RagStatus | null> {
  try {
    const res = await fetch("/api/rag/status", { signal });
    if (!res.ok) return null;
    return (await res.json()) as RagStatus;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

// ====================== 长期记忆管理 API ======================

export interface MemoryItem {
  id: string;
  content: string;
  category: string | null;
  confidence: number | null;
  updatedAt: string | null;
}

export interface MemoryListResponse {
  items: MemoryItem[];
  total: number;
}

/** 拉取当前用户的全部长期记忆（分页 + 关键字过滤）。 */
export async function memoryListApi(
  keyword?: string,
  limit = 50,
  offset = 0,
  signal?: AbortSignal,
): Promise<MemoryListResponse | null> {
  const params = new URLSearchParams();
  const kw = keyword?.trim();
  if (kw) params.set("keyword", kw);
  params.set("limit", String(limit));
  params.set("offset", String(offset));
  try {
    const res = await fetch(`/api/memory?${params.toString()}`, { signal });
    if (!res.ok) return null;
    return (await res.json()) as MemoryListResponse;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

/** 编辑单条记忆（内容 + 分类）。 */
export async function memoryUpdateApi(
  id: string,
  content: string,
  category: string | null,
): Promise<MemoryItem | null> {
  try {
    const res = await fetch(`/api/memory/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ content, category }),
    });
    if (!res.ok) return null;
    return (await res.json()) as MemoryItem;
  } catch {
    return null;
  }
}

/** 删除单条记忆。 */
export async function memoryDeleteApi(id: string): Promise<boolean> {
  try {
    const res = await fetch(`/api/memory/${id}`, {
      method: "DELETE",
    });
    return res.ok;
  } catch {
    return false;
  }
}
