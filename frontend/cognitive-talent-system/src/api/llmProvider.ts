import { apiFetch } from './client';

export const llmProviderApi = {
  list: () => apiFetch<any>('/api/llm-provider/list'),

  get: (id) => apiFetch<any>('/api/llm-provider/' + id),

  create: (req) =>
    apiFetch<any>('/api/llm-provider', {
      method: 'POST',
      body: JSON.stringify(req),
    }),

  update: (id, req) =>
    apiFetch<any>('/api/llm-provider/' + id, {
      method: 'PUT',
      body: JSON.stringify(req),
    }),

  delete: (id) =>
    apiFetch<any>('/api/llm-provider/' + id, { method: 'DELETE' }),

  test: (id) =>
    apiFetch<any>('/api/llm-provider/' + id + '/test', { method: 'POST' }),

  reload: () =>
    apiFetch<any>('/api/llm-provider/reload', { method: 'POST' }),

  getDefaultProvider: () =>
    apiFetch<any>('/api/llm-provider/default-provider'),

  updateDefaultProvider: (req) =>
    apiFetch<any>('/api/llm-provider/default-provider', {
      method: 'PUT',
      body: JSON.stringify(req),
    }),
};
