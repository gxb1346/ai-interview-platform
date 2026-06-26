/**
 * API 工具模块
 * 统一管理 JWT Token、自动刷新、后端 API 调用
 */

import type { DashboardStats, SessionSearchParams, SpringPage, SessionRecord, SessionDetail, UserProfile, TokenRefreshResponse } from "./types";

const API_BASE = "http://localhost:8082";

// ─── Token / User 管理 ───────────────────────────────────────────────

export function getToken(): string | null {
  return localStorage.getItem("auth_token");
}

export function setToken(token: string) {
  localStorage.setItem("auth_token", token);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem("refresh_token");
}

export function setRefreshToken(token: string) {
  localStorage.setItem("refresh_token", token);
}

export function clearToken() {
  localStorage.removeItem("auth_token");
  localStorage.removeItem("refresh_token");
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

// ─── Token 自动刷新 ───────────────────────────────────────────────────

let isRefreshing = false;
let refreshPromise: Promise<boolean> | null = null;

/** 尝试使用 refreshToken 刷新 accessToken */
async function tryRefreshToken(): Promise<boolean> {
  const rt = getRefreshToken();
  if (!rt) return false;

  try {
    const res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: rt }),
    });
    if (res.ok) {
      const data: TokenRefreshResponse = await res.json();
      setToken(data.token);
      setRefreshToken(data.refreshToken);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

/** 带自动刷新的 fetch */
async function fetchWithRefresh(url: string, options: RequestInit = {}): Promise<Response> {
  const res = await fetch(url, options);

  if (res.status === 401) {
    if (!isRefreshing) {
      isRefreshing = true;
      refreshPromise = tryRefreshToken().finally(() => {
        isRefreshing = false;
        refreshPromise = null;
      });
    }
    const success = await refreshPromise;
    if (success) {
      const token = getToken();
      const headers = { ...(options.headers as Record<string, string> || {}) };
      headers["Authorization"] = `Bearer ${token}`;
      return fetch(url, { ...options, headers });
    }
    clearToken();
    window.location.reload();
    throw new Error("Token 已过期，请重新登录");
  }

  return res;
}

// ─── 通用 API 请求封装 ─────────────────────────────────────────────────

export function authFetch(input: string, init?: RequestInit): Promise<Response> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (init?.headers) {
    Object.assign(headers, init.headers);
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return fetchWithRefresh(input, { ...init, headers });
}

export async function apiFetch<T = any>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();

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

  const res = await fetchWithRefresh(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (res.status === 204) {
    return undefined as T;
  }

  return res.json();
}

// ─── 认证 API ─────────────────────────────────────────────────────────

export const authApi = {
  register: (username: string, password: string, displayName?: string) =>
    apiFetch<{ token: string; refreshToken: string; username: string; displayName: string; email: string }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password, displayName }),
    }),

  login: (username: string, password: string) =>
    apiFetch<{ token: string; refreshToken: string; username: string; displayName: string; email: string }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  me: () =>
    apiFetch<UserProfile>("/api/auth/me"),

  /** 修改密码 */
  changePassword: (oldPassword: string, newPassword: string) =>
    apiFetch<{ message: string }>("/api/auth/password", {
      method: "PUT",
      body: JSON.stringify({ oldPassword, newPassword }),
    }),

  /** 更新个人信息 */
  updateProfile: (displayName: string, email: string) =>
    apiFetch<UserProfile>("/api/auth/profile", {
      method: "PUT",
      body: JSON.stringify({ displayName, email }),
    }),
};

// ─── 面试 API ─────────────────────────────────────────────────────────

export const interviewApi = {
  /** 仪表盘统计 */
  getDashboardStats: () =>
    apiFetch<DashboardStats>("/api/mock-interview/dashboard/stats"),

  /** 面试历史搜索 */
  searchSessions: (params: SessionSearchParams) => {
    const query = new URLSearchParams();
    query.set("page", String(params.page));
    query.set("size", String(params.size));
    if (params.candidateId) query.set("candidateId", params.candidateId);
    if (params.direction) query.set("direction", params.direction);
    if (params.status) query.set("status", params.status);
    if (params.startTime) query.set("startTime", params.startTime);
    if (params.endTime) query.set("endTime", params.endTime);
    return apiFetch<SpringPage<SessionRecord>>(`/api/mock-interview/sessions/search?${query.toString()}`);
  },

  /** 获取面试会话详情（含评估报告） */
  getSession: (sessionId: string) =>
    apiFetch<SessionDetail>(`/api/mock-interview/sessions/${sessionId}`),
};