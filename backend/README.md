# AI Copilot 后端 · 多模型供应商统一抽象层

基于 **Spring Boot 4.1 / Spring AI 2.0** 的响应式（WebFlux）后端，提供一套与厂商无关的聊天能力。
前端只需指定「用哪个供应商的哪个模型」，后端自动完成协议适配、上下文裁剪与流式转发。

> 当前 README 聚焦「多模型供应商抽象层」这一新增模块；项目整体的启动方式见根目录说明。

---

## 1. 核心概念

### 1.1 两类公民（Citizens）

| | 一等公民 (First-class) | 二等公民 (Second-class) |
| --- | --- | --- |
| 代表厂商 | DeepSeek、OpenAI、Google Gemini、Ollama、Anthropic | 通义千问 Qwen、百度千帆、智谱 GLM 等 |
| 接入方式 | 由 Spring AI 官方 starter **自动装配** Bean | 启动时读 YAML **动态构建** `ChatModel` |
| 连接参数 | 由 `spring.ai.*` 管理（原生行为不变） | 由 `app.ai.second-class[*]` 配置 |
| 代码改动 | 无需改动 | 绝大多数情况**零代码**，仅加 YAML |

两者在 `ProviderRegistry` 中归一注册、路由时无差别对待，Service 层完全不可区分。

### 1.2 Provider ↔ Model 的 1:N 关系

一个供应商（如 `qwen`）下可声明**多个模型**（`qwen-max`、`qwen-plus` …），
每个模型有独立的展示名、描述、标签与上下文窗口大小。
多个模型**共享同一个** `ChatModel` 实例（同一套连接池），请求时通过 `ChatOptions.model(name)` 指定具体模型名，
因此「新增一个模型」只是加一行 YAML，不产生任何新的 HTTP 客户端。

---

## 2. 目录结构

```
src/main/java/xyz/ppmblszdp/ai/
├── config/        AiProviderProperties / ModelConfig / ProviderProtocol / ApiKeyValidator / AiBeansConfiguration
├── registry/      ProviderDescriptor / ModelDescriptor / ResolvedModel / ProviderRegistry
│                  FirstClassProviderRegistrar / SecondClassProviderRegistrar
├── factory/       ChatModelFactory / OpenAiCompatibleChatModelFactory / AnthropicCompatibleChatModelFactory / CustomChatModelFactory
├── spi/           CustomChatModelSupplier            # 自定义扩展点
├── context/       TokenEstimator / HeuristicTokenEstimator / ContextAssembler
├── service/       ChatService
├── controller/    ChatController / ModelCatalogController
├── dto/           ChatMessageDto / ChatRequest / ChatResponseDto / ModelCatalogResponse
└── exception/     AiException / ProviderNotFoundException / ModelNotFoundException / GlobalExceptionHandler
```

---

## 3. 配置样例

### 3.1 一等公民（`application.yaml` 的 `spring.ai.*` + `app.ai.first-class`）

连接参数放在 `spring.ai.*`（由 starter 自动装配），展示元数据放在 `app.ai.first-class`：

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:your_deepseek_api_key_here}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      chat:
        model: ${DEEPSEEK_MODEL:deepseek-chat}

app:
  ai:
    first-class:
      deepseek:
        display-name: DeepSeek
        models:
          - id: deepseek-chat
            name: deepseek-chat
            display-name: DeepSeek Chat
            description: DeepSeek 通用对话模型
            badge: 推荐
            tags: [chat]
            max-context-tokens: 32768
            default: true
```

### 3.2 二等公民（`app.ai.second-class`）

三种 `protocol` 任选其一：`openai` / `anthropic` / `custom`。密钥一律以 `${ENV:占位}` 引用。

```yaml
app:
  ai:
    second-class:
      # 通义千问：OpenAI 兼容协议（DashScope compatible-mode）
      - id: qwen
        display-name: 通义千问
        protocol: openai
        base-url: ${QWEN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
        api-key: ${QWEN_API_KEY:your_qwen_api_key_here}
        models:
          - id: qwen-max
            name: qwen-max
            display-name: 通义千问 Max
            description: 通义千问最强版本
            badge: 推荐
            tags: [chat]
            max-context-tokens: 32768
            default: true
          - id: qwen-plus
            name: qwen-plus
            display-name: 通义千问 Plus
            max-context-tokens: 131072

      # 百度千帆：OpenAI 兼容协议
      - id: qianfan
        display-name: 百度千帆
        protocol: openai
        base-url: ${QIANFAN_BASE_URL:https://qianfan.baidubce.com/v2}
        api-key: ${QIANFAN_API_KEY:your_qianfan_api_key_here}
        models:
          - id: ernie-4.5
            name: ernie-4.5-8k-preview
            display-name: 文心一言 4.5
            max-context-tokens: 8192
            default: true

      # 智谱 GLM：OpenAI 兼容协议
      - id: zhipu
        display-name: 智谱 GLM
        protocol: openai
        base-url: ${ZHIPU_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
        api-key: ${ZHIPU_API_KEY:your_zhipu_api_key_here}
        models:
          - id: glm-4
            name: glm-4
            display-name: 智谱 GLM-4
            max-context-tokens: 32768
            default: true
```

### 3.3 环境变量样例（`.env.example`）

```dotenv
# 二等公民（your_xxx_here 占位时自动跳过注册）
QWEN_API_KEY=your_qwen_api_key_here
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

QIANFAN_API_KEY=your_qianfan_api_key_here
QIANFAN_BASE_URL=https://qianfan.baidubce.com/v2

ZHIPU_API_KEY=your_zhipu_api_key_here
ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4

# 全局默认 provider / model（前端不带 provider/model 时使用）
AI_DEFAULT_PROVIDER=deepseek
AI_DEFAULT_MODEL=deepseek-chat
```

配置项完整字段参见 `AiProviderProperties`、`ModelConfig`、`ProviderProtocol` 源码中的 Javadoc。

---

## 4. 如何新增一家厂商

### 4.1 情况 A：OpenAI 兼容（最常见的 90% 场景）

绝大多数国产/第三方厂商都提供 OpenAI 兼容端点（Qwen、千帆 v2、智谱、月之暗面等）。
**只需在 `app.ai.second-class` 追加一段 YAML，零代码改动**：

```yaml
app:
  ai:
    second-class:
      - id: my-vendor
        display-name: 我的厂商
        protocol: openai                       # 关键：选 openai 协议
        base-url: ${MY_VENDOR_BASE_URL:https://api.my-vendor.com/v1}
        api-key: ${MY_VENDOR_API_KEY:your_my_vendor_api_key_here}
        models:
          - id: my-model-a
            name: actual-model-name            # 真正下发给厂商 API 的模型名
            display-name: 我的模型 A
            max-context-tokens: 32768
            default: true
```

### 4.2 情况 B：Anthropic / Claude 兼容

与 `openai` 完全对称，仅把 `protocol` 改为 `anthropic`：

```yaml
      - id: my-claude-gw
        display-name: Claude 兼容网关
        protocol: anthropic
        base-url: ${MY_CLUDE_BASE_URL:https://api.my-gateway.com}
        api-key: ${MY_CLUDE_API_KEY:your_xxx_here}
        models:
          - id: claude-compat
            name: claude-3-5-sonnet
            display-name: Claude 兼容模型
            max-context-tokens: 200000
            default: true
```

### 4.3 情况 C：非标准协议（自定义 SPI）

当厂商无法用 OpenAI/Anthropic 兼容协议对接（如百度原生 AK/SK 换 token），
实现扩展点接口并注册为 Spring Bean 即可，无需改动框架代码：

```java
@Component("baiduNative")   // Bean 名要与 YAML 的 supplier 字段一致
public class BaiduNativeSupplier implements CustomChatModelSupplier {
    @Override
    public ChatModel supply(AiProviderProperties.SecondClassConfig config) {
        // 自行用 config.apiKey() / config.baseUrl() 完成鉴权与客户端构建
        // 返回任意 ChatModel 实现
        return ...;
    }
}
```

```yaml
app:
  ai:
    second-class:
      - id: baidu-native
        display-name: 百度原生 AK/SK
        protocol: custom
        supplier: baiduNative               # 指向上面的 Bean 名
        api-key: ${BAIDU_API_KEY:your_baidu_api_key_here}
        models:
          - id: ernie-native
            name: ernie-4.0
            display-name: 文心 4.0 (原生)
            max-context-tokens: 8192
            default: true
```

> 新增协议类型时，只需新增一个 `ChatModelFactory` 实现 Bean，无需改动注册器与其它代码（开闭原则）。

---

## 5. 接口契约

### 5.1 流式对话 `POST /api/chat/stream`

请求体：

```json
{
  "message": "你好",
  "history": [
    { "role": "user",      "content": "上一轮问题" },
    { "role": "assistant", "content": "上一轮回答" }
  ],
  "provider": "qwen",        // 可选；缺省回落全局默认
  "model": "qwen-max",       // 可选；缺省回落该供应商默认模型
  "systemPrompt": "可选覆盖的系统提示词",
  "conversationId": "可选；记忆驱动会话 id，用于多轮对话",
  "userId": "可选；长期记忆隔离维度（用户画像/偏好）"
}
```

> **前端契约向后兼容**：只传 `{ message, history }` 也能跑通（provider/model 缺省回落全局默认）。
> `history` 末尾若已包含本轮 `message`，后端会自动**去重**，避免用户消息发送两遍。
> 传入 `conversationId` 即启用**记忆驱动路径**（需 `app.ai.memory.enabled=true`）：后端自动从
> PostgreSQL 读写会话历史，前端无需自行维护 `history` 即可多轮对话；若前端未传 `conversationId`，
> 后端会生成 UUID 并在流式首帧 `{"type":"conversation","conversationId":"..."}` 回传，前端应保存该值用于后续轮次。
> 不传 `conversationId` 时退化为旧 `history` 模式，完全零改动兼容。

响应：SSE 流，每帧为紧凑 JSON：

```
data: {"type":"conversation","conversationId":"uuid-xxxx"}   // 仅记忆路径且后端生成时
data: {"content":"你好"}
data: {"content":"，我是"}
...
data: [DONE]
```

> 流末补发 `[DONE]`；中途异常转为一条错误事件后正常结束，不会让前端只见网络错误。

完整的帧类型（含 `tool_call` / `tool_result` / `artifact`）、字段定义、发送顺序约定与前端消费指引，见独立协议文档 [SSE 流式协议说明](docs/sse-protocol.md)。

### 5.2 非流式对话 `POST /api/chat`

请求体同上，一次性返回：

```json
{
  "content": "你好，我是 AI 助手……",
  "provider": "qwen",
  "model": "qwen-max",
  "usage": null,
  "finishReason": null
}
```

### 5.3 模型列表 `GET /api/models`

返回 Provider → Model 的 1:N 结构，仅包含已注册（密钥有效且启用）的项：

```json
{
  "defaultProvider": "deepseek",
  "defaultModel": "deepseek-chat",
  "providers": [
    {
      "id": "qwen",
      "displayName": "通义千问",
      "tier": "SECOND_CLASS",
      "protocol": "openai",
      "defaultModelId": "qwen-max",
      "models": [
        { "id": "qwen-max", "displayName": "通义千问 Max", "description": "...",
          "badge": "推荐", "tags": ["chat"], "maxContextTokens": 32768 }
      ]
    }
  ]
}
```

---

## 6. 上下文管理（ContextAssembler）

- **System Prompt 保底注入**：优先级 请求内 system > provider 级 > 全局；永不参与历史裁剪。
- **Token 预算滑动窗口**：可用预算 = `maxContextTokens × historyRatio − systemTokens − reserveOutputTokens`；
  从最新消息向前累加，超出即止，时间复杂度 O(n)。
- **成对对齐**：以 user/assistant 轮次为单位保留，避免裁出「有 assistant 无 user」的孤儿消息导致厂商报错。
- **Token 估算**：默认启发式（CJK ≈1 token/字，ASCII ≈4 字符/token，乘安全系数）；已抽成 `TokenEstimator` 接口，可替换为精确 tokenizer。

相关配置（`app.ai.context`）：

```yaml
app:
  ai:
    context:
      reserve-output-tokens: 2048        # 为输出预留的 token
      history-ratio: 0.7                 # 历史可占用的预算比例 (0,1]
      default-max-context-tokens: 32768  # 模型未声明时的兜底
      safety-factor: 1.1                 # Token 估算安全系数（>1 高估）
```

---

## 7. 健壮性与排查

- **占位值跳过注册**：密钥为空白、匹配 `^your_.*_here$` 或长度 < 8 时，该供应商自动跳过注册，不出现在 `/api/models`。
  本地模型（如自建 Ollama 网关）可设 `requires-api-key: false` 豁免密钥校验。
- **精确错误**：请求不存在/未启用的 `provider` 或 `model` 时，返回结构化错误（含可用列表），便于排障。
- **启动期不做网络探活**：仅做配置完整性校验，避免某厂商临时不可达拖慢启动。
- **日志脱敏**：注册日志只输出 provider id / protocol / baseUrl / 模型数量；**严禁打印 apiKey**。

常见排查：

| 现象 | 可能原因 |
| --- | --- |
| `/api/models` 看不到某厂商 | 密钥仍是 `your_xxx_here` 占位，或 `enabled: false` |
| 流式中断只看到网络错误 | 上游厂商超时；已通过 `onErrorResume` + 错误事件兜底 |
| 用户消息重复 | 前端 `history` 已含当前消息且后端未去重（当前已自动去重） |
| 返回 4xx 配置错误 | `provider`/`model` 拼写错误或未启用 |

---

## 7.5. 记忆子系统（Spring AI Adapter 集成）

自本版本起，后端支持**会话记忆 + 长期记忆**双轨记忆，基于 Spring AI 2.0 原生 `ChatMemory` /
Advisor 范式实现，替代原来「前端维护 history」的纯前端驱动模式（旧模式仍兼容）。

### 架构与职责

- **会话记忆**：按 `conversationId` 隔离，持久化于 **PostgreSQL**（`spring_ai_chat_memory` 表，
  由 `spring-ai-starter-model-chat-memory-repository-jdbc` 自动建表）。`MessageChatMemoryAdvisor`
  在每次请求时通过 `ChatMemory.CONVERSATION_ID` 参数动态注入会话 id（**每条请求独立注入，避免多线程并发串线**），
  流式结束时自动回写本次对话。
- **Redis 热缓存**：`RedisCachingChatMemory` 装饰器在 JDBC 之上缓存最近 `hotCacheSize`（默认 20）条热消息：
  - 读：先查 Redis List，命中且条数满足直接返回；未命中回源 JDBC 并回填，设置 TTL（默认 14 天）防冷数据常驻。
  - 写：先写 JDBC（Ground Truth），再写 Redis 并更新 TTL。
  - Redis 不可用时自动降级为纯 JDBC，不阻断业务。
- **长期记忆**：用户画像/偏好经 embedding 存入 **pgvector**（`ai_long_term_memory` 表），每次请求由
  `QuestionAnswerAdvisor` 按 `userId` 维度向量检索 Top-K 注入。检索隔离通过
  `FilterExpressionBuilder.eq("userId", ...)` 实现，确保用户 A 不会检索到用户 B 的偏好。
- **限流**：`ChatRateLimiter` 基于 Redis 固定窗口计数（`INCR + EXPIRE`），保护上游配额；Redis 不可用时降级放行。

### 启用方式

记忆路径由总开关 `app.ai.memory.enabled` 控制（默认 `false`，此时完全退化为旧 `history` 模式）：

```yaml
app:
  ai:
    memory:
      enabled: true                      # 记忆路径总开关
      hot-cache-size: 20                 # Redis 热缓存条数 / RETRIEVE_SIZE 上限
      longterm-top-k: 5                  # 长期记忆 Top-K
      conversation-ttl-days: 14          # 会话热缓存 TTL（天）
      rate-limit:
        enabled: false                   # Redis 限流开关（默认关闭）
        capacity: 20
        refill-seconds: 60

spring:
  datasource:                            # PostgreSQL（会话记忆 + 长期记忆共用）
    url: jdbc:postgresql://localhost:5432/ai_copilot
    username: ai
    password: ai
  data:
    redis:
      host: localhost
      port: 6379
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE
        dimensions: 1536
```

### Docker 依赖

`compose.yaml` 已新增 `postgres`（pgvector 镜像）与 `redis` 服务。一键启动：

```bash
docker compose up -d postgres redis
# 表结构（会话记忆表 + 向量表 + vector 扩展）由 Spring AI starter 在启动时自动建，无需手动执行 SQL。
```

### 兼容性

- 仅传 `{ message, history }` → 沿用 `ContextAssembler` 旧路径，零改动兼容。
- 传 `conversationId` → 走记忆驱动；前端无需维护 `history`。`conversationId` 缺省时后端生成并回传。
- 记忆相关异常降级为「无记忆单次对话」并打 WARN，不向上抛 5xx。

---

## 8. 扩展依赖说明

本模块新增了 `spring-ai-starter-model-anthropic` 依赖，原因有二：

1. 让 **Claude 作为一等公民**可用；
2. 让**二等公民可走 Anthropic 兼容协议**（`protocol: anthropic`）。

其余 starter（deepseek / openai / google-genai / ollama）为项目既有依赖，未做改动。
`AiCopilotApplication.java` 未改动；`application.yaml` 仅追加 `app.ai.*` 段落，既有 `spring.ai.*` 完全不动。
