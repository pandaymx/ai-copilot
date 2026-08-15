"use client";

import {
  ArrowLeft,
  CheckCircle2,
  Code,
  Edit2,
  Globe,
  Layers,
  MessageSquare,
  Play,
  Plus,
  Power,
  Search,
  Sliders,
  Sparkles,
  Trash2,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { CustomToolTester } from "@/components/tools/custom-tool-tester";
import { CustomToolWizard } from "@/components/tools/custom-tool-wizard";
import {
  type CustomToolItem,
  type CustomToolType,
  deleteCustomTool,
  listCustomTools,
  toggleCustomTool,
} from "@/lib/custom-tool-api";

export default function CustomToolsPage() {
  const [tools, setTools] = useState<CustomToolItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<"ALL" | CustomToolType>("ALL");
  const [statusFilter, setStatusFilter] = useState<
    "ALL" | "ENABLED" | "DISABLED"
  >("ALL");

  // 向导与测试弹窗
  const [wizardOpen, setWizardOpen] = useState(false);
  const [editingTool, setEditingTool] = useState<CustomToolItem | null>(null);
  const [testingTool, setTestingTool] = useState<CustomToolItem | null>(null);

  const fetchTools = useCallback(async () => {
    try {
      setLoading(true);
      const data = await listCustomTools();
      setTools(data);
    } catch (e) {
      console.error("加载自定义工具列表失败", e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTools();
  }, [fetchTools]);

  const handleToggle = async (id?: string) => {
    if (!id) return;
    try {
      await toggleCustomTool(id);
      setTools((prev) =>
        prev.map((t) => (t.id === id ? { ...t, enabled: !t.enabled } : t)),
      );
    } catch (e) {
      console.error("切换工具状态失败", e);
    }
  };

  const handleDelete = async (id?: string) => {
    if (!id) return;
    if (!confirm("确定要删除此自定义工具吗？删除后不可恢复。")) {
      return;
    }
    try {
      await deleteCustomTool(id);
      setTools((prev) => prev.filter((t) => t.id !== id));
      if (testingTool?.id === id) setTestingTool(null);
    } catch (e) {
      console.error("删除工具失败", e);
    }
  };

  // 统计指标
  const stats = useMemo(() => {
    const total = tools.length;
    const enabled = tools.filter((t) => t.enabled !== false).length;
    const httpCount = tools.filter((t) => t.type === "HTTP").length;
    const scriptCount = tools.filter((t) => t.type === "SCRIPT").length;
    const promptCount = tools.filter((t) => t.type === "PROMPT").length;
    return { total, enabled, httpCount, scriptCount, promptCount };
  }, [tools]);

  // 过滤列表
  const filteredTools = useMemo(() => {
    return tools.filter((t) => {
      // 搜索
      const matchSearch =
        search.trim() === "" ||
        t.name.toLowerCase().includes(search.toLowerCase()) ||
        t.displayName.toLowerCase().includes(search.toLowerCase()) ||
        t.description.toLowerCase().includes(search.toLowerCase());

      // 类型
      const matchType = typeFilter === "ALL" || t.type === typeFilter;

      // 状态
      const isEnabled = t.enabled !== false;
      const matchStatus =
        statusFilter === "ALL" ||
        (statusFilter === "ENABLED" && isEnabled) ||
        (statusFilter === "DISABLED" && !isEnabled);

      return matchSearch && matchType && matchStatus;
    });
  }, [tools, search, typeFilter, statusFilter]);

  const parseParamKeys = (schemaStr: string): string[] => {
    try {
      const parsed = JSON.parse(schemaStr || "{}");
      if (parsed.properties) {
        return Object.keys(parsed.properties);
      }
    } catch {}
    return [];
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-zinc-50 to-zinc-100 text-zinc-900 dark:from-zinc-950 dark:to-zinc-900 dark:text-zinc-100">
      {/* 顶部导航 */}
      <header className="sticky top-0 z-30 border-b border-zinc-200/80 bg-white/80 backdrop-blur-md dark:border-zinc-800/80 dark:bg-zinc-950/80">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3.5 sm:px-6">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="flex size-8 items-center justify-center rounded-lg border border-zinc-200 bg-white text-zinc-600 shadow-2xs hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 transition-colors"
            >
              <ArrowLeft className="size-4" />
            </Link>
            <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white shadow-md shadow-indigo-500/20">
              <Sparkles className="size-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-bold sm:text-lg">
                  自定义工具中心 (Custom Tool DSL)
                </h1>
                <span className="rounded-md bg-indigo-500/10 px-2 py-0.5 text-[10px] font-bold text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
                  Agent 热装配
                </span>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                运行时声明 HTTP API、Python/JS 脚本沙箱与 Prompt 虚拟工具
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => {
              setEditingTool(null);
              setWizardOpen(true);
            }}
            className="flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 px-4 py-2 text-xs font-semibold text-white shadow-md shadow-indigo-500/20 hover:from-indigo-600 hover:to-purple-700 transition-all hover:scale-[1.02]"
          >
            <Plus className="size-4" />
            <span>新建工具</span>
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-7xl p-4 sm:p-6 space-y-6">
        {/* 统计指标卡片 */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          <div className="rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-xs dark:border-zinc-800 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">全部工具</span>
              <Layers className="size-4 text-indigo-500" />
            </div>
            <p className="mt-2 text-2xl font-black text-zinc-900 dark:text-zinc-100">
              {stats.total}
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-xs dark:border-zinc-800 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">已启用 Agent</span>
              <CheckCircle2 className="size-4 text-emerald-500" />
            </div>
            <p className="mt-2 text-2xl font-black text-emerald-600 dark:text-emerald-400">
              {stats.enabled}
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-xs dark:border-zinc-800 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">HTTP API</span>
              <Globe className="size-4 text-blue-500" />
            </div>
            <p className="mt-2 text-2xl font-black text-blue-600 dark:text-blue-400">
              {stats.httpCount}
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-xs dark:border-zinc-800 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">脚本沙箱</span>
              <Code className="size-4 text-emerald-500" />
            </div>
            <p className="mt-2 text-2xl font-black text-emerald-600 dark:text-emerald-400">
              {stats.scriptCount}
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-xs dark:border-zinc-800 dark:bg-zinc-900/60">
            <div className="flex items-center justify-between text-zinc-500 dark:text-zinc-400">
              <span className="text-xs font-medium">Prompt 工具</span>
              <MessageSquare className="size-4 text-purple-500" />
            </div>
            <p className="mt-2 text-2xl font-black text-purple-600 dark:text-purple-400">
              {stats.promptCount}
            </p>
          </div>
        </div>

        {/* 筛选与搜索工具栏 */}
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-zinc-200/80 bg-white p-3.5 shadow-xs dark:border-zinc-800 dark:bg-zinc-900/60">
          <div className="flex flex-1 items-center gap-2 min-w-[200px] max-w-md rounded-xl border border-zinc-200 bg-zinc-50/80 px-3 py-1.5 text-xs dark:border-zinc-700 dark:bg-zinc-800">
            <Search className="size-4 text-zinc-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索工具名称、函数名或功能说明..."
              className="w-full bg-transparent text-zinc-900 placeholder:text-zinc-400 focus:outline-none dark:text-zinc-100"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {/* 类型切换 */}
            <div className="flex rounded-xl bg-zinc-100 p-1 dark:bg-zinc-800">
              {(["ALL", "HTTP", "SCRIPT", "PROMPT"] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setTypeFilter(t)}
                  className={`rounded-lg px-3 py-1 text-xs font-medium transition-all ${
                    typeFilter === t
                      ? "bg-white text-zinc-900 shadow-2xs dark:bg-zinc-700 dark:text-zinc-100"
                      : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
                  }`}
                >
                  {t === "ALL" && "全部类型"}
                  {t === "HTTP" && "HTTP API"}
                  {t === "SCRIPT" && "脚本沙箱"}
                  {t === "PROMPT" && "Prompt 工具"}
                </button>
              ))}
            </div>

            {/* 状态切换 */}
            <select
              value={statusFilter}
              onChange={(e) =>
                setStatusFilter(
                  e.target.value as "ALL" | "ENABLED" | "DISABLED",
                )
              }
              className="rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs font-medium text-zinc-700 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200"
            >
              <option value="ALL">全部状态</option>
              <option value="ENABLED">仅已启用</option>
              <option value="DISABLED">仅已停用</option>
            </select>
          </div>
        </div>

        {/* 工具列表卡片网格 */}
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20">
            <div className="size-8 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
            <p className="mt-3 text-xs text-zinc-500">正在加载自定义工具...</p>
          </div>
        ) : filteredTools.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-3xl border border-dashed border-zinc-300 py-20 text-center dark:border-zinc-800">
            <div className="flex size-14 items-center justify-center rounded-2xl bg-zinc-100 dark:bg-zinc-800 text-zinc-400">
              <Sliders className="size-7" />
            </div>
            <h3 className="mt-4 text-sm font-bold text-zinc-800 dark:text-zinc-200">
              未找到符合条件的自定义工具
            </h3>
            <p className="mt-1 max-w-sm text-xs text-zinc-500 dark:text-zinc-400">
              {search || typeFilter !== "ALL" || statusFilter !== "ALL"
                ? "请调整筛选条件或搜索关键词"
                : "点击下方按钮创建第一个自定义工具，让 Agent 拥有专属技能"}
            </p>
            <button
              type="button"
              onClick={() => {
                setEditingTool(null);
                setWizardOpen(true);
              }}
              className="mt-5 flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 px-4 py-2 text-xs font-semibold text-white shadow-md shadow-indigo-500/20 hover:from-indigo-600 hover:to-purple-700"
            >
              <Plus className="size-4" />
              <span>新建自定义工具</span>
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filteredTools.map((tool) => {
              const isEnabled = tool.enabled !== false;
              const paramKeys = parseParamKeys(tool.parametersSchema);

              return (
                <div
                  key={tool.id}
                  className={`group relative flex flex-col justify-between rounded-2xl border bg-white p-5 shadow-xs transition-all duration-200 hover:shadow-md dark:bg-zinc-900/80 ${
                    isEnabled
                      ? "border-zinc-200/80 dark:border-zinc-800"
                      : "border-zinc-200/50 opacity-60 dark:border-zinc-800/50"
                  }`}
                >
                  <div className="space-y-3">
                    {/* 头部：类型徽章 + 开关 */}
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0">
                        <div
                          className={`flex size-8 shrink-0 items-center justify-center rounded-xl ${
                            tool.type === "HTTP"
                              ? "bg-blue-500/10 text-blue-600 dark:bg-blue-500/20 dark:text-blue-400"
                              : tool.type === "SCRIPT"
                                ? "bg-emerald-500/10 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400"
                                : "bg-purple-500/10 text-purple-600 dark:bg-purple-500/20 dark:text-purple-400"
                          }`}
                        >
                          {tool.type === "HTTP" && <Globe className="size-4" />}
                          {tool.type === "SCRIPT" && (
                            <Code className="size-4" />
                          )}
                          {tool.type === "PROMPT" && (
                            <MessageSquare className="size-4" />
                          )}
                        </div>
                        <div className="min-w-0">
                          <h4 className="truncate text-sm font-bold text-zinc-900 dark:text-zinc-100">
                            {tool.displayName || tool.name}
                          </h4>
                          <span className="font-mono text-[10px] text-indigo-500 font-semibold">
                            {tool.name}
                          </span>
                        </div>
                      </div>

                      {/* 启用状态 Toggle */}
                      <button
                        type="button"
                        onClick={() => handleToggle(tool.id)}
                        className={`flex items-center gap-1 rounded-full px-2.5 py-1 text-[10px] font-semibold transition-all ${
                          isEnabled
                            ? "bg-emerald-500/10 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400"
                            : "bg-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
                        }`}
                        title={
                          isEnabled
                            ? "已启用（点击停用）"
                            : "已停用（点击启用）"
                        }
                      >
                        <Power className="size-3" />
                        <span>{isEnabled ? "启用中" : "已停用"}</span>
                      </button>
                    </div>

                    {/* 描述 */}
                    <p className="line-clamp-2 text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
                      {tool.description || "暂无描述"}
                    </p>

                    {/* 参数标签 */}
                    {paramKeys.length > 0 && (
                      <div className="flex flex-wrap items-center gap-1 pt-1">
                        <span className="text-[10px] text-zinc-400">参数:</span>
                        {paramKeys.slice(0, 4).map((pk) => (
                          <span
                            key={pk}
                            className="rounded-md bg-zinc-100 px-1.5 py-0.5 font-mono text-[10px] text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300"
                          >
                            {pk}
                          </span>
                        ))}
                        {paramKeys.length > 4 && (
                          <span className="text-[10px] text-zinc-400">
                            +{paramKeys.length - 4}
                          </span>
                        )}
                      </div>
                    )}

                    {/* 详细配置简要 */}
                    <div className="rounded-xl bg-zinc-50 p-2 text-[11px] font-mono text-zinc-600 dark:bg-zinc-800/50 dark:text-zinc-400 truncate">
                      {tool.type === "HTTP" && (
                        <span>
                          <span className="font-bold text-blue-600">
                            {tool.httpConfig?.method || "GET"}
                          </span>{" "}
                          {tool.httpConfig?.url}
                        </span>
                      )}
                      {tool.type === "SCRIPT" && (
                        <span>
                          <span className="font-bold text-emerald-600">
                            {tool.scriptConfig?.language?.toUpperCase()}
                          </span>{" "}
                          沙箱安全脚本
                        </span>
                      )}
                      {tool.type === "PROMPT" && (
                        <span>
                          <span className="font-bold text-purple-600">
                            PROMPT
                          </span>{" "}
                          模板驱动
                        </span>
                      )}
                    </div>
                  </div>

                  {/* 底部操作栏 */}
                  <div className="mt-4 flex items-center justify-between border-t border-zinc-100 pt-3 dark:border-zinc-800/80">
                    <button
                      type="button"
                      onClick={() => setTestingTool(tool)}
                      className="flex items-center gap-1 text-xs font-semibold text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 dark:hover:text-emerald-300 transition-colors"
                    >
                      <Play className="size-3.5" />
                      <span>沙箱测试</span>
                    </button>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => {
                          setEditingTool(tool);
                          setWizardOpen(true);
                        }}
                        className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
                        title="编辑工具"
                      >
                        <Edit2 className="size-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(tool.id)}
                        className="rounded-lg p-1.5 text-zinc-400 hover:bg-rose-500/10 hover:text-rose-600 dark:hover:bg-rose-500/20 dark:hover:text-rose-400 transition-colors"
                        title="删除工具"
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </main>

      {/* 创建 / 编辑 向导弹窗 */}
      {wizardOpen && (
        <CustomToolWizard
          initialTool={editingTool}
          onClose={() => {
            setWizardOpen(false);
            setEditingTool(null);
          }}
          onSuccess={(_saved) => {
            setWizardOpen(false);
            setEditingTool(null);
            fetchTools();
          }}
        />
      )}

      {/* 独立快速测试抽屉/弹窗 */}
      {testingTool && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs animate-in fade-in duration-200">
          <div className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
            <div className="flex items-center justify-between border-b border-zinc-200/80 px-6 py-4 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <Play className="size-5 text-emerald-500" />
                <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                  测试运行: {testingTool.displayName || testingTool.name}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setTestingTool(null)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                ✕
              </button>
            </div>
            <div className="overflow-y-auto p-6">
              <CustomToolTester tool={testingTool} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
