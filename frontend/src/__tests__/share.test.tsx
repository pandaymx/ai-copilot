import { describe, expect, it, mock } from "bun:test";
import { checkShare, createSessionShare, resolveShare } from "../lib/share-api";

describe("share-api client tests", () => {
  it("createSessionShare sends POST and returns ShareMeta", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            token: "s_abc123",
            sessionId: "sess-1",
            userId: "user-1",
            title: "架构方案讨论",
            expireAt: null,
            hasPassword: true,
            viewCount: 0,
            createdAt: 1000,
          }),
          { status: 201, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await createSessionShare("sess-1", {
      title: "架构方案讨论",
      messagesJson: "[]",
      password: "pass",
    });

    expect(res.token).toBe("s_abc123");
    expect(res.hasPassword).toBe(true);
    expect(res.title).toBe("架构方案讨论");

    globalThis.fetch = originalFetch;
  });

  it("checkShare fetches public share requirement", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            token: "s_abc123",
            requiresPassword: true,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await checkShare("s_abc123");
    expect(res.requiresPassword).toBe(true);

    globalThis.fetch = originalFetch;
  });

  it("resolveShare resolves snapshot view", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            token: "s_abc123",
            title: "架构方案讨论",
            messagesJson: '[{"id":"1","role":"user","content":"hello"}]',
            createdAt: 1000,
            viewCount: 1,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await resolveShare("s_abc123", "pass");
    expect(res.title).toBe("架构方案讨论");
    expect(res.viewCount).toBe(1);

    globalThis.fetch = originalFetch;
  });
});
