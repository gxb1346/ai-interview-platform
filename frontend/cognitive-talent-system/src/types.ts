/**
 * Core type definitions for the Cognitive Talent System (RecruitAI)
 */

export enum CandidateStatus {
  INVITED = "已邀约",
  WAITING_INTERVIEW = "待面试",
  PASSED = "面试通过",
  REJECTED = "不合适"
}

/** 人才库管道状态（与后端 TalentStatus 对应） */
export enum TalentStatus {
  NEW = "NEW",
  INVITED = "INVITED",
  WAITING_INTERVIEW = "WAITING_INTERVIEW",
  PASSED = "PASSED",
  REJECTED = "REJECTED"
}

export interface CompetencyScores {
  technical: number;
  communication: number;
  problemSolving: number;
  teamFit: number;
  drive: number;
}

export interface Candidate {
  id: string;
  name: string;
  role: string;
  experienceYears: number;
  education: string;
  status: CandidateStatus;
  avatar: string;
  matchScore: number;
  email: string;
  phone: string;
  resumeText?: string;
  competencies: CompetencyScores;
  strengths: string[];
  weaknesses: string[];
  highlights: string[];
  aiSummary: string;
  analyzedAt: string;
  createdAt?: string;
}

/** 后端 ResumeVO 类型 */
export interface ResumeVO {
  id: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  candidateName: string | null;
  candidateRole: string | null;
  experienceYears: number | null;
  education: string | null;
  email: string | null;
  phone: string | null;
  matchScore: number | null;
  aiSummary: string | null;
  analyzedAt: string;
  createdAt: string;
  inTalentPool: boolean;
  talentStatus: string;
  competencies: Record<string, number> | null;
  strengths: string[] | null;
  weaknesses: string[] | null;
  highlights: string[] | null;
}

/** 后端分页结果类型 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

/** 后端统一响应类型 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/** 简历更新 DTO */
export interface ResumeUpdateDTO {
  candidateName?: string;
  candidateRole?: string;
  experienceYears?: number;
  education?: string;
  email?: string;
  phone?: string;
  matchScore?: number;
  aiSummary?: string;
  competencies?: Record<string, number>;
  strengths?: string[];
  weaknesses?: string[];
  highlights?: string[];
}

export interface Interview {
  id: string;
  candidateId: string;
  candidateName: string;
  role: string;
  scheduledAt: string;
  status: "pending" | "completed" | "cancelled";
  suggestedQuestions: string[];
  notes?: string;
}

export interface ChatMessage {
  id: string;
  sender: "interviewer" | "candidate";
  text: string;
  timestamp: string;
}

export interface ScoreCard {
  id: string;
  candidateId: string;
  candidateName: string;
  role: string;
  overallScore: number;
  scores: {
    technical: number;
    communication: number;
    problemSolving: number;
    culturalFit: number;
  };
  summary: string;
  strengths: string[];
  improvements: string[];
  verdict: "建议录用" | "待定" | "不予录用";
  evaluatedAt: string;
}

/* ===== 模拟面试系统新增类型 ===== */

/** 面试阶段配置 */
export interface StageConfig {
  totalMinutes: number;
  stageMinutes: Record<string, number>;
}

/** 面试题目 */
export interface InterviewQuestion {
  id: string;
  text: string;
  source: "SKILL" | "RESUME_DEEP_DIVE" | "JD_PARSE";
  direction: string;
  level: string;
  stage: string;
  category: string;
  difficultyScore: number;
}

/** 面试对话消息（后端格式） */
export interface InterviewMessage {
  id: string;
  sender: "interviewer" | "candidate";
  text: string;
  stage: string;
  roundNumber: number;
  timestamp: string;
}

/** 面试会话（从后端获取） */
export interface InterviewSession {
  sessionId: string;
  candidateId: string;
  candidateName: string;
  candidateRole: string;
  resumeText: string;
  direction: string;
  level: string;
  mode: "text" | "voice";
  totalDuration: number;
  stageConfig: StageConfig;
  followUpCount: number;
  status: "PREPARING" | "IN_PROGRESS" | "COMPLETED" | "TERMINATED" | "PAUSED";
  customJD: string;
  questions: InterviewQuestion[];
  messages: InterviewMessage[];
  askedQuestionIds: string[];
  currentStage: string;
  currentRound: number;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

/** 创建会话请求 */
export interface CreateSessionRequest {
  candidateId: string;
  candidateName: string;
  candidateRole: string;
  resumeText?: string;
  direction: string;
  level: string;
  mode: "text" | "voice";
  totalDuration: number;
  followUpCount: number;
  customJD?: string;
}

/** 创建会话响应 */
export interface CreateSessionResponse {
  sessionId: string;
  status: string;
  stageConfig: StageConfig;
}

/** 开始面试响应 */
export interface StartInterviewResponse {
  sessionId: string;
  status: string;
  questions: {
    id: string;
    text: string;
    category: string;
    difficultyScore: number;
    source: string;
  }[];
  currentStage: string;
  messages: InterviewMessage[];
}

/** 聊天响应 */
export interface ChatResponse {
  reply: string;
  currentRound: number;
  currentQuestionIndex?: number;
  totalQuestions?: number;
  currentStage: string;
  status: string;
  /** 语音面试时，后端返回 base64 编码的 TTS 音频数据 */
  audio?: string;
}

/** 方向推荐结果 */
export interface DirectionRecommendation {
  direction: string;
  matchScore: number;
  reason: string;
}

/** JD 解析结果 */
export interface JDParseResult {
  matchedDirection: string;
  skills: string[];
  experienceRequired: number;
  techStack: string[];
}

/** 评估报告 */
export interface EvaluationReport {
  reportId: string;
  sessionId: string;
  candidateId: string;
  candidateName: string;
  direction: string;
  level: string;
  totalRounds: number;
  overallScore: number;
  dimensionScores: Record<string, number>;
  strengths: string[];
  improvements: string[];
  summary: string;
  verdict: string;
  mode: string;
  evaluatedAt: string;
  pdfReportPath: string | null;
}

/* ===== 知识库模块类型 ===== */

/** 知识文档 */
export interface KnowledgeDocument {
  id: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  title: string;
  description: string | null;
  chunkCount: number;
  indexStatus: "PENDING" | "INDEXING" | "INDEXED" | "FAILED";
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 知识库统计 */
export interface KnowledgeStats {
  totalDocuments: number;
  indexedDocuments: number;
  pendingDocuments: number;
  failedDocuments: number;
}

/** 阶段时长对应表 */
export const STAGE_LABELS: Record<string, string> = {
  selfIntro: "自我介绍",
  techExam: "技术考察",
  projectDeep: "项目深挖",
  qaRound: "反问环节"
};

/* ===== P1 新增类型 ===== */

/** 仪表盘统计数据 */
export interface DashboardStats {
  totalSessions: number;
  completedSessions: number;
  inProgressSessions: number;
  averageScore: number;
  passRate: number;
  directionStats: { direction: string; count: number }[];
  statusStats: { status: string; count: number }[];
  dailyStats: { date: string; count: number }[];
  // 语音面试统计
  voiceTotalSessions?: number;
  voiceCompletedSessions?: number;
  voiceInProgressSessions?: number;
  voiceAverageScore?: number;
  voicePassRate?: number;
  voiceDirectionStats?: { direction: string; count: number }[];
  voiceDailyStats?: { date: string; count: number }[];
}

/** 面试历史搜索条件 */
export interface SessionSearchParams {
  candidateId?: string;
  direction?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
  page: number;
  size: number;
}

/** 面试会话记录（后端分页返回） */
export interface SessionRecord {
  sessionId: string;
  candidateId: string;
  candidateName: string;
  direction: string;
  level: string;
  mode: string;
  status: string;
  totalRounds: number;
  overallScore: number;
  verdict: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

/** 面试会话详情（含评估报告） */
export interface SessionDetail {
  sessionId: string;
  candidateId: string;
  candidateName: string;
  direction: string;
  level: string;
  mode: string;
  status: string;
  currentRound: number;          // 当前总轮数（后端字段名）
  createdAt: string;
  completedAt: string | null;
  evaluationReport: {
    summary: string;
    strengths: string[];
    improvements: string[];
    dimensionScores: Record<string, number>;
    verdict: string;
    overallScore: number;
  } | null;
}

/** 后端分页返回（Spring Data Page） */
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

/** 更新个人信息请求 */
export interface UpdateProfileRequest {
  displayName: string;
  email: string;
}

/** 用户信息 */
export interface UserProfile {
  username: string;
  displayName: string;
  email: string;
  role: string;
  userId: number;
}

/** Token 刷新 */
export interface TokenRefreshResponse {
  token: string;
  refreshToken: string;
}
/* ===== 语音面试模块 ===== */

export interface CreateVoiceSessionRequest {
  userId: string;
  candidateName?: string;
  skillId: string;
  roleType: string;
}

export interface VoiceSessionMeta {
  id?: number;
  sessionId?: number;
  userId: string;
  candidateName?: string;
  skillId: string;
  roleType: string;
  status: string;
  createdAt: string;
  updatedAt?: string;
  overallScore?: number;
  messageCount?: number;
  evaluateStatus?: string;
}

export interface VoiceSessionDetail {
  id?: number;
  sessionId?: number;
  userId: string;
  skillId: string;
  roleType: string;
  status: string;
  messages: { role: string; content: string; timestamp: string }[];
  createdAt: string;
}

export interface VoiceEvaluationStatus {
  sessionId: number;
  evaluateStatus?: string; // PENDING / PROCESSING / COMPLETED / FAILED
  evaluateError?: string;
  evaluation?: VoiceEvaluationDetail;
  status?: string;
  overallScore?: number;
  totalRounds?: number;
  verdict?: string;
  summary?: string;
  strengths?: string[];
  improvements?: string[];
  dimensionScores?: Record<string, number>;
  evaluatedAt?: string;
}

export interface VoiceEvaluationDetail {
  sessionId?: number;
  overallScore?: number;
  overallFeedback?: string;
  questionEvaluations?: QuestionEvalItem[];
  strengths?: string[];
  improvements?: string[];
  answerDetails?: AnswerDetail[];
  answers?: VoiceAnswerEvalItem[];
}

export interface VoiceAnswerEvalItem {
  questionIndex?: number;
  question?: string;
  category?: string;
  userAnswer?: string;
  score?: number;
  feedback?: string;
  referenceAnswer?: string;
  keyPoints?: string[];
}

export interface QuestionEvalItem {
  questionIndex?: number;
  question?: string;
  category?: string;
  userAnswer?: string;
  score?: number;
  feedback?: string;
  referenceAnswer?: string;
  keyPoints?: string[];
}

export interface AnswerDetail {
  question?: string;
  answer?: string;
  score?: number;
  feedback?: string;
}

/* ===== LLM 提供商模块 ===== */

export interface LlmProvider {
  id: string;
  name?: string;
  baseUrl?: string;
  model?: string;
  embeddingModel?: string;
  supportsEmbedding?: boolean;
  enabled?: boolean;
  createdAt?: string;
}

export interface LlmProviderTestResult {
  success: boolean;
  message?: string;
  latencyMs?: number;
}

export interface LlmDefaultProvider {
  defaultChatProviderId: string;
  defaultEmbeddingProviderId?: string;
}

export interface AsrConfig {
  provider?: string;
  model?: string;
  apiKey?: string;
}

export interface TtsConfig {
  provider?: string;
  model?: string;
  voice?: string;
}

/* ===== 面试日程模块 ===== */

export interface InterviewSchedule {
  id: number;
  companyName: string;
  position: string;
  interviewTime: string;
  interviewType: string;
  status: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ParseInterviewRequest {
  rawText: string;
}

export interface ParseInterviewResponse {
  companyName?: string;
  position?: string;
  interviewTime?: string;
  interviewType?: string;
  notes?: string;
  rawText?: string;
}