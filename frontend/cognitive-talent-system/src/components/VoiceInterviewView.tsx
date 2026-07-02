import { useState, useEffect, useCallback, useRef } from "react";
import { Mic, MicOff, Phone, PhoneOff, Loader2, AlertCircle, CheckCircle, Volume2, User, Search, Send } from "lucide-react";
import { voiceInterviewApi } from "../api/voiceInterview";
import { authFetch } from "../api";
import type { VoiceSessionMeta, VoiceEvaluationStatus, VoiceEvaluationDetail, QuestionEvalItem, Candidate, ResumeVO, ApiResult } from "../types";

const WEBSOCKET_URL = "ws://localhost:8082/ws/voice-interview";
const API_BASE = "http://localhost:8082";

// 11 大技能方向（与模拟面试对齐）
const SKILL_DIRECTIONS = [
  "AI Agent开发", "算法与数据结构", "阿里后端", "字节后端",
  "前端工程", "Java后端开发", "腾讯后端", "Python后端开发",
  "系统设计", "测试开发", "自定义方向"
];

interface VoiceInterviewViewProps {
  userId?: string;
  candidates?: Candidate[];
}

/** 对话消息 */
interface ChatMessage {
  role: "user" | "assistant" | "system";
  content: string;
  isFinal?: boolean;
  score?: number;
  scoreFeedback?: string;
}

export function VoiceInterviewView({ userId: propUserId, candidates: propCandidates = [] }: VoiceInterviewViewProps = {}) {
  const [sessions, setSessions] = useState<VoiceSessionMeta[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [userId, setUserId] = useState("");
  const [selectedCandidateId, setSelectedCandidateId] = useState<string>("");
  const [candidateSearch, setCandidateSearch] = useState("");
  const [candidateDropdownOpen, setCandidateDropdownOpen] = useState(false);
  const [talentCandidates, setTalentCandidates] = useState<Candidate[]>([]);
  const [skillId, setSkillId] = useState("Java后端开发");
  const [roleType, setRoleType] = useState("TECH");
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [isRecording, setIsRecording] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [interimText, setInterimText] = useState<string>("");
  const [voiceText, setVoiceText] = useState<string>(""); // 手动编辑文本框
  const [sessionFilter, setSessionFilter] = useState<"IN_PROGRESS" | "COMPLETED">("IN_PROGRESS");

  // === 每个会话独立的状态（隔离不同会话，防止串通） ===
  const [sessionMessages, setSessionMessages] = useState<Map<number, ChatMessage[]>>(new Map());
  const [sessionEvaluations, setSessionEvaluations] = useState<Map<number, VoiceEvaluationStatus>>(new Map());
  const [connectedSessionId, setConnectedSessionId] = useState<number | null>(null);

  // 派生状态：当前选中会话的消息和评估
  const messages = activeSessionId ? (sessionMessages.get(activeSessionId) ?? []) : [];
  const evaluation = activeSessionId ? (sessionEvaluations.get(activeSessionId) ?? null) : null;

  // 更新当前会话消息的辅助函数
  const setMessages = (updater: ChatMessage[] | ((prev: ChatMessage[]) => ChatMessage[])) => {
    if (activeSessionId == null) return;
    setSessionMessages(prev => {
      const next = new Map(prev);
      const current = next.get(activeSessionId) ?? [];
      const updated = typeof updater === "function" ? updater(current) : updater;
      next.set(activeSessionId, updated);
      return next;
    });
  };

  // 更新当前会话评估的辅助函数
  const setEvaluation = (val: VoiceEvaluationStatus | null) => {
    if (activeSessionId == null) return;
    setSessionEvaluations(prev => {
      const next = new Map(prev);
      if (val === null) {
        next.delete(activeSessionId);
      } else {
        next.set(activeSessionId, val);
      }
      return next;
    });
  };

  // ---- 音频录制相关 refs ----
  const micStreamRef = useRef<MediaStream | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const sourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const pcmBufferRef = useRef<Int16Array[]>([]);
  const pcmFlushTimerRef = useRef<number | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const wsConnectedRef = useRef<boolean>(false);
  const userJustSentRef = useRef<boolean>(false); // 防止 ASR 在用户刚发送后覆盖文本框
  // 音频播放队列，防止多个 TTS 音频同时播放
  const audioQueueRef = useRef<{ data: string; text: string }[]>([]);
  const isPlayingRef = useRef<boolean>(false);

  // ---- 清理音频资源 ----
  const cleanupAudio = useCallback(() => {
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
      audioCtxRef.current.close().catch(() => {});
      audioCtxRef.current = null;
    }
    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach(t => t.stop());
      micStreamRef.current = null;
    }
    pcmBufferRef.current = [];
    audioQueueRef.current = [];
    isPlayingRef.current = false;
  }, []);

  // ---- 播放 TTS 音频队列 ----
  const playNextAudio = useCallback(() => {
    if (audioQueueRef.current.length === 0) {
      isPlayingRef.current = false;
      return;
    }
    isPlayingRef.current = true;
    const item = audioQueueRef.current.shift()!;
    try {
      const binary = atob(item.data);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: "audio/wav" });
      const url = URL.createObjectURL(blob);
      const audio = new Audio(url);
      audio.onended = () => {
        URL.revokeObjectURL(url);
        playNextAudio();
      };
      audio.onerror = () => {
        URL.revokeObjectURL(url);
        playNextAudio();
      };
      audio.play().catch(() => {
        URL.revokeObjectURL(url);
        playNextAudio();
      });
    } catch {
      playNextAudio();
    }
  }, []);

  // ---- Base64 编码 ArrayBuffer ----
  const arrayBufferToBase64 = (buffer: ArrayBuffer): string => {
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  };

  // ---- 从后端人才库 API 拉取候选人 ----
  useEffect(() => {
    let cancelled = false;
    async function fetchTalentPool() {
      try {
        const res = await authFetch(`${API_BASE}/api/resume/talent-pool`);
        if (!res.ok) return;
        const json: ApiResult<ResumeVO[]> = await res.json();
        if (json.code === 200 && json.data && !cancelled) {
          const mapped: Candidate[] = json.data.map((c: ResumeVO) => ({
            id: "cand_" + c.id,
            name: c.candidateName || "未知",
            role: c.candidateRole || "",
            experienceYears: c.experienceYears || 0,
            education: c.education || "未知",
            status: c.talentStatus as any,
            avatar: "",
            matchScore: c.matchScore || 0,
            email: c.email || "",
            phone: c.phone || "",
            competencies: (c.competencies ?? { technical: 5, communication: 5, problemSolving: 5, teamFit: 5, drive: 5 }) as { technical: number; communication: number; problemSolving: number; teamFit: number; drive: number },
            strengths: c.strengths || [],
            weaknesses: c.weaknesses || [],
            highlights: c.highlights || [],
            aiSummary: c.aiSummary || "",
            analyzedAt: c.analyzedAt || "",
          }));
          setTalentCandidates(mapped);
        }
      } catch { /* ignore */ }
    }
    fetchTalentPool();
    return () => { cancelled = true; };
  }, []);

  // 合并 props 传入的候选人和 API 拉取的候选人，API 优先
  const allCandidates = (() => {
    const talentKeys = new Set(talentCandidates.map(c => `${c.name}|${c.role}`));
    const merged = [...talentCandidates, ...propCandidates.filter(c => !talentKeys.has(`${c.name}|${c.role}`))];
    const seen = new Set<string>();
    return merged.filter(c => {
      const key = `${c.name}|${c.role}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  })();

  const filteredCandidates = allCandidates.filter(c => {
    if (!candidateSearch) return true;
    const s = candidateSearch.toLowerCase();
    return c.name.toLowerCase().includes(s) || c.role.toLowerCase().includes(s);
  });

  const handleSelectCandidate = (candId: string) => {
    setSelectedCandidateId(candId);
    const candidate = allCandidates.find(c => c.id === candId);
    if (candidate) {
      const realId = candidate.id.replace(/^cand_/, "");
      setUserId(realId);
      if (candidate.role.toLowerCase().includes("hr") || candidate.role.includes("人事")) {
        setRoleType("HR");
      } else {
        setRoleType("TECH");
      }
    }
    setCandidateSearch("");
    setCandidateDropdownOpen(false);
  };

  // ---- 会话管理 ----
  const loadSessions = useCallback(async () => {
    try {
      const data = await voiceInterviewApi.getSessions(userId, undefined);
      const list = data?.data ?? data ?? [];
      setSessions(Array.isArray(list) ? list : []);
    } catch {
      setError("加载会话列表失败");
    }
  }, [userId]);

  useEffect(() => {
    if (userId) {
      loadSessions();
    }
  }, [userId]);

  const createSession = async () => {
    setLoading(true);
    setError(null);
    try {
      const candidateName = selectedCandidateId 
        ? allCandidates.find(c => c.id === selectedCandidateId)?.name 
        : (userId || "未命名候选人");
      const resumeId = selectedCandidateId
        ? Number(selectedCandidateId.replace("cand_", ""))
        : undefined;
      const res = await voiceInterviewApi.createSession({ userId, candidateName, skillId, roleType, resumeId });
      const data = res?.data ?? res;
      setSuccessMsg("语音面试会话创建成功");
      setActiveSessionId(data.sessionId ?? data.id);
      await loadSessions();
    } catch {
      setError("创建会话失败");
    } finally {
      setLoading(false);
    }
  };

  // ---- WebSocket 连接 ----
  const connectWebSocket = (sessionId: number) => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    setConnectedSessionId(sessionId);
    setActiveSessionId(sessionId);
    setIsConnecting(true);
    // 初始化当前会话的消息（清空旧消息）
    setSessionMessages(prev => {
      const next = new Map(prev);
      next.set(sessionId, []);
      return next;
    });
    setInterimText("");

    const socket = new WebSocket(`${WEBSOCKET_URL}/${sessionId}?userId=${userId}`);
    wsRef.current = socket;
    wsConnectedRef.current = false;

    socket.onopen = () => {
      wsConnectedRef.current = true;
      setIsConnecting(false);
      setSuccessMsg("WebSocket 已连接，面试官即将开始提问...");
      setWs(socket);
    };

    socket.onmessage = (event) => {
      // 先尝试解析 JSON
      try {
        const msg = JSON.parse(event.data);

        // 辅助函数：往当前连接的会话写入消息
        const appendMessage = (role: ChatMessage["role"], content: string, extra?: Partial<ChatMessage>) => {
          setSessionMessages(prev => {
            const next = new Map(prev);
            const current = next.get(sessionId) ?? [];
            next.set(sessionId, [...current, { role, content, isFinal: true, ...extra }]);
            return next;
          });
        };

        const updateLastAssistant = (content: string, isFinal: boolean) => {
          setSessionMessages(prev => {
            const next = new Map(prev);
            const current = next.get(sessionId) ?? [];
            const last = current[current.length - 1];
            if (last && last.role === "assistant" && !last.isFinal) {
              next.set(sessionId, [...current.slice(0, -1), { ...last, content, isFinal }]);
            } else {
              next.set(sessionId, [...current, { role: "assistant", content, isFinal }]);
            }
            return next;
          });
        };

        const updateLastUserScore = (score: number, feedback: string) => {
          setSessionMessages(prev => {
            const next = new Map(prev);
            const current = next.get(sessionId) ?? [];
            if (current.length === 0) return prev;
            const lastUserIndex = [...current].reverse().findIndex(m => m.role === "user");
            if (lastUserIndex < 0) return prev;
            const actualIndex = current.length - 1 - lastUserIndex;
            const updated = [...current];
            updated[actualIndex] = { ...updated[actualIndex], score, scoreFeedback: feedback };
            next.set(sessionId, updated);
            return next;
          });
        };

        switch (msg.type) {
          case "control":
            if (msg.action === "welcome") {
              setSuccessMsg(msg.message || "连接成功");
            } else if (msg.action === "asr_ready") {
              setSuccessMsg("语音识别已就绪，可以开始说话");
            } else if (msg.action === "pause_warning") {
              setError(msg.message || "面试即将超时");
            } else if (msg.action === "session_paused") {
              setError("面试已暂停（超时）");
            }
            break;

          case "subtitle":
            // ASR 实时字幕：仅显示在输入框中，不自动加入聊天记录
            // 用户需手动编辑确认后点击"发送"按钮才会提交给 LLM
            if (msg.text) {
              if (msg.isFinal) {
                setInterimText("");
                if (!userJustSentRef.current) {
                  setVoiceText(msg.text); // 自动填充到编辑框，方便用户修改
                }
                userJustSentRef.current = false;
              } else {
                setInterimText(msg.text);
              }
            }
            break;

          case "text":
            // AI 文本回复
            if (msg.content) {
              updateLastAssistant(msg.content, msg.final);
            }
            break;

          case "audio":
            // TTS 音频（Base64 WAV）
            if (msg.data) {
              audioQueueRef.current.push({ data: msg.data, text: msg.text || "" });
              if (!isPlayingRef.current) {
                playNextAudio();
              }
            }
            break;

          case "audio_chunk":
            // 分块音频（暂不处理，等 audio_complete 后统一播放）
            break;

          case "error":
            setError(msg.message || "服务端错误");
            break;

          case "history":
            // 刷新页面后恢复历史消息（每条记录可能同时包含用户和AI文本）
            if (msg.messages && Array.isArray(msg.messages)) {
              const historyMsgs: ChatMessage[] = [];
              msg.messages.forEach((m: any) => {
                const userText = m.userRecognizedText ?? "";
                const aiText = m.aiGeneratedText ?? "";
                if (userText) {
                  historyMsgs.push({
                    role: "user",
                    content: userText,
                    isFinal: true,
                    score: m.score,
                    scoreFeedback: m.scoreFeedback
                  });
                }
                if (aiText) {
                  historyMsgs.push({ role: "assistant", content: aiText, isFinal: true });
                }
              });
              setSessionMessages(prev => {
                const next = new Map(prev);
                next.set(sessionId, historyMsgs);
                return next;
              });
              setSuccessMsg(`已恢复 ${historyMsgs.length} 条历史消息`);
            }
            break;

          case "score":
            // 实时评分推送，附加到最后一条用户消息
            updateLastUserScore(msg.score, msg.feedback);
            setSuccessMsg(`实时评分: ${msg.score}分`);
            break;

          default:
            console.log("[Voice WS] 未知消息类型:", msg.type);
        }
      } catch {
        // 非 JSON 消息（可能是二进制音频），忽略
      }
    };

    socket.onerror = () => {
      setIsConnecting(false);
      setError("WebSocket 连接错误");
    };

    socket.onclose = () => {
      wsConnectedRef.current = false;
      setWs(null);
      setIsRecording(false);
      cleanupAudio();
      setSuccessMsg("WebSocket 已断开");
      // 刷新会话列表，确保状态同步（如自动结束的会话变为 COMPLETED）
      loadSessions();
    };
  };

  const disconnectWebSocket = () => {
    cleanupAudio();
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    wsConnectedRef.current = false;
    setWs(null);
    setIsRecording(false);
    setInterimText("");
    setConnectedSessionId(null);
    // 刷新会话列表，确保状态同步
    loadSessions();
  };

  // ---- 录音控制 ----
  const startRecording = async () => {
    if (!wsRef.current || !wsConnectedRef.current) {
      setError("请先连接 WebSocket");
      return;
    }

    try {
      // 1. 请求麦克风权限
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true,
        }
      });
      micStreamRef.current = stream;

      // 2. 创建 AudioContext（16kHz 单声道）
      let audioCtx: AudioContext;
      try {
        audioCtx = new AudioContext({ sampleRate: 16000 });
      } catch {
        audioCtx = new AudioContext();
      }
      audioCtxRef.current = audioCtx;

      const source = audioCtx.createMediaStreamSource(stream);
      sourceNodeRef.current = source;

      // 3. 使用 ScriptProcessorNode 获取 PCM 数据
      const processor = audioCtx.createScriptProcessor(4096, 1, 1);
      processorRef.current = processor;

      processor.onaudioprocess = (event) => {
        const inputData = event.inputBuffer.getChannelData(0);
        // Float32 → Int16 PCM
        const pcm16 = new Int16Array(inputData.length);
        for (let i = 0; i < inputData.length; i++) {
          const s = Math.max(-1, Math.min(1, inputData[i]));
          pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        }
        pcmBufferRef.current.push(pcm16);
      };

      source.connect(processor);
      // GainNode(0) 防止回声
      const silentGain = audioCtx.createGain();
      silentGain.gain.value = 0;
      processor.connect(silentGain);
      silentGain.connect(audioCtx.destination);

      // 4. 每 200ms 将 PCM 缓冲区通过 WebSocket 发送
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
        // 发送 base64 编码的 PCM 音频
        const base64 = arrayBufferToBase64(merged.buffer);
        wsRef.current?.send(JSON.stringify({ type: "audio", data: base64 }));
      }, 200);
      pcmFlushTimerRef.current = flushTimer;

      setIsRecording(true);
      setSuccessMsg("🎤 录音中...");
    } catch (err: any) {
      console.error("录音启动失败:", err);
      if (err.name === "NotAllowedError") {
        setError("麦克风权限被拒绝，请在浏览器设置中允许麦克风访问");
      } else {
        setError("录音启动失败: " + (err.message || "未知错误"));
      }
    }
  };

  const stopRecording = () => {
    cleanupAudio();
    setIsRecording(false);
    setInterimText("");
    setSuccessMsg("录音已停止");
  };

  // ---- 手动提交文本（编辑 ASR 结果后发送） ----
  const sendManualText = () => {
    const text = voiceText.trim();
    if (!text || !wsRef.current || !wsConnectedRef.current) return;
    wsRef.current.send(JSON.stringify({
      type: "control",
      action: "submit",
      data: { text }
    }));
    userJustSentRef.current = true; // 标记刚发送，防止 ASR 覆盖
    // 手动提交后，将用户消息加入当前连接会话的聊天记录
    const sid = connectedSessionId;
    if (sid != null) {
      setSessionMessages(prev => {
        const next = new Map(prev);
        const current = next.get(sid) ?? [];
        next.set(sid, [...current, { role: "user", content: text, isFinal: true }]);
        return next;
      });
    }
    setVoiceText("");
    setInterimText("");
    setSuccessMsg("已发送");
  };

  // ---- 评估功能 ----
  const triggerEvaluation = async (sessionId: number) => {
    setLoading(true);
    try {
      const res = await voiceInterviewApi.triggerEvaluation(sessionId);
      const result = res?.data ?? res;
      // 写入该会话专属的评估结果
      setSessionEvaluations(prev => {
        const next = new Map(prev);
        next.set(sessionId, result);
        return next;
      });
      setSuccessMsg("评估已触发");

      // 如果评估未完成，启动轮询
      if (result?.evaluateStatus === "PENDING" || result?.evaluateStatus === "PROCESSING") {
        setSuccessMsg("评估正在生成中，请稍候...");
        const pollTimer = setInterval(async () => {
          try {
            const pollRes = await voiceInterviewApi.getEvaluation(sessionId);
            const pollResult = pollRes?.data ?? pollRes;
            setSessionEvaluations(prev => {
              const next = new Map(prev);
              next.set(sessionId, pollResult);
              return next;
            });
            if (pollResult?.evaluateStatus === "COMPLETED") {
              clearInterval(pollTimer);
              setSuccessMsg("评估完成");
              // 刷新会话列表，更新 evaluateStatus
              loadSessions();
            } else if (pollResult?.evaluateStatus === "FAILED") {
              clearInterval(pollTimer);
              setError(pollResult?.evaluateError || "评估失败");
              loadSessions();
            }
          } catch {
            clearInterval(pollTimer);
            setError("获取评估结果失败");
          }
        }, 2000);
        // 最多轮询 60 次（2 分钟）
        setTimeout(() => clearInterval(pollTimer), 120000);
      }
    } catch {
      setError("触发评估失败");
    } finally {
      setLoading(false);
    }
  };

  const pollEvaluation = async (sessionId: number) => {
    try {
      const res = await voiceInterviewApi.getEvaluation(sessionId);
      setSessionEvaluations(prev => {
        const next = new Map(prev);
        next.set(sessionId, res?.data ?? res);
        return next;
      });
    } catch {
      setError("获取评估结果失败");
    }
  };

  // ---- 组件卸载时清理 ----
  useEffect(() => {
    return () => {
      cleanupAudio();
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [cleanupAudio]);

  // ---- 自动清除提示 ----
  useEffect(() => {
    if (successMsg || error) {
      const timer = setTimeout(() => {
        if (successMsg) setSuccessMsg(null);
        if (error) setError(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [successMsg, error]);

  return (
    <div className="space-y-6">
      {/* 创建会话 */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6">
        <h3 className="text-sm font-extrabold text-slate-800 mb-4">创建语音面试会话</h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
          <div className="relative">
            <label className="block text-xs font-bold text-slate-600 mb-1.5">选择候选人</label>
            {selectedCandidateId ? (
              <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5">
                <User className="w-4 h-4 text-primary" />
                <span className="text-sm font-semibold text-slate-700 flex-1 truncate">
                  {allCandidates.find(c => c.id === selectedCandidateId)?.name ?? "候选人"}
                </span>
                <button
                  onClick={() => { setSelectedCandidateId(""); setUserId(""); }}
                  className="text-xs text-slate-400 hover:text-red-500 transition cursor-pointer"
                >
                  ✕
                </button>
              </div>
            ) : (
              <div className="relative">
                <input
                  value={candidateSearch}
                  onChange={(e) => { setCandidateSearch(e.target.value); setCandidateDropdownOpen(true); }}
                  onFocus={() => setCandidateDropdownOpen(true)}
                  onBlur={() => setTimeout(() => setCandidateDropdownOpen(false), 200)}
                  placeholder="搜索候选人姓名或岗位..."
                  className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 pl-9 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                />
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-3" />
                {candidateDropdownOpen && filteredCandidates.length > 0 && (
                  <div className="absolute z-10 top-full mt-1 w-full bg-white border border-slate-200 rounded-xl shadow-lg max-h-48 overflow-y-auto">
                    {filteredCandidates.slice(0, 20).map(c => (
                      <button
                        key={c.id}
                        onClick={() => handleSelectCandidate(c.id)}
                        className="w-full text-left px-4 py-2.5 hover:bg-primary/5 transition flex items-center gap-3 cursor-pointer"
                      >
                        <div className="w-7 h-7 bg-primary/10 rounded-full flex items-center justify-center shrink-0">
                          <User className="w-3.5 h-3.5 text-primary" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-slate-700 truncate">{c.name}</p>
                          <p className="text-xs text-slate-400 truncate">{c.role} · 匹配 {c.matchScore}%</p>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
                {candidateDropdownOpen && candidateSearch && filteredCandidates.length === 0 && (
                  <div className="absolute z-10 top-full mt-1 w-full bg-white border border-slate-200 rounded-xl shadow-lg p-3">
                    <p className="text-xs text-slate-400 text-center">未找到匹配候选人</p>
                  </div>
                )}
                {!candidateSearch && allCandidates.length > 0 && (
                  <div className="text-xs text-slate-400 mt-1">
                    共 {allCandidates.length} 位候选人，输入关键词搜索
                  </div>
                )}
                {!candidateSearch && allCandidates.length === 0 && (
                  <div className="text-xs text-slate-400 mt-1">
                    暂无候选人数据，请先上传简历
                  </div>
                )}
              </div>
            )}
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">用户 ID</label>
            <input
              value={selectedCandidateId ? `${allCandidates.find(c => c.id === selectedCandidateId)?.name ?? ""} (ID: ${userId})` : userId}
              onChange={(e) => { setUserId(e.target.value); setSelectedCandidateId(""); }}
              readOnly={!!selectedCandidateId}
              className={`w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 ${selectedCandidateId ? "bg-slate-50 text-slate-500" : ""}`}
              placeholder="先选择候选人，或手动输入 ID"
            />
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">技能方向</label>
            <select
              value={skillId}
              onChange={(e) => setSkillId(e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
            >
              {SKILL_DIRECTIONS.map(dir => (
                <option key={dir} value={dir}>{dir}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">角色类型</label>
            <select
              value={roleType}
              onChange={(e) => setRoleType(e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
            >
              <option value="TECH">技术面试</option>
              <option value="HR">HR 面试</option>
              <option value="COMPREHENSIVE">综合面试</option>
            </select>
          </div>
          <div className="flex items-end">
            <button
              onClick={createSession}
              disabled={loading || !userId}
              className="w-full flex items-center justify-center gap-2 text-sm font-bold text-white bg-primary hover:bg-primary-dark rounded-xl px-4 py-2.5 transition cursor-pointer disabled:opacity-50"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Phone className="w-4 h-4" />}
              创建会话
            </button>
          </div>
        </div>
      </div>

      {/* 消息提示 */}
      {error && (
        <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-xl px-4 py-3">
          <AlertCircle className="w-4 h-4 shrink-0" />{error}
        </div>
      )}
      {successMsg && (
        <div className="flex items-center gap-2 text-sm text-emerald-600 bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3">
          <CheckCircle className="w-4 h-4 shrink-0" />{successMsg}
        </div>
      )}

      {/* 会话列表 */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-extrabold text-slate-800">我的会话</h3>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setSessionFilter("IN_PROGRESS")}
              className={`text-xs font-semibold px-3 py-1.5 rounded-lg border transition cursor-pointer ${
                sessionFilter === "IN_PROGRESS"
                  ? "bg-amber-50 text-amber-700 border-amber-200"
                  : "bg-white text-slate-500 border-slate-200 hover:bg-slate-50"
              }`}
            >
              进行中
            </button>
            <button
              onClick={() => setSessionFilter("COMPLETED")}
              className={`text-xs font-semibold px-3 py-1.5 rounded-lg border transition cursor-pointer ${
                sessionFilter === "COMPLETED"
                  ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                  : "bg-white text-slate-500 border-slate-200 hover:bg-slate-50"
              }`}
            >
              已完成
            </button>
          </div>
        </div>
        {sessions.filter(s => s.status === sessionFilter).length === 0 ? (
          <p className="text-sm text-slate-400 py-8 text-center">
            {sessionFilter === "IN_PROGRESS" ? "暂无进行中的会话" : "暂无已完成的会话"}
          </p>
        ) : (
          <div className="space-y-3">
            {sessions.filter(s => s.status === sessionFilter).map((s) => (
              <div
                key={s.id ?? s.sessionId}
                className={`flex items-center justify-between p-4 rounded-xl border transition cursor-pointer ${
                  activeSessionId === (s.id ?? s.sessionId)
                    ? "border-primary bg-primary/5"
                    : "border-slate-100 hover:border-slate-200"
                }`}
                onClick={() => setActiveSessionId(s.id ?? s.sessionId)}
              >
                <div>
                  <p className="text-sm font-bold text-slate-800">
                    {s.candidateName || "未命名候选人"}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {s.skillId ?? "未知方向"} · {s.roleType ?? "未知角色"}
                  </p>
                  <p className="text-xs text-slate-400">
                    {s.createdAt ?? "—"} · 状态: {s.status ?? "—"}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {activeSessionId === (s.id ?? s.sessionId) && connectedSessionId !== (s.id ?? s.sessionId) && !isConnecting && (
                    <button
                      onClick={(e) => { e.stopPropagation(); connectWebSocket(s.id ?? s.sessionId); }}
                      className="flex items-center gap-1.5 text-xs font-bold text-emerald-600 bg-emerald-50 px-3 py-1.5 rounded-lg hover:bg-emerald-100 cursor-pointer"
                    >
                      <Phone className="w-3.5 h-3.5" />连接
                    </button>
                  )}
                  {isConnecting && connectedSessionId === (s.id ?? s.sessionId) && (
                    <span className="flex items-center gap-1.5 text-xs font-bold text-amber-600 bg-amber-50 px-3 py-1.5 rounded-lg">
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />连接中
                    </span>
                  )}
                  {connectedSessionId === (s.id ?? s.sessionId) && ws && (
                    <button
                      onClick={(e) => { e.stopPropagation(); disconnectWebSocket(); }}
                      className="flex items-center gap-1.5 text-xs font-bold text-red-600 bg-red-50 px-3 py-1.5 rounded-lg hover:bg-red-100 cursor-pointer"
                    >
                      <PhoneOff className="w-3.5 h-3.5" />挂断
                    </button>
                  )}
                  <button
                    onClick={(e) => { e.stopPropagation(); triggerEvaluation(s.id ?? s.sessionId); }}
                    className="text-xs font-bold text-primary bg-primary/10 px-3 py-1.5 rounded-lg hover:bg-primary/20 cursor-pointer"
                  >
                    评估
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 实时对话区 */}
      {activeSessionId && (
        <div className="bg-white rounded-2xl border border-slate-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-extrabold text-slate-800">实时对话</h3>
            <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-1 rounded-full ${
              connectedSessionId === activeSessionId && wsConnectedRef.current
                ? "text-emerald-700 bg-emerald-100"
                : "text-slate-400 bg-slate-100"
            }`}>
              <span className={`w-2 h-2 rounded-full ${
                connectedSessionId === activeSessionId && wsConnectedRef.current ? "bg-emerald-500" : "bg-slate-300"
              }`} />
              {connectedSessionId === activeSessionId && wsConnectedRef.current ? "已连接" : "未连接"}
            </span>
          </div>
          <div className="h-80 overflow-y-auto space-y-3 mb-4 bg-slate-50 rounded-xl p-4">
            {messages.map((msg, i) => (
              <div
                key={i}
                className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[70%] text-sm rounded-2xl px-4 py-2.5 ${
                    msg.role === "user"
                      ? "bg-primary text-white"
                      : msg.role === "system"
                      ? "bg-amber-50 border border-amber-200 text-amber-700"
                      : "bg-white border border-slate-200 text-slate-700"
                  } ${!msg.isFinal && msg.role === "user" ? "opacity-70 italic" : ""}`}
                >
                  {msg.content}
                  {!msg.isFinal && msg.role === "user" && (
                    <span className="inline-block w-2 h-4 bg-primary/50 animate-pulse ml-1 align-middle" />
                  )}
                  {/* 实时评分 */}
                  {msg.score != null && msg.role === "user" && msg.isFinal && (
                    <div className="mt-2 pt-1.5 border-t border-white/20 text-xs">
                      <span className="inline-flex items-center gap-1">
                        <span className="font-bold">{msg.score}分</span>
                        {msg.scoreFeedback && (
                          <span className="text-white/70">— {msg.scoreFeedback}</span>
                        )}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            ))}
            {messages.length === 0 && (
              <p className="text-sm text-slate-400 text-center py-12">
                {connectedSessionId === activeSessionId
                  ? '连接成功后，面试官将自动开始提问。点击「开始录音」按钮开始对话，<br />说话后文字会实时显示在下方输入框，编辑确认后点击「发送」按钮提交。'
                  : '点击上方「连接」按钮开始语音面试'}
              </p>
            )}
          </div>

          {/* 手动编辑文本框：只有当前会话已连接时才显示 */}
          {connectedSessionId === activeSessionId && (
            <div className="flex items-center gap-2 mt-3">
              {/* 麦克风按钮 */}
              <button
                onClick={isRecording ? stopRecording : startRecording}
                className={`flex items-center justify-center w-10 h-10 rounded-xl cursor-pointer transition ${
                  isRecording
                    ? "text-red-600 bg-red-50 hover:bg-red-100 animate-pulse"
                    : "text-emerald-600 bg-emerald-50 hover:bg-emerald-100"
                }`}
                title={isRecording ? "停止录音" : "开始录音"}
              >
                {isRecording ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
              </button>
              <input
                type="text"
                value={voiceText || interimText}
                onChange={(e) => setVoiceText(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); sendManualText(); } }}
                placeholder="说话后文字实时显示在此，可编辑修改，按 Enter 发送..."
                className="flex-1 text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 bg-white"
              />
              <button
                onClick={sendManualText}
                disabled={!voiceText.trim()}
                className="flex items-center gap-1.5 text-sm font-bold text-white bg-primary hover:bg-primary-dark rounded-xl px-4 py-2.5 transition cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Send className="w-4 h-4" />
                发送
              </button>
            </div>
          )}
        </div>
      )}

      {/* 评估结果 */}
      {evaluation && (
        <div className="bg-white rounded-2xl border border-slate-200 p-6">
          <h3 className="text-sm font-extrabold text-slate-800 mb-4">评估结果</h3>

          {/* 评估进行中 */}
          {(evaluation.evaluateStatus === "PENDING" || evaluation.evaluateStatus === "PROCESSING") && (
            <div className="flex items-center gap-3 text-amber-600 bg-amber-50 rounded-xl p-4">
              <Loader2 className="w-5 h-5 animate-spin" />
              <span className="text-sm font-bold">评估正在生成中，请稍候...</span>
            </div>
          )}

          {/* 评估失败 */}
          {evaluation.evaluateStatus === "FAILED" && (
            <div className="flex items-center gap-3 text-red-600 bg-red-50 rounded-xl p-4">
              <AlertCircle className="w-5 h-5" />
              <span className="text-sm">{evaluation.evaluateError || "评估生成失败，请重试"}</span>
            </div>
          )}

          {/* 评估完成 */}
          {evaluation.evaluateStatus === "COMPLETED" && evaluation.evaluation && (
            <>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-primary">
                    {evaluation.evaluation.overallScore ?? "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">总分</p>
                </div>
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-emerald-600">完成</p>
                  <p className="text-xs text-slate-400 mt-1">状态</p>
                </div>
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-blue-600">
                    {evaluation.evaluation.questionEvaluations?.length ?? "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">问题数</p>
                </div>
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-amber-600">
                    {evaluation.evaluation.strengths?.length ?? "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">亮点</p>
                </div>
              </div>

              {/* 各题评分 */}
              {evaluation.evaluation.questionEvaluations && evaluation.evaluation.questionEvaluations.length > 0 && (
                <div className="space-y-3 mb-4">
                  <p className="text-xs font-bold text-slate-600">各题评分</p>
                  {evaluation.evaluation.questionEvaluations.map((item, i) => (
                    <div key={i} className="bg-slate-50 rounded-xl p-4">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-bold text-slate-700">
                          Q{i + 1}. {item.question || "问题"}
                        </span>
                        <span className="text-sm font-extrabold text-primary">{item.score ?? "—"}分</span>
                      </div>
                      {item.feedback && (
                        <p className="text-xs text-slate-500">{item.feedback}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* 亮点与改进 */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {evaluation.evaluation.strengths && evaluation.evaluation.strengths.length > 0 && (
                  <div className="bg-emerald-50 rounded-xl p-4">
                    <p className="text-xs font-bold text-emerald-700 mb-2">✅ 亮点</p>
                    <ul className="text-xs text-emerald-600 space-y-1">
                      {evaluation.evaluation.strengths.map((s, i) => (
                        <li key={i}>• {s}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {evaluation.evaluation.improvements && evaluation.evaluation.improvements.length > 0 && (
                  <div className="bg-amber-50 rounded-xl p-4">
                    <p className="text-xs font-bold text-amber-700 mb-2">📈 改进建议</p>
                    <ul className="text-xs text-amber-600 space-y-1">
                      {evaluation.evaluation.improvements.map((s, i) => (
                        <li key={i}>• {s}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>

              {/* 总体评价 */}
              {evaluation.evaluation.overallFeedback && (
                <div className="bg-slate-50 rounded-xl p-4 mt-4">
                  <p className="text-xs font-bold text-slate-600 mb-1">总体评价</p>
                  <p className="text-sm text-slate-700">{evaluation.evaluation.overallFeedback}</p>
                </div>
              )}
            </>
          )}

          {/* 兼容旧版评估数据（无嵌套 evaluation 对象） */}
          {evaluation.evaluateStatus === "COMPLETED" && !evaluation.evaluation && (
            <>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-primary">
                    {evaluation.overallScore ?? "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">总分</p>
                </div>
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-emerald-600">完成</p>
                  <p className="text-xs text-slate-400 mt-1">状态</p>
                </div>
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-blue-600">
                    {evaluation.verdict ?? "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">结论</p>
                </div>
                <div className="bg-slate-50 rounded-xl p-4 text-center">
                  <p className="text-2xl font-extrabold text-amber-600">
                    {evaluation.totalRounds ?? "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">轮次</p>
                </div>
              </div>
              {evaluation.dimensionScores && (
                <div className="space-y-2 mb-4">
                  <p className="text-xs font-bold text-slate-600">各维度评分</p>
                  {Object.entries(evaluation.dimensionScores).map(([k, v]) => (
                    <div key={k} className="flex items-center gap-3">
                      <span className="text-xs text-slate-500 w-24">{k}</span>
                      <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-primary rounded-full transition-all"
                          style={{ width: `${Math.min((v as number) * 10, 100)}%` }}
                        />
                      </div>
                      <span className="text-xs font-bold text-slate-600 w-8">{v}</span>
                    </div>
                  ))}
                </div>
              )}
              {evaluation.summary && (
                <div className="bg-slate-50 rounded-xl p-4">
                  <p className="text-xs font-bold text-slate-600 mb-1">总结</p>
                  <p className="text-sm text-slate-700">{evaluation.summary}</p>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}