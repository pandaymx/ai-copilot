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
import { ContextInheritanceModal } from "@/components/chat/context-inheritance-modal";
import { InheritedContextBanner } from "@/components/chat/inherited-context-banner";
import type { InheritedContext } from "@/lib/api";

const mockContext: InheritedContext = {
  sourceSessionId: "sess-source-1",
  sourceSessionTitle: "订单系统架构选型",
  contextSummary: "探讨了订单微服务重构方案，确定采用 CQRS 与 Event Sourcing。",
  keyDecisions: [
    {
      decision: "采用 CQRS 架构",
      rationale: "读写分离应对高并发查询",
      category: "架构",
    },
  ],
  codeSnippets: [
    {
      language: "typescript",
      code: "export interface OrderCreatedEvent { orderId: string; }",
      description: "事件载荷定义",
      filePath: "src/events/order.ts",
    },
  ],
  fileReferences: [
    {
      fileName: "OrderService.java",
      fileType: "java",
      description: "订单主服务",
    },
  ],
  pendingQuestions: [
    {
      question: "事件投递的 Exactly-Once 保障方案？",
      context: "评估事务消息与 Outbox 模式",
      priority: "HIGH",
    },
  ],
  entityRelations: [
    {
      subject: "OrderService",
      relation: "publishes",
      object: "KafkaTopic",
    },
  ],
  exportedAt: Date.now(),
  estimatedTokens: 350,
  extractionMode: "LLM",
};

// 模拟 API
mock.module("@/lib/api", () => {
  return {
    exportSessionContextApi: async () => mockContext,
    importSessionContextApi: async () => ({
      success: true,
      targetSessionId: "sess-target-1",
      targetTitle: "继承: 订单系统架构选型",
      importedModules: ["summary", "decisions", "code"],
      formattedContextPreview: "### 📝 背景与核心主旨概述\n...",
      importedAt: Date.now(),
    }),
  };
});

describe("InheritedContextBanner", () => {
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

  it("renders inherited context banner with summary and expand toggle", async () => {
    await act(async () => {
      if (root) {
        root.render(
          <InheritedContextBanner
            inheritedContextJson={JSON.stringify(mockContext)}
            parentSessionId="sess-source-1"
          />,
        );
      }
    });

    expect(container?.innerHTML).toContain("跨会话继承上下文");
    expect(container?.innerHTML).toContain("订单系统架构选型");
    expect(container?.innerHTML).toContain("深度提炼");
  });
});

describe("ContextInheritanceModal", () => {
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

  it("renders modal with source sessions and 5-dimension preview", async () => {
    const sessions = [
      {
        id: "sess-source-1",
        title: "订单系统架构选型",
        updatedAt: Date.now(),
      },
      {
        id: "sess-2",
        title: "常规聊天",
        updatedAt: Date.now() - 10000,
      },
    ];

    await act(async () => {
      if (root) {
        root.render(
          <ContextInheritanceModal
            isOpen={true}
            onClose={() => {}}
            sessions={sessions}
            currentSessionId="sess-2"
            initialSourceSessionId="sess-source-1"
            onSuccess={() => {}}
          />,
        );
      }
    });

    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(container?.innerHTML).toContain("跨会话上下文继承");
    expect(container?.innerHTML).toContain("1. 选择源会话");
    expect(container?.innerHTML).toContain("2. 结构化上下文预览与按需勾选");
  });
});
