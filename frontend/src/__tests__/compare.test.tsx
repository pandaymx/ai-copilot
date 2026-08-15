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
} from "@/components/chat/message-bubble";
import { ModelCompareModal } from "@/components/chat/model-compare-modal";

describe("Stream Interruption, Regenerate & Multi-Model Compare Suite", () => {
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

  it("User Message Bubble enables inline editing and resend", async () => {
    const userMsg: ChatMessage = {
      id: "msg-user-1",
      role: "user",
      content: "请编写一个快速排序算法",
    };

    const handleEditAndResend = mock((_text: string) => {});
    const handleCompare = mock((_prompt: string) => {});

    await act(async () => {
      if (root) {
        root.render(
          <MessageBubble
            message={userMsg}
            onEditAndResend={handleEditAndResend}
            onOpenCompare={handleCompare}
          />,
        );
      }
    });

    expect(container?.innerHTML).toContain("请编写一个快速排序算法");

    // 触发编辑按钮
    const editBtn = container?.querySelector(
      "button[title='编辑并重新发送']",
    ) as HTMLButtonElement;
    expect(editBtn).toBeTruthy();

    await act(async () => {
      editBtn.click();
    });

    // 渲染了文本编辑框
    const textarea = container?.querySelector(
      "textarea",
    ) as HTMLTextAreaElement;
    expect(textarea).toBeTruthy();
    expect(textarea.value).toBe("请编写一个快速排序算法");

    // 修改内容并点击保存并重发
    await act(async () => {
      const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
        window.HTMLTextAreaElement.prototype,
        "value",
      )?.set;
      nativeInputValueSetter?.call(
        textarea,
        "请编写一个快速排序算法并附带复杂度分析",
      );
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
      textarea.dispatchEvent(new Event("change", { bubbles: true }));
    });

    const saveBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("保存并重发"));
    expect(saveBtn).toBeTruthy();

    await act(async () => {
      saveBtn?.click();
    });

    expect(handleEditAndResend).toHaveBeenCalledWith(
      "请编写一个快速排序算法并附带复杂度分析",
    );
  });

  it("Assistant Message Bubble renders model switch menu and compare button", async () => {
    const assistantMsg: ChatMessage = {
      id: "msg-ai-1",
      role: "assistant",
      content: "这是模型的回答内容。",
    };

    const handleRegen = mock(() => {});
    const handleRegenWithModel = mock(
      (_provider: string, _model: string) => {},
    );
    const handleCompare = mock((_prompt: string) => {});

    await act(async () => {
      if (root) {
        root.render(
          <MessageBubble
            message={assistantMsg}
            onRegenerate={handleRegen}
            onRegenerateWithModel={handleRegenWithModel}
            onOpenCompare={handleCompare}
          />,
        );
      }
    });

    expect(container?.innerHTML).toContain("这是模型的回答内容。");

    // 验证换模型按钮与下拉菜单
    const modelSwitchBtn = container?.querySelector(
      "button[title='换模型重新生成...']",
    ) as HTMLButtonElement;
    expect(modelSwitchBtn).toBeTruthy();

    await act(async () => {
      modelSwitchBtn.click();
    });

    expect(container?.innerHTML).toContain("换模型重新生成");
    expect(container?.innerHTML).toContain("Claude 3.5 Sonnet");

    // 点击换模型选项
    const claudeBtn = Array.from(
      container?.querySelectorAll("button") || [],
    ).find((b) => b.textContent?.includes("Claude 3.5 Sonnet"));
    expect(claudeBtn).toBeTruthy();

    await act(async () => {
      claudeBtn?.click();
    });

    expect(handleRegenWithModel).toHaveBeenCalledWith(
      "anthropic",
      "claude-3-5-sonnet",
    );

    // 点击对比按钮
    const compareBtn = container?.querySelector(
      "button[title='多模型对比生成']",
    ) as HTMLButtonElement;
    expect(compareBtn).toBeTruthy();

    await act(async () => {
      compareBtn.click();
    });

    expect(handleCompare).toHaveBeenCalledWith("这是模型的回答内容。");
  });

  it("ModelCompareModal renders side-by-side columns and supports model selection", async () => {
    const handleAdopt = mock(
      (_content: string, _provider: string, _model: string) => {},
    );
    const handleClose = mock(() => {});

    await act(async () => {
      if (root) {
        root.render(
          <ModelCompareModal
            open={true}
            onClose={handleClose}
            initialPrompt="并发模型解析"
            onAdopt={handleAdopt}
          />,
        );
      }
    });

    expect(container?.innerHTML).toContain("多模型并排对比竞技场");
    expect(container?.innerHTML).toContain("2 模型并发");
    expect(container?.innerHTML).toContain("并发模型解析");

    // 点击添加第 3 个模型
    const addBtn = Array.from(container?.querySelectorAll("button") || []).find(
      (b) => b.textContent?.includes("+ 添加第 3 个模型"),
    );
    expect(addBtn).toBeTruthy();

    await act(async () => {
      addBtn?.click();
    });

    expect(container?.innerHTML).toContain("3 模型并发");
  });
});
