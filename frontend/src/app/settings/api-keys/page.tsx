"use client";

import {
  AlertCircle,
  ArrowLeft,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  KeyRound,
  Loader2,
  Plus,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Trash2,
  Wallet,
  X,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  type ApiKeyItem,
  type ApiKeyTestResult,
  deleteApiKey,
  fetchApiKeys,
  saveApiKey,
  testApiKey,
} from "@/lib/api-key-api";

const PRESET_PROVIDERS = [
  {
    id: "openai",
    name: "OpenAI",
    description: "GPT-4o, GPT-4o-mini, O1 等旗舰模型",
    iconColor: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
    docsUrl: "https://platform.openai.com/api-keys",
    placeholder: "sk-proj-...",
  },
  {
    id: "deepseek",
    name: "DeepSeek",
    description: "DeepSeek-V3, DeepSeek-R1 推理模型（性价比旗舰）",
    iconColor: "text-blue-400 bg-blue-500/10 border-blue-500/20",
    docsUrl: "https://platform.deepseek.com/api_keys",
    placeholder: "sk-...",
  },
  {
    id: "anthropic",
    name: "Anthropic Claude",
    description: "Claude 3.5 Sonnet, Claude 3.5 Haiku 模型",
    iconColor: "text-amber-400 bg-amber-500/10 border-amber-500/20",
    docsUrl: "https://console.anthropic.com/settings/keys",
    placeholder: "sk-ant-...",
  },
  {
    id: "google",
    name: "Google Gemini",
    description: "Gemini 2.5 Pro, Gemini 2.5 Flash 超长上下文",
    iconColor: "text-purple-400 bg-purple-500/10 border-purple-500/20",
    docsUrl: "https://aistudio.google.com/app/apikey",
    placeholder: "AIzaSy...",
  },
  {
    id: "qwen",
    name: "通义千问 (DashScope)",
    description: "Qwen-Max, Qwen-Plus, Qwen-Turbo 系列",
    iconColor: "text-cyan-400 bg-cyan-500/10 border-cyan-500/20",
    docsUrl: "https://dashscope.console.aliyun.com/apiKey",
    placeholder: "sk-...",
  },
  {
    id: "qianfan",
    name: "百度千帆 (文心一言)",
    description: "ERNIE-4.0, ERNIE-Speed 等系列",
    iconColor: "text-indigo-400 bg-indigo-500/10 border-indigo-500/20",
    docsUrl: "https://console.bce.baidu.com/qianfan/ais/console/onlineService",
    placeholder: "bce-v3/...",
  },
];

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<
    Record<string, ApiKeyTestResult>
  >({});
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // 弹窗状态
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedProvider, setSelectedProvider] = useState("openai");
  const [inputKey, setInputKey] = useState("");
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  const loadKeys = useCallback(async () => {
    try {
      setLoading(true);
      const data = await fetchApiKeys();
      setKeys(data);
    } catch (e) {
      console.error("加载 API Key 列表失败", e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadKeys();
  }, [loadKeys]);

  const handleTestKey = async (id: string) => {
    try {
      setTestingId(id);
      const res = await testApiKey(id);
      setTestResults((prev) => ({ ...prev, [id]: res }));
      setKeys((prev) =>
        prev.map((k) =>
          k.id === id
            ? {
                ...k,
                status: res.valid ? "ACTIVE" : "INVALID",
                balance: res.balance || k.balance,
                errorMessage: res.message,
              }
            : k,
        ),
      );
    } catch (e: unknown) {
      const err = e instanceof Error ? e.message : String(e);
      setTestResults((prev) => ({
        ...prev,
        [id]: { valid: false, status: "INVALID", message: err },
      }));
    } finally {
      setTestingId(null);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("确定要删除该 API Key 吗？删除后将回退使用环境变量配置。")) {
      return;
    }
    try {
      await deleteApiKey(id);
      setKeys((prev) => prev.filter((k) => k.id !== id));
    } catch (e) {
      console.error("删除 API Key 失败", e);
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputKey.trim()) {
      setErrorMsg("请输入 API Key");
      return;
    }
    try {
      setSaving(true);
      setErrorMsg("");
      await saveApiKey(selectedProvider, inputKey.trim());
      setModalOpen(false);
      setInputKey("");
      await loadKeys();
    } catch (e: unknown) {
      const err = e instanceof Error ? e.message : String(e);
      setErrorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6 md:p-10 font-sans">
      <div className="max-w-5xl mx-auto space-y-8">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-800/80 pb-6">
          <div className="space-y-1">
            <div className="flex items-center gap-3">
              <Link
                href="/"
                className="flex size-8 items-center justify-center rounded-lg border border-zinc-800 bg-zinc-900 text-zinc-400 hover:text-zinc-100 hover:border-zinc-700 transition-colors"
                title="返回对话"
              >
                <ArrowLeft className="size-4" />
              </Link>
              <div className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-xl bg-violet-500/20 text-violet-400 border border-violet-500/30">
                  <KeyRound className="size-4" />
                </span>
                <h1 className="text-xl font-bold tracking-tight text-zinc-100">
                  API Key 管理面板
                </h1>
              </div>
            </div>
            <p className="text-xs text-zinc-400 pl-11">
              运行时为各大 AI 模型供应商配置自定义 API Key。Key 值经 AES-256-GCM
              强加密存储并在界面脱敏展示。
            </p>
          </div>

          <button
            type="button"
            onClick={() => {
              setSelectedProvider("openai");
              setInputKey("");
              setErrorMsg("");
              setModalOpen(true);
            }}
            className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-violet-600 to-indigo-600 px-4 py-2.5 text-xs font-semibold text-white shadow-lg shadow-violet-500/20 hover:from-violet-500 hover:to-indigo-500 transition-all duration-200"
          >
            <Plus className="size-4" />
            <span>配置新 Key</span>
          </button>
        </div>

        {/* Info Banner */}
        <div className="flex items-start gap-3 rounded-2xl border border-indigo-500/20 bg-indigo-950/20 p-4 text-xs text-indigo-300">
          <ShieldCheck className="size-5 shrink-0 text-indigo-400 mt-0.5" />
          <div className="space-y-1">
            <p className="font-semibold text-indigo-200">
              运行时优先级与安全保障
            </p>
            <p className="text-indigo-300/80 leading-relaxed">
              1. 运行时配置的 Key
              享有最高优先级，未配置的供应商自动回退至服务端的 <code>.env</code>{" "}
              全局密钥。
              <br />
              2. 所有 Key
              均由多租户受信任上下文隔离，仅当前用户可见，防止任何越权访问。
            </p>
          </div>
        </div>

        {/* Key List Section */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-zinc-300">
              已配置的 API Key 列表
            </h2>
            <button
              type="button"
              onClick={loadKeys}
              className="flex items-center gap-1.5 text-xs text-zinc-400 hover:text-zinc-200 transition-colors"
            >
              <RefreshCw
                className={`size-3.5 ${loading ? "animate-spin" : ""}`}
              />
              <span>刷新</span>
            </button>
          </div>

          {loading && keys.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 rounded-2xl border border-zinc-800/80 bg-zinc-900/40">
              <Loader2 className="size-6 text-violet-400 animate-spin mb-2" />
              <span className="text-xs text-zinc-400">
                正在加载 API Key 配置...
              </span>
            </div>
          ) : keys.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 rounded-2xl border border-dashed border-zinc-800 bg-zinc-900/20 text-center space-y-3">
              <div className="flex size-12 items-center justify-center rounded-2xl bg-zinc-800/80 text-zinc-400">
                <KeyRound className="size-6" />
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium text-zinc-300">
                  暂无运行时自定义 Key
                </p>
                <p className="text-xs text-zinc-500">
                  当前对话请求正使用服务端的默认环境变量密钥。
                </p>
              </div>
              <button
                type="button"
                onClick={() => setModalOpen(true)}
                className="mt-2 text-xs font-semibold text-violet-400 hover:text-violet-300"
              >
                + 立即添加第一个自定义 Key
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {keys.map((k) => {
                const preset = PRESET_PROVIDERS.find(
                  (p) => p.id === k.provider,
                );
                const testRes = testResults[k.id];
                const isTesting = testingId === k.id;

                return (
                  <div
                    key={k.id}
                    className="flex flex-col justify-between rounded-2xl border border-zinc-800 bg-zinc-900/60 p-4 space-y-4 hover:border-zinc-700 transition-all shadow-sm"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div
                          className={`flex size-10 items-center justify-center rounded-xl font-bold font-mono text-sm border ${preset?.iconColor || "text-zinc-400 bg-zinc-800 border-zinc-700"}`}
                        >
                          {k.provider.slice(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-semibold text-zinc-100">
                              {preset?.name || k.provider}
                            </span>
                            <span
                              className={`rounded-full px-2 py-0.5 text-[10px] font-semibold border ${
                                k.status === "ACTIVE"
                                  ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                                  : k.status === "INVALID"
                                    ? "bg-rose-500/10 text-rose-400 border-rose-500/20"
                                    : "bg-amber-500/10 text-amber-400 border-amber-500/20"
                              }`}
                            >
                              {k.status === "ACTIVE"
                                ? "可用"
                                : k.status === "INVALID"
                                  ? "失效"
                                  : "未测试"}
                            </span>
                          </div>
                          <p className="text-[11px] text-zinc-400">
                            {preset?.description || "自定义模型供应商"}
                          </p>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleDelete(k.id)}
                        className="flex size-7 items-center justify-center rounded-lg text-zinc-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                        title="删除此 Key"
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>

                    {/* Key & Balance View */}
                    <div className="rounded-xl border border-zinc-800/80 bg-black/40 p-3 space-y-2">
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-zinc-500 font-mono text-[11px]">
                          Key:
                        </span>
                        <div className="flex items-center gap-2">
                          <code className="font-mono text-xs text-zinc-300">
                            {k.maskedKey}
                          </code>
                          <button
                            type="button"
                            onClick={() => handleCopy(k.id, k.maskedKey)}
                            className="text-zinc-400 hover:text-zinc-200 transition-colors"
                            title="复制脱敏 Key"
                          >
                            {copiedId === k.id ? (
                              <Check className="size-3 text-emerald-400" />
                            ) : (
                              <Copy className="size-3" />
                            )}
                          </button>
                        </div>
                      </div>

                      {k.balance && (
                        <div className="flex items-center justify-between text-xs pt-1 border-t border-zinc-800/60">
                          <span className="text-zinc-500 flex items-center gap-1">
                            <Wallet className="size-3 text-emerald-400" />
                            <span>账户余额:</span>
                          </span>
                          <span className="font-semibold text-emerald-400 font-mono">
                            {k.balance}
                          </span>
                        </div>
                      )}

                      {testRes && !testRes.valid && (
                        <div className="flex items-center gap-1.5 text-[11px] text-rose-400 pt-1 border-t border-zinc-800/60">
                          <AlertCircle className="size-3.5 shrink-0" />
                          <span className="truncate">{testRes.message}</span>
                        </div>
                      )}
                      {testRes?.valid && (
                        <div className="flex items-center gap-1.5 text-[11px] text-emerald-400 pt-1 border-t border-zinc-800/60">
                          <CheckCircle2 className="size-3.5 shrink-0" />
                          <span>连通性正常</span>
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div className="flex items-center justify-between pt-1">
                      <span className="text-[10px] text-zinc-500">
                        更新于 {new Date(k.updatedAt).toLocaleDateString()}
                      </span>
                      <button
                        type="button"
                        onClick={() => handleTestKey(k.id)}
                        disabled={isTesting}
                        className="flex items-center gap-1.5 rounded-lg border border-zinc-700 bg-zinc-800/80 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-700 transition-colors disabled:opacity-50"
                      >
                        {isTesting ? (
                          <>
                            <Loader2 className="size-3.5 animate-spin" />
                            <span>测试中...</span>
                          </>
                        ) : (
                          <>
                            <Sparkles className="size-3.5 text-violet-400" />
                            <span>测试连通性</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Quick Setup Cards for Available Providers */}
        <div className="space-y-4 pt-4 border-t border-zinc-800/80">
          <h2 className="text-sm font-semibold text-zinc-300">
            支持的供应商快捷配置
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {PRESET_PROVIDERS.map((p) => {
              const configured = keys.some((k) => k.provider === p.id);
              return (
                <div
                  key={p.id}
                  className="flex flex-col justify-between rounded-xl border border-zinc-800/70 bg-zinc-900/30 p-3.5 space-y-3 hover:border-zinc-700 transition-all"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-zinc-200">
                        {p.name}
                      </span>
                      <a
                        href={p.docsUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="flex items-center gap-1 text-[10px] text-zinc-400 hover:text-zinc-200 transition-colors"
                      >
                        <span>获取 Key</span>
                        <ExternalLink className="size-3" />
                      </a>
                    </div>
                    <p className="text-[11px] text-zinc-400 line-clamp-2">
                      {p.description}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setSelectedProvider(p.id);
                      setInputKey("");
                      setErrorMsg("");
                      setModalOpen(true);
                    }}
                    className={`w-full rounded-lg py-1.5 text-xs font-semibold transition-colors ${
                      configured
                        ? "border border-zinc-700 bg-zinc-800 text-zinc-300 hover:bg-zinc-700"
                        : "bg-violet-600 text-white hover:bg-violet-500"
                    }`}
                  >
                    {configured ? "修改配置" : "+ 配置 Key"}
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Add / Edit Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-900 p-6 shadow-2xl space-y-5">
            <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
              <div className="flex items-center gap-2">
                <span className="flex size-7 items-center justify-center rounded-lg bg-violet-500/20 text-violet-400">
                  <KeyRound className="size-4" />
                </span>
                <h3 className="text-sm font-bold text-zinc-100">
                  配置 API Key
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="rounded-lg p-1 text-zinc-400 hover:text-zinc-200 transition-colors"
              >
                <X className="size-4" />
              </button>
            </div>

            <form onSubmit={handleSave} className="space-y-4">
              <div className="space-y-1.5">
                <label
                  htmlFor="provider-select"
                  className="text-xs font-medium text-zinc-300"
                >
                  选择模型供应商
                </label>
                <select
                  id="provider-select"
                  value={selectedProvider}
                  onChange={(e) => setSelectedProvider(e.target.value)}
                  className="w-full rounded-xl border border-zinc-800 bg-zinc-950 px-3 py-2 text-xs text-zinc-100 focus:outline-hidden focus:border-violet-500"
                >
                  {PRESET_PROVIDERS.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.id})
                    </option>
                  ))}
                  <option value="custom">其他 OpenAI 兼容供应商</option>
                </select>
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="key-input"
                  className="text-xs font-medium text-zinc-300"
                >
                  API Key 密钥
                </label>
                <input
                  id="key-input"
                  type="password"
                  value={inputKey}
                  onChange={(e) => setInputKey(e.target.value)}
                  placeholder={
                    PRESET_PROVIDERS.find((p) => p.id === selectedProvider)
                      ?.placeholder || "sk-..."
                  }
                  className="w-full rounded-xl border border-zinc-800 bg-zinc-950 px-3 py-2 text-xs text-zinc-100 focus:outline-hidden focus:border-violet-500 font-mono"
                  required
                />
                <p className="text-[10px] text-zinc-500">
                  密钥将使用 AES-256 强加密保存，仅在调用时在内存中解密。
                </p>
              </div>

              {errorMsg && (
                <div className="flex items-center gap-1.5 rounded-lg border border-rose-500/20 bg-rose-950/20 p-2.5 text-xs text-rose-300">
                  <AlertCircle className="size-4 shrink-0 text-rose-400" />
                  <span>{errorMsg}</span>
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-zinc-800">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-400 hover:text-zinc-200 transition-colors"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex items-center gap-1.5 rounded-xl bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-500 transition-colors disabled:opacity-50"
                >
                  {saving && <Loader2 className="size-3.5 animate-spin" />}
                  <span>{saving ? "保存中..." : "保存 Key"}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
