import { useState, useEffect, useCallback } from "react";
import { Plus, Trash2, CheckCircle, XCircle, Loader2, AlertCircle, Sparkles, Calendar, MapPin, Clock, Building2 } from "lucide-react";
import { scheduleApi } from "../api/schedule";
import type { InterviewSchedule, ParseInterviewResponse } from "../types";

export function InterviewScheduleView() {
  const [schedules, setSchedules] = useState<InterviewSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [parseInput, setParseInput] = useState("");
  const [parseResult, setParseResult] = useState<ParseInterviewResponse | null>(null);
  const [form, setForm] = useState({
    companyName: "", position: "", interviewTime: "", interviewType: "视频面试", notes: ""
  });

  const loadSchedules = useCallback(async () => {
    try {
      const res = await scheduleApi.list({});
      setSchedules(res?.data ?? res ?? []);
    } catch {
      setError("加载日程失败");
    }
  }, []);

  useEffect(() => { loadSchedules(); }, [loadSchedules]);

  const handleCreate = async () => {
    if (!form.companyName || !form.position || !form.interviewTime) {
      setError("请填写完整信息");
      return;
    }
    setLoading(true);
    try {
      await scheduleApi.create(form);
      setSuccessMsg("日程已创建");
      setShowAddForm(false);
      setForm({ companyName: "", position: "", interviewTime: "", interviewType: "视频面试", notes: "" });
      await loadSchedules();
    } catch {
      setError("创建失败");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm("确定删除？")) return;
    try {
      await scheduleApi.delete(id);
      setSuccessMsg("已删除");
      await loadSchedules();
    } catch {
      setError("删除失败");
    }
  };

  const handleParse = async () => {
    if (!parseInput.trim()) return;
    setLoading(true);
    setParseResult(null);
    try {
      const res = await scheduleApi.parse({ rawText: parseInput });
      const data = res?.data ?? res;
      setParseResult(data);
      if (data) {
        setForm({
          companyName: data.companyName ?? "",
          position: data.position ?? "",
          interviewTime: data.interviewTime ?? "",
          interviewType: data.interviewType ?? "视频面试",
          notes: data.notes ?? parseInput,
        });
        setShowAddForm(true);
      }
    } catch {
      setError("AI 解析失败");
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status: string) => {
    const map: Record<string, { className: string; label: string }> = {
      pending: { className: "bg-amber-50 text-amber-700", label: "待面试" },
      confirmed: { className: "bg-emerald-50 text-emerald-700", label: "已确认" },
      completed: { className: "bg-blue-50 text-blue-700", label: "已完成" },
      cancelled: { className: "bg-red-50 text-red-600", label: "已取消" },
    };
    const item = map[status] ?? { className: "bg-slate-50 text-slate-600", label: status };
    return (
      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${item.className}`}>
        {item.label}
      </span>
    );
  };

  return (
    <div className="space-y-6">
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

      {/* AI 解析面试通知 */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6">
        <div className="flex items-center gap-2 mb-4">
          <Sparkles className="w-5 h-5 text-amber-500" />
          <h3 className="text-sm font-extrabold text-slate-800">AI 解析面试通知</h3>
        </div>
        <div className="flex gap-3">
          <textarea
            value={parseInput}
            onChange={(e) => setParseInput(e.target.value)}
            placeholder="粘贴面试邮件或短信内容，AI 将自动提取信息..."
            rows={2}
            className="flex-1 text-sm border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 resize-none"
          />
          <button
            onClick={handleParse}
            disabled={loading || !parseInput.trim()}
            className="flex items-center gap-1.5 text-sm font-bold text-white bg-primary px-5 py-3 rounded-xl hover:bg-primary-dark transition cursor-pointer disabled:opacity-50 shrink-0"
          >
            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
            AI 解析
          </button>
        </div>

        {/* 解析结果 */}
        {parseResult && (
          <div className="mt-4 p-4 rounded-xl bg-amber-50 border border-amber-200">
            <p className="text-xs font-bold text-amber-700 mb-2">解析结果：</p>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-sm">
              <div className="flex items-center gap-2">
                <Building2 className="w-4 h-4 text-amber-600" />
                <span className="font-bold">{parseResult.companyName ?? "—"}</span>
              </div>
              <div className="flex items-center gap-2">
                <Calendar className="w-4 h-4 text-amber-600" />
                <span>{parseResult.interviewTime ?? "—"}</span>
              </div>
              <div className="flex items-center gap-2">
                <MapPin className="w-4 h-4 text-amber-600" />
                <span>{parseResult.interviewType ?? "—"}</span>
              </div>
              <div className="flex items-center gap-2">
                <Clock className="w-4 h-4 text-amber-600" />
                <span className="font-bold">{parseResult.position ?? "—"}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 操作栏 */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-extrabold text-slate-800">面试日程</h3>
        <button
          onClick={() => setShowAddForm(!showAddForm)}
          className="flex items-center gap-1.5 text-xs font-bold text-white bg-primary px-3 py-2 rounded-xl hover:bg-primary-dark cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5" />添加日程
        </button>
      </div>

      {/* 添加表单 */}
      {showAddForm && (
        <div className="bg-white rounded-2xl border border-slate-200 p-6">
          <h4 className="text-sm font-bold text-slate-800 mb-4">新增面试日程</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">公司名称</label>
              <input
                value={form.companyName}
                onChange={(e) => setForm((f) => ({ ...f, companyName: e.target.value }))}
                placeholder="如: 字节跳动"
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">职位</label>
              <input
                value={form.position}
                onChange={(e) => setForm((f) => ({ ...f, position: e.target.value }))}
                placeholder="如: Java 开发工程师"
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">面试时间</label>
              <input
                type="datetime-local"
                value={form.interviewTime}
                onChange={(e) => setForm((f) => ({ ...f, interviewTime: e.target.value }))}
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">面试类型</label>
              <select
                value={form.interviewType}
                onChange={(e) => setForm((f) => ({ ...f, interviewType: e.target.value }))}
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              >
                <option>视频面试</option>
                <option>电话面试</option>
                <option>现场面试</option>
                <option>笔试</option>
              </select>
            </div>
          </div>
          <div className="flex justify-end gap-2 mt-4">
            <button
              onClick={() => setShowAddForm(false)}
              className="text-xs font-bold text-slate-500 px-4 py-2 rounded-xl hover:bg-slate-100 cursor-pointer"
            >
              取消
            </button>
            <button
              onClick={handleCreate}
              disabled={loading}
              className="flex items-center gap-1.5 text-xs font-bold text-white bg-primary px-4 py-2 rounded-xl hover:bg-primary-dark cursor-pointer disabled:opacity-50"
            >
              {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : null}保存
            </button>
          </div>
        </div>
      )}

      {/* 日程列表 */}
      <div className="space-y-3">
        {schedules.map((s) => (
          <div
            key={s.id}
            className="bg-white rounded-2xl border border-slate-200 p-5 flex items-center justify-between"
          >
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
                <Building2 className="w-5 h-5 text-primary" />
              </div>
              <div>
                <div className="flex items-center gap-2 mb-0.5">
                  <p className="text-sm font-extrabold text-slate-800">{s.companyName}</p>
                  {getStatusBadge(s.status ?? "pending")}
                </div>
                <p className="text-xs text-slate-500">{s.position} · {s.interviewType}</p>
                <p className="text-xs text-slate-400 mt-0.5">{s.interviewTime}</p>
              </div>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => handleDelete(s.id)}
                className="p-2 rounded-lg hover:bg-red-50 cursor-pointer"
                title="删除"
              >
                <Trash2 className="w-4 h-4 text-red-400" />
              </button>
            </div>
          </div>
        ))}
        {schedules.length === 0 && (
          <p className="text-sm text-slate-400 text-center py-12">暂无面试日程</p>
        )}
      </div>
    </div>
  );
}
