export interface UserProfile {
  id: string;
  username: string;
  role: "ADMIN" | "USER" | "GUEST" | string;
  permissions: string[];
  createdAt: number;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserProfile;
}

export interface UserAdminItem {
  id: string;
  username: string;
  role: string;
  status: "ACTIVE" | "DISABLED" | string;
  createdAt: number;
  updatedAt: number;
}

const ACCESS_TOKEN_KEY = "ai_copilot_access_token";
const REFRESH_TOKEN_KEY = "ai_copilot_refresh_token";
const USER_KEY = "ai_copilot_user";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getStoredUser(): UserProfile | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function saveAuthSession(pair: TokenPair) {
  if (typeof window === "undefined") return;
  localStorage.setItem(ACCESS_TOKEN_KEY, pair.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, pair.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(pair.user));
}

export function clearAuthSession() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  const token = getStoredToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

export async function login(
  username: string,
  password: string,
): Promise<TokenPair> {
  const res = await fetch("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ error: "登录失败" }));
    throw new Error(errorData.error || "登录失败");
  }
  const pair: TokenPair = await res.json();
  saveAuthSession(pair);
  return pair;
}

export async function register(
  username: string,
  password: string,
  role?: string,
): Promise<TokenPair> {
  const res = await fetch("/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, role: role || "USER" }),
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ error: "注册失败" }));
    throw new Error(errorData.error || "注册失败");
  }
  const pair: TokenPair = await res.json();
  saveAuthSession(pair);
  return pair;
}

export async function fetchMe(): Promise<UserProfile> {
  const res = await fetch("/api/auth/me", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    throw new Error("获取用户信息失败");
  }
  const profile: UserProfile = await res.json();
  if (typeof window !== "undefined") {
    localStorage.setItem(USER_KEY, JSON.stringify(profile));
  }
  return profile;
}

export async function listAllUsers(): Promise<UserAdminItem[]> {
  const res = await fetch("/api/admin/users", {
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "加载用户列表失败" }));
    throw new Error(err.error || "加载用户列表失败");
  }
  return res.json();
}

export async function updateUserRole(
  userId: string,
  role: string,
): Promise<void> {
  const res = await fetch(`/api/admin/users/${userId}/role`, {
    method: "PUT",
    headers: getAuthHeaders(),
    body: JSON.stringify({ role }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "修改用户角色失败" }));
    throw new Error(err.error || "修改用户角色失败");
  }
}

export async function updateUserStatus(
  userId: string,
  status: string,
): Promise<void> {
  const res = await fetch(`/api/admin/users/${userId}/status`, {
    method: "PUT",
    headers: getAuthHeaders(),
    body: JSON.stringify({ status }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "修改用户状态失败" }));
    throw new Error(err.error || "修改用户状态失败");
  }
}
