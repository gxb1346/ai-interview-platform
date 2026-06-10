import React, { useState } from "react";
import { Search, Filter, Calendar, Mail, Phone, ChevronRight, X, Sparkles, Star, Plus, CheckCircle2, Award, Play } from "lucide-react";
import { Candidate, CandidateStatus } from "../types";

interface TalentPoolViewProps {
  candidates: Candidate[];
  onSelectCandidate: (cand: Candidate) => void;
  onNavigateToMock: (cand: Candidate) => void;
  onNavigateToInterview: (cand: Candidate) => void;
}

export default function TalentPoolView({
  candidates,
  onSelectCandidate,
  onNavigateToMock,
  onNavigateToInterview
}: TalentPoolViewProps) {
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedStatus, setSelectedStatus] = useState<string>("ALL");
  const [drawerCandidate, setDrawerCandidate] = useState<Candidate | null>(null);

  // Filter candidates dynamically based on live search and tag clicks
  const filteredCandidates = candidates.filter((cand) => {
    const matchesSearch =
      cand.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      cand.role.toLowerCase().includes(searchTerm.toLowerCase()) ||
      cand.education.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus =
      selectedStatus === "ALL" || cand.status === selectedStatus;

    return matchesSearch && matchesStatus;
  });

  const getStatusBadgeStyle = (status: CandidateStatus) => {
    switch (status) {
      case CandidateStatus.INVITED:
        return "bg-blue-50 text-blue-700 border-blue-100";
      case CandidateStatus.WAITING_INTERVIEW:
        return "bg-amber-50 text-amber-700 border-amber-100";
      case CandidateStatus.PASSED:
        return "bg-emerald-50 text-emerald-700 border-emerald-100";
      case CandidateStatus.REJECTED:
        return "bg-red-50 text-red-700 border-red-100";
      default:
        return "bg-slate-100 text-slate-600 border-slate-200";
    }
  };

  // Custom visual capacity polygon radar overlay for Candidate Details drawer
  const renderDrawerRadar = (comp: any) => {
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

    const getPoints = (scale: number) => {
      return angles.map((angle) => {
        const x = center + r * scale * Math.cos(angle);
        const y = center + r * scale * Math.sin(angle);
        return `${x},${y}`;
      }).join(" ");
    };

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

  return (
    <div className="space-y-6" id="talent-pool-container">
      {/* Title & Fast Stats header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            人才库管理中心
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            聚合全局求职者档案。支持动态搜索学技术栈，快捷查阅 AI 定性雷达评价和一键呼叫考官。
          </p>
        </div>

        {/* Aggregate overview metrics */}
        <div className="flex items-center gap-3">
          <div className="bg-white/80 p-3 rounded-xl border border-slate-100 text-center min-w-20 shadow-sm">
            <span className="text-xs text-slate-400 block font-sans">总人才</span>
            <span className="text-lg font-bold font-mono text-slate-800">{candidates.length}</span>
          </div>
          <div className="bg-emerald-50/50 p-3 rounded-xl border border-emerald-100 text-center min-w-20 shadow-sm">
            <span className="text-xs text-emerald-800 font-medium block font-sans">已通关</span>
            <span className="text-lg font-extrabold font-mono text-emerald-600">
              {candidates.filter(c => c.status === CandidateStatus.PASSED).length}
            </span>
          </div>
          <div className="bg-amber-50/50 p-3 rounded-xl border border-amber-100 text-center min-w-20 shadow-sm">
            <span className="text-xs text-amber-800 font-medium block font-sans">待面试</span>
            <span className="text-lg font-extrabold font-mono text-amber-600">
              {candidates.filter(c => c.status === CandidateStatus.WAITING_INTERVIEW).length}
            </span>
          </div>
        </div>
      </div>

      {/* Filter and Search Bar Row */}
      <div className="bg-white/70 backdrop-blur-md p-4 rounded-xl border border-white/40 shadow-sm flex flex-col md:flex-row items-center gap-4 justify-between">
        <div className="relative w-full md:w-80">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="搜索求职者姓名、学校或招聘岗位..."
            className="w-full text-xs pl-10 pr-4 py-2.5 bg-slate-100/50 hover:bg-slate-100 focus:bg-white border border-slate-200 focus:border-primary outline-none rounded-xl transition font-sans"
          />
        </div>

        {/* Filter tags selection */}
        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          <button
            onClick={() => setSelectedStatus("ALL")}
            className={`text-xs font-semibold px-4 py-2 rounded-xl border transition cursor-pointer ${
              selectedStatus === "ALL"
                ? "bg-primary text-white border-primary shadow-sm"
                : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
            }`}
          >
            全部
          </button>
          {Object.values(CandidateStatus).map((status) => (
            <button
              key={status}
              onClick={() => setSelectedStatus(status)}
              className={`text-xs font-semibold px-3.5 py-2 rounded-xl border transition cursor-pointer ${
                selectedStatus === status
                  ? "bg-primary text-white border-primary shadow-sm"
                  : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
              }`}
            >
              {status}
            </button>
          ))}
        </div>
      </div>

      {/* Candidate List Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredCandidates.map((cand) => (
          <div
            key={cand.id}
            onClick={() => setDrawerCandidate(cand)}
            className="bg-white/80 hover:bg-white backdrop-blur-md p-5 rounded-2xl border border-white/30 shadow-sm hover:shadow-lg transition duration-300 flex flex-col justify-between cursor-pointer group hover:scale-[1.01]"
          >
            <div>
              {/* Header profile of list item */}
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-3">
                  <img
                    src={cand.avatar}
                    alt={cand.name}
                    referrerPolicy="no-referrer"
                    className="w-12 h-12 rounded-full object-cover border border-slate-100"
                  />
                  <div>
                    <h3 className="text-sm font-bold text-slate-800 font-sans flex items-center gap-1.5">
                      {cand.name}
                      <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${getStatusBadgeStyle(cand.status)}`}>
                        {cand.status}
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500 font-sans">{cand.education.split("·")[0]}</p>
                  </div>
                </div>

                {/* Score badge indicator */}
                <div className="text-right">
                  <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">
                    适配匹配度
                  </span>
                  <span className="text-base font-extrabold text-primary font-mono block">
                    {cand.matchScore}%
                  </span>
                </div>
              </div>

              {/* Depicted Job Title section */}
              <div className="mt-4 space-y-1">
                <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-400 block font-sans">
                  拟招纳职位
                </span>
                <span className="text-xs font-bold text-slate-700 font-sans block">
                  {cand.role}
                </span>
              </div>

              {/* Minimalist competencies badges row */}
              <div className="flex flex-wrap gap-1 mt-3">
                <span className="text-[10px] bg-sky-50 text-sky-700 border border-sky-100/50 rounded px-1.5 py-0.5">
                  技术 {cand.competencies.technical}/10
                </span>
                <span className="text-[10px] bg-purple-50 text-purple-700 border border-purple-100/50 rounded px-1.5 py-0.5">
                  沟通 {cand.competencies.communication}/10
                </span>
                <span className="text-[10px] bg-red-50 text-red-700 border border-red-100/50 rounded px-1.5 py-0.5">
                  解决 {cand.competencies.problemSolving}/10
                </span>
              </div>

              {/* Truncated assessment statement summary */}
              <p className="text-[11px] text-slate-500 line-clamp-2 mt-4 font-sans bg-slate-50 p-2 rounded-lg border border-slate-100">
                “{cand.aiSummary}”
              </p>
            </div>

            {/* Bottom Row links or direct operations */}
            <div className="flex items-center justify-between border-t border-slate-100 pt-4 mt-4 lg:opacity-80 group-hover:opacity-100 transition duration-200">
              <span className="text-[10px] text-slate-400 font-sans">
                神析于：{cand.analyzedAt.substring(5, 10)}
              </span>

              <div className="flex items-center gap-1.5">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onNavigateToInterview(cand);
                  }}
                  className="text-[10px] font-bold text-primary bg-primary/5 hover:bg-primary/10 border border-primary/10 px-2.5 py-1.5 rounded-lg transition cursor-pointer"
                >
                  提纲设想
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onNavigateToMock(cand);
                  }}
                  className="text-[10px] font-bold text-white bg-primary hover:bg-primary-container px-2.5 py-1.5 rounded-lg flex items-center gap-1 transition cursor-pointer"
                >
                  <Play className="w-2.5 h-2.5 fill-white" />
                  提枪面试
                </button>
              </div>
            </div>
          </div>
        ))}

        {filteredCandidates.length === 0 && (
          <div className="col-span-full bg-slate-50 rounded-2xl border border-slate-100/80 p-12 text-center text-slate-400 space-y-2">
            <X className="w-8 h-8 mx-auto text-slate-300" />
            <p className="text-sm font-semibold font-sans">未查阅到符合当前搜索和分类标签下的候选人。</p>
            <p className="text-xs font-sans text-slate-400">您可以尝试更换其他搜索词或在“简历分析”中解析新简历导入。</p>
          </div>
        )}
      </div>

      {/* Floating sliding Candidate Detail System Drawer overlay */}
      {drawerCandidate && (
        <div className="fixed inset-0 z-50 overflow-hidden bg-slate-900/40 backdrop-blur-sm animate-fade-in flex justify-end">
          {/* Backdrop closer click mechanism */}
          <div className="absolute inset-0 cursor-pointer" onClick={() => setDrawerCandidate(null)} />

          {/* Drawer Sidebar */}
          <div className="relative w-full max-w-2xl bg-white shadow-2xl h-full flex flex-col justify-between overflow-y-auto animate-slide-in p-6 sm:p-8 space-y-6">
            
            {/* Header top row section */}
            <div className="flex items-center justify-between border-b border-slate-100 pb-4">
              <div className="flex items-center gap-3">
                <Award className="w-6 h-6 text-primary" />
                <h2 className="text-lg font-bold text-slate-800 font-sans">候选人智慧档案卡</h2>
              </div>
              <button
                onClick={() => setDrawerCandidate(null)}
                className="w-8 h-8 rounded-full border border-slate-100 flex items-center justify-center hover:bg-slate-50 transition cursor-pointer"
              >
                <X className="w-4 h-4 text-slate-400" />
              </button>
            </div>

            {/* Profile Detail Block */}
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-slate-50 p-5 rounded-2xl border border-slate-120">
                <div className="flex items-center gap-4">
                  <img
                    src={drawerCandidate.avatar}
                    alt={drawerCandidate.name}
                    referrerPolicy="no-referrer"
                    className="w-14 h-14 rounded-full object-cover border border-slate-200"
                  />
                  <div>
                    <h3 className="text-base font-bold text-slate-800 font-sans flex items-center gap-2">
                      {drawerCandidate.name}
                      <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${getStatusBadgeStyle(drawerCandidate.status)}`}>
                        {drawerCandidate.status}
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500 mt-0.5">{drawerCandidate.education}</p>
                    <p className="text-[11px] text-slate-400 font-mono mt-1">
                      ✉️ {drawerCandidate.email} | 📞 {drawerCandidate.phone}
                    </p>
                  </div>
                </div>

                <div className="text-right">
                  <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">首选适配度</span>
                  <span className="text-2xl font-extrabold text-primary font-mono">{drawerCandidate.matchScore}%</span>
                </div>
              </div>

              {/* Dual Column grid logic: Radar and text columns */}
              <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-start">
                {/* Radar section (5 cols) */}
                <div className="md:col-span-5 bg-white border border-slate-100 p-4 rounded-xl text-center space-y-4 shadow-sm">
                  <span className="text-xs font-bold text-slate-400 block font-sans">能力神经测评</span>
                  {renderDrawerRadar(drawerCandidate.competencies)}
                  
                  {/* Digital value map details */}
                  <div className="grid grid-cols-2 gap-2 text-[10px] bg-slate-50 p-2.5 rounded-lg text-slate-500 font-sans text-left">
                    <div>技术能力: <strong className="text-slate-800">{drawerCandidate.competencies.technical}/10</strong></div>
                    <div>沟通协调: <strong className="text-slate-800">{drawerCandidate.competencies.communication}/10</strong></div>
                    <div>解决痛点: <strong className="text-slate-800">{drawerCandidate.competencies.problemSolving}/10</strong></div>
                    <div>团队活力: <strong className="text-slate-800">{drawerCandidate.competencies.teamFit}/10</strong></div>
                  </div>
                </div>

                {/* AI assessment insights paragraphs (7 cols) */}
                <div className="md:col-span-7 space-y-4">
                  <div className="space-y-1.5 bg-primary/5 p-4 rounded-xl border border-primary/10">
                    <span className="text-xs font-bold text-primary flex items-center gap-1 font-sans">
                      <Sparkles className="w-3.5 h-3.5" /> AI 诊断大纲意见
                    </span>
                    <p className="text-xs leading-relaxed text-slate-600 font-sans italic">
                      “ {drawerCandidate.aiSummary} ”
                    </p>
                  </div>

                  {/* Highlights stack */}
                  <div className="space-y-2">
                    <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">重大闪光亮点</span>
                    <ul className="space-y-1.5">
                      {drawerCandidate.highlights.map((light, idx) => (
                        <li key={idx} className="text-xs text-amber-800 bg-amber-50 rounded-lg p-2 font-sans border border-amber-100/50 flex gap-1.5 items-start">
                          <Star className="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0 fill-amber-500" />
                          <span>{light}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>

              {/* Strengths / Weaknesses list rows */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 bg-emerald-50/40 rounded-xl border border-emerald-100/80 space-y-2">
                  <span className="text-[11px] font-extrabold text-emerald-800 block font-sans uppercase flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> 核心优势
                  </span>
                  <ul className="space-y-1.5">
                    {drawerCandidate.strengths.slice(0, 2).map((s, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans list-disc pl-1 ml-3">
                        {s}
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="p-4 bg-red-50/40 rounded-xl border border-red-100/80 space-y-2">
                  <span className="text-[11px] font-extrabold text-red-800 block font-sans uppercase flex items-center gap-1">
                    <X className="w-3.5 h-3.5" /> 潜在弱势补给建议
                  </span>
                  <ul className="space-y-1.5">
                    {drawerCandidate.weaknesses.slice(0, 2).map((w, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans list-disc pl-1 ml-3">
                        {w}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>

            {/* Bottom Row buttons drawer controls */}
            <div className="border-t border-slate-100 pt-5 flex items-center justify-between">
              <button
                onClick={() => setDrawerCandidate(null)}
                className="font-sans text-xs bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 px-5 rounded-lg transition cursor-pointer"
              >
                返回列表
              </button>

              <div className="flex items-center gap-3">
                <button
                  onClick={() => {
                    setDrawerCandidate(null);
                    onNavigateToInterview(drawerCandidate);
                  }}
                  className="font-sans text-xs text-primary bg-primary/10 border border-primary/10 hover:bg-primary/20 font-bold py-2.5 px-5 rounded-lg transition cursor-pointer"
                >
                  拟制问题提纲
                </button>
                <button
                  onClick={() => {
                    setDrawerCandidate(null);
                    onNavigateToMock(drawerCandidate);
                  }}
                  className="font-sans text-xs text-white bg-primary hover:bg-primary-container font-semibold py-2.5 px-5 rounded-lg transition shadow-md flex items-center gap-1 cursor-pointer"
                >
                  <Play className="w-3 h-3 fill-white" />
                  一键开启模拟面试
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
