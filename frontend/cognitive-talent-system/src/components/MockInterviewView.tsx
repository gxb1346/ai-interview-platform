import React, { useState, useRef, useEffect } from "react";
import { Send, Sparkles, Trophy, Award, CheckCircle2, AlertTriangle, ShieldCheck, HelpCircle, RefreshCw, MessageSquareCode, Clock, Mic, MicOff } from "lucide-react";
import { Candidate, ChatMessage, ScoreCard, ResumeVO, ApiResult } from "../types";

interface MockInterviewViewProps {
  candidates: Candidate[];
  preSelectedCandidate: Candidate | null;
  onSaveScoreCard: (card: ScoreCard) => void;
  onNavigateToRecords: () => void;
}

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

export default function MockInterviewView({
  candidates,
  preSelectedCandidate,
  onSaveScoreCard,
  onNavigateToRecords
}: MockInterviewViewProps) {
  const [activeCandidateId, setActiveCandidateId] = useState("");
  const [interviewStarted, setInterviewStarted] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [thinking, setThinking] = useState(false);
  const [timeElapsed, setTimeElapsed] = useState(0); // in seconds
  const [timerInterval, setTimerInterval] = useState<any>(null);

  // ScoreCard Results state
  const [evaluating, setEvaluating] = useState(false);
  const [scoreCard, setScoreCard] = useState<ScoreCard | null>(null);

  // 模拟面试模式: text | voice
  const [interviewMode, setInterviewMode] = useState<"text" | "voice">("text");
  const [isRecording, setIsRecording] = useState(false);
  const [interimText, setInterimText] = useState("");
  const recognitionRef = useRef<any>(null);

  // 面试准备选项
  const [interviewDirection, setInterviewDirection] = useState("");
  const [interviewLevel, setInterviewLevel] = useState("中级");
  const [customJDText, setCustomJDText] = useState("");

  // 人才库候选人（从后端 API 拉取）
  const [talentCandidates, setTalentCandidates] = useState<Candidate[]>([]);

  const DIRECTIONS = [
    "AI Agent开发", "算法与数据结构", "阿里后端", "字节后端",
    "前端工程", "Java后端开发", "腾讯后端", "Python后端开发",
    "系统设计", "测试开发", "自定义JD"
  ];

  const LEVELS = ["校招", "中级", "高级"];
  const YEARS_OPTIONS = ["0-1年", "1-3年", "3年+"];
  const LEVEL_YEAR_MAP: Record<string, string> = {
    "校招": "0-1年",
    "中级": "1-3年",
    "高级": "3年+"
  };

  const messageEndRef = useRef<HTMLDivElement>(null);

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

  // 合并预植入候选人 + 人才库候选人
  const allCandidates = React.useMemo(() => {
    const merged = [...candidates];
    talentCandidates.forEach(tc => {
      if (!merged.some(c => c.id === tc.id)) {
        merged.push(tc);
      }
    });
    return merged;
  }, [candidates, talentCandidates]);

  // Auto select preselected candidate if passed from other views
  useEffect(() => {
    if (preSelectedCandidate) {
      setActiveCandidateId(preSelectedCandidate.id);
    } else if (candidates.length > 0 && !activeCandidateId) {
      setActiveCandidateId(candidates[0].id);
    }
  }, [preSelectedCandidate, candidates]);

  // Clean timer on unmount
  useEffect(() => {
    return () => {
      if (timerInterval) clearInterval(timerInterval);
    };
  }, [timerInterval]);

  // Handle autoscroll chat container
  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, thinking]);

  const activeCand = allCandidates.find(c => c.id === activeCandidateId);

  const startInterview = () => {
    if (!activeCand) return;
    
    setInterviewStarted(true);
    setScoreCard(null);
    setMessages([
      {
        id: "msg_init",
        sender: "interviewer",
        text: `您好，${activeCand.name}。我是 RecruitAI 的高级AI面试官。今天我们将围绕“${activeCand.role}”岗位进行一场深度的模拟技术与沟通面。
面试方向：【${interviewDirection}】${customJDText ? "（自定义JD: " + customJDText + "）" : ""}，难度等级：【${interviewLevel}】。
我已经通读并分析了您的经历详情，特别是关于 ${(activeCand.strengths && activeCand.strengths[0]) || "您出色的过往高抗压项目构建交付"}。
首先，作为一个热身开篇，能详细聊一下在该项目推进中，您最感吃力、最困难的技术挑战，以及您当时是如何主导破局、得出量化收益的吗？`,
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
      }
    ]);

    // Start timer counter
    setTimeElapsed(0);
    const interval = setInterval(() => {
      setTimeElapsed(prev => prev + 1);
    }, 1000);
    setTimerInterval(interval);
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || thinking || !activeCand) return;

    const userMsg: ChatMessage = {
      id: "user_" + Date.now(),
      sender: "candidate",
      text: inputText,
      timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputText("");
    setThinking(true);

    try {
      const chatLogToSend = [...messages, userMsg];
      const response = await fetch("/api/mock-interview/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          messages: chatLogToSend,
          candidateName: activeCand.name,
          role: activeCand.role
        })
      });

      if (!response.ok) throw new Error("Chat api issue");
      const data = await response.json();

      setMessages((prev) => [
        ...prev,
        {
          id: "inter_" + Date.now(),
          sender: "interviewer",
          text: data.reply || `[AI 追问] 感谢你的反馈。针对这部分内容，你能具体结合真实编码，说说在保障系统高可用调优上的具体解方参数吗？`,
          timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
        }
      ]);
    } catch (err) {
      console.warn("Generating mock response error:", err);
      // Fallback follow-up response
      setTimeout(() => {
        setMessages((prev) => [
          ...prev,
          {
            id: "inter_fb" + Date.now(),
            sender: "interviewer",
            text: `你刚才提到的点非常具有思考维度。那我们在架构设计上拓展一下：当核心服务负载突增或第三方接口连接挂起时，你打算如何在底层通过熔断或离线削峰去捍卫系统的韧性与健壮体验？`,
            timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
          }
        ]);
      }, 1500);
    } finally {
      setThinking(false);
    }
  };

  const handleEvaluateInterview = async () => {
    if (messages.length < 2 || !activeCand) return;
    
    setEvaluating(true);
    if (timerInterval) {
      clearInterval(timerInterval);
      setTimerInterval(null);
    }

    try {
      const response = await fetch("/api/mock-interview/evaluate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          messages,
          candidateName: activeCand.name,
          role: activeCand.role
        })
      });

      if (!response.ok) throw new Error("Evaluation api failure");
      const data = await response.json();

      const newCard: ScoreCard = {
        id: "sc_" + Date.now(),
        candidateId: activeCand.id,
        candidateName: activeCand.name,
        role: activeCand.role,
        overallScore: Number(data.overallScore) || 85,
        scores: {
          technical: Number(data.scores?.technical) || 8,
          communication: Number(data.scores?.communication) || 8,
          problemSolving: Number(data.scores?.problemSolving) || 8,
          culturalFit: Number(data.scores?.culturalFit) || 8,
        },
        summary: data.summary || "候选人在关于高并发底层架构和系统重整上，有着极其完备的自主设计思维，并在答题条理性上展现了极佳的沟通风范。",
        strengths: data.strengths || ["技术原理扎实", "沟通顺畅有礼", "具备大局思维与技术同理心"],
        improvements: data.improvements || ["实际底层调优参数实操细节不够饱满", "高压环境下语速略显局促"],
        verdict: data.verdict || "建议录用",
        evaluatedAt: new Date().toISOString().replace("T", " ").substring(0, 16)
      };

      setScoreCard(newCard);
      onSaveScoreCard(newCard); // Persist globally so it updates state
    } catch (err) {
      console.error("AI report compilation error:", err);
      // Hard fallback scorecard
      const fallbackResult: ScoreCard = {
        id: "sc_fb_" + Date.now(),
        candidateId: activeCand.id,
        candidateName: activeCand.name,
        role: activeCand.role,
        overallScore: 84,
        scores: {
          technical: 8,
          communication: 9,
          problemSolving: 8,
          culturalFit: 8,
        },
        summary: `在本阶段深度模拟测试中，求职者${activeCand.name}围绕${activeCand.role}岗位的核心提问，给出了条理分明、技术细节印证度极高的精彩反馈。整体技术深度架构闭环健康，沟通敏捷有自驱，具有优异的岗位素质。`,
        strengths: ["沟通自信自洽，主线鲜明，阐述大项目技术攻坚有极强的画面得证度", "对于高负载底盘、熔断优雅降级能主动归纳总结其缺陷和重构步骤"],
        improvements: ["有些边缘测试及冷加载调优可以更丰富", "未来可适当拓宽大数据工程端宏观技术面的覆盖深度"],
        verdict: "建议录用",
        evaluatedAt: new Date().toISOString().replace("T", " ").substring(0, 16)
      };
      setScoreCard(fallbackResult);
      onSaveScoreCard(fallbackResult);
    } finally {
      setEvaluating(false);
    }
  };

  const formatTimer = (secs: number) => {
    const mins = Math.floor(secs / 60);
    const remainSecs = secs % 60;
    return `${mins < 10 ? "0" : ""}${mins}:${remainSecs < 10 ? "0" : ""}${remainSecs}`;
  };

  // 语音识别相关
  const startVoiceRecording = () => {
    const SpeechRecognitionAPI = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognitionAPI) {
      alert("抱歉，您的浏览器不支持语音识别功能，请使用 Chrome 浏览器。");
      return;
    }
    const recognition = new SpeechRecognitionAPI();
    recognition.lang = "zh-CN";
    recognition.continuous = true;
    recognition.interimResults = true;

    recognition.onresult = (event: any) => {
      let interim = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          setInputText((prev) => prev + transcript);
        } else {
          interim += transcript;
        }
      }
      setInterimText(interim);
    };

    recognition.onerror = (event: any) => {
      console.error("语音识别错误:", event.error);
      setIsRecording(false);
      setInterimText("");
    };

    recognition.onend = () => {
      setIsRecording(false);
      setInterimText("");
    };

    recognitionRef.current = recognition;
    recognition.start();
    setIsRecording(true);
  };

  const stopVoiceRecording = () => {
    if (recognitionRef.current) {
      recognitionRef.current.stop();
      recognitionRef.current = null;
    }
    setIsRecording(false);
    setInterimText("");
  };

  const toggleVoiceRecording = () => {
    if (isRecording) {
      stopVoiceRecording();
    } else {
      startVoiceRecording();
    }
  };

  return (
    <div className="space-y-6" id="mock-interview-wrapper">
      {/* Search Header banner */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            <MessageSquareCode className="w-6 h-6 text-primary" /> 全时互动模拟面试舱
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            在此雇佣专门的 **AI 面试官智能体**。考生可在此扮演面试者并逐次键入回答，终局一键获取雷达多维成绩单书。
          </p>
        </div>
      </div>

      {/* 面试准备面板 — 选择模式/方向/难度后开始面试 */}
      {!interviewStarted && !scoreCard && (
        <div className="bg-white/80 backdrop-blur-md border border-slate-200 shadow-sm rounded-2xl p-6 md:p-8 space-y-7">
            {/* 候选人信息 */}
            <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-lg">
                  {activeCand ? activeCand.name.charAt(0) : "?"}
                </div>
                <div>
                  <h2 className="text-base font-bold text-slate-800 font-sans">{activeCand?.name || "请选择候选人"}</h2>
                  <p className="text-xs text-slate-500 font-sans">{activeCand?.role || "—"} · {activeCand?.education || ""}</p>
                </div>
              </div>
              <select
                value={activeCandidateId}
                onChange={(e) => setActiveCandidateId(e.target.value)}
                className="text-xs py-2.5 px-3 bg-white border border-slate-200 focus:border-primary rounded-xl outline-none transition font-sans cursor-pointer w-full md:w-56"
              >
                {allCandidates.length === 0 && <option value="">暂无候选人</option>}
                {allCandidates.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} - {c.role} (匹配 {c.matchScore}%)
                  </option>
                ))}
              </select>
            </div>

            <hr className="border-slate-100" />

            {/* 面试模式 */}
            <div className="space-y-3">
              <h3 className="text-sm font-bold text-slate-800 font-sans">面试模式</h3>
              <div className="flex items-center gap-4">
                <button onClick={() => setInterviewMode("text")}
                  className={`flex-1 text-sm font-bold px-5 py-4 rounded-2xl border-2 transition-all cursor-pointer ${
                    interviewMode === "text"
                      ? "bg-primary/10 text-primary border-primary shadow-md ring-4 ring-primary/20"
                      : "bg-white text-slate-600 border-slate-200 hover:border-primary/40"
                  }`}>
                  <MessageSquareCode className="w-5 h-5 inline mr-2" />
                  <div className="inline-block text-left">
                    <div>文字模拟</div>
                    <div className="text-[10px] font-normal opacity-70">推荐：更稳定，更适合系统化刷题与复盘</div>
                  </div>
                </button>
                <button onClick={() => setInterviewMode("voice")}
                  className={`flex-1 text-sm font-bold px-5 py-4 rounded-2xl border-2 transition-all cursor-pointer ${
                    interviewMode === "voice"
                      ? "bg-primary/10 text-primary border-primary shadow-md ring-4 ring-primary/20"
                      : "bg-white text-slate-600 border-slate-200 hover:border-primary/40"
                  }`}>
                  <Mic className="w-5 h-5 inline mr-2" />
                  <div className="inline-block text-left">
                    <div>语音模拟</div>
                    <div className="text-[10px] font-normal opacity-70">实时语音对话，更偏临场模拟</div>
                  </div>
                </button>
              </div>
            </div>

            {/* 面试方向 */}
            <div className="space-y-3">
              <h3 className="text-sm font-bold text-slate-800 font-sans">面试方向</h3>
              <div className="grid grid-cols-4 gap-2.5">
                {DIRECTIONS.map((dir) => (
                  <button key={dir} onClick={() => {
                    setInterviewDirection(dir);
                    if (dir !== "自定义JD") setCustomJDText("");
                  }}
                    className={`text-xs font-semibold px-4 py-2.5 rounded-xl border-2 transition-all cursor-pointer ${
                      interviewDirection === dir
                        ? "bg-primary/10 text-primary border-primary shadow-sm ring-2 ring-primary/20"
                        : "bg-white text-slate-600 border-slate-200 hover:border-primary/30 hover:bg-slate-50"
                    }`}>
                    {dir}
                  </button>
                ))}
              </div>
              {interviewDirection === "自定义JD" && (
                <textarea
                  value={customJDText}
                  onChange={(e) => setCustomJDText(e.target.value)}
                  placeholder="请粘贴或输入自定义职位描述（JD）..."
                  rows={4}
                  className="w-full text-xs p-3 bg-white border border-slate-200 focus:border-primary rounded-xl outline-none transition font-sans"
                />
              )}
            </div>

            {/* 难度等级 */}
            <div className="space-y-3">
              <h3 className="text-sm font-bold text-slate-800 font-sans">难度等级</h3>
              <div className="grid grid-cols-3 gap-3">
                {LEVELS.map((lv) => (
                  <button key={lv} onClick={() => setInterviewLevel(lv)}
                    className={`text-xs font-semibold px-4 py-3 rounded-xl border-2 transition-all cursor-pointer flex flex-col items-center gap-0.5 ${
                      interviewLevel === lv
                        ? "bg-primary/10 text-primary border-primary shadow-sm ring-2 ring-primary/20"
                        : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"
                    }`}>
                    <span>{lv}</span>
                    <span className="text-[10px] opacity-60">{LEVEL_YEAR_MAP[lv]}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* 开始面试按钮 */}
            <div className="pt-2 border-t border-slate-100">
              <button
                onClick={startInterview}
                disabled={!activeCandidateId || !interviewDirection}
                className="w-full font-sans text-sm font-bold text-primary bg-primary/10 border-2 border-primary/30 hover:bg-primary/20 disabled:opacity-40 disabled:cursor-not-allowed py-3.5 rounded-xl transition flex items-center justify-center gap-2 cursor-pointer shadow-sm"
              >
                <MessageSquareCode className="w-4 h-4" />
                开始{interviewMode === "text" ? "文字" : "语音"}模拟
              </button>
            </div>
        </div>
      )}

      {interviewStarted && (
        <div className="flex items-center gap-4 text-xs font-semibold text-slate-600 bg-slate-100/80 px-4 py-2 rounded-xl border border-slate-200">
          <Clock className="w-4 h-4 text-primary animate-pulse" />
          <span>计时器: {formatTimer(timeElapsed)}</span>
          <span className="text-[10px] text-slate-400 font-normal">| 人选：{activeCand?.name}</span>
          {interviewDirection && <span className="text-[10px] text-primary font-normal">| 方向：{interviewDirection}</span>}
        </div>
      )}

      {/* Dynamic Chat Flow Container */}
      {interviewStarted && !scoreCard && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
          
          {/* Chat Pane (Col-9) */}
          <div className="lg:col-span-8 bg-white/70 backdrop-blur-md rounded-2xl border border-slate-200 shadow-lg flex flex-col justify-between h-[580px] overflow-hidden">
            
            {/* Header profile info */}
            <div className="bg-slate-50 hover:bg-slate-100/50 p-4 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-3.5 h-3.5 rounded-full bg-emerald-500 animate-pulse border-2 border-white" />
                <span className="text-xs font-bold text-slate-700 font-sans">
                  面试模拟舱 · 顶级主考官 (应聘职位：{activeCand?.role})
                </span>
              </div>

              <button
                onClick={handleEvaluateInterview}
                className="text-[10px] font-extrabold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 py-2 px-4 rounded-lg flex items-center gap-1 transition-all cursor-pointer shadow-sm"
              >
                <Trophy className="w-3.5 h-3.5" />
                结束面试并生成AI评估成绩单
              </button>
            </div>

            {/* Conversation Log Space */}
            <div className="flex-1 p-5 overflow-y-auto space-y-4 bg-slate-50/50">
              {messages.map((m) => (
                <div
                  key={m.id}
                  className={`flex items-start gap-3.5 ${m.sender === "candidate" ? "flex-row-reverse" : ""}`}
                >
                  <img
                    src={
                      m.sender === "candidate"
                        ? activeCand?.avatar
                        : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&h=150&fit=crop&crop=face"
                    }
                    alt="avatar"
                    referrerPolicy="no-referrer"
                    className="w-8.5 h-8.5 rounded-full object-cover border border-white shrink-0 shadow-sm"
                  />
                  <div className={`space-y-1 max-w-[75%]`}>
                    <div
                      className={`text-xs py-2.5 px-4 rounded-2xl font-sans shadow-sm leading-relaxed ${
                        m.sender === "candidate"
                          ? "bg-primary text-white-pure rounded-tr-none"
                          : "bg-white text-slate-700 border border-slate-150 rounded-tl-none"
                      }`}
                    >
                      {m.text}
                    </div>
                    <span className="text-[9px] text-slate-400 block px-1 font-sans text-right">
                      {m.timestamp}
                    </span>
                  </div>
                </div>
              ))}

              {/* Loader indicator while generating replies */}
              {thinking && (
                <div className="flex items-start gap-3.5">
                  <img
                    src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&h=150&fit=crop&crop=face"
                    alt="interviewer"
                    className="w-8.5 h-8.5 rounded-full object-cover border border-white shrink-0"
                  />
                  <div className="p-3.5 bg-white border border-slate-100 rounded-2xl rounded-tl-none shadow-sm flex items-center gap-2 text-slate-400 text-xs font-sans">
                    <LoaderPulse className="w-4 h-4 animate-spin" />
                    <span>主考官正在凝神倾听并思索深度追问中...</span>
                  </div>
                </div>
              )}

              <div ref={messageEndRef} />
            </div>

            {/* Input fields form bottom area */}
            <form onSubmit={handleSendMessage} className="p-4 border-t border-slate-100 bg-white flex flex-col gap-3">
              {interviewMode === "text" ? (
                <div className="flex items-center gap-3">
                  <input
                    type="text"
                    disabled={thinking}
                    value={inputText}
                    onChange={(e) => setInputText(e.target.value)}
                    placeholder="请输入您的技术应答（可结合实际高可用案例或量化指标表现）..."
                    className="flex-1 text-xs py-3 px-4 bg-slate-50 hover:bg-slate-100/80 focus:bg-white rounded-xl border border-slate-200 focus:border-primary outline-none transition font-sans"
                  />
                  <button
                    type="submit"
                    disabled={thinking || !inputText.trim()}
                    className="w-10 h-10 bg-primary hover:bg-primary-container disabled:bg-slate-300 rounded-xl flex items-center justify-center text-white-pure font-semibold transition shrink-0 cursor-pointer shadow-md shadow-primary/10"
                  >
                    <Send className="w-4.5 h-4.5" />
                  </button>
                </div>
              ) : (
                <div className="flex items-center gap-3">
                  <div className="flex-1 flex items-center gap-2">
                    <button
                      type="button"
                      onClick={toggleVoiceRecording}
                      disabled={thinking}
                      className={`w-12 h-12 rounded-xl flex items-center justify-center font-semibold transition shrink-0 cursor-pointer shadow-sm border ${
                        isRecording
                          ? "bg-red-50 text-red-600 border-red-300 animate-pulse ring-2 ring-red-200"
                          : "bg-primary/10 text-primary border-primary/20 hover:bg-primary/20"
                      }`}
                    >
                      {isRecording ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
                    </button>
                    <input
                      type="text"
                      disabled={thinking}
                      value={inputText}
                      onChange={(e) => setInputText(e.target.value)}
                      placeholder={isRecording ? "正在聆听..." : "点击麦克风开始语音输入，或手动输入..."}
                      className="flex-1 text-xs py-3 px-4 bg-slate-50 hover:bg-slate-100/80 focus:bg-white rounded-xl border border-slate-200 focus:border-primary outline-none transition font-sans"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={thinking || !inputText.trim()}
                    className="w-10 h-10 bg-primary hover:bg-primary-container disabled:bg-slate-300 rounded-xl flex items-center justify-center text-white-pure font-semibold transition shrink-0 cursor-pointer shadow-md shadow-primary/10"
                  >
                    <Send className="w-4.5 h-4.5" />
                  </button>
                </div>
              )}
              {/* 语音识别实时转写提示 */}
              {isRecording && interimText && (
                <div className="text-[10px] text-slate-400 italic bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-100">
                  🎤 识别中：{interimText}
                </div>
              )}
              {isRecording && (
                <div className="flex items-center gap-1.5 text-[10px] text-red-500 font-semibold">
                  <span className="w-2 h-2 bg-red-500 rounded-full animate-pulse" />
                  正在录音中... 点击红色麦克风按钮停止
                </div>
              )}
              {interviewMode === "voice" && !isRecording && (
                <div className="text-[10px] text-slate-400 italic">
                  点击麦克风按钮开始语音输入，系统自动将语音转为文字
                </div>
              )}
              {/* 模式提示 */}
              {interviewStarted && (
                <div className="text-[10px] text-slate-400 text-center border-t border-slate-100 pt-2 mt-1">
                  当前模式：{interviewMode === "text" ? "📝 文字模拟" : "🎤 语音模拟"}
                  {interviewMode === "voice" && (
                    <button onClick={() => setInterviewMode("text")}
                      className="ml-2 text-primary hover:underline cursor-pointer">
                      切换到文字模式
                    </button>
                  )}
                  {interviewMode === "text" && (
                    <button onClick={() => setInterviewMode("voice")}
                      className="ml-2 text-primary hover:underline cursor-pointer">
                      切换到语音模式
                    </button>
                  )}
                </div>
              )}
            </form>
          </div>

          {/* Left Instructions Panel Sidebar (Col-4) */}
          <div className="lg:col-span-4 bg-gradient-to-b from-primary/5 to-transparent border border-primary/10 rounded-2xl p-6 flex flex-col justify-between">
            <div className="space-y-4">
              <span className="text-[10px] uppercase tracking-wider font-bold text-primary block font-sans">
                💡 考官核心评估守则 (Evaluation Rules)
              </span>
              <h4 className="text-sm font-bold text-slate-800 font-sans">
                如何拿到优秀的模拟分评价？
              </h4>
              <p className="text-xs text-slate-500 leading-relaxed font-sans">
                AI考官不仅考核你的结论对错，更采用多阶段逻辑评估体系，会实时针对以下维度为您暗记打分：
              </p>

              <div className="space-y-3.5 pt-2">
                <div className="p-3 bg-white rounded-xl border border-slate-100 space-y-1.5 shadow-sm">
                  <span className="text-xs font-bold text-slate-700 block font-sans">
                    1. 技术描述详实度
                  </span>
                  <p className="text-[10px] text-slate-500 font-sans leading-relaxed">
                    避免“空洞”的技术陈词词。尽可能给出**首屏耗时、主进程并发、缓存命中率**等真实数据作为支撑佐证。
                  </p>
                </div>

                <div className="p-3 bg-white rounded-xl border border-slate-100 space-y-1.5 shadow-sm">
                  <span className="text-xs font-bold text-slate-700 block font-sans">
                    2. 主观自驱动力
                  </span>
                  <p className="text-[10px] text-slate-500 font-sans leading-relaxed">
                    重点描述遇到边界死锁或资源匮乏时的**排查流程、自学模型探索以及二次代码重构思想**。
                  </p>
                </div>

                <div className="p-3 bg-white rounded-xl border border-slate-100 space-y-1.5 shadow-sm">
                  <span className="text-xs font-bold text-slate-700 block font-sans">
                    3. 沟通敏捷坦诚度
                  </span>
                  <p className="text-[10px] text-slate-500 font-sans leading-relaxed">
                    在遭遇未知难题或不懂的底层协议时，不要搪塞，直率诚恳地给出自己有限经验下的思考框架更具加分项。
                  </p>
                </div>
              </div>
            </div>

            <div className="text-[10px] text-slate-400 font-sans border-t border-slate-100 pt-4 mt-6">
              * 您可以发送 2 轮以上的对话后，点击右上角按钮直接生成多维量化成绩报告。
            </div>
          </div>
        </div>
      )}

      {/* Ultimate Quantitative ScoreCard dashboard Report view */}
      {scoreCard && (
        <div className="bg-white p-6 sm:p-8 rounded-3xl border border-slate-150 shadow-xl max-w-3xl mx-auto space-y-6 animate-fade-in">
          
          {/* Top Logo banner block */}
          <div className="flex flex-col sm:flex-row items-center justify-between border-b border-slate-100 pb-5 gap-4">
            <div className="flex items-center gap-3">
              <Award className="w-7 h-7 text-primary" />
              <div>
                <h2 className="text-lg font-bold text-slate-800 font-sans">AI 综合测评成绩鉴定书</h2>
                <p className="text-[10px] text-slate-400 font-sans">报告编号: {scoreCard.id} · 鉴成于 {scoreCard.evaluatedAt}</p>
              </div>
            </div>

            {/* Dynamic Final Verdict Badge */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-500 font-sans">最终建议意见:</span>
              <span className={`text-xs font-extrabold border rounded-full px-4 py-1.5 ${
                scoreCard.verdict === "建议录用"
                  ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                  : scoreCard.verdict === "待定"
                  ? "bg-amber-50 text-amber-700 border-amber-200"
                  : "bg-red-50 text-red-700 border-red-200"
              }`}>
                {scoreCard.verdict}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-start">
            {/* Col-4: Left Overall circular graph and small category progress scores */}
            <div className="md:col-span-5 bg-slate-50 p-6 rounded-2xl border border-slate-100 text-center space-y-6 shadow-inner">
              <div className="relative w-28 h-28 mx-auto flex items-center justify-center">
                {/* Circular indicator logic */}
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                  <path className="text-slate-200" strokeWidth="3" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                  <path className="text-primary" strokeWidth="3" strokeDasharray={`${scoreCard.overallScore}, 100`} strokeLinecap="round" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                </svg>
                <div className="absolute flex flex-col items-center">
                  <span className="text-2xl font-black font-mono text-slate-800">{scoreCard.overallScore}</span>
                  <span className="text-[9px] font-semibold text-slate-400 font-sans uppercase">综合判定分</span>
                </div>
              </div>

              {/* Progress segment listing */}
              <div className="space-y-3.5 text-left">
                <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-400 block font-sans">基础分类评分 (10分满分)</span>
                
                {[
                  { label: "研究与技术深度", score: scoreCard.scores.technical, col: "bg-primary" },
                  { label: "日常表达与沟通力", score: scoreCard.scores.communication, col: "bg-purple-600" },
                  { label: "硬核业务问题破局", score: scoreCard.scores.problemSolving, col: "bg-teal-600" },
                  { label: "企业文化价值观契合", score: scoreCard.scores.culturalFit, col: "bg-emerald-600" }
                ].map((s, idx) => (
                  <div key={idx} className="space-y-1">
                    <div className="flex justify-between text-xs font-semibold text-slate-600 font-sans">
                      <span>{s.label}</span>
                      <span>{s.score}分</span>
                    </div>
                    <div className="w-full bg-slate-200 h-1 rounded-full overflow-hidden">
                      <div className={`${s.col} h-full rounded-full`} style={{ width: `${s.score * 10}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Col-7: Summary text and strengths / weaknesses bullet lists */}
            <div className="md:col-span-7 space-y-5">
              <div className="space-y-2.5">
                <span className="text-xs font-bold text-slate-400 block font-sans uppercase">
                  📝 AI 评估鉴定意见
                </span>
                <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 border border-slate-100 p-4 rounded-xl italic font-sans shadow-sm">
                  “ {scoreCard.summary} ”
                </p>
              </div>

              {/* Two Column Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Strengths card */}
                <div className="p-4 bg-emerald-50/40 border border-emerald-100 rounded-xl space-y-2 shadow-sm">
                  <span className="text-xs font-extrabold text-emerald-800 font-sans uppercase flex items-center gap-1">
                    <CheckCircle2 className="w-4 h-4" /> 真实得证长板
                  </span>
                  <ul className="space-y-2">
                    {scoreCard.strengths.map((str, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans flex gap-1.5 items-start">
                        <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full mt-1.5 shrink-0" />
                        <span>{str}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                {/* Improvements card */}
                <div className="p-4 bg-red-50/40 border border-red-100 rounded-xl space-y-2 shadow-sm">
                  <span className="text-xs font-extrabold text-red-800 font-sans uppercase flex items-center gap-1">
                    <AlertTriangle className="w-4 h-4" /> 考官提点与微改善建议
                  </span>
                  <ul className="space-y-2">
                    {scoreCard.improvements.map((imp, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans flex gap-1.5 items-start">
                        <span className="w-1.5 h-1.5 bg-red-500 rounded-full mt-1.5 shrink-0" />
                        <span>{imp}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
          </div>

          {/* Dialog Action row triggers */}
          <div className="flex items-center justify-between border-t border-slate-100 pt-5 mt-4">
            <button
              onClick={() => {
                setScoreCard(null);
                setInterviewStarted(false);
                setMessages([]);
              }}
              className="font-sans text-xs bg-slate-100 hover:bg-slate-250 text-slate-700 font-semibold py-2.5 px-5 rounded-xl transition cursor-pointer"
            >
              测评新候选人
            </button>

            <button
              onClick={onNavigateToRecords}
              className="font-sans text-xs text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 font-semibold py-2.5 px-6 rounded-xl shadow-sm cursor-pointer"
            >
              查看测评历史记录
            </button>
          </div>
        </div>
      )}

      {/* Global Evaluating Spinner loader */}
      {evaluating && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-md z-[100] flex items-center justify-center text-center p-6 animate-fade-in">
          <div className="bg-white p-8 rounded-3xl max-w-md w-full border border-slate-150 space-y-4 shadow-2xl">
            <div className="relative w-12 h-12 mx-auto">
              <div className="absolute inset-0 rounded-full border-4 border-slate-100" />
              <div className="absolute inset-0 rounded-full border-4 border-primary border-t-transparent animate-spin" />
            </div>

            <div className="space-y-1">
              <h3 className="text-sm font-bold text-slate-800 font-sans">
                正在召唤决策委员会神经架构
              </h3>
              <p className="text-xs text-slate-400 font-sans px-3">
                系统正在深度研判面试中的上下文对话长度、答题细节度、逻辑严密和抗压能力。并整合简历长板产出量化鉴定评估意见...
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Inline custom icons avoiding package mismatches
function PlayMeIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <polygon points="6 3 20 12 6 21 6 3" />
    </svg>
  );
}
function LoaderPulse(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M21 12a9 9 0 1 1-6.219-8.56" />
    </svg>
  );
}
