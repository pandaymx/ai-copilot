"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { BackendModelEntry } from "@/components/chat/model-selector";
import { fetchRealtimeUsageApi, type RealtimeUsageData } from "@/lib/api";
import {
  estimatePromptCostRmb,
  estimatePromptTokens,
} from "@/lib/token-estimator";

export type BudgetAlertLevel = "normal" | "warning" | "danger" | "exceeded";

export interface SseUsageUpdate {
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  estimatedCostRmb?: number;
  monthlyUsed?: number;
  monthlyQuota?: number;
  monthlyPercent?: number;
}

export interface TokenBudgetContextValue {
  /** 实时月度用量与配额（基于 Redis） */
  realtimeUsage: RealtimeUsageData | null;
  /** 是否正在加载最新用量 */
  loading: boolean;
  /** 当前输入草稿预估产生的 Token 数 */
  estimatedDeltaTokens: number;
  /** 当前输入草稿预估费用（元，人民币） */
  estimatedCostRmb: number;
  /** 是否已达到或超过 100% 月度配额（超额阻断） */
  isOverBudget: boolean;
  /** 警示分级：normal (<60%), warning (60-80%), danger (>80%), exceeded (>=100%) */
  alertLevel: BudgetAlertLevel;
  /** 刷新月度配额接口数据 */
  refreshRealtimeUsage: () => Promise<void>;
  /** 收到 SSE usage 帧时就地更新月度累计 */
  updateFromSseUsage: (update: SseUsageUpdate) => void;
  /** 更新输入框草稿文本与当前选中模型（内置 120ms 防抖分词估算） */
  setDraft: (text: string, modelObj?: BackendModelEntry | null) => void;
}

const TokenBudgetContext = createContext<TokenBudgetContextValue | null>(null);

export function TokenBudgetProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [realtimeUsage, setRealtimeUsage] = useState<RealtimeUsageData | null>(
    null,
  );
  const [loading, setLoading] = useState<boolean>(true);
  const [estimatedDeltaTokens, setEstimatedDeltaTokens] = useState<number>(0);
  const [estimatedCostRmb, setEstimatedCostRmb] = useState<number>(0);

  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 拉取后端最新实时配额数据（不查 DB）
  const refreshRealtimeUsage = useCallback(async () => {
    try {
      const data = await fetchRealtimeUsageApi();
      if (data) {
        setRealtimeUsage(data);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshRealtimeUsage();
  }, [refreshRealtimeUsage]);

  // 从 SSE usage 帧同步最新月度累计
  const updateFromSseUsage = useCallback((update: SseUsageUpdate) => {
    if (!update) return;
    setRealtimeUsage((prev) => {
      if (!prev) {
        if (
          update.monthlyUsed !== undefined &&
          update.monthlyQuota !== undefined
        ) {
          const quota = update.monthlyQuota;
          const used = update.monthlyUsed;
          const remaining = quota > 0 ? Math.max(0, quota - used) : quota;
          const percent =
            update.monthlyPercent !== undefined
              ? update.monthlyPercent
              : quota > 0
                ? Math.min(100, (used * 100) / quota)
                : 0;
          return {
            month: `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}`,
            usedTokens: used,
            quotaTokens: quota,
            remainingTokens: remaining,
            usedPercent: percent,
            alertThresholdPercent: 80.0,
          };
        }
        return null;
      }

      const newQuota =
        update.monthlyQuota !== undefined
          ? update.monthlyQuota
          : prev.quotaTokens;
      const newUsed =
        update.monthlyUsed !== undefined
          ? update.monthlyUsed
          : prev.usedTokens + (update.totalTokens ?? 0);
      const newRemaining =
        newQuota > 0 ? Math.max(0, newQuota - newUsed) : newQuota;
      const newPercent =
        update.monthlyPercent !== undefined
          ? update.monthlyPercent
          : newQuota > 0
            ? Math.min(100, (newUsed * 100) / newQuota)
            : 0;

      return {
        ...prev,
        usedTokens: newUsed,
        quotaTokens: newQuota,
        remainingTokens: newRemaining,
        usedPercent: newPercent,
      };
    });
  }, []);

  // 防抖计算输入草稿的 Token 增量与费用
  const setDraft = useCallback(
    (text: string, modelObj?: BackendModelEntry | null) => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }

      if (!text || text.trim().length === 0) {
        setEstimatedDeltaTokens(0);
        setEstimatedCostRmb(0);
        return;
      }

      debounceTimerRef.current = setTimeout(() => {
        const tokens = estimatePromptTokens(text);
        const cost = estimatePromptCostRmb(tokens, modelObj?.inputPricePerK);
        setEstimatedDeltaTokens(tokens);
        setEstimatedCostRmb(cost);
      }, 120);
    },
    [],
  );

  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  // 计算告警等级与超额状态
  const quota = realtimeUsage?.quotaTokens ?? 0;
  const used = realtimeUsage?.usedTokens ?? 0;
  const percent = realtimeUsage?.usedPercent ?? 0;
  const threshold = realtimeUsage?.alertThresholdPercent ?? 80.0;

  const isOverBudget = quota > 0 && used >= quota;

  let alertLevel: BudgetAlertLevel = "normal";
  if (isOverBudget || percent >= 100) {
    alertLevel = "exceeded";
  } else if (percent >= threshold || percent >= 80) {
    alertLevel = "danger";
  } else if (percent >= 60) {
    alertLevel = "warning";
  } else {
    alertLevel = "normal";
  }

  const value: TokenBudgetContextValue = {
    realtimeUsage,
    loading,
    estimatedDeltaTokens,
    estimatedCostRmb,
    isOverBudget,
    alertLevel,
    refreshRealtimeUsage,
    updateFromSseUsage,
    setDraft,
  };

  return (
    <TokenBudgetContext.Provider value={value}>
      {children}
    </TokenBudgetContext.Provider>
  );
}

const DEFAULT_BUDGET_CONTEXT: TokenBudgetContextValue = {
  realtimeUsage: null,
  loading: false,
  estimatedDeltaTokens: 0,
  estimatedCostRmb: 0,
  isOverBudget: false,
  alertLevel: "normal",
  refreshRealtimeUsage: async () => {},
  updateFromSseUsage: () => {},
  setDraft: () => {},
};

export function useTokenBudget(): TokenBudgetContextValue {
  const context = useContext(TokenBudgetContext);
  return context ?? DEFAULT_BUDGET_CONTEXT;
}
