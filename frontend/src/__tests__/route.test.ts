import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { NextRequest } from "next/server";
import { DELETE, GET, OPTIONS, POST, PUT } from "../app/api/[...path]/route";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;
const originalTrustXUserId = process.env.PROXY_TRUST_X_USER_ID;

beforeEach(() => {
  mockFetch = mock();
  globalThis.fetch = mockFetch as unknown as typeof fetch;
  // 开启「信任客户端 X-User-Id」模式，覆盖代理透传分支（对应生产经 Caddy 网关注入的场景）。
  process.env.PROXY_TRUST_X_USER_ID = "true";
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalTrustXUserId === undefined) {
    delete process.env.PROXY_TRUST_X_USER_ID;
  } else {
    process.env.PROXY_TRUST_X_USER_ID = originalTrustXUserId;
  }
});

describe("API Proxy Router Unit Tests - app/api/[...path]/route.ts", () => {
  describe("Forward Headers Whitelist & Trusted Identity", () => {
    it("should filter out unallowed client headers and inject X-User-Id", async () => {
      mockFetch.mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify({ ok: true }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        ),
      );

      const req = new NextRequest("http://localhost:3000/api/chat/sessions", {
        method: "GET",
        headers: {
          "x-forwarded-for": "198.51.100.1",
          authorization: "Bearer secret-token",
          "content-type": "application/json",
          "x-user-id": "alice-123",
          "x-custom-malicious-header": "attack-vector",
          accept: "application/json",
        },
      });

      const params = Promise.resolve({ path: ["chat", "sessions"] });
      const res = await GET(req, { params });

      expect(res.status).toBe(200);
      expect(mockFetch).toHaveBeenCalled();

      const [targetUrl, options] = mockFetch.mock.calls[0];
      expect(targetUrl).toBe("http://localhost:8084/api/chat/sessions");

      const forwardedHeaders = options.headers as Headers;
      expect(forwardedHeaders.get("authorization")).toBe("Bearer secret-token");
      expect(forwardedHeaders.get("content-type")).toBe("application/json");
      expect(forwardedHeaders.get("accept")).toBe("application/json");
      expect(forwardedHeaders.get("X-User-Id")).toBe("alice-123");
      expect(forwardedHeaders.get("x-custom-malicious-header")).toBeNull();
    });
  });

  describe("IP Rate Limiting (120 req/min)", () => {
    it("should enforce sliding window rate limit per client IP", async () => {
      const clientIp = "198.51.100.50";
      mockFetch.mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify({ success: true }), { status: 200 }),
        ),
      );

      const params = Promise.resolve({ path: ["test", "rate-limit"] });

      // Send 120 requests from clientIp
      for (let i = 0; i < 120; i++) {
        const req = new NextRequest(
          "http://localhost:3000/api/test/rate-limit",
          {
            headers: { "x-forwarded-for": clientIp },
          },
        );
        const res = await GET(req, { params });
        expect(res.status).toBe(200);
      }

      // 121st request from same IP should be blocked with 429
      const blockedReq = new NextRequest(
        "http://localhost:3000/api/test/rate-limit",
        {
          headers: { "x-forwarded-for": clientIp },
        },
      );
      const blockedRes = await GET(blockedReq, { params });
      expect(blockedRes.status).toBe(429);

      const body = await blockedRes.json();
      expect(body.error).toBeTrue();
      expect(body.message).toContain("429 Rate Limit Exceeded");
      expect(blockedRes.headers.get("Retry-After")).toBe("60");

      // Request from a different IP should be allowed
      const anotherIpReq = new NextRequest(
        "http://localhost:3000/api/test/rate-limit",
        {
          headers: { "x-forwarded-for": "198.51.100.99" },
        },
      );
      const anotherRes = await GET(anotherIpReq, { params });
      expect(anotherRes.status).toBe(200);
    });
  });

  describe("SSE TransformStream Handling", () => {
    it("should pipe backend stream with proper SSE headers when path or content-type matches", async () => {
      mockFetch.mockImplementation(() => {
        const sseStream = new ReadableStream({
          start(controller) {
            controller.enqueue(new TextEncoder().encode("data: Hello SSE\n\n"));
            controller.close();
          },
        });
        return Promise.resolve(
          new Response(sseStream, {
            status: 200,
            headers: { "content-type": "text/event-stream" },
          }),
        );
      });

      const req = new NextRequest("http://localhost:3000/api/chat/stream", {
        method: "POST",
        headers: {
          "x-forwarded-for": "198.51.100.200",
          "content-type": "application/json",
        },
        body: JSON.stringify({ message: "hello" }),
      });
      const params = Promise.resolve({ path: ["chat", "stream"] });

      let res: Response;
      try {
        res = await POST(req, { params });
      } catch (err) {
        console.error("POST threw error:", err);
        throw err;
      }
      try {
        expect(res.status).toBe(200);
        expect(res.headers.get("Content-Type")).toBe(
          "text/event-stream; charset=utf-8",
        );
        expect(res.headers.get("Cache-Control")).toBe("no-cache, no-transform");
        expect(res.headers.get("X-Accel-Buffering")).toBe("no");

        await new Promise((r) => setTimeout(r, 20));
        const text = await res.text();
        expect(text).toBe("data: Hello SSE\n\n");
      } catch (err) {
        console.error(
          "SSE Test Assertion Fail. status:",
          res.status,
          "headers:",
          Object.fromEntries(res.headers.entries()),
        );
        throw err;
      }
    });
  });

  describe("OPTIONS Preflight & Fallback CORS", () => {
    it("should proxy OPTIONS and fallback to 204 CORS headers on fetch error", async () => {
      // 1. Success case proxying OPTIONS
      mockFetch.mockImplementationOnce(() =>
        Promise.resolve(
          new Response(null, {
            status: 200,
            headers: { "Access-Control-Allow-Origin": "*" },
          }),
        ),
      );

      const req = new NextRequest("http://localhost:3000/api/chat/sessions", {
        method: "OPTIONS",
        headers: {
          "x-forwarded-for": "198.51.100.2",
          origin: "http://localhost:3000",
        },
      });
      const params = Promise.resolve({ path: ["chat", "sessions"] });

      let res = await OPTIONS(req, { params });
      expect(res.status).toBe(200);
      expect(res.headers.get("Access-Control-Max-Age")).toBe("86400");

      // 2. Network error case fallback CORS
      mockFetch.mockImplementationOnce(() =>
        Promise.reject(new Error("Backend unreachable")),
      );
      res = await OPTIONS(req, { params });
      expect(res.status).toBe(204);
      expect(res.headers.get("Access-Control-Allow-Methods")).toContain(
        "GET, POST",
      );
    });
  });

  describe("PUT, DELETE, and Error Handling", () => {
    it("should proxy PUT and DELETE requests correctly", async () => {
      mockFetch.mockImplementationOnce(() =>
        Promise.resolve(
          new Response(JSON.stringify({ updated: true }), { status: 200 }),
        ),
      );

      const putReq = new NextRequest("http://localhost:3000/api/memory/mem-1", {
        method: "PUT",
        headers: { "x-forwarded-for": "198.51.100.3" },
        body: JSON.stringify({ content: "updated" }),
      });
      const putParams = Promise.resolve({ path: ["memory", "mem-1"] });

      const putRes = await PUT(putReq, { params: putParams });
      expect(putRes.status).toBe(200);

      mockFetch.mockImplementationOnce(() =>
        Promise.resolve(new Response(null, { status: 204 })),
      );

      const deleteReq = new NextRequest(
        "http://localhost:3000/api/memory/mem-1",
        {
          method: "DELETE",
          headers: { "x-forwarded-for": "198.51.100.4" },
        },
      );
      const deleteParams = Promise.resolve({ path: ["memory", "mem-1"] });

      const deleteRes = await DELETE(deleteReq, { params: deleteParams });
      expect(deleteRes.status).toBe(204);
    });

    it("should return HTTP 500 JSON when backend fetch throws an error", async () => {
      mockFetch.mockImplementationOnce(() =>
        Promise.reject(new Error("Connection refused")),
      );

      const req = new NextRequest("http://localhost:3000/api/chat/sessions", {
        headers: { "x-forwarded-for": "198.51.100.5" },
      });
      const params = Promise.resolve({ path: ["chat", "sessions"] });

      const res = await GET(req, { params });
      expect(res.status).toBe(500);

      const body = await res.json();
      expect(body.error).toBeTrue();
      expect(body.message).toBe("Connection refused");
    });
  });
});
