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
import { MultiAgentModal } from "@/components/chat/multi-agent-modal";

describe("Multi-Agent Collaboration & DAG Orchestration Suite", () => {
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

  it("MultiAgentModal renders initial header, goal input, presets, and controls", async () => {
    const handleClose = mock(() => {});
    const handleAdopt = mock((_res: string) => {});

    await act(async () => {
      if (root) {
        root.render(
          <MultiAgentModal
            open={true}
            onClose={handleClose}
            initialGoal="对比 3 个框架性能"
            onAdopt={handleAdopt}
          />,
        );
      }
    });

    expect(container?.innerHTML).toContain("多 Agent 协同研讨与执行工作台");
    expect(container?.innerHTML).toContain("DAG 拓扑调度");
    expect(container?.innerHTML).toContain("开启人工裁决 (HITL)");
    expect(container?.innerHTML).toContain("发起多 Agent 协同");

    const input = container?.querySelector(
      "input[type='text']",
    ) as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(input.value).toBe("对比 3 个框架性能");
  });

  it("Preset buttons correctly populate goal input", async () => {
    await act(async () => {
      if (root) {
        root.render(
          <MultiAgentModal open={true} onClose={() => {}} initialGoal="" />,
        );
      }
    });

    const presetBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("对比 Quarkus"));
    expect(presetBtn).toBeTruthy();

    await act(async () => {
      presetBtn?.click();
    });

    const input = container?.querySelector(
      "input[type='text']",
    ) as HTMLInputElement;
    expect(input.value).toContain("Quarkus");
  });
});
