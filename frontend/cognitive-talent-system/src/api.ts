/**
 * API 工具模块
 * 统一管理 JWT Token 和后端 API 调用
 */

const API_BASE = "http://localhost:8082";

// ─── Token / User 管理 ───────────────────────────────────────────────

export function getToken(): string | null {
  return localStorage.getItem("auth_token");
}

export function setToken(token: string) {
  localStorage.setItem("auth_token", token);
}

export function clearToken() {
  localStorage.removeItem("auth_token");
  localStorage.removeItem("auth_user");
}

export function getStoredUser(): { username: string; displayName: string } | null {
  try {
    const raw = localStorage.getItem("auth_user");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setStoredUser(user: { username: string; displayName: string }) {
  localStorage.setItem("auth_user", JSON.stringify(user));
}

export function isAuthenticated(): boolean {
  return !!getToken();
}

// ─── 通用 API 请求封装 ─────────────────────────────────────────────────

/** 带认证的 fetch 包装，自动添加 Authorization 头 */
export function authFetch(input: string, init?: RequestInit): Promise<Response> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (init?.headers) {
    Object.assign(headers, init.headers);
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return fetch(input, { ...init, headers });
}

export async function apiFetch<T = any>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();

  // 合并 headers：不要覆盖 Content-Type，FormData 时让浏览器自动设置
  const headers: Record<string, string> = {};
  if (options.headers) {
    Object.assign(headers, options.headers);
  }
  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  // 401 → 清除 token 并刷新页面（跳转到登录页）
  if (res.status === 401) {
    clearToken();
    window.location.reload();
    throw new Error("未登录或 Token 已过期");
  }

  // 204 No Content
  if (res.status === 204) {
    return undefined as T;
  }

  return res.json();
}

// ─── 认证 API ─────────────────────────────────────────────────────────

export const authApi = {
  register: (username: string, password: string, displayName?: string) =>
    apiFetch<{ token: string; username: string; displayName: string; email: string }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password, displayName }),
    }),

  login: (username: string, password: string) =>
    apiFetch<{ token: string; username: string; displayName: string; email: string }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  me: () =>
    apiFetch<{ username: string; displayName: string; email: string }>("/api/auth/me"),
};
