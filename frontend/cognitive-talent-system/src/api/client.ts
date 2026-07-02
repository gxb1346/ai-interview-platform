/// <reference types="vite/client" />

const API_BASE: string = import.meta.env.VITE_API_BASE || 'http://localhost:8082';

const TOKEN_KEY = 'auth_token';
const REFRESH_TOKEN_KEY = 'auth_refresh_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string, refreshToken?: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

let isRefreshing = false;
let refreshPromise: Promise<boolean> | null = null;

async function tryRefreshToken(): Promise<boolean> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return false;
  try {
    const res = await fetch(API_BASE + '/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = await res.json();
    if (data && data.data && data.data.token) {
      setToken(data.data.token, data.data.refreshToken);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

export async function fetchWithRefresh(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
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
      const headers: Record<string, string> = { ...(options.headers || {}) as Record<string, string> };
      headers['Authorization'] = 'Bearer ' + token;
      return fetch(url, { ...options, headers });
    }
    clearToken();
    window.location.reload();
    throw new Error('Token expired');
  }
  return res;
}

export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (options.headers) {
    Object.assign(headers, options.headers as Record<string, string>);
  }
  if (!(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }
  const res = await fetchWithRefresh(API_BASE + path, { ...options, headers });
  if (res.status === 204) return undefined as T;
  return res.json();
}
