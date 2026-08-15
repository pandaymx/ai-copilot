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
import { KnowledgeSourceManager } from "@/components/knowledge/knowledge-source-manager";

describe("Knowledge Auto Sync Suite", () => {
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

  it("KnowledgeSourceManager renders title, controls, and Add Source button", async () => {
    await act(async () => {
      if (root) {
        root.render(<KnowledgeSourceManager />);
      }
    });

    expect(container?.innerHTML).toContain("知识库自动增量同步数据源");
    expect(container?.innerHTML).toContain("添加自动同步数据源");
    expect(container?.innerHTML).toContain("刷新状态");
  });

  it("Clicking Add Source button opens the modal wizard", async () => {
    await act(async () => {
      if (root) {
        root.render(<KnowledgeSourceManager />);
      }
    });

    const addBtn = Array.from(container?.querySelectorAll("button") || []).find(
      (b) => b.textContent?.includes("添加自动同步数据源"),
    );
    expect(addBtn).toBeTruthy();

    await act(async () => {
      addBtn?.click();
    });

    expect(container?.innerHTML).toContain("新建自动同步知识源");
    expect(container?.innerHTML).toContain("GitHub 仓库地址");
    expect(container?.innerHTML).toContain("自动同步频率 (Cron)");
  });
});
