import { describe, expect, it, mock } from "bun:test";
import { listBookmarks, toggleBookmark, togglePin } from "../lib/bookmark-api";

describe("bookmark-api client tests", () => {
  it("toggleBookmark sends POST request and returns updated status", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            pinned: false,
            bookmarked: true,
            tags: ["核心要点"],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await toggleBookmark("msg_1", {
      sessionId: "sess_1",
      role: "assistant",
      content: "测试消息内容",
      tags: ["核心要点"],
    });

    expect(res.bookmarked).toBe(true);
    expect(res.tags).toContain("核心要点");

    globalThis.fetch = originalFetch;
  });

  it("togglePin sends pin request", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            pinned: true,
            bookmarked: false,
            tags: [],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await togglePin("msg_2", {
      sessionId: "sess_1",
      role: "user",
      content: "请帮我分析",
    });

    expect(res.pinned).toBe(true);

    globalThis.fetch = originalFetch;
  });

  it("listBookmarks returns all bookmarked messages", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify([
            {
              id: "bm_1",
              userId: "user-1",
              sessionId: "sess_1",
              messageId: "msg_1",
              role: "assistant",
              content: "已收藏内容",
              tags: ["tag1"],
              pinned: false,
              bookmarked: true,
              createdAt: 1000,
            },
          ]),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const list = await listBookmarks();
    expect(list).toHaveLength(1);
    expect(list[0].content).toBe("已收藏内容");

    globalThis.fetch = originalFetch;
  });
});
