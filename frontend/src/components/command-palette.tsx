"use client";

import {
  Activity,
  Award,
  BarChart3,
  BookTemplate,
  Brain,
  Command as CommandIcon,
  Database,
  KeyRound,
  Moon,
  Plus,
  Search,
  Server,
  SunMedium,
  Users,
  Webhook,
  Workflow,
  Wrench,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { cn } from "@/lib/utils";
import {
  type CommandContext,
  type CommandItem,
  STATIC_COMMANDS,
} from "./command-registry";

const ICON_MAP: Record<string, React.ElementType> = {
  Plus,
  BookTemplate,
  BarChart3,
  Brain,
  Workflow,
  Wrench,
  Award,
  Database,
  KeyRound,
  Server,
  Webhook,
  Activity,
  Users,
  Moon,
  SunMedium,
};

const GROUP_LABELS: Record<string, string> = {
  action: "快捷操作",
  navigation: "功能导航",
  settings: "系统设置",
  theme: "外观主题",
};

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);

  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // 全局快捷键监听 Cmd+K / Ctrl+K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((prev) => !prev);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // 弹窗打开时聚焦输入框并重置选择
  useEffect(() => {
    if (open) {
      setQuery("");
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  // 过滤命令
  const filteredCommands = useMemo(() => {
    if (!query.trim()) return STATIC_COMMANDS;
    const q = query.toLowerCase().trim();
    return STATIC_COMMANDS.filter((cmd) => {
      return (
        cmd.title.toLowerCase().includes(q) ||
        cmd.description?.toLowerCase().includes(q) ||
        cmd.keywords.some((k) => k.toLowerCase().includes(q))
      );
    });
  }, [query]);

  // 选择索引越界重置
  useEffect(() => {
    if (selectedIndex >= filteredCommands.length) {
      setSelectedIndex(0);
    }
  }, [filteredCommands.length, selectedIndex]);

  const executeCommand = useCallback(
    (cmd: CommandItem) => {
      const ctx: CommandContext = {
        router,
        setTheme,
        currentTheme: theme,
        close: () => setOpen(false),
      };
      cmd.run(ctx);
    },
    [router, setTheme, theme],
  );

  // 键盘导航: 上下键 + 回车 + ESC
  const handleInputKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex((prev) =>
        prev < filteredCommands.length - 1 ? prev + 1 : 0,
      );
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex((prev) =>
        prev > 0 ? prev - 1 : filteredCommands.length - 1,
      );
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (filteredCommands[selectedIndex]) {
        executeCommand(filteredCommands[selectedIndex]);
      }
    } else if (e.key === "Escape") {
      e.preventDefault();
      setOpen(false);
    }
  };

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-start justify-center pt-[12vh] px-4 animate-in fade-in"
      onKeyDown={(e) => {
        if (e.key === "Escape") setOpen(false);
      }}
    >
      <button
        type="button"
        aria-label="关闭命令面板"
        className="fixed inset-0 bg-black/50 backdrop-blur-xs cursor-default w-full h-full border-0 p-0"
        onClick={() => setOpen(false)}
      />
      <div className="relative w-full max-w-xl rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden flex flex-col max-h-[70vh] animate-in zoom-in-95 z-10">
        {/* 顶部搜索输入 */}
        <div className="flex items-center gap-2.5 px-4 py-3.5 border-b border-zinc-100 dark:border-zinc-800/80">
          <Search className="size-4 text-zinc-400 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleInputKeyDown}
            placeholder="键入命令、功能或关键字快速检索..."
            className="w-full bg-transparent text-xs sm:text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 outline-hidden"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery("")}
              className="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
            >
              <X className="size-3.5" />
            </button>
          )}
          <kbd className="hidden sm:inline-flex items-center gap-0.5 rounded border border-zinc-200 dark:border-zinc-800 bg-zinc-100 dark:bg-zinc-800 px-1.5 py-0.5 text-[10px] font-mono text-zinc-500">
            ESC
          </kbd>
        </div>

        {/* 命令结果列表 */}
        <div ref={listRef} className="flex-1 overflow-y-auto p-2 space-y-1">
          {filteredCommands.length === 0 ? (
            <div className="p-8 text-center text-xs text-zinc-400">
              未找到匹配的命令或功能 “{query}”
            </div>
          ) : (
            filteredCommands.map((cmd, idx) => {
              const IconComp = ICON_MAP[cmd.iconName] || CommandIcon;
              const isSelected = idx === selectedIndex;

              return (
                <button
                  key={cmd.id}
                  type="button"
                  onClick={() => executeCommand(cmd)}
                  onMouseEnter={() => setSelectedIndex(idx)}
                  className={cn(
                    "w-full flex items-center justify-between p-2.5 rounded-2xl text-left transition-colors text-xs",
                    isSelected
                      ? "bg-purple-500/10 text-purple-600 dark:text-purple-400 dark:bg-purple-950/40 font-semibold"
                      : "text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800/60",
                  )}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={cn(
                        "size-7 rounded-xl flex items-center justify-center shrink-0",
                        isSelected
                          ? "bg-purple-500/20 text-purple-600 dark:text-purple-300"
                          : "bg-zinc-100 dark:bg-zinc-800 text-zinc-500",
                      )}
                    >
                      <IconComp className="size-3.5" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 truncate">
                        <span className="truncate">{cmd.title}</span>
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-400 font-normal">
                          {GROUP_LABELS[cmd.group] || cmd.group}
                        </span>
                      </div>
                      {cmd.description && (
                        <div className="text-[11px] text-zinc-400 dark:text-zinc-500 truncate font-normal">
                          {cmd.description}
                        </div>
                      )}
                    </div>
                  </div>

                  {cmd.shortcut && (
                    <kbd className="hidden sm:inline-block font-mono text-[10px] text-zinc-400 bg-zinc-100 dark:bg-zinc-800 px-1.5 py-0.5 rounded shrink-0">
                      {cmd.shortcut}
                    </kbd>
                  )}
                </button>
              );
            })
          )}
        </div>

        {/* 底部导航提示 */}
        <div className="flex items-center justify-between px-4 py-2 border-t border-zinc-100 dark:border-zinc-800/80 bg-zinc-50 dark:bg-zinc-950 text-[11px] text-zinc-400 font-mono">
          <div className="flex items-center gap-3">
            <span>↑↓ 导航选择</span>
            <span>↵ 执行确认</span>
            <span>ESC 关闭</span>
          </div>
          <div className="flex items-center gap-1">
            <CommandIcon className="size-3" />
            <span>Cmd+K / Ctrl+K 唤起</span>
          </div>
        </div>
      </div>
    </div>
  );
}
