"use client";

import { ChevronRight, Pin, PinOff } from "lucide-react";
import { useState } from "react";
import type { MessageBookmark } from "@/lib/bookmark-api";
import { cn } from "@/lib/utils";

interface PinnedBarProps {
  pinnedMessages: MessageBookmark[];
  onUnpin: (messageId: string) => void;
  onScrollTo?: (messageId: string) => void;
}

export function PinnedBar({
  pinnedMessages,
  onUnpin,
  onScrollTo,
}: PinnedBarProps) {
  const [collapsed, setCollapsed] = useState(false);

  if (!pinnedMessages || pinnedMessages.length === 0) return null;

  return (
    <div className="sticky top-0 z-20 mx-auto w-full max-w-4xl px-4 py-2">
      <div className="rounded-2xl border border-amber-500/30 bg-amber-50/90 dark:bg-amber-950/40 p-2.5 shadow-md backdrop-blur-md transition-all text-xs">
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => setCollapsed(!collapsed)}
            className="flex items-center gap-2 font-bold text-amber-700 dark:text-amber-300 hover:opacity-80 transition-opacity"
          >
            <div className="flex size-5 items-center justify-center rounded-lg bg-amber-500/20 text-amber-600 dark:text-amber-400">
              <Pin className="size-3" />
            </div>
            <span>会话置顶 ({pinnedMessages.length})</span>
            <ChevronRight
              className={cn(
                "size-3.5 transition-transform duration-200",
                !collapsed && "rotate-90",
              )}
            />
          </button>

          <span className="text-[10px] text-amber-600/70 dark:text-amber-400/70 font-mono hidden sm:inline">
            点击消息快速定位
          </span>
        </div>

        {!collapsed && (
          <div className="mt-2 space-y-1.5 max-h-40 overflow-y-auto pr-1">
            {pinnedMessages.map((msg) => (
              <div
                key={msg.id || msg.messageId}
                className="flex items-center justify-between gap-2 rounded-xl bg-white/70 dark:bg-zinc-900/60 p-2 border border-amber-500/20 transition-colors hover:bg-white dark:hover:bg-zinc-900"
              >
                <button
                  type="button"
                  onClick={() => onScrollTo?.(msg.messageId)}
                  className="flex items-center gap-2 min-w-0 text-left flex-1"
                >
                  <span className="px-1.5 py-0.2 rounded font-mono text-[9px] font-bold uppercase bg-amber-500/10 text-amber-600 dark:text-amber-400 shrink-0">
                    {msg.role}
                  </span>
                  <span className="truncate text-[11px] text-zinc-700 dark:text-zinc-300">
                    {msg.content}
                  </span>
                </button>

                <button
                  type="button"
                  onClick={() => onUnpin(msg.messageId)}
                  title="取消置顶"
                  className="p-1 rounded-lg text-zinc-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition-colors shrink-0"
                >
                  <PinOff className="size-3" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
