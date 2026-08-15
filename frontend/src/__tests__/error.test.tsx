import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
  (
    globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT?: boolean }
  ).IS_REACT_ACT_ENVIRONMENT = true;
}

import {
  afterAll,
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  mock,
} from "bun:test";
import type React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import GlobalError from "../app/error";

let originalConsoleError: typeof console.error;

beforeEach(() => {
  originalConsoleError = console.error;
  console.error = () => {};
});

afterEach(() => {
  console.error = originalConsoleError;
});

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

describe("Global Error Boundary Page (src/app/error.tsx)", () => {
  it("should render error message and action buttons", () => {
    const mockError = new Error("Simulated Global Component Failure");
    const mockReset = mock();

    const { container, unmount } = renderComponent(
      <GlobalError error={mockError} reset={mockReset} />,
    );

    expect(container.textContent).toContain("应用遇到非预期错误");
    expect(container.textContent).toContain(
      "抱歉，前端组件在渲染或交互过程中抛出了未捕获异常",
    );
    expect(container.textContent).toContain("重新尝试加载");
    expect(container.textContent).toContain("返回首页");

    unmount();
  });

  it("should display digest tag when error.digest is provided", () => {
    const mockError = Object.assign(new Error("Database connection failed"), {
      digest: "ERR_REF_12345",
    });

    const { container, unmount } = renderComponent(
      <GlobalError error={mockError} reset={() => {}} />,
    );

    expect(container.textContent).toContain("Digest:");
    expect(container.textContent).toContain("ERR_REF_12345");

    unmount();
  });

  it("should trigger reset callback when clicking reload button", () => {
    const mockReset = mock();
    const mockError = new Error("Component Crash");

    const { container, unmount } = renderComponent(
      <GlobalError error={mockError} reset={mockReset} />,
    );

    const reloadBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("重新尝试加载"),
    ) as HTMLButtonElement;
    expect(reloadBtn).not.toBeNull();

    act(() => {
      reloadBtn.click();
    });

    expect(mockReset).toHaveBeenCalledTimes(1);

    unmount();
  });

  it("should toggle stack trace details when clicking toggle button", () => {
    const mockError = new Error("Stack Trace Detail Test");
    mockError.stack =
      "Error: Stack Trace Detail Test\n at TestComponent (error.test.tsx:10:15)";

    const { container, unmount } = renderComponent(
      <GlobalError error={mockError} reset={() => {}} />,
    );

    const toggleBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("查看技术排查细节"),
    ) as HTMLButtonElement;
    expect(toggleBtn).not.toBeNull();

    // Stack trace initially hidden
    expect(container.textContent).not.toContain(
      "TestComponent (error.test.tsx:10:15)",
    );

    act(() => {
      toggleBtn.click();
    });

    expect(container.textContent).toContain(
      "TestComponent (error.test.tsx:10:15)",
    );

    unmount();
  });
});
