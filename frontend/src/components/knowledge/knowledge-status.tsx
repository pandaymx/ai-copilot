"use client";

import { Database, HardDrive, Layers, Power } from "lucide-react";
import { Card } from "@/components/ui/card";
import type { RagStatus } from "@/lib/api";

interface KnowledgeStatusProps {
  status: RagStatus | null;
  loading?: boolean;
}

function StatusCard({
  icon: Icon,
  label,
  value,
  accent,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: React.ReactNode;
  accent: string;
}) {
  return (
    <Card className="relative overflow-hidden border-zinc-200/70 bg-white/70 p-4 shadow-xs backdrop-blur-xl transition-all duration-200 hover:shadow-md hover:shadow-indigo-500/10 dark:border-zinc-800/70 dark:bg-zinc-900/60">
      <div
        className={`absolute -right-6 -top-6 size-20 rounded-full bg-gradient-to-br ${accent} opacity-10 blur-xl`}
      />
      <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-zinc-400 dark:text-zinc-500">
        <Icon className="size-3.5" />
        {label}
      </div>
      <div className="mt-2 text-lg font-bold text-zinc-800 dark:text-zinc-100">
        {value}
      </div>
    </Card>
  );
}

export function KnowledgeStatus({ status, loading }: KnowledgeStatusProps) {
  if (loading && !status) {
    return (
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[1, 2, 3, 4].map((i) => (
          <div
            key={i}
            className="h-20 animate-pulse rounded-2xl border border-zinc-200/70 bg-zinc-200/40 dark:border-zinc-800/70 dark:bg-zinc-900/40"
          />
        ))}
      </div>
    );
  }

  if (!status) {
    return (
      <Card className="border-rose-500/30 bg-rose-500/10 p-4 text-xs text-rose-600 dark:text-rose-400">
        无法获取向量库状态，请确认后端 RAG
        服务已启用（app.ai.rag.enabled=true）。
      </Card>
    );
  }

  const available = status.available;

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <StatusCard
        icon={Power}
        label="启用状态"
        accent="from-emerald-500 to-teal-500"
        value={
          <span className="flex items-center gap-1.5">
            <span
              className={`relative flex size-2.5 ${status.enabled ? "" : "opacity-50"}`}
            >
              {status.enabled && (
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              )}
              <span
                className={`relative inline-flex size-2.5 rounded-full ${status.enabled ? "bg-emerald-500" : "bg-zinc-400"}`}
              />
            </span>
            {status.enabled ? "已启用" : "未启用"}
          </span>
        }
      />
      <StatusCard
        icon={Database}
        label="向量库可用性"
        accent="from-indigo-500 to-purple-500"
        value={
          <span className="flex items-center gap-1.5">
            <span
              className={`relative flex size-2.5 ${available ? "" : "opacity-50"}`}
            >
              {available && (
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              )}
              <span
                className={`relative inline-flex size-2.5 rounded-full ${available ? "bg-emerald-500" : "bg-rose-500"}`}
              />
            </span>
            {available ? "在线" : "离线"}
          </span>
        }
      />
      <StatusCard
        icon={HardDrive}
        label="集合名称"
        accent="from-violet-500 to-fuchsia-500"
        value={
          <span className="truncate text-sm font-semibold">
            {status.collectionName || "—"}
          </span>
        }
      />
      <StatusCard
        icon={Layers}
        label="文档 / 向量"
        accent="from-pink-500 to-rose-500"
        value={
          <span>
            {status.documentCount}
            <span className="text-xs font-normal text-zinc-400">
              {" "}
              篇 / {status.vectorCount} 向量
            </span>
          </span>
        }
      />
    </div>
  );
}
