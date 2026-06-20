import React, { useState, useMemo, useCallback } from "react";
import { Calendar as CalendarIcon, Clock, Inbox, Plus, ChevronLeft, ChevronRight, X, List, Grid3X3, Trash } from "lucide-react";
import { Calendar, dayjsLocalizer, SlotInfo } from "react-big-calendar";
import dayjs from "dayjs";
import "react-big-calendar/lib/css/react-big-calendar.css";
import { Interview } from "../types";

const localizer = dayjsLocalizer(dayjs);

type ViewMode = "month" | "week" | "day" | "list";

interface ScheduleViewProps {
  interviews: Interview[];
  onAddInterview: (int: Interview) => void;
  onRemoveInterview: (id: string) => void;
  onReschedule: (id: string, newDate: string) => void;
  onStatusChange: (id: string, status: "pending" | "completed" | "cancelled") => void;
}

const STATUS_STYLE: Record<string, { bg: string; text: string; border: string; label: string }> = {
  pending:    { bg: "bg-amber-50", text: "text-amber-700", border: "border-amber-200", label: "待面试" },
  completed:  { bg: "bg-emerald-50", text: "text-emerald-700", border: "border-emerald-200", label: "已完成" },
  cancelled:  { bg: "bg-slate-100", text: "text-slate-500", border: "border-slate-200", label: "已取消" },
};

function toCalendarEvent(int: Interview) {
  const dt = dayjs(int.scheduledAt);
  return {
    id: int.id,
    title: `${int.candidateName} - ${int.role}`,
    start: dt.toDate(),
    end: dt.add(1, "hour").toDate(),
    resource: int,
  };
}

export default function ScheduleView({
  interviews, onAddInterview, onRemoveInterview,
  onReschedule, onStatusChange,
}: ScheduleViewProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("month");
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedEvent, setSelectedEvent] = useState<Interview | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [addSlot, setAddSlot] = useState<Date | null>(null);
  const [formName, setFormName] = useState("");
  const [formRole, setFormRole] = useState("");
  const [formNotes, setFormNotes] = useState("");

  const calendarEvents = useMemo(() => interviews.map(toCalendarEvent), [interviews]);

  const eventStyleGetter = useCallback((event: { resource: Interview }) => {
    const status = (event.resource as Interview).status;
    return {
      className: `rounded-lg border-l-4 text-xs font-semibold`,
      style: {
        borderLeftColor: status === "pending" ? "#f59e0b" : status === "completed" ? "#10b981" : "#94a3b8",
        backgroundColor: status === "pending" ? "#fffbeb" : status === "completed" ? "#ecfdf5" : "#f1f5f9",
        color: status === "pending" ? "#b45309" : status === "completed" ? "#047857" : "#64748b",
      },
    };
  }, []);

  const handleSelectEvent = useCallback((event: { resource: Interview }) => {
    setSelectedEvent(event.resource as Interview);
  }, []);

  const handleSelectSlot = useCallback((slotInfo: SlotInfo) => {
    setAddSlot(slotInfo.start);
    setShowAddModal(true);
  }, []);

  const handleEventDrop = useCallback(({ event, start }: { event: { id: string }; start: Date }) => {
    const formatted = dayjs(start).format("YYYY-MM-DD HH:mm");
    onReschedule(event.id as string, formatted);
  }, [onReschedule]);

  const handleAddSubmit = () => {
    if (!formName.trim() || !formRole.trim()) return;
    const newInt: Interview = {
      id: "int_" + Date.now(),
      candidateId: "cand_" + Date.now(),
      candidateName: formName.trim(),
      role: formRole.trim(),
      scheduledAt: addSlot ? dayjs(addSlot).format("YYYY-MM-DD HH:mm") : dayjs().format("YYYY-MM-DD HH:mm"),
      status: "pending",
      suggestedQuestions: [],
      notes: formNotes.trim() || undefined,
    };
    onAddInterview(newInt);
    setShowAddModal(false);
    setFormName("");
    setFormRole("");
    setFormNotes("");
  };

  const cycleStatus = (int: Interview) => {
    const next: Record<string, "pending" | "completed" | "cancelled"> = {
      pending: "completed",
      completed: "cancelled",
      cancelled: "pending",
    };
    onStatusChange(int.id, next[int.status]);
  };

  const navigate = (dir: "prev" | "next" | "today") => {
    if (dir === "today") { setCurrentDate(new Date()); return; }
    const unit = viewMode === "month" ? "month" : viewMode === "week" ? "week" : "day";
    setCurrentDate(dayjs(currentDate)[dir === "prev" ? "subtract" : "add"](1, unit).toDate());
  };

  const renderListView = () => {
    const sorted = [...interviews].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
    const grouped: Record<string, Interview[]> = {};
    sorted.forEach(int => {
      const dateKey = int.scheduledAt.split(" ")[0];
      if (!grouped[dateKey]) grouped[dateKey] = [];
      grouped[dateKey].push(int);
    });

    return (
      <div className="space-y-6 overflow-y-auto max-h-[600px] pr-2">
        {Object.entries(grouped).length === 0 ? (
          <div className="text-center py-12 text-slate-400"><Inbox className="w-10 h-10 mx-auto mb-2" />暂无面试安排</div>
        ) : Object.entries(grouped).map(([date, items]) => (
          <div key={date}>
            <h3 className="text-xs font-bold text-slate-500 mb-2 sticky top-0 bg-white/90 py-1 z-10">{date}</h3>
            <div className="space-y-2">
              {items.map(int => {
                const st = STATUS_STYLE[int.status];
                return (
                  <div key={int.id}
                    className={`flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition hover:shadow-sm ${st.bg} ${st.border}`}
                    onClick={() => setSelectedEvent(int)}>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-slate-800">{int.candidateName}</span>
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-semibold ${st.text} ${st.bg} ${st.border}`}>{st.label}</span>
                      </div>
                      <p className="text-[10px] text-slate-500 mt-0.5">{int.role} · {int.scheduledAt.split(" ")[1]}</p>
                    </div>
                    <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="space-y-6" id="schedule-calendar-container">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            <CalendarIcon className="w-6 h-6 text-primary" /> 面试日程管理
          </h1>
          <p className="text-sm text-slate-500 font-sans mt-0.5">日/周/月视图 · 拖拽调整时间 · 状态流转管理</p>
        </div>
        <div className="flex bg-slate-100 rounded-xl p-1 gap-1">
          {(["month", "week", "day", "list"] as ViewMode[]).map(v => (
            <button key={v} onClick={() => setViewMode(v)}
              className={`text-[10px] font-semibold px-3 py-1.5 rounded-lg transition cursor-pointer ${
                viewMode === v ? "bg-white text-primary shadow-sm" : "text-slate-500 hover:text-slate-700"
              }`}>
              {v === "month" ? <><Grid3X3 className="w-3 h-3 inline mr-1" />月</>
                : v === "week" ? "周" : v === "day" ? "日" : <><List className="w-3 h-3 inline mr-1" />列表</>}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-8 bg-white/75 backdrop-blur-md p-4 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <button onClick={() => navigate("prev")}
                className="w-8 h-8 rounded-lg hover:bg-slate-100 flex items-center justify-center transition cursor-pointer">
                <ChevronLeft className="w-4 h-4 text-slate-600" />
              </button>
              <h2 className="text-sm font-bold text-slate-800 min-w-[140px] text-center">
                {viewMode === "month"
                  ? dayjs(currentDate).format("YYYY年M月")
                  : dayjs(currentDate).format("YYYY年M月D日 ddd")}
              </h2>
              <button onClick={() => navigate("next")}
                className="w-8 h-8 rounded-lg hover:bg-slate-100 flex items-center justify-center transition cursor-pointer">
                <ChevronRight className="w-4 h-4 text-slate-600" />
              </button>
            </div>
            <div className="flex items-center gap-3">
              <button onClick={() => navigate("today")}
                className="text-[10px] font-semibold text-primary bg-primary/10 hover:bg-primary/20 px-3 py-1.5 rounded-lg transition cursor-pointer">今天</button>
              <button onClick={() => { setAddSlot(new Date()); setShowAddModal(true); }}
                className="text-[10px] font-semibold text-white bg-primary hover:bg-primary-container px-3 py-1.5 rounded-lg transition cursor-pointer flex items-center gap-1">
                <Plus className="w-3 h-3" /> 新建面试
              </button>
            </div>
          </div>

          {viewMode === "list" ? renderListView() : (
            <div className="calendar-container" style={{ height: viewMode === "month" ? 500 : 600 }}>
              <Calendar
                localizer={localizer}
                events={calendarEvents}
                startAccessor="start"
                endAccessor="end"
                view={viewMode as ViewMode}
                date={currentDate}
                onNavigate={d => setCurrentDate(d)}
                onView={v => setViewMode(v as ViewMode)}
                onSelectEvent={handleSelectEvent}
                onSelectSlot={handleSelectSlot}
                selectable
                resizable
                onEventDrop={handleEventDrop}
                eventPropGetter={eventStyleGetter}
                views={["month", "week", "day"]}
                step={60}
                timeslots={1}
                popup
                className="rounded-xl"
              />
            </div>
          )}
        </div>

        <div className="lg:col-span-4 space-y-4">
          {selectedEvent ? (
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-800">面试详情</h3>
                <button onClick={() => setSelectedEvent(null)}
                  className="text-slate-300 hover:text-slate-600 cursor-pointer"><X className="w-4 h-4" /></button>
              </div>
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-sm">
                    {selectedEvent.candidateName.charAt(0)}
                  </div>
                  <div>
                    <p className="text-sm font-bold text-slate-800">{selectedEvent.candidateName}</p>
                    <p className="text-[10px] text-slate-500">{selectedEvent.role}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2 text-xs text-slate-600">
                  <Clock className="w-3.5 h-3.5 text-slate-400" /> {selectedEvent.scheduledAt}
                </div>
                {selectedEvent.notes && (
                  <p className="text-xs text-slate-500 bg-slate-50 p-3 rounded-xl border border-slate-100">{selectedEvent.notes}</p>
                )}
                <div className="flex items-center gap-2">
                  <span className="text-[10px] text-slate-400">状态：</span>
                  <button onClick={() => cycleStatus(selectedEvent)}
                    className={`text-[10px] font-semibold px-3 py-1 rounded-full border cursor-pointer transition
                      ${STATUS_STYLE[selectedEvent.status].bg}
                      ${STATUS_STYLE[selectedEvent.status].text}
                      ${STATUS_STYLE[selectedEvent.status].border}`}
                    title="点击切换状态">
                    {STATUS_STYLE[selectedEvent.status].label} ↻
                  </button>
                </div>
                {selectedEvent.suggestedQuestions.length > 0 && (
                  <div className="space-y-1.5">
                    <span className="text-[10px] font-bold text-slate-400 uppercase">面试提纲</span>
                    <div className="space-y-1 max-h-28 overflow-y-auto">
                      {selectedEvent.suggestedQuestions.map((q, i) => (
                        <p key={i} className="text-[10px] text-slate-600 bg-slate-50 p-2 rounded-lg border border-slate-100">{i + 1}. {q}</p>
                      ))}
                    </div>
                  </div>
                )}
              </div>
              <div className="flex gap-2 pt-2 border-t border-slate-100">
                <button onClick={() => cycleStatus(selectedEvent)}
                  className="text-[10px] font-semibold text-white bg-primary hover:bg-primary-container px-3 py-1.5 rounded-lg transition cursor-pointer flex-1">
                  {selectedEvent.status === "pending" ? "标记已完成" : selectedEvent.status === "completed" ? "标记已取消" : "恢复待面试"}
                </button>
                <button onClick={() => { onRemoveInterview(selectedEvent.id); setSelectedEvent(null); }}
                  className="text-[10px] font-semibold text-red-500 hover:bg-red-50 px-3 py-1.5 rounded-lg transition cursor-pointer">
                  <Trash className="w-3 h-3" />
                </button>
              </div>
            </div>
          ) : (
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
              <h3 className="text-xs font-bold text-slate-400 uppercase">面试统计</h3>
              <div className="grid grid-cols-3 gap-3">
                {(["pending", "completed", "cancelled"] as const).map(st => {
                  const count = interviews.filter(i => i.status === st).length;
                  const s = STATUS_STYLE[st];
                  return (
                    <div key={st} className={`text-center p-3 rounded-xl border ${s.bg} ${s.border}`}>
                      <div className={`text-lg font-black ${s.text}`}>{count}</div>
                      <div className={`text-[9px] font-semibold ${s.text}`}>{s.label}</div>
                    </div>
                  );
                })}
              </div>
              <div className="text-center text-[10px] text-slate-400">共 {interviews.length} 场面试 · 点击日历事件查看详情</div>
            </div>
          )}

          <div className="bg-gradient-to-b from-primary/5 to-transparent border border-primary/10 rounded-2xl p-4 text-[10px] text-slate-500 leading-relaxed space-y-1.5">
            <strong className="text-primary block text-xs">💡 操作提示</strong>
            · <strong>点击空白</strong> 新建面试<br />
            · <strong>拖拽事件</strong> 调整时间<br />
            · <strong>点击事件</strong> 查看/修改状态<br />
            · <strong>状态循环</strong>：待面试 → 已完成 → 已取消 → 待面试
          </div>
        </div>
      </div>

      {showAddModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 backdrop-blur-sm">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-xl p-6 mx-4 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">新建面试安排</h3>
              <button onClick={() => setShowAddModal(false)} className="text-slate-300 hover:text-slate-600 cursor-pointer"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="text-[10px] font-semibold text-slate-500 block mb-1">候选人姓名</label>
                <input value={formName} onChange={e => setFormName(e.target.value)}
                  placeholder="例如：张三"
                  className="w-full text-xs py-2.5 px-3 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition" />
              </div>
              <div>
                <label className="text-[10px] font-semibold text-slate-500 block mb-1">应聘岗位</label>
                <input value={formRole} onChange={e => setFormRole(e.target.value)}
                  placeholder="例如：前端架构师"
                  className="w-full text-xs py-2.5 px-3 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition" />
              </div>
              <div>
                <label className="text-[10px] font-semibold text-slate-500 block mb-1">面试时间</label>
                <input type="datetime-local"
                  defaultValue={addSlot ? dayjs(addSlot).format("YYYY-MM-DDTHH:mm") : ""}
                  onChange={e => setAddSlot(new Date(e.target.value))}
                  className="w-full text-xs py-2.5 px-3 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition" />
              </div>
              <div>
                <label className="text-[10px] font-semibold text-slate-500 block mb-1">备注 <span className="text-slate-300">(选填)</span></label>
                <textarea value={formNotes} onChange={e => setFormNotes(e.target.value)}
                  placeholder="面试要点、考察方向等" rows={2}
                  className="w-full text-xs py-2.5 px-3 bg-slate-50 rounded-xl border border-slate-200 focus:border-primary outline-none transition resize-none" />
              </div>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setShowAddModal(false)}
                className="flex-1 text-xs font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 py-2.5 rounded-xl transition cursor-pointer">取消</button>
              <button onClick={handleAddSubmit} disabled={!formName.trim() || !formRole.trim()}
                className="flex-1 text-xs font-semibold text-white bg-primary hover:bg-primary-container disabled:bg-slate-300 py-2.5 rounded-xl transition cursor-pointer">创建面试</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
