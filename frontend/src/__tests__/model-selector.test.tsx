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
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import {
  formatModelPriceText,
  isFreePrice,
  isVisionModel,
  ModelSelector,
  type SelectedModel,
} from "../components/chat/model-selector";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock().mockResolvedValue(
    new Response(JSON.stringify({ providers: [], models: {} }), {
      status: 200,
    }),
  );
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

describe("ModelSelector Component Tests - components/chat/model-selector.tsx", () => {
  describe("isVisionModel Utility", () => {
    it("should correctly identify vision and multimodal tagged model entries", () => {
      expect(isVisionModel(null)).toBeFalse();
      expect(isVisionModel(undefined)).toBeFalse();
      expect(
        isVisionModel({
          id: "m1",
          displayName: "M1",
          description: "",
          tags: ["chat"],
        }),
      ).toBeFalse();
      expect(
        isVisionModel({
          id: "m2",
          displayName: "M2",
          description: "",
          tags: ["vision"],
        }),
      ).toBeTrue();
      expect(
        isVisionModel({
          id: "m3",
          displayName: "M3",
          description: "",
          tags: ["multimodal"],
        }),
      ).toBeTrue();
    });
  });

  describe("Price Estimation Utility", () => {
    it("should correctly identify free models", () => {
      expect(isFreePrice(0, 0)).toBeTrue();
      expect(isFreePrice(undefined, undefined)).toBeTrue();
      expect(isFreePrice(0.001, 0.002)).toBeFalse();
    });

    it("should format model estimated price correctly", () => {
      expect(formatModelPriceText(0, 0)).toBe("免费");
      expect(formatModelPriceText(0.001, 0.002)).toBe("预估 ¥0.0030/次");
      expect(formatModelPriceText(0.018, 0.072)).toBe("预估 ¥0.0900/次");
    });
  });

  describe("ModelSelector UI Component", () => {
    it("should render initial selected provider, model name, and estimated price", () => {
      const selected: SelectedModel = {
        provider: "google",
        model: "gemini-3.6-flash",
      };

      const { container, unmount } = renderComponent(
        <ModelSelector value={selected} onChange={() => {}} />,
      );

      expect(container.textContent).toContain("Google Gemini");
      expect(container.textContent).toContain("Gemini 3.6 Flash");
      expect(container.textContent).toContain("预估 ¥0.0025/次");

      unmount();
    });

    it("should toggle popover dropdown when trigger button is clicked", () => {
      const selected: SelectedModel = {
        provider: "deepseek",
        model: "deepseek-chat",
      };

      const { container, unmount } = renderComponent(
        <ModelSelector value={selected} onChange={() => {}} />,
      );

      const triggerBtn = container.querySelector(
        'button[aria-label="选择 AI 模型"]',
      ) as HTMLButtonElement;
      expect(triggerBtn).not.toBeNull();

      // Popover is initially closed
      expect(container.textContent).not.toContain("1. AI 供应商");

      // Click trigger to open dropdown
      act(() => {
        triggerBtn.click();
      });

      expect(container.textContent).toContain("1. AI 供应商");
      expect(container.textContent).toContain("2. 选择模型");

      unmount();
    });

    it("should trigger onChange when selecting a different model from catalog", () => {
      let newSelected: SelectedModel | null = null;
      const selected: SelectedModel = {
        provider: "google",
        model: "gemini-3.6-flash",
      };

      const { container, unmount } = renderComponent(
        <ModelSelector
          value={selected}
          onChange={(val) => {
            newSelected = val;
          }}
        />,
      );

      const triggerBtn = container.querySelector(
        'button[aria-label="选择 AI 模型"]',
      ) as HTMLButtonElement;
      act(() => {
        triggerBtn.click();
      });

      // Find model button for Gemini 3.5 Flash inside popover
      const modelBtns = Array.from(container.querySelectorAll("button"));
      const targetModelBtn = modelBtns.find((b) =>
        b.textContent?.includes("Gemini 3.5 Flash"),
      ) as HTMLButtonElement;

      expect(targetModelBtn).toBeDefined();
      act(() => {
        targetModelBtn.click();
      });

      expect(newSelected!).toEqual({
        provider: "google",
        model: "gemini-3.5-flash",
      });

      unmount();
    });

    it("should allow entering and applying a custom model ID", () => {
      let newSelected: SelectedModel | null = null;
      const selected: SelectedModel = {
        provider: "openai",
        model: "gpt-4o",
      };

      const { container, unmount } = renderComponent(
        <ModelSelector
          value={selected}
          onChange={(val) => {
            newSelected = val;
          }}
        />,
      );

      const triggerBtn = container.querySelector(
        'button[aria-label="选择 AI 模型"]',
      ) as HTMLButtonElement;
      act(() => {
        triggerBtn.click();
      });

      const input = container.querySelector(
        'input[placeholder="输入模型 ID (如 gpt-4o-mini)"]',
      ) as HTMLInputElement;
      expect(input).not.toBeNull();

      act(() => {
        const reactPropsKey = Object.keys(input).find((k) =>
          k.startsWith("__reactProps$"),
        );
        if (reactPropsKey) {
          // biome-ignore lint/suspicious/noExplicitAny: access React internal props in test
          (input as any)[reactPropsKey].onChange({
            target: { value: "gpt-4o-mini" },
          });
        }
      });

      const applyBtn = Array.from(container.querySelectorAll("button")).find(
        (b) => b.textContent === "应用",
      ) as HTMLButtonElement;
      expect(applyBtn).toBeDefined();

      act(() => {
        applyBtn.click();
      });

      expect(newSelected!).toEqual({
        provider: "openai",
        model: "gpt-4o-mini",
      });

      unmount();
    });
  });
});
