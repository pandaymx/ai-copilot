"use client";

import {
  BookOpen,
  Bot,
  BrainCircuit,
  Check,
  Code2,
  Cpu,
  Eye,
  Layers,
  Palette,
  Plus,
  Search,
  ShieldCheck,
  Sliders,
  Sparkles,
  TestTube2,
  Trash2,
  Wand2,
  X,
  Zap,
} from "lucide-react";
import type React from "react";
import { useCallback, useEffect, useId, useState } from "react";
import {
  type CreatePersonaPayload,
  createPersonaApi,
  deletePersonaApi,
  fetchPersonasApi,
  matchPersonaApi,
  type Persona,
} from "@/lib/api";

interface PersonaMarketModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedPersona: Persona | null;
  onSelectPersona: (persona: Persona | null) => void;
}

const CATEGORIES = [
  { id: "ALL", label: "全部角色", icon: Layers },
  { id: "开发架构", label: "开发架构", icon: Code2 },
  { id: "产品设计", label: "产品设计", icon: Zap },
  { id: "测试质量", label: "测试质量", icon: TestTube2 },
  { id: "文档写作", label: "文档写作", icon: BookOpen },
  { id: "安全审计", label: "安全审计", icon: ShieldCheck },
  { id: "界面设计", label: "界面设计", icon: Palette },
  { id: "性能调优", label: "性能调优", icon: Cpu },
  { id: "CUSTOM", label: "我的自定义", icon: Bot },
];

export function PersonaMarketModal({
  isOpen,
  onClose,
  selectedPersona,
  onSelectPersona,
}: PersonaMarketModalProps) {
  const [personas, setPersonas] = useState<Persona[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState("ALL");
  const [searchKeyword, setSearchKeyword] = useState("");

  // 智能匹配状态
  const [matchGoal, setMatchGoal] = useState("");
  const [matching, setMatching] = useState(false);
  const [matchResult, setMatchResult] = useState<{
    personaId: string;
    reason: string;
    confidence: number;
  } | null>(null);

  // 展开查看 Prompt 预览
  const [expandedPromptId, setExpandedPromptId] = useState<string | null>(null);

  // 创建自定义角色表单
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [creating, setCreating] = useState(false);
  const [formData, setFormData] = useState<CreatePersonaPayload>({
    name: "",
    description: "",
    avatar: "🤖",
    category: "开发架构",
    systemPrompt: "",
    temperature: 0.7,
    tags: [],
  });
  const [tagInput, setTagInput] = useState("");

  // Unique Form Field IDs
  const avatarInputId = useId();
  const nameInputId = useId();
  const categorySelectId = useId();
  const temperatureInputId = useId();
  const descriptionInputId = useId();
  const systemPromptInputId = useId();
  const tagInputFieldId = useId();

  const loadPersonas = useCallback(async () => {
    setLoading(true);
    const categoryParam =
      selectedCategory === "CUSTOM" || selectedCategory === "ALL"
        ? undefined
        : selectedCategory;
    const res = await fetchPersonasApi(categoryParam, searchKeyword);
    if (res) {
      if (selectedCategory === "CUSTOM") {
        setPersonas(res.filter((p) => !p.isBuiltin));
      } else {
        setPersonas(res);
      }
    }
    setLoading(false);
  }, [selectedCategory, searchKeyword]);

  useEffect(() => {
    if (isOpen) {
      void loadPersonas();
    }
  }, [isOpen, loadPersonas]);

  // 智能匹配人设
  const handleSmartMatch = async () => {
    if (!matchGoal.trim()) return;
    setMatching(true);
    const res = await matchPersonaApi(matchGoal.trim());
    if (res?.recommendedPersona) {
      setMatchResult({
        personaId: res.recommendedPersona.id,
        reason: res.reason,
        confidence: res.confidence,
      });
      // 自动滚动或选中
      setExpandedPromptId(res.recommendedPersona.id);
    }
    setMatching(false);
  };

  // 创建自定义角色
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim() || !formData.systemPrompt.trim()) return;
    setCreating(true);
    const created = await createPersonaApi(formData);
    if (created) {
      setShowCreateForm(false);
      setFormData({
        name: "",
        description: "",
        avatar: "🤖",
        category: "开发架构",
        systemPrompt: "",
        temperature: 0.7,
        tags: [],
      });
      void loadPersonas();
    }
    setCreating(false);
  };

  // 删除自定义角色
  const handleDeletePersona = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm("确定要删除这个自定义角色吗？")) return;
    const success = await deletePersonaApi(id);
    if (success) {
      if (selectedPersona?.id === id) {
        onSelectPersona(null);
      }
      void loadPersonas();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative flex flex-col w-full max-w-5xl h-[85vh] max-h-[850px] bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/50">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-violet-500/10 text-violet-600 dark:text-violet-400 border border-violet-500/20">
              <BrainCircuit className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                智能体角色市场
                <span className="text-xs font-normal px-2 py-0.5 rounded-full bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300">
                  Persona Store
                </span>
              </h2>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                切换专业智能体人设，注入专属系统提示词、思考温度与工具策略
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {selectedPersona && (
              <button
                type="button"
                onClick={() => onSelectPersona(null)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800/60 rounded-lg hover:bg-amber-100 dark:hover:bg-amber-900/60 transition-colors"
              >
                <span>
                  当前: {selectedPersona.avatar} {selectedPersona.name}
                </span>
                <X className="w-3.5 h-3.5" />
                <span>重置为默认</span>
              </button>
            )}
            <button
              type="button"
              onClick={() => setShowCreateForm(!showCreateForm)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors shadow-sm"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>{showCreateForm ? "返回市场" : "自建人设"}</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              className="p-2 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {showCreateForm ? (
          /* 自定义人设创建表单 */
          <div className="flex-1 overflow-y-auto p-6">
            <form
              onSubmit={handleCreateSubmit}
              className="max-w-2xl mx-auto space-y-5"
            >
              <div className="flex items-center justify-between pb-3 border-b border-zinc-100 dark:border-zinc-800">
                <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                  创建新的自定义智能体角色
                </h3>
                <span className="text-xs text-zinc-400">
                  仅您本人可见与使用
                </span>
              </div>

              <div className="grid grid-cols-6 gap-4">
                <div className="col-span-1">
                  <label
                    htmlFor={avatarInputId}
                    className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                  >
                    图标
                  </label>
                  <input
                    id={avatarInputId}
                    type="text"
                    value={formData.avatar}
                    onChange={(e) =>
                      setFormData({ ...formData, avatar: e.target.value })
                    }
                    className="w-full text-center text-xl px-2 py-2 border border-zinc-200 dark:border-zinc-700 rounded-lg bg-zinc-50 dark:bg-zinc-800 focus:outline-none focus:ring-2 focus:ring-violet-500"
                    placeholder="🤖"
                    maxLength={4}
                  />
                </div>

                <div className="col-span-3">
                  <label
                    htmlFor={nameInputId}
                    className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                  >
                    角色名称 <span className="text-rose-500">*</span>
                  </label>
                  <input
                    id={nameInputId}
                    type="text"
                    required
                    value={formData.name}
                    onChange={(e) =>
                      setFormData({ ...formData, name: e.target.value })
                    }
                    placeholder="如：K8s 运维专家 / 心理学顾问"
                    className="w-full px-3 py-2 text-sm border border-zinc-200 dark:border-zinc-700 rounded-lg bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-violet-500"
                  />
                </div>

                <div className="col-span-2">
                  <label
                    htmlFor={categorySelectId}
                    className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                  >
                    所属分类
                  </label>
                  <select
                    id={categorySelectId}
                    value={formData.category}
                    onChange={(e) =>
                      setFormData({ ...formData, category: e.target.value })
                    }
                    className="w-full px-3 py-2 text-sm border border-zinc-200 dark:border-zinc-700 rounded-lg bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-violet-500"
                  >
                    <option value="开发架构">开发架构</option>
                    <option value="产品设计">产品设计</option>
                    <option value="测试质量">测试质量</option>
                    <option value="文档写作">文档写作</option>
                    <option value="安全审计">安全审计</option>
                    <option value="界面设计">界面设计</option>
                    <option value="性能调优">性能调优</option>
                    <option value="通识助手">通识助手</option>
                  </select>
                </div>
              </div>

              <div>
                <label
                  htmlFor={temperatureInputId}
                  className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                >
                  推荐发散温度 (Temperature: {formData.temperature})
                </label>
                <div className="flex items-center gap-3">
                  <input
                    id={temperatureInputId}
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={formData.temperature}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        temperature: Number.parseFloat(e.target.value),
                      })
                    }
                    className="flex-1 accent-violet-600"
                  />
                  <span className="text-xs text-zinc-400 font-mono w-10">
                    {formData.temperature?.toFixed(2)}
                  </span>
                </div>
              </div>

              <div>
                <label
                  htmlFor={descriptionInputId}
                  className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                >
                  角色简介与定位
                </label>
                <input
                  id={descriptionInputId}
                  type="text"
                  value={formData.description}
                  onChange={(e) =>
                    setFormData({ ...formData, description: e.target.value })
                  }
                  placeholder="用一句话描述该角色的核心特长与服务场景..."
                  className="w-full px-3 py-2 text-sm border border-zinc-200 dark:border-zinc-700 rounded-lg bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </div>

              <div>
                <label
                  htmlFor={systemPromptInputId}
                  className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                >
                  系统人设提示词 (System Prompt){" "}
                  <span className="text-rose-500">*</span>
                </label>
                <textarea
                  id={systemPromptInputId}
                  required
                  rows={6}
                  value={formData.systemPrompt}
                  onChange={(e) =>
                    setFormData({ ...formData, systemPrompt: e.target.value })
                  }
                  placeholder="详细描述该角色的思考方式、回答结构、语气风格与专业准则..."
                  className="w-full px-3 py-2 text-xs font-mono border border-zinc-200 dark:border-zinc-700 rounded-lg bg-zinc-50 dark:bg-zinc-800/80 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </div>

              <div>
                <label
                  htmlFor={tagInputFieldId}
                  className="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1"
                >
                  技能标签 (按 Enter 添加)
                </label>
                <div className="flex flex-wrap items-center gap-1.5 p-2 border border-zinc-200 dark:border-zinc-700 rounded-lg bg-white dark:bg-zinc-800">
                  {formData.tags?.map((tag) => (
                    <span
                      key={tag}
                      className="inline-flex items-center gap-1 px-2 py-0.5 text-xs bg-zinc-100 dark:bg-zinc-700 text-zinc-800 dark:text-zinc-200 rounded-md"
                    >
                      {tag}
                      <button
                        type="button"
                        onClick={() =>
                          setFormData({
                            ...formData,
                            tags: formData.tags?.filter((t) => t !== tag),
                          })
                        }
                        className="hover:text-rose-500"
                      >
                        ×
                      </button>
                    </span>
                  ))}
                  <input
                    id={tagInputFieldId}
                    type="text"
                    value={tagInput}
                    onChange={(e) => setTagInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && tagInput.trim()) {
                        e.preventDefault();
                        if (!formData.tags?.includes(tagInput.trim())) {
                          setFormData({
                            ...formData,
                            tags: [...(formData.tags || []), tagInput.trim()],
                          });
                        }
                        setTagInput("");
                      }
                    }}
                    placeholder="输入标签并回车..."
                    className="flex-1 min-w-[120px] text-xs bg-transparent focus:outline-none text-zinc-900 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowCreateForm(false)}
                  className="px-4 py-2 text-xs font-medium text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-lg transition-colors"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  className="px-5 py-2 text-xs font-semibold text-white bg-violet-600 hover:bg-violet-700 disabled:opacity-50 rounded-lg transition-colors shadow-sm"
                >
                  {creating ? "创建中..." : "确认创建角色"}
                </button>
              </div>
            </form>
          </div>
        ) : (
          /* 角色市场主浏览区 */
          <div className="flex-1 flex flex-col overflow-hidden">
            {/* 智能匹配与搜索栏 */}
            <div className="p-4 border-b border-zinc-100 dark:border-zinc-800 space-y-3 bg-white dark:bg-zinc-900">
              <div className="flex items-center gap-3">
                {/* 智能意图匹配输入框 */}
                <div className="flex-1 flex items-center gap-2 px-3 py-2 rounded-xl bg-violet-50/50 dark:bg-violet-950/20 border border-violet-200/60 dark:border-violet-800/40">
                  <Wand2 className="w-4 h-4 text-violet-600 dark:text-violet-400 shrink-0" />
                  <input
                    type="text"
                    value={matchGoal}
                    onChange={(e) => setMatchGoal(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleSmartMatch()}
                    placeholder="输入当前工作目标（如：编写单元测试/设计PRD/排查SQL慢查询），AI 智能匹配人设..."
                    className="flex-1 text-xs bg-transparent focus:outline-none text-zinc-800 dark:text-zinc-200 placeholder-zinc-400"
                  />
                  <button
                    type="button"
                    onClick={handleSmartMatch}
                    disabled={matching || !matchGoal.trim()}
                    className="px-3 py-1 text-xs font-medium text-white bg-violet-600 hover:bg-violet-700 disabled:opacity-50 rounded-lg transition-colors shrink-0"
                  >
                    {matching ? "匹配中..." : "智能匹配"}
                  </button>
                </div>

                {/* 传统关键词搜索 */}
                <div className="w-64 flex items-center gap-2 px-3 py-2 rounded-xl bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700">
                  <Search className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
                  <input
                    type="text"
                    value={searchKeyword}
                    onChange={(e) => setSearchKeyword(e.target.value)}
                    placeholder="按名称/标签搜索..."
                    className="w-full text-xs bg-transparent focus:outline-none text-zinc-900 dark:text-zinc-100"
                  />
                </div>
              </div>

              {/* 智能匹配结果提示条 */}
              {matchResult && (
                <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800/60 text-xs text-emerald-800 dark:text-emerald-200 animate-in fade-in slide-in-from-top-1">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400 shrink-0" />
                    <span>
                      推荐人设匹配度{" "}
                      <strong>
                        {Math.round(matchResult.confidence * 100)}%
                      </strong>
                      ：{matchResult.reason}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setMatchResult(null)}
                    className="text-emerald-600 hover:text-emerald-800 text-xs font-medium"
                  >
                    清除
                  </button>
                </div>
              )}

              {/* 分类 Tabs */}
              <div className="flex items-center gap-1 overflow-x-auto pb-1 scrollbar-none">
                {CATEGORIES.map((cat) => {
                  const Icon = cat.icon;
                  const active = selectedCategory === cat.id;
                  return (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => setSelectedCategory(cat.id)}
                      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors shrink-0 ${
                        active
                          ? "bg-violet-600 text-white shadow-sm"
                          : "text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                      }`}
                    >
                      <Icon className="w-3.5 h-3.5" />
                      <span>{cat.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 角色卡片网格 */}
            <div className="flex-1 overflow-y-auto p-4">
              {loading ? (
                <div className="flex items-center justify-center h-48 text-zinc-400 text-xs">
                  加载角色市场中...
                </div>
              ) : personas.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-48 text-zinc-400 text-xs gap-2">
                  <Bot className="w-8 h-8 opacity-40" />
                  <span>未找到匹配的智能体角色</span>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {personas.map((persona) => {
                    const isSelected = selectedPersona?.id === persona.id;
                    const isMatched = matchResult?.personaId === persona.id;
                    const isPromptExpanded = expandedPromptId === persona.id;

                    return (
                      <div
                        key={persona.id}
                        className={`group relative flex flex-col p-4 rounded-xl border transition-all duration-200 ${
                          isSelected
                            ? "bg-violet-50/70 dark:bg-violet-950/30 border-violet-500 shadow-md ring-1 ring-violet-500"
                            : isMatched
                              ? "bg-emerald-50/40 dark:bg-emerald-950/20 border-emerald-400 shadow-sm"
                              : "bg-white dark:bg-zinc-800/60 border-zinc-200 dark:border-zinc-700/80 hover:border-violet-300 dark:hover:border-violet-700 hover:shadow-sm"
                        }`}
                      >
                        {/* 顶栏图标、名称与分类 Badge */}
                        <div className="flex items-start justify-between gap-2 mb-2">
                          <div className="flex items-center gap-2.5">
                            <span
                              className="text-2xl select-none"
                              role="img"
                              aria-label={persona.name}
                            >
                              {persona.avatar || "🤖"}
                            </span>
                            <div>
                              <div className="flex items-center gap-1.5">
                                <h4 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                                  {persona.name}
                                </h4>
                                {persona.isBuiltin ? (
                                  <span className="text-[10px] px-1.5 py-0.2 rounded bg-zinc-100 dark:bg-zinc-700 text-zinc-600 dark:text-zinc-300 font-mono">
                                    官方
                                  </span>
                                ) : (
                                  <span className="text-[10px] px-1.5 py-0.2 rounded bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300 font-mono">
                                    自定义
                                  </span>
                                )}
                              </div>
                              <span className="text-[11px] text-zinc-400">
                                {persona.category}
                              </span>
                            </div>
                          </div>

                          {!persona.isBuiltin && (
                            <button
                              type="button"
                              onClick={(e) =>
                                handleDeletePersona(persona.id, e)
                              }
                              className="opacity-0 group-hover:opacity-100 p-1 text-zinc-400 hover:text-rose-500 transition-opacity"
                              title="删除自定义角色"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          )}
                        </div>

                        {/* 简介 */}
                        <p className="text-xs text-zinc-600 dark:text-zinc-300 line-clamp-2 mb-3 min-h-[32px]">
                          {persona.description}
                        </p>

                        {/* 技能标签 */}
                        {persona.tags && persona.tags.length > 0 && (
                          <div className="flex flex-wrap gap-1 mb-3">
                            {persona.tags.slice(0, 4).map((tag) => (
                              <span
                                key={tag}
                                className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-700/60 text-zinc-500 dark:text-zinc-400"
                              >
                                #{tag}
                              </span>
                            ))}
                          </div>
                        )}

                        {/* Prompt 展开预览区域 */}
                        {isPromptExpanded && (
                          <div className="p-2.5 mb-3 rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-100 dark:border-zinc-800 text-[11px] font-mono text-zinc-600 dark:text-zinc-300 max-h-36 overflow-y-auto whitespace-pre-wrap">
                            {persona.systemPrompt}
                          </div>
                        )}

                        {/* 底部参数与操作按钮 */}
                        <div className="mt-auto pt-2 flex items-center justify-between border-t border-zinc-100 dark:border-zinc-700/60">
                          <div className="flex items-center gap-2 text-[11px] text-zinc-400">
                            <span
                              className="flex items-center gap-0.5"
                              title="发散度"
                            >
                              <Sliders className="w-3 h-3" />
                              {persona.temperature ?? 0.7}
                            </span>
                            <button
                              type="button"
                              onClick={() =>
                                setExpandedPromptId(
                                  isPromptExpanded ? null : persona.id,
                                )
                              }
                              className="flex items-center gap-0.5 hover:text-zinc-600 dark:hover:text-zinc-200"
                            >
                              <Eye className="w-3 h-3" />
                              {isPromptExpanded ? "收起" : "Prompt"}
                            </button>
                          </div>

                          <button
                            type="button"
                            onClick={() => {
                              if (isSelected) {
                                onSelectPersona(null);
                              } else {
                                onSelectPersona(persona);
                                onClose();
                              }
                            }}
                            className={`flex items-center gap-1 px-3 py-1 text-xs font-semibold rounded-lg transition-colors ${
                              isSelected
                                ? "bg-emerald-600 hover:bg-emerald-700 text-white"
                                : "bg-violet-600 hover:bg-violet-700 text-white shadow-sm"
                            }`}
                          >
                            {isSelected ? (
                              <>
                                <Check className="w-3.5 h-3.5" />
                                <span>已激活</span>
                              </>
                            ) : (
                              <span>应用人设</span>
                            )}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
