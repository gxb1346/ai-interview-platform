import { apiFetch } from './client';

export const scheduleApi = {
  list: (params) => {
    const query = new URLSearchParams();
    if (params && params.status) query.set('status', params.status);
    if (params && params.start) query.set('start', params.start);
    if (params && params.end) query.set('end', params.end);
    const qs = query.toString();
    return apiFetch<any>('/api/interview-schedule' + (qs ? '?' + qs : ''));
  },

  get: (id) => apiFetch<any>('/api/interview-schedule/' + id),

  create: (req) =>
    apiFetch<any>('/api/interview-schedule', {
      method: 'POST',
      body: JSON.stringify(req),
    }),

  update: (id, req) =>
    apiFetch<any>('/api/interview-schedule/' + id, {
      method: 'PUT',
      body: JSON.stringify(req),
    }),

  delete: (id) =>
    apiFetch<any>('/api/interview-schedule/' + id, { method: 'DELETE' }),

  parse: (req) =>
    apiFetch<any>('/api/interview-schedule/parse', {
      method: 'POST',
      body: JSON.stringify(req),
    }),
};
