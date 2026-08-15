"use client";

import {
  Check,
  Code2,
  Copy,
  Download,
  Eye,
  Maximize2,
  Minimize2,
  RotateCcw,
  Sparkles,
  Terminal,
  Trash2,
  X,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

interface InteractiveArtifactViewerProps {
  artifact: ArtifactItem;
  className?: string;
}

interface ConsoleLogItem {
  id: string;
  type: "log" | "warn" | "error" | "info";
  message: string;
  timestamp: number;
}

/** 清理并提取 HTML/JS 内容 */
function cleanHtmlSnippet(content?: string): string {
  if (!content || !content.trim()) return "";
  let clean = content.trim();
  if (clean.startsWith("```html") || clean.startsWith("```htm")) {
    clean = clean
      .replace(/^```html?/, "")
      .replace(/```$/, "")
      .trim();
  } else if (clean.startsWith("```")) {
    clean = clean
      .replace(/^```\w*/, "")
      .replace(/```$/, "")
      .trim();
  }
  return clean;
}

export function InteractiveArtifactViewer({
  artifact,
  className,
}: InteractiveArtifactViewerProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [activeTab, setActiveTab] = useState<"preview" | "source" | "console">(
    "preview",
  );
  const [logs, setLogs] = useState<ConsoleLogItem[]>([]);
  const [copied, setCopied] = useState<boolean>(false);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [key, setKey] = useState<number>(0);

  const rawHtml = useMemo(
    () => cleanHtmlSnippet(artifact.content),
    [artifact.content],
  );

  // 构建注入了控制台拦截和现代化基础样式的沙箱 HTML
  const sandboxedSrcDoc = useMemo(() => {
    const consoleBridgeScript = `
      <script>
        (function() {
          function sendLog(type, args) {
            try {
              var msg = Array.prototype.slice.call(args).map(function(arg) {
                if (typeof arg === 'object') {
                  try { return JSON.stringify(arg); } catch(e) { return String(arg); }
                }
                return String(arg);
              }).join(' ');
              window.parent.postMessage({
                source: 'copilot-sandbox',
                artifactId: '${artifact.artifactId}',
                type: type,
                message: msg
              }, '*');
            } catch(err) {}
          }
          var _log = console.log, _warn = console.warn, _error = console.error, _info = console.info;
          console.log = function() { sendLog('log', arguments); _log.apply(console, arguments); };
          console.warn = function() { sendLog('warn', arguments); _warn.apply(console, arguments); };
          console.error = function() { sendLog('error', arguments); _error.apply(console, arguments); };
          console.info = function() { sendLog('info', arguments); _info.apply(console, arguments); };
          window.onerror = function(msg, url, line) {
            sendLog('error', ['[Uncaught]', msg, 'at line ' + line]);
          };
        })();
      </script>
    `;

    // 如果本身就是完整 HTML
    if (
      rawHtml.toLowerCase().includes("<html") ||
      rawHtml.toLowerCase().includes("<!doctype")
    ) {
      return rawHtml.replace(/<head>/i, `<head>${consoleBridgeScript}`);
    }

    // 片段包裹
    return `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          ${consoleBridgeScript}
          <style>
            *, *::before, *::after { box-sizing: border-box; }
            body {
              margin: 0;
              padding: 16px;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              color: #18181b;
              background-color: transparent;
            }
            @media (prefers-color-scheme: dark) {
              body { color: #f4f4f5; }
            }
          </style>
        </head>
        <body>
          ${rawHtml}
        </body>
      </html>
    `;
  }, [rawHtml, artifact.artifactId]);

  // 监听来自沙箱 iframe 的控制台 postMessage
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (
        event.data &&
        event.data.source === "copilot-sandbox" &&
        event.data.artifactId === artifact.artifactId
      ) {
        setLogs((prev) => [
          ...prev.slice(-99),
          {
            id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            type: event.data.type || "log",
            message: event.data.message || "",
            timestamp: Date.now(),
          },
        ]);
      }
    };

    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, [artifact.artifactId]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(rawHtml || artifact.content || "");
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  const handleDownload = () => {
    if (!rawHtml) return;
    const blob = new Blob([sandboxedSrcDoc], {
      type: "text/html;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `interactive-artifact-${artifact.artifactId || Date.now()}.html`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleReload = () => {
    setKey((prev) => prev + 1);
    setLogs([]);
  };

  const contentBody = (
    <div className="space-y-3">
      {/* 视图 Tab 与控制栏 */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-indigo-100/60 pb-2.5 dark:border-indigo-900/40">
        <div className="flex items-center gap-2 min-w-0">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
            <Sparkles className="size-4" />
          </div>
          <div className="min-w-0">
            <h4 className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
              {artifact.title || "交互式 HTML/JS 组件沙箱"}
            </h4>
            <span className="text-[10px] text-zinc-400">
              独立 IFrame 沙箱环境 · 实时热执行
            </span>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          {/* Tab 切换器 */}
          <div className="flex items-center rounded-lg bg-zinc-100 p-0.5 dark:bg-zinc-800">
            <button
              type="button"
              onClick={() => setActiveTab("preview")}
              className={cn(
                "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                activeTab === "preview"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <Eye className="size-3" />
              <span>运行预览</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("source")}
              className={cn(
                "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                activeTab === "source"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <Code2 className="size-3" />
              <span>源码</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("console")}
              className={cn(
                "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                activeTab === "console"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <Terminal className="size-3" />
              <span>控制台</span>
              {logs.length > 0 && (
                <span className="rounded-full bg-indigo-500/20 px-1 text-[9px] font-bold text-indigo-600 dark:text-indigo-300">
                  {logs.length}
                </span>
              )}
            </button>
          </div>

          <button
            type="button"
            onClick={handleReload}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white p-1 text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="重新运行 / 重置"
          >
            <RotateCcw className="size-3.5" />
          </button>

          <button
            type="button"
            onClick={handleCopy}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="复制代码"
          >
            {copied ? (
              <Check className="size-3 text-emerald-500" />
            ) : (
              <Copy className="size-3" />
            )}
          </button>

          <button
            type="button"
            onClick={handleDownload}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="下载 HTML 文件"
          >
            <Download className="size-3" />
          </button>

          <button
            type="button"
            onClick={() => setIsFullscreen(!isFullscreen)}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white p-1 text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title={isFullscreen ? "退出全屏" : "全屏放大"}
          >
            {isFullscreen ? (
              <Minimize2 className="size-3.5" />
            ) : (
              <Maximize2 className="size-3.5" />
            )}
          </button>
        </div>
      </div>

      {/* 主展示区 */}
      <div className="relative overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-2xs dark:border-zinc-800 dark:bg-zinc-900 min-h-[300px]">
        {activeTab === "preview" ? (
          <iframe
            key={key}
            ref={iframeRef}
            srcDoc={sandboxedSrcDoc}
            title={artifact.title || "Interactive Sandbox"}
            sandbox="allow-scripts allow-modals"
            className="h-[360px] w-full border-0 bg-transparent"
          />
        ) : activeTab === "source" ? (
          <div className="rounded-xl bg-zinc-950 p-4 font-mono text-xs text-emerald-300">
            <pre className="max-h-[360px] overflow-y-auto whitespace-pre-wrap">
              {rawHtml}
            </pre>
          </div>
        ) : (
          /* 控制台日志面板 */
          <div className="flex h-[360px] flex-col bg-zinc-950 p-3 font-mono text-xs text-zinc-300">
            <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
              <span className="text-[11px] text-zinc-400">
                捕获控制台输出 ({logs.length} 条)
              </span>
              <button
                type="button"
                onClick={() => setLogs([])}
                className="flex items-center gap-1 rounded px-2 py-0.5 text-[10px] text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200"
              >
                <Trash2 className="size-3" />
                <span>清空</span>
              </button>
            </div>
            <div className="flex-1 space-y-1.5 overflow-y-auto pt-2 scrollbar-hidden">
              {logs.length === 0 ? (
                <div className="py-12 text-center text-zinc-600">
                  暂无控制台日志输出
                </div>
              ) : (
                logs.map((log) => (
                  <div
                    key={log.id}
                    className={cn(
                      "flex items-start gap-2 rounded px-2 py-1 text-[11px]",
                      log.type === "error"
                        ? "bg-rose-950/40 text-rose-300 border-l-2 border-rose-500"
                        : log.type === "warn"
                          ? "bg-amber-950/40 text-amber-300 border-l-2 border-amber-500"
                          : "text-zinc-300",
                    )}
                  >
                    <span className="text-[9px] text-zinc-500 shrink-0">
                      {new Date(log.timestamp).toLocaleTimeString()}
                    </span>
                    <span className="font-semibold uppercase text-[9px] text-zinc-400">
                      [{log.type}]
                    </span>
                    <span className="break-all whitespace-pre-wrap">
                      {log.message}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );

  return (
    <>
      <div
        className={cn(
          "group relative my-3 overflow-hidden rounded-2xl border border-indigo-200/70 bg-gradient-to-br from-indigo-50/40 via-white to-purple-50/30 p-4 shadow-sm transition-all duration-300 dark:border-indigo-900/60 dark:from-zinc-950 dark:via-zinc-900 dark:to-indigo-950/30 backdrop-blur-md",
          className,
        )}
      >
        {contentBody}
      </div>

      {/* 全屏模态框 */}
      {isFullscreen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 sm:p-6 backdrop-blur-xs animate-in fade-in duration-200">
          <div className="flex h-[90vh] w-full max-w-6xl flex-col rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
            <div className="flex items-center justify-between border-b border-zinc-200/80 pb-3 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <Sparkles className="size-5 text-indigo-600" />
                <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                  {artifact.title || "交互式 HTML/JS 组件沙箱 (全屏预览)"}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsFullscreen(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="size-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto pt-4">{contentBody}</div>
          </div>
        </div>
      )}
    </>
  );
}
