import { expect, helpers, selectors, test } from "./fixtures";

/**
 * 真实后端链路集成测试（TEST_ENV=e2e 时执行）。
 *
 * 前提：已运行 `bun run dev`（及后端服务，参考 README 混合模式）。
 * 运行：TEST_ENV=e2e bun run test:e2e --project=real-e2e
 *
 * 该文件不注册任何 Mock 拦截，直接连接真实 Next.js + 后端 API，
 * 用于 nightly 或上线前的真实链路验证。
 */

test.describe("真实后端链路集成（TEST_ENV=e2e）", () => {
  test("首页加载并可发送消息得到真实流式回复", async ({ page }) => {
    await page.goto("/");

    // 清空本地存储避免离线缓存干扰真实链路
    await page.evaluate(() => localStorage.clear());
    await page.reload();

    const textarea = page.locator(selectors.textarea);
    await expect(textarea).toBeVisible({ timeout: 15_000 });

    await textarea.fill("ping");
    await page.locator(selectors.send).click();

    // 用户消息应出现在界面
    await expect(page.getByText("ping", { exact: true })).toBeVisible();

    // 真实后端流式：等待出现任何 assistant 回复文本或停止按钮
    await expect(page.locator(selectors.stop)).toBeVisible({
      timeout: 20_000,
    });

    // 等待生成结束
    await helpers.waitForStreamDone(page);
  });

  test("模型选择器能加载真实模型目录", async ({ page }) => {
    await page.goto("/");
    await page.locator(selectors.modelSelector).click();

    const popover = page.getByRole("listbox").or(page.locator("[role='menu']"));
    await expect(popover.first()).toBeVisible({ timeout: 10_000 });
    await page.keyboard.press("Escape");
  });

  test("新建会话后侧边栏出现对应条目", async ({ page }) => {
    await page.goto("/");
    await helpers.newSession(page);
    // 新建后侧边栏应至少存在一个会话项（data-session-id 属性）
    await expect(
      page.locator("[data-session-id]").first(),
    ).toBeVisible({ timeout: 10_000 });
  });
});
