"use client";

import { cn } from "@/lib/utils";

export interface SwitchProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
  /** 左侧/右侧标签（可选）。 */
  label?: string;
  /** 激活时显示的徽标文本，如 "Agent"。 */
  badge?: string;
  className?: string;
  id?: string;
}

/**
 * Agent 模式开关：玻璃拟态风格的可访问 Toggle（role=switch）。
 * 与设计系统一致的渐变激活态与 200ms 平滑过渡，激活时附带高亮描边与徽标。
 */
export function Switch({
  checked,
  onCheckedChange,
  disabled = false,
  label,
  badge = "Agent",
  className,
  id,
}: SwitchProps) {
  return (
    <label
      htmlFor={id}
      className={cn(
        "group inline-flex select-none items-center gap-2",
        disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer",
        className,
      )}
    >
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label ?? badge}
        disabled={disabled}
        onClick={() => onCheckedChange(!checked)}
        className={cn(
          "relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border transition-all duration-200 ease-out outline-none",
          "focus-visible:ring-3 focus-visible:ring-violet-500/40",
          checked
            ? "border-violet-400/60 bg-gradient-to-r from-indigo-500 via-violet-500 to-fuchsia-500 shadow-md shadow-violet-500/30"
            : "border-border bg-muted dark:bg-input/40",
        )}
      >
        <span
          className={cn(
            "pointer-events-none inline-block size-3.5 rounded-full bg-white shadow-sm transition-transform duration-200 ease-out",
            checked ? "translate-x-[18px]" : "translate-x-0.5",
          )}
        />
      </button>
      {(label || badge) && (
        <span
          className={cn(
            "inline-flex items-center gap-1.5 text-xs font-medium transition-colors",
            checked
              ? "text-violet-600 dark:text-violet-300"
              : "text-muted-foreground",
          )}
        >
          {label}
          {checked && badge && (
            <span className="rounded-full bg-gradient-to-r from-indigo-500 to-fuchsia-500 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white shadow-sm">
              {badge}
            </span>
          )}
        </span>
      )}
    </label>
  );
}
