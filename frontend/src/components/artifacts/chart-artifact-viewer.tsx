"use client";

import {
  BarChart3,
  Check,
  Code2,
  Copy,
  Download,
  LineChart,
  PieChart,
  Table as TableIcon,
} from "lucide-react";
import { useId, useMemo, useRef, useState } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { cn } from "@/lib/utils";

interface ChartArtifactViewerProps {
  artifact: ArtifactItem;
  className?: string;
}

export type ChartType = "bar" | "line" | "area" | "pie" | "doughnut";

export interface ChartDataset {
  label: string;
  data: number[];
  color?: string;
}

export interface StandardChartData {
  title?: string;
  type?: ChartType;
  labels: string[];
  datasets: ChartDataset[];
}

const DEFAULT_PALETTE = [
  "#6366f1", // indigo
  "#3b82f6", // blue
  "#10b981", // emerald
  "#f59e0b", // amber
  "#ec4899", // pink
  "#8b5cf6", // purple
  "#06b6d4", // cyan
  "#f97316", // orange
];

/** 从 artifact.content 中解析图表数据（支持标准 JSON 或 ECharts/Chart.js 结构） */
function parseChartContent(content?: string): StandardChartData {
  if (!content) {
    return { labels: [], datasets: [] };
  }

  try {
    let clean = content.trim();
    if (clean.startsWith("```json")) {
      clean = clean
        .replace(/^```json/, "")
        .replace(/```$/, "")
        .trim();
    } else if (clean.startsWith("```")) {
      clean = clean
        .replace(/^```\w*/, "")
        .replace(/```$/, "")
        .trim();
    }

    const obj = JSON.parse(clean);

    // 格式 1: 标准 { type, labels, datasets }
    if (Array.isArray(obj.labels) && Array.isArray(obj.datasets)) {
      return {
        title: obj.title,
        type: obj.type || "bar",
        labels: obj.labels.map(String),
        datasets: obj.datasets.map(
          (ds: Record<string, unknown>, i: number) => ({
            label: String(ds.label || `系列 ${i + 1}`),
            data: Array.isArray(ds.data) ? ds.data.map(Number) : [],
            color:
              typeof ds.color === "string"
                ? ds.color
                : DEFAULT_PALETTE[i % DEFAULT_PALETTE.length],
          }),
        ),
      };
    }

    // 格式 2: 简化 { type, data: { labels, datasets } }
    if (
      obj.data &&
      Array.isArray(obj.data.labels) &&
      Array.isArray(obj.data.datasets)
    ) {
      return {
        title: obj.title || obj.data.title,
        type: obj.type || obj.data.type || "bar",
        labels: obj.data.labels.map(String),
        datasets: obj.data.datasets.map(
          (ds: Record<string, unknown>, i: number) => ({
            label: String(ds.label || `系列 ${i + 1}`),
            data: Array.isArray(ds.data) ? ds.data.map(Number) : [],
            color:
              typeof ds.color === "string"
                ? ds.color
                : DEFAULT_PALETTE[i % DEFAULT_PALETTE.length],
          }),
        ),
      };
    }

    // 格式 3: 键值对对象数组 [ { category: "A", value: 10, value2: 20 }, ... ]
    if (Array.isArray(obj) && obj.length > 0 && typeof obj[0] === "object") {
      const keys = Object.keys(obj[0]);
      const labelKey =
        keys.find((k) => typeof obj[0][k] === "string") || keys[0];
      const numericKeys = keys.filter(
        (k) => k !== labelKey && typeof obj[0][k] === "number",
      );

      const labels = obj.map((row) => String(row[labelKey] ?? ""));
      const datasets = (
        numericKeys.length > 0 ? numericKeys : [keys[1] || "数值"]
      ).map((numKey, i) => ({
        label: numKey,
        data: obj.map((row) => Number(row[numKey] || 0)),
        color: DEFAULT_PALETTE[i % DEFAULT_PALETTE.length],
      }));

      return {
        title: undefined,
        type: "bar",
        labels,
        datasets,
      };
    }

    // 格式 4: ECharts 简化结构 { xAxis: { data: [...] }, series: [{ name, data }] }
    if (obj.xAxis?.data && Array.isArray(obj.series)) {
      return {
        title: obj.title?.text,
        type: "bar",
        labels: obj.xAxis.data.map(String),
        datasets: obj.series.map((s: Record<string, unknown>, i: number) => ({
          label: String(s.name || `系列 ${i + 1}`),
          data: Array.isArray(s.data) ? s.data.map(Number) : [],
          color: DEFAULT_PALETTE[i % DEFAULT_PALETTE.length],
        })),
      };
    }
  } catch {
    // 容错返回空
  }

  return {
    labels: ["分类 A", "分类 B", "分类 C", "分类 D"],
    datasets: [{ label: "示例数据", data: [12, 19, 8, 15], color: "#6366f1" }],
  };
}

export function ChartArtifactViewer({
  artifact,
  className,
}: ChartArtifactViewerProps) {
  const chartId = useId();
  const svgRef = useRef<SVGSVGElement>(null);
  const [activeTab, setActiveTab] = useState<"chart" | "table" | "json">(
    "chart",
  );
  const [activeType, setActiveType] = useState<ChartType | null>(null);
  const [hiddenSeries, setHiddenSeries] = useState<Record<string, boolean>>({});
  const [hoveredPoint, setHoveredPoint] = useState<{
    label: string;
    series: string;
    value: number;
    x: number;
    y: number;
  } | null>(null);
  const [copied, setCopied] = useState(false);

  const chartData = useMemo(
    () => parseChartContent(artifact.content),
    [artifact.content],
  );
  const effectiveType: ChartType = activeType || chartData.type || "bar";

  const visibleDatasets = useMemo(() => {
    return chartData.datasets.filter((ds) => !hiddenSeries[ds.label]);
  }, [chartData.datasets, hiddenSeries]);

  const allValues = useMemo(() => {
    return visibleDatasets.flatMap((ds) => ds.data);
  }, [visibleDatasets]);

  const maxValue = useMemo(() => {
    const max = Math.max(...allValues, 10);
    return Math.ceil(max * 1.15);
  }, [allValues]);

  const minValue = useMemo(() => {
    const min = Math.min(...allValues, 0);
    return min < 0 ? Math.floor(min * 1.15) : 0;
  }, [allValues]);

  const handleCopyJson = async () => {
    try {
      await navigator.clipboard.writeText(
        artifact.content || JSON.stringify(chartData, null, 2),
      );
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  const handleExportPng = () => {
    if (!svgRef.current) return;
    const svgElement = svgRef.current;
    const svgData = new XMLSerializer().serializeToString(svgElement);
    const svgBlob = new Blob([svgData], {
      type: "image/svg+xml;charset=utf-8",
    });
    const URL = window.URL || window.webkitURL || window;
    const blobURL = URL.createObjectURL(svgBlob);
    const image = new Image();

    image.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = svgElement.clientWidth * 2 || 1200;
      canvas.height = svgElement.clientHeight * 2 || 600;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      const png = canvas.toDataURL("image/png");
      const a = document.createElement("a");
      a.download = `chart-${artifact.artifactId || Date.now()}.png`;
      a.href = png;
      a.click();
    };
    image.src = blobURL;
  };

  // SVG 尺寸常量
  const width = 640;
  const height = 320;
  const padding = { top: 30, right: 30, bottom: 45, left: 55 };
  const chartW = width - padding.left - padding.right;
  const chartH = height - padding.top - padding.bottom;

  // 坐标映射
  const getY = (val: number) => {
    const range = maxValue - minValue || 1;
    return padding.top + chartH - ((val - minValue) / range) * chartH;
  };

  const getX = (index: number, count: number) => {
    if (count <= 1) return padding.left + chartW / 2;
    return padding.left + (index / (count - 1)) * chartW;
  };

  return (
    <div
      className={cn(
        "group relative my-3 overflow-hidden rounded-2xl border border-indigo-200/70 bg-gradient-to-br from-indigo-50/40 via-white to-purple-50/30 shadow-sm transition-all duration-300 dark:border-indigo-900/60 dark:from-zinc-950 dark:via-zinc-900 dark:to-indigo-950/30 backdrop-blur-md",
        className,
      )}
    >
      {/* 顶部控制栏 */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-indigo-100/60 bg-white/70 px-4 py-2.5 dark:border-indigo-900/40 dark:bg-zinc-900/80">
        <div className="flex items-center gap-2 min-w-0">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
            <BarChart3 className="size-4" />
          </div>
          <div className="min-w-0">
            <h4 className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
              {artifact.title || chartData.title || "交互式多模态图表"}
            </h4>
            <span className="text-[10px] text-zinc-400">
              {chartData.labels.length} 个维度 · {chartData.datasets.length}{" "}
              组数据系列
            </span>
          </div>
        </div>

        {/* 视图 Tab & 快捷操作 */}
        <div className="flex items-center gap-1.5">
          {/* 图表类型即时切换 */}
          <div className="flex items-center rounded-lg bg-zinc-100/80 p-0.5 dark:bg-zinc-800">
            <button
              type="button"
              onClick={() => {
                setActiveType("bar");
                setActiveTab("chart");
              }}
              title="柱状图"
              className={cn(
                "rounded-md p-1 text-xs transition-colors",
                effectiveType === "bar" && activeTab === "chart"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <BarChart3 className="size-3.5" />
            </button>
            <button
              type="button"
              onClick={() => {
                setActiveType("line");
                setActiveTab("chart");
              }}
              title="折线图"
              className={cn(
                "rounded-md p-1 text-xs transition-colors",
                effectiveType === "line" && activeTab === "chart"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <LineChart className="size-3.5" />
            </button>
            <button
              type="button"
              onClick={() => {
                setActiveType("pie");
                setActiveTab("chart");
              }}
              title="饼图"
              className={cn(
                "rounded-md p-1 text-xs transition-colors",
                (effectiveType === "pie" || effectiveType === "doughnut") &&
                  activeTab === "chart"
                  ? "bg-white text-indigo-600 shadow-2xs dark:bg-zinc-700 dark:text-indigo-300"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400",
              )}
            >
              <PieChart className="size-3.5" />
            </button>
          </div>

          {/* 表格 / JSON 切换 */}
          <button
            type="button"
            onClick={() =>
              setActiveTab(activeTab === "table" ? "chart" : "table")
            }
            className={cn(
              "flex items-center gap-1 rounded-lg border px-2 py-1 text-[11px] font-medium transition-colors",
              activeTab === "table"
                ? "border-indigo-300 bg-indigo-50 text-indigo-700 dark:border-indigo-800 dark:bg-indigo-950 dark:text-indigo-300"
                : "border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300",
            )}
          >
            <TableIcon className="size-3" />
            <span className="hidden sm:inline">明细表</span>
          </button>

          <button
            type="button"
            onClick={() =>
              setActiveTab(activeTab === "json" ? "chart" : "json")
            }
            className={cn(
              "flex items-center gap-1 rounded-lg border px-2 py-1 text-[11px] font-medium transition-colors",
              activeTab === "json"
                ? "border-indigo-300 bg-indigo-50 text-indigo-700 dark:border-indigo-800 dark:bg-indigo-950 dark:text-indigo-300"
                : "border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300",
            )}
          >
            <Code2 className="size-3" />
            <span className="hidden sm:inline">JSON</span>
          </button>

          <button
            type="button"
            onClick={handleCopyJson}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="复制图表 JSON 配置"
          >
            {copied ? (
              <Check className="size-3 text-emerald-500" />
            ) : (
              <Copy className="size-3" />
            )}
          </button>

          <button
            type="button"
            onClick={handleExportPng}
            className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300"
            title="导出为 PNG 图片"
          >
            <Download className="size-3" />
          </button>
        </div>
      </div>

      {/* 数据系列图例过滤栏 */}
      {chartData.datasets.length > 1 && activeTab === "chart" && (
        <div className="flex flex-wrap items-center gap-2 border-b border-zinc-100 bg-zinc-50/50 px-4 py-1.5 text-xs dark:border-zinc-800/60 dark:bg-zinc-900/40">
          <span className="text-[10px] text-zinc-400">图例：</span>
          {chartData.datasets.map((ds) => {
            const isHidden = hiddenSeries[ds.label];
            return (
              <button
                key={ds.label}
                type="button"
                onClick={() =>
                  setHiddenSeries((prev) => ({
                    ...prev,
                    [ds.label]: !prev[ds.label],
                  }))
                }
                className={cn(
                  "flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium transition-all",
                  isHidden
                    ? "opacity-40 grayscale line-through"
                    : "bg-white shadow-2xs dark:bg-zinc-800",
                )}
              >
                <span
                  className="size-2 rounded-full"
                  style={{ backgroundColor: ds.color }}
                />
                <span className="text-zinc-700 dark:text-zinc-300">
                  {ds.label}
                </span>
              </button>
            );
          })}
        </div>
      )}

      {/* 主画布区 */}
      <div className="p-4">
        {activeTab === "chart" ? (
          <div className="relative flex justify-center overflow-x-auto">
            {chartData.labels.length === 0 || visibleDatasets.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-xs text-zinc-400">
                <BarChart3 className="size-8 text-zinc-300 mb-2" />
                <span>暂无可渲染的图表数据</span>
              </div>
            ) : effectiveType === "pie" || effectiveType === "doughnut" ? (
              /* 饼图 / 环形图 渲染 */
              <div className="flex flex-col sm:flex-row items-center justify-center gap-8 py-4 w-full">
                <svg
                  ref={svgRef}
                  viewBox="0 0 300 300"
                  className="size-60 drop-shadow-sm"
                >
                  <title>饼图</title>
                  {(() => {
                    const ds = visibleDatasets[0] || chartData.datasets[0];
                    const total = ds.data.reduce((a, b) => a + b, 0) || 1;
                    let currentAngle = -Math.PI / 2;
                    const cx = 150;
                    const cy = 150;
                    const r = 120;
                    const innerR = effectiveType === "doughnut" ? 65 : 0;

                    return ds.data.map((val, idx) => {
                      const sliceAngle = (val / total) * 2 * Math.PI;
                      const x1 = cx + r * Math.cos(currentAngle);
                      const y1 = cy + r * Math.sin(currentAngle);
                      const x2 = cx + r * Math.cos(currentAngle + sliceAngle);
                      const y2 = cy + r * Math.sin(currentAngle + sliceAngle);
                      const ix1 = cx + innerR * Math.cos(currentAngle);
                      const iy1 = cy + innerR * Math.sin(currentAngle);
                      const ix2 =
                        cx + innerR * Math.cos(currentAngle + sliceAngle);
                      const iy2 =
                        cy + innerR * Math.sin(currentAngle + sliceAngle);

                      const largeArc = sliceAngle > Math.PI ? 1 : 0;
                      const d =
                        innerR > 0
                          ? `M ${ix1} ${iy1} L ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 1 ${x2} ${y2} L ${ix2} ${iy2} A ${innerR} ${innerR} 0 ${largeArc} 0 ${ix1} ${iy1} Z`
                          : `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 1 ${x2} ${y2} Z`;

                      const sliceColor =
                        DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length];
                      const label = chartData.labels[idx] || `项 ${idx + 1}`;
                      currentAngle += sliceAngle;

                      return (
                        // biome-ignore lint/a11y/noStaticElementInteractions: 图表 SVG 扇形交互节点
                        <path
                          key={`${label}-${val}`}
                          d={d}
                          fill={sliceColor}
                          className="transition-all duration-200 hover:opacity-85 cursor-pointer"
                          onMouseEnter={(e) => {
                            const rect =
                              e.currentTarget.getBoundingClientRect();
                            setHoveredPoint({
                              label,
                              series: ds.label,
                              value: val,
                              x: rect.left + rect.width / 2,
                              y: rect.top,
                            });
                          }}
                          onMouseLeave={() => setHoveredPoint(null)}
                        />
                      );
                    });
                  })()}
                </svg>

                {/* 饼图比例图例 */}
                <div className="space-y-1.5 max-w-xs">
                  {chartData.labels.map((lbl, idx) => {
                    const ds = visibleDatasets[0] || chartData.datasets[0];
                    const val = ds?.data[idx] ?? 0;
                    const total = ds?.data.reduce((a, b) => a + b, 0) || 1;
                    const pct = ((val / total) * 100).toFixed(1);
                    return (
                      <div
                        key={lbl}
                        className="flex items-center justify-between gap-4 text-xs"
                      >
                        <div className="flex items-center gap-1.5 truncate">
                          <span
                            className="size-2.5 shrink-0 rounded-full"
                            style={{
                              backgroundColor:
                                DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length],
                            }}
                          />
                          <span className="truncate text-zinc-700 dark:text-zinc-300">
                            {lbl}
                          </span>
                        </div>
                        <span className="font-mono font-semibold text-zinc-900 dark:text-zinc-100">
                          {val} ({pct}%)
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : (
              /* 柱状图 / 折线图 / 面积图 渲染 */
              <svg
                ref={svgRef}
                viewBox={`0 0 ${width} ${height}`}
                className="w-full max-w-2xl overflow-visible text-xs font-sans"
              >
                <title>图表画布</title>
                <defs>
                  {visibleDatasets.map((ds, i) => (
                    <linearGradient
                      key={ds.label}
                      id={`grad-${chartId}-${i}`}
                      x1="0"
                      y1="0"
                      x2="0"
                      y2="1"
                    >
                      <stop
                        offset="0%"
                        stopColor={ds.color || DEFAULT_PALETTE[i]}
                        stopOpacity="0.45"
                      />
                      <stop
                        offset="100%"
                        stopColor={ds.color || DEFAULT_PALETTE[i]}
                        stopOpacity="0.02"
                      />
                    </linearGradient>
                  ))}
                </defs>

                {/* Y 轴网格线与刻度 */}
                {[0, 0.25, 0.5, 0.75, 1].map((pct) => {
                  const val = minValue + (maxValue - minValue) * (1 - pct);
                  const y = padding.top + chartH * pct;
                  return (
                    <g key={pct}>
                      <line
                        x1={padding.left}
                        y1={y}
                        x2={padding.left + chartW}
                        y2={y}
                        className="stroke-zinc-200/80 dark:stroke-zinc-800/80 stroke-dasharray-2"
                        strokeDasharray="3 3"
                      />
                      <text
                        x={padding.left - 8}
                        y={y + 3}
                        textAnchor="end"
                        className="fill-zinc-400 text-[10px] font-mono"
                      >
                        {Math.round(val)}
                      </text>
                    </g>
                  );
                })}

                {/* X 轴标签 */}
                {chartData.labels.map((lbl, idx) => {
                  const count = chartData.labels.length;
                  const x =
                    effectiveType === "bar"
                      ? padding.left + (idx + 0.5) * (chartW / count)
                      : getX(idx, count);
                  return (
                    <text
                      key={lbl}
                      x={x}
                      y={padding.top + chartH + 18}
                      textAnchor="middle"
                      className="fill-zinc-500 dark:fill-zinc-400 text-[10px] max-w-[50px] truncate"
                    >
                      {lbl.length > 6 ? `${lbl.slice(0, 5)}…` : lbl}
                    </text>
                  );
                })}

                {/* 柱状图模式 */}
                {effectiveType === "bar" && (
                  <g>
                    {chartData.labels.map((lbl, labelIdx) => {
                      const groupWidth = chartW / chartData.labels.length;
                      const barCount = visibleDatasets.length;
                      const barWidth = Math.min(
                        36,
                        (groupWidth * 0.75) / barCount,
                      );
                      const startX =
                        padding.left +
                        labelIdx * groupWidth +
                        (groupWidth - barWidth * barCount) / 2;

                      return visibleDatasets.map((ds, dsIdx) => {
                        const val = ds.data[labelIdx] ?? 0;
                        const x = startX + dsIdx * barWidth;
                        const y = getY(val);
                        const h = Math.max(2, padding.top + chartH - y);
                        const color =
                          ds.color ||
                          DEFAULT_PALETTE[dsIdx % DEFAULT_PALETTE.length];

                        return (
                          // biome-ignore lint/a11y/noStaticElementInteractions: 柱状图矩形悬停交互节点
                          <rect
                            key={`${ds.label}-${lbl}`}
                            x={x + 1}
                            y={y}
                            width={Math.max(2, barWidth - 2)}
                            height={h}
                            rx={3}
                            fill={color}
                            className="transition-all duration-200 hover:opacity-80 cursor-pointer"
                            onMouseEnter={(e) => {
                              const rect =
                                e.currentTarget.getBoundingClientRect();
                              setHoveredPoint({
                                label: lbl,
                                series: ds.label,
                                value: val,
                                x: rect.left + rect.width / 2,
                                y: rect.top,
                              });
                            }}
                            onMouseLeave={() => setHoveredPoint(null)}
                          />
                        );
                      });
                    })}
                  </g>
                )}

                {/* 折线图与面积图模式 */}
                {(effectiveType === "line" || effectiveType === "area") && (
                  <g>
                    {visibleDatasets.map((ds, dsIdx) => {
                      const count = chartData.labels.length;
                      const points = ds.data.map((val, i) => ({
                        x: getX(i, count),
                        y: getY(val),
                        val,
                        lbl: chartData.labels[i],
                      }));

                      const linePath = points.reduce((acc, p, i) => {
                        return `${acc} ${i === 0 ? "M" : "L"} ${p.x} ${p.y}`;
                      }, "");

                      const areaPath = `${linePath} L ${points[points.length - 1]?.x ?? 0} ${
                        padding.top + chartH
                      } L ${points[0]?.x ?? 0} ${padding.top + chartH} Z`;

                      const color =
                        ds.color ||
                        DEFAULT_PALETTE[dsIdx % DEFAULT_PALETTE.length];

                      return (
                        <g key={ds.label}>
                          {effectiveType === "area" && (
                            <path
                              d={areaPath}
                              fill={`url(#grad-${chartId}-${dsIdx})`}
                            />
                          )}
                          <path
                            d={linePath}
                            fill="none"
                            stroke={color}
                            strokeWidth={2.5}
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                          {points.map((p, pIdx) => (
                            // biome-ignore lint/a11y/noStaticElementInteractions: 折线图圆点悬停交互节点
                            <circle
                              key={`${ds.label}-${p.lbl}-${pIdx}`}
                              cx={p.x}
                              cy={p.y}
                              r={4}
                              fill="#ffffff"
                              stroke={color}
                              strokeWidth={2}
                              className="transition-all duration-200 hover:r-6 cursor-pointer"
                              onMouseEnter={(e) => {
                                const rect =
                                  e.currentTarget.getBoundingClientRect();
                                setHoveredPoint({
                                  label: p.lbl,
                                  series: ds.label,
                                  value: p.val,
                                  x: rect.left + rect.width / 2,
                                  y: rect.top,
                                });
                              }}
                              onMouseLeave={() => setHoveredPoint(null)}
                            />
                          ))}
                        </g>
                      );
                    })}
                  </g>
                )}
              </svg>
            )}

            {/* Hover 提示浮层 */}
            {hoveredPoint && (
              <div className="pointer-events-none absolute top-2 right-2 rounded-lg border border-zinc-200 bg-white/95 px-3 py-1.5 text-xs shadow-md dark:border-zinc-800 dark:bg-zinc-900/95 backdrop-blur-xs">
                <p className="font-semibold text-zinc-900 dark:text-zinc-100">
                  {hoveredPoint.label}
                </p>
                <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                  {hoveredPoint.series}:{" "}
                  <span className="font-mono font-bold text-indigo-600 dark:text-indigo-400">
                    {hoveredPoint.value}
                  </span>
                </p>
              </div>
            )}
          </div>
        ) : activeTab === "table" ? (
          /* 明细数据表 */
          <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-[11px] font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-300">
                <tr>
                  <th className="px-3.5 py-2">维度 / 类别</th>
                  {chartData.datasets.map((ds) => (
                    <th key={ds.label} className="px-3.5 py-2">
                      {ds.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800 font-mono text-[11px]">
                {chartData.labels.map((lbl, idx) => (
                  <tr
                    key={lbl}
                    className="hover:bg-zinc-50 dark:hover:bg-zinc-800/40"
                  >
                    <td className="px-3.5 py-2 font-sans font-medium text-zinc-800 dark:text-zinc-200">
                      {lbl}
                    </td>
                    {chartData.datasets.map((ds) => (
                      <td
                        key={ds.label}
                        className="px-3.5 py-2 text-zinc-600 dark:text-zinc-400"
                      >
                        {ds.data[idx] ?? "-"}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          /* JSON 配置查看 */
          <div className="rounded-xl bg-zinc-950 p-3 font-mono text-xs text-emerald-300">
            <pre className="max-h-60 overflow-y-auto">
              {artifact.content || JSON.stringify(chartData, null, 2)}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
}
