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

// ====================== 上下文压缩（Smart Context Compression） ======================

export interface CompressionMetadata {
  compressedTurnCount: number;
  originalTokens: number;
  compressedTokens: number;
  level: "LIGHT" | "DEEP" | "KEYWORDS";
  summarySnippet: string;
  fallback: boolean;
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

// ====================== 文档对话 (Chat with Document) API ======================

export interface DocumentCitationItem {
  citationId: string;
  docId: string;
  fileName: string;
  pageNumber?: string;
  paragraphIndex?: string;
  snippet?: string;
  similarityScore?: number;
}

export interface DocChatDocItem {
  docId: string;
  conversationId: string;
  fileName: string;
  sourceType: string;
  chunkCount: number;
  ingestedAt: string;
}

export interface DocChunkItem {
  chunkId: string;
  docId: string;
  fileName: string;
  pageNumber: string;
  paragraphIndex: string;
  content: string;
}

export interface DocChatIngestPayload {
  conversationId: string;
  sourceType: string;
  fileName?: string;
  rawText?: string;
  targetUrl?: string;
  fileStoragePath?: string;
}

/** 上传/挂载文档至指定会话（会话级向量索引） */
export async function ingestDocChatDocumentApi(
  payload: DocChatIngestPayload,
): Promise<DocChatDocItem | null> {
  try {
    const res = await fetch("/api/rag/doc-chat/ingest", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) return null;
    return (await res.json()) as DocChatDocItem;
  } catch {
    return null;
  }
}

/** 获取指定会话挂载的所有文档 */
export async function fetchDocChatDocumentsApi(
  conversationId: string,
): Promise<DocChatDocItem[]> {
  if (!conversationId) return [];
  try {
    const res = await fetch(
      `/api/rag/doc-chat/documents?conversationId=${encodeURIComponent(conversationId)}`,
    );
    if (!res.ok) return [];
    return (await res.json()) as DocChatDocItem[];
  } catch {
    return [];
  }
}

/** 从会话中移除/删除挂载的文档 */
export async function deleteDocChatDocumentApi(
  docId: string,
  conversationId: string,
): Promise<{ success: boolean; docId: string } | null> {
  try {
    const res = await fetch(
      `/api/rag/doc-chat/documents/${encodeURIComponent(docId)}?conversationId=${encodeURIComponent(conversationId)}`,
      {
        method: "DELETE",
      },
    );
    if (!res.ok) return null;
    return (await res.json()) as { success: boolean; docId: string };
  } catch {
    return null;
  }
}

/** 获取文档切片列表（供原文对照抽屉高亮与渲染） */
export async function fetchDocChatChunksApi(
  docId: string,
  conversationId?: string,
): Promise<DocChunkItem[]> {
  if (!docId) return [];
  try {
    const url = conversationId
      ? `/api/rag/doc-chat/chunks/${encodeURIComponent(docId)}?conversationId=${encodeURIComponent(conversationId)}`
      : `/api/rag/doc-chat/chunks/${encodeURIComponent(docId)}`;
    const res = await fetch(url);
    if (!res.ok) return [];
    return (await res.json()) as DocChunkItem[];
  } catch {
    return [];
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

export interface RealtimeUsageData {
  month: string;
  usedTokens: number;
  quotaTokens: number;
  remainingTokens: number;
  usedPercent: number;
  alertThresholdPercent: number;
}

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

/** 获取用户本月实时 Token 配额与消耗状态（基于 Redis，不查 DB）。 */
export async function fetchRealtimeUsageApi(
  signal?: AbortSignal,
): Promise<RealtimeUsageData | null> {
  try {
    const res = await fetch("/api/usage/realtime", { signal });
    if (!res.ok) return null;
    return (await res.json()) as RealtimeUsageData;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
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

// ---------------------------------------------------------------------------
// 知识图谱（Knowledge Graph / GraphRAG）接口
// ---------------------------------------------------------------------------

export interface KnowledgeEntity {
  id: string;
  userId?: string;
  documentId?: string;
  name: string;
  type:
    | "CONCEPT"
    | "TECHNOLOGY"
    | "COMPONENT"
    | "ORGANIZATION"
    | "PERSON"
    | "OTHER"
    | string;
  description?: string;
  weight?: number;
  createdAt?: number;
}

export interface KnowledgeRelation {
  id: string;
  userId?: string;
  documentId?: string;
  sourceEntityName: string;
  relation: string;
  targetEntityName: string;
  description?: string;
  weight?: number;
  createdAt?: number;
}

export interface GraphStatsDto {
  totalNodes: number;
  totalEdges: number;
  totalDocuments: number;
  nodeTypeDistribution: Record<string, number>;
  relationTypeDistribution: Record<string, number>;
}

export interface KnowledgeGraphDto {
  nodes: KnowledgeEntity[];
  edges: KnowledgeRelation[];
  stats?: GraphStatsDto;
}

export async function ragGraphApi(
  documentId?: string,
  userId?: string,
): Promise<KnowledgeGraphDto | null> {
  try {
    const params = new URLSearchParams();
    if (documentId) params.append("documentId", documentId);
    if (userId) params.append("userId", userId);
    const res = await fetch(`/api/rag/graph?${params.toString()}`);
    if (!res.ok) return null;
    return (await res.json()) as KnowledgeGraphDto;
  } catch {
    return null;
  }
}

export async function ragGraphSubgraphApi(params: {
  seeds?: string;
  query?: string;
  maxHops?: number;
  maxNodes?: number;
  userId?: string;
}): Promise<KnowledgeGraphDto | null> {
  try {
    const searchParams = new URLSearchParams();
    if (params.seeds) searchParams.append("seeds", params.seeds);
    if (params.query) searchParams.append("query", params.query);
    if (params.maxHops) searchParams.append("maxHops", String(params.maxHops));
    if (params.maxNodes)
      searchParams.append("maxNodes", String(params.maxNodes));
    if (params.userId) searchParams.append("userId", params.userId);
    const res = await fetch(
      `/api/rag/graph/subgraph?${searchParams.toString()}`,
    );
    if (!res.ok) return null;
    return (await res.json()) as KnowledgeGraphDto;
  } catch {
    return null;
  }
}

export async function ragGraphStatsApi(
  userId?: string,
): Promise<GraphStatsDto | null> {
  try {
    const params = new URLSearchParams();
    if (userId) params.append("userId", userId);
    const res = await fetch(`/api/rag/graph/stats?${params.toString()}`);
    if (!res.ok) return null;
    return (await res.json()) as GraphStatsDto;
  } catch {
    return null;
  }
}

export async function ragGraphExtractApi(
  rawText: string,
  documentId?: string,
  userId?: string,
): Promise<KnowledgeGraphDto | null> {
  try {
    const res = await fetch("/api/rag/graph/extract", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rawText, documentId, userId }),
    });
    if (!res.ok) return null;
    return (await res.json()) as KnowledgeGraphDto;
  } catch {
    return null;
  }
}

export async function ragGraphDeleteDocumentApi(
  documentId: string,
  userId?: string,
): Promise<boolean> {
  try {
    const params = new URLSearchParams();
    if (userId) params.append("userId", userId);
    const res = await fetch(
      `/api/rag/graph/documents/${encodeURIComponent(documentId)}?${params.toString()}`,
      {
        method: "DELETE",
      },
    );
    return res.ok;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 向量生命周期管理（Embedding Lifecycle Management）接口
// ---------------------------------------------------------------------------

export interface HealthIssue {
  documentId: string;
  fileName: string;
  issueType: string;
  description: string;
  severity: "INFO" | "WARNING" | "CRITICAL" | string;
}

export interface EmbeddingHealthDto {
  totalVectors: number;
  healthyVectors: number;
  emptyOrZeroVectors: number;
  dimensionMismatchCount: number;
  modelMismatchCount: number;
  staleVectorsCount: number;
  activeModelName: string;
  activeModelDimensions: number;
  healthScore: number;
  status: "HEALTHY" | "WARNING" | "CRITICAL" | string;
  dimensionDistribution: Record<string, number>;
  issues: HealthIssue[];
}

export interface EmbeddingReindexTaskDto {
  taskId: string;
  total: number;
  processed: number;
  successCount: number;
  failedCount: number;
  lastProcessedId?: string;
  targetModel: string;
  targetDimension: number;
  isRunning: boolean;
  isPaused: boolean;
  startedAt: number;
  finishedAt?: number;
  errorSummary: string[];
}

export interface DocumentSimilarityClusterDto {
  clusterId: string;
  similarityScore: number;
  docAId: string;
  docAName: string;
  docAExcerpt: string;
  docBId: string;
  docBName: string;
  docBExcerpt: string;
  conflictType:
    | "INTRA_DOC_OVERLAP"
    | "CROSS_DOC_DUPLICATE"
    | "SEMANTIC_CONFLICT"
    | string;
  suggestedAction:
    | "KEEP_BOTH"
    | "MERGE"
    | "DELETE_OLDER"
    | "DELETE_DOC_B"
    | string;
}

export interface StaleVectorDto {
  id: string;
  fileName: string;
  sourceType: string;
  content: string;
  createdAt: number;
  hitCount: number;
  lastHitTime?: number;
  isArchived: boolean;
}

export async function embeddingHealthApi(
  userId?: string,
): Promise<EmbeddingHealthDto | null> {
  try {
    const params = new URLSearchParams();
    if (userId) params.append("userId", userId);
    const res = await fetch(`/api/rag/embeddings/health?${params.toString()}`);
    if (!res.ok) return null;
    return (await res.json()) as EmbeddingHealthDto;
  } catch {
    return null;
  }
}

export async function embeddingReembedStartApi(
  force?: boolean,
  userId?: string,
): Promise<EmbeddingReindexTaskDto | null> {
  try {
    const params = new URLSearchParams();
    if (force) params.append("force", "true");
    if (userId) params.append("userId", userId);
    const res = await fetch(
      `/api/rag/embeddings/reembed/start?${params.toString()}`,
      {
        method: "POST",
      },
    );
    if (!res.ok) return null;
    return (await res.json()) as EmbeddingReindexTaskDto;
  } catch {
    return null;
  }
}

export async function embeddingReembedStatusApi(): Promise<EmbeddingReindexTaskDto | null> {
  try {
    const res = await fetch("/api/rag/embeddings/reembed/status");
    if (!res.ok) return null;
    return (await res.json()) as EmbeddingReindexTaskDto;
  } catch {
    return null;
  }
}

export async function embeddingReembedPauseApi(): Promise<boolean> {
  try {
    const res = await fetch("/api/rag/embeddings/reembed/pause", {
      method: "POST",
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function embeddingReembedResumeApi(): Promise<boolean> {
  try {
    const res = await fetch("/api/rag/embeddings/reembed/resume", {
      method: "POST",
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function embeddingSimilarityClustersApi(
  minSimilarity?: number,
  limit?: number,
  userId?: string,
): Promise<DocumentSimilarityClusterDto[] | null> {
  try {
    const params = new URLSearchParams();
    if (minSimilarity) params.append("minSimilarity", String(minSimilarity));
    if (limit) params.append("limit", String(limit));
    if (userId) params.append("userId", userId);
    const res = await fetch(
      `/api/rag/embeddings/similarity-clusters?${params.toString()}`,
    );
    if (!res.ok) return null;
    return (await res.json()) as DocumentSimilarityClusterDto[];
  } catch {
    return null;
  }
}

export async function embeddingStaleVectorsApi(
  retentionDays?: number,
  limit?: number,
  userId?: string,
): Promise<StaleVectorDto[] | null> {
  try {
    const params = new URLSearchParams();
    if (retentionDays) params.append("retentionDays", String(retentionDays));
    if (limit) params.append("limit", String(limit));
    if (userId) params.append("userId", userId);
    const res = await fetch(`/api/rag/embeddings/stale?${params.toString()}`);
    if (!res.ok) return null;
    return (await res.json()) as StaleVectorDto[];
  } catch {
    return null;
  }
}

export async function embeddingArchiveStaleApi(
  docIds: string[],
  userId?: string,
): Promise<{ success: boolean; archivedCount: number } | null> {
  try {
    const res = await fetch("/api/rag/embeddings/stale/archive", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ docIds, userId }),
    });
    if (!res.ok) return null;
    return (await res.json()) as { success: boolean; archivedCount: number };
  } catch {
    return null;
  }
}

export async function embeddingPurgeStaleApi(
  docIds: string[],
  userId?: string,
): Promise<{ success: boolean; purgedCount: number } | null> {
  try {
    const res = await fetch("/api/rag/embeddings/stale/purge", {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ docIds, userId }),
    });
    if (!res.ok) return null;
    return (await res.json()) as { success: boolean; purgedCount: number };
  } catch {
    return null;
  }
}

// ====================== 多语言翻译引擎 API ======================

export interface TranslateRequest {
  text: string;
  targetLang: string;
  sourceLang?: string;
  glossary?: Record<string, string>;
  provider?: string;
  model?: string;
  preserveFormatting?: boolean;
}

export interface TranslateResponse {
  originalText: string;
  sourceLang: string;
  targetLang: string;
  detectedLang: string;
  translatedText: string;
  glossaryAppliedCount: number;
  latencyMs: number;
}

export interface SupportedLanguage {
  code: string;
  name: string;
  nativeName: string;
}

/** 执行多语言即时翻译。 */
export async function translateApi(
  request: TranslateRequest,
  signal?: AbortSignal,
): Promise<TranslateResponse | null> {
  try {
    const res = await fetch("/api/translate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      signal,
    });
    if (!res.ok) return null;
    return (await res.json()) as TranslateResponse;
  } catch (err: unknown) {
    if ((err as Error)?.name === "AbortError") return null;
    return null;
  }
}

/** 获取支持的标准语种列表。 */
export async function fetchSupportedLanguagesApi(
  signal?: AbortSignal,
): Promise<SupportedLanguage[]> {
  try {
    const res = await fetch("/api/translate/languages", { signal });
    if (!res.ok) return [];
    return (await res.json()) as SupportedLanguage[];
  } catch {
    return [];
  }
}
