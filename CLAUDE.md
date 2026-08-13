# CLAUDE.md - AI Development & Orientation Guide

This document provides project orientation, architectural context, development commands, and coding standards for AI coding assistants working in this codebase.

---

## 🛠️ Technology Stack Overview

- **Backend (`/backend`)**:
  - **Framework & Runtime**: Java 25 / JDK 21+, Spring Boot 4.1.0, Spring AI 2.0 (Starter modules for DeepSeek, OpenAI, Google GenAI, Ollama, Anthropic).
  - **Build System**: Gradle Kotlin DSL (`build.gradle.kts`), Gradle Wrapper (`./gradlew`).
  - **Databases & Caching**: PostgreSQL 18 (with `pgvector` extension), Redis (Lettuce pool & reactive rate limiter).
  - **AI Architecture**: Unified `ProviderRegistry` supporting 1st-class starters and 2nd-class protocol factories, `ChatService`, `ModelHealthTracker`, JDBC chat memory + `pgvector` long-term vector store.

- **Frontend (`/frontend`)**:
  - **Framework & Runtime**: Next.js 16 (App Router, Turbopack), React 19, TypeScript 5, Bun 1.3.14.
  - **Styling & UI**: Tailwind CSS v4, Base UI, Lucide icons, dark mode support.
  - **Code Quality**: Biome (`biome check`) for linting and formatting.
  - **PWA & SSE**: Service Worker (`/sw.js`), SSE streaming via `@microsoft/fetch-event-source` & `useSpringAiStream`.

- **Infrastructure & Orchestration**:
  - **Containerization**: `compose.yaml` (PostgreSQL, Redis, Ollama, Backend, Frontend).
  - **Task Runner**: `start.sh` rootless orchestrator, `Taskfile.yml`.

---

## ⚡ Frequently Used Commands

### Backend (`/backend`)
```bash
# Run unit tests
./gradlew test

# Start backend standalone (port 8084)
./gradlew bootRun

# Build executable production JAR
./gradlew bootJar
```

### Frontend (`/frontend`)
```bash
# Run dev server (port 3000)
bun dev   # or npm run dev

# Run linter / code check (Biome)
bun run lint

# Build Next.js production bundle
bun run build
```

### Root Orchestration
```bash
# Start hybrid dev mode (Containers for DB/Redis/Ollama + Native Backend & Frontend)
./start.sh
# or using Taskfile:
task dev

# Start containerized infrastructure only (Postgres, Redis, Ollama)
./start.sh infra

# Full Docker containerized deployment
./start.sh docker

# Check status of containers and listening ports
./start.sh status

# Stop all containers
./start.sh stop
```

---

## 📐 Key Architecture Patterns

1. **API Gateway & Proxying (`frontend/src/app/api/[...path]/route.ts`)**:
   - Next.js route handler acts as proxy forwarding frontend requests to Spring Boot (`http://localhost:8084/api/...`).
   - Includes an in-memory sliding window rate limiter (`120 req/min` per IP) to prevent request flooding.
   - For SSE streaming endpoints (`/api/chat/stream`), streams chunks directly via `TransformStream`.

2. **Provider & Model Registry (`backend/src/main/java/xyz/ppmblszdp/ai/registry`)**:
   - **`FirstClassProviderRegistrar`**: Validates API keys using `ApiKeyValidator`. If an API key is missing or set to placeholder string (`your_xxx_here`), the provider is automatically skipped at startup. Ollama is keyless (`requiresApiKey: false`) and will register even without cloud API keys.
   - **`ProviderRegistry`**: Immutable thread-safe registry. If the configured `default-provider` (e.g. `deepseek`) is skipped, `ProviderRegistry` automatically falls back to the first available registered provider (e.g. `ollama`).

3. **Frontend Model Calibration (`frontend/src/components/chat/model-selector.tsx`)**:
   - Fetches available models dynamically via `GET /api/models`.
   - If current selected provider is not available on backend (e.g. `deepseek` missing key), auto-calibrates selection to backend's available default provider (e.g. `ollama` / `llama3`).
   - Performs low-frequency health check polling (`GET /api/models/health`). `fetchHealth()` must be decoupled from `catalog` dependency array in `useEffect` to prevent infinite fetch loops.

4. **Environment Variables**:
   - Environment variables for LLM keys (`DEEPSEEK_API_KEY`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`) are read by Spring Boot backend via `.env` file at project root (`/home/panda/ai-copilot/.env`) or `backend/.env`.
   - Spring Boot loads `.env` natively via `spring.config.import=optional:file:.env[.properties]`.

---

## 🎨 Code Style & Quality Guidelines

- **TypeScript / React**:
  - Format code using Biome standards (`bun run lint`).
  - Keep components modular and handle async error boundaries gracefully.
  - Never mutate transient state directly in global arrays; use immutable state setters (`setCatalog`, `setSessions`).

- **Java / Spring Boot**:
  - Follow standard Spring Boot idiomatic conventions and Java 21+ features (records, sealed interfaces, pattern matching).
  - Log errors with SLF4J loggers. Do not swallow exceptions silently without log context.
  - Ensure all new API endpoints have proper DTOs and validation.

- **Verification Protocol**:
  - Always execute `bun run lint` and `bun run build` (in `frontend/`) and `./gradlew test` (in `backend/`) after making non-trivial code modifications.

---

## 📝 Git Commit Conventions

Commits are enforced by husky + commitlint (root `package.json` + `.husky/`) and a Gradle Spotless plugin (backend). The same checks run remotely via the `commitlint` CI job on pull requests.

- **Format**: Conventional Commits `type(scope): subject`.
  - Types: `feat`, `fix`, `refactor`, `style`, `docs`, `chore`, `ci`, `test`, `perf`, `build`, `revert`.
  - Scopes: `backend`, `frontend`, `ci`, `docs`, `deps`, `release`, `root`.
  - Example: `fix(backend): validate API key before provider registration`
- **Pre-commit gates** (run by `.husky/pre-commit` on the changed subtree only):
  - Frontend → `bun run lint` (Biome).
  - Backend → `./gradlew spotlessCheck` (AOSP 4-space style via Palantir Java Format). Run `./gradlew spotlessApply` to auto-format if it fails.
- **Enable hooks**: `bun install` at repo root (the `prepare` script points `core.hooksPath` to `.husky`). Never commit with `--no-verify` to bypass gates.
