"use client";

import {
  ArrowLeft,
  BookOpen,
  Check,
  Copy,
  Radio,
  RefreshCw,
  Server,
  Terminal,
  Wrench,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

interface McpTool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

interface McpResource {
  uri: string;
  name: string;
  description: string;
  mimeType: string;
}

interface McpStatus {
  enabled: boolean;
  serverName: string;
  serverVersion: string;
  toolsCount: number;
  ragResourceEnabled: boolean;
}

export default function McpServerSettingsPage() {
  const [status, setStatus] = useState<McpStatus | null>(null);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [resources, setResources] = useState<McpResource[]>([]);
  const [loading, setLoading] = useState(false);
  const [copiedType, setCopiedType] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"config" | "tools" | "resources">(
    "config",
  );

  const origin =
    typeof window !== "undefined"
      ? window.location.origin
      : "http://localhost:8080";

  const loadMcpData = useCallback(async () => {
    try {
      setLoading(true);
      const [statusRes, toolsRes, resRes] = await Promise.all([
        fetch("/mcp/status").then((r) => (r.ok ? r.json() : null)),
        fetch("/mcp/tools").then((r) => (r.ok ? r.json() : null)),
        fetch("/mcp/resources").then((r) => (r.ok ? r.json() : null)),
      ]);

      if (statusRes) setStatus(statusRes);
      if (toolsRes?.tools) setTools(toolsRes.tools);
      if (resRes?.resources) setResources(resRes.resources);
    } catch {
      toast.error("加载 MCP Server 信息失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMcpData();
  }, [loadMcpData]);

  const claudeDesktopConfig = useMemo(() => {
    return JSON.stringify(
      {
        mcpServers: {
          "ai-copilot": {
            url: `${origin}/mcp/sse`,
            transport: "sse",
          },
        },
      },
      null,
      2,
    );
  }, [origin]);

  const cursorConfig = useMemo(() => {
    return JSON.stringify(
      {
        name: "ai-copilot",
        type: "sse",
        url: `${origin}/mcp/sse`,
      },
      null,
      2,
    );
  }, [origin]);

  const handleCopy = async (text: string, type: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedType(type);
      toast.success("配置已复制到剪贴板");
      setTimeout(() => setCopiedType(null), 2000);
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 flex flex-col">
      {/* 顶部导航栏 */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 bg-white/80 dark:bg-zinc-900/80 px-4 sm:px-6 py-3.5 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          >
            <ArrowLeft className="size-3.5" />
            <span>返回对话</span>
          </Link>
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 flex items-center justify-center">
              <Server className="size-4" />
            </div>
            <div>
              <h1 className="text-sm sm:text-base font-bold">
                MCP Server 模式 (Model Context Protocol)
              </h1>
              <p className="text-[11px] text-zinc-400">
                将内置工具库与 RAG 知识库暴露给 Claude Desktop / Cursor / VS
                Code Copilot
              </p>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => void loadMcpData()}
          disabled={loading}
          className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={cn("size-3.5", loading && "animate-spin")} />
          <span>刷新状态</span>
        </button>
      </header>

      {/* 核心展示区 */}
      <main className="flex-1 max-w-5xl w-full mx-auto p-4 sm:p-6 space-y-6">
        {/* 服务状态卡片 */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
          <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 space-y-1 shadow-2xs">
            <div className="flex items-center justify-between text-xs text-zinc-400 font-medium">
              <span>运行状态</span>
              <span className="size-2 rounded-full bg-emerald-500 animate-pulse" />
            </div>
            <div className="text-lg font-bold text-emerald-600 dark:text-emerald-400">
              {status?.enabled ? "MCP Server 已就绪" : "已离线"}
            </div>
            <div className="text-[11px] text-zinc-400">
              协议版本 2024-11-05 (JSON-RPC 2.0)
            </div>
          </div>

          <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 space-y-1 shadow-2xs">
            <div className="flex items-center justify-between text-xs text-zinc-400 font-medium">
              <span>暴露工具数</span>
              <Wrench className="size-3.5 text-indigo-500" />
            </div>
            <div className="text-lg font-bold text-zinc-900 dark:text-white">
              {tools.length} 个内置工具
            </div>
            <div className="text-[11px] text-zinc-400">
              包含计算器、代码执行、数据库、知识库查询等
            </div>
          </div>

          <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 space-y-1 shadow-2xs">
            <div className="flex items-center justify-between text-xs text-zinc-400 font-medium">
              <span>传输端点 (SSE)</span>
              <Radio className="size-3.5 text-cyan-500" />
            </div>
            <div className="text-xs font-mono font-bold text-cyan-600 dark:text-cyan-400 truncate">
              /mcp/sse
            </div>
            <div className="text-[11px] text-zinc-400">
              支持 SSE 长连接与 HTTP 消息通道
            </div>
          </div>
        </div>

        {/* Tab 导航 */}
        <div className="flex items-center gap-2 border-b border-zinc-200 dark:border-zinc-800 pb-2 text-xs font-semibold">
          {[
            { id: "config", label: "接入客户端配置", icon: Terminal },
            {
              id: "tools",
              label: `已暴露工具 (${tools.length})`,
              icon: Wrench,
            },
            {
              id: "resources",
              label: `知识库 Resources (${resources.length})`,
              icon: BookOpen,
            },
          ].map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setActiveTab(t.id as typeof activeTab)}
              className={cn(
                "flex items-center gap-1.5 px-3 py-1.5 rounded-xl transition-colors",
                activeTab === t.id
                  ? "bg-cyan-600 text-white shadow-xs"
                  : "text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800",
              )}
            >
              <t.icon className="size-3.5" />
              <span>{t.label}</span>
            </button>
          ))}
        </div>

        {/* Tab 1: 客户端配置代码 */}
        {activeTab === "config" && (
          <div className="space-y-5 animate-in fade-in">
            {/* Claude Desktop */}
            <div className="rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-5 space-y-3 shadow-2xs">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="size-7 rounded-lg bg-orange-500/10 text-orange-600 flex items-center justify-center font-bold text-xs">
                    C
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold">
                      Claude Desktop 接入配置 (claude_desktop_config.json)
                    </h3>
                    <p className="text-[11px] text-zinc-400">
                      粘贴至 macOS: ~/Library/Application Support/Claude/ 或
                      Windows: %APPDATA%/Claude/
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void handleCopy(claudeDesktopConfig, "claude")}
                  className="flex items-center gap-1.5 px-3 py-1 rounded-xl bg-zinc-100 dark:bg-zinc-800 hover:bg-orange-50 dark:hover:bg-orange-950/50 text-xs font-semibold text-zinc-700 dark:text-zinc-300 hover:text-orange-600 transition-colors"
                >
                  {copiedType === "claude" ? (
                    <Check className="size-3.5 text-emerald-500" />
                  ) : (
                    <Copy className="size-3.5" />
                  )}
                  <span>复制配置</span>
                </button>
              </div>

              <pre className="p-3.5 rounded-xl bg-zinc-950 text-zinc-200 font-mono text-xs overflow-x-auto leading-relaxed border border-zinc-800">
                {claudeDesktopConfig}
              </pre>
            </div>

            {/* Cursor */}
            <div className="rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-5 space-y-3 shadow-2xs">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="size-7 rounded-lg bg-blue-500/10 text-blue-600 flex items-center justify-center font-bold text-xs">
                    ⚡
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold">
                      Cursor / VS Code MCP 接入配置
                    </h3>
                    <p className="text-[11px] text-zinc-400">
                      在 Cursor 设置 → Features → MCP Servers 中添加 SSE
                      协议端点
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void handleCopy(cursorConfig, "cursor")}
                  className="flex items-center gap-1.5 px-3 py-1 rounded-xl bg-zinc-100 dark:bg-zinc-800 hover:bg-blue-50 dark:hover:bg-blue-950/50 text-xs font-semibold text-zinc-700 dark:text-zinc-300 hover:text-blue-600 transition-colors"
                >
                  {copiedType === "cursor" ? (
                    <Check className="size-3.5 text-emerald-500" />
                  ) : (
                    <Copy className="size-3.5" />
                  )}
                  <span>复制配置</span>
                </button>
              </div>

              <pre className="p-3.5 rounded-xl bg-zinc-950 text-zinc-200 font-mono text-xs overflow-x-auto leading-relaxed border border-zinc-800">
                {cursorConfig}
              </pre>
            </div>
          </div>
        )}

        {/* Tab 2: 工具列表 */}
        {activeTab === "tools" && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5 animate-in fade-in">
            {tools.map((t) => (
              <div
                key={t.name}
                className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 space-y-2 shadow-2xs"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <Wrench className="size-3.5 text-cyan-600 dark:text-cyan-400" />
                    <span className="font-bold text-xs font-mono text-zinc-900 dark:text-white">
                      {t.name}
                    </span>
                  </div>
                  <span className="px-2 py-0.5 rounded-md bg-zinc-100 dark:bg-zinc-800 text-[10px] font-mono text-zinc-500">
                    @Tool
                  </span>
                </div>
                <p className="text-xs text-zinc-500 dark:text-zinc-400 line-clamp-2">
                  {t.description || "暂无描述"}
                </p>
                {t.inputSchema && (
                  <pre className="p-2 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-[10px] font-mono text-zinc-600 dark:text-zinc-400 overflow-x-auto max-h-24">
                    {JSON.stringify(t.inputSchema, null, 2)}
                  </pre>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Tab 3: 知识库 Resources */}
        {activeTab === "resources" && (
          <div className="space-y-3.5 animate-in fade-in">
            {resources.map((r) => (
              <div
                key={r.uri}
                className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 space-y-2 shadow-2xs"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <BookOpen className="size-3.5 text-indigo-500" />
                    <span className="font-bold text-xs text-zinc-900 dark:text-white">
                      {r.name}
                    </span>
                  </div>
                  <span className="px-2 py-0.5 rounded-md bg-indigo-50 dark:bg-indigo-950/60 text-[10px] font-mono text-indigo-600 dark:text-indigo-400">
                    {r.mimeType}
                  </span>
                </div>
                <p className="text-xs text-zinc-500 dark:text-zinc-400">
                  {r.description}
                </p>
                <div className="p-2 rounded-lg bg-zinc-50 dark:bg-zinc-950 text-[11px] font-mono text-cyan-600 dark:text-cyan-400">
                  URI: {r.uri}
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
