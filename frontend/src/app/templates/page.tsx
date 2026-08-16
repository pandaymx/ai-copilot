"use client";

import {
  ArrowLeft,
  BookOpen,
  Calendar,
  Check,
  Code,
  Copy,
  Download,
  FileText,
  History,
  Loader2,
  Mail,
  PenTool,
  RotateCcw,
  Sparkles,
  Trash2,
  Users,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  type ContentGenerationHistoryItem,
  type ContentTemplateMetadata,
  deleteContentHistory,
  generateContent,
  listContentHistory,
  listContentTemplates,
} from "@/lib/content-template-api";
import { cn } from "@/lib/utils";

const ICON_MAP: Record<string, typeof FileText> = {
  Calendar: Calendar,
  Code: Code,
  Users: Users,
  Mail: Mail,
  BookOpen: BookOpen,
  FileText: FileText,
};

export default function ContentTemplatesPage() {
  const [templates, setTemplates] = useState<ContentTemplateMetadata[]>([]);
  const [selectedTemplate, setSelectedTemplate] =
    useState<ContentTemplateMetadata | null>(null);
  const [inputs, setInputs] = useState<Record<string, string>>({});
  const [title, setTitle] = useState("");
  const [customPrompt, setCustomPrompt] = useState("");

  const [generating, setGenerating] = useState(false);
  const [generatedResult, setGeneratedResult] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const [history, setHistory] = useState<ContentGenerationHistoryItem[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const loadTemplates = useCallback(async () => {
    try {
      const data = await listContentTemplates();
      setTemplates(data);
      if (data.length > 0) {
        setSelectedTemplate(data[0]);
        setTitle(data[0].name);
      }
    } catch {
      toast.error("加载内容生成模板失败");
    }
  }, []);

  const loadHistory = useCallback(async () => {
    try {
      setLoadingHistory(true);
      const data = await listContentHistory();
      setHistory(data);
    } catch {
      toast.error("加载历史生成记录失败");
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    void loadTemplates();
    void loadHistory();
  }, [loadTemplates, loadHistory]);

  const handleSelectTemplate = (tpl: ContentTemplateMetadata) => {
    setSelectedTemplate(tpl);
    setTitle(tpl.name);
    setInputs({});
    setGeneratedResult(null);
  };

  const handleInputChange = (field: string, value: string) => {
    setInputs((prev) => ({ ...prev, [field]: value }));
  };

  const handleGenerate = async () => {
    if (!selectedTemplate) return;
    try {
      setGenerating(true);
      const res = await generateContent({
        templateId: selectedTemplate.id,
        title: title || selectedTemplate.name,
        inputs: inputs,
        customPrompt: customPrompt,
      });
      setGeneratedResult(res.markdownContent);
      toast.success("内容生成完毕！");
      void loadHistory();
    } catch {
      toast.error("生成失败，请检查网络或配置");
    } finally {
      setGenerating(false);
    }
  };

  const handleCopy = async () => {
    if (!generatedResult) return;
    try {
      await navigator.clipboard.writeText(generatedResult);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
      toast.success("已复制 Markdown 内容");
    } catch {
      toast.error("复制失败");
    }
  };

  const handleDownload = () => {
    if (!generatedResult) return;
    const blob = new Blob([generatedResult], {
      type: "text/markdown;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${title || "generated-document"}.md`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success("已导出为 Markdown 文件");
  };

  const handleDeleteHistory = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await deleteContentHistory(id);
      setHistory((prev) => prev.filter((item) => item.id !== id));
      toast.success("已删除历史记录");
    } catch {
      toast.error("删除失败");
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 p-4 md:p-8">
      <div className="mx-auto max-w-6xl space-y-6">
        {/* 顶部导航与操作 */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="p-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-colors"
            >
              <ArrowLeft className="size-4" />
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <div className="size-8 rounded-xl bg-purple-500/10 text-purple-600 flex items-center justify-center">
                  <PenTool className="size-4" />
                </div>
                <h1 className="text-xl font-bold">
                  AI 结构化内容创作向导 (Content Templates)
                </h1>
              </div>
              <p className="text-xs text-zinc-500 mt-0.5">
                按行业与岗位模板标准输入素材，一键生成结构化高质量周报、技术方案与商务邮件
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setShowHistory(!showHistory)}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-xs font-semibold text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors shadow-2xs cursor-pointer"
          >
            <History className="size-3.5" />
            <span>历史生成 ({history.length})</span>
          </button>
        </div>

        {/* 模板选择卡片网格 */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
          {templates.map((tpl) => {
            const Icon = ICON_MAP[tpl.icon] || FileText;
            const isSelected = selectedTemplate?.id === tpl.id;
            return (
              <button
                key={tpl.id}
                type="button"
                onClick={() => handleSelectTemplate(tpl)}
                className={cn(
                  "p-3 rounded-2xl border text-left transition-all cursor-pointer flex flex-col justify-between",
                  isSelected
                    ? "border-purple-500 bg-purple-50/70 dark:bg-purple-950/40 shadow-sm"
                    : "border-zinc-200/80 dark:border-zinc-800/80 bg-white dark:bg-zinc-900 hover:border-zinc-300 dark:hover:border-zinc-700",
                )}
              >
                <div className="flex items-center justify-between w-full mb-2">
                  <div
                    className={cn(
                      "size-7 rounded-lg flex items-center justify-center",
                      isSelected
                        ? "bg-purple-500 text-white"
                        : "bg-zinc-100 dark:bg-zinc-800 text-zinc-500",
                    )}
                  >
                    <Icon className="size-3.5" />
                  </div>
                  <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-md bg-zinc-100 dark:bg-zinc-800 text-zinc-500">
                    {tpl.category}
                  </span>
                </div>
                <div>
                  <h4 className="text-xs font-bold text-zinc-900 dark:text-white">
                    {tpl.name}
                  </h4>
                  <p className="text-[10px] text-zinc-400 line-clamp-1 mt-0.5">
                    {tpl.description}
                  </p>
                </div>
              </button>
            );
          })}
        </div>

        {/* 主编辑与预览两栏布局 */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* 左侧：表单素材向导 */}
          <div className="lg:col-span-5 space-y-4">
            <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs space-y-4">
              <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800/80 pb-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-zinc-400">
                  素材与参数配置
                </h3>
                <button
                  type="button"
                  onClick={() => setInputs({})}
                  className="flex items-center gap-1 text-[11px] text-zinc-400 hover:text-rose-500 transition-colors"
                >
                  <RotateCcw className="size-3" />
                  <span>清空表单</span>
                </button>
              </div>

              <div className="space-y-3">
                <label className="block text-xs font-medium text-zinc-700 dark:text-zinc-300">
                  <span className="mb-1 block">
                    文档主标题 <span className="text-rose-500">*</span>
                  </span>
                  <input
                    type="text"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="输入文档主标题..."
                    className="w-full rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 px-3 py-2 text-xs outline-hidden focus:border-purple-500"
                  />
                </label>

                {selectedTemplate?.fields.map((f) => (
                  <div key={f.name}>
                    <label
                      htmlFor={`field-${f.name}`}
                      className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      {f.label}{" "}
                      {f.required && <span className="text-rose-500">*</span>}
                    </label>
                    {f.type === "textarea" ? (
                      <textarea
                        id={`field-${f.name}`}
                        rows={3}
                        value={inputs[f.name] || ""}
                        onChange={(e) =>
                          handleInputChange(f.name, e.target.value)
                        }
                        placeholder={f.placeholder}
                        className="w-full rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 p-2.5 text-xs outline-hidden focus:border-purple-500"
                      />
                    ) : (
                      <input
                        id={`field-${f.name}`}
                        type="text"
                        value={inputs[f.name] || ""}
                        onChange={(e) =>
                          handleInputChange(f.name, e.target.value)
                        }
                        placeholder={f.placeholder}
                        className="w-full rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 px-3 py-2 text-xs outline-hidden focus:border-purple-500"
                      />
                    )}
                  </div>
                ))}

                <label className="block text-xs font-medium text-zinc-700 dark:text-zinc-300">
                  <span className="mb-1 block">附加定制要求 (可选)</span>
                  <input
                    type="text"
                    value={customPrompt}
                    onChange={(e) => setCustomPrompt(e.target.value)}
                    placeholder="如：突出量化指标、语气严谨、控制在800字以内..."
                    className="w-full rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 px-3 py-2 text-xs outline-hidden focus:border-purple-500"
                  />
                </label>
              </div>

              <button
                type="button"
                onClick={handleGenerate}
                disabled={generating || !title.trim()}
                className="w-full flex items-center justify-center gap-2 py-3 rounded-2xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-bold text-xs shadow-md transition-all disabled:opacity-50 cursor-pointer"
              >
                {generating ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Sparkles className="size-4" />
                )}
                <span>
                  {generating ? "AI 正在创作与排版..." : "一键开始生成内容"}
                </span>
              </button>
            </div>
          </div>

          {/* 右侧：生成结果预览与导出 */}
          <div className="lg:col-span-7 space-y-4">
            <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs min-h-[520px] flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800/80 pb-3 mb-4">
                  <div className="flex items-center gap-2">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-zinc-400">
                      文档预览与成果物
                    </h3>
                    {generatedResult && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-400">
                        生成完成
                      </span>
                    )}
                  </div>

                  {generatedResult && (
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={handleCopy}
                        className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-100 dark:bg-zinc-800 text-[11px] font-medium text-zinc-600 dark:text-zinc-300 hover:bg-zinc-200 transition-colors"
                      >
                        {copied ? (
                          <Check className="size-3 text-emerald-500" />
                        ) : (
                          <Copy className="size-3" />
                        )}
                        <span>{copied ? "已复制" : "复制"}</span>
                      </button>
                      <button
                        type="button"
                        onClick={handleDownload}
                        className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-purple-50 text-purple-600 dark:bg-purple-950/50 dark:text-purple-400 text-[11px] font-semibold hover:bg-purple-100 transition-colors"
                      >
                        <Download className="size-3" />
                        <span>导出 .md</span>
                      </button>
                    </div>
                  )}
                </div>

                {generatedResult ? (
                  <div className="p-4 rounded-2xl bg-zinc-50 dark:bg-zinc-950/80 border border-zinc-200/50 dark:border-zinc-800/50 font-mono text-xs text-zinc-800 dark:text-zinc-200 whitespace-pre-wrap leading-relaxed max-h-[560px] overflow-y-auto">
                    {generatedResult}
                  </div>
                ) : (
                  <div className="p-16 text-center text-zinc-400 space-y-3">
                    <PenTool className="size-10 mx-auto text-purple-400 opacity-40" />
                    <p className="text-xs">
                      在左侧填写素材并点击「一键开始生成内容」
                      <br />
                      AI 将自动为您组织标题、分节与专业排版
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* 历史生成侧滑/弹窗 */}
        {showHistory && (
          <div className="p-5 rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800/80 shadow-xs space-y-3">
            <h3 className="text-sm font-bold">历史生成文档</h3>
            {loadingHistory ? (
              <div className="p-8 text-center text-zinc-400 text-xs">
                <Loader2 className="size-4 animate-spin mx-auto mb-2" />
                加载中...
              </div>
            ) : history.length === 0 ? (
              <p className="text-xs text-zinc-400">暂无历史生成记录</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {history.map((h) => (
                  <div
                    key={h.id}
                    className="p-3.5 rounded-2xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/60 dark:border-zinc-800/60 flex items-center justify-between gap-3 hover:border-purple-400/50 transition-colors"
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setTitle(h.title);
                        setGeneratedResult(h.markdownContent);
                        setShowHistory(false);
                      }}
                      className="text-left flex-1 min-w-0"
                    >
                      <span className="font-bold text-xs truncate block text-zinc-900 dark:text-white">
                        {h.title}
                      </span>
                      <span className="text-[10px] text-zinc-400 font-mono">
                        {new Date(h.createdAt).toLocaleString()} · 模板:{" "}
                        {h.templateId}
                      </span>
                    </button>
                    <button
                      type="button"
                      onClick={(e) => void handleDeleteHistory(h.id, e)}
                      className="p-1.5 rounded-lg text-zinc-400 hover:text-rose-500 transition-colors shrink-0"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
