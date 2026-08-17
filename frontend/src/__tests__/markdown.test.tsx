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
import { Markdown } from "../components/chat/markdown";

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

describe("Markdown Component Tests - components/chat/markdown.tsx", () => {
  it("should render standard markdown text, headings, lists, and blockquotes", () => {
    const content = `
# Title Heading
This is a paragraph with **bold text** and *italic text*.

- Item 1
- Item 2

> Important note
    `.trim();

    const { container, unmount } = renderComponent(
      <Markdown content={content} />,
    );

    expect(container.textContent).toContain("Title Heading");
    expect(container.textContent).toContain(
      "This is a paragraph with bold text and italic text.",
    );
    expect(container.textContent).toContain("Item 1");
    expect(container.textContent).toContain("Item 2");
    expect(container.textContent).toContain("Important note");

    unmount();
  });

  it("should render code blocks with language badge and Mac style window controls", () => {
    const content = `
\`\`\`typescript
const greeting: string = "Hello World";
console.log(greeting);
\`\`\`
    `.trim();

    const { container, unmount } = renderComponent(
      <Markdown content={content} />,
    );

    expect(container.textContent).toContain("typescript");
    expect(container.textContent).toContain(
      'const greeting: string = "Hello World";',
    );
    expect(container.textContent).toContain("复制");

    unmount();
  });

  it("should auto-close unclosed code block during streaming mode", () => {
    const unclosedContent =
      "Here is partial code:\n```javascript\nconst a = 1;";

    const { container, unmount } = renderComponent(
      <Markdown content={unclosedContent} isStreaming={true} />,
    );

    expect(container.textContent).toContain("javascript");
    expect(container.textContent).toContain("const a = 1;");

    unmount();
  });

  it("should render Markdown table structure", () => {
    const tableMarkdown = `
| Name | Role |
| --- | --- |
| Alice | Admin |
| Bob | User |
    `.trim();

    const { container, unmount } = renderComponent(
      <Markdown content={tableMarkdown} />,
    );

    const table = container.querySelector("table");
    expect(table).not.toBeNull();
    expect(container.textContent).toContain("Alice");
    expect(container.textContent).toContain("Admin");
    expect(container.textContent).toContain("Bob");

    unmount();
  });

  it("should render Mermaid block with controls bar and source toggle option", () => {
    const mermaidMarkdown = `
\`\`\`mermaid
graph TD;
    A-->B;
\`\`\`
    `.trim();

    const { container, unmount } = renderComponent(
      <Markdown content={mermaidMarkdown} />,
    );

    expect(container.textContent).toContain("Mermaid 图表");
    expect(container.textContent).toContain("源码");

    unmount();
  });

  it("should not show visible error panel for incomplete streaming mermaid", async () => {
    // 未闭合/残缺的 mermaid 代码块在流式阶段不应抛出可见的"语法解析失败"
    const partialMermaid = "```mermaid\ngraph TD;\n    A-->B;\n    B--";

    const { container, unmount } = renderComponent(
      <Markdown content={partialMermaid} isStreaming={true} />,
    );

    // 等待防抖 + 渲染周期结束
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 500));
    });

    expect(container.textContent).not.toContain("Mermaid 语法解析失败");
    expect(container.textContent).not.toContain("Syntax error");

    unmount();
  });

  it("should show error panel for invalid non-streaming mermaid", async () => {
    // 非流式阶段，明确非法的 mermaid 应展示语法解析失败提示
    const invalidMermaid = "```mermaid\nthis is not a valid diagram;;;\n```";

    const { container, unmount } = renderComponent(
      <Markdown content={invalidMermaid} isStreaming={false} />,
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 500));
    });

    expect(container.textContent).toContain("Mermaid 语法解析失败");

    unmount();
  });
});
