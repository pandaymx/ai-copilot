# AGENTS.md - Behavioral Rules & Operational Mandates for AI Agents

This file defines behavioral constraints, debugging workflows, and operational instructions for AI agents working in this repository.

---

## 1. Primary Operating Principles

- **Obey Project Conventions**: Always follow the architectural patterns established in `CLAUDE.md`, `README.md`, and the existing codebase.
- **Log Inspection Before Diagnosis**: Inspect raw log files and full stack traces before attempting to diagnose runtime issues or build failures. Never guess root causes without empirical log evidence.
- **No Superficial Symptom Masking**: Do not resolve errors by swallowing exceptions, masking symptoms with empty fallback objects, or commenting out failing test assertions. Fix the root cause of the underlying failure.
- **Empirical Verification Required**: Editing a file does NOT mark a task as completed. You MUST run build/lint/test verification commands (`bun run lint`, `bun run build` in `frontend/`, `./gradlew test` in `backend/`) to prove clean execution.

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
