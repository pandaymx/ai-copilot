import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import EvaluationPage from "@/app/evaluation/page";

const mockSummary = {
  totalEvaluations: 12,
  totalAbTests: 4,
  averageScore: 0.92,
  dimensionAverages: {
    relevance: 0.95,
    accuracy: 0.93,
    completeness: 0.89,
    fluency: 0.94,
    safety: 1.0,
    overallScore: 0.92,
  },
  leaderboard: [
    {
      modelKey: "deepseek::deepseek-chat",
      provider: "deepseek",
      model: "deepseek-chat",
      count: 8,
      averageScore: 0.94,
      averageLatencyMs: 450,
      metrics: {
        relevance: 0.95,
        accuracy: 0.95,
        completeness: 0.9,
        fluency: 0.95,
        safety: 1.0,
        overallScore: 0.94,
      },
    },
  ],
  categoryDistribution: { "RAG 检索问答": 3, 代码生成与优化: 2 },
  recentResults: [],
  recentAbTests: [],
};

const mockBenchmarks = [
  {
    id: "bench-1",
    title: "Spring AI RAG 检索问答",
    category: "RAG 检索问答",
    prompt: "如何使用 Spring AI 实现向量检索？",
    expectedOutput: "使用 VectorStore 进行过滤",
    tags: ["RAG"],
  },
];

describe("EvaluationPage", () => {
  let container: HTMLDivElement | null = null;
  let root: ReturnType<typeof createRoot> | null = null;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);

    global.fetch = mock(async (url: string | URL | Request) => {
      const urlStr = url.toString();
      if (urlStr.includes("/summary")) {
        return new Response(JSON.stringify(mockSummary), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (urlStr.includes("/benchmarks")) {
        return new Response(JSON.stringify(mockBenchmarks), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({}), { status: 200 });
    }) as unknown as typeof fetch;
  });

  afterEach(() => {
    if (root) {
      act(() => {
        root?.unmount();
      });
    }
    if (container?.parentNode) {
      container.parentNode.removeChild(container);
    }
  });

  it("渲染评测大盘 KPI 卡片与排行榜", async () => {
    await act(async () => {
      root?.render(<EvaluationPage />);
    });

    await new Promise((r) => setTimeout(r, 50));

    expect(container?.textContent).toContain(
      "AI 评测与评估体系 (Evaluation Arena)",
    );
    expect(container?.textContent).toContain("自动化评测总轮次");
    expect(container?.textContent).toContain("12");
    expect(container?.textContent).toContain("deepseek-chat");
    expect(container?.textContent).toContain("94.0分");
  });

  it("切换到 A/B 盲测竞技场 Tab 并渲染对决表单", async () => {
    await act(async () => {
      root?.render(<EvaluationPage />);
    });

    await new Promise((r) => setTimeout(r, 50));

    const abTabBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("A/B 盲测竞技场"));
    expect(abTabBtn).toBeTruthy();

    await act(async () => {
      abTabBtn?.click();
    });

    await new Promise((r) => setTimeout(r, 50));

    expect(container?.textContent).toContain("Model A (对决方 A)");
    expect(container?.textContent).toContain("Model B (对决方 B)");
    expect(container?.textContent).toContain("发起 A/B 盲测对比评测");
  });
});
