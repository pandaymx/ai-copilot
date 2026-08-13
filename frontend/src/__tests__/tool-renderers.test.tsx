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
  CalculatorRenderer,
  DefaultToolRenderer,
  FileReadRenderer,
  HttpRequestRenderer,
  KnowledgeQueryRenderer,
  ToolResultRenderer,
  WebSearchRenderer,
} from "../components/chat/tool-renderers";

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

describe("Tool Renderers Component Tests - components/chat/tool-renderers.tsx", () => {
  it("should render HttpRequestRenderer correctly with status code and body", () => {
    const argsJson = JSON.stringify({
      method: "POST",
      url: "https://api.example.com/test",
    });
    const resultJson = JSON.stringify({
      status: 200,
      length: 128,
      headers: { "content-type": "application/json" },
      body: { success: true, message: "OK" },
    });

    const { container, unmount } = renderComponent(
      <HttpRequestRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("POST");
    expect(container.textContent).toContain("https://api.example.com/test");
    expect(container.textContent).toContain("HTTP 200");
    expect(container.textContent).toContain("Response Body");

    unmount();
  });

  it("should render CalculatorRenderer correctly with KaTeX formula and result", () => {
    const argsJson = JSON.stringify({ expression: "12 * (3 + 4) / 2" });
    const resultJson = JSON.stringify({ output: 42 });

    const { container, unmount } = renderComponent(
      <CalculatorRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("数学公式求值");
    expect(container.textContent).toContain("42");

    unmount();
  });

  it("should render FileReadRenderer correctly with file path and line count", () => {
    const argsJson = JSON.stringify({ path: "src/utils/math.ts" });
    const resultJson = JSON.stringify({
      output: "export function add(a: number, b: number) {\n  return a + b;\n}",
    });

    const { container, unmount } = renderComponent(
      <FileReadRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("src/utils/math.ts");
    expect(container.textContent).toContain("typescript");
    expect(container.textContent).toContain("3 行");

    unmount();
  });

  it("should render KnowledgeQueryRenderer correctly with documents and links", () => {
    const argsJson = JSON.stringify({ query: "Spring AI RAG" });
    const resultJson = JSON.stringify({
      count: 1,
      documents: [
        {
          content: "Spring AI provides vector store abstractions for RAG.",
          metadata: {
            title: "Spring AI Docs",
            url: "https://docs.spring.io/spring-ai",
          },
        },
      ],
    });

    const { container, unmount } = renderComponent(
      <KnowledgeQueryRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("知识库检索");
    expect(container.textContent).toContain("1 篇相关文档");
    expect(container.textContent).toContain("Spring AI Docs");
    expect(container.textContent).toContain(
      "Spring AI provides vector store abstractions for RAG.",
    );

    unmount();
  });

  it("should render WebSearchRenderer correctly with search results", () => {
    const argsJson = JSON.stringify({ query: "Next.js 16" });
    const resultJson = JSON.stringify({
      query: "Next.js 16",
      results: [
        {
          title: "Next.js 16 Release Notes",
          snippet: "Next.js 16 comes with React 19 support.",
          url: "https://nextjs.org/blog/next-16",
        },
      ],
    });

    const { container, unmount } = renderComponent(
      <WebSearchRenderer argsJson={argsJson} resultJson={resultJson} />,
    );

    expect(container.textContent).toContain("网络搜索");
    expect(container.textContent).toContain("1 条检索结果");
    expect(container.textContent).toContain("Next.js 16 Release Notes");
    expect(container.textContent).toContain("nextjs.org");

    unmount();
  });

  it("should dispatch to appropriate renderer via ToolResultRenderer", () => {
    const { container: httpContainer, unmount: unmountHttp } = renderComponent(
      <ToolResultRenderer
        toolName="http_request"
        argsJson={JSON.stringify({ url: "https://example.com" })}
        resultJson={JSON.stringify({ status: 200 })}
      />,
    );
    expect(httpContainer.textContent).toContain("HTTP 200");
    unmountHttp();

    const { container: calcContainer, unmount: unmountCalc } = renderComponent(
      <ToolResultRenderer
        toolName="calculator"
        argsJson={JSON.stringify({ expression: "5 + 5" })}
        resultJson={JSON.stringify({ output: 10 })}
      />,
    );
    expect(calcContainer.textContent).toContain("数学公式求值");
    unmountCalc();
  });

  it("should fallback to DefaultToolRenderer on unknown tool or error", () => {
    const { container, unmount } = renderComponent(
      <DefaultToolRenderer resultJson={JSON.stringify({ custom: "data" })} />,
    );
    expect(container.textContent).toContain('"custom": "data"');
    unmount();
  });
});
