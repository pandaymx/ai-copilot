import { describe, expect, it, mock } from "bun:test";
import { getInsightSummary, refreshInsightSummary } from "../lib/insights-api";

describe("insights-api client tests", () => {
  it("getInsightSummary returns structured data", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            userId: "user-1",
            totalConversations: 12,
            totalMessages: 58,
            topicClusters: [
              {
                topic: "代码开发与调试",
                count: 30,
                percentage: 51.7,
                sampleSnippets: ["如何优化 React 渲染性能"],
              },
            ],
            quality: {
              overallScore: 93.5,
              relevance: 95.0,
              clarity: 92.0,
              accuracy: 96.0,
              completeness: 90.0,
              helpfulness: 94.5,
            },
            modelDistribution: [
              {
                provider: "openai",
                model: "gpt-4o",
                messageCount: 40,
                percentage: 69.0,
              },
            ],
            satisfactionTrends: [
              {
                period: "2026-08-16",
                satisfactionScore: 96.0,
                positiveCount: 15,
                neutralCount: 3,
                negativeCount: 0,
              },
            ],
            generatedAt: 10000,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const data = await getInsightSummary();
    expect(data).not.toBeNull();
    expect(data?.totalConversations).toBe(12);
    expect(data?.quality.overallScore).toBe(93.5);
    expect(data?.topicClusters[0].topic).toBe("代码开发与调试");

    globalThis.fetch = originalFetch;
  });

  it("refreshInsightSummary posts refresh request", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            userId: "user-1",
            totalConversations: 15,
            totalMessages: 70,
            topicClusters: [],
            quality: { overallScore: 94.0 },
            modelDistribution: [],
            satisfactionTrends: [],
            generatedAt: 20000,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await refreshInsightSummary();
    expect(res).not.toBeNull();
    expect(res?.totalConversations).toBe(15);

    globalThis.fetch = originalFetch;
  });
});
