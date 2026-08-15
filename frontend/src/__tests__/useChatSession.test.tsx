import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, beforeEach, describe, expect, it } from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { useChatSession } from "../hooks/useChatSession";

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

let sessionHookResult: ReturnType<typeof useChatSession> | null = null;

function SessionTestComponent() {
  const hook = useChatSession();
  sessionHookResult = hook;
  return <div id="session-test">{hook.activeId ?? "none"}</div>;
}

describe("useChatSession Hook Unit Tests", () => {
  beforeEach(() => {
    sessionHookResult = null;
    localStorage.clear();
  });

  it("should initialize with default states and provide session actions", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<SessionTestComponent />);
    });

    expect(sessionHookResult).not.toBeNull();
    expect(sessionHookResult?.messages).toEqual([]);
    expect(typeof sessionHookResult?.selectSession).toBe("function");
    expect(typeof sessionHookResult?.deleteSession).toBe("function");
    expect(typeof sessionHookResult?.renameSession).toBe("function");
    expect(typeof sessionHookResult?.newSession).toBe("function");

    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("should reset activeId and messages on newSession()", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<SessionTestComponent />);
    });

    act(() => {
      sessionHookResult?.setActiveId("sess-123");
      sessionHookResult?.setMessages([
        { id: "m1", role: "user", content: "Hello" },
      ]);
    });

    expect(sessionHookResult?.activeId).toBe("sess-123");
    expect(sessionHookResult?.messages.length).toBe(1);

    act(() => {
      sessionHookResult?.newSession();
    });

    expect(sessionHookResult?.activeId).toBeNull();
    expect(sessionHookResult?.messages.length).toBe(0);

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
