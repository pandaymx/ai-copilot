import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { describe, expect, it } from "bun:test";
import type React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { ToolResultRenderer } from "../components/chat/tool-renderers";

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

describe("EmailTool Frontend Renderer Tests", () => {
  it("renders sent email result correctly", () => {
    const resultJson = JSON.stringify({
      success: true,
      action: "SEND",
      result: {
        messageId: "msg_12345678",
        to: ["partner@example.com"],
        subject: "商业合作洽谈",
        status: "SENT",
        preview: "尊敬的合作伙伴，您好...",
      },
    });

    const { container, unmount } = renderComponent(
      <ToolResultRenderer
        toolName="email_tool"
        argsJson="{}"
        resultJson={resultJson}
      />,
    );

    expect(container.textContent).toContain("邮件代发结果");
    expect(container.textContent).toContain("partner@example.com");
    expect(container.textContent).toContain("商业合作洽谈");
    expect(container.textContent).toContain("SENT");

    unmount();
  });

  it("renders draft email correctly", () => {
    const resultJson = JSON.stringify({
      success: true,
      action: "DRAFT",
      draft: {
        to: ["user@company.com"],
        subject: "周报草稿",
        status: "DRAFT",
        preview: "本周工作总结如下...",
      },
    });

    const { container, unmount } = renderComponent(
      <ToolResultRenderer
        toolName="email_tool"
        argsJson="{}"
        resultJson={resultJson}
      />,
    );

    expect(container.textContent).toContain("邮件草稿生成");
    expect(container.textContent).toContain("user@company.com");
    expect(container.textContent).toContain("周报草稿");

    unmount();
  });
});
