import { getStoredToken } from "./auth-api";

export interface TestCaseResult {
  id: string;
  category: string;
  prompt: string;
  isAdversarial: boolean;
  blocked: boolean;
  triggerType: string;
  matchedRule: string;
  passed: boolean;
}

export interface CategoryMetric {
  total: number;
  blocked: number;
  bypassed: number;
  blockRatePct: number;
}

export interface RedTeamReport {
  runId: string;
  userId: string;
  totalTests: number;
  blockedCount: number;
  bypassCount: number;
  hitRatePct: number;
  categoryBreakdown: Record<string, CategoryMetric>;
  testResults: TestCaseResult[];
  createdAt: number;
}

export interface RedTeamRunHistoryItem {
  id: string;
  userId: string;
  totalTests: number;
  blockedCount: number;
  bypassCount: number;
  hitRatePct: number;
  detailsJson: string;
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

export async function runRedTeamEvaluation(rounds = 5): Promise<RedTeamReport> {
  const res = await fetch("/api/safeguard/redteam/run", {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify({ rounds }),
  });
  if (!res.ok) {
    throw new Error("执行红队安全评估失败");
  }
  return res.json();
}

export async function listRedTeamRuns(
  limit = 10,
): Promise<RedTeamRunHistoryItem[]> {
  const res = await fetch(
    `/api/safeguard/redteam/runs?limit=${encodeURIComponent(limit)}`,
    {
      headers: getAuthHeaders(),
    },
  );
  if (!res.ok) return [];
  return res.json();
}
