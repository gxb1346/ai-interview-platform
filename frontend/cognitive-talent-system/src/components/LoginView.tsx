import React, { useState } from "react";
import { BrainCircuit, Loader2, Eye, EyeOff, CheckCircle2, AlertCircle } from "lucide-react";
import { authApi, setToken, setRefreshToken, setStoredUser } from "../api";

interface LoginViewProps {
  onLoginSuccess: () => void;
}

export default function LoginView({ onLoginSuccess }: LoginViewProps) {
  const [tab, setTab] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccessMsg("");

    if (!username.trim()) { setError("请输入用户名"); return; }
    if (password.length < 6) { setError("密码至少 6 位"); return; }

    setLoading(true);
    try {
      if (tab === "login") {
        const res = await authApi.login(username.trim(), password);
        setToken(res.token);
        if (res.refreshToken) setRefreshToken(res.refreshToken);
        setStoredUser({ username: res.username, displayName: res.displayName || res.username });
        onLoginSuccess();
      } else {
        await authApi.register(username.trim(), password, displayName.trim() || undefined);
        setSuccessMsg("注册成功！请登录");
        setTab("login");
        setPassword("");
      }
    } catch (err: any) {
      if (err?.message) {
        setError(err.message);
      } else {
        try {
          const body = await (err as Response)?.json?.();
          setError(body?.message || body?.error || "请求失败，请检查网络或后端服务");
        } catch {
          setError("请求失败，请检查网络或后端服务");
        }
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F5F7FA] flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8 animate-fade-in">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary rounded-2xl shadow-lg shadow-primary/25 mb-4">
            <BrainCircuit className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-extrabold text-slate-800 tracking-tight">RecruitAI</h1>
          <p className="text-sm text-slate-400 mt-1 font-medium">智能招聘套件 · 企业版</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8 animate-fade-in">
          {/* 标签切换 */}
          <div className="flex bg-slate-100 rounded-xl p-1 mb-6">
            <button
              onClick={() => { setTab("login"); setError(""); setSuccessMsg(""); }}
              className={`flex-1 py-2 text-sm font-bold rounded-lg transition cursor-pointer ${
                tab === "login" ? "bg-white text-primary shadow-sm" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              登录
            </button>
            <button
              onClick={() => { setTab("register"); setError(""); setSuccessMsg(""); }}
              className={`flex-1 py-2 text-sm font-bold rounded-lg transition cursor-pointer ${
                tab === "register" ? "bg-white text-primary shadow-sm" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              注册
            </button>
          </div>

          {/* 成功消息 */}
          {successMsg && (
            <div className="flex items-center gap-2 text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3 mb-4">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              {successMsg}
            </div>
          )}

          {/* 错误消息 */}
          {error && (
            <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-xl px-4 py-3 mb-4">
              <AlertCircle className="w-4 h-4 shrink-0" />
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* 用户名 */}
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">用户名</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="请输入用户名"
                autoFocus
                className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition placeholder:text-slate-300"
              />
            </div>

            {/* 显示名称（注册时） */}
            {tab === "register" && (
              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1.5">显示名称（选填）</label>
                <input
                  type="text"
                  value={displayName}
                  onChange={e => setDisplayName(e.target.value)}
                  placeholder="你的名字"
                  className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition placeholder:text-slate-300"
                />
              </div>
            )}

            {/* 密码 */}
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">密码</label>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder={tab === "register" ? "至少 6 位密码" : "请输入密码"}
                  className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 pr-11 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition placeholder:text-slate-300"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* 提交按钮 */}
            <button
              type="submit"
              disabled={loading}
              className="w-full text-sm font-bold text-white bg-primary hover:bg-primary-dark rounded-xl px-4 py-3 transition cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              {loading ? "处理中..." : tab === "login" ? "登 录" : "注 册"}
            </button>
          </form>

          {/* 底部提示 */}
          <p className="text-[11px] text-slate-400 text-center mt-6 leading-relaxed">
            {tab === "login" ? (
              <>还没有账号？<button onClick={() => { setTab("register"); setError(""); }} className="text-primary font-bold hover:underline cursor-pointer">立即注册</button></>
            ) : (
              <>已有账号？<button onClick={() => { setTab("login"); setError(""); }} className="text-primary font-bold hover:underline cursor-pointer">去登录</button></>
            )}
          </p>
        </div>

        <p className="text-[10px] text-slate-300 text-center mt-6">
          &copy; 2026 RecruitAI. All rights reserved.
        </p>
      </div>
    </div>
  );
}