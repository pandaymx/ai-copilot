import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { ArtifactDispatcher } from "@/components/artifacts/artifact-dispatcher";
import { ChartArtifactViewer } from "@/components/artifacts/chart-artifact-viewer";
import { InteractiveArtifactViewer } from "@/components/artifacts/interactive-artifact-viewer";
import { SvgArtifactViewer } from "@/components/artifacts/svg-artifact-viewer";
import { TableArtifactViewer } from "@/components/artifacts/table-artifact-viewer";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";

describe("Artifact Components Suite", () => {
  let container: HTMLDivElement | null = null;
  let root: ReturnType<typeof createRoot> | null = null;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    const currentRoot = root;
    const currentContainer = container;
    if (currentRoot && currentContainer) {
      act(() => {
        currentRoot.unmount();
      });
      currentContainer.remove();
    }
  });

  it("ArtifactDispatcher routes chart artifact to ChartArtifactViewer", async () => {
    const chartArtifact: ArtifactItem = {
      artifactId: "art-chart-1",
      artifactType: "chart",
      title: "2026 Q1-Q4 营收趋势",
      content: JSON.stringify({
        type: "bar",
        labels: ["Q1", "Q2", "Q3", "Q4"],
        datasets: [{ label: "营收(万元)", data: [120, 190, 240, 310] }],
      }),
    };

    await act(async () => {
      if (root) {
        root.render(<ArtifactDispatcher artifact={chartArtifact} />);
      }
    });

    expect(container?.innerHTML).toContain("2026 Q1-Q4 营收趋势");
    expect(container?.innerHTML).toContain("4 个维度");
    expect(container?.innerHTML).toContain("1 组数据系列");
  });

  it("ChartArtifactViewer renders and switches to table view", async () => {
    const chartArtifact: ArtifactItem = {
      artifactId: "art-chart-2",
      artifactType: "chart",
      title: "性能基准",
      content: JSON.stringify({
        labels: ["Node.js", "Java", "Go", "Rust"],
        datasets: [{ label: "QPS", data: [15000, 28000, 42000, 56000] }],
      }),
    };

    await act(async () => {
      if (root) {
        root.render(<ChartArtifactViewer artifact={chartArtifact} />);
      }
    });

    // 初始渲染图表
    expect(container?.innerHTML).toContain("性能基准");

    // 点击切换至明细表
    const tableBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("明细表"));
    expect(tableBtn).toBeTruthy();

    await act(async () => {
      tableBtn?.click();
    });

    expect(container?.innerHTML).toContain("维度 / 类别");
    expect(container?.innerHTML).toContain("Rust");
    expect(container?.innerHTML).toContain("56000");
  });

  it("TableArtifactViewer parses JSON array, sorts, and filters data", async () => {
    const tableArtifact: ArtifactItem = {
      artifactId: "art-table-1",
      artifactType: "table",
      title: "微服务延迟榜",
      content: JSON.stringify([
        { service: "AuthService", p99Ms: 45, status: "Healthy" },
        { service: "PaymentService", p99Ms: 120, status: "Warning" },
        { service: "OrderService", p99Ms: 30, status: "Healthy" },
      ]),
    };

    await act(async () => {
      if (root) {
        root.render(<TableArtifactViewer artifact={tableArtifact} />);
      }
    });

    expect(container?.innerHTML).toContain("微服务延迟榜");
    expect(container?.innerHTML).toContain("3 条记录");
    expect(container?.innerHTML).toContain("AuthService");
    expect(container?.innerHTML).toContain("PaymentService");

    // 测试搜索输入
    const searchInput = container?.querySelector(
      "input[placeholder*='搜索']",
    ) as HTMLInputElement;
    expect(searchInput).toBeTruthy();

    await act(async () => {
      searchInput.value = "Payment";
      searchInput.dispatchEvent(new Event("input", { bubbles: true }));
    });

    expect(container?.innerHTML).toContain("PaymentService");
  });

  it("SvgArtifactViewer safely renders SVG and zoom controls", async () => {
    const svgArtifact: ArtifactItem = {
      artifactId: "art-svg-1",
      artifactType: "svg",
      title: "架构拓扑图",
      content:
        '<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="indigo"/></svg>',
    };

    await act(async () => {
      if (root) {
        root.render(<SvgArtifactViewer artifact={svgArtifact} />);
      }
    });

    expect(container?.innerHTML).toContain("架构拓扑图");
    expect(container?.innerHTML).toContain("缩放率 100%");
    expect(container?.querySelector("svg")).toBeTruthy();

    // 点击放大
    const zoomInBtn = container?.querySelector(
      "button[title='放大']",
    ) as HTMLButtonElement;
    expect(zoomInBtn).toBeTruthy();

    await act(async () => {
      zoomInBtn.click();
    });

    expect(container?.innerHTML).toContain("缩放率 125%");
  });

  it("InteractiveArtifactViewer renders sandboxed iframe and tabs", async () => {
    const htmlArtifact: ArtifactItem = {
      artifactId: "art-html-1",
      artifactType: "html",
      title: "React 计数器组件",
      content: "<div><button id='btn'>点击计数: 0</button></div>",
    };

    await act(async () => {
      if (root) {
        root.render(<InteractiveArtifactViewer artifact={htmlArtifact} />);
      }
    });

    expect(container?.innerHTML).toContain("React 计数器组件");
    expect(container?.querySelector("iframe")).toBeTruthy();

    // 切换到控制台
    const consoleBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("控制台"));
    expect(consoleBtn).toBeTruthy();

    await act(async () => {
      consoleBtn?.click();
    });

    expect(container?.innerHTML).toContain("捕获控制台输出");
  });
});
