"use client";

import { useMemo } from "react";
import type { ArtifactItem } from "@/hooks/useSpringAiStream";
import { ChartArtifactViewer } from "./chart-artifact-viewer";
import { CodeDocArtifactViewer } from "./code-doc-artifact-viewer";
import { ImageArtifactViewer } from "./image-artifact-viewer";
import { InteractiveArtifactViewer } from "./interactive-artifact-viewer";
import { SvgArtifactViewer } from "./svg-artifact-viewer";
import { TableArtifactViewer } from "./table-artifact-viewer";

interface ArtifactDispatcherProps {
  artifact: ArtifactItem;
  className?: string;
}

/** 智能推断与判定 Artifact 目标渲染类型 */
function detectArtifactType(artifact: ArtifactItem): string {
  const type = (artifact.artifactType || "").toLowerCase().trim();

  // 显式指定类型
  if (type === "image" || artifact.mimeType?.startsWith("image/")) {
    if (type === "svg" || artifact.mimeType === "image/svg+xml") return "svg";
    return "image";
  }
  if (type === "chart" || type === "echarts" || type === "chartjs") {
    return "chart";
  }
  if (type === "table" || type === "data-table" || type === "csv") {
    return "table";
  }
  if (type === "svg") {
    return "svg";
  }
  if (type === "html" || type === "interactive" || type === "sandbox") {
    return "html";
  }
  if (type === "code" || type === "document") {
    return "code";
  }

  // 启发式内容特征探测
  const content = (artifact.content || "").trim();
  if (
    content.startsWith("<svg") ||
    (content.includes("<svg") && content.includes("</svg>"))
  ) {
    return "svg";
  }
  if (
    content.toLowerCase().includes("<!doctype html") ||
    content.toLowerCase().includes("<html") ||
    (content.includes("<div") && content.includes("<script"))
  ) {
    return "html";
  }

  if (content.startsWith("{") || content.startsWith("[")) {
    try {
      const obj = JSON.parse(content);
      if (Array.isArray(obj)) {
        return "table";
      }
      if (obj.labels && obj.datasets) {
        return "chart";
      }
      if (obj.xAxis && obj.series) {
        return "chart";
      }
    } catch {}
  }

  return "code";
}

export function ArtifactDispatcher({
  artifact,
  className,
}: ArtifactDispatcherProps) {
  const resolvedType = useMemo(() => detectArtifactType(artifact), [artifact]);

  switch (resolvedType) {
    case "image":
      return <ImageArtifactViewer artifact={artifact} className={className} />;
    case "chart":
      return <ChartArtifactViewer artifact={artifact} className={className} />;
    case "table":
      return <TableArtifactViewer artifact={artifact} className={className} />;
    case "svg":
      return <SvgArtifactViewer artifact={artifact} className={className} />;
    case "html":
      return (
        <InteractiveArtifactViewer artifact={artifact} className={className} />
      );
    default:
      return (
        <CodeDocArtifactViewer artifact={artifact} className={className} />
      );
  }
}
