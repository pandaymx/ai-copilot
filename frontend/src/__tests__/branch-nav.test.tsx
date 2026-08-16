import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import type React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { BranchNav } from "../components/chat/branch-nav";
import type { BranchSummary } from "../lib/branch-api";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

const mockBranches: BranchSummary[] = [
  {
    branchId: "br-main",
    sessionId: "sess-1",
    branchLabel: "主线方案",
    parentBranchId: null,
    forkFromMessageId: null,
    messageCount: 5,
    createdAt: 1000,
    updatedAt: 1000,
  },
  {
    branchId: "br-explore",
    sessionId: "sess-1",
    branchLabel: "重构探索分支",
    parentBranchId: "br-main",
    forkFromMessageId: "msg-1",
    messageCount: 3,
    createdAt: 2000,
    updatedAt: 2000,
  },
];

beforeEach(() => {
  mockFetch = mock().mockResolvedValue(
    new Response(JSON.stringify(mockBranches), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
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

describe("BranchNav Component Tests", () => {
  it("should render active branch and list branches on menu click", async () => {
    let selectedBranch = "br-main";

    const { container, unmount } = renderComponent(
      <BranchNav
        sessionId="sess-1"
        activeBranchId={selectedBranch}
        onSelectBranch={(id) => {
          selectedBranch = id;
        }}
      />,
    );

    // 等待数据加载
    await act(async () => {
      await new Promise((r) => setTimeout(r, 40));
    });

    expect(container.textContent).toContain("主线方案");

    // 点击菜单展开按钮
    const triggerBtn = container.querySelector("button") as HTMLButtonElement;
    expect(triggerBtn).toBeDefined();

    await act(async () => {
      triggerBtn.click();
    });

    expect(container.textContent).toContain("重构探索分支");
    expect(container.textContent).toContain("新建空白分支");

    unmount();
  });
});
