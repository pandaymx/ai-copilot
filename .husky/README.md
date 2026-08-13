# Git Hooks

This directory contains Husky-managed git hooks for the ai-copilot monorepo.

- `commit-msg` — validates commit messages against Conventional Commits via commitlint (see root `commitlint.config.js`).
- `pre-commit` — runs `bun run lint` (frontend) and `./gradlew spotlessCheck` (backend) on the changed subtree.

Hooks are enabled automatically after `bun install` at the repo root (the `prepare` script sets `core.hooksPath` to `.husky`).
