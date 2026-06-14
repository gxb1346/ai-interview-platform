/**
 * 人才库评估视图 — 从后端 API 获取已移入人才库的候选人
 * 支持状态筛选、查看详情、面试/模拟面试跳转、状态流转
 */
import React, { useState, useEffect, useCallback } from "react";
import {
  Search, X, Sparkles, Star, Play, Award, CheckCircle2,
  AlertCircle, ChevronRight, UserCheck, UserX, Clock, Mail, Phone, Calendar
} from "lucide-react";
import { ResumeVO, ApiResult, TalentStatus, Interview } from "../types";

const API_BASE = "http://localhost:8082";

interface TalentPoolViewProps {
  onNavigateToMock: (cand: any) => void;
  onNavigateToInterview: (cand: any) => void;
  onAddInterview: (int: Interview) => void;
}

/** 状态中文映射 */
const STATUS_LABELS: Record<string, string> = {
  NEW: "已评估",
  INVITED: "已邀约",
  WAITING_INTERVIEW: "待面试",
  PASSED: "面试通过",
  REJECTED: "不合适"
};

const STATUS_COLORS: Record<string, string> = {
  NEW: "bg-slate-100 text-slate-600 border-slate-200",
  INVITED: "bg-blue-50 text-blue-700 border-blue-100",
  WAITING_INTERVIEW: "bg-amber-50 text-amber-700 border-amber-100",
  PASSED: "bg-emerald-50 text-emerald-700 border-emerald-100",
  REJECTED: "bg-red-50 text-red-700 border-red-100"
};

export default function TalentPoolView({
  onNavigateToMock,
  onNavigateToInterview,
  onAddInterview
}: TalentPoolViewProps) {
  const [candidates, setCandidates] = useState<ResumeVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedStatus, setSelectedStatus] = useState<string>("ALL");
  const [drawerCandidate, setDrawerCandidate] = useState<ResumeVO | null>(null);
  // 更新状态 loading
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  // 面试日程安排弹窗
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [scheduledDate, setScheduledDate] = useState("");
  const [scheduledTime, setScheduledTime] = useState("");
  const [scheduleNotes, setScheduleNotes] = useState("");

  // 移出人才库
  const [removingId, setRemovingId] = useState<number | null>(null);

  const handleRemoveFromTalentPool = async (id: number) => {
    if (!confirm("确定将该候选人移出人才库？")) return;
    setRemovingId(id);
    try {
      const res = await fetch(`${API_BASE}/api/resume/${id}/remove-from-talent-pool`, { method: "DELETE" });
      const json: ApiResult<ResumeVO> = await res.json();
      if (json.code === 200) {
        setCandidates(prev => prev.filter(c => c.id !== id));
        if (drawerCandidate?.id === id) setDrawerCandidate(null);
      }
    } catch (err: any) {
      console.error("移出人才库失败:", err);
    } finally {
      setRemovingId(null);
    }
  };

  const fetchTalentPool = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/resume/talent-pool`);
      const json: ApiResult<ResumeVO[]> = await res.json();
      if (json.code === 200) {
        setCandidates(json.data || []);
      } else {
        setError(json.message || "加载失败");
      }
    } catch (err: any) {
      setError("网络错误: " + err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchTalentPool(); }, [fetchTalentPool]);

  // 筛选
  const filteredCandidates = candidates.filter((cand) => {
    const matchesSearch =
      (cand.candidateName || "").toLowerCase().includes(searchTerm.toLowerCase()) ||
      (cand.candidateRole || "").toLowerCase().includes(searchTerm.toLowerCase()) ||
      (cand.education || "").toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = selectedStatus === "ALL" || cand.talentStatus === selectedStatus;
    return matchesSearch && matchesStatus;
  });

  // 待面试状态处理 - 弹出时间安排
  const handleWaitingInterviewClick = () => {
    setScheduledDate("");
    setScheduledTime("");
    setScheduleNotes("");
    setShowScheduleModal(true);
  };

  // 确认安排面试时间
  const handleConfirmSchedule = () => {
    if (!scheduledDate) {
      alert("请先选择面试日期");
      return;
    }
    if (!scheduledTime) {
      alert("请先选择面试时间");
      return;
    }
    if (!drawerCandidate) return;

    const cand = drawerCandidate;
    const newInt: Interview = {
      id: "int_" + Date.now(),
      candidateId: "cand_" + cand.id,
      candidateName: cand.candidateName || "未知",
      role: cand.candidateRole || "",
      scheduledAt: scheduledDate + " " + scheduledTime,
      status: "pending",
      suggestedQuestions: [
        `作为应聘的${cand.candidateRole || "该岗位"}的候选人，谈谈你对该领域的核心看法。`,
        "说说你在以往工作中攻克的最难技术场景。"
      ],
      notes: scheduleNotes || "待面试安排"
    };
    onAddInterview(newInt);

    handleUpdateStatus(cand.id, "WAITING_INTERVIEW");
    setShowScheduleModal(false);
    setDrawerCandidate(null);
    onNavigateToInterview(toCandidate(cand));
  };

  // 更新候选人在人才库中的状态
  const handleUpdateStatus = async (id: number, status: string) => {
    setUpdatingId(id);
    try {
      const res = await fetch(`${API_BASE}/api/resume/${id}/talent-status`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ talentStatus: status })
      });
      const json: ApiResult<ResumeVO> = await res.json();
      if (json.code === 200) {
        setCandidates(prev => prev.map(c => c.id === id ? json.data : c));
        if (drawerCandidate?.id === id) setDrawerCandidate(json.data);
      }
    } catch (err: any) {
      console.error("更新状态失败:", err);
    } finally {
      setUpdatingId(null);
    }
  };

  // 五维雷达图
  const renderDrawerRadar = (comp: Record<string, number> | null) => {
    if (!comp) return null;
    const center = 80;
    const r = 50;
    const angles = [
      -Math.PI / 2,
      -Math.PI / 2 + (2 * Math.PI) / 5,
      -Math.PI / 2 + (4 * Math.PI) / 5,
      -Math.PI / 2 + (6 * Math.PI) / 5,
      -Math.PI / 2 + (8 * Math.PI) / 5,
    ];
    const keys = ["technical", "communication", "problemSolving", "teamFit", "drive"];
    const labels = ["研发", "沟通", "解方", "协同", "驱动"];

    const getPoints = (scale: number) =>
      angles.map((angle) => {
        const x = center + r * scale * Math.cos(angle);
        const y = center + r * scale * Math.sin(angle);
        return `${x},${y}`;
      }).join(" ");

    const scorePoints = angles.map((angle, i) => {
      const score = comp[keys[i]] || 8;
      const x = center + r * (score / 10) * Math.cos(angle);
      const y = center + r * (score / 10) * Math.sin(angle);
      return `${x},${y}`;
    }).join(" ");

    return (
      <svg width="160" height="150" className="overflow-visible mx-auto">
        {[0.3, 0.6, 1.0].map((scale, i) => (
          <polygon key={i} points={getPoints(scale)} fill="none" stroke="#e2e7ff" />
        ))}
        <polygon points={scorePoints} fill="rgba(0, 88, 190, 0.15)" stroke="#0058be" strokeWidth="2" />
        {angles.map((angle, i) => {
          const x = center + (r + 14) * Math.cos(angle);
          const y = center + (r + 14) * Math.sin(angle) + 4;
          return (
            <text key={i} x={x} y={y} fill="#54647a" fontSize="10" textAnchor="middle" fontWeight="500">
              {labels[i]}
            </text>
          );
        })}
      </svg>
    );
  };

  // 构建通用候选人对象（兼容 Candidate 接口）
  const toCandidate = (cand: ResumeVO) => ({
    id: "cand_" + cand.id,
    name: cand.candidateName || "未知",
    role: cand.candidateRole || "",
    experienceYears: cand.experienceYears || 0,
    education: cand.education || "未知",
    status: cand.talentStatus,
    avatar: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&h=150&fit=crop&crop=face",
    matchScore: cand.matchScore || 0,
    email: cand.email || "",
    phone: cand.phone || "",
    competencies: cand.competencies || { technical: 5, communication: 5, problemSolving: 5, teamFit: 5, drive: 5 },
    strengths: cand.strengths || [],
    weaknesses: cand.weaknesses || [],
    highlights: cand.highlights || [],
    aiSummary: cand.aiSummary || "",
    analyzedAt: cand.analyzedAt || ""
  });

  return (
    <div className="space-y-6" id="talent-pool-container">
      {/* 标题 */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            人才库管理中心
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            经AI评估后的候选人集中管理，支持状态流转与一键发起面试
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="bg-white/80 p-3 rounded-xl border border-slate-100 text-center min-w-20 shadow-sm">
            <span className="text-xs text-slate-400 block font-sans">总人才</span>
            <span className="text-lg font-bold font-mono text-slate-800">{candidates.length}</span>
          </div>
          <div className="bg-emerald-50/50 p-3 rounded-xl border border-emerald-100 text-center min-w-20 shadow-sm">
            <span className="text-xs text-emerald-800 font-medium block font-sans">已通过</span>
            <span className="text-lg font-extrabold font-mono text-emerald-600">
              {candidates.filter(c => c.talentStatus === "PASSED").length}
            </span>
          </div>
          <div className="bg-amber-50/50 p-3 rounded-xl border border-amber-100 text-center min-w-20 shadow-sm">
            <span className="text-xs text-amber-800 font-medium block font-sans">待面试</span>
            <span className="text-lg font-extrabold font-mono text-amber-600">
              {candidates.filter(c => c.talentStatus === "WAITING_INTERVIEW").length}
            </span>
          </div>
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="flex items-center gap-2.5 p-3.5 bg-red-50 rounded-xl border border-red-100 text-red-600 text-xs font-sans">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-auto cursor-pointer"><X className="w-3.5 h-3.5" /></button>
        </div>
      )}

      {/* 搜索+筛选 */}
      <div className="bg-white/70 backdrop-blur-md p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col md:flex-row items-center gap-4 justify-between">
        <div className="relative w-full md:w-80">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="搜索姓名、岗位、学历..."
            className="w-full text-xs pl-10 pr-4 py-2.5 bg-slate-100/50 focus:bg-white border border-slate-200 focus:border-primary outline-none rounded-xl transition font-sans"
          />
        </div>
        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          <button onClick={() => setSelectedStatus("ALL")}
            className={`text-xs font-semibold px-4 py-2 rounded-xl border transition cursor-pointer ${
              selectedStatus === "ALL"
                ? "bg-primary/10 text-primary font-bold border-primary shadow-sm"
                : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
            }`}>全部</button>
          {["NEW", "INVITED", "WAITING_INTERVIEW", "PASSED", "REJECTED"].map((status) => (
            <button key={status} onClick={() => setSelectedStatus(status)}
              className={`text-xs font-semibold px-3.5 py-2 rounded-xl border transition cursor-pointer ${
                selectedStatus === status
                  ? "bg-primary/10 text-primary font-bold border-primary shadow-sm"
                  : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
              }`}>{STATUS_LABELS[status]}</button>
          ))}
        </div>
      </div>

      {/* 加载状态 */}
      {loading && (
        <div className="p-12 text-center text-sm text-slate-400">加载中...</div>
      )}

      {/* 候选人列表 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredCandidates.map((cand) => (
          <div key={cand.id}
            onClick={() => setDrawerCandidate(cand)}
            className="bg-white/80 hover:bg-white backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition duration-300 flex flex-col justify-between cursor-pointer group hover:scale-[1.01]"
          >
            <div>
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-sm">
                    {(cand.candidateName || "?").charAt(0)}
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-800 font-sans flex items-center gap-1.5">
                      {cand.candidateName || "未知"}
                      <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${STATUS_COLORS[cand.talentStatus] || STATUS_COLORS.NEW}`}>
                        {STATUS_LABELS[cand.talentStatus] || cand.talentStatus}
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500 font-sans">{cand.education || "—"}</p>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">匹配度</span>
                  <span className="text-base font-extrabold text-primary font-mono block">{cand.matchScore || 0}%</span>
                </div>
              </div>

              <div className="mt-4 space-y-1">
                <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-400 block font-sans">
                  目标岗位
                </span>
                <span className="text-xs font-bold text-slate-700 font-sans block">{cand.candidateRole || "—"}</span>
              </div>

              {cand.competencies && (
                <div className="flex flex-wrap gap-1 mt-3">
                  <span className="text-[10px] bg-sky-50 text-sky-700 border border-sky-100/50 rounded px-1.5 py-0.5">
                    技术 {cand.competencies.technical || 0}/10
                  </span>
                  <span className="text-[10px] bg-purple-50 text-purple-700 border border-purple-100/50 rounded px-1.5 py-0.5">
                    沟通 {cand.competencies.communication || 0}/10
                  </span>
                  <span className="text-[10px] bg-red-50 text-red-700 border border-red-100/50 rounded px-1.5 py-0.5">
                    解决 {cand.competencies.problemSolving || 0}/10
                  </span>
                </div>
              )}

              <p className="text-[11px] text-slate-500 line-clamp-2 mt-4 font-sans bg-slate-50 p-2 rounded-lg border border-slate-100">
                “{cand.aiSummary || "暂无AI评估摘要"}”
              </p>
            </div>

            <div className="flex items-center justify-between border-t border-slate-100 pt-4 mt-4 opacity-80 group-hover:opacity-100 transition duration-200">
              <button onClick={(e) => { e.stopPropagation(); handleRemoveFromTalentPool(cand.id); }}
                disabled={removingId === cand.id}
                className="text-[10px] font-semibold text-red-500 bg-red-50 hover:bg-red-100 border border-red-200 px-2.5 py-1.5 rounded-lg transition cursor-pointer disabled:opacity-40">
                {removingId === cand.id ? "..." : "移出人才库"}
              </button>
              <div className="flex items-center gap-1.5">
                <button onClick={(e) => { e.stopPropagation(); onNavigateToInterview(toCandidate(cand)); }}
                  className="text-[10px] font-bold text-primary bg-primary/5 hover:bg-primary/10 border border-primary/10 px-2.5 py-1.5 rounded-lg transition cursor-pointer">
                  提纲设想
                </button>
                <button onClick={(e) => { e.stopPropagation(); onNavigateToMock(toCandidate(cand)); }}
                  className="text-[10px] font-bold text-primary bg-primary/10 hover:bg-primary/20 px-2.5 py-1.5 rounded-lg flex items-center gap-1 transition cursor-pointer">
                  <Play className="w-2.5 h-2.5" />
                  提枪面试
                </button>
              </div>
            </div>
          </div>
        ))}

        {!loading && filteredCandidates.length === 0 && (
          <div className="col-span-full bg-slate-50 rounded-2xl border border-slate-100/80 p-12 text-center text-slate-400 space-y-2">
            <X className="w-8 h-8 mx-auto text-slate-300" />
            <p className="text-sm font-semibold font-sans">暂无符合条件的人才</p>
            <p className="text-xs font-sans text-slate-400">请先在「简历管理」中将候选人移入人才库</p>
          </div>
        )}
      </div>

      {/* 候选人详情抽屉 */}
      {drawerCandidate && (
        <div className="fixed inset-0 z-50 overflow-hidden bg-slate-900/40 backdrop-blur-sm animate-fade-in flex justify-end">
          <div className="absolute inset-0 cursor-pointer" onClick={() => setDrawerCandidate(null)} />
          <div className="relative w-full max-w-2xl bg-white shadow-2xl h-full flex flex-col overflow-y-auto p-6 sm:p-8 space-y-6">
            {/* 头部 */}
            <div className="flex items-center justify-between border-b border-slate-100 pb-4">
              <div className="flex items-center gap-3">
                <Award className="w-6 h-6 text-primary" />
                <h2 className="text-lg font-bold text-slate-800 font-sans">候选人档案卡</h2>
              </div>
              <button onClick={() => setDrawerCandidate(null)}
                className="w-8 h-8 rounded-full border border-slate-100 flex items-center justify-center hover:bg-slate-50 transition cursor-pointer">
                <X className="w-4 h-4 text-slate-400" />
              </button>
            </div>

            <div className="space-y-6">
              {/* 基本信息 */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-slate-50 p-5 rounded-2xl border border-slate-120">
                <div className="flex items-center gap-4">
                  <div className="w-14 h-14 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-lg">
                    {(drawerCandidate.candidateName || "?").charAt(0)}
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-slate-800 font-sans flex items-center gap-2">
                      {drawerCandidate.candidateName || "未知"}
                      <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${STATUS_COLORS[drawerCandidate.talentStatus] || STATUS_COLORS.NEW}`}>
                        {STATUS_LABELS[drawerCandidate.talentStatus] || drawerCandidate.talentStatus}
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500 mt-0.5">{drawerCandidate.education || "—"} · {drawerCandidate.experienceYears || 0}年经验</p>
                    <p className="text-[11px] text-slate-400 font-mono mt-1">
                      ✉️ {drawerCandidate.email || "—"} | 📞 {drawerCandidate.phone || "—"}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">适配度</span>
                  <span className="text-2xl font-extrabold text-primary font-mono">{drawerCandidate.matchScore || 0}%</span>
                </div>
              </div>

              {/* 雷达图 + AI评估 */}
              <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-start">
                <div className="md:col-span-5 bg-white border border-slate-100 p-4 rounded-xl text-center space-y-4 shadow-sm">
                  <span className="text-xs font-bold text-slate-400 block font-sans">能力测评</span>
                  {renderDrawerRadar(drawerCandidate.competencies)}
                  {drawerCandidate.competencies && (
                    <div className="grid grid-cols-2 gap-2 text-[10px] bg-slate-50 p-2.5 rounded-lg text-slate-500 font-sans text-left">
                      <div>技术能力: <strong className="text-slate-800">{drawerCandidate.competencies.technical || 0}/10</strong></div>
                      <div>沟通协调: <strong className="text-slate-800">{drawerCandidate.competencies.communication || 0}/10</strong></div>
                      <div>解决问题: <strong className="text-slate-800">{drawerCandidate.competencies.problemSolving || 0}/10</strong></div>
                      <div>团队活力: <strong className="text-slate-800">{drawerCandidate.competencies.teamFit || 0}/10</strong></div>
                    </div>
                  )}
                </div>

                <div className="md:col-span-7 space-y-4">
                  <div className="space-y-1.5 bg-primary/5 p-4 rounded-xl border border-primary/10">
                    <span className="text-xs font-bold text-primary flex items-center gap-1 font-sans">
                      <Sparkles className="w-3.5 h-3.5" /> AI 评估
                    </span>
                    <p className="text-xs leading-relaxed text-slate-600 font-sans italic">
                      “ {drawerCandidate.aiSummary || "暂无"} ”
                    </p>
                  </div>

                  {drawerCandidate.highlights && drawerCandidate.highlights.length > 0 && (
                    <div className="space-y-2">
                      <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">闪光亮点</span>
                      <ul className="space-y-1.5">
                        {drawerCandidate.highlights.map((light, idx) => (
                          <li key={idx} className="text-xs text-amber-800 bg-amber-50 rounded-lg p-2 font-sans border border-amber-100/50 flex gap-1.5 items-start">
                            <Star className="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0 fill-amber-500" />
                            <span>{light}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              </div>

              {/* 优劣势 */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 bg-emerald-50/40 rounded-xl border border-emerald-100/80 space-y-2">
                  <span className="text-[11px] font-extrabold text-emerald-800 block font-sans uppercase flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> 核心优势
                  </span>
                  <ul className="space-y-1.5">
                    {(drawerCandidate.strengths || []).slice(0, 3).map((s, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans list-disc pl-1 ml-3">{s}</li>
                    ))}
                    {(!drawerCandidate.strengths || drawerCandidate.strengths.length === 0) && (
                      <li className="text-xs text-slate-400">暂无数据</li>
                    )}
                  </ul>
                </div>
                <div className="p-4 bg-red-50/40 rounded-xl border border-red-100/80 space-y-2">
                  <span className="text-[11px] font-extrabold text-red-800 block font-sans uppercase flex items-center gap-1">
                    <X className="w-3.5 h-3.5" /> 改善建议
                  </span>
                  <ul className="space-y-1.5">
                    {(drawerCandidate.weaknesses || []).slice(0, 3).map((w, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans list-disc pl-1 ml-3">{w}</li>
                    ))}
                    {(!drawerCandidate.weaknesses || drawerCandidate.weaknesses.length === 0) && (
                      <li className="text-xs text-slate-400">暂无数据</li>
                    )}
                  </ul>
                </div>
              </div>

              {/* 状态流转 */}
              <div className="bg-white border border-slate-100 p-4 rounded-xl space-y-3">
                <span className="text-xs font-bold text-slate-500 block font-sans">状态管理</span>
                <div className="flex flex-wrap gap-2">
                  {["NEW", "INVITED", "WAITING_INTERVIEW", "PASSED", "REJECTED"].map((status) => (
                    <button key={status}
                      onClick={() => {
                        if (status === "WAITING_INTERVIEW" && drawerCandidate.talentStatus !== "WAITING_INTERVIEW") {
                          handleWaitingInterviewClick();
                        } else {
                          handleUpdateStatus(drawerCandidate.id, status);
                        }
                      }}
                      disabled={updatingId === drawerCandidate.id}
                      className={`text-xs font-semibold px-3 py-1.5 rounded-lg border transition cursor-pointer disabled:opacity-50 ${
                        drawerCandidate.talentStatus === status
                          ? "bg-primary/10 text-primary font-bold border-primary"
                          : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
                      }`}>
                      {updatingId === drawerCandidate.id ? "..." : STATUS_LABELS[status]}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* 底部操作 */}
            <div className="border-t border-slate-100 pt-5 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <button onClick={() => setDrawerCandidate(null)}
                  className="font-sans text-xs bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 px-5 rounded-lg transition cursor-pointer">
                  返回列表
                </button>
                <button onClick={() => { handleRemoveFromTalentPool(drawerCandidate.id); }}
                  disabled={removingId === drawerCandidate.id}
                  className="font-sans text-xs bg-red-50 hover:bg-red-100 text-red-600 font-semibold border border-red-200 py-2.5 px-4 rounded-lg transition cursor-pointer disabled:opacity-40">
                  {removingId === drawerCandidate.id ? "..." : "移出人才库"}
                </button>
              </div>
              <div className="flex items-center gap-3">
                <button onClick={() => { setDrawerCandidate(null); onNavigateToInterview(toCandidate(drawerCandidate)); }}
                  className="font-sans text-xs text-primary bg-primary/10 border border-primary/10 hover:bg-primary/20 font-bold py-2.5 px-5 rounded-lg transition cursor-pointer">
                  拟制问题提纲
                </button>
                <button onClick={() => { setDrawerCandidate(null); onNavigateToMock(toCandidate(drawerCandidate)); }}
                  className="font-sans text-xs text-primary bg-primary/10 hover:bg-primary/20 font-semibold py-2.5 px-5 rounded-lg transition shadow-sm flex items-center gap-1 cursor-pointer">
                  <Play className="w-3 h-3" />
                  一键模拟面试
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 面试时间安排弹窗 */}
      {showScheduleModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-xl p-6 mx-4 space-y-5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800 font-sans flex items-center gap-2">
                <Calendar className="w-4 h-4 text-primary" />
                安排面试时间
              </h3>
              <button onClick={() => setShowScheduleModal(false)}
                className="w-7 h-7 rounded-full border border-slate-100 flex items-center justify-center hover:bg-slate-50 transition cursor-pointer">
                <X className="w-3.5 h-3.5 text-slate-400" />
              </button>
            </div>

            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500 font-semibold font-sans">面试日期 *</label>
                  <input
                    type="date"
                    required
                    value={scheduledDate}
                    onChange={(e) => setScheduledDate(e.target.value)}
                    className="w-full text-xs py-2.5 px-3 bg-white text-slate-800 rounded-xl border border-slate-300 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition font-sans"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs text-slate-500 font-semibold font-sans">面试时间 *</label>
                  <input
                    type="time"
                    required
                    value={scheduledTime}
                    onChange={(e) => setScheduledTime(e.target.value)}
                    className="w-full text-xs py-2.5 px-3 bg-white text-slate-800 rounded-xl border border-slate-300 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition font-sans"
                  />
                </div>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs text-slate-500 font-semibold font-sans">面试备注 (选填)</label>
                <input
                  type="text"
                  value={scheduleNotes}
                  onChange={(e) => setScheduleNotes(e.target.value)}
                  placeholder="例如：技术面，考察微服务架构..."
                  className="w-full text-xs py-2.5 px-3 bg-white text-slate-800 rounded-xl border border-slate-300 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition font-sans"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
              <button onClick={() => setShowScheduleModal(false)}
                className="text-xs font-semibold py-2.5 px-5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition cursor-pointer">
                取消
              </button>
              <button onClick={handleConfirmSchedule}
                className="text-xs font-semibold py-2.5 px-5 rounded-xl bg-primary/10 text-primary font-bold border border-primary hover:bg-primary/20 transition cursor-pointer shadow-sm flex items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5" />
                确定安排
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
