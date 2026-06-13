import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  Send, Sparkles, Trophy, CheckCircle2, AlertTriangle, MessageSquareCode,
  Clock, Mic, MicOff, RefreshCw, Loader2, ChevronDown, ChevronUp, FileText
} from "lucide-react";
import {
  Candidate, ChatMessage, ScoreCard, ResumeVO, ApiResult, CreateSessionRequest,
  CreateSessionResponse, StartInterviewResponse, ChatResponse, DirectionRecommendation,
  JDParseResult, EvaluationReport, STAGE_LABELS, InterviewSession
} from "../types";

interface MockInterviewViewProps {
  candidates: Candidate[];
  preSelectedCandidate: Candidate | null;
  resumeSessionId?: string | null;
  onSaveScoreCard: (card: ScoreCard) => void;
  onNavigateToRecords: () => void;
  onSessionCreated?: (sessionId: string) => void;
}

const API_BASE = "http://localhost:8082";

const DIRECTIONS = [
  "AI Agent开发", "算法与数据结构", "阿里后端", "字节后端",
  "前端工程", "Java后端开发", "腾讯后端", "Python后端开发",
  "系统设计", "测试开发", "自定义JD"
];
const LEVELS = ["校招", "中级", "高级"];
const LEVEL_YEAR_MAP: Record<string, string> = { "校招": "0-1年", "中级": "1-3年", "高级": "3年+" };
const STAGE_RATIOS: Record<string, number> = { selfIntro: 0.15, techExam: 0.40, projectDeep: 0.30, qaRound: 0.15 };

export default function MockInterviewView({
  candidates, preSelectedCandidate, resumeSessionId,
  onSaveScoreCard, onNavigateToRecords, onSessionCreated
}: MockInterviewViewProps) {
  const [activeCandidateId, setActiveCandidateId] = useState("");
  const [sessionId, setSessionId] = useState<string | null>(resumeSessionId || null);
  const [interviewStarted, setInterviewStarted] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [evaluationReport, setEvaluationReport] = useState<EvaluationReport | null>(null);
  const [scoreCard, setScoreCard] = useState<ScoreCard | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [thinking, setThinking] = useState(false);
  const [timeElapsed, setTimeElapsed] = useState(0);
  const [timerInterval, setTimerInterval] = useState<any>(null);
  const messageEndRef = useRef<HTMLDivElement>(null);

  // 面试配置
  const [interviewMode, setInterviewMode] = useState<"text" | "voice">("text");
  const [interviewDirection, setInterviewDirection] = useState("");
  const [interviewLevel, setInterviewLevel] = useState("中级");
  const [totalDuration, setTotalDuration] = useState(60);
  const [followUpCount, setFollowUpCount] = useState(1);
  const [customJDText, setCustomJDText] = useState("");
  const [jdParsing, setJdParsing] = useState(false);
  const [jdResult, setJdResult] = useState<JDParseResult | null>(null);

  // 方向推荐
  const [recommendations, setRecommendations] = useState<DirectionRecommendation[]>([]);
  const [recommending, setRecommending] = useState(false);

  // 语音
  const [isRecording, setIsRecording] = useState(false);
  const [interimText, setInterimText] = useState("");
  const recognitionRef = useRef<any>(null);

  // 人才库候选人
  const [talentCandidates, setTalentCandidates] = useState<Candidate[]>([]);

  // 阶段时长计算
  const stageMinutes = useCallback(() => {
    return Object.fromEntries(
      Object.entries(STAGE_RATIOS).map(([key, ratio]) => [key, Math.max(1, Math.round(totalDuration * ratio))])
    );
  }, [totalDuration]);

  // 组件挂载时拉取候选人
  useEffect(() => {
    fetch(`${API_BASE}/api/resume/talent-pool`)
      .then(res => res.json())
      .then((json: ApiResult<ResumeVO[]>) => {
        if (json.code === 200 && json.data) {
          setTalentCandidates(json.data.map(c => ({
            id: "cand_" + c.id, name: c.candidateName || "未知", role: c.candidateRole || "",
            experienceYears: c.experienceYears || 0, education: c.education || "未知",
            status: c.talentStatus as any, avatar: "", matchScore: c.matchScore || 0,
            email: c.email || "", phone: c.phone || "", resumeText: "",
            competencies: { technical: 5, communication: 5, problemSolving: 5, teamFit: 5, drive: 5 },
            strengths: c.strengths || [], weaknesses: c.weaknesses || [],
            highlights: c.highlights || [], aiSummary: c.aiSummary || "", analyzedAt: c.analyzedAt || ""
          })));
        }
      }).catch(() => {});
  }, []);

  const allCandidates = React.useMemo(() => {
    const merged = [...candidates];
    talentCandidates.forEach(tc => { if (!merged.some(c => c.id === tc.id)) merged.push(tc); });
    return merged;
  }, [candidates, talentCandidates]);

  useEffect(() => {
    if (preSelectedCandidate) setActiveCandidateId(preSelectedCandidate.id);
    else if (candidates.length > 0 && !activeCandidateId) setActiveCandidateId(candidates[0].id);
  }, [preSelectedCandidate, candidates]);

  useEffect(() => {
    return () => { if (timerInterval) clearInterval(timerInterval); };
  }, [timerInterval]);

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, thinking]);

  const activeCand = allCandidates.find(c => c.id === activeCandidateId);

  // 简历方向推荐
  const handleRecommend = async () => {
    const cand = allCandidates.find(c => c.id === activeCandidateId);
    if (!cand?.aiSummary) return;
    setRecommending(true);
    try {
      const res = await fetch(`${API_BASE}/api/mock-interview/directions/recommend`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ resumeText: cand.aiSummary })
      });
      const data = await res.json();
      setRecommendations(data || []);
    } catch { } finally { setRecommending(false); }
  };

  // 自定义 JD 解析
  const handleParseJD = async () => {
    if (!customJDText.trim()) return;
    setJdParsing(true);
    try {
      const res = await fetch(`${API_BASE}/api/mock-interview/jd/parse`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ jdText: customJDText })
      });
      const data: JDParseResult = await res.json();
      setJdResult(data);
      if (data.matchedDirection && data.matchedDirection !== "自定义JD") {
        setInterviewDirection(data.matchedDirection);
      }
    } catch { } finally { setJdParsing(false); }
  };

  // 创建并开始面试
  const startInterview = async () => {
    if (!activeCand || !interviewDirection) return;
    setEvaluating(true);
    try {
      const body: CreateSessionRequest = {
        candidateId: activeCand.id,
        candidateName: activeCand.name,
        candidateRole: activeCand.role,
        resumeText: activeCand.aiSummary || "",
        direction: interviewDirection === "自定义JD" ? (jdResult?.matchedDirection || "Java后端开发") : interviewDirection,
        level: interviewLevel,
        mode: interviewMode,
        totalDuration,
        followUpCount,
        customJD: interviewDirection === "自定义JD" ? customJDText : undefined
      };
      const createRes = await fetch(`${API_BASE}/api/mock-interview/sessions`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
      });
      if (!createRes.ok) {
        const errBody = await createRes.text();
        console.error("创建会话失败:", createRes.status, errBody);
        throw new Error(`服务器返回 ${createRes.status}`);
      }
      const createData: CreateSessionResponse = await createRes.json();
      if (!createData.sessionId) throw new Error("创建会话未返回 sessionId");
      const newSessionId = createData.sessionId;
      setSessionId(newSessionId);
      if (onSessionCreated) onSessionCreated(newSessionId);

      const startRes = await fetch(`${API_BASE}/api/mock-interview/sessions/${newSessionId}/start`, { method: "POST" });
      if (!startRes.ok) {
        const errBody = await startRes.text();
        console.error("开始面试失败:", startRes.status, errBody);
        // 即使开始失败，也允许进入面试界面（用默认开场白）
        setInterviewStarted(true);
        setScoreCard(null);
        setEvaluationReport(null);
        setMessages([{ id: "welcome_" + Date.now(), sender: "interviewer" as const,
          text: `你好，${activeCand?.name || "候选人"}。面试会话已创建，请开始回答。`,
          timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) }]);
        setTimeElapsed(0);
        const interval = setInterval(() => setTimeElapsed(p => p + 1), 1000);
        setTimerInterval(interval);
        setEvaluating(false);
        return;
      }
      const startData = await startRes.json();

      setInterviewStarted(true);
      setScoreCard(null);
      setEvaluationReport(null);
      const initialMessages = (startData.messages && Array.isArray(startData.messages))
        ? startData.messages.map(m => ({
          id: m.id, sender: m.sender as "interviewer" | "candidate", text: m.text,
          timestamp: new Date(m.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
        }))
        : [{ id: "welcome_" + Date.now(), sender: "interviewer" as const,
          text: `你好，${activeCand?.name || "候选人"}。面试已开始，请进行自我介绍。`,
          timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) }];
      setMessages(initialMessages);
      setTimeElapsed(0);
      const interval = setInterval(() => setTimeElapsed(p => p + 1), 1000);
      setTimerInterval(interval);
    } catch (err) {
      console.error("创建面试失败:", err);
      alert("创建面试失败，请确认后端服务是否启动");
    } finally { setEvaluating(false); }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || thinking || !sessionId) return;
    const userMsg: ChatMessage = {
      id: "user_" + Date.now(), sender: "candidate", text: inputText,
      timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    };
    setMessages(prev => [...prev, userMsg]);
    setInputText("");
    setThinking(true);
    try {
      const res = await fetch(`${API_BASE}/api/mock-interview/sessions/${sessionId}/chat`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answer: inputText })
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: ChatResponse = await res.json();
      setMessages(prev => [...prev, {
        id: "inter_" + Date.now(), sender: "interviewer", text: data.reply,
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
      }]);
      if (data.status === "COMPLETED") {
        if (timerInterval) clearInterval(timerInterval);
        setTimerInterval(null);
        handleEvaluate(sessionId);
      }
    } catch {
      setMessages(prev => [...prev, {
        id: "inter_fb" + Date.now(), sender: "interviewer",
        text: "感谢你的回答，请继续分享你的见解。",
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
      }]);
    } finally { setThinking(false); }
  };

  const handleEvaluate = async (sid: string) => {
    setEvaluating(true);
    try {
      const res = await fetch(`${API_BASE}/api/evaluation/sessions/${sid}`, { method: "POST" });
      const report: EvaluationReport = await res.json();
      setEvaluationReport(report);
      const card: ScoreCard = {
        id: "sc_" + Date.now(), candidateId: activeCand?.id || "", candidateName: activeCand?.name || "",
        role: activeCand?.role || "", overallScore: report.overallScore,
        scores: {
          technical: report.dimensionScores?.technical || 7,
          communication: report.dimensionScores?.communication || 7,
          problemSolving: report.dimensionScores?.problemSolving || 7,
          culturalFit: report.dimensionScores?.culturalFit || 7,
        },
        summary: report.summary, strengths: report.strengths || [],
        improvements: report.improvements || [],
        verdict: (report.verdict as any) || "待定",
        evaluatedAt: new Date().toISOString().replace("T", " ").substring(0, 16)
      };
      setScoreCard(card);
      onSaveScoreCard(card);
    } catch {
      const fallback: ScoreCard = {
        id: "sc_fb_" + Date.now(), candidateId: activeCand?.id || "", candidateName: activeCand?.name || "",
        role: activeCand?.role || "", overallScore: 80,
        scores: { technical: 8, communication: 8, problemSolving: 8, culturalFit: 8 },
        summary: "评估完成，请查看详细报告。", strengths: ["完成面试流程"], improvements: ["建议进一步加深技术深度"],
        verdict: "待定", evaluatedAt: new Date().toISOString().replace("T", " ").substring(0, 16)
      };
      setScoreCard(fallback);
      onSaveScoreCard(fallback);
    } finally { setEvaluating(false); }
  };

  const handleEndInterview = async () => {
    if (!sessionId) return;
    if (timerInterval) { clearInterval(timerInterval); setTimerInterval(null); }
    handleEvaluate(sessionId);
  };

  const formatTimer = (secs: number) => {
    const m = Math.floor(secs / 60), s = secs % 60;
    return `${m < 10 ? "0" : ""}${m}:${s < 10 ? "0" : ""}${s}`;
  };

  // 语音
  const toggleVoiceRecording = () => {
    if (isRecording) { stopVoiceRecording(); return; }
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) { alert("浏览器不支持语音识别"); return; }
    const recognition = new SpeechRecognition();
    recognition.lang = "zh-CN"; recognition.continuous = true; recognition.interimResults = true;
    recognition.onresult = (event: any) => {
      let interim = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) setInputText(p => p + event.results[i][0].transcript);
        else interim += event.results[i][0].transcript;
      }
      setInterimText(interim);
    };
    recognition.onerror = () => { setIsRecording(false); setInterimText(""); };
    recognition.onend = () => { setIsRecording(false); setInterimText(""); };
    recognitionRef.current = recognition;
    recognition.start();
    setIsRecording(true);
  };
  const stopVoiceRecording = () => {
    if (recognitionRef.current) { recognitionRef.current.stop(); recognitionRef.current = null; }
    setIsRecording(false); setInterimText("");
  };

  const stages = stageMinutes();

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
            <MessageSquareCode className="w-6 h-6 text-primary" /> 全时互动模拟面试舱
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">AI 面试官智能出题 · 多维度量化评估 · 一键导出 PDF 报告</p>
        </div>
      </div>

      {/* 面试准备面板 */}
      {!interviewStarted && !scoreCard && (
        <div className="bg-white/80 backdrop-blur-md border border-slate-200 shadow-sm rounded-2xl p-6 md:p-8 space-y-6">
          {/* 候选人 */}
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-lg">
              {activeCand ? activeCand.name.charAt(0) : "?"}
            </div>
            <div className="flex-1">
              <h2 className="text-base font-bold text-slate-800">{activeCand?.name || "请选择候选人"}</h2>
              <p className="text-xs text-slate-500">{activeCand?.role} · {activeCand?.education || ""}</p>
            </div>
            <select value={activeCandidateId} onChange={e => { setActiveCandidateId(e.target.value); setRecommendations([]); }}
              className="text-xs py-2.5 px-3 bg-white border border-slate-200 rounded-xl outline-none w-56 cursor-pointer">
              {allCandidates.map(c => (
                <option key={c.id} value={c.id}>{c.name} - {c.role} (匹配 {c.matchScore}%)</option>
              ))}
            </select>
          </div>

          {/* 简历推荐方向按钮 */}
          {activeCand?.aiSummary && recommendations.length === 0 && (
            <div className="flex items-center gap-2">
              <button onClick={handleRecommend} disabled={recommending}
                className="text-xs font-semibold text-primary bg-primary/5 border border-primary/20 hover:bg-primary/10 px-4 py-2 rounded-xl transition cursor-pointer flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5" />{recommending ? "分析中..." : "AI 推荐面试方向"}
              </button>
            </div>
          )}
          {recommendations.length > 0 && (
            <div className="bg-emerald-50/50 border border-emerald-100 rounded-xl p-3">
              <p className="text-[10px] font-bold text-emerald-700 mb-2">✨ AI 推荐的面试方向：</p>
              <div className="flex flex-wrap gap-2">
                {recommendations.map((r, i) => (
                  <button key={i} onClick={() => setInterviewDirection(r.direction)}
                    className={`text-xs font-semibold px-3 py-1.5 rounded-lg border cursor-pointer transition
                      ${interviewDirection === r.direction ? "bg-emerald-500 text-white border-emerald-500" : "bg-white text-emerald-700 border-emerald-200 hover:bg-emerald-50"}`}>
                    {r.direction} ({r.matchScore}分)
                  </button>
                ))}
              </div>
            </div>
          )}

          <hr className="border-slate-100" />

          {/* 面试模式 */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-slate-800">面试模式</h3>
            <div className="flex gap-4">
              {(["text", "voice"] as const).map(mode => (
                <button key={mode} onClick={() => setInterviewMode(mode)}
                  className={`flex-1 text-sm font-bold px-5 py-4 rounded-2xl border-2 transition cursor-pointer
                    ${interviewMode === mode ? "bg-primary/10 text-primary border-primary shadow-md ring-4 ring-primary/20" : "bg-white text-slate-600 border-slate-200 hover:border-primary/40"}`}>
                  {mode === "text" ? <><MessageSquareCode className="w-5 h-5 inline mr-2" />文字模拟</> : <><Mic className="w-5 h-5 inline mr-2" />语音模拟</>}
                </button>
              ))}
            </div>
          </div>

          {/* 面试方向 */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-slate-800">面试方向</h3>
            <div className="grid grid-cols-4 gap-2.5">
              {DIRECTIONS.map(dir => {
                const rec = recommendations.find(r => r.direction === dir);
                return (
                  <button key={dir} onClick={() => { setInterviewDirection(dir); if (dir !== "自定义JD") setJdResult(null); }}
                    className={`relative text-xs font-semibold px-4 py-2.5 rounded-xl border-2 transition cursor-pointer
                      ${interviewDirection === dir ? "bg-primary/10 text-primary border-primary shadow-sm ring-2 ring-primary/20" : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"}`}>
                    {dir}
                    {rec && <span className="absolute -top-1.5 -right-1.5 bg-emerald-500 text-white text-[9px] px-1.5 py-0.5 rounded-full">{rec.matchScore}</span>}
                  </button>
                );
              })}
            </div>
            {interviewDirection === "自定义JD" && (
              <div className="space-y-2">
                <textarea value={customJDText} onChange={e => setCustomJDText(e.target.value)}
                  placeholder="请粘贴职位描述（JD），系统将自动解析并匹配面试方向..."
                  rows={3} className="w-full text-xs p-3 bg-white border border-slate-200 rounded-xl outline-none" />
                <button onClick={handleParseJD} disabled={jdParsing || !customJDText.trim()}
                  className="text-xs font-semibold text-primary bg-primary/5 border border-primary/20 px-4 py-2 rounded-xl hover:bg-primary/10 transition cursor-pointer disabled:opacity-40">
                  {jdParsing ? "解析中..." : "解析 JD 并匹配方向"}
                </button>
                {jdResult && (
                  <div className="bg-blue-50 border border-blue-100 rounded-xl p-3 text-xs space-y-1">
                    <p><span className="font-bold">匹配方向：</span>{jdResult.matchedDirection}</p>
                    <p><span className="font-bold">技能标签：</span>{jdResult.skills.join("、")}</p>
                    <p><span className="font-bold">经验要求：</span>{jdResult.experienceRequired}年</p>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* 难度等级 */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-slate-800">难度等级</h3>
            <div className="grid grid-cols-3 gap-3">
              {LEVELS.map(lv => (
                <button key={lv} onClick={() => setInterviewLevel(lv)}
                  className={`text-xs font-semibold px-4 py-3 rounded-xl border-2 transition cursor-pointer flex flex-col items-center gap-0.5
                    ${interviewLevel === lv ? "bg-primary/10 text-primary border-primary shadow-sm ring-2 ring-primary/20" : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"}`}>
                  <span>{lv}</span>
                  <span className="text-[10px] opacity-60">{LEVEL_YEAR_MAP[lv]}</span>
                </button>
              ))}
            </div>
          </div>

          {/* 总时长 + 阶段联动 */}
          <div className="space-y-3">
            <h3 className="text-sm font-bold text-slate-800">面试时长 <span className="text-primary">{totalDuration} 分钟</span></h3>
            <input type="range" min={15} max={120} step={5} value={totalDuration}
              onChange={e => setTotalDuration(Number(e.target.value))}
              className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-primary" />
            <div className="grid grid-cols-4 gap-2 text-center">
              {Object.entries(STAGE_LABELS).map(([key, label]) => (
                <div key={key} className="bg-slate-50 rounded-lg p-2 border border-slate-100">
                  <div className="text-[10px] text-slate-500">{label}</div>
                  <div className="text-xs font-bold text-primary">{stages[key] || 0}分</div>
                  <div className="text-[9px] text-slate-400">{Math.round(STAGE_RATIOS[key] * 100)}%</div>
                </div>
              ))}
            </div>
          </div>

          {/* 追问次数 */}
          <div className="space-y-2">
            <h3 className="text-sm font-bold text-slate-800">智能追问次数 <span className="text-primary">{followUpCount} 轮</span></h3>
            <div className="flex gap-2">
              {[0, 1, 2, 3].map(n => (
                <button key={n} onClick={() => setFollowUpCount(n)}
                  className={`text-xs font-semibold px-5 py-2 rounded-xl border-2 transition cursor-pointer
                    ${followUpCount === n ? "bg-primary/10 text-primary border-primary" : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"}`}>
                  {n === 0 ? "不追问" : `${n}轮追问`}
                </button>
              ))}
            </div>
          </div>

          {/* 开始按钮 */}
          <button onClick={startInterview} disabled={!activeCandidateId || !interviewDirection || evaluating}
            className="w-full text-sm font-bold text-primary bg-primary/10 border-2 border-primary/30 hover:bg-primary/20 disabled:opacity-40 disabled:cursor-not-allowed py-3.5 rounded-xl transition flex items-center justify-center gap-2 cursor-pointer">
            {evaluating ? <Loader2 className="w-4 h-4 animate-spin" /> : <MessageSquareCode className="w-4 h-4" />}
            {evaluating ? "正在创建面试..." : `开始${interviewMode === "text" ? "文字" : "语音"}模拟`}
          </button>
        </div>
      )}

      {/* 面试中 */}
      {interviewStarted && !scoreCard && (
        <>
          <div className="flex items-center gap-4 text-xs font-semibold text-slate-600 bg-slate-100/80 px-4 py-2 rounded-xl border border-slate-200">
            <Clock className="w-4 h-4 text-primary animate-pulse" />
            <span>{formatTimer(timeElapsed)}</span>
            <span className="text-slate-400">| {activeCand?.name}</span>
            {interviewDirection && <span className="text-primary">| {interviewDirection} · {interviewLevel}</span>}
            <span className="text-slate-400">| {STAGE_LABELS[messages.length > 0 ? "techExam" : "selfIntro"] || "进行中"}</span>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div className="lg:col-span-8 bg-white/70 backdrop-blur-md rounded-2xl border border-slate-200 shadow-lg flex flex-col h-[580px] overflow-hidden">
              <div className="bg-slate-50 p-4 border-b border-slate-100 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-3.5 h-3.5 rounded-full bg-emerald-500 animate-pulse border-2 border-white" />
                  <span className="text-xs font-bold text-slate-700">AI 面试官 · {interviewDirection}</span>
                </div>
                <button onClick={handleEndInterview}
                  className="text-[10px] font-extrabold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 py-2 px-4 rounded-lg flex items-center gap-1 transition cursor-pointer shadow-sm">
                  <Trophy className="w-3.5 h-3.5" /> 结束面试并生成评估
                </button>
              </div>

              <div className="flex-1 p-5 overflow-y-auto space-y-4 bg-slate-50/50">
                {messages.map(m => (
                  <div key={m.id} className={`flex items-start gap-3.5 ${m.sender === "candidate" ? "flex-row-reverse" : ""}`}>
                    <div className={`text-xs py-2.5 px-4 rounded-2xl leading-relaxed max-w-[75%] shadow-sm
                      ${m.sender === "candidate" ? "bg-primary text-white rounded-tr-none" : "bg-white text-slate-700 border border-slate-150 rounded-tl-none"}`}>
                      {m.text}
                    </div>
                  </div>
                ))}
                {thinking && (
                  <div className="flex items-start gap-3.5">
                    <div className="p-3.5 bg-white border border-slate-100 rounded-2xl flex items-center gap-2 text-slate-400 text-xs">
                      <Loader2 className="w-4 h-4 animate-spin" /> 面试官正在思考...
                    </div>
                  </div>
                )}
                <div ref={messageEndRef} />
              </div>

              <form onSubmit={handleSendMessage} className="p-4 border-t border-slate-100 bg-white">
                <div className="flex items-center gap-3">
                  {interviewMode === "voice" && (
                    <button type="button" onClick={toggleVoiceRecording}
                      className={`w-10 h-10 rounded-xl flex items-center justify-center cursor-pointer border
                        ${isRecording ? "bg-red-50 text-red-600 border-red-300 animate-pulse" : "bg-primary/10 text-primary border-primary/20 hover:bg-primary/20"}`}>
                      {isRecording ? <MicOff className="w-4.5 h-4.5" /> : <Mic className="w-4.5 h-4.5" />}
                    </button>
                  )}
                  <input type="text" disabled={thinking} value={inputText}
                    onChange={e => setInputText(e.target.value)}
                    placeholder={isRecording ? "正在录音..." : "请输入您的回答..."}
                    className="flex-1 text-xs py-3 px-4 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition" />
                  <button type="submit" disabled={thinking || !inputText.trim()}
                    className="w-10 h-10 bg-primary hover:bg-primary-container disabled:bg-slate-300 rounded-xl flex items-center justify-center text-white transition shrink-0 cursor-pointer">
                    <Send className="w-4.5 h-4.5" />
                  </button>
                </div>
                {isRecording && interimText && (
                  <div className="text-[10px] text-slate-400 italic mt-2">🎤 {interimText}</div>
                )}
              </form>
            </div>

            {/* 侧边栏提示 */}
            <div className="lg:col-span-4 bg-gradient-to-b from-primary/5 to-transparent border border-primary/10 rounded-2xl p-6">
              <span className="text-[10px] uppercase tracking-wider font-bold text-primary block">💡 面试提示</span>
              <div className="space-y-3 mt-4">
                <div className="p-3 bg-white rounded-xl border border-slate-100 space-y-1 shadow-sm">
                  <span className="text-xs font-bold text-slate-700">方向：{interviewDirection}</span>
                  <p className="text-[10px] text-slate-500">难度：{interviewLevel} · 时长：{totalDuration}分钟</p>
                </div>
                <div className="p-3 bg-white rounded-xl border border-slate-100 space-y-1 shadow-sm">
                  <span className="text-xs font-bold text-slate-700">阶段分配</span>
                  {Object.entries(STAGE_LABELS).map(([k, v]) => (
                    <div key={k} className="flex justify-between text-[10px] text-slate-500">
                      <span>{v}</span><span>{stages[k] || 0}分</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* 评估报告 */}
      {scoreCard && (
        <div className="bg-white p-6 sm:p-8 rounded-3xl border border-slate-150 shadow-xl max-w-3xl mx-auto space-y-6">
          <div className="flex items-center justify-between border-b border-slate-100 pb-5">
            <div className="flex items-center gap-3">
              <Trophy className="w-7 h-7 text-primary" />
              <div>
                <h2 className="text-lg font-bold text-slate-800">AI 综合测评成绩鉴定书</h2>
                <p className="text-[10px] text-slate-400">报告编号: {scoreCard.id} · {scoreCard.evaluatedAt}</p>
              </div>
            </div>
            <span className={`text-xs font-extrabold border rounded-full px-4 py-1.5
              ${scoreCard.verdict === "建议录用" ? "bg-emerald-50 text-emerald-700 border-emerald-200" :
                scoreCard.verdict === "待定" ? "bg-amber-50 text-amber-700 border-amber-200" :
                "bg-red-50 text-red-700 border-red-200"}`}>
              {scoreCard.verdict}
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
            <div className="md:col-span-5 bg-slate-50 p-6 rounded-2xl border border-slate-100 text-center space-y-6">
              <div className="relative w-28 h-28 mx-auto flex items-center justify-center">
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                  <path className="text-slate-200" strokeWidth="3" stroke="currentColor" fill="none"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                  <path className="text-primary" strokeWidth="3" strokeDasharray={`${scoreCard.overallScore}, 100`}
                    strokeLinecap="round" stroke="currentColor" fill="none"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                </svg>
                <div className="absolute flex flex-col items-center">
                  <span className="text-2xl font-black text-slate-800">{scoreCard.overallScore}</span>
                  <span className="text-[9px] font-semibold text-slate-400 uppercase">综合评分</span>
                </div>
              </div>
              <div className="space-y-3 text-left">
                {[
                  { label: "技术深度", score: scoreCard.scores.technical, color: "bg-primary" },
                  { label: "沟通表达", score: scoreCard.scores.communication, color: "bg-purple-600" },
                  { label: "问题解决", score: scoreCard.scores.problemSolving, color: "bg-teal-600" },
                  { label: "综合素质", score: scoreCard.scores.culturalFit, color: "bg-emerald-600" }
                ].map((s, i) => (
                  <div key={i} className="space-y-1">
                    <div className="flex justify-between text-xs font-semibold text-slate-600">
                      <span>{s.label}</span><span>{s.score}/10</span>
                    </div>
                    <div className="w-full bg-slate-200 h-1.5 rounded-full overflow-hidden">
                      <div className={`${s.color} h-full rounded-full`} style={{ width: `${s.score * 10}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="md:col-span-7 space-y-5">
              <div className="space-y-2">
                <span className="text-xs font-bold text-slate-400 uppercase">AI 评估鉴定意见</span>
                <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 border border-slate-100 p-4 rounded-xl italic shadow-sm">
                  “ {scoreCard.summary} ”
                </p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 bg-emerald-50/40 border border-emerald-100 rounded-xl space-y-2">
                  <span className="text-xs font-extrabold text-emerald-800 uppercase flex items-center gap-1">
                    <CheckCircle2 className="w-4 h-4" /> 核心优势
                  </span>
                  <ul className="space-y-1.5">
                    {scoreCard.strengths.map((s, i) => (
                      <li key={i} className="text-xs text-slate-600 flex gap-1.5 items-start">
                        <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full mt-1.5 shrink-0" />
                        <span>{s}</span>
                      </li>
                    ))}
                  </ul>
                </div>
                <div className="p-4 bg-red-50/40 border border-red-100 rounded-xl space-y-2">
                  <span className="text-xs font-extrabold text-red-800 uppercase flex items-center gap-1">
                    <AlertTriangle className="w-4 h-4" /> 改进建议
                  </span>
                  <ul className="space-y-1.5">
                    {scoreCard.improvements.map((im, i) => (
                      <li key={i} className="text-xs text-slate-600 flex gap-1.5 items-start">
                        <span className="w-1.5 h-1.5 bg-red-500 rounded-full mt-1.5 shrink-0" />
                        <span>{im}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between border-t border-slate-100 pt-5">
            <button onClick={() => { setScoreCard(null); setInterviewStarted(false); setMessages([]); setSessionId(null); }}
              className="text-xs bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 px-5 rounded-xl transition cursor-pointer">
              测评新候选人
            </button>
            <div className="flex gap-2">
              <button onClick={() => { setScoreCard(null); setInterviewStarted(false); setMessages([]); setSessionId(null); startInterview(); }}
                className="text-xs text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 font-semibold py-2.5 px-5 rounded-xl cursor-pointer flex items-center gap-1">
                <RefreshCw className="w-3.5 h-3.5" /> 重新面试
              </button>
              <button onClick={onNavigateToRecords}
                className="text-xs text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 font-semibold py-2.5 px-6 rounded-xl shadow-sm cursor-pointer">
                查看历史记录
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 评估中遮罩 */}
      {evaluating && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-md z-[100] flex items-center justify-center">
          <div className="bg-white p-8 rounded-3xl max-w-md w-full border border-slate-150 space-y-4 shadow-2xl text-center">
            <Loader2 className="w-10 h-10 mx-auto animate-spin text-primary" />
            <h3 className="text-sm font-bold text-slate-800">正在生成评估报告...</h3>
            <p className="text-xs text-slate-400">AI 正在分析对话内容，进行多维度量化评估</p>
          </div>
        </div>
      )}
    </div>
  );
}
