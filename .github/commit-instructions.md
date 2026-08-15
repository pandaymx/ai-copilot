# Copilot Commit Message 生成指令

本文件指导 GitHub Copilot 生成 commit message 时遵循本仓库的提交约定（与 `commitlint.config.js` 一致，由 husky `commit-msg` 钩子强制校验）。

## 格式（Conventional Commits）

```
<type>(<scope>): <subject>
```

### type（必填，仅限以下之一）
`feat` · `fix` · `refactor` · `style` · `docs` · `chore` · `ci` · `test` · `perf` · `build` · `revert`

### scope（必填，仅限以下之一）
`backend` · `frontend` · `ci` · `docs` · `deps` · `release` · `root`

### subject
- 非空，≤ 100 字符。
- 使用祈使句、简洁描述改动；默认使用**中文**。
- 不写句号结尾。

## 示例

- `feat(frontend): 新增会话重命名快捷键`
- `fix(backend): 修复 ApiKey 校验空指针`
- `chore(deps): 升级 jsoup 至 1.23.1 修复 CVE`
- `docs(root): 更新安全扫描整改说明`

## 禁止

- 不要生成无 scope 或 scope 不在白名单的 message（钩子会拒绝）。
- 不要使用 `Update`、`WIP`、`fix bug` 这类无意义 subject。
- 不要跳过或绕过 commit-msg 钩子。
