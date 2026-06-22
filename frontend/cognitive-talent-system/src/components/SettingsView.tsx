import React, { useState, useEffect } from "react";
import { User, Lock, Loader2, CheckCircle2, AlertCircle, Shield } from "lucide-react";
import { authApi, getStoredUser } from "../api";
import type { UserProfile } from "../types";

export default function SettingsView() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  // 个人信息表单
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileMsg, setProfileMsg] = useState("");
  const [profileError, setProfileError] = useState("");

  // 密码表单
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [pwdLoading, setPwdLoading] = useState(false);
  const [pwdMsg, setPwdMsg] = useState("");
  const [pwdError, setPwdError] = useState("");

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    setLoading(true);
    try {
      const data = await authApi.me();
      setProfile(data);
      setDisplayName(data.displayName || "");
      setEmail(data.email || "");
    } catch {
      const stored = getStoredUser();
      if (stored) {
        setProfile({ username: stored.username, displayName: stored.displayName, email: "", role: "", userId: 0 });
        setDisplayName(stored.displayName || "");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileMsg("");
    setProfileError("");
    if (!displayName.trim()) {
      setProfileError("显示名称不能为空");
      return;
    }
    setProfileLoading(true);
    try {
      await authApi.updateProfile(displayName.trim(), email.trim());
      setProfileMsg("个人信息更新成功");
      setProfile(prev => prev ? { ...prev, displayName: displayName.trim(), email: email.trim() } : prev);
    } catch (err: any) {
      setProfileError(err?.message || "更新失败");
    } finally {
      setProfileLoading(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwdMsg("");
    setPwdError("");
    if (!oldPassword) { setPwdError("请输入原密码"); return; }
    if (newPassword.length < 6) { setPwdError("新密码至少 6 位"); return; }
    if (newPassword !== confirmPassword) { setPwdError("两次输入的新密码不一致"); return; }
    if (oldPassword === newPassword) { setPwdError("新密码不能与原密码相同"); return; }

    setPwdLoading(true);
    try {
      await authApi.changePassword(oldPassword, newPassword);
      setPwdMsg("密码修改成功");
      setOldPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err: any) {
      setPwdError(err?.message || "密码修改失败");
    } finally {
      setPwdLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-2xl">
      {/* 头部 */}
      <div>
        <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight">账号设置</h1>
        <p className="text-sm text-slate-500 font-sans mt-0.5">管理您的个人信息和账号安全</p>
      </div>

      {/* 个人信息卡片 */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
        <div className="flex items-center gap-2 mb-5">
          <User className="w-4 h-4 text-primary" />
          <h3 className="text-sm font-bold text-slate-800">个人信息</h3>
        </div>

        {profileMsg && (
          <div className="flex items-center gap-2 text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3 mb-4">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            {profileMsg}
          </div>
        )}
        {profileError && (
          <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-xl px-4 py-3 mb-4">
            <AlertCircle className="w-4 h-4 shrink-0" />
            {profileError}
          </div>
        )}

        <form onSubmit={handleUpdateProfile} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">用户名</label>
            <input
              type="text"
              value={profile?.username || ""}
              disabled
              className="w-full text-sm bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-400 cursor-not-allowed"
            />
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">显示名称</label>
            <input
              type="text"
              value={displayName}
              onChange={e => setDisplayName(e.target.value)}
              placeholder="请输入显示名称"
              className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition"
            />
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">邮箱</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="请输入邮箱地址"
              className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition"
            />
          </div>
          <button
            type="submit"
            disabled={profileLoading}
            className="text-sm font-bold text-white bg-primary hover:bg-primary-dark rounded-xl px-6 py-3 transition cursor-pointer disabled:opacity-50 flex items-center gap-2"
          >
            {profileLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
            {profileLoading ? "保存中..." : "保存修改"}
          </button>
        </form>
      </div>

      {/* 修改密码卡片 */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm">
        <div className="flex items-center gap-2 mb-5">
          <Lock className="w-4 h-4 text-primary" />
          <h3 className="text-sm font-bold text-slate-800">修改密码</h3>
        </div>

        {pwdMsg && (
          <div className="flex items-center gap-2 text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3 mb-4">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            {pwdMsg}
          </div>
        )}
        {pwdError && (
          <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-xl px-4 py-3 mb-4">
            <AlertCircle className="w-4 h-4 shrink-0" />
            {pwdError}
          </div>
        )}

        <form onSubmit={handleChangePassword} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">原密码</label>
            <input
              type="password"
              value={oldPassword}
              onChange={e => setOldPassword(e.target.value)}
              placeholder="请输入原密码"
              className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition"
            />
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">新密码</label>
            <input
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder="至少 6 位新密码"
              className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition"
            />
          </div>
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1.5">确认新密码</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder="再次输入新密码"
              className="w-full text-sm bg-white border border-slate-200 rounded-xl px-4 py-3 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition"
            />
          </div>
          <button
            type="submit"
            disabled={pwdLoading}
            className="text-sm font-bold text-white bg-primary hover:bg-primary-dark rounded-xl px-6 py-3 transition cursor-pointer disabled:opacity-50 flex items-center gap-2"
          >
            {pwdLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
            {pwdLoading ? "修改中..." : "修改密码"}
          </button>
        </form>
      </div>
    </div>
  );
}