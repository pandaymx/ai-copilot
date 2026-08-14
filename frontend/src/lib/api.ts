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
  priority?: number | null;
  accessCount?: number | null;
  lastAccessedAt?: string | null;
  priorityScore?: number | null;
  archived?: boolean | null;
}

export interface MemoryListResponse {
  items: MemoryItem[];
  total: number;
}

/** 拉取当前用户的长期记忆（支持状态过滤：active / archived / all，分页 + 关键字过滤）。 */
export async function memoryListApi(
  keyword?: string,
  status?: "active" | "archived" | "all",
  limit = 50,
  offset = 0,
  signal?: AbortSignal,
): Promise<MemoryListResponse | null> {
  const params = new URLSearchParams();
  const kw = keyword?.trim();
  if (kw) params.set("keyword", kw);
  if (status) params.set("status", status);
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

/** 编辑/更新单条记忆（内容、分类、优先级权重、归档状态）。 */
export async function memoryUpdateApi(
  id: string,
  content: string,
  category: string | null,
  priority?: number | null,
  archived?: boolean | null,
): Promise<MemoryItem | null> {
  try {
    const res = await fetch(`/api/memory/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ content, category, priority, archived }),
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

/** 触发记忆优先级时间衰减与自动归档/清理。 */
export async function memoryDecayApi(): Promise<{
  archived: number;
  deleted: number;
} | null> {
  try {
    const res = await fetch("/api/memory/decay", { method: "POST" });
    if (!res.ok) return null;
    return (await res.json()) as { archived: number; deleted: number };
  } catch {
    return null;
  }
}

/** 触发细粒度记忆摘要压缩。 */
export async function memoryCompressApi(): Promise<{
  compressedCategories: number;
} | null> {
  try {
    const res = await fetch("/api/memory/compress", { method: "POST" });
    if (!res.ok) return null;
    return (await res.json()) as { compressedCategories: number };
  } catch {
    return null;
  }
}

/** 触发记忆冲突检测与合并。 */
export async function memoryResolveConflictsApi(): Promise<{
  resolvedConflicts: number;
} | null> {
  try {
    const res = await fetch("/api/memory/resolve-conflicts", {
      method: "POST",
    });
    if (!res.ok) return null;
    return (await res.json()) as { resolvedConflicts: number };
  } catch {
    return null;
  }
}

// ====================== 成本看板与用量配额 API ======================

export interface QuotaConfig {
  monthlyTokenQuota: number;
  alertThresholdPercent: number;
  monthlyCostQuotaRmb: number;
}

export interface UsageUserSummary {
  userId: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  totalCost: number;
  requestCount: number;
}

export interface UsageModelDetailSummary {
  modelId: string;
  providerId: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  totalCost: number;
  requestCount: number;
}

export interface UsageDailySummary {
  day: string;
  totalTokens: number;
  totalCost: number;
  requestCount: number;
}

export interface UsageDashboardData {
  monthKey: string;
  totalTokens: number;
  totalCost: number;
  totalRequests: number;
  activeUsers: number;
  activeModels: number;
  byUser: UsageUserSummary[];
  byModel: UsageModelDetailSummary[];
  dailyTrend: UsageDailySummary[];
  quotaConfig: QuotaConfig;
  quotaAlertTriggered: boolean;
}

/** 获取成本看板大盘聚合数据。 */
export async function fetchUsageDashboardApi(
  month?: string,
  signal?: AbortSignal,
): Promise<UsageDashboardData | null> {
  const url = month
    ? `/api/usage/dashboard?month=${encodeURIComponent(month)}`
    : "/api/usage/dashboard";
  try {
    const res = await fetch(url, { signal });
    if (!res.ok) return null;
    return (await res.json()) as UsageDashboardData;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

/** 获取配额与告警阈值配置。 */
export async function fetchQuotaConfigApi(
  signal?: AbortSignal,
): Promise<QuotaConfig | null> {
  try {
    const res = await fetch("/api/usage/quota-config", { signal });
    if (!res.ok) return null;
    return (await res.json()) as QuotaConfig;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

/** 管理员更新配额与告警阈值配置。 */
export async function updateQuotaConfigApi(
  config: QuotaConfig,
): Promise<QuotaConfig | null> {
  try {
    const res = await fetch("/api/usage/quota-config", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(config),
    });
    if (!res.ok) return null;
    return (await res.json()) as QuotaConfig;
  } catch {
    return null;
  }
}

// ====================== 会话结构化摘要与知识沉淀 API ======================

export interface ConversationSummary {
  conversationId: string;
  title: string;
  summary: string;
  keyDecisions: string[];
  todos: string[];
  references: string[];
  openIssues: string[];
  tags: string[];
  messageCount: number;
  createdAt: number;
}

export interface KnowledgeCaptureResult {
  success: boolean;
  fileName: string;
  title: string;
  ingestedChunks: number;
  skippedChunks: number;
  sourceType: string;
  error?: string;
}

/** 生成/重新提炼会话结构化摘要。 */
export async function generateSessionSummaryApi(
  sessionId: string,
  provider?: string,
  model?: string,
  signal?: AbortSignal,
): Promise<ConversationSummary | null> {
  try {
    const res = await fetch(`/api/chat/sessions/${sessionId}/summary`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ provider, model }),
      signal,
    });
    if (!res.ok) return null;
    return (await res.json()) as ConversationSummary;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

/** 一键将当前会话摘要沉淀入库为 RAG 知识库文档。 */
export async function saveSessionToKnowledgeApi(
  sessionId: string,
  summary: ConversationSummary,
  customTitle?: string,
  signal?: AbortSignal,
): Promise<KnowledgeCaptureResult | null> {
  try {
    const res = await fetch(`/api/chat/sessions/${sessionId}/knowledge`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ summary, customTitle }),
      signal,
    });
    if (!res.ok) {
      const errJson = (await res.json().catch(() => ({}))) as {
        error?: string;
      };
      return {
        success: false,
        fileName: "",
        title: "",
        ingestedChunks: 0,
        skippedChunks: 0,
        sourceType: "CONVERSATION_SUMMARY",
        error: errJson.error || "存入知识库失败",
      };
    }
    return (await res.json()) as KnowledgeCaptureResult;
  } catch (err: unknown) {
    return {
      success: false,
      fileName: "",
      title: "",
      ingestedChunks: 0,
      skippedChunks: 0,
      sourceType: "CONVERSATION_SUMMARY",
      error: err instanceof Error ? err.message : "请求失败",
    };
  }
}

// ====================== AI 评测与评估体系 (Evaluation Framework) API ======================

export interface BenchmarkCase {
  id: string;
  title: string;
  category: string;
  prompt: string;
  expectedOutput: string;
  context?: string;
  tags?: string[];
  createdAt?: number;
}

export interface EvaluationMetrics {
  relevance: number;
  accuracy: number;
  completeness: number;
  fluency: number;
  safety: number;
  overallScore: number;
}

export interface EvaluationResultDto {
  id: string;
  benchmarkId?: string;
  benchmarkTitle?: string;
  provider: string;
  model: string;
  judgeProvider: string;
  judgeModel: string;
  prompt: string;
  responseText: string;
  expectedOutput?: string;
  metrics: EvaluationMetrics;
  judgeFeedback: string;
  latencyMs: number;
  totalTokens?: number;
  humanScore?: number;
  humanAnnotation?: string;
  evaluatedAt: number;
}

export interface AbTestResultDto {
  id: string;
  prompt: string;
  context?: string;
  providerA: string;
  modelA: string;
  responseA: string;
  latencyMsA: number;
  metricsA: EvaluationMetrics;
  providerB: string;
  modelB: string;
  responseB: string;
  latencyMsB: number;
  metricsB: EvaluationMetrics;
  judgeProvider: string;
  judgeModel: string;
  winner: "MODEL_A" | "MODEL_B" | "TIE";
  comparisonReason: string;
  executedAt: number;
}

export interface ModelLeaderboardEntry {
  modelKey: string;
  provider: string;
  model: string;
  count: number;
  averageScore: number;
  averageLatencyMs: number;
  metrics: EvaluationMetrics;
}

export interface EvaluationSummaryDto {
  totalEvaluations: number;
  totalAbTests: number;
  averageScore: number;
  dimensionAverages: EvaluationMetrics;
  leaderboard: ModelLeaderboardEntry[];
  categoryDistribution: Record<string, number>;
  recentResults: EvaluationResultDto[];
  recentAbTests: AbTestResultDto[];
}

export async function fetchEvaluationSummaryApi(
  signal?: AbortSignal,
): Promise<EvaluationSummaryDto | null> {
  try {
    const res = await fetch("/api/evaluation/summary", { signal });
    if (!res.ok) return null;
    return (await res.json()) as EvaluationSummaryDto;
  } catch {
    return null;
  }
}

export async function fetchBenchmarksApi(
  category?: string,
  signal?: AbortSignal,
): Promise<BenchmarkCase[]> {
  try {
    const url = category
      ? `/api/evaluation/benchmarks?category=${encodeURIComponent(category)}`
      : "/api/evaluation/benchmarks";
    const res = await fetch(url, { signal });
    if (!res.ok) return [];
    return (await res.json()) as BenchmarkCase[];
  } catch {
    return [];
  }
}

export async function addBenchmarkApi(
  benchmark: Partial<BenchmarkCase>,
): Promise<BenchmarkCase | null> {
  try {
    const res = await fetch("/api/evaluation/benchmarks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(benchmark),
    });
    if (!res.ok) return null;
    return (await res.json()) as BenchmarkCase;
  } catch {
    return null;
  }
}

export async function deleteBenchmarkApi(id: string): Promise<boolean> {
  try {
    const res = await fetch(`/api/evaluation/benchmarks/${id}`, {
      method: "DELETE",
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function runBatchEvaluationApi(
  provider: string,
  model: string,
  judgeProvider?: string,
  judgeModel?: string,
  benchmarkIds?: string[],
  category?: string,
): Promise<EvaluationResultDto[]> {
  try {
    const res = await fetch("/api/evaluation/run", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        provider,
        model,
        judgeProvider,
        judgeModel,
        benchmarkIds,
        category,
      }),
    });
    if (!res.ok) return [];
    return (await res.json()) as EvaluationResultDto[];
  } catch {
    return [];
  }
}

export async function runAbTestApi(
  prompt: string,
  providerA: string,
  modelA: string,
  providerB: string,
  modelB: string,
  context?: string,
  expectedOutput?: string,
  judgeProvider?: string,
  judgeModel?: string,
): Promise<AbTestResultDto | null> {
  try {
    const res = await fetch("/api/evaluation/ab-test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt,
        context,
        expectedOutput,
        providerA,
        modelA,
        providerB,
        modelB,
        judgeProvider,
        judgeModel,
      }),
    });
    if (!res.ok) return null;
    return (await res.json()) as AbTestResultDto;
  } catch {
    return null;
  }
}

export async function judgeSingleApi(
  prompt: string,
  responseText: string,
  context?: string,
  expectedOutput?: string,
  judgeProvider?: string,
  judgeModel?: string,
): Promise<EvaluationResultDto | null> {
  try {
    const res = await fetch("/api/evaluation/judge-single", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt,
        responseText,
        context,
        expectedOutput,
        judgeProvider,
        judgeModel,
      }),
    });
    if (!res.ok) return null;
    return (await res.json()) as EvaluationResultDto;
  } catch {
    return null;
  }
}

export async function annotateEvaluationResultApi(
  id: string,
  humanScore: number,
  humanAnnotation: string,
): Promise<EvaluationResultDto | null> {
  try {
    const res = await fetch(`/api/evaluation/results/${id}/annotate`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ humanScore, humanAnnotation }),
    });
    if (!res.ok) return null;
    return (await res.json()) as EvaluationResultDto;
  } catch {
    return null;
  }
}
