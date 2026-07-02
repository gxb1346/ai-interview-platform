import { apiFetch } from './client';
import type { UserProfile } from '../types';

export const authApi = {
  register: (username, password, displayName) =>
    apiFetch<any>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, displayName }),
    }),

  login: (username, password) =>
    apiFetch<any>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  me: () => apiFetch<any>('/api/auth/me'),

  changePassword: (oldPassword, newPassword) =>
    apiFetch<any>('/api/auth/password', {
      method: 'PUT',
      body: JSON.stringify({ oldPassword, newPassword }),
    }),

  updateProfile: (displayName, email) =>
    apiFetch<any>('/api/auth/profile', {
      method: 'PUT',
      body: JSON.stringify({ displayName, email }),
    }),
};
