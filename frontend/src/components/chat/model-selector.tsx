"use client";

import {
  Bot,
  Brain,
  Check,
  ChevronDown,
  ChevronRight,
  Cpu,
  Layers,
  Sparkles,
  Zap,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export interface BackendModelEntry {
  id: string;
  displayName: string;
  description: string;
  badge?: string;
  tags?: string[];
  maxContextTokens?: number;
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
      },
      {
        id: "gemini-3.5-flash",
        displayName: "Gemini 3.5 Flash",
        description: "Google 稳定版轻量多模态模型",
        tags: ["multimodal"],
        maxContextTokens: 1048576,
      },
      {
        id: "gemini-3.1-pro-preview",
        displayName: "Gemini 3.1 Pro",
        description: "Google 旗舰深度推理多模态预览版模型",
        badge: "预览",
        tags: ["multimodal"],
        maxContextTokens: 2097152,
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

  // 尝试从后端获取动态模型清单 /api/models
  useEffect(() => {
    if (initialProviders && initialProviders.length > 0) return;
    let isMounted = true;
    fetch("/api/models")
      .then((res) => {
        if (!res.ok) throw new Error("Failed to fetch models");
        return res.json();
      })
      .then((data: { providers?: BackendProviderEntry[] }) => {
        if (isMounted && data.providers && data.providers.length > 0) {
          setCatalog(data.providers);
        }
      })
      .catch(() => {
        // 后端无法连接时静默回退默认目录
      });
    return () => {
      isMounted = false;
    };
  }, [initialProviders]);

  // 当外部选中的 provider 变化或 open 展开时校准 activeProviderId
  useEffect(() => {
    if (open) {
      const match = catalog.find((p) => p.id === value.provider);
      if (match) {
        setActiveProviderId(match.id);
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

  // 匹配当前选中的 供应商 与 模型 对象
  const currentProvider =
    catalog.find((p) => p.id === value.provider) ??
    catalog.find((p) => p.models.some((m) => m.id === value.model)) ??
    catalog[0];

  const currentModel =
    currentProvider?.models.find((m) => m.id === value.model) ??
    currentProvider?.models[0];

  const currentMeta = getProviderIconAndAccent(
    currentProvider?.id ?? "deepseek",
  );
  const CurrentIcon = currentMeta.icon;

  // 二级结构的 当前激活供应商 对象
  const activeProviderObj =
    catalog.find((p) => p.id === activeProviderId) ?? catalog[0];

  return (
    <div className="relative inline-block text-left" ref={containerRef}>
      {/* 按钮触发器：第一级供应商/第二级模型 */}
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className={cn(
          "group flex items-center gap-2 rounded-xl border border-zinc-200/80 bg-white/80 px-2.5 py-1.5 text-xs font-medium text-zinc-700 shadow-xs backdrop-blur transition-all duration-200 hover:border-indigo-500/40 hover:bg-white hover:shadow-sm dark:border-zinc-800/80 dark:bg-zinc-900/60 dark:text-zinc-200 dark:hover:border-indigo-500/50 dark:hover:bg-zinc-900",
          open &&
            "border-indigo-500 ring-2 ring-indigo-500/20 dark:border-indigo-500",
        )}
      >
        <span
          className={cn(
            "flex size-5 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-xs",
            currentMeta.accent,
          )}
        >
          <CurrentIcon className="size-3" />
        </span>
        <span className="font-semibold text-zinc-900 dark:text-zinc-100">
          {currentModel?.displayName || value.model}
        </span>
        <span className="text-[10px] text-zinc-400 dark:text-zinc-500">
          ({currentProvider?.displayName || value.provider})
        </span>
        {currentModel?.badge && (
          <span className="rounded bg-indigo-500/10 text-indigo-600 dark:bg-indigo-400/15 dark:text-indigo-400 px-1.5 py-0.5 text-[10px] font-medium">
            {currentModel.badge}
          </span>
        )}
        <ChevronDown
          className={cn(
            "size-3.5 text-zinc-400 transition-transform duration-200",
            open && "rotate-180 text-zinc-700 dark:text-zinc-200",
          )}
        />
      </button>

      {/* 二级联动下拉选择面板 */}
      {open && (
        <div className="absolute left-0 bottom-full mb-2 z-50 w-[480px] rounded-2xl border border-zinc-200/90 bg-white/95 p-3 shadow-2xl shadow-indigo-500/10 backdrop-blur-2xl transition-all dark:border-zinc-800/90 dark:bg-zinc-950/95 dark:shadow-none animate-in fade-in slide-in-from-bottom-2 duration-150 sm:w-[540px]">
          {/* 面板头部说明 */}
          <div className="flex items-center justify-between border-b border-zinc-100 pb-2.5 dark:border-zinc-800/60 px-1">
            <div className="flex items-center gap-2">
              <div className="flex size-5 items-center justify-center rounded-md bg-indigo-500/10 text-indigo-600 dark:bg-indigo-400/10 dark:text-indigo-400">
                <Layers className="size-3.5" />
              </div>
              <span className="text-xs font-bold tracking-tight text-zinc-800 dark:text-zinc-200">
                AI 模型选择器
              </span>
            </div>
            <span className="text-[10px] font-medium text-zinc-400 dark:text-zinc-500">
              双级架构 (1. 供应商 → 2. 具体模型)
            </span>
          </div>

          {/* 双级分栏联动主体 */}
          <div className="mt-2.5 grid grid-cols-12 gap-2.5 min-h-[260px] max-h-[340px]">
            {/* 左栏：一级结构 (供应商列表) */}
            <div className="col-span-5 space-y-1 border-r border-zinc-100 pr-2 dark:border-zinc-800/60 overflow-y-auto scrollbar-hidden">
              <div className="px-2 py-1 text-[10px] font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-wider">
                1. 供应商 ({catalog.length})
              </div>
              {catalog.map((p) => {
                const isActive = p.id === activeProviderId;
                const isCurrentlySelectedProvider =
                  p.id === currentProvider?.id;
                const meta = getProviderIconAndAccent(p.id);
                const Icon = meta.icon;

                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setActiveProviderId(p.id)}
                    className={cn(
                      "group flex w-full items-center justify-between rounded-xl px-2.5 py-2 text-left text-xs font-medium transition-all duration-150",
                      isActive
                        ? "bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-md shadow-indigo-500/20"
                        : "hover:bg-zinc-100 dark:hover:bg-zinc-900 text-zinc-700 dark:text-zinc-300",
                    )}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <span
                        className={cn(
                          "flex size-6 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-xs transition-transform group-hover:scale-105",
                          meta.accent,
                        )}
                      >
                        <Icon className="size-3.5" />
                      </span>
                      <span className="truncate font-semibold text-[12px]">
                        {p.displayName}
                      </span>
                    </div>

                    <div className="flex items-center gap-1">
                      {isCurrentlySelectedProvider && !isActive && (
                        <span className="size-1.5 rounded-full bg-indigo-500 dark:bg-indigo-400" />
                      )}
                      <span
                        className={cn(
                          "text-[10px] rounded-full px-1.5 py-0.2 font-mono",
                          isActive
                            ? "bg-white/20 text-white"
                            : "bg-zinc-100 dark:bg-zinc-800 text-zinc-400",
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
            <div className="col-span-7 space-y-1.5 overflow-y-auto scrollbar-hidden pl-1">
              <div className="flex items-center justify-between px-1 py-1 text-[10px] font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-wider">
                <span>
                  2. 可用模型 ({activeProviderObj?.displayName ?? ""})
                </span>
              </div>

              {activeProviderObj?.models &&
              activeProviderObj.models.length > 0 ? (
                activeProviderObj.models.map((m) => {
                  const isSelectedModel =
                    value.provider === activeProviderObj.id &&
                    value.model === m.id;

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
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
