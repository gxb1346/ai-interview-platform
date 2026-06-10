/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import {
  FileText,
  Users,
  Film,
  MessageSquareCode,
  History,
  Calendar,
  Sparkles,
  Search,
  Bell,
  Menu,
  ChevronDown,
  LogOut,
  BrainCircuit,
  MessageSquare,
  Video
} from "lucide-react";

import { Candidate, CandidateStatus, Interview, ScoreCard } from "./types";
import { PRESEEDED_CANDIDATES, PRESEEDED_INTERVIEWS, PRESEEDED_SCORECARDS } from "./data";

// Sub-views
import ResumeAnalysisView from "./components/ResumeAnalysisView";
import TalentPoolView from "./components/TalentPoolView";
import InterviewCenterView from "./components/InterviewCenterView";
import MockInterviewView from "./components/MockInterviewView";
import InterviewRecordsView from "./components/InterviewRecordsView";
import ScheduleView from "./components/ScheduleView";

type ActiveView =
  | "RESUME_ANALYSIS"
  | "TALENT_POOL"
  | "INTERVIEW_CENTER"
  | "MOCK_INTERVIEW"
  | "INTERVIEW_RECORDS"
  | "SCHEDULE";

export default function App() {
  const [currentView, setCurrentView] = useState<ActiveView>("RESUME_ANALYSIS");
  
  // App Global Sync States
  const [candidates, setCandidates] = useState<Candidate[]>(PRESEEDED_CANDIDATES);
  const [interviews, setInterviews] = useState<Interview[]>(PRESEEDED_INTERVIEWS);
  const [scoreCards, setScoreCards] = useState<ScoreCard[]>(PRESEEDED_SCORECARDS);
  const [preSelectedCandidate, setPreSelectedCandidate] = useState<Candidate | null>(null);

  // Search input matching local list
  const [globalSearchTerm, setGlobalSearchTerm] = useState("");

  // Sidebar toggle state on mobile devices
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

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

  const handleNavigateToMock = (cand: Candidate) => {
    setPreSelectedCandidate(cand);
    setCurrentView("MOCK_INTERVIEW");
  };

  const handleNavigateToInterview = (cand: Candidate) => {
    setPreSelectedCandidate(cand);
    setCurrentView("INTERVIEW_CENTER");
  };

  // Switch View name label maps
  const getViewTitle = () => {
    switch (currentView) {
      case "RESUME_ANALYSIS":
        return "简历分析";
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
      default:
        return "系统仪表盘";
    }
  };

  return (
    <div className="min-h-screen bg-[#faf8ff] text-[#131b2e] flex flex-col font-sans selection:bg-primary/10 select-none">
      {/* Absolute top grid wrapper */}
      <div className="flex flex-1 items-stretch overflow-hidden">
        
        {/* Left Sidebar Menu Layout */}
        <aside
          className={`fixed inset-y-0 left-0 bg-white border-r border-[#eaedff] w-64 z-30 transition-transform transform md:translate-x-0 flex flex-col justify-between p-5 md:static ${
            mobileSidebarOpen ? "translate-x-0" : "-translate-x-full"
          }`}
        >
          {/* Brand Logo & descriptive headers */}
          <div className="space-y-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary hover:bg-primary-container rounded-xl flex items-center justify-center text-white shadow-md shadow-primary/25 cursor-pointer">
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
                { id: "TALENT_POOL", label: "人才库", icon: Users },
                { id: "INTERVIEW_CENTER", label: "面试中心", icon: Video },
                { id: "MOCK_INTERVIEW", label: "模拟面试", icon: MessageSquareCode },
                { id: "INTERVIEW_RECORDS", label: "面试记录", icon: History },
                { id: "SCHEDULE", label: "日程安排", icon: Calendar },
              ].map((item) => {
                const IconComponent = item.icon;
                const isActive = currentView === item.id;

                return (
                  <button
                    key={item.id}
                    onClick={() => {
                      setCurrentView(item.id as ActiveView);
                      setMobileSidebarOpen(false);
                    }}
                    className={`w-full text-xs font-semibold py-3 px-4 rounded-xl flex items-center gap-3 transition-colors cursor-pointer justify-start ${
                      isActive
                        ? "bg-primary text-white shadow-md shadow-primary/15"
                        : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
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
          <div className="border-t border-[#eaedff] pt-4 mt-6">
            <div className="flex items-center gap-3 group">
              <img
                src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&h=150&fit=crop&crop=face"
                alt="user avatar"
                referrerPolicy="no-referrer"
                className="w-9 h-9 rounded-full object-cover border-2 border-slate-100 shadow-sm"
              />
              <div className="flex-1 min-w-0">
                <span className="text-xs font-bold text-slate-800 block truncate font-sans">
                  AI招聘组长
                </span>
                <span className="text-[9px] text-slate-400 block truncate font-mono">
                  guo99039@gmail.com
                </span>
              </div>
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
        <main className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header Top Nav bar */}
          <header className="bg-white/80 backdrop-blur-md border-b border-[#eaedff] py-4 px-6 flex items-center justify-between gap-4 sticky top-0 z-10">
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

            {/* Middle search bar matching local list search constraints */}
            <div className="relative max-w-sm w-full hidden sm:block">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="text"
                value={globalSearchTerm}
                onChange={(e) => {
                  setGlobalSearchTerm(e.target.value);
                  // Dynamic keyword forwarding directly to search panels
                  if (currentView !== "TALENT_POOL" && currentView !== "INTERVIEW_RECORDS") {
                    setCurrentView("TALENT_POOL");
                  }
                }}
                placeholder="智搜简历人才、岗位关键词..."
                className="w-full text-xs pl-10 pr-4 py-2 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 focus:border-primary outline-none transition font-sans"
              />
            </div>

            {/* Right section icons and profile notifications */}
            <div className="flex items-center gap-3">
              <div className="relative cursor-pointer hover:scale-105 transition">
                <div className="absolute top-1 right-1 w-2 h-2 rounded-full bg-red-500 animate-pulse border-2 border-white" />
                <button className="w-9 h-9 rounded-xl border border-[#eaedff] bg-white flex items-center justify-center hover:bg-slate-50 cursor-pointer">
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
                  Guo
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

            {currentView === "TALENT_POOL" && (
              <TalentPoolView
                candidates={candidates}
                onSelectCandidate={handleNavigateToMock}
                onNavigateToMock={handleNavigateToMock}
                onNavigateToInterview={handleNavigateToInterview}
              />
            )}

            {currentView === "INTERVIEW_CENTER" && (
              <InterviewCenterView
                interviews={interviews}
                candidates={candidates}
                onAddInterview={handleAddInterview}
                onRemoveInterview={handleRemoveInterview}
                onNavigateToMock={handleNavigateToMock}
              />
            )}

            {currentView === "MOCK_INTERVIEW" && (
              <MockInterviewView
                candidates={candidates}
                preSelectedCandidate={preSelectedCandidate}
                onSaveScoreCard={handleSaveScoreCard}
                onNavigateToRecords={() => {
                  setPreSelectedCandidate(null);
                  setCurrentView("INTERVIEW_RECORDS");
                }}
              />
            )}

            {currentView === "INTERVIEW_RECORDS" && (
              <InterviewRecordsView scoreCards={scoreCards} />
            )}

            {currentView === "SCHEDULE" && (
              <ScheduleView interviews={interviews} />
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
