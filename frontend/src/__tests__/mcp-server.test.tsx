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
import McpServerSettingsPage from "../app/settings/mcp-server/page";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock((url: string) => {
    if (url.includes("/mcp/status")) {
      return Promise.resolve(
        new Response(
          JSON.stringify({
            enabled: true,
            serverName: "ai-copilot-mcp-server",
            serverVersion: "2.0.0",
            toolsCount: 5,
            ragResourceEnabled: true,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      );
    }
    if (url.includes("/mcp/tools")) {
      return Promise.resolve(
        new Response(
          JSON.stringify({
            tools: [
              {
                name: "calculator",
                description: "基础数学计算器",
                inputSchema: { type: "object" },
              },
            ],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      );
    }
    return Promise.resolve(
      new Response(JSON.stringify({ resources: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
  });
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
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

describe("McpServerSettingsPage Component Tests", () => {
  it("should render MCP server status and configuration tabs", async () => {
    const { container, unmount } = renderComponent(<McpServerSettingsPage />);

    await act(async () => {
      await new Promise((r) => setTimeout(r, 40));
    });

    expect(container.textContent).toContain("MCP Server 模式");
    expect(container.textContent).toContain("MCP Server 已就绪");
    expect(container.textContent).toContain("Claude Desktop 接入配置");

    unmount();
  });
});
