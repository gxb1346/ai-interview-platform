import React, { useState, useEffect, useCallback } from "react";
import {
  Search, X, ChevronLeft, ChevronRight, Loader2, FileSpreadsheet,
  Trash2, CheckSquare, Square, AlertTriangle, Star, Award, Trophy
} from "lucide-react";
import { interviewApi, authFetch } from "../api";
import type { SessionRecord, SpringPage } from "../types";

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

  // 详情弹窗
  const [activeRecord, setActiveRecord] = useState<SessionRecord | null>(null);

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
      <div>
        <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight">面试历史记录</h1>
        <p className="text-sm text-slate-500 font-sans mt-0.5">
          {data ? `共 ${data.totalElements} 条记录，第 ${data.number + 1}/${data.totalPages || 1} 页` : "加载中..."}
        </p>
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

      {/* 加载中 */}
      {loading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary animate-spin" />
        </div>
      )}

      {/* 错误 */}
      {error && !loading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
          <p className="text-red-600 text-sm">{error}</p>
          <button onClick={fetchRecords} className="mt-3 text-xs text-primary font-bold hover:underline cursor-pointer">重新加载</button>
        </div>
      )}

      {/* 记录卡片列表 */}
      {!loading && !error && data && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {data.content.map(record => (
              <div
                key={record.sessionId}
                className="bg-white/80 hover:bg-white backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition cursor-pointer group flex flex-col justify-between hover:scale-[1.01] duration-200 relative"
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

                <div className="space-y-4" onClick={() => setActiveRecord(record)}>
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

      {/* 详情弹窗 */}
      {activeRecord && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4">
          <div className="bg-white w-full max-w-lg rounded-3xl p-6 sm:p-8 space-y-5 border border-slate-150 shadow-2xl relative overflow-y-auto max-h-[90vh]">
            <button
              onClick={() => setActiveRecord(null)}
              className="absolute right-6 top-6 w-8 h-8 rounded-full border border-slate-100 flex items-center justify-center hover:bg-slate-50 cursor-pointer"
            >
              <X className="w-4 h-4 text-slate-400" />
            </button>

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
                <span className="text-xs font-bold text-slate-700">{activeRecord.totalRounds}</span>
              </div>
              <div className="bg-slate-50 p-3 rounded-xl">
                <span className="text-[10px] uppercase text-slate-400 block">综合评分</span>
                <span className="text-xs font-bold text-primary">{activeRecord.overallScore || "-"}</span>
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

            {activeRecord.verdict && (
              <div className="bg-slate-50 p-4 rounded-xl border border-slate-100">
                <span className="text-[10px] uppercase text-slate-400 block mb-1">面试结论</span>
                <span className={`text-xs font-bold border rounded-full px-3 py-1 ${getVerdictStyle(activeRecord.verdict)}`}>
                  {activeRecord.verdict}
                </span>
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