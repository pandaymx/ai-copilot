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
import { ConversationSummaryModal } from "@/components/chat/conversation-summary-modal";

const mockSummary = {
  conversationId: "sess-1",
  title: "React 19 Hooks 深度解析",
  summary:
    "探讨了 React 19 的新特性、useActionState 与 Server Actions 的用法。",
  keyDecisions: ["全面使用 React 19 异步 Action 处理并发"],
  todos: ["升级 Next.js 16 项目依赖"],
  references: ["React 19 Official Documentation"],
  openIssues: ["兼容老版第三库 ref 传递"],
  tags: ["React", "Next.js", "Frontend"],
  messageCount: 5,
  createdAt: 1718000000000,
};

describe("ConversationSummaryModal", () => {
  let container: HTMLDivElement | null = null;
  let root: ReturnType<typeof createRoot> | null = null;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);

    global.fetch = mock(async (url: string | URL | Request) => {
      const urlStr = url.toString();
      if (urlStr.includes("/summary")) {
        return new Response(JSON.stringify(mockSummary), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (urlStr.includes("/knowledge")) {
        return new Response(
          JSON.stringify({
            success: true,
            fileName: "会话沉淀-React 19 Hooks 深度解析.md",
            title: "React 19 Hooks 深度解析",
            ingestedChunks: 2,
            skippedChunks: 0,
            sourceType: "CONVERSATION_SUMMARY",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      return new Response(JSON.stringify({}), { status: 200 });
    }) as unknown as typeof fetch;
  });

  afterEach(() => {
    if (root) {
      act(() => {
        root?.unmount();
      });
    }
    if (container?.parentNode) {
      container.parentNode.removeChild(container);
    }
  });

  it("当 isOpen 为 true 时渲染摘要内容与结构化卡片", async () => {
    await act(async () => {
      root?.render(
        <ConversationSummaryModal
          isOpen={true}
          onClose={() => {}}
          sessionId="sess-1"
        />,
      );
    });

    // 等待异步 fetch
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(container?.textContent).toContain("会话摘要与知识沉淀");
    expect(container?.textContent).toContain("React 19 Hooks 深度解析");
    expect(container?.textContent).toContain("全面使用 React 19 异步 Action");
    expect(container?.textContent).toContain("升级 Next.js 16 项目依赖");
    expect(container?.textContent).toContain("React 19 Official Documentation");
    expect(container?.textContent).toContain("存入 RAG 知识库");
  });

  it("点击「存入 RAG 知识库」成功后切换为「已存入知识库 ✓ (前往查看)」状态", async () => {
    await act(async () => {
      root?.render(
        <ConversationSummaryModal
          isOpen={true}
          onClose={() => {}}
          sessionId="sess-1"
        />,
      );
    });

    await new Promise((resolve) => setTimeout(resolve, 50));

    const saveBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("存入 RAG 知识库"));
    expect(saveBtn).toBeTruthy();

    await act(async () => {
      saveBtn?.click();
    });

    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(container?.textContent).toContain("已存入知识库 ✓ (前往查看)");
  });
});
