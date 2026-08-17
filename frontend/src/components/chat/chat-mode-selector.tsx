"use client";

import {
  BookOpen,
  Bot,
  Check,
  ChevronDown,
  ChevronUp,
  Image as ImageIcon,
  MessageSquare,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export type ChatMode = "chat" | "agent" | "image" | "doc";

export interface ChatModeOption {
  id: ChatMode;
  label: string;
  shortLabel: string;
  description: string;
  icon: typeof MessageSquare;
  accent: {
    badge: string;
    iconBg: string;
    iconColor: string;
    activeBorder: string;
  };
}

export const CHAT_MODE_OPTIONS: ChatModeOption[] = [
  {
    id: "chat",
    label: "普通对话",
    shortLabel: "普通对话",
    description: "标准多轮问答与对话交互",
    icon: MessageSquare,
    accent: {
      badge:
        "bg-zinc-100 text-zinc-700 hover:bg-zinc-200/80 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700/80",
      iconBg: "bg-zinc-100 dark:bg-zinc-800",
      iconColor: "text-zinc-600 dark:text-zinc-400",
      activeBorder: "border-zinc-300 dark:border-zinc-700",
    },
  },
  {
    id: "agent",
    label: "Agent 模式",
    shortLabel: "Agent 模式",
    description: "工具调用、代码执行与自主检索",
    icon: Bot,
    accent: {
      badge:
        "bg-indigo-50 text-indigo-700 border-indigo-200/80 hover:bg-indigo-100/80 dark:bg-indigo-950/50 dark:text-indigo-300 dark:border-indigo-800/60 dark:hover:bg-indigo-900/50",
      iconBg: "bg-indigo-100 dark:bg-indigo-900/60",
      iconColor: "text-indigo-600 dark:text-indigo-400",
      activeBorder: "border-indigo-500/50 dark:border-indigo-500/50",
    },
  },
  {
    id: "image",
    label: "生图模式",
    shortLabel: "生图模式",
    description: "输入提示词快速生成高质量图像",
    icon: ImageIcon,
    accent: {
      badge:
        "bg-purple-50 text-purple-700 border-purple-200/80 hover:bg-purple-100/80 dark:bg-purple-950/50 dark:text-purple-300 dark:border-purple-800/60 dark:hover:bg-purple-900/50",
      iconBg: "bg-purple-100 dark:bg-purple-900/60",
      iconColor: "text-purple-600 dark:text-purple-400",
      activeBorder: "border-purple-500/50 dark:border-purple-500/50",
    },
  },
  {
    id: "doc",
    label: "文档对话",
    shortLabel: "文档对话",
    description: "基于挂载文档严格问答与页码引用",
    icon: BookOpen,
    accent: {
      badge:
        "bg-emerald-50 text-emerald-700 border-emerald-200/80 hover:bg-emerald-100/80 dark:bg-emerald-950/50 dark:text-emerald-300 dark:border-emerald-800/60 dark:hover:bg-emerald-900/50",
      iconBg: "bg-emerald-100 dark:bg-emerald-900/60",
      iconColor: "text-emerald-600 dark:text-emerald-400",
      activeBorder: "border-emerald-500/50 dark:border-emerald-500/50",
    },
  },
];

export interface ChatModeSelectorProps {
  imageMode: boolean;
  onImageModeChange: (enabled: boolean) => void;
  agentEnabled: boolean;
  onAgentEnabledChange: (enabled: boolean) => void;
  documentChatEnabled?: boolean;
  onDocumentChatEnabledChange?: (enabled: boolean) => void;
  disabled?: boolean;
  className?: string;
}

export function ChatModeSelector({
  imageMode,
  onImageModeChange,
  agentEnabled,
  onAgentEnabledChange,
  documentChatEnabled = false,
  onDocumentChatEnabledChange,
  disabled = false,
  className,
}: ChatModeSelectorProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // 计算当前选中的模式
  const currentMode: ChatMode = imageMode
    ? "image"
    : agentEnabled
      ? "agent"
      : documentChatEnabled
        ? "doc"
        : "chat";

  const currentOption =
    CHAT_MODE_OPTIONS.find((opt) => opt.id === currentMode) ??
    CHAT_MODE_OPTIONS[0];

  const TriggerIcon = currentOption.icon;

  // 点击外部自动关闭
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [open]);

  // ESC 键关闭
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && open) {
        setOpen(false);
      }
    }
    if (open) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const handleSelectMode = (mode: ChatMode) => {
    if (mode === "image") {
      onImageModeChange(true);
      onAgentEnabledChange(false);
      onDocumentChatEnabledChange?.(false);
    } else if (mode === "agent") {
      onImageModeChange(false);
      onAgentEnabledChange(true);
      onDocumentChatEnabledChange?.(false);
    } else if (mode === "doc") {
      onImageModeChange(false);
      onAgentEnabledChange(false);
      onDocumentChatEnabledChange?.(true);
    } else {
      onImageModeChange(false);
      onAgentEnabledChange(false);
      onDocumentChatEnabledChange?.(false);
    }
    setOpen(false);
  };

  return (
    <div
      ref={containerRef}
      className={cn("relative inline-block text-left", className)}
    >
      {/* 触发下拉按钮 */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((prev) => !prev)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={`当前对话模式: ${currentOption.label}，点击切换`}
        className={cn(
          "group inline-flex items-center gap-1.5 rounded-lg border px-2 py-1 text-xs font-medium transition-all duration-150 cursor-pointer disabled:cursor-not-allowed disabled:opacity-50",
          currentMode === "chat"
            ? "border-zinc-200/80 bg-zinc-50/80 text-zinc-600 hover:border-zinc-300 hover:bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-900/80 dark:text-zinc-400 dark:hover:border-zinc-700 dark:hover:bg-zinc-800/80"
            : currentOption.accent.badge,
          open && "ring-2 ring-indigo-500/20",
        )}
      >
        <TriggerIcon
          className={cn("size-3.5 shrink-0", currentOption.accent.iconColor)}
        />
        <span className="font-medium">{currentOption.shortLabel}</span>
        {open ? (
          <ChevronUp className="size-3 opacity-60 transition-transform" />
        ) : (
          <ChevronDown className="size-3 opacity-60 transition-transform" />
        )}
      </button>

      {/* 弹出菜单（向上弹出） */}
      {open && (
        <div
          role="listbox"
          aria-label="选择对话模式"
          className="absolute bottom-full left-0 z-50 mb-1.5 min-w-[220px] origin-bottom-left rounded-xl border border-zinc-200/90 bg-white/95 p-1 shadow-lg shadow-zinc-900/10 backdrop-blur-md transition-all animate-in fade-in zoom-in-95 dark:border-zinc-800/90 dark:bg-zinc-900/95 dark:shadow-black/40"
        >
          <div className="px-2 py-1 text-[10px] font-semibold tracking-wider text-zinc-400 uppercase dark:text-zinc-500">
            对话模式
          </div>
          <div className="space-y-0.5">
            {CHAT_MODE_OPTIONS.map((opt) => {
              const isSelected = opt.id === currentMode;
              const Icon = opt.icon;
              return (
                <button
                  key={opt.id}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => handleSelectMode(opt.id)}
                  className={cn(
                    "flex w-full items-center gap-2.5 rounded-lg px-2 py-1.5 text-left text-xs transition-colors cursor-pointer",
                    isSelected
                      ? "bg-zinc-100 font-semibold text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100"
                      : "text-zinc-600 hover:bg-zinc-50 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800/60 dark:hover:text-zinc-200",
                  )}
                >
                  <span
                    className={cn(
                      "flex size-6 shrink-0 items-center justify-center rounded-md",
                      opt.accent.iconBg,
                      opt.accent.iconColor,
                    )}
                  >
                    <Icon className="size-3.5" />
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="truncate">{opt.label}</span>
                      {isSelected && (
                        <Check className="size-3.5 shrink-0 text-indigo-600 dark:text-indigo-400 ml-1.5" />
                      )}
                    </div>
                    <p className="truncate text-[11px] font-normal text-zinc-400 dark:text-zinc-500">
                      {opt.description}
                    </p>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
