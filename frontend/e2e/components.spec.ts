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
    // 下拉打开，显示默认 provider 分组（模型选择器读取 /api/models 的目录）
    const popover = page.getByRole("listbox").or(page.locator("[role='menu']"));
    await expect(popover.first()).toBeVisible({ timeout: 5_000 });

    // 选择第一个可用模型项（若存在）
    const firstModel = page
      .locator("[role='menuitem']:not([aria-disabled='true']), [cmdk-item]:not([aria-disabled='true'])")
      .first();
    if (await firstModel.count()) {
      await firstModel.click();
      // 选择后弹出层关闭
      await expect(popover.first()).toHaveCount(0, { timeout: 5_000 });
    } else {
      // 无可用模型时关闭下拉即可
      await page.keyboard.press("Escape");
    }
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

  test("⌘K 打开搜索弹窗并可输入查询", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    await page.keyboard.press("Meta+k");
    // 搜索输入出现
    const searchBox = page.getByPlaceholder("搜索你的历史消息...");
    await expect(searchBox).toBeVisible({ timeout: 5_000 });

    await searchBox.fill("测试");
    // 搜索请求已发出（Mock 返回空结果，验证输入可交互）
    await expect(searchBox).toHaveValue("测试");
  });

  test("点击搜索按钮同样打开搜索弹窗", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    await page.locator(selectors.searchBtn).click();
    await expect(
      page.getByPlaceholder("搜索你的历史消息..."),
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

    const mdButton = page.getByRole("button", { name: "复制为 Markdown" });
    await expect(mdButton).toBeVisible();
    await mdButton.click();

    // 复制动作后弹窗关闭
    await expect(dialog).toHaveCount(0, { timeout: 5_000 });
  });

  test("附件输入框支持选择图片文件并显示预览", async ({ mockChat }) => {
    const page = mockChat;
    await helpers.mockApiRoutes(page, { initialSessions: [] });
    await page.goto("/");

    // 写入一个临时图片文件供上传
    const fileInput = page.locator('input[type="file"]');
    await expect(fileInput).toHaveCount(1, { timeout: 5_000 });

    // 使用 Playwright 的 setInputFiles 上传一个 1x1 png（base64）
    const pngBase64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
    await fileInput.setInputFiles({
      name: "test.png",
      mimeType: "image/png",
      buffer: Buffer.from(pngBase64, "base64"),
    });

    // 出现附件预览（img 元素）
    await expect(page.locator("img").first()).toBeVisible({
      timeout: 5_000,
    });
  });
});
