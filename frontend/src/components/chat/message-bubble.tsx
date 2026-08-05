"use client";

import { Bot, User } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { Markdown } from "./markdown";

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
}

interface MessageBubbleProps {
  message: ChatMessage;
  /** 是否为当前正在流式接收的助手消息（用于展示"思考中"状态）。 */
  streaming?: boolean;
}

/** 单条对话气泡：用户右对齐、AI 左对齐，含头像与 Markdown 渲染。 */
export function MessageBubble({ message, streaming }: MessageBubbleProps) {
  const isUser = message.role === "user";

  return (
    <div
      className={cn(
        "mx-auto flex w-full max-w-3xl gap-3 px-4 py-3 sm:px-6",
        isUser ? "flex-row-reverse" : "flex-row",
      )}
    >
      <Avatar
        size="sm"
        className={cn(
          "mt-0.5 shrink-0",
          // AI 思考时：头像带顺时针旋转的翡翠绿呼吸光环
          !isUser && streaming && "animate-[spin_3s_linear_infinite] ring-2 ring-emerald-500/60 ring-offset-2 ring-offset-background",
        )}
      >
        <AvatarFallback
          className={cn(
            isUser
              ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
              : "bg-linear-to-br from-emerald-500 to-teal-600 text-white",
          )}
        >
          {isUser ? <User className="size-4" /> : <Bot className="size-4" />}
        </AvatarFallback>
      </Avatar>

      <div
        className={cn(
          "relative flex min-w-0 max-w-[85%] flex-col gap-1",
          isUser ? "items-end" : "items-start",
        )}
      >
        {/* 暗夜流式流光：AI 气泡左侧动态竖向渐变线条 */}
        {!isUser && streaming && (
          <span className="absolute -left-3 top-1 bottom-1 w-0.5 rounded-full bg-linear-to-b from-emerald-500 to-transparent dark:block hidden" />
        )}

        <div
          className={cn(
            "rounded-2xl px-4 py-2.5 text-sm",
            isUser
              ? // 用户气泡：日间磨砂黑钛金灰 / 暗夜高亮米白
                "rounded-tr-xs bg-zinc-900 font-medium text-white dark:bg-zinc-100 dark:text-zinc-900"
              : // AI 气泡：日间冰灰微衬 / 暗夜石墨玻璃拟态
                "rounded-tl-md bg-zinc-50/80 text-zinc-900 ring-1 ring-zinc-200/60 dark:bg-zinc-900/60 dark:text-zinc-100 dark:ring-zinc-800/60 dark:backdrop-blur",
          )}
        >
          {isUser ? (
            <p className="whitespace-pre-wrap break-words">{message.content}</p>
          ) : message.content ? (
            <Markdown content={message.content} />
          ) : streaming ? (
            <BreathingCursor />
          ) : null}
        </div>
      </div>
    </div>
  );
}

/** AI 生成中：精致的光标呼吸动画（竖直光标高亮 + 柔光脉冲）。 */
function BreathingCursor() {
  return (
    <output
      className="flex items-center gap-2 py-1.5"
      aria-label="正在生成"
      aria-live="polite"
    >
      <span className="relative inline-flex size-2.5">
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-500/70" />
        <span className="relative inline-flex size-2.5 rounded-full bg-emerald-500" />
      </span>
      <span className="relative h-4 w-[2px] animate-pulse rounded-full bg-foreground/70" />
      <span className="text-sm text-muted-foreground">生成中…</span>
    </output>
  );
}
