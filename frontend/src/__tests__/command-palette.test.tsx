import { describe, expect, it, mock } from "bun:test";
import {
  type CommandContext,
  STATIC_COMMANDS,
} from "../components/command-registry";

describe("CommandPalette and registry tests", () => {
  it("contains expected essential static commands", () => {
    const ids = STATIC_COMMANDS.map((c) => c.id);
    expect(ids).toContain("new-chat");
    expect(ids).toContain("nav-knowledge");
    expect(ids).toContain("nav-workflows");
    expect(ids).toContain("prompt-templates");
    expect(ids).toContain("conversation-insights");
    expect(ids).toContain("setting-api-keys");
    expect(ids).toContain("setting-webhooks");
    expect(ids).toContain("setting-mcp");
    expect(ids).toContain("theme-toggle");
  });

  it("new-chat command triggers router navigation to / and closes palette", () => {
    const newChatCmd = STATIC_COMMANDS.find((c) => c.id === "new-chat");
    expect(newChatCmd).toBeDefined();

    const pushMock = mock(() => {});
    const closeMock = mock(() => {});

    const ctx = {
      router: { push: pushMock } as unknown as CommandContext["router"],
      close: closeMock,
    };

    newChatCmd?.run(ctx);

    expect(pushMock).toHaveBeenCalledWith("/");
    expect(closeMock).toHaveBeenCalledTimes(1);
  });

  it("theme-toggle toggles current dark/light theme", () => {
    const themeToggleCmd = STATIC_COMMANDS.find((c) => c.id === "theme-toggle");
    expect(themeToggleCmd).toBeDefined();

    const setThemeMock = mock(() => {});
    const closeMock = mock(() => {});

    const ctx = {
      router: { push: () => {} } as unknown as CommandContext["router"],
      setTheme: setThemeMock,
      currentTheme: "dark",
      close: closeMock,
    };

    themeToggleCmd?.run(ctx);

    expect(setThemeMock).toHaveBeenCalledWith("light");
    expect(closeMock).toHaveBeenCalledTimes(1);
  });
});
