"use client";

import {
  Bot,
  Brain,
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  FileText,
  RotateCcw,
  ThumbsDown,
  ThumbsUp,
  User,
} from "lucide-react";
import { memo, useEffect, useRef, useState } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { type StreamStore, useStreamData } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";
import { ChatMessageErrorBoundary } from "./error-boundary";
import { Markdown } from "./markdown";

export interface AttachmentItem {
  id: string;
  name: string;
  type: "image" | "file";
  mimeType: string;
  url: string;
  size?: number;
  /** 非图片文件的文本内容（readAsText 读取后存储） */
  textContent?: string;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  thinking?: string;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  };
  attachments?: AttachmentItem[];
}

interface MessageBubbleProps {
  message: ChatMessage;
  streaming?: boolean;
  conversationId?: string;
  onRegenerate?: () => void;
}

function MessageBubbleBase({
  message,
  streaming,
  conversationId,
  onRegenerate,
}: MessageBubbleProps) {
  const isUser = message.role === "user";
  const [copied, setCopied] = useState(false);
  const [liked, setLiked] = useState<boolean | null>(null);
  const [showThinking, setShowThinking] = useState(true);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // 忽略复制失败
    }
  };

  const handleFeedback = (newLiked: boolean | null) => {
    setLiked(newLiked);
    if (newLiked !== null) {
      fetch("/api/chat/feedback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          conversationId: conversationId || "",
          messageId: message.id || "",
          rating: newLiked ? "THUMBS_UP" : "THUMBS_DOWN",
        }),
      }).catch((err) => {
        console.warn("提交点赞/点踩反馈失败:", err);
      });
    }
  };

  return (
    <div
      id={`msg-${message.id}`}
      data-message-id={message.id}
      className={cn(
        "group relative flex w-full gap-3.5 px-4 py-3 sm:px-6 transition-all rounded-2xl duration-500",
        isUser ? "flex-row-reverse" : "flex-row",
      )}
    >
      {/* 头像组件 */}
      <Avatar
        size="sm"
        className={cn(
          "mt-0.5 shrink-0 shadow-sm transition-transform duration-300 group-hover:scale-105",
          !isUser &&
            streaming &&
            "ring-2 ring-indigo-500/80 ring-offset-2 ring-offset-background animate-pulse",
        )}
      >
        <AvatarFallback
          className={cn(
            isUser
              ? "bg-gradient-to-br from-zinc-800 to-zinc-900 text-white dark:from-zinc-100 dark:to-zinc-300 dark:text-zinc-900"
              : "bg-gradient-to-br from-indigo-500 via-purple-600 to-pink-500 text-white",
          )}
        >
          {isUser ? <User className="size-4" /> : <Bot className="size-4" />}
        </AvatarFallback>
      </Avatar>

      {/* 消息卡片主体 */}
      <div
        className={cn(
          "relative flex min-w-0 flex-col gap-1.5",
          isUser ? "items-end max-w-[85%]" : "items-start w-full min-w-0",
        )}
      >
        {/* AI 助手 Badge 及 Token 用量展示 */}
        {!isUser && (
          <div className="flex flex-wrap items-center gap-2 px-1 text-[11px] font-medium text-zinc-500 dark:text-zinc-400">
            <span className="font-semibold text-zinc-800 dark:text-zinc-200">
              AI Copilot
            </span>
            <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-semibold text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-300 border border-indigo-200/50 dark:border-indigo-800/50">
              Spring AI Core
            </span>
            {message.usage && message.usage.totalTokens > 0 && (
              <span className="flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-medium text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border border-emerald-200/50 dark:border-emerald-800/50">
                <span>
                  Tokens: {message.usage.totalTokens} (Prompt:{" "}
                  {message.usage.promptTokens} / Completion:{" "}
                  {message.usage.completionTokens})
                </span>
                {message.usage.estimatedCostRmb !== undefined && (
                  <span className="font-semibold text-emerald-800 dark:text-emerald-200">
                    · 约 ¥{message.usage.estimatedCostRmb.toFixed(4)}
                  </span>
                )}
              </span>
            )}
          </div>
        )}

        {/* 思考过程折叠盒（针对推理型输出） */}
        {!isUser && message.thinking && (
          <div className="mb-1.5 w-full min-w-0 overflow-hidden rounded-xl border border-indigo-200/60 bg-indigo-50/40 text-xs dark:border-indigo-900/50 dark:bg-indigo-950/30">
            <button
              type="button"
              onClick={() => setShowThinking((prev) => !prev)}
              className="flex w-full items-center justify-between px-3 py-2 text-indigo-700 hover:bg-indigo-100/50 dark:text-indigo-300 dark:hover:bg-indigo-900/30 font-medium"
            >
              <div className="flex items-center gap-1.5">
                <Brain className="size-3.5 animate-pulse text-indigo-500" />
                <span>思考过程 ({streaming ? "推理中..." : "已完成"})</span>
              </div>
              {showThinking ? (
                <ChevronDown className="size-3.5" />
              ) : (
                <ChevronRight className="size-3.5" />
              )}
            </button>
            {showThinking && (
              <div className="border-t border-indigo-200/40 p-3 text-zinc-600 leading-relaxed dark:border-indigo-900/40 dark:text-zinc-400">
                {message.thinking}
              </div>
            )}
          </div>
        )}

        {/* 多模态附件渲染 */}
        {message.attachments && message.attachments.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-1 max-w-full">
            {message.attachments.map((att) => (
              <div
                key={att.id}
                className="group/att relative overflow-hidden rounded-xl border border-zinc-200/80 bg-white/80 dark:border-zinc-800/80 dark:bg-zinc-900/80 p-1 shadow-xs"
              >
                {att.type === "image" ? (
                  <div className="relative size-24 overflow-hidden rounded-lg">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={att.url}
                      alt={att.name}
                      className="size-full object-cover transition-transform duration-300 group-hover/att:scale-105"
                    />
                  </div>
                ) : (
                  <div className="flex items-center gap-2 px-2.5 py-1.5 text-xs">
                    <FileText className="size-4 shrink-0 text-indigo-500" />
                    <span className="max-w-[140px] truncate font-medium text-zinc-700 dark:text-zinc-300">
                      {att.name}
                    </span>
                    {att.textContent && (
                      <span className="rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] font-medium text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-300">
                        已读取
                      </span>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* 气泡本文 */}
        {(isUser || message.content || streaming) && (
          <div
            className={cn(
              "relative min-w-0 rounded-2xl px-4 py-3 text-sm shadow-xs transition-all duration-200",
              isUser
                ? "rounded-tr-xs bg-zinc-900 font-medium text-white shadow-md shadow-zinc-900/10 dark:bg-gradient-to-r dark:from-indigo-600 dark:to-purple-600 dark:text-white dark:shadow-indigo-500/20"
                : "w-full rounded-tl-xs bg-white text-zinc-900 border border-zinc-200/80 shadow-sm dark:bg-zinc-900/80 dark:text-zinc-100 dark:border-zinc-800/80 backdrop-blur-md",
            )}
          >
            {isUser ? (
              <p className="whitespace-pre-wrap break-words">
                {message.content}
              </p>
            ) : message.content ? (
              <ChatMessageErrorBoundary>
                <Markdown content={message.content} />
              </ChatMessageErrorBoundary>
            ) : streaming ? (
              <BreathingCursor />
            ) : null}
          </div>
        )}

        {/* AI 消息底栏 Action Bar (Hover 显示) */}
        {!isUser && message.content && (
          <div className="flex items-center gap-1 px-1 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
            <button
              type="button"
              onClick={handleCopy}
              className="flex size-7 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
              title="复制回答"
            >
              {copied ? (
                <Check className="size-3.5 text-emerald-500" />
              ) : (
                <Copy className="size-3.5" />
              )}
            </button>

            {onRegenerate && (
              <button
                type="button"
                onClick={onRegenerate}
                className="flex size-7 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
                title="重新生成"
              >
                <RotateCcw className="size-3.5" />
              </button>
            )}

            <button
              type="button"
              onClick={() => handleFeedback(liked === true ? null : true)}
              className={cn(
                "flex size-7 items-center justify-center rounded-lg text-zinc-400 transition-colors",
                liked === true
                  ? "text-indigo-600 bg-indigo-50 dark:bg-indigo-950/50 dark:text-indigo-400"
                  : "hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200",
              )}
              title="赞"
            >
              <ThumbsUp className="size-3.5" />
            </button>

            <button
              type="button"
              onClick={() => handleFeedback(liked === false ? null : false)}
              className={cn(
                "flex size-7 items-center justify-center rounded-lg text-zinc-400 transition-colors",
                liked === false
                  ? "text-rose-600 bg-rose-50 dark:bg-rose-950/50 dark:text-rose-400"
                  : "hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200",
              )}
              title="踩"
            >
              <ThumbsDown className="size-3.5" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export const MessageBubble = memo(
  MessageBubbleBase,
  (prev, next) =>
    prev.message === next.message &&
    prev.streaming === next.streaming &&
    prev.conversationId === next.conversationId &&
    prev.onRegenerate === next.onRegenerate,
);

interface LiveMessageBubbleProps {
  message: ChatMessage;
  streamStore: StreamStore;
  conversationId?: string;
}

export function LiveMessageBubble({
  message,
  streamStore,
  conversationId,
}: LiveMessageBubbleProps) {
  const { content, thinking, usage } = useStreamData(streamStore);
  const containerRef = useRef<HTMLDivElement>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: scroll into view on streaming content update
  useEffect(() => {
    containerRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [content, thinking]);

  const liveMessage: ChatMessage = {
    ...message,
    content: content || message.content,
    thinking: thinking || message.thinking,
    usage: usage ?? message.usage,
  };

  return (
    <div ref={containerRef}>
      <MessageBubbleBase
        message={liveMessage}
        streaming={true}
        conversationId={conversationId}
      />
    </div>
  );
}

/** 生成中优雅动画指示器 */
function BreathingCursor() {
  return (
    <div className="flex items-center gap-2.5 py-1 text-xs text-zinc-500 dark:text-zinc-400 font-medium">
      <div className="flex items-center gap-1">
        <span className="size-2 rounded-full bg-indigo-500 animate-ping" />
        <span className="size-2 rounded-full bg-purple-500 animate-pulse" />
        <span className="size-2 rounded-full bg-pink-500 animate-bounce" />
      </div>
      <span>AI 正在思考与撰写...</span>
    </div>
  );
}
