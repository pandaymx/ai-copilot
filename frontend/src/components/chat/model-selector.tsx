"use client";

import {
  AlertTriangle,
  Bot,
  Brain,
  Check,
  ChevronDown,
  ChevronRight,
  Cpu,
  RefreshCw,
  Sparkles,
  Zap,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export interface BackendModelEntry {
  id: string;
  displayName: string;
  description: string;
  badge?: string;
  tags?: string[];
  maxContextTokens?: number;
  status?: "UP" | "DOWN" | "HALF_OPEN";
  healthy?: boolean;
}

export interface BackendProviderEntry {
  id: string;
  displayName: string;
  tier?: string;
  protocol?: string;
  defaultModelId?: string;
  models: BackendModelEntry[];
}

export interface SelectedModel {
  provider: string;
  model: string;
}

interface ModelSelectorProps {
  value: SelectedModel;
  onChange: (selected: SelectedModel) => void;
  providers?: BackendProviderEntry[];
}

const DEFAULT_PROVIDERS: BackendProviderEntry[] = [
  {
    id: "deepseek",
    displayName: "DeepSeek",
    tier: "FIRST_CLASS",
    protocol: "OPENAI",
    defaultModelId: "deepseek-chat",
    models: [
      {
        id: "deepseek-chat",
        displayName: "DeepSeek Chat",
        description: "DeepSeek 高性价比通用语言模型，极具性价比",
        badge: "推荐",
        tags: ["chat"],
        maxContextTokens: 32768,
        status: "UP",
        healthy: true,
      },
    ],
  },
  {
    id: "openai",
    displayName: "OpenAI",
    tier: "FIRST_CLASS",
    protocol: "OPENAI",
    defaultModelId: "gpt-4o",
    models: [
      {
        id: "gpt-4o",
        displayName: "GPT-4o",
        description: "OpenAI 多模态旗舰模型，强表达与代码构建能力",
        badge: "全能",
        tags: ["multimodal"],
        maxContextTokens: 128000,
        status: "UP",
        healthy: true,
      },
    ],
  },
  {
    id: "google",
    displayName: "Google Gemini",
    tier: "FIRST_CLASS",
    protocol: "GENAI",
    defaultModelId: "gemini-3.6-flash",
    models: [
      {
        id: "gemini-3.6-flash",
        displayName: "Gemini 3.6 Flash",
        description: "Google 最新一代轻量高速多模态推理模型",
        badge: "极速",
        tags: ["multimodal"],
        maxContextTokens: 1048576,
        status: "UP",
        healthy: true,
      },
      {
        id: "gemini-3.5-flash",
        displayName: "Gemini 3.5 Flash",
        description: "Google 稳定版轻量多模态模型",
        tags: ["multimodal"],
        maxContextTokens: 1048576,
        status: "UP",
        healthy: true,
      },
      {
        id: "gemini-3.1-pro-preview",
        displayName: "Gemini 3.1 Pro",
        description: "Google 旗舰深度推理多模态预览版模型",
        badge: "预览",
        tags: ["multimodal"],
        maxContextTokens: 2097152,
        status: "UP",
        healthy: true,
      },
    ],
  },
  {
    id: "anthropic",
    displayName: "Anthropic Claude",
    tier: "FIRST_CLASS",
    protocol: "ANTHROPIC",
    defaultModelId: "claude-sonnet-4",
    models: [
      {
        id: "claude-sonnet-4",
        displayName: "Claude Sonnet 4",
        description: "Anthropic 旗舰高可信度长文本分析模型",
        badge: "强力",
        tags: ["chat"],
        maxContextTokens: 200000,
        status: "UP",
        healthy: true,
      },
    ],
  },
  {
    id: "ollama",
    displayName: "Ollama (本地)",
    tier: "FIRST_CLASS",
    protocol: "OLLAMA",
    defaultModelId: "llama3",
    models: [
      {
        id: "llama3",
        displayName: "Llama 3",
        description: "本地私有化部署开源模型，免联网无隐私风险",
        badge: "本地",
        tags: ["local"],
        maxContextTokens: 8192,
        status: "UP",
        healthy: true,
      },
    ],
  },
];

function getProviderIconAndAccent(providerId: string): {
  icon: typeof Sparkles;
  accent: string;
} {
  const p = providerId.toLowerCase();
  if (p.includes("deepseek")) {
    return { icon: Brain, accent: "from-indigo-500 to-purple-600" };
  }
  if (p.includes("openai")) {
    return { icon: Sparkles, accent: "from-blue-500 to-cyan-500" };
  }
  if (p.includes("google") || p.includes("gemini")) {
    return { icon: Zap, accent: "from-emerald-500 to-teal-500" };
  }
  if (p.includes("anthropic") || p.includes("claude")) {
    return { icon: Bot, accent: "from-amber-500 to-orange-500" };
  }
  if (p.includes("ollama")) {
    return { icon: Cpu, accent: "from-teal-500 to-emerald-600" };
  }
  return { icon: Bot, accent: "from-slate-500 to-zinc-600" };
}

export function ModelSelector({
  value,
  onChange,
  providers: initialProviders,
}: ModelSelectorProps) {
  const [open, setOpen] = useState(false);
  const [catalog, setCatalog] = useState<BackendProviderEntry[]>(
    initialProviders && initialProviders.length > 0
      ? initialProviders
      : DEFAULT_PROVIDERS,
  );

  // 一级结构：选中的供应商
  const [activeProviderId, setActiveProviderId] = useState<string>(
    value.provider || catalog[0]?.id || "deepseek",
  );

  const containerRef = useRef<HTMLDivElement>(null);

  // 自定义模型输入（允许在选定供应商下自由指定任意模型名）
  const [customModelInput, setCustomModelInput] = useState<string>(
    catalog
      .find((p) => p.id === value.provider)
      ?.models.some((m) => m.id === value.model)
      ? ""
      : value.model || "",
  );
  const isCustomSelected =
    !!value.model &&
    !(
      catalog
        .find((p) => p.id === value.provider)
        ?.models.some((m) => m.id === value.model) ?? false
    );

  // 轻量获取健康诊断
  const fetchHealth = useCallback(() => {
    fetch("/api/models/health")
      .then((res) => (res.ok ? res.json() : null))
      .then(
        (data: { models?: Record<string, "UP" | "DOWN" | "HALF_OPEN"> }) => {
          if (!data?.models) return;
          setCatalog((prevCatalog) =>
            prevCatalog.map((provider) => ({
              ...provider,
              models: provider.models.map((model) => {
                const key = `${provider.id}:${model.id}`;
                const status = data.models?.[key] ?? model.status ?? "UP";
                return {
                  ...model,
                  status,
                  healthy: status !== "DOWN",
                };
              }),
            })),
          );
        },
      )
      .catch(() => {
        // 捕获请求异常
      });
  }, []);

  // 尝试从后端获取动态模型清单 /api/models
  useEffect(() => {
    if (initialProviders && initialProviders.length > 0) return;
    let isMounted = true;
    fetch("/api/models")
      .then((res) => {
        if (!res.ok) throw new Error("Failed to fetch models");
        return res.json();
      })
      .then(
        (data: {
          providers?: BackendProviderEntry[];
          defaultProvider?: string;
          defaultModel?: string;
        }) => {
          if (isMounted && data.providers && data.providers.length > 0) {
            setCatalog(data.providers);

            // 校验当前选中的 (value.provider) 是否存在于后端返回的可用供应商清单中。
            // 若不存在（例如默认配置了 deepseek，但后端因缺少 API Key 仅注册了 ollama），
            // 自动校准切换为可用目录中的默认供应商与模型。
            const currentProviderExists = data.providers.some(
              (p) => p.id === value.provider,
            );
            if (!currentProviderExists) {
              const targetProvider =
                data.providers.find((p) => p.id === data.defaultProvider) ||
                data.providers[0];
              const targetModelId =
                (data.defaultProvider === targetProvider.id
                  ? data.defaultModel
                  : undefined) ||
                targetProvider.defaultModelId ||
                targetProvider.models[0]?.id ||
                "";
              if (targetProvider && targetModelId) {
                onChange({
                  provider: targetProvider.id,
                  model: targetModelId,
                });
              }
            }
          }
        },
      )
      .catch(() => {
        // 后端无法连接时静默回退默认目录
      });
    return () => {
      isMounted = false;
    };
  }, [initialProviders, value.provider, onChange]);

  // 低频轮询 + 下拉框打开/页面 Focus 时静默刷新健康数据
  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 45_000);
    const handleFocus = () => fetchHealth();
    window.addEventListener("focus", handleFocus);
    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", handleFocus);
    };
  }, [fetchHealth]);

  // 当 open 展开时静默刷新健康诊断
  useEffect(() => {
    if (open) {
      fetchHealth();
    }
  }, [open, fetchHealth]);

  // 当外部选中的 provider 变化或 open 展开时校准 activeProviderId
  useEffect(() => {
    if (open) {
      const match = catalog.find((p) => p.id === value.provider);
      if (match) {
        setActiveProviderId(match.id);
      } else if (catalog.length > 0) {
        setActiveProviderId(catalog[0].id);
      }
    }
  }, [open, value.provider, catalog]);

  // 点击外部关闭
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const activeProviderObj =
    catalog.find((p) => p.id === activeProviderId) ?? catalog[0];

  // 查找当前主界面选中的 Provider 与 Model 信息
  const currentProviderObj = catalog.find((p) => p.id === value.provider);
  const currentModelObj = currentProviderObj?.models.find(
    (m) => m.id === value.model,
  );
  const isCurrentModelDown =
    currentModelObj?.status === "DOWN" || currentModelObj?.healthy === false;

  const activeProviderAccent = getProviderIconAndAccent(
    value.provider || "deepseek",
  );
  const TriggerIcon = activeProviderAccent.icon;

  return (
    <div className="relative inline-block text-left" ref={containerRef}>
      {/* 选中的模型在当前属于 DOWN 时展现防熔断警告提示 */}
      {isCurrentModelDown && (
        <div className="mb-2 flex items-center gap-2 rounded-xl border border-amber-500/30 bg-amber-500/10 px-3 py-1.5 text-xs text-amber-700 dark:bg-amber-500/15 dark:text-amber-300 shadow-2xs backdrop-blur-md">
          <AlertTriangle className="size-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
          <span>
            选中的模型（{currentModelObj?.displayName ?? value.model}
            ）响应受阻，发送请求将自动降级回复
          </span>
        </div>
      )}

      {/* 选择器触发展开按钮 */}
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label="选择 AI 模型"
        className={cn(
          "group inline-flex items-center gap-2 rounded-xl border px-3 py-1.5 text-xs font-semibold shadow-xs backdrop-blur-md transition-all duration-200",
          open
            ? "border-indigo-500/80 bg-white/90 ring-2 ring-indigo-500/20 dark:border-indigo-500/80 dark:bg-zinc-900/90"
            : isCurrentModelDown
              ? "border-amber-500/60 bg-amber-500/10 text-amber-800 dark:border-amber-500/40 dark:bg-amber-950/30 dark:text-amber-300"
              : "border-zinc-200/80 bg-white/80 text-zinc-800 hover:border-zinc-300 hover:bg-white dark:border-zinc-800/80 dark:bg-zinc-900/80 dark:text-zinc-200 dark:hover:border-zinc-700",
        )}
      >
        <span
          className={cn(
            "flex size-5 items-center justify-center rounded-lg bg-gradient-to-tr text-white shadow-xs transition-transform duration-200 group-hover:scale-105",
            activeProviderAccent.accent,
          )}
        >
          <TriggerIcon className="size-3" />
        </span>
        <div className="flex items-center gap-1.5">
          <span className="font-bold">
            {currentProviderObj?.displayName ?? value.provider}
          </span>
          <span className="text-zinc-400 dark:text-zinc-500">/</span>
          <span className="font-medium text-zinc-600 dark:text-zinc-300">
            {isCustomSelected
              ? `自定义 (${value.model})`
              : currentModelObj?.displayName || value.model}
          </span>
          {/* 健康指示灯 */}
          {isCurrentModelDown ? (
            <span className="flex items-center gap-1 text-[10px] font-semibold text-amber-600 dark:text-amber-400">
              <span className="size-2 rounded-full bg-amber-500 animate-pulse" />
              <span>降级中</span>
            </span>
          ) : (
            <span className="size-1.5 rounded-full bg-emerald-500" />
          )}
        </div>
        <ChevronDown
          className={cn(
            "size-3.5 text-zinc-400 transition-transform duration-200",
            open && "rotate-180 text-indigo-500 dark:text-indigo-400",
          )}
        />
      </button>

      {/* 下拉弹出面板 */}
      {open && (
        <div className="absolute left-0 bottom-full mb-2 z-50 w-[540px] max-w-[90vw] overflow-hidden rounded-2xl border border-zinc-200/80 bg-white/95 p-2 shadow-2xl shadow-indigo-500/10 backdrop-blur-xl dark:border-zinc-800/80 dark:bg-zinc-900/95 dark:shadow-none animate-in zoom-in-95 fade-in duration-150">
          <div className="grid grid-cols-12 gap-2 h-80">
            {/* 左栏：一级结构 (供应商 Provider 列表) */}
            <div className="col-span-5 border-r border-zinc-100 pr-1.5 space-y-1 overflow-y-auto dark:border-zinc-800/60">
              <div className="flex items-center justify-between px-2 py-1 text-[10px] font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-wider">
                <span>1. AI 供应商</span>
                <button
                  type="button"
                  onClick={fetchHealth}
                  className="text-zinc-400 hover:text-indigo-600 transition-colors"
                  title="刷新健康诊断"
                >
                  <RefreshCw className="size-3" />
                </button>
              </div>
              {catalog.map((p) => {
                const isActive = p.id === activeProviderId;
                const pAccent = getProviderIconAndAccent(p.id);
                const Icon = pAccent.icon;
                const hasDownModel = p.models?.some(
                  (m) => m.status === "DOWN" || m.healthy === false,
                );

                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setActiveProviderId(p.id)}
                    className={cn(
                      "flex w-full items-center justify-between rounded-xl px-2.5 py-2 text-left text-xs transition-all duration-150",
                      isActive
                        ? "bg-gradient-to-r from-indigo-600 to-purple-600 font-semibold text-white shadow-md shadow-indigo-500/20"
                        : "text-zinc-700 hover:bg-zinc-100/80 dark:text-zinc-300 dark:hover:bg-zinc-800/60",
                    )}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <span
                        className={cn(
                          "flex size-6 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-xs",
                          pAccent.accent,
                        )}
                      >
                        <Icon className="size-3.5" />
                      </span>
                      <span className="truncate">{p.displayName}</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      {hasDownModel && (
                        <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
                      )}
                      <span
                        className={cn(
                          "rounded-md px-1.5 py-0.5 text-[10px] font-mono",
                          isActive
                            ? "bg-white/20 text-white"
                            : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400",
                        )}
                      >
                        {p.models?.length ?? 0}
                      </span>
                      <ChevronRight
                        className={cn(
                          "size-3 opacity-60",
                          isActive ? "text-white" : "text-zinc-400",
                        )}
                      />
                    </div>
                  </button>
                );
              })}
            </div>

            {/* 右栏：二级结构 (指定供应商下的模型卡片) */}
            <div className="col-span-7 min-h-0 h-full space-y-1.5 overflow-y-auto overscroll-contain pl-1">
              <div className="flex items-center justify-between px-1 py-1 text-[10px] font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-wider">
                <span>
                  2. 选择模型 ({activeProviderObj?.displayName ?? ""})
                </span>
              </div>

              {activeProviderObj?.models &&
              activeProviderObj.models.length > 0 ? (
                activeProviderObj.models.map((m) => {
                  const isSelectedModel =
                    value.provider === activeProviderObj.id &&
                    value.model === m.id;
                  const isModelDown =
                    m.status === "DOWN" || m.healthy === false;

                  return (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => {
                        onChange({
                          provider: activeProviderObj.id,
                          model: m.id,
                        });
                        setOpen(false);
                      }}
                      className={cn(
                        "group flex w-full items-start justify-between rounded-xl p-2.5 text-left border transition-all duration-150",
                        isSelectedModel
                          ? "border-indigo-500/80 bg-indigo-50/70 dark:border-indigo-500/60 dark:bg-indigo-950/40 shadow-xs"
                          : "border-zinc-200/60 bg-white/60 hover:border-indigo-500/40 hover:bg-zinc-50 dark:border-zinc-800/60 dark:bg-zinc-900/40 dark:hover:border-indigo-500/30 dark:hover:bg-zinc-900/80",
                      )}
                    >
                      <div className="space-y-1 min-w-0 flex-1 pr-2">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <span className="font-bold text-xs text-zinc-900 dark:text-zinc-100">
                            {m.displayName}
                          </span>

                          {/* 健康在线/降级指示 */}
                          {isModelDown ? (
                            <span className="flex items-center gap-1 rounded-md bg-amber-500/10 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 dark:bg-amber-950/50 dark:text-amber-300 border border-amber-500/20">
                              <span className="size-1.5 rounded-full bg-amber-500 animate-pulse" />
                              <span>不可用 (自动降级)</span>
                            </span>
                          ) : (
                            <span className="flex items-center gap-1 text-[10px] text-emerald-600 dark:text-emerald-400 font-medium">
                              <span className="size-1.5 rounded-full bg-emerald-500" />
                              <span>在线</span>
                            </span>
                          )}

                          {m.badge && (
                            <span className="rounded-md bg-indigo-500/10 dark:bg-indigo-400/15 px-1.5 py-0.5 text-[10px] font-semibold text-indigo-600 dark:text-indigo-400">
                              {m.badge}
                            </span>
                          )}
                        </div>

                        <p className="text-[11px] leading-relaxed text-zinc-500 dark:text-zinc-400 line-clamp-2">
                          {m.description}
                        </p>

                        {m.maxContextTokens && (
                          <div className="pt-0.5">
                            <span className="inline-flex items-center rounded-md bg-zinc-100 px-1.5 py-0.2 font-mono text-[9px] text-zinc-500 dark:bg-zinc-800/80 dark:text-zinc-400">
                              上下文: {Math.round(m.maxContextTokens / 1024)}k
                            </span>
                          </div>
                        )}
                      </div>

                      {isSelectedModel && (
                        <div className="flex size-5 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white shadow-xs dark:bg-indigo-500 mt-0.5">
                          <Check className="size-3 stroke-[3]" />
                        </div>
                      )}
                    </button>
                  );
                })
              ) : (
                <div className="flex flex-col items-center justify-center py-8 text-center text-xs text-zinc-400">
                  当前供应商暂无可用模型
                </div>
              )}

              {/* 自定义模型：允许直接输入该供应商下的任意模型名 */}
              <div className="mt-1.5 rounded-xl border border-dashed border-indigo-300/70 bg-indigo-50/40 p-2.5 dark:border-indigo-500/40 dark:bg-indigo-950/25">
                <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold text-indigo-600 dark:text-indigo-400 uppercase tracking-wider">
                  <Sparkles className="size-3" />
                  自定义模型
                </div>
                <div className="flex items-center gap-1.5">
                  <input
                    type="text"
                    value={customModelInput}
                    onChange={(e) => setCustomModelInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && customModelInput.trim()) {
                        onChange({
                          provider: activeProviderObj.id,
                          model: customModelInput.trim(),
                        });
                        setOpen(false);
                      }
                    }}
                    placeholder="输入模型 ID (如 gpt-4o-mini)"
                    className="flex-1 rounded-lg border border-zinc-200 bg-white px-2.5 py-1 text-xs text-zinc-900 placeholder:text-zinc-400 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                  />
                  <button
                    type="button"
                    onClick={() => {
                      if (customModelInput.trim()) {
                        onChange({
                          provider: activeProviderObj.id,
                          model: customModelInput.trim(),
                        });
                        setOpen(false);
                      }
                    }}
                    className="rounded-lg bg-indigo-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-indigo-700 dark:bg-indigo-500 transition-colors"
                  >
                    应用
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
