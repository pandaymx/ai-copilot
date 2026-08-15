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
import { act } from "react";
import { createRoot } from "react-dom/client";
import { ModelPerformanceModal } from "../components/chat/model-performance-modal";
import { StreamingMetricsBar } from "../components/chat/streaming-metrics-bar";
import { StreamStore } from "../hooks/useSpringAiStream";

let container: HTMLDivElement | null = null;
let root: ReturnType<typeof createRoot> | null = null;

const originalFetch = globalThis.fetch;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  if (root && container) {
    act(() => {
      root?.unmount();
    });
    container.remove();
  }
  globalThis.fetch = originalFetch;
});

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

describe("StreamingMetricsBar Component", () => {
  it("renders final metrics correctly with TTFT, tokensPerSecond, and totalDuration", () => {
    act(() => {
      root?.render(
        <StreamingMetricsBar
          metrics={{
            timeToFirstToken: 240,
            tokensPerSecond: 45.5,
            totalDuration: 1800,
            toolCallDuration: 350,
            isEstimated: false,
          }}
          streaming={false}
          contentLength={150}
        />,
      );
    });

    const html = container?.innerHTML ?? "";
    expect(html).toContain("240ms");
    expect(html).toContain("45.5");
    expect(html).toContain("1.8s");
    expect(html).toContain("350ms");
  });

  it("renders live streaming state with Stream pulse badge", () => {
    act(() => {
      root?.render(<StreamingMetricsBar streaming={true} contentLength={80} />);
    });

    const html = container?.innerHTML ?? "";
    expect(html).toContain("Stream");
  });

  it("does not render when metrics is null and not streaming", () => {
    act(() => {
      root?.render(<StreamingMetricsBar metrics={null} streaming={false} />);
    });

    expect(container?.innerHTML).toBe("");
  });
});

describe("ModelPerformanceModal Component", () => {
  const mockMetricsData = {
    timestamp: Date.now(),
    models: [
      {
        providerId: "deepseek",
        modelId: "deepseek-chat",
        sampleCount: 12,
        p50TtftMs: 250,
        p90TtftMs: 450,
        avgTtftMs: 280,
        minTtftMs: 180,
        maxTtftMs: 500,
        p50TotalDurationMs: 1200,
        p90TotalDurationMs: 2100,
        avgTotalDurationMs: 1400,
        avgTokensPerSecond: 52.3,
        maxTokensPerSecond: 65.0,
        avgToolCallDurationMs: 0,
        lowSampleWarning: false,
      },
      {
        providerId: "openai",
        modelId: "gpt-4o",
        sampleCount: 3,
        p50TtftMs: 350,
        p90TtftMs: 600,
        avgTtftMs: 380,
        minTtftMs: 300,
        maxTtftMs: 650,
        p50TotalDurationMs: 1800,
        p90TotalDurationMs: 3000,
        avgTotalDurationMs: 2000,
        avgTokensPerSecond: 40.0,
        maxTokensPerSecond: 48.0,
        avgToolCallDurationMs: 200,
        lowSampleWarning: true,
      },
    ],
  };

  it("renders modal header and metrics correctly when open", async () => {
    globalThis.fetch = mock().mockResolvedValue(
      new Response(JSON.stringify(mockMetricsData), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    ) as unknown as typeof fetch;

    await act(async () => {
      root?.render(<ModelPerformanceModal isOpen={true} onClose={() => {}} />);
    });

    // 等待微任务与 fetch
    await new Promise((r) => setTimeout(r, 50));

    const html = container?.innerHTML ?? "";
    expect(html).toContain("模型流式性能大盘");
    expect(html).toContain("deepseek-chat");
    expect(html).toContain("gpt-4o");
    expect(html).toContain("低样本");
  });
});

describe("StreamStore Metrics", () => {
  it("updates and resets metrics in StreamStore", () => {
    const store = new StreamStore();
    expect(store.getSnapshot().metrics).toBeNull();

    store.updateMetrics({
      timeToFirstToken: 200,
      tokensPerSecond: 48.0,
      totalDuration: 1500,
      toolCallDuration: 0,
      isEstimated: false,
    });

    expect(store.getSnapshot().metrics?.timeToFirstToken).toBe(200);
    expect(store.getSnapshot().metrics?.tokensPerSecond).toBe(48.0);

    store.reset();
    expect(store.getSnapshot().metrics).toBeNull();
  });
});
