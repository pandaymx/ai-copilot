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
import { TokenBudgetBar } from "../components/chat/token-budget-bar";
import { TokenBudgetProvider } from "../context/token-budget-context";

let container: HTMLDivElement | null = null;
let root: ReturnType<typeof createRoot> | null = null;
let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock().mockResolvedValue(
    new Response(
      JSON.stringify({
        month: "2026-08",
        usedTokens: 450000,
        quotaTokens: 1000000,
        remainingTokens: 550000,
        usedPercent: 45.0,
        alertThresholdPercent: 80.0,
      }),
      { status: 200 },
    ),
  );
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

describe("TokenBudgetBar component", () => {
  it("should render full TokenBudgetBar and display quota info", async () => {
    await act(async () => {
      root?.render(
        <TokenBudgetProvider>
          <TokenBudgetBar />
        </TokenBudgetProvider>,
      );
    });

    // Wait a tick for fetch to settle
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    const text = container?.textContent || "";
    expect(text).toContain("月度 Token 配额");
    expect(text).toContain("45.0%");
  });

  it("should render compact TokenBudgetBar in header mode", async () => {
    await act(async () => {
      root?.render(
        <TokenBudgetProvider>
          <TokenBudgetBar compact />
        </TokenBudgetProvider>,
      );
    });

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    const text = container?.textContent || "";
    expect(text).toContain("45%");
  });

  it("should toggle popover on click", async () => {
    await act(async () => {
      root?.render(
        <TokenBudgetProvider>
          <TokenBudgetBar />
        </TokenBudgetProvider>,
      );
    });

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    const clickable = container?.querySelector("button") as HTMLElement;
    expect(clickable).not.toBeNull();

    await act(async () => {
      clickable.click();
    });

    const textAfterClick = container?.textContent || "";
    expect(textAfterClick).toContain("实时 Token 预算明细");
    expect(textAfterClick).toContain("进入成本看板总览");
  });
});
