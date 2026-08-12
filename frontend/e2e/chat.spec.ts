import { expect, helpers, selectors, test } from "./fixtures";

/**
 * 核心聊天交互测试（Mock 模式）。
 * 覆盖：空白欢迎页、发送消息、流式逐字渲染、停止生成、会话列表新建/切换/删除、清空、错误卡片。
 */

test.describe("核心聊天交互（Mock）", () => {
  test("空白状态下显示欢迎页引导文案", async ({ mockChat }) => {
    await helpers.mockApiRoutes(mockChat, { initialSessions: [] });
    await mockChat.goto("/");
    await expect(mockChat.getByText("今天想与 AI 创造什么？")).toBeVisible();
    await expect(mockChat.locator(selectors.textarea)).toBeVisible();
  });

  test("发送消息后流式回复逐字渲染并最终完成", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, {
      initialSessions: [],
      streamText: "你好，我是基于 Spring AI 的 Copilot，很高兴为你服务。",
    });
    await page.goto("/");

    const textarea = page.locator(selectors.textarea);
    await textarea.fill("你好");
    await page.locator(selectors.send).click();

    // 用户消息出现在界面
    await expect(page.locator("main").getByText("你好", { exact: true })).toBeVisible();

    // 生成中：停止按钮出现
    await expect(page.locator(selectors.stop)).toBeVisible();

    // 流式内容逐步累加（不依赖固定 waitForTimeout）
    await expect(
      page.getByText("我是基于 Spring AI 的 Copilot", { exact: false }),
    ).toBeVisible({ timeout: 10_000 });

    // 生成结束：停止按钮消失
    await helpers.waitForStreamDone(page);

    // 完整回复落定
    await expect(
      page.getByText("很高兴为你服务。", { exact: false }),
    ).toBeVisible();
  });

  test("点击停止按钮可中断生成", async ({ mockChat }) => {
    const page = mockChat;
    // 用很长的文本，保证中途可点击停止
    const longText = "重复内容".repeat(40);
    await helpers.mockApiRoutes(page, {
      initialSessions: [],
      streamText: longText,
    });
    await page.goto("/");

    const textarea = page.locator(selectors.textarea);
    await textarea.fill("请讲个长故事");
    await page.locator(selectors.send).click();

    await expect(page.locator(selectors.stop)).toBeVisible();
    await page.locator(selectors.stop).click();

    // 停止后停止按钮消失
    await expect(page.locator(selectors.stop)).toHaveCount(0, {
      timeout: 5_000,
    });
  });

  test("新建会话后回到空白草稿态", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    await helpers.newSession(page);
    // 新建后应为空白草稿，欢迎文案可见
    await expect(
      page.getByText("今天想与 AI 创造什么？"),
    ).toBeVisible();
  });

  test("删除会话后从侧边栏移除", async ({ mockChat }) => {
    const page = mockChat;
    const sessionId = "sess-to-delete";
    await helpers.mockApiRoutes(page, {
      initialSessions: [
        { id: sessionId, title: "待删除会话", updatedAt: Date.now() },
      ],
    });
    await page.goto("/");

    const item = page.locator(`[data-session-id="${sessionId}"]`);
    await expect(item).toBeVisible();

    await page.locator(`[data-session-id="${sessionId}"] button[aria-label="删除会话"]`).click();
    // 确认弹窗（AlertDialog）确认
    await page.getByRole("button", { name: "确认删除" }).click();

    await expect(item).toHaveCount(0, { timeout: 5_000 });
  });

  test("清空会话按钮可点击且清空历史", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    // 先发一条消息以产生内容
    await helpers.sendMessage(page, "测试消息");
    await helpers.waitForStreamDone(page);
    await expect(page.locator("main").getByText("测试消息", { exact: true })).toBeVisible();

    // 点击清空（带确认弹窗）
    await page.getByRole("button", { name: "清空" }).click();
    await page.getByRole("button", { name: "确认清空" }).click();

    // 清空后回到空白态
    await expect(page.locator("main").getByText("测试消息", { exact: true })).toHaveCount(0, {
      timeout: 5_000,
    });
  });

  test("SSE 错误事件渲染为服务异常卡片", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, {
      initialSessions: [],
      streamError: true,
    });
    await page.goto("/");

    await helpers.sendMessage(page, "触发错误");
    await helpers.waitForStreamDone(page);

    await expect(page.locator(selectors.errorCard)).toBeVisible();
  });

  test("stream 接口返回 500 时显示连接受阻卡片", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, {
      initialSessions: [],
      streamStatus: 500,
    });
    await page.goto("/");

    await helpers.sendMessage(page, "触发 500");
    await helpers.waitForStreamDone(page);

    await expect(page.locator(selectors.errorCard)).toBeVisible();
  });

  test("sessions 接口失败时降级为本地缓存模式", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    // 让 sessions 请求失败 → 进入离线降级
    await page.route("**/api/chat/sessions", (route) => route.abort());
    await page.goto("/");

    await expect(page.locator(selectors.offlineBanner)).toBeVisible({
      timeout: 10_000,
    });
  });
});
