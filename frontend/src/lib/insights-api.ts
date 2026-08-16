import { getStoredToken } from "./auth-api";

export interface TopicCluster {
  topic: string;
  count: number;
  percentage: number;
  sampleSnippets: string[];
}

export interface QualityMetric {
  overallScore: number;
  relevance: number;
  clarity: number;
  accuracy: number;
  completeness: number;
  helpfulness: number;
}

export interface ModelDistribution {
  provider: string;
  model: string;
  messageCount: number;
  percentage: number;
}

export interface SatisfactionTrend {
  period: string;
  satisfactionScore: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
}

export interface InsightSummary {
  userId: string;
  totalConversations: number;
  totalMessages: number;
  topicClusters: TopicCluster[];
  quality: QualityMetric;
  modelDistribution: ModelDistribution[];
  satisfactionTrends: SatisfactionTrend[];
  generatedAt: number;
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

export async function getInsightSummary(): Promise<InsightSummary | null> {
  const res = await fetch("/api/insights/summary", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) return null;
  return res.json();
}

export async function refreshInsightSummary(): Promise<InsightSummary | null> {
  const res = await fetch("/api/insights/refresh", {
    method: "POST",
    headers: getAuthHeaders(),
  });
  if (!res.ok) return null;
  return res.json();
}
