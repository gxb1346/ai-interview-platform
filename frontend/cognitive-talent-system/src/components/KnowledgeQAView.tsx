import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  Send, Loader2, BookOpen, CheckSquare, Square,
  Sparkles, ArrowLeft, FileText, ChevronDown, ChevronUp, ExternalLink
} from "lucide-react";
import { KnowledgeDocument } from "../types";
import { authFetch } from "../api";

const API_BASE = "http://localhost:8082";

interface SourceInfo {
  documentTitle: string;
  fileName: string;
  chunkContent: string;
  score: number;
  chunkIndex: number;
}

interface Message {
  role: "user" | "assistant";
  content: string;
  sources?: SourceInfo[];
}

interface KnowledgeQAViewProps {
  onNavigateBack: () => void;
}

export default function KnowledgeQAView({ onNavigateBack }: KnowledgeQAViewProps) {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [selectAll, setSelectAll] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    { role: "assistant", content: "你好！我是基于知识库的智能问答助手。请在上方选择一个或多个文档，然后开始提问。" }
  ]);
  const [inputText, setInputText] = useState("");
  const [thinking, setThinking] = useState(false);
  const [streamingText, setStreamingText] = useState("");
  const [showDocPanel, setShowDocPanel] = useState(true);
  const [expandedSources, setExpandedSources] = useState<Set<string>>(new Set());
  const messageEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // 加载文档列表
  useEffect(() => {
    authFetch(`${API_BASE}/api/knowledge/documents`)
      .then(res => res.json())
      .then((data: KnowledgeDocument[]) => setDocuments(data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamingText, thinking]);

  // 全选/取消全选
  const toggleSelectAll = () => {
    if (selectAll) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(documents.filter(d => d.indexStatus === "INDEXED").map(d => d.id)));
    }
    setSelectAll(!selectAll);
  };

  // 单选切换
  const toggleDocument = (id: number) => {
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedIds(next);
    setSelectAll(next.size === documents.filter(d => d.indexStatus === "INDEXED").length);
  };

  // 切换引用来源展开
  const toggleSourceExpand = (key: string) => {
    setExpandedSources(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  // 发送问题
  const handleSend = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!inputText.trim() || thinking) return;

    const userMsg: Message = { role: "user", content: inputText.trim() };
    setMessages(prev => [...prev, userMsg]);
    setInputText("");
    setThinking(true);
    setStreamingText("");

    let fullAnswer = "";
    let receivedDone = false;
    let sources: SourceInfo[] = [];

    // 构建对话历史（最近6轮）
    const history = messages
      .filter(m => m.role === "user" || m.role === "assistant")
      .slice(-12) // 最多6轮 = 12条消息
      .map(m => ({ role: m.role === "assistant" ? "assistant" : "user", content: m.content }));

    try {
      const body: Record<string, unknown> = {
        question: userMsg.content,
      };
      if (selectedIds.size > 0) {
        body.documentIds = Array.from(selectedIds);
      }
      if (history.length > 0) {
        body.history = history;
      }
      const url = `${API_BASE}/api/knowledge/qa/stream`;

      const response = await authFetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(`SSE 连接失败 (${response.status})`);

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      // SSE 事件上下文（跨 chunk 保持状态，解决 event:/data: 行被拆散的问题）
      let sseEventType = "";
      let sseData = "";

      const processLines = (lines: string[]) => {
        for (const line of lines) {
          if (line.startsWith("event:")) {
            sseEventType = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            sseData += line.slice(5);  // 拼接多行 data（trim 放在最后）
          } else if (line === "") {
            if (sseEventType === "token") {
              fullAnswer += sseData;
              setStreamingText(fullAnswer);
            } else if (sseEventType === "sources") {
              try {
                sources = JSON.parse(sseData.trim());
              } catch (e) {
                console.warn("解析 sources 失败:", e);
              }
            } else if (sseEventType === "done") {
              receivedDone = true;
              setMessages(prev => [...prev, { role: "assistant", content: fullAnswer, sources }]);
              setStreamingText("");
              setThinking(false);
            } else if (sseEventType === "error") {
              if (fullAnswer) {
                setMessages(prev => [...prev, { role: "assistant", content: fullAnswer, sources }]);
              } else {
                setMessages(prev => [...prev, {
                  role: "assistant",
                  content: "抱歉，AI 回答时出现错误，请稍后重试。",
                }]);
              }
              setStreamingText("");
              setThinking(false);
            }
            sseEventType = "";
            sseData = "";
          }
        }
      };

      abortRef.current = new AbortController();
      abortRef.current.signal.addEventListener("abort", () => {
        reader.cancel();
        if (fullAnswer) {
          setMessages(prev => [...prev, { role: "assistant", content: fullAnswer, sources }]);
        }
        setStreamingText("");
        setThinking(false);
      });

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          if (buffer.trim()) processLines(buffer.split("\n"));
          if (!receivedDone && fullAnswer) {
            setMessages(prev => [...prev, { role: "assistant", content: fullAnswer, sources }]);
            setStreamingText("");
            setThinking(false);
          }
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";
        processLines(lines);
      }

    } catch (err) {
      console.error("[KnowledgeQA] SSE 错误:", err);
      if (!receivedDone) {
        setMessages(prev => [...prev, {
          role: "assistant",
          content: fullAnswer || `SSE 连接失败: ${err instanceof Error ? err.message : ""}`,
        }]);
      }
      setStreamingText("");
      setThinking(false);
    }
  };

  // 清空对话
  const handleClear = () => {
    if (abortRef.current) abortRef.current.abort();
    setMessages([
      { role: "assistant", content: "你好！我是基于知识库的智能问答助手。请在上方选择一个或多个文档，然后开始提问。" }
    ]);
    setStreamingText("");
    setExpandedSources(new Set());
  };

  const indexedDocs = documents.filter(d => d.indexStatus === "INDEXED");

  // 渲染引用来源卡片
  const renderSources = (sources: SourceInfo[] | undefined, msgIndex: number) => {
    if (!sources || sources.length === 0) return null;

    // 按文档去重，每个文档只显示最高分的分块
    const uniqueSources = sources.reduce((acc, s) => {
      const key = s.documentTitle;
      if (!acc.has(key) || (acc.get(key)!.score < s.score)) {
        acc.set(key, s);
      }
      return acc;
    }, new Map<string, SourceInfo>());

    return (
      <div className="mt-3 pt-3 border-t border-slate-100">
        <div className="text-[10px] font-semibold text-slate-500 mb-2 flex items-center gap-1">
          <BookOpen className="w-3 h-3" /> 引用来源（{uniqueSources.size} 个文档）
        </div>
        <div className="space-y-1.5">
          {Array.from(uniqueSources.values()).map((source, idx) => {
            const expandKey = `${msgIndex}-${idx}`;
            const isExpanded = expandedSources.has(expandKey);
            const scorePercent = Math.round(source.score * 100);

            return (
              <div key={idx} className="bg-slate-50 rounded-lg overflow-hidden border border-slate-100">
                <button
                  onClick={() => toggleSourceExpand(expandKey)}
                  className="w-full flex items-center justify-between px-3 py-2 text-[10px] hover:bg-slate-100 transition cursor-pointer"
                >
                  <div className="flex items-center gap-2 flex-1 min-w-0">
                    <FileText className="w-3 h-3 text-primary shrink-0" />
                    <span className="font-semibold text-slate-700 truncate">{source.documentTitle}</span>
                    <span className="text-slate-400 shrink-0">#{source.chunkIndex}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0 ml-2">
                    <span className={`text-[9px] px-1.5 py-0.5 rounded-full font-bold ${
                      scorePercent >= 85 ? "bg-emerald-100 text-emerald-700" :
                      scorePercent >= 70 ? "bg-amber-100 text-amber-700" :
                      "bg-red-100 text-red-700"
                    }`}>
                      {scorePercent}%
                    </span>
                    {isExpanded ? <ChevronUp className="w-3 h-3 text-slate-400" /> : <ChevronDown className="w-3 h-3 text-slate-400" />}
                  </div>
                </button>
                {isExpanded && (
                  <div className="px-3 pb-2">
                    <div className="text-[10px] text-slate-600 leading-relaxed bg-white rounded-lg p-2 border border-slate-100 max-h-32 overflow-y-auto whitespace-pre-wrap">
                      {source.chunkContent}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div className="flex flex-col h-[calc(100vh-10rem)]">
      {/* 头部 */}
      <div className="bg-white/80 backdrop-blur-md border border-slate-200 rounded-2xl p-4 shadow-sm mb-4 shrink-0">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <button onClick={onNavigateBack}
              className="w-9 h-9 rounded-xl border border-slate-200 flex items-center justify-center hover:bg-slate-50 transition cursor-pointer">
              <ArrowLeft className="w-4 h-4 text-slate-500" />
            </button>
            <div>
              <h1 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-primary" /> 知识问答助手
              </h1>
              <p className="text-[10px] text-slate-400">
                已选 {selectedIds.size} 个文档
                {selectedIds.size === 0 && "（未选择则搜索全部知识库）"}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={handleClear}
              className="text-[10px] font-semibold text-slate-500 bg-slate-100 hover:bg-slate-200 px-3 py-1.5 rounded-lg transition cursor-pointer">
              清空对话
            </button>
            <button onClick={() => setShowDocPanel(!showDocPanel)}
              className="text-[10px] font-semibold text-primary bg-primary/10 hover:bg-primary/20 px-3 py-1.5 rounded-lg transition cursor-pointer flex items-center gap-1">
              <BookOpen className="w-3 h-3" />
              {showDocPanel ? "收起文档" : "选择文档"}
            </button>
          </div>
        </div>

        {/* 文档选择面板 */}
        {showDocPanel && (
          <div className="mt-4 pt-4 border-t border-slate-100">
            <div className="flex items-center gap-2 mb-2">
              <button onClick={toggleSelectAll}
                className="text-[10px] font-semibold text-slate-600 hover:text-primary transition cursor-pointer flex items-center gap-1">
                {selectAll ? <CheckSquare className="w-3.5 h-3.5" /> : <Square className="w-3.5 h-3.5" />}
                {selectAll ? "取消全选" : "全选"}
              </button>
              <span className="text-[10px] text-slate-400">
                （{indexedDocs.length} 个已索引文档可用）
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5 max-h-28 overflow-y-auto">
              {indexedDocs.map(doc => (
                <button key={doc.id} onClick={() => toggleDocument(doc.id)}
                  className={`text-[10px] font-semibold px-2.5 py-1.5 rounded-lg border transition cursor-pointer flex items-center gap-1
                    ${selectedIds.has(doc.id)
                      ? "bg-primary/10 text-primary border-primary/30"
                      : "bg-white text-slate-600 border-slate-200 hover:border-primary/30"}`}>
                  {selectedIds.has(doc.id)
                    ? <CheckSquare className="w-3 h-3" />
                    : <FileText className="w-3 h-3" />}
                  <span className="truncate max-w-[120px]">{doc.title}</span>
                </button>
              ))}
              {indexedDocs.length === 0 && (
                <p className="text-[10px] text-slate-400 py-1">暂无已索引的文档，请先在知识库上传文档</p>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 对话区域 */}
      <div className="flex-1 bg-white/70 backdrop-blur-md border border-slate-200 rounded-2xl shadow-sm flex flex-col overflow-hidden min-h-0">
        {/* 消息列表 */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {messages.map((msg, i) => (
            <div key={i} className={`flex items-start gap-3.5 ${msg.role === "user" ? "flex-row-reverse" : ""}`}>
              <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 text-xs font-bold
                ${msg.role === "user"
                  ? "bg-primary/10 text-primary"
                  : "bg-emerald-50 text-emerald-600"}`}>
                {msg.role === "user" ? "U" : "AI"}
              </div>
              <div className={`max-w-[75%] text-xs leading-relaxed p-3.5 rounded-2xl shadow-sm
                ${msg.role === "user"
                  ? "bg-primary text-white rounded-tr-none"
                  : "bg-white text-slate-700 border border-slate-100 rounded-tl-none"}`}>
                <div className="whitespace-pre-wrap">{msg.content}</div>
                {/* 引用来源卡片 */}
                {msg.role === "assistant" && renderSources(msg.sources, i)}
              </div>
            </div>
          ))}

          {/* 流式输出中的部分 */}
          {thinking && streamingText && (
            <div className="flex items-start gap-3.5">
              <div className="w-8 h-8 rounded-xl bg-emerald-50 flex items-center justify-center shrink-0 text-xs font-bold text-emerald-600">AI</div>
              <div className="max-w-[75%] text-xs leading-relaxed p-3.5 rounded-2xl bg-white text-slate-700 border border-slate-100 rounded-tl-none shadow-sm">
                <div className="whitespace-pre-wrap">{streamingText}</div>
                <span className="inline-block w-1.5 h-4 bg-primary ml-0.5 animate-pulse" />
              </div>
            </div>
          )}

          {/* 等待中动画 */}
          {thinking && !streamingText && (
            <div className="flex items-start gap-3.5">
              <div className="w-8 h-8 rounded-xl bg-emerald-50 flex items-center justify-center shrink-0 text-xs font-bold text-emerald-600">AI</div>
              <div className="p-3.5 bg-white border border-slate-100 rounded-2xl flex items-center gap-2 text-slate-400 text-xs">
                <Loader2 className="w-4 h-4 animate-spin" /> AI 正在检索知识库...
              </div>
            </div>
          )}

          <div ref={messageEndRef} />
        </div>

        {/* 输入框 */}
        <div className="border-t border-slate-100 p-4 bg-white/50">
          <form onSubmit={handleSend} className="flex items-center gap-3">
            <input type="text" value={inputText}
              onChange={e => setInputText(e.target.value)}
              disabled={thinking}
              placeholder={thinking ? "AI 正在回答..." : "输入您的问题...（支持多轮追问）"}
              className="flex-1 text-xs py-3 px-4 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition" />
            <button type="submit" disabled={thinking || !inputText.trim()}
              className="w-10 h-10 bg-primary hover:bg-primary-container disabled:bg-slate-300 rounded-xl flex items-center justify-center text-white transition shrink-0 cursor-pointer">
              <Send className="w-4.5 h-4.5" />
            </button>
          </form>
          <p className="text-[10px] text-slate-400 mt-2 text-center">
            基于 RAG 检索增强生成 · 支持多轮追问 · 答案可追溯来源
            {selectedIds.size > 0 && ` · 限定 ${selectedIds.size} 个文档`}
          </p>
        </div>
      </div>
    </div>
  );
}