"use client";

import {
  AlertCircle,
  CheckCircle2,
  Clock,
  FileCode,
  FileText,
  FolderGit2,
  Globe,
  Loader2,
  Plus,
  RefreshCw,
  RotateCcw,
  Trash2,
  X,
  Zap,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  type CreateSourcePayload,
  createKnowledgeSourceApi,
  deleteKnowledgeSourceApi,
  fetchKnowledgeSourcesApi,
  type KnowledgeSource,
  triggerKnowledgeSyncApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

const SOURCE_TYPE_META: Record<
  string,
  { name: string; color: string; icon: typeof Globe }
> = {
  GITHUB: {
    name: "GitHub 仓库",
    color:
      "text-zinc-800 bg-zinc-100 dark:bg-zinc-800 dark:text-zinc-200 border-zinc-300 dark:border-zinc-700",
    icon: FolderGit2,
  },
  WEBSITE: {
    name: "Web 文档站点",
    color:
      "text-blue-600 bg-blue-50 dark:bg-blue-950/60 dark:text-blue-400 border-blue-200 dark:border-blue-800",
    icon: Globe,
  },
  SITEMAP: {
    name: "Sitemap 站点地图",
    color:
      "text-cyan-600 bg-cyan-50 dark:bg-cyan-950/60 dark:text-cyan-400 border-cyan-200 dark:border-cyan-800",
    icon: Globe,
  },
  NOTION: {
    name: "Notion 知识库",
    color:
      "text-purple-600 bg-purple-50 dark:bg-purple-950/60 dark:text-purple-400 border-purple-200 dark:border-purple-800",
    icon: FileText,
  },
  CONFLUENCE: {
    name: "Confluence 空间",
    color:
      "text-indigo-600 bg-indigo-50 dark:bg-indigo-950/60 dark:text-indigo-400 border-indigo-200 dark:border-indigo-800",
    icon: FileCode,
  },
};

const CRON_PRESETS = [
  { label: "每 30 分钟自动同步", value: "0 */30 * * * ?" },
  { label: "每 2 小时自动同步", value: "0 0 */2 * * ?" },
  { label: "每天凌晨 0 点自动同步", value: "0 0 0 * * ?" },
  { label: "每周一凌晨同步", value: "0 0 0 ? * MON" },
];

export function KnowledgeSourceManager() {
  const [sources, setSources] = useState<KnowledgeSource[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [syncingMap, setSyncingMap] = useState<Record<string, boolean>>({});

  // 表单状态
  const [sourceType, setSourceType] = useState<
    "GITHUB" | "WEBSITE" | "NOTION" | "CONFLUENCE"
  >("GITHUB");
  const [name, setName] = useState("");
  const [cronExpression, setCronExpression] = useState("0 0 */2 * * ?");
  const [enabled, setEnabled] = useState(true);

  // GitHub 特定配置
  const [ghRepo, setGhRepo] = useState("");
  const [ghBranch, setGhBranch] = useState("main");
  const [ghPath, setGhPath] = useState("docs/");
  const [ghToken, setGhToken] = useState("");

  // Web 特定配置
  const [webUrl, setWebUrl] = useState("");
  const [webDepth, setWebDepth] = useState(2);
  const [webPattern, setWebPattern] = useState("");

  // Notion 特定配置
  const [notionToken, setNotionToken] = useState("");

  // Confluence 特定配置
  const [confUrl, setConfUrl] = useState("");
  const [confSpace, setConfSpace] = useState("");
  const [confToken, setConfToken] = useState("");

  const [submitting, setSubmitting] = useState(false);

  const loadSources = useCallback(async () => {
    setLoading(true);
    const data = await fetchKnowledgeSourcesApi();
    setSources(data);
    setLoading(false);
  }, []);

  useEffect(() => {
    void loadSources();
  }, [loadSources]);

  const resetForm = () => {
    setName("");
    setGhRepo("");
    setGhBranch("main");
    setGhPath("docs/");
    setGhToken("");
    setWebUrl("");
    setWebDepth(2);
    setWebPattern("");
    setNotionToken("");
    setConfUrl("");
    setConfSpace("");
    setConfToken("");
  };

  const handleCreateSource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      toast.error("请输入知识源名称");
      return;
    }

    let config: Record<string, unknown> = {};
    if (sourceType === "GITHUB") {
      if (!ghRepo.trim()) {
        toast.error("请输入 GitHub 仓库地址 (如 facebook/react)");
        return;
      }
      config = {
        repo: ghRepo.trim(),
        branch: ghBranch.trim() || "main",
        path: ghPath.trim(),
        token: ghToken.trim() || undefined,
      };
    } else if (sourceType === "WEBSITE") {
      if (!webUrl.trim()) {
        toast.error("请输入 Web 站点入口 URL");
        return;
      }
      config = {
        url: webUrl.trim(),
        maxDepth: webDepth,
        pathPattern: webPattern.trim() || undefined,
      };
    } else if (sourceType === "NOTION") {
      if (!notionToken.trim()) {
        toast.error("请输入 Notion API Token");
        return;
      }
      config = { apiKey: notionToken.trim() };
    } else if (sourceType === "CONFLUENCE") {
      if (!confUrl.trim()) {
        toast.error("请输入 Confluence Base URL");
        return;
      }
      config = {
        baseUrl: confUrl.trim(),
        spaceKey: confSpace.trim() || undefined,
        apiToken: confToken.trim() || undefined,
      };
    }

    setSubmitting(true);
    const payload: CreateSourcePayload = {
      name: name.trim(),
      sourceType,
      config,
      cronExpression,
      enabled,
    };

    const res = await createKnowledgeSourceApi(payload);
    setSubmitting(false);

    if (res) {
      toast.success("知识源添加成功！");
      setModalOpen(false);
      resetForm();
      void loadSources();
    } else {
      toast.error("添加知识源失败，请重试");
    }
  };

  const handleSyncNow = async (source: KnowledgeSource, force = false) => {
    setSyncingMap((prev) => ({ ...prev, [source.id]: true }));
    toast.info(`正在启动【${source.name}】增量同步...`);

    const result = await triggerKnowledgeSyncApi(source.id, force);
    setSyncingMap((prev) => ({ ...prev, [source.id]: false }));

    if (result?.success) {
      toast.success(
        `【${source.name}】同步完成！新增 ${result.addedCount} 篇，更新 ${result.updatedCount} 篇，跳过 ${result.skippedCount} 篇，清理 ${result.deletedCount} 篇`,
      );
      void loadSources();
    } else {
      toast.error(
        `【${source.name}】同步受阻：${result?.message || "网络异常"}`,
      );
    }
  };

  const handleDelete = async (id: string, sourceName: string) => {
    if (!confirm(`确定要删除知识源【${sourceName}】及其所有关联向量数据吗？`)) {
      return;
    }
    const ok = await deleteKnowledgeSourceApi(id);
    if (ok) {
      toast.success(`知识源【${sourceName}】已删除`);
      void loadSources();
    } else {
      toast.error("删除知识源失败");
    }
  };

  return (
    <div className="space-y-6">
      {/* 顶栏控制栏 */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between rounded-2xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900 shadow-2xs">
        <div>
          <div className="flex items-center gap-2">
            <RefreshCw className="size-5 text-indigo-600" />
            <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
              知识库自动增量同步数据源
            </h3>
            <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300">
              contentHash 增量过滤
            </span>
          </div>
          <p className="text-xs text-zinc-500 mt-1">
            支持定时与事件驱动拉取 GitHub 仓库、Web
            站点、Notion、Confluence，自动清理已删除的幽灵文档
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => void loadSources()}
            disabled={loading}
            className="flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-3 py-2 text-xs font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-200 transition-colors cursor-pointer"
          >
            <RotateCcw className={cn("size-3.5", loading && "animate-spin")} />
            <span>刷新状态</span>
          </button>

          <button
            type="button"
            onClick={() => {
              resetForm();
              setModalOpen(true);
            }}
            className="flex items-center gap-1.5 rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-bold text-white hover:bg-indigo-700 shadow-md shadow-indigo-600/20 transition-all cursor-pointer"
          >
            <Plus className="size-4" />
            <span>添加自动同步数据源</span>
          </button>
        </div>
      </div>

      {/* 知识源列表 */}
      {loading && sources.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center text-center">
          <Loader2 className="size-8 animate-spin text-indigo-500 mb-2" />
          <p className="text-xs text-zinc-500">正在获取已注册知识源...</p>
        </div>
      ) : sources.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-zinc-300 p-8 text-center dark:border-zinc-800">
          <Globe className="size-10 text-zinc-400 mb-2" />
          <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-200">
            尚未配置自动同步数据源
          </h4>
          <p className="text-xs text-zinc-500 mt-1 max-w-sm">
            点击上方「添加自动同步数据源」按钮，连接 GitHub 仓库或 Web
            站点以实现文档定期自动刷新。
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {sources.map((source) => {
            const meta = SOURCE_TYPE_META[source.sourceType] || {
              name: source.sourceType,
              color:
                "text-zinc-700 bg-zinc-100 border-zinc-200 dark:bg-zinc-800",
              icon: Globe,
            };
            const Icon = meta.icon;
            const isSyncing =
              source.status === "SYNCING" || syncingMap[source.id];

            return (
              <div
                key={source.id}
                className="flex flex-col justify-between rounded-2xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900 shadow-2xs hover:border-zinc-300 dark:hover:border-zinc-700 transition-all"
              >
                <div className="space-y-3">
                  {/* 顶部类型与状态 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span
                        className={cn(
                          "flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-bold",
                          meta.color,
                        )}
                      >
                        <Icon className="size-3.5" />
                        <span>{meta.name}</span>
                      </span>
                      <span className="font-mono text-[11px] text-zinc-400">
                        {source.id}
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      {isSyncing ? (
                        <span className="flex items-center gap-1 rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-bold text-indigo-600 dark:bg-indigo-950 dark:text-indigo-400">
                          <Loader2 className="size-3 animate-spin" />
                          <span>正在同步</span>
                        </span>
                      ) : source.status === "SUCCESS" ? (
                        <span className="flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-bold text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400">
                          <CheckCircle2 className="size-3" />
                          <span>已同步</span>
                        </span>
                      ) : source.status === "FAILED" ? (
                        <span className="flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs font-bold text-rose-600 dark:bg-rose-950 dark:text-rose-400">
                          <AlertCircle className="size-3" />
                          <span>同步失败</span>
                        </span>
                      ) : (
                        <span className="text-xs font-medium text-zinc-400">
                          待同步
                        </span>
                      )}
                    </div>
                  </div>

                  {/* 知识源名称与主要配置 */}
                  <div>
                    <h4 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                      {source.name}
                    </h4>
                    <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-zinc-500">
                      {source.config?.repo ? (
                        <span className="font-mono bg-zinc-100 px-1.5 py-0.5 rounded dark:bg-zinc-800">
                          repo: {String(source.config.repo)}
                        </span>
                      ) : null}
                      {source.config?.url ? (
                        <span className="truncate max-w-[240px] font-mono bg-zinc-100 px-1.5 py-0.5 rounded dark:bg-zinc-800">
                          url: {String(source.config.url)}
                        </span>
                      ) : null}
                      {source.config?.branch ? (
                        <span className="font-mono bg-zinc-100 px-1.5 py-0.5 rounded dark:bg-zinc-800">
                          branch: {String(source.config.branch)}
                        </span>
                      ) : null}
                    </div>
                  </div>

                  {/* 同步状态摘要与增量指标 */}
                  <div className="rounded-xl border border-zinc-100 bg-zinc-50/70 p-2.5 dark:border-zinc-800/80 dark:bg-zinc-950/40 text-xs space-y-1">
                    <div className="flex items-center justify-between text-zinc-600 dark:text-zinc-400">
                      <span className="flex items-center gap-1">
                        <Clock className="size-3" />
                        <span>
                          调度: {source.cronExpression || "0 0 */2 * * ?"}
                        </span>
                      </span>
                      <span className="font-bold text-zinc-800 dark:text-zinc-200">
                        已同步 {source.documentCount} 篇
                      </span>
                    </div>
                    {source.lastSyncStatus && (
                      <p className="text-[11px] text-zinc-500 truncate">
                        {source.lastSyncStatus}
                      </p>
                    )}
                  </div>
                </div>

                {/* 底部操作栏 */}
                <div className="mt-4 flex items-center justify-between border-t border-zinc-100 pt-3 dark:border-zinc-800">
                  <div className="text-[11px] text-zinc-400">
                    {source.lastSyncDurationMs
                      ? `耗时: ${source.lastSyncDurationMs}ms`
                      : "自动调度已开启"}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => void handleSyncNow(source, false)}
                      disabled={isSyncing}
                      className="flex items-center gap-1 rounded-xl bg-indigo-50 px-2.5 py-1.5 text-xs font-bold text-indigo-700 hover:bg-indigo-100 dark:bg-indigo-950/60 dark:text-indigo-300 transition-colors disabled:opacity-50 cursor-pointer"
                    >
                      {isSyncing ? (
                        <Loader2 className="size-3.5 animate-spin" />
                      ) : (
                        <Zap className="size-3.5" />
                      )}
                      <span>立即增量同步</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => void handleDelete(source.id, source.name)}
                      className="p-1.5 text-zinc-400 hover:text-rose-600 transition-colors"
                      title="删除知识源"
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

      {/* 添加知识源 Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in duration-200">
          <div className="flex w-full max-w-lg flex-col rounded-3xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
            <div className="flex items-center justify-between border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <Plus className="size-5 text-indigo-600" />
                <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                  新建自动同步知识源
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
              >
                <X className="size-4" />
              </button>
            </div>

            <form
              onSubmit={handleCreateSource}
              className="p-6 space-y-4 text-xs"
            >
              {/* 数据源类型选择 */}
              <div>
                <label
                  htmlFor="source-type-label"
                  id="source-type-label"
                  className="block font-bold text-zinc-700 dark:text-zinc-300 mb-1.5"
                >
                  数据源类型
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {(["GITHUB", "WEBSITE", "NOTION", "CONFLUENCE"] as const).map(
                    (type) => {
                      const meta = SOURCE_TYPE_META[type];
                      const isSelected = sourceType === type;
                      return (
                        <button
                          type="button"
                          key={type}
                          onClick={() => setSourceType(type)}
                          className={cn(
                            "flex items-center gap-2 rounded-xl border p-2.5 text-left transition-all",
                            isSelected
                              ? "border-indigo-600 bg-indigo-50/50 text-indigo-900 dark:border-indigo-500 dark:bg-indigo-950/40 dark:text-indigo-200 font-bold shadow-2xs"
                              : "border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300",
                          )}
                        >
                          <meta.icon className="size-4" />
                          <span>{meta.name}</span>
                        </button>
                      );
                    },
                  )}
                </div>
              </div>

              {/* 知识源名称 */}
              <div>
                <label
                  htmlFor="input-source-name"
                  className="block font-bold text-zinc-700 dark:text-zinc-300 mb-1"
                >
                  知识源名称 *
                </label>
                <input
                  id="input-source-name"
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="如：官方核心设计文档库 / 产品 API 文档"
                  className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 px-3.5 py-2 text-xs text-zinc-900 focus:border-indigo-500 focus:bg-white focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                />
              </div>

              {/* 类型特定配置项 */}
              {sourceType === "GITHUB" && (
                <div className="space-y-3 rounded-2xl border border-zinc-100 bg-zinc-50/50 p-3.5 dark:border-zinc-800/60 dark:bg-zinc-900/40">
                  <div>
                    <label
                      htmlFor="input-gh-repo"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      GitHub 仓库地址 (owner/repo) *
                    </label>
                    <input
                      id="input-gh-repo"
                      type="text"
                      required
                      value={ghRepo}
                      onChange={(e) => setGhRepo(e.target.value)}
                      placeholder="如：facebook/react 或 https://github.com/..."
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label
                        htmlFor="input-gh-branch"
                        className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                      >
                        分支 (Branch)
                      </label>
                      <input
                        id="input-gh-branch"
                        type="text"
                        value={ghBranch}
                        onChange={(e) => setGhBranch(e.target.value)}
                        placeholder="main"
                        className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                      />
                    </div>
                    <div>
                      <label
                        htmlFor="input-gh-path"
                        className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                      >
                        路径前缀 (Path)
                      </label>
                      <input
                        id="input-gh-path"
                        type="text"
                        value={ghPath}
                        onChange={(e) => setGhPath(e.target.value)}
                        placeholder="docs/"
                        className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                      />
                    </div>
                  </div>

                  <div>
                    <label
                      htmlFor="input-gh-token"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      GitHub Personal Access Token (可选，私有库或高频同步必填)
                    </label>
                    <input
                      id="input-gh-token"
                      type="password"
                      value={ghToken}
                      onChange={(e) => setGhToken(e.target.value)}
                      placeholder="ghp_************************"
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>
                </div>
              )}

              {sourceType === "WEBSITE" && (
                <div className="space-y-3 rounded-2xl border border-zinc-100 bg-zinc-50/50 p-3.5 dark:border-zinc-800/60 dark:bg-zinc-900/40">
                  <div>
                    <label
                      htmlFor="input-web-url"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      站点入口 URL *
                    </label>
                    <input
                      id="input-web-url"
                      type="url"
                      required
                      value={webUrl}
                      onChange={(e) => setWebUrl(e.target.value)}
                      placeholder="https://docs.spring.io/spring-boot/docs/current/reference/html/"
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label
                        htmlFor="input-web-depth"
                        className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                      >
                        爬取最大深度 (Max Depth)
                      </label>
                      <input
                        id="input-web-depth"
                        type="number"
                        min={1}
                        max={4}
                        value={webDepth}
                        onChange={(e) => setWebDepth(Number(e.target.value))}
                        className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                      />
                    </div>
                    <div>
                      <label
                        htmlFor="input-web-pattern"
                        className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                      >
                        URL 路径白名单正则
                      </label>
                      <input
                        id="input-web-pattern"
                        type="text"
                        value={webPattern}
                        onChange={(e) => setWebPattern(e.target.value)}
                        placeholder="如 .*/docs/.*"
                        className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                      />
                    </div>
                  </div>
                </div>
              )}

              {sourceType === "NOTION" && (
                <div className="space-y-3 rounded-2xl border border-zinc-100 bg-zinc-50/50 p-3.5 dark:border-zinc-800/60 dark:bg-zinc-900/40">
                  <div>
                    <label
                      htmlFor="input-notion-token"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      Notion Internal Integration Token *
                    </label>
                    <input
                      id="input-notion-token"
                      type="password"
                      required
                      value={notionToken}
                      onChange={(e) => setNotionToken(e.target.value)}
                      placeholder="secret_************************"
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>
                </div>
              )}

              {sourceType === "CONFLUENCE" && (
                <div className="space-y-3 rounded-2xl border border-zinc-100 bg-zinc-50/50 p-3.5 dark:border-zinc-800/60 dark:bg-zinc-900/40">
                  <div>
                    <label
                      htmlFor="input-conf-url"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      Confluence Base URL *
                    </label>
                    <input
                      id="input-conf-url"
                      type="url"
                      required
                      value={confUrl}
                      onChange={(e) => setConfUrl(e.target.value)}
                      placeholder="https://your-domain.atlassian.net"
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>
                  <div>
                    <label
                      htmlFor="input-conf-space"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      Space Key (空间代码，可选)
                    </label>
                    <input
                      id="input-conf-space"
                      type="text"
                      value={confSpace}
                      onChange={(e) => setConfSpace(e.target.value)}
                      placeholder="ENG / DOCS"
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>
                  <div>
                    <label
                      htmlFor="input-conf-token"
                      className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1"
                    >
                      API Token
                    </label>
                    <input
                      id="input-conf-token"
                      type="password"
                      value={confToken}
                      onChange={(e) => setConfToken(e.target.value)}
                      placeholder="Atlassian API Token"
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                    />
                  </div>
                </div>
              )}

              {/* 周期性同步频率 */}
              <div>
                <label
                  htmlFor="select-cron-expr"
                  className="block font-bold text-zinc-700 dark:text-zinc-300 mb-1"
                >
                  自动同步频率 (Cron)
                </label>
                <select
                  id="select-cron-expr"
                  value={cronExpression}
                  onChange={(e) => setCronExpression(e.target.value)}
                  className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 px-3 py-2 text-xs text-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
                >
                  {CRON_PRESETS.map((preset) => (
                    <option key={preset.value} value={preset.value}>
                      {preset.label} ({preset.value})
                    </option>
                  ))}
                </select>
              </div>

              {/* 启用自动同步开关 */}
              <div className="flex items-center justify-between rounded-xl border border-zinc-200 bg-zinc-50/50 p-3 dark:border-zinc-800 dark:bg-zinc-900">
                <div>
                  <span className="font-bold text-zinc-800 dark:text-zinc-200">
                    启用自动增量同步
                  </span>
                  <p className="text-[11px] text-zinc-500">
                    开启后将按上述 Cron 表达式在后台自动检测并刷新知识库
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                  className="size-4 rounded text-indigo-600 focus:ring-indigo-500 cursor-pointer"
                />
              </div>

              {/* 底部按钮 */}
              <div className="flex items-center justify-end gap-2 pt-2 border-t border-zinc-100 dark:border-zinc-800">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex items-center gap-1.5 rounded-xl bg-indigo-600 px-4 py-2 text-xs font-bold text-white hover:bg-indigo-700 shadow-md shadow-indigo-600/20 disabled:opacity-50 transition-all cursor-pointer"
                >
                  {submitting ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <Plus className="size-3.5" />
                  )}
                  <span>保存并创建数据源</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
