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
import type { TaskPlanState } from "@/hooks/useSpringAiStream";
import { TaskPlanCard } from "./task-plan-card";

describe("TaskPlanCard Component", () => {
  let container: HTMLDivElement;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
  });

  afterEach(() => {
    document.body.removeChild(container);
  });

  const mockPlan: TaskPlanState = {
    planId: "plan_test_001",
    title: "多模块自动化重构",
    goal: "清理废弃符号并生成单元测试",
    status: "EXECUTING",
    currentStep: 2,
    totalSteps: 2,
    steps: [
      {
        stepId: 1,
        title: "克隆代码仓库",
        description: "浅克隆主干分支",
        toolName: "git_clone",
        status: "COMPLETED",
        observation: "Cloned successfully",
      },
      {
        stepId: 2,
        title: "检索废弃注解",
        description: "查找所有 @Deprecated",
        toolName: "code_find_symbols",
        thought: "首先需要查找所有标注了废弃注解的类与方法",
        status: "RUNNING",
        replanCount: 1,
      },
    ],
  };

  it("renders plan title, steps and status accurately", async () => {
    await act(async () => {
      const root = createRoot(container);
      root.render(<TaskPlanCard plan={mockPlan} />);
    });

    expect(container.textContent).toContain("ReAct 多步任务规划");
    expect(container.textContent).toContain("多模块自动化重构");
    expect(container.textContent).toContain("克隆代码仓库");
    expect(container.textContent).toContain("检索废弃注解");
    expect(container.textContent).toContain("git_clone");
    expect(container.textContent).toContain("code_find_symbols");
  });

  it("toggles step details on click", async () => {
    await act(async () => {
      const root = createRoot(container);
      root.render(<TaskPlanCard plan={mockPlan} />);
    });

    // Step 2 is RUNNING, so its thought should be rendered
    expect(container.textContent).toContain("首先需要查找所有标注了废弃注解");

    // Click step 1 header to open observation
    const step1El = container.querySelector(".cursor-pointer") as HTMLElement;
    if (step1El) {
      await act(async () => {
        step1El.click();
      });
      expect(container.textContent).toContain("Cloned successfully");
    }
  });
});
