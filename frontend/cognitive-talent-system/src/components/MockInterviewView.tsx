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

/**
 * ASR 英文技术术语后处理校正
 * 将 ASR 返回文本中常见的误识英文词纠正为正确写法
 */
const ASR_CORRECTIONS: [RegExp, string][] = [
  // ===== Redis (常见误识: reddies, ladies, radish, ready's) =====
  [/\breddis\b/gi, "Redis"],
  [/\bredies\b/gi, "Redis"],
  [/\brediesi\b/gi, "Redis"],
  [/\bredis\b/gi, "Redis"],
  [/\bladys?\b/gi, "Redis"],
  [/\blad[ei]es\b/gi, "Redis"],
  [/\blady['’]?s\b/gi, "Redis"],
  [/\breddish\b/gi, "Redis"],
  [/\bradish\b/gi, "Redis"],
  [/\bre[- ]?dis\b/gi, "Redis"],
  // ===== Spring Boot (常见误识: sprboard, springboard, spread boot) =====
  [/\bspring boot\b/gi, "Spring Boot"],
  [/\bspringboot\b/gi, "Spring Boot"],
  [/\bsprboard\b/gi, "Spring Boot"],
  [/\bspread[ -]?boot\b/gi, "Spring Boot"],
  [/\bspringboard\b/gi, "Spring Boot"],
  [/\bsprint[ -]?boot\b/gi, "Spring Boot"],
  [/\bspringport\b/gi, "Spring Boot"],
  [/\bsprin[gk]?boot\b/gi, "Spring Boot"],
  // ===== Spring Cloud =====
  [/\bspringcloud\b/gi, "Spring Cloud"],
  [/\bspring[ -]?cloud\b/gi, "Spring Cloud"],
  // ===== MyBatis =====
  [/\bmy batis\b/gi, "MyBatis"],
  [/\bmybatis\b/gi, "MyBatis"],
  [/\bmy batis plus\b/gi, "MyBatis Plus"],
  [/\bmabatis\b/gi, "MyBatis"],
  // ===== Docker (常见误识: doctor, dicker) =====
  [/\bdocker\b/gi, "Docker"],
  [/\bdoctor\b/gi, "Docker"],
  // ===== Kubernetes =====
  [/\bkubernetes\b/gi, "Kubernetes"],
  [/\bk8s\b/gi, "K8s"],
  [/\bkubornetis\b/gi, "Kubernetes"],
  [/\bkubenetes\b/gi, "Kubernetes"],
  // ===== Elasticsearch =====
  [/\belasticsearch\b/gi, "Elasticsearch"],
  [/\belastic[ -]?search\b/gi, "Elasticsearch"],
  [/\blasticsearch\b/gi, "Elasticsearch"],
  // ===== 数据库 =====
  [/\bmysql\b/gi, "MySQL"],
  [/\bmy[ -]?sql\b/gi, "MySQL"],
  [/\bmy circle\b/gi, "MySQL"],
  [/\bmy sequel\b/gi, "MySQL"],
  [/\bpostgresql\b/gi, "PostgreSQL"],
  [/\bpostgres\b/gi, "PostgreSQL"],
  [/\bmongodb\b/gi, "MongoDB"],
  [/\bmongo[ -]?db\b/gi, "MongoDB"],
  // ===== 消息队列 =====
  [/\bkafka\b/gi, "Kafka"],
  [/\bkaffka\b/gi, "Kafka"],
  [/\brabbitmq\b/gi, "RabbitMQ"],
  [/\brabi[td]mq\b/gi, "RabbitMQ"],
  [/\brocketmq\b/gi, "RocketMQ"],
  [/\brock iq\b/gi, "RocketMQ"],
  // ===== 网关与代理 =====
  [/\bnginx\b/gi, "Nginx"],
  [/\bengine x\b/gi, "Nginx"],
  [/\benginx\b/gi, "Nginx"],
  // ===== 编程语言 =====
  [/\bjavascript\b/gi, "JavaScript"],
  [/\bjava[ -]?script\b/gi, "JavaScript"],
  [/\btypescript\b/gi, "TypeScript"],
  [/\btype[ -]?script\b/gi, "TypeScript"],
  [/\bpython\b/gi, "Python"],
  [/\bpy[ -]?thon\b/gi, "Python"],
  [/\bkotlin\b/gi, "Kotlin"],
  [/\bcotlin\b/gi, "Kotlin"],
  [/\bgolang\b/gi, "Go"],
  [/\bgo[ -]?lang\b/gi, "Go"],
  // ===== 框架与库 =====
  [/\bmicroservice\b/gi, "Microservice"],
  [/\bmicroservices\b/gi, "Microservices"],
  [/\bmicro[ -]?service\b/gi, "Microservice"],
  [/\bmicro[ -]?services\b/gi, "Microservices"],
  [/\bpytorch\b/gi, "PyTorch"],
  [/\bpio[ -]?torch\b/gi, "PyTorch"],
  [/\btensorflow\b/gi, "TensorFlow"],
  [/\btensor[ -]?flow\b/gi, "TensorFlow"],
  // ===== 平台与工具 =====
  [/\bgithub\b/gi, "GitHub"],
  [/\bgithup\b/gi, "GitHub"],
  [/\bgit 哈b\b/gi, "GitHub"],
  [/\bgitlab\b/gi, "GitLab"],
  [/\bgit[ -]?lab\b/gi, "GitLab"],
  [/\bgit\b/gi, "Git"],
  // ===== 缩写与协议 =====
  [/\bjwt\b/gi, "JWT"],
  [/\bj[ -]?w[ -]?t\b/gi, "JWT"],
  [/\bjvm\b/gi, "JVM"],
  [/\bj[ -]?v[ -]?m\b/gi, "JVM"],
  [/\borm\b/gi, "ORM"],
  [/\bo[ -]?r[ -]?m\b/gi, "ORM"],
  [/\baop\b/gi, "AOP"],
  [/\ba[ -]?o[ -]?p\b/gi, "AOP"],
  [/\bioc\b/gi, "IOC"],
  [/\bi[ -]?o[ -]?c\b/gi, "IOC"],
  [/\bdd d\b/gi, "DDD"],
  [/\bddd\b/gi, "DDD"],
  [/\bdd[ -]?d\b/gi, "DDD"],
  [/\brpc\b/gi, "RPC"],
  [/\br[ -]?p[ -]?c\b/gi, "RPC"],
  [/\bapi\b/gi, "API"],
  [/\ba[ -]?p[ -]?i\b/gi, "API"],
  [/\bsdk\b/gi, "SDK"],
  [/\bs[ -]?d[ -]?k\b/gi, "SDK"],
  [/\bll m\b/gi, "LLM"],
  [/\bllm\b/gi, "LLM"],
  [/\bl[ -]?l[ -]?m\b/gi, "LLM"],
];

/** 对 ASR 识别文本进行后处理校正 */
function correctAsrText(text: string): string {
  let corrected = text;
  for (const [pattern, replacement] of ASR_CORRECTIONS) {
    corrected = corrected.replace(pattern, replacement);
  }
  return corrected;
}


const DIRECTIONS = [
  "AI Agent开发", "算法与数据结构", "阿里后端", "字节后端",
  "前端工程", "Java后端开发", "腾讯后端", "Python后端开发",
  "系统设计", "测试开发", "自定义JD"
];
const LEVELS = ["校招", "中级", "高级"];
const LEVEL_YEAR_MAP: Record<string, string> = { "校招": "0-1年", "中级": "1-3年", "高级": "3年+" };
const STAGE_RATIOS: Record<string, number> = { selfIntro: 0.15, techExam: 0.40, projectDeep: 0.30, qaRound: 0.15 };

/** 阶段英文名 -> 中文名映射 */
const STAGE_LABELS_CN: Record<string, string> = {
  selfIntro: "自我介绍",
  techExam: "技术考察",
  projectDeep: "项目挖深",
  qaRound: "反问环节"
};

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

  // 阶段流转跟踪
  const [currentStage, setCurrentStage] = useState<string>("");

  // 续面功能
  const [activeSessions, setActiveSessions] = useState<InterviewSession[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [resuming, setResuming] = useState(false);

  // 方向推荐
  const [recommendations, setRecommendations] = useState<DirectionRecommendation[]>([]);
  const [recommending, setRecommending] = useState(false);

  // 面试官流式回复（逐字输出效果）
  const [streamingReply, setStreamingReply] = useState<string>("");
  const streamingTimerRef = useRef<number | null>(null);

  // 题目进度跟踪
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState<number>(0);
  const [totalQuestions, setTotalQuestions] = useState<number>(0);

  // 回答超时倒计时（秒）
  const [timeoutRemaining, setTimeoutRemaining] = useState<number>(0);
  const timeoutTimerRef = useRef<number | null>(null);

  // 面试暂停
  const [isPaused, setIsPaused] = useState(false);
  const pausedTimeRef = useRef<number>(0);
  const pauseResumeLoading = useRef(false);

  // 语音
  const [isRecording, setIsRecording] = useState(false);
  const [interimText, setInterimText] = useState("");
  const recognitionRef = useRef<any>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);

  // WebSocket 实时 ASR
  const wsRef = useRef<WebSocket | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const sourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const micStreamRef = useRef<MediaStream | null>(null);
  const pcmBufferRef = useRef<Int16Array[]>([]);
  const pcmFlushTimerRef = useRef<number | null>(null);
  const wsConnectedRef = useRef(false);
  // 累积的 ASR 最终文本（避免闭包陈旧问题）
  const asrAccumulatedRef = useRef("");
  const asrBaseTextRef = useRef("");

  // 麦克风设备选择
  const [audioDevices, setAudioDevices] = useState<MediaDeviceInfo[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");

  // 枚举可用麦克风设备
  useEffect(() => {
    async function enumerateMicDevices() {
      try {
        // 先请求一次权限，确保能获取到设备列表
        await navigator.mediaDevices.getUserMedia({ audio: true }).then(s => s.getTracks().forEach(t => t.stop()));
        const devices = await navigator.mediaDevices.enumerateDevices();
        const mics = devices.filter(d => d.kind === "audioinput");
        setAudioDevices(mics);
        if (mics.length > 0 && !selectedDeviceId) {
          setSelectedDeviceId(mics[0].deviceId);
        }
      } catch { /* 权限被拒时不阻塞 */ }
    }
    enumerateMicDevices();
  }, []);

  // 人才库候选人
  const [talentCandidates, setTalentCandidates] = useState<Candidate[]>([]);

  // 获取候选人的活跃会话（用于续面）
  const fetchActiveSessions = useCallback(async (candId: string) => {
    if (!candId) return;
    setLoadingSessions(true);
    try {
      const res = await fetch(`${API_BASE}/api/mock-interview/candidates/${candId}/active-sessions`);
      const sessions: InterviewSession[] = await res.json();
      setActiveSessions(Array.isArray(sessions) ? sessions : []);
    } catch {
      setActiveSessions([]);
    } finally {
      setLoadingSessions(false);
    }
  }, []);

  // 切换候选人时拉取活跃会话
  useEffect(() => {
    if (activeCandidateId) {
      fetchActiveSessions(activeCandidateId);
    }
  }, [activeCandidateId, fetchActiveSessions]);

  // 恢复面试会话
  const handleResumeSession = async (sid: string) => {
    setResuming(true);
    try {
      const res = await fetch(`${API_BASE}/api/mock-interview/sessions/${sid}/resume`, { method: "POST" });
      if (!res.ok) throw new Error("续面失败");
      const data = await res.json();

      setSessionId(data.sessionId);
      setInterviewStarted(true);

      // 恢复面试模式（语音/文字）
      if (data.mode) {
        setInterviewMode(data.mode as "text" | "voice");
      }

      const initialMessages = (data.messages && Array.isArray(data.messages))
        ? data.messages.map((m: any) => ({
          id: m.id, sender: m.sender as "interviewer" | "candidate", text: m.text,
          timestamp: new Date(m.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
        }))
        : [];
      setMessages(initialMessages);
      setCurrentStage(data.currentStage || "");

      setTimeElapsed(0);
      const interval = setInterval(() => setTimeElapsed(p => p + 1), 1000);
      setTimerInterval(interval);
    } catch (err) {
      console.error("续面失败:", err);
      alert("恢复面试失败，请重试");
    } finally {
      setResuming(false);
    }
  };

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
    // talentCandidates 来自后端人才库（权威来源），优先级最高
    // 以 name+role 为唯一标识，忽略可能不一致的 matchScore
    const talentKeys = new Set(talentCandidates.map(c => `${c.name}|${c.role}`));
    // 只保留 candidates 中不在 talentCandidates 里的条目（纯虚拟候选人）
    // 同时在最终结果中按 name+role 去重，避免同一个人出现两次
    const merged = [...talentCandidates, ...candidates.filter(c => !talentKeys.has(`${c.name}|${c.role}`))];
    const seen = new Set<string>();
    return merged.filter(c => {
      const key = `${c.name}|${c.role}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [candidates, talentCandidates]);

  useEffect(() => {
    if (preSelectedCandidate) setActiveCandidateId(preSelectedCandidate.id);
    else if (candidates.length > 0 && !activeCandidateId) setActiveCandidateId(candidates[0].id);
  }, [preSelectedCandidate, candidates]);

  useEffect(() => {
    return () => {
      if (timerInterval) clearInterval(timerInterval);
      if (streamingTimerRef.current !== null) {
        clearInterval(streamingTimerRef.current);
        streamingTimerRef.current = null;
      }
      if (timeoutTimerRef.current !== null) {
        clearInterval(timeoutTimerRef.current);
        timeoutTimerRef.current = null;
      }
    };
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
      setCurrentStage(startData.currentStage || "selfIntro");
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

  /** 播放 TTS 语音回复 */
  const playTTSAudio = useCallback((audioBase64: string) => {
    try {
      // 输出格式为 WAV（由后端 AudioService 的 CosyVoice 参数决定）
      const audioSrc = `data:audio/wav;base64,${audioBase64}`;
      // 复用 audio 元素，避免重复创建
      if (!audioRef.current) {
        audioRef.current = new Audio();
      }
      const audio = audioRef.current;
      audio.src = audioSrc;
      audio.play().catch(err => console.warn("TTS 自动播放被拦截（需要用户交互）:", err));
    } catch (err) {
      console.error("TTS 播放失败:", err);
    }
  }, []);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || thinking || !sessionId) return;
    // 提交时自动停止录音
    if (isRecording) {
      stopVoiceRecording();
    }
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

      // 面试官回复流式逐字输出（typewriter 效果）
      const replyText = data.reply || "";
      const replyId = "inter_" + Date.now();
      const timestamp = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

      let charIdx = 0;
      const typeSpeed = 30; // ms/字符，约 33 字符/秒
      const timerId = window.setInterval(() => {
        charIdx++;
        const partialText = replyText.substring(0, charIdx);
        setStreamingReply(partialText);
        if (charIdx >= replyText.length) {
          clearInterval(timerId);
          streamingTimerRef.current = null;
          // 流式输出结束后，将完整消息加入 messages 数组
          setMessages(prev => [...prev, { id: replyId, sender: "interviewer" as const, text: replyText, timestamp }]);
          setStreamingReply("");
          // 流式结束后启动回答超时计时器（180s）
          startTimeoutTimer();
        }
      }, typeSpeed);
      streamingTimerRef.current = timerId;

      // 如果后端返回了语音音频，自动播放（语音面试模式默认播放，文字面试也可播放）
      if (data.audio) {
        playTTSAudio(data.audio);
      }

      if (data.currentStage) {
        setCurrentStage(data.currentStage);
      }
      if (data.currentQuestionIndex !== undefined) {
        setCurrentQuestionIndex(data.currentQuestionIndex);
      }
      if (data.totalQuestions !== undefined) {
        setTotalQuestions(data.totalQuestions);
      }

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
      // 评估完成后刷新活跃会话列表
      if (activeCandidateId) {
        fetchActiveSessions(activeCandidateId);
      }
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
    stopTimeoutTimer();
    if (timerInterval) { clearInterval(timerInterval); setTimerInterval(null); }
    handleEvaluate(sessionId);
  };

  // 暂停面试
  const handlePause = useCallback(async () => {
    if (!sessionId || pauseResumeLoading.current) return;
    pauseResumeLoading.current = true;
    try {
      await fetch(`${API_BASE}/api/mock-interview/sessions/${sessionId}/pause`, { method: "POST" });
      if (timerInterval) {
        clearInterval(timerInterval);
        setTimerInterval(null);
      }
      pausedTimeRef.current = timeElapsed;
      stopTimeoutTimer();
      setIsPaused(true);
    } catch {
      console.error("暂停面试失败");
    } finally { pauseResumeLoading.current = false; }
  }, [sessionId, timerInterval, timeElapsed]);

  // 继续面试
  const handleResume = useCallback(async () => {
    if (!sessionId || pauseResumeLoading.current) return;
    pauseResumeLoading.current = true;
    try {
      await fetch(`${API_BASE}/api/mock-interview/sessions/${sessionId}/unpause`, { method: "POST" });
      setIsPaused(false);
      // 重新启动计时器
      const interval = setInterval(() => setTimeElapsed(p => p + 1), 1000);
      setTimerInterval(interval);
    } catch {
      console.error("恢复面试失败");
    } finally { pauseResumeLoading.current = false; }
  }, [sessionId]);

  const formatTimer = (secs: number) => {
    const m = Math.floor(secs / 60), s = secs % 60;
    return `${m < 10 ? "0" : ""}${m}:${s < 10 ? "0" : ""}${s}`;
  };

  // 回答超时管理 - handleTimeoutSubmit 必须定义在 startTimeoutTimer 之前（函数顺序依赖）
  const handleTimeoutSubmit = useCallback(async () => {
    if (!sessionId || thinking) return;
    setThinking(true);
    stopTimeoutTimer();
    try {
      const res = await fetch(`${API_BASE}/api/mock-interview/sessions/${sessionId}/chat`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answer: "（回答超时）" })
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: ChatResponse = await res.json();
      const replyText = data.reply || "";
      const replyId = "inter_tout_" + Date.now();
      const timestamp = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

      // 流式输出
      let charIdx = 0;
      const typeSpeed = 30;
      const timerId = window.setInterval(() => {
        charIdx++;
        setStreamingReply(replyText.substring(0, charIdx));
        if (charIdx >= replyText.length) {
          clearInterval(timerId);
          setMessages(prev => [...prev, {
            id: replyId, sender: "interviewer" as const, text: replyText, timestamp
          }]);
          setStreamingReply("");
          // 超时回复后启动新的超时计时器
          startTimeoutTimer();
        }
      }, typeSpeed);

      if (data.currentStage) setCurrentStage(data.currentStage);
      if (data.currentQuestionIndex !== undefined) setCurrentQuestionIndex(data.currentQuestionIndex);
      if (data.totalQuestions !== undefined) setTotalQuestions(data.totalQuestions);

      if (data.status === "COMPLETED") {
        if (timerInterval) clearInterval(timerInterval);
        handleEvaluate(sessionId);
      }
    } catch {
      // 超时提交失败时静默处理
    } finally { setThinking(false); }
  }, [sessionId, thinking, timerInterval]);

  const startTimeoutTimer = useCallback(() => {
    stopTimeoutTimer();
    setTimeoutRemaining(180);
    timeoutTimerRef.current = window.setInterval(() => {
      setTimeoutRemaining(prev => {
        if (prev <= 1) {
          stopTimeoutTimer();
          // 超时自动提交
          handleTimeoutSubmit();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  const stopTimeoutTimer = useCallback(() => {
    if (timeoutTimerRef.current !== null) {
      clearInterval(timeoutTimerRef.current);
      timeoutTimerRef.current = null;
    }
    setTimeoutRemaining(0);
  }, []);

  // 语音 - 始终使用 WebSocket 实时 ASR
  const toggleVoiceRecording = () => {
    if (isRecording) { stopVoiceRecording(); return; }
    // 保存当前输入框文本作为基础文本（确保键盘输入不会被覆盖）
    asrBaseTextRef.current = inputText;
    asrAccumulatedRef.current = "";

    // 直接使用 WebSocket 实时 ASR（绕过浏览器原生 SpeechRecognition，
    // 因为原生 API 依赖 Google 服务，且不可控制格式和模型）
    startMediaRecorderFallback();
  };

  /** WebSocket 实时 ASR - 使用 AudioContext 直接获取 PCM 音频流 */
  const startWebSocketRecording = async () => {
    try {
      // 请求麦克风
      const audioConstraints: MediaStreamConstraints["audio"] = selectedDeviceId
        ? { deviceId: { exact: selectedDeviceId } }
        : true;
      const stream = await navigator.mediaDevices.getUserMedia({ audio: audioConstraints });
      micStreamRef.current = stream;

      // 打开 WebSocket 连接
      const ws = new WebSocket(`ws://localhost:8082/ws/asr`);
      wsRef.current = ws;
      wsConnectedRef.current = false;

      ws.onopen = () => {
        wsConnectedRef.current = true;
        console.log("[ASR WS] 连接已建立");
        setInterimText("🎤 录音中...");

        // WebSocket 连接建立后启动 AudioContext
        try {
          // 尝试 16kHz，部分浏览器可能不支持，回退到默认采样率
          let audioCtx: AudioContext;
          try {
            audioCtx = new AudioContext({ sampleRate: 16000 });
          } catch {
            console.warn("[ASR WS] 16kHz AudioContext 不受支持，使用默认采样率");
            audioCtx = new AudioContext();
          }
          audioCtxRef.current = audioCtx;

          const source = audioCtx.createMediaStreamSource(stream);
          sourceNodeRef.current = source;

          // 使用 ScriptProcessorNode 获取原始 PCM
          const processor = audioCtx.createScriptProcessor(4096, 1, 1);
          processorRef.current = processor;

          processor.onaudioprocess = (event) => {
            const inputData = event.inputBuffer.getChannelData(0);
            // Float32 -> Int16 PCM
            const pcm16 = new Int16Array(inputData.length);
            for (let i = 0; i < inputData.length; i++) {
              const s = Math.max(-1, Math.min(1, inputData[i]));
              pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
            }
            pcmBufferRef.current.push(pcm16);
          };

          source.connect(processor);
          // 使用 GainNode(0) 保持音频图活跃，但不播放麦克风声音到扬声器
          const silentGain = audioCtx.createGain();
          silentGain.gain.value = 0;
          processor.connect(silentGain);
          silentGain.connect(audioCtx.destination);

          // 每 200ms 刷新一次 PCM 缓冲区到 WebSocket（较短的间隔让 ASR 更快返回中间结果）
          const flushTimer = window.setInterval(() => {
            if (pcmBufferRef.current.length === 0 || !wsConnectedRef.current) return;
            const chunks = pcmBufferRef.current.splice(0);
            const totalLen = chunks.reduce((sum, arr) => sum + arr.length, 0);
            const merged = new Int16Array(totalLen);
            let offset = 0;
            for (const arr of chunks) {
              merged.set(arr, offset);
              offset += arr.length;
            }
            // 发送 PCM 二进制数据
            ws.send(merged.buffer);
          }, 200);
          pcmFlushTimerRef.current = flushTimer;

          setIsRecording(true);
        } catch (ctxErr) {
          console.error("[ASR WS] AudioContext 初始化失败:", ctxErr);
          setInterimText("⚠️ 音频初始化失败");
          ws.close();
          stream.getTracks().forEach(t => t.stop());
        }
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          console.log("[ASR WS] 收到消息:", data);
          if (data.type === "transcript" && data.text) {
            const correctedText = correctAsrText(data.text);
            if (data.isFinal) {
              // 最终结果：累积到 accumulatedRef 并更新输入框
              asrAccumulatedRef.current += correctedText;
              setInputText(asrBaseTextRef.current + asrAccumulatedRef.current);
              setInterimText("🎤 录音中...");

              // 语音指令检测：结束面试
              const lowerText = correctedText.toLowerCase();
              if (/(结束面试|生成报告|结束吧|到此为止|交卷|可以了|评估报告)/.test(lowerText)) {
                stopTimeoutTimer();
                setTimeout(() => {
                  if (sessionId) {
                    handleEndInterview();
                  }
                }, 500);
                return;
              }
            } else {
              // 中间结果：实时显示在输入框中（累积文本 + 当前中间结果）
              setInputText(asrBaseTextRef.current + asrAccumulatedRef.current + correctedText);
              setInterimText(correctedText);
            }
          } else if (data.type === "error") {
            console.warn("[ASR WS] 错误:", data.message);
            setInterimText("⚠️ " + (data.message || "识别错误"));
          } else if (data.type === "complete") {
            console.log("[ASR WS] 识别完成");
            setInterimText("🎤 录音中...");
          } else if (data.type === "ready") {
            console.log("[ASR WS] 就绪:", data.message);
          }
        } catch (e) {
          console.warn("[ASR WS] 消息解析失败:", event.data);
        }
      };

      ws.onerror = (err) => {
        console.error("[ASR WS] 连接错误:", err);
        setInterimText("⚠️ WebSocket 连接失败");
        setIsRecording(false);
      };

      ws.onclose = (event) => {
        console.log("[ASR WS] 连接关闭:", event.code, event.reason);
        wsConnectedRef.current = false;
        if (pcmFlushTimerRef.current !== null) {
          clearInterval(pcmFlushTimerRef.current);
          pcmFlushTimerRef.current = null;
        }
        setIsRecording(false);
      };
    } catch (err) {
      console.error("[ASR WS] 启动失败:", err);
      setInterimText("⚠️ 麦克风访问被拒绝");
      alert("无法访问麦克风，请检查浏览器权限设置。");
    }
  };

  /** MediaRecorder 兜底录制（作为 WebSocket 的最终后备） */
  const startMediaRecorderFallback = async () => {
    // 先尝试 WebSocket 实时方案
    await startWebSocketRecording();
  };

  /**
   * 停止录音
   * 停止浏览器原生 SpeechRecognition + WebSocket ASR + AudioContext
   */
  const stopVoiceRecording = () => {
    // 停止浏览器原生 SpeechRecognition
    if (recognitionRef.current) {
      recognitionRef.current.stop();
      recognitionRef.current = null;
    }
    // 停止 MediaRecorder 兜底
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
      mediaRecorderRef.current = null;
    }
    // 停止 AudioContext / ScriptProcessorNode
    if (pcmFlushTimerRef.current !== null) {
      clearInterval(pcmFlushTimerRef.current);
      pcmFlushTimerRef.current = null;
    }
    if (processorRef.current) {
      processorRef.current.disconnect();
      processorRef.current = null;
    }
    if (sourceNodeRef.current) {
      sourceNodeRef.current.disconnect();
      sourceNodeRef.current = null;
    }
    if (audioCtxRef.current) {
      audioCtxRef.current.close();
      audioCtxRef.current = null;
    }
    // 停止麦克风流
    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach(t => t.stop());
      micStreamRef.current = null;
    }
    // 关闭 WebSocket（发送 EOS 信号）
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send("EOS");
      wsRef.current.close();
      wsRef.current = null;
    }
    wsConnectedRef.current = false;
    pcmBufferRef.current = [];
    // 停止录音时，确保累积的 ASR 最终文本写入输入框
    if (asrAccumulatedRef.current) {
      setInputText(asrBaseTextRef.current + asrAccumulatedRef.current);
    }
    asrAccumulatedRef.current = "";
    setIsRecording(false);
    setInterimText("");
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

          {/* 续面提示：检测到活跃面试会话 */}
          {activeSessions.length > 0 && !loadingSessions && (
            <div className="bg-amber-50/80 border border-amber-200 rounded-xl p-4 space-y-3">
              <div className="flex items-center gap-2">
                <RefreshCw className="w-4 h-4 text-amber-600" />
                <span className="text-sm font-bold text-amber-800">检测到未完成的面试</span>
              </div>
              <p className="text-xs text-amber-700">
                该候选人共有 {activeSessions.length} 个进行中的面试会话，可以继续上次的面试。
              </p>
              <div className="flex flex-wrap gap-2">
                {activeSessions.map(s => (
                  <button key={s.sessionId} onClick={() => handleResumeSession(s.sessionId)} disabled={resuming}
                    className="text-xs font-semibold bg-white text-amber-700 border border-amber-300 hover:bg-amber-100 px-4 py-2 rounded-xl transition cursor-pointer disabled:opacity-40 flex items-center gap-1.5">
                    <RefreshCw className="w-3 h-3" />
                    续面: {s.direction} ({s.level}) - 第{s.currentRound}轮
                  </button>
                ))}
              </div>
            </div>
          )}
          {loadingSessions && (
            <div className="text-xs text-slate-400 italic flex items-center gap-1.5">
              <Loader2 className="w-3 h-3 animate-spin" /> 检查未完成面试...
            </div>
          )}

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

            {/* 麦克风设备选择 - 仅语音模式显示 */}
            {interviewMode === "voice" && audioDevices.length > 1 && (
              <div className="flex items-center gap-3 bg-slate-50/70 border border-slate-200 rounded-xl px-4 py-2.5">
                <label className="text-[11px] font-semibold text-slate-500 shrink-0 flex items-center gap-1.5">
                  <Mic className="w-3.5 h-3.5" />
                  麦克风
                </label>
                <select value={selectedDeviceId}
                  onChange={e => setSelectedDeviceId(e.target.value)}
                  className="flex-1 text-xs py-2 px-3 bg-white border border-slate-200 rounded-lg outline-none cursor-pointer">
                  {audioDevices.map(d => (
                    <option key={d.deviceId} value={d.deviceId}>
                      {d.label || `麦克风 ${d.deviceId.slice(0, 8)}...`}
                    </option>
                  ))}
                </select>
              </div>
            )}
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
            <span className="text-primary font-bold">| {STAGE_LABELS_CN[currentStage] || "进行中"}</span>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div className="lg:col-span-8 bg-white/70 backdrop-blur-md rounded-2xl border border-slate-200 shadow-lg flex flex-col h-[580px] overflow-hidden">
              <div className="bg-slate-50 p-4 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-3">
                  <div className="w-3.5 h-3.5 rounded-full bg-emerald-500 animate-pulse border-2 border-white" />
                  <span className="text-xs font-bold text-slate-700">AI 面试官 · {interviewDirection}</span>
                </div>
                <div className="flex items-center gap-2">
                  {!isPaused ? (
                    <button onClick={handlePause}
                      className="text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-100 py-2 px-3 rounded-lg flex items-center gap-1 transition cursor-pointer">
                      ⏸ 暂停
                    </button>
                  ) : (
                    <button onClick={handleResume}
                      className="text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 py-2 px-3 rounded-lg flex items-center gap-1 transition cursor-pointer">
                      ▶ 继续
                    </button>
                  )}
                  <button onClick={handleEndInterview}
                    className="text-[10px] font-extrabold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 py-2 px-4 rounded-lg flex items-center gap-1 transition cursor-pointer shadow-sm">
                    <Trophy className="w-3.5 h-3.5" /> 结束面试并生成评估
                  </button>
                </div>
              </div>

              <div className="flex-1 p-5 overflow-y-auto space-y-4 bg-slate-50/50 relative">
                {/* 暂停中的遮罩层 */}
                {isPaused && (
                  <div className="absolute inset-0 bg-white/80 backdrop-blur-sm z-10 flex flex-col items-center justify-center" style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}>
                    <div className="bg-amber-50 border-2 border-amber-200 rounded-2xl p-6 text-center shadow-lg">
                      <span className="text-2xl">⏸</span>
                      <p className="text-sm font-bold text-amber-800 mt-3">面试已暂停</p>
                      <p className="text-[11px] text-amber-600 mt-1">点击「继续」按钮恢复面试</p>
                    </div>
                  </div>
                )}
                {messages.map(m => (
                  <div key={m.id} className={`flex items-start gap-3.5 ${m.sender === "candidate" ? "flex-row-reverse" : ""}`}>
                    <div className={`text-xs py-2.5 px-4 rounded-2xl leading-relaxed max-w-[75%] shadow-sm
                      ${m.sender === "candidate" ? "bg-primary text-white rounded-tr-none" : "bg-white text-slate-700 border border-slate-150 rounded-tl-none"}`}>
                      {m.text}
                    </div>
                  </div>
                ))}
                {thinking && !streamingReply && (
                  <div className="flex items-start gap-3.5">
                    <div className="p-3.5 bg-white border border-slate-100 rounded-2xl flex items-center gap-2 text-slate-400 text-xs">
                      <Loader2 className="w-4 h-4 animate-spin" /> 面试官正在思考...
                    </div>
                  </div>
                )}
                {/* 流式输出中的面试官回复 */}
                {streamingReply && (
                  <div className="flex items-start gap-3.5">
                    <div className="text-xs py-2.5 px-4 rounded-2xl leading-relaxed max-w-[75%] shadow-sm bg-white text-slate-700 border border-slate-150 rounded-tl-none">
                      {streamingReply}<span className="animate-pulse text-slate-400 font-bold">▊</span>
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
                  <input type="text" value={inputText}
                    onChange={e => { setInputText(e.target.value); stopTimeoutTimer(); }}
                    placeholder={isRecording ? "正在录音..." : isPaused ? "面试已暂停" : "请输入您的回答..."}
                    className="flex-1 text-xs py-3 px-4 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition disabled:opacity-50"
                    disabled={thinking || isPaused} />
                  <button type="submit" disabled={thinking || !inputText.trim() || isPaused}
                    className="w-10 h-10 bg-primary hover:bg-primary-container disabled:bg-slate-300 rounded-xl flex items-center justify-center text-white transition shrink-0 cursor-pointer">
                    <Send className="w-4.5 h-4.5" />
                  </button>
                </div>
                {isRecording && (
                  <div className="text-[10px] text-slate-400 italic mt-2">🎤 录音中，实时语音将显示在上方输入框中</div>
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
                {/* 题目进度 */}
                {totalQuestions > 0 && (
                  <div className="p-3 bg-white rounded-xl border border-slate-100 space-y-1 shadow-sm">
                    <span className="text-xs font-bold text-slate-700">题目进度</span>
                    <div className="flex items-center gap-2 mt-1.5">
                      <div className="flex-1 bg-slate-200 h-2 rounded-full overflow-hidden">
                        <div className="bg-primary h-full rounded-full transition-all duration-500"
                          style={{ width: `${Math.min(100, (currentQuestionIndex / Math.max(1, totalQuestions)) * 100)}%` }} />
                      </div>
                      <span className="text-[10px] font-bold text-primary shrink-0">{Math.min(currentQuestionIndex + 1, totalQuestions)}/{totalQuestions}</span>
                    </div>
                  </div>
                )}
                {/* 回答超时倒计时 */}
                {timeoutRemaining > 0 && (
                  <div className={`p-3 rounded-xl border space-y-1 shadow-sm ${
                    timeoutRemaining <= 30 ? "bg-red-50 border-red-200" : "bg-amber-50 border-amber-200"
                  }`}>
                    <div className="flex items-center justify-between text-[10px]">
                      <span className={`font-bold ${timeoutRemaining <= 30 ? "text-red-700" : "text-amber-700"}`}>
                        ⏱ 作答剩余时间
                      </span>
                      <span className={`font-black ${timeoutRemaining <= 30 ? "text-red-600" : "text-amber-600"}`}>
                        {formatTimer(timeoutRemaining)}
                      </span>
                    </div>
                    <div className="w-full bg-slate-200 h-1.5 rounded-full overflow-hidden mt-1">
                      <div className={`h-full rounded-full transition-all duration-1000 ${
                        timeoutRemaining <= 30 ? "bg-red-500" : "bg-amber-500"
                      }`} style={{ width: `${(timeoutRemaining / 180) * 100}%` }} />
                    </div>
                  </div>
                )}
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
