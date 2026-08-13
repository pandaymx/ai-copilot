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
import {
  parseThinkingToSteps,
  ReasoningView,
} from "../components/chat/reasoning-view";

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

describe("ReasoningView Component & parseThinkingToSteps Tests", () => {
  it("should parse raw thinking into structured steps with categories and decision points", () => {
    const rawThinking = `
1. 目标分析：用户需要实现一个全栈 RAG 工具。
2. 决策判定：决定选择 Spring AI 作为后端 starter，并引入 PgVector 向量库。
3. 结论总结：经测试架构方案可行。
    `;

    const steps = parseThinkingToSteps(rawThinking);
    expect(steps.length).toBe(3);

    expect(steps[0].category).toBe("Goal");
    expect(steps[0].stepIndex).toBe(1);

    expect(steps[1].category).toBe("Decision");
    expect(steps[1].isDecision).toBe(true);

    expect(steps[2].category).toBe("Summary");
  });

  it("should render ReasoningView with duration and decision badges", () => {
    const rawThinking = `
1. 分析当前需求与依赖。
2. 关键决策：选择微任务队列进行批处理优化。
3. 确认总结测试通过。
    `;

    const { container, unmount } = renderComponent(
      <ReasoningView
        thinking={rawThinking}
        durationMs={3200}
        streaming={false}
      />,
    );

    expect(container.textContent).toContain("思维链推理过程");
    expect(container.textContent).toContain("用时 3.2s");
    expect(container.textContent).toContain("3 个步骤");
    expect(container.textContent).toContain("1 处决策点");
    expect(container.textContent).toContain("关键决策点");

    unmount();
  });

  it("should toggle expand and collapse when clicking header button", () => {
    const rawThinking = "Step 1. 分析逻辑\n\nStep 2. 结论方案";
    const { container, unmount } = renderComponent(
      <ReasoningView thinking={rawThinking} streaming={false} />,
    );

    expect(container.textContent).toContain("分析逻辑");

    const headerBtn = container.querySelector("button")!;
    act(() => {
      headerBtn.click();
    });

    expect(container.textContent).not.toContain("分析逻辑");

    unmount();
  });
});
