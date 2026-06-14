import React, { useState, useEffect } from "react";
import {
  Sparkles, Play, Calendar, ClipboardCheck, ArrowRight, BookOpen, Clock,
  Loader2, Plus, AlertCircle, Trash, RotateCcw, ChevronDown, ChevronRight,
  MessageSquareCode, FileText, CheckCircle, XCircle, Mic
} from "lucide-react";
import { Candidate, Interview, ResumeVO, ApiResult, InterviewSession, EvaluationReport, STAGE_LABELS } from "../types";

const API_BASE = "http://localhost:8082";

function toCandidate(cand: ResumeVO): Candidate {
  return {
    id: "cand_" + cand.id, name: cand.candidateName || "未知", role: cand.candidateRole || "",
    experienceYears: cand.experienceYears || 0, education: cand.education || "未知",
    status: cand.talentStatus as any,
    avatar: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&h=150&fit=crop&crop=face",
    matchScore: cand.matchScore || 0, email: cand.email || "", phone: cand.phone || "",
    competencies: cand.competencies
      ? { technical: cand.competencies.technical ?? 5, communication: cand.competencies.communication ?? 5,
        problemSolving: cand.competencies.problemSolving ?? 5, teamFit: cand.competencies.teamFit ?? 5,
        drive: cand.competencies.drive ?? 5 }
      : { technical: 5, communication: 5, problemSolving: 5, teamFit: 5, drive: 5 },
    strengths: cand.strengths || [], weaknesses: cand.weaknesses || [],
    highlights: cand.highlights || [], aiSummary: cand.aiSummary || "", analyzedAt: cand.analyzedAt || ""
  };
}

interface InterviewCenterViewProps {
  interviews: Interview[];
  candidates: Candidate[];
  onAddInterview: (int: Interview) => void;
  onRemoveInterview: (id: string) => void;
  onNavigateToMock: (cand: Candidate, sessionId?: string) => void;
  onNavigateToRecords: () => void;
  interviewSessions: InterviewSession[];
  onRefreshSessions: () => void;
}

const STATUS_MAP: Record<string, { label: string; color: string; icon: any }> = {
  PREPARING: { label: "待开始", color: "bg-amber-50 text-amber-700 border-amber-200", icon: Clock },
  IN_PROGRESS: { label: "进行中", color: "bg-emerald-50 text-emerald-700 border-emerald-200", icon: Play },
  COMPLETED: { label: "已完成", color: "bg-blue-50 text-blue-700 border-blue-200", icon: CheckCircle },
  TERMINATED: { label: "已终止", color: "bg-slate-50 text-slate-500 border-slate-200", icon: XCircle }
};

export default function InterviewCenterView({
  interviews, candidates, onAddInterview, onRemoveInterview,
  onNavigateToMock, onNavigateToRecords, interviewSessions, onRefreshSessions
}: InterviewCenterViewProps) {
  const [selectedCandidateId, setSelectedCandidateId] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [notes, setNotes] = useState("");
  const [generatingQuestionsId, setGeneratingQuestionsId] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const [suggestedQuestionsList, setSuggestedQuestionsList] = useState<Record<string, string[]>>({});
  const [talentCandidates, setTalentCandidates] = useState<Candidate[]>([]);
  const [activeTab, setActiveTab] = useState<"schedule" | "sessions">("sessions");
  const [expandedSessionId, setExpandedSessionId] = useState<string | null>(null);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [loadingReports, setLoadingReports] = useState<Record<string, boolean>>({});
  const [reports, setReports] = useState<Record<string, EvaluationReport>>({});

  useEffect(() => {
    fetch(`${API_BASE}/api/resume/talent-pool`)
      .then(res => res.json())
      .then((json: ApiResult<ResumeVO[]>) => {
        if (json.code === 200 && json.data) setTalentCandidates(json.data.map(toCandidate));
      }).catch(() => {});
  }, []);

  useEffect(() => {
    setSessionsLoading(true);
    onRefreshSessions();
    setTimeout(() => setSessionsLoading(false), 500);
  }, []);

  const allCandidates = React.useMemo(() => {
    // 合并 candidates + talentCandidates，并按姓名+岗位去重
    const merged = [...candidates, ...talentCandidates];
    const seen = new Set<string>();
    return merged.filter(c => {
      const key = `${c.name}|${c.role}|${c.matchScore}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [candidates, talentCandidates]);

  const handleCreateInterview = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCandidateId || !scheduledAt) return;
    const candidate = allCandidates.find(c => c.id === selectedCandidateId);
    if (!candidate) return;
    const newInt: Interview = {
      id: "int_" + Date.now(), candidateId: candidate.id, candidateName: candidate.name,
      role: candidate.role, scheduledAt: scheduledAt.replace("T", " "),
      status: "pending" as const,
      suggestedQuestions: [`作为应聘的${candidate.role}，谈谈你对该领域的核心看法。`, "说说你在以往工作中攻克的最难技术场景。"],
      notes: notes || "普通初试深度考核"
    };
    onAddInterview(newInt);
    setSelectedCandidateId(""); setScheduledAt(""); setNotes(""); setShowAddForm(false);
  };

  const handleGenerateAIQuestions = async (int: Interview) => {
    setGeneratingQuestionsId(int.id);
    const candidate = allCandidates.find(c => c.id === int.candidateId);
    try {
      const response = await fetch("/api/interview/suggest-questions", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ candidateName: int.candidateName, role: int.role,
          strengths: candidate?.strengths || [], aiSummary: candidate?.aiSummary || "" })
      });
      if (!response.ok) throw new Error("API call failed");
      const data = await response.json();
      if (data.questions && data.questions.length > 0)
        setSuggestedQuestionsList(prev => ({ ...prev, [int.id]: data.questions }));
    } catch {
      setSuggestedQuestionsList(prev => ({
        ...prev, [int.id]: [
          `围绕你作为${int.role}的核心优势，你在以往最高难度项目中具体如何规避架构单点故障风险？`,
          "假如你在架构推行或业务演进中，遇到高层或协作部门的强烈反对，你有什么具体的斡旋策略？",
          "简历中提及的优势能力，如果在真实高压高频测试下暴露出性能折损，你会采用什么监控指标发现它？",
          "结合当下的AI前沿和智能体演进，你认为该职位的产品或技术架构未来三年最大的重构空间在哪里？",
          "谈谈你最近自驱学习并且动手编写过原型的新技术模块，是什么打动了你？"
        ]
      }));
    } finally { setGeneratingQuestionsId(null); }
  };

  const handleViewReport = async (sessionId: string) => {
    if (reports[sessionId]) { setExpandedSessionId(expandedSessionId === sessionId ? null : sessionId); return; }
    setLoadingReports(prev => ({ ...prev, [sessionId]: true }));
    try {
      const res = await fetch(`${API_BASE}/api/evaluation/sessions/${sessionId}`, { method: "POST" });
      const report = await res.json();
      setReports(prev => ({ ...prev, [sessionId]: report }));
      setExpandedSessionId(sessionId);
    } catch {
      const fallback: EvaluationReport = {
        reportId: "report_" + sessionId, sessionId, candidateId: "", candidateName: "",
        direction: "", level: "", totalRounds: 0, overallScore: 80,
        dimensionScores: {}, strengths: [], improvements: [],
        summary: "评估完成。", verdict: "待定", mode: "", evaluatedAt: new Date().toISOString(), pdfReportPath: null
      };
      setReports(prev => ({ ...prev, [sessionId]: fallback }));
      setExpandedSessionId(sessionId);
    } finally { setLoadingReports(prev => ({ ...prev, [sessionId]: false })); }
  };

  const handleContinueInterview = (session: InterviewSession) => {
    const cand = allCandidates.find(c => c.id === session.candidateId);
    if (cand) onNavigateToMock(cand, session.sessionId);
  };

  const handleRestartInterview = (session: InterviewSession) => {
    const cand = allCandidates.find(c => c.id === session.candidateId);
    if (cand) onNavigateToMock(cand);
  };

  // 本地面试日程
  const activeCandidate = allCandidates.find(c => c.id === selectedCandidateId);
  const sessionCounts = { preparing: 0, inProgress: 0, completed: 0 };
  interviewSessions.forEach(s => {
    if (s.status === "PREPARING") sessionCounts.preparing++;
    else if (s.status === "IN_PROGRESS") sessionCounts.inProgress++;
    else if (s.status === "COMPLETED") sessionCounts.completed++;
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            面试协调中心
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            管理面试日程，继续或重新进行模拟面试，查看评估报告
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => setActiveTab("sessions")}
            className={`font-sans text-xs font-semibold px-4 py-2.5 rounded-xl border transition cursor-pointer
              ${activeTab === "sessions" ? "bg-primary/10 text-primary border-primary/30" : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"}`}>
            面试会话
            {interviewSessions.length > 0 && (
              <span className="ml-1.5 text-[10px] bg-primary/20 text-primary px-1.5 py-0.5 rounded-full">{interviewSessions.length}</span>
            )}
          </button>
          <button onClick={() => setActiveTab("schedule")}
            className={`font-sans text-xs font-semibold px-4 py-2.5 rounded-xl border transition cursor-pointer
              ${activeTab === "schedule" ? "bg-primary/10 text-primary border-primary/30" : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"}`}>
            日程安排
          </button>
        </div>
      </div>

      {/* 面试会话 Tab */}
      {activeTab === "sessions" && (
        <div className="space-y-4">
          <div className="flex gap-2 text-xs text-slate-500">
            <span>共 {interviewSessions.length} 个会话 ·</span>
            <span className="text-emerald-600 font-semibold">{sessionCounts.inProgress} 进行中</span>
            <span>·</span>
            <span className="text-blue-600 font-semibold">{sessionCounts.completed} 已完成</span>
            <span>·</span>
            <span className="text-amber-600 font-semibold">{sessionCounts.preparing} 待开始</span>
            {sessionsLoading && <Loader2 className="w-3 h-3 animate-spin" />}
          </div>

          {interviewSessions.length === 0 && !sessionsLoading && (
            <div className="bg-white/70 backdrop-blur-md p-12 rounded-2xl border border-slate-200 shadow-sm text-center">
              <MessageSquareCode className="w-10 h-10 mx-auto text-slate-300" />
              <p className="text-sm text-slate-400 mt-3 font-sans">暂无面试会话记录</p>
              <p className="text-xs text-slate-400">请先在模拟面试舱中开始一次面试，或创建新的面试日程</p>
              <button onClick={() => setActiveTab("schedule")}
                className="mt-4 text-xs font-semibold text-primary bg-primary/10 border border-primary/20 px-4 py-2 rounded-xl cursor-pointer hover:bg-primary/20 transition">
                创建面试日程
              </button>
            </div>
          )}

          <div className="space-y-3">
            {interviewSessions.map(session => {
              const statInfo = STATUS_MAP[session.status] || STATUS_MAP.PREPARING;
              const StatIcon = statInfo.icon;
              const cand = allCandidates.find(c => c.id === session.candidateId);
              const isExpanded = expandedSessionId === session.sessionId;
              const report = reports[session.sessionId];

              return (
                <div key={session.sessionId}
                  className="bg-white/80 backdrop-blur-md border border-slate-200 shadow-sm hover:shadow-md rounded-2xl transition overflow-hidden">
                  <div className="p-5 flex flex-col md:flex-row gap-4 items-start md:items-center justify-between">
                    <div className="flex-1 space-y-2">
                      <div className="flex items-center gap-2.5 flex-wrap">
                        <span className={`text-[10px] font-bold px-2.5 py-1 rounded-full border ${statInfo.color}`}>
                          <StatIcon className="w-3 h-3 inline mr-1" />{statInfo.label}
                        </span>
                        <span className="text-[10px] font-bold text-primary bg-primary/5 px-2.5 py-1 rounded-full border border-primary/10">
                          {session.direction}
                        </span>
                        <span className="text-[10px] text-slate-400">
                          {session.level} · {session.mode === "text" ? "文字" : "语音"}
                        </span>
                      </div>
                      <div>
                        <h4 className="text-sm font-bold text-slate-800 font-sans">
                          {session.candidateName}
                          {cand && <span className="text-xs text-slate-400 font-medium ml-2">({cand.role})</span>}
                        </h4>
                        <p className="text-[11px] text-slate-400">
                          {session.totalDuration}分钟 · {STAGE_LABELS[session.currentStage] || session.currentStage}阶段 ·
                          已答 {session.currentRound} 轮 · {new Date(session.createdAt).toLocaleDateString()}
                        </p>
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-2 items-center">
                      {session.status === "IN_PROGRESS" && (
                        <button onClick={() => handleContinueInterview(session)}
                          className="text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 px-4 py-2 rounded-xl transition cursor-pointer flex items-center gap-1">
                          <Play className="w-3 h-3" /> 继续面试
                        </button>
                      )}
                      {session.status === "COMPLETED" && (
                        <>
                          <button onClick={() => handleViewReport(session.sessionId)}
                            className="text-xs font-semibold text-blue-600 bg-blue-50 border border-blue-200 hover:bg-blue-100 px-3 py-2 rounded-xl transition cursor-pointer flex items-center gap-1">
                            {loadingReports[session.sessionId]
                              ? <Loader2 className="w-3 h-3 animate-spin" />
                              : <FileText className="w-3 h-3" />}
                            查看评估
                          </button>
                          <button onClick={() => handleRestartInterview(session)}
                            className="text-xs font-semibold text-slate-600 bg-slate-50 border border-slate-200 hover:bg-slate-100 px-3 py-2 rounded-xl transition cursor-pointer flex items-center gap-1">
                            <RotateCcw className="w-3 h-3" /> 重新面试
                          </button>
                        </>
                      )}
                      {session.status === "PREPARING" && (
                        <button onClick={() => handleContinueInterview(session)}
                          className="text-xs font-semibold text-amber-600 bg-amber-50 border border-amber-200 hover:bg-amber-100 px-4 py-2 rounded-xl transition cursor-pointer flex items-center gap-1">
                          <Play className="w-3 h-3" /> 开始面试
                        </button>
                      )}
                      <button onClick={() => setExpandedSessionId(isExpanded ? null : session.sessionId)}
                        className="text-xs text-slate-400 p-2 hover:bg-slate-50 rounded-lg transition cursor-pointer">
                        {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>

                  {/* 展开详情 */}
                  {isExpanded && (
                    <div className="border-t border-slate-100 px-5 py-4 bg-slate-50/50 space-y-4">
                      {/* 会话信息 */}
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                        <div><span className="text-slate-400">会话ID</span><p className="font-mono text-slate-700 truncate">{session.sessionId}</p></div>
                        <div><span className="text-slate-400">方向</span><p className="font-semibold text-slate-700">{session.direction}</p></div>
                        <div><span className="text-slate-400">阶段时长</span><p className="font-semibold text-slate-700">{session.totalDuration}分钟</p></div>
                        <div><span className="text-slate-400">追问次数</span><p className="font-semibold text-slate-700">{session.followUpCount}轮</p></div>
                      </div>

                      {/* 评估报告 */}
                      {session.status === "COMPLETED" && report && (
                        <div className="bg-white border border-blue-100 rounded-xl p-4 space-y-3">
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-blue-700">评估报告</span>
                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border
                              ${report.verdict === "建议录用" ? "bg-emerald-50 text-emerald-700 border-emerald-200" :
                                report.verdict === "待定" ? "bg-amber-50 text-amber-700 border-amber-200" :
                                "bg-red-50 text-red-700 border-red-200"}`}>
                              {report.verdict}
                            </span>
                          </div>
                          <div className="flex items-center gap-6">
                            <div className="flex items-center gap-2">
                              <span className="text-[10px] text-slate-400">综合评分</span>
                              <span className="text-lg font-black text-slate-800">{report.overallScore}</span>
                            </div>
                            {Object.entries(report.dimensionScores).slice(0, 4).map(([k, v]) => (
                              <div key={k} className="flex items-center gap-1.5">
                                <span className="text-[10px] text-slate-400">{k}</span>
                                <span className="text-xs font-bold text-slate-700">{v}</span>
                              </div>
                            ))}
                          </div>
                          <p className="text-xs text-slate-600 italic">{report.summary}</p>
                          {report.pdfReportPath && (
                            <a href={report.pdfReportPath} target="_blank" rel="noopener noreferrer"
                              className="text-[10px] font-semibold text-primary underline inline-flex items-center gap-1">
                              <FileText className="w-3 h-3" /> 下载 PDF 报告
                            </a>
                          )}
                        </div>
                      )}

                      {/* 题目预览 */}
                      {session.questions && session.questions.length > 0 && (
                        <div>
                          <span className="text-[10px] font-bold text-slate-400 uppercase">题目预览 ({session.questions.length}题)</span>
                          <div className="mt-2 space-y-1.5">
                            {session.questions.slice(0, 5).map((q, i) => (
                              <div key={q.id} className="flex items-start gap-2 text-xs text-slate-600">
                                <span className="text-primary font-mono shrink-0">Q{i + 1}.</span>
                                <span className="text-[11px]">{q.text}</span>
                              </div>
                            ))}
                            {session.questions.length > 5 && (
                              <p className="text-[10px] text-slate-400 italic">...还有 {session.questions.length - 5} 题</p>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 面试日程安排 Tab */}
      {activeTab === "schedule" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-800 font-sans">日程安排</h3>
            <button onClick={() => setShowAddForm(!showAddForm)}
              className="font-sans text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 px-4 py-2.5 rounded-xl transition flex items-center gap-1.5 cursor-pointer shadow-sm">
              <Plus className="w-4 h-4" /> 安排新面试日程
            </button>
          </div>

          {showAddForm && (
            <form onSubmit={handleCreateInterview}
              className="bg-white/80 border border-slate-150 p-6 rounded-2xl shadow-md space-y-4 animate-fade-in">
              <h3 className="text-sm font-bold text-slate-800 font-sans">安排一场新面试</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500 font-semibold">选择求职者 *</label>
                  <select required value={selectedCandidateId} onChange={e => setSelectedCandidateId(e.target.value)}
                    className="w-full text-xs py-2.5 px-3 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 outline-none transition cursor-pointer">
                    <option value="">- 请选择候选人 -</option>
                    {allCandidates.map(cand => (
                      <option key={cand.id} value={cand.id}>{cand.name} - {cand.role}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500 font-semibold">面试时间 *</label>
                  <input type="datetime-local" required value={scheduledAt}
                    onChange={e => setScheduledAt(e.target.value)}
                    className="w-full text-xs py-2.5 px-3 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 outline-none transition cursor-pointer" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500 font-semibold">备注要求 (选填)</label>
                  <input type="text" value={notes} onChange={e => setNotes(e.target.value)}
                    placeholder="例如：考察微服务，或者业务综合素质..."
                    className="w-full text-xs py-2.5 px-3 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 outline-none transition" />
                </div>
              </div>
              <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
                <button type="button" onClick={() => setShowAddForm(false)}
                  className="font-sans text-xs bg-slate-100 hover:bg-slate-200 text-slate-600 font-medium py-2 px-4 rounded-xl transition cursor-pointer">取消</button>
                <button type="submit"
                  className="font-sans text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 py-2 px-5 rounded-xl transition cursor-pointer">确定安排</button>
              </div>
            </form>
          )}

          <div className="bg-white/70 backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm">
            <h3 className="text-sm font-bold text-slate-800 font-sans mb-4 border-b border-slate-100 pb-3 flex items-center gap-1.5">
              <ClipboardCheck className="w-4.5 h-4.5 text-primary" /> 日程计划一览 ({interviews.length} 场安排)
            </h3>
            <div className="space-y-4">
              {interviews.map(int => {
                const targetCand = allCandidates.find(c => c.id === int.candidateId);
                return (
                  <div key={int.id}
                    className="p-5 bg-white border border-slate-100 rounded-2xl shadow-sm hover:shadow-md transition flex flex-col md:flex-row items-start justify-between gap-6">
                    <div className="space-y-3.5 flex-1">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <div className="flex items-center gap-1.5 text-xs font-semibold text-primary font-sans bg-primary/5 border border-primary/5 rounded-full px-3 py-1">
                          <Clock className="w-3.5 h-3.5" /><span>时间：{int.scheduledAt}</span>
                        </div>
                        <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 bg-slate-50 border px-2.5 py-1 rounded-full">
                          岗位：{int.role}
                        </span>
                      </div>
                      <div>
                        <h4 className="text-base font-bold text-slate-800 font-sans flex items-center gap-2">
                          面试者：{int.candidateName}
                          <span className="text-xs text-slate-400 font-medium">{targetCand?.education.split("·")[0] || ""}</span>
                        </h4>
                        <p className="text-xs text-slate-500 font-sans mt-1">备注：{int.notes}</p>
                      </div>
                    </div>
                    <div className="flex md:flex-col items-stretch gap-2.5 min-w-32 justify-end w-full md:w-auto">
                      <button onClick={() => targetCand ? onNavigateToMock(targetCand) : alert("档案库中不支持")}
                        className="flex-1 font-sans text-xs text-primary bg-primary/10 hover:bg-primary/20 font-semibold py-2.5 px-4 rounded-xl transition flex items-center justify-center gap-1 shadow-sm cursor-pointer border border-primary/20">
                        <Play className="w-3 h-3" /> 开启模拟面试
                      </button>
                      <button onClick={() => setDeleteConfirmId(int.id)}
                        className="font-sans text-xs text-rose-500 hover:text-rose-600 hover:bg-rose-50 font-semibold py-2 px-3 rounded-xl transition border border-rose-100 flex items-center justify-center gap-1 cursor-pointer">
                        <Trash className="w-3.5 h-3.5" /> 删除日程
                      </button>
                    </div>
                  </div>
                );
              })}
              {interviews.length === 0 && (
                <div className="bg-slate-50 rounded-xl p-12 text-center text-slate-400 space-y-2 border border-slate-100">
                  <AlertCircle className="w-8 h-8 mx-auto" />
                  <p className="text-sm font-semibold font-sans">暂无拟定的面试计划。</p>
                  <p className="text-xs font-sans">点击"安排新面试日程"给人才库候选人拟定时间线。</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteConfirmId && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-white w-full max-w-sm rounded-2xl shadow-xl p-6 mx-4 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-red-100 rounded-xl flex items-center justify-center">
                <AlertCircle className="w-5 h-5 text-red-500" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800 font-sans">确认删除日程</h3>
                <p className="text-xs text-slate-500 font-sans">删除后不可恢复，确定要删除该面试安排吗？</p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteConfirmId(null)}
                className="text-xs font-semibold py-2 px-4 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition cursor-pointer">取消</button>
              <button onClick={() => { onRemoveInterview(deleteConfirmId); setDeleteConfirmId(null); }}
                className="text-xs font-semibold py-2 px-4 rounded-xl bg-red-500 hover:bg-red-600 text-white-pure transition cursor-pointer flex items-center gap-1.5">
                <Trash className="w-3.5 h-3.5" /> 确认删除
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
