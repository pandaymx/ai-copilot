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
import { DbQueryRenderer } from "../components/chat/tool-renderers";

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

describe("DbQueryRenderer Component Tests", () => {
  it("should render error alert when query fails", () => {
    const argsJson = JSON.stringify({ question: "查询所有用户" });
    const resultJson = JSON.stringify({
      success: false,
      error: "禁止访问核心系统表 pg_shadow",
      executionTimeMs: 4,
    });

    const { container, unmount } = renderComponent(
      <DbQueryRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("执行失败");
    expect(container.textContent).toContain("禁止访问核心系统表 pg_shadow");
    unmount();
  });

  it("should render table with columns, rows and copyable SQL on success", () => {
    const argsJson = JSON.stringify({ question: "查询最近创建的会话" });
    const resultJson = JSON.stringify({
      success: true,
      sql: "SELECT id, title FROM sessions ORDER BY created_at DESC LIMIT 2",
      columns: ["id", "title"],
      rows: [
        { id: "s-1", title: "Chat Session 1" },
        { id: "s-2", title: "Chat Session 2" },
      ],
      rowCount: 2,
      executionTimeMs: 15,
    });

    const { container, unmount } = renderComponent(
      <DbQueryRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("PostgreSQL 只读查询");
    expect(container.textContent).toContain("成功 (15ms)");
    expect(container.textContent).toContain("共返回 2 行记录");
    expect(container.textContent).toContain("Chat Session 1");
    expect(container.textContent).toContain("Chat Session 2");

    unmount();
  });
});
