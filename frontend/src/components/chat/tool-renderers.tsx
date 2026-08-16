"use client";

import katex from "katex";
import {
  AlertCircle,
  AlertTriangle,
  BarChart3,
  BookOpen,
  Calculator,
  Calendar,
  CalendarDays,
  Check,
  CheckSquare,
  ChevronDown,
  ChevronRight,
  Clock,
  Code2,
  Copy,
  Database,
  Download,
  ExternalLink,
  FileText,
  Flag,
  Globe,
  ImageIcon,
  Lightbulb,
  ListTodo,
  Loader2,
  Mail,
  Maximize2,
  Search,
  Server,
  ShieldCheck,
  Table,
  Tag,
  Terminal,
  Users,
} from "lucide-react";
import { useState } from "react";
import { PrismLight as SyntaxHighlighter } from "react-syntax-highlighter";
import bash from "react-syntax-highlighter/dist/esm/languages/prism/bash";
import c from "react-syntax-highlighter/dist/esm/languages/prism/c";
import cpp from "react-syntax-highlighter/dist/esm/languages/prism/cpp";
import css from "react-syntax-highlighter/dist/esm/languages/prism/css";
import docker from "react-syntax-highlighter/dist/esm/languages/prism/docker";
import go from "react-syntax-highlighter/dist/esm/languages/prism/go";
import java from "react-syntax-highlighter/dist/esm/languages/prism/java";
import javascript from "react-syntax-highlighter/dist/esm/languages/prism/javascript";
import json from "react-syntax-highlighter/dist/esm/languages/prism/json";
import jsx from "react-syntax-highlighter/dist/esm/languages/prism/jsx";
import markdown from "react-syntax-highlighter/dist/esm/languages/prism/markdown";
import xml from "react-syntax-highlighter/dist/esm/languages/prism/markup";
import python from "react-syntax-highlighter/dist/esm/languages/prism/python";
import rust from "react-syntax-highlighter/dist/esm/languages/prism/rust";
import sql from "react-syntax-highlighter/dist/esm/languages/prism/sql";
import tsx from "react-syntax-highlighter/dist/esm/languages/prism/tsx";
import typescript from "react-syntax-highlighter/dist/esm/languages/prism/typescript";
import yaml from "react-syntax-highlighter/dist/esm/languages/prism/yaml";
import oneDark from "react-syntax-highlighter/dist/esm/styles/prism/one-dark";
import { ImagePreviewModal } from "@/components/chat/image-preview-modal";

import { cn } from "@/lib/utils";

// 注册常用语法高亮语言
SyntaxHighlighter.registerLanguage("javascript", javascript);
SyntaxHighlighter.registerLanguage("js", javascript);
SyntaxHighlighter.registerLanguage("typescript", typescript);
SyntaxHighlighter.registerLanguage("ts", typescript);
SyntaxHighlighter.registerLanguage("jsx", jsx);
SyntaxHighlighter.registerLanguage("tsx", tsx);
SyntaxHighlighter.registerLanguage("java", java);
SyntaxHighlighter.registerLanguage("python", python);
SyntaxHighlighter.registerLanguage("py", python);
SyntaxHighlighter.registerLanguage("bash", bash);
SyntaxHighlighter.registerLanguage("sh", bash);
SyntaxHighlighter.registerLanguage("json", json);
SyntaxHighlighter.registerLanguage("yaml", yaml);
SyntaxHighlighter.registerLanguage("yml", yaml);
SyntaxHighlighter.registerLanguage("css", css);
SyntaxHighlighter.registerLanguage("sql", sql);
SyntaxHighlighter.registerLanguage("markdown", markdown);
SyntaxHighlighter.registerLanguage("md", markdown);
SyntaxHighlighter.registerLanguage("c", c);
SyntaxHighlighter.registerLanguage("cpp", cpp);
SyntaxHighlighter.registerLanguage("go", go);
SyntaxHighlighter.registerLanguage("rust", rust);
SyntaxHighlighter.registerLanguage("xml", xml);
SyntaxHighlighter.registerLanguage("html", xml);
SyntaxHighlighter.registerLanguage("docker", docker);
SyntaxHighlighter.registerLanguage("dockerfile", docker);

/** 安全地将 JSON 字符串解析为对象或返回 fallback */
function safeParseJson<T = Record<string, unknown>>(raw?: string): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

/** 一键复制按钮 Hook/Component */
function CopyButton({
  content,
  label = "复制",
}: {
  content: string;
  label?: string;
}) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // 忽略复制失败
    }
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={cn(
        "inline-flex items-center gap-1 rounded-md px-2 py-0.5 font-mono text-[10px] transition-all duration-200",
        copied
          ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
          : "text-zinc-400 hover:bg-white/10 hover:text-zinc-200",
      )}
      aria-label={label}
    >
      {copied ? (
        <>
          <Check className="size-3 text-emerald-400" />
          <span>已复制</span>
        </>
      ) : (
        <>
          <Copy className="size-3" />
          <span>{label}</span>
        </>
      )}
    </button>
  );
}

// ----------------------------------------------------------------------
// 1. HTTP Request 专属结构化渲染器 (http_request)
// ----------------------------------------------------------------------
export function HttpRequestRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const [showHeaders, setShowHeaders] = useState(false);
  const [showBody, setShowBody] = useState(true);

  const args = safeParseJson<{ method?: string; url?: string; body?: string }>(
    argsJson,
  );
  const res = safeParseJson<{
    status?: string | number;
    statusCode?: number;
    length?: number;
    headers?: Record<string, string>;
    body?: unknown;
  }>(resultJson);

  const method = args?.method?.toUpperCase() || "GET";
  const url = args?.url || "";
  const statusCode =
    res?.statusCode ?? (typeof res?.status === "number" ? res.status : null);
  const statusStr =
    res?.status !== undefined
      ? String(res.status)
      : statusCode
        ? String(statusCode)
        : "OK";

  // 判断 HTTP 状态码类别 (2xx, 4xx, 5xx)
  const is2xx = statusCode
    ? statusCode >= 200 && statusCode < 300
    : statusStr.toLowerCase() === "ok" || statusStr === "200";
  const isErrorStatus = statusCode
    ? statusCode >= 400
    : statusStr.startsWith("4") || statusStr.startsWith("5");

  // Body 处理
  let bodyText = "";
  if (res?.body !== undefined) {
    if (typeof res.body === "string") {
      const parsedBody = safeParseJson(res.body);
      bodyText = parsedBody ? JSON.stringify(parsedBody, null, 2) : res.body;
    } else {
      bodyText = JSON.stringify(res.body, null, 2);
    }
  } else if (resultJson) {
    bodyText = resultJson;
  }

  const headers = res?.headers || null;

  return (
    <div className="space-y-2.5 rounded-xl border border-sky-500/20 bg-sky-950/10 p-3 text-xs dark:border-sky-500/30 dark:bg-sky-950/20">
      {/* 头部：Method + URL + Status Badge */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-sky-500/20 pb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-sky-500/20 text-sky-400">
            <Server className="size-3.5" />
          </span>
          <span className="rounded-md bg-sky-500/20 px-1.5 py-0.5 font-mono text-[10px] font-bold text-sky-300">
            {method}
          </span>
          <span
            className="truncate font-mono text-[11px] text-zinc-300"
            title={url}
          >
            {url || "HTTP Request"}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {res?.length !== undefined && (
            <span className="font-mono text-[10px] text-zinc-400">
              {res.length} bytes
            </span>
          )}
          <span
            className={cn(
              "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold font-mono",
              is2xx
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                : isErrorStatus
                  ? "bg-rose-500/20 text-rose-400 border border-rose-500/30"
                  : "bg-sky-500/20 text-sky-300 border border-sky-500/30",
            )}
          >
            {statusCode ? `HTTP ${statusCode}` : statusStr}
          </span>
        </div>
      </div>

      {/* Headers 键值预览 (可折叠) */}
      {headers && Object.keys(headers).length > 0 && (
        <div className="rounded-lg bg-black/30 p-2">
          <button
            type="button"
            onClick={() => setShowHeaders((v) => !v)}
            className="flex w-full items-center justify-between text-[11px] font-medium text-sky-300 hover:text-sky-200"
          >
            <span className="flex items-center gap-1 font-mono">
              Response Headers ({Object.keys(headers).length})
            </span>
            {showHeaders ? (
              <ChevronDown className="size-3.5" />
            ) : (
              <ChevronRight className="size-3.5" />
            )}
          </button>
          {showHeaders && (
            <div className="mt-2 space-y-1 font-mono text-[10px] text-zinc-300">
              {Object.entries(headers).map(([k, v]) => (
                <div key={k} className="flex gap-2">
                  <span className="font-semibold text-sky-400">{k}:</span>
                  <span className="truncate text-zinc-300">{v}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Response Body 折叠/预览区 */}
      {bodyText && (
        <div className="rounded-lg bg-zinc-950/80 border border-zinc-800/80 overflow-hidden">
          <div className="flex items-center justify-between border-b border-zinc-800 px-3 py-1.5">
            <button
              type="button"
              onClick={() => setShowBody((v) => !v)}
              className="flex items-center gap-1.5 font-mono text-[11px] font-medium text-zinc-300 hover:text-zinc-100"
            >
              <Code2 className="size-3 text-sky-400" />
              <span>Response Body</span>
              {showBody ? (
                <ChevronDown className="size-3 text-zinc-400" />
              ) : (
                <ChevronRight className="size-3 text-zinc-400" />
              )}
            </button>
            <CopyButton content={bodyText} />
          </div>
          {showBody && (
            <div className="max-h-56 overflow-auto p-2.5 font-mono text-[11px]">
              <SyntaxHighlighter
                language="json"
                style={oneDark}
                customStyle={{
                  margin: 0,
                  padding: 0,
                  background: "transparent",
                  fontSize: "0.75rem",
                }}
              >
                {bodyText}
              </SyntaxHighlighter>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ----------------------------------------------------------------------
// 2. Calculator 专属结构化渲染器 (calculator)
// ----------------------------------------------------------------------
export function CalculatorRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const args = safeParseJson<{ expression?: string; expr?: string }>(argsJson);
  const res = safeParseJson<{
    output?: number | string;
    result?: number | string;
  }>(resultJson);

  const rawExpression = args?.expression || args?.expr || "";
  const rawOutput =
    res?.output ??
    res?.result ??
    (resultJson ? (safeParseJson(resultJson) ?? resultJson) : "");

  // 格式化表达式供 KaTeX 展现
  const formattedExpr = rawExpression
    .replace(/\*/g, " \\times ")
    .replace(/\//g, " \\div ");

  let katexHtml = "";
  let isKatexSuccess = false;
  if (rawExpression) {
    try {
      const tex = `\\text{${formattedExpr.replace(/\\times|\\div/g, (m) => `} ${m} \\text{`)}} = ${rawOutput}`;
      katexHtml = katex.renderToString(tex, {
        throwOnError: false,
        displayMode: true,
      });
      isKatexSuccess = true;
    } catch {
      isKatexSuccess = false;
    }
  }

  return (
    <div className="space-y-2 rounded-xl border border-indigo-500/20 bg-gradient-to-br from-indigo-950/20 via-purple-950/15 to-violet-950/20 p-3.5 text-xs shadow-inner">
      <div className="flex items-center justify-between border-b border-indigo-500/20 pb-2">
        <div className="flex items-center gap-2">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-indigo-500/20 text-indigo-400">
            <Calculator className="size-3.5" />
          </span>
          <span className="font-semibold text-indigo-300 text-xs">
            数学公式求值
          </span>
        </div>
        <CopyButton content={String(rawOutput)} label="复制结果" />
      </div>

      {/* KaTeX 公式卡片 */}
      <div className="my-2 rounded-lg bg-black/40 p-3 text-center overflow-x-auto border border-indigo-500/10">
        {isKatexSuccess && katexHtml ? (
          <div
            className="katex-display my-1 text-indigo-100 [&_.katex]:text-sm sm:[&_.katex]:text-base"
            // biome-ignore lint/security/noDangerouslySetInnerHtml: KaTeX 受控渲染 HTML 字符串
            dangerouslySetInnerHTML={{ __html: katexHtml }}
          />
        ) : (
          <div className="font-mono text-xs text-indigo-200">
            <span>{rawExpression}</span>
            <span className="mx-2 text-indigo-400 font-bold">=</span>
            <span className="text-emerald-400 font-bold">
              {String(rawOutput)}
            </span>
          </div>
        )}
      </div>

      {/* 底部结果展示 */}
      <div className="flex items-center justify-between rounded-lg bg-indigo-950/30 px-3 py-1.5 font-mono text-[11px]">
        <span className="text-zinc-400">计算结果 (Result):</span>
        <span className="font-bold text-emerald-400 text-sm">
          {String(rawOutput)}
        </span>
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------
// 3. File Read 专属结构化渲染器 (file_read / file_write)
// ----------------------------------------------------------------------
export function FileReadRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const args = safeParseJson<{ path?: string; content?: string }>(argsJson);
  const res = safeParseJson<{ output?: string; content?: string }>(resultJson);

  const filePath = args?.path || "file";
  const fileContent =
    res?.output ??
    res?.content ??
    (typeof resultJson === "string" ? resultJson : "");

  // 从文件名中提取扩展名
  const ext = filePath.includes(".")
    ? filePath.split(".").pop()?.toLowerCase() || ""
    : "";
  const langMap: Record<string, string> = {
    ts: "typescript",
    tsx: "tsx",
    js: "javascript",
    jsx: "jsx",
    py: "python",
    java: "java",
    json: "json",
    md: "markdown",
    sql: "sql",
    html: "html",
    css: "css",
    sh: "bash",
    yaml: "yaml",
    yml: "yaml",
  };
  const language = langMap[ext] || ext || "text";
  const lineCount = fileContent ? fileContent.split("\n").length : 0;

  return (
    <div className="space-y-2 rounded-xl border border-amber-500/20 bg-zinc-950/90 p-3 text-xs">
      {/* 头部：文件名 + 扩展名 Badge + 行数 + 复制 */}
      <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-amber-500/20 text-amber-400">
            <FileText className="size-3.5" />
          </span>
          <span
            className="truncate font-mono text-[11px] font-semibold text-zinc-200"
            title={filePath}
          >
            {filePath}
          </span>
          <span className="rounded bg-zinc-800 px-1.5 py-0.5 font-mono text-[9px] uppercase text-amber-400">
            {language}
          </span>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <span className="font-mono text-[10px] text-zinc-400">
            {lineCount} 行
          </span>
          <CopyButton content={fileContent} />
        </div>
      </div>

      {/* 代码视图 */}
      <div className="max-h-60 overflow-auto rounded-lg bg-black/50 p-2.5 font-mono text-[11px]">
        {fileContent ? (
          <SyntaxHighlighter
            language={language}
            style={oneDark}
            showLineNumbers={lineCount > 3}
            lineNumberStyle={{
              minWidth: "2rem",
              paddingRight: "0.8rem",
              color: "rgba(156, 163, 175, 0.4)",
              textAlign: "right",
              userSelect: "none",
            }}
            customStyle={{
              margin: 0,
              padding: 0,
              background: "transparent",
              fontSize: "0.75rem",
            }}
          >
            {fileContent}
          </SyntaxHighlighter>
        ) : (
          <div className="text-zinc-500 italic text-center py-2">
            文件内容为空
          </div>
        )}
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------
// 4. Knowledge Query 专属结构化渲染器 (knowledge_query)
// ----------------------------------------------------------------------
export function KnowledgeQueryRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const args = safeParseJson<{ query?: string }>(argsJson);
  const res = safeParseJson<{
    count?: number;
    documents?: Array<{
      content?: string;
      metadata?: Record<string, unknown>;
    }>;
  }>(resultJson);

  const query = args?.query || "";
  const docs = res?.documents || [];
  const count = res?.count ?? docs.length;

  return (
    <div className="space-y-2.5 rounded-xl border border-emerald-500/20 bg-emerald-950/10 p-3 text-xs dark:border-emerald-500/30 dark:bg-emerald-950/20">
      {/* 顶栏：查询词 + 命中文档数 */}
      <div className="flex items-center justify-between border-b border-emerald-500/20 pb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-emerald-500/20 text-emerald-400">
            <BookOpen className="size-3.5" />
          </span>
          <span className="text-xs font-semibold text-emerald-300">
            知识库检索
          </span>
          {query && (
            <span className="truncate rounded-md bg-black/40 px-2 py-0.5 font-mono text-[10px] text-zinc-300 max-w-[200px]">
              &quot;{query}&quot;
            </span>
          )}
        </div>
        <span className="rounded-full bg-emerald-500/20 border border-emerald-500/30 px-2 py-0.5 font-mono text-[10px] font-bold text-emerald-400">
          {count} 篇相关文档
        </span>
      </div>

      {/* 文档卡片列表 */}
      {docs.length > 0 ? (
        <div className="space-y-2">
          {docs.map((doc, idx) => {
            const meta = doc.metadata || {};
            const title = (meta.title ||
              meta.fileName ||
              meta.source ||
              `文档卡片 #${idx + 1}`) as string;
            const url = (meta.url ||
              meta.link ||
              (typeof meta.source === "string" && meta.source.startsWith("http")
                ? meta.source
                : "")) as string;

            return (
              <div
                key={`${meta.source || meta.url || meta.title || "doc"}-${idx}`}
                className="rounded-lg border border-emerald-500/15 bg-black/40 p-2.5 space-y-1.5 transition-all hover:border-emerald-500/30"
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className="flex size-4 items-center justify-center rounded bg-emerald-500/20 text-[9px] font-bold text-emerald-400 font-mono">
                      {idx + 1}
                    </span>
                    <span
                      className="truncate text-[11px] font-semibold text-zinc-200"
                      title={title}
                    >
                      {title}
                    </span>
                  </div>

                  {url && (
                    <a
                      href={url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-[10px] text-emerald-400 hover:underline shrink-0 font-mono"
                    >
                      <span>来源</span>
                      <ExternalLink className="size-2.5" />
                    </a>
                  )}
                </div>

                {doc.content && (
                  <p className="text-[11px] leading-relaxed text-zinc-300 bg-zinc-950/40 rounded p-2 border border-zinc-800/50 whitespace-pre-wrap max-h-32 overflow-auto">
                    {doc.content}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="py-4 text-center text-[11px] text-zinc-400 italic">
          未检索到相关知识库文档
        </div>
      )}
    </div>
  );
}

// ----------------------------------------------------------------------
// 5. Web Search 专属结构化渲染器 (web_search)
// ----------------------------------------------------------------------
export function WebSearchRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const args = safeParseJson<{ query?: string; keywords?: string }>(argsJson);
  const res = safeParseJson<{
    query?: string;
    count?: number;
    results?: Array<{
      title?: string;
      snippet?: string;
      description?: string;
      summary?: string;
      url?: string;
      link?: string;
    }>;
  }>(resultJson);

  const query = args?.query || args?.keywords || res?.query || "";
  let resultsList = res?.results || [];

  if (resultsList.length === 0 && resultJson) {
    const rawArr =
      safeParseJson<Array<{ title?: string; snippet?: string; url?: string }>>(
        resultJson,
      );
    if (Array.isArray(rawArr)) {
      resultsList = rawArr;
    }
  }

  return (
    <div className="space-y-2.5 rounded-xl border border-purple-500/20 bg-purple-950/10 p-3 text-xs dark:border-purple-500/30 dark:bg-purple-950/20">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-purple-500/20 pb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-purple-500/20 text-purple-400">
            <Globe className="size-3.5" />
          </span>
          <span className="text-xs font-semibold text-purple-300">
            网络搜索
          </span>
          {query && (
            <span className="truncate rounded-md bg-black/40 px-2 py-0.5 font-mono text-[10px] text-zinc-300 max-w-[200px]">
              &quot;{query}&quot;
            </span>
          )}
        </div>
        <span className="rounded-full bg-purple-500/20 border border-purple-500/30 px-2 py-0.5 font-mono text-[10px] font-bold text-purple-400">
          {resultsList.length} 条检索结果
        </span>
      </div>

      {/* 搜索结果列表 */}
      {resultsList.length > 0 ? (
        <div className="space-y-2">
          {resultsList.map((item, idx) => {
            const title = item.title || "搜索结果项";
            const url = item.url || item.link || "";
            const snippet =
              item.snippet || item.description || item.summary || "";

            let domain = "";
            if (url) {
              try {
                domain = new URL(url).hostname;
              } catch {
                domain = url;
              }
            }

            return (
              <div
                key={`${item.url || item.title || "result"}-${idx}`}
                className="rounded-lg border border-purple-500/15 bg-black/40 p-2.5 space-y-1 transition-all hover:border-purple-500/30 hover:bg-black/60 group/item"
              >
                <div className="flex items-start justify-between gap-2">
                  <a
                    href={url || "#"}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 text-[11px] font-semibold text-purple-300 group-hover/item:text-purple-200 hover:underline min-w-0"
                  >
                    <Search className="size-3 shrink-0 text-purple-400" />
                    <span className="truncate">{title}</span>
                    {url && (
                      <ExternalLink className="size-2.5 shrink-0 opacity-70" />
                    )}
                  </a>
                  {domain && (
                    <span className="rounded bg-purple-950/60 px-1.5 py-0.5 font-mono text-[9px] text-purple-400 shrink-0 border border-purple-500/20">
                      {domain}
                    </span>
                  )}
                </div>

                {snippet && (
                  <p className="text-[11px] leading-relaxed text-zinc-300 pl-4 font-sans line-clamp-3">
                    {snippet}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="py-4 text-center text-[11px] text-zinc-400 italic">
          未检索到网络结果
        </div>
      )}
    </div>
  );
}

// ----------------------------------------------------------------------
// 6. Generic Default Tool Renderer (通用 JSON/文本结构化降级)
// ----------------------------------------------------------------------
export function DefaultToolRenderer({
  resultJson,
  isError,
}: {
  resultJson?: string;
  isError?: boolean;
}) {
  if (!resultJson) return null;

  let text = resultJson;
  const parsed = safeParseJson(resultJson);
  if (parsed) {
    text = JSON.stringify(parsed, null, 2);
  }

  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-950/80 overflow-hidden">
      <div className="flex items-center justify-between border-b border-zinc-800 px-3 py-1.5">
        <span className="font-mono text-[10px] uppercase font-semibold text-zinc-400">
          {isError ? "错误原因" : "返回结果"}
        </span>
        <CopyButton content={text} />
      </div>
      <pre
        className={cn(
          "max-h-48 overflow-auto p-2.5 font-mono text-[11px] leading-relaxed whitespace-pre-wrap",
          isError
            ? "bg-rose-950/30 text-rose-200"
            : "bg-black/40 text-zinc-200",
        )}
      >
        {text}
      </pre>
    </div>
  );
}

// ----------------------------------------------------------------------
// 日历事件 / 任务 工具辅助函数
// ----------------------------------------------------------------------

/** 将 ISO-8601 时间字符串（UTC）格式化为本地可读时间。 */
function formatInstant(iso?: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const TASK_STATUS_COLUMNS = ["TODO", "IN_PROGRESS", "DONE", "BLOCKED"] as const;
const TASK_STATUS_LABEL: Record<string, string> = {
  TODO: "待办",
  IN_PROGRESS: "进行中",
  DONE: "已完成",
  BLOCKED: "受阻",
};
const PRIORITY_LABEL: Record<number, string> = {
  1: "紧急",
  2: "高",
  3: "中",
  4: "低",
};
const STATUS_STYLE: Record<string, string> = {
  TODO: "border-zinc-700 text-zinc-300",
  IN_PROGRESS: "border-amber-500/40 text-amber-300",
  DONE: "border-emerald-500/40 text-emerald-300",
  BLOCKED: "border-rose-500/40 text-rose-300",
};

// ----------------------------------------------------------------------
// Calendar 渲染器 (calendar_tool)
// ----------------------------------------------------------------------
interface CalendarEventView {
  id?: string;
  title?: string;
  description?: string;
  start?: string;
  end?: string;
  allDay?: boolean;
  reminderMinutes?: number;
  attendees?: string[];
  location?: string;
}

function CalendarRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const res = safeParseJson<{
    status?: string;
    action?: string;
    event?: CalendarEventView;
    events?: CalendarEventView[];
    count?: number;
    ical?: string;
    message?: string;
  }>(resultJson);
  const args = safeParseJson<{ action?: string }>(argsJson);
  const action = res?.action || args?.action || "";
  const isError = res?.status === "error";
  const events = res?.events ?? (res?.event ? [res.event] : []);

  return (
    <div className="space-y-2 rounded-xl border border-sky-500/20 bg-gradient-to-br from-sky-950/20 via-cyan-950/15 to-blue-950/20 p-3.5 text-xs shadow-inner">
      <div className="flex items-center justify-between border-b border-sky-500/20 pb-2">
        <div className="flex items-center gap-2">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-sky-500/20 text-sky-300">
            <CalendarDays className="size-3.5" />
          </span>
          <span className="font-semibold text-sky-300 text-xs">日历事件</span>
          {action ? (
            <span className="rounded bg-sky-500/20 px-1.5 py-0.5 font-mono text-[9px] uppercase text-sky-300">
              {action}
            </span>
          ) : null}
        </div>
        {res?.ical ? <CopyButton content={res.ical} label="复制 iCal" /> : null}
      </div>

      {isError ? (
        <div className="rounded-lg border border-rose-500/30 bg-rose-950/30 px-3 py-2 text-rose-200">
          {res?.message || "日历操作失败"}
        </div>
      ) : null}

      {res?.ical ? (
        <div className="rounded-lg bg-black/40 p-2">
          <div className="mb-1 font-mono text-[10px] text-sky-400">
            iCal 导出 (RFC 5545)
          </div>
          <pre className="max-h-44 overflow-auto whitespace-pre-wrap font-mono text-[10px] text-sky-200">
            {res.ical}
          </pre>
        </div>
      ) : null}

      {events.length > 0 ? (
        <div className="space-y-1.5">
          {events.map((ev, i) => (
            <div
              key={ev.id || `ev-${i}`}
              className="rounded-lg border border-sky-500/15 bg-sky-950/25 px-2.5 py-1.5"
            >
              <div className="flex items-center gap-1.5 font-semibold text-sky-100">
                <Calendar className="size-3 shrink-0 text-sky-400" />
                <span className="truncate">{ev.title || "(无标题)"}</span>
              </div>
              <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[10px] text-zinc-400">
                <span className="inline-flex items-center gap-1">
                  <Clock className="size-3" />
                  {formatInstant(ev.start)}
                  {ev.end ? ` → ${formatInstant(ev.end)}` : ""}
                </span>
                {ev.reminderMinutes ? (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="size-3" />
                    提前 {ev.reminderMinutes} 分钟提醒
                  </span>
                ) : null}
                {ev.location ? (
                  <span className="inline-flex items-center gap-1">
                    <Server className="size-3" />
                    {ev.location}
                  </span>
                ) : null}
              </div>
              {ev.attendees && ev.attendees.length > 0 ? (
                <div className="mt-0.5 flex flex-wrap items-center gap-1 text-[10px] text-zinc-400">
                  <Users className="size-3" />
                  {ev.attendees.map((a) => (
                    <span
                      key={a}
                      className="rounded bg-sky-500/15 px-1.5 py-0.5 text-sky-300"
                    >
                      {a}
                    </span>
                  ))}
                </div>
              ) : null}
              {ev.description ? (
                <div className="mt-0.5 text-[10px] text-zinc-500">
                  {ev.description}
                </div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}

      <div className="text-right font-mono text-[9px] text-zinc-500">
        操作结果快照
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------
// Task 渲染器 (task_tool) —— Kanban 风格看板
// ----------------------------------------------------------------------
interface TaskView {
  id?: string;
  title?: string;
  description?: string;
  status?: string;
  priority?: number;
  dueDate?: string;
  tags?: string[];
  assignee?: string;
  dependencies?: string[];
}

function TaskBoardRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const res = safeParseJson<{
    status?: string;
    action?: string;
    task?: TaskView;
    tasks?: TaskView[];
    count?: number;
    message?: string;
  }>(resultJson);
  const args = safeParseJson<{ action?: string }>(argsJson);
  const action = res?.action || args?.action || "";
  const isError = res?.status === "error";
  const tasks = res?.tasks ?? (res?.task ? [res.task] : []);

  return (
    <div className="space-y-2 rounded-xl border border-violet-500/20 bg-gradient-to-br from-violet-950/20 via-purple-950/15 to-fuchsia-950/20 p-3.5 text-xs shadow-inner">
      <div className="flex items-center justify-between border-b border-violet-500/20 pb-2">
        <div className="flex items-center gap-2">
          <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-violet-500/20 text-violet-300">
            <ListTodo className="size-3.5" />
          </span>
          <span className="font-semibold text-violet-300 text-xs">
            任务看板
          </span>
          {action ? (
            <span className="rounded bg-violet-500/20 px-1.5 py-0.5 font-mono text-[9px] uppercase text-violet-300">
              {action}
            </span>
          ) : null}
          {tasks.length > 0 ? (
            <span className="font-mono text-[10px] text-zinc-400">
              ({tasks.length})
            </span>
          ) : null}
        </div>
      </div>

      {isError ? (
        <div className="rounded-lg border border-rose-500/30 bg-rose-950/30 px-3 py-2 text-rose-200">
          {res?.message || "任务操作失败"}
        </div>
      ) : null}

      {tasks.length > 0 ? (
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {TASK_STATUS_COLUMNS.map((col) => {
            const colTasks = tasks.filter((t) => (t.status || "TODO") === col);
            return (
              <div
                key={col}
                className="flex flex-col gap-1.5 rounded-lg border border-white/5 bg-black/30 p-1.5"
              >
                <div
                  className={cn(
                    "flex items-center justify-between rounded px-1.5 py-0.5 text-[10px] font-semibold border bg-black/40",
                    STATUS_STYLE[col] ?? "border-zinc-700 text-zinc-300",
                  )}
                >
                  <span>{TASK_STATUS_LABEL[col] ?? col}</span>
                  <span className="font-mono">{colTasks.length}</span>
                </div>
                {colTasks.map((t, i) => (
                  <div
                    key={t.id || `t-${col}-${i}`}
                    className="rounded-md border border-white/10 bg-violet-950/30 px-2 py-1.5"
                  >
                    <div className="flex items-start gap-1 font-medium text-violet-100">
                      <CheckSquare className="mt-0.5 size-3 shrink-0 text-violet-400" />
                      <span className="leading-tight">
                        {t.title || "(无标题)"}
                      </span>
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-1 text-[9px] text-zinc-400">
                      <span className="inline-flex items-center gap-0.5 rounded bg-amber-500/15 px-1 text-amber-300">
                        <Flag className="size-2.5" />
                        {PRIORITY_LABEL[t.priority ?? 3] ?? t.priority ?? 3}
                      </span>
                      {t.dueDate ? (
                        <span className="inline-flex items-center gap-0.5 rounded bg-sky-500/15 px-1 text-sky-300">
                          <Clock className="size-2.5" />
                          {formatInstant(t.dueDate)}
                        </span>
                      ) : null}
                      {t.assignee ? (
                        <span className="inline-flex items-center gap-0.5 rounded bg-emerald-500/15 px-1 text-emerald-300">
                          <Users className="size-2.5" />
                          {t.assignee}
                        </span>
                      ) : null}
                    </div>
                    {t.tags && t.tags.length > 0 ? (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {t.tags.map((tag) => (
                          <span
                            key={tag}
                            className="inline-flex items-center gap-0.5 rounded bg-zinc-700/40 px-1 text-[9px] text-zinc-300"
                          >
                            <Tag className="size-2.5" />
                            {tag}
                          </span>
                        ))}
                      </div>
                    ) : null}
                    {t.dependencies && t.dependencies.length > 0 ? (
                      <div className="mt-1 text-[9px] text-zinc-500">
                        依赖：{t.dependencies.length} 项
                      </div>
                    ) : null}
                  </div>
                ))}
                {colTasks.length === 0 ? (
                  <div className="rounded-md border border-dashed border-white/5 px-2 py-2 text-center text-[9px] text-zinc-600">
                    无
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : null}

      <div className="text-right font-mono text-[9px] text-zinc-500">
        操作结果快照
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Email 渲染器 (email_tool) —— 邮件外发与草稿卡片
// ---------------------------------------------------------------------------
interface EmailItemView {
  messageId?: string;
  id?: string;
  to?: string[];
  subject?: string;
  status?: string;
  preview?: string;
  bodySnippet?: string;
  timestamp?: number;
  createdAt?: number;
}

interface EmailToolData {
  action?: string;
  success?: boolean;
  result?: EmailItemView;
  draft?: EmailItemView;
  history?: EmailItemView[];
}

function EmailRenderer({
  argsJson,
  resultJson,
}: {
  argsJson?: string;
  resultJson?: string;
}) {
  let parsedArgs: { action?: string; payload?: string } = {};
  try {
    if (argsJson) parsedArgs = JSON.parse(argsJson);
  } catch {}

  let data: EmailToolData = {};
  try {
    if (resultJson) data = JSON.parse(resultJson);
  } catch {}

  const action = (data.action || parsedArgs.action || "SEND").toUpperCase();
  const email = data.result || data.draft;
  const history = data.history || [];

  return (
    <div className="my-2 space-y-2.5 rounded-xl border border-zinc-200/80 bg-zinc-900/90 p-3.5 text-xs text-zinc-200 dark:border-zinc-800">
      {/* 头部标题与操作 */}
      <div className="flex items-center justify-between border-b border-zinc-800/80 pb-2">
        <div className="flex items-center gap-2">
          <div className="flex size-6 items-center justify-center rounded-lg bg-blue-500/20 text-blue-400">
            <Mail className="size-3.5" />
          </div>
          <span className="font-semibold text-zinc-100">
            {action === "DRAFT"
              ? "邮件草稿生成"
              : action === "LIST_HISTORY"
                ? "外发邮件记录"
                : "邮件代发结果"}
          </span>
        </div>
        <span
          className={cn(
            "rounded-md px-2 py-0.5 font-mono text-[10px] font-bold uppercase",
            action === "DRAFT"
              ? "bg-amber-500/20 text-amber-400"
              : action === "LIST_HISTORY"
                ? "bg-purple-500/20 text-purple-400"
                : "bg-emerald-500/20 text-emerald-400",
          )}
        >
          {email?.status || action}
        </span>
      </div>

      {/* 单封邮件展示 */}
      {email && (
        <div className="space-y-2 rounded-lg bg-zinc-950/60 p-3 border border-zinc-800/60">
          <div className="flex items-start justify-between gap-2">
            <div className="space-y-1 min-w-0">
              <div className="flex items-center gap-1.5 flex-wrap">
                <span className="text-[11px] text-zinc-400 font-medium">
                  收件人:
                </span>
                {(email.to || []).map((addr) => (
                  <span
                    key={addr}
                    className="rounded bg-blue-500/15 text-blue-300 px-1.5 py-0.2 text-[10px] font-mono"
                  >
                    {addr}
                  </span>
                ))}
              </div>
              <div className="font-bold text-xs text-white truncate">
                主题: {email.subject || "无主题"}
              </div>
            </div>
            {email.messageId && (
              <span className="font-mono text-[9px] text-zinc-500 shrink-0">
                ID: {email.messageId.substring(0, 12)}...
              </span>
            )}
          </div>

          {email.preview && (
            <div className="rounded bg-zinc-900/90 p-2 text-[11px] text-zinc-300 font-mono whitespace-pre-wrap line-clamp-4 border border-zinc-800/40">
              {email.preview}
            </div>
          )}
        </div>
      )}

      {/* 发送历史列表 */}
      {action === "LIST_HISTORY" && (
        <div className="space-y-1.5">
          {history.length === 0 ? (
            <p className="text-center text-[11px] text-zinc-500 py-2">
              暂无历史外发记录
            </p>
          ) : (
            history.map((h) => (
              <div
                key={h.id}
                className="flex items-center justify-between p-2 rounded bg-zinc-950/40 border border-zinc-800/40 text-[11px]"
              >
                <div className="space-y-0.5 truncate">
                  <div className="font-semibold text-zinc-200 truncate">
                    {h.subject}
                  </div>
                  <div className="text-[10px] text-zinc-400 font-mono">
                    至: {(h.to || []).join(", ")}
                  </div>
                </div>
                <span className="text-[9px] font-mono text-emerald-400 shrink-0 bg-emerald-500/10 px-1.5 py-0.5 rounded">
                  {h.status}
                </span>
              </div>
            ))
          )}
        </div>
      )}

      <div className="text-right font-mono text-[9px] text-zinc-500">
        AI-Copilot Email Gateway
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------
// Code Execution Sandbox Renderer
// ----------------------------------------------------------------------
interface CodeExecutionArgs {
  language?: string;
  code?: string;
}

interface CodeExecutionImage {
  name: string;
  mimeType: string;
  data: string;
}

interface CodeExecutionResult {
  status?: string;
  language?: string;
  sandboxType?: string;
  exitCode?: number;
  stdout?: string;
  stderr?: string;
  executionTimeMs?: number;
  images?: CodeExecutionImage[];
  truncated?: boolean;
}

export function CodeExecutionRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const [activeTab, setActiveTab] = useState<"stdout" | "stderr" | "images">(
    "stdout",
  );
  const [copiedCode, setCopiedCode] = useState(false);
  const [isCodeExpanded, setIsCodeExpanded] = useState(false);
  const [previewImage, setPreviewImage] = useState<string | null>(null);

  // 解析入参代码与语言
  let args: CodeExecutionArgs = {};
  try {
    args = JSON.parse(argsJson || "{}");
  } catch {
    args = { code: argsJson };
  }

  const language = (args.language || "python").toLowerCase();
  const code = args.code || "";
  const isPython = language.includes("py");

  // 解析沙箱执行返回
  let result: CodeExecutionResult | null = null;
  const isCalling = !resultJson;

  if (resultJson) {
    try {
      result = JSON.parse(resultJson);
    } catch {
      result = { stdout: resultJson };
    }
  }

  const handleCopyCode = async () => {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code);
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2000);
    } catch {
      // ignore
    }
  };

  // 执行中（Calling）骨架状态
  if (isCalling || !result) {
    return (
      <div className="space-y-2.5 overflow-hidden rounded-xl border border-violet-500/30 bg-violet-950/10 p-3 dark:bg-violet-950/20 backdrop-blur-md">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="flex size-6 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500/20 to-indigo-500/20 text-violet-400">
              <Terminal className="size-3.5" />
            </span>
            <span className="font-semibold text-xs text-violet-300">
              {isPython ? "Python 3.11" : "Node.js 20"} 沙箱环境
            </span>
            <span className="rounded-full bg-violet-500/20 px-2 py-0.5 text-[10px] text-violet-300 font-mono">
              隔离容器
            </span>
          </div>
          <span className="flex items-center gap-1.5 text-[11px] text-violet-400 animate-pulse font-medium">
            <Loader2 className="size-3.5 animate-spin" />
            正在沙箱容器中安全运行并捕获图表...
          </span>
        </div>

        {/* 代码快照预览 */}
        {code && (
          <div className="rounded-lg border border-white/5 bg-zinc-950/80 p-2.5 font-mono text-[11px] text-zinc-300">
            <div className="mb-1 text-[10px] text-zinc-500 font-sans">
              待执行源代码
            </div>
            <pre className="max-h-32 overflow-x-auto whitespace-pre-wrap">
              {code}
            </pre>
          </div>
        )}
      </div>
    );
  }

  const exitCode = result.exitCode ?? 0;
  const isSuccess = exitCode === 0;
  const stdout = result.stdout || "";
  const stderr = result.stderr || "";
  const images = result.images || [];
  const executionTimeMs = result.executionTimeMs ?? 0;
  const sandboxLabel =
    result.sandboxType === "docker"
      ? "Docker 容器隔离"
      : result.sandboxType === "local-blocked"
        ? "安全拦截"
        : "本地进程沙箱";

  // 默认 Tab 优先级：若有图表且无 stdout 则优先图表；若有错误优先 stderr
  const hasImages = images.length > 0;
  const hasStderr = stderr.length > 0;
  const effectiveTab =
    activeTab === "stdout" && !stdout && hasImages
      ? "images"
      : activeTab === "stdout" && !stdout && hasStderr
        ? "stderr"
        : activeTab;

  return (
    <div className="space-y-3 overflow-hidden rounded-xl border border-zinc-200/80 bg-zinc-50/50 p-3 dark:border-zinc-800/80 dark:bg-zinc-900/50 backdrop-blur-md">
      {/* 顶部运行状态与沙箱信息 */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-zinc-200/60 pb-2.5 dark:border-zinc-800/60">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "flex size-6.5 items-center justify-center rounded-lg text-xs font-bold shadow-xs",
              isPython
                ? "bg-gradient-to-br from-sky-500/20 to-indigo-500/20 text-sky-600 dark:text-sky-400"
                : "bg-gradient-to-br from-emerald-500/20 to-teal-500/20 text-emerald-600 dark:text-emerald-400",
            )}
          >
            <Terminal className="size-3.5" />
          </span>
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5">
              <span className="font-semibold text-xs text-zinc-900 dark:text-zinc-100">
                {isPython ? "Python 3.11" : "Node.js 20"}
              </span>
              <span
                className={cn(
                  "rounded-full px-2 py-0.2 text-[10px] font-medium border",
                  result.sandboxType === "docker"
                    ? "border-indigo-500/30 bg-indigo-500/10 text-indigo-700 dark:text-indigo-300"
                    : result.sandboxType === "local-blocked"
                      ? "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300"
                      : "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300",
                )}
              >
                {sandboxLabel}
              </span>
            </div>
          </div>
        </div>

        {/* 耗时与退出码 */}
        <div className="flex items-center gap-2 text-xs">
          <span className="font-mono text-[11px] text-zinc-500 dark:text-zinc-400">
            ⏱️ {executionTimeMs}ms
          </span>
          <span
            className={cn(
              "inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-bold font-mono",
              isSuccess
                ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300"
                : "bg-rose-500/15 text-rose-700 dark:text-rose-300",
            )}
          >
            {isSuccess ? "Exit 0 (OK)" : `Exit ${exitCode}`}
          </span>
        </div>
      </div>

      {/* 源代码折叠/展开预览 */}
      {code && (
        <div className="rounded-xl border border-zinc-200/80 bg-zinc-950 dark:border-zinc-800/80 overflow-hidden shadow-inner">
          <div className="flex items-center justify-between border-b border-zinc-800/80 bg-zinc-900/90 px-3 py-1.5 text-[11px] text-zinc-400">
            <button
              type="button"
              onClick={() => setIsCodeExpanded((prev) => !prev)}
              className="flex items-center gap-1 font-medium hover:text-zinc-200 transition-colors"
            >
              {isCodeExpanded ? (
                <ChevronDown className="size-3.5 text-zinc-400" />
              ) : (
                <ChevronRight className="size-3.5 text-zinc-400" />
              )}
              <Code2 className="size-3.5 text-indigo-400" />
              <span>执行源代码 ({code.split("\n").length} 行)</span>
            </button>
            <button
              type="button"
              onClick={handleCopyCode}
              className="flex items-center gap-1 text-[10px] hover:text-zinc-200 transition-colors"
              title="复制代码"
            >
              {copiedCode ? (
                <Check className="size-3 text-emerald-400" />
              ) : (
                <Copy className="size-3" />
              )}
              <span>{copiedCode ? "已复制" : "复制"}</span>
            </button>
          </div>
          <div
            className={cn(
              !isCodeExpanded && "max-h-24 overflow-hidden relative",
            )}
          >
            <pre className="p-3 text-[11px] font-mono leading-relaxed text-zinc-200 overflow-x-auto whitespace-pre-wrap">
              {code}
            </pre>
            {!isCodeExpanded && (
              <div className="absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-zinc-950 to-transparent pointer-events-none" />
            )}
          </div>
        </div>
      )}

      {/* 选项卡导航：标准输出 / 错误日志 / 图表可视化 */}
      <div className="flex items-center gap-1.5 border-b border-zinc-200 dark:border-zinc-800 pb-1">
        <button
          type="button"
          onClick={() => setActiveTab("stdout")}
          className={cn(
            "flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium transition-colors",
            effectiveTab === "stdout"
              ? "bg-indigo-600 text-white shadow-xs"
              : "text-zinc-600 hover:bg-zinc-200/60 dark:text-zinc-400 dark:hover:bg-zinc-800/60",
          )}
        >
          <Terminal className="size-3.5" />
          <span>标准输出</span>
          {stdout && <span className="size-1.5 rounded-full bg-emerald-400" />}
        </button>

        {stderr && (
          <button
            type="button"
            onClick={() => setActiveTab("stderr")}
            className={cn(
              "flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium transition-colors",
              effectiveTab === "stderr"
                ? "bg-rose-600 text-white shadow-xs"
                : "text-rose-600 hover:bg-rose-100/60 dark:text-rose-400 dark:hover:bg-rose-950/40",
            )}
          >
            <AlertCircle className="size-3.5" />
            <span>错误输出</span>
            <span className="size-1.5 rounded-full bg-rose-400" />
          </button>
        )}

        {hasImages && (
          <button
            type="button"
            onClick={() => setActiveTab("images")}
            className={cn(
              "flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium transition-colors",
              effectiveTab === "images"
                ? "bg-purple-600 text-white shadow-xs"
                : "text-purple-600 hover:bg-purple-100/60 dark:text-purple-400 dark:hover:bg-purple-950/40",
            )}
          >
            <ImageIcon className="size-3.5" />
            <span>生成图表 ({images.length})</span>
            <span className="size-1.5 rounded-full bg-purple-400" />
          </button>
        )}
      </div>

      {/* 截断警告提示 */}
      {result.truncated && (
        <div className="flex items-center gap-1.5 rounded-lg border border-amber-500/30 bg-amber-950/20 px-2.5 py-1.5 text-xs text-amber-300">
          <AlertCircle className="size-3.5 shrink-0 text-amber-400" />
          <span>输出内容已达到 64KB 上限并进行了截断保护。</span>
        </div>
      )}

      {/* 选项卡内容区 */}
      {effectiveTab === "stdout" && (
        <div className="relative rounded-xl border border-zinc-800/80 bg-zinc-950 p-3 shadow-inner">
          {stdout ? (
            <pre className="max-h-60 overflow-auto font-mono text-[11px] leading-relaxed text-zinc-100 whitespace-pre-wrap select-text">
              {stdout}
            </pre>
          ) : (
            <div className="py-4 text-center text-xs text-zinc-500 font-mono">
              (无标准输出内容)
            </div>
          )}
        </div>
      )}

      {effectiveTab === "stderr" && (
        <div className="relative rounded-xl border border-rose-900/60 bg-rose-950/40 p-3 shadow-inner">
          <pre className="max-h-60 overflow-auto font-mono text-[11px] leading-relaxed text-rose-200 whitespace-pre-wrap select-text">
            {stderr}
          </pre>
        </div>
      )}

      {effectiveTab === "images" && hasImages && (
        <div className="space-y-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {images.map((img) => (
              <div
                key={img.name}
                className="group relative flex flex-col overflow-hidden rounded-xl border border-zinc-200/80 bg-white/80 dark:border-zinc-800 dark:bg-zinc-950/80 shadow-sm"
              >
                <div className="relative aspect-video w-full overflow-hidden bg-zinc-100 dark:bg-zinc-900 flex items-center justify-center">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={img.data}
                    alt={img.name}
                    className="size-full object-contain transition-transform duration-300 group-hover:scale-102"
                  />
                  {/* 悬停放大蒙层按钮 */}
                  <button
                    type="button"
                    onClick={() => setPreviewImage(img.data)}
                    className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity cursor-zoom-in"
                    title="点击放大查看"
                  >
                    <span className="flex size-9 items-center justify-center rounded-full bg-white/90 text-zinc-900 shadow-lg">
                      <Maximize2 className="size-4.5" />
                    </span>
                  </button>
                </div>
                <div className="flex items-center justify-between px-2.5 py-1.5 border-t border-zinc-100 dark:border-zinc-800 text-[11px]">
                  <span className="truncate font-mono font-medium text-zinc-700 dark:text-zinc-300">
                    {img.name}
                  </span>
                  <a
                    href={img.data}
                    download={img.name}
                    className="flex size-6 items-center justify-center rounded-md text-zinc-500 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-white transition-colors"
                    title="下载图表"
                  >
                    <Download className="size-3.5" />
                  </a>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 图片全屏预览模态框 */}
      {previewImage && (
        <ImagePreviewModal
          src={previewImage}
          onClose={() => setPreviewImage(null)}
        />
      )}
    </div>
  );
}

// ----------------------------------------------------------------------
// 8. 数据库查询结果渲染器 (DatabaseQueryTool / db_query)
// ----------------------------------------------------------------------
export function DbQueryRenderer({
  argsJson,
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const args = safeParseJson<{
    question?: string;
    sql?: string;
    maxRows?: number;
  }>(argsJson);

  const result = safeParseJson<{
    success: boolean;
    sql?: string;
    columns?: string[];
    rows?: Record<string, unknown>[];
    rowCount?: number;
    truncated?: boolean;
    executionTimeMs?: number;
    error?: string;
  }>(resultJson);

  const [viewMode, setViewMode] = useState<"table" | "chart">("table");
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 10;

  const question = args?.question;
  const sql = result?.sql || args?.sql;
  const columns = result?.columns || [];
  const rows = result?.rows || [];
  const rowCount = result?.rowCount ?? rows.length;
  const executionTimeMs = result?.executionTimeMs ?? 0;
  const isTruncated = result?.truncated ?? false;
  const error = result?.error;
  const isSuccess = result?.success !== false && !error;

  // 智能推断数值列以支持图表渲染
  const numericColumns = columns.filter((col) =>
    rows.some((r) => {
      const v = r[col];
      return (
        typeof v === "number" ||
        (!Number.isNaN(Number(v)) && typeof v === "string" && v !== "")
      );
    }),
  );
  const labelColumn =
    columns.find((col) => !numericColumns.includes(col)) || columns[0];
  const chartValueColumn = numericColumns[0];

  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const paginatedRows = rows.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize,
  );

  // 计算柱状图最大值
  const maxVal = chartValueColumn
    ? Math.max(
        1,
        ...rows.map((r) => {
          const n = Number(r[chartValueColumn]);
          return Number.isNaN(n) ? 0 : n;
        }),
      )
    : 1;

  return (
    <div className="space-y-3">
      {/* 头部元信息与 SQL 预览 */}
      <div className="rounded-xl border border-zinc-200/80 bg-zinc-50/60 dark:border-zinc-800/80 dark:bg-zinc-900/60 p-3.5 space-y-2.5">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <div className="flex size-7 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <Database className="size-4" />
            </div>
            <div>
              <div className="text-xs font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-1.5">
                <span>PostgreSQL 只读查询</span>
                {isSuccess ? (
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                    成功 ({executionTimeMs}ms)
                  </span>
                ) : (
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
                    执行失败
                  </span>
                )}
              </div>
              {question && (
                <p className="text-[11px] text-zinc-500 dark:text-zinc-400 truncate max-w-md">
                  问题: {question}
                </p>
              )}
            </div>
          </div>

          {/* 视图切换按钮 */}
          {isSuccess && rows.length > 0 && numericColumns.length > 0 && (
            <div className="flex items-center rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-800/80 p-0.5">
              <button
                type="button"
                onClick={() => setViewMode("table")}
                className={cn(
                  "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                  viewMode === "table"
                    ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-700 dark:text-zinc-100 shadow-sm"
                    : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200",
                )}
              >
                <Table className="size-3.5" />
                <span>表格</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode("chart")}
                className={cn(
                  "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                  viewMode === "chart"
                    ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-700 dark:text-zinc-100 shadow-sm"
                    : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200",
                )}
              >
                <BarChart3 className="size-3.5" />
                <span>图表</span>
              </button>
            </div>
          )}
        </div>

        {/* SQL 代码展示 */}
        {sql && (
          <div className="relative rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-950 overflow-hidden">
            <div className="flex items-center justify-between px-3 py-1.5 border-b border-zinc-800 bg-zinc-900/90 text-[11px] text-zinc-400 font-mono">
              <span>SQL 语句</span>
              <CopyButton content={sql} />
            </div>
            <div className="max-h-40 overflow-auto p-2 text-xs font-mono">
              <SyntaxHighlighter
                language="sql"
                style={oneDark}
                customStyle={{
                  margin: 0,
                  padding: "0.25rem",
                  background: "transparent",
                  fontSize: "12px",
                }}
              >
                {sql}
              </SyntaxHighlighter>
            </div>
          </div>
        )}
      </div>

      {/* 异常提示 */}
      {!isSuccess && error && (
        <div className="flex items-start gap-2 rounded-xl border border-rose-500/20 bg-rose-50/50 dark:bg-rose-950/20 p-3 text-xs text-rose-700 dark:text-rose-300">
          <AlertCircle className="size-4 shrink-0 mt-0.5 text-rose-500" />
          <div className="space-y-1">
            <p className="font-semibold">数据库查询执行异常</p>
            <p className="font-mono text-[11px] text-rose-600 dark:text-rose-400 break-all">
              {error}
            </p>
          </div>
        </div>
      )}

      {/* 正常结果展示 */}
      {isSuccess && (
        <div className="rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 overflow-hidden shadow-xs">
          {/* 统计条 */}
          <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800 px-3.5 py-2 text-xs text-zinc-500 dark:text-zinc-400 bg-zinc-50/50 dark:bg-zinc-900/50">
            <div>
              共返回{" "}
              <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                {rowCount}
              </span>{" "}
              行记录
              {isTruncated && (
                <span className="ml-1.5 text-amber-600 dark:text-amber-400 font-medium">
                  (已达行数上限并截断)
                </span>
              )}
            </div>
            {rows.length > pageSize && viewMode === "table" && (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={currentPage <= 1}
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  className="px-2 py-0.5 rounded border border-zinc-200 dark:border-zinc-700 text-[11px] disabled:opacity-40 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  上一页
                </button>
                <span className="text-[11px]">
                  {currentPage} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={currentPage >= totalPages}
                  onClick={() =>
                    setCurrentPage((p) => Math.min(totalPages, p + 1))
                  }
                  className="px-2 py-0.5 rounded border border-zinc-200 dark:border-zinc-700 text-[11px] disabled:opacity-40 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  下一页
                </button>
              </div>
            )}
          </div>

          {/* 表格视图 */}
          {viewMode === "table" && (
            <div className="overflow-x-auto max-h-96">
              {rows.length === 0 ? (
                <div className="py-8 text-center text-xs text-zinc-400">
                  查询结果为空 (0 rows)
                </div>
              ) : (
                <table className="w-full text-left text-xs border-collapse font-mono">
                  <thead>
                    <tr className="border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-800/50 sticky top-0">
                      {columns.map((col) => (
                        <th
                          key={col}
                          className="px-3 py-2 text-[11px] font-semibold text-zinc-600 dark:text-zinc-300 whitespace-nowrap"
                        >
                          {col}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/60">
                    {paginatedRows.map((row, idx) => (
                      <tr
                        // biome-ignore lint/suspicious/noArrayIndexKey: simple table row index
                        key={idx}
                        className="hover:bg-zinc-50/80 dark:hover:bg-zinc-800/40 transition-colors"
                      >
                        {columns.map((col) => (
                          <td
                            key={col}
                            className="px-3 py-2 text-zinc-800 dark:text-zinc-200 whitespace-nowrap max-w-xs truncate"
                            title={String(row[col] ?? "")}
                          >
                            {row[col] === null ? (
                              <span className="text-zinc-400 italic font-sans text-[11px]">
                                NULL
                              </span>
                            ) : (
                              String(row[col])
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {/* 图表视图 (柱状图) */}
          {viewMode === "chart" && chartValueColumn && (
            <div className="p-4 space-y-3">
              <div className="text-[11px] text-zinc-500 dark:text-zinc-400">
                以{" "}
                <span className="font-semibold text-zinc-700 dark:text-zinc-300">
                  {labelColumn}
                </span>{" "}
                为维度， 展示{" "}
                <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                  {chartValueColumn}
                </span>{" "}
                的数值分布：
              </div>
              <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
                {rows.slice(0, 30).map((row, idx) => {
                  const label = String(row[labelColumn] ?? `Item ${idx + 1}`);
                  const val = Number(row[chartValueColumn]) || 0;
                  const percent = Math.min(
                    100,
                    Math.max(2, (val / maxVal) * 100),
                  );

                  return (
                    // biome-ignore lint/suspicious/noArrayIndexKey: simple chart bar index
                    <div key={idx} className="space-y-1">
                      <div className="flex items-center justify-between text-xs font-mono">
                        <span className="truncate max-w-[200px] text-zinc-700 dark:text-zinc-300 font-medium">
                          {label}
                        </span>
                        <span className="text-zinc-500 font-semibold">
                          {val}
                        </span>
                      </div>
                      <div className="h-3 w-full rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-teal-400 transition-all duration-300"
                          style={{ width: `${percent}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ----------------------------------------------------------------------
// Main Dispatcher: ToolResultRenderer
// ----------------------------------------------------------------------
export function ToolResultRenderer({
  toolName,
  argsJson,
  resultJson,
  isError,
}: {
  toolName: string;
  argsJson: string;
  resultJson?: string;
  isError?: boolean;
}) {
  const name = toolName.toLowerCase();

  // 若处于错误状态，优先走失败提示与降级组件
  if (isError) {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-1.5 rounded-lg border border-rose-500/30 bg-rose-950/20 p-2 text-xs text-rose-300">
          <AlertCircle className="size-4 shrink-0 text-rose-400" />
          <span>工具执行返回错误异常</span>
        </div>
        <DefaultToolRenderer resultJson={resultJson} isError />
      </div>
    );
  }

  // 根据工具名匹配专属渲染组件
  if (
    name === "code_execution" ||
    name === "code_interpreter" ||
    name === "python_interpreter" ||
    name === "code_runner" ||
    name.includes("code_execution")
  ) {
    return (
      <CodeExecutionRenderer argsJson={argsJson} resultJson={resultJson} />
    );
  }

  if (
    name === "http_request" ||
    name === "http" ||
    name.includes("http_request")
  ) {
    return <HttpRequestRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  if (
    name === "calculator" ||
    name === "calculate" ||
    name.includes("calculator")
  ) {
    return <CalculatorRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  if (
    name === "file_read" ||
    name === "file_write" ||
    name.includes("file_read") ||
    name.includes("file_write")
  ) {
    return <FileReadRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  if (
    name === "knowledge_query" ||
    name.includes("knowledge_query") ||
    name.includes("knowledge_search")
  ) {
    return (
      <KnowledgeQueryRenderer argsJson={argsJson} resultJson={resultJson} />
    );
  }

  if (
    name === "web_search" ||
    name.includes("web_search") ||
    name.includes("search_web")
  ) {
    return <WebSearchRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  if (
    name === "db_query" ||
    name === "database_query" ||
    name.includes("db_query") ||
    name.includes("database_query")
  ) {
    return <DbQueryRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  // 代码审查：必须在 code_ 通用分支之前匹配，避免被 CodeSearchToolRenderer 提前拦截
  if (name === "code_review" || name.includes("code_review")) {
    return <CodeReviewRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  if (name.startsWith("git_") || name.includes("git")) {
    return (
      <GitToolRenderer
        toolName={toolName}
        argsJson={argsJson}
        resultJson={resultJson}
      />
    );
  }

  if (
    name.startsWith("code_") ||
    name.includes("code_search") ||
    name.includes("code_file_tree") ||
    name.includes("code_find")
  ) {
    return (
      <CodeSearchToolRenderer
        toolName={toolName}
        argsJson={argsJson}
        resultJson={resultJson}
      />
    );
  }

  // 日历事件工具：以日历视图卡片渲染返回的快照数据
  if (name === "calendar_tool" || name.includes("calendar")) {
    return <CalendarRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  // 任务工具：以 Kanban 风格看板卡片渲染返回的快照数据
  if (name === "task_tool" || name.includes("task_tool")) {
    return <TaskBoardRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  // 邮件工具：以邮件卡片渲染返回的外发/草稿结果
  if (name === "email_tool" || name.includes("email")) {
    return <EmailRenderer argsJson={argsJson} resultJson={resultJson} />;
  }

  return <DefaultToolRenderer resultJson={resultJson} />;
}

function extractOutput(resultJson?: string): string {
  if (!resultJson) return "";
  try {
    const obj = JSON.parse(resultJson);
    if (typeof obj?.output === "string") return obj.output;
    return JSON.stringify(obj, null, 2);
  } catch {
    return resultJson;
  }
}

// ---------------------------------------------------------------------------
// GitTool 专属渲染器
// ---------------------------------------------------------------------------

function GitToolRenderer({
  toolName,
  argsJson,
  resultJson,
}: {
  toolName: string;
  argsJson: string;
  resultJson?: string;
}) {
  const [copied, setCopied] = useState(false);
  const output = extractOutput(resultJson);
  let repoDetail = "";
  try {
    const parsed = JSON.parse(argsJson);
    repoDetail = parsed.repoName || parsed.repoUrl || "";
  } catch {
    repoDetail = "";
  }

  const handleCopy = () => {
    if (!output) return;
    navigator.clipboard.writeText(output);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="space-y-2 rounded-xl border border-zinc-200/80 bg-zinc-900/95 p-3.5 text-xs text-zinc-100 dark:border-zinc-800 shadow-sm">
      <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
        <div className="flex items-center gap-2">
          <span className="flex size-5 items-center justify-center rounded-md bg-orange-500/20 text-orange-400 font-mono text-[10px] font-bold">
            GIT
          </span>
          <span className="font-mono text-zinc-300 text-[11px]">
            {toolName}
          </span>
          {repoDetail && (
            <span className="text-[10px] text-zinc-400 bg-zinc-800/80 px-1.5 py-0.5 rounded font-mono truncate max-w-[150px]">
              {repoDetail}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={handleCopy}
          className="flex items-center gap-1 text-[10px] text-zinc-400 hover:text-zinc-200 transition-colors"
        >
          {copied ? (
            <Check className="size-3 text-emerald-400" />
          ) : (
            <Copy className="size-3" />
          )}
          <span>{copied ? "已复制" : "复制"}</span>
        </button>
      </div>

      <pre className="max-h-72 overflow-x-auto overflow-y-auto font-mono text-[11px] leading-relaxed text-zinc-200 whitespace-pre-wrap">
        {output || "（无输出内容）"}
      </pre>
    </div>
  );
}

// ---------------------------------------------------------------------------
// CodeReview 专属渲染器（类 GitHub PR Review 概览卡片）
// ---------------------------------------------------------------------------

type ReviewLevel = "CRITICAL" | "WARNING" | "SUGGESTION";

interface ReviewFinding {
  level?: ReviewLevel;
  category?: string;
  file?: string;
  line?: number;
  message?: string;
  suggestion?: string;
  ruleId?: string;
}

interface ReviewReport {
  summary?: string;
  criticalCount?: number;
  warningCount?: number;
  suggestionCount?: number;
  truncated?: boolean;
  findings?: ReviewFinding[];
  suggestedTests?: string[];
}

// 防御性解析：支持 {"output":"<json>"} 与裸 JSON 两层
function parseReviewReport(resultJson?: string): ReviewReport | null {
  if (!resultJson) return null;
  const tryParse = (s: string): ReviewReport | null => {
    try {
      const obj = JSON.parse(s);
      if (obj && typeof obj === "object") {
        const data =
          typeof obj.output === "string" ? safeParse(obj.output) : obj;
        if (data && Array.isArray(data.findings)) return data as ReviewReport;
      }
    } catch {
      /* ignore */
    }
    return null;
  };
  const safeParse = (s: string): ReviewReport | null => {
    try {
      const obj = JSON.parse(s);
      return obj && Array.isArray(obj.findings) ? (obj as ReviewReport) : null;
    } catch {
      return null;
    }
  };
  return tryParse(resultJson) ?? safeParse(resultJson);
}

const LEVEL_META: Record<
  ReviewLevel,
  {
    label: string;
    dot: string;
    badge: string;
    bar: string;
    icon: typeof AlertTriangle;
  }
> = {
  CRITICAL: {
    label: "Critical",
    dot: "bg-rose-500",
    badge: "bg-rose-500/15 text-rose-300 border-rose-500/30",
    bar: "bg-rose-500/70",
    icon: AlertTriangle,
  },
  WARNING: {
    label: "Warning",
    dot: "bg-amber-500",
    badge: "bg-amber-500/15 text-amber-300 border-amber-500/30",
    bar: "bg-amber-500/70",
    icon: AlertTriangle,
  },
  SUGGESTION: {
    label: "Suggestion",
    dot: "bg-sky-500",
    badge: "bg-sky-500/15 text-sky-300 border-sky-500/30",
    bar: "bg-sky-500/70",
    icon: Lightbulb,
  },
};

const LEVEL_RANK: Record<ReviewLevel, number> = {
  CRITICAL: 0,
  WARNING: 1,
  SUGGESTION: 2,
};

function normalizeLevel(l?: string): ReviewLevel {
  const v = (l || "").toUpperCase();
  if (v === "CRITICAL") return "CRITICAL";
  if (v === "WARNING") return "WARNING";
  return "SUGGESTION";
}

export function CodeReviewRenderer({
  resultJson,
}: {
  argsJson: string;
  resultJson?: string;
}) {
  const report = parseReviewReport(resultJson);
  const [expandAll, setExpandAll] = useState(false);
  const [openSet, setOpenSet] = useState<Set<number>>(new Set());

  if (!report) {
    return (
      <div className="flex items-center gap-1.5 rounded-lg border border-zinc-200/80 bg-white/70 p-2 text-xs text-zinc-600 dark:border-zinc-800 dark:bg-zinc-950/70 dark:text-zinc-400">
        <ShieldCheck className="size-4 shrink-0 text-violet-400" />
        <span>代码审查完成，但未解析到结构化报告。</span>
      </div>
    );
  }

  const findings = (report.findings ?? [])
    .slice()
    .sort(
      (a, b) =>
        LEVEL_RANK[normalizeLevel(a.level)] -
        LEVEL_RANK[normalizeLevel(b.level)],
    );
  const criticalCount =
    report.criticalCount ??
    findings.filter((f) => normalizeLevel(f.level) === "CRITICAL").length;
  const warningCount =
    report.warningCount ??
    findings.filter((f) => normalizeLevel(f.level) === "WARNING").length;
  const suggestionCount =
    report.suggestionCount ??
    findings.filter((f) => normalizeLevel(f.level) === "SUGGESTION").length;
  const suggestedTests = report.suggestedTests ?? [];

  // 性能策略：超过 20 条时默认仅展开 critical，warning/suggestion 折叠
  const isLarge = findings.length > 20;
  const isOpen = (idx: number, level: ReviewLevel) => {
    if (expandAll) return true;
    if (openSet.has(idx)) return true;
    if (isLarge && level === "CRITICAL") return true;
    return false;
  };
  const toggle = (idx: number) =>
    setOpenSet((prev) => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });

  const counts: { level: ReviewLevel; n: number }[] = [
    { level: "CRITICAL", n: criticalCount },
    { level: "WARNING", n: warningCount },
    { level: "SUGGESTION", n: suggestionCount },
  ];

  return (
    <div className="overflow-hidden rounded-xl border border-zinc-200/80 bg-gradient-to-br from-violet-500/[0.04] to-indigo-500/[0.04] shadow-sm dark:border-zinc-800 dark:bg-zinc-950/70">
      {/* 顶部总览 */}
      <div className="flex flex-wrap items-center gap-2 border-b border-zinc-200/70 bg-white/60 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-900/40">
        <div className="flex size-7 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-indigo-500 text-white shadow">
          <ShieldCheck className="size-4" />
        </div>
        <span className="text-[13px] font-semibold text-zinc-800 dark:text-zinc-100">
          代码审查报告
        </span>
        <div className="ml-auto flex items-center gap-1.5">
          {counts.map(({ level, n }) => {
            const meta = LEVEL_META[level];
            return (
              <span
                key={level}
                className={cn(
                  "flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium",
                  meta.badge,
                )}
              >
                <span className={cn("size-1.5 rounded-full", meta.dot)} />
                {meta.label} {n}
              </span>
            );
          })}
        </div>
      </div>

      {/* 摘要 */}
      {report.summary && (
        <div className="px-3 py-2 text-[12px] leading-relaxed text-zinc-600 dark:text-zinc-300">
          {report.summary}
        </div>
      )}

      {/* 发现列表 */}
      <div className="space-y-1.5 px-3 pb-2">
        {findings.length === 0 && (
          <div className="flex items-center gap-1.5 py-1 text-[12px] text-emerald-400">
            <ShieldCheck className="size-4" />
            未发现明显问题，代码质量良好。
          </div>
        )}
        {findings.map((f, idx) => {
          const level = normalizeLevel(f.level);
          const meta = LEVEL_META[level];
          const Icon = meta.icon;
          const open = isOpen(idx, level);
          const findingKey = `${f.ruleId ?? "LLM"}-${f.file ?? "?"}-${f.line ?? 0}-${idx}`;
          return (
            <div
              key={findingKey}
              className="overflow-hidden rounded-lg border border-zinc-200/70 bg-white/70 dark:border-zinc-800 dark:bg-zinc-900/40"
            >
              <button
                type="button"
                onClick={() => toggle(idx)}
                className="flex w-full items-center gap-2 px-2.5 py-1.5 text-left transition-colors hover:bg-zinc-100/60 dark:hover:bg-zinc-800/40"
              >
                <span
                  className={cn("h-7 w-1 shrink-0 rounded-full", meta.bar)}
                />
                <Icon
                  className={cn(
                    "size-3.5 shrink-0",
                    meta.dot.replace("bg-", "text-"),
                  )}
                />
                <span className="truncate text-[12px] font-medium text-zinc-700 dark:text-zinc-200">
                  {f.category || "未分类"}
                </span>
                {f.file && (
                  <span className="truncate font-mono text-[10px] text-zinc-500 dark:text-zinc-400">
                    {f.file}
                    {f.line != null && (
                      <span className="ml-1 rounded bg-zinc-200/70 px-1 py-0.5 text-[9px] font-semibold text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
                        L{f.line}
                      </span>
                    )}
                  </span>
                )}
                <span className="ml-auto flex items-center gap-1 text-zinc-400">
                  <span
                    className={cn(
                      "rounded px-1.5 py-0.5 text-[9px] font-semibold",
                      meta.badge,
                    )}
                  >
                    {meta.label}
                  </span>
                  {open ? (
                    <ChevronDown className="size-3.5" />
                  ) : (
                    <ChevronRight className="size-3.5" />
                  )}
                </span>
              </button>
              {open && (
                <div className="space-y-2 border-t border-zinc-200/60 px-3 py-2 dark:border-zinc-800">
                  {f.message && (
                    <p className="text-[11px] leading-relaxed text-zinc-600 dark:text-zinc-300">
                      {f.message}
                    </p>
                  )}
                  {f.suggestion && (
                    <div>
                      <div className="mb-1 flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-violet-400">
                        <Lightbulb className="size-3" />
                        修复建议
                      </div>
                      <SyntaxHighlighter
                        language="text"
                        style={oneDark}
                        customStyle={{
                          margin: 0,
                          borderRadius: "0.5rem",
                          fontSize: "11px",
                          background: "rgba(13,13,18,0.6)",
                        }}
                      >
                        {f.suggestion}
                      </SyntaxHighlighter>
                    </div>
                  )}
                  {f.ruleId && (
                    <span className="inline-block rounded bg-zinc-200/60 px-1.5 py-0.5 font-mono text-[9px] text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
                      {f.ruleId}
                    </span>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 建议测试点 */}
      {suggestedTests.length > 0 && (
        <div className="border-t border-zinc-200/70 bg-white/40 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-900/30">
          <div className="mb-1.5 flex items-center gap-1 text-[11px] font-semibold text-zinc-700 dark:text-zinc-200">
            <Code2 className="size-3.5 text-violet-400" />
            建议测试点（可由 code_execution 自动验证）
          </div>
          <ul className="space-y-1">
            {suggestedTests.map((t, i) => (
              <li
                key={`test-${i}-${t.slice(0, 16)}`}
                className="rounded-md border border-zinc-200/70 bg-zinc-50/70 px-2 py-1 text-[11px] text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/30 dark:text-zinc-300"
              >
                {t}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 底部：一键展开/折叠 + 截断提示 */}
      <div className="flex items-center justify-between border-t border-zinc-200/70 px-3 py-1.5 text-[10px] text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
        <span>
          {findings.length} 项发现
          {report.truncated && " · 已截断"}
        </span>
        <button
          type="button"
          onClick={() => setExpandAll((v) => !v)}
          className="rounded-md px-2 py-0.5 text-violet-400 transition-colors hover:bg-violet-500/10"
        >
          {expandAll ? "全部折叠" : "全部展开"}
        </button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// CodeSearchTool 专属渲染器
// ---------------------------------------------------------------------------

function CodeSearchToolRenderer({
  toolName,
  argsJson,
  resultJson,
}: {
  toolName: string;
  argsJson: string;
  resultJson?: string;
}) {
  const [copied, setCopied] = useState(false);
  const output = extractOutput(resultJson);
  let searchDetail = "";
  try {
    const parsed = JSON.parse(argsJson);
    searchDetail =
      parsed.query ||
      parsed.pattern ||
      parsed.symbolName ||
      parsed.repoName ||
      "";
  } catch {
    searchDetail = "";
  }

  const handleCopy = () => {
    if (!output) return;
    navigator.clipboard.writeText(output);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="space-y-2 rounded-xl border border-zinc-200/80 bg-zinc-900/95 p-3.5 text-xs text-zinc-100 dark:border-zinc-800 shadow-sm">
      <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
        <div className="flex items-center gap-2">
          <span className="flex size-5 items-center justify-center rounded-md bg-blue-500/20 text-blue-400 font-mono text-[10px] font-bold">
            CODE
          </span>
          <span className="font-mono text-zinc-300 text-[11px]">
            {toolName}
          </span>
          {searchDetail && (
            <span className="text-[10px] text-zinc-400 bg-zinc-800/80 px-1.5 py-0.5 rounded font-mono truncate max-w-[150px]">
              {searchDetail}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={handleCopy}
          className="flex items-center gap-1 text-[10px] text-zinc-400 hover:text-zinc-200 transition-colors"
        >
          {copied ? (
            <Check className="size-3 text-emerald-400" />
          ) : (
            <Copy className="size-3" />
          )}
          <span>{copied ? "已复制" : "复制"}</span>
        </button>
      </div>

      <pre className="max-h-80 overflow-x-auto overflow-y-auto font-mono text-[11px] leading-relaxed text-zinc-200 whitespace-pre-wrap">
        {output || "（无搜索结果）"}
      </pre>
    </div>
  );
}
