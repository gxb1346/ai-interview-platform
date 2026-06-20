/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 简历智能分析视图
 * 支持拖拽/点击上传 PDF / DOCX / TXT，调用后端 Java API 完成
 * Tika 解析 → AI 分析 → 结构化输出 → 持久化存储
 */

import React, { useState, useRef } from "react";
import {
  Upload, FileText, Sparkles, AlertCircle,
  CheckCircle2, ChevronRight, Play, Check, File, X, Loader2, Layers
} from "lucide-react";
import { Candidate, CandidateStatus } from "../types";
import { getToken } from "../api";

// Java 后端 API 地址
const API_BASE = "http://localhost:8082";

// 允许的文件类型
const ALLOWED_TYPES = [
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/msword",
  "text/plain",
];
const ALLOWED_EXTENSIONS = [".pdf", ".docx", ".doc", ".txt"];

// 文件大小限制 20MB
const MAX_FILE_SIZE = 20 * 1024 * 1024;

interface ResumeAnalysisViewProps {
  onAddCandidate: (cand: Candidate) => void;
  onNavigateToInterview: (cand: Candidate) => void;
  onNavigateToMock: (cand: Candidate) => void;
}

export default function ResumeAnalysisView({
  onAddCandidate,
  onNavigateToInterview,
  onNavigateToMock,
}: ResumeAnalysisViewProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [targetJob, setTargetJob] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [analyzedCandidate, setAnalyzedCandidate] = useState<Candidate | null>(null);

  // 拖拽状态 + 文件信息
  const [dragActive, setDragActive] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  // 批量上传
  const [batchFiles, setBatchFiles] = useState<File[]>([]);
  const [batchUploading, setBatchUploading] = useState(false);
  const [batchResults, setBatchResults] = useState<{
    fileName: string;
    status: "success" | "duplicate" | "error";
    message: string;
    candidateName?: string;
    matchScore?: number;
  }[] | null>(null);

  // 进度指示
  const [progressStep, setProgressStep] = useState(0);
  const progressSteps = [
    "📄 Tika 引擎解析文件格式...",
    "🔍 提取结构化字段...",
    "🧠 AI 多维度深度评估...",
    "✅ 分析完成，生成报告",
  ];

  const triggerProgress = () => {
    setProgressStep(1);
    const timers = [
      setTimeout(() => setProgressStep(2), 2000),
      setTimeout(() => setProgressStep(3), 4500),
      setTimeout(() => setProgressStep(4), 6500),
    ];
    return timers;
  };

  // ==================== 文件校验 ====================

  const validateFile = (file: File): string | null => {
    const ext = "." + file.name.split(".").pop()?.toLowerCase();
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      return "不支持的文件格式，仅支持 PDF、DOCX、TXT";
    }
    if (file.size > MAX_FILE_SIZE) {
      return "文件大小超过 20MB 限制";
    }
    return null;
  };

  // ==================== 上传 + 分析 ====================

  const handleUploadAndAnalyze = async (file: File) => {
    const errMsg = validateFile(file);
    if (errMsg) {
      setError(errMsg);
      return;
    }

    setLoading(true);
    setError(null);
    setAnalyzedCandidate(null);
    setSelectedFile(file);

    const timers = triggerProgress();

    try {
      const formData = new FormData();
      formData.append("file", file);
      if (targetJob.trim()) {
        formData.append("targetJob", targetJob.trim());
      }

      const response = await fetch(`${API_BASE}/api/resume/upload`, {
        method: "POST",
        headers: getToken() ? { "Authorization": `Bearer ${getToken()}` } : undefined,
        body: formData,
      });

      if (!response.ok) {
        const errBody = await response.json().catch(() => null);
        throw new Error(errBody?.message || `服务器错误: ${response.status}`);
      }

      const result = await response.json();

      if (result.code !== 200) {
        throw new Error(result.message || "分析失败");
      }

      const data = result.data;

      // 映射后端返回的 ResumeVO 到前端 Candidate 结构
      const newCand: Candidate = {
        id: "cand_" + Date.now(),
        name: data.candidateName || "求职者",
        role: data.candidateRole || targetJob || "资深工程师",
        experienceYears: data.experienceYears || 3,
        education: data.education || "未知",
        status: CandidateStatus.WAITING_INTERVIEW,
        avatar:
          "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&h=150&fit=crop&crop=face",
        matchScore: data.matchScore || 85,
        email: data.email || "",
        phone: data.phone || "",
        resumeText: "",
        competencies: {
          technical: data.competencies?.technical ?? 8,
          communication: data.competencies?.communication ?? 7,
          problemSolving: data.competencies?.problemSolving ?? 8,
          teamFit: data.competencies?.teamFit ?? 8,
          drive: data.competencies?.drive ?? 8,
        },
        strengths: data.strengths || [],
        weaknesses: data.weaknesses || [],
        highlights: data.highlights || [],
        aiSummary: data.aiSummary || "",
        analyzedAt: data.analyzedAt || new Date().toISOString().replace("T", " ").substring(0, 16),
      };

      // 自动将新简历移入人才库
      if (data.id) {
        fetch(`${API_BASE}/api/resume/${data.id}/to-talent-pool`, {
          method: "POST",
          headers: getToken() ? { "Authorization": `Bearer ${getToken()}` } : undefined,
        }).catch(() => {});
      }

      // 延迟展示，让进度条走完
      setTimeout(() => {
        setAnalyzedCandidate(newCand);
        onAddCandidate(newCand);
        setLoading(false);
        timers.forEach((t) => clearTimeout(t));
      }, 7000);

    } catch (err: any) {
      console.error("简历分析失败:", err);
      setError(err.message || "AI 分析服务暂时繁忙，请稍后重试");
      setLoading(false);
      setSelectedFile(null);
      timers.forEach((t) => clearTimeout(t));
    }
  };

  // ==================== 拖拽事件 ====================

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      handleUploadAndAnalyze(files[0]);
    }
  };

  // ==================== 文件选择器 ====================

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      handleUploadAndAnalyze(files[0]);
    }
    // 重置 input 以便重复选择同一文件
    e.target.value = "";
  };

  // ==================== 批量文件选择 ====================

  const handleMultiFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      setBatchFiles(Array.from(files));
      setBatchResults(null);
      setError(null);
    }
    e.target.value = "";
  };

  // ==================== 批量上传分析 ====================

  const handleBatchUpload = async () => {
    if (batchFiles.length === 0) return;
    setBatchUploading(true);
    setBatchResults(null);
    setError(null);

    try {
      const formData = new FormData();
      batchFiles.forEach(file => formData.append("files", file));
      if (targetJob.trim()) {
        formData.append("targetJob", targetJob.trim());
      }

      const response = await fetch(`${API_BASE}/api/resume/upload/batch`, {
        method: "POST",
        headers: getToken() ? { "Authorization": `Bearer ${getToken()}` } : undefined,
        body: formData,
      });

      if (!response.ok) {
        const errBody = await response.json().catch(() => null);
        throw new Error(errBody?.message || `服务器错误: ${response.status}`);
      }

      const result = await response.json();

      if (result.code !== 200) {
        throw new Error(result.message || "批量分析失败");
      }

      setBatchResults(result.data || []);

      // 自动将成功分析的简历加入候选人列表
      if (result.data) {
        const successOnes = result.data.filter((r: any) => r.status === "success");
        successOnes.forEach((item: any) => {
          const newCand: Candidate = {
            id: "cand_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
            name: item.candidateName || "求职者",
            role: targetJob || "资深工程师",
            experienceYears: 3,
            education: "未知",
            status: CandidateStatus.WAITING_INTERVIEW,
            avatar: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&h=150&fit=crop&crop=face",
            matchScore: item.matchScore || 85,
            email: "", phone: "", resumeText: "",
            competencies: { technical: 5, communication: 5, problemSolving: 5, teamFit: 5, drive: 5 },
            strengths: [], weaknesses: [], highlights: [], aiSummary: "",
            analyzedAt: new Date().toISOString().replace("T", " ").substring(0, 16)
          };
          onAddCandidate(newCand);
        });
      }
    } catch (err: any) {
      console.error("批量分析失败:", err);
      setError(err.message || "批量分析失败");
    } finally {
      setBatchUploading(false);
    }
  };

  // ==================== 重渲染 ====================

  const handleReset = () => {
    setAnalyzedCandidate(null);
    setSelectedFile(null);
    setTargetJob("");
    setError(null);
    setProgressStep(0);
  };

  // ==================== 五维雷达图 (SVG) ====================

  const renderCompetencyChart = (comp: any) => {
    const center = 100;
    const r = 70;
    const labels = ["技术深度", "沟通表达", "解决问题", "团队契合", "自驱动力"];
    const keys = ["technical", "communication", "problemSolving", "teamFit", "drive"];

    const angles = [
      -Math.PI / 2,
      -Math.PI / 2 + (2 * Math.PI) / 5,
      -Math.PI / 2 + (4 * Math.PI) / 5,
      -Math.PI / 2 + (6 * Math.PI) / 5,
      -Math.PI / 2 + (8 * Math.PI) / 5,
    ];

    const getPoints = (scale: number) =>
      angles.map((a) => {
        const x = center + r * scale * Math.cos(a);
        const y = center + r * scale * Math.sin(a);
        return `${x},${y}`;
      }).join(" ");

    const scorePoints = angles
      .map((a, i) => {
        const score = comp[keys[i]] || 8;
        const s = score / 10;
        const x = center + r * s * Math.cos(a);
        const y = center + r * s * Math.sin(a);
        return `${x},${y}`;
      })
      .join(" ");

    return (
      <div className="flex flex-col items-center justify-center p-4">
        <svg width="240" height="230" className="drop-shadow-sm overflow-visible">
          {angles.map((a, i) => (
            <line
              key={i}
              x1={center} y1={center}
              x2={center + r * Math.cos(a)} y2={center + r * Math.sin(a)}
              strokeWidth="1" stroke="#c2c6d6" strokeDasharray="3,3"
            />
          ))}
          {[0.2, 0.4, 0.6, 0.8, 1.0].map((scale, i) => (
            <polygon key={i} points={getPoints(scale)} fill="none" stroke="#e2e7ff" strokeWidth="1.5" />
          ))}
          <polygon points={scorePoints} fill="rgba(0, 88, 190, 0.15)" stroke="#0058be" strokeWidth="2.5" className="transition-all duration-1000 ease-in-out" />
          {angles.map((a, i) => {
            const score = comp[keys[i]] || 8;
            const s = score / 10;
            const x = center + r * s * Math.cos(a);
            const y = center + r * s * Math.sin(a);
            return (
              <g key={i} className="group cursor-help">
                <circle cx={x} cy={y} r="5" fill="#0058be" stroke="#ffffff" strokeWidth="2" />
                <rect x={x - 15} y={y - 24} width="30" height="16" rx="3" fill="#283044" className="opacity-0 group-hover:opacity-100 transition-opacity" />
                <text x={x} y={y - 12} fill="#ffffff" fontSize="10" fontWeight="bold" textAnchor="middle" className="pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity">
                  {score}分
                </text>
              </g>
            );
          })}
          {angles.map((a, i) => {
            const ld = r + 18;
            const x = center + ld * Math.cos(a);
            const y = center + ld * Math.sin(a) + 4;
            const ta = Math.cos(a) > 0.1 ? "start" : Math.cos(a) < -0.1 ? "end" : "middle";
            return (
              <text key={i} x={x} y={y} fill="#424754" fontSize="12" fontWeight="500" textAnchor={ta} className="font-sans">
                {labels[i]}
              </text>
            );
          })}
        </svg>
      </div>
    );
  };

  // ==================== 渲染 ====================

  return (
    <div className="space-y-6" id="resume-analysis-container">
      {/* 顶部标题栏 */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            <Sparkles className="w-6 h-6 text-primary" /> 简历智能神经分析
          </h1>
          <p className="text-sm text-slate-500 mt-1 font-sans">
            上传 PDF / DOCX / TXT 简历 → Apache Tika 文档解析 → AI 多维度深度评估 → 结构化输出与持久化
          </p>
        </div>
      </div>

      {!analyzedCandidate && (
        <div className="grid grid-cols-1 gap-6">
          {/* 上传区 */}
          <div className="bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
            {/* 目标岗位输入 */}
            <div className="space-y-2">
              <label className="text-sm font-semibold text-slate-700 font-sans block">
                招聘目标岗位 <span className="text-slate-400 font-normal">(选填，帮助AI更精准匹配)</span>
              </label>
              <input
                type="text"
                value={targetJob}
                onChange={(e) => setTargetJob(e.target.value)}
                placeholder="例如：高级前端开发专家、深度学习算法专家"
                className="w-full text-sm py-2.5 px-4 bg-slate-100/50 hover:bg-slate-100 focus:bg-white rounded-xl border border-slate-200 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none transition font-sans"
              />
            </div>

            {/* 拖拽上传区域 */}
            <div
              onDragEnter={handleDrag}
              onDragOver={handleDrag}
              onDragLeave={handleDrag}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`border-2 border-dashed rounded-2xl p-8 text-center transition flex flex-col justify-center items-center gap-3 cursor-pointer ${
                dragActive
                  ? "border-primary bg-primary/5 scale-[1.02]"
                  : "border-slate-200 bg-slate-50/50 hover:bg-slate-50 hover:border-primary/40"
              }`}
            >
              {selectedFile && !error ? (
                <>
                  <File className="w-10 h-10 text-primary" />
                  <p className="text-sm font-semibold text-slate-700 font-sans">{selectedFile.name}</p>
                  <p className="text-xs text-slate-400 font-sans">
                    {(selectedFile.size / 1024).toFixed(1)} KB
                  </p>
                </>
              ) : (
                <>
                  <div className="w-14 h-14 bg-primary/5 rounded-2xl flex items-center justify-center">
                    <Upload className="w-7 h-7 text-primary" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-slate-600 font-sans">
                      拖拽简历到此处，或<span className="text-primary">点击选择文件</span>
                    </p>
                    <p className="text-xs text-slate-400 font-sans mt-1">
                      支持 PDF、DOCX、TXT 格式，最大 20MB
                    </p>
                  </div>
                </>
              )}

              {/* 隐藏的文件选择器 */}
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.docx,.doc,.txt"
                onChange={handleFileSelect}
                className="hidden"
              />
            </div>

            {/* 错误提示 */}
            {error && (
              <div className="flex items-center gap-2.5 p-3.5 bg-red-50 rounded-xl border border-red-100 text-red-600 text-xs font-sans">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {/* 上传分析按钮 */}
            {selectedFile && !loading && (
              <button
                onClick={() => handleUploadAndAnalyze(selectedFile)}
                className="w-full font-sans text-sm font-semibold text-primary bg-gradient-to-r from-primary/10 to-primary/5 border border-primary/20 hover:from-primary/20 hover:to-primary/10 py-3.5 px-6 rounded-xl transition flex items-center justify-center gap-2 cursor-pointer"
              >
                <Sparkles className="w-4.5 h-4.5" />
                分析此简历
              </button>
            )}

            {/* 分隔线 + 批量上传区域 */}
            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-slate-200" />
              </div>
              <div className="relative flex justify-center">
                <span className="bg-white px-3 text-xs text-slate-400 font-sans">或</span>
              </div>
            </div>

            <div className="bg-slate-50/70 rounded-xl border border-slate-200 p-4 space-y-3">
              <div className="flex items-center gap-2">
                <Layers className="w-4 h-4 text-primary" />
                <span className="text-sm font-semibold text-slate-700 font-sans">批量上传简历</span>
              </div>

              {/* 批量文件选择 */}
              <input
                type="file"
                multiple
                accept=".pdf,.docx,.doc,.txt"
                onChange={handleMultiFileSelect}
                className="hidden"
                id="batch-file-input"
              />
              <label htmlFor="batch-file-input"
                className="block text-center border-2 border-dashed border-slate-300 rounded-xl p-4 cursor-pointer hover:border-primary/40 hover:bg-primary/5 transition">
                <Upload className="w-5 h-5 text-slate-400 mx-auto mb-1" />
                <p className="text-xs text-slate-500 font-sans">
                  {batchFiles.length > 0
                    ? `已选择 ${batchFiles.length} 个文件`
                    : "点击选择多个文件（支持 PDF/DOCX/TXT）"}
                </p>
                <p className="text-[10px] text-slate-400 font-sans mt-0.5">最多 20 个文件</p>
              </label>

              {/* 已选文件列表 */}
              {batchFiles.length > 0 && (
                <div className="max-h-32 overflow-y-auto space-y-1">
                  {batchFiles.map((f, i) => (
                    <div key={i} className="flex items-center justify-between text-xs text-slate-600 bg-white rounded-lg px-2.5 py-1.5 border border-slate-100">
                      <span className="truncate flex-1">{f.name}</span>
                      <span className="text-slate-400 ml-2 shrink-0">{(f.size / 1024).toFixed(0)}KB</span>
                      <button onClick={() => setBatchFiles(prev => prev.filter((_, idx) => idx !== i))}
                        className="ml-1.5 text-slate-300 hover:text-red-500 cursor-pointer">
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {/* 批量上传按钮 */}
              {batchFiles.length > 0 && (
                <button onClick={handleBatchUpload} disabled={batchUploading}
                  className="w-full text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 disabled:opacity-40 py-2.5 px-4 rounded-xl transition flex items-center justify-center gap-1.5 cursor-pointer">
                  {batchUploading ? (
                    <><Loader2 className="w-3.5 h-3.5 animate-spin" /> 正在分析 {batchFiles.length} 份简历...</>
                  ) : (
                    <><Sparkles className="w-3.5 h-3.5" /> 批量分析 {batchFiles.length} 份简历</>
                  )}
                </button>
              )}

              {/* 批量结果展示 */}
              {batchResults && (
                <div className="space-y-2 pt-1">
                  <div className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                    批量分析完成
                  </div>
                  <div className="space-y-1 max-h-40 overflow-y-auto">
                    {batchResults.map((r, i) => (
                      <div key={i} className={`flex items-center gap-2 text-xs p-2 rounded-lg border ${
                        r.status === "success"
                          ? "bg-emerald-50 border-emerald-100 text-emerald-800"
                          : r.status === "duplicate"
                          ? "bg-amber-50 border-amber-100 text-amber-800"
                          : "bg-red-50 border-red-100 text-red-800"
                      }`}>
                        {r.status === "success" ? <CheckCircle2 className="w-3 h-3 shrink-0" />
                          : r.status === "duplicate" ? <FileText className="w-3 h-3 shrink-0" />
                          : <AlertCircle className="w-3 h-3 shrink-0" />}
                        <span className="truncate flex-1">{r.fileName}</span>
                        {r.candidateName && <span className="shrink-0">{r.candidateName}</span>}
                        {r.matchScore && <span className="shrink-0 font-bold">{r.matchScore}分</span>}
                      </div>
                    ))}
                  </div>
                  <button onClick={() => { setBatchFiles([]); setBatchResults(null); }}
                    className="text-xs text-primary bg-primary/5 border border-primary/20 hover:bg-primary/10 px-3 py-1.5 rounded-lg transition cursor-pointer w-full">
                    清空结果，继续上传
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ==================== 分析结果展示 ==================== */}
      {analyzedCandidate && (
        <div className="space-y-6 animate-fade-in">
          {/* 头部信息 */}
          <div className="bg-white/80 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div className="flex items-center gap-4">
              <img
                src={analyzedCandidate.avatar}
                alt={analyzedCandidate.name}
                referrerPolicy="no-referrer"
                className="w-16 h-16 rounded-full object-cover border-2 border-primary/20 shadow-sm"
              />
              <div className="space-y-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <h2 className="text-lg font-bold font-sans text-slate-800">{analyzedCandidate.name}</h2>
                  <span className="text-xs bg-slate-100 text-slate-600 font-medium py-0.5 px-2.5 rounded-full border border-slate-200">
                    {analyzedCandidate.education}
                  </span>
                  <span className="text-xs bg-primary/10 text-primary font-semibold py-0.5 px-2.5 rounded-full border border-primary/10">
                    {analyzedCandidate.experienceYears}年经验
                  </span>
                </div>
                <p className="text-sm text-slate-500 font-sans">推荐定岗：{analyzedCandidate.role}</p>
                <div className="flex items-center gap-4 text-xs text-slate-400 mt-1 font-sans">
                  <span>✉️ {analyzedCandidate.email}</span>
                  <span>📞 {analyzedCandidate.phone}</span>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-4 bg-slate-50/80 p-3 rounded-xl border border-slate-100">
              <div className="relative w-16 h-16 flex items-center justify-center">
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                  <path className="text-slate-200" strokeWidth="3.5" stroke="currentColor" fill="none"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                  <path className="text-primary transition-all duration-1000" strokeWidth="3.5"
                    strokeDasharray={`${analyzedCandidate.matchScore}, 100`} strokeLinecap="round"
                    stroke="currentColor" fill="none"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                </svg>
                <span className="absolute text-sm font-extrabold text-slate-800 font-mono">
                  {analyzedCandidate.matchScore}%
                </span>
              </div>
              <div className="space-y-0.5">
                <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-400 block font-sans">
                  AI 竞争力匹配度
                </span>
                <span className="text-xs font-bold text-slate-700 font-sans">
                  {analyzedCandidate.matchScore >= 90
                    ? "极高纯度核心人选"
                    : analyzedCandidate.matchScore >= 80
                    ? "高适配资深候选"
                    : "基本适配人选"}
                </span>
              </div>
            </div>
          </div>

          {/* 雷达图 + 亮点 */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div className="lg:col-span-5 bg-white/80 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-800 font-sans border-b border-slate-100 pb-3 block">
                  五大维度能力神经映射
                </h3>
                {renderCompetencyChart(analyzedCandidate.competencies)}
              </div>

              <div className="space-y-2.5 mt-4">
                <span className="text-xs font-bold text-slate-400 block font-sans uppercase">
                  ⭐ 求职者核心亮点
                </span>
                <div className="flex flex-wrap gap-2">
                  {analyzedCandidate.highlights.map((light, i) => (
                    <div
                      key={i}
                      className="text-xs font-medium text-amber-800 bg-amber-50 rounded-lg border border-amber-100 p-2 text-left w-full flex items-start gap-1.5"
                    >
                      <Sparkles className="w-3.5 h-3.5 text-amber-500 shrink-0 mt-0.5" />
                      <span>{light}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* 优势 + 劣势 + AI总结 */}
            <div className="lg:col-span-7 space-y-6">
              <div className="bg-white/80 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm space-y-3">
                <h3 className="text-sm font-bold text-slate-800 font-sans flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-primary" /> AI HR 评估决策书
                </h3>
                <p className="text-xs leading-relaxed text-slate-600 font-sans italic bg-slate-50 p-4 rounded-xl border border-slate-100 border-l-4 border-l-primary shadow-sm">
                  “ {analyzedCandidate.aiSummary} ”
                </p>
                <div className="text-[10px] text-slate-400 font-sans text-right">
                  分析时间：{analyzedCandidate.analyzedAt} · RecruitAI 智能招聘内核
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="bg-emerald-50/40 p-5 rounded-2xl border border-emerald-100 shadow-sm space-y-3">
                  <h4 className="text-xs font-extrabold text-emerald-800 font-sans uppercase flex items-center gap-1.5">
                    <CheckCircle2 className="w-4.5 h-4.5" /> 3 大核心优势
                  </h4>
                  <ul className="space-y-2.5">
                    {analyzedCandidate.strengths.map((str, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans flex gap-2">
                        <span className="w-4 h-4 bg-emerald-100 text-emerald-800 font-bold font-mono rounded-full flex items-center justify-center shrink-0 mt-0.5">
                          {idx + 1}
                        </span>
                        <span>{str}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="bg-red-50/40 p-5 rounded-2xl border border-red-100 shadow-sm space-y-3">
                  <h4 className="text-xs font-extrabold text-red-800 font-sans uppercase flex items-center gap-1.5">
                    <AlertCircle className="w-4.5 h-4.5" /> 2 项改善建议
                  </h4>
                  <ul className="space-y-2.5">
                    {analyzedCandidate.weaknesses.map((weak, idx) => (
                      <li key={idx} className="text-xs text-slate-600 leading-relaxed font-sans flex gap-2">
                        <span className="w-4 h-4 bg-red-100 text-red-800 font-bold font-mono rounded-full flex items-center justify-center shrink-0 mt-0.5">
                          {idx + 1}
                        </span>
                        <span>{weak}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              {/* 底部操作 */}
              <div className="bg-white/80 p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-4">
                <button
                  onClick={handleReset}
                  className="w-full sm:w-auto font-sans text-xs bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 px-5 rounded-xl transition cursor-pointer flex items-center gap-1.5"
                >
                  <X className="w-3.5 h-3.5" />
                  解析新简历
                </button>

                <div className="flex items-center gap-3 w-full sm:w-auto">
                  <button
                    onClick={() => onNavigateToInterview(analyzedCandidate)}
                    className="flex-1 sm:flex-initial font-sans text-xs text-primary bg-primary/10 hover:bg-primary/20 font-bold py-2.5 px-5 rounded-xl transition flex items-center justify-center gap-1 cursor-pointer border border-primary/10"
                  >
                    安排面试提纲
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>

                  <button
                    onClick={() => onNavigateToMock(analyzedCandidate)}
                    className="flex-1 sm:flex-initial font-sans text-xs text-primary bg-primary/10 hover:bg-primary/20 font-semibold py-2.5 px-5 rounded-xl transition flex items-center justify-center gap-1 cursor-pointer border border-primary/10"
                  >
                    <Play className="w-3 h-3 fill-white" />
                    开启模拟面试
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
