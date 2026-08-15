import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, describe, expect, it, mock } from "bun:test";
import type React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { CitationViewerDrawer } from "../components/chat/citation-viewer-drawer";
import { DocumentChatBar } from "../components/chat/document-chat-bar";
import { Markdown } from "../components/chat/markdown";
import { MessageBubble } from "../components/chat/message-bubble";
import type { DocChatDocItem, DocumentCitationItem } from "../lib/api";

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

describe("Document Chat Suite - Frontend Components", () => {
  const mockCitations: DocumentCitationItem[] = [
    {
      citationId: "1",
      docId: "doc-101",
      fileName: "采购合同.pdf",
      pageNumber: "3",
      paragraphIndex: "2",
      snippet: "甲方逾期付款需支付每日万分之五违约金。",
      similarityScore: 0.92,
    },
    {
      citationId: "2",
      docId: "doc-102",
      fileName: "补充协议.docx",
      pageNumber: "1",
      paragraphIndex: "4",
      snippet: "违约金上限为合同总金额的10%。",
      similarityScore: 0.88,
    },
  ];

  const mockDocuments: DocChatDocItem[] = [
    {
      docId: "doc-101",
      conversationId: "conv-001",
      fileName: "采购合同.pdf",
      sourceType: "PDF",
      chunkCount: 12,
      ingestedAt: "2026-08-15T12:00:00Z",
    },
    {
      docId: "doc-102",
      conversationId: "conv-001",
      fileName: "补充协议.docx",
      sourceType: "TIKA",
      chunkCount: 5,
      ingestedAt: "2026-08-15T12:05:00Z",
    },
  ];

  describe("CitationViewerDrawer Component", () => {
    it("should render citation details, page number, and quoted excerpt", () => {
      const onClose = mock(() => {});
      const { container, unmount } = renderComponent(
        <CitationViewerDrawer
          open={true}
          onClose={onClose}
          citations={mockCitations}
          activeCitationId="1"
        />,
      );

      expect(container.textContent).toContain("原文引用对照");
      expect(container.textContent).toContain("采购合同.pdf");
      expect(container.textContent).toContain("第 3 页");
      expect(container.textContent).toContain("段落 #2");
      expect(container.textContent).toContain(
        "甲方逾期付款需支付每日万分之五违约金。",
      );
      expect(container.textContent).toContain("92%");

      unmount();
    });

    it("should allow switching between multiple citations", () => {
      const onSelect = mock((_id: string) => {});
      const { container, unmount } = renderComponent(
        <CitationViewerDrawer
          open={true}
          onClose={() => {}}
          citations={mockCitations}
          activeCitationId="1"
          onSelectCitation={onSelect}
        />,
      );

      const tabBtn = container.querySelectorAll("button");
      const tab2 = Array.from(tabBtn).find((b) =>
        b.textContent?.includes("补充协议.docx"),
      );
      expect(tab2).toBeDefined();

      if (tab2) {
        act(() => {
          tab2.click();
        });
        expect(onSelect).toHaveBeenCalledWith("2");
      }

      unmount();
    });
  });

  describe("DocumentChatBar Component", () => {
    it("should render attached document pills and mode indicator", () => {
      const onToggle = mock((_v: boolean) => {});
      const { container, unmount } = renderComponent(
        <DocumentChatBar
          enabled={true}
          onToggleEnabled={onToggle}
          conversationId="conv-001"
          documents={mockDocuments}
          selectedDocIds={["doc-101"]}
          onSelectDocIds={() => {}}
          onDocumentsChange={() => {}}
        />,
      );

      expect(container.textContent).toContain("文档对话模式");
      expect(container.textContent).toContain("ON");
      expect(container.textContent).toContain("采购合同.pdf");
      expect(container.textContent).toContain("12 切片");
      expect(container.textContent).toContain("补充协议.docx");

      unmount();
    });
  });

  describe("Markdown Citation Tokens & Interactivity", () => {
    it("should parse [引用 1: ...] tokens into interactive citation buttons", () => {
      const onCitationClick = mock((_id: string) => {});
      const text =
        "双方违约金条款如下：[引用 1: 采购合同.pdf (第 3 页 / 段落 2)]，另外 [引用 2: 补充协议.docx (第 1 页 / 段落 4)]。";

      const { container, unmount } = renderComponent(
        <Markdown content={text} onCitationClick={onCitationClick} />,
      );

      expect(container.textContent).toContain(
        "引用 1: 采购合同.pdf (第 3 页 / 段落 2)",
      );
      expect(container.textContent).toContain(
        "引用 2: 补充协议.docx (第 1 页 / 段落 4)",
      );

      const buttons = container.querySelectorAll("button");
      expect(buttons.length).toBeGreaterThanOrEqual(2);

      act(() => {
        buttons[0]?.click();
      });
      expect(onCitationClick).toHaveBeenCalledWith("1");

      unmount();
    });
  });

  describe("MessageBubble Citations & Refusal Alert", () => {
    it("should render citations footer panel and click-to-drawer callback", () => {
      const onCitationClick = mock((_c: DocumentCitationItem) => {});
      const msg = {
        id: "msg-1",
        role: "assistant" as const,
        content: "根据采购合同第三条，违约金为每日万分之五。",
        citations: mockCitations,
      };

      const { container, unmount } = renderComponent(
        <MessageBubble message={msg} onCitationClick={onCitationClick} />,
      );

      expect(container.textContent).toContain("引用依据 (2):");
      expect(container.textContent).toContain("[1]");
      expect(container.textContent).toContain("采购合同.pdf");

      const citeBtn = Array.from(container.querySelectorAll("button")).find(
        (b) => b.textContent?.includes("采购合同.pdf"),
      );
      expect(citeBtn).toBeDefined();

      act(() => {
        citeBtn?.click();
      });
      expect(onCitationClick).toHaveBeenCalledWith(mockCitations[0]);

      unmount();
    });

    it("should render refusal warning banner when out-of-scope question is rejected", () => {
      const msg = {
        id: "msg-2",
        role: "assistant" as const,
        content:
          "抱歉，根据当前提供的会话文档，未检索到相关内容。作为文档专属助手，我无法回答文档范围之外的问题。",
      };

      const { container, unmount } = renderComponent(
        <MessageBubble message={msg} />,
      );

      expect(container.textContent).toContain(
        "文档限定模式：由于挂载文档中未包含相关事实，已自动拦截文档外无关内容。",
      );

      unmount();
    });
  });
});
