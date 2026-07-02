import { Bell } from "lucide-react";

interface HeaderProps {
  title: string;
  userName?: string;
}

export function Header({ title, userName }: HeaderProps) {
  return (
    <header className="h-14 bg-white border-b border-slate-200 flex items-center justify-between px-6 shrink-0">
      <h2 className="text-base font-extrabold text-slate-800 tracking-tight">
        {title}
      </h2>

      <div className="flex items-center gap-3">
        <div className="relative">
          <div className="absolute top-1 right-1 w-2 h-2 rounded-full bg-red-500 animate-pulse border-2 border-white" />
          <button className="w-9 h-9 rounded-xl border border-slate-200 bg-white flex items-center justify-center hover:bg-slate-50 cursor-pointer">
            <Bell className="w-4 h-4 text-slate-500" />
          </button>
        </div>

        <div className="w-px h-6 bg-slate-200" />

        <div className="text-right hidden md:block">
          <span className="text-[10px] text-slate-400 block font-semibold uppercase tracking-wider">
            AI 招聘协作
          </span>
          <span className="text-xs font-bold text-slate-700">
            {userName || "用户"}
          </span>
        </div>
      </div>
    </header>
  );
}
