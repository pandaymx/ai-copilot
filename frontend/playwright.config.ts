import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright 配置。
 *
 * 默认（TEST_ENV !== "e2e"）走 Mock 模式：测试用例内部用 page.route 拦截 /api，
 * 模拟 SSE 流式响应与错误，零后端依赖、秒级跑完，稳定且可复现。
 *
 * 真实后端链路（TEST_ENV=e2e）：不注册 Mock 拦截，连接已运行的 next dev + backend。
 * 适合 nightly 或上线前的链路集成测试。
 *
 * webServer 由 Playwright 自动拉起本地 Next.js 服务，测试结束后自动回收。
 */
const PORT = process.env.PORT || 3099;
const baseURL = `http://localhost:${PORT}`;
const isRealE2E = process.env.TEST_ENV === "e2e";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.spec.ts",
  // e2e-real.spec.ts 仅在真实后端模式下执行，其余默认 Mock。
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ["html", { open: "never", outputFolder: "playwright-report" }],
    ["list"],
    ...(process.env.CI ? [["github", {}] as const] : []),
  ],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    // 录像仅 CI 开启以控制体积
    video: process.env.CI ? "retain-on-failure" : "off",
  },
  projects: [
    {
      name: "mock",
      testMatch: /e2e\/(chat|components)\.spec\.ts/,
    },
    {
      name: "real-e2e",
      testMatch: /e2e\/e2e-real\.spec\.ts/,
      // 真实后端模式才启用，否则跳过
      ...(isRealE2E ? {} : { testIgnore: /.*/ }),
    },
  ],
  webServer: {
    command: `PORT=${PORT} bun run dev`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});
