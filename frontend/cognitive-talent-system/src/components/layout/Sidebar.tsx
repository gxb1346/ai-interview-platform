import {
  LayoutDashboard, FileText, FileSearch, Users, Calendar,
  Mic, MessageSquare, History, BookOpen, HelpCircle,
  Settings, LogOut, Cpu, PanelLeftClose, PanelLeft
} from "lucide-react";
import { useState } from "react";

interface SidebarProps {
  currentView: string;
  onNavigate: (view: string) => void;
  onLogout: () => void;
}

const NAV_ITEMS = [
  { id: "DASHBOARD", label: "仪表盘", icon: LayoutDashboard },
  { id: "RESUME_ANALYSIS", label: "简历分析", icon: FileSearch },
  { id: "RESUME_MANAGE", label: "简历管理", icon: FileText },
  { id: "TALENT_POOL", label: "人才库", icon: Users },
  { id: "INTERVIEW_CENTER", label: "面试中心", icon: Calendar },
  { id: "MOCK_INTERVIEW", label: "模拟面试", icon: MessageSquare },
  { id: "VOICE_INTERVIEW", label: "语音面试", icon: Mic, isNew: true },
  { id: "INTERVIEW_RECORDS", label: "面试记录", icon: History },
  { id: "SCHEDULE", label: "面试日程", icon: Calendar, isNew: true },
  { id: "LLM_PROVIDER", label: "AI 配置", icon: Cpu, isNew: true },
  { id: "KNOWLEDGE_BASE", label: "知识库", icon: BookOpen },
  { id: "KNOWLEDGE_QA", label: "知识问答", icon: HelpCircle },
  { id: "SETTINGS", label: "设置", icon: Settings },
];

export function Sidebar({ currentView, onNavigate, onLogout }: SidebarProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={`${
        collapsed ? "w-16" : "w-56"
      } bg-white border-r border-slate-200 flex flex-col transition-all duration-200 shrink-0`}
    >
      {/* Logo */}
      <div className="h-14 flex items-center gap-3 px-4 border-b border-slate-100">
        <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center shrink-0">
          <span className="text-white text-xs font-black">AI</span>
        </div>
        {!collapsed && (
          <span className="text-sm font-extrabold text-slate-800">RecruitAI</span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-3 px-2 space-y-0.5">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = currentView === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ${
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-slate-600 hover:bg-slate-50 hover:text-slate-800"
              }`}
              title={collapsed ? item.label : undefined}
            >
              <Icon className="w-4.5 h-4.5 shrink-0" />
              {!collapsed && (
                <span className="flex-1 text-left truncate">{item.label}</span>
              )}
              {!collapsed && item.isNew && (
                <span className="text-[10px] bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded-full font-bold">
                  NEW
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="border-t border-slate-100 p-2 space-y-1">
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-400 hover:bg-slate-50 hover:text-slate-600 transition cursor-pointer"
        >
          {collapsed ? <PanelLeft className="w-4 h-4" /> : <PanelLeftClose className="w-4 h-4" />}
        </button>
        <button
          onClick={onLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-red-500 hover:bg-red-50 transition cursor-pointer"
          title={collapsed ? "退出登录" : undefined}
        >
          <LogOut className="w-4 h-4 shrink-0" />
          {!collapsed && "退出登录"}
        </button>
      </div>
    </aside>
  );
}
