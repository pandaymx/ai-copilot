"use client";

import {
  BookOpen,
  Bot,
  Check,
  ChevronDown,
  Copy,
  FileText,
  Languages,
  Loader2,
  Maximize2,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  User,
  Volume2,
  X,
} from "lucide-react";
import { memo, useEffect, useRef, useState } from "react";
import { ArtifactDispatcher } from "@/components/artifacts/artifact-dispatcher";
import { ImagePreviewModal } from "@/components/chat/image-preview-modal";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  type ArtifactItem,
  type InteractionMetadata,
  type StreamMetrics,
  type StreamStore,
  type TaskPlanState,
  type ToolCallItem,
  type UsageInfo,
  useStreamData,
} from "@/hooks/useSpringAiStream";
import {
  type CompressionMetadata,
  type DocumentCitationItem,
  type TranslateResponse,
  translateApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";
import { tts } from "@/lib/voice";
import { CompressionMarker } from "./compression-marker";
import { ChatMessageErrorBoundary } from "./error-boundary";
import { Markdown } from "./markdown";
import { ReasoningView } from "./reasoning-view";
import { StreamingMetricsBar } from "./streaming-metrics-bar";
import { TaskPlanCard } from "./task-plan-card";
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
  usage?: UsageInfo;
  attachments?: AttachmentItem[];
  /** 工具调用列表（已完成消息持久化用；流式消息由 streamStore.toolCalls 驱动）。 */
  toolCalls?: ToolCallItem[];
  /** 意图识别标识与中文标签 */
  intent?: string;
  intentLabel?: string;
  /** 产物列表（包含图片 artifact 等） */
  artifacts?: ArtifactItem[];
  /** ReAct 多步任务规划状态与时间轴步骤 */
  taskPlan?: TaskPlanState | null;
  /** 智能上下文压缩元数据（若触发了历史消息摘要压缩） */
  compressionMetadata?: CompressionMetadata | null;
  /** 文档对话精准引用列表（若开启了文档对话模式） */
  citations?: DocumentCitationItem[];
  /** 实时流式性能指标（TTFT、Token 生成速率、总耗时、工具执行耗时） */
  metrics?: StreamMetrics;
  /** 交互状态理解元数据（认知状态、原子信号、响应策略） */
  interaction?: InteractionMetadata | null;
}

interface MessageBubbleProps {
  message: ChatMessage;
  streaming?: boolean;
  conversationId?: string;
  onRegenerate?: () => void;
  onCitationClick?: (citation: DocumentCitationItem) => void;
}

const SUPPORTED_LANGUAGES = [
  { code: "zh-CN", name: "简体中文" },
  { code: "en", name: "English" },
  { code: "ja", name: "日本語" },
  { code: "ko", name: "한국어" },
  { code: "fr", name: "Français" },
  { code: "de", name: "Deutsch" },
  { code: "es", name: "Español" },
  { code: "ru", name: "Русский" },
];

function getLanguageLabel(code: string): string {
  const target = SUPPORTED_LANGUAGES.find((l) => l.code === code);
  return target ? target.name : code;
}

function MessageBubbleBase({
  message,
  streaming,
  conversationId,
  onRegenerate,
  onCitationClick,
}: MessageBubbleProps) {
  const isUser = message.role === "user";
  const [copied, setCopied] = useState(false);
  const [liked, setLiked] = useState<boolean | null>(null);
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  // 语音播放：合成中状态与音频对象 URL
  const [speaking, setSpeaking] = useState(false);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const audioUrlRef = useRef<string | null>(null);

  // 翻译功能状态
  const [translation, setTranslation] = useState<TranslateResponse | null>(
    null,
  );
  const [translating, setTranslating] = useState(false);
  const [targetLang, setTargetLang] = useState("zh-CN");
  const [showTranslation, setShowTranslation] = useState(false);
  const [showLangMenu, setShowLangMenu] = useState(false);
  const [copiedTranslation, setCopiedTranslation] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const langMenuRef = useRef<HTMLDivElement>(null);

  // 点击外部关闭语言菜单
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        langMenuRef.current &&
        !langMenuRef.current.contains(e.target as Node)
      ) {
        setShowLangMenu(false);
      }
    }
    if (showLangMenu) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [showLangMenu]);

  // 组件卸载时终止未完成的翻译请求
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  const handleTranslate = async (langToUse?: string) => {
    const lang = langToUse || targetLang;
    if (!message.content.trim()) return;

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setTranslating(true);
    setShowTranslation(true);
    setShowLangMenu(false);
    setTargetLang(lang);

    try {
      const res = await translateApi(
        {
          text: message.content,
          targetLang: lang,
          sourceLang: "auto",
          preserveFormatting: true,
        },
        controller.signal,
      );
      if (res) {
        setTranslation(res);
      }
    } catch (err) {
      console.warn("翻译请求异常:", err);
    } finally {
      setTranslating(false);
    }
  };

  const handleCopyTranslation = async () => {
    if (!translation?.translatedText) return;
    try {
      await navigator.clipboard.writeText(translation.translatedText);
      setCopiedTranslation(true);
      setTimeout(() => setCopiedTranslation(false), 1800);
    } catch {
      // 忽略复制失败
    }
  };

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
            {!isUser &&
              message.content &&
              message.content.includes("AI 自我纠错与补充") && (
                <span className="rounded-full bg-amber-50 dark:bg-amber-950/60 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300 border border-amber-200/50 dark:border-amber-800/50 flex items-center gap-1 shadow-2xs">
                  <Sparkles className="size-3 text-amber-500" />
                  <span>已触发自我反思纠偏</span>
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

        {/* ReAct 多步任务规划与执行看板 */}
        {!isUser && message.taskPlan && (
          <div className="w-full">
            <TaskPlanCard plan={message.taskPlan} />
          </div>
        )}

        {/* 多模态附件渲染 */}
        {message.attachments && message.attachments.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-1 max-w-full">
            {message.attachments.map((att) => (
              <div
                key={att.id}
                className="group/att relative overflow-hidden rounded-xl border border-zinc-200/80 bg-white/80 dark:border-zinc-800/80 dark:bg-zinc-900/80 p-1 shadow-xs transition-all hover:border-indigo-300 dark:hover:border-indigo-700"
              >
                {att.type === "image" ? (
                  <button
                    type="button"
                    onClick={() => setPreviewImage(att.url)}
                    className="relative size-24 overflow-hidden rounded-lg cursor-zoom-in block text-left group/img"
                    title="点击放大预览图片"
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={att.url}
                      alt={att.name}
                      className="size-full object-cover transition-transform duration-300 group-hover/img:scale-105"
                    />
                    <div className="absolute inset-0 flex items-center justify-center bg-black/30 opacity-0 backdrop-blur-2xs transition-opacity duration-200 group-hover/img:opacity-100">
                      <div className="flex size-7 items-center justify-center rounded-full bg-black/60 text-white shadow-md">
                        <Maximize2 className="size-3.5" />
                      </div>
                    </div>
                  </button>
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

        {/* 可渲染多模态产物卡片区（图片 / 图表 / 表格 / SVG / HTML 组件） */}
        {!isUser && message.artifacts && message.artifacts.length > 0 && (
          <div className="flex w-full flex-col gap-2">
            {message.artifacts.map((art) => (
              <ArtifactDispatcher key={art.artifactId} artifact={art} />
            ))}
          </div>
        )}

        {/* 上下文智能压缩折叠卡片（若当前消息为压缩摘要或附加了压缩元数据） */}
        {!isUser && message.compressionMetadata && (
          <div className="w-full">
            <CompressionMarker metadata={message.compressionMetadata} />
          </div>
        )}

        {/* 气泡本文 */}
        {!isUser && message.content?.startsWith("[COMPRESSED:") ? (
          <div className="w-full">
            <CompressionMarker
              rawText={message.content}
              metadata={message.compressionMetadata}
            />
          </div>
        ) : (
          (isUser || message.content || streaming) && (
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
                <div className="space-y-3">
                  {/* 文档限定范围自动拒答提示条 */}
                  {(message.content.includes("抱歉，根据当前提供的会话文档") ||
                    message.content.includes("抱歉，在您挂载的文档中未找到") ||
                    message.content.includes("未检索到相关内容")) && (
                    <div className="flex items-center gap-2 rounded-xl border border-amber-500/30 bg-amber-50/70 px-3 py-2 text-xs font-medium text-amber-800 dark:border-amber-500/30 dark:bg-amber-950/30 dark:text-amber-300">
                      <ShieldAlert className="size-4 text-amber-500 shrink-0" />
                      <span>
                        文档限定模式：由于挂载文档中未包含相关事实，已自动拦截文档外无关内容。
                      </span>
                    </div>
                  )}
                  <ChatMessageErrorBoundary>
                    <Markdown
                      content={message.content}
                      isStreaming={streaming}
                      onCitationClick={(citeId) => {
                        const target = (message.citations || []).find(
                          (c) => c.citationId === citeId,
                        );
                        if (target) {
                          onCitationClick?.(target);
                        } else if (message.citations?.[0]) {
                          onCitationClick?.(message.citations[0]);
                        }
                      }}
                    />
                  </ChatMessageErrorBoundary>

                  {/* 多语言翻译结果卡片 */}
                  {showTranslation && (
                    <div className="mt-2.5 w-full overflow-hidden rounded-2xl border border-indigo-200/80 bg-gradient-to-br from-indigo-50/70 via-white/80 to-purple-50/50 p-3 shadow-sm backdrop-blur-md dark:border-indigo-900/60 dark:from-indigo-950/40 dark:via-zinc-900/80 dark:to-purple-950/30 animate-in fade-in zoom-in-95 duration-200">
                      {/* 顶部控制与语种状态条 */}
                      <div className="flex items-center justify-between border-b border-indigo-100/80 pb-2 dark:border-indigo-900/50">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <div className="flex size-5 items-center justify-center rounded-md bg-indigo-500/10 text-indigo-600 dark:text-indigo-400">
                            <Languages className="size-3.5" />
                          </div>
                          <span className="text-xs font-bold text-zinc-800 dark:text-zinc-200">
                            多语言译文
                          </span>

                          {translation && (
                            <span className="rounded-md bg-indigo-100/80 px-1.5 py-0.5 text-[10px] font-semibold text-indigo-700 dark:bg-indigo-900/50 dark:text-indigo-300">
                              {translation.detectedLang ||
                                translation.sourceLang}{" "}
                              → {translation.targetLang}
                            </span>
                          )}

                          {translation &&
                            translation.glossaryAppliedCount > 0 && (
                              <span className="rounded-md bg-emerald-100/80 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
                                术语命中 {translation.glossaryAppliedCount}
                              </span>
                            )}
                        </div>

                        {/* 右侧操作按钮 */}
                        <div className="flex items-center gap-1">
                          {/* 目标语种切换器 */}
                          <div className="relative" ref={langMenuRef}>
                            <button
                              type="button"
                              onClick={() => setShowLangMenu((prev) => !prev)}
                              className="flex items-center gap-1 rounded-lg border border-zinc-200/80 bg-white/90 px-2 py-0.5 text-[11px] font-medium text-zinc-700 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors shadow-2xs cursor-pointer"
                            >
                              <span>{getLanguageLabel(targetLang)}</span>
                              <ChevronDown className="size-3 text-zinc-400" />
                            </button>

                            {showLangMenu && (
                              <div className="absolute right-0 top-full mt-1.5 z-30 w-36 rounded-xl border border-zinc-200 bg-white p-1 shadow-lg dark:border-zinc-800 dark:bg-zinc-900 animate-in fade-in-50 zoom-in-95 duration-100">
                                {SUPPORTED_LANGUAGES.map((l) => (
                                  <button
                                    key={l.code}
                                    type="button"
                                    onClick={() => handleTranslate(l.code)}
                                    className={cn(
                                      "flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-xs text-left transition-colors cursor-pointer",
                                      targetLang === l.code
                                        ? "bg-indigo-50 font-semibold text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400"
                                        : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800",
                                    )}
                                  >
                                    <span>{l.name}</span>
                                    <span className="text-[10px] text-zinc-400 font-mono">
                                      {l.code}
                                    </span>
                                  </button>
                                ))}
                              </div>
                            )}
                          </div>

                          {/* 复制译文 */}
                          {translation && (
                            <button
                              type="button"
                              onClick={handleCopyTranslation}
                              className="rounded-lg p-1 text-zinc-400 hover:bg-white/80 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer"
                              title="复制译文"
                            >
                              {copiedTranslation ? (
                                <Check className="size-3.5 text-emerald-500" />
                              ) : (
                                <Copy className="size-3.5" />
                              )}
                            </button>
                          )}

                          {/* 重新翻译 */}
                          <button
                            type="button"
                            onClick={() => handleTranslate()}
                            disabled={translating}
                            className="rounded-lg p-1 text-zinc-400 hover:bg-white/80 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer"
                            title="重新翻译"
                          >
                            <RotateCcw
                              className={cn(
                                "size-3.5",
                                translating && "animate-spin text-indigo-500",
                              )}
                            />
                          </button>

                          {/* 关闭/折叠译文 */}
                          <button
                            type="button"
                            onClick={() => setShowTranslation(false)}
                            className="rounded-lg p-1 text-zinc-400 hover:bg-white/80 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer"
                            title="收起译文"
                          >
                            <X className="size-3.5" />
                          </button>
                        </div>
                      </div>

                      {/* 译文正文内容区 */}
                      <div className="mt-2.5 text-sm leading-relaxed text-zinc-800 dark:text-zinc-200">
                        {translating ? (
                          <div className="flex items-center gap-2 py-3 text-xs text-indigo-600 dark:text-indigo-400 font-medium">
                            <Loader2 className="size-4 animate-spin text-indigo-500" />
                            <span>正在精准翻译并保留代码/公式结构...</span>
                          </div>
                        ) : translation ? (
                          <ChatMessageErrorBoundary>
                            <Markdown content={translation.translatedText} />
                          </ChatMessageErrorBoundary>
                        ) : (
                          <p className="text-xs text-zinc-400">暂无译文</p>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              ) : streaming ? (
                <BreathingCursor />
              ) : null}
            </div>
          )
        )}

        {/* 文档对话引用底栏 Panel */}
        {!isUser && message.citations && message.citations.length > 0 && (
          <div className="flex w-full flex-wrap items-center gap-1.5 rounded-xl border border-indigo-500/20 bg-indigo-50/40 p-2.5 dark:border-indigo-500/30 dark:bg-indigo-950/20">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-indigo-700 dark:text-indigo-300 mr-1">
              <ShieldCheck className="size-3.5 text-indigo-500" />
              <span>引用依据 ({message.citations.length}):</span>
            </div>
            {message.citations.map((cite) => (
              <button
                key={cite.citationId}
                type="button"
                onClick={() => onCitationClick?.(cite)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200/80 bg-white/90 px-2 py-0.5 text-xs font-medium text-zinc-700 hover:border-indigo-400 hover:bg-indigo-50 hover:text-indigo-600 dark:border-indigo-900/60 dark:bg-zinc-900/90 dark:text-zinc-300 dark:hover:bg-indigo-950/50 dark:hover:text-indigo-300 transition-all shadow-2xs cursor-pointer"
              >
                <BookOpen className="size-3 text-indigo-500" />
                <span>[{cite.citationId}]</span>
                <span className="max-w-[120px] truncate">{cite.fileName}</span>
                {cite.pageNumber && (
                  <span className="font-mono text-[10px] text-zinc-400">
                    p.{cite.pageNumber}
                  </span>
                )}
              </button>
            ))}
          </div>
        )}

        {/* 实时流式性能指标条 (首字延迟 TTFT / 生成速率 / 总耗时 / 工具耗时) */}
        {!isUser && (
          <StreamingMetricsBar
            metrics={message.metrics}
            streaming={streaming}
            contentLength={message.content?.length ?? 0}
            toolCallsCount={message.toolCalls?.length ?? 0}
          />
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

            {/* 多语言翻译按钮 */}
            <button
              type="button"
              onClick={() => {
                if (showTranslation && translation) {
                  setShowTranslation(false);
                } else {
                  handleTranslate();
                }
              }}
              disabled={translating || !message.content.trim()}
              className={cn(
                "flex size-7 items-center justify-center rounded-lg text-zinc-400 transition-colors cursor-pointer",
                showTranslation
                  ? "text-indigo-600 bg-indigo-50 dark:bg-indigo-950/50 dark:text-indigo-400"
                  : "hover:bg-zinc-100 hover:text-indigo-600 dark:hover:bg-zinc-800 dark:hover:text-indigo-400",
              )}
              title={showTranslation ? "收起译文" : "多语言即时翻译"}
            >
              {translating ? (
                <Loader2 className="size-3.5 animate-spin text-indigo-500" />
              ) : (
                <Languages className="size-3.5" />
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

      {/* 图片全屏查看模态框 */}
      <ImagePreviewModal
        src={previewImage}
        onClose={() => setPreviewImage(null)}
      />
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
  onCitationClick?: (citation: DocumentCitationItem) => void;
}

export function LiveMessageBubble({
  message,
  streamStore,
  conversationId,
  onCitationClick,
}: LiveMessageBubbleProps) {
  const {
    content,
    thinking,
    reasoningDurationMs,
    usage,
    metrics,
    toolCalls,
    artifacts,
    taskPlan,
    contextCompression,
    citations,
  } = useStreamData(streamStore);
  const containerRef = useRef<HTMLDivElement>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: scroll into view on streaming content update
  useEffect(() => {
    containerRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [
    content,
    thinking,
    toolCalls,
    artifacts,
    taskPlan,
    contextCompression,
    citations,
  ]);

  const liveMessage: ChatMessage = {
    ...message,
    content: content || message.content,
    thinking: thinking || message.thinking,
    reasoningDurationMs: reasoningDurationMs ?? message.reasoningDurationMs,
    usage: usage ?? message.usage,
    metrics: metrics ?? message.metrics,
    // 将流式 Map 转为数组（保留 callId 作 ToolCard key, artifactId 作 ImageArtifactViewer key）
    toolCalls: Object.values(toolCalls),
    artifacts: Object.values(artifacts),
    taskPlan: taskPlan ?? message.taskPlan,
    compressionMetadata: contextCompression ?? message.compressionMetadata,
    citations:
      citations && citations.length > 0 ? citations : message.citations,
  };

  return (
    <div ref={containerRef}>
      <MessageBubbleBase
        message={liveMessage}
        streaming={true}
        conversationId={conversationId}
        onCitationClick={onCitationClick}
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
