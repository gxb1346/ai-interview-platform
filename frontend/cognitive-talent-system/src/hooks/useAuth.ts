import { useState, useEffect, useCallback } from 'react';
import { getToken, setToken, clearToken } from '../api/client';
import { authApi } from '../api/auth';

export function useAuth() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    try {
      const data = await authApi.me();
      setUser((data && data.data) || data);
    } catch {
      clearToken();
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const login = async (username, password) => {
    const res = await authApi.login(username, password);
    const data = (res && res.data) || res;
    setToken(data.token, data.refreshToken);
    setUser({ username: data.username, displayName: data.displayName, email: data.email, role: '', userId: 0 });
  };

  const register = async (username, password, displayName) => {
    await authApi.register(username, password, displayName);
  };

  const logout = () => {
    clearToken();
    setUser(null);
  };

  return { user, loading, login, register, logout, refreshUser: fetchUser };
}
