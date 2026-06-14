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
  status: "PREPARING" | "IN_PROGRESS" | "COMPLETED" | "TERMINATED";
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
  currentStage: string;
  status: string;
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
