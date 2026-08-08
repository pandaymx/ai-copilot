import type { ChatMessage } from "@/components/chat/message-bubble";

export type ExportFormat = "markdown" | "text" | "json";

export interface ExportOptions {
  format: ExportFormat;
  includeThinking: boolean;
  title: string;
}

// ─────────────────────────────────────────────
// 格式化函数
// ─────────────────────────────────────────────

function buildMarkdown(messages: ChatMessage[], includeThinking: boolean): string {
  const lines: string[] = [
    "# AI Copilot — 对话导出",
    "",
    `> 导出时间：${new Date().toLocaleString("zh-CN")}`,
    "",
    "---",
    "",
  ];

  for (const msg of messages) {
    if (msg.role === "user") {
      lines.push("## 🧑 用户", "");
      lines.push(msg.content.trim());
      lines.push("");
    } else {
      lines.push("## 🤖 AI Copilot", "");

      // 思考过程（可选）
      if (includeThinking && msg.thinking?.trim()) {
        lines.push("> [!NOTE]");
        lines.push("> **思考过程**");
        lines.push(">");
        for (const line of msg.thinking.trim().split("\n")) {
          lines.push(`> ${line}`);
        }
        lines.push("");
      }

      lines.push(msg.content.trim());

      // Token 用量
      if (msg.usage && msg.usage.totalTokens > 0) {
        lines.push("");
        const cost =
          msg.usage.estimatedCostRmb !== undefined
            ? ` · 约 ¥${msg.usage.estimatedCostRmb.toFixed(4)}`
            : "";
        lines.push(
          `*Tokens：${msg.usage.totalTokens}（Prompt ${msg.usage.promptTokens} / Completion ${msg.usage.completionTokens}）${cost}*`,
        );
      }

      lines.push("");
    }

    lines.push("---", "");
  }

  return lines.join("\n");
}

function buildPlainText(messages: ChatMessage[], includeThinking: boolean): string {
  const lines: string[] = [
    "AI Copilot — 对话导出",
    `导出时间：${new Date().toLocaleString("zh-CN")}`,
    "",
    "═".repeat(40),
    "",
  ];

  for (const msg of messages) {
    if (msg.role === "user") {
      lines.push("【用户】");
      lines.push(msg.content.trim());
    } else {
      lines.push("【AI Copilot】");

      if (includeThinking && msg.thinking?.trim()) {
        lines.push("▸ 思考过程：");
        for (const line of msg.thinking.trim().split("\n")) {
          lines.push(`  ${line}`);
        }
        lines.push("");
      }

      lines.push(msg.content.trim());

      if (msg.usage && msg.usage.totalTokens > 0) {
        const cost =
          msg.usage.estimatedCostRmb !== undefined
            ? ` · 约 ¥${msg.usage.estimatedCostRmb.toFixed(4)}`
            : "";
        lines.push(
          `[Tokens：${msg.usage.totalTokens}（Prompt ${msg.usage.promptTokens} / Completion ${msg.usage.completionTokens}）${cost}]`,
        );
      }
    }

    lines.push("");
    lines.push("─".repeat(40));
    lines.push("");
  }

  return lines.join("\n");
}

function buildJson(
  messages: ChatMessage[],
  title: string,
  includeThinking: boolean,
): string {
  const exported = messages.map((msg) => {
    const out: Omit<ChatMessage, "thinking"> & { thinking?: string } = {
      id: msg.id,
      role: msg.role,
      content: msg.content,
      ...(includeThinking && msg.thinking ? { thinking: msg.thinking } : {}),
      ...(msg.usage ? { usage: msg.usage } : {}),
    };
    return out;
  });

  return JSON.stringify(
    {
      exportedAt: new Date().toISOString(),
      title,
      messages: exported,
    },
    null,
    2,
  );
}

// ─────────────────────────────────────────────
// 文件名生成
// ─────────────────────────────────────────────

function buildFilename(title: string, format: ExportFormat): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const datePart = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}`;
  // 去掉文件名非法字符
  const safeTitle = title.replace(/[/\\:*?"<>|]/g, "_").slice(0, 40);
  const extMap: Record<ExportFormat, string> = {
    markdown: "md",
    text: "txt",
    json: "json",
  };
  return `${safeTitle}_${datePart}.${extMap[format]}`;
}

// ─────────────────────────────────────────────
// 主入口
// ─────────────────────────────────────────────

export function exportConversation(
  messages: ChatMessage[],
  options: ExportOptions,
): void {
  const { format, includeThinking, title } = options;

  let content: string;
  let mimeType: string;

  switch (format) {
    case "markdown":
      content = buildMarkdown(messages, includeThinking);
      mimeType = "text/markdown;charset=utf-8";
      break;
    case "text":
      content = buildPlainText(messages, includeThinking);
      mimeType = "text/plain;charset=utf-8";
      break;
    case "json":
      content = buildJson(messages, title, includeThinking);
      mimeType = "application/json;charset=utf-8";
      break;
  }

  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = buildFilename(title, format);
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  // 延迟释放，避免某些浏览器取消过早导致下载失败
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
