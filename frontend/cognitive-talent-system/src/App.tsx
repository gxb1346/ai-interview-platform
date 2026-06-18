/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  FileText,
  Users,
  Film,
  MessageSquareCode,
  History,
  Sparkles,
  Bell,
  Menu,
  ChevronDown,
  LogOut,
  BrainCircuit,
  MessageSquare,
  Video,
  ClipboardList,
  BookOpen,
  Database
} from "lucide-react";

const API_BASE = "http://localhost:8082";

import { Candidate, CandidateStatus, Interview, ScoreCard, InterviewSession } from "./types";
import { PRESEEDED_CANDIDATES, PRESEEDED_INTERVIEWS, PRESEEDED_SCORECARDS } from "./data";

// Sub-views
import ResumeAnalysisView from "./components/ResumeAnalysisView";
import TalentPoolView from "./components/TalentPoolView";
import InterviewCenterView from "./components/InterviewCenterView";
import MockInterviewView from "./components/MockInterviewView";
import InterviewRecordsView from "./components/InterviewRecordsView";
import ScheduleView from "./components/ScheduleView";
import ResumeManageView from "./components/ResumeManageView";
import KnowledgeBaseView from "./components/KnowledgeBaseView";
import KnowledgeQAView from "./components/KnowledgeQAView";

// Auth
import LoginView from "./components/LoginView";
import { isAuthenticated, clearToken, getStoredUser, authFetch, getToken } from "./api";

// 全局 fetch 拦截：自动为后端 Java API 请求添加 JWT Authorization 头
{
  const origFetch = window.fetch.bind(window);
  window.fetch = function(input: RequestInfo | URL, init?: RequestInit) {
    const token = getToken();
    if (token) {
      const headers: Record<string, string> = {};
      if (init?.headers) {
        if (init.headers instanceof Headers) {
          init.headers.forEach((value, key) => { headers[key] = value; });
        } else if (Array.isArray(init.headers)) {
          init.headers.forEach(([key, value]) => { headers[key] = value; });
        } else {
          Object.assign(headers, init.headers as Record<string, string>);
        }
      }
      // 只对 Java 后端请求添加 Authorization（含 localhost:8082 的请求）
      const urlStr = typeof input === "string" ? input : (input instanceof URL ? input.href : input.url);
      if (urlStr.includes("localhost:8082")) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      return origFetch(input, { ...init, headers });
    }
    return origFetch(input, init);
  };
}

type ActiveView =
  | "RESUME_ANALYSIS"
  | "RESUME_MANAGE"
  | "TALENT_POOL"
  | "INTERVIEW_CENTER"
  | "MOCK_INTERVIEW"
  | "INTERVIEW_RECORDS"
  | "SCHEDULE"
  | "KNOWLEDGE_BASE"
  | "KNOWLEDGE_QA";

// 从 localStorage 读取数据，失败则回退到预设数据
function loadFromStorage<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (raw) return JSON.parse(raw) as T;
  } catch {}
  return fallback;
}

function saveToStorage<T>(key: string, data: T) {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch {}
}

export default function App() {
  const [currentView, setCurrentView] = useState<ActiveView>("RESUME_ANALYSIS");
  const [loggedIn, setLoggedIn] = useState(false);
  const [authChecking, setAuthChecking] = useState(true);
  
  // 启动时清除可能残留的旧 localStorage 数据（避免已移除人才库的候选人仍出现）
  try { localStorage.removeItem("recruit_candidates"); } catch {}
  
  // App Global Sync States — 优先从 localStorage 恢复
  const [candidates, setCandidates] = useState<Candidate[]>(
    () => loadFromStorage("recruit_candidates", PRESEEDED_CANDIDATES)
  );
  const [interviews, setInterviews] = useState<Interview[]>(
    () => loadFromStorage("recruit_interviews", PRESEEDED_INTERVIEWS)
  );
  const [scoreCards, setScoreCards] = useState<ScoreCard[]>(
    () => loadFromStorage("recruit_scorecards", PRESEEDED_SCORECARDS)
  );
  const [preSelectedCandidate, setPreSelectedCandidate] = useState<Candidate | null>(null);
  const [interviewSessions, setInterviewSessions] = useState<InterviewSession[]>([]);
  const [resumeSessionId, setResumeSessionId] = useState<string | null>(null);

  // Search input matching local list
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  // 数据变更时自动持久化到 localStorage
  useEffect(() => { saveToStorage("recruit_candidates", candidates); }, [candidates]);
  useEffect(() => { saveToStorage("recruit_interviews", interviews); }, [interviews]);
  useEffect(() => { saveToStorage("recruit_scorecards", scoreCards); }, [scoreCards]);

  // Add Candidate to list
  const handleAddCandidate = (cand: Candidate) => {
    setCandidates((prev) => [cand, ...prev]);
  };

  // Add Interview Slot
  const handleAddInterview = (int: Interview) => {
    setInterviews((prev) => [int, ...prev]);
  };

  // Remove Interview Schedule slot
  const handleRemoveInterview = (id: string) => {
    setInterviews((prev) => prev.filter((i) => i.id !== id));
  };

  // Save score evaluation scorecard
  const handleSaveScoreCard = (card: ScoreCard) => {
    setScoreCards((prev) => [card, ...prev]);

    // Update candidate status to PASSED or REJECTED based on verdict automatically!
    setCandidates((prev) =>
      prev.map((c) => {
        if (c.id === card.candidateId) {
          return {
            ...c,
            status: card.verdict === "建议录用" 
              ? CandidateStatus.PASSED 
              : card.verdict === "不予录用" 
              ? CandidateStatus.REJECTED 
              : CandidateStatus.WAITING_INTERVIEW
          };
        }
        return c;
      })
    );
  };

  // Delete score evaluation scorecard
  const handleDeleteScoreCard = (cardId: string) => {
    setScoreCards((prev) => prev.filter((c) => c.id !== cardId));
  };

  // 从后端拉取面试会话列表
  const refreshInterviewSessions = async () => {
    try {
      // 从候选人列表中拉取会话
      const candidateIds = [...new Set(candidates.map(c => c.id))];
      const allSessions: InterviewSession[] = [];
      for (const cId of candidateIds.slice(0, 20)) { // 限制最多查询20个候选人
        try {
          const res = await authFetch(`${API_BASE}/api/mock-interview/candidates/${cId}/sessions`);
          const sessions = await res.json();
          if (Array.isArray(sessions)) allSessions.push(...sessions);
        } catch {}
      }
      // 去重
      const seen = new Set<string>();
      const unique = allSessions.filter(s => {
        if (seen.has(s.sessionId)) return false;
        seen.add(s.sessionId);
        return true;
      });
      if (unique.length > 0) setInterviewSessions(unique);
    } catch {}
  };

  const onSessionCreated = (sessionId: string) => {
    // 新会话创建后，延迟拉取最新状态
    setTimeout(refreshInterviewSessions, 100);
  };

  // 组件挂载时拉取会话列表
  useEffect(() => { refreshInterviewSessions(); }, []);

  const handleNavigateToMock = (cand: Candidate, sessionId?: string) => {
    if (sessionId) setResumeSessionId(sessionId);
    else setResumeSessionId(null);
    setPreSelectedCandidate(cand);
    setCurrentView("MOCK_INTERVIEW");
    // 同步候选人到全局列表，确保模拟面试页面可选
    setCandidates(prev => {
      if (prev.some(c => c.id === cand.id)) return prev;
      return [cand, ...prev];
    });
  };

  const handleNavigateToInterview = (cand: Candidate) => {
    setPreSelectedCandidate(cand);
    setCurrentView("INTERVIEW_CENTER");
    // 同步新候选人到全局列表，确保面试中心下拉框可选
    setCandidates(prev => {
      if (prev.some(c => c.id === cand.id)) return prev;
      return [cand, ...prev];
    });
  };

  // 登录成功回调
  const handleLoginSuccess = () => {
    setLoggedIn(true);
  };

  // 退出登录
  const handleLogout = () => {
    clearToken();
    setLoggedIn(false);
  };

  // Switch View name label maps
  const getViewTitle = () => {
    switch (currentView) {
      case "RESUME_ANALYSIS":
        return "简历分析";
      case "RESUME_MANAGE":
        return "简历管理";
      case "TALENT_POOL":
        return "人才库评估";
      case "INTERVIEW_CENTER":
        return "面试中心";
      case "MOCK_INTERVIEW":
        return "模拟面试";
      case "INTERVIEW_RECORDS":
        return "面试历史记录";
      case "SCHEDULE":
        return "日程安排";
      case "KNOWLEDGE_BASE":
        return "知识库管理";
      case "KNOWLEDGE_QA":
        return "知识问答助手";
      default:
        return "系统仪表盘";
    }
  };

  // 启动时验证 Token 是否有效
  useEffect(() => {
    const token = getToken();
    if (!token) {
      setAuthChecking(false);
      return;
    }
    // 调用 /api/auth/me 验证 token 是否仍然有效
    fetch(`${API_BASE}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(res => {
        if (res.ok) setLoggedIn(true);
        else clearToken();
      })
      .catch(() => clearToken())
      .finally(() => setAuthChecking(false));
  }, []);

  // 校验中显示加载中（登录页），校验完毕后才决定显示什么
  if (authChecking) {
    return <LoginView onLoginSuccess={handleLoginSuccess} />;
  }

  // 未登录 → 显示登录页
  if (!loggedIn) {
    return <LoginView onLoginSuccess={handleLoginSuccess} />;
  }

  const authUser = getStoredUser();

  return (
    <div className="min-h-screen bg-[#F5F7FA] text-slate-800 flex flex-col font-sans selection:bg-primary/10 select-none">
      {/* Absolute top grid wrapper */}
      <div className="flex flex-1 items-stretch overflow-hidden">
        
        {/* Left Sidebar Menu Layout */}
        <aside
          className={`fixed inset-y-0 left-0 bg-white border-r border-slate-200 w-64 z-30 transition-transform transform md:translate-x-0 flex flex-col justify-between p-5 ${
            mobileSidebarOpen ? "translate-x-0" : "-translate-x-full"
          }`}
        >
          {/* Brand Logo & descriptive headers */}
          <div className="space-y-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary hover:bg-primary-container rounded-xl flex items-center justify-center text-white-pure shadow-md shadow-primary/25 cursor-pointer">
                <BrainCircuit className="w-5 h-5 animate-pulse" />
              </div>
              <div className="space-y-0.5">
                <h1 className="text-base font-bold text-primary flex items-center gap-1 font-sans">
                  RecruitAI
                </h1>
                <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider font-sans">
                  智能招聘套件
                </p>
              </div>
            </div>

            {/* Menu Links navigation */}
            <nav className="space-y-1.5 pt-2">
              {[
                { id: "RESUME_ANALYSIS", label: "简历分析", icon: FileText },
                { id: "RESUME_MANAGE", label: "简历管理", icon: ClipboardList },
                { id: "TALENT_POOL", label: "人才库", icon: Users },
                { id: "INTERVIEW_CENTER", label: "面试中心", icon: Video },
                { id: "MOCK_INTERVIEW", label: "模拟面试", icon: MessageSquareCode },
                { id: "INTERVIEW_RECORDS", label: "面试记录", icon: History },
                { id: "KNOWLEDGE_BASE", label: "知识库", icon: Database },
              ].map((item) => {
                const IconComponent = item.icon;
                const isActive = currentView === item.id;

                return (
                  <button
                    key={item.id}
                    onClick={() => {
                      setCurrentView(item.id as ActiveView);
                    }}
                    className={`w-full text-xs font-semibold py-3 px-4 rounded-xl flex items-center gap-3 transition-colors cursor-pointer justify-start ${
                      isActive
                        ? "bg-primary/10 text-primary font-bold shadow-sm border-l-4 border-primary"
                        : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                    }`}
                  >
                    <IconComponent className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </nav>
          </div>

          {/* User profile bottom corner */}
          <div className="border-t border-slate-200 pt-4 mt-6">
            <div className="flex items-center gap-3 group">
              {/* 头像：取 displayName 首字符 */}
              <div className="w-9 h-9 rounded-full bg-primary flex items-center justify-center text-white text-sm font-bold shadow-sm shrink-0">
                {authUser?.displayName?.charAt(0)?.toUpperCase() || "U"}
              </div>
              <div className="flex-1 min-w-0">
                <span className="text-xs font-bold text-slate-800 block truncate font-sans">
                  {authUser?.displayName || authUser?.username || "用户"}
                </span>
                <span className="text-[9px] text-slate-400 block truncate font-mono">
                  {authUser?.username || ""}
                </span>
              </div>
              <button
                onClick={handleLogout}
                title="退出登录"
                className="w-7 h-7 rounded-lg hover:bg-red-50 hover:text-red-500 flex items-center justify-center text-slate-400 transition cursor-pointer"
              >
                <LogOut className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </aside>

        {/* Mobile menu modal backdrop drawer background closer */}
        {mobileSidebarOpen && (
          <div
            className="fixed inset-0 z-20 bg-slate-900/30 backdrop-blur-xs md:hidden"
            onClick={() => setMobileSidebarOpen(false)}
          />
        )}

        {/* Main Content Dashboard Frame */}
        <main className="flex-1 flex flex-col min-w-0 overflow-y-auto md:ml-64">
          {/* Header Top Nav bar */}
          <header className="bg-white/80 backdrop-blur-md border-b border-slate-200 py-4 px-6 flex items-center justify-between gap-4 sticky top-0 z-10">
            {/* Left section displays screen name or mobile toggler */}
            <div className="flex items-center gap-3">
              <button
                onClick={() => setMobileSidebarOpen(!mobileSidebarOpen)}
                className="w-9 h-9 rounded-lg border border-slate-200 flex items-center justify-center hover:bg-slate-50 md:hidden cursor-pointer"
              >
                <Menu className="w-4.5 h-4.5" />
              </button>

              <h2 className="text-base font-extrabold text-slate-800 font-sans tracking-tight">
                {getViewTitle()}
              </h2>
            </div>

            {/* Right section icons and profile notifications */}
            <div className="flex items-center gap-3">
              <div className="relative cursor-pointer hover:scale-105 transition">
                <div className="absolute top-1 right-1 w-2 h-2 rounded-full bg-red-500 animate-pulse border-2 border-white" />
                <button className="w-9 h-9 rounded-xl border border-slate-200 bg-white flex items-center justify-center hover:bg-slate-50 cursor-pointer">
                  <Bell className="w-4 h-4 text-slate-500" />
                </button>
              </div>

              <div className="w-px h-6 bg-slate-200" />

              {/* Developer credentials */}
              <div className="text-right hidden md:block">
                <span className="text-[10px] text-slate-400 block font-semibold uppercase tracking-wider font-sans">
                  AI招聘协作
                </span>
                <span className="text-xs font-bold text-slate-700 font-sans">
                  {authUser?.displayName || authUser?.username || "Guo"}
                </span>
              </div>
            </div>
          </header>

          {/* Active View Router Content Container */}
          <div className="p-6 max-w-7xl mx-auto w-full flex-1">
            {currentView === "RESUME_ANALYSIS" && (
              <ResumeAnalysisView
                onAddCandidate={handleAddCandidate}
                onNavigateToInterview={handleNavigateToInterview}
                onNavigateToMock={handleNavigateToMock}
              />
            )}

            {currentView === "RESUME_MANAGE" && (
              <ResumeManageView />
            )}

            {currentView === "TALENT_POOL" && (
              <TalentPoolView
                onNavigateToMock={handleNavigateToMock}
                onNavigateToInterview={handleNavigateToInterview}
                onAddInterview={handleAddInterview}
              />
            )}

            {currentView === "INTERVIEW_CENTER" && (
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
            )}

            {currentView === "MOCK_INTERVIEW" && (
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
            )}

            {currentView === "INTERVIEW_RECORDS" && (
              <InterviewRecordsView scoreCards={scoreCards} onDeleteScoreCard={handleDeleteScoreCard} />
            )}

            {currentView === "SCHEDULE" && (
              <ScheduleView interviews={interviews} />
            )}

            {currentView === "KNOWLEDGE_BASE" && (
              <KnowledgeBaseView
                onNavigateToQA={() => setCurrentView("KNOWLEDGE_QA")}
              />
            )}

            {currentView === "KNOWLEDGE_QA" && (
              <KnowledgeQAView
                onNavigateBack={() => setCurrentView("KNOWLEDGE_BASE")}
              />
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
