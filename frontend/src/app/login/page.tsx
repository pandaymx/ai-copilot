"use client";

import {
  ArrowLeft,
  ArrowRight,
  Bot,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Lock,
  Shield,
  Sparkles,
  User,
  UserCheck,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { login, register } from "@/lib/auth-api";
import { cn } from "@/lib/utils";

export default function LoginPage() {
  const router = useRouter();
  const [tab, setTab] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("USER");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      toast.error("请输入用户名");
      return;
    }
    if (!password) {
      toast.error("请输入密码");
      return;
    }

    try {
      setSubmitting(true);
      if (tab === "login") {
        const pair = await login(username.trim(), password);
        toast.success(`欢迎回来，${pair.user.username} (${pair.user.role})`);
      } else {
        const pair = await register(username.trim(), password, role);
        toast.success(
          `注册成功，已自动登录为 ${pair.user.username} (${pair.user.role})`,
        );
      }
      router.push("/");
      router.refresh();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "认证操作失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleQuickFill = (u: string, p: string) => {
    setUsername(u);
    setPassword(p);
    setTab("login");
  };

  return (
    <div className="min-h-screen w-full flex flex-col justify-center items-center relative overflow-hidden bg-gradient-to-br from-indigo-50/70 via-white to-violet-50/70 dark:from-zinc-950 dark:via-zinc-900 dark:to-indigo-950/40 p-4">
      {/* 动态光晕背景效果 */}
      <div className="absolute top-1/4 left-1/4 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-indigo-400/20 dark:bg-indigo-600/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 translate-x-1/2 translate-y-1/2 w-96 h-96 bg-violet-400/20 dark:bg-violet-600/10 rounded-full blur-3xl pointer-events-none" />

      {/* 顶部返回链接 */}
      <div className="absolute top-6 left-6">
        <Link
          href="/"
          className="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/70 dark:bg-zinc-900/70 backdrop-blur-md border border-zinc-200/80 dark:border-zinc-800 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors shadow-2xs"
        >
          <ArrowLeft className="size-3.5" />
          <span>返回对话</span>
        </Link>
      </div>

      {/* 核心玻璃态登录卡片 */}
      <div className="w-full max-w-md rounded-3xl bg-white/80 dark:bg-zinc-900/80 backdrop-blur-xl border border-zinc-200/80 dark:border-zinc-800/80 shadow-2xl p-6 sm:p-8 z-10 space-y-6">
        {/* 品牌头部 */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center size-12 rounded-2xl bg-gradient-to-tr from-indigo-600 to-violet-600 text-white shadow-lg shadow-indigo-500/25 mb-1">
            <Bot className="size-6" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-white">
            AI Copilot
          </h1>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            企业级多模型智能协作平台 · RBAC 统一认证中心
          </p>
        </div>

        {/* 登录 / 注册 Tab 切换 */}
        <div className="grid grid-cols-2 p-1 rounded-2xl bg-zinc-100 dark:bg-zinc-800/70 border border-zinc-200/50 dark:border-zinc-700/50 text-xs font-semibold">
          <button
            type="button"
            onClick={() => setTab("login")}
            className={cn(
              "py-2 rounded-xl transition-all",
              tab === "login"
                ? "bg-white dark:bg-zinc-900 text-indigo-600 dark:text-indigo-400 shadow-xs"
                : "text-zinc-500 hover:text-zinc-900 dark:hover:text-white",
            )}
          >
            账号登录
          </button>
          <button
            type="button"
            onClick={() => setTab("register")}
            className={cn(
              "py-2 rounded-xl transition-all",
              tab === "register"
                ? "bg-white dark:bg-zinc-900 text-indigo-600 dark:text-indigo-400 shadow-xs"
                : "text-zinc-500 hover:text-zinc-900 dark:hover:text-white",
            )}
          >
            注册新账号
          </button>
        </div>

        {/* 表单主体 */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label
              htmlFor="auth-username"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              用户名
            </label>
            <div className="relative">
              <User className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
              <input
                id="auth-username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                required
                className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-zinc-200 dark:border-zinc-700/80 bg-white/70 dark:bg-zinc-800/60 text-xs text-zinc-900 dark:text-white placeholder:text-zinc-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/50"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label
              htmlFor="auth-password"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              密码
            </label>
            <div className="relative">
              <Lock className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
              <input
                id="auth-password"
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码（至少 6 位）"
                required
                className="w-full pl-9 pr-10 py-2.5 rounded-xl border border-zinc-200 dark:border-zinc-700/80 bg-white/70 dark:bg-zinc-800/60 text-xs text-zinc-900 dark:text-white placeholder:text-zinc-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/50"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 transition-colors"
              >
                {showPassword ? (
                  <EyeOff className="size-4" />
                ) : (
                  <Eye className="size-4" />
                )}
              </button>
            </div>
          </div>

          {tab === "register" && (
            <div className="space-y-1.5 animate-in fade-in">
              <span className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                申请角色权限
              </span>
              <div className="grid grid-cols-3 gap-2">
                {[
                  {
                    id: "USER",
                    name: "标准用户",
                    icon: UserCheck,
                    desc: "对话/知识库",
                  },
                  {
                    id: "ADMIN",
                    name: "超级管理",
                    icon: Shield,
                    desc: "全系统最高权限",
                  },
                  {
                    id: "GUEST",
                    name: "只读访客",
                    icon: Sparkles,
                    desc: "浏览与轻对话",
                  },
                ].map((r) => (
                  <button
                    key={r.id}
                    type="button"
                    onClick={() => setRole(r.id)}
                    className={cn(
                      "flex flex-col items-center gap-1 p-2 rounded-xl border text-center transition-all",
                      role === r.id
                        ? "border-indigo-500 bg-indigo-50/80 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400"
                        : "border-zinc-200 dark:border-zinc-800 bg-white/40 dark:bg-zinc-800/40 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-800",
                    )}
                  >
                    <r.icon className="size-4" />
                    <span className="text-[11px] font-semibold">{r.name}</span>
                    <span className="text-[9px] text-zinc-400 line-clamp-1">
                      {r.desc}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-700 hover:to-violet-700 text-white text-xs font-semibold shadow-md shadow-indigo-500/20 disabled:opacity-50 transition-all cursor-pointer"
          >
            {submitting ? (
              <>
                <Loader2 className="size-4 animate-spin" />
                <span>处理中...</span>
              </>
            ) : (
              <>
                <span>{tab === "login" ? "立即登录" : "注册并登录"}</span>
                <ArrowRight className="size-4" />
              </>
            )}
          </button>
        </form>

        {/* 预置演示账号快捷点击 */}
        <div className="pt-2 border-t border-zinc-100 dark:border-zinc-800 space-y-2">
          <div className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider text-center">
            一键体验预置账号
          </div>
          <div className="grid grid-cols-2 gap-2 text-xs">
            <button
              type="button"
              onClick={() => handleQuickFill("admin", "admin123")}
              className="flex items-center justify-between p-2 rounded-xl bg-zinc-50 dark:bg-zinc-800/50 hover:bg-indigo-50/80 dark:hover:bg-indigo-950/40 border border-zinc-200/60 dark:border-zinc-700/60 transition-colors text-left"
            >
              <div>
                <div className="font-semibold text-zinc-800 dark:text-zinc-200">
                  admin (管理员)
                </div>
                <div className="text-[10px] text-zinc-400 font-mono">
                  admin123
                </div>
              </div>
              <KeyRound className="size-3.5 text-indigo-500 shrink-0" />
            </button>

            <button
              type="button"
              onClick={() => handleQuickFill("user", "user123")}
              className="flex items-center justify-between p-2 rounded-xl bg-zinc-50 dark:bg-zinc-800/50 hover:bg-indigo-50/80 dark:hover:bg-indigo-950/40 border border-zinc-200/60 dark:border-zinc-700/60 transition-colors text-left"
            >
              <div>
                <div className="font-semibold text-zinc-800 dark:text-zinc-200">
                  user (普通用户)
                </div>
                <div className="text-[10px] text-zinc-400 font-mono">
                  user123
                </div>
              </div>
              <KeyRound className="size-3.5 text-violet-500 shrink-0" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
