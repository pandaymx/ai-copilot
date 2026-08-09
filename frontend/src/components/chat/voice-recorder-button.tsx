"use client";

import { Mic, Square } from "lucide-react";
import { cn } from "@/lib/utils";

interface VoiceRecorderButtonProps {
  recording: boolean;
  seconds: number;
  disabled?: boolean;
  onStart: () => void;
  onStop: () => void;
}

/**
 * 麦克风录音按钮：静息态展示麦克风图标，录音态切换为红色脉冲动画并展示倒计时。
 */
export function VoiceRecorderButton({
  recording,
  seconds,
  disabled,
  onStart,
  onStop,
}: VoiceRecorderButtonProps) {
  if (recording) {
    return (
      <button
        type="button"
        onClick={onStop}
        aria-label="停止录音"
        title="停止录音"
        className="relative flex size-8 items-center justify-center rounded-xl text-white shadow-md shadow-rose-500/30 bg-gradient-to-tr from-rose-500 to-red-500 transition-all duration-200 hover:scale-105"
      >
        <span className="absolute inline-flex size-full animate-ping rounded-xl bg-rose-400 opacity-60" />
        <Square className="relative size-3.5" />
        <span className="relative ml-1 font-mono text-[10px] tabular-nums">
          {String(seconds).padStart(2, "0")}
        </span>
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={onStart}
      disabled={disabled}
      aria-label="语音输入"
      title="语音输入（说话即转文字）"
      className={cn(
        "flex size-8 items-center justify-center rounded-xl text-zinc-400 transition-all duration-200 hover:scale-105 hover:text-indigo-600 hover:bg-indigo-50 dark:text-zinc-500 dark:hover:text-indigo-400 dark:hover:bg-indigo-950/50",
        "disabled:opacity-40 disabled:hover:scale-100 disabled:hover:bg-transparent",
      )}
    >
      <Mic className="size-4" />
    </button>
  );
}
