import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

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
import {
  type ChatMessage,
  LiveMessageBubble,
  MessageBubble,
} from "../components/chat/message-bubble";
import { StreamStore } from "../hooks/useSpringAiStream";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock();
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
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

describe("MessageBubble Component Tests - components/chat/message-bubble.tsx", () => {
  it("should render user message content and attachments correctly", () => {
    const userMsg: ChatMessage = {
      id: "msg-user-1",
      role: "user",
      content: "Explain React 19 concurrent mode",
      attachments: [
        {
          id: "att-1",
          name: "diagram.png",
          type: "image",
          mimeType: "image/png",
          url: "data:image/png;base64,ABC",
        },
        {
          id: "att-2",
          name: "notes.txt",
          type: "file",
          mimeType: "text/plain",
          url: "http://localhost/notes.txt",
          textContent: "Some notes",
        },
      ],
    };

    const { container, unmount } = renderComponent(
      <MessageBubble message={userMsg} />,
    );

    expect(container.textContent).toContain("Explain React 19 concurrent mode");
    expect(container.textContent).toContain("notes.txt");
    expect(container.textContent).toContain("已读取");

    const img = container.querySelector("img");
    expect(img).not.toBeNull();
    expect(img?.getAttribute("src")).toBe("data:image/png;base64,ABC");

    unmount();
  });

  it("should render assistant message badges, tokens, cost, and thinking container", () => {
    const assistantMsg: ChatMessage = {
      id: "msg-ai-1",
      role: "assistant",
      content: "React 19 introduces automatic memoization and actions.",
      thinking: "Analyzing React 19 release notes...",
      usage: {
        promptTokens: 120,
        completionTokens: 80,
        totalTokens: 200,
        estimatedCostRmb: 0.0015,
      },
    };

    const { container, unmount } = renderComponent(
      <MessageBubble message={assistantMsg} conversationId="conv-1" />,
    );

    expect(container.textContent).toContain("AI Copilot");
    expect(container.textContent).toContain("Spring AI Core");
    expect(container.textContent).toContain("Tokens: 200");
    expect(container.textContent).toContain("约 ¥0.0015");
    expect(container.textContent).toContain("思维链推理过程");
    expect(container.textContent).toContain(
      "Analyzing React 19 release notes...",
    );

    unmount();
  });

  it("should toggle thinking collapse when clicking thinking accordion button", () => {
    const assistantMsg: ChatMessage = {
      id: "msg-ai-thinking",
      role: "assistant",
      content: "Answer text",
      thinking: "Deep thought details",
    };

    const { container, unmount } = renderComponent(
      <MessageBubble message={assistantMsg} />,
    );

    expect(container.textContent).toContain("Deep thought details");

    const toggleBtn = container.querySelector("button")!;
    act(() => {
      toggleBtn.click();
    });

    expect(container.textContent).not.toContain("Deep thought details");

    unmount();
  });

  it("should trigger copy to clipboard when clicking copy button", async () => {
    let copiedText = "";
    Object.defineProperty(navigator, "clipboard", {
      value: {
        writeText: (text: string) => {
          copiedText = text;
          return Promise.resolve();
        },
      },
      writable: true,
      configurable: true,
    });

    const assistantMsg: ChatMessage = {
      id: "msg-copy",
      role: "assistant",
      content: "Copy this exact text",
    };

    const { container, unmount } = renderComponent(
      <MessageBubble message={assistantMsg} />,
    );

    const copyBtn = container.querySelector('button[title="复制回答"]');
    expect(copyBtn).not.toBeNull();

    await act(async () => {
      (copyBtn as HTMLButtonElement).click();
    });

    expect(copiedText).toBe("Copy this exact text");
    unmount();
  });

  it("should send feedback POST request when clicking thumbs up button", async () => {
    mockFetch.mockResolvedValueOnce(new Response(null, { status: 200 }));

    const assistantMsg: ChatMessage = {
      id: "msg-feedback",
      role: "assistant",
      content: "Feedback test content",
    };

    const { container, unmount } = renderComponent(
      <MessageBubble message={assistantMsg} conversationId="conv-99" />,
    );

    const thumbsUpBtn = container.querySelector('button[title="赞"]');
    expect(thumbsUpBtn).not.toBeNull();

    act(() => {
      (thumbsUpBtn as HTMLButtonElement).click();
    });

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/chat/feedback",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          conversationId: "conv-99",
          messageId: "msg-feedback",
          rating: "THUMBS_UP",
        }),
      }),
    );

    unmount();
  });

  it("should render LiveMessageBubble streaming from StreamStore", () => {
    const store = new StreamStore();
    store.update("Streaming response text...", "Live thinking", {
      promptTokens: 10,
      completionTokens: 20,
      totalTokens: 30,
    });

    const baseMsg: ChatMessage = {
      id: "live-1",
      role: "assistant",
      content: "",
    };

    const { container, unmount } = renderComponent(
      <LiveMessageBubble message={baseMsg} streamStore={store} />,
    );

    expect(container.textContent).toContain("Streaming response text...");
    expect(container.textContent).toContain("Live thinking");

    unmount();
  });

  it("should render Self-Reflection and Correction badge when message contains self-correction", () => {
    const reflectedMsg: ChatMessage = {
      id: "reflected-1",
      role: "assistant",
      content:
        "这是原始回答。\n\n> 🔍 **AI 自我纠错与补充**\n> **自检要点**：修正了参数",
    };

    const { container, unmount } = renderComponent(
      <MessageBubble message={reflectedMsg} />,
    );

    expect(container.textContent).toContain("已触发自我反思纠偏");
    expect(container.textContent).toContain("AI 自我纠错与补充");

    unmount();
  });
});
