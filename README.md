# AI-Copilot

AI-Copilot 是一个结合 Java (Spring AI) 与 TypeScript (Next.js) 的高性能 AI 助手平台。

## 🚀 快速启动 (Quick Start)

项目在根目录提供了免 Root 的极简编排工具，支持**本地混合开发**与**全 Docker 容器化部署**两种模式。

---

### 模式一：本地混合开发模式 (宿主机原生运行 + 容器化基础设施)

基础设施 (PostgreSQL + Redis + Ollama) 运行在容器中，后端与前端在宿主机原生运行。

#### 📋 宿主机前置依赖 (Prerequisites)
- **Java (JDK 25)**: 后端基于 Spring Boot 4.1 + Java 25，需确保 `java` 在 `PATH` 中。
  - *推荐使用 SDKMAN 安装*: `sdk install java 25-open`
- **Bun (1.3.14)**: 前端推荐使用 Bun 包管理器。
  - *安装方式*: `curl -fsSL https://bun.sh/install | bash`
  - *PATH 声明*: 请确保 `~/.bun/bin` 已加入环境变量（`start.sh` 会自动检测并优先包含 `~/.bun/bin`）。

```bash
./start.sh
# 或使用 Task: task dev
```

---

### 模式二：全 Docker 容器化部署 (零依赖/一键开箱即用)

前端、后端与基础设施全量打包为 Docker 镜像并在容器中独立运行。**镜像内已自动预置 JDK 25 与运行环境，宿主机无需安装任何 JDK 25 或 Bun/Node 工具链**：

```bash
./start.sh docker
# 或使用 Task: task docker:up
```

---

### 🌐 生产部署 (Caddy 反向代理 + 自动 TLS)

容器化部署之上，可叠加一个 **Caddy 网关容器**作为统一入口，提供生产级 HTTPS：

- **自动 TLS**：Caddy 内置 ACME (Let's Encrypt) 客户端，自动签发证书并在到期前 ~30 天自动续期，**无需 certbot 容器**。
- **反向代理**：`Caddy(443) → frontend(3000) → backend(8084)`，证书续期全程无人工干预。
- **身份边界**：整站 `basic_auth`，认证通过后将受信任头 `X-User-Id` 注入上游，后端 (`app.auth.mode=strict`) 据此做多租户隔离。

#### 前置条件
1. 将 `CADDY_DOMAIN` 的 **A 记录指向本机公网 IP**。
2. 服务器 **80/443 端口公网可达**（采用 HTTP 挑战，无需 Cloudflare Token）。

#### 部署步骤
```bash
# 1. 准备环境变量（含 Caddy 域名与 basic_auth 哈希）
cp .env.example .env
# 生成 basic_auth 哈希并填入 .env 的 CADDY_BASIC_AUTH_HASH
docker run --rm caddy:2-alpine caddy hash-password '你的密码'
# 编辑 .env：设置 CADDY_DOMAIN 与 CADDY_BASIC_AUTH_HASH

# 2. 启动全部服务（含 caddy 网关）
./start.sh docker
# 或 task docker:up
```

#### 说明
- 证书与 ACME 状态持久化于 `caddy-data` 卷，容器重启不丢证书、不触发 Let's Encrypt 限频。
- `frontend` / `backend` 仍保留 `127.0.0.1` 回环绑定，便于本地直连调试，与 Caddy 通过服务名访问互不冲突。
- `Caddyfile` 已内置配置；如需通配/隐藏源站等高级场景，可改用 DNS 挑战（需补充对应 DNS 插件与环境变量）。

---

### 🛠️ 实用指令集

```bash
# 仅启动基础设施 (PostgreSQL + Redis + Ollama)
./start.sh infra
# 或 task infra

# 检查所有服务与端口健康就绪状态
./start.sh status
# 或 task status

# 停止并清理所有容器
./start.sh stop
# 或 task stop
```

---

## 🏗️ 项目架构

- **`backend/`**: Java 25 + Spring Boot 4.1 + Spring AI 后端 (包含多阶段 `Dockerfile`)
- **`frontend/`**: Next.js 16 + React 19 + TailwindCSS 前端 (包含 Standalone 极简 `Dockerfile`)
- **`compose.yaml`**: 根目录全服务容器编排定义
- **`start.sh`**: 根目录免 Root 自动化拉起与控制脚本 (包含环境预检与工具链 PATH 自动注入)
- **`Taskfile.yml`**: Task 工具链任务定义
