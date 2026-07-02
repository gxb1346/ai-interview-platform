import { apiFetch } from './client';
import type { DashboardStats, SessionSearchParams, SpringPage, SessionRecord, SessionDetail } from '../types';

export const interviewApi = {
  getDashboardStats: () =>
    apiFetch<any>('/api/mock-interview/dashboard/stats'),

  searchSessions: (params) => {
    const query = new URLSearchParams();
    query.set('page', String(params.page));
    query.set('size', String(params.size));
    if (params.candidateId) query.set('candidateId', params.candidateId);
    if (params.direction) query.set('direction', params.direction);
    if (params.status) query.set('status', params.status);
    if (params.startTime) query.set('startTime', params.startTime);
    if (params.endTime) query.set('endTime', params.endTime);
    return apiFetch<any>('/api/mock-interview/sessions/search?' + query.toString());
  },

  getSession: (sessionId) =>
    apiFetch<any>('/api/mock-interview/sessions/' + sessionId),
};
