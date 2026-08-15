"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import type { AttachmentItem } from "@/components/chat/message-bubble";
import { useVoiceRecorder } from "@/hooks/useVoiceRecorder";
import { type DocChatDocItem, fetchDocChatDocumentsApi } from "@/lib/api";
import { compressImage } from "@/lib/image-compressor";
import { transcribe } from "@/lib/voice";

export interface UseChatInputOptions {
  currentSupportsVision: boolean;
  activeId: string | null;
}

export interface UseChatInputResult {
  input: string;
  setInput: React.Dispatch<React.SetStateAction<string>>;
  attachments: AttachmentItem[];
  setAttachments: React.Dispatch<React.SetStateAction<AttachmentItem[]>>;
  imageMode: boolean;
  setImageMode: React.Dispatch<React.SetStateAction<boolean>>;
  agentEnabled: boolean;
  setAgentEnabled: React.Dispatch<React.SetStateAction<boolean>>;
  documentChatEnabled: boolean;
  setDocumentChatEnabled: React.Dispatch<React.SetStateAction<boolean>>;
  docChatDocuments: DocChatDocItem[];
  setDocChatDocuments: React.Dispatch<React.SetStateAction<DocChatDocItem[]>>;
  selectedDocIds: string[];
  setSelectedDocIds: React.Dispatch<React.SetStateAction<string[]>>;
  refreshDocChatDocs: () => Promise<void>;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
  recorder: ReturnType<typeof useVoiceRecorder>;
  handleVoiceStop: () => Promise<void>;
  processFiles: (files: FileList | File[]) => Promise<void>;
  handleFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  removeAttachment: (id: string) => void;
  handlePaste: (e: React.ClipboardEvent<HTMLTextAreaElement>) => void;
  isDraggingOver: boolean;
  handleDragEnter: (e: React.DragEvent) => void;
  handleDragOver: (e: React.DragEvent) => void;
  handleDragLeave: (e: React.DragEvent) => void;
  handleDrop: (e: React.DragEvent) => void;
}

export function useChatInput({
  currentSupportsVision,
  activeId,
}: UseChatInputOptions): UseChatInputResult {
  const [input, setInput] = useState("");
  const [attachments, setAttachments] = useState<AttachmentItem[]>([]);
  const [imageMode, setImageMode] = useState(false);
  const [agentEnabled, setAgentEnabled] = useState(false);

  // 文档对话模式状态
  const [documentChatEnabled, setDocumentChatEnabled] = useState(false);
  const [docChatDocuments, setDocChatDocuments] = useState<DocChatDocItem[]>(
    [],
  );
  const [selectedDocIds, setSelectedDocIds] = useState<string[]>([]);

  const [isDraggingOver, setIsDraggingOver] = useState(false);
  const dragCounterRef = useRef<number>(0);
  const lastPastedRef = useRef<{ time: number; key: string }>({
    time: 0,
    key: "",
  });

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  // 刷新当前会话挂载文档列表
  const refreshDocChatDocs = useCallback(async () => {
    if (!activeId) {
      setDocChatDocuments([]);
      return;
    }
    try {
      const docs = await fetchDocChatDocumentsApi(activeId);
      setDocChatDocuments(docs);
      if (docs && docs.length > 0) {
        setDocumentChatEnabled(true);
      }
    } catch {
      setDocChatDocuments([]);
    }
  }, [activeId]);

  useEffect(() => {
    void refreshDocChatDocs();
  }, [refreshDocChatDocs]);

  // 自适应输入框高度
  // biome-ignore lint/correctness/useExhaustiveDependencies: 高度随 input 重新计算
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  }, [input]);

  // 语音录制与转写
  const recorder = useVoiceRecorder();
  const handleVoiceStop = useCallback(async () => {
    const result = await recorder.stop();
    if (!result) return;
    try {
      const text = await transcribe(result.base64, result.mimeType);
      if (text) setInput((prev) => (prev ? `${prev} ${text}` : text).trim());
    } catch (err) {
      console.error("语音识别失败:", err);
    }
  }, [recorder]);

  // 处理选择或拖拽的文件
  const processFiles = useCallback(
    async (files: FileList | File[]) => {
      const fileList = Array.from(files);
      if (fileList.length === 0) return;

      const newAttachments: AttachmentItem[] = [];
      for (const file of fileList) {
        if (file.size > 10 * 1024 * 1024) {
          toast.error(`文件 "${file.name}" 超过 10MB 限制`);
          continue;
        }

        if (file.type.startsWith("image/")) {
          if (!currentSupportsVision) {
            toast.error(
              "当前模型不支持图片，请切换到支持图片的模型 (如 GPT-4o, Gemini 等)",
            );
            continue;
          }
          try {
            const compressed = await compressImage(file);
            newAttachments.push({
              id: `att-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
              name: compressed.name,
              type: "image",
              mimeType: compressed.mimeType,
              url: compressed.dataUrl,
              size: compressed.size,
            });
          } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "图片处理失败");
          }
        } else {
          // 非图片文件：读取文本内容，存储为 AttachmentItem
          const textContent = await file.text();
          newAttachments.push({
            id: `att-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
            name: file.name,
            type: "file",
            mimeType: file.type || "text/plain",
            url: "",
            size: file.size,
            textContent,
          });
        }
      }

      if (newAttachments.length > 0) {
        setAttachments((prev) => [...prev, ...newAttachments].slice(0, 4));
      }
    },
    [currentSupportsVision],
  );

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      void processFiles(e.target.files);
      e.target.value = "";
    }
  };

  const removeAttachment = (id: string) => {
    setAttachments((prev) => prev.filter((a) => a.id !== id));
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (e.clipboardData.files && e.clipboardData.files.length > 0) {
      const files = Array.from(e.clipboardData.files);
      const pasteKey = files.map((f) => `${f.name}-${f.size}`).join(",");
      const now = Date.now();
      if (
        now - lastPastedRef.current.time < 500 &&
        lastPastedRef.current.key === pasteKey
      ) {
        e.preventDefault();
        return;
      }
      lastPastedRef.current = { time: now, key: pasteKey };

      const imageFiles = files.filter((f) => f.type.startsWith("image/"));
      if (imageFiles.length > 0) {
        e.preventDefault();
        if (!currentSupportsVision) {
          toast.error(
            "当前模型不支持图片，请切换到支持图片的模型 (如 GPT-4o, Gemini 等)",
          );
          return;
        }
        void processFiles(imageFiles);
      }
    }
  };

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current += 1;
    if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
      setIsDraggingOver(true);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current -= 1;
    if (dragCounterRef.current <= 0) {
      dragCounterRef.current = 0;
      setIsDraggingOver(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current = 0;
    setIsDraggingOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      void processFiles(e.dataTransfer.files);
    }
  };

  return {
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
    setDocChatDocuments,
    selectedDocIds,
    setSelectedDocIds,
    refreshDocChatDocs,
    fileInputRef,
    textareaRef,
    recorder,
    handleVoiceStop,
    processFiles,
    handleFileChange,
    removeAttachment,
    handlePaste,
    isDraggingOver,
    handleDragEnter,
    handleDragOver,
    handleDragLeave,
    handleDrop,
  };
}
