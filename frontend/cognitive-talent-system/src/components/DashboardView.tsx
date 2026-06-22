import React, { useState, useEffect, useCallback } from "react";
import {
  BarChart3, Users, CheckCircle2, Clock, TrendingUp, Award,
  PieChart, Calendar, Loader2, RefreshCw
} from "lucide-react";
import { interviewApi } from "../api";
import type { DashboardStats } from "../types";

export default function DashboardView() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchStats = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await interviewApi.getDashboardStats();
      setStats(data);
    } catch (err: any) {
      setError(err?.message || "加载统计数据失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchStats(); }, [fetchStats]);

  const statusLabels: Record<string, string> = {
    PREPARING: "准备中",
    IN_PROGRESS: "进行中",
    COMPLETED: "已完成",
    TERMINATED: "已终止",
    PAUSED: "已暂停",
  };

  const statusColors: Record<string, string> = {
    PREPARING: "bg-blue-100 text-blue-700",
    IN_PROGRESS: "bg-amber-100 text-amber-700",
    COMPLETED: "bg-emerald-100 text-emerald-700",
    TERMINATED: "bg-red-100 text-red-700",
    PAUSED: "bg-slate-100 text-slate-700",
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
        <p className="text-red-600 text-sm">{error}</p>
        <button onClick={fetchStats} className="mt-3 text-xs text-primary font-bold hover:underline cursor-pointer">
          重新加载
        </button>
      </div>
    );
  }

  if (!stats) return null;

  return (
    <div className="space-y-6">
      {/* 头部 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight">系统仪表盘</h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">面试数据概览与趋势分析</p>
        </div>
        <button
          onClick={fetchStats}
          className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-primary px-3 py-2 rounded-xl border border-slate-200 hover:border-primary/30 transition cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          刷新
        </button>
      </div>

      {/* 核心指标卡片 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard
          icon={<Users className="w-5 h-5" />}
          label="面试总数"
          value={stats.totalSessions}
          color="bg-blue-50 text-blue-600 border-blue-100"
        />
        <StatCard
          icon={<CheckCircle2 className="w-5 h-5" />}
          label="已完成"
          value={stats.completedSessions}
          color="bg-emerald-50 text-emerald-600 border-emerald-100"
        />
        <StatCard
          icon={<Clock className="w-5 h-5" />}
          label="进行中"
          value={stats.inProgressSessions}
          color="bg-amber-50 text-amber-600 border-amber-100"
        />
        <StatCard
          icon={<Award className="w-5 h-5" />}
          label="平均分"
          value={stats.averageScore}
          suffix="分"
          color="bg-purple-50 text-purple-600 border-purple-100"
        />
        <StatCard
          icon={<TrendingUp className="w-5 h-5" />}
          label="通过率"
          value={stats.passRate}
          suffix="%"
          color="bg-teal-50 text-teal-600 border-teal-100"
        />
      </div>

      {/* 方向分布 + 状态分布 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 方向分布 */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <BarChart3 className="w-4 h-4 text-primary" />
            <h3 className="text-sm font-bold text-slate-800">方向分布</h3>
          </div>
          {stats.directionStats.length === 0 ? (
            <p className="text-xs text-slate-400 py-4 text-center">暂无数据</p>
          ) : (
            <div className="space-y-2.5">
              {stats.directionStats.map((item, i) => {
                const maxCount = Math.max(...stats.directionStats.map(d => d.count), 1);
                const pct = Math.round((item.count / maxCount) * 100);
                return (
                  <div key={i} className="flex items-center gap-3">
                    <span className="text-xs text-slate-600 w-24 truncate font-medium">{item.direction}</span>
                    <div className="flex-1 bg-slate-100 rounded-full h-2.5 overflow-hidden">
                      <div
                        className="h-full rounded-full bg-primary transition-all duration-500"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <span className="text-xs font-bold text-slate-700 w-8 text-right">{item.count}</span>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 状态分布 */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <PieChart className="w-4 h-4 text-primary" />
            <h3 className="text-sm font-bold text-slate-800">状态分布</h3>
          </div>
          {stats.statusStats.length === 0 ? (
            <p className="text-xs text-slate-400 py-4 text-center">暂无数据</p>
          ) : (
            <div className="flex flex-wrap gap-2.5">
              {stats.statusStats.map((item, i) => (
                <span
                  key={i}
                  className={`text-xs font-semibold px-3.5 py-2 rounded-full border ${statusColors[item.status] || "bg-slate-100 text-slate-600"}`}
                >
                  {statusLabels[item.status] || item.status}：{item.count}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 每日趋势 */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
        <div className="flex items-center gap-2 mb-4">
          <Calendar className="w-4 h-4 text-primary" />
          <h3 className="text-sm font-bold text-slate-800">每日面试趋势</h3>
        </div>
        {stats.dailyStats.length === 0 ? (
          <p className="text-xs text-slate-400 py-4 text-center">暂无数据</p>
        ) : (
          <div className="flex items-end gap-2 h-32">
            {stats.dailyStats.map((item, i) => {
              const maxCount = Math.max(...stats.dailyStats.map(d => d.count), 1);
              const height = Math.max((item.count / maxCount) * 100, 4);
              return (
                <div key={i} className="flex-1 flex flex-col items-center gap-1 min-w-0">
                  <span className="text-[10px] font-bold text-slate-600">{item.count}</span>
                  <div className="w-full bg-primary/80 hover:bg-primary rounded-t-md transition-all cursor-pointer"
                    style={{ height: `${height}%` }}
                    title={`${item.date}: ${item.count} 场`}
                  />
                  <span className="text-[9px] text-slate-400 truncate w-full text-center">
                    {item.date.slice(5)}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

/** 统计卡片子组件 */
function StatCard({ icon, label, value, suffix, color }: {
  icon: React.ReactNode;
  label: string;
  value: number;
  suffix?: string;
  color: string;
}) {
  return (
    <div className={`rounded-2xl border p-4 shadow-sm ${color} bg-opacity-50`}>
      <div className="flex items-center gap-2.5 mb-2">
        {icon}
        <span className="text-xs font-semibold opacity-80">{label}</span>
      </div>
      <div className="text-2xl font-black tracking-tight">
        {value}{suffix}
      </div>
    </div>
  );
}