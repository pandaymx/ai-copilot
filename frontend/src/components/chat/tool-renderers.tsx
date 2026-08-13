"use client";

import katex from "katex";
import {
  AlertCircle,
  BookOpen,
  Calculator,
  Check,
  ChevronDown,
  ChevronRight,
  Code2,
  Copy,
  ExternalLink,
  FileText,
  Globe,
  Search,
  Server,
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
                key={idx}
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
                key={idx}
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

  return <DefaultToolRenderer resultJson={resultJson} />;
}
