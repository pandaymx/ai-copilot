# AGENTS.md - Behavioral Rules & Operational Mandates for AI Agents

This file defines behavioral constraints, debugging workflows, and operational instructions for AI agents working in this repository.

---

## 1. Primary Operating Principles

- **Obey Project Conventions**: Always follow the architectural patterns established in `CLAUDE.md`, `README.md`, and the existing codebase.
- **Log Inspection Before Diagnosis**: Inspect raw log files and full stack traces before attempting to diagnose runtime issues or build failures. Never guess root causes without empirical log evidence.
- **No Superficial Symptom Masking**: Do not resolve errors by swallowing exceptions, masking symptoms with empty fallback objects, or commenting out failing test assertions. Fix the root cause of the underlying failure.
- **Empirical Verification Required**: Editing a file does NOT mark a task as completed. You MUST run build/lint/test verification commands (`bun run lint`, `bun run build` in `frontend/`, `./gradlew test` in `backend/`) to prove clean execution.
- **Commit Convention Compliance**: All commits MUST follow [Conventional Commits](https://www.conventionalcommits.org/) (`type(scope): subject`), enforced by the root `commitlint.config.js` via the `commit-msg` git hook. When staging changes, run `bun run lint` (frontend) and `./gradlew spotlessCheck` (backend) locally first so the `pre-commit` hook does not reject your commit. Never bypass hooks with `--no-verify` except in genuine emergencies, and document the reason when you do.

---

## 2. Component Specific Guidelines

### Frontend (Next.js 16 + React 19 + TypeScript)
- **API Proxy (`frontend/src/app/api/[...path]/route.ts`)**:
  - Keep proxy headers and CORS responses aligned with backend CORS configuration.
  - Maintain the sliding window rate limiter (`isRateLimited`). Do NOT remove IP-based rate limiting on sensitive or expensive endpoints.
  - Maintain `TransformStream` piping for SSE streaming endpoints (`/api/chat/stream`).

- **React State & Effects**:
  - Beware of state-triggered infinite fetch loops. Any function updating state (like `setCatalog`) MUST NOT be in the dependency array of a `useEffect` that calls that state-updating function.
  - For PWA Service Worker registration (`pwa-register.tsx`), check `document.readyState === "complete"` to handle client hydration mounting after window `load` event.

### Backend (Spring Boot 4.1.0 + Spring AI 2.0 + Java 21/25)
- **Provider & Model Registration**:
  - `FirstClassProviderRegistrar` MUST validate API keys with `ApiKeyValidator.isValid(apiKey)`. Do NOT bypass key validation for cloud providers.
  - `ProviderRegistry` MUST gracefully handle missing/unregistered default providers by falling back to the first registered active provider (e.g. `ollama`).

- **Database & Cache**:
  - Database schema migrations for `spring_ai_chat_memory` and vector tables (`ai_long_term_memory`) are managed by Spring AI starters.
  - Redis connection failures MUST NOT block Spring Boot startup (graceful degradation fallback enabled).

---

## 3. Environment & Deployment Workflow

- **Environment File**:
  - Root environment configuration is stored in `.env` (or `.env.example`).
  - Do NOT check secret API keys into git. Use placeholder strings (`your_xxx_here`) in `.env.example`.

- **Build Commands**:
  - Frontend code check: `bun run lint`
  - Frontend production build: `bun run build`
  - Backend test execution: `./gradlew test`

---

## 4. Git Commit Conventions

This repository enforces commit hygiene via husky + commitlint (root `package.json` + `.husky/`) and a Gradle Spotless plugin (backend). These run on **every local commit** and are mirrored by the `commitlint` CI job on pull requests.

### Commit Message Format
- Must match Conventional Commits: `type(scope): subject`
  - **Allowed types**: `feat`, `fix`, `refactor`, `style`, `docs`, `chore`, `ci`, `test`, `perf`, `build`, `revert`
  - **Allowed scopes**: `backend`, `frontend`, `ci`, `docs`, `deps`, `release`, `root`
  - Example: `feat(frontend): add session rename shortcut`
- Subject must be non-empty and ≤ 100 characters. The `commit-msg` hook (commitlint) rejects anything else.
- **Body lines must also be ≤ 100 characters** (`body-max-line-length` rule). Keep each bullet on its own short line. **If a bullet is too long, split it into multiple bullets or rephrase — never let a single line exceed 100 chars.** Run-on bullets like `- Added functionality to manage schema properties including adding, removing, and editing properties.` are the most common failure; break them up.
- **Never use `--allow-empty-message`** (or `-m ""`); an empty subject is rejected by commitlint.
- **Two-stage validation**: `pre-commit` (Spotless/Biome formatting) runs first, then `commit-msg` (commitlint). A commit can pass pre-commit yet still be rejected for a malformed message — fix the message and re-run `git commit` (staged files are preserved, nothing is lost).
- Full multi-line example (subject + body, all lines within 100 chars):
  ```
  feat(frontend): add SchemaBuilder component for visual JSON schema editing

  - Implemented SchemaBuilder for visual JSON schema editing.
  - Added property management: add, remove, and edit schema properties.
  - Created API client for custom tools with CRUD operations.
  ```
- Source of truth: root `commitlint.config.js`, which `extends: ['@commitlint/config-conventional']`. The `body-max-line-length: 100` rule comes from that preset (not spelled out in the file itself).

### Pre-Commit Quality Gates
- `pre-commit` hook runs checks **only for the parts you changed**:
  - Frontend changed → `bun run lint` (Biome) in `frontend/`.
  - Backend changed → `./gradlew spotlessCheck` in `backend/` (AOSP 4-space style via Palantir Java Format).
- Before committing, run these locally so the hook passes. If `spotlessCheck` fails, run `./gradlew spotlessApply` to auto-format the backend.
- Do NOT use `git commit --no-verify` to skip gates. If an emergency forces it, state the reason in the commit body.

### Enabling Hooks
- Hooks activate automatically after `bun install` at the repo root (the `prepare` script sets `core.hooksPath` to `.husky`). If hooks are missing, run `git config core.hooksPath .husky` once.
