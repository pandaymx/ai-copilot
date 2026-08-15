"use client";

import {
  AlertCircle,
  BookOpen,
  Check,
  Code2,
  Copy,
  Loader2,
  Network,
  Terminal,
} from "lucide-react";
import { useTheme } from "next-themes";
import { memo, useEffect, useId, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
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
import rehypeKatex from "rehype-katex";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import { cn } from "@/lib/utils";

// 注册常用编程语言语法
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

/**
 * 流式 Markdown 补全防抖预处理
 * 解决流式输出中代码块未闭合、表格半截导致的破版与频繁重排
 */
function preprocessStreamingMarkdown(content: string): string {
  if (!content) return "";

  let processed = content;

  // 1. 代码块未闭合防抖 (``` 或 ~~~)
  const backtickMatches = processed.match(/^```/gm) || [];
  const tildeMatches = processed.match(/^~~~/gm) || [];

  if (backtickMatches.length % 2 !== 0) {
    processed += "\n```";
  } else if (tildeMatches.length % 2 !== 0) {
    processed += "\n~~~";
  }

  // 2. 流式表格破版防抖：若最后一行包含 | 且未以 \n 结尾
  const lines = processed.split("\n");
  const lastLine = lines[lines.length - 1];
  if (lastLine?.includes("|") && !processed.endsWith("\n")) {
    if (!lastLine.trim().endsWith("|")) {
      processed += " |";
    }
    processed += "\n";
  }

  // 3. 块级数学公式 $$ 闭合防抖
  const mathBlockMatches = processed.match(/\$\$/g) || [];
  if (mathBlockMatches.length % 2 !== 0) {
    processed += "\n$$";
  }

  return processed;
}

/** Mac 风格代码块：红黄绿小圆点 + 语言 Badge + 一键复制 */
function CodeBlock({
  className,
  children,
}: {
  className?: string;
  children?: React.ReactNode;
}) {
  const [copied, setCopied] = useState(false);
  const match = /language-(\w+)/.exec(className ?? "");
  const language = match?.[1] ?? "text";
  const code = String(children).replace(/\n$/, "");

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // 忽略剪贴板错误
    }
  };

  return (
    <div className="not-prose group/code relative my-4 w-full min-w-0 overflow-hidden rounded-2xl border border-zinc-800/80 bg-zinc-950/95 shadow-xl shadow-black/20">
      {/* 终端 Header */}
      <div className="flex items-center justify-between border-b border-zinc-800/80 bg-zinc-900/90 px-3.5 py-2">
        <div className="flex items-center gap-2">
          {/* Mac 窗口三色圆点 */}
          <div className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-full bg-rose-500/80" />
            <span className="size-2.5 rounded-full bg-amber-500/80" />
            <span className="size-2.5 rounded-full bg-emerald-500/80" />
          </div>
          <span className="ml-2 flex items-center gap-1 font-mono text-[11px] font-medium text-zinc-400">
            <Terminal className="size-3 text-indigo-400" />
            {language}
          </span>
        </div>

        {/* 复制按钮 */}
        <button
          type="button"
          onClick={handleCopy}
          className={cn(
            "inline-flex items-center gap-1.5 rounded-lg px-2 py-1 font-mono text-[11px] transition-all duration-200",
            copied
              ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
              : "text-zinc-400 hover:bg-white/10 hover:text-zinc-100",
          )}
          aria-label="复制代码"
        >
          {copied ? (
            <>
              <Check className="size-3 text-emerald-400" />
              <span>已复制</span>
            </>
          ) : (
            <>
              <Copy className="size-3" />
              <span>复制</span>
            </>
          )}
        </button>
      </div>

      {/* 语法高亮 */}
      <div className="w-full min-w-0 overflow-x-auto p-4 font-mono text-xs leading-relaxed text-zinc-200">
        <SyntaxHighlighter
          language={language}
          style={oneDark}
          showLineNumbers={code.split("\n").length > 3}
          lineNumberStyle={{
            minWidth: "2.2rem",
            paddingRight: "1rem",
            color: "rgba(156, 163, 175, 0.4)",
            textAlign: "right",
            userSelect: "none",
          }}
          PreTag="div"
          customStyle={{
            margin: 0,
            padding: 0,
            background: "transparent",
            fontSize: "0.825rem",
            lineHeight: "1.6",
          }}
        >
          {code}
        </SyntaxHighlighter>
      </div>
    </div>
  );
}

/** Mermaid 图表渲染组件：结合 next-themes 暗色模式适配与渲染/代码视图切换 */
function MermaidBlock({
  code,
  isStreaming,
}: {
  code: string;
  isStreaming?: boolean;
}) {
  const { resolvedTheme, theme } = useTheme();
  const isDark = resolvedTheme === "dark" || theme === "dark";
  const rawId = useId();
  const containerId = useMemo(
    () => `mermaid-${rawId.replace(/[^a-zA-Z0-9_-]/g, "_")}`,
    [rawId],
  );

  const [svg, setSvg] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [showCode, setShowCode] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;
    const cleanCode = code.trim();
    if (!cleanCode) return;

    const renderDiagram = async () => {
      setLoading(true);
      try {
        const { default: mermaid } = await import("mermaid");
        mermaid.initialize({
          startOnLoad: false,
          theme: isDark ? "dark" : "default",
          securityLevel: "loose",
          fontFamily: "var(--font-sans), system-ui, sans-serif",
        });

        // 唯一渲染元素 key 避免冲突
        const renderKey = `svg-${containerId}-${Math.random().toString(36).substring(2, 7)}`;
        const { svg: renderedSvg } = await mermaid.render(renderKey, cleanCode);

        if (isMounted) {
          setSvg(renderedSvg);
          setError(null);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (isMounted) {
          const message = err instanceof Error ? err.message : String(err);
          setError(message);
          setLoading(false);
        }
      }
    };

    renderDiagram();

    return () => {
      isMounted = false;
    };
  }, [code, isDark, containerId]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // 忽略复制失败
    }
  };

  return (
    <div className="not-prose group/mermaid relative my-4 w-full min-w-0 overflow-hidden rounded-2xl border border-zinc-800/80 bg-zinc-950/95 shadow-xl shadow-black/20">
      {/* 顶栏控制条 */}
      <div className="flex items-center justify-between border-b border-zinc-800/80 bg-zinc-900/90 px-3.5 py-2">
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-full bg-rose-500/80" />
            <span className="size-2.5 rounded-full bg-amber-500/80" />
            <span className="size-2.5 rounded-full bg-emerald-500/80" />
          </div>
          <span className="ml-2 flex items-center gap-1.5 font-mono text-[11px] font-medium text-zinc-300">
            <Network className="size-3 text-purple-400" />
            Mermaid 图表
          </span>
        </div>

        <div className="flex items-center gap-1.5">
          {/* 切换 图表 / 代码 视图 */}
          <button
            type="button"
            onClick={() => setShowCode((prev) => !prev)}
            className="inline-flex items-center gap-1 rounded-lg px-2 py-1 font-mono text-[11px] text-zinc-400 hover:bg-white/10 hover:text-zinc-100 transition-colors"
          >
            {showCode ? (
              <>
                <Network className="size-3 text-purple-400" />
                <span>图表视图</span>
              </>
            ) : (
              <>
                <Code2 className="size-3 text-indigo-400" />
                <span>源码</span>
              </>
            )}
          </button>

          {/* 复制代码 */}
          <button
            type="button"
            onClick={handleCopy}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-lg px-2 py-1 font-mono text-[11px] transition-all duration-200",
              copied
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                : "text-zinc-400 hover:bg-white/10 hover:text-zinc-100",
            )}
            aria-label="复制 Mermaid 代码"
          >
            {copied ? (
              <>
                <Check className="size-3 text-emerald-400" />
                <span>已复制</span>
              </>
            ) : (
              <>
                <Copy className="size-3" />
                <span>复制</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* 主视图区 */}
      <div className="w-full min-w-0 p-4">
        {showCode ? (
          <CodeBlock className="language-mermaid">{code}</CodeBlock>
        ) : loading && isStreaming && !svg ? (
          <div className="flex items-center justify-center gap-2 py-8 text-xs text-zinc-400 font-mono">
            <Loader2 className="size-4 animate-spin text-purple-400" />
            <span>Mermaid 图表生成中...</span>
          </div>
        ) : error ? (
          isStreaming ? (
            <div className="flex items-center justify-center gap-2 py-6 text-xs text-zinc-400 font-mono">
              <Loader2 className="size-4 animate-spin text-purple-400" />
              <span>接收图表数据中...</span>
            </div>
          ) : (
            <div className="flex flex-col gap-2 rounded-xl border border-rose-500/30 bg-rose-950/20 p-3 text-xs text-rose-300">
              <div className="flex items-center gap-1.5 font-semibold text-rose-400">
                <AlertCircle className="size-4" />
                <span>Mermaid 语法解析失败</span>
              </div>
              <p className="font-mono text-[11px] text-rose-200/80 leading-relaxed overflow-x-auto whitespace-pre-wrap">
                {error}
              </p>
              <button
                type="button"
                onClick={() => setShowCode(true)}
                className="mt-1 self-start text-[11px] text-indigo-400 hover:underline"
              >
                查看原始 DSL 代码
              </button>
            </div>
          )
        ) : (
          <div
            className="flex justify-center overflow-x-auto rounded-lg bg-zinc-900/60 p-4 backdrop-blur-xs [&_svg]:max-w-full [&_svg]:h-auto"
            // biome-ignore lint/security/noDangerouslySetInnerHtml: Mermaid 渲染出的受控 SVG 字符串
            dangerouslySetInnerHTML={{ __html: svg }}
          />
        )}
      </div>
    </div>
  );
}

/** Markdown 渲染组件 */
export const Markdown = memo(function Markdown({
  content,
  isStreaming,
  onCitationClick,
}: {
  content: string;
  isStreaming?: boolean;
  onCitationClick?: (citationId: string) => void;
}) {
  // 流式输出时，进行语法防抖与自动闭合补全，并将引用标识符转换为内部锚点链接
  const processedContent = useMemo(() => {
    let text = isStreaming ? preprocessStreamingMarkdown(content) : content;
    if (text) {
      // 匹配 [引用 1: 文档名 (第X页/段落Y)] 或 [引用 1] 或 [[cite:1]]
      text = text.replace(
        /\[引用\s*(\d+)(?::\s*([^\]\n]+))?\]/g,
        (_m, id, label) => {
          const title = label ? `引用 ${id}: ${label.trim()}` : `引用 ${id}`;
          return `[📄 ${title}](#cite-${id})`;
        },
      );
      text = text.replace(/\[\[cite:(\d+)\]\]/g, (_m, id) => {
        return `[📄 引用 ${id}](#cite-${id})`;
      });
    }
    return text;
  }, [content, isStreaming]);

  const remarkPlugins = useMemo(() => [remarkGfm, remarkMath], []);
  const rehypePlugins = useMemo(() => [rehypeKatex], []);

  const components = useMemo(
    () => ({
      pre: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
      a: ({
        href,
        children,
        ...props
      }: React.ComponentPropsWithoutRef<"a">) => {
        if (href?.startsWith("#cite-")) {
          const citeId = href.replace("#cite-", "");
          return (
            <button
              type="button"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                onCitationClick?.(citeId);
              }}
              className="inline-flex items-center gap-1 mx-1 px-1.5 py-0.5 rounded-md bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400 font-mono text-[11px] font-medium border border-indigo-500/20 transition-all cursor-pointer shadow-2xs hover:scale-105"
              title="点击查看文档原文引用对照"
            >
              <BookOpen className="size-3 text-indigo-500 shrink-0" />
              <span className="truncate max-w-[200px]">{children}</span>
            </button>
          );
        }
        return (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-indigo-600 underline hover:text-indigo-700 dark:text-indigo-400"
            {...props}
          >
            {children}
          </a>
        );
      },
      code: ({
        className,
        children,
        ...props
      }: {
        className?: string;
        children?: React.ReactNode;
      }) => {
        const match = /language-(\w+)/.exec(className ?? "");
        const language = match?.[1] ?? "";

        if (language === "mermaid") {
          return (
            <MermaidBlock
              code={String(children).replace(/\n$/, "")}
              isStreaming={isStreaming}
            />
          );
        }

        const isBlock = Boolean(match);
        if (isBlock) {
          return <CodeBlock className={className}>{children}</CodeBlock>;
        }

        return (
          <code
            className={cn(
              "rounded-md bg-zinc-100 px-1.5 py-0.5 font-mono text-[0.825em] font-medium text-indigo-600 dark:bg-zinc-800/80 dark:text-indigo-300",
              className,
            )}
            {...props}
          >
            {children}
          </code>
        );
      },
      table: ({
        children,
        node,
        ...props
      }: React.ComponentPropsWithoutRef<"table"> & { node?: unknown }) => (
        <div className="my-4 w-full overflow-x-auto rounded-xl border border-zinc-200/80 bg-white/40 dark:border-zinc-800/80 dark:bg-zinc-900/40 backdrop-blur-xs">
          <table
            className="w-full text-left text-xs border-collapse"
            {...props}
          >
            {children}
          </table>
        </div>
      ),
    }),
    [isStreaming, onCitationClick],
  );

  return (
    <div
      className={cn(
        "prose prose-zinc w-full min-w-0 max-w-none break-words text-sm dark:prose-invert leading-relaxed",
        "prose-p:my-2 prose-p:leading-relaxed",
        "prose-headings:font-heading prose-headings:font-semibold prose-headings:tracking-tight",
        "prose-h1:text-lg prose-h1:my-3",
        "prose-h2:text-base prose-h2:my-2.5",
        "prose-h3:text-sm prose-h3:my-2",
        "prose-pre:p-0 prose-pre:bg-transparent prose-pre:m-0",
        "prose-blockquote:my-3 prose-blockquote:border-l-2 prose-blockquote:border-indigo-500 prose-blockquote:bg-indigo-50/40 prose-blockquote:px-4 prose-blockquote:py-2 prose-blockquote:rounded-r-xl prose-blockquote:not-italic dark:prose-blockquote:bg-indigo-950/20",
        "prose-ul:my-2 prose-ul:list-disc prose-ul:pl-5",
        "prose-ol:my-2 prose-ol:list-decimal prose-ol:pl-5",
        "prose-li:my-0.5",
        "prose-th:bg-zinc-100/80 prose-th:px-3 prose-th:py-2 prose-th:text-left prose-th:text-xs prose-th:font-semibold dark:prose-th:bg-zinc-800/60",
        "prose-td:border-t prose-td:border-zinc-200/60 prose-td:px-3 prose-td:py-2 prose-td:text-xs dark:prose-td:border-zinc-800/60",
      )}
    >
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        components={components}
      >
        {processedContent}
      </ReactMarkdown>
    </div>
  );
});
