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
import { PersonaMarketModal } from "@/components/chat/persona-market-modal";
import type { Persona } from "@/lib/api";

const mockPersonas: Persona[] = [
  {
    id: "architect",
    name: "全栈架构师",
    description: "专精系统设计与微服务",
    avatar: "🏗️",
    category: "开发架构",
    systemPrompt: "你是一位全栈架构师",
    temperature: 0.4,
    tags: ["架构", "微服务"],
    isBuiltin: true,
  },
  {
    id: "qa_expert",
    name: "测试质量专家",
    description: "专精测试用例与质量保障",
    avatar: "🧪",
    category: "测试质量",
    systemPrompt: "你是一位测试专家",
    temperature: 0.3,
    tags: ["测试", "QA"],
    isBuiltin: true,
  },
];

describe("Persona Market (Persona Store) Frontend Suite", () => {
  let container: HTMLDivElement | null = null;
  let root: ReturnType<typeof createRoot> | null = null;
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);

    globalThis.fetch = mock(async (url: string | URL | Request) => {
      const urlStr = url.toString();
      if (urlStr.includes("/api/personas/match")) {
        return new Response(
          JSON.stringify({
            recommendedPersona: mockPersonas[1],
            confidence: 0.92,
            reason: "目标与测试强相关",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      return new Response(JSON.stringify(mockPersonas), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }) as unknown as typeof fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    const currentRoot = root;
    const currentContainer = container;
    if (currentRoot && currentContainer) {
      act(() => {
        currentRoot.unmount();
      });
      currentContainer.remove();
    }
  });

  it("renders PersonaMarketModal with categories, search and persona cards", async () => {
    const handleClose = mock(() => {});
    const handleSelectPersona = mock((_p: Persona | null) => {});

    await act(async () => {
      if (root) {
        root.render(
          <PersonaMarketModal
            isOpen={true}
            onClose={handleClose}
            selectedPersona={null}
            onSelectPersona={handleSelectPersona}
          />,
        );
      }
    });

    expect(container?.innerHTML).toContain("智能体角色市场");
    expect(container?.innerHTML).toContain("Persona Store");
    expect(container?.innerHTML).toContain("全栈架构师");
    expect(container?.innerHTML).toContain("开发架构");
    expect(container?.innerHTML).toContain("测试质量专家");
  });

  it("handles activating a persona", async () => {
    const handleClose = mock(() => {});
    const handleSelectPersona = mock((_p: Persona | null) => {});

    await act(async () => {
      if (root) {
        root.render(
          <PersonaMarketModal
            isOpen={true}
            onClose={handleClose}
            selectedPersona={null}
            onSelectPersona={handleSelectPersona}
          />,
        );
      }
    });

    const applyBtns = container?.querySelectorAll("button");
    const applyBtn = Array.from(applyBtns || []).find((b) =>
      b.textContent?.includes("应用人设"),
    );
    expect(applyBtn).toBeDefined();

    if (applyBtn) {
      await act(async () => {
        applyBtn.click();
      });
      expect(handleSelectPersona).toHaveBeenCalled();
      expect(handleClose).toHaveBeenCalled();
    }
  });
});
