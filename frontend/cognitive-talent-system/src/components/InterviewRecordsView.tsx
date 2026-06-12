import React, { useState } from "react";
import { Search, Trophy, ShieldCheck, HelpCircle, X, Clock, Award, Star, AlertTriangle, FileSpreadsheet } from "lucide-react";
import { ScoreCard } from "../types";

interface InterviewRecordsViewProps {
  scoreCards: ScoreCard[];
}

export default function InterviewRecordsView({ scoreCards }: InterviewRecordsViewProps) {
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedVerdict, setSelectedVerdict] = useState<string>("ALL");
  const [activeCard, setActiveCard] = useState<ScoreCard | null>(null);

  // Filter history records based on search and verdict tag selection
  const filteredRecords = scoreCards.filter((card) => {
    const matchesSearch =
      card.candidateName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      card.role.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesVerdict =
      selectedVerdict === "ALL" || card.verdict === selectedVerdict;

    return matchesSearch && matchesVerdict;
  });

  const getVerdictStyle = (verdict: string) => {
    switch (verdict) {
      case "建议录用":
        return "bg-emerald-50 text-emerald-700 border-emerald-150";
      case "待定":
        return "bg-amber-50 text-amber-700 border-amber-150";
      default:
        return "bg-red-50 text-red-700 border-red-150";
    }
  };

  return (
    <div className="space-y-6" id="interview-records-container">
      {/* Search Header Banner */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight">
            量化面测测评历史库
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            聚合所有模拟与真实面测产生的诊断书。支持回温雷达星级评级，查阅闪高亮度优势以及改善性弱项对答档案。
          </p>
        </div>
      </div>

      {/* Filter and Search controls */}
      <div className="bg-white/70 backdrop-blur-md p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col md:flex-row items-center gap-4 justify-between">
        <div className="relative w-full md:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="搜索候选人姓名或面对应聘岗位..."
            className="w-full text-xs pl-9 pr-4 py-2.5 bg-slate-100/50 hover:bg-slate-100 focus:bg-white border border-slate-200 focus:border-primary outline-none rounded-xl transition font-sans"
          />
        </div>

        {/* Verdict filter category */}
        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          <button
            onClick={() => setSelectedVerdict("ALL")}
            className={`text-xs font-semibold px-4 py-2 rounded-xl border transition cursor-pointer ${
              selectedVerdict === "ALL" ? "bg-primary/10 text-primary font-bold border-primary shadow-sm" : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
            }`}
          >
            全部
          </button>
          {["建议录用", "待定", "不予录用"].map((verd) => (
            <button
              key={verd}
              onClick={() => setSelectedVerdict(verd)}
              className={`text-xs font-semibold px-3.5 py-2 rounded-xl border transition cursor-pointer ${
                selectedVerdict === verd ? "bg-primary/10 text-primary font-bold border-primary shadow-sm" : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
              }`}
            >
              {verd}
            </button>
          ))}
        </div>
      </div>

      {/* Record Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredRecords.map((card) => (
          <div
            key={card.id}
            onClick={() => setActiveCard(card)}
            className="bg-white/80 hover:bg-white backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition cursor-pointer group flex flex-col justify-between hover:scale-[1.01] duration-200"
          >
            <div className="space-y-4">
              {/* Header profile row */}
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-800 font-sans flex items-center gap-2">
                    {card.candidateName}
                    <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${getVerdictStyle(card.verdict)}`}>
                      {card.verdict}
                    </span>
                  </h3>
                  <p className="text-xs text-slate-500 font-sans mt-0.5">{card.role}</p>
                </div>

                {/* Overall Score Badge */}
                <div className="bg-primary/5 p-2 rounded-xl border border-primary/5 text-center min-w-16">
                  <span className="text-[9px] uppercase font-bold text-slate-400 block font-sans">综合分</span>
                  <span className="text-base font-black text-primary font-mono">{card.overallScore}%</span>
                </div>
              </div>

              {/* Slider previews of score indicators */}
              <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-[10px] text-slate-500 border-t border-slate-100 pt-3">
                <div className="flex justify-between">
                  <span>技术深度:</span>
                  <span className="font-bold text-slate-700">{card.scores.technical}/10</span>
                </div>
                <div className="flex justify-between">
                  <span>沟通表达:</span>
                  <span className="font-bold text-slate-700">{card.scores.communication}/10</span>
                </div>
                <div className="flex justify-between">
                  <span>解决痛点:</span>
                  <span className="font-bold text-slate-700">{card.scores.problemSolving}/10</span>
                </div>
                <div className="flex justify-between">
                  <span>价值观契合:</span>
                  <span className="font-bold text-slate-700">{card.scores.culturalFit}/10</span>
                </div>
              </div>

              {/* Evaluated summary preview */}
              <p className="text-[11px] text-slate-500 leading-relaxed font-sans line-clamp-3 bg-slate-50/50 p-2.5 rounded-lg border border-slate-100">
                “ {card.summary} ”
              </p>
            </div>

            {/* Bottom time and click cues */}
            <div className="flex items-center justify-between border-t border-slate-100 pt-3 mt-4 text-[10px] text-slate-400 font-sans">
              <span>测评时间：{card.evaluatedAt}</span>
              <span className="text-primary font-bold group-hover:translate-x-1 transition duration-200">
                查看大纲详情 →
              </span>
            </div>
          </div>
        ))}

        {filteredRecords.length === 0 && (
          <div className="col-span-full border border-dashed rounded-2xl p-12 text-center text-slate-400 space-y-2">
            <FileSpreadsheet className="w-8 h-8 mx-auto" />
            <p className="text-sm font-semibold font-sans">测评记录库清空或没有查阅到对应筛选信息。</p>
            <p className="text-xs font-sans">您可以进入“模拟面试”开启实际对话，系统将自动汇总成正式鉴定成绩卡。</p>
          </div>
        )}
      </div>

      {/* Pop up Card dialog detailed view */}
      {activeCard && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-white w-full max-w-xl rounded-3xl p-6 sm:p-8 space-y-5 border border-slate-150 shadow-2xl relative overflow-y-auto max-h-[90vh]">
            {/* Close button dial */}
            <button
              onClick={() => setActiveCard(null)}
              className="absolute right-6 top-6 w-8 h-8 rounded-full border border-slate-100 flex items-center justify-center hover:bg-slate-50 cursor-pointer"
            >
              <X className="w-4 h-4 text-slate-400" />
            </button>

            {/* Header top section */}
            <div className="border-b border-slate-100 pb-4 space-y-2.5">
              <div className="flex items-center gap-2">
                <Trophy className="w-6 h-6 text-primary" />
                <h2 className="text-base font-bold text-slate-800 font-sans">AI 综合招聘测评结论卡</h2>
              </div>
              <div className="flex flex-wrap items-center gap-2.5 text-xs text-slate-500 font-sans">
                <span>测试者姓名: <strong className="text-slate-800">{activeCard.candidateName}</strong></span>
                <span>拟岗位: <strong className="text-slate-800">{activeCard.role}</strong></span>
                <span>时间: <strong>{activeCard.evaluatedAt}</strong></span>
              </div>
            </div>

            {/* Evaluation body metrics */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 py-2">
              {/* Overall circle gauge */}
              <div className="bg-slate-50 p-4 rounded-2xl border border-slate-100 text-center space-y-3">
                <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">加权核心总得分</span>
                <div className="relative w-20 h-20 mx-auto flex items-center justify-center">
                  <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                    <circle cx="18" cy="18" r="16" fill="none" stroke="#eaedff" strokeWidth="3" />
                    <circle cx="18" cy="18" r="16" fill="none" stroke="#0058be" strokeWidth="3" strokeDasharray={`${activeCard.overallScore}, 100`} strokeLinecap="round" />
                  </svg>
                  <span className="absolute text-xl font-black font-mono text-slate-800">{activeCard.overallScore}</span>
                </div>
                <span className={`text-[10px] font-bold border rounded-full px-3 py-1 block w-max mx-auto ${getVerdictStyle(activeCard.verdict)}`}>
                  综合决议：{activeCard.verdict}
                </span>
              </div>

              {/* Slider category rating */}
              <div className="space-y-2.5">
                <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">分类细项诊断评价</span>

                {[
                  { label: "研究/研发底盘", val: activeCard.scores.technical, col: "bg-primary" },
                  { label: "表达/协调素养", val: activeCard.scores.communication, col: "bg-purple-600" },
                  { label: "问题破局攻坚", val: activeCard.scores.problemSolving, col: "bg-teal-600" },
                  { label: "价值观契合度", val: activeCard.scores.culturalFit, col: "bg-emerald-600" }
                ].map((item, id) => (
                  <div key={id} className="space-y-0.5">
                    <div className="flex justify-between text-[11px] font-semibold text-slate-600 font-sans">
                      <span>{item.label}</span>
                      <span>{item.val}分</span>
                    </div>
                    <div className="w-full bg-slate-100 h-1 rounded-full overflow-hidden">
                      <div className={`${item.col} h-full`} style={{ width: `${item.val * 10}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Paragraph advice summary */}
            <div className="space-y-1.5">
              <span className="text-[10px] uppercase font-bold text-slate-400 block font-sans">招聘专家AI综合评估</span>
              <p className="text-xs leading-relaxed text-slate-600 bg-slate-50 border border-slate-100 p-4 rounded-xl italic font-sans shadow-sm">
                “ {activeCard.summary} ”
              </p>
            </div>

            {/* Bullet list of strengths and weaknesses */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="p-3.5 bg-emerald-50/45 rounded-xl border border-emerald-100 space-y-1.5">
                <span className="text-[10px] font-extrabold text-emerald-800 block uppercase font-sans flex items-center gap-1">
                  <Star className="w-3.5 h-3.5 text-emerald-600" /> 被验证的亮点
                </span>
                <ul className="space-y-1">
                  {activeCard.strengths.map((s, idx) => (
                    <li key={idx} className="text-[11px] text-slate-600 leading-relaxed font-sans flex gap-1 items-start">
                      <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full mt-1.5 shrink-0" />
                      <span>{s}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="p-3.5 bg-red-50/45 rounded-xl border border-red-100 space-y-1.5">
                <span className="text-[10px] font-extrabold text-red-800 block uppercase font-sans flex items-center gap-1">
                  <AlertTriangle className="w-3.5 h-3.5 text-red-500" /> 面测尚存改善项
                </span>
                <ul className="space-y-1">
                  {activeCard.improvements.map((w, idx) => (
                    <li key={idx} className="text-[11px] text-slate-600 leading-relaxed font-sans flex gap-1 items-start">
                      <span className="w-1.5 h-1.5 bg-red-500 rounded-full mt-1.5 shrink-0" />
                      <span>{w}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            {/* Footer triggers */}
            <div className="border-t border-slate-100 pt-4 flex items-center justify-end">
              <button
                onClick={() => setActiveCard(null)}
                className="font-sans text-xs bg-primary/10 text-primary font-bold hover:bg-primary/20 py-2 px-5 rounded-lg transition shadow-sm border border-primary cursor-pointer"
              >
                确定确定，关闭档案
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
