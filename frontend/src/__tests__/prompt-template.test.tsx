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
import { PromptTemplateDialog } from "../components/chat/prompt-template-dialog";
import type { PromptTemplate } from "../lib/prompt-template-api";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

const mockTemplates: PromptTemplate[] = [
  {
    id: "tpl-1",
    userId: "u-1",
    title: "Clean Code 代码重构",
    description: "重构代码结构",
    category: "coding",
    body: "请帮我重构以下 {{language}} 代码：\n\n```\n{{code}}\n```",
    variables: ["language", "code"],
    rating: 5,
    favorite: true,
    isSystem: true,
    createdAt: 1000,
    updatedAt: 1000,
  },
];

beforeEach(() => {
  mockFetch = mock().mockResolvedValue(
    new Response(JSON.stringify(mockTemplates), {
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

describe("PromptTemplateDialog Component Tests", () => {
  it("should render template dialog and allow variable input substitution", async () => {
    let selectedPrompt = "";

    const { container, unmount } = renderComponent(
      <PromptTemplateDialog
        open={true}
        onClose={() => {}}
        onSelectPrompt={(res) => {
          selectedPrompt = res;
        }}
      />,
    );

    // 等待异步数据加载与 state 渲染
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(container.textContent).toContain("Clean Code 代码重构");
    expect(container.textContent).toContain("变量插槽");

    // 找到 language 变量输入框并触发输入
    const langInput = container.querySelector(
      'input[id="slot-language"]',
    ) as HTMLInputElement;
    if (langInput) {
      await act(async () => {
        const reactPropsKey = Object.keys(langInput).find((k) =>
          k.startsWith("__reactProps$"),
        );
        if (reactPropsKey) {
          // biome-ignore lint/suspicious/noExplicitAny: access React internal props
          (langInput as any)[reactPropsKey].onChange({
            target: { value: "TypeScript" },
          });
        }
      });
    }

    // 找到插入按钮
    const insertBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("插入到对话输入框"),
    ) as HTMLButtonElement;
    expect(insertBtn).toBeDefined();

    await act(async () => {
      insertBtn.click();
    });

    expect(selectedPrompt).toContain("TypeScript");

    unmount();
  });
});
