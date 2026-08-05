"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import remarkGfm from "remark-gfm";
import { cn } from "@/lib/utils";

/** 代码块：自动识别语言 + 语法高亮 + 一键复制。 */
function CodeBlock({
  className,
  children,
}: {
  className?: string;
  children?: React.ReactNode;
}) {
  const [copied, setCopied] = useState(false);
  // className 形如 "language-java"，提取语言名。
  const match = /language-(\w+)/.exec(className ?? "");
  const language = match?.[1];
  const code = String(children).replace(/\n$/, "");

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // 剪贴板不可用时静默忽略。
    }
  };

  return (
    <div className="group/code relative my-3 overflow-hidden rounded-lg border border-border bg-zinc-950">
      <div className="flex items-center justify-between border-b border-white/10 px-3 py-1.5">
        <span className="font-mono text-xs text-zinc-400">
          {language ?? "text"}
        </span>
        <button
          type="button"
          onClick={handleCopy}
          className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-zinc-400 transition-colors hover:bg-white/10 hover:text-zinc-100"
          aria-label="复制代码"
        >
          {copied ? (
            <>
              <Check className="size-3" /> 已复制
            </>
          ) : (
            <>
              <Copy className="size-3" /> 复制
            </>
          )}
        </button>
      </div>
      <SyntaxHighlighter
        language={language ?? "text"}
        style={oneDark}
        PreTag="div"
        customStyle={{
          margin: 0,
          borderRadius: 0,
          background: "transparent",
          fontSize: "0.825rem",
        }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );
}

/** 将 AI 文本渲染为 Markdown，同时美化代码、表格、列表等。 */
export function Markdown({ content }: { content: string }) {
  return (
    <div
      className={cn(
        "prose prose-zinc max-w-none break-words text-sm dark:prose-invert",
        "prose-pre:p-0 prose-pre:bg-transparent prose-pre:m-0",
        "prose-code:rounded prose-code:bg-muted prose-code:px-1 prose-code:py-0.5 prose-code:text-[0.85em] prose-code:before:content-[''] prose-code:after:content-['']",
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
              <code className={className} {...props}>
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
