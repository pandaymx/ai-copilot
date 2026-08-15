# ==========================================
# Multi-stage Dockerfile for Next.js Frontend
# Synchronized with Bun 1.3.14 lockfile
# ==========================================

# 1. Base Image & Dependencies Stage (Bun for strict lockfile compliance)
FROM oven/bun:1.3.14-alpine AS deps
WORKDIR /app

COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

# 2. Builder Stage (Node.js V8 engine for reliable Next.js Turbopack build)
FROM node:26.7-alpine@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS builder
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production

RUN npx next build

# 3. Runner Stage (Minimal Node.js 22 runtime for standalone output)
FROM node:26.7-alpine@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 --ingroup nodejs nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

# Ensure the standalone server can write to its working directory
RUN chmod -R g+w /app

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]
