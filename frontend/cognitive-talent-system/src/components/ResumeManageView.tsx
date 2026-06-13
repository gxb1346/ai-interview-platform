/**
 * 简历管理视图 — 分页展示/搜索/筛选/删除
 * 前后端联调：调用 Java 后端 API
 */
import React, { useState, useEffect, useCallback } from "react";
import {
  Search, Filter, Trash2, Eye, FileText, ChevronLeft, ChevronRight,
  X, AlertCircle, Sparkles, SlidersHorizontal, Download, HardDrive,
  RotateCcw, UserPlus, UserCheck
} from "lucide-react";
import { ResumeVO, PageResult, ApiResult } from "../types";
import ResumeDetailEditModal from "./ResumeDetailEditModal";

const API_BASE = "http://localhost:8082";

const PAGE_SIZE_OPTIONS = [5, 10, 20, 50];

export default function ResumeManageView() {
  // 列表数据
  const [data, setData] = useState<PageResult<ResumeVO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 分页
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  // 搜索 & 筛选
  const [keyword, setKeyword] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [education, setEducation] = useState("");
  const [minScore, setMinScore] = useState<number | undefined>(undefined);
  const [maxScore, setMaxScore] = useState<number | undefined>(undefined);
  const [showFilters, setShowFilters] = useState(false);

  // 详情/编辑弹窗
  const [selectedResume, setSelectedResume] = useState<ResumeVO | null>(null);
  const [showDetail, setShowDetail] = useState(false);

  // 删除确认
  const [deleteTarget, setDeleteTarget] = useState<ResumeVO | null>(null);
  const [deleting, setDeleting] = useState(false);

  // 移入人才库
  const [talentPoolTarget, setTalentPoolTarget] = useState<number | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("pageSize", String(pageSize));
      if (searchKeyword) params.set("keyword", searchKeyword);
      if (education) params.set("education", education);
      if (minScore !== undefined) params.set("minScore", String(minScore));
      if (maxScore !== undefined) params.set("maxScore", String(maxScore));

      const res = await fetch(`${API_BASE}/api/resume/page?${params}`);
      const json: ApiResult<PageResult<ResumeVO>> = await res.json();
      if (json.code === 200) {
        setData(json.data);
      } else {
        setError(json.message || "加载失败");
      }
    } catch (err: any) {
      setError("网络错误: " + err.message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, searchKeyword, education, minScore, maxScore]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // 搜索
  const handleSearch = () => {
    setPage(0);
    setSearchKeyword(keyword);
  };

  const handleReset = () => {
    setKeyword("");
    setSearchKeyword("");
    setEducation("");
    setMinScore(undefined);
    setMaxScore(undefined);
    setPage(0);
  };

  // 删除（仅软删除）
  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      const res = await fetch(`${API_BASE}/api/resume/${deleteTarget.id}/soft`, { method: "DELETE" });
      const json: ApiResult<null> = await res.json();
      if (json.code === 200) {
        setDeleteTarget(null);
        fetchData();
      } else {
        setError(json.message || "删除失败");
      }
    } catch (err: any) {
      setError("删除失败: " + err.message);
    } finally {
      setDeleting(false);
    }
  };

  // 查看详情
  const handleViewDetail = (item: ResumeVO) => {
    setSelectedResume(item);
    setShowDetail(true);
  };

  // 移入人才库
  const handleMoveToTalentPool = async (id: number) => {
    setTalentPoolTarget(id);
    try {
      const res = await fetch(`${API_BASE}/api/resume/${id}/to-talent-pool`, { method: "POST" });
      const json: ApiResult<ResumeVO> = await res.json();
      if (json.code === 200) {
        fetchData();
      }
    } catch (err: any) {
      console.error("移入人才库失败:", err);
    } finally {
      setTalentPoolTarget(null);
    }
  };

  // 编辑成功后刷新
  const handleEditSuccess = () => {
    setShowDetail(false);
    setSelectedResume(null);
    fetchData();
  };

  // 格式化文件大小
  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  // 分数颜色
  const scoreColor = (score: number | null) => {
    if (score === null) return "text-slate-400";
    if (score >= 90) return "text-emerald-600";
    if (score >= 80) return "text-blue-600";
    if (score >= 70) return "text-amber-600";
    return "text-red-500";
  };

  return (
    <div className="space-y-6">
      {/* 头部 */}
      <div className="bg-white/70 backdrop-blur-md p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center">
              <FileText className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h1 className="text-lg font-bold font-sans text-slate-900">简历管理</h1>
              <p className="text-xs text-slate-500 font-sans">
                {data ? `共 ${data.total} 条记录` : "加载中..."}
              </p>
            </div>
          </div>

          {/* 搜索栏 */}
          <div className="flex items-center gap-2 w-full md:w-auto">
            <div className="relative flex-1 md:w-64">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
                placeholder="搜索姓名/岗位/文件名..."
                className="w-full text-xs pl-9 pr-3 py-2 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition font-sans"
              />
            </div>
            <button onClick={handleSearch}
              className="text-xs bg-primary/10 text-primary font-bold border border-primary/30 py-2 px-4 rounded-xl hover:bg-primary/20 transition cursor-pointer">
              搜索
            </button>
            <button onClick={() => setShowFilters(!showFilters)}
              className={`text-xs font-semibold py-2 px-3 rounded-xl border transition cursor-pointer flex items-center gap-1 ${
                showFilters ? "bg-primary/10 text-primary border-primary/30" : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
              }`}>
              <SlidersHorizontal className="w-3.5 h-3.5" /> 筛选
            </button>
          </div>
        </div>

        {/* 筛选面板 */}
        {showFilters && (
          <div className="mt-4 p-4 bg-slate-50 rounded-xl border border-slate-200 space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
              <div>
                <label className="text-[10px] font-semibold text-slate-500 uppercase block mb-1">学历</label>
                <select value={education} onChange={(e) => setEducation(e.target.value)}
                  className="w-full text-xs py-2 px-3 bg-white rounded-lg border border-slate-200 outline-none focus:border-primary font-sans">
                  <option value="">全部</option>
                  <option value="本科">本科</option>
                  <option value="硕士">硕士</option>
                  <option value="博士">博士</option>
                  <option value="大专">大专</option>
                </select>
              </div>
              <div>
                <label className="text-[10px] font-semibold text-slate-500 uppercase block mb-1">最低匹配分</label>
                <input type="number" min={0} max={100} value={minScore ?? ""}
                  onChange={(e) => setMinScore(e.target.value ? Number(e.target.value) : undefined)}
                  className="w-full text-xs py-2 px-3 bg-white rounded-lg border border-slate-200 outline-none focus:border-primary font-sans"
                  placeholder="0" />
              </div>
              <div>
                <label className="text-[10px] font-semibold text-slate-500 uppercase block mb-1">最高匹配分</label>
                <input type="number" min={0} max={100} value={maxScore ?? ""}
                  onChange={(e) => setMaxScore(e.target.value ? Number(e.target.value) : undefined)}
                  className="w-full text-xs py-2 px-3 bg-white rounded-lg border border-slate-200 outline-none focus:border-primary font-sans"
                  placeholder="100" />
              </div>
              <div className="flex items-end">
                <button onClick={handleReset}
                  className="text-xs bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold py-2 px-4 rounded-lg transition cursor-pointer flex items-center gap-1">
                  <RotateCcw className="w-3 h-3" /> 重置
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="flex items-center gap-2.5 p-3.5 bg-red-50 rounded-xl border border-red-100 text-red-600 text-xs font-sans">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-auto cursor-pointer"><X className="w-3.5 h-3.5" /></button>
        </div>
      )}

      {/* 列表 */}
      <div className="bg-white/80 backdrop-blur-md rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {loading && !data ? (
          <div className="p-12 text-center text-sm text-slate-400">加载中...</div>
        ) : data && data.list.length === 0 ? (
          <div className="p-12 text-center text-sm text-slate-400">
            <FileText className="w-10 h-10 mx-auto mb-3 text-slate-300" />
            暂无简历记录
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="text-left font-semibold text-slate-500 uppercase py-3.5 px-4">姓名</th>
                  <th className="text-left font-semibold text-slate-500 uppercase py-3.5 px-4 hidden md:table-cell">岗位</th>
                  <th className="text-left font-semibold text-slate-500 uppercase py-3.5 px-4 hidden lg:table-cell">学历</th>
                  <th className="text-center font-semibold text-slate-500 uppercase py-3.5 px-4">匹配度</th>
                  <th className="text-left font-semibold text-slate-500 uppercase py-3.5 px-4 hidden lg:table-cell">文件名</th>
                  <th className="text-left font-semibold text-slate-500 uppercase py-3.5 px-4 hidden sm:table-cell">分析时间</th>
                  <th className="text-center font-semibold text-slate-500 uppercase py-3.5 px-4">操作</th>
                </tr>
              </thead>
              <tbody>
                {data?.list.map((item) => (
                  <tr key={item.id} className="border-b border-slate-100 hover:bg-slate-50/50 transition">
                    <td className="py-3.5 px-4">
                      <div>
                        <span className="font-semibold text-slate-800">{item.candidateName || "—"}</span>
                        {item.experienceYears && (
                          <span className="ml-2 text-[10px] text-slate-400">{item.experienceYears}年</span>
                        )}
                      </div>
                      {item.email && <div className="text-[10px] text-slate-400 mt-0.5">{item.email}</div>}
                    </td>
                    <td className="py-3.5 px-4 hidden md:table-cell text-slate-600">{item.candidateRole || "—"}</td>
                    <td className="py-3.5 px-4 hidden lg:table-cell">
                      {item.education ? (
                        <span className="bg-slate-100 text-slate-600 py-0.5 px-2 rounded text-[10px] font-medium">{item.education}</span>
                      ) : "—"}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      {item.matchScore !== null ? (
                        <span className={`font-bold text-sm font-mono ${scoreColor(item.matchScore)}`}>
                          {item.matchScore}%
                        </span>
                      ) : "—"}
                    </td>
                    <td className="py-3.5 px-4 hidden lg:table-cell">
                      <div className="flex items-center gap-1.5">
                        <FileText className="w-3 h-3 text-slate-400" />
                        <span className="text-slate-600 truncate max-w-[160px]">{item.fileName}</span>
                        <span className="text-[10px] text-slate-400">({formatSize(item.fileSize)})</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-4 hidden sm:table-cell text-slate-400 text-[10px]">{item.analyzedAt}</td>
                    <td className="py-3.5 px-4">
                      <div className="flex items-center justify-center gap-1.5">
                        {!item.inTalentPool && (
                          <button onClick={() => handleMoveToTalentPool(item.id)}
                            disabled={talentPoolTarget === item.id}
                            className="p-1.5 rounded-lg hover:bg-emerald-50 text-slate-400 hover:text-emerald-500 transition cursor-pointer disabled:opacity-50"
                            title="移入人才库">
                            <UserPlus className="w-4 h-4" />
                          </button>
                        )}
                        {item.inTalentPool && (
                          <span className="p-1.5 text-emerald-500" title="已在人才库">
                            <UserCheck className="w-4 h-4" />
                          </span>
                        )}
                        <button onClick={() => handleViewDetail(item)}
                          className="p-1.5 rounded-lg hover:bg-primary/10 text-slate-400 hover:text-primary transition cursor-pointer"
                          title="查看/编辑">
                          <Eye className="w-4 h-4" />
                        </button>
                        <button onClick={() => { setDeleteTarget(item); }}
                          className="p-1.5 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-500 transition cursor-pointer"
                          title="删除">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* 分页 */}
        {data && data.totalPages > 0 && (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 border-t border-slate-200 bg-white">
            <div className="flex items-center gap-2 text-[10px] text-slate-500">
              <span>每页</span>
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
                className="text-xs py-1 px-2 bg-white rounded border border-slate-200 outline-none font-sans">
                {PAGE_SIZE_OPTIONS.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
              <span>条，第 {data.page + 1}/{data.totalPages} 页</span>
            </div>
            <div className="flex items-center gap-1.5">
              <button disabled={page <= 0} onClick={() => setPage(p => Math.max(0, p - 1))}
                className="p-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 disabled:opacity-30 transition cursor-pointer disabled:cursor-not-allowed">
                <ChevronLeft className="w-4 h-4 text-slate-600" />
              </button>
              {Array.from({ length: Math.min(5, data.totalPages) }, (_, i) => {
                const start = Math.max(0, Math.min(page - 2, data.totalPages - 5));
                const p = start + i;
                if (p >= data.totalPages) return null;
                return (
                  <button key={p} onClick={() => setPage(p)}
                    className={`min-w-[32px] h-8 text-xs font-semibold rounded-lg transition cursor-pointer ${
                      p === page ? "bg-primary/10 text-primary font-bold" : "text-slate-600 hover:bg-slate-100"
                    }`}>
                    {p + 1}
                  </button>
                );
              })}
              <button disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)}
                className="p-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 disabled:opacity-30 transition cursor-pointer disabled:cursor-not-allowed">
                <ChevronRight className="w-4 h-4 text-slate-600" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/30 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-xl p-6 max-w-sm w-full mx-4 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-red-100 rounded-xl flex items-center justify-center">
                <AlertCircle className="w-5 h-5 text-red-500" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800 font-sans">确认软删除</h3>
                <p className="text-xs text-slate-500 font-sans">
                  候选人：{deleteTarget.candidateName || "未知"}
                </p>
                <p className="text-[10px] text-slate-400 font-sans mt-1">
                  软删除后可在数据库中恢复，前端不再展示
                </p>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteTarget(null)}
                className="text-xs font-semibold py-2 px-4 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition cursor-pointer">
                取消
              </button>
              <button onClick={handleDelete} disabled={deleting}
                className="text-xs font-semibold py-2 px-4 rounded-xl bg-red-500 hover:bg-red-600 text-white-pure transition cursor-pointer disabled:opacity-50 flex items-center gap-1.5">
                {deleting ? "删除中..." : "确认删除"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 详情/编辑弹窗 */}
      {showDetail && selectedResume && (
        <ResumeDetailEditModal
          resume={selectedResume}
          onClose={() => { setShowDetail(false); setSelectedResume(null); }}
          onSuccess={handleEditSuccess}
        />
      )}
    </div>
  );
}
