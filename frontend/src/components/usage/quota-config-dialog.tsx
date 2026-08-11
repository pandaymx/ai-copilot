"use client";

import { AlertTriangle, Loader2, Save, ShieldAlert, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import type { QuotaConfig } from "@/lib/api";

interface QuotaConfigDialogProps {
  open: boolean;
  config: QuotaConfig | null;
  saving?: boolean;
  onClose: () => void;
  onSave: (config: QuotaConfig) => Promise<void>;
}

export function QuotaConfigDialog({
  open,
  config,
  saving = false,
  onClose,
  onSave,
}: QuotaConfigDialogProps) {
  const [tokenQuotaStr, setTokenQuotaStr] = useState("10000000");
  const [alertPercentStr, setAlertPercentStr] = useState("80");
  const [costQuotaStr, setCostQuotaStr] = useState("500");
  const [errorMsg, setErrorMsg] = useState("");

  useEffect(() => {
    if (config) {
      setTokenQuotaStr(String(config.monthlyTokenQuota ?? 10000000));
      setAlertPercentStr(String(config.alertThresholdPercent ?? 80));
      setCostQuotaStr(String(config.monthlyCostQuotaRmb ?? 500));
    }
  }, [config]);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg("");

    const monthlyTokenQuota = Number(tokenQuotaStr);
    const alertThresholdPercent = Number(alertPercentStr);
    const monthlyCostQuotaRmb = Number(costQuotaStr);

    if (Number.isNaN(monthlyTokenQuota) || monthlyTokenQuota < 0) {
      setErrorMsg("月度 Token 配额上限请输入有效正整数或 0（无限制）");
      return;
    }
    if (
      Number.isNaN(alertThresholdPercent) ||
      alertThresholdPercent <= 0 ||
      alertThresholdPercent > 100
    ) {
      setErrorMsg("告警阈值百分比请输入 1 ~ 100 之间的数值（例如 80）");
      return;
    }
    if (Number.isNaN(monthlyCostQuotaRmb) || monthlyCostQuotaRmb < 0) {
      setErrorMsg("月度费用上限 RMB 请输入有效数字或 0（无限制）");
      return;
    }

    await onSave({
      monthlyTokenQuota,
      alertThresholdPercent,
      monthlyCostQuotaRmb,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="relative w-full max-w-md rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
        <div className="flex items-center justify-between border-b border-zinc-100 pb-3 dark:border-zinc-800">
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-xl bg-amber-500/10 text-amber-600 dark:bg-amber-500/20 dark:text-amber-400">
              <ShieldAlert className="size-4" />
            </div>
            <h2 className="font-heading text-base font-bold text-zinc-900 dark:text-zinc-100">
              配额阈值与告警设置
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
          >
            <X className="size-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {errorMsg && (
            <div className="flex items-center gap-2 rounded-xl bg-rose-500/10 p-3 text-xs font-medium text-rose-600 dark:bg-rose-500/20 dark:text-rose-400">
              <AlertTriangle className="size-4 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <div>
            <label
              htmlFor="token-quota-input"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              月度 Token 总配额上限
            </label>
            <p className="mt-0.5 text-[11px] text-zinc-400">
              全站用户单月允许消耗的总 Token 数（设为 0 表示无限制）
            </p>
            <input
              id="token-quota-input"
              type="number"
              value={tokenQuotaStr}
              onChange={(e) => setTokenQuotaStr(e.target.value)}
              placeholder="10000000"
              className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 px-3.5 py-2 text-xs font-mono text-zinc-900 outline-none transition focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100 dark:focus:border-indigo-500"
            />
          </div>

          <div>
            <label
              htmlFor="alert-percent-input"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              配额告警触发阈值 (%)
            </label>
            <p className="mt-0.5 text-[11px] text-zinc-400">
              当消耗量达到月配额此百分比时触发高亮告警 Banner（如 80%）
            </p>
            <input
              id="alert-percent-input"
              type="number"
              value={alertPercentStr}
              onChange={(e) => setAlertPercentStr(e.target.value)}
              placeholder="80"
              min="1"
              max="100"
              className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 px-3.5 py-2 text-xs font-mono text-zinc-900 outline-none transition focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100 dark:focus:border-indigo-500"
            />
          </div>

          <div>
            <label
              htmlFor="cost-quota-input"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              月度费用上限 (RMB 元)
            </label>
            <p className="mt-0.5 text-[11px] text-zinc-400">
              当月所有模型调用的预估费用控制上限（设为 0 表示无限制）
            </p>
            <input
              id="cost-quota-input"
              type="number"
              step="0.01"
              value={costQuotaStr}
              onChange={(e) => setCostQuotaStr(e.target.value)}
              placeholder="500"
              className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 px-3.5 py-2 text-xs font-mono text-zinc-900 outline-none transition focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100 dark:focus:border-indigo-500"
            />
          </div>

          <div className="mt-6 flex items-center justify-end gap-2.5 pt-3 border-t border-zinc-100 dark:border-zinc-800">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
              disabled={saving}
              className="text-xs"
            >
              取消
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={saving}
              className="gap-1.5 bg-gradient-to-r from-indigo-600 to-purple-600 text-xs font-semibold text-white shadow-md hover:from-indigo-500 hover:to-purple-500"
            >
              {saving ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  <span>保存中…</span>
                </>
              ) : (
                <>
                  <Save className="size-3.5" />
                  <span>保存阈值设置</span>
                </>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
