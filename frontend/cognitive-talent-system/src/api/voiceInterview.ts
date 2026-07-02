import { apiFetch } from './client';
import type {
  VoiceSessionMeta,
  VoiceSessionDetail,
  CreateVoiceSessionRequest,
  VoiceEvaluationStatus,
} from '../types';

export const voiceInterviewApi = {
  createSession: (req) =>
    apiFetch<any>('/api/voice-interview/sessions', {
      method: 'POST',
      body: JSON.stringify(req),
    }),

  getSessions: (userId, status) => {
    const params = new URLSearchParams();
    if (userId) params.set('userId', userId);
    if (status) params.set('status', status);
    return apiFetch<any>('/api/voice-interview/sessions?' + params.toString());
  },

  getSession: (sessionId) =>
    apiFetch<any>('/api/voice-interview/sessions/' + sessionId),

  deleteSession: (sessionId) =>
    apiFetch<any>('/api/voice-interview/sessions/' + sessionId, {
      method: 'DELETE',
    }),

  getMessages: (sessionId) =>
    apiFetch<any>('/api/voice-interview/sessions/' + sessionId + '/messages'),

  getEvaluation: (sessionId) =>
    apiFetch<any>('/api/voice-interview/sessions/' + sessionId + '/evaluation'),

  triggerEvaluation: (sessionId) =>
    apiFetch<any>('/api/voice-interview/sessions/' + sessionId + '/evaluation', {
      method: 'POST',
    }),
};
