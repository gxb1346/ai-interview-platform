import React, { useState, useEffect, useRef } from "react";
import {
  FileText, Upload, Trash2, Database, BookOpen,
  Loader2, CheckCircle2, XCircle, Clock, AlertCircle,
  BookMarked, FileUp, MessageSquare, ExternalLink
} from "lucide-react";
import { KnowledgeDocument, KnowledgeStats } from "../types";
import { authFetch } from "../api";

const API_BASE = "http://localhost:8082";

interface KnowledgeBaseViewProps {
  onNavigateToQA: () => void;
}

export default function KnowledgeBaseView({ onNavigateToQA }: KnowledgeBaseViewProps) {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [stats, setStats] = useState<KnowledgeStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 加载文档列表和统计
  const loadData = async () => {
    try {
      const [docRes, statsRes] = await Promise.all([
        authFetch(`${API_BASE}/api/knowledge/documents`),
        authFetch(`${API_BASE}/api/knowledge/statistics`)
      ]);
      if (docRes.ok) setDocuments(await docRes.json());
      if (statsRes.ok) setStats(await statsRes.json());
    } catch (err) {
      console.error("加载知识库失败:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  // 上传文档
  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("title", file.name.replace(/\.[^/.]+$/, ""));
      const res = await authFetch(`${API_BASE}/api/knowledge/documents/upload`, {
        method: "POST", body: formData
      });
      if (res.ok) {
        loadData();
      }
    } catch (err) {
      console.error("上传失败:", err);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  // 删除文档
  const handleDelete = async (id: number, title: string) => {
    if (!confirm(`确定删除「${title}」？删除后不可恢复。`)) return;
    try {
      const res = await authFetch(`${API_BASE}/api/knowledge/documents/${id}`, { method: "DELETE" });
      if (res.ok) loadData();
    } catch (err) {
      console.error("删除失败:", err);
    }
  };

  const getStatusBadge = (doc: KnowledgeDocument) => {
    switch (doc.indexStatus) {
      case "INDEXED":
        return <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full"><CheckCircle2 className="w-3 h-3" />已索引</span>;
      case "INDEXING":
        return <span className="flex items-center gap-1 text-[10px] font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full"><Loader2 className="w-3 h-3 animate-spin" />索引中</span>;
      case "FAILED":
        return (
          <span className="group relative flex items-center gap-1 text-[10px] font-bold text-red-700 bg-red-50 px-2 py-0.5 rounded-full cursor-help">
            <XCircle className="w-3 h-3" />失败
            {doc.errorMessage && (
              <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 hidden group-hover:block bg-slate-800 text-white text-[10px] px-2.5 py-1.5 rounded-lg whitespace-nowrap shadow-lg z-10">
                {doc.errorMessage}
                <span className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-slate-800" />
              </span>
            )}
          </span>
        );
      default:
        return <span className="flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-full"><Clock className="w-3 h-3" />待处理</span>;
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 头部 */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
            <Database className="w-6 h-6 text-primary" /> 知识库管理
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">上传文档构建知识库，支持 PDF、DOCX、Markdown 等格式</p>
        </div>
        <div className="flex gap-3">
          <button onClick={onNavigateToQA}
            className="text-xs font-semibold text-primary bg-primary/10 border border-primary/20 hover:bg-primary/20 px-4 py-2.5 rounded-xl transition cursor-pointer flex items-center gap-1.5">
            <MessageSquare className="w-4 h-4" /> 知识问答
          </button>
          <button onClick={() => fileInputRef.current?.click()} disabled={uploading}
            className="text-xs font-semibold text-white bg-primary hover:bg-primary-container px-4 py-2.5 rounded-xl transition cursor-pointer disabled:opacity-40 flex items-center gap-1.5 shadow-sm">
            {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileUp className="w-4 h-4" />}
            {uploading ? "上传中..." : "上传文档"}
          </button>
          <input ref={fileInputRef} type="file" accept=".pdf,.docx,.md,.txt" className="hidden"
            onChange={handleUpload} />
        </div>
      </div>

      {/* 统计卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
              <BookOpen className="w-5 h-5 text-primary" />
            </div>
            <div>
              <div className="text-2xl font-black text-slate-800">{stats?.totalDocuments || 0}</div>
              <div className="text-[10px] font-semibold text-slate-400 uppercase">总文档数</div>
            </div>
          </div>
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 flex items-center justify-center">
              <CheckCircle2 className="w-5 h-5 text-emerald-600" />
            </div>
            <div>
              <div className="text-2xl font-black text-slate-800">{stats?.indexedDocuments || 0}</div>
              <div className="text-[10px] font-semibold text-slate-400 uppercase">已索引</div>
            </div>
          </div>
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center">
              <Clock className="w-5 h-5 text-amber-600" />
            </div>
            <div>
              <div className="text-2xl font-black text-slate-800">{stats?.pendingDocuments || 0}</div>
              <div className="text-[10px] font-semibold text-slate-400 uppercase">待处理</div>
            </div>
          </div>
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-red-50 flex items-center justify-center">
              <AlertCircle className="w-5 h-5 text-red-600" />
            </div>
            <div>
              <div className="text-2xl font-black text-slate-800">{stats?.failedDocuments || 0}</div>
              <div className="text-[10px] font-semibold text-slate-400 uppercase">索引失败</div>
            </div>
          </div>
        </div>
      </div>

      {/* 文档列表 */}
      <div className="bg-white/80 backdrop-blur-md border border-slate-200 shadow-sm rounded-2xl overflow-hidden">
        <div className="p-5 border-b border-slate-100">
          <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2">
            <BookMarked className="w-4.5 h-4.5 text-primary" /> 文档列表
            <span className="text-[10px] font-normal text-slate-400">（{documents.length} 个文档）</span>
          </h2>
        </div>

        {documents.length === 0 ? (
          <div className="p-12 text-center">
            <Database className="w-12 h-12 mx-auto text-slate-300 mb-3" />
            <p className="text-sm text-slate-500">知识库为空</p>
            <p className="text-xs text-slate-400 mt-1">上传 PDF、DOCX 或 Markdown 文档开始构建知识库</p>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {documents.map(doc => (
              <div key={doc.id} className="p-4 hover:bg-slate-50/50 transition flex items-center gap-4">
                <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center shrink-0">
                  <FileText className="w-5 h-5 text-slate-500" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-slate-800 truncate">{doc.title}</span>
                    {getStatusBadge(doc)}
                  </div>
                  <div className="flex items-center gap-3 mt-0.5 text-[10px] text-slate-400">
                    <span>{doc.fileName}</span>
                    <span>·</span>
                    <span>{formatFileSize(doc.fileSize)}</span>
                    <span>·</span>
                    <span>{doc.chunkCount} 个分块</span>
                    <span>·</span>
                    <span>{new Date(doc.createdAt).toLocaleString()}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button onClick={() => handleDelete(doc.id, doc.title)}
                    className="w-8 h-8 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 flex items-center justify-center transition cursor-pointer">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}