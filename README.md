# AI-Copilot

AI-Copilot 是一个结合 Java (Spring AI) 与 TypeScript (Next.js) 的高性能 AI 助手平台。

## 🚀 快速启动 (Quick Start)

项目在根目录提供了免 Root 的极简编排工具，支持**本地混合开发**与**全容器化部署**两种模式。

---

### 模式一：本地开发模式 (推荐开发调试)

基础设施 (PostgreSQL + Redis + Ollama) 运行在容器中，后端与前端在宿主机原生运行：

```bash
./start.sh
# 或使用 Task: task dev
```

---

### 模式二：全 Docker 容器化部署

前端、后端与基础设施全量打包为 Docker 镜像并于容器中独立运行：

```bash
./start.sh docker
# 或使用 Task: task docker:up
```

---

### 实用指令集

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

## 🛠️ 项目架构

- **`backend/`**: Java 25 + Spring Boot 3 + Spring AI 后端 (包含多阶段 `Dockerfile`)
- **`frontend/`**: Next.js 16 + React 19 + TailwindCSS 前端 (包含 Standalone 极简 `Dockerfile`)
- **`compose.yaml`**: 根目录全服务容器编排定义
- **`start.sh`**: 根目录免 Root 自动化拉起与控制脚本
- **`Taskfile.yml`**: Task 工具链任务定义
