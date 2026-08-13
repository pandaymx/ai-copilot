"use client";

import {
  Bot,
  Brain,
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  FileText,
  Loader2,
  RotateCcw,
  ThumbsDown,
  ThumbsUp,
  User,
  Volume2,
} from "lucide-react";
import { memo, useEffect, useRef, useState } from "react";
import { ImageArtifactViewer } from "@/components/artifacts/image-artifact-viewer";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  type ArtifactItem,
  type StreamStore,
  type ToolCallItem,
  useStreamData,
} from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";
import { tts } from "@/lib/voice";
import { ChatMessageErrorBoundary } from "./error-boundary";
import { Markdown } from "./markdown";
import { ReasoningView } from "./reasoning-view";
import { ToolCard } from "./tool-card";

export interface AttachmentItem {
  id: string;
  name: string;
  type: "image" | "file";
  mimeType?: string;
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
  reasoningDurationMs?: number;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    estimatedCostRmb?: number;
  };
  attachments?: AttachmentItem[];
  /** 工具调用列表（已完成消息持久化用；流式消息由 streamStore.toolCalls 驱动）。 */
  toolCalls?: ToolCallItem[];
  /** 意图识别标识与中文标签 */
  intent?: string;
  intentLabel?: string;
  /** 产物列表（包含图片 artifact 等） */
  artifacts?: ArtifactItem[];
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
  // 语音播放：合成中状态与音频对象 URL
  const [speaking, setSpeaking] = useState(false);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const audioUrlRef = useRef<string | null>(null);

  const handleSpeak = async () => {
    if (!message.content.trim() || speaking) return;
    try {
      setSpeaking(true);
      const blob = await tts(message.content);
      if (audioUrlRef.current) URL.revokeObjectURL(audioUrlRef.current);
      const url = URL.createObjectURL(blob);
      audioUrlRef.current = url;
      setAudioUrl(url);
    } catch (err) {
      console.error("语音合成失败:", err);
    } finally {
      setSpeaking(false);
    }
  };

  // 组件卸载时回收音频 URL，避免内存泄漏
  useEffect(() => {
    return () => {
      if (audioUrlRef.current) URL.revokeObjectURL(audioUrlRef.current);
    };
  }, []);

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
            {message.intentLabel && (
              <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[10px] font-semibold text-purple-600 dark:bg-purple-950/60 dark:text-purple-300 border border-purple-200/50 dark:border-purple-800/50 flex items-center gap-1 shadow-2xs">
                <span>🏷️ {message.intentLabel}</span>
              </span>
            )}
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

        {/* 结构化思维链可视化展示（针对推理型输出） */}
        {!isUser && message.thinking && (
          <ReasoningView
            thinking={message.thinking}
            streaming={streaming}
            durationMs={message.reasoningDurationMs}
          />
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

        {/* 工具调用卡片区：Agent 模式下渲染（callId 作唯一 key，保证并行多调用不闪烁/不顺序颠倒） */}
        {!isUser && message.toolCalls && message.toolCalls.length > 0 && (
          <div className="flex w-full flex-col gap-2">
            {message.toolCalls.map((tc) => (
              <ToolCard key={tc.callId} item={tc} />
            ))}
          </div>
        )}

        {/* 可渲染产物卡片区（如图片 artifact） */}
        {!isUser && message.artifacts && message.artifacts.length > 0 && (
          <div className="flex w-full flex-col gap-2">
            {message.artifacts.map((art) => (
              <ImageArtifactViewer key={art.artifactId} artifact={art} />
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
                <Markdown content={message.content} isStreaming={streaming} />
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

            {/* 朗读：调用 TTS 合成语音并可重播 */}
            <button
              type="button"
              onClick={handleSpeak}
              disabled={speaking || !message.content.trim()}
              className={cn(
                "flex size-7 items-center justify-center rounded-lg text-zinc-400 transition-colors",
                speaking
                  ? "text-indigo-500"
                  : "hover:bg-zinc-100 hover:text-indigo-600 dark:hover:bg-zinc-800 dark:hover:text-indigo-400",
              )}
              title="朗读回复"
            >
              {speaking ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Volume2 className="size-3.5" />
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

        {/* 语音播放器：朗读后内嵌音频控件，支持重播 */}
        {!isUser && audioUrl && (
          <div className="mt-1.5">
            {/* biome-ignore lint/a11y/useMediaCaption: 语音播放器无需字幕轨道 */}
            <audio
              controls
              src={audioUrl}
              className="h-9 w-full max-w-sm rounded-lg"
            />
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
  const {
    content,
    thinking,
    reasoningDurationMs,
    usage,
    toolCalls,
    artifacts,
  } = useStreamData(streamStore);
  const containerRef = useRef<HTMLDivElement>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: scroll into view on streaming content update
  useEffect(() => {
    containerRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [content, thinking, toolCalls, artifacts]);

  const liveMessage: ChatMessage = {
    ...message,
    content: content || message.content,
    thinking: thinking || message.thinking,
    reasoningDurationMs: reasoningDurationMs ?? message.reasoningDurationMs,
    usage: usage ?? message.usage,
    // 将流式 Map 转为数组（保留 callId 作 ToolCard key, artifactId 作 ImageArtifactViewer key）
    toolCalls: Object.values(toolCalls),
    artifacts: Object.values(artifacts),
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
