"use client";

import {
  Activity,
  ArrowLeft,
  Clock,
  Copy,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Plus,
  RefreshCw,
  Send,
  Trash2,
  Webhook as WebhookIcon,
  X,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  createWebhook,
  deleteWebhook,
  listWebhookDeliveries,
  listWebhooks,
  testWebhook,
  updateWebhook,
  type WebhookCreateRequest,
  type WebhookDelivery,
  type WebhookSubscription,
  type WebhookTestResult,
} from "@/lib/webhook-api";

const EVENT_TYPES = [
  { id: "*", label: "全部系统事件 (*)", desc: "监听所有产生并发布的领域事件" },
  {
    id: "chat.completed",
    label: "对话完成 (chat.completed)",
    desc: "AI 成功生成完整对话后触发",
  },
  {
    id: "knowledge.updated",
    label: "知识库更新 (knowledge.updated)",
    desc: "上传或索引新知识文档后触发",
  },
  {
    id: "quota.warning",
    label: "配额告警 (quota.warning)",
    desc: "用户 Token 或限流额度预警时触发",
  },
  {
    id: "error.occurred",
    label: "错误异常 (error.occurred)",
    desc: "系统或工具执行发生严重异常时触发",
  },
];

export default function WebhookSettingsPage() {
  const [subscriptions, setSubscriptions] = useState<WebhookSubscription[]>([]);
  const [loading, setLoading] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [revealedSecrets, setRevealedSecrets] = useState<
    Record<string, boolean>
  >({});
  const [selectedSubForLogs, setSelectedSubForLogs] =
    useState<WebhookSubscription | null>(null);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [loadingLogs, setLoadingLogs] = useState(false);

  // 新建表单状态
  const [formName, setFormName] = useState("");
  const [formUrl, setFormUrl] = useState("");
  const [formEvent, setFormEvent] = useState("*");
  const [formSecret, setFormSecret] = useState("");
  const [creating, setCreating] = useState(false);

  const loadSubscriptions = useCallback(async () => {
    try {
      setLoading(true);
      const data = await listWebhooks();
      setSubscriptions(data);
    } catch {
      toast.error("加载 Webhook 订阅列表失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSubscriptions();
  }, [loadSubscriptions]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim() || !formUrl.trim()) return;

    try {
      setCreating(true);
      const req: WebhookCreateRequest = {
        name: formName.trim(),
        url: formUrl.trim(),
        eventType: formEvent,
        secret: formSecret.trim() || undefined,
      };
      await createWebhook(req);
      toast.success("Webhook 订阅创建成功");
      setShowCreateModal(false);
      setFormName("");
      setFormUrl("");
      setFormSecret("");
      void loadSubscriptions();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const handleToggleEnable = async (sub: WebhookSubscription) => {
    try {
      await updateWebhook(sub.id, { enabled: !sub.enabled });
      toast.success(sub.enabled ? "已禁用该 Webhook" : "已启用该 Webhook");
      void loadSubscriptions();
    } catch {
      toast.error("更新状态失败");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("确定要删除该 Webhook 订阅吗？相关的投递历史也将一并清除。"))
      return;
    try {
      await deleteWebhook(id);
      toast.success("已删除该 Webhook 订阅");
      void loadSubscriptions();
    } catch {
      toast.error("删除失败");
    }
  };

  const handleTest = async (id: string) => {
    try {
      setTestingId(id);
      const res: WebhookTestResult = await testWebhook(id);
      if (res.success) {
        toast.success(
          `测试发送成功 (HTTP ${res.statusCode}, 耗时 ${res.durationMs}ms)`,
        );
      } else {
        toast.error(
          `测试推送失败 (HTTP ${res.statusCode || 500}): ${res.message || "请求超时"}`,
        );
      }
      void loadSubscriptions();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "测试发送异常");
    } finally {
      setTestingId(null);
    }
  };

  const handleOpenLogs = async (sub: WebhookSubscription) => {
    setSelectedSubForLogs(sub);
    try {
      setLoadingLogs(true);
      const logs = await listWebhookDeliveries(sub.id);
      setDeliveries(logs);
    } catch {
      toast.error("获取投递日志失败");
    } finally {
      setLoadingLogs(false);
    }
  };

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${label}已复制`);
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 flex flex-col">
      {/* 顶部导航栏 */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 bg-white/80 dark:bg-zinc-900/80 px-4 sm:px-6 py-3.5 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          >
            <ArrowLeft className="size-3.5" />
            <span>返回对话</span>
          </Link>
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center">
              <WebhookIcon className="size-4" />
            </div>
            <div>
              <h1 className="text-sm sm:text-base font-bold">
                Webhook 与系统事件通知
              </h1>
              <p className="text-[11px] text-zinc-400">
                将对话生成、知识库更新、配额告警等系统事件推送至外部系统并带
                HMAC-SHA256 签名
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => void loadSubscriptions()}
            disabled={loading}
            className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors disabled:opacity-50"
          >
            <RefreshCw className={cn("size-3.5", loading && "animate-spin")} />
            <span>刷新</span>
          </button>
          <button
            type="button"
            onClick={() => setShowCreateModal(true)}
            className="flex items-center gap-1.5 rounded-xl bg-purple-600 hover:bg-purple-700 px-3.5 py-1.5 text-xs font-semibold text-white shadow-xs transition-colors"
          >
            <Plus className="size-3.5" />
            <span>新建 Webhook</span>
          </button>
        </div>
      </header>

      {/* 主体展示区 */}
      <main className="flex-1 max-w-5xl w-full mx-auto p-4 sm:p-6 space-y-6">
        {/* 概览说明卡片 */}
        <div className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-2xs">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-xs font-bold text-zinc-900 dark:text-white">
              <Activity className="size-4 text-purple-500" />
              <span>异步事件总线 (Event-Driven Gateway)</span>
            </div>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
              每次事件推送均包含标准 HTTP 请求头{" "}
              <code className="font-mono text-purple-600 dark:text-purple-400">
                X-Webhook-Signature
              </code>{" "}
              与{" "}
              <code className="font-mono text-purple-600 dark:text-purple-400">
                X-Webhook-Timestamp
              </code>
              ，防止重放与伪造攻击。
            </p>
          </div>
          <div className="flex items-center gap-4 shrink-0 text-xs font-semibold">
            <div className="text-right">
              <div className="text-sm font-bold text-purple-600 dark:text-purple-400">
                {subscriptions.length}
              </div>
              <div className="text-[10px] text-zinc-400">已配置订阅</div>
            </div>
          </div>
        </div>

        {/* 订阅列表 */}
        {loading && subscriptions.length === 0 ? (
          <div className="p-12 text-center text-zinc-400">
            <Loader2 className="size-6 animate-spin mx-auto mb-2 text-purple-600" />
            <p className="text-xs">加载订阅配置中...</p>
          </div>
        ) : subscriptions.length === 0 ? (
          <div className="p-12 text-center rounded-3xl border border-dashed border-zinc-200 dark:border-zinc-800 bg-white/50 dark:bg-zinc-900/50 space-y-3">
            <div className="size-12 rounded-2xl bg-purple-500/10 text-purple-600 flex items-center justify-center mx-auto">
              <WebhookIcon className="size-6" />
            </div>
            <h3 className="text-sm font-bold text-zinc-900 dark:text-white">
              暂无已配置的 Webhook
            </h3>
            <p className="text-xs text-zinc-400 max-w-sm mx-auto">
              点击上方「新建
              Webhook」按钮，将系统事件无缝推送至飞书、企微、钉钉或自建服务。
            </p>
          </div>
        ) : (
          <div className="space-y-3.5">
            {subscriptions.map((sub) => {
              const isRevealed = revealedSecrets[sub.id];
              const isTesting = testingId === sub.id;

              return (
                <div
                  key={sub.id}
                  className="rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 p-4 sm:p-5 space-y-3.5 shadow-2xs transition-all hover:border-purple-500/30"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div
                        className={cn(
                          "size-8 rounded-xl flex items-center justify-center font-bold text-xs shrink-0",
                          sub.enabled
                            ? "bg-purple-500/10 text-purple-600 dark:text-purple-400"
                            : "bg-zinc-100 text-zinc-400 dark:bg-zinc-800",
                        )}
                      >
                        <WebhookIcon className="size-4" />
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="text-xs sm:text-sm font-bold truncate">
                            {sub.name}
                          </h3>
                          <span className="px-2 py-0.5 rounded-md bg-purple-50 dark:bg-purple-950/60 text-purple-600 dark:text-purple-400 font-mono text-[10px] font-semibold">
                            {sub.eventType}
                          </span>
                          <span
                            className={cn(
                              "px-2 py-0.5 rounded-md font-mono text-[10px] font-semibold",
                              sub.enabled
                                ? "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-400"
                                : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800",
                            )}
                          >
                            {sub.enabled ? "已启用" : "已禁用"}
                          </span>
                          {sub.lastStatus && (
                            <span
                              className={cn(
                                "px-1.5 py-0.5 rounded text-[9px] font-mono",
                                sub.lastStatus === "SUCCESS"
                                  ? "text-emerald-500 bg-emerald-500/10"
                                  : "text-rose-500 bg-rose-500/10",
                              )}
                            >
                              最近投递: {sub.lastStatus}
                            </span>
                          )}
                        </div>
                        <div className="text-[11px] font-mono text-zinc-500 dark:text-zinc-400 truncate mt-0.5">
                          {sub.url}
                        </div>
                      </div>
                    </div>

                    {/* 操作按钮组 */}
                    <div className="flex items-center gap-1.5 self-end sm:self-auto shrink-0">
                      <button
                        type="button"
                        onClick={() => void handleTest(sub.id)}
                        disabled={isTesting}
                        className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xs font-medium text-zinc-700 dark:text-zinc-300 transition-colors disabled:opacity-50"
                      >
                        {isTesting ? (
                          <Loader2 className="size-3.5 animate-spin" />
                        ) : (
                          <Send className="size-3.5" />
                        )}
                        <span>测试</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleOpenLogs(sub)}
                        className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xs font-medium text-zinc-700 dark:text-zinc-300 transition-colors"
                      >
                        <Clock className="size-3.5" />
                        <span>日志</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleToggleEnable(sub)}
                        className="px-2.5 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xs font-medium text-zinc-700 dark:text-zinc-300 transition-colors"
                      >
                        {sub.enabled ? "禁用" : "启用"}
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleDelete(sub.id)}
                        className="p-1.5 rounded-xl hover:bg-rose-50 dark:hover:bg-rose-950/50 text-zinc-400 hover:text-rose-600 transition-colors"
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Secret 栏 */}
                  <div className="flex items-center justify-between p-2 rounded-xl bg-zinc-50 dark:bg-zinc-950 text-xs border border-zinc-200/60 dark:border-zinc-800/60">
                    <div className="flex items-center gap-2 font-mono text-[11px] text-zinc-500 truncate">
                      <KeyRound className="size-3 text-purple-500 shrink-0" />
                      <span className="text-zinc-400">Secret:</span>
                      <span className="font-semibold text-zinc-700 dark:text-zinc-300 truncate">
                        {isRevealed ? sub.secret : "••••••••••••••••••••••••"}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() =>
                          setRevealedSecrets({
                            ...revealedSecrets,
                            [sub.id]: !isRevealed,
                          })
                        }
                        className="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 transition-colors"
                      >
                        {isRevealed ? (
                          <EyeOff className="size-3" />
                        ) : (
                          <Eye className="size-3" />
                        )}
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          void copyToClipboard(sub.secret, "签名 Secret ")
                        }
                        className="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 transition-colors"
                      >
                        <Copy className="size-3" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </main>

      {/* 新建 Webhook 弹窗 */}
      {showCreateModal && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in"
        >
          <div className="w-full max-w-lg rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl p-6 space-y-4 animate-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800/80 pb-3">
              <div className="flex items-center gap-2">
                <div className="size-8 rounded-xl bg-purple-500/10 text-purple-600 flex items-center justify-center">
                  <WebhookIcon className="size-4" />
                </div>
                <h3 className="text-sm font-bold text-zinc-900 dark:text-white">
                  新建 Webhook 订阅
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
              >
                <X className="size-4" />
              </button>
            </div>

            <form
              onSubmit={(e) => void handleCreate(e)}
              className="space-y-3.5"
            >
              <div className="space-y-1">
                <label
                  htmlFor="sub-name-input"
                  className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  订阅名称
                </label>
                <input
                  id="sub-name-input"
                  type="text"
                  required
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  placeholder="如：飞书群告警机器人"
                  className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white/70 dark:bg-zinc-800/60 text-xs focus:ring-2 focus:ring-purple-500/50"
                />
              </div>

              <div className="space-y-1">
                <label
                  htmlFor="sub-url-input"
                  className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  目标 Webhook URL (POST)
                </label>
                <input
                  id="sub-url-input"
                  type="url"
                  required
                  value={formUrl}
                  onChange={(e) => setFormUrl(e.target.value)}
                  placeholder="https://your-domain.com/api/webhooks"
                  className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white/70 dark:bg-zinc-800/60 text-xs font-mono focus:ring-2 focus:ring-purple-500/50"
                />
              </div>

              <div className="space-y-1">
                <label
                  htmlFor="sub-event-select"
                  className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  监听事件类型
                </label>
                <select
                  id="sub-event-select"
                  value={formEvent}
                  onChange={(e) => setFormEvent(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white/70 dark:bg-zinc-800/60 text-xs focus:ring-2 focus:ring-purple-500/50"
                >
                  {EVENT_TYPES.map((ev) => (
                    <option key={ev.id} value={ev.id}>
                      {ev.label} - {ev.desc}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1">
                <label
                  htmlFor="sub-secret-input"
                  className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
                >
                  自定义签名 Secret (可选)
                </label>
                <input
                  id="sub-secret-input"
                  type="text"
                  value={formSecret}
                  onChange={(e) => setFormSecret(e.target.value)}
                  placeholder="留空则系统自动随机生成强签名密钥"
                  className="w-full px-3 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white/70 dark:bg-zinc-800/60 text-xs font-mono focus:ring-2 focus:ring-purple-500/50"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-3.5 py-2 rounded-xl border border-zinc-200 dark:border-zinc-700 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-xs font-semibold text-white shadow-xs disabled:opacity-50"
                >
                  {creating && <Loader2 className="size-3.5 animate-spin" />}
                  <span>确认创建</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 投递日志抽屉/弹窗 */}
      {selectedSubForLogs && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in"
        >
          <div className="w-full max-w-2xl rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl p-6 space-y-4 max-h-[85vh] flex flex-col animate-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-zinc-100 dark:border-zinc-800/80 pb-3">
              <div className="flex items-center gap-2">
                <Clock className="size-4 text-purple-600" />
                <h3 className="text-sm font-bold text-zinc-900 dark:text-white">
                  投递日志: {selectedSubForLogs.name}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedSubForLogs(null)}
                className="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2.5 pr-1">
              {loadingLogs ? (
                <div className="p-8 text-center text-zinc-400">
                  <Loader2 className="size-5 animate-spin mx-auto mb-2 text-purple-600" />
                  <p className="text-xs">加载日志中...</p>
                </div>
              ) : deliveries.length === 0 ? (
                <p className="text-center text-xs text-zinc-400 py-8">
                  暂无历史投递记录
                </p>
              ) : (
                deliveries.map((del) => (
                  <div
                    key={del.id}
                    className="p-3 rounded-2xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/70 dark:border-zinc-800/70 space-y-1.5 text-xs font-mono"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span
                          className={cn(
                            "px-1.5 py-0.5 rounded text-[10px] font-bold",
                            del.success
                              ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
                              : "bg-rose-500/15 text-rose-600 dark:text-rose-400",
                          )}
                        >
                          HTTP {del.responseStatus}
                        </span>
                        <span className="text-zinc-700 dark:text-zinc-300 font-semibold">
                          {del.eventType}
                        </span>
                      </div>
                      <span className="text-[10px] text-zinc-400">
                        {new Date(del.createdAt).toLocaleTimeString()} (
                        {del.durationMs}ms)
                      </span>
                    </div>

                    {del.responseBody && (
                      <div className="text-[11px] text-zinc-500 dark:text-zinc-400 truncate">
                        响应: {del.responseBody}
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
