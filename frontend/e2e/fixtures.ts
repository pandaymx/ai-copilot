import { test as base, expect, type Page } from "@playwright/test";

import {
  buildErrorFrames,
  buildInternalErrorBody,
  buildSearchResponse,
  buildSessionDetail,
  buildSessions,
  buildStreamFrames,
  type MockMessage,
  type MockSession,
} from "./mocks/api";

/**
 * 选择器常量：复用组件既有可访问性属性，避免依赖易变的 class 名。
 */
export const selectors = {
  textarea:
    'textarea[placeholder="给 Spring AI 发送指令、问题或拖入/粘贴图片..."]',
  send: 'button[aria-label="发送"]',
  stop: 'button[aria-label="停止生成"]',
  newChat: 'button:has-text("开启新会话")',
  deleteSession: 'button[aria-label="删除会话"]',
  renameSession: 'button[aria-label="重命名"]',
  modelSelector: 'button[aria-label="选择 AI 模型"]',
  themeToggle: 'button[aria-label="切换主题"]',
  exportBtn: 'button[aria-label="导出对话"]',
  searchBtn: 'button[aria-label="搜索历史消息 (⌘K)"]',
  offlineBanner: "text=云端同步失败，使用本地缓存",
  errorCard: "text=服务连接受阻",
};

/** 默认 Mock 的会话与消息。 */
function defaultSession(): { session: MockSession; messages: MockMessage[] } {
  const id = "sess-mock-1";
  return {
    session: { id, title: "Mock 会话", updatedAt: Date.now() },
    messages: [
      { id: "m1", role: "user", content: "你好" },
      { id: "m2", role: "assistant", content: "你好，我是 Spring AI Copilot。" },
    ],
  };
}

/**
 * 在 Mock 模式下拦截所有 /api/chat 与 /api/models 请求。
 * 零后端依赖：sessions 返回空数组（或注入的会话），stream 返回 SSE 分帧。
 */
async function mockApiRoutes(
  page: Page,
  opts: {
    initialSessions?: MockSession[];
    streamText?: string;
    streamError?: boolean;
    streamStatus?: number;
    searchResults?: Parameters<typeof buildSearchResponse>[1];
  } = {},
) {
  const {
    initialSessions = [],
    streamText = "这是一段由 Playwright Mock 生成的流式回复内容。",
    streamError = false,
    streamStatus = 200,
    searchResults = [],
  } = opts;

  await page.route("**/api/chat/sessions", async (route) => {
    const method = route.request().method();
    if (method === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(buildSessions(initialSessions)),
      });
    } else {
      // POST 新建会话：返回新会话
      const created: MockSession = {
        id: `sess-${Date.now()}`,
        title: "新会话",
        updatedAt: Date.now(),
        isDefaultTitle: true,
      };
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(created),
      });
    }
  });

  // GET /api/chat/sessions/:id → 详情
  await page.route(/.*\/api\/chat\/sessions\/[^/]+$/, async (route) => {
    const { session, messages } = defaultSession();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(buildSessionDetail(session, messages)),
    });
  });

  // PUT /api/chat/sessions/:id/title
  await page.route(
    /.*\/api\/chat\/sessions\/[^/]+\/title$/,
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(true),
      });
    },
  );

  // DELETE /api/chat/sessions/:id
  await page.route(
    /.*\/api\/chat\/sessions\/[^/]+$/,
    async (route) => {
      if (route.request().method() === "DELETE") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(true),
        });
      }
    },
  );

  // GET /api/chat/search
  await page.route(/.*\/api\/chat\/search.*/, async (route) => {
    const url = new URL(route.request().url());
    const q = url.searchParams.get("q") ?? "";
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(buildSearchResponse(q, searchResults)),
    });
  });

  // POST /api/chat/stream（SSE）
  await page.route(/.*\/api\/chat\/stream.*/, async (route) => {
    if (streamStatus !== 200) {
      await route.fulfill({
        status: streamStatus,
        contentType: "application/json",
        body: buildInternalErrorBody("Mock 500 Internal Error"),
      });
      return;
    }
    const body = streamError
      ? buildErrorFrames("Mock SSE 错误事件")
      : buildStreamFrames(streamText);
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream; charset=utf-8",
      headers: {
        "Cache-Control": "no-cache",
        Connection: "keep-alive",
      },
      body,
    });
  });

  // /api/models 与 /api/models/health：模型选择器会读取，返回空即可（使用默认目录）
  await page.route(/.*\/api\/models.*/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ providers: [], defaultProvider: "" }),
    });
  });
}

interface Fixtures {
  /** 进入首页并挂载 Mock 路由；默认清空 localStorage 保证隔离。 */
  mockChat: () => Promise<Page>;
}

export const test = base.extend<Fixtures>({
  mockChat: async ({ page }, use) => {
    // 每个测试前清空本地存储（离线模式基于 localStorage）
    await page.goto("/");
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await use(page);
  },
});

export { expect };

/** 通用辅助函数集合。 */
export const helpers = {
  /** 挂载 Mock 路由（可在 mockChat 之后调用，覆盖默认行为）。 */
  mockApiRoutes,

  /** 发送一条消息并等待流式回复完成（用户消息出现在界面 + 停止按钮消失）。 */
  async sendMessage(page: Page, text: string) {
    const textarea = page.locator(selectors.textarea);
    await textarea.fill(text);
    await page.locator(selectors.send).click();
  },

  /** 等待流式生成结束：停止按钮消失，表示 [DONE] 已消费。 */
  async waitForStreamDone(page: Page) {
    await expect(page.locator(selectors.stop)).toHaveCount(0, {
      timeout: 15_000,
    });
  },

  /** 通过侧边栏新建会话。 */
  async newSession(page: Page) {
    await page.locator(selectors.newChat).click();
  },
};

/** 是否处于真实后端 E2E 模式。 */
export const isRealE2E = process.env.TEST_ENV === "e2e";
