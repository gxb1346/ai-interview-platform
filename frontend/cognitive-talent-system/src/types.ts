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
  technical: number;      // 技术深度
  communication: number;  // 沟通表达
  problemSolving: number; // 解决问题
  teamFit: number;        // 团队契合
  drive: number;          // 自驱动力
}

export interface Candidate {
  id: string;
  name: string;
  role: string;
  experienceYears: number;
  education: string;
  status: CandidateStatus;
  avatar: string;
  matchScore: number;     // AI 匹配度 %
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

/** 后端 ResumeVO 类型（与 API 返回一致） */
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
  scheduledAt: string; // e.g. "2026-06-11 10:00"
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
