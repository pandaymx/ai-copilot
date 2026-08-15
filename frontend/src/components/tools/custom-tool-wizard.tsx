"use client";

import { Code, Globe, MessageSquare, Save, Sparkles, X } from "lucide-react";
import { useState } from "react";
import {
  type CustomToolItem,
  type CustomToolType,
  createCustomTool,
  type HttpConfig,
  updateCustomTool,
} from "@/lib/custom-tool-api";
import { CustomToolTester } from "./custom-tool-tester";
import { SchemaBuilder } from "./schema-builder";

interface CustomToolWizardProps {
  initialTool?: CustomToolItem | null;
  onClose: () => void;
  onSuccess: (saved: CustomToolItem) => void;
}

const TEMPLATES: {
  title: string;
  description: string;
  type: CustomToolType;
  tool: Partial<CustomToolItem>;
}[] = [
  {
    title: "天气实时查询 (HTTP)",
    description: "调用开放天气 API 获取指定城市温度与天气实况",
    type: "HTTP",
    tool: {
      name: "get_weather",
      displayName: "天气查询",
      description: "查询指定城市的实时气温、天气状况与空气湿度",
      type: "HTTP",
      parametersSchema: JSON.stringify(
        {
          type: "object",
          properties: {
            city: {
              type: "string",
              description: "城市名称（如 Beijing, Shanghai）",
            },
          },
          required: ["city"],
        },
        null,
        2,
      ),
      httpConfig: {
        url: "https://wttr.in/{{city}}?format=j1",
        method: "GET",
        headers: { Accept: "application/json" },
        authType: "NONE",
        timeoutSeconds: 15,
      },
    },
  },
  {
    title: "数据统计分析 (Python)",
    description: "在 Python 沙箱中计算数组均值、中位数与方差",
    type: "SCRIPT",
    tool: {
      name: "calc_statistics",
      displayName: "数据统计计算",
      description: "输入一组数值，计算均值、中位数、极值与标准差",
      type: "SCRIPT",
      parametersSchema: JSON.stringify(
        {
          type: "object",
          properties: {
            numbers: {
              type: "array",
              description: "数值列表 (例如 [12, 34, 56, 78])",
            },
          },
          required: ["numbers"],
        },
        null,
        2,
      ),
      scriptConfig: {
        language: "python",
        scriptCode: `import statistics

nums = params.get('numbers', [])
if not nums:
    print('错误：数组为空')
else:
    avg = statistics.mean(nums)
    median = statistics.median(nums)
    print(f"数量: {len(nums)}, 平均值: {avg:.2f}, 中位数: {median}, 最小值: {min(nums)}, 最大值: {max(nums)}")`,
      },
    },
  },
  {
    title: "SQL 查询生成器 (Prompt)",
    description: "基于业务需求和表结构生成优化 SQL",
    type: "PROMPT",
    tool: {
      name: "generate_sql",
      displayName: "SQL 语句生成",
      description: "根据自然语言需求与数据库类型生成高效的 SQL 查询语句",
      type: "PROMPT",
      parametersSchema: JSON.stringify(
        {
          type: "object",
          properties: {
            dialect: {
              type: "string",
              description: "数据库方言 (如 PostgreSQL / MySQL)",
            },
            requirement: { type: "string", description: "具体的查询需求描述" },
          },
          required: ["requirement"],
        },
        null,
        2,
      ),
      promptConfig: {
        systemPrompt:
          "你是一个精通 SQL 优化的专家，请只输出纯 SQL 代码，包含关键索引提示，不要添加多余客套话。",
        promptTemplate:
          "目标数据库: {{dialect}}\n业务查询需求: {{requirement}}\n请生成对应的 SQL 语句：",
      },
    },
  },
];

export function CustomToolWizard({
  initialTool,
  onClose,
  onSuccess,
}: CustomToolWizardProps) {
  const isEdit = !!initialTool?.id;

  const [activeTab, setActiveTab] = useState<"config" | "schema" | "test">(
    "config",
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 表单状态
  const [name, setName] = useState(initialTool?.name || "");
  const [displayName, setDisplayName] = useState(
    initialTool?.displayName || "",
  );
  const [description, setDescription] = useState(
    initialTool?.description || "",
  );
  const [type, setType] = useState<CustomToolType>(initialTool?.type || "HTTP");
  const [schemaJson, setSchemaJson] = useState(
    initialTool?.parametersSchema ||
      '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}',
  );

  // HTTP 配置
  const [httpUrl, setHttpUrl] = useState(
    initialTool?.httpConfig?.url || "https://api.example.com/data",
  );
  const [httpMethod, setHttpMethod] = useState<HttpConfig["method"]>(
    initialTool?.httpConfig?.method || "GET",
  );
  const [httpBody, setHttpBody] = useState(
    initialTool?.httpConfig?.bodyTemplate || "",
  );
  const [authType, setAuthType] = useState<
    "NONE" | "BEARER" | "API_KEY" | "BASIC"
  >(initialTool?.httpConfig?.authType || "NONE");
  const [authToken, setAuthToken] = useState(
    initialTool?.httpConfig?.authToken || "",
  );
  const [authHeader, setAuthHeader] = useState(
    initialTool?.httpConfig?.authHeader || "X-API-Key",
  );
  const [timeoutSec, setTimeoutSec] = useState(
    initialTool?.httpConfig?.timeoutSeconds || 30,
  );

  // Script 配置
  const [scriptLang, setScriptLang] = useState<"python" | "javascript">(
    initialTool?.scriptConfig?.language || "python",
  );
  const [scriptCode, setScriptCode] = useState(
    initialTool?.scriptConfig?.scriptCode ||
      '# params 字典已注入\nprint(f"Received params: {params}")',
  );

  // Prompt 配置
  const [systemPrompt, setSystemPrompt] = useState(
    initialTool?.promptConfig?.systemPrompt ||
      "你是一个严谨的辅助工具助手，请直接输出结果。",
  );
  const [promptTemplate, setPromptTemplate] = useState(
    initialTool?.promptConfig?.promptTemplate || "请处理如下任务：\n{{input}}",
  );

  const applyTemplate = (t: (typeof TEMPLATES)[0]) => {
    if (t.tool.name) setName(t.tool.name);
    if (t.tool.displayName) setDisplayName(t.tool.displayName);
    if (t.tool.description) setDescription(t.tool.description);
    if (t.tool.type) setType(t.tool.type);
    if (t.tool.parametersSchema) setSchemaJson(t.tool.parametersSchema);

    if (t.tool.httpConfig) {
      setHttpUrl(t.tool.httpConfig.url || "");
      setHttpMethod(t.tool.httpConfig.method || "GET");
      setHttpBody(t.tool.httpConfig.bodyTemplate || "");
      setAuthType(t.tool.httpConfig.authType || "NONE");
      setAuthToken(t.tool.httpConfig.authToken || "");
      setTimeoutSec(t.tool.httpConfig.timeoutSeconds || 30);
    }
    if (t.tool.scriptConfig) {
      setScriptLang(t.tool.scriptConfig.language || "python");
      setScriptCode(t.tool.scriptConfig.scriptCode || "");
    }
    if (t.tool.promptConfig) {
      setSystemPrompt(t.tool.promptConfig.systemPrompt || "");
      setPromptTemplate(t.tool.promptConfig.promptTemplate || "");
    }
  };

  const buildCurrentToolPayload = (): CustomToolItem => {
    return {
      id: initialTool?.id,
      name: name.trim(),
      displayName: displayName.trim() || name.trim(),
      description: description.trim(),
      type,
      enabled: initialTool?.enabled ?? true,
      parametersSchema: schemaJson,
      httpConfig:
        type === "HTTP"
          ? {
              url: httpUrl.trim(),
              method: httpMethod,
              bodyTemplate: httpBody.trim() || undefined,
              authType: authType,
              authHeader:
                authType === "API_KEY" ? authHeader.trim() : undefined,
              authToken: authToken.trim() || undefined,
              timeoutSeconds: Number(timeoutSec) || 30,
            }
          : undefined,
      scriptConfig:
        type === "SCRIPT"
          ? {
              language: scriptLang,
              scriptCode,
            }
          : undefined,
      promptConfig:
        type === "PROMPT"
          ? {
              systemPrompt: systemPrompt.trim() || undefined,
              promptTemplate: promptTemplate.trim(),
            }
          : undefined,
    };
  };

  const handleSave = async () => {
    setError(null);
    if (!name.trim()) {
      setError("工具函数名 (name) 不能为空");
      return;
    }
    if (!/^[a-zA-Z0-9_-]{1,64}$/.test(name.trim())) {
      setError("工具函数名必须由 1~64 位字母、数字、下划线或中划线组成");
      return;
    }

    setLoading(true);
    const payload = buildCurrentToolPayload();

    try {
      let saved: CustomToolItem;
      if (isEdit && initialTool?.id) {
        saved = await updateCustomTool(initialTool.id, payload);
      } else {
        saved = await createCustomTool(payload);
      }
      onSuccess(saved);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "保存失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-2xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
        {/* 弹窗头部 */}
        <div className="flex items-center justify-between border-b border-zinc-200/80 px-6 py-4 dark:border-zinc-800/80">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white shadow-xs">
              <Sparkles className="size-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                {isEdit ? "编辑自定义工具" : "创建自定义工具 (Tool DSL)"}
              </h3>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                定义 HTTP API、脚本沙箱或 Prompt 驱动的 Agent 工具
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
          >
            <X className="size-5" />
          </button>
        </div>

        {/* 模版快捷选择（仅创建模式展示） */}
        {!isEdit && (
          <div className="border-b border-zinc-100 bg-zinc-50/50 px-6 py-2.5 dark:border-zinc-800/60 dark:bg-zinc-900/40">
            <div className="flex items-center gap-2 overflow-x-auto text-xs scrollbar-hidden">
              <span className="text-[11px] font-semibold text-zinc-500 shrink-0">
                快速套用模版：
              </span>
              {TEMPLATES.map((tmpl) => (
                <button
                  key={tmpl.title}
                  type="button"
                  onClick={() => applyTemplate(tmpl)}
                  className="flex items-center gap-1.5 rounded-lg border border-zinc-200 bg-white px-2.5 py-1 text-xs text-zinc-700 hover:border-indigo-500 hover:text-indigo-600 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:border-indigo-400 dark:hover:text-indigo-300 transition-all shrink-0"
                >
                  {tmpl.type === "HTTP" && (
                    <Globe className="size-3 text-blue-500" />
                  )}
                  {tmpl.type === "SCRIPT" && (
                    <Code className="size-3 text-emerald-500" />
                  )}
                  {tmpl.type === "PROMPT" && (
                    <MessageSquare className="size-3 text-purple-500" />
                  )}
                  <span>{tmpl.title}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* 选项卡导航 */}
        <div className="flex border-b border-zinc-200/80 px-6 dark:border-zinc-800">
          <button
            type="button"
            onClick={() => setActiveTab("config")}
            className={`border-b-2 px-4 py-2.5 text-xs font-semibold transition-all ${
              activeTab === "config"
                ? "border-indigo-500 text-indigo-600 dark:text-indigo-400"
                : "border-transparent text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
            }`}
          >
            1. 基础与配置
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("schema")}
            className={`border-b-2 px-4 py-2.5 text-xs font-semibold transition-all ${
              activeTab === "schema"
                ? "border-indigo-500 text-indigo-600 dark:text-indigo-400"
                : "border-transparent text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
            }`}
          >
            2. 参数 Schema 定义
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("test")}
            className={`border-b-2 px-4 py-2.5 text-xs font-semibold transition-all ${
              activeTab === "test"
                ? "border-indigo-500 text-indigo-600 dark:text-indigo-400"
                : "border-transparent text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
            }`}
          >
            3. 沙箱测试调试
          </button>
        </div>

        {/* 弹窗主体内容 */}
        <div className="flex-1 overflow-y-auto p-6 space-y-5">
          {error && (
            <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs font-medium text-rose-600 dark:text-rose-400">
              ⚠️ {error}
            </div>
          )}

          {activeTab === "config" && (
            <div className="space-y-4">
              {/* 基础字段 */}
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="space-y-1">
                  <span className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    工具函数名 (Function Name){" "}
                    <span className="text-rose-500">*</span>
                  </span>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="如 get_weather, query_db"
                    className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs font-mono text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                  />
                  <p className="text-[10px] text-zinc-400">
                    供大模型 Function Calling 调用识别，仅限英文/数字/下划线
                  </p>
                </div>

                <div className="space-y-1">
                  <span className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    工具展示名 (Display Title)
                  </span>
                  <input
                    type="text"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="如 天气实时查询"
                    className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <span className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  工具功能描述 (Description){" "}
                  <span className="text-rose-500">*</span>
                </span>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={2}
                  placeholder="清晰描述此工具的功能、适用场景与返回值，大模型将根据此描述自动决策何时调用。"
                  className="w-full rounded-lg border border-zinc-200 bg-white p-2.5 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                />
              </div>

              {/* 工具类型选择 */}
              <div className="space-y-2">
                <span className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  工具类型
                </span>
                <div className="grid grid-cols-3 gap-3">
                  <button
                    type="button"
                    onClick={() => setType("HTTP")}
                    className={`flex flex-col items-center gap-1.5 rounded-xl border p-3 text-center transition-all ${
                      type === "HTTP"
                        ? "border-blue-500 bg-blue-50/50 text-blue-700 shadow-xs dark:border-blue-500 dark:bg-blue-500/10 dark:text-blue-300"
                        : "border-zinc-200 hover:border-zinc-300 dark:border-zinc-800 dark:hover:border-zinc-700"
                    }`}
                  >
                    <Globe className="size-5 text-blue-500" />
                    <span className="text-xs font-bold">HTTP API</span>
                    <span className="text-[10px] text-zinc-500">
                      外部 REST 服务
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setType("SCRIPT")}
                    className={`flex flex-col items-center gap-1.5 rounded-xl border p-3 text-center transition-all ${
                      type === "SCRIPT"
                        ? "border-emerald-500 bg-emerald-50/50 text-emerald-700 shadow-xs dark:border-emerald-500 dark:bg-emerald-500/10 dark:text-emerald-300"
                        : "border-zinc-200 hover:border-zinc-300 dark:border-zinc-800 dark:hover:border-zinc-700"
                    }`}
                  >
                    <Code className="size-5 text-emerald-500" />
                    <span className="text-xs font-bold">脚本沙箱</span>
                    <span className="text-[10px] text-zinc-500">
                      Python / JS 执行
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setType("PROMPT")}
                    className={`flex flex-col items-center gap-1.5 rounded-xl border p-3 text-center transition-all ${
                      type === "PROMPT"
                        ? "border-purple-500 bg-purple-50/50 text-purple-700 shadow-xs dark:border-purple-500 dark:bg-purple-500/10 dark:text-purple-300"
                        : "border-zinc-200 hover:border-zinc-300 dark:border-zinc-800 dark:hover:border-zinc-700"
                    }`}
                  >
                    <MessageSquare className="size-5 text-purple-500" />
                    <span className="text-xs font-bold">Prompt 工具</span>
                    <span className="text-[10px] text-zinc-500">
                      领域 Prompt 虚拟工具
                    </span>
                  </button>
                </div>
              </div>

              {/* 类型专属详细配置 */}
              {type === "HTTP" && (
                <div className="space-y-3 rounded-xl border border-blue-500/20 bg-blue-50/30 p-4 dark:border-blue-500/20 dark:bg-blue-950/20">
                  <div className="flex items-center gap-2 text-xs font-semibold text-blue-700 dark:text-blue-300">
                    <Globe className="size-4" />
                    <span>HTTP 接口配置</span>
                  </div>

                  <div className="flex gap-2">
                    <select
                      value={httpMethod}
                      onChange={(e) =>
                        setHttpMethod(e.target.value as HttpConfig["method"])
                      }
                      className="w-24 rounded-lg border border-zinc-300 bg-white px-2.5 py-2 text-xs font-bold text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                    >
                      <option value="GET">GET</option>
                      <option value="POST">POST</option>
                      <option value="PUT">PUT</option>
                      <option value="DELETE">DELETE</option>
                      <option value="PATCH">PATCH</option>
                    </select>
                    <input
                      type="text"
                      value={httpUrl}
                      onChange={(e) => setHttpUrl(e.target.value)}
                      placeholder="https://api.example.com/v1/resource?query={{param}}"
                      className="flex-1 rounded-lg border border-zinc-300 bg-white px-3 py-2 text-xs font-mono text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>

                  {/* 鉴权配置 */}
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <div className="space-y-1">
                      <span className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                        认证方式 (Authentication)
                      </span>
                      <select
                        value={authType}
                        onChange={(e) =>
                          setAuthType(
                            e.target.value as
                              | "NONE"
                              | "BEARER"
                              | "API_KEY"
                              | "BASIC",
                          )
                        }
                        className="w-full rounded-lg border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                      >
                        <option value="NONE">无认证 (None)</option>
                        <option value="BEARER">
                          Bearer Token (Authorization)
                        </option>
                        <option value="API_KEY">API Key (自定义请求头)</option>
                        <option value="BASIC">Basic Auth</option>
                      </select>
                    </div>

                    {authType !== "NONE" && (
                      <div className="space-y-1">
                        <span className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                          {authType === "API_KEY"
                            ? "Header 名称 & Token 密钥"
                            : "Token 密钥 (AES 加密存储)"}
                        </span>
                        <div className="flex gap-1.5">
                          {authType === "API_KEY" && (
                            <input
                              type="text"
                              value={authHeader}
                              onChange={(e) => setAuthHeader(e.target.value)}
                              placeholder="X-API-Key"
                              className="w-28 rounded-lg border border-zinc-200 bg-white px-2 py-1.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                            />
                          )}
                          <input
                            type="password"
                            value={authToken}
                            onChange={(e) => setAuthToken(e.target.value)}
                            placeholder="输入密钥（落库自动加密）"
                            className="flex-1 rounded-lg border border-zinc-200 bg-white px-3 py-1.5 text-xs font-mono text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                          />
                        </div>
                      </div>
                    )}
                  </div>

                  {(httpMethod === "POST" ||
                    httpMethod === "PUT" ||
                    httpMethod === "PATCH") && (
                    <div className="space-y-1">
                      <span className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                        JSON 请求体模板 (Body Template)
                      </span>
                      <textarea
                        value={httpBody}
                        onChange={(e) => setHttpBody(e.target.value)}
                        rows={3}
                        placeholder='{"query": "{{query}}", "limit": 10}'
                        className="w-full rounded-lg border border-zinc-300 bg-zinc-950 p-2.5 font-mono text-xs text-emerald-400 dark:border-zinc-700"
                      />
                    </div>
                  )}
                </div>
              )}

              {type === "SCRIPT" && (
                <div className="space-y-3 rounded-xl border border-emerald-500/20 bg-emerald-50/30 p-4 dark:border-emerald-500/20 dark:bg-emerald-950/20">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                      <Code className="size-4" />
                      <span>脚本沙箱配置</span>
                    </div>
                    <select
                      value={scriptLang}
                      onChange={(e) =>
                        setScriptLang(e.target.value as "python" | "javascript")
                      }
                      className="rounded-lg border border-zinc-300 bg-white px-2.5 py-1 text-xs font-semibold text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                    >
                      <option value="python">Python 3</option>
                      <option value="javascript">JavaScript (Node.js)</option>
                    </select>
                  </div>

                  <div className="space-y-1">
                    <p className="text-[10px] text-zinc-500 dark:text-zinc-400">
                      提示：参数将作为全局字典 <code>params</code>
                      （Python）或对象 <code>params</code>
                      （JS）预置就绪，标准输出 <code>stdout</code>{" "}
                      即为工具返回结果。
                    </p>
                    <textarea
                      value={scriptCode}
                      onChange={(e) => setScriptCode(e.target.value)}
                      rows={8}
                      className="w-full rounded-lg border border-zinc-300 bg-zinc-950 p-3 font-mono text-xs text-emerald-400 dark:border-zinc-700"
                    />
                  </div>
                </div>
              )}

              {type === "PROMPT" && (
                <div className="space-y-3 rounded-xl border border-purple-500/20 bg-purple-50/30 p-4 dark:border-purple-500/20 dark:bg-purple-950/20">
                  <div className="flex items-center gap-2 text-xs font-semibold text-purple-700 dark:text-purple-300">
                    <MessageSquare className="size-4" />
                    <span>Prompt 模板配置</span>
                  </div>

                  <div className="space-y-1">
                    <span className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                      系统预置提示词 (System Prompt)
                    </span>
                    <input
                      type="text"
                      value={systemPrompt}
                      onChange={(e) => setSystemPrompt(e.target.value)}
                      className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>

                  <div className="space-y-1">
                    <span className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400">
                      提示词模板 (支持 {"{{param}}"} 插值)
                    </span>
                    <textarea
                      value={promptTemplate}
                      onChange={(e) => setPromptTemplate(e.target.value)}
                      rows={5}
                      className="w-full rounded-lg border border-zinc-300 bg-white p-2.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {activeTab === "schema" && (
            <SchemaBuilder value={schemaJson} onChange={setSchemaJson} />
          )}

          {activeTab === "test" && (
            <CustomToolTester tool={buildCurrentToolPayload()} />
          )}
        </div>

        {/* 弹窗底部操作 */}
        <div className="flex items-center justify-between border-t border-zinc-200/80 bg-zinc-50/50 px-6 py-3.5 dark:border-zinc-800/80 dark:bg-zinc-900/50">
          <div className="flex items-center gap-2">
            <span className="flex size-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-[11px] text-zinc-500">
              支持热加载与多租户隔离
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-zinc-200 bg-white px-4 py-2 text-xs font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700 transition-colors"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleSave}
              disabled={loading}
              className="flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 px-4 py-2 text-xs font-semibold text-white shadow-md shadow-indigo-500/20 hover:from-indigo-600 hover:to-purple-700 transition-all disabled:opacity-50"
            >
              <Save className="size-3.5" />
              <span>
                {loading ? "保存中..." : isEdit ? "保存更改" : "创建工具"}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
