"use client";

import {
  BookTemplate,
  ChevronRight,
  Loader2,
  Search,
  Sparkles,
  Wand2,
  X,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  fetchPromptTemplates,
  type PromptTemplate,
  smartFillPromptTemplate,
} from "@/lib/prompt-template-api";
import { cn } from "@/lib/utils";

interface PromptTemplateDialogProps {
  open: boolean;
  onClose: () => void;
  onSelectPrompt: (renderedPrompt: string) => void;
}

const DIALOG_CATEGORIES = [
  { id: "all", label: "全部" },
  { id: "coding", label: "编程" },
  { id: "writing", label: "写作" },
  { id: "translation", label: "翻译" },
  { id: "analysis", label: "分析" },
];

export function PromptTemplateDialog({
  open,
  onClose,
  onSelectPrompt,
}: PromptTemplateDialogProps) {
  const [templates, setTemplates] = useState<PromptTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("all");

  const [selectedTemplate, setSelectedTemplate] =
    useState<PromptTemplate | null>(null);
  const [variableValues, setVariableValues] = useState<Record<string, string>>(
    {},
  );
  const [smartContext, setSmartContext] = useState("");
  const [smartFilling, setSmartFilling] = useState(false);

  useEffect(() => {
    if (!open) return;
    let isMounted = true;
    setLoading(true);
    fetchPromptTemplates()
      .then((data) => {
        if (isMounted) {
          setTemplates(data);
          if (data.length > 0) {
            setSelectedTemplate(data[0]);
            const initVars: Record<string, string> = {};
            for (const v of data[0].variables) {
              initVars[v] = "";
            }
            setVariableValues(initVars);
          }
        }
      })
      .catch((e: unknown) => {
        toast.error(e instanceof Error ? e.message : "加载模板失败");
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, [open]);

  const filtered = useMemo(() => {
    return templates.filter((t) => {
      if (selectedCategory !== "all" && t.category !== selectedCategory) {
        return false;
      }
      if (search.trim()) {
        const q = search.toLowerCase();
        return (
          t.title.toLowerCase().includes(q) ||
          t.description?.toLowerCase().includes(q) ||
          t.body.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [templates, selectedCategory, search]);

  const handleSelectTemplate = (t: PromptTemplate) => {
    setSelectedTemplate(t);
    const initVars: Record<string, string> = {};
    for (const v of t.variables) {
      initVars[v] = "";
    }
    setVariableValues(initVars);
    setSmartContext("");
  };

  const renderedText = useMemo(() => {
    if (!selectedTemplate) return "";
    let text = selectedTemplate.body;
    for (const [k, v] of Object.entries(variableValues)) {
      const reg = new RegExp(`\\{\\{${k}\\}\\}`, "g");
      text = text.replace(reg, v || `{{${k}}}`);
    }
    return text;
  }, [selectedTemplate, variableValues]);

  const handleSmartFill = async () => {
    if (!selectedTemplate || !smartContext.trim()) return;
    try {
      setSmartFilling(true);
      const filled = await smartFillPromptTemplate(
        selectedTemplate.id,
        smartContext.trim(),
      );
      if (Object.keys(filled).length > 0) {
        setVariableValues((prev) => ({ ...prev, ...filled }));
        toast.success("AI 智能推断完成");
      }
    } catch {
      toast.error("智能填充失败");
    } finally {
      setSmartFilling(false);
    }
  };

  const handleApply = () => {
    if (!renderedText) return;
    onSelectPrompt(renderedText);
    onClose();
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="relative flex flex-col w-full max-w-4xl h-[640px] max-h-[90vh] rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden">
        {/* 顶部标题栏 */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-950/40">
          <div className="flex items-center gap-2 text-zinc-900 dark:text-zinc-100 font-semibold text-sm">
            <BookTemplate className="size-4 text-indigo-500" />
            <span>选择并插入 Prompt 模板</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* 主体分栏 */}
        <div className="flex-1 flex min-h-0 divide-x divide-zinc-200 dark:divide-zinc-800">
          {/* 左侧列表 */}
          <div className="w-2/5 flex flex-col min-h-0 bg-zinc-50/40 dark:bg-zinc-950/20">
            {/* 搜索与分类切换 */}
            <div className="p-3 border-b border-zinc-200 dark:border-zinc-800 space-y-2">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-zinc-400" />
                <input
                  type="text"
                  placeholder="搜索模板..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-xs focus:outline-hidden"
                />
              </div>
              <div className="flex items-center gap-1 overflow-x-auto pb-0.5">
                {DIALOG_CATEGORIES.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => setSelectedCategory(c.id)}
                    className={cn(
                      "px-2 py-0.5 rounded text-[11px] font-medium transition-colors whitespace-nowrap",
                      selectedCategory === c.id
                        ? "bg-indigo-600 text-white"
                        : "bg-white dark:bg-zinc-900 text-zinc-600 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-800",
                    )}
                  >
                    {c.label}
                  </button>
                ))}
              </div>
            </div>

            {/* 列表内容 */}
            <div className="flex-1 overflow-y-auto divide-y divide-zinc-100 dark:divide-zinc-800/60">
              {loading ? (
                <div className="py-12 text-center text-xs text-zinc-400 flex flex-col items-center gap-2">
                  <Loader2 className="size-5 animate-spin text-indigo-500" />
                  <span>加载中...</span>
                </div>
              ) : filtered.length === 0 ? (
                <div className="py-12 text-center text-xs text-zinc-400">
                  无匹配模板
                </div>
              ) : (
                filtered.map((t) => {
                  const isSelected = selectedTemplate?.id === t.id;
                  return (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => handleSelectTemplate(t)}
                      className={cn(
                        "w-full p-3 text-left transition-colors flex items-center justify-between gap-2",
                        isSelected
                          ? "bg-indigo-50/80 dark:bg-indigo-950/50 text-indigo-900 dark:text-indigo-200"
                          : "hover:bg-zinc-100/70 dark:hover:bg-zinc-800/40 text-zinc-700 dark:text-zinc-300",
                      )}
                    >
                      <div className="min-w-0 space-y-1">
                        <div className="flex items-center gap-1.5">
                          <span className="text-xs font-semibold truncate">
                            {t.title}
                          </span>
                          {t.isSystem && (
                            <span className="px-1 py-0.2 rounded text-[9px] font-medium bg-amber-100 dark:bg-amber-950 text-amber-700 dark:text-amber-400">
                              预设
                            </span>
                          )}
                        </div>
                        {t.description && (
                          <p className="text-[11px] text-zinc-400 truncate">
                            {t.description}
                          </p>
                        )}
                      </div>
                      <ChevronRight className="size-4 shrink-0 text-zinc-300 dark:text-zinc-700" />
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* 右侧详情与变量填写 */}
          <div className="flex-1 flex flex-col min-h-0 p-4 space-y-3 overflow-y-auto text-xs">
            {selectedTemplate ? (
              <>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                      {selectedTemplate.title}
                    </h3>
                    <span className="px-2 py-0.5 rounded text-[10px] bg-indigo-50 dark:bg-indigo-950 text-indigo-600 dark:text-indigo-400 font-medium">
                      {selectedTemplate.category}
                    </span>
                  </div>
                  {selectedTemplate.description && (
                    <p className="text-xs text-zinc-500 mt-1">
                      {selectedTemplate.description}
                    </p>
                  )}
                </div>

                {/* 智能填充 */}
                <div className="p-2.5 rounded-xl border border-indigo-200/60 dark:border-indigo-800/60 bg-indigo-50/30 dark:bg-indigo-950/20 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-indigo-600 dark:text-indigo-400 text-[11px] flex items-center gap-1">
                      <Wand2 className="size-3" />
                      AI 智能填充变量
                    </span>
                    <button
                      type="button"
                      disabled={smartFilling || !smartContext.trim()}
                      onClick={handleSmartFill}
                      className="px-2 py-0.5 rounded bg-indigo-600 text-white text-[10px] font-medium disabled:opacity-40"
                    >
                      {smartFilling ? "解析中..." : "自动解析"}
                    </button>
                  </div>
                  <input
                    type="text"
                    placeholder="输入自然语言描述..."
                    value={smartContext}
                    onChange={(e) => setSmartContext(e.target.value)}
                    className="w-full px-2.5 py-1 rounded-md border border-indigo-200 dark:border-indigo-800 bg-white dark:bg-zinc-900 text-xs focus:outline-hidden"
                  />
                </div>

                {/* 变量输入 */}
                {selectedTemplate.variables.length > 0 && (
                  <div className="space-y-2">
                    <div className="font-semibold text-zinc-700 dark:text-zinc-300">
                      变量插槽:
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      {selectedTemplate.variables.map((v) => (
                        <div key={v}>
                          <label
                            htmlFor={`slot-${v}`}
                            className="block font-mono text-[10px] text-zinc-400 mb-0.5"
                          >
                            {`{{${v}}}`}
                          </label>
                          <input
                            id={`slot-${v}`}
                            type="text"
                            placeholder={v}
                            value={variableValues[v] || ""}
                            onChange={(e) =>
                              setVariableValues((prev) => ({
                                ...prev,
                                [v]: e.target.value,
                              }))
                            }
                            className="w-full px-2.5 py-1 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 font-mono text-xs focus:outline-hidden"
                          />
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 预览 */}
                <div className="space-y-1 flex-1 flex flex-col min-h-0">
                  <div className="font-semibold text-zinc-700 dark:text-zinc-300">
                    Prompt 渲染效果:
                  </div>
                  <div className="flex-1 p-3 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-950 text-zinc-200 font-mono text-xs overflow-y-auto whitespace-pre-wrap">
                    {renderedText}
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-zinc-400">
                请在左侧选择一个 Prompt 模板
              </div>
            )}
          </div>
        </div>

        {/* 底部按钮栏 */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-950/40">
          <Link
            href="/prompt-templates"
            className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
          >
            打开完整模板管理中心 →
          </Link>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 text-xs font-medium hover:bg-zinc-100 dark:hover:bg-zinc-800"
            >
              取消
            </button>
            <button
              type="button"
              disabled={!selectedTemplate}
              onClick={handleApply}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 disabled:opacity-40"
            >
              <Sparkles className="size-3.5" />
              <span>插入到对话输入框</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
