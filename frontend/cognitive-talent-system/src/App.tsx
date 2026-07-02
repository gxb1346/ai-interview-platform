/**
 * RecruitAI - AI 智能面试平台
 * 重构后的 App 入口：负责全局状态 + 视图路由
 */
import React, { useState, useEffect } from "react";
import { getToken, clearToken } from "./api/client";
import { authFetch } from "./api";
import { useAuth } from "./hooks/useAuth";
import { AppLayout } from "./components/layout/AppLayout";
import { CandidateStatus } from "./types";
import type { Candidate, Interview, ScoreCard, InterviewSession } from "./types";
import { PRESEEDED_CANDIDATES, PRESEEDED_INTERVIEWS, PRESEEDED_SCORECARDS } from "./data";

// 子视图组件
import DashboardView from "./components/DashboardView";
import ResumeAnalysisView from "./components/ResumeAnalysisView";
import ResumeManageView from "./components/ResumeManageView";
import TalentPoolView from "./components/TalentPoolView";
import InterviewCenterView from "./components/InterviewCenterView";
import MockInterviewView from "./components/MockInterviewView";
import InterviewRecordsView from "./components/InterviewRecordsView";
import { InterviewScheduleView } from "./components/InterviewScheduleView";
import { VoiceInterviewView } from "./components/VoiceInterviewView";
import { LlmProviderView } from "./components/LlmProviderView";
import KnowledgeBaseView from "./components/KnowledgeBaseView";
import KnowledgeQAView from "./components/KnowledgeQAView";
import SettingsView from "./components/SettingsView";
import LoginView from "./components/LoginView";

type ActiveView =
  | "DASHBOARD"
  | "RESUME_ANALYSIS"
  | "RESUME_MANAGE"
  | "TALENT_POOL"
  | "INTERVIEW_CENTER"
  | "MOCK_INTERVIEW"
  | "INTERVIEW_RECORDS"
  | "VOICE_INTERVIEW"
  | "SCHEDULE"
  | "LLM_PROVIDER"
  | "KNOWLEDGE_BASE"
  | "KNOWLEDGE_QA"
  | "SETTINGS";

function loadFromStorage<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (raw) return JSON.parse(raw) as T;
  } catch { /* ignore */ }
  return fallback;
}

function saveToStorage<T>(key: string, data: T) {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch { /* ignore */ }
}

export default function App() {
  const { user, loading: authLoading, login, register, logout } = useAuth();
  const [loggedIn, setLoggedIn] = useState(false);
  const [currentView, setCurrentView] = useState<ActiveView>("DASHBOARD");

  // 当 useAuth 从 token 恢复用户时，自动标记已登录
  useEffect(() => {
    if (user && !authLoading) {
      setLoggedIn(true);
    }
  }, [user, authLoading]);



  // 全局状态
  const [candidates, setCandidates] = useState<Candidate[]>(() =>
    loadFromStorage("recruit_candidates", PRESEEDED_CANDIDATES)
  );
  const [interviews, setInterviews] = useState<Interview[]>(() =>
    loadFromStorage("recruit_interviews", PRESEEDED_INTERVIEWS)
  );
  const [scoreCards, setScoreCards] = useState<ScoreCard[]>(() =>
    loadFromStorage("recruit_scorecards", PRESEEDED_SCORECARDS)
  );
  const [preSelectedCandidate, setPreSelectedCandidate] = useState<Candidate | null>(null);
  const [interviewSessions, setInterviewSessions] = useState<InterviewSession[]>(() =>
    loadFromStorage("recruit_sessions", [] as InterviewSession[])
  );
  const [resumeSessionId, setResumeSessionId] = useState<string | null>(null);

  // 持久化
  useEffect(() => { saveToStorage("recruit_candidates", candidates); }, [candidates]);
  useEffect(() => { saveToStorage("recruit_interviews", interviews); }, [interviews]);
  useEffect(() => { saveToStorage("recruit_sessions", interviewSessions); }, [interviewSessions]);
  useEffect(() => { saveToStorage("recruit_scorecards", scoreCards); }, [scoreCards]);

  // 认证状态同步
  useEffect(() => {
    setLoggedIn(!!user);
  }, [user]);

  // 业务逻辑 handlers
  const handleAddCandidate = (cand: Candidate) => setCandidates((prev) => [cand, ...prev]);

  const handleAddInterview = (int: Interview) => setInterviews((prev) => [int, ...prev]);

  const handleRemoveInterview = (id: string) =>
    setInterviews((prev) => prev.filter((i) => i.id !== id));

  const handleRescheduleInterview = (id: string, newDate: string) =>
    setInterviews((prev) =>
      prev.map((i) => (i.id === id ? { ...i, scheduledAt: newDate } : i))
    );

  const handleUpdateInterviewStatus = (id: string, status: "pending" | "completed" | "cancelled") =>
    setInterviews((prev) =>
      prev.map((i) => (i.id === id ? { ...i, status } : i))
    );

  const handleSaveScoreCard = (card: ScoreCard) => {
    setScoreCards((prev) => [card, ...prev]);
    setCandidates((prev) =>
      prev.map((c) =>
        c.id === card.candidateId
          ? { ...c, status: card.verdict === "建议录用" ? CandidateStatus.PASSED : card.verdict === "不予录用" ? CandidateStatus.REJECTED : CandidateStatus.WAITING_INTERVIEW }
          : c
      )
    );
  };

  const handleNavigateToMock = (candidate: Candidate) => {
    setPreSelectedCandidate(candidate);
    setCurrentView("MOCK_INTERVIEW");
  };

  const handleNavigateToInterview = (candidate: Candidate) => {
    setPreSelectedCandidate(candidate);
    setCurrentView("INTERVIEW_CENTER");
  };

  const onSessionCreated = (sessionId: string) => {
    setResumeSessionId(sessionId);
  };

  const refreshInterviewSessions = async () => {
    try {
      const candidateIds = [...new Set(candidates.map((c) => c.id))];
      const allSessions: InterviewSession[] = [];
      for (const cId of candidateIds.slice(0, 20)) {
        try {
          const res = await authFetch(`http://localhost:8082/api/mock-interview/candidates/${cId}/sessions`);
          if (res.ok) {
            const data = await res.json();
            allSessions.push(...(data?.data ?? data ?? []));
          }
        } catch { /* ignore */ }
      }
      setInterviewSessions(allSessions);
    } catch { /* ignore */ }
  };

  // 登录页面
  if (!loggedIn) {
    if (authLoading) {
      return (
        <div className="h-screen flex items-center justify-center bg-slate-50">
          <div className="animate-spin w-8 h-8 border-2 border-primary border-t-transparent rounded-full" />
        </div>
      );
    }
    return (
      <LoginView
        onLoginSuccess={() => setLoggedIn(true)}
       
      />
    );
  }

  // 视图路由
  const renderView = () => {
    switch (currentView) {
      case "DASHBOARD":
        return <DashboardView />;
      case "RESUME_ANALYSIS":
        return (
          <ResumeAnalysisView
            onAddCandidate={handleAddCandidate}
            onNavigateToInterview={handleNavigateToInterview}
            onNavigateToMock={handleNavigateToMock}
          />
        );
      case "RESUME_MANAGE":
        return <ResumeManageView />;
      case "TALENT_POOL":
        return (
          <TalentPoolView
            onNavigateToMock={handleNavigateToMock}
            onNavigateToInterview={handleNavigateToInterview}
            onAddInterview={handleAddInterview}
          />
        );
      case "INTERVIEW_CENTER":
        return (
          <InterviewCenterView
            interviews={interviews}
            candidates={candidates}
            onAddInterview={handleAddInterview}
            onRemoveInterview={handleRemoveInterview}
            onNavigateToMock={handleNavigateToMock}
            onNavigateToRecords={() => {
              setPreSelectedCandidate(null);
              setCurrentView("INTERVIEW_RECORDS");
            }}
            interviewSessions={interviewSessions}
            onRefreshSessions={refreshInterviewSessions}
          />
        );
      case "MOCK_INTERVIEW":
        return (
          <MockInterviewView
            candidates={candidates}
            preSelectedCandidate={preSelectedCandidate}
            resumeSessionId={resumeSessionId}
            onSaveScoreCard={handleSaveScoreCard}
            onNavigateToRecords={() => {
              setPreSelectedCandidate(null);
              setCurrentView("INTERVIEW_RECORDS");
            }}
            onSessionCreated={onSessionCreated}
          />
        );
      case "INTERVIEW_RECORDS":
        return <InterviewRecordsView />;
      case "VOICE_INTERVIEW":
          return <VoiceInterviewView userId={user?.userId?.toString()} candidates={candidates} />;
      case "SCHEDULE":
        return <InterviewScheduleView />;
      case "LLM_PROVIDER":
        return <LlmProviderView />;
      case "KNOWLEDGE_BASE":
        return <KnowledgeBaseView onNavigateToQA={() => setCurrentView("KNOWLEDGE_QA")} />;
      case "KNOWLEDGE_QA":
        return <KnowledgeQAView onNavigateBack={() => setCurrentView("KNOWLEDGE_BASE")} />;
      case "SETTINGS":
        return <SettingsView />;
      default:
        return <DashboardView />;
    }
  };

  return (
    <AppLayout currentView={currentView} onNavigate={setCurrentView}>
      {renderView()}
    </AppLayout>
  );
}