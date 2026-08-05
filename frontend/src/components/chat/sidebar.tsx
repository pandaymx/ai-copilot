"use client";

import {
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import type { ChatMessage } from "./message-bubble";

export interface ChatSession {
  id: string;
  title: string;
  updatedAt: number;
  /** 持久化的消息历史（仅用于存储，列表中不展示）。 */
  messages?: ChatMessage[];
}

interface SidebarProps {
  sessions: ChatSession[];
  activeId: string | null;
  collapsed: boolean;
  onSelect: (id: string) => void;
  onNew: () => void;
  onDelete: (id: string) => void;
  onToggleCollapsed: () => void;
}

function formatRelative(ts: number): string {
  const diff = Date.now() - ts;
  const min = Math.floor(diff / 60000);
  if (min < 1) return "刚刚";
  if (min < 60) return `${min} 分钟前`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr} 小时前`;
  return `${Math.floor(hr / 24)} 天前`;
}

/** 多会话管理侧边栏：新建 / 切换 / 删除，可折叠。 */
export function Sidebar({
  sessions,
  activeId,
  collapsed,
  onSelect,
  onNew,
  onDelete,
  onToggleCollapsed,
}: SidebarProps) {
  return (
    <aside
      className={cn(
        "fixed inset-y-0 left-0 z-30 flex w-64 flex-col border-r border-zinc-200/60 bg-zinc-50/80 backdrop-blur transition-transform duration-200 dark:border-zinc-800/70 dark:bg-zinc-900/60",
        "md:static md:translate-x-0",
        collapsed ? "-translate-x-full md:hidden" : "translate-x-0",
      )}
    >
      {/* 头部：新会话 + 折叠 */}
      <div className="flex items-center gap-2 border-b border-border p-3">
        <Button
          variant="secondary"
          size="sm"
          className="flex-1 justify-start gap-2"
          onClick={onNew}
        >
          <Plus className="size-4" />
          新会话
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="md:hidden"
          onClick={onToggleCollapsed}
          aria-label="收起侧边栏"
        >
          <PanelLeftClose className="size-4" />
        </Button>
      </div>

      {/* 会话列表 */}
      <nav className="flex-1 space-y-1 overflow-y-auto p-2 scrollbar-hidden">
        {sessions.length === 0 ? (
          <p className="px-3 py-6 text-center text-xs text-muted-foreground">
            还没有会话，点击「新会话」开始
          </p>
        ) : (
          sessions.map((s) => {
            const active = s.id === activeId;
            return (
              <div
                key={s.id}
                className="group flex items-center rounded-lg focus-within:ring-2 focus-within:ring-ring"
              >
                <button
                  type="button"
                  className={cn(
                    "flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition-colors outline-none",
                    active
                      ? "bg-primary/10 text-foreground"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground",
                  )}
                  onClick={() => onSelect(s.id)}
                >
                  <MessageSquare className="size-4 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">
                      {s.title || "新会话"}
                    </p>
                    <p className="text-[11px] text-muted-foreground/70">
                      {formatRelative(s.updatedAt)}
                    </p>
                  </div>
                </button>
                <button
                  type="button"
                  onClick={() => onDelete(s.id)}
                  className="mr-1 hidden size-7 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-destructive/10 hover:text-destructive group-hover:flex"
                  aria-label="删除会话"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </div>
            );
          })
        )}
      </nav>

      {/* 底部：展开（桌面折叠态入口） */}
      {collapsed && (
        <div className="hidden border-t border-border p-2 md:block">
          <Button
            variant="ghost"
            size="icon"
            className="mx-auto"
            onClick={onToggleCollapsed}
            aria-label="展开侧边栏"
          >
            <PanelLeftOpen className="size-4" />
          </Button>
        </div>
      )}
    </aside>
  );
}
