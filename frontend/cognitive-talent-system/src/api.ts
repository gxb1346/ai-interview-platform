import { getToken, setToken, clearToken, apiFetch, fetchWithRefresh } from './api/client';

export { getToken, setToken, clearToken, apiFetch, fetchWithRefresh };
export { authApi } from './api/auth';
export { interviewApi } from './api/interview';

export function isAuthenticated(): boolean {
  return !!getToken();
}

export function getStoredUser(): any {
  return null;
}

export function setStoredUser(_user: any): void {
  // no-op
}

export function setRefreshToken(_token: string): void {
  // no-op
}

export function authFetch(input: string, init?: RequestInit): Promise<Response> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (init && init.headers) {
    Object.assign(headers, init.headers as Record<string, string>);
  }
  if (!(init && init.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }
  return fetchWithRefresh(input, { ...init, headers });
}
