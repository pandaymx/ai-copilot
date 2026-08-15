"use client";

import {
  Archive,
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  FileText,
  Sparkles,
} from "lucide-react";
import { useState } from "react";
import type { CompressionMetadata } from "@/lib/api";
import { cn } from "@/lib/utils";

interface CompressionMarkerProps {
  metadata?: CompressionMetadata | null;
  rawText?: string;
  className?: string;
}

/**
 * 智能上下文压缩折叠标记组件（Smart Context Compression Marker）。
 * 当历史对话超出 Token 预算被 LLM 摘要压缩时，在消息流中渲染该折叠卡片，
 * 代替单调的原始多轮消息，提供清晰的压缩统计与展开预览能力。
 */
export function CompressionMarker({
  metadata,
  rawText,
  className,
}: CompressionMarkerProps) {
  const [expanded, setExpanded] = useState(false);
  const [copied, setCopied] = useState(false);

  // 从 rawText 解析 [COMPRESSED:N turns] 标签
  const parsedFromText = (() => {
    if (!rawText) return null;
    const match = /^\[COMPRESSED:(\d+)\s*turns\]\s*([\s\S]*)$/i.exec(
      rawText.trim(),
    );
    if (!match) return null;
    return {
      turnCount: parseInt(match[1], 10) || 1,
      summary: match[2].trim(),
    };
  })();

  const turnCount =
    metadata?.compressedTurnCount ?? parsedFromText?.turnCount ?? 1;
  const summary =
    metadata?.summarySnippet ?? parsedFromText?.summary ?? rawText ?? "";
  const originalTokens = metadata?.originalTokens ?? 0;
  const compressedTokens = metadata?.compressedTokens ?? 0;
  const level = metadata?.level ?? "LIGHT";
  const isFallback = metadata?.fallback ?? false;

  const savedPercent =
    originalTokens > 0 && compressedTokens > 0
      ? Math.max(
          0,
          Math.round(
            ((originalTokens - compressedTokens) / originalTokens) * 100,
          ),
        )
      : null;

  const handleCopy = () => {
    if (!summary) return;
    navigator.clipboard.writeText(summary);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const levelLabel =
    level === "DEEP"
      ? "深度压缩"
      : level === "KEYWORDS"
        ? "关键信息提取"
        : "轻度压缩";

  return (
    <div
      className={cn(
        "my-3 w-full rounded-xl border border-border/70 bg-gradient-to-r from-muted/50 via-muted/30 to-background p-3.5 shadow-sm backdrop-blur-sm transition-all duration-200 hover:border-border",
        className,
      )}
    >
      {/* 头部摘要栏 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary ring-1 ring-primary/20">
            <Archive className="h-4 w-4" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold tracking-wide text-foreground">
                上下文已智能压缩
              </span>
              <span className="inline-flex items-center rounded-md px-1.5 py-0.5 text-[10px] font-medium bg-primary/10 text-primary ring-1 ring-primary/20">
                <Sparkles className="mr-1 h-2.5 w-2.5" />
                {turnCount} 轮对话已摘要
              </span>
              {!isFallback && (
                <span className="inline-flex items-center rounded-md px-1.5 py-0.5 text-[10px] text-muted-foreground ring-1 ring-border">
                  {levelLabel}
                </span>
              )}
              {isFallback && (
                <span className="inline-flex items-center rounded-md px-1.5 py-0.5 text-[10px] bg-destructive/10 text-destructive ring-1 ring-destructive/20 font-medium">
                  降级截断
                </span>
              )}
            </div>
            {originalTokens > 0 && compressedTokens > 0 && (
              <p className="text-[11px] text-muted-foreground mt-0.5">
                Token:{" "}
                <span className="font-mono">
                  {originalTokens.toLocaleString()}
                </span>{" "}
                →{" "}
                <span className="font-mono text-foreground font-medium">
                  {compressedTokens.toLocaleString()}
                </span>
                {savedPercent !== null && (
                  <span className="ml-1.5 font-medium text-emerald-600 dark:text-emerald-400">
                    (节省 ~{savedPercent}%)
                  </span>
                )}
              </p>
            )}
          </div>
        </div>

        {/* 右侧交互按钮 */}
        <div className="flex items-center gap-1.5 ml-auto">
          {summary && (
            <button
              type="button"
              onClick={handleCopy}
              className="inline-flex h-7 items-center gap-1 rounded-md px-2 text-[11px] text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
              title="复制摘要"
            >
              {copied ? (
                <>
                  <Check className="h-3 w-3 text-emerald-500" />
                  <span>已复制</span>
                </>
              ) : (
                <>
                  <Copy className="h-3 w-3" />
                  <span>复制</span>
                </>
              )}
            </button>
          )}
          <button
            type="button"
            onClick={() => setExpanded(!expanded)}
            className="inline-flex h-7 items-center gap-1 rounded-md bg-background/80 px-2.5 text-[11px] font-medium text-foreground shadow-xs ring-1 ring-border hover:bg-accent transition-colors"
          >
            {expanded ? (
              <>
                <span>收起</span>
                <ChevronUp className="h-3 w-3" />
              </>
            ) : (
              <>
                <span>展开摘要</span>
                <ChevronDown className="h-3 w-3" />
              </>
            )}
          </button>
        </div>
      </div>

      {/* 折叠/展开正文 */}
      {expanded && (
        <div className="mt-3 border-t border-border/50 pt-3 text-xs leading-relaxed text-muted-foreground">
          <div className="rounded-lg bg-background/60 p-3 font-normal text-foreground/90 ring-1 ring-border/50">
            <div className="flex items-center gap-1.5 font-medium text-xs text-foreground mb-1.5">
              <FileText className="h-3.5 w-3.5 text-primary" />
              <span>历史上下文核心摘要</span>
            </div>
            <p className="whitespace-pre-wrap">{summary}</p>
          </div>
          <div className="mt-2 flex items-center justify-between text-[10px] text-muted-foreground/80 px-1">
            <span>✨ 最近 2~4 轮原始问答已完整保留在活跃记忆中</span>
            <span>无损指代与连贯推理已就绪</span>
          </div>
        </div>
      )}
    </div>
  );
}
