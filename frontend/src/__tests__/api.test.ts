import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import {
  deleteSessionApi,
  fetchQuotaConfigApi,
  fetchSessionDetailApi,
  fetchSessionsApi,
  fetchUsageDashboardApi,
  memoryDeleteApi,
  memoryListApi,
  memoryUpdateApi,
  ragDeleteApi,
  ragListApi,
  ragReingestApi,
  ragStatusApi,
  ragUploadApi,
  renameSessionApi,
  searchChatHistoryApi,
  type UsageDashboardData,
  updateQuotaConfigApi,
} from "../lib/api";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock();
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe("API Layer Unit Tests - lib/api.ts", () => {
  describe("Sessions API", () => {
    it("fetchSessionsApi returns sessions on 200 OK", async () => {
      const mockSessions = [{ id: "s-1", title: "Session 1", updatedAt: 1000 }];
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(mockSessions), { status: 200 }),
      );

      const res = await fetchSessionsApi();
      expect(res).toEqual(mockSessions);
      expect(mockFetch).toHaveBeenCalledWith("/api/chat/sessions");
    });

    it("fetchSessionsApi returns null on non-200 or network error", async () => {
      mockFetch.mockResolvedValueOnce(new Response("Error", { status: 500 }));
      expect(await fetchSessionsApi()).toBeNull();

      mockFetch.mockRejectedValueOnce(new Error("Network fail"));
      expect(await fetchSessionsApi()).toBeNull();
    });

    it("fetchSessionDetailApi maps attachments and filters non-user/assistant roles", async () => {
      const backendResponse = {
        id: "s-1",
        title: "Session 1",
        updatedAt: 1000,
        messages: [
          { id: "m-sys", role: "system", content: "You are helpful assistant" },
          {
            id: "m-1",
            role: "user",
            content: "Hello",
            media: [
              { mimeType: "image/png", data: "data:image/png;base64,ABC" },
            ],
          },
          {
            id: "m-2",
            role: "assistant",
            content: "Hi",
            media: [{ mimeType: "image/jpeg", data: "XYZ" }],
          },
        ],
      };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(backendResponse), { status: 200 }),
      );

      const detail = await fetchSessionDetailApi("s-1");
      expect(detail).not.toBeNull();
      expect(detail?.messages.length).toBe(2);

      // User message attachment with existing data: prefix
      expect(detail?.messages[0].attachments?.[0].url).toBe(
        "data:image/png;base64,ABC",
      );

      // Assistant message attachment formatted with base64 prefix
      expect(detail?.messages[1].attachments?.[0].url).toBe(
        "data:image/jpeg;base64,XYZ",
      );
    });

    it("fetchSessionDetailApi returns null on 404 or network exception", async () => {
      mockFetch.mockResolvedValueOnce(
        new Response("Not Found", { status: 404 }),
      );
      expect(await fetchSessionDetailApi("invalid-id")).toBeNull();

      mockFetch.mockRejectedValueOnce(new Error("Connection reset"));
      expect(await fetchSessionDetailApi("invalid-id")).toBeNull();
    });

    it("renameSessionApi sends PUT and returns boolean", async () => {
      mockFetch.mockResolvedValueOnce(new Response(null, { status: 200 }));
      expect(await renameSessionApi("s-1", "New Title")).toBeTrue();
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/chat/sessions/s-1/title",
        expect.objectContaining({
          method: "PUT",
          body: JSON.stringify({ title: "New Title" }),
        }),
      );

      mockFetch.mockResolvedValueOnce(new Response(null, { status: 400 }));
      expect(await renameSessionApi("s-1", "New Title")).toBeFalse();

      mockFetch.mockRejectedValueOnce(new Error("Timeout"));
      expect(await renameSessionApi("s-1", "New Title")).toBeFalse();
    });

    it("deleteSessionApi sends DELETE and returns boolean", async () => {
      mockFetch.mockResolvedValueOnce(new Response(null, { status: 200 }));
      expect(await deleteSessionApi("s-1")).toBeTrue();

      mockFetch.mockResolvedValueOnce(new Response(null, { status: 500 }));
      expect(await deleteSessionApi("s-1")).toBeFalse();
    });
  });

  describe("Search API", () => {
    it("searchChatHistoryApi validates empty query and handles success & errors", async () => {
      // Empty string validation
      expect(await searchChatHistoryApi("   ")).toBeNull();

      const searchRes = { query: "test", results: [] };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(searchRes), { status: 200 }),
      );

      const res = await searchChatHistoryApi("test", 10);
      expect(res).toEqual(searchRes);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/chat/search?q=test&limit=10",
        {
          signal: undefined,
        },
      );

      // AbortError handling
      const abortErr = new Error("Aborted");
      abortErr.name = "AbortError";
      mockFetch.mockRejectedValueOnce(abortErr);
      expect(await searchChatHistoryApi("test")).toBeNull();
    });
  });

  describe("RAG Document & Status APIs", () => {
    it("ragListApi formats query parameters correctly", async () => {
      const mockList = { items: [], total: 0, sourceTypeCounts: {} };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(mockList), { status: 200 }),
      );

      const res = await ragListApi("user-1", "FILE", 20);
      expect(res).toEqual(mockList);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/rag/documents?userId=user-1&sourceType=FILE&limit=20",
        { signal: undefined },
      );
    });

    it("ragUploadApi handles HTTP non-200 with structured error object", async () => {
      const errorResponse = { error: "Parse error", detail: "PDF encrypted" };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(errorResponse), { status: 400 }),
      );

      const result = await ragUploadApi({
        sourceType: "FILE",
        fileName: "secret.pdf",
      });

      expect(result).toEqual({
        success: false,
        sourceType: "FILE",
        source: "",
        ingested: 0,
        skipped: 0,
        error: "Parse error",
        detail: "PDF encrypted",
      });
    });

    it("ragReingestApi handles success and network exception", async () => {
      const successRes = {
        success: true,
        sourceType: "URL",
        source: "https://example.com",
        ingested: 5,
        skipped: 0,
        removed: 5,
      };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(successRes), { status: 200 }),
      );

      const res = await ragReingestApi({
        sourceType: "URL",
        targetUrl: "https://example.com",
      });
      expect(res).toEqual(successRes);

      mockFetch.mockRejectedValueOnce(new Error("Network drop"));
      expect(
        await ragReingestApi({
          sourceType: "URL",
          targetUrl: "https://example.com",
        }),
      ).toBeNull();
    });

    it("ragDeleteApi deletes document by source and maps errors", async () => {
      const deleteSuccess = {
        success: true,
        source: "doc.txt",
        userId: "user-1",
        removed: 3,
      };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(deleteSuccess), { status: 200 }),
      );

      const res = await ragDeleteApi("doc.txt", "user-1");
      expect(res).toEqual(deleteSuccess);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/rag/documents?source=doc.txt&userId=user-1",
        { method: "DELETE" },
      );

      // Non-200 response
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify({ error: "Access denied" }), {
          status: 403,
        }),
      );
      const errRes = await ragDeleteApi("doc.txt", "user-1");
      expect(errRes?.success).toBeFalse();
      expect(errRes?.error).toBe("Access denied");
    });

    it("ragStatusApi returns vector status or handles AbortError", async () => {
      const statusData = {
        enabled: true,
        available: true,
        collectionName: "ai_kb",
        documentCount: 10,
        vectorCount: 100,
      };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(statusData), { status: 200 }),
      );

      expect(await ragStatusApi()).toEqual(statusData);

      const abortErr = new Error("Aborted");
      abortErr.name = "AbortError";
      mockFetch.mockRejectedValueOnce(abortErr);
      expect(await ragStatusApi()).toBeNull();
    });
  });

  describe("Memory APIs", () => {
    it("memoryListApi constructs keyword and pagination parameters", async () => {
      const mockMemories = { items: [], total: 0 };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(mockMemories), { status: 200 }),
      );

      const res = await memoryListApi(" preference ", 10, 20);
      expect(res).toEqual(mockMemories);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/memory?keyword=preference&limit=10&offset=20",
        { signal: undefined },
      );
    });

    it("memoryUpdateApi updates item and memoryDeleteApi deletes item", async () => {
      const updatedItem = {
        id: "mem-1",
        content: "Prefers TypeScript",
        category: "coding",
        confidence: 0.9,
        updatedAt: "2026-08-12",
      };
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(updatedItem), { status: 200 }),
      );

      const res = await memoryUpdateApi(
        "mem-1",
        "Prefers TypeScript",
        "coding",
      );
      expect(res).toEqual(updatedItem);

      mockFetch.mockResolvedValueOnce(new Response(null, { status: 200 }));
      expect(await memoryDeleteApi("mem-1")).toBeTrue();

      mockFetch.mockResolvedValueOnce(new Response(null, { status: 500 }));
      expect(await memoryDeleteApi("mem-1")).toBeFalse();
    });
  });

  describe("Usage & Quota Dashboard APIs", () => {
    it("fetchUsageDashboardApi supports optional month filter", async () => {
      const mockDashboard = {
        monthKey: "2026-08",
      } as unknown as UsageDashboardData;
      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(mockDashboard), { status: 200 }),
      );

      const res = await fetchUsageDashboardApi("2026-08");
      expect(res).toEqual(mockDashboard);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/usage/dashboard?month=2026-08",
        { signal: undefined },
      );
    });

    it("fetchQuotaConfigApi and updateQuotaConfigApi handle 200 and errors", async () => {
      const config = {
        monthlyTokenQuota: 1000000,
        alertThresholdPercent: 80,
        monthlyCostQuotaRmb: 100,
      };

      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(config), { status: 200 }),
      );
      expect(await fetchQuotaConfigApi()).toEqual(config);

      mockFetch.mockResolvedValueOnce(
        new Response(JSON.stringify(config), { status: 200 }),
      );
      expect(await updateQuotaConfigApi(config)).toEqual(config);

      mockFetch.mockResolvedValueOnce(new Response(null, { status: 500 }));
      expect(await updateQuotaConfigApi(config)).toBeNull();
    });
  });
});
