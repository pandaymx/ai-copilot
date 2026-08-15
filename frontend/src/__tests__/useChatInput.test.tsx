import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, beforeEach, describe, expect, it } from "bun:test";
import type React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { useChatInput } from "../hooks/useChatInput";

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

let inputHookResult: ReturnType<typeof useChatInput> | null = null;

function InputTestComponent({
  currentSupportsVision = true,
  activeId = "sess-abc",
}: {
  currentSupportsVision?: boolean;
  activeId?: string | null;
}) {
  const hook = useChatInput({ currentSupportsVision, activeId });
  inputHookResult = hook;
  return <div id="input-test">{hook.input}</div>;
}

describe("useChatInput Hook Unit Tests", () => {
  beforeEach(() => {
    inputHookResult = null;
  });

  it("should manage input text, mode switches and attachments", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<InputTestComponent />);
    });

    expect(inputHookResult).not.toBeNull();
    expect(inputHookResult?.input).toBe("");
    expect(inputHookResult?.attachments).toEqual([]);
    expect(inputHookResult?.imageMode).toBe(false);
    expect(inputHookResult?.agentEnabled).toBe(false);
    expect(inputHookResult?.documentChatEnabled).toBe(false);

    // 更新输入文本与模式
    act(() => {
      inputHookResult?.setInput("测试问题");
      inputHookResult?.setImageMode(true);
      inputHookResult?.setAgentEnabled(true);
      inputHookResult?.setDocumentChatEnabled(true);
    });

    expect(inputHookResult?.input).toBe("测试问题");
    expect(inputHookResult?.imageMode).toBe(true);
    expect(inputHookResult?.agentEnabled).toBe(true);
    expect(inputHookResult?.documentChatEnabled).toBe(true);

    // 添加与移除附件
    act(() => {
      inputHookResult?.setAttachments([
        { id: "att-1", name: "doc.txt", type: "file", url: "" },
        {
          id: "att-2",
          name: "img.png",
          type: "image",
          url: "data:image/png;base64,123",
        },
      ]);
    });

    expect(inputHookResult?.attachments.length).toBe(2);

    act(() => {
      inputHookResult?.removeAttachment("att-1");
    });

    expect(inputHookResult?.attachments.length).toBe(1);
    expect(inputHookResult?.attachments[0].id).toBe("att-2");

    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("should handle drag and drop states", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<InputTestComponent />);
    });

    expect(inputHookResult?.isDraggingOver).toBe(false);

    const mockDragEvent = {
      preventDefault: () => {},
      stopPropagation: () => {},
      dataTransfer: {
        items: [{ kind: "file" }],
        files: [],
      },
    } as unknown as React.DragEvent;

    act(() => {
      inputHookResult?.handleDragEnter(mockDragEvent);
    });
    expect(inputHookResult?.isDraggingOver).toBe(true);

    act(() => {
      inputHookResult?.handleDragLeave(mockDragEvent);
    });
    expect(inputHookResult?.isDraggingOver).toBe(false);

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
