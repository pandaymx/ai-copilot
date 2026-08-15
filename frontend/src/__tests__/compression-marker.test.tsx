import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, describe, expect, it } from "bun:test";
import type React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { CompressionMarker } from "../components/chat/compression-marker";
import type { CompressionMetadata } from "@/lib/api";

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

function renderComponent(ui: React.ReactNode) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(ui);
  });
  return {
    container,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

describe("CompressionMarker Component Tests", () => {
  it("should render metadata stats correctly and allow expanding summary", () => {
    const meta: CompressionMetadata = {
      compressedTurnCount: 5,
      originalTokens: 4000,
      compressedTokens: 800,
      level: "LIGHT",
      summarySnippet: "讨论了微服务架构拆分与Redis缓存策略",
      fallback: false,
    };

    const { container, unmount } = renderComponent(
      <CompressionMarker metadata={meta} />,
    );

    expect(container.textContent).toContain("上下文已智能压缩");
    expect(container.textContent).toContain("5 轮对话已摘要");
    expect(container.textContent).toContain("轻度压缩");
    expect(container.textContent).toContain("4,000");
    expect(container.textContent).toContain("800");
    expect(container.textContent).toContain("节省 ~80%");

    // 默认折叠，不显示详细内容区
    expect(container.textContent).not.toContain("历史上下文核心摘要");

    // 点击展开
    const buttons = container.querySelectorAll("button");
    const expandBtn = Array.from(buttons).find((b) =>
      b.textContent?.includes("展开摘要"),
    );
    expect(expandBtn).toBeDefined();

    act(() => {
      expandBtn?.click();
    });

    expect(container.textContent).toContain("历史上下文核心摘要");
    expect(container.textContent).toContain("讨论了微服务架构拆分与Redis缓存策略");
    expect(container.textContent).toContain("最近 2~4 轮原始问答已完整保留");

    unmount();
  });

  it("should parse [COMPRESSED:N turns] text tag when metadata is absent", () => {
    const raw = "[COMPRESSED:3 turns] 这里是之前多轮关于数据库索引优化的摘要";

    const { container, unmount } = renderComponent(
      <CompressionMarker rawText={raw} />,
    );

    expect(container.textContent).toContain("上下文已智能压缩");
    expect(container.textContent).toContain("3 轮对话已摘要");

    const buttons = container.querySelectorAll("button");
    const expandBtn = Array.from(buttons).find((b) =>
      b.textContent?.includes("展开摘要"),
    );

    act(() => {
      expandBtn?.click();
    });

    expect(container.textContent).toContain("这里是之前多轮关于数据库索引优化的摘要");
    unmount();
  });

  it("should display fallback badge when compression is downgraded", () => {
    const meta: CompressionMetadata = {
      compressedTurnCount: 4,
      originalTokens: 2000,
      compressedTokens: 0,
      level: "KEYWORDS",
      summarySnippet: "",
      fallback: true,
    };

    const { container, unmount } = renderComponent(
      <CompressionMarker metadata={meta} />,
    );

    expect(container.textContent).toContain("降级截断");
    unmount();
  });
});
