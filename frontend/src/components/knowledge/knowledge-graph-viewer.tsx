"use client";

import {
  ArrowRight,
  Filter,
  Network,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  X,
  Zap,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  type GraphStatsDto,
  type KnowledgeEntity,
  type KnowledgeGraphDto,
  type KnowledgeRelation,
  ragGraphApi,
  ragGraphExtractApi,
  ragGraphStatsApi,
  ragGraphSubgraphApi,
} from "@/lib/api";
import { cn } from "@/lib/utils";

interface GraphNode extends KnowledgeEntity {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

interface GraphLink {
  source: GraphNode;
  target: GraphNode;
  relation: KnowledgeRelation;
}

const TYPE_COLORS: Record<
  string,
  { bg: string; border: string; text: string; fill: string }
> = {
  TECHNOLOGY: {
    bg: "bg-indigo-500/10 dark:bg-indigo-500/20",
    border: "border-indigo-500",
    text: "text-indigo-600 dark:text-indigo-400",
    fill: "#6366f1",
  },
  CONCEPT: {
    bg: "bg-emerald-500/10 dark:bg-emerald-500/20",
    border: "border-emerald-500",
    text: "text-emerald-600 dark:text-emerald-400",
    fill: "#10b981",
  },
  COMPONENT: {
    bg: "bg-amber-500/10 dark:bg-amber-500/20",
    border: "border-amber-500",
    text: "text-amber-600 dark:text-amber-400",
    fill: "#f59e0b",
  },
  ORGANIZATION: {
    bg: "bg-sky-500/10 dark:bg-sky-500/20",
    border: "border-sky-500",
    text: "text-sky-600 dark:text-sky-400",
    fill: "#0ea5e9",
  },
  PERSON: {
    bg: "bg-rose-500/10 dark:bg-rose-500/20",
    border: "border-rose-500",
    text: "text-rose-600 dark:text-rose-400",
    fill: "#f43f5e",
  },
  OTHER: {
    bg: "bg-zinc-500/10 dark:bg-zinc-500/20",
    border: "border-zinc-500",
    text: "text-zinc-600 dark:text-zinc-400",
    fill: "#71717a",
  },
};

export function KnowledgeGraphViewer({
  selectedDocumentId,
}: {
  selectedDocumentId?: string;
}) {
  const [graphData, setGraphData] = useState<KnowledgeGraphDto | null>(null);
  const [stats, setStats] = useState<GraphStatsDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [extracting, setExtracting] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeHop, setActiveHop] = useState<number>(2);
  const [selectedTypes, setSelectedTypes] = useState<Set<string>>(
    new Set([
      "TECHNOLOGY",
      "CONCEPT",
      "COMPONENT",
      "ORGANIZATION",
      "PERSON",
      "OTHER",
    ]),
  );
  const [selectedNode, setSelectedNode] = useState<KnowledgeEntity | null>(
    null,
  );
  const [showExtractModal, setShowExtractModal] = useState(false);
  const [extractText, setExtractText] = useState("");

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const nodesRef = useRef<GraphNode[]>([]);
  const linksRef = useRef<GraphLink[]>([]);
  const transformRef = useRef({ x: 0, y: 0, scale: 1 });
  const isDraggingCanvasRef = useRef(false);
  const dragStartRef = useRef({ x: 0, y: 0 });
  const draggedNodeRef = useRef<GraphNode | null>(null);
  const hoveredNodeRef = useRef<GraphNode | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  const fetchGraph = useCallback(async () => {
    setLoading(true);
    const data = await ragGraphApi(selectedDocumentId || undefined);
    const statsData = await ragGraphStatsApi();
    if (data) setGraphData(data);
    if (statsData) setStats(statsData);
    setLoading(false);
  }, [selectedDocumentId]);

  useEffect(() => {
    void fetchGraph();
  }, [fetchGraph]);

  // 构建节点与连接拓扑
  useEffect(() => {
    if (!graphData || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const width = canvas.width || 800;
    const height = canvas.height || 600;

    const filteredNodes = graphData.nodes.filter(
      (n) => selectedTypes.has(n.type) || selectedTypes.size === 0,
    );

    const nodeMap = new Map<string, GraphNode>();
    const newNodes: GraphNode[] = filteredNodes.map((n, idx) => {
      const angle = (idx / (filteredNodes.length || 1)) * 2 * Math.PI;
      const radius = 180 + (idx % 3) * 60;
      const node: GraphNode = {
        ...n,
        x: width / 2 + Math.cos(angle) * radius + (Math.random() - 0.5) * 40,
        y: height / 2 + Math.sin(angle) * radius + (Math.random() - 0.5) * 40,
        vx: 0,
        vy: 0,
        radius: 20 + Math.min((n.weight || 1) * 8, 16),
      };
      nodeMap.set(n.name.toLowerCase().trim(), node);
      return node;
    });

    const newLinks: GraphLink[] = [];
    for (const edge of graphData.edges) {
      const src = nodeMap.get(edge.sourceEntityName.toLowerCase().trim());
      const tgt = nodeMap.get(edge.targetEntityName.toLowerCase().trim());
      if (src && tgt) {
        newLinks.push({ source: src, target: tgt, relation: edge });
      }
    }

    nodesRef.current = newNodes;
    linksRef.current = newLinks;
  }, [graphData, selectedTypes]);

  // 力导向物理引擎循环与 Canvas 渲染
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let isRunning = true;

    const render = () => {
      if (!isRunning) return;

      const nodes = nodesRef.current;
      const links = linksRef.current;
      const width = canvas.width;
      const height = canvas.height;
      const centerX = width / 2;
      const centerY = height / 2;

      // 1. 物理模拟步进
      const repulsion = 1200;
      const springLength = 120;
      const springK = 0.04;
      const centerGravity = 0.015;
      const damping = 0.85;

      // 排斥力 (斥力)
      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const n1 = nodes[i];
          const n2 = nodes[j];
          const dx = n2.x - n1.x;
          const dy = n2.y - n1.y;
          const distSq = dx * dx + dy * dy || 1;
          const dist = Math.sqrt(distSq);
          if (dist < 400) {
            const force = repulsion / distSq;
            const fx = (dx / dist) * force;
            const fy = (dy / dist) * force;
            n1.vx -= fx;
            n1.vy -= fy;
            n2.vx += fx;
            n2.vy += fy;
          }
        }
      }

      // 弹簧引力 (边拉力)
      for (const link of links) {
        const dx = link.target.x - link.source.x;
        const dy = link.target.y - link.source.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const force = (dist - springLength) * springK;
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        link.source.vx += fx;
        link.source.vy += fy;
        link.target.vx -= fx;
        link.target.vy -= fy;
      }

      // 中心引力与速度积分
      for (const node of nodes) {
        if (node === draggedNodeRef.current) continue;
        node.vx += (centerX - node.x) * centerGravity;
        node.vy += (centerY - node.y) * centerGravity;

        node.vx *= damping;
        node.vy *= damping;

        node.x += node.vx;
        node.y += node.vy;
      }

      // 2. Canvas 绘制
      ctx.clearRect(0, 0, width, height);
      ctx.save();

      const { x: panX, y: panY, scale } = transformRef.current;
      ctx.translate(panX, panY);
      ctx.scale(scale, scale);

      // 绘制网格背景点
      ctx.fillStyle = "rgba(161, 161, 170, 0.15)";
      const gridSize = 40;
      const startX = -panX / scale - 100;
      const endX = (width - panX) / scale + 100;
      const startY = -panY / scale - 100;
      const endY = (height - panY) / scale + 100;

      for (
        let gx = Math.floor(startX / gridSize) * gridSize;
        gx < endX;
        gx += gridSize
      ) {
        for (
          let gy = Math.floor(startY / gridSize) * gridSize;
          gy < endY;
          gy += gridSize
        ) {
          ctx.beginPath();
          ctx.arc(gx, gy, 1, 0, 2 * Math.PI);
          ctx.fill();
        }
      }

      // 绘制关系边与箭头
      for (const link of links) {
        const isHoveredEdge =
          hoveredNodeRef.current &&
          (link.source === hoveredNodeRef.current ||
            link.target === hoveredNodeRef.current);
        const isSelectedEdge =
          selectedNode &&
          (link.source.name === selectedNode.name ||
            link.target.name === selectedNode.name);

        ctx.strokeStyle = isSelectedEdge
          ? "#ec4899"
          : isHoveredEdge
            ? "#6366f1"
            : "rgba(148, 163, 184, 0.4)";
        ctx.lineWidth = isSelectedEdge ? 2.5 : isHoveredEdge ? 2 : 1.2;

        ctx.beginPath();
        ctx.moveTo(link.source.x, link.source.y);
        ctx.lineTo(link.target.x, link.target.y);
        ctx.stroke();

        // 绘制箭头
        const dx = link.target.x - link.source.x;
        const dy = link.target.y - link.source.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 30) {
          const arrowX = link.target.x - (dx / dist) * (link.target.radius + 6);
          const arrowY = link.target.y - (dy / dist) * (link.target.radius + 6);
          const angle = Math.atan2(dy, dx);
          const arrowSize = 6;

          ctx.fillStyle = ctx.strokeStyle;
          ctx.beginPath();
          ctx.moveTo(arrowX, arrowY);
          ctx.lineTo(
            arrowX - arrowSize * Math.cos(angle - Math.PI / 6),
            arrowY - arrowSize * Math.sin(angle - Math.PI / 6),
          );
          ctx.lineTo(
            arrowX - arrowSize * Math.cos(angle + Math.PI / 6),
            arrowY - arrowSize * Math.sin(angle + Math.PI / 6),
          );
          ctx.closePath();
          ctx.fill();

          // 绘制关系文字谓词
          const midX = (link.source.x + link.target.x) / 2;
          const midY = (link.source.y + link.target.y) / 2;
          ctx.font = "9px sans-serif";
          ctx.fillStyle = isSelectedEdge
            ? "#ec4899"
            : "rgba(100, 116, 139, 0.85)";
          ctx.textAlign = "center";
          ctx.fillText(link.relation.relation, midX, midY - 4);
        }
      }

      // 绘制实体节点
      for (const node of nodes) {
        const isHovered = hoveredNodeRef.current === node;
        const isSelected = selectedNode?.name === node.name;
        const isSearchMatch =
          searchQuery.trim().length > 0 &&
          node.name.toLowerCase().includes(searchQuery.toLowerCase().trim());

        const colorInfo = TYPE_COLORS[node.type] || TYPE_COLORS.OTHER;

        // 阴影发光 Halo
        if (isSelected || isHovered || isSearchMatch) {
          ctx.beginPath();
          ctx.arc(
            node.x,
            node.y,
            node.radius + (isSelected ? 8 : 5),
            0,
            2 * Math.PI,
          );
          ctx.fillStyle = isSelected
            ? "rgba(236, 72, 153, 0.25)"
            : "rgba(99, 102, 241, 0.25)";
          ctx.fill();
        }

        // 节点本体
        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius, 0, 2 * Math.PI);
        ctx.fillStyle = colorInfo.fill;
        ctx.fill();
        ctx.lineWidth = isSelected ? 3 : 2;
        ctx.strokeStyle = isSelected ? "#ec4899" : "#ffffff";
        ctx.stroke();

        // 节点文字 Label
        ctx.font = `bold ${Math.max(10, node.radius * 0.55)}px sans-serif`;
        ctx.fillStyle = "#ffffff";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";

        const shortName =
          node.name.length > 8 ? `${node.name.slice(0, 7)}…` : node.name;
        ctx.fillText(shortName, node.x, node.y);

        // 下方实体全名与类型小标
        ctx.font = "10px sans-serif";
        ctx.fillStyle = "rgba(71, 85, 105, 0.9)";
        ctx.fillText(node.name, node.x, node.y + node.radius + 12);
      }

      ctx.restore();
      animationFrameRef.current = requestAnimationFrame(render);
    };

    animationFrameRef.current = requestAnimationFrame(render);

    return () => {
      isRunning = false;
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [searchQuery, selectedNode]);

  // 窗口与画布自适应
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const updateSize = () => {
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width;
      canvas.height = rect.height;
    };
    updateSize();
    window.addEventListener("resize", updateSize);
    return () => window.removeEventListener("resize", updateSize);
  }, []);

  // 鼠标交互事件处理
  const getCanvasCoords = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return { x: 0, y: 0 };
    const rect = canvas.getBoundingClientRect();
    const rawX = e.clientX - rect.left;
    const rawY = e.clientY - rect.top;
    const { x: panX, y: panY, scale } = transformRef.current;
    return {
      x: (rawX - panX) / scale,
      y: (rawY - panY) / scale,
    };
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const coords = getCanvasCoords(e);
    const nodes = nodesRef.current;
    const clickedNode = nodes.find((n) => {
      const dx = n.x - coords.x;
      const dy = n.y - coords.y;
      return Math.sqrt(dx * dx + dy * dy) <= n.radius;
    });

    if (clickedNode) {
      draggedNodeRef.current = clickedNode;
      setSelectedNode(clickedNode);
    } else {
      isDraggingCanvasRef.current = true;
      dragStartRef.current = { x: e.clientX, y: e.clientY };
    }
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (draggedNodeRef.current) {
      const coords = getCanvasCoords(e);
      draggedNodeRef.current.x = coords.x;
      draggedNodeRef.current.y = coords.y;
      draggedNodeRef.current.vx = 0;
      draggedNodeRef.current.vy = 0;
      return;
    }

    if (isDraggingCanvasRef.current) {
      const dx = e.clientX - dragStartRef.current.x;
      const dy = e.clientY - dragStartRef.current.y;
      transformRef.current.x += dx;
      transformRef.current.y += dy;
      dragStartRef.current = { x: e.clientX, y: e.clientY };
      return;
    }

    const coords = getCanvasCoords(e);
    const nodes = nodesRef.current;
    const hovered = nodes.find((n) => {
      const dx = n.x - coords.x;
      const dy = n.y - coords.y;
      return Math.sqrt(dx * dx + dy * dy) <= n.radius;
    });
    hoveredNodeRef.current = hovered || null;
  };

  const handleMouseUp = () => {
    draggedNodeRef.current = null;
    isDraggingCanvasRef.current = false;
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
    const newScale = Math.min(
      Math.max(0.3, transformRef.current.scale * zoomFactor),
      3,
    );
    transformRef.current.scale = newScale;
  };

  const handleResetView = () => {
    transformRef.current = { x: 0, y: 0, scale: 1 };
  };

  const handleQuerySubgraph = async (seeds: string) => {
    if (!seeds.trim()) {
      void fetchGraph();
      return;
    }
    setLoading(true);
    const subgraph = await ragGraphSubgraphApi({
      seeds,
      maxHops: activeHop,
      maxNodes: 50,
    });
    if (subgraph) {
      setGraphData(subgraph);
    }
    setLoading(false);
  };

  const handleExtractSubmit = async () => {
    if (!extractText.trim()) return;
    setExtracting(true);
    const result = await ragGraphExtractApi(
      extractText,
      selectedDocumentId || "custom-extract",
    );
    if (result) {
      setShowExtractModal(false);
      setExtractText("");
      void fetchGraph();
    }
    setExtracting(false);
  };

  const toggleType = (type: string) => {
    const next = new Set(selectedTypes);
    if (next.has(type)) next.delete(type);
    else next.add(type);
    setSelectedTypes(next);
  };

  // 计算当前选中节点的相连关系边
  const connectedRelations = selectedNode
    ? (graphData?.edges || []).filter(
        (e) =>
          e.sourceEntityName.toLowerCase() ===
            selectedNode.name.toLowerCase() ||
          e.targetEntityName.toLowerCase() === selectedNode.name.toLowerCase(),
      )
    : [];

  return (
    <div className="relative flex flex-col h-[750px] w-full rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800/80 dark:bg-zinc-950 overflow-hidden">
      {/* 顶部控制栏 */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200/80 px-4 py-3 dark:border-zinc-800/80 bg-zinc-50/50 dark:bg-zinc-900/50 backdrop-blur-md">
        <div className="flex items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-400">
            <Network className="size-4" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <span>GraphRAG 知识图谱拓扑网络</span>
              {stats && (
                <span className="rounded-full bg-indigo-50 dark:bg-indigo-950/60 px-2 py-0.5 text-[11px] font-medium text-indigo-600 dark:text-indigo-300 border border-indigo-200/50 dark:border-indigo-800/50">
                  {stats.totalNodes} 实体 · {stats.totalEdges} 关系三元组
                </span>
              )}
            </h3>
          </div>
        </div>

        {/* 检索与子图扩散操作 */}
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-zinc-400" />
            <input
              type="text"
              placeholder="搜索实体定位或子图扩散..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleQuerySubgraph(searchQuery);
              }}
              className="h-8 w-56 rounded-lg border border-zinc-200 bg-white pl-8 pr-3 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-hidden dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
            />
          </div>

          {/* 扩散跳数选择 */}
          <div className="flex items-center rounded-lg border border-zinc-200 bg-white p-0.5 text-xs dark:border-zinc-800 dark:bg-zinc-900">
            {[1, 2, 3].map((hop) => (
              <button
                key={hop}
                type="button"
                onClick={() => {
                  setActiveHop(hop);
                  if (searchQuery) handleQuerySubgraph(searchQuery);
                }}
                className={cn(
                  "px-2 py-1 rounded-md transition-colors",
                  activeHop === hop
                    ? "bg-indigo-600 text-white font-medium shadow-xs"
                    : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100",
                )}
              >
                {hop} 跳
              </button>
            ))}
          </div>

          <Button
            size="sm"
            variant="outline"
            onClick={fetchGraph}
            disabled={loading}
            className="h-8 text-xs gap-1.5"
          >
            <RefreshCw
              className={cn(
                "size-3.5",
                loading && "animate-spin text-indigo-500",
              )}
            />
            <span>刷新</span>
          </Button>

          <Button
            size="sm"
            onClick={() => setShowExtractModal(true)}
            className="h-8 text-xs gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs"
          >
            <Plus className="size-3.5" />
            <span>三元组抽取</span>
          </Button>
        </div>
      </div>

      {/* 实体类型过滤胶囊栏 */}
      <div className="flex items-center gap-1.5 border-b border-zinc-100 px-4 py-2 text-xs dark:border-zinc-900 bg-white/60 dark:bg-zinc-950/60 overflow-x-auto">
        <span className="text-zinc-400 text-[11px] mr-1 flex items-center gap-1">
          <Filter className="size-3" />
          <span>实体分类：</span>
        </span>
        {Object.entries(TYPE_COLORS).map(([type, colors]) => (
          <button
            key={type}
            type="button"
            onClick={() => toggleType(type)}
            className={cn(
              "flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium border transition-all",
              selectedTypes.has(type)
                ? `${colors.bg} ${colors.border} ${colors.text}`
                : "border-zinc-200 text-zinc-400 opacity-50 dark:border-zinc-800",
            )}
          >
            <span
              className="size-1.5 rounded-full"
              style={{ backgroundColor: colors.fill }}
            />
            <span>{type}</span>
          </button>
        ))}
      </div>

      {/* 主画布与侧边详情抽屉 */}
      <div className="relative flex-1 w-full h-full bg-zinc-900/5 dark:bg-black/20">
        <canvas
          ref={canvasRef}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onWheel={handleWheel}
          className="size-full cursor-grab active:cursor-grabbing block"
        />

        {/* 缩放与视图控制浮动按钮组 */}
        <div className="absolute bottom-4 right-4 flex flex-col gap-1 rounded-xl border border-zinc-200/80 bg-white/90 p-1 shadow-md dark:border-zinc-800 dark:bg-zinc-900/90 backdrop-blur-md">
          <button
            type="button"
            onClick={() => {
              transformRef.current.scale = Math.min(
                transformRef.current.scale * 1.2,
                3,
              );
            }}
            className="flex size-7 items-center justify-center rounded-lg text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100 transition-colors"
            title="放大"
          >
            <Plus className="size-3.5" />
          </button>
          <button
            type="button"
            onClick={() => {
              transformRef.current.scale = Math.max(
                transformRef.current.scale * 0.8,
                0.3,
              );
            }}
            className="flex size-7 items-center justify-center rounded-lg text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100 transition-colors"
            title="缩小"
          >
            <span className="text-sm font-bold leading-none">-</span>
          </button>
          <button
            type="button"
            onClick={handleResetView}
            className="flex size-7 items-center justify-center rounded-lg text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100 transition-colors"
            title="重置视图"
          >
            <RefreshCw className="size-3.5" />
          </button>
        </div>

        {/* 选中实体属性与关系详情抽屉 */}
        {selectedNode && (
          <div className="absolute top-4 right-4 w-80 max-h-[calc(100%-32px)] overflow-y-auto rounded-2xl border border-zinc-200/90 bg-white/95 p-4 shadow-xl dark:border-zinc-800/90 dark:bg-zinc-900/95 backdrop-blur-xl animate-in slide-in-from-right-4 duration-200">
            <div className="flex items-start justify-between gap-2 border-b border-zinc-100 pb-3 dark:border-zinc-800">
              <div className="min-w-0">
                <span
                  className={cn(
                    "inline-block rounded-full px-2 py-0.5 text-[10px] font-semibold border mb-1.5",
                    TYPE_COLORS[selectedNode.type]?.bg || TYPE_COLORS.OTHER.bg,
                    TYPE_COLORS[selectedNode.type]?.border ||
                      TYPE_COLORS.OTHER.border,
                    TYPE_COLORS[selectedNode.type]?.text ||
                      TYPE_COLORS.OTHER.text,
                  )}
                >
                  {selectedNode.type}
                </span>
                <h4 className="text-base font-bold text-zinc-900 dark:text-zinc-100 truncate">
                  {selectedNode.name}
                </h4>
              </div>
              <button
                type="button"
                onClick={() => setSelectedNode(null)}
                className="flex size-6 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="mt-3 space-y-3 text-xs">
              {selectedNode.description && (
                <div>
                  <span className="font-semibold text-zinc-500 dark:text-zinc-400">
                    概念释义与描述：
                  </span>
                  <p className="mt-1 text-zinc-700 dark:text-zinc-300 leading-relaxed bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-xl border border-zinc-100 dark:border-zinc-800">
                    {selectedNode.description}
                  </p>
                </div>
              )}

              {selectedNode.documentId && (
                <div className="flex items-center justify-between text-[11px] text-zinc-500">
                  <span>关联文档标识:</span>
                  <span className="font-mono text-zinc-700 dark:text-zinc-300 truncate max-w-[140px]">
                    {selectedNode.documentId}
                  </span>
                </div>
              )}

              {/* 关联三元组边列表 */}
              <div>
                <div className="flex items-center justify-between mb-1.5 font-semibold text-zinc-700 dark:text-zinc-300">
                  <span>拓扑连接关系 ({connectedRelations.length})</span>
                </div>

                <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                  {connectedRelations.length === 0 ? (
                    <p className="text-zinc-400 text-[11px] py-1">
                      暂无与其他实体的直连边
                    </p>
                  ) : (
                    connectedRelations.map((rel) => (
                      <div
                        key={rel.id}
                        className="rounded-lg border border-zinc-200/60 bg-zinc-50/70 p-2 text-[11px] dark:border-zinc-800/60 dark:bg-zinc-800/40"
                      >
                        <div className="flex items-center gap-1.5 font-medium text-zinc-800 dark:text-zinc-200">
                          <span className="text-indigo-600 dark:text-indigo-400">
                            {rel.sourceEntityName}
                          </span>
                          <span className="rounded-sm bg-zinc-200 px-1 py-0.2 text-[9px] font-bold text-zinc-700 dark:bg-zinc-700 dark:text-zinc-300">
                            {rel.relation}
                          </span>
                          <ArrowRight className="size-3 text-zinc-400" />
                          <span className="text-purple-600 dark:text-purple-400">
                            {rel.targetEntityName}
                          </span>
                        </div>
                        {rel.description && (
                          <p className="mt-1 text-zinc-500 dark:text-zinc-400 text-[10px] leading-tight">
                            {rel.description}
                          </p>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* 针对当前实体执行扩散 */}
              <div className="pt-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => handleQuerySubgraph(selectedNode.name)}
                  className="w-full text-xs gap-1.5 border-indigo-200 dark:border-indigo-800 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/40"
                >
                  <Sparkles className="size-3.5" />
                  <span>以该实体为种子展开拓扑</span>
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 实体关系抽取对话框 */}
      {showExtractModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="w-full max-w-lg rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                <Zap className="size-4 text-indigo-600" />
                <span>LLM 知识图谱实体与三元组抽取</span>
              </h3>
              <button
                type="button"
                onClick={() => setShowExtractModal(false)}
                className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
              >
                <X className="size-4" />
              </button>
            </div>

            <p className="text-xs text-zinc-500 mb-3">
              输入任意技术文档、架构设计或业务背景，模型将自动提取关键实体（CONCEPT
              / TECHNOLOGY / COMPONENT）以及因果/依赖三元组边并写入知识图谱。
            </p>

            <textarea
              rows={6}
              value={extractText}
              onChange={(e) => setExtractText(e.target.value)}
              placeholder="在此粘贴待提取知识三元组的文本..."
              className="w-full rounded-xl border border-zinc-200 bg-zinc-50 p-3 text-xs text-zinc-900 focus:border-indigo-500 focus:outline-hidden dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100 font-mono"
            />

            <div className="mt-4 flex items-center justify-end gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowExtractModal(false)}
                className="text-xs"
              >
                取消
              </Button>
              <Button
                size="sm"
                onClick={handleExtractSubmit}
                disabled={extracting || !extractText.trim()}
                className="bg-indigo-600 hover:bg-indigo-700 text-white text-xs gap-1.5"
              >
                {extracting ? (
                  <>
                    <RefreshCw className="size-3.5 animate-spin" />
                    <span>抽取中...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="size-3.5" />
                    <span>执行抽取并入库</span>
                  </>
                )}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
