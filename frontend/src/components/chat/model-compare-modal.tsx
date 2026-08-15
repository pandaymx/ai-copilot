"use client";

import {
  Bot,
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  Download,
  Gauge,
  Loader2,
  Play,
  Sparkles,
  Swords,
  Timer,
  X,
  Zap,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Markdown } from "@/components/chat/markdown";
import {
  type CompareChunk,
  type CompareTarget,
  compareModelsApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

interface ModelOption {
  provider: string;
  model: string;
  displayName: string;
}

const PRESET_MODELS: ModelOption[] = [
  { provider: "openai", model: "gpt-4o", displayName: "GPT-4o" },
  { provider: "openai", model: "gpt-4o-mini", displayName: "GPT-4o Mini" },
  {
    provider: "anthropic",
    model: "claude-3-5-sonnet",
    displayName: "Claude 3.5 Sonnet",
  },
  { provider: "deepseek", model: "deepseek-chat", displayName: "DeepSeek V3" },
  {
    provider: "deepseek",
    model: "deepseek-reasoner",
    displayName: "DeepSeek R1",
  },
  {
    provider: "google",
    model: "gemini-2.5-flash",
    displayName: "Gemini 2.5 Flash",
  },
  { provider: "ollama", model: "qwen2.5:7b", displayName: "Qwen 2.5 (本地)" },
];

interface ModelStreamState {
  text: string;
  thinking: string;
  ttftMs?: number;
  totalDurationMs?: number;
  tokensPerSecond?: number;
  tokensCount?: number;
  status: "idle" | "streaming" | "done" | "error";
  error?: string;
}

interface ModelCompareModalProps {
  open: boolean;
  onClose: () => void;
  initialPrompt?: string;
  conversationId?: string;
  onAdopt?: (content: string, provider: string, model: string) => void;
}

export function ModelCompareModal({
  open,
  onClose,
  initialPrompt = "",
  conversationId,
  onAdopt,
}: ModelCompareModalProps) {
  const [prompt, setPrompt] = useState(initialPrompt);
  const [targets, setTargets] = useState<CompareTarget[]>([
    { provider: "openai", model: "gpt-4o" },
    { provider: "deepseek", model: "deepseek-chat" },
  ]);
  const [comparing, setComparing] = useState(false);
  const [modelStates, setModelStates] = useState<
    Record<number, ModelStreamState>
  >({
    0: { text: "", thinking: "", status: "idle" },
    1: { text: "", thinking: "", status: "idle" },
    2: { text: "", thinking: "", status: "idle" },
  });
  const [showThinking, setShowThinking] = useState<Record<number, boolean>>({});
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (initialPrompt) {
      setPrompt(initialPrompt);
    }
  }, [initialPrompt]);

  const handleAddTarget = () => {
    if (targets.length >= 3) return;
    const available =
      PRESET_MODELS.find(
        (m) =>
          !targets.some(
            (t) => t.provider === m.provider && t.model === m.model,
          ),
      ) || PRESET_MODELS[0];
    setTargets([
      ...targets,
      { provider: available.provider, model: available.model },
    ]);
  };

  const handleRemoveTarget = (index: number) => {
    if (targets.length <= 2) return;
    setTargets(targets.filter((_, i) => i !== index));
  };

  const handleUpdateTarget = (
    index: number,
    provider: string,
    model: string,
  ) => {
    const next = [...targets];
    next[index] = { provider, model };
    setTargets(next);
  };

  const handleStartCompare = async () => {
    if (!prompt.trim() || comparing) return;

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    setComparing(true);
    const initialStates: Record<number, ModelStreamState> = {};
    targets.forEach((_, i) => {
      initialStates[i] = { text: "", thinking: "", status: "streaming" };
    });
    setModelStates(initialStates);

    try {
      const response = await fetch("/api/chat/compare/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          prompt: prompt.trim(),
          models: targets,
          conversationId,
        }),
        signal: abortController.signal,
      });

      if (!response.ok || !response.body) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith("data:")) {
            const jsonStr = trimmed.replace(/^data:\s*/, "").trim();
            if (!jsonStr || jsonStr === "[DONE]") continue;

            try {
              const chunk = JSON.parse(jsonStr) as CompareChunk;
              const idx = chunk.modelIndex;
              if (idx === -1) continue; // global done/error

              setModelStates((prev) => {
                const cur = prev[idx] || {
                  text: "",
                  thinking: "",
                  status: "streaming",
                };
                const nextState = { ...cur };

                if (chunk.chunkType === "text" && chunk.content) {
                  nextState.text = (nextState.text || "") + chunk.content;
                } else if (chunk.chunkType === "thinking" && chunk.content) {
                  nextState.thinking =
                    (nextState.thinking || "") + chunk.content;
                } else if (chunk.chunkType === "metrics") {
                  if (chunk.ttftMs) nextState.ttftMs = chunk.ttftMs;
                  if (chunk.totalDurationMs)
                    nextState.totalDurationMs = chunk.totalDurationMs;
                  if (chunk.tokensPerSecond)
                    nextState.tokensPerSecond = chunk.tokensPerSecond;
                  if (chunk.tokensCount)
                    nextState.tokensCount = chunk.tokensCount;
                } else if (chunk.chunkType === "error") {
                  nextState.status = "error";
                  nextState.error = chunk.error || "生成失败";
                } else if (chunk.chunkType === "done") {
                  nextState.status = "done";
                }

                return { ...prev, [idx]: nextState };
              });
            } catch {}
          }
        }
      }
    } catch (err: unknown) {
      if ((err as Error)?.name !== "AbortError") {
        // 降级尝试非流式请求
        try {
          const fallback = await compareModelsApi({
            prompt: prompt.trim(),
            models: targets,
            conversationId,
          });
          if (fallback?.results) {
            const nextStates: Record<number, ModelStreamState> = {};
            fallback.results.forEach((res, i) => {
              nextStates[i] = {
                text: res.content || "",
                thinking: res.thinking || "",
                ttftMs: res.ttftMs,
                totalDurationMs: res.totalDurationMs,
                tokensPerSecond: res.tokensPerSecond,
                tokensCount: res.tokensCount,
                status: res.error ? "error" : "done",
                error: res.error,
              };
            });
            setModelStates(nextStates);
          }
        } catch {}
      }
    } finally {
      setComparing(false);
      setModelStates((prev) => {
        const next = { ...prev };
        Object.keys(next).forEach((k) => {
          const i = Number(k);
          if (next[i] && next[i].status === "streaming") {
            next[i].status = "done";
          }
        });
        return next;
      });
    }
  };

  const handleCopy = async (idx: number, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedIdx(idx);
      setTimeout(() => setCopiedIdx(null), 2000);
    } catch {}
  };

  const handleExportMarkdown = () => {
    let md = `# 多模型生成比对报告\n\n**提示词**：\n> ${prompt}\n\n`;
    md += `| 维度指标 | ${targets.map((t) => `${t.provider}/${t.model}`).join(" | ")} |\n`;
    md += `|---|${targets.map(() => "---").join("|")}|\n`;
    md += `| 首字延迟 (TTFT) | ${targets.map((_, i) => `${modelStates[i]?.ttftMs ?? "-"} ms`).join(" | ")} |\n`;
    md += `| 生成速率 (Tokens/s) | ${targets.map((_, i) => `${modelStates[i]?.tokensPerSecond ?? "-"} t/s`).join(" | ")} |\n`;
    md += `| 总耗时 | ${targets.map((_, i) => `${modelStates[i]?.totalDurationMs ?? "-"} ms`).join(" | ")} |\n`;
    md += `| Token 数量 | ${targets.map((_, i) => `${modelStates[i]?.tokensCount ?? "-"} tokens`).join(" | ")} |\n\n`;

    targets.forEach((t, i) => {
      md += `## ${i + 1}. 【${t.provider} / ${t.model}】 回答\n\n`;
      if (modelStates[i]?.thinking) {
        md += `<details><summary>思考过程</summary>\n\n${modelStates[i].thinking}\n\n</details>\n\n`;
      }
      md += `${modelStates[i]?.text || (modelStates[i]?.error ? `错误: ${modelStates[i].error}` : "无回答")}\n\n---\n\n`;
    });

    const blob = new Blob([md], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `model-comparison-${Date.now()}.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 sm:p-6 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="flex h-[90vh] w-full max-w-7xl flex-col rounded-3xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
        {/* Header */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200/80 px-6 py-4 dark:border-zinc-800">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white shadow-sm">
              <Swords className="size-4" />
            </div>
            <div>
              <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                <span>多模型并排对比竞技场</span>
                <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                  {targets.length} 模型并发
                </span>
              </h2>
              <p className="text-xs text-zinc-500">
                实时多路流式对比 · 首字延迟 TTFT / 速率 / 思考链 / 一键采纳
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleExportMarkdown}
              className="flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs font-medium text-zinc-700 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 transition-colors cursor-pointer"
            >
              <Download className="size-3.5" />
              <span>导出报告</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl p-1.5 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            >
              <X className="size-5" />
            </button>
          </div>
        </div>

        {/* Prompt 输入与模型配置栏 */}
        <div className="border-b border-zinc-100 bg-zinc-50/50 p-4 dark:border-zinc-900 dark:bg-zinc-900/30">
          <div className="flex flex-col gap-3">
            <div className="flex items-start gap-2">
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="输入希望对比的 Prompt 问题..."
                rows={2}
                className="w-full resize-none rounded-xl border border-zinc-200 bg-white p-3 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
              />
              <button
                type="button"
                onClick={handleStartCompare}
                disabled={comparing || !prompt.trim()}
                className="flex shrink-0 items-center gap-1.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 px-5 py-3 text-xs font-semibold text-white shadow-md hover:from-indigo-700 hover:to-purple-700 disabled:opacity-50 transition-all cursor-pointer"
              >
                {comparing ? (
                  <>
                    <Loader2 className="size-4 animate-spin" />
                    <span>生成比对中...</span>
                  </>
                ) : (
                  <>
                    <Play className="size-4" />
                    <span>并发比对</span>
                  </>
                )}
              </button>
            </div>

            {/* 模型选择插槽 */}
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold text-zinc-500">
                参与模型：
              </span>
              {targets.map((t, idx) => (
                <div
                  key={`target-model-select-${t.provider}-${t.model}-${idx}`}
                  className="flex items-center gap-1 rounded-xl border border-indigo-200 bg-indigo-50/60 px-2.5 py-1 text-xs dark:border-indigo-900 dark:bg-indigo-950/40"
                >
                  <span className="size-1.5 rounded-full bg-indigo-500" />
                  <select
                    value={`${t.provider}::${t.model}`}
                    onChange={(e) => {
                      const [p, m] = e.target.value.split("::");
                      handleUpdateTarget(idx, p, m);
                    }}
                    aria-label={`选择模型 ${idx + 1}`}
                    className="bg-transparent text-xs font-semibold text-indigo-950 dark:text-indigo-200 outline-none"
                  >
                    {PRESET_MODELS.map((pm) => (
                      <option
                        key={`${pm.provider}::${pm.model}`}
                        value={`${pm.provider}::${pm.model}`}
                        className="dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100"
                      >
                        {pm.displayName} ({pm.provider})
                      </option>
                    ))}
                  </select>
                  {targets.length > 2 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveTarget(idx)}
                      className="ml-1 text-zinc-400 hover:text-rose-500"
                      title="移除此模型"
                    >
                      <X className="size-3" />
                    </button>
                  )}
                </div>
              ))}

              {targets.length < 3 && (
                <button
                  type="button"
                  onClick={handleAddTarget}
                  className="rounded-xl border border-dashed border-zinc-300 px-3 py-1 text-xs font-medium text-zinc-500 hover:border-indigo-500 hover:text-indigo-600 dark:border-zinc-700 transition-colors"
                >
                  + 添加第 3 个模型
                </button>
              )}
            </div>
          </div>
        </div>

        {/* 响应分栏对比区 */}
        <div className="flex-1 overflow-y-auto p-4">
          <div
            className={cn(
              "grid h-full gap-4",
              targets.length === 2
                ? "grid-cols-1 md:grid-cols-2"
                : "grid-cols-1 md:grid-cols-3",
            )}
          >
            {targets.map((target, idx) => {
              const state = modelStates[idx] || {
                text: "",
                thinking: "",
                status: "idle",
              };
              const modelMeta = PRESET_MODELS.find(
                (m) =>
                  m.provider === target.provider && m.model === target.model,
              ) || { displayName: target.model };

              return (
                <div
                  key={`target-column-${target.provider}-${target.model}-${idx}`}
                  className="flex flex-col rounded-2xl border border-zinc-200 bg-zinc-50/30 p-4 shadow-2xs dark:border-zinc-800 dark:bg-zinc-900/40"
                >
                  {/* Column Header */}
                  <div className="flex items-center justify-between border-b border-zinc-200/80 pb-3 dark:border-zinc-800">
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
                        <Bot className="size-4" />
                      </div>
                      <div className="min-w-0">
                        <h4 className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
                          {modelMeta.displayName}
                        </h4>
                        <span className="font-mono text-[10px] text-zinc-400">
                          {target.provider}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-1">
                      {state.text && (
                        <button
                          type="button"
                          onClick={() => handleCopy(idx, state.text)}
                          className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-200/60 dark:hover:bg-zinc-800"
                          title="复制回答"
                        >
                          {copiedIdx === idx ? (
                            <Check className="size-3.5 text-emerald-500" />
                          ) : (
                            <Copy className="size-3.5" />
                          )}
                        </button>
                      )}
                      {onAdopt && state.text && (
                        <button
                          type="button"
                          onClick={() => {
                            onAdopt(state.text, target.provider, target.model);
                            onClose();
                          }}
                          className="flex items-center gap-1 rounded-lg bg-indigo-600 px-2 py-1 text-[11px] font-semibold text-white hover:bg-indigo-700 shadow-2xs cursor-pointer"
                          title="将该回答采纳并插入当前会话"
                        >
                          <Check className="size-3" />
                          <span>采纳</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {/* 性能指标徽章条 */}
                  <div className="my-2.5 flex flex-wrap items-center gap-2 rounded-xl bg-white/70 px-2.5 py-1.5 text-[10px] text-zinc-500 border border-zinc-200/60 dark:bg-zinc-800/40 dark:border-zinc-800">
                    <span className="flex items-center gap-1">
                      <Timer className="size-3 text-indigo-500" />
                      <span>
                        TTFT: {state.ttftMs ? `${state.ttftMs}ms` : "-"}
                      </span>
                    </span>
                    <span className="flex items-center gap-1">
                      <Zap className="size-3 text-amber-500" />
                      <span>
                        速率:{" "}
                        {state.tokensPerSecond
                          ? `${state.tokensPerSecond} t/s`
                          : "-"}
                      </span>
                    </span>
                    <span className="flex items-center gap-1">
                      <Gauge className="size-3 text-emerald-500" />
                      <span>Tokens: {state.tokensCount ?? "-"}</span>
                    </span>
                  </div>

                  {/* 思考链折叠 */}
                  {state.thinking && (
                    <div className="mb-2 rounded-xl border border-purple-200/60 bg-purple-50/30 p-2 text-xs dark:border-purple-900/40 dark:bg-purple-950/20">
                      <button
                        type="button"
                        onClick={() =>
                          setShowThinking((prev) => ({
                            ...prev,
                            [idx]: !prev[idx],
                          }))
                        }
                        className="flex w-full items-center justify-between text-purple-700 dark:text-purple-300 font-semibold text-[11px]"
                      >
                        <span className="flex items-center gap-1">
                          <Sparkles className="size-3" />
                          <span>深度思考过程</span>
                        </span>
                        {showThinking[idx] ? (
                          <ChevronDown className="size-3.5" />
                        ) : (
                          <ChevronRight className="size-3.5" />
                        )}
                      </button>
                      {showThinking[idx] && (
                        <div className="mt-2 text-zinc-600 dark:text-zinc-400 font-mono text-[11px] whitespace-pre-wrap max-h-48 overflow-y-auto">
                          {state.thinking}
                        </div>
                      )}
                    </div>
                  )}

                  {/* 回答正文 */}
                  <div className="flex-1 overflow-y-auto rounded-xl bg-white p-3 text-xs dark:bg-zinc-900/90 min-h-[220px]">
                    {state.error ? (
                      <div className="p-3 text-rose-500">
                        <strong>生成异常：</strong> {state.error}
                      </div>
                    ) : state.text ? (
                      <Markdown
                        content={state.text}
                        isStreaming={state.status === "streaming"}
                      />
                    ) : state.status === "streaming" ? (
                      <div className="flex items-center gap-2 text-zinc-400 py-6 justify-center">
                        <Loader2 className="size-4 animate-spin text-indigo-500" />
                        <span>等待模型流式返回...</span>
                      </div>
                    ) : (
                      <div className="py-12 text-center text-zinc-400 text-xs">
                        点击“并发比对”开始生成
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
