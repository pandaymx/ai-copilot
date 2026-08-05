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
      <Avatar size="sm" className="mt-0.5 shrink-0">
        <AvatarFallback
          className={cn(
            isUser
              ? "bg-primary text-primary-foreground"
              : "bg-gradient-to-br from-emerald-500 to-teal-600 text-white",
          )}
        >
          {isUser ? <User className="size-4" /> : <Bot className="size-4" />}
        </AvatarFallback>
      </Avatar>

      <div
        className={cn(
          "flex min-w-0 max-w-[85%] flex-col gap-1",
          isUser ? "items-end" : "items-start",
        )}
      >
        <div
          className={cn(
            // 用户消息：深色圆角气泡
            "rounded-2xl px-4 py-2.5 text-sm shadow-sm",
            isUser
              ? "rounded-tr-md bg-primary font-medium text-primary-foreground"
              : // AI 消息：卡片拟物风格
                "rounded-tl-md border border-border bg-card text-card-foreground shadow-md/40 ring-1 ring-black/[0.03] dark:ring-white/[0.04]",
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
