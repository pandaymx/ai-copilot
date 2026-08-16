"use client";

import {
  Award,
  BarChart3,
  Bookmark,
  BookTemplate,
  Brain,
  Calendar,
  Check,
  CloudOff,
  Database,
  Edit2,
  GitFork,
  KeyRound,
  LogIn,
  LogOut,
  MessageSquare,
  PanelLeftClose,
  Plus,
  Search,
  Server,
  Sparkles,
  Trash2,
  Users,
  Webhook,
  Workflow,
  Wrench,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { BookmarksDrawer } from "@/components/chat/bookmarks-drawer";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  clearAuthSession,
  getStoredUser,
  type UserProfile,
} from "@/lib/auth-api";
import { cn } from "@/lib/utils";
import type { ChatMessage } from "./message-bubble";
import { TokenBudgetBar } from "./token-budget-bar";

export interface ChatSession {
  id: string;
  title: string;
  updatedAt: number;
  /** 持久化的消息历史（仅用于存储，列表中不展示）。 */
  messages?: ChatMessage[];
  /**
   * 标志位：true=标题为自动生成（首轮用问题生成），可在 AI 回答完成后被改写；
   * false=用户手动重命名或已被 AI 改写，不应被后续轮次覆盖。
   * 老数据未定义时一律视作 false（见 loadSessions）。
   */
  isDefaultTitle?: boolean;
  /** 继承上下文的父会话 ID */
  parentSessionId?: string;
  /** 继承的结构化上下文 JSON */
  inheritedContextJson?: string;
}

interface SidebarProps {
  sessions: ChatSession[];
  activeId: string | null;
  collapsed: boolean;
  loadingSessions?: boolean;
  isOfflineFallback?: boolean;
  activePersona?: import("@/lib/api").Persona | null;
  onSelect: (id: string) => void;
  onNew: () => void;
  onDelete: (id: string) => void;
  onRename?: (id: string, newTitle: string) => void;
  onInherit?: (sessionId: string) => void;
  onToggleCollapsed: () => void;
  onOpenSearch?: () => void;
  onOpenPersonaMarket?: () => void;
}

function formatRelative(ts: number): string {
  const diff = Date.now() - ts;
  const min = Math.floor(diff / 60000);
  if (min < 1) return "刚刚";
  if (min < 60) return `${min}m 前`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h 前`;
  return `${Math.floor(hr / 24)}d 前`;
}

/** 会话按时间分组算法 */
function groupSessions(sessions: ChatSession[]) {
  const now = new Date();
  const todayStart = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime();
  const yesterdayStart = todayStart - 86400000;
  const weekStart = todayStart - 6 * 86400000;

  const groups: { title: string; items: ChatSession[] }[] = [
    { title: "今天", items: [] },
    { title: "昨天", items: [] },
    { title: "过去 7 天", items: [] },
    { title: "更早", items: [] },
  ];

  for (const s of sessions) {
    if (s.updatedAt >= todayStart) {
      groups[0].items.push(s);
    } else if (s.updatedAt >= yesterdayStart) {
      groups[1].items.push(s);
    } else if (s.updatedAt >= weekStart) {
      groups[2].items.push(s);
    } else {
      groups[3].items.push(s);
    }
  }

  return groups.filter((g) => g.items.length > 0);
}

export function Sidebar({
  sessions,
  activeId,
  collapsed,
  loadingSessions,
  isOfflineFallback,
  activePersona,
  onSelect,
  onNew,
  onDelete,
  onRename,
  onInherit,
  onToggleCollapsed,
  onOpenSearch,
  onOpenPersonaMarket,
}: SidebarProps) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(null);
  const [showBookmarksDrawer, setShowBookmarksDrawer] = useState(false);

  useEffect(() => {
    setCurrentUser(getStoredUser());
  }, []);

  const groups = groupSessions(sessions);

  const startRename = (s: ChatSession, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingId(s.id);
    setEditTitle(s.title || "新会话");
  };

  const saveRename = (id: string, e?: React.FormEvent) => {
    e?.preventDefault();
    if (onRename && editTitle.trim()) {
      onRename(id, editTitle.trim());
    }
    setEditingId(null);
  };

  return (
    <aside
      className={cn(
        "fixed inset-y-0 left-0 z-30 h-full border-r border-zinc-200/70 bg-zinc-50/70 backdrop-blur-xl transition-all duration-300 ease-in-out dark:border-zinc-800/80 dark:bg-zinc-950/80",
        "md:relative md:z-auto md:shrink-0 md:translate-x-0 md:border-r md:bg-zinc-50/70 dark:md:bg-zinc-950/80",
        collapsed
          ? "-translate-x-full w-72 md:w-0 md:opacity-0 md:pointer-events-none md:overflow-hidden md:border-r-0"
          : "translate-x-0 w-72 md:w-72 md:opacity-100 md:pointer-events-auto",
      )}
    >
      <div className="flex h-full w-72 flex-col overflow-hidden">
        {/* 头部：品牌 Badge + 高能新建按钮 */}
        <div className="flex flex-col gap-3 border-b border-zinc-200/60 p-3.5 dark:border-zinc-800/60">
          <div className="flex items-center justify-between px-1">
            <div className="flex items-center gap-2">
              <div className="flex size-7 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 text-white shadow-md shadow-indigo-500/20">
                <Sparkles className="size-4" />
              </div>
              <span className="font-heading text-sm font-bold tracking-tight bg-gradient-to-r from-zinc-900 via-zinc-800 to-zinc-600 bg-clip-text text-transparent dark:from-white dark:via-zinc-200 dark:to-zinc-400">
                AI Copilot Pro
              </span>
            </div>
            <Button
              variant="ghost"
              size="icon-sm"
              className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors"
              onClick={onToggleCollapsed}
              aria-label="收起侧边栏"
              title="收起侧边栏 (⌘B)"
            >
              <PanelLeftClose className="size-4" />
            </Button>
          </div>

          {/* 核心新建按钮与搜索按钮 */}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onNew}
              className="group relative flex flex-1 items-center justify-center gap-2 overflow-hidden rounded-xl bg-gradient-to-r from-indigo-600 via-violet-600 to-indigo-700 px-3.5 py-2.5 text-xs font-semibold text-white shadow-md shadow-indigo-500/25 transition-all duration-200 hover:shadow-lg hover:shadow-indigo-500/35 hover:scale-[1.01] active:scale-[0.99]"
            >
              <span className="absolute inset-0 bg-white/10 opacity-0 transition-opacity group-hover:opacity-100" />
              <Plus className="size-4 transition-transform duration-200 group-hover:rotate-90" />
              <span>开启新会话</span>
            </button>
            {onOpenSearch && (
              <button
                type="button"
                onClick={onOpenSearch}
                title="搜索历史消息 (⌘K / Ctrl+K)"
                className="flex items-center justify-center rounded-xl border border-zinc-200/80 bg-white px-3 py-2.5 text-xs font-medium text-zinc-700 shadow-xs hover:bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
              >
                <Search className="size-4 text-zinc-500 dark:text-zinc-400" />
              </button>
            )}
          </div>

          {/* 实时 Token 配额与预算进度条 */}
          <div className="px-0.5 pt-1">
            <TokenBudgetBar />
          </div>

          {/* 🎭 智能体角色市场入口 */}
          {onOpenPersonaMarket && (
            <div className="px-0.5 pt-0.5">
              <button
                type="button"
                onClick={onOpenPersonaMarket}
                className="w-full group flex items-center justify-between rounded-xl border border-violet-200/70 bg-violet-50/50 px-3 py-2 text-xs font-semibold text-violet-700 shadow-xs transition-all duration-200 hover:border-violet-500/50 hover:bg-violet-100/70 hover:shadow-md hover:shadow-violet-500/10 dark:border-violet-800/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:border-violet-700 dark:hover:bg-violet-900/50"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-base select-none">
                    {activePersona ? activePersona.avatar : "🎭"}
                  </span>
                  <span className="truncate">
                    {activePersona ? activePersona.name : "智能体角色市场"}
                  </span>
                </div>
                <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-violet-200/70 dark:bg-violet-800/60 text-violet-800 dark:text-violet-200 shrink-0">
                  {activePersona ? "已激活" : "人设"}
                </span>
              </button>
            </div>
          )}

          {/* 知识库管理入口（任务 7.4） */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/knowledge"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-indigo-500/40 hover:bg-white hover:text-indigo-600 hover:shadow-md hover:shadow-indigo-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-indigo-500/50 dark:hover:bg-zinc-900 dark:hover:text-indigo-400"
            >
              <Database className="size-4 text-indigo-500 transition-transform duration-200 group-hover:scale-110" />
              <span>知识库管理</span>
            </Link>
          </div>

          {/* 长期记忆管理入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/memory"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-violet-500/40 hover:bg-white hover:text-violet-600 hover:shadow-md hover:shadow-violet-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-violet-500/50 dark:hover:bg-zinc-900 dark:hover:text-violet-400"
            >
              <Brain className="size-4 text-violet-500 transition-transform duration-200 group-hover:scale-110" />
              <span>长期记忆</span>
            </Link>
          </div>

          {/* AI 工作流编排入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/workflows"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-purple-500/40 hover:bg-white hover:text-purple-600 hover:shadow-md hover:shadow-purple-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-purple-500/50 dark:hover:bg-zinc-900 dark:hover:text-purple-400"
            >
              <Workflow className="size-4 text-purple-500 transition-transform duration-200 group-hover:scale-110" />
              <span>工作流编排</span>
            </Link>
          </div>

          {/* AI 评估与评测中心入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/evaluation"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-amber-500/40 hover:bg-white hover:text-amber-600 hover:shadow-md hover:shadow-amber-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-amber-500/50 dark:hover:bg-zinc-900 dark:hover:text-amber-400"
            >
              <Award className="size-4 text-amber-500 transition-transform duration-200 group-hover:scale-110" />
              <span>评测中心 (A/B)</span>
            </Link>
          </div>

          {/* 自定义工具 (DSL) 入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/tools"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-blue-500/40 hover:bg-white hover:text-blue-600 hover:shadow-md hover:shadow-blue-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-blue-500/50 dark:hover:bg-zinc-900 dark:hover:text-blue-400"
            >
              <Wrench className="size-4 text-blue-500 transition-transform duration-200 group-hover:scale-110" />
              <span>自定义工具</span>
            </Link>
          </div>

          {/* API Key 管理面板入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/settings/api-keys"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-emerald-500/40 hover:bg-white hover:text-emerald-600 hover:shadow-md hover:shadow-emerald-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-emerald-500/50 dark:hover:bg-zinc-900 dark:hover:text-emerald-400"
            >
              <KeyRound className="size-4 text-emerald-500 transition-transform duration-200 group-hover:scale-110" />
              <span>API Key 管理</span>
            </Link>
          </div>

          {/* Prompt 模板库入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/prompt-templates"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-indigo-500/40 hover:bg-white hover:text-indigo-600 hover:shadow-md hover:shadow-indigo-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-indigo-500/50 dark:hover:bg-zinc-900 dark:hover:text-indigo-400"
            >
              <BookTemplate className="size-4 text-indigo-500 transition-transform duration-200 group-hover:scale-110" />
              <span>Prompt 模板库</span>
            </Link>
          </div>

          {/* MCP Server 状态与接入入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/settings/mcp-server"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-cyan-500/40 hover:bg-white hover:text-cyan-600 hover:shadow-md hover:shadow-cyan-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-cyan-500/50 dark:hover:bg-zinc-900 dark:hover:text-cyan-400"
            >
              <Server className="size-4 text-cyan-500 transition-transform duration-200 group-hover:scale-110" />
              <span>MCP Server 接入</span>
            </Link>
          </div>

          {/* Webhook 订阅入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/settings/webhooks"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-purple-500/40 hover:bg-white hover:text-purple-600 hover:shadow-md hover:shadow-purple-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-purple-500/50 dark:hover:bg-zinc-900 dark:hover:text-purple-400"
            >
              <Webhook className="size-4 text-purple-500 transition-transform duration-200 group-hover:scale-110" />
              <span>Webhook 订阅</span>
            </Link>
          </div>

          {/* 对话洞察仪表盘入口 */}
          <div className="px-0.5 pt-0.5">
            <Link
              href="/insights"
              className="group flex items-center gap-2 rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-amber-500/40 hover:bg-white hover:text-amber-600 hover:shadow-md hover:shadow-amber-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-amber-500/50 dark:hover:bg-zinc-900 dark:hover:text-amber-400"
            >
              <BarChart3 className="size-4 text-amber-500 transition-transform duration-200 group-hover:scale-110" />
              <span>对话洞察分析</span>
            </Link>
          </div>

          {/* 我的收藏夹入口 */}
          <div className="px-0.5 pt-0.5">
            <button
              type="button"
              onClick={() => setShowBookmarksDrawer(true)}
              className="group flex items-center gap-2 w-full text-left rounded-xl border border-zinc-200/70 bg-white/70 px-3 py-2.5 text-xs font-semibold text-zinc-700 shadow-xs transition-all duration-200 hover:border-amber-500/40 hover:bg-white hover:text-amber-600 hover:shadow-md hover:shadow-amber-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/50 dark:text-zinc-200 dark:hover:border-amber-500/50 dark:hover:bg-zinc-900 dark:hover:text-amber-400"
            >
              <Bookmark className="size-4 text-amber-500 transition-transform duration-200 group-hover:scale-110" />
              <span>我的收藏夹</span>
            </button>
          </div>
        </div>

        {/* 会话列表：分组滚动展示 */}
        <nav className="flex-1 space-y-4 overflow-y-auto px-2 py-3 scrollbar-hidden">
          {/* 云端同步失败提示 */}
          {isOfflineFallback && (
            <div className="mx-1 mb-2 flex items-center gap-2 rounded-xl bg-amber-500/10 border border-amber-500/20 px-3 py-2 text-[11px] font-medium text-amber-700 dark:bg-amber-500/15 dark:border-amber-500/30 dark:text-amber-300 shadow-2xs">
              <CloudOff className="size-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
              <span className="flex-1 truncate">
                云端同步失败，使用本地缓存
              </span>
            </div>
          )}

          {loadingSessions ? (
            <div className="space-y-3 px-1 py-1">
              <div className="h-3 w-16 rounded bg-zinc-200/70 dark:bg-zinc-800/70 animate-pulse" />
              <div className="space-y-2">
                {[1, 2, 3, 4].map((i) => (
                  <div
                    key={i}
                    className="flex items-center gap-2.5 rounded-xl px-3 py-2.5 bg-zinc-200/40 dark:bg-zinc-900/40 animate-pulse"
                  >
                    <div className="size-4 rounded-md bg-zinc-300/60 dark:bg-zinc-800/60" />
                    <div className="flex-1 space-y-1.5 min-w-0">
                      <div className="h-3 w-3/4 rounded bg-zinc-300/60 dark:bg-zinc-800/60" />
                      <div className="h-2 w-1/3 rounded bg-zinc-200/80 dark:bg-zinc-800/40" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : sessions.length === 0 ? (
            <div className="flex flex-col items-center justify-center px-4 py-12 text-center">
              <div className="flex size-10 items-center justify-center rounded-2xl bg-zinc-200/50 text-zinc-400 dark:bg-zinc-800/50">
                <MessageSquare className="size-5" />
              </div>
              <p className="mt-3 text-xs font-medium text-zinc-500 dark:text-zinc-400">
                暂无历史会话
              </p>
              <p className="mt-1 text-[11px] text-zinc-400 dark:text-zinc-500">
                点击上方按钮开始第一次探索吧
              </p>
            </div>
          ) : (
            groups.map((group) => (
              <div key={group.title} className="space-y-1">
                <div className="flex items-center gap-1.5 px-3 py-1 text-[10px] font-semibold tracking-wider text-zinc-400 uppercase dark:text-zinc-500">
                  <Calendar className="size-3" />
                  <span>{group.title}</span>
                </div>
                {group.items.map((s) => {
                  const active = s.id === activeId;
                  const isEditing = editingId === s.id;

                  return (
                    <div
                      key={s.id}
                      data-session-id={s.id}
                      className={cn(
                        "group relative flex items-center rounded-xl transition-all duration-150",
                        active
                          ? "bg-white shadow-sm ring-1 ring-zinc-950/5 dark:bg-zinc-900/90 dark:ring-white/10"
                          : "hover:bg-zinc-200/50 dark:hover:bg-zinc-900/40",
                      )}
                    >
                      {/* 活动态左侧侧边发光条 */}
                      {active && (
                        <span className="absolute left-0 top-2 bottom-2 w-1 rounded-r-full bg-gradient-to-b from-indigo-500 to-purple-600 shadow-sm shadow-indigo-500/50" />
                      )}

                      {isEditing ? (
                        <form
                          onSubmit={(e) => saveRename(s.id, e)}
                          className="flex min-w-0 flex-1 items-center gap-1 px-3 py-1.5"
                        >
                          <input
                            type="text"
                            value={editTitle}
                            onChange={(e) => setEditTitle(e.target.value)}
                            className="min-w-0 flex-1 rounded-md border border-indigo-500 bg-transparent px-2 py-1 text-xs text-zinc-900 outline-none dark:text-zinc-100"
                            // biome-ignore lint/a11y/noAutofocus: 会话重命名输入框需自动聚焦
                            autoFocus
                            onBlur={() => saveRename(s.id)}
                          />
                          <button
                            type="submit"
                            className="rounded p-1 text-emerald-600 hover:bg-emerald-50 dark:text-emerald-400 dark:hover:bg-emerald-950/40"
                          >
                            <Check className="size-3.5" />
                          </button>
                        </form>
                      ) : (
                        <button
                          type="button"
                          className="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-xs transition-all outline-none"
                          onClick={() => onSelect(s.id)}
                        >
                          <MessageSquare
                            className={cn(
                              "size-4 shrink-0 transition-colors",
                              active
                                ? "text-indigo-600 dark:text-indigo-400"
                                : "text-zinc-400 group-hover:text-zinc-600 dark:text-zinc-500 dark:group-hover:text-zinc-300",
                            )}
                          />
                          <div className="min-w-0 flex-1">
                            <p
                              className={cn(
                                "truncate font-medium leading-tight",
                                active
                                  ? "text-zinc-900 dark:text-zinc-100"
                                  : "text-zinc-600 dark:text-zinc-400",
                              )}
                            >
                              {s.title || "新会话"}
                            </p>
                            <p className="mt-0.5 text-[10px] text-zinc-400 dark:text-zinc-500">
                              {formatRelative(s.updatedAt)}
                            </p>
                          </div>
                        </button>
                      )}

                      {!isEditing && (
                        <div className="mr-1.5 hidden items-center gap-0.5 group-hover:flex">
                          {onInherit && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                onInherit(s.id);
                              }}
                              className="flex size-6 items-center justify-center rounded-lg text-zinc-400 transition-colors hover:bg-indigo-500/10 hover:text-indigo-600 dark:hover:bg-indigo-500/20 dark:hover:text-indigo-400"
                              aria-label="以此为基础继承上下文"
                              title="以此为基础继承上下文"
                            >
                              <GitFork className="size-3" />
                            </button>
                          )}
                          {onRename && (
                            <button
                              type="button"
                              onClick={(e) => startRename(s, e)}
                              className="flex size-6 items-center justify-center rounded-lg text-zinc-400 transition-colors hover:bg-zinc-200/80 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
                              aria-label="重命名"
                            >
                              <Edit2 className="size-3" />
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              onDelete(s.id);
                            }}
                            className="flex size-6 items-center justify-center rounded-lg text-zinc-400 transition-colors hover:bg-rose-500/10 hover:text-rose-600 dark:hover:bg-rose-500/20 dark:hover:text-rose-400"
                            aria-label="删除会话"
                          >
                            <Trash2 className="size-3" />
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ))
          )}
        </nav>

        {/* 底部：用户个人中心与系统状态 */}
        <div className="border-t border-zinc-200/60 p-3 dark:border-zinc-800/60 space-y-2">
          {currentUser ? (
            <div className="flex items-center justify-between rounded-xl bg-white/60 p-2 shadow-2xs backdrop-blur dark:bg-zinc-900/60 border border-zinc-200/50 dark:border-zinc-800/50">
              <div className="flex items-center gap-2 min-w-0">
                <Avatar size="sm" className="ring-2 ring-indigo-500/30">
                  <AvatarFallback className="bg-gradient-to-tr from-indigo-500 to-purple-600 text-white text-xs font-semibold">
                    {currentUser.username.charAt(0).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <p className="truncate text-xs font-semibold text-zinc-800 dark:text-zinc-200">
                      {currentUser.username}
                    </p>
                    <span
                      className={cn(
                        "px-1.5 py-0.2 rounded text-[9px] font-bold uppercase",
                        currentUser.role === "ADMIN"
                          ? "bg-purple-100 text-purple-700 dark:bg-purple-950 dark:text-purple-300"
                          : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400",
                      )}
                    >
                      {currentUser.role}
                    </span>
                  </div>
                  <div className="flex items-center gap-1 text-[10px] text-emerald-600 dark:text-emerald-400 font-medium">
                    <span className="size-1.5 rounded-full bg-emerald-500 animate-pulse" />
                    <span>已连接</span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-1">
                {currentUser.role === "ADMIN" && (
                  <Link
                    href="/admin/users"
                    className="p-1 rounded-lg text-zinc-400 hover:text-purple-600 dark:hover:text-purple-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
                    title="RBAC 用户管理"
                  >
                    <Users className="size-3.5" />
                  </Link>
                )}
                <button
                  type="button"
                  onClick={() => {
                    clearAuthSession();
                    setCurrentUser(null);
                  }}
                  className="p-1 rounded-lg text-zinc-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
                  title="退出登录"
                >
                  <LogOut className="size-3.5" />
                </button>
              </div>
            </div>
          ) : (
            <Link
              href="/login"
              className="flex items-center justify-center gap-2 w-full py-2 px-3 rounded-xl bg-indigo-50/80 hover:bg-indigo-100/80 dark:bg-indigo-950/40 dark:hover:bg-indigo-900/50 border border-indigo-200/60 dark:border-indigo-800/60 text-xs font-semibold text-indigo-600 dark:text-indigo-400 transition-colors shadow-2xs"
            >
              <LogIn className="size-3.5" />
              <span>登录 / 注册统一身份</span>
            </Link>
          )}
        </div>
      </div>

      <BookmarksDrawer
        open={showBookmarksDrawer}
        onClose={() => setShowBookmarksDrawer(false)}
      />
    </aside>
  );
}
