import { describe, expect, it, mock } from "bun:test";
import {
  deleteContentHistory,
  generateContent,
  listContentHistory,
  listContentTemplates,
} from "../lib/content-template-api";

describe("content-template-api client tests", () => {
  it("listContentTemplates returns template array", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify([
            {
              id: "weekly-report",
              name: "工作周报",
              description: "结构化整理本周工作",
              category: "职场效能",
              icon: "Calendar",
              fields: [],
            },
          ]),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const list = await listContentTemplates();
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe("weekly-report");

    globalThis.fetch = originalFetch;
  });

  it("generateContent sends POST request and returns generated response", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            id: "cgen_1",
            templateId: "weekly-report",
            title: "周报",
            markdownContent: "# 周报\n\n已完成任务",
            structuredSections: {},
            createdAt: 1000,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await generateContent({
      templateId: "weekly-report",
      title: "周报",
      inputs: { completed: "已完成任务" },
    });

    expect(res.id).toBe("cgen_1");
    expect(res.markdownContent).toContain("已完成任务");

    globalThis.fetch = originalFetch;
  });

  it("listContentHistory and deleteContentHistory work as expected", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify([
            {
              id: "cgen_1",
              userId: "user-1",
              templateId: "weekly-report",
              title: "周报",
              markdownContent: "# 内容",
              createdAt: 1000,
            },
          ]),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const history = await listContentHistory();
    expect(history).toHaveLength(1);

    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(JSON.stringify({ success: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    ) as unknown as typeof fetch;

    await deleteContentHistory("cgen_1");

    globalThis.fetch = originalFetch;
  });
});
