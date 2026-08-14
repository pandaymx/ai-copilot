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
import { EmbeddingManagementView } from "@/components/knowledge/embedding-management-view";
import type * as api from "@/lib/api";

describe("EmbeddingManagementView Component", () => {
  let container: HTMLDivElement;
  let root: ReturnType<typeof createRoot>;

  const mockHealth: api.EmbeddingHealthDto = {
    totalVectors: 150,
    healthyVectors: 140,
    emptyOrZeroVectors: 2,
    dimensionMismatchCount: 0,
    modelMismatchCount: 8,
    staleVectorsCount: 12,
    activeModelName: "text-embedding-3-small",
    activeModelDimensions: 1536,
    healthScore: 88,
    status: "HEALTHY",
    dimensionDistribution: { "1536": 150 },
    issues: [
      {
        documentId: "doc-1",
        fileName: "test.pdf",
        issueType: "MODEL_MISMATCH",
        description: "模型失配",
        severity: "WARNING",
      },
    ],
  };

  const mockTask: api.EmbeddingReindexTaskDto = {
    taskId: "task-test-1",
    total: 150,
    processed: 75,
    successCount: 75,
    failedCount: 0,
    targetModel: "text-embedding-3-small",
    targetDimension: 1536,
    isRunning: false,
    isPaused: false,
    startedAt: 1000,
    errorSummary: [],
  };

  const mockClusters: api.DocumentSimilarityClusterDto[] = [
    {
      clusterId: "c-1",
      similarityScore: 0.96,
      docAId: "d1",
      docAName: "guide_v1.md",
      docAExcerpt: "Spring Boot 指南第一版",
      docBId: "d2",
      docBName: "guide_v2.md",
      docBExcerpt: "Spring Boot 指南第二版",
      conflictType: "CROSS_DOC_DUPLICATE",
      suggestedAction: "DELETE_DOC_B",
    },
  ];

  const mockStale: api.StaleVectorDto[] = [
    {
      id: "stale-1",
      fileName: "old_notes.txt",
      sourceType: "TEXT",
      content: "40 天前的内容",
      createdAt: Date.now() - 40 * 24 * 3600 * 1000,
      hitCount: 0,
      isArchived: false,
    },
  ];

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);

    globalThis.fetch = mock(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.includes("/api/rag/embeddings/health")) {
        return new Response(JSON.stringify(mockHealth), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (url.includes("/api/rag/embeddings/reembed/status")) {
        return new Response(JSON.stringify(mockTask), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (url.includes("/api/rag/embeddings/similarity-clusters")) {
        return new Response(JSON.stringify(mockClusters), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (url.includes("/api/rag/embeddings/stale")) {
        return new Response(JSON.stringify(mockStale), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response("{}", { status: 200 });
    }) as unknown as typeof fetch;
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("renders health overview, KPI cards and active model badge", async () => {
    await act(async () => {
      root.render(<EmbeddingManagementView />);
    });

    expect(container.textContent).toContain("Embedding 向量生命周期大盘");
    expect(container.textContent).toContain("text-embedding-3-small");
    expect(container.textContent).toContain("88");
    expect(container.textContent).toContain("150");
  });

  it("renders re-embedding pipeline and similarity clusters", async () => {
    await act(async () => {
      root.render(<EmbeddingManagementView />);
    });

    expect(container.textContent).toContain("模型切换与批量重嵌入管道");
    expect(container.textContent).toContain("一键全量重新向量化");
    expect(container.textContent).toContain("向量相似度地图与冲突聚类");
    expect(container.textContent).toContain("guide_v1.md");
    expect(container.textContent).toContain("CROSS_DOC_DUPLICATE");
  });

  it("renders stale vectors table", async () => {
    await act(async () => {
      root.render(<EmbeddingManagementView />);
    });

    expect(container.textContent).toContain("冷数据死向量管理");
    expect(container.textContent).toContain("old_notes.txt");
  });
});
