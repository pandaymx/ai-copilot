import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, describe, expect, it } from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import {
  CodeExecutionRenderer,
  ToolResultRenderer,
} from "@/components/chat/tool-renderers";

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

describe("CodeExecutionRenderer Unit Tests", () => {
  it("should render calling / running skeleton state", () => {
    const container = document.createElement("div");
    const root = createRoot(container);

    act(() => {
      root.render(
        <CodeExecutionRenderer
          argsJson={JSON.stringify({
            language: "python",
            code: "import matplotlib.pyplot as plt\nplt.plot([1, 2, 3])",
          })}
        />,
      );
    });

    expect(container.textContent).toContain("Python 3.11");
    expect(container.textContent).toContain("正在沙箱容器中安全运行并捕获图表");
    expect(container.textContent).toContain("plt.plot([1, 2, 3])");
  });

  it("should render successful execution with stdout and Docker sandbox badge", () => {
    const container = document.createElement("div");
    const root = createRoot(container);

    const argsJson = JSON.stringify({
      language: "python",
      code: "print(sum(range(10)))",
    });
    const resultJson = JSON.stringify({
      status: "success",
      language: "python",
      sandboxType: "docker",
      exitCode: 0,
      stdout: "45\n",
      stderr: "",
      executionTimeMs: 142,
      images: [],
      truncated: false,
    });

    act(() => {
      root.render(
        <CodeExecutionRenderer argsJson={argsJson} resultJson={resultJson} />,
      );
    });

    expect(container.textContent).toContain("Python 3.11");
    expect(container.textContent).toContain("Docker 容器隔离");
    expect(container.textContent).toContain("Exit 0 (OK)");
    expect(container.textContent).toContain("142ms");
    expect(container.textContent).toContain("45");
  });

  it("should render generated chart images and support image inspection", () => {
    const container = document.createElement("div");
    const root = createRoot(container);

    const argsJson = JSON.stringify({
      language: "python",
      code: "import matplotlib.pyplot as plt\nplt.plot([1, 2, 3])\nplt.savefig('output.png')",
    });
    const resultJson = JSON.stringify({
      status: "success",
      language: "python",
      sandboxType: "docker",
      exitCode: 0,
      stdout: "Plot generated\n",
      stderr: "",
      executionTimeMs: 310,
      images: [
        {
          name: "output.png",
          mimeType: "image/png",
          data: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        },
      ],
      truncated: false,
    });

    act(() => {
      root.render(
        <CodeExecutionRenderer argsJson={argsJson} resultJson={resultJson} />,
      );
    });

    expect(container.textContent).toContain("生成图表 (1)");

    // 点击生成图表 Tab 切换
    const imageTabBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("生成图表"),
    );
    expect(imageTabBtn).toBeDefined();

    act(() => {
      imageTabBtn?.click();
    });

    expect(container.textContent).toContain("output.png");
    const imgElement = container.querySelector("img");
    expect(imgElement).toBeDefined();
    expect(imgElement?.getAttribute("src")).toContain("data:image/png;base64,");
  });

  it("should dispatch code_execution to CodeExecutionRenderer via ToolResultRenderer", () => {
    const container = document.createElement("div");
    const root = createRoot(container);

    act(() => {
      root.render(
        <ToolResultRenderer
          toolName="code_execution"
          argsJson={JSON.stringify({
            language: "javascript",
            code: "console.log('test js execution')",
          })}
          resultJson={JSON.stringify({
            status: "success",
            language: "javascript",
            sandboxType: "local",
            exitCode: 0,
            stdout: "test js execution\n",
            executionTimeMs: 50,
          })}
        />,
      );
    });

    expect(container.textContent).toContain("Node.js 20");
    expect(container.textContent).toContain("本地进程沙箱");
    expect(container.textContent).toContain("test js execution");
  });
});
