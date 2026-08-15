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
import CustomToolsPage from "@/app/tools/page";

mock.module("@/lib/custom-tool-api", () => {
  return {
    listCustomTools: async () => [
      {
        id: "tool-test-1",
        name: "test_weather",
        displayName: "天气查询",
        description: "查询指定城市的天气",
        type: "HTTP",
        enabled: true,
        parametersSchema: JSON.stringify({
          type: "object",
          properties: { city: { type: "string" } },
          required: ["city"],
        }),
        httpConfig: {
          url: "https://wttr.in/{{city}}",
          method: "GET",
        },
      },
      {
        id: "tool-test-2",
        name: "calc_py",
        displayName: "Python计算器",
        description: "计算数学公式",
        type: "SCRIPT",
        enabled: false,
        parametersSchema: "{}",
        scriptConfig: {
          language: "python",
          scriptCode: "print(1+1)",
        },
      },
    ],
    toggleCustomTool: async () => true,
    deleteCustomTool: async () => true,
    testCustomTool: async () => ({
      status: "SUCCESS",
      output: '{"temp": 24}',
      executionTimeMs: 45,
      isTruncated: false,
    }),
    createCustomTool: async (tool: unknown) => ({
      ...(tool as object),
      id: "tool-new-1",
    }),
    updateCustomTool: async (id: string, tool: unknown) => ({
      ...(tool as object),
      id,
    }),
  };
});

describe("CustomToolsPage", () => {
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

  it("renders custom tools list and KPI cards accurately", async () => {
    await act(async () => {
      if (root) root.render(<CustomToolsPage />);
    });

    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(container?.innerHTML).toContain("自定义工具中心");
    expect(container?.innerHTML).toContain("天气查询");
    expect(container?.innerHTML).toContain("Python计算器");
    expect(container?.innerHTML).toContain("HTTP API");
  });
});
