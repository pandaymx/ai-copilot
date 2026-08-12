import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { fetchTitle } from "../lib/title";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock();
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe("Title Generator API - lib/title.ts", () => {
  it("should return generated title when API responds with 200 OK", async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "  Refactoring Guide  " }), {
        status: 200,
      }),
    );

    const title = await fetchTitle({
      message: "How to refactor code?",
      answer: "Start with unit tests.",
      provider: "openai",
      model: "gpt-4o",
      conversationId: "conv-123",
    });

    expect(title).toBe("Refactoring Guide");
    expect(mockFetch).toHaveBeenCalledWith(
      "/api/chat/title",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: "How to refactor code?",
          answer: "Start with unit tests.",
          provider: "openai",
          model: "gpt-4o",
          conversationId: "conv-123",
        }),
      }),
    );
  });

  it("should default null/undefined parameters when optional fields are omitted", async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "Simple Title" }), { status: 200 }),
    );

    const title = await fetchTitle({
      message: "Hello",
      answer: "Hi there",
    });

    expect(title).toBe("Simple Title");
    expect(mockFetch).toHaveBeenCalledWith(
      "/api/chat/title",
      expect.objectContaining({
        body: JSON.stringify({
          message: "Hello",
          answer: "Hi there",
          provider: null,
          model: null,
          conversationId: null,
        }),
      }),
    );
  });

  it("should return null when API returns non-200 HTTP status", async () => {
    mockFetch.mockResolvedValueOnce(
      new Response("Internal Error", { status: 500 }),
    );
    const title = await fetchTitle({ message: "Test", answer: "Answer" });
    expect(title).toBeNull();
  });

  it("should return null when response title is missing or whitespace only", async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "   " }), { status: 200 }),
    );
    expect(await fetchTitle({ message: "Test", answer: "Answer" })).toBeNull();

    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({}), { status: 200 }),
    );
    expect(await fetchTitle({ message: "Test", answer: "Answer" })).toBeNull();
  });

  it("should return null when network exception is thrown", async () => {
    mockFetch.mockRejectedValueOnce(new Error("Network connection lost"));
    const title = await fetchTitle({ message: "Test", answer: "Answer" });
    expect(title).toBeNull();
  });
});
