import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, beforeEach, describe, expect, it, mock } from "bun:test";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import {
  type ArtifactItem,
  StreamStore,
  type ToolCallItem,
  useSpringAiStream,
} from "../hooks/useSpringAiStream";

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

// Mock @microsoft/fetch-event-source
let mockFetchEventSourceImpl: (
  _url: string,
  options: {
    onmessage: (ev: { data: string }) => void;
    onerror: (err: Error) => void;
  },
) => Promise<void>;

mock.module("@microsoft/fetch-event-source", () => ({
  fetchEventSource: (
    url: string,
    options: {
      onmessage: (ev: { data: string }) => void;
      onerror: (err: Error) => void;
    },
  ) => mockFetchEventSourceImpl(url, options),
}));

function renderHook<T>(hookFn: () => T) {
  const result: { current: T } = { current: null as unknown as T };
  function TestComponent() {
    result.current = hookFn();
    return null;
  }
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(React.createElement(TestComponent));
  });
  return {
    result,
    rerender: () => {
      act(() => {
        root.render(React.createElement(TestComponent));
      });
    },
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

describe("StreamStore Unit Tests", () => {
  let store: StreamStore;

  beforeEach(() => {
    store = new StreamStore();
  });

  it("should initialize with default empty state", () => {
    const snapshot = store.getSnapshot();
    expect(snapshot.content).toBe("");
    expect(snapshot.thinking).toBe("");
    expect(snapshot.usage).toBeNull();
    expect(snapshot.toolCalls).toEqual({});
    expect(snapshot.artifacts).toEqual({});
  });

  it("should update content, thinking, and usage and notify listeners", () => {
    let notified = false;
    const unsubscribe = store.subscribe(() => {
      notified = true;
    });

    const usage = { promptTokens: 10, completionTokens: 20, totalTokens: 30 };
    store.update("Hello", "Thinking...", usage);

    const snapshot = store.getSnapshot();
    expect(snapshot.content).toBe("Hello");
    expect(snapshot.thinking).toBe("Thinking...");
    expect(snapshot.usage).toEqual(usage);
    expect(notified).toBeTrue();

    unsubscribe();
  });

  it("should update tool calls incrementally", () => {
    let notifyCount = 0;
    store.subscribe(() => {
      notifyCount++;
    });

    // 1. Initial calling state
    store.updateToolCall("call-1", {
      name: "search_web",
      arguments: '{"query":"test"}',
      status: "calling",
    });

    let snapshot = store.getSnapshot();
    expect(snapshot.toolCalls["call-1"]).toEqual({
      callId: "call-1",
      name: "search_web",
      arguments: '{"query":"test"}',
      status: "calling",
    });
    expect(notifyCount).toBe(1);

    // 2. Patch with result
    store.updateToolCall("call-1", {
      result: "search result data",
      status: "success",
    });

    snapshot = store.getSnapshot();
    expect(snapshot.toolCalls["call-1"]).toEqual({
      callId: "call-1",
      name: "search_web",
      arguments: '{"query":"test"}',
      result: "search result data",
      status: "success",
    });
    expect(notifyCount).toBe(2);
  });

  it("should update artifacts incrementally", () => {
    let notifyCount = 0;
    store.subscribe(() => {
      notifyCount++;
    });

    store.updateArtifact("art-1", {
      artifactType: "code",
      title: "Main.java",
      content: "public class Main {}",
      status: "streaming",
    });

    let snapshot = store.getSnapshot();
    expect(snapshot.artifacts["art-1"]).toEqual({
      artifactId: "art-1",
      artifactType: "code",
      title: "Main.java",
      content: "public class Main {}",
      status: "streaming",
    });

    store.updateArtifact("art-1", {
      status: "complete",
    });

    snapshot = store.getSnapshot();
    expect(snapshot.artifacts["art-1"].status).toBe("complete");
    expect(notifyCount).toBe(2);
  });

  it("should reset store state back to initial and notify listeners", () => {
    store.update("Hello", "Thinking...", null);
    store.updateToolCall("call-1", {
      name: "test",
      arguments: "",
      status: "calling",
    });
    store.updateArtifact("art-1", { artifactType: "image" });

    let resetNotified = false;
    store.subscribe(() => {
      resetNotified = true;
    });

    store.reset();

    const snapshot = store.getSnapshot();
    expect(snapshot.content).toBe("");
    expect(snapshot.thinking).toBe("");
    expect(snapshot.usage).toBeNull();
    expect(snapshot.toolCalls).toEqual({});
    expect(snapshot.artifacts).toEqual({});
    expect(resetNotified).toBeTrue();
  });
});

describe("useSpringAiStream SSE Frame Parsing & Behavior", () => {
  beforeEach(() => {
    mockFetchEventSourceImpl = async () => {};
  });

  it("should parse conversation frame and trigger onConversationId", async () => {
    let receivedConversationId = "";
    mockFetchEventSourceImpl = async (_url, options) => {
      options.onmessage({
        data: JSON.stringify({
          type: "conversation",
          conversationId: "conv-999",
        }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onConversationId: (id: string) => {
          receivedConversationId = id;
        },
      }),
    );

    act(() => {
      result.current.send("Hello");
    });

    await new Promise((r) => setTimeout(r, 20));
    expect(receivedConversationId).toBe("conv-999");
    unmount();
  });

  it("should parse reasoning frame and append thinking", async () => {
    let reasoningOutput = "";
    mockFetchEventSourceImpl = async (_url, options) => {
      options.onmessage({
        data: JSON.stringify({
          type: "reasoning",
          reasoning: "Step 1: analyze input. ",
        }),
      });
      options.onmessage({
        data: JSON.stringify({
          type: "reasoning",
          reasoning: "Step 2: build plan.",
        }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onReasoning: (delta) => {
          reasoningOutput += delta;
        },
      }),
    );

    act(() => {
      result.current.send("Test input");
    });
    await new Promise((r) => setTimeout(r, 20));

    expect(reasoningOutput).toBe("Step 1: analyze input. Step 2: build plan.");
    unmount();
  });

  it("should parse artifact frame and populate streamStore and callback", async () => {
    let capturedArtifact: ArtifactItem | null = null;

    mockFetchEventSourceImpl = async (_url, options) => {
      options.onmessage({
        data: JSON.stringify({
          type: "artifact",
          artifactId: "art-100",
          artifactType: "html",
          title: "Dashboard Mockup",
          content: "<div>Hello</div>",
        }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onArtifact: (item) => {
          capturedArtifact = item;
        },
      }),
    );

    act(() => {
      result.current.send("Show UI");
    });
    await new Promise((r) => setTimeout(r, 20));

    expect(capturedArtifact).not.toBeNull();
    const artifact = capturedArtifact as unknown as ArtifactItem;
    expect(artifact?.artifactId).toBe("art-100");
    expect(artifact?.artifactType).toBe("html");
    expect(artifact?.title).toBe("Dashboard Mockup");
    expect(
      result.current.streamStore.getSnapshot().artifacts["art-100"],
    ).toBeDefined();
    unmount();
  });

  it("should parse tool_call frame with innerThought JSON and regex fallback", async () => {
    const capturedCalls: ToolCallItem[] = [];

    mockFetchEventSourceImpl = async (_url, options) => {
      // Standard JSON innerThought
      options.onmessage({
        data: JSON.stringify({
          type: "tool_call",
          toolCallId: "call-clean",
          toolName: "get_weather",
          arguments: JSON.stringify({
            city: "Beijing",
            innerThought: "Checking weather in Beijing",
          }),
        }),
      });
      // Partial/Raw string fallback regex innerThought
      options.onmessage({
        data: JSON.stringify({
          type: "tool_call",
          toolCallId: "call-fallback",
          toolName: "search_db",
          arguments:
            '{"query": "users", "innerThought": "Searching database for user records...',
        }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onToolCall: (item) => {
          capturedCalls.push(item);
        },
      }),
    );

    act(() => {
      result.current.send("Execute tools");
    });
    await new Promise((r) => setTimeout(r, 20));

    expect(capturedCalls.length).toBe(2);
    expect(capturedCalls[0].innerThought).toBe("Checking weather in Beijing");
    expect(capturedCalls[1].innerThought).toBe(
      "Searching database for user records...",
    );
    unmount();
  });

  it("should parse tool_result frame with success and error status", async () => {
    const results: ToolCallItem[] = [];

    mockFetchEventSourceImpl = async (_url, options) => {
      options.onmessage({
        data: JSON.stringify({
          type: "tool_result",
          toolCallId: "call-1",
          toolName: "get_weather",
          result: '{"temp": 25}',
          isError: false,
        }),
      });
      options.onmessage({
        data: JSON.stringify({
          type: "tool_result",
          toolCallId: "call-2",
          toolName: "search_db",
          result: "Database connection failed",
          isError: true,
        }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onToolResult: (item) => {
          results.push(item);
        },
      }),
    );

    act(() => {
      result.current.send("Tool results test");
    });
    await new Promise((r) => setTimeout(r, 20));

    expect(results.length).toBe(2);
    expect(results[0].status).toBe("success");
    expect(results[1].status).toBe("error");
    unmount();
  });

  it("should parse usage frame and trigger onUsage", async () => {
    let capturedUsage: {
      promptTokens: number;
      completionTokens: number;
      totalTokens: number;
      estimatedCostRmb?: number;
    } | null = null;

    mockFetchEventSourceImpl = async (_url, options) => {
      options.onmessage({
        data: JSON.stringify({
          type: "usage",
          usage: {
            promptTokens: 100,
            completionTokens: 50,
            totalTokens: 150,
            estimatedCostRmb: 0.005,
          },
        }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onUsage: (u) => {
          capturedUsage = u;
        },
      }),
    );

    act(() => {
      result.current.send("Tokens check");
    });
    await new Promise((r) => setTimeout(r, 20));

    expect(capturedUsage!).toEqual({
      promptTokens: 100,
      completionTokens: 50,
      totalTokens: 150,
      estimatedCostRmb: 0.005,
    });
    unmount();
  });

  it("should parse text chunks, handle error frames via error state, and filter incomplete JSON", async () => {
    let finalFinishedContent = "";

    mockFetchEventSourceImpl = async (_url, options) => {
      // Plain text chunk
      options.onmessage({ data: "Hello, " });
      // Structured delta content
      options.onmessage({ data: JSON.stringify({ content: "world!" }) });
      // Incomplete JSON string -> should be suppressed
      options.onmessage({ data: '{"type": "chat", "content": "partial...' });
      // Error frame -> should set error state (NOT pollute the reply text)
      options.onmessage({
        data: JSON.stringify({ type: "error", message: "Quota exceeded" }),
      });
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onFinish: (content) => {
          finalFinishedContent = content;
        },
      }),
    );

    act(() => {
      result.current.send("Chunk test");
    });
    await new Promise((r) => setTimeout(r, 20));

    // 正常文本增量仍正确累加
    expect(finalFinishedContent).toContain("Hello, world!");
    // 不完整 JSON 被忽略（不进入正文）
    expect(finalFinishedContent).not.toContain("partial...");
    // 业务 error 帧不再作为正文文本追加（修复：改为置位 error 状态）
    expect(finalFinishedContent).not.toContain("⚠️ [服务异常]");
    // error 帧通过统一的 error 状态暴露，供错误卡片/重试联动使用
    expect(result.current.error?.message).toBe("Quota exceeded");
    unmount();
  });

  it("should handle error thrown inside fetchEventSource", async () => {
    mockFetchEventSourceImpl = async (_url, options) => {
      const err = new Error("Network disconnection");
      options.onerror(err);
      throw err;
    };

    const { result, unmount } = renderHook(() => useSpringAiStream());

    act(() => {
      result.current.send("Fault test");
    });
    await new Promise((r) => setTimeout(r, 20));

    expect(result.current.error?.message).toBe("Network disconnection");
    unmount();
  });

  it("should trigger stop() and call onFinish with accumulated content", async () => {
    let finishedContent = "";

    mockFetchEventSourceImpl = async (_url, options) => {
      options.onmessage({ data: "Partial stream response..." });
      await new Promise(() => {});
    };

    const { result, unmount } = renderHook(() =>
      useSpringAiStream({
        onFinish: (content) => {
          finishedContent = content;
        },
      }),
    );

    act(() => {
      result.current.send("Long request");
    });
    await new Promise((r) => setTimeout(r, 20));

    act(() => {
      result.current.stop();
    });

    expect(finishedContent).toBe("Partial stream response...");
    expect(result.current.loading).toBeFalse();
    unmount();
  });
});
