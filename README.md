# 🤖 AI-Copilot

AI-Copilot 是一个面向个人与团队的全栈 AI 助手平台。它以 Spring AI 为后端能力层、
Next.js 为交互界面，提供多模型对话、流式生成、知识库检索、长期记忆、工作流与模型评测。

## ✨ 功能概览

- **多模型对话**：统一接入 DeepSeek、OpenAI、Google Gemini、Anthropic 和 Ollama；
  也可通过 OpenAI / Anthropic 兼容协议扩展其他供应商。
- **智能对话体验**：SSE 流式输出、会话管理、上下文继承与摘要、代码审查、翻译、
  图片生成、语音输入与语音合成。
- **知识库与 RAG**：上传或抓取多种文档，支持向量检索、全文混合检索、重排、文档对话、
  知识图谱、嵌入健康检查和定时知识源同步。
- **记忆与用量**：PostgreSQL/pgvector 长期记忆、Redis 热缓存与限流、用量统计和配额管理。
- **AI 工作台**：自定义工具、MCP 客户端、Agent 工具调用、多 Agent 编排、可视化工作流、
  模型对比与评测基准。
- **生产运维**：Caddy TLS 网关与基础认证、Prometheus、Tempo、OpenTelemetry Collector 和
  Grafana 仪表盘。

## 🧰 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Next.js 16、React 19、TypeScript、Tailwind CSS 4、Base UI、Biome |
| 后端 | Java 25、Spring Boot 4.1、Spring AI 2.0、WebFlux、Gradle |
| 数据与基础设施 | PostgreSQL 18 + pgvector、Redis 8、Ollama、Docker Compose |
| 可观测性 | Actuator、Prometheus、OpenTelemetry、Tempo、Grafana |

## 🚀 快速开始

### 1️⃣ 配置环境变量

从示例创建本地配置，按使用的模型填写至少一个真实 API Key。不要提交 `.env`。

```bash
cp .env.example .env
```

如果本地以宿主机方式运行，请在 `.env` 添加下面两项。`dev` 模式允许本地请求使用请求体中的
用户标识；生产环境必须保持 `strict` 并由可信网关注入 `X-User-Id`。

```dotenv
AUTH_MODE=dev
DEEPSEEK_API_KEY=your_real_key
```

也可以不使用云端模型，改用 Ollama。首次启动基础设施后拉取默认模型：

```bash
docker compose exec ollama ollama pull llama3
```

> 默认供应商为 DeepSeek。若未配置其密钥，可在 `.env` 设置
> `AI_DEFAULT_PROVIDER=ollama` 与 `AI_DEFAULT_MODEL=llama3`。

### 2️⃣ 选择启动模式

#### 💻 本地混合开发

PostgreSQL、Redis 和 Ollama 在容器中运行；后端和前端在宿主机运行。

前置要求：Docker Compose（或兼容的 Podman Compose）、JDK 25、Bun 1.3.14（Node/npm 可作为
前端启动回退）。

```bash
./start.sh
# 或 task dev
```

启动后访问 <http://localhost:3000>，后端健康检查为
<http://localhost:8084/actuator/health>。

#### 🐳 全 Docker 部署

无需在宿主机安装 JDK 或 Bun。此命令会构建并启动 compose 中的所有服务，包括 Caddy 网关和
监控栈；因此需先按下面的生产部署说明设置必填环境变量。

```bash
./start.sh docker
# 或 task docker:up
```

### 🛠️ 常用命令

```bash
# 仅启动 PostgreSQL、Redis 与 Ollama
./start.sh infra

# 查看 compose 服务及核心端口状态
./start.sh status

# 停止并删除 compose 容器（命名卷会保留）
./start.sh stop

# 前端开发、检查、构建与单元测试
cd frontend
bun dev
bun run lint
bun run build
bun test

# 后端启动、测试与格式检查
cd backend
./gradlew bootRun
./gradlew test
./gradlew spotlessCheck
```

## 🌐 服务与端口

所有容器端口（Caddy 除外）仅绑定到 `127.0.0.1`，避免直接暴露内部服务。

| 服务 | 地址/端口 | 用途 |
| --- | --- | --- |
| Frontend | <http://localhost:3000> | Web UI 与后端 API 代理 |
| Backend | <http://localhost:8084> | Spring Boot API；健康检查 `/actuator/health` |
| PostgreSQL | `127.0.0.1:5432` | 会话、知识库、pgvector 记忆 |
| Redis | `127.0.0.1:6379` | 限流、缓存和用量计数 |
| Ollama | `127.0.0.1:11435` | 本地模型运行时 |
| Grafana | <http://localhost:3100> | 指标与链路可视化 |
| Prometheus | <http://localhost:9090> | 指标采集与查询 |
| Tempo | `127.0.0.1:3200` | Trace 查询 API |

## 🏗️ 架构

```text
Browser
  │
  ▼
Next.js 16 (3000) ── API proxy / SSE ──► Spring Boot WebFlux (8084)
                                            │
             ┌──────────────────────────────┼──────────────────────────────┐
             ▼                              ▼                              ▼
   ProviderRegistry / Spring AI     PostgreSQL + pgvector                 Redis
   DeepSeek · OpenAI · Gemini       chat memory · RAG · memory     cache · rate limit
   Anthropic · Ollama · compatible
                                            │
                                            ▼
                                  OTEL Collector → Tempo
                                  Prometheus → Grafana
```

前端的 `/api/*` Route Handler 代理到后端，并保持 SSE 流、CORS 和按 IP 的滑动窗口限流。
后端通过 `ProviderRegistry` 注册可用模型；未配置真实密钥的云端供应商会被跳过，默认供应商
不可用时会回退到已注册的模型（例如 Ollama）。

## ⚙️ 配置说明

根目录 `.env.example` 列出了部署所需的变量，常用项如下：

| 变量 | 说明 |
| --- | --- |
| `DEEPSEEK_API_KEY`、`OPENAI_API_KEY`、`GEMINI_API_KEY`、`ANTHROPIC_API_KEY` | 云模型密钥；占位值不会注册对应供应商 |
| `AI_DEFAULT_PROVIDER`、`AI_DEFAULT_MODEL` | 未在请求中指定时使用的默认模型 |
| `POSTGRES_*`、`OLLAMA_HOST_PORT` | 基础设施连接信息和 Ollama 对外端口 |
| `AI_MEMORY_RATELIMIT_*` | 后端令牌桶限流开关、容量与窗口 |
| `CORS_ALLOWED_ORIGINS` | 允许访问后端的 Origin 列表 |
| `AUTH_MODE` | `dev` 仅用于本地开发；`strict` 为生产默认值 |
| `CADDY_DOMAIN`、`CADDY_BASIC_AUTH_HASH` | Caddy HTTPS 网关和基础认证配置 |

后端还支持在 `backend/.env` 配置更完整的模型、RAG、记忆和二等公民供应商参数；详细的多供应商
配置示例见 [backend/README.md](backend/README.md)。

## 🔐 生产部署

生产部署使用 Caddy 作为唯一公网入口，自动申请/续期 TLS 证书，并在验证 Basic Auth 后把可信的
`X-User-Id` 传给前端和后端，用于多租户数据隔离。

1. 将域名的 A 记录指向服务器，并确保公网可访问 80 和 443 端口。
2. 创建 `.env`，设置 `CADDY_DOMAIN` 和至少一个真实模型密钥。
3. 生成密码哈希，并将输出写入 `CADDY_BASIC_AUTH_HASH`：

   ```bash
   docker run --rm caddy:2-alpine caddy hash-password 'your-password'
   ```

4. 设置 Grafana 管理员密码 `GRAFANA_ADMIN_PASSWORD`，然后启动：

   ```bash
   ./start.sh docker
   ```

`caddy-data` 命名卷保存证书和 ACME 状态。生产环境不要设置
`PROXY_TRUST_X_USER_ID=true`，也不要将后端的 `AUTH_MODE` 改为 `dev`。

## 📁 项目结构

```text
.
├── backend/       Spring Boot、Spring AI、RAG、记忆、工具与 API
├── frontend/      Next.js UI、API 代理、PWA 与 E2E/组件测试
├── monitor/       Prometheus、Tempo、OpenTelemetry 和 Grafana 配置
├── compose.yaml   全服务 Docker Compose 编排
├── Caddyfile      HTTPS、Basic Auth 与反向代理配置
├── start.sh       本地混合开发与 Docker 启动脚本
└── Taskfile.yml   Task 快捷命令
```

## ✅ 质量检查

提交前请运行与改动范围对应的检查：

```bash
(cd frontend && bun run lint && bun run build)
(cd backend && ./gradlew spotlessCheck && ./gradlew test)
```

提交信息遵循 Conventional Commits，例如：

```text
docs(root): refresh project readme
```

## 📚 相关文档

- [后端多模型供应商配置](backend/README.md)
- [前端开发说明](frontend/README.md)
- [SSE 协议](backend/docs/sse-protocol.md)
- [安全扫描记录](docs/security-scan-findings.md)
