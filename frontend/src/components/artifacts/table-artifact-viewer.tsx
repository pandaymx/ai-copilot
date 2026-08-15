"use client";

import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Check,
  ChevronLeft,
  ChevronRight,
  Copy,
  Download,
  FileSpreadsheet,
  Maximize2,
  Minimize2,
  Search,
  X,
} from "lucide-react";
import { useMemo, useState } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

interface TableArtifactViewerProps {
  artifact: ArtifactItem;
  className?: string;
}

interface ParsedTable {
  headers: string[];
  rows: Record<string, string | number | boolean | null>[];
}

/** 解析 JSON 数组或 CSV/TSV 文本为表格结构 */
function parseTableData(content?: string): ParsedTable {
  if (!content || !content.trim()) {
    return { headers: [], rows: [] };
  }

  try {
    let clean = content.trim();
    if (clean.startsWith("```json")) {
      clean = clean
        .replace(/^```json/, "")
        .replace(/```$/, "")
        .trim();
    } else if (
      clean.startsWith("```csv") ||
      clean.startsWith("```tsv") ||
      clean.startsWith("```")
    ) {
      clean = clean
        .replace(/^```\w*/, "")
        .replace(/```$/, "")
        .trim();
    }

    // 尝试按 JSON 解析
    const parsed = JSON.parse(clean);
    if (Array.isArray(parsed) && parsed.length > 0) {
      if (typeof parsed[0] === "object" && parsed[0] !== null) {
        const headers = Array.from(
          new Set(parsed.flatMap((item) => Object.keys(item))),
        );
        return { headers, rows: parsed };
      }
    }
  } catch {
    // 降级尝试 CSV/TSV 格式解析
    const lines = content
      .trim()
      .split(/\r?\n/)
      .filter((l) => l.trim().length > 0);
    if (lines.length > 0) {
      const delimiter = lines[0].includes("\t") ? "\t" : ",";
      const headers = lines[0]
        .split(delimiter)
        .map((h) => h.trim().replace(/^["']|["']$/g, ""));
      const rows = lines.slice(1).map((line) => {
        const cols = line
          .split(delimiter)
          .map((c) => c.trim().replace(/^["']|["']$/g, ""));
        const row: Record<string, string> = {};
        headers.forEach((h, i) => {
          row[h] = cols[i] ?? "";
        });
        return row;
      });
      return { headers, rows };
    }
  }

  return { headers: [], rows: [] };
}

export function TableArtifactViewer({
  artifact,
  className,
}: TableArtifactViewerProps) {
  const [search, setSearch] = useState("");
  const [sortCol, setSortCol] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");
  const [pageSize, setPageSize] = useState<number>(10);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);

  const tableData = useMemo(
    () => parseTableData(artifact.content),
    [artifact.content],
  );

  // 排序与搜索过滤
  const filteredAndSortedRows = useMemo(() => {
    let result = [...tableData.rows];

    // 全文模糊搜索
    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter((row) =>
        Object.values(row).some((val) =>
          String(val ?? "")
            .toLowerCase()
            .includes(q),
        ),
      );
    }

    // 列排序
    if (sortCol) {
      result.sort((a, b) => {
        const valA = a[sortCol];
        const valB = b[sortCol];
        if (typeof valA === "number" && typeof valB === "number") {
          return sortDir === "asc" ? valA - valB : valB - valA;
        }
        const strA = String(valA ?? "");
        const strB = String(valB ?? "");
        return sortDir === "asc"
          ? strA.localeCompare(strB, "zh-CN")
          : strB.localeCompare(strA, "zh-CN");
      });
    }

    return result;
  }, [tableData.rows, search, sortCol, sortDir]);

  // 分页数据
  const totalPages = Math.max(
    1,
    Math.ceil(filteredAndSortedRows.length / pageSize),
  );
  const paginatedRows = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredAndSortedRows.slice(start, start + pageSize);
  }, [filteredAndSortedRows, currentPage, pageSize]);

  // 数值指标统计
  const numericStats = useMemo(() => {
    const stats: Record<string, { sum: number; avg: number }> = {};
    for (const h of tableData.headers) {
      const nums = tableData.rows
        .map((r) => r[h])
        .filter(
          (v): v is number =>
            typeof v === "number" ||
            (!Number.isNaN(Number(v)) && String(v).trim() !== ""),
        )
        .map(Number);
      if (nums.length > 0 && nums.length === tableData.rows.length) {
        const sum = nums.reduce((a, b) => a + b, 0);
        stats[h] = { sum, avg: sum / nums.length };
      }
    }
    return stats;
  }, [tableData.headers, tableData.rows]);

  const handleSort = (col: string) => {
    if (sortCol === col) {
      if (sortDir === "asc") {
        setSortDir("desc");
      } else {
        setSortCol(null);
        setSortDir("asc");
      }
    } else {
      setSortCol(col);
      setSortDir("asc");
    }
  };

  const handleExportCsv = () => {
    if (tableData.headers.length === 0) return;
    const headerLine = tableData.headers.join(",");
    const rowLines = tableData.rows.map((row) =>
      tableData.headers
        .map((h) => `"${String(row[h] ?? "").replace(/"/g, '""')}"`)
        .join(","),
    );
    const csvContent = `\uFEFF${[headerLine, ...rowLines].join("\n")}`;
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `table-export-${artifact.artifactId || Date.now()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleCopyMarkdown = async () => {
    if (tableData.headers.length === 0) return;
    const headerLine = `| ${tableData.headers.join(" | ")} |`;
    const sepLine = `| ${tableData.headers.map(() => "---").join(" | ")} |`;
    const rowLines = tableData.rows.map(
      (r) =>
        `| ${tableData.headers.map((h) => String(r[h] ?? "")).join(" | ")} |`,
    );
    const md = [headerLine, sepLine, ...rowLines].join("\n");
    try {
      await navigator.clipboard.writeText(md);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  const contentBody = (
    <div className="space-y-3">
      {/* 工具栏：搜索、导出与全屏 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search className="absolute left-2.5 top-2.5 size-3.5 text-zinc-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
            placeholder="搜索表格内容..."
            className="w-full rounded-xl border border-zinc-200 bg-white/80 py-1.5 pl-8 pr-3 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-none dark:border-zinc-700 dark:bg-zinc-800/80 dark:text-zinc-100"
          />
        </div>

        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={handleCopyMarkdown}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2.5 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300 transition-colors"
            title="复制 Markdown 表格"
          >
            {copied ? (
              <>
                <Check className="size-3 text-emerald-500" />
                <span className="text-emerald-500">已复制 MD</span>
              </>
            ) : (
              <>
                <Copy className="size-3" />
                <span>复制 MD</span>
              </>
            )}
          </button>

          <button
            type="button"
            onClick={handleExportCsv}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2.5 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300 transition-colors"
            title="导出为 CSV 文件"
          >
            <Download className="size-3" />
            <span>导出 CSV</span>
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

      {/* 表格容器 */}
      <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white shadow-2xs dark:border-zinc-800 dark:bg-zinc-900">
        <table className="w-full text-left text-xs border-collapse">
          <thead className="border-b border-zinc-200 bg-zinc-50/80 text-[11px] font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-300">
            <tr>
              <th className="w-12 px-3 py-2 text-center text-zinc-400 font-mono">
                #
              </th>
              {tableData.headers.map((h) => {
                const isSorted = sortCol === h;
                return (
                  <th
                    key={h}
                    onClick={() => handleSort(h)}
                    className="cursor-pointer select-none px-3.5 py-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
                  >
                    <div className="flex items-center gap-1.5">
                      <span>{h}</span>
                      {isSorted ? (
                        sortDir === "asc" ? (
                          <ArrowUp className="size-3 text-indigo-600 dark:text-indigo-400" />
                        ) : (
                          <ArrowDown className="size-3 text-indigo-600 dark:text-indigo-400" />
                        )
                      ) : (
                        <ArrowUpDown className="size-3 text-zinc-300 opacity-0 group-hover:opacity-100 hover:opacity-100 transition-opacity" />
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/80">
            {paginatedRows.length === 0 ? (
              <tr>
                <td
                  colSpan={tableData.headers.length + 1}
                  className="py-10 text-center text-zinc-400 text-xs"
                >
                  未找到匹配的表格行数据
                </td>
              </tr>
            ) : (
              paginatedRows.map((row, idx) => {
                const rowKey =
                  tableData.headers
                    .map((h) => String(row[h] ?? ""))
                    .join("-") || `row-${(currentPage - 1) * pageSize + idx}`;
                return (
                  <tr
                    key={rowKey}
                    className="hover:bg-indigo-50/30 dark:hover:bg-indigo-950/20 transition-colors"
                  >
                    <td className="px-3 py-2 text-center text-zinc-400 font-mono text-[10px]">
                      {(currentPage - 1) * pageSize + idx + 1}
                    </td>
                    {tableData.headers.map((h) => (
                      <td
                        key={h}
                        className="px-3.5 py-2 text-zinc-700 dark:text-zinc-300 font-mono text-[11px]"
                      >
                        {String(row[h] ?? "-")}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
          {Object.keys(numericStats).length > 0 && (
            <tfoot className="border-t-2 border-zinc-200 bg-zinc-50/90 text-[10px] font-mono font-medium text-zinc-600 dark:border-zinc-700 dark:bg-zinc-800/80 dark:text-zinc-300">
              <tr>
                <td className="px-3 py-1.5 text-center text-zinc-400 font-sans">
                  统计
                </td>
                {tableData.headers.map((h) => (
                  <td key={h} className="px-3.5 py-1.5">
                    {numericStats[h] ? (
                      <div>
                        <div>
                          <strong>求和:</strong>{" "}
                          {Math.round(numericStats[h].sum * 100) / 100}
                        </div>
                        <div className="text-zinc-400">
                          均值: {Math.round(numericStats[h].avg * 100) / 100}
                        </div>
                      </div>
                    ) : (
                      "-"
                    )}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>

      {/* 分页控制栏 */}
      <div className="flex flex-wrap items-center justify-between gap-2 px-1 text-xs text-zinc-500">
        <div className="flex items-center gap-2">
          <span>
            共{" "}
            <strong className="text-zinc-800 dark:text-zinc-200">
              {filteredAndSortedRows.length}
            </strong>{" "}
            行
          </span>
          <select
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value));
              setCurrentPage(1);
            }}
            aria-label="每页显示条数"
            className="rounded-lg border border-zinc-200 bg-white px-2 py-0.5 text-xs text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200"
          >
            <option value={5}>5 条/页</option>
            <option value={10}>10 条/页</option>
            <option value={25}>25 条/页</option>
            <option value={50}>50 条/页</option>
          </select>
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-[11px]">
            第 {currentPage} / {totalPages} 页
          </span>
          <button
            type="button"
            disabled={currentPage <= 1}
            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
            className="rounded-lg border border-zinc-200 p-1 text-zinc-600 hover:bg-zinc-50 disabled:opacity-40 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
          >
            <ChevronLeft className="size-3.5" />
          </button>
          <button
            type="button"
            disabled={currentPage >= totalPages}
            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
            className="rounded-lg border border-zinc-200 p-1 text-zinc-600 hover:bg-zinc-50 disabled:opacity-40 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
          >
            <ChevronRight className="size-3.5" />
          </button>
        </div>
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
        {/* Header */}
        <div className="mb-3 flex items-center justify-between gap-2 border-b border-indigo-100/60 pb-2.5 dark:border-indigo-900/40">
          <div className="flex items-center gap-2 min-w-0">
            <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
              <FileSpreadsheet className="size-4" />
            </div>
            <div className="min-w-0">
              <h4 className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
                {artifact.title || "交互式数据表格"}
              </h4>
              <span className="text-[10px] text-zinc-400">
                {tableData.headers.length} 列 · {tableData.rows.length} 条记录
              </span>
            </div>
          </div>
        </div>

        {contentBody}
      </div>

      {/* 全屏放大 Modal */}
      {isFullscreen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 sm:p-6 backdrop-blur-xs animate-in fade-in duration-200">
          <div className="flex max-h-[90vh] w-full max-w-5xl flex-col rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 overflow-hidden">
            <div className="flex items-center justify-between border-b border-zinc-200/80 pb-3 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <FileSpreadsheet className="size-5 text-indigo-600" />
                <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                  {artifact.title || "交互式数据表格"}
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
