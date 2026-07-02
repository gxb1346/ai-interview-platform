import React, { useState, useEffect, useCallback } from "react";
import {
  Search, X, ChevronLeft, ChevronRight, Loader2, FileSpreadsheet,
  Trash2, CheckSquare, Square, AlertTriangle, Star, Award, Trophy
} from "lucide-react";
import { interviewApi, authFetch } from "../api";
import { voiceInterviewApi } from "../api/voiceInterview";
import type { SessionRecord, SpringPage, SessionDetail, VoiceSessionMeta, VoiceEvaluationDetail } from "../types";

const API_BASE = "http://localhost:8082";
const PAGE_SIZE = 9;

export default function InterviewRecordsView() {
  const [data, setData] = useState<SpringPage<SessionRecord> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // 搜索条件
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [page, setPage] = useState(0);

  // 面试类型：模拟面试 / 语音面试
  const [interviewType, setInterviewType] = useState<"mock" | "voice">("mock");

  // 语音面试记录
  const [voiceRecords, setVoiceRecords] = useState<SessionRecord[]>([]);
  const [voiceLoading, setVoiceLoading] = useState(false);
  const [voiceError, setVoiceError] = useState("");

  // 详情弹窗
  const [activeRecord, setActiveRecord] = useState<SessionDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 批量删除
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showBatchConfirm, setShowBatchConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const fetchRecords = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await interviewApi.searchSessions({
        page,
        size: PAGE_SIZE,
        status: statusFilter || undefined,
        candidateId: searchTerm || undefined,
      });
      setData(result);
      setSelectedIds(new Set());
    } catch (err: any) {
      setError(err?.message || "加载面试记录失败");
    } finally {
      setLoading(false);
    }
  }, [page, searchTerm, statusFilter]);

  useEffect(() => { fetchRecords(); }, [fetchRecords]);

  // 语音面试记录获取
  const fetchVoiceRecords = useCallback(async () => {
    setVoiceLoading(true);
    setVoiceError("");
    try {
      const result = await voiceInterviewApi.getSessions(undefined, statusFilter || undefined);
      const data = result?.data ?? result;
      const list = Array.isArray(data) ? (data as VoiceSessionMeta[]) : [];
      // 映射为 SessionRecord 格式
      const mapped: SessionRecord[] = list.map((v) => ({
        sessionId: `voice-${v.sessionId ?? v.id}`,
        candidateId: v.userId,
        candidateName: v.candidateName || (v.userId && v.userId !== "default" ? `用户${v.userId}` : "未命名候选人"),
        direction: v.skillId,
        level: v.roleType,
        mode: "voice",
        status: v.status,
        totalRounds: v.messageCount ?? 0,
        overallScore: v.overallScore ?? 0,
        verdict: v.overallScore != null ? (v.overallScore >= 80 ? "推荐录用" : v.overallScore >= 60 ? "待定" : "不推荐") : "",
        createdAt: v.createdAt,
        updatedAt: v.updatedAt ?? v.createdAt,
        completedAt: v.status === "COMPLETED" ? v.updatedAt ?? v.createdAt : null,
      }));
      // 按创建时间倒序
      mapped.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setVoiceRecords(mapped);
    } catch (err: any) {
      setVoiceError(err?.message || "加载语音面试记录失败");
    } finally {
      setVoiceLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (interviewType === "voice") {
      fetchVoiceRecords();
    }
  }, [interviewType, fetchVoiceRecords]);

  const handleSearch = () => {
    setPage(0);
    fetchRecords();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") handleSearch();
  };

  // 批量删除
  const toggleSelect = (id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (!data) return;
    const allIds = data.content.map(r => r.sessionId);
    if (allIds.every(id => selectedIds.has(id))) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(allIds));
    }
  };

  const handleBatchDelete = async () => {
    setDeleting(true);
    try {
      await authFetch(`${API_BASE}/api/mock-interview/sessions/batch-delete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(Array.from(selectedIds)),
      });
      setSelectedIds(new Set());
      setShowBatchConfirm(false);
      fetchRecords();
    } catch (err) {
      console.error("批量删除失败:", err);
    } finally {
      setDeleting(false);
    }
  };

  const handleDeleteOne = async (sessionId: string) => {
    try {
      await authFetch(`${API_BASE}/api/mock-interview/sessions/batch-delete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify([sessionId]),
      });
      setActiveRecord(null);
      fetchRecords();
    } catch (err) {
      console.error("删除失败:", err);
    }
  };

  // 打开详情弹窗（调用API获取完整评估报告）
  const handleOpenDetail = async (record: SessionRecord) => {
    setDetailLoading(true);
    try {
      const detail = await interviewApi.getSession(record.sessionId);
      setActiveRecord(detail);
    } catch (err) {
      console.error("获取面试详情失败:", err);
      // 降级：使用列表数据展示基本详情
      setActiveRecord(record as any);
    } finally {
      setDetailLoading(false);
    }
  };

  // 打开语音面试详情弹窗
  const handleVoiceDetail = async (record: SessionRecord) => {
    setDetailLoading(true);
    try {
      const voiceId = record.sessionId.replace("voice-", "");
      const result = await voiceInterviewApi.getSession(Number(voiceId));
      const detail = result?.data ?? result;

      // 尝试获取 AI 评估结果
      let evaluationReport: SessionDetail["evaluationReport"] = null;
      let voiceEvalDetail: VoiceEvaluationDetail | null = null;
      try {
        const evalResult = await voiceInterviewApi.getEvaluation(Number(voiceId));
        const evalData = evalResult?.data ?? evalResult;
        const evaluation = evalData?.evaluation;
        if (evaluation) {
          voiceEvalDetail = evaluation;
          // 映射为 evaluationReport 格式
          evaluationReport = {
            overallScore: evaluation.overallScore ?? 0,
            summary: evaluation.overallFeedback ?? "",
            strengths: evaluation.strengths ?? [],
            improvements: evaluation.improvements ?? [],
            dimensionScores: {},
            verdict: (evaluation.overallScore ?? 0) >= 80
              ? "推荐录用" : (evaluation.overallScore ?? 0) >= 60 ? "待定" : "不推荐",
          };
        }
      } catch {
        // 评估未完成或不存在，忽略
      }

      setActiveRecord({
        ...record,
        candidateName: record.candidateName,
        direction: record.direction,
        status: record.status,
        overallScore: record.overallScore,
        verdict: record.verdict,
        totalRounds: record.totalRounds,
        createdAt: record.createdAt,
        messages: detail?.messages ?? [],
        evaluationReport,
        _voiceEvalDetail: voiceEvalDetail,
      } as any);
    } catch (err) {
      console.error("获取语音面试详情失败:", err);
      setActiveRecord(record as any);
    } finally {
      setDetailLoading(false);
    }
  };

  const statusLabels: Record<string, string> = {
    PREPARING: "准备中",
    IN_PROGRESS: "进行中",
    COMPLETED: "已完成",
    TERMINATED: "已终止",
    PAUSED: "已暂停",
  };

  const statusStyles: Record<string, string> = {
    COMPLETED: "bg-emerald-50 text-emerald-700 border-emerald-150",
    IN_PROGRESS: "bg-amber-50 text-amber-700 border-amber-150",
    PREPARING: "bg-blue-50 text-blue-700 border-blue-150",
    TERMINATED: "bg-red-50 text-red-700 border-red-150",
    PAUSED: "bg-slate-50 text-slate-600 border-slate-150",
  };

  const getVerdictStyle = (verdict: string) => {
    if (!verdict) return "bg-slate-50 text-slate-500";
    if (verdict.includes("录用") || verdict === "PASS") return "bg-emerald-50 text-emerald-700 border-emerald-150";
    if (verdict === "待定" || verdict === "PENDING") return "bg-amber-50 text-amber-700 border-amber-150";
    return "bg-red-50 text-red-700 border-red-150";
  };

  return (
    <div className="space-y-6">
      {/* 头部 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight">面试历史记录</h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            {interviewType === "mock"
              ? (data ? `共 ${data.totalElements} 条记录，第 ${data.number + 1}/${data.totalPages || 1} 页` : "加载中...")
              : `共 ${voiceRecords.length} 条语音面试记录`}
          </p>
        </div>
        {/* 面试类型切换 */}
        <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-xl">
          <button
            onClick={() => { setInterviewType("mock"); setPage(0); }}
            className={`text-xs font-semibold px-4 py-2 rounded-lg transition cursor-pointer ${
              interviewType === "mock"
                ? "bg-white text-primary shadow-sm"
                : "text-slate-500 hover:text-slate-700"
            }`}
          >
            模拟面试
          </button>
          <button
            onClick={() => { setInterviewType("voice"); }}
            className={`text-xs font-semibold px-4 py-2 rounded-lg transition cursor-pointer ${
              interviewType === "voice"
                ? "bg-white text-primary shadow-sm"
                : "text-slate-500 hover:text-slate-700"
            }`}
          >
            语音面试
          </button>
        </div>
      </div>

      {/* 搜索和筛选 */}
      <div className="bg-white/70 backdrop-blur-md p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col md:flex-row items-center gap-4 justify-between">
        <div className="relative w-full md:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="搜索候选人姓名..."
            className="w-full text-xs pl-9 pr-4 py-2.5 bg-slate-100/50 hover:bg-slate-100 focus:bg-white border border-slate-200 focus:border-primary outline-none rounded-xl transition"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <button
            onClick={() => { setStatusFilter(""); setPage(0); }}
            className={`text-xs font-semibold px-4 py-2 rounded-xl border transition cursor-pointer ${!statusFilter ? "bg-primary/10 text-primary font-bold border-primary" : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"}`}
          >
            全部
          </button>
          {["IN_PROGRESS", "COMPLETED", "TERMINATED", "PAUSED"].map(s => (
            <button
              key={s}
              onClick={() => { setStatusFilter(s); setPage(0); }}
              className={`text-xs font-semibold px-3.5 py-2 rounded-xl border transition cursor-pointer ${statusFilter === s ? "bg-primary/10 text-primary font-bold border-primary" : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"}`}
            >
              {statusLabels[s] || s}
            </button>
          ))}
        </div>
      </div>

      {/* 批量操作栏 */}
      {interviewType === "mock" && (
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button onClick={toggleSelectAll} className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 transition cursor-pointer flex items-center gap-1.5">
            {data && data.content.length > 0 && data.content.every(r => selectedIds.has(r.sessionId))
              ? <CheckSquare className="w-3.5 h-3.5 text-primary" />
              : <Square className="w-3.5 h-3.5 text-slate-400" />
            }
            全选
          </button>
          {selectedIds.size > 0 && (
            <span className="text-xs text-slate-400">已选 {selectedIds.size} 条</span>
          )}
        </div>
        {selectedIds.size > 0 && (
          <button
            onClick={() => setShowBatchConfirm(true)}
            className="text-xs font-semibold px-4 py-2 rounded-xl bg-red-50 text-red-600 hover:bg-red-100 border border-red-200 transition cursor-pointer flex items-center gap-1.5"
          >
            <Trash2 className="w-3.5 h-3.5" />
            批量删除 ({selectedIds.size})
          </button>
        )}
      </div>
      )}

      {/* 模拟面试：加载中 */}
      {interviewType === "mock" && loading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary animate-spin" />
        </div>
      )}

      {/* 模拟面试：错误 */}
      {interviewType === "mock" && error && !loading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
          <p className="text-red-600 text-sm">{error}</p>
          <button onClick={fetchRecords} className="mt-3 text-xs text-primary font-bold hover:underline cursor-pointer">重新加载</button>
        </div>
      )}

      {/* 模拟面试：记录卡片列表 */}
      {interviewType === "mock" && !loading && !error && data && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {data.content.map(record => (
              <div
                key={record.sessionId}
                className="bg-white/80 hover:bg-white backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition cursor-pointer group flex flex-col justify-between hover:scale-[1.01] duration-200 relative"
                onClick={() => handleOpenDetail(record)}
              >
                <button
                  onClick={e => { e.stopPropagation(); toggleSelect(record.sessionId); }}
                  className="absolute top-3 right-3 w-6 h-6 flex items-center justify-center rounded-md hover:bg-slate-100 transition z-10 cursor-pointer"
                >
                  {selectedIds.has(record.sessionId)
                    ? <CheckSquare className="w-4 h-4 text-primary" />
                    : <Square className="w-4 h-4 text-slate-300" />
                  }
                </button>

                <div className="space-y-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                        {record.candidateName || "未知"}
                        <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${statusStyles[record.status] || "bg-slate-50 text-slate-500"}`}>
                          {statusLabels[record.status] || record.status}
                        </span>
                      </h3>
                      <p className="text-xs text-slate-500 mt-0.5">{record.direction} · {record.level}</p>
                    </div>
                    {record.overallScore > 0 && (
                      <div className="bg-primary/5 p-2 rounded-xl border border-primary/5 text-center min-w-14">
                        <span className="text-[9px] uppercase font-bold text-slate-400 block">分数</span>
                        <span className="text-base font-black text-primary">{record.overallScore}</span>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-[10px] text-slate-500 border-t border-slate-100 pt-3">
                    <div className="flex justify-between">
                      <span>模式:</span>
                      <span className="font-bold text-slate-700">{record.mode === "voice" ? "语音" : "文本"}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>轮次:</span>
                      <span className="font-bold text-slate-700">{record.totalRounds}</span>
                    </div>
                    {record.verdict && (
                      <div className="flex justify-between col-span-2">
                        <span>结论:</span>
                        <span className={`font-bold text-[10px] border rounded-full px-2 py-0.5 ${getVerdictStyle(record.verdict)}`}>
                          {record.verdict}
                        </span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between border-t border-slate-100 pt-3 mt-4 text-[10px] text-slate-400">
                  <span>创建: {record.createdAt?.slice(0, 16) || "-"}</span>
                  <span className="text-primary font-bold group-hover:translate-x-1 transition duration-200">
                    查看详情 →
                  </span>
                </div>
              </div>
            ))}

            {data.content.length === 0 && (
              <div className="col-span-full border border-dashed rounded-2xl p-12 text-center text-slate-400 space-y-2">
                <FileSpreadsheet className="w-8 h-8 mx-auto" />
                <p className="text-sm font-semibold">暂无面试记录</p>
                <p className="text-xs">进入"模拟面试"开始您的第一场面试吧</p>
              </div>
            )}
          </div>

          {/* 分页 */}
          {data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 pt-4">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="text-xs font-semibold px-4 py-2 rounded-xl border border-slate-200 hover:bg-slate-50 transition cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed flex items-center gap-1"
              >
                <ChevronLeft className="w-3.5 h-3.5" /> 上一页
              </button>
              <span className="text-xs text-slate-500">
                {page + 1} / {data.totalPages}
              </span>
              <button
                onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                disabled={page >= data.totalPages - 1}
                className="text-xs font-semibold px-4 py-2 rounded-xl border border-slate-200 hover:bg-slate-50 transition cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed flex items-center gap-1"
              >
                下一页 <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
        </>
      )}

      {/* 语音面试：加载中 */}
      {interviewType === "voice" && voiceLoading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary animate-spin" />
        </div>
      )}

      {/* 语音面试：错误 */}
      {interviewType === "voice" && voiceError && !voiceLoading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
          <p className="text-red-600 text-sm">{voiceError}</p>
          <button onClick={fetchVoiceRecords} className="mt-3 text-xs text-primary font-bold hover:underline cursor-pointer">重新加载</button>
        </div>
      )}

      {/* 语音面试：记录卡片列表 */}
      {interviewType === "voice" && !voiceLoading && !voiceError && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {voiceRecords.map(record => (
            <div
              key={record.sessionId}
              className="bg-white/80 hover:bg-white backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition cursor-pointer group flex flex-col justify-between hover:scale-[1.01] duration-200"
              onClick={() => handleVoiceDetail(record)}
            >
              <div className="space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                      {record.candidateName || "未知"}
                      <span className={`text-[10px] font-bold border rounded-full px-2 py-0.5 ${statusStyles[record.status] || "bg-slate-50 text-slate-500"}`}>
                        {statusLabels[record.status] || record.status}
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500 mt-0.5">{record.direction} · {record.level}</p>
                  </div>
                  {record.overallScore > 0 && (
                    <div className="bg-primary/5 p-2 rounded-xl border border-primary/5 text-center min-w-14">
                      <span className="text-[9px] uppercase font-bold text-slate-400 block">分数</span>
                      <span className="text-base font-black text-primary">{record.overallScore}</span>
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-[10px] text-slate-500 border-t border-slate-100 pt-3">
                  <div className="flex justify-between">
                    <span>模式:</span>
                    <span className="font-bold text-slate-700">语音</span>
                  </div>
                  <div className="flex justify-between">
                    <span>轮次:</span>
                    <span className="font-bold text-slate-700">{record.totalRounds}</span>
                  </div>
                  {record.verdict && (
                    <div className="flex justify-between col-span-2">
                      <span>结论:</span>
                      <span className={`font-bold text-[10px] border rounded-full px-2 py-0.5 ${getVerdictStyle(record.verdict)}`}>
                        {record.verdict}
                      </span>
                    </div>
                  )}
                </div>
              </div>

              <div className="flex items-center justify-between border-t border-slate-100 pt-3 mt-4 text-[10px] text-slate-400">
                <span>创建: {record.createdAt?.slice(0, 16) || "-"}</span>
                <span className="text-primary font-bold group-hover:translate-x-1 transition duration-200">
                  查看详情 →
                </span>
              </div>
            </div>
          ))}

          {voiceRecords.length === 0 && (
            <div className="col-span-full border border-dashed rounded-2xl p-12 text-center text-slate-400 space-y-2">
              <FileSpreadsheet className="w-8 h-8 mx-auto" />
              <p className="text-sm font-semibold">暂无语音面试记录</p>
              <p className="text-xs">进入"语音面试"开始您的第一场语音面试吧</p>
            </div>
          )}
        </div>
      )}

      {/* 详情弹窗 */}
      {activeRecord && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4">
          <div className="bg-white w-full max-w-2xl rounded-3xl p-6 sm:p-8 space-y-5 border border-slate-150 shadow-2xl relative overflow-y-auto max-h-[90vh]">
            <button
              onClick={() => setActiveRecord(null)}
              className="absolute right-6 top-6 w-8 h-8 rounded-full border border-slate-100 flex items-center justify-center hover:bg-slate-50 cursor-pointer"
            >
              <X className="w-4 h-4 text-slate-400" />
            </button>

            {detailLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="w-8 h-8 text-primary animate-spin" />
              </div>
            ) : (
              <>
                <div className="border-b border-slate-100 pb-4 space-y-2.5">
                  <div className="flex items-center gap-2">
                    <Trophy className="w-6 h-6 text-primary" />
                    <h2 className="text-base font-bold text-slate-800">面试记录详情</h2>
                  </div>
                  <div className="flex flex-wrap items-center gap-2.5 text-xs text-slate-500">
                    <span>候选人: <strong className="text-slate-800">{activeRecord.candidateName || "-"}</strong></span>
                    <span>方向: <strong className="text-slate-800">{activeRecord.direction}</strong></span>
                    <span>等级: <strong className="text-slate-800">{activeRecord.level}</strong></span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div className="bg-slate-50 p-3 rounded-xl">
                    <span className="text-[10px] uppercase text-slate-400 block">状态</span>
                    <span className={`text-xs font-bold border rounded-full px-2 py-0.5 mt-1 inline-block ${statusStyles[activeRecord.status] || ""}`}>
                      {statusLabels[activeRecord.status] || activeRecord.status}
                    </span>
                  </div>
                  <div className="bg-slate-50 p-3 rounded-xl">
                    <span className="text-[10px] uppercase text-slate-400 block">面试模式</span>
                    <span className="text-xs font-bold text-slate-700">{activeRecord.mode === "voice" ? "语音面试" : "文本面试"}</span>
                  </div>
                  <div className="bg-slate-50 p-3 rounded-xl">
                    <span className="text-[10px] uppercase text-slate-400 block">总轮次</span>
                    <span className="text-xs font-bold text-slate-700">{activeRecord.currentRound ?? (activeRecord as any).totalRounds ?? "-"}</span>
                  </div>
                  <div className="bg-slate-50 p-3 rounded-xl">
                    <span className="text-[10px] uppercase text-slate-400 block">综合评分</span>
                    <span className="text-xs font-bold text-primary">
                      {activeRecord.evaluationReport?.overallScore ?? "-"}
                    </span>
                  </div>
                  <div className="bg-slate-50 p-3 rounded-xl">
                    <span className="text-[10px] uppercase text-slate-400 block">创建时间</span>
                    <span className="text-xs font-bold text-slate-700">{activeRecord.createdAt?.slice(0, 16) || "-"}</span>
                  </div>
                  <div className="bg-slate-50 p-3 rounded-xl">
                    <span className="text-[10px] uppercase text-slate-400 block">完成时间</span>
                    <span className="text-xs font-bold text-slate-700">{activeRecord.completedAt?.slice(0, 16) || "未完成"}</span>
                  </div>
                </div>

                {activeRecord.evaluationReport?.verdict && (
                  <div className="bg-slate-50 p-4 rounded-xl border border-slate-100">
                    <span className="text-[10px] uppercase text-slate-400 block mb-1">面试结论</span>
                    <span className={`text-xs font-bold border rounded-full px-3 py-1 ${getVerdictStyle(activeRecord.evaluationReport.verdict)}`}>
                      {activeRecord.evaluationReport.verdict}
                    </span>
                  </div>
                )}

                {/* AI 评估报告 */}
                {activeRecord.evaluationReport && (
                  <div className="space-y-4 border-t border-slate-100 pt-4">
                    <div className="flex items-center gap-2">
                      <Award className="w-5 h-5 text-amber-500" />
                      <h3 className="text-sm font-bold text-slate-800">AI 评估报告</h3>
                    </div>

                    {/* 维度评分 */}
                    {activeRecord.evaluationReport.dimensionScores && Object.keys(activeRecord.evaluationReport.dimensionScores).length > 0 && (
                      <div className="grid grid-cols-2 gap-3">
                        {Object.entries(activeRecord.evaluationReport.dimensionScores).map(([key, score]) => (
                          <div key={key} className="bg-slate-50 p-3 rounded-xl">
                            <span className="text-[10px] uppercase text-slate-400 block">
                              {{ technical: "技术深度", communication: "沟通表达", problemSolving: "问题解决", culturalFit: "综合素质" }[key] || key}
                            </span>
                            <div className="flex items-center gap-2 mt-1">
                              <div className="flex-1 h-1.5 bg-slate-200 rounded-full overflow-hidden">
                                <div
                                  className="h-full bg-primary rounded-full transition-all"
                                  style={{ width: `${(score / 10) * 100}%` }}
                                />
                              </div>
                              <span className="text-xs font-bold text-primary">{score}/10</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* AI 总结 */}
                    {activeRecord.evaluationReport.summary && (
                      <div className="bg-blue-50/50 border border-blue-100 rounded-xl p-4">
                        <span className="text-[10px] uppercase text-blue-500 font-bold block mb-2">AI 评估总结</span>
                        <p className="text-xs text-slate-700 leading-relaxed whitespace-pre-wrap">
                          {activeRecord.evaluationReport.summary}
                        </p>
                      </div>
                    )}

                    {/* 优势 */}
                    {activeRecord.evaluationReport.strengths && activeRecord.evaluationReport.strengths.length > 0 && (
                      <div className="bg-emerald-50/50 border border-emerald-100 rounded-xl p-4">
                        <span className="text-[10px] uppercase text-emerald-600 font-bold block mb-2 flex items-center gap-1">
                          <Star className="w-3.5 h-3.5" /> 优势亮点
                        </span>
                        <ul className="space-y-1.5">
                          {activeRecord.evaluationReport.strengths.map((s, i) => (
                            <li key={i} className="text-xs text-slate-700 flex items-start gap-1.5">
                              <span className="text-emerald-500 mt-0.5">•</span>
                              {s}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* 待改进项 */}
                    {activeRecord.evaluationReport.improvements && activeRecord.evaluationReport.improvements.length > 0 && (
                      <div className="bg-amber-50/50 border border-amber-100 rounded-xl p-4">
                        <span className="text-[10px] uppercase text-amber-600 font-bold block mb-2">待改进项</span>
                        <ul className="space-y-1.5">
                          {activeRecord.evaluationReport.improvements.map((item, i) => (
                            <li key={i} className="text-xs text-slate-700 flex items-start gap-1.5">
                              <span className="text-amber-500 mt-0.5">•</span>
                              {item}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}

                {/* 语音面试逐题评估 */}
                {activeRecord.mode === "voice" && (activeRecord as any)._voiceEvalDetail && (
                  <div className="space-y-4 border-t border-slate-100 pt-4">
                    <div className="flex items-center gap-2">
                      <Award className="w-5 h-5 text-purple-500" />
                      <h3 className="text-sm font-bold text-slate-800">逐题评估</h3>
                    </div>
                    <div className="space-y-3">
                      {((activeRecord as any)._voiceEvalDetail as VoiceEvaluationDetail).answers?.map((item: any, i: number) => (
                        <div key={i} className="bg-slate-50 rounded-xl p-4 border border-slate-100">
                          <div className="flex items-start justify-between gap-3 mb-2">
                            <div className="flex-1">
                              <span className="text-[10px] font-bold text-slate-400 uppercase">第 {item.questionIndex ?? i + 1} 题</span>
                              <p className="text-xs font-semibold text-slate-800 mt-0.5">{item.question}</p>
                            </div>
                            <span className={`text-xs font-bold px-2.5 py-1 rounded-full border ${
                              (item.score ?? 0) >= 80 ? "bg-emerald-50 text-emerald-700 border-emerald-200" :
                              (item.score ?? 0) >= 60 ? "bg-amber-50 text-amber-700 border-amber-200" :
                              "bg-red-50 text-red-700 border-red-200"
                            }`}>
                              {item.score ?? "-"} 分
                            </span>
                          </div>
                          <p className="text-xs text-slate-600 leading-relaxed mb-2">
                            <span className="font-bold text-slate-500">回答：</span>{item.userAnswer || "（无回答）"}
                          </p>
                          {item.feedback && (
                            <p className="text-xs text-slate-600 leading-relaxed mb-2">
                              <span className="font-bold text-slate-500">评价：</span>{item.feedback}
                            </p>
                          )}
                          {item.keyPoints && item.keyPoints.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1">
                              {item.keyPoints.map((kp: string, j: number) => (
                                <span key={j} className="text-[10px] bg-blue-50 text-blue-600 px-2 py-0.5 rounded-full border border-blue-100">
                                  {kp}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 语音面试对话记录 */}
                {activeRecord.mode === "voice" && (activeRecord as any).messages && (activeRecord as any).messages.length > 0 && (
                  <div className="space-y-4 border-t border-slate-100 pt-4">
                    <div className="flex items-center gap-2">
                      <Star className="w-5 h-5 text-indigo-500" />
                      <h3 className="text-sm font-bold text-slate-800">对话记录</h3>
                    </div>
                    <div className="space-y-2 max-h-64 overflow-y-auto">
                      {((activeRecord as any).messages as { role: string; content: string; timestamp?: string }[]).map((msg, i) => (
                        <div key={i} className={`p-3 rounded-xl text-xs ${
                          msg.role === "interviewer" || msg.role === "assistant"
                            ? "bg-blue-50 text-slate-700"
                            : "bg-emerald-50 text-slate-700"
                        }`}>
                          <span className="text-[10px] font-bold text-slate-400 block mb-1">
                            {msg.role === "interviewer" || msg.role === "assistant" ? "🤖 面试官" : "👤 候选人"}
                          </span>
                          <p className="leading-relaxed">{msg.content}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="border-t border-slate-100 pt-4 flex items-center justify-between">
                  <button
                    onClick={() => {
                      if (window.confirm("确定删除该面试记录？")) {
                        handleDeleteOne(activeRecord.sessionId);
                      }
                    }}
                    className="text-xs bg-red-50 text-red-600 hover:bg-red-100 font-semibold py-2 px-4 rounded-lg transition border border-red-200 cursor-pointer flex items-center gap-1.5"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    删除记录
                  </button>
                  <button
                    onClick={() => setActiveRecord(null)}
                    className="text-xs bg-primary/10 text-primary font-bold hover:bg-primary/20 py-2 px-5 rounded-lg transition shadow-sm border border-primary cursor-pointer"
                  >
                    关闭
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* 批量删除确认弹窗 */}
      {showBatchConfirm && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-[200] flex items-center justify-center p-4">
          <div className="bg-white w-full max-w-sm rounded-2xl p-6 space-y-4 border border-slate-200 shadow-2xl">
            <div className="text-center">
              <AlertTriangle className="w-10 h-10 text-red-500 mx-auto mb-2" />
              <h3 className="text-lg font-bold text-slate-800">确认批量删除</h3>
              <p className="text-sm text-slate-500 mt-1">
                确定要删除选中的 <span className="font-bold text-red-600">{selectedIds.size}</span> 条记录吗？不可恢复。
              </p>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => setShowBatchConfirm(false)}
                className="flex-1 text-xs font-semibold py-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 transition cursor-pointer"
              >
                取消
              </button>
              <button
                onClick={handleBatchDelete}
                disabled={deleting}
                className="flex-1 text-xs font-semibold py-2.5 rounded-xl bg-red-600 text-white hover:bg-red-700 transition cursor-pointer disabled:opacity-50"
              >
                {deleting ? "删除中..." : "确认删除"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}