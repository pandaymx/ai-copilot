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
} from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { type ChatSession, Sidebar } from "../components/chat/sidebar";

const FIXED_NOW = 1700000000000; // Fixed timestamp for deterministic relative time tests
const realDateNow = Date.now;

beforeEach(() => {
  Date.now = () => FIXED_NOW;
});

afterEach(() => {
  Date.now = realDateNow;
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

describe("Sidebar Component Tests - components/chat/sidebar.tsx", () => {
  const sampleSessions: ChatSession[] = [
    {
      id: "s-today",
      title: "Today Discussion",
      updatedAt: FIXED_NOW - 1000 * 60 * 5, // 5m ago
    },
    {
      id: "s-yesterday",
      title: "Yesterday Design",
      updatedAt: FIXED_NOW - 1000 * 60 * 60 * 25, // 25h ago
    },
    {
      id: "s-week",
      title: "Weekly Planning",
      updatedAt: FIXED_NOW - 1000 * 60 * 60 * 24 * 3, // 3d ago
    },
  ];

  it("should render brand header, action links, and session list", () => {
    const { container, unmount } = renderComponent(
      <Sidebar
        sessions={sampleSessions}
        activeId="s-today"
        collapsed={false}
        onSelect={() => {}}
        onNew={() => {}}
        onDelete={() => {}}
        onToggleCollapsed={() => {}}
      />,
    );

    expect(container.textContent).toContain("AI Copilot Pro");
    expect(container.textContent).toContain("开启新会话");
    expect(container.textContent).toContain("月度 Token 配额");
    expect(container.textContent).toContain("知识库管理");
    expect(container.textContent).toContain("长期记忆");

    expect(container.textContent).toContain("Today Discussion");
    expect(container.textContent).toContain("Yesterday Design");
    expect(container.textContent).toContain("Weekly Planning");

    unmount();
  });

  it("should trigger onNew callback when clicking New Session button", () => {
    let newClicked = false;
    const { container, unmount } = renderComponent(
      <Sidebar
        sessions={sampleSessions}
        activeId={null}
        collapsed={false}
        onSelect={() => {}}
        onNew={() => {
          newClicked = true;
        }}
        onDelete={() => {}}
        onToggleCollapsed={() => {}}
      />,
    );

    const newBtn = Array.from(container.querySelectorAll("button")).find((b) =>
      b.textContent?.includes("开启新会话"),
    );
    expect(newBtn).toBeDefined();

    act(() => {
      newBtn?.click();
    });

    expect(newClicked).toBeTrue();
    unmount();
  });

  it("should trigger onSelect callback when clicking a session item", () => {
    let selectedId = "";
    const { container, unmount } = renderComponent(
      <Sidebar
        sessions={sampleSessions}
        activeId="s-today"
        collapsed={false}
        onSelect={(id) => {
          selectedId = id;
        }}
        onNew={() => {}}
        onDelete={() => {}}
        onToggleCollapsed={() => {}}
      />,
    );

    const yesterdaySessionBtn = Array.from(
      container.querySelectorAll("button"),
    ).find((b) => b.textContent?.includes("Yesterday Design"));

    expect(yesterdaySessionBtn).toBeDefined();

    act(() => {
      yesterdaySessionBtn?.click();
    });

    expect(selectedId).toBe("s-yesterday");
    unmount();
  });

  it("should support inline renaming mode", () => {
    let renamedTitle = "";
    const { container, unmount } = renderComponent(
      <Sidebar
        sessions={sampleSessions}
        activeId="s-today"
        collapsed={false}
        onSelect={() => {}}
        onNew={() => {}}
        onDelete={() => {}}
        onRename={(_id, newTitle) => {
          renamedTitle = newTitle;
        }}
        onToggleCollapsed={() => {}}
      />,
    );

    const editBtn = container.querySelector(
      'button[aria-label="重命名"]',
    ) as HTMLButtonElement;
    expect(editBtn).not.toBeNull();

    act(() => {
      editBtn.click();
    });

    const editInput = container.querySelector(
      'input[type="text"]',
    ) as HTMLInputElement;
    expect(editInput).not.toBeNull();

    act(() => {
      const reactPropsKey = Object.keys(editInput).find((k) =>
        k.startsWith("__reactProps$"),
      );
      if (reactPropsKey) {
        // biome-ignore lint/suspicious/noExplicitAny: access React internal props in test
        (editInput as any)[reactPropsKey].onChange({
          target: { value: "Renamed Today Session" },
        });
      }
    });

    const submitBtn = container.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(submitBtn).not.toBeNull();

    act(() => {
      submitBtn.click();
    });

    expect(renamedTitle).toBe("Renamed Today Session");
    unmount();
  });

  it("should trigger onDelete callback when clicking trash button", () => {
    let deletedId = "";
    const { container, unmount } = renderComponent(
      <Sidebar
        sessions={sampleSessions}
        activeId="s-today"
        collapsed={false}
        onSelect={() => {}}
        onNew={() => {}}
        onDelete={(id) => {
          deletedId = id;
        }}
        onToggleCollapsed={() => {}}
      />,
    );

    const deleteBtn = container.querySelector(
      'button[aria-label="删除会话"]',
    ) as HTMLButtonElement;
    expect(deleteBtn).not.toBeNull();

    act(() => {
      deleteBtn.click();
    });

    expect(deletedId).toBe("s-today");
    unmount();
  });

  it("should display offline fallback warning banner when isOfflineFallback is true", () => {
    const { container, unmount } = renderComponent(
      <Sidebar
        sessions={sampleSessions}
        activeId={null}
        collapsed={false}
        isOfflineFallback={true}
        onSelect={() => {}}
        onNew={() => {}}
        onDelete={() => {}}
        onToggleCollapsed={() => {}}
      />,
    );

    expect(container.textContent).toContain("云端同步失败，使用本地缓存");
    unmount();
  });
});
