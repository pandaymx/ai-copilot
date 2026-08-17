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
import {
  CHAT_MODE_OPTIONS,
  ChatModeSelector,
} from "../components/chat/chat-mode-selector";

let container: HTMLDivElement;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
});

afterEach(() => {
  if (container?.parentNode) {
    container.parentNode.removeChild(container);
  }
});

function renderComponent(ui: React.ReactNode) {
  const root = createRoot(container);
  act(() => {
    root.render(ui);
  });
  return {
    container,
    rerender: (newUi: React.ReactNode) => {
      act(() => {
        root.render(newUi);
      });
    },
    unmount: () => {
      act(() => {
        root.unmount();
      });
    },
  };
}

describe("ChatModeSelector Component Unit Tests", () => {
  it("exports CHAT_MODE_OPTIONS with expected 4 modes", () => {
    expect(CHAT_MODE_OPTIONS.length).toBe(4);
    expect(CHAT_MODE_OPTIONS.map((o) => o.id)).toEqual([
      "chat",
      "agent",
      "image",
      "doc",
    ]);
  });

  it("renders default 普通对话 mode when imageMode, agentEnabled, and documentChatEnabled are false", () => {
    const onImageModeChange = mock(() => {});
    const onAgentEnabledChange = mock(() => {});
    const onDocumentChatEnabledChange = mock(() => {});

    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={onImageModeChange}
        agentEnabled={false}
        onAgentEnabledChange={onAgentEnabledChange}
        documentChatEnabled={false}
        onDocumentChatEnabledChange={onDocumentChatEnabledChange}
      />,
    );

    const trigger = container.querySelector("button");
    expect(trigger).not.toBeNull();
    expect(trigger?.textContent).toContain("普通对话");
    expect(trigger?.getAttribute("aria-expanded")).toBe("false");
  });

  it("renders Agent 模式 when agentEnabled is true", () => {
    const onImageModeChange = mock(() => {});
    const onAgentEnabledChange = mock(() => {});

    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={onImageModeChange}
        agentEnabled={true}
        onAgentEnabledChange={onAgentEnabledChange}
      />,
    );

    const trigger = container.querySelector("button");
    expect(trigger?.textContent).toContain("Agent 模式");
  });

  it("renders 生图模式 when imageMode is true", () => {
    const onImageModeChange = mock(() => {});
    const onAgentEnabledChange = mock(() => {});

    renderComponent(
      <ChatModeSelector
        imageMode={true}
        onImageModeChange={onImageModeChange}
        agentEnabled={false}
        onAgentEnabledChange={onAgentEnabledChange}
      />,
    );

    const trigger = container.querySelector("button");
    expect(trigger?.textContent).toContain("生图模式");
  });

  it("renders 文档对话 when documentChatEnabled is true", () => {
    const onImageModeChange = mock(() => {});
    const onAgentEnabledChange = mock(() => {});
    const onDocChange = mock(() => {});

    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={onImageModeChange}
        agentEnabled={false}
        onAgentEnabledChange={onAgentEnabledChange}
        documentChatEnabled={true}
        onDocumentChatEnabledChange={onDocChange}
      />,
    );

    const trigger = container.querySelector("button");
    expect(trigger?.textContent).toContain("文档对话");
  });

  it("opens popover menu on click and lists all options", () => {
    const onImageModeChange = mock(() => {});
    const onAgentEnabledChange = mock(() => {});

    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={onImageModeChange}
        agentEnabled={false}
        onAgentEnabledChange={onAgentEnabledChange}
      />,
    );

    const trigger = container.querySelector("button");
    act(() => {
      trigger?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    const listbox = container.querySelector('[role="listbox"]');
    expect(listbox).not.toBeNull();

    const options = container.querySelectorAll('[role="option"]');
    expect(options.length).toBe(4);
    expect(options[0].textContent).toContain("普通对话");
    expect(options[1].textContent).toContain("Agent 模式");
    expect(options[2].textContent).toContain("生图模式");
    expect(options[3].textContent).toContain("文档对话");
  });

  it("selects 文档对话 and triggers callbacks accurately", () => {
    const onImageModeChange = mock((_val: boolean) => {});
    const onAgentEnabledChange = mock((_val: boolean) => {});
    const onDocChange = mock((_val: boolean) => {});

    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={onImageModeChange}
        agentEnabled={false}
        onAgentEnabledChange={onAgentEnabledChange}
        documentChatEnabled={false}
        onDocumentChatEnabledChange={onDocChange}
      />,
    );

    const trigger = container.querySelector("button");
    act(() => {
      trigger?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    const options = Array.from(container.querySelectorAll('[role="option"]'));
    const docOption = options.find((opt) =>
      opt.textContent?.includes("文档对话"),
    );
    expect(docOption).toBeDefined();

    act(() => {
      docOption?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onImageModeChange).toHaveBeenCalledWith(false);
    expect(onAgentEnabledChange).toHaveBeenCalledWith(false);
    expect(onDocChange).toHaveBeenCalledWith(true);
    expect(container.querySelector('[role="listbox"]')).toBeNull();
  });

  it("selects Agent 模式 and triggers callbacks accurately", () => {
    const onImageModeChange = mock((_val: boolean) => {});
    const onAgentEnabledChange = mock((_val: boolean) => {});
    const onDocChange = mock((_val: boolean) => {});

    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={onImageModeChange}
        agentEnabled={false}
        onAgentEnabledChange={onAgentEnabledChange}
        documentChatEnabled={true}
        onDocumentChatEnabledChange={onDocChange}
      />,
    );

    const trigger = container.querySelector("button");
    act(() => {
      trigger?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    const options = Array.from(container.querySelectorAll('[role="option"]'));
    const agentOption = options.find((opt) =>
      opt.textContent?.includes("Agent 模式"),
    );
    expect(agentOption).toBeDefined();

    act(() => {
      agentOption?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onImageModeChange).toHaveBeenCalledWith(false);
    expect(onAgentEnabledChange).toHaveBeenCalledWith(true);
    expect(onDocChange).toHaveBeenCalledWith(false);
    expect(container.querySelector('[role="listbox"]')).toBeNull();
  });

  it("closes popover when pressing Escape key", () => {
    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={() => {}}
        agentEnabled={false}
        onAgentEnabledChange={() => {}}
      />,
    );

    const trigger = container.querySelector("button");
    act(() => {
      trigger?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(container.querySelector('[role="listbox"]')).not.toBeNull();

    act(() => {
      window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    });
    expect(container.querySelector('[role="listbox"]')).toBeNull();
  });

  it("disables trigger when disabled prop is true", () => {
    renderComponent(
      <ChatModeSelector
        imageMode={false}
        onImageModeChange={() => {}}
        agentEnabled={false}
        onAgentEnabledChange={() => {}}
        disabled={true}
      />,
    );

    const trigger = container.querySelector("button");
    expect((trigger as HTMLButtonElement)?.disabled).toBe(true);
  });
});
