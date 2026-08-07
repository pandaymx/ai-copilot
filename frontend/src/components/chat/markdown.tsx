"use client";

import { Check, Copy, Terminal } from "lucide-react";
import { useState } from "react";
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
import python from "react-syntax-highlighter/dist/esm/languages/prism/python";
import rust from "react-syntax-highlighter/dist/esm/languages/prism/rust";
import sql from "react-syntax-highlighter/dist/esm/languages/prism/sql";
import tsx from "react-syntax-highlighter/dist/esm/languages/prism/tsx";
import typescript from "react-syntax-highlighter/dist/esm/languages/prism/typescript";
import xml from "react-syntax-highlighter/dist/esm/languages/prism/xml";
import yaml from "react-syntax-highlighter/dist/esm/languages/prism/yaml";
import oneDark from "react-syntax-highlighter/dist/esm/styles/prism/one-dark";

import remarkGfm from "remark-gfm";
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

/** Markdown 渲染组件 */
export function Markdown({ content }: { content: string }) {
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
        "prose-table:my-4 prose-table:w-full prose-table:overflow-hidden prose-table:rounded-xl prose-table:border prose-table:border-zinc-200 dark:prose-table:border-zinc-800",
        "prose-th:bg-zinc-100/80 prose-th:px-3 prose-th:py-2 prose-th:text-left prose-th:text-xs prose-th:font-semibold dark:prose-th:bg-zinc-800/60",
        "prose-td:border-t prose-td:border-zinc-200/60 prose-td:px-3 prose-td:py-2 prose-td:text-xs dark:prose-td:border-zinc-800/60",
      )}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre: ({ children }) => <>{children}</>,
          code: ({ className, children, ...props }) => {
            const isBlock = /language-/.test(className ?? "");
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
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
