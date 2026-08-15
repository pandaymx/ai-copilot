import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { RateLimitIndicator } from "@/components/chat/rate-limit-indicator";
import type { RateLimitStatus } from "@/lib/api";

describe("RateLimitIndicator Component Suite", () => {
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

  it("renders compact pill when remaining requests and monthly quota are abundant", async () => {
    const abundantStatus: RateLimitStatus = {
      remainingRequests: 18,
      capacity: 20,
      windowSeconds: 60,
      resetAfterSeconds: 0,
      resetAtMs: Date.now(),
      monthlyRemainingTokens: 900000,
      monthlyQuotaTokens: 1000000,
      monthlyUsedPercent: 10.0,
      isRateLimited: false,
      isQuotaExhausted: false,
    };

    await act(async () => {
      if (root) {
        root.render(<RateLimitIndicator externalStatus={abundantStatus} />);
      }
    });

    expect(container?.innerHTML).toContain("请求余量: 18/20");
  });

  it("renders amber warning banner when window requests are nearly exhausted (<= 5)", async () => {
    const nearLimitStatus: RateLimitStatus = {
      remainingRequests: 3,
      capacity: 20,
      windowSeconds: 60,
      resetAfterSeconds: 15,
      resetAtMs: Date.now() + 15000,
      monthlyRemainingTokens: 800000,
      monthlyQuotaTokens: 1000000,
      monthlyUsedPercent: 20.0,
      isRateLimited: false,
      isQuotaExhausted: false,
    };

    await act(async () => {
      if (root) {
        root.render(<RateLimitIndicator externalStatus={nearLimitStatus} />);
      }
    });

    expect(container?.innerHTML).toContain("短时请求频率接近上限");
    expect(container?.innerHTML).toContain("3/20");
    expect(container?.innerHTML).toContain("15s 后重置");
  });

  it("renders friendly rose alert and countdown when rate limited", async () => {
    const rateLimitedStatus: RateLimitStatus = {
      remainingRequests: 0,
      capacity: 20,
      windowSeconds: 60,
      resetAfterSeconds: 25,
      resetAtMs: Date.now() + 25000,
      monthlyRemainingTokens: 700000,
      monthlyQuotaTokens: 1000000,
      monthlyUsedPercent: 30.0,
      isRateLimited: true,
      isQuotaExhausted: false,
    };

    await act(async () => {
      if (root) {
        root.render(<RateLimitIndicator externalStatus={rateLimitedStatus} />);
      }
    });

    expect(container?.innerHTML).toContain("请求过于频繁（已触发速率限制）");
    expect(container?.innerHTML).toContain("25s");
  });

  it("renders quota exhausted warning when monthly token quota is 0", async () => {
    const exhaustedStatus: RateLimitStatus = {
      remainingRequests: 20,
      capacity: 20,
      windowSeconds: 60,
      resetAfterSeconds: 0,
      resetAtMs: Date.now(),
      monthlyRemainingTokens: 0,
      monthlyQuotaTokens: 1000000,
      monthlyUsedPercent: 100.0,
      isRateLimited: false,
      isQuotaExhausted: true,
    };

    await act(async () => {
      if (root) {
        root.render(<RateLimitIndicator externalStatus={exhaustedStatus} />);
      }
    });

    expect(container?.innerHTML).toContain("月度 Token 配额已耗尽");
    expect(container?.innerHTML).toContain("本月额度已用尽");
  });
});
