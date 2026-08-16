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
import { KnowledgeGraphViewer } from "@/components/knowledge/knowledge-graph-viewer";
import type * as api from "@/lib/api";

const originalFetch = globalThis.fetch;

describe("KnowledgeGraphViewer Component", () => {
  let container: HTMLDivElement;
  let root: ReturnType<typeof createRoot>;

  const mockGraphData: api.KnowledgeGraphDto = {
    nodes: [
      {
        id: "e-1",
        name: "Spring Boot",
        type: "TECHNOLOGY",
        description: "微服务框架",
        weight: 1.0,
      },
      {
        id: "e-2",
        name: "GraphRAG",
        type: "CONCEPT",
        description: "知识图谱增强检索",
        weight: 0.9,
      },
    ],
    edges: [
      {
        id: "r-1",
        sourceEntityName: "Spring Boot",
        relation: "INTEGRATES_WITH",
        targetEntityName: "GraphRAG",
        description: "集成",
      },
    ],
    stats: {
      totalNodes: 2,
      totalEdges: 1,
      totalDocuments: 1,
      nodeTypeDistribution: { TECHNOLOGY: 1, CONCEPT: 1 },
      relationTypeDistribution: { INTEGRATES_WITH: 1 },
    },
  };

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);

    globalThis.fetch = mock(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.includes("/api/rag/graph/stats")) {
        return new Response(JSON.stringify(mockGraphData.stats), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (url.includes("/api/rag/graph/subgraph")) {
        return new Response(JSON.stringify(mockGraphData), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (url.includes("/api/rag/graph")) {
        return new Response(JSON.stringify(mockGraphData), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response("{}", { status: 200 });
    }) as unknown as typeof fetch;

    if (HTMLCanvasElement.prototype) {
      HTMLCanvasElement.prototype.getContext = mock(() => ({
        clearRect: () => {},
        save: () => {},
        restore: () => {},
        translate: () => {},
        scale: () => {},
        beginPath: () => {},
        arc: () => {},
        fill: () => {},
        stroke: () => {},
        moveTo: () => {},
        lineTo: () => {},
        closePath: () => {},
        fillText: () => {},
      })) as unknown as typeof HTMLCanvasElement.prototype.getContext;
    }
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("renders graph control headers and canvas", async () => {
    await act(async () => {
      root.render(<KnowledgeGraphViewer />);
    });

    expect(container.textContent).toContain("GraphRAG 知识图谱拓扑网络");
    expect(container.textContent).toContain("三元组抽取");
  });

  it("renders type filter pills", async () => {
    await act(async () => {
      root.render(<KnowledgeGraphViewer />);
    });

    expect(container.textContent).toContain("TECHNOLOGY");
    expect(container.textContent).toContain("CONCEPT");
    expect(container.textContent).toContain("COMPONENT");
    expect(container.textContent).toContain("ORGANIZATION");
  });
});
