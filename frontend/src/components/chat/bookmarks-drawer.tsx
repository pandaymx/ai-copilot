"use client";

import {
  Bookmark,
  BookmarkX,
  Copy,
  ExternalLink,
  Loader2,
  Search,
  Tag,
  Trash2,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  listBookmarks,
  type MessageBookmark,
  toggleBookmark,
} from "@/lib/bookmark-api";
import { cn } from "@/lib/utils";

interface BookmarksDrawerProps {
  open: boolean;
  onClose: () => void;
}

export function BookmarksDrawer({ open, onClose }: BookmarksDrawerProps) {
  const [bookmarks, setBookmarks] = useState<MessageBookmark[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [selectedTag, setSelectedTag] = useState<string | null>(null);

  const router = useRouter();

  const loadBookmarks = useCallback(async () => {
    try {
      setLoading(true);
      const data = await listBookmarks();
      setBookmarks(data);
    } catch {
      toast.error("加载收藏夹失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      void loadBookmarks();
    }
  }, [open, loadBookmarks]);

  // 所有标签聚合
  const allTags = useMemo(() => {
    const set = new Set<string>();
    for (const b of bookmarks) {
      if (b.tags) {
        for (const t of b.tags) set.add(t);
      }
    }
    return Array.from(set);
  }, [bookmarks]);

  // 过滤后的收藏列表
  const filtered = useMemo(() => {
    return bookmarks.filter((b) => {
      const matchSearch =
        !search.trim() ||
        b.content.toLowerCase().includes(search.toLowerCase().trim());
      const matchTag = !selectedTag || b.tags?.includes(selectedTag);
      return matchSearch && matchTag;
    });
  }, [bookmarks, search, selectedTag]);

  const handleRemove = async (b: MessageBookmark) => {
    try {
      await toggleBookmark(b.messageId, {
        sessionId: b.sessionId,
        role: b.role,
        content: b.content,
      });
      toast.success("已移出收藏夹");
      setBookmarks((prev) => prev.filter((item) => item.id !== b.id));
    } catch {
      toast.error("操作失败");
    }
  };

  const handleCopy = async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      toast.success("内容已复制");
    } catch {
      toast.error("复制失败");
    }
  };

  const handleJump = (sessionId: string) => {
    onClose();
    router.push(`/?sessionId=${encodeURIComponent(sessionId)}`);
  };

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in"
    >
      <button
        type="button"
        aria-label="关闭收藏夹"
        className="fixed inset-0 bg-transparent border-0 cursor-default w-full h-full"
        onClick={onClose}
      />
      <div className="relative w-full max-w-2xl rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden flex flex-col max-h-[85vh] animate-in zoom-in-95 z-10">
        {/* 头部标题与关闭 */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-100 dark:border-zinc-800/80">
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-amber-500/10 text-amber-600 flex items-center justify-center">
              <Bookmark className="size-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-zinc-900 dark:text-white">
                我的对话收藏夹
              </h3>
              <p className="text-[11px] text-zinc-400">
                已收藏 {bookmarks.length} 条重要消息与代码片段
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* 搜索与标签栏 */}
        <div className="p-4 border-b border-zinc-100 dark:border-zinc-800 space-y-2.5">
          <div className="flex items-center gap-2 rounded-xl bg-zinc-100 dark:bg-zinc-800/80 px-3 py-2 text-xs">
            <Search className="size-3.5 text-zinc-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索收藏的消息或代码..."
              className="w-full bg-transparent outline-hidden text-zinc-800 dark:text-zinc-200 placeholder:text-zinc-400"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch("")}
                className="text-zinc-400 hover:text-zinc-600"
              >
                <X className="size-3" />
              </button>
            )}
          </div>

          {allTags.length > 0 && (
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
              <button
                type="button"
                onClick={() => setSelectedTag(null)}
                className={cn(
                  "px-2.5 py-1 rounded-lg text-[11px] font-medium transition-colors shrink-0",
                  !selectedTag
                    ? "bg-amber-500 text-white"
                    : "bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200",
                )}
              >
                全部标签
              </button>
              {allTags.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  onClick={() =>
                    setSelectedTag(tag === selectedTag ? null : tag)
                  }
                  className={cn(
                    "flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-medium transition-colors shrink-0",
                    tag === selectedTag
                      ? "bg-amber-500 text-white"
                      : "bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200",
                  )}
                >
                  <Tag className="size-2.5" />
                  <span>{tag}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* 收藏列表 */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {loading ? (
            <div className="p-12 text-center text-zinc-400">
              <Loader2 className="size-5 animate-spin mx-auto mb-2 text-amber-500" />
              <p className="text-xs">加载收藏中...</p>
            </div>
          ) : filtered.length === 0 ? (
            <div className="p-12 text-center text-zinc-400 space-y-2">
              <BookmarkX className="size-8 mx-auto opacity-40 text-amber-500" />
              <p className="text-xs">未找到匹配的收藏记录</p>
            </div>
          ) : (
            filtered.map((b) => (
              <div
                key={b.id || b.messageId}
                className="p-4 rounded-2xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/80 dark:border-zinc-800/80 space-y-2.5 transition-all hover:border-amber-500/30"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="px-1.5 py-0.5 rounded font-mono text-[9px] font-bold uppercase bg-amber-500/10 text-amber-600 dark:text-amber-400">
                      {b.role}
                    </span>
                    <span className="text-[10px] text-zinc-400 font-mono">
                      {new Date(b.createdAt).toLocaleString()}
                    </span>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => void handleCopy(b.content)}
                      title="复制内容"
                      className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-200 dark:hover:bg-zinc-800 transition-colors"
                    >
                      <Copy className="size-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => handleJump(b.sessionId)}
                      title="定位会话"
                      className="p-1.5 rounded-lg text-zinc-400 hover:text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-950/40 transition-colors"
                    >
                      <ExternalLink className="size-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleRemove(b)}
                      title="取消收藏"
                      className="p-1.5 rounded-lg text-zinc-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition-colors"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  </div>
                </div>

                <div className="text-xs text-zinc-800 dark:text-zinc-200 whitespace-pre-wrap line-clamp-4 font-mono bg-white dark:bg-zinc-900 p-2.5 rounded-xl border border-zinc-200/50 dark:border-zinc-800/50">
                  {b.content}
                </div>

                {b.tags && b.tags.length > 0 && (
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {b.tags.map((tag) => (
                      <span
                        key={tag}
                        className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-500/10 text-amber-600 dark:text-amber-400 text-[10px]"
                      >
                        <Tag className="size-2.5" />
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
