"use client";

import {
  ArrowLeft,
  Check,
  Loader2,
  RefreshCw,
  Search,
  Shield,
  ShieldCheck,
  Users,
  X,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  listAllUsers,
  type UserAdminItem,
  updateUserRole,
  updateUserStatus,
} from "@/lib/auth-api";
import { cn } from "@/lib/utils";

const ROLE_DEFINITIONS: Record<
  string,
  { label: string; color: string; desc: string; permissions: string[] }
> = {
  ADMIN: {
    label: "超级管理员",
    color:
      "bg-purple-100 dark:bg-purple-950/60 text-purple-700 dark:text-purple-300 border-purple-200 dark:border-purple-800",
    desc: "拥有系统全量控制权限",
    permissions: [
      "chat:create",
      "chat:delete",
      "knowledge:read",
      "knowledge:write",
      "tool:use",
      "admin:manage_users",
      "admin:api_keys",
    ],
  },
  USER: {
    label: "标准用户",
    color:
      "bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 border-blue-200 dark:border-blue-800",
    desc: "拥有对话、知识库读写与工具调用权限",
    permissions: [
      "chat:create",
      "chat:delete",
      "knowledge:read",
      "knowledge:write",
      "tool:use",
    ],
  },
  GUEST: {
    label: "只读访客",
    color:
      "bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 border-zinc-200 dark:border-zinc-700",
    desc: "仅支持浏览与只读对话",
    permissions: ["chat:read", "knowledge:read"],
  },
};

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserAdminItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("ALL");

  // 修改角色弹窗状态
  const [editingUser, setEditingUser] = useState<UserAdminItem | null>(null);
  const [selectedRole, setSelectedRole] = useState("USER");
  const [updating, setUpdating] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const data = await listAllUsers();
      setUsers(data);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "加载用户列表失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const matchSearch =
        u.username.toLowerCase().includes(search.toLowerCase()) ||
        u.id.toLowerCase().includes(search.toLowerCase());
      const matchRole = roleFilter === "ALL" || u.role === roleFilter;
      return matchSearch && matchRole;
    });
  }, [users, search, roleFilter]);

  const handleSaveRole = async () => {
    if (!editingUser) return;
    try {
      setUpdating(true);
      await updateUserRole(editingUser.id, selectedRole);
      toast.success(
        `已将用户 ${editingUser.username} 的角色更改为 ${selectedRole}`,
      );
      setEditingUser(null);
      await loadData();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "修改角色失败");
    } finally {
      setUpdating(false);
    }
  };

  const handleToggleStatus = async (user: UserAdminItem) => {
    const nextStatus = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await updateUserStatus(user.id, nextStatus);
      toast.success(
        `用户 ${user.username} 状态已更新为: ${nextStatus === "ACTIVE" ? "启用" : "禁用"}`,
      );
      await loadData();
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "修改状态失败");
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 flex flex-col">
      {/* 顶部导航 */}
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
              <Users className="size-4" />
            </div>
            <div>
              <h1 className="text-sm sm:text-base font-bold">
                RBAC 用户与权限管理中心
              </h1>
              <p className="text-[11px] text-zinc-400">
                管理系统注册用户、分配角色等级与精细化权限矩阵
              </p>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => void loadData()}
          disabled={loading}
          className="flex items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={cn("size-3.5", loading && "animate-spin")} />
          <span>刷新</span>
        </button>
      </header>

      {/* 核心内容区 */}
      <main className="flex-1 max-w-6xl w-full mx-auto p-4 sm:p-6 space-y-6">
        {/* 权限模型概要说明 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5">
          {Object.entries(ROLE_DEFINITIONS).map(([key, def]) => (
            <div
              key={key}
              className="p-4 rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 space-y-2 shadow-2xs"
            >
              <div className="flex items-center justify-between">
                <span
                  className={cn(
                    "px-2.5 py-0.5 rounded-lg text-xs font-bold border",
                    def.color,
                  )}
                >
                  {def.label} ({key})
                </span>
                <span className="text-[10px] text-zinc-400">
                  {def.permissions.length} 项权限
                </span>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                {def.desc}
              </p>
              <div className="flex flex-wrap gap-1 pt-1">
                {def.permissions.map((p) => (
                  <span
                    key={p}
                    className="px-1.5 py-0.5 rounded-md bg-zinc-100 dark:bg-zinc-800 text-[10px] font-mono text-zinc-600 dark:text-zinc-400"
                  >
                    {p}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>

        {/* 搜索与过滤工具栏 */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-white dark:bg-zinc-900 p-3 rounded-2xl border border-zinc-200/80 dark:border-zinc-800 shadow-2xs">
          <div className="relative w-full sm:w-72">
            <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索用户名或用户 ID..."
              className="w-full pl-9 pr-3 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700/80 bg-zinc-50/50 dark:bg-zinc-800/50 text-xs text-zinc-900 dark:text-white placeholder:text-zinc-400 focus:outline-hidden focus:ring-2 focus:ring-purple-500/50"
            />
          </div>

          <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto">
            {["ALL", "ADMIN", "USER", "GUEST"].map((rf) => (
              <button
                key={rf}
                type="button"
                onClick={() => setRoleFilter(rf)}
                className={cn(
                  "px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-colors",
                  roleFilter === rf
                    ? "bg-purple-600 text-white shadow-xs"
                    : "text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800",
                )}
              >
                {rf === "ALL" ? "全部角色" : rf}
              </button>
            ))}
          </div>
        </div>

        {/* 用户列表表格 */}
        <div className="rounded-2xl bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 shadow-2xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/60 border-b border-zinc-200/80 dark:border-zinc-800 text-zinc-500 font-semibold">
                <tr>
                  <th className="px-4 py-3">用户</th>
                  <th className="px-4 py-3">用户 ID</th>
                  <th className="px-4 py-3">系统角色</th>
                  <th className="px-4 py-3">账号状态</th>
                  <th className="px-4 py-3">注册时间</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/80">
                {filteredUsers.length === 0 ? (
                  <tr>
                    <td
                      colSpan={6}
                      className="px-4 py-8 text-center text-zinc-400"
                    >
                      {loading ? (
                        <div className="flex items-center justify-center gap-2">
                          <Loader2 className="size-4 animate-spin text-purple-500" />
                          <span>正在加载用户数据...</span>
                        </div>
                      ) : (
                        "未找到匹配的用户"
                      )}
                    </td>
                  </tr>
                ) : (
                  filteredUsers.map((u) => {
                    const roleInfo =
                      ROLE_DEFINITIONS[u.role] || ROLE_DEFINITIONS.USER;
                    const isActive = u.status === "ACTIVE";

                    return (
                      <tr
                        key={u.id}
                        className="hover:bg-zinc-50/80 dark:hover:bg-zinc-800/40 transition-colors"
                      >
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2.5">
                            <div className="size-8 rounded-full bg-gradient-to-tr from-purple-500 to-indigo-500 text-white flex items-center justify-center font-bold text-xs shadow-xs">
                              {u.username.charAt(0).toUpperCase()}
                            </div>
                            <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                              {u.username}
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-3 font-mono text-zinc-400 text-[11px]">
                          {u.id}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              "px-2.5 py-0.5 rounded-lg text-[11px] font-semibold border inline-flex items-center gap-1",
                              roleInfo.color,
                            )}
                          >
                            <Shield className="size-3" />
                            <span>{roleInfo.label}</span>
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              "px-2 py-0.5 rounded-full text-[10px] font-semibold inline-flex items-center gap-1",
                              isActive
                                ? "bg-emerald-100 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300"
                                : "bg-rose-100 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300",
                            )}
                          >
                            <span
                              className={cn(
                                "size-1.5 rounded-full",
                                isActive ? "bg-emerald-500" : "bg-rose-500",
                              )}
                            />
                            <span>{isActive ? "正常" : "已禁用"}</span>
                          </span>
                        </td>
                        <td className="px-4 py-3 text-zinc-400 text-[11px]">
                          {new Date(u.createdAt).toLocaleDateString("zh-CN")}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="inline-flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={() => {
                                setEditingUser(u);
                                setSelectedRole(u.role);
                              }}
                              className="px-2.5 py-1 rounded-lg bg-zinc-100 dark:bg-zinc-800 hover:bg-purple-50 dark:hover:bg-purple-950/60 text-zinc-700 dark:text-zinc-300 hover:text-purple-600 text-[11px] font-medium transition-colors"
                            >
                              变更角色
                            </button>
                            <button
                              type="button"
                              onClick={() => void handleToggleStatus(u)}
                              className={cn(
                                "px-2.5 py-1 rounded-lg text-[11px] font-medium transition-colors",
                                isActive
                                  ? "bg-rose-50 dark:bg-rose-950/40 text-rose-600 hover:bg-rose-100"
                                  : "bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 hover:bg-emerald-100",
                              )}
                            >
                              {isActive ? "禁用" : "启用"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </main>

      {/* 角色变更弹窗 */}
      {editingUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in">
          <div className="w-full max-w-md rounded-3xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl p-6 space-y-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldCheck className="size-5 text-purple-600" />
                <h3 className="text-sm font-bold">变更用户角色与权限</h3>
              </div>
              <button
                type="button"
                onClick={() => setEditingUser(null)}
                className="p-1 rounded-lg text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="p-3 rounded-2xl bg-zinc-50 dark:bg-zinc-800/50 text-xs space-y-1">
              <div className="font-semibold text-zinc-800 dark:text-zinc-200">
                目标用户: {editingUser.username}
              </div>
              <div className="text-[11px] font-mono text-zinc-400">
                用户 ID: {editingUser.id}
              </div>
            </div>

            <div className="space-y-2">
              <span className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                选择新角色:
              </span>
              <div className="grid grid-cols-1 gap-2">
                {Object.entries(ROLE_DEFINITIONS).map(([rKey, rDef]) => (
                  <button
                    key={rKey}
                    type="button"
                    onClick={() => setSelectedRole(rKey)}
                    className={cn(
                      "flex items-center justify-between p-3 rounded-xl border text-left transition-all",
                      selectedRole === rKey
                        ? "border-purple-500 bg-purple-50/80 dark:bg-purple-950/60 text-purple-700 dark:text-purple-300"
                        : "border-zinc-200 dark:border-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-800",
                    )}
                  >
                    <div>
                      <div className="font-bold text-xs">{rDef.label}</div>
                      <div className="text-[11px] text-zinc-400">
                        {rDef.desc}
                      </div>
                    </div>
                    {selectedRole === rKey && (
                      <Check className="size-4 text-purple-600" />
                    )}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setEditingUser(null)}
                className="px-4 py-2 rounded-xl text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void handleSaveRole()}
                disabled={updating}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs disabled:opacity-50"
              >
                {updating ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <Check className="size-3.5" />
                )}
                <span>保存变更</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
