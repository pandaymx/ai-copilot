"use client";

import { FileText, Link2, Loader2, Type, UploadCloud } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { type RagIngestResult, ragReingestApi, ragUploadApi } from "@/lib/api";
import { cn } from "@/lib/utils";

type Tab = "text" | "url" | "file";

const TABS: {
  key: Tab;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}[] = [
  { key: "text", label: "文本", icon: Type },
  { key: "url", label: "网页 URL", icon: Link2 },
  { key: "file", label: "文件", icon: FileText },
];

interface KnowledgeUploadProps {
  onSuccess: (result: RagIngestResult) => void;
}

export function KnowledgeUpload({ onSuccess }: KnowledgeUploadProps) {
  const [tab, setTab] = useState<Tab>("text");
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [fileName, setFileName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const file = files[0];
    if (file.size > 10 * 1024 * 1024) {
      toast.error(`文件 "${file.name}" 超过 10MB 限制`);
      return;
    }
    const content = await file.text();
    setText(content);
    setFileName(file.name);
    setTab("text");
  };

  const handleSubmit = async () => {
    let sourceType = "TEXT";
    let rawText: string | undefined;
    let targetUrl: string | undefined;

    if (tab === "text") {
      if (!text.trim()) return;
      sourceType = "TEXT";
      rawText = text;
    } else if (tab === "url") {
      if (!url.trim()) return;
      sourceType = "URL";
      targetUrl = url;
    } else {
      if (!text.trim()) return;
      sourceType = "TEXT";
      rawText = text;
    }

    setSubmitting(true);
    try {
      const result = await ragUploadApi({
        sourceType,
        rawText,
        targetUrl,
        fileName: fileName || undefined,
      });
      if (result) {
        onSuccess(result);
        setText("");
        setUrl("");
        setFileName("");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleReingest = async () => {
    if (tab !== "text" || !text.trim()) return;
    setSubmitting(true);
    try {
      const result = await ragReingestApi({
        sourceType: "TEXT",
        rawText: text,
        fileName: fileName || undefined,
      });
      if (result) onSuccess(result);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="border-zinc-200/70 bg-white/70 p-5 shadow-xs backdrop-blur-xl dark:border-zinc-800/70 dark:bg-zinc-900/60">
      <div className="mb-4 flex items-center gap-2">
        <span className="flex size-8 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 text-white shadow-md shadow-indigo-500/20">
          <UploadCloud className="size-4" />
        </span>
        <div className="leading-tight">
          <h3 className="font-heading text-sm font-bold text-zinc-800 dark:text-zinc-100">
            上传入库
          </h3>
          <p className="text-[11px] text-zinc-400 dark:text-zinc-500">
            相同内容自动去重，重复切片将被跳过
          </p>
        </div>
      </div>

      {/* 选项卡 */}
      <div className="mb-4 flex gap-1 rounded-xl border border-zinc-200/70 bg-zinc-100/60 p-1 dark:border-zinc-800/70 dark:bg-zinc-800/40">
        {TABS.map((t) => {
          const Icon = t.icon;
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={cn(
                "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition-all duration-200",
                active
                  ? "bg-white text-indigo-600 shadow-xs dark:bg-zinc-900 dark:text-indigo-400"
                  : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              <Icon className="size-3.5" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === "text" && (
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={6}
          placeholder="粘贴或输入需要入库的文本内容…"
          className="w-full resize-none rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2.5 text-xs text-zinc-800 outline-none transition-colors placeholder:text-zinc-400 focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-100 dark:placeholder:text-zinc-500"
        />
      )}

      {tab === "url" && (
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/docs/article"
          className="w-full rounded-xl border border-zinc-200/80 bg-white/80 px-3 py-2.5 text-xs text-zinc-800 outline-none transition-colors placeholder:text-zinc-400 focus:border-indigo-500/60 focus:ring-2 focus:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/70 dark:text-zinc-100 dark:placeholder:text-zinc-500"
        />
      )}

      {tab === "file" && (
        <div className="space-y-2">
          <input
            ref={fileInputRef}
            type="file"
            accept=".txt,.md,.json,.js,.ts,.java,.py,.csv,.log"
            onChange={(e) => handleFiles(e.target.files)}
            className="hidden"
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="flex w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-zinc-300/80 bg-zinc-50/60 px-4 py-6 text-center text-xs text-zinc-500 transition-colors hover:border-indigo-500/60 hover:bg-indigo-50/40 dark:border-zinc-700/80 dark:bg-zinc-900/40 dark:text-zinc-400 dark:hover:border-indigo-500/50 dark:hover:bg-indigo-500/5"
          >
            <FileText className="size-5 text-indigo-500" />
            点击选择文本/代码文件（读取内容以纯文本入库）
          </button>
          {fileName && (
            <div className="flex items-center gap-2 rounded-lg bg-zinc-100/70 px-3 py-1.5 text-[11px] text-zinc-600 dark:bg-zinc-800/50 dark:text-zinc-300">
              <FileText className="size-3.5 shrink-0 text-indigo-500" />
              <span className="truncate">{fileName}</span>
              {text.length > 0 && (
                <span className="ml-auto text-zinc-400">
                  {text.length} 字符
                </span>
              )}
            </div>
          )}
        </div>
      )}

      <div className="mt-4 flex items-center gap-2">
        <Input
          value={fileName}
          onChange={(e) => setFileName(e.target.value)}
          placeholder="文件名 / 标题（可选）"
          className="flex-1"
        />
        <Button
          variant="gradient"
          size="sm"
          onClick={handleSubmit}
          disabled={
            submitting ||
            (tab === "text" && !text.trim()) ||
            (tab === "url" && !url.trim())
          }
        >
          {submitting && <Loader2 className="size-3.5 animate-spin" />}
          入库
        </Button>
        {tab === "text" && text.trim() && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleReingest}
            disabled={submitting}
            title="先删除旧版本再覆盖写入"
          >
            重新入库
          </Button>
        )}
      </div>
    </Card>
  );
}
