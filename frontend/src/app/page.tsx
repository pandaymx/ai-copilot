"use client";

import {
  AlertTriangle,
  BarChart2,
  Download,
  FileText,
  GitFork,
  PanelLeftOpen,
  Paperclip,
  RotateCcw,
  Search,
  Send,
  Sparkles,
  Square,
  UploadCloud,
  Users,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { CitationViewerDrawer } from "@/components/chat/citation-viewer-drawer";
import { ContextInheritanceModal } from "@/components/chat/context-inheritance-modal";
import { ConversationSummaryModal } from "@/components/chat/conversation-summary-modal";
import { DocumentChatBar } from "@/components/chat/document-chat-bar";
import { EmptyState } from "@/components/chat/empty-state";
import { ExportDialog } from "@/components/chat/export-dialog";
import { InheritedContextBanner } from "@/components/chat/inherited-context-banner";
import {
  type ChatMessage,
  LiveMessageBubble,
  MessageBubble,
} from "@/components/chat/message-bubble";
import { ModelCompareModal } from "@/components/chat/model-compare-modal";
import { ModelPerformanceModal } from "@/components/chat/model-performance-modal";
import {
  type BackendProviderEntry,
  isVisionModel,
  ModelSelector,
  type SelectedModel,
} from "@/components/chat/model-selector";
import { MultiAgentModal } from "@/components/chat/multi-agent-modal";
import { PersonaMarketModal } from "@/components/chat/persona-market-modal";
import { RateLimitIndicator } from "@/components/chat/rate-limit-indicator";
import { SearchDialog } from "@/components/chat/search-dialog";
import { Sidebar } from "@/components/chat/sidebar";
import { TokenBudgetBar } from "@/components/chat/token-budget-bar";
import { VisionScenarioPills } from "@/components/chat/vision-scenario-pills";
import { VoiceRecorderButton } from "@/components/chat/voice-recorder-button";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { useTokenBudget } from "@/context/token-budget-context";
import { useChatInput } from "@/hooks/useChatInput";
import { useChatSession } from "@/hooks/useChatSession";
import { useChatStreaming } from "@/hooks/useChatStreaming";
import type { DocumentCitationItem, Persona } from "@/lib/api";
import { cn } from "@/lib/utils";

const MODEL_STORAGE_KEY = "ai-copilot-selected-model";

/** 从 localStorage 读取上次使用的模型配置（仅在客户端挂载后调用，避免 SSR 水合不匹配） */
function loadSavedModel(): SelectedModel {
  try {
    const raw = localStorage.getItem(MODEL_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as SelectedModel;
      if (parsed?.provider && parsed?.model) {
        return parsed;
      }
    }
  } catch {
    // 忽略解析错误
  }
  return { provider: "deepseek", model: "deepseek-chat" };
}

export default function Home() {
  // 初始状态使用固定的 SSR 安全默认值，确保服务端渲染与客户端首次渲染一致，
  // 避免 hydration mismatch（从 localStorage 读取的模型在挂载后再应用）。
  const [model, setModel] = useState<SelectedModel>({
    provider: "deepseek",
    model: "deepseek-chat",
  });
  const [catalog, setCatalog] = useState<BackendProviderEntry[]>([]);

  // 侧边栏与弹窗状态
  const [collapsed, setCollapsed] = useState(false);
  const [showExport, setShowExport] = useState(false);
  const [showSummary, setShowSummary] = useState(false);
  const [showInheritanceModal, setShowInheritanceModal] = useState(false);
  const [inheritanceSourceId, setInheritanceSourceId] = useState<string | null>(
    null,
  );
  const [showPerformanceModal, setShowPerformanceModal] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);

  // 原文引用抽屉状态
  const [citationDrawer, setCitationDrawer] = useState<{
    open: boolean;
    citations: DocumentCitationItem[];
    activeCitationId?: string;
  }>({
    open: false,
    citations: [],
  });

  // 计算当前选中的模型是否支持多模态视觉
  const currentProviderObj = catalog.find((p) => p.id === model.provider);
  const currentModelObj = currentProviderObj?.models.find(
    (m) => m.id === model.model,
  );
  const currentSupportsVision = currentModelObj
    ? isVisionModel(currentModelObj)
    : model.provider === "openai" ||
      model.provider === "google" ||
      model.model.includes("gpt-4") ||
      model.model.includes("gemini");

  // 1. 会话与历史消息 Hook
  const {
    sessions,
    activeId,
    activeSession,
    setActiveId,
    messages,
    setMessages,
    loadingSessions,
    isOfflineFallback,
    mutateSessions,
    setStreaming,
    selectSession,
    deleteSession,
    renameSession,
    newSession,
    sessionsRef,
  } = useChatSession({
    onSelectSessionCallback: () => {
      if (typeof window !== "undefined" && window.innerWidth < 768) {
        setCollapsed(true);
      }
    },
  });

  // 2. 输入框、附件与文档对话管理 Hook
  const {
    input,
    setInput,
    attachments,
    setAttachments,
    imageMode,
    setImageMode,
    agentEnabled,
    setAgentEnabled,
    documentChatEnabled,
    setDocumentChatEnabled,
    docChatDocuments,
    selectedDocIds,
    setSelectedDocIds,
    refreshDocChatDocs,
    fileInputRef,
    textareaRef,
    recorder,
    handleVoiceStop,
    handleFileChange,
    removeAttachment,
    handlePaste,
    isDraggingOver,
    handleDragEnter,
    handleDragOver,
    handleDragLeave,
    handleDrop,
  } = useChatInput({
    currentSupportsVision,
    activeId,
  });

  // 实时 Token 预算与草稿估算 Hook
  const { setDraft, isOverBudget } = useTokenBudget();

  useEffect(() => {
    setDraft(input, currentModelObj);
  }, [input, currentModelObj, setDraft]);

  // 智能体角色市场 (Persona Store) 弹窗与激活状态
  const [personaMarketOpen, setPersonaMarketOpen] = useState(false);
  const [selectedPersona, setSelectedPersona] = useState<Persona | null>(null);

  // 3. 流式 SSE 编排与发送 Hook
  const {
    isStreaming,
    stop,
    streamStore,
    error: streamError,
    handleSend,
    handleRegenerate,
    handleEditAndResend,
    liveIdRef,
  } = useChatStreaming({
    activeId,
    setActiveId,
    messages,
    setMessages,
    mutateSessions,
    sessionsRef,
    model,
    currentSupportsVision,
    attachments,
    setAttachments,
    input,
    setInput,
    imageMode,
    agentEnabled,
    documentChatEnabled,
    docChatDocuments,
    selectedDocIds,
    personaId: selectedPersona?.id,
  });

  // 多模型并排对比竞技场弹窗状态
  const [compareModalOpen, setCompareModalOpen] = useState(false);
  const [comparePrompt, setComparePrompt] = useState("");

  // 多 Agent 协同研讨工作台弹窗状态
  const [multiAgentModalOpen, setMultiAgentModalOpen] = useState(false);
  const [multiAgentGoal, setMultiAgentGoal] = useState("");

  // 将流式状态桥接进会话持久化 Hook：流式传输期间跳过 localStorage 全量写入，
  // 配合 useChatSession 内部的 500ms 防抖，避免每帧 SSE 更新阻塞主线程。
  useEffect(() => {
    setStreaming(isStreaming);
  }, [isStreaming, setStreaming]);

  const bottomRef = useRef<HTMLDivElement>(null);

  // 挂载后从 localStorage 恢复上次选中的模型（不在 useState 初始化时读取，
  // 否则服务端渲染与客户端首次渲染会不一致导致 hydration mismatch）。
  useEffect(() => {
    const saved = loadSavedModel();
    setModel(saved);
    // 仅运行一次：客户端挂载后应用持久化模型
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 模型选择本地持久化
  useEffect(() => {
    if (typeof window !== "undefined" && model?.provider && model?.model) {
      localStorage.setItem(MODEL_STORAGE_KEY, JSON.stringify(model));
    }
  }, [model]);

  // 全局 ⌘K / Ctrl+K 快捷键唤起全盘全文检索
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setSearchOpen(true);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // 自动滚动到底部
  // biome-ignore lint/correctness/useExhaustiveDependencies: 副作用触发滚动
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    void handleSend();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleSend();
    }
  };

  return (
    <div className="relative flex h-dvh overflow-hidden bg-ambient-mesh bg-zinc-50 dark:bg-zinc-950">
      <Sidebar
        sessions={sessions}
        activeId={activeId}
        collapsed={collapsed}
        loadingSessions={loadingSessions}
        isOfflineFallback={isOfflineFallback}
        activePersona={selectedPersona}
        onSelect={selectSession}
        onNew={newSession}
        onDelete={(id) => setDeleteTarget(id)}
        onRename={renameSession}
        onInherit={(id) => {
          setInheritanceSourceId(id);
          setShowInheritanceModal(true);
        }}
        onToggleCollapsed={() => setCollapsed((c) => !c)}
        onOpenSearch={() => setSearchOpen(true)}
        onOpenPersonaMarket={() => setPersonaMarketOpen(true)}
      />

      {/* 移动端遮罩 */}
      <button
        type="button"
        className={cn(
          "fixed inset-0 z-20 bg-black/40 backdrop-blur-xs transition-opacity duration-300 md:hidden",
          collapsed
            ? "opacity-0 pointer-events-none"
            : "opacity-100 pointer-events-auto",
        )}
        onClick={() => setCollapsed(true)}
        aria-label="关闭侧边栏"
      />

      {/* biome-ignore lint/a11y/noStaticElementInteractions: 页面级拖拽文件上传容器 */}
      <div
        className="relative flex h-full min-h-0 min-w-0 flex-1 flex-col bg-transparent"
        onDragEnter={handleDragEnter}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {/* 拖拽上传覆盖层 */}
        {isDraggingOver && (
          <div className="pointer-events-none absolute inset-0 z-50 flex flex-col items-center justify-center gap-3 bg-indigo-950/70 backdrop-blur-sm border-2 border-dashed border-indigo-400 text-white animate-in fade-in duration-200">
            <div className="flex size-16 items-center justify-center rounded-2xl bg-indigo-600/90 shadow-xl ring-4 ring-indigo-400/40 animate-bounce">
              <UploadCloud className="size-8" />
            </div>
            <p className="text-base font-semibold">释放文件以添加附件</p>
            <p className="text-xs text-indigo-200">
              支持图片 (JPG, PNG, WebP) 与各类文本代码文件 (最大 10MB)
            </p>
          </div>
        )}

        {/* 顶部状态栏 Header */}
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-zinc-200/80 bg-white/80 px-4 backdrop-blur-md dark:border-zinc-800/80 dark:bg-zinc-950/80">
          <div className="flex items-center gap-2">
            {collapsed && (
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setCollapsed(false)}
                className="size-8 rounded-lg text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
                aria-label="展开侧边栏"
              >
                <PanelLeftOpen className="size-4" />
              </Button>
            )}

            {/* 模型切换器 */}
            <ModelSelector
              value={model}
              onChange={setModel}
              onCatalogChange={setCatalog}
            />

            {/* 🎭 智能体角色快速切换入口 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setPersonaMarketOpen(true)}
              className={cn(
                "h-8 gap-1.5 rounded-lg px-2 text-xs transition-colors cursor-pointer",
                selectedPersona
                  ? "bg-violet-100 dark:bg-violet-950/60 text-violet-700 dark:text-violet-300 font-medium"
                  : "text-zinc-500 hover:text-violet-600 dark:text-zinc-400 dark:hover:text-violet-400",
              )}
              title="打开智能体角色市场 (Persona Store)"
            >
              <span>{selectedPersona ? selectedPersona.avatar : "🎭"}</span>
              <span className="hidden sm:inline">
                {selectedPersona ? selectedPersona.name : "角色市场"}
              </span>
            </Button>

            {/* 紧凑型实时 Token 预算进度条 */}
            <TokenBudgetBar compact className="hidden sm:inline-flex" />
          </div>

          <div className="flex items-center gap-1.5 sm:gap-2">
            {/* 全盘搜索按钮 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setSearchOpen(true)}
              className="h-8 gap-1.5 rounded-lg px-2 text-xs text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              title="搜索全部对话历史 (⌘K / Ctrl+K)"
            >
              <Search className="size-3.5" />
              <span className="hidden sm:inline">搜索</span>
              <kbd className="hidden rounded bg-zinc-100 px-1 py-0.5 font-mono text-[10px] text-zinc-400 dark:bg-zinc-800 md:inline-block">
                ⌘K
              </kbd>
            </Button>

            {/* 性能大盘与延迟对比按钮 */}
            <Button
              variant="ghost"
              size="sm"
              aria-label="模型流式性能大盘"
              onClick={() => setShowPerformanceModal(true)}
              className="h-8 gap-1.5 rounded-lg px-2 text-xs text-zinc-500 hover:text-indigo-600 dark:text-zinc-400 dark:hover:text-indigo-400 cursor-pointer"
              title="查看各模型首字延迟 (P50/P90)、Token 生成速率与性能对比"
            >
              <BarChart2 className="size-3.5 text-indigo-500" />
              <span className="hidden sm:inline">性能大盘</span>
            </Button>

            {/* 结构化摘要与沉淀按钮 */}
            <Button
              variant="ghost"
              size="sm"
              disabled={messages.length === 0}
              onClick={() => setShowSummary(true)}
              className="h-8 gap-1.5 rounded-lg px-2 text-xs text-zinc-500 hover:text-indigo-600 dark:text-zinc-400 dark:hover:text-indigo-400"
              title="生成当前会话核心摘要并一键沉淀至知识库"
            >
              <Sparkles className="size-3.5 text-indigo-500" />
              <span className="hidden sm:inline">会话沉淀</span>
            </Button>

            {/* 跨会话上下文继承按钮 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setInheritanceSourceId(null);
                setShowInheritanceModal(true);
              }}
              className="h-8 gap-1.5 rounded-lg px-2 text-xs text-zinc-500 hover:text-indigo-600 dark:text-zinc-400 dark:hover:text-indigo-400"
              title="跨会话继承关键决策、代码片段、文件引用与待办"
            >
              <GitFork className="size-3.5 text-indigo-500" />
              <span className="hidden sm:inline">继承上下文</span>
            </Button>

            {/* 多 Agent 协同研讨与 DAG 任务调度入口 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                const lastUserMsg = [...messages]
                  .reverse()
                  .find((m) => m.role === "user");
                setMultiAgentGoal(lastUserMsg?.content || input || "");
                setMultiAgentModalOpen(true);
              }}
              className="h-8 gap-1.5 rounded-lg px-2 text-xs text-zinc-500 hover:text-indigo-600 dark:text-zinc-400 dark:hover:text-indigo-400 cursor-pointer"
              title="开启多 Agent 协同研讨与 DAG 任务调度工作台"
            >
              <Users className="size-3.5 text-indigo-500" />
              <span className="hidden sm:inline">多 Agent 协同</span>
            </Button>

            {/* 历史导出按钮 */}
            <Button
              variant="ghost"
              size="sm"
              aria-label="导出对话"
              disabled={messages.length === 0}
              onClick={() => setShowExport(true)}
              className="h-8 gap-1.5 rounded-lg px-2 text-xs text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              title="导出为 Markdown / JSON / TXT / 图片长图"
            >
              <Download className="size-3.5" />
              <span className="hidden sm:inline">导出</span>
            </Button>

            <ThemeToggle />
          </div>
        </header>

        {/* 离线降级提示条 */}
        {isOfflineFallback && (
          <div className="flex items-center gap-2 border-b border-amber-200 bg-amber-50 px-4 py-1.5 text-xs text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/50 dark:text-amber-300">
            <AlertTriangle className="size-3.5 shrink-0" />
            <span>
              无法连接后端服务器，已启用本地离线模式，历史记录仅保存在当前浏览器中。
            </span>
          </div>
        )}

        {/* 会话删除确认对话框 */}
        {deleteTarget && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-dialog-title"
          >
            <div className="w-full max-w-sm rounded-xl border border-zinc-200 bg-white p-4 shadow-xl dark:border-zinc-800 dark:bg-zinc-900">
              <h2
                id="delete-dialog-title"
                className="text-sm font-semibold text-zinc-900 dark:text-zinc-100"
              >
                删除会话
              </h2>
              <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
                确定删除该会话吗？此操作不可撤销，会话内的全部消息将被永久删除。
              </p>
              <div className="mt-4 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setDeleteTarget(null)}
                >
                  取消
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => {
                    const id = deleteTarget;
                    setDeleteTarget(null);
                    void deleteSession(id);
                  }}
                >
                  确认删除
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* 主消息列表区 */}
        <main
          className="flex min-h-0 flex-1 flex-col overflow-y-auto scroll-smooth scrollbar-hidden"
          aria-live="polite"
        >
          {/* 文档对话模式专属顶部挂载管理栏 */}
          <div className="mx-auto w-full max-w-3xl px-4 pt-3 pb-1">
            <DocumentChatBar
              enabled={documentChatEnabled}
              onToggleEnabled={setDocumentChatEnabled}
              conversationId={activeId || undefined}
              documents={docChatDocuments}
              selectedDocIds={selectedDocIds}
              onSelectDocIds={setSelectedDocIds}
              onDocumentsChange={() => void refreshDocChatDocs()}
            />
          </div>

          {/* 跨会话继承专属上下文卡片 Banner */}
          {activeSession?.inheritedContextJson && (
            <InheritedContextBanner
              inheritedContextJson={activeSession.inheritedContextJson}
              parentSessionId={activeSession.parentSessionId}
              onSelectSourceSession={(id) => void selectSession(id)}
            />
          )}

          {messages.length === 0 ? (
            <EmptyState onPickPrompt={(text) => void handleSend(text)} />
          ) : (
            <div className="mx-auto w-full max-w-3xl py-4">
              {messages.map((m, index) => {
                const isLive = m.id === liveIdRef.current && isStreaming;
                if (isLive) {
                  return (
                    <LiveMessageBubble
                      key={m.id}
                      message={m}
                      streamStore={streamStore}
                      conversationId={activeId || undefined}
                      onCitationClick={(cite) =>
                        setCitationDrawer({
                          open: true,
                          citations: m.citations || [cite],
                          activeCitationId: cite.citationId,
                        })
                      }
                    />
                  );
                }
                const isAssistant = m.role === "assistant";
                return (
                  <MessageBubble
                    key={m.id}
                    message={m}
                    conversationId={activeId || undefined}
                    onRegenerate={
                      isAssistant ? () => handleRegenerate(index) : undefined
                    }
                    onRegenerateWithModel={
                      isAssistant
                        ? (provider, model) =>
                            handleRegenerate(index, { provider, model })
                        : undefined
                    }
                    onEditAndResend={
                      !isAssistant
                        ? (newText) => handleEditAndResend(index, newText)
                        : undefined
                    }
                    onOpenCompare={(prompt) => {
                      setComparePrompt(prompt);
                      setCompareModalOpen(true);
                    }}
                    onCitationClick={(cite) =>
                      setCitationDrawer({
                        open: true,
                        citations: m.citations || [cite],
                        activeCitationId: cite.citationId,
                      })
                    }
                  />
                );
              })}
              <div ref={bottomRef} className="h-6" />
            </div>
          )}

          {streamError && (
            <div className="mx-auto w-full max-w-3xl py-4">
              <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
                <span aria-hidden className="mt-0.5 text-base leading-none">
                  ⚠️
                </span>
                <div className="flex-1">
                  <p className="font-medium">服务连接受阻</p>
                  <p className="mt-0.5 text-xs opacity-80">
                    {streamError.message || "请求失败，请稍后重试。"}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void handleRegenerate()}
                  className="shrink-0 rounded-lg border border-red-300 bg-white px-3 py-1 text-xs font-medium text-red-700 transition-colors hover:bg-red-100 dark:border-red-800 dark:bg-red-950 dark:text-red-200 dark:hover:bg-red-900"
                >
                  重试
                </button>
              </div>
            </div>
          )}
        </main>

        {/* 底部悬浮发光输入框 */}
        <div className="sticky bottom-0 z-10 bg-linear-to-t from-zinc-50 via-zinc-50/90 to-transparent pb-4 pt-2 dark:from-zinc-950 dark:via-zinc-950/90 px-4 sm:px-6">
          <div className="mx-auto flex w-full max-w-3xl flex-col gap-2">
            <RateLimitIndicator />
            <form
              onSubmit={handleSubmit}
              className="flex w-full flex-col gap-2 rounded-2xl border border-zinc-200/80 bg-white/90 p-3 shadow-2xl shadow-indigo-500/10 backdrop-blur-xl transition-all duration-200 focus-within:border-indigo-500/60 focus-within:ring-2 focus-within:ring-indigo-500/20 dark:border-zinc-800/80 dark:bg-zinc-900/90 dark:shadow-none"
            >
              {/* 隐藏的原生文件上传 Input */}
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleFileChange}
                accept="image/jpeg,image/png,image/webp,image/gif,text/*,.txt,.md,.json,.js,.ts,.tsx,.java,.py,.go,.rs"
                multiple
                className="hidden"
              />

              {/* 激活的智能体人设提示 Banner */}
              {selectedPersona && (
                <div className="flex items-center justify-between px-3 py-1.5 rounded-t-xl bg-violet-50/90 dark:bg-violet-950/40 border-b border-violet-200/80 dark:border-violet-800/60 text-xs animate-in fade-in">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="text-base select-none">
                      {selectedPersona.avatar}
                    </span>
                    <span className="font-bold text-violet-900 dark:text-violet-100 truncate">
                      {selectedPersona.name}
                    </span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-violet-200/70 dark:bg-violet-800/60 text-violet-800 dark:text-violet-200 font-mono">
                      {selectedPersona.category}
                    </span>
                    <span className="hidden sm:inline text-[11px] text-violet-600 dark:text-violet-300 truncate">
                      — {selectedPersona.description}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => setPersonaMarketOpen(true)}
                      className="text-[11px] text-violet-700 dark:text-violet-300 hover:underline font-medium"
                    >
                      更换
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedPersona(null);
                        toast.info("已退出角色人设，恢复默认 Copilot 模式");
                      }}
                      className="p-1 text-violet-500 hover:text-rose-500 rounded-md transition-colors"
                      title="重置角色"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              )}

              {/* 视觉快捷场景胶囊栏 */}
              {attachments.some((att) => att.type === "image") && (
                <div className="px-2 pt-1.5 pb-1 border-b border-zinc-100 dark:border-zinc-800/60 bg-zinc-50/40 dark:bg-zinc-900/40 rounded-t-xl">
                  <VisionScenarioPills
                    onSelect={(prompt) => {
                      setInput(prompt);
                      textareaRef.current?.focus();
                    }}
                  />
                </div>
              )}

              {/* 待发送附件预览栏 */}
              {attachments.length > 0 && (
                <div className="flex flex-wrap gap-2 px-1 pt-1 pb-1.5 border-b border-zinc-100 dark:border-zinc-800/60">
                  {attachments.map((att) => (
                    <div
                      key={att.id}
                      className="group relative flex items-center gap-2 rounded-xl border border-zinc-200/80 bg-zinc-50/80 p-1.5 dark:border-zinc-800/80 dark:bg-zinc-800/60 text-xs shadow-xs"
                    >
                      {att.type === "image" ? (
                        <div className="relative size-9 overflow-hidden rounded-lg">
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={att.url}
                            alt={att.name}
                            className="size-full object-cover"
                          />
                        </div>
                      ) : (
                        <FileText className="size-4 text-indigo-500 shrink-0" />
                      )}
                      <span className="max-w-[120px] truncate font-medium text-zinc-700 dark:text-zinc-300">
                        {att.name}
                      </span>
                      {att.size && (
                        <span className="text-[10px] text-zinc-400 font-mono">
                          {(att.size / 1024).toFixed(0)}KB
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={() => removeAttachment(att.id)}
                        className="flex size-4.5 items-center justify-center rounded-full bg-zinc-200/80 text-zinc-500 hover:bg-rose-500 hover:text-white dark:bg-zinc-700 dark:text-zinc-400 dark:hover:bg-rose-600 transition-colors"
                        title="移除附件"
                      >
                        <X className="size-3" />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {/* 文本输入区 */}
              <div className="flex items-end gap-2 px-1">
                <textarea
                  ref={textareaRef}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  onPaste={handlePaste}
                  placeholder={
                    imageMode
                      ? "输入图像生成提示词，例如：赛博朋克风格的雨夜未来城市街道..."
                      : documentChatEnabled
                        ? "向已挂载的专属文档提问（仅依据文档内容回答，附段落引用）..."
                        : "发送消息给 AI Copilot... (Shift + Enter 换行，支持拖入/粘贴图片与代码文件)"
                  }
                  rows={1}
                  disabled={isStreaming}
                  className="max-h-50 min-h-[38px] flex-1 resize-none bg-transparent py-2 text-sm leading-relaxed text-zinc-900 placeholder:text-zinc-400 focus:outline-hidden disabled:opacity-50 dark:text-zinc-100 dark:placeholder:text-zinc-500"
                />

                {/* 语音录入与状态按钮 */}
                <div className="flex shrink-0 items-center gap-1.5 pb-1">
                  <VoiceRecorderButton
                    recording={recorder.recording}
                    seconds={recorder.seconds}
                    disabled={recorder.unsupported}
                    onStart={() => void recorder.start()}
                    onStop={() => void handleVoiceStop()}
                  />

                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => fileInputRef.current?.click()}
                    className="size-8 rounded-xl text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
                    title="添加附件 (支持拖入图片与代码文件)"
                  >
                    <Paperclip className="size-4" />
                  </Button>

                  {isStreaming ? (
                    <Button
                      type="button"
                      variant="destructive"
                      size="icon"
                      onClick={stop}
                      aria-label="停止生成"
                      className="size-8 rounded-xl bg-rose-500 shadow-md shadow-rose-500/20 hover:bg-rose-600"
                      title="停止生成"
                    >
                      <Square className="size-3.5 fill-current" />
                    </Button>
                  ) : (
                    <Button
                      type="submit"
                      size="icon"
                      aria-label="发送"
                      disabled={
                        isOverBudget ||
                        (!input.trim() && attachments.length === 0)
                      }
                      className={cn(
                        "size-8 rounded-xl text-white shadow-md transition-all",
                        isOverBudget
                          ? "bg-rose-500/80 cursor-not-allowed opacity-60 hover:bg-rose-500/80"
                          : "bg-indigo-600 shadow-indigo-500/20 hover:bg-indigo-700 disabled:opacity-40 dark:bg-indigo-500 dark:hover:bg-indigo-600",
                      )}
                      title={
                        isOverBudget
                          ? "本月 Token 配额已耗尽，请前往成本中心调整"
                          : "发送消息"
                      }
                    >
                      <Send className="size-3.5" />
                    </Button>
                  )}
                </div>
              </div>

              {/* 输入框底部功能条 */}
              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 px-1 pt-2 dark:border-zinc-800/60">
                <div className="flex items-center gap-3">
                  {/* 图像生成模式开关 */}
                  <div className="flex items-center gap-1.5">
                    <Switch
                      id="image-mode"
                      checked={imageMode}
                      onCheckedChange={setImageMode}
                      className="scale-75"
                    />
                    <label
                      htmlFor="image-mode"
                      className="cursor-pointer text-xs font-medium text-zinc-500 dark:text-zinc-400"
                    >
                      生图模式
                    </label>
                  </div>

                  {/* Agent 工具开关 */}
                  <div className="flex items-center gap-1.5 border-l border-zinc-200 pl-3 dark:border-zinc-800">
                    <Switch
                      id="agent-mode"
                      checked={agentEnabled}
                      onCheckedChange={setAgentEnabled}
                      className="scale-75"
                    />
                    <label
                      htmlFor="agent-mode"
                      className="cursor-pointer text-xs font-medium text-zinc-500 dark:text-zinc-400"
                    >
                      Agent 模式
                    </label>
                  </div>
                </div>

                {/* 快捷清空草稿（二次确认） */}
                {messages.length > 0 &&
                  !isStreaming &&
                  (confirmClear ? (
                    <div className="flex items-center gap-2">
                      <span className="text-[11px] text-zinc-500 dark:text-zinc-400">
                        确认清空？
                      </span>
                      <button
                        type="button"
                        onClick={() => {
                          newSession();
                          setConfirmClear(false);
                        }}
                        className="rounded bg-rose-500 px-2 py-0.5 text-[11px] text-white hover:bg-rose-600"
                      >
                        确认清空
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmClear(false)}
                        className="text-[11px] text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300"
                      >
                        取消
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmClear(true)}
                      className="flex items-center gap-1 text-[11px] text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300 transition-colors"
                    >
                      <RotateCcw className="size-3" />
                      <span>清空</span>
                    </button>
                  ))}
              </div>
            </form>
          </div>
        </div>
      </div>

      {/* 对话历史全量导出弹窗 */}
      <ExportDialog
        open={showExport}
        onClose={() => setShowExport(false)}
        messages={messages}
        title={
          sessions.find((s) => s.id === activeId)?.title ?? "AI-Copilot-对话"
        }
      />

      {/* 原文引用对照抽屉 (Citation Drawer) */}
      <CitationViewerDrawer
        open={citationDrawer.open}
        onClose={() => setCitationDrawer((prev) => ({ ...prev, open: false }))}
        citations={citationDrawer.citations}
        activeCitationId={citationDrawer.activeCitationId}
        onSelectCitation={(id) =>
          setCitationDrawer((prev) => ({ ...prev, activeCitationId: id }))
        }
        conversationId={activeId || undefined}
      />

      {/* 历史消息全文检索弹窗 */}
      <SearchDialog
        open={searchOpen}
        onOpenChange={setSearchOpen}
        sessions={sessions}
        onSelectResult={(sessionId, messageId) =>
          void selectSession(sessionId, messageId)
        }
      />

      {/* 会话结构化摘要与知识沉淀弹窗 */}
      <ConversationSummaryModal
        isOpen={showSummary}
        onClose={() => setShowSummary(false)}
        sessionId={activeId || ""}
        provider={model?.provider}
        model={model?.model}
      />

      {/* 模型流式性能大盘与 P50/P90 延迟对比弹窗 */}
      <ModelPerformanceModal
        isOpen={showPerformanceModal}
        onClose={() => setShowPerformanceModal(false)}
        initialProvider={model?.provider}
        initialModel={model?.model}
      />

      {/* 跨会话上下文继承弹窗 */}
      <ContextInheritanceModal
        isOpen={showInheritanceModal}
        onClose={() => {
          setShowInheritanceModal(false);
          setInheritanceSourceId(null);
        }}
        sessions={sessions}
        currentSessionId={activeId}
        initialSourceSessionId={inheritanceSourceId}
        onSuccess={(resp) => {
          void mutateSessions();
          void selectSession(resp.targetSessionId);
        }}
      />

      {/* 多模型并排对比竞技场弹窗 */}
      <ModelCompareModal
        open={compareModalOpen}
        onClose={() => setCompareModalOpen(false)}
        initialPrompt={comparePrompt}
        conversationId={activeId || undefined}
        onAdopt={(adoptedContent, provider, adoptedModel) => {
          const newAssistantMsg: ChatMessage = {
            id: `msg-${crypto.randomUUID()}`,
            role: "assistant",
            content: adoptedContent,
          };
          setMessages((prev) => [...prev, newAssistantMsg]);
          toast.success(`已采纳来自 ${provider}/${adoptedModel} 的回答！`);
        }}
      />

      {/* 多 Agent 协同研讨与 DAG 任务调度工作台 */}
      <MultiAgentModal
        open={multiAgentModalOpen}
        onClose={() => setMultiAgentModalOpen(false)}
        initialGoal={multiAgentGoal}
        conversationId={activeId || undefined}
        onAdopt={(synthesisResult) => {
          const newAssistantMsg: ChatMessage = {
            id: `msg-${crypto.randomUUID()}`,
            role: "assistant",
            content: synthesisResult,
          };
          setMessages((prev) => [...prev, newAssistantMsg]);
          toast.success("已采纳多 Agent 综合汇总交付报告！");
        }}
      />

      {/* 🎭 智能体角色市场 (Persona Store) 弹窗 */}
      <PersonaMarketModal
        isOpen={personaMarketOpen}
        onClose={() => setPersonaMarketOpen(false)}
        selectedPersona={selectedPersona}
        onSelectPersona={(persona) => {
          setSelectedPersona(persona);
          if (persona) {
            toast.success(
              `已激活智能体角色: ${persona.avatar} ${persona.name}`,
            );
            if (persona.preferredProvider && persona.preferredModel) {
              setModel({
                provider: persona.preferredProvider,
                model: persona.preferredModel,
              });
            }
          }
        }}
      />
    </div>
  );
}
