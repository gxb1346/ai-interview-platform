import React, { useState } from "react";
import { Calendar as CalendarIcon, Clock, UserCheck, Inbox, Plus, ChevronLeft, ChevronRight, Check } from "lucide-react";
import { Interview } from "../types";

interface ScheduleViewProps {
  interviews: Interview[];
}

export default function ScheduleView({ interviews }: ScheduleViewProps) {
  const [currentMonth, setCurrentMonth] = useState("2026年6月");

  // Format month dates (June 2026 for demonstration matching metadata timestamp)
  const daysInJune = Array.from({ length: 30 }, (_, i) => i + 1);
  const startOffsetJune = 1; // June 1st, 2026 is Monday (offset grid alignment)

  // Calendar dates with active interviews
  const getDayInterviews = (dayNum: number) => {
    return interviews.filter((int) => {
      // Parse scheduled date: e.g. "2026-06-11 10:00"
      const dateParts = int.scheduledAt.split(" ")[0].split("-");
      if (dateParts.length === 3) {
        const d = parseInt(dateParts[2]);
        const m = parseInt(dateParts[1]);
        return d === dayNum && m === 6; // June
      }
      return false;
    });
  };

  return (
    <div className="space-y-6" id="schedule-calendar-container">
      {/* Title block */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-white/40 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            <CalendarIcon className="w-6 h-6 text-primary" /> 智慧校招与内部面试日程
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">
            可视化日历主舱。统一安排各业务线面试时段、锁定核心考官，并实时更新候选人邀约、面测、通关和录用状态。
          </p>
        </div>

        {/* Direction Switch Month triggers */}
        <div className="flex items-center gap-2 bg-slate-100/85 p-1 rounded-xl border border-slate-200">
          <button className="w-8 h-8 rounded-lg hover:bg-white flex items-center justify-center text-slate-600 transition cursor-pointer">
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span className="text-xs font-bold text-slate-700 font-sans px-2 bg-white rounded-lg shadow-sm py-1">
            {currentMonth}
          </span>
          <button className="w-8 h-8 rounded-lg hover:bg-white flex items-center justify-center text-slate-600 transition cursor-pointer">
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        
        {/* Calendar Monthly Matrix (Col-8) */}
        <div className="lg:col-span-8 bg-white/75 backdrop-blur-md p-6 rounded-2xl border border-white/50 shadow-sm">
          <h2 className="text-sm font-bold text-slate-800 font-sans mb-4 block">
            面试时段时空矩阵
          </h2>

          <div className="grid grid-cols-7 gap-2 text-center text-[10px] tracking-wider font-bold text-slate-400 uppercase font-sans mb-2">
            <span>一</span><span>二</span><span>三</span><span>四</span><span>五</span><span>六</span><span>日</span>
          </div>

          <div className="grid grid-cols-7 gap-2">
            {/* Blank offsets heading */}
            {Array.from({ length: startOffsetJune }).map((_, idx) => (
              <div key={`offset-${idx}`} className="h-20 bg-slate-50/30 rounded-xl" />
            ))}

            {/* Days block */}
            {daysInJune.map((day) => {
              const dayInterviews = getDayInterviews(day);
              const isToday = day === 10; // matching current seed date June 10, 2026

              return (
                <div
                  key={day}
                  className={`h-20 p-2 border rounded-xl flex flex-col justify-between transition cursor-pointer relative ${
                    isToday
                      ? "ring-2 ring-primary bg-primary/5 border-primary/20"
                      : dayInterviews.length > 0
                      ? "bg-sky-50/20 border-sky-100 hover:bg-sky-50/40"
                      : "bg-white hover:bg-slate-50 border-slate-100"
                  }`}
                >
                  <span className={`text-[11px] font-black font-mono block w-max h-max rounded-full p-1 leading-none ${
                    isToday ? "bg-primary text-white" : "text-slate-500"
                  }`}>
                    {day}
                  </span>

                  {dayInterviews.length > 0 && (
                    <div className="space-y-1 overflow-hidden">
                      {dayInterviews.map((int) => (
                        <div
                          key={int.id}
                          className="text-[9px] font-semibold text-primary bg-primary/10 border border-primary/20 rounded px-1.5 py-0.5 line-clamp-1 w-full text-center"
                          title={`${int.candidateName}面试 ${int.role}`}
                        >
                          {int.candidateName} 面面
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Live Active Agenda Sidebar details (Col-4) */}
        <div className="lg:col-span-4 bg-gradient-to-b from-primary/5 to-transparent border border-primary/10 rounded-2xl p-6 flex flex-col justify-between space-y-6">
          <div className="space-y-4">
            <h2 className="text-xs font-extrabold text-primary block font-sans uppercase flex items-center gap-1.5">
              📅 直击本日日程 (Agenda of June 10, 2026)
            </h2>

            <div className="space-y-3.5">
              {interviews.length > 0 ? (
                interviews.map((int) => (
                  <div
                    key={int.id}
                    className="p-3.5 bg-white border border-slate-100 rounded-xl shadow-sm space-y-2"
                  >
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="font-extrabold text-slate-800 font-sans flex items-center gap-1">
                        <UserCheck className="w-3.5 h-3.5 text-primary" /> {int.candidateName}
                      </span>
                      <span className="text-[10px] text-slate-400 font-mono font-bold flex items-center gap-1">
                        <Clock className="w-3 h-3" /> {int.scheduledAt.split(" ")[1] || "10:00"}
                      </span>
                    </div>

                    <p className="text-[10px] text-slate-500 font-sans leading-relaxed">
                      应聘：<strong>{int.role}</strong><br/>
                      考题提纲已由AI生成。拟测评要项：{int.notes || "系统重构及沟通基本面考察"}
                    </p>
                  </div>
                ))
              ) : (
                <div className="text-center text-slate-400 p-8 space-y-2">
                  <Inbox className="w-8 h-8 mx-auto" />
                  <p className="text-xs font-semibold font-sans">本日暂无安排安排的面试任务。</p>
                </div>
              )}
            </div>
          </div>

          <div className="bg-primary/5 p-4 rounded-xl border border-primary/10 text-[10px] text-slate-500 leading-relaxed font-sans space-y-1.5">
            <strong className="text-primary block font-sans">🧠 招聘飞轮提示</strong>
            建议优先完成**李明**一轮全栈性能探底模拟。点击‘面试协调中心’可实时深挖和增加候选人在并发或缓存场景的技术提问纲要。
          </div>
        </div>
      </div>
    </div>
  );
}
