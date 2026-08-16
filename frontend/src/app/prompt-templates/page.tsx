"use client";

import {
  ArrowLeft,
  BookTemplate,
  Copy,
  Edit2,
  Heart,
  Loader2,
  Plus,
  Search,
  Sparkles,
  Star,
  Trash2,
  Wand2,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  createPromptTemplate,
  deletePromptTemplate,
  fetchPromptTemplates,
  type PromptTemplate,
  ratePromptTemplate,
  smartFillPromptTemplate,
  toggleFavoritePromptTemplate,
  updatePromptTemplate,
} from "@/lib/prompt-template-api";
import { cn } from "@/lib/utils";

const CATEGORIES = [
  { id: "all", label: "全部" },
  { id: "coding", label: "💻 编程与技术" },
  { id: "writing", label: "✍️ 写作与润色" },
  { id: "translation", label: "🌐 翻译与语境" },
  { id: "analysis", label: "📊 分析与决策" },
  { id: "learning", label: "🎓 学习与解答" },
  { id: "creative", label: "🎨 创意与设计" },
  { id: "favorites", label: "❤️ 我的收藏" },
];

export default function PromptTemplatesPage() {
  const [templates, setTemplates] = useState<PromptTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");

  // 创建/编辑弹窗状态
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<PromptTemplate | null>(
    null,
  );
  const [formTitle, setFormTitle] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formCategory, setFormCategory] = useState("coding");
  const [formBody, setFormBody] = useState("");
  const [saving, setSaving] = useState(false);

  // 变量填写与渲染预览弹窗状态
  const [renderModalOpen, setRenderModalOpen] = useState(false);
  const [activeTemplate, setActiveTemplate] = useState<PromptTemplate | null>(
    null,
  );
  const [variableValues, setVariableValues] = useState<Record<string, string>>(
    {},
  );
  const [smartContext, setSmartContext] = useState("");
  const [smartFilling, setSmartFilling] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);
      const data = await fetchPromptTemplates();
      setTemplates(data);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "加载模板列表失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void (async () => {
      try {
        setLoading(true);
        const data = await fetchPromptTemplates();
        setTemplates(data);
      } catch (e: unknown) {
        toast.error(e instanceof Error ? e.message : "加载模板列表失败");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // 过滤后的模板列表
  const filteredTemplates = useMemo(() => {
    return templates.filter((t) => {
      if (selectedCategory === "favorites" && !t.favorite) return false;
      if (
        selectedCategory !== "all" &&
        selectedCategory !== "favorites" &&
        t.category !== selectedCategory
      ) {
        return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        return (
          t.title.toLowerCase().includes(q) ||
          t.description?.toLowerCase().includes(q) ||
          t.body.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [templates, selectedCategory, searchQuery]);

  // 从编辑表单 body 中实时提取变量
  const detectedVariables = useMemo(() => {
    const matches = formBody.matchAll(/\{\{([a-zA-Z0-9_-]+)\}\}/g);
    const set = new Set<string>();
    for (const m of matches) {
      set.add(m[1]);
    }
    return Array.from(set);
  }, [formBody]);

  // 打开创建弹窗
  const handleOpenCreate = () => {
    setEditingTemplate(null);
    setFormTitle("");
    setFormDescription("");
    setFormCategory("coding");
    setFormBody("");
    setEditModalOpen(true);
  };

  // 打开编辑弹窗
  const handleOpenEdit = (t: PromptTemplate) => {
    if (t.isSystem) {
      toast.info("系统预设模板为只读，您可以基于它创建自定义模板");
      return;
    }
    setEditingTemplate(t);
    setFormTitle(t.title);
    setFormDescription(t.description || "");
    setFormCategory(t.category);
    setFormBody(t.body);
    setEditModalOpen(true);
  };

  // 保存（创建/编辑）模板
  const handleSave = async () => {
    if (!formTitle.trim()) {
      toast.error("请输入模板标题");
      return;
    }
    if (!formBody.trim()) {
      toast.error("请输入模板正文内容");
      return;
    }

    try {
      setSaving(true);
      if (editingTemplate) {
        await updatePromptTemplate(editingTemplate.id, {
          title: formTitle.trim(),
          description: formDescription.trim(),
          category: formCategory,
          body: formBody,
        });
        toast.success("模板更新成功");
      } else {
        await createPromptTemplate({
          title: formTitle.trim(),
          description: formDescription.trim(),
          category: formCategory,
          body: formBody,
        });
        toast.success("模板创建成功");
      }
      setEditModalOpen(false);
      await loadData();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "保存模板失败");
    } finally {
      setSaving(false);
    }
  };

  // 删除模板
  const handleDelete = async (id: string) => {
    if (!confirm("确定要删除该 Prompt 模板吗？")) return;
    try {
      await deletePromptTemplate(id);
      toast.success("模板已删除");
      setTemplates((prev) => prev.filter((t) => t.id !== id));
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "删除模板失败");
    }
  };

  // 收藏切换
  const handleToggleFavorite = async (id: string) => {
    try {
      await toggleFavoritePromptTemplate(id);
      setTemplates((prev) =>
        prev.map((t) => (t.id === id ? { ...t, favorite: !t.favorite } : t)),
      );
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "收藏操作失败");
    }
  };

  // 评分
  const handleRate = async (id: string, rating: number) => {
    try {
      await ratePromptTemplate(id, rating);
      setTemplates((prev) =>
        prev.map((t) => (t.id === id ? { ...t, rating } : t)),
      );
      toast.success(`评分已更新: ${rating} 星`);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "评分操作失败");
    }
  };

  // 打开填写与渲染弹窗
  const handleOpenRender = (t: PromptTemplate) => {
    setActiveTemplate(t);
    const initial: Record<string, string> = {};
    for (const v of t.variables) {
      initial[v] = "";
    }
    setVariableValues(initial);
    setSmartContext("");
    setRenderModalOpen(true);
  };

  // 实时渲染计算
  const renderedText = useMemo(() => {
    if (!activeTemplate) return "";
    let text = activeTemplate.body;
    for (const [k, v] of Object.entries(variableValues)) {
      const reg = new RegExp(`\\{\\{${k}\\}\\}`, "g");
      text = text.replace(reg, v || `{{${k}}}`);
    }
    return text;
  }, [activeTemplate, variableValues]);

  // AI 智能填充变量
  const handleSmartFill = async () => {
    if (!activeTemplate || !smartContext.trim()) {
      toast.error("请输入一段参考上下文以供 AI 推理变量");
      return;
    }
    try {
      setSmartFilling(true);
      const filled = await smartFillPromptTemplate(
        activeTemplate.id,
        smartContext.trim(),
      );
      if (Object.keys(filled).length > 0) {
        setVariableValues((prev) => ({ ...prev, ...filled }));
        toast.success("AI 智能填充变量完成！");
      } else {
        toast.info("未能根据提供的信息推断出具体变量，请手动填写");
      }
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "智能填充失败");
    } finally {
      setSmartFilling(false);
    }
  };

  // 复制渲染后的 Prompt
  const handleCopyRendered = () => {
    void navigator.clipboard.writeText(renderedText);
    toast.success("已复制到剪贴板！");
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 p-4 sm:p-8">
      <div className="max-w-6xl mx-auto space-y-6">
        {/* 顶部导航与操作栏 */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-zinc-200 dark:border-zinc-800">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="flex size-9 items-center justify-center rounded-xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            >
              <ArrowLeft className="size-4" />
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <BookTemplate className="size-5 text-indigo-500" />
                <h1 className="text-xl font-bold tracking-tight">
                  Prompt 模板库
                </h1>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                沉淀并复用高质量 AI 提示词结构，支持变量插槽与一键智能填充
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleOpenCreate}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 shadow-sm transition-all"
            >
              <Plus className="size-4" />
              <span>新建模板</span>
            </button>
          </div>
        </div>

        {/* 搜索与分类 Tab */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          {/* 分类标签 */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full">
            {CATEGORIES.map((cat) => (
              <button
                key={cat.id}
                type="button"
                onClick={() => setSelectedCategory(cat.id)}
                className={cn(
                  "px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all",
                  selectedCategory === cat.id
                    ? "bg-indigo-500 text-white shadow-xs font-semibold"
                    : "bg-white dark:bg-zinc-900 text-zinc-600 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 dark:hover:bg-zinc-800",
                )}
              >
                {cat.label}
              </button>
            ))}
          </div>

          {/* 搜索框 */}
          <div className="relative w-full md:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-zinc-400" />
            <input
              type="text"
              placeholder="搜索模板标题、内容或标签..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-xs focus:outline-hidden focus:ring-2 focus:ring-indigo-500/30"
            />
          </div>
        </div>

        {/* 模板卡片网格 */}
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3 text-zinc-400 text-xs">
            <Loader2 className="size-6 animate-spin text-indigo-500" />
            <span>正在加载 Prompt 模板库...</span>
          </div>
        ) : filteredTemplates.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 rounded-2xl border border-dashed border-zinc-200 dark:border-zinc-800 bg-white/50 dark:bg-zinc-900/50 gap-3 text-center">
            <BookTemplate className="size-10 text-zinc-300 dark:text-zinc-700" />
            <div className="space-y-1">
              <p className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
                暂无匹配的 Prompt 模板
              </p>
              <p className="text-xs text-zinc-400">
                点击右上角「新建模板」立即创建专属 Prompt 结构
              </p>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredTemplates.map((tpl) => (
              <div
                key={tpl.id}
                className="group relative flex flex-col justify-between rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4.5 shadow-xs hover:shadow-md hover:border-indigo-300 dark:hover:border-indigo-700/60 transition-all"
              >
                <div className="space-y-3">
                  {/* 顶部标签与收藏 */}
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 border border-indigo-200/50 dark:border-indigo-800/50">
                        {tpl.category}
                      </span>
                      {tpl.isSystem && (
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400">
                          系统预设
                        </span>
                      )}
                    </div>

                    <button
                      type="button"
                      onClick={() => handleToggleFavorite(tpl.id)}
                      className={cn(
                        "p-1.5 rounded-lg transition-colors",
                        tpl.favorite
                          ? "text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/30"
                          : "text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800",
                      )}
                      title={tpl.favorite ? "取消收藏" : "加入收藏"}
                    >
                      <Heart
                        className={cn(
                          "size-4",
                          tpl.favorite && "fill-rose-500",
                        )}
                      />
                    </button>
                  </div>

                  {/* 标题与描述 */}
                  <div className="space-y-1">
                    <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors line-clamp-1">
                      {tpl.title}
                    </h3>
                    {tpl.description && (
                      <p className="text-xs text-zinc-500 dark:text-zinc-400 line-clamp-2">
                        {tpl.description}
                      </p>
                    )}
                  </div>

                  {/* 模板变量插槽预览 */}
                  {tpl.variables.length > 0 && (
                    <div className="flex items-center gap-1 flex-wrap pt-1">
                      <span className="text-[10px] text-zinc-400">变量:</span>
                      {tpl.variables.slice(0, 4).map((v) => (
                        <span
                          key={v}
                          className="px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-800 text-[10px] font-mono text-zinc-600 dark:text-zinc-300"
                        >
                          {`{{${v}}}`}
                        </span>
                      ))}
                      {tpl.variables.length > 4 && (
                        <span className="text-[10px] text-zinc-400">
                          +{tpl.variables.length - 4}
                        </span>
                      )}
                    </div>
                  )}

                  {/* 模板正文预览 */}
                  <div className="p-2.5 rounded-xl bg-zinc-50 dark:bg-zinc-950/60 border border-zinc-100 dark:border-zinc-800/80 text-[11px] font-mono text-zinc-600 dark:text-zinc-400 line-clamp-3 whitespace-pre-wrap">
                    {tpl.body}
                  </div>
                </div>

                {/* 底部评分与操作按钮 */}
                <div className="flex items-center justify-between pt-4 mt-3 border-t border-zinc-100 dark:border-zinc-800/80">
                  {/* 星级评分 */}
                  <div className="flex items-center gap-0.5">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <button
                        key={star}
                        type="button"
                        onClick={() => handleRate(tpl.id, star)}
                        className="text-amber-400 hover:scale-110 transition-transform"
                      >
                        <Star
                          className={cn(
                            "size-3.5",
                            star <= (tpl.rating || 5)
                              ? "fill-amber-400"
                              : "text-zinc-300 dark:text-zinc-700",
                          )}
                        />
                      </button>
                    ))}
                  </div>

                  {/* 操作区 */}
                  <div className="flex items-center gap-1">
                    {!tpl.isSystem && (
                      <>
                        <button
                          type="button"
                          onClick={() => handleOpenEdit(tpl)}
                          className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
                          title="编辑模板"
                        >
                          <Edit2 className="size-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(tpl.id)}
                          className="p-1.5 rounded-lg text-zinc-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors"
                          title="删除模板"
                        >
                          <Trash2 className="size-3.5" />
                        </button>
                      </>
                    )}

                    <button
                      type="button"
                      onClick={() => handleOpenRender(tpl)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 dark:hover:bg-indigo-900/60 text-xs font-medium transition-colors ml-1"
                    >
                      <Sparkles className="size-3" />
                      <span>使用 / 填充</span>
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* 创建/编辑弹窗 */}
        {editModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
            <div className="relative w-full max-w-xl rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 p-6 shadow-2xl space-y-4">
              <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 pb-3">
                <h2 className="text-base font-bold">
                  {editingTemplate ? "编辑 Prompt 模板" : "新建 Prompt 模板"}
                </h2>
                <button
                  type="button"
                  onClick={() => setEditModalOpen(false)}
                  className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 text-xs"
                >
                  ✕
                </button>
              </div>

              <div className="space-y-3.5 text-xs">
                <div>
                  <label
                    htmlFor="form-title"
                    className="block font-semibold mb-1"
                  >
                    模板标题 *
                  </label>
                  <input
                    id="form-title"
                    type="text"
                    placeholder="如：代码重构大师、论文摘要生成"
                    value={formTitle}
                    onChange={(e) => setFormTitle(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/30"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label
                      htmlFor="form-category"
                      className="block font-semibold mb-1"
                    >
                      分类
                    </label>
                    <select
                      id="form-category"
                      value={formCategory}
                      onChange={(e) => setFormCategory(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 focus:outline-hidden"
                    >
                      <option value="coding">💻 编程与技术</option>
                      <option value="writing">✍️ 写作与润色</option>
                      <option value="translation">🌐 翻译与语境</option>
                      <option value="analysis">📊 分析与决策</option>
                      <option value="learning">🎓 学习与解答</option>
                      <option value="creative">🎨 创意与设计</option>
                      <option value="general">🧩 通用</option>
                    </select>
                  </div>
                  <div>
                    <label
                      htmlFor="form-desc"
                      className="block font-semibold mb-1"
                    >
                      简要说明
                    </label>
                    <input
                      id="form-desc"
                      type="text"
                      placeholder="适用场景简述"
                      value={formDescription}
                      onChange={(e) => setFormDescription(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 focus:outline-hidden"
                    />
                  </div>
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label htmlFor="form-body" className="block font-semibold">
                      模板正文 *
                    </label>
                    <span className="text-[11px] text-zinc-400">
                      支持用{" "}
                      <code className="text-indigo-500">{`{{变量名}}`}</code>{" "}
                      声明变量插槽
                    </span>
                  </div>
                  <textarea
                    id="form-body"
                    rows={6}
                    placeholder="请输入 Prompt 模板内容，例如：请将以下 {{language}} 代码重构为更优雅的实现：\n\n```\n{{code}}\n```"
                    value={formBody}
                    onChange={(e) => setFormBody(e.target.value)}
                    className="w-full p-3 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 font-mono text-xs focus:outline-hidden focus:ring-2 focus:ring-indigo-500/30"
                  />
                </div>

                {/* 实时识别的插槽变量 */}
                {detectedVariables.length > 0 && (
                  <div className="flex items-center gap-1.5 flex-wrap p-2 rounded-xl bg-indigo-50/50 dark:bg-indigo-950/30 border border-indigo-200/40 dark:border-indigo-800/40">
                    <span className="text-[11px] text-indigo-600 dark:text-indigo-400 font-medium">
                      已识别变量:
                    </span>
                    {detectedVariables.map((v) => (
                      <span
                        key={v}
                        className="px-2 py-0.5 rounded-md bg-white dark:bg-zinc-900 border border-indigo-200 dark:border-indigo-800 text-[11px] font-mono text-indigo-700 dark:text-indigo-300"
                      >
                        {`{{${v}}}`}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-zinc-100 dark:border-zinc-800">
                <button
                  type="button"
                  onClick={() => setEditModalOpen(false)}
                  className="px-3.5 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 text-xs font-medium hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  取消
                </button>
                <button
                  type="button"
                  disabled={saving}
                  onClick={handleSave}
                  className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 disabled:opacity-50"
                >
                  {saving && <Loader2 className="size-3.5 animate-spin" />}
                  <span>保存模板</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 变量填写与渲染预览弹窗 */}
        {renderModalOpen && activeTemplate && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
            <div className="relative w-full max-w-2xl rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 p-6 shadow-2xl space-y-4 max-h-[90vh] flex flex-col">
              <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 pb-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="size-4 text-indigo-500" />
                  <h2 className="text-base font-bold">
                    使用模板: {activeTemplate.title}
                  </h2>
                </div>
                <button
                  type="button"
                  onClick={() => setRenderModalOpen(false)}
                  className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 text-xs"
                >
                  ✕
                </button>
              </div>

              <div className="overflow-y-auto space-y-4 pr-1 text-xs">
                {/* 智能填充入口 */}
                <div className="p-3 rounded-xl border border-indigo-200/60 dark:border-indigo-800/60 bg-indigo-50/40 dark:bg-indigo-950/20 space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 font-semibold text-indigo-700 dark:text-indigo-300">
                      <Wand2 className="size-3.5" />
                      <span>AI 智能填充变量</span>
                    </div>
                    <button
                      type="button"
                      disabled={smartFilling || !smartContext.trim()}
                      onClick={handleSmartFill}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-indigo-600 text-white text-[11px] font-medium hover:bg-indigo-700 disabled:opacity-40"
                    >
                      {smartFilling ? (
                        <Loader2 className="size-3 animate-spin" />
                      ) : (
                        <Sparkles className="size-3" />
                      )}
                      <span>智能解析</span>
                    </button>
                  </div>
                  <input
                    type="text"
                    placeholder="输入或粘贴您的原始需求文本，AI 将自动推断填写下方变量..."
                    value={smartContext}
                    onChange={(e) => setSmartContext(e.target.value)}
                    className="w-full px-3 py-1.5 rounded-lg border border-indigo-200 dark:border-indigo-800 bg-white dark:bg-zinc-900 text-xs focus:outline-hidden"
                  />
                </div>

                {/* 变量输入项 */}
                {activeTemplate.variables.length > 0 ? (
                  <div className="space-y-2.5">
                    <div className="font-semibold text-zinc-700 dark:text-zinc-300">
                      填写变量插槽:
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                      {activeTemplate.variables.map((v) => (
                        <div key={v} className="space-y-1">
                          <label
                            htmlFor={`var-${v}`}
                            className="block font-mono text-[11px] text-zinc-500 dark:text-zinc-400"
                          >
                            {`{{${v}}}`}
                          </label>
                          <input
                            id={`var-${v}`}
                            type="text"
                            placeholder={`输入 ${v} 的值`}
                            value={variableValues[v] || ""}
                            onChange={(e) =>
                              setVariableValues((prev) => ({
                                ...prev,
                                [v]: e.target.value,
                              }))
                            }
                            className="w-full px-3 py-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 font-mono text-xs focus:outline-hidden focus:ring-2 focus:ring-indigo-500/30"
                          />
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="text-zinc-400 text-xs">
                    该模板无额外变量插槽，可直接复制使用。
                  </p>
                )}

                {/* 实时渲染预览 */}
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-zinc-700 dark:text-zinc-300">
                      渲染预览:
                    </span>
                    <button
                      type="button"
                      onClick={handleCopyRendered}
                      className="inline-flex items-center gap-1 text-indigo-600 dark:text-indigo-400 hover:underline text-[11px]"
                    >
                      <Copy className="size-3" />
                      <span>复制全文</span>
                    </button>
                  </div>
                  <div className="p-3 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-950 font-mono text-xs text-zinc-200 max-h-48 overflow-y-auto whitespace-pre-wrap">
                    {renderedText}
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-zinc-100 dark:border-zinc-800">
                <button
                  type="button"
                  onClick={() => setRenderModalOpen(false)}
                  className="px-3.5 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 text-xs font-medium hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  关闭
                </button>
                <button
                  type="button"
                  onClick={handleCopyRendered}
                  className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700"
                >
                  <Copy className="size-3.5" />
                  <span>一键复制 Prompt</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
