import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, describe, expect, it, mock } from "bun:test";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { KnowledgeUpload } from "../components/knowledge/knowledge-upload";
import * as api from "../lib/api";

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

describe("KnowledgeUpload Component Batch File Upload Tests", () => {
  it("should render upload tab buttons correctly", () => {
    const onSuccess = mock();
    const { container, unmount } = renderComponent(
      <KnowledgeUpload onSuccess={onSuccess} />,
    );

    expect(container.textContent).toContain("上传入库");
    expect(container.textContent).toContain("文本");
    expect(container.textContent).toContain("网页 URL");
    expect(container.textContent).toContain("批量文件");

    unmount();
  });

  it("should switch tabs and show batch file upload dropzone", () => {
    const onSuccess = mock();
    const { container, unmount } = renderComponent(
      <KnowledgeUpload onSuccess={onSuccess} />,
    );

    const batchTabBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("批量文件"),
    );
    expect(batchTabBtn).not.toBeUndefined();

    act(() => {
      batchTabBtn?.click();
    });

    expect(container.textContent).toContain("点击或拖拽文件到此处");
    expect(container.textContent).toContain("支持多选批量上传");

    unmount();
  });

  it("should handle multi-file selection and batch submission", async () => {
    const onSuccess = mock();

    // Mock globalThis.fetch
    const originalFetch = globalThis.fetch;
    let fetchCallCount = 0;
    globalThis.fetch = mock(async (url: string | URL | Request) => {
      fetchCallCount++;
      return new Response(
        JSON.stringify({
          success: true,
          sourceType: "TEXT",
          source: "doc",
          ingested: 3,
          skipped: 1,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }) as unknown as typeof fetch;

    const { container, unmount } = renderComponent(
      <KnowledgeUpload onSuccess={onSuccess} />,
    );

    // Switch to file tab
    const batchTabBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("批量文件"),
    );
    act(() => {
      batchTabBtn?.click();
    });

    // Simulate input file selection
    const fileInput = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    expect(fileInput).not.toBeNull();
    expect(fileInput.multiple).toBe(true);

    const file1 = new File(["Content 1"], "doc1.txt", { type: "text/plain" });
    const file2 = new File(["Content 2"], "doc2.md", { type: "text/plain" });

    // Mock File.prototype.text
    file1.text = async () => "Content 1";
    file2.text = async () => "Content 2";

    act(() => {
      const changeEvent = new Event("change", { bubbles: true });
      Object.defineProperty(fileInput, "files", {
        value: [file1, file2],
        writable: true,
      });
      fileInput.dispatchEvent(changeEvent);
    });

    expect(container.textContent).toContain("doc1.txt");
    expect(container.textContent).toContain("doc2.md");
    expect(container.textContent).toContain("已选择 2 个文件");
    expect(container.textContent).toContain("批量入库 (2)");

    // Click submit
    const submitBtn = Array.from(container.querySelectorAll("button")).find(
      (b) => b.textContent?.includes("批量入库"),
    );

    await act(async () => {
      submitBtn?.click();
    });

    expect(fetchCallCount).toBe(2);
    expect(onSuccess).toHaveBeenCalledWith(
      expect.objectContaining({
        success: true,
        source: "2 个文件",
        ingested: 6,
        skipped: 2,
      }),
    );

    globalThis.fetch = originalFetch;

    unmount();
  });
});
