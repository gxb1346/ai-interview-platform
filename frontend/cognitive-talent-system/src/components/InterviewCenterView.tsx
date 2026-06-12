import React, { useState, useEffect } from "react";
import { Sparkles, Play, Calendar, ClipboardCheck, ArrowRight, BookOpen, Clock, Loader2, Plus, AlertCircle, Trash } from "lucide-react";
import { Candidate, Interview, ResumeVO, ApiResult } from "../types";

const API_BASE = "http://localhost:8082";

function toCandidate(cand: ResumeVO): Candidate {
  return {
    id: "cand_" + cand.id,
    name: cand.candidateName || "未知",
    role: cand.candidateRole || "",
    experienceYears: cand.experienceYears || 0,
    education: cand.education || "未知",
    status: cand.talentStatus as any,
    avatar: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&h=150&fit=crop&crop=face",
    matchScore: cand.matchScore || 0,
    email: cand.email || "",
    phone: cand.phone || "",
    competencies: cand.competencies
      ? {
          technical: cand.competencies.technical ?? 5,
          communication: cand.competencies.communication ?? 5,
          problemSolving: cand.competencies.problemSolving ?? 5,
          teamFit: cand.competencies.teamFit ?? 5,
          drive: cand.competencies.drive ?? 5,
        }
      : { technical: 5, communication: 5, problemSolving: 5, teamFit: 5, drive: 5 },
    strengths: cand.strengths || [],
    weaknesses: cand.weaknesses || [],
    highlights: cand.highlights || [],
    aiSummary: cand.aiSummary || "",
    analyzedAt: cand.analyzedAt || ""
  };
}

interface InterviewCenterViewProps {
  interviews: Interview[];
  candidates: Candidate[];
  onAddInterview: (int: Interview) => void;
  onRemoveInterview: (id: string) => void;
  onNavigateToMock: (cand: Candidate) => void;
}

export default function InterviewCenterView({
  interviews,
  candidates,
  onAddInterview,
  onRemoveInterview,
  onNavigateToMock
}: InterviewCenterViewProps) {
  const [selectedCandidateId, setSelectedCandidateId] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [notes, setNotes] = useState("");
  const [generatingQuestionsId, setGeneratingQuestionsId] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  // 删除确认
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);

  // Suggested questions state mapped by Interview ID
  const [suggestedQuestionsList, setSuggestedQuestionsList] = useState<Record<string, string[]>>({});
  // 人才库候选人（从后端 API 拉取）
  const [talentCandidates, setTalentCandidates] = useState<Candidate[]>([]);

  // 组件挂载时从后端拉取人才库候选人
  useEffect(() => {
    fetch(`${API_BASE}/api/resume/talent-pool`)
      .then(res => res.json())
      .then((json: ApiResult<ResumeVO[]>) => {
        if (json.code === 200 && json.data) {
          setTalentCandidates(json.data.map(toCandidate));
        }
      })
      .catch(() => {});
  }, []);

  // 合并预植入候选人 + 人才库候选人（按 id 去重）
  const allCandidates = React.useMemo(() => {
    const merged = [...candidates];
    talentCandidates.forEach(tc => {
      if (!merged.some(c => c.id === tc.id)) {
        merged.push(tc);
      }
    });
    return merged;
  }, [candidates, talentCandidates]);

  const handleCreateInterview = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCandidateId || !scheduledAt) return;

    const candidate = allCandidates.find((c) => c.id === selectedCandidateId);
    if (!candidate) return;

    const newInt: Interview = {
      id: "int_" + Date.now(),
      candidateId: candidate.id,
      candidateName: candidate.name,
      role: candidate.role,
      scheduledAt: scheduledAt.replace("T", " "),
      status: "pending",
      suggestedQuestions: [
        `作为应聘的${candidate.role}，谈谈你对该领域的核心看法。`,
        "说说你在以往工作中攻克的最难技术场景。"
      ],
      notes: notes || "普通初试深度考核"
    };

    onAddInterview(newInt);
    setSelectedCandidateId("");
    setScheduledAt("");
    setNotes("");
    setShowAddForm(false);
  };

  const handleGenerateAIQuestions = async (int: Interview) => {
    setGeneratingQuestionsId(int.id);
    const candidate = allCandidates.find((c) => c.id === int.candidateId);
    
    try {
      const response = await fetch("/api/interview/suggest-questions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          candidateName: int.candidateName,
          role: int.role,
          strengths: candidate?.strengths || [],
          aiSummary: candidate?.aiSummary || ""
        })
      });

      if (!response.ok) throw new Error("API call failed");
      const data = await response.json();
      
      if (data.questions && data.questions.length > 0) {
        setSuggestedQuestionsList(prev => ({
          ...prev,
          [int.id]: data.questions
        }));
      }
    } catch (err) {
      console.error("AI question generation failing, using presets:", err);
      // Fallback questions on failure
      setSuggestedQuestionsList(prev => ({
        ...prev,
        [int.id]: [
          `围绕你作为${int.role}的核心优势，你在以往最高难度项目中具体如何规避架构单点故障风险？`,
          "假如你在架构推行或业务演进中，遇到高层或协作部门的强烈反对，你有什么具体的斡旋策略？",
          "简历中提及的优势能力，如果在真实高压高频测试下暴露出性能折损，你会采用什么监控指标发现它？",
          "结合当下的AI前沿和智能体演进，你认为该职位的产品或技术架构未来三年最大的重构空间在哪里？",
          "谈谈你最近自驱学习并且动手编写过原型的新技术模块，是什么打动了你？"
        ]
      }));
    } finally {
      setGeneratingQuestionsId(null);
    }
  };

  const activeCandidate = allCandidates.find(c => c.id === selectedCandidateId);

  return (
    <div className="space-y-6" id="interview-center-container">
      {/* Title & Top triggers */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            面试协调中心
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            在此处快速拟定面试计划，通过 AI 神经模型为各位求职者精准定制“针刺型”核心技术测试提纲。
          </p>
        </div>

        <button
          onClick={() => setShowAddForm(!showAddForm)}
          className="font-sans text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 px-4 py-2.5 rounded-xl transition flex items-center gap-1.5 cursor-pointer shadow-sm"
        >
          <Plus className="w-4 h-4" />
          安排新面试日程
        </button>
      </div>

      {/* Slide down Schedule Interventional schedule Form */}
      {showAddForm && (
        <form
          onSubmit={handleCreateInterview}
          className="bg-white/80 border border-slate-150 p-6 rounded-2xl shadow-md space-y-4 animate-fade-in"
        >
          <h3 className="text-sm font-bold text-slate-800 font-sans">安排一场新面试</h3>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-1.5 animate-pulse-once">
              <label className="text-xs text-slate-500 font-semibold font-sans">选择人才库求职者 *</label>
              <select
                required
                value={selectedCandidateId}
                onChange={(e) => setSelectedCandidateId(e.target.value)}
                className="w-full text-xs py-2.5 px-3 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 outline-none transition font-sans cursor-pointer"
              >
                <option value="">- 请选择候选人 -</option>
                {allCandidates.map((cand) => (
                  <option key={cand.id} value={cand.id}>
                    {cand.name} - {cand.role}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs text-slate-500 font-semibold font-sans">面试时间 *</label>
              <input
                type="datetime-local"
                required
                value={scheduledAt}
                onChange={(e) => setScheduledAt(e.target.value)}
                className="w-full text-xs py-2.5 px-3 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 outline-none transition font-sans cursor-pointer"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs text-slate-500 font-semibold font-sans">备注要求 (选填)</label>
              <input
                type="text"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="例如：考察微服务，或者业务综合素质..."
                className="w-full text-xs py-2.5 px-3 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 outline-none transition font-sans"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
            <button
              type="button"
              onClick={() => setShowAddForm(false)}
              className="font-sans text-xs bg-slate-100 hover:bg-slate-200 text-slate-600 font-medium py-2 px-4 rounded-xl transition cursor-pointer"
            >
              取消
            </button>
            <button
              type="submit"
              className="font-sans text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 py-2 px-5 rounded-xl transition cursor-pointer"
            >
              确定安排
            </button>
          </div>
        </form>
      )}

      {/* Scheduled lists layouts */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        
        {/* Scheduled List Panel (Col-7) */}
        <div className="lg:col-span-12 space-y-4">
          <div className="bg-white/70 backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm">
            <h3 className="text-sm font-bold text-slate-800 font-sans mb-4 border-b border-slate-100 pb-3 flex items-center gap-1.5">
              <ClipboardCheck className="w-4.5 h-4.5 text-primary" /> 日程计划一览 ({interviews.length} 场场安排)
            </h3>

            <div className="space-y-4">
              {interviews.map((int) => {
                const targetCand = allCandidates.find(c => c.id === int.candidateId);
                const hasAIGenQuestions = suggestedQuestionsList[int.id] && suggestedQuestionsList[int.id].length > 0;

                return (
                  <div
                    key={int.id}
                    className="p-5 bg-white border border-slate-100 rounded-2xl shadow-sm hover:shadow-md transition flex flex-col md:flex-row items-start justify-between gap-6"
                  >
                    <div className="space-y-3.5 flex-1">
                      {/* Top meta tags */}
                      <div className="flex flex-wrap items-center gap-2.5">
                        <div className="flex items-center gap-1.5 text-xs font-semibold text-primary font-sans bg-primary/5 border border-primary/5 rounded-full px-3 py-1">
                          <Clock className="w-3.5 h-3.5" />
                          <span>时间：{int.scheduledAt}</span>
                        </div>
                        <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 bg-slate-50 border px-2.5 py-1 rounded-full">
                          拟岗位：{int.role}
                        </span>
                      </div>

                      {/* Name of Candidate profile links */}
                      <div>
                        <h4 className="text-base font-bold text-slate-800 font-sans flex items-center gap-2">
                          面试者：{int.candidateName}
                          <span className="text-xs text-slate-400 font-medium font-sans">
                            {targetCand?.education.split("·")[0] || "名牌大学硕士"}
                          </span>
                        </h4>
                        <p className="text-xs text-slate-500 font-sans mt-1">备注内容：{int.notes}</p>
                      </div>

                      {/* Display dynamically generated or fallback suggested questions */}
                      <div className="bg-slate-50/50 p-4 rounded-xl border border-slate-100 space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-bold text-slate-700 font-sans flex items-center gap-1">
                            <BookOpen className="w-4 h-4 text-primary" />
                            AI 智能面试刺针问题提纲 (专属针对该候选人)
                          </span>

                          <button
                            onClick={() => handleGenerateAIQuestions(int)}
                            disabled={generatingQuestionsId === int.id}
                            className="text-[10px] font-bold text-primary hover:text-primary-container flex items-center gap-1 transition cursor-pointer"
                          >
                            {generatingQuestionsId === int.id ? (
                              <>
                                <Loader2 className="w-3 h-3 animate-spin" />
                                正在神思中...
                              </>
                            ) : (
                              <>
                                <Sparkles className="w-3 h-3 animate-pulse" />
                                {hasAIGenQuestions ? "重新生提问大纲" : "智能神经模型生成"}
                              </>
                            )}
                          </button>
                        </div>

                        <ul className="space-y-2">
                          {(suggestedQuestionsList[int.id] || int.suggestedQuestions).map((q, qIdx) => (
                            <li key={qIdx} className="text-xs text-slate-600 leading-relaxed font-sans flex gap-2">
                              <span className="w-4 h-4 bg-primary/10 text-primary font-bold font-mono rounded-full flex items-center justify-center shrink-0 mt-0.5">
                                {qIdx + 1}
                              </span>
                              <span>{q}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    </div>

                    {/* Quick Trigger launcher buttons */}
                    <div className="flex md:flex-col items-stretch gap-2.5 min-w-32 justify-end w-full md:w-auto">
                      <button
                        onClick={() => {
                          if (targetCand) {
                            onNavigateToMock(targetCand);
                          } else {
                            alert("档案库中不支持直接模拟其测试。");
                          }
                        }}
                        className="flex-1 font-sans text-xs text-primary bg-primary/10 hover:bg-primary/20 font-semibold py-2.5 px-4 rounded-xl transition flex items-center justify-center gap-1 shadow-sm cursor-pointer border border-primary/20"
                      >
                        <Play className="w-3 h-3" />
                        开启模拟面试
                      </button>

                      <button
                        onClick={() => setDeleteConfirmId(int.id)}
                        className="font-sans text-xs text-rose-500 hover:text-rose-600 hover:bg-rose-50 font-semibold py-2 px-3 rounded-xl transition border border-rose-100 flex items-center justify-center gap-1 cursor-pointer"
                      >
                        <Trash className="w-3.5 h-3.5" />
                        删除日程
                      </button>
                    </div>
                  </div>
                );
              })}

              {interviews.length === 0 && (
                <div className="bg-slate-50 rounded-xl p-12 text-center text-slate-400 space-y-2 border border-slate-100">
                  <AlertCircle className="w-8 h-8 mx-auto" />
                  <p className="text-sm font-semibold font-sans">暂无拟定的面试计划。</p>
                  <p className="text-xs font-sans">点击右上角“安排新面试日程”给人才库候选人拟定时间线。</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

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
                className="text-xs font-semibold py-2 px-4 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition cursor-pointer">
                取消
              </button>
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
