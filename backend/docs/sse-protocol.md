# SSE 流式协议说明（ChatChunkDto）

本文档定义后端 `/api/chat/stream` 通过 `text/event-stream` 推送的每一帧（`ChatChunkDto`）的字段、发送顺序与示例。所有帧均为 JSON，通过 SSE 的 `data:` 行下发；流结束以 SSE 原生 `data: [DONE]` 或业务 `done` 帧表示（二者语义一致，前端二选一终止即可）。

> 实现入口：`backend/src/main/java/xyz/ppmblszdp/ai/dto/ChatChunkDto.java`
> 序列化为 `@JsonInclude(NON_NULL)`，未使用的字段不会出现在 JSON 中。

---

## 1. 帧类型总表

| type | 含义 | 增量/快照 | 关键字段 |
| --- | --- | --- | --- |
| `conversation` | 会话建立 | 单帧 | `conversationId`, `provider`, `model`, `isFallback` |
| `content` | 文本增量 | **Delta（追加）** | `content` |
| `reasoning` | 推理/思考增量 | **Delta（追加）** | `reasoning` |
| `tool_call` | 工具调用意图 | **Single Snapshot** | `toolCallId`, `toolName`, `arguments` |
| `tool_result` | 工具调用结果 | **Single Snapshot** | `toolCallId`, `toolName`, `result`, `isError` |
| `artifact` | 可渲染产物 | **可流式** | `artifactId`, `language`, `artifactType`, `title`, `html`, `status` |
| `usage` | Token 用量 | 单帧 | `usage` |
| `metrics` | 实时流式性能指标 | 单帧快照（流结束前） | `metrics` (`timeToFirstToken`, `tokensPerSecond`, `totalDuration`, `toolCallDuration`) |
| `error` | 错误 | 单帧 | `code`, `message` |
| `done` | 流结束 | 单帧 | （无） |

---

## 2. 通用字段

所有帧都含 `type` 字段。以下字段按帧类型条件出现：

| 字段 | 类型 | 适用帧 |
| --- | --- | --- |
| `type` | string | 全部 |
| `conversationId` | string | conversation |
| `content` | string | content |
| `reasoning` | string | reasoning |
| `usage` | object | usage / done |
| `code` | string | error |
| `message` | string | error |
| `provider` | string | conversation |
| `model` | string | conversation |
| `isFallback` | boolean | conversation |
| `toolName` | string | tool_call / tool_result |
| `toolCallId` | string | tool_call / tool_result |
| `arguments` | string (JSON) | tool_call |
| `result` | string (JSON) | tool_result |
| `isError` | boolean | tool_result |
| `artifactId` | string | artifact |
| `language` | string | artifact |
| `artifactType` | string | artifact |
| `title` | string | artifact（可选） |
| `html` | string | artifact |
| `status` | string | artifact（可选：`drafting` / `streaming` / `final`） |

### `usage` 对象结构

```json
{
  "promptTokens": 12,
  "completionTokens": 34,
  "totalTokens": 46,
  "estimatedCostRmb": 0.00021,
  "monthlyUsed": 124500,
  "monthlyQuota": 1000000,
  "monthlyPercent": 12.45
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `promptTokens` | integer | 本次请求 Prompt Token 数 |
| `completionTokens` | integer | 本次请求模型生成 Token 数 |
| `totalTokens` | integer | 本次请求总 Token 数 |
| `estimatedCostRmb` | double | 本次请求预估费用（元，人民币） |
| `monthlyUsed` | integer (long) | 用户当月累计消耗总 Token 数（含本次） |
| `monthlyQuota` | integer (long) | 用户当月配额上限（0 表示无限制） |
| `monthlyPercent` | double | 用户当月配额使用百分比（0.0 ~ 100.0） |


---

## 3. 三类新增帧字段说明

### 3.1 `tool_call` 帧

表达一次工具调用意图，为**单帧快照**（非增量）。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `toolCallId` | 是 | 调用标识，需与对应 `tool_result` 帧的 `toolCallId` 配对 |
| `toolName` | 是 | 工具名称，如 `get_weather` |
| `arguments` | 是 | 工具参数，**必须是合法序列化的 JSON Object 字符串**，禁止发送 Raw Plain Text |

`arguments` 正确示例：

```json
{ "type": "tool_call", "toolCallId": "call_1", "toolName": "get_weather", "arguments": "{\"location\":\"Beijing\"}" }
```

### 3.2 `tool_result` 帧

表达工具调用结果，为**单帧快照**。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `toolCallId` | 是 | 与对应 `tool_call` 帧配对 |
| `toolName` | 否 | 工具名称，便于前端展示 |
| `result` | 是 | 结果内容，**推荐为 JSON Object 或 JSON Array** |
| `isError` | 否 | 标记该结果是否为错误（工具执行失败） |

`result` 格式规范：

- 推荐 `result` 为 JSON Object 或 JSON Array。
- 若工具实际返回的是纯字符串/数字，后端**必须**统一包装为标准结构，例如：

  ```json
  { "output": "晴，26℃" }
  ```

  而非直接发送裸文本 `"晴，26℃"`。
- 错误结果示例：

  ```json
  { "type": "tool_result", "toolCallId": "call_1", "toolName": "get_weather", "isError": true, "result": "{\"output\":\"timeout\"}" }
  ```

### 3.3 `artifact` 帧

表达可渲染产物（HTML / SVG / Markdown 等），**可流式**。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `artifactId` | 是 | 产物唯一 ID，用于前端去重与局部更新 |
| `language` | 是 | 产物语言，如 `html` / `svg` / `markdown` |
| `artifactType` | 是 | 产物类型，如 `code` / `document` / `chart` |
| `title` | 否 | 产物标题 |
| `html` | 是 | 产物内容 |
| `status` | 否 | 产物状态：`drafting` / `streaming` / `final` |

**`html` 字段判定规则（前端必读）**：

- 当 `status` 缺省或 `status="final"` 时，`html` 为**完整**内容，前端应整体覆写该 `artifactId` 产物。
- 当 `status="streaming"` 时，`html` 可能是**增量片段**（delta），前端应按 `artifactId` 累加拼接。
- 当 `status="drafting"` 时，表示产物已声明但内容尚未就绪，前端可先占位。

示例：

```json
{ "type": "artifact", "artifactId": "art_1", "language": "html", "artifactType": "document", "title": "周报", "html": "<h1>周报</h1>", "status": "streaming" }
```

---

## 4. SSE 帧顺序约定

为避免前端拼接错乱，后端须按如下顺序推进（带 `*` 表示可重复 0..n 次）：

```
conversation
→ (reasoning → content)*        // 多轮思考/文本可交替，但建议 reasoning 在前
→ (tool_call → tool_result)*    // 每轮工具调用：先意图后结果，按 toolCallId 配对
→ artifact*                     // 产物可多次下发（流式更新）
→ usage                         // 用量统计（可选，done 携带时亦可省略）
→ metrics                       // 实时性能指标（流结束前下发精准 TTFT、速率与耗时）
→ done                          // 流结束
→ [DONE]                        // SSE 原生结束标记（可选）
```

约束：

1. `conversation` 必须永远是流的第一帧。
2. `tool_call` 与 `tool_result` 必须成对出现，且 `tool_result.toolCallId` 必须等于其前序 `tool_call.toolCallId`。
3. `metrics` 帧在所有文本、思考及工具调用执行完毕、`done` 帧之前发送，确保前端精准接收完整调用性能指标。
4. `error` 帧可出现在任意位置以中断当前阶段；出现 `error` 后通常紧跟 `done`。
5. `done` 必须是业务最后一帧（其后可跟 SSE 原生 `[DONE]`）。

### 完整顺序示例（含工具调用、产物与性能指标）

```text
data: {"type":"conversation","conversationId":"c-1001","provider":"deepseek","model":"deepseek-chat","isFallback":false}

data: {"type":"reasoning","reasoning":"我需要先查天气。"}

data: {"type":"tool_call","toolCallId":"call_1","toolName":"get_weather","arguments":"{\"location\":\"Beijing\"}"}

data: {"type":"tool_result","toolCallId":"call_1","toolName":"get_weather","result":"{\"output\":\"晴，26℃\"}","isError":false}

data: {"type":"content","content":"北京今天"}

data: {"type":"content","content":"晴，26℃。"}

data: {"type":"artifact","artifactId":"art_1","language":"html","artifactType":"document","title":"天气卡片","html":"<div>晴 26℃</div>","status":"final"}

data: {"type":"usage","usage":{"promptTokens":20,"completionTokens":40,"totalTokens":60,"estimatedCostRmb":0.0003}}

data: {"type":"metrics","metrics":{"timeToFirstToken":280,"tokensPerSecond":42.5,"totalDuration":1220,"toolCallDuration":450,"isEstimated":false}}

data: {"type":"done"}

data: [DONE]
```

---

## 5. 前端消费侧指引（Next.js / EventSource / fetchEventSource）

- **增量拼接 vs 完整覆写**
  - `content` / `reasoning`：**Delta 模式**，按帧顺序 `+=` 追加到对应缓冲区。
  - `tool_call` / `tool_result`：**Single Snapshot**，按 `toolCallId` 建立/更新一条调用记录，不做增量拼接。
  - `artifact`：**按 `status` 判定**——
    - `status="streaming"` → `html` 为增量片段，按 `artifactId` 拼接；
    - `status="final"` 或缺省 → `html` 为完整内容，整体覆写该 `artifactId`。

- **JSON 解析**
  - `arguments`、`result` 均为 JSON 字符串，消费端须 `JSON.parse`；解析失败时应降级为展示原始字符串而非崩溃。

- **配对**
  - 维护 `Map<toolCallId, {call, result}>`，收到 `tool_result` 时回填到对应 `tool_call`，用于展示「调用 → 结果」结构。

- **结束判定**
  - 收到 `done` 帧**或** SSE 原生 `data: [DONE]` 即终止流，二者出现任一即可。
```
