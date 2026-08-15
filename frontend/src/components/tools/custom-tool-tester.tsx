"use client";

import { AlertCircle, Check, Clock, Copy, Play } from "lucide-react";
import { useEffect, useState } from "react";
import {
  type CustomToolItem,
  type ToolTestResponse,
  testCustomTool,
} from "@/lib/custom-tool-api";

interface CustomToolTesterProps {
  tool: CustomToolItem;
}

interface JsonPropDef {
  type?: string;
  description?: string;
}

export function CustomToolTester({ tool }: CustomToolTesterProps) {
  const [params, setParams] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ToolTestResponse | null>(null);
  const [copied, setCopied] = useState(false);

  // 解析 parametersSchema 提取参数定义
  const [paramFields, setParamFields] = useState<
    { name: string; type: string; description: string; required: boolean }[]
  >([]);

  useEffect(() => {
    try {
      const schema = JSON.parse(tool.parametersSchema || "{}");
      if (schema.properties) {
        const requiredList: string[] = Array.isArray(schema.required)
          ? schema.required
          : [];
        const fields = Object.entries(
          schema.properties as Record<string, JsonPropDef>,
        ).map(([name, prop]) => ({
          name,
          type: prop.type || "string",
          description: prop.description || "",
          required: requiredList.includes(name),
        }));
        setParamFields(fields);

        // 初始化默认参数值
        setParams((prev) => {
          const initialParams: Record<string, string> = {};
          for (const f of fields) {
            if (prev[f.name] !== undefined) {
              initialParams[f.name] = prev[f.name];
            } else {
              initialParams[f.name] =
                f.type === "number" || f.type === "integer" ? "0" : "";
            }
          }
          return initialParams;
        });
      } else {
        setParamFields([]);
      }
    } catch {
      setParamFields([]);
    }
  }, [tool.parametersSchema]);

  const handleRunTest = async () => {
    setLoading(true);
    setResult(null);

    // 类型转换
    const typedParams: Record<string, unknown> = {};
    for (const f of paramFields) {
      const rawVal = params[f.name];
      if (f.type === "number" || f.type === "integer") {
        typedParams[f.name] = rawVal ? Number(rawVal) : 0;
      } else if (f.type === "boolean") {
        typedParams[f.name] = rawVal === "true" || rawVal === "1";
      } else if (f.type === "object" || f.type === "array") {
        try {
          typedParams[f.name] = JSON.parse(
            rawVal || (f.type === "array" ? "[]" : "{}"),
          );
        } catch {
          typedParams[f.name] = rawVal;
        }
      } else {
        typedParams[f.name] = rawVal || "";
      }
    }

    try {
      const resp = await testCustomTool({
        tool,
        inputParameters: typedParams,
      });
      setResult(resp);
    } catch (e: unknown) {
      setResult({
        status: "FAILURE",
        executionTimeMs: 0,
        isTruncated: false,
        errorMessage: e instanceof Error ? e.message : "请求失败",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (result?.output) {
      navigator.clipboard.writeText(result.output);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="space-y-4 rounded-xl border border-zinc-200/80 bg-zinc-50/50 p-4 dark:border-zinc-800 dark:bg-zinc-900/40">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Play className="size-4 text-emerald-500" />
          <h4 className="text-xs font-semibold text-zinc-900 dark:text-zinc-100">
            在线沙箱测试 (Live Tester)
          </h4>
        </div>
        <button
          type="button"
          onClick={handleRunTest}
          disabled={loading}
          className="flex items-center gap-1.5 rounded-lg bg-gradient-to-r from-emerald-500 to-teal-600 px-3.5 py-1.5 text-xs font-medium text-white shadow-xs transition-all hover:from-emerald-600 hover:to-teal-700 disabled:opacity-50"
        >
          <Play className={`size-3.5 ${loading ? "animate-spin" : ""}`} />
          <span>{loading ? "执行中..." : "发送测试"}</span>
        </button>
      </div>

      {/* 参数填报区域 */}
      {paramFields.length > 0 && (
        <div className="space-y-2">
          <p className="text-[11px] font-medium text-zinc-500 dark:text-zinc-400">
            测试入参：
          </p>
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
            {paramFields.map((field) => (
              <div key={field.name} className="space-y-1">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="font-mono text-zinc-700 dark:text-zinc-300">
                    {field.name}
                    {field.required && (
                      <span className="ml-1 text-rose-500 font-bold">*</span>
                    )}
                  </span>
                  <span className="text-zinc-400 text-[10px]">
                    {field.type}
                  </span>
                </div>
                <input
                  type="text"
                  value={params[field.name] || ""}
                  onChange={(e) =>
                    setParams({ ...params, [field.name]: e.target.value })
                  }
                  placeholder={field.description || `输入 ${field.name}`}
                  className="w-full rounded-md border border-zinc-200 bg-white px-2.5 py-1.5 text-xs text-zinc-900 placeholder:text-zinc-400 focus:border-emerald-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 结果显示区域 */}
      {result && (
        <div className="space-y-2 pt-2 border-t border-zinc-200/60 dark:border-zinc-800/60">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span
                className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                  result.status === "SUCCESS"
                    ? "bg-emerald-500/10 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-400"
                    : "bg-rose-500/10 text-rose-600 dark:bg-rose-500/20 dark:text-rose-400"
                }`}
              >
                {result.status === "SUCCESS" ? "执行成功" : "执行失败"}
              </span>
              <span className="flex items-center gap-1 text-[11px] text-zinc-400">
                <Clock className="size-3" />
                <span>{result.executionTimeMs}ms</span>
              </span>
              {result.isTruncated && (
                <span className="rounded bg-amber-500/10 px-1.5 py-0.5 text-[10px] font-medium text-amber-600 dark:bg-amber-500/20 dark:text-amber-400">
                  输出已截断 (8KB)
                </span>
              )}
            </div>
            {result.output && (
              <button
                type="button"
                onClick={handleCopy}
                className="flex items-center gap-1 text-[11px] text-zinc-500 transition-colors hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
              >
                {copied ? (
                  <>
                    <Check className="size-3 text-emerald-500" />
                    <span className="text-emerald-500">已复制</span>
                  </>
                ) : (
                  <>
                    <Copy className="size-3" />
                    <span>复制结果</span>
                  </>
                )}
              </button>
            )}
          </div>

          {result.errorMessage ? (
            <div className="flex items-start gap-2 rounded-lg bg-rose-500/10 p-3 text-xs text-rose-600 dark:bg-rose-500/15 dark:text-rose-400">
              <AlertCircle className="size-4 shrink-0 mt-0.5" />
              <pre className="whitespace-pre-wrap font-mono text-[11px]">
                {result.errorMessage}
              </pre>
            </div>
          ) : (
            <pre className="max-h-60 overflow-y-auto rounded-lg border border-zinc-300 bg-zinc-950 p-3 font-mono text-xs text-zinc-200 dark:border-zinc-800">
              {result.output}
            </pre>
          )}
        </div>
      )}
    </div>
  );
}
