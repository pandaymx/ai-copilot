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
import {
  type ChatMessage,
  MessageBubble,
} from "../components/chat/message-bubble";

let container: HTMLDivElement | null = null;
let root: ReturnType<typeof createRoot> | null = null;
let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock().mockImplementation(async (url: string) => {
    if (url.includes("/api/translate")) {
      return new Response(
        JSON.stringify({
          originalText: "Hello world, this is a test message.",
          sourceLang: "en",
          targetLang: "zh-CN",
          detectedLang: "en",
          translatedText: "你好世界，这是一条测试消息。",
          glossaryAppliedCount: 0,
          latencyMs: 45,
        }),
        { status: 200 },
      );
    }
    return new Response(JSON.stringify({}), { status: 200 });
  });
  globalThis.fetch = mockFetch as unknown as typeof fetch;

  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  if (root && container) {
    act(() => {
      root?.unmount();
    });
  }
  if (container?.parentNode) {
    container.parentNode.removeChild(container);
  }
  globalThis.fetch = originalFetch;
});

describe("MessageBubble translation engine", () => {
  const sampleMessage: ChatMessage = {
    id: "msg-test-1",
    role: "assistant",
    content: "Hello world, this is a test message.",
  };

  it("should trigger translation and render translation card upon clicking translate button", async () => {
    await act(async () => {
      root?.render(<MessageBubble message={sampleMessage} streaming={false} />);
    });

    const translateBtn = container?.querySelector(
      'button[title="多语言即时翻译"]',
    ) as HTMLElement;
    expect(translateBtn).not.toBeNull();

    await act(async () => {
      translateBtn.click();
    });

    // Wait a tick for async API resolve
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    const text = container?.textContent || "";
    expect(text).toContain("多语言译文");
    expect(text).toContain("你好世界，这是一条测试消息。");
    expect(text).toContain("en → zh-CN");
  });
});
