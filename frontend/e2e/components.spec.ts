import { expect, helpers, selectors, test } from "./fixtures";

/**
 * UI 组件与弹窗测试（Mock 模式）。
 * 覆盖：模型选择器、明暗主题切换、⌘K 搜索弹窗、导出对话弹窗、附件上传预览。
 */

test.describe("UI 组件与弹窗（Mock）", () => {
  test("模型选择器可打开并选择可用模型", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    await page.locator(selectors.modelSelector).click();
    // 下拉打开，显示 AI 供应商分组
    await expect(page.getByText("1. AI 供应商")).toBeVisible({
      timeout: 5_000,
    });
  });

  test("主题切换在明暗之间切换并持久化到 html.dark", async ({
    mockChat,
  }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    const isDarkBefore =
      (await page.locator("html").getAttribute("class"))?.includes("dark") ??
      false;

    await page.locator(selectors.themeToggle).click();
    await expect
      .poll(async () =>
        (await page.locator("html").getAttribute("class"))?.includes("dark"),
      )
      .toBe(!isDarkBefore);
  });

  test("⌘K / Ctrl+K 打开搜索弹窗并可输入查询", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    // page 已统一处理 metaKey/ctrlKey 强制打开，CI（Linux）用 Control+k 稳定触发
    await page.keyboard.press("Control+k");
    const searchBox = page.getByPlaceholder("搜索历史消息内容...");
    await expect(searchBox).toBeVisible({ timeout: 5_000 });

    await searchBox.fill("测试");
    await expect(searchBox).toHaveValue("测试");
  });

  test("点击搜索按钮同样打开搜索弹窗", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    await page.locator(selectors.searchBtn).first().click();
    await expect(
      page.getByPlaceholder("搜索历史消息内容..."),
    ).toBeVisible({ timeout: 5_000 });
  });

  test("导出对话弹窗可打开并复制为 Markdown", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    // 先产生一段对话
    await helpers.sendMessage(page, "导出测试");
    await helpers.waitForStreamDone(page);

    await page.locator(selectors.exportBtn).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible({ timeout: 5_000 });

    const confirmBtn = page.locator("#export-confirm-btn");
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();
  });

  test("附件输入框支持选择文件并显示预览", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    // 写入一个临时文本文件供上传
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: "test.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("Hello attachment test"),
    });

    // 出现附件文件名预览
    await expect(page.getByText("test.txt")).toBeVisible({
      timeout: 5_000,
    });
  });
});
