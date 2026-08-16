import { describe, expect, it, mock } from "bun:test";
import { createWebhook, listWebhooks, testWebhook } from "../lib/webhook-api";

describe("webhook-api client tests", () => {
  it("listWebhooks fetches all user subscriptions", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify([
            {
              id: "wh_1",
              userId: "user-1",
              name: "企业微信群机器人",
              url: "https://qyapi.weixin.qq.com/cgi-bin/webhook/send",
              eventType: "chat.completed",
              secret: "whsec_123",
              enabled: true,
              lastStatus: "SUCCESS",
              lastDeliveredAt: 1000,
              createdAt: 500,
            },
          ]),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const list = await listWebhooks();
    expect(list).toHaveLength(1);
    expect(list[0].name).toBe("企业微信群机器人");
    expect(list[0].eventType).toBe("chat.completed");

    globalThis.fetch = originalFetch;
  });

  it("createWebhook posts subscription and returns created entity", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            id: "wh_2",
            userId: "user-1",
            name: "飞书通知",
            url: "https://open.feishu.cn/hook",
            eventType: "*",
            secret: "whsec_456",
            enabled: true,
            createdAt: 1000,
          }),
          { status: 201, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await createWebhook({
      name: "飞书通知",
      url: "https://open.feishu.cn/hook",
      eventType: "*",
    });

    expect(res.id).toBe("wh_2");
    expect(res.name).toBe("飞书通知");

    globalThis.fetch = originalFetch;
  });

  it("testWebhook sends test ping", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            success: true,
            statusCode: 200,
            message: "OK",
            durationMs: 45,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const res = await testWebhook("wh_1");
    expect(res.success).toBe(true);
    expect(res.statusCode).toBe(200);

    globalThis.fetch = originalFetch;
  });
});
