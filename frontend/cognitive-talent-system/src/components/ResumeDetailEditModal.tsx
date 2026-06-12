/**
 * 简历详情/编辑弹窗
 * 支持查看 AI 分析结果，手动修正后提交后端
 */
import React, { useState } from "react";
import {
  X, Sparkles, CheckCircle2, AlertCircle, Save, FileText,
  BarChart3, Mail, Phone, GraduationCap, Briefcase, Building
} from "lucide-react";
import { ResumeVO, ResumeUpdateDTO, ApiResult } from "../types";

const API_BASE = "http://localhost:8082";

interface Props {
  resume: ResumeVO;
  onClose: () => void;
  onSuccess: () => void;
}

export default function ResumeDetailEditModal({ resume, onClose, onSuccess }: Props) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 编辑表单状态
  const [form, setForm] = useState<ResumeUpdateDTO>({
    candidateName: resume.candidateName || "",
    candidateRole: resume.candidateRole || "",
    experienceYears: resume.experienceYears || undefined,
    education: resume.education || "",
    email: resume.email || "",
    phone: resume.phone || "",
    matchScore: resume.matchScore || undefined,
    aiSummary: resume.aiSummary || "",
    competencies: resume.competencies || undefined,
    strengths: resume.strengths || undefined,
    weaknesses: resume.weaknesses || undefined,
    highlights: resume.highlights || undefined,
  });

  const handleChange = (field: string, value: any) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  // 修改能力评分
  const handleCompetencyChange = (key: string, value: number) => {
    setForm((prev) => ({
      ...prev,
      competencies: { ...(prev.competencies || {}), [key]: value },
    }));
  };

  // 修改数组字段（strengths/weaknesses/highlights）
  const handleArrayChange = (field: string, index: number, value: string) => {
    setForm((prev) => {
      const arr = [...(prev[field as keyof ResumeUpdateDTO] as string[] || [])];
      arr[index] = value;
      return { ...prev, [field]: arr };
    });
  };

  const addArrayItem = (field: string) => {
    setForm((prev) => {
      const arr = [...(prev[field as keyof ResumeUpdateDTO] as string[] || []), ""];
      return { ...prev, [field]: arr };
    });
  };

  const removeArrayItem = (field: string, index: number) => {
    setForm((prev) => {
      const arr = [...(prev[field as keyof ResumeUpdateDTO] as string[] || [])];
      arr.splice(index, 1);
      return { ...prev, [field]: arr };
    });
  };

  // 保存
  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/resume/${resume.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      const json: ApiResult<ResumeVO> = await res.json();
      if (json.code === 200) {
        onSuccess();
      } else {
        setError(json.message || "保存失败");
      }
    } catch (err: any) {
      setError("保存失败: " + err.message);
    } finally {
      setSaving(false);
    }
  };

  // 雷达图渲染（与 ResumeAnalysisView 一致）
  const renderChart = (comp: Record<string, number> | null | undefined) => {
    if (!comp) return <div className="text-xs text-slate-400">暂无数据</div>;
    const center = 80;
    const r = 55;
    const labels = ["技术深度", "沟通表达", "解决问题", "团队契合", "自驱动力"];
    const keys = ["technical", "communication", "problemSolving", "teamFit", "drive"];
    const angles = keys.map((_, i) => -Math.PI / 2 + (2 * Math.PI * i) / 5);

    const getPoints = (scale: number) =>
      angles.map((a) => `${center + r * scale * Math.cos(a)},${center + r * scale * Math.sin(a)}`).join(" ");

    const scorePoints = angles.map((a, i) => {
      const s = (comp[keys[i]] || 8) / 10;
      return `${center + r * s * Math.cos(a)},${center + r * s * Math.sin(a)}`;
    }).join(" ");

    return (
      <svg width="180" height="180" className="mx-auto">
        {angles.map((a, i) => (
          <line key={i} x1={center} y1={center} x2={center + r * Math.cos(a)} y2={center + r * Math.sin(a)}
            strokeWidth="1" stroke="#d1d5db" strokeDasharray="3,3" />
        ))}
        {[0.2, 0.4, 0.6, 0.8, 1.0].map((scale, i) => (
          <polygon key={i} points={getPoints(scale)} fill="none" stroke="#e5e7eb" strokeWidth="1" />
        ))}
        <polygon points={scorePoints} fill="rgba(0, 88, 190, 0.15)" stroke="#0058be" strokeWidth="2" />
        {angles.map((a, i) => (
          <text key={i} x={center + (r + 16) * Math.cos(a)} y={center + (r + 16) * Math.sin(a) + 3}
            fill="#64748b" fontSize="9" textAnchor="middle" fontWeight="500">
            {labels[i]}
          </text>
        ))}
      </svg>
    );
  };

  // 编辑模式下的能力评分输入
  const renderCompetencyInputs = () => {
    if (!editing) return null;
    const keys = ["technical", "communication", "problemSolving", "teamFit", "drive"];
    const labels = ["技术深度", "沟通表达", "解决问题", "团队契合", "自驱动力"];
    return (
      <div className="grid grid-cols-5 gap-2 mt-2">
        {keys.map((key, i) => (
          <div key={key} className="text-center">
            <div className="text-[9px] text-slate-500 mb-1">{labels[i]}</div>
            <input type="number" min={1} max={10} value={form.competencies?.[key] ?? 8}
              onChange={(e) => handleCompetencyChange(key, Number(e.target.value))}
              className="w-full text-xs text-center py-1.5 bg-white rounded-lg border border-slate-200 outline-none focus:border-primary" />
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/30 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl shadow-xl max-w-3xl w-full max-h-[90vh] overflow-y-auto">
        {/* 头部 */}
        <div className="flex items-center justify-between p-5 border-b border-slate-200 sticky top-0 bg-white z-10 rounded-t-2xl">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-primary/10 rounded-xl flex items-center justify-center">
              <FileText className="w-4.5 h-4.5 text-primary" />
            </div>
            <div>
              <h2 className="text-sm font-bold font-sans text-slate-800">
                {editing ? "编辑简历信息" : "简历详情"}
              </h2>
              <p className="text-[10px] text-slate-400 font-sans">ID: {resume.id} · {resume.fileName}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {!editing ? (
              <button onClick={() => setEditing(true)}
                className="text-xs font-semibold py-1.5 px-3 rounded-xl bg-primary/10 text-primary hover:bg-primary/20 transition cursor-pointer">
                编辑修正
              </button>
            ) : (
              <>
                <button onClick={() => setEditing(false)}
                  className="text-xs font-semibold py-1.5 px-3 rounded-xl bg-slate-100 text-slate-600 hover:bg-slate-200 transition cursor-pointer">
                  取消
                </button>
                <button onClick={handleSave} disabled={saving}
                  className="text-xs font-semibold py-1.5 px-3 rounded-xl bg-primary text-white hover:bg-primary-container transition cursor-pointer flex items-center gap-1 disabled:opacity-50">
                  <Save className="w-3.5 h-3.5" /> {saving ? "保存中..." : "保存"}
                </button>
              </>
            )}
            <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 transition cursor-pointer">
              <X className="w-4 h-4 text-slate-400" />
            </button>
          </div>
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="mx-5 mt-3 flex items-center gap-2 p-3 bg-red-50 rounded-xl border border-red-100 text-red-600 text-xs">
            <AlertCircle className="w-4 h-4 shrink-0" /> <span>{error}</span>
            <button onClick={() => setError(null)} className="ml-auto cursor-pointer"><X className="w-3 h-3" /></button>
          </div>
        )}

        {/* 内容 */}
        <div className="p-5 space-y-6">
          {/* 基本信息 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-3">
              <InfoRow icon={<Briefcase className="w-3.5 h-3.5" />} label="候选人姓名"
                value={form.candidateName || "—"}
                editing={editing} onChange={(v) => handleChange("candidateName", v)} />
              <InfoRow icon={<Building className="w-3.5 h-3.5" />} label="目标岗位"
                value={form.candidateRole || "—"}
                editing={editing} onChange={(v) => handleChange("candidateRole", v)} />
              <InfoRow icon={<GraduationCap className="w-3.5 h-3.5" />} label="最高学历"
                value={form.education || "—"}
                editing={editing} onChange={(v) => handleChange("education", v)} />
              <InfoRow icon={<Sparkles className="w-3.5 h-3.5" />} label="工作年限"
                value={form.experienceYears ? `${form.experienceYears}年` : "—"}
                editing={editing} onChange={(v) => handleChange("experienceYears", v ? Number(v) : undefined)} />
            </div>
            <div className="space-y-3">
              <InfoRow icon={<Mail className="w-3.5 h-3.5" />} label="邮箱"
                value={form.email || "—"}
                editing={editing} onChange={(v) => handleChange("email", v)} />
              <InfoRow icon={<Phone className="w-3.5 h-3.5" />} label="电话"
                value={form.phone || "—"}
                editing={editing} onChange={(v) => handleChange("phone", v)} />
              <InfoRow icon={<BarChart3 className="w-3.5 h-3.5" />} label="匹配度"
                value={form.matchScore ? `${form.matchScore}%` : "—"}
                editing={editing} onChange={(v) => handleChange("matchScore", v ? Number(v) : undefined)} />
              <div className="text-[10px] text-slate-400 pt-1">
                <span>分析时间: {resume.analyzedAt || "—"}</span>
                <span className="ml-3">创建时间: {resume.createdAt || "—"}</span>
              </div>
            </div>
          </div>

          {/* 五维能力 */}
          <div className="bg-slate-50/60 p-4 rounded-xl border border-slate-200">
            <h3 className="text-xs font-bold text-slate-700 mb-3 font-sans">五维能力评估</h3>
            {editing ? renderCompetencyInputs() : renderChart(form.competencies)}
          </div>

          {/* AI 综合评估 */}
          <div>
            <h3 className="text-xs font-bold text-slate-700 mb-2 font-sans flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-primary" /> AI 综合评估
            </h3>
            {editing ? (
              <textarea value={form.aiSummary || ""} onChange={(e) => handleChange("aiSummary", e.target.value)}
                className="w-full text-xs p-3 bg-white rounded-xl border border-slate-200 outline-none focus:border-primary min-h-[80px] font-sans leading-relaxed" />
            ) : (
              <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 p-4 rounded-xl border border-slate-100 italic">
                “ {form.aiSummary || "暂无"} ”
              </p>
            )}
          </div>

          {/* 优势 + 劣势 + 亮点 */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <ArraySection title="核心优势" icon={<CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />}
              items={form.strengths || []} field="strengths" color="emerald"
              editing={editing} onItemChange={handleArrayChange} onAdd={addArrayItem} onRemove={removeArrayItem} />
            <ArraySection title="改善建议" icon={<AlertCircle className="w-3.5 h-3.5 text-red-500" />}
              items={form.weaknesses || []} field="weaknesses" color="red"
              editing={editing} onItemChange={handleArrayChange} onAdd={addArrayItem} onRemove={removeArrayItem} />
            <ArraySection title="闪光亮点" icon={<Sparkles className="w-3.5 h-3.5 text-amber-500" />}
              items={form.highlights || []} field="highlights" color="amber"
              editing={editing} onItemChange={handleArrayChange} onAdd={addArrayItem} onRemove={removeArrayItem} />
          </div>

          {/* 文件信息 */}
          <div className="text-[10px] text-slate-400 border-t border-slate-100 pt-4 flex items-center justify-between">
            <span>文件名: {resume.fileName}</span>
            <span>类型: {resume.fileType}</span>
            <span>大小: {resume.fileSize ? `${(resume.fileSize / 1024).toFixed(1)} KB` : "—"}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

// 单行信息组件
function InfoRow({ icon, label, value, editing, onChange }: {
  icon: React.ReactNode; label: string; value: string;
  editing: boolean; onChange: (v: string) => void;
}) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="text-slate-400 shrink-0">{icon}</span>
      <span className="text-[10px] font-semibold text-slate-500 w-16 shrink-0">{label}</span>
      {editing ? (
        <input type="text" value={value === "—" ? "" : value}
          onChange={(e) => onChange(e.target.value)}
          className="flex-1 text-xs py-1.5 px-2.5 bg-white rounded-lg border border-slate-200 outline-none focus:border-primary font-sans" />
      ) : (
        <span className="text-xs text-slate-700 font-sans">{value}</span>
      )}
    </div>
  );
}

// 数组字段组件（优势/劣势/亮点）
function ArraySection({ title, icon, items, field, color, editing, onItemChange, onAdd, onRemove }: {
  title: string; icon: React.ReactNode; items: string[]; field: string;
  color: string; editing: boolean;
  onItemChange: (field: string, index: number, value: string) => void;
  onAdd: (field: string) => void;
  onRemove: (field: string, index: number) => void;
}) {
  const borderColor = `border-${color}-100`;
  const bgColor = `bg-${color}-50/40`;
  return (
    <div className={`${bgColor} p-4 rounded-xl border ${borderColor}`}>
      <h4 className="text-xs font-bold text-slate-700 font-sans flex items-center gap-1.5 mb-2.5">
        {icon} {title}
      </h4>
      <div className="space-y-2">
        {items.length === 0 && !editing && (
          <p className="text-[10px] text-slate-400 italic">暂无</p>
        )}
        {items.map((item, i) => (
          <div key={i} className="flex items-start gap-1.5">
            {editing ? (
              <>
                <input type="text" value={item} onChange={(e) => onItemChange(field, i, e.target.value)}
                  className="flex-1 text-[11px] py-1.5 px-2.5 bg-white rounded-lg border border-slate-200 outline-none focus:border-primary font-sans" />
                <button onClick={() => onRemove(field, i)}
                  className="p-1 text-red-400 hover:text-red-600 transition cursor-pointer">
                  <X className="w-3 h-3" />
                </button>
              </>
            ) : (
              <div className="flex items-start gap-1.5">
                <span className="w-4 h-4 rounded-full flex items-center justify-center shrink-0 mt-0.5 text-[10px] font-bold font-mono"
                  style={{ backgroundColor: color === "emerald" ? "#d1fae5" : color === "red" ? "#fee2e2" : "#fef3c7",
                    color: color === "emerald" ? "#065f46" : color === "red" ? "#991b1b" : "#92400e" }}>
                  {i + 1}
                </span>
                <span className="text-xs text-slate-600 leading-relaxed font-sans">{item}</span>
              </div>
            )}
          </div>
        ))}
        {editing && (
          <button onClick={() => onAdd(field)}
            className="text-[10px] font-medium text-primary hover:text-primary-container transition cursor-pointer">
            + 添加一项
          </button>
        )}
      </div>
    </div>
  );
}
