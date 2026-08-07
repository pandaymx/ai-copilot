# AI-Copilot

AI-Copilot 是一个结合 Java (Spring AI) 与 TypeScript (Next.js) 的高性能 AI 助手平台。

## 🚀 快速启动 (One-Click Launch)

项目在根目录提供了一个**免 Root 极简拉起脚本**，能够自动检测环境中的容器引擎（`docker compose` 或 `podman compose`），并并行启动前端、后端与基础设施。

### 方式一：直接运行根脚本 (推荐)

```bash
# 启动全栈服务 (基础设施 + 后端 + 前端)
./start.sh

# 仅启动基础设施 (PostgreSQL + Redis + Ollama)
./start.sh infra

# 检查服务与端口就绪状态
./start.sh status

# 停止基础设施容器
./start.sh stop
```

### 方式二：使用 `Task` 工具

若系统中安装了 [`task`](https://taskfile.dev) (Go Task)，亦可运行：

```bash
task        # 启动全栈
task infra  # 启动基础设施
task status # 查看服务状态
task stop   # 停止基础设施
```

---

## 🛠️ 项目结构

- **`backend/`**: Java 21 + Spring Boot 3 + Spring AI 后端
- **`frontend/`**: Next.js 16 + React 19 + TailwindCSS + Shadcn/ui 前端
- **`start.sh`**: 根目录无根编排脚本
- **`Taskfile.yml`**: Task 编排配置文件
