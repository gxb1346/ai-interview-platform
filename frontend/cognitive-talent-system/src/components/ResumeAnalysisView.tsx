import React, { useState } from "react";
import { Upload, FileText, Sparkles, AlertCircle, BarChart3, CheckCircle2, ChevronRight, Play, Check } from "lucide-react";
import { Candidate, CandidateStatus } from "../types";

// Dynamic detailed demo text candidates for instant analysis
const DEMO_RESUMES = {
  frontend: `姓名：叶志豪   应聘岗位：资深前端通道专家
邮箱：zhihao.ye@example.com   电话：136-2299-1111   最高学历：同济大学 计算机本科

个人简介：
5年大厂研发底座沉淀，深耕现代前端框架（React/Vue3）与高可用微前端体系工程落地。开源社区核心活跃者，对冷启动静态渲染优化、Webpack/Vite 构筑底座和底层 V8 执行沙箱机制具有极致优化能力。

工作经历：
1. 蚂蚁集团 · 智能商业前端组 · 资深研发工程师 (2023.03 - 2026.06)
- 作为技术骨干从零重构了微前端应用核心管理舱，解决45个子系统的复杂沙箱隔离与样式冲突。
- 引入端侧惰性渲染、离线多级缓存以及基于数据依赖按需预加载策略，首屏FCP时间从 2.4s 骤降至 0.8s，用户侧综合Crash率下降 68%。
- 开源微前端微架构，GitHub收获超过 4.8K Star，成为团队内部前端性能优化标杆专家。
2. 携程旅行 · 前端架构部 · 高级研发工程师 (2021.06 - 2023.03)
- 主控搭建了跨多端统一渲染组件库设计系统，深度定制跨平台移动响应机制。
- 针对Web View混合容器渲染性能进行白屏诊断及全链路调优，解决大流量秒杀期间由于冷拔插引发的渲染内存泄露痛点。

核心技能：
TypeScript, React/Next.js, Vite/Rsbuild, CSS Houdini, WebAssembly, Node.js集群治理, 性能调优`,

  aimodel: `姓名：邓思源   应聘岗位：大语言模型研发算法科学家 
邮箱：siyuan.deng@example.com   电话：185-1100-8822   最高学历：北京大学 智能科学系博士

个人简介：
主要致力于LLM多模太混合微调训练提效、高质量对齐训练与检索增强生成(RAG)底层全闭环打通。在分布式训练框架 (DeepSpeed/Megatron-LM) 训练加速、千亿级参数自建大模型微调、评测场景拥有丰富的大型科研及头部实战工业积累。

工作经历：
1. 腾讯 · 混元大模型核心算法中心 · 高级研究员 (2024.01 - 2026.06)
- 负责多模态理解与文字高纯对齐的底层微调算法打通。主导了基于 RLAIF（AI反馈RL）的核心策略调优，使大模型安全性对齐率获得 15% 以上大幅攀升。
- 深度优化多卡异构环境下梯度融合和全局吞吐效率，结合 FP8 精度切片混洗，降低算力耗用超 22%。
- 主持构建全链路高精度法律/医疗垂直垂类LLM预训练及落地，模型在中游垂直度测评排名第一。
2. 百度 · 深度学习国家实验室 · 算法工程师 (2022.07 - 2024.01)
- 深入参与自研 PaddlePaddle 分布式深度训练底层矩阵流加速优化。
- 针对混合检索 (Dense-Sparse Hybrid RAG) 降噪提出多阶段召回答案生成架构，并在核心知识库中上线落地。

核心技能：
大模型微调 (SFT/RLHF/DPO/PPO), DeepSpeed, Megatron-LM, PyTorch底层, LLM检索增强, 高并发算法架构设计, 数理建模推理`
};

interface ResumeAnalysisViewProps {
  onAddCandidate: (cand: Candidate) => void;
  onNavigateToInterview: (cand: Candidate) => void;
  onNavigateToMock: (cand: Candidate) => void;
}

export default function ResumeAnalysisView({
  onAddCandidate,
  onNavigateToInterview,
  onNavigateToMock
}: ResumeAnalysisViewProps) {
  const [resumeText, setResumeText] = useState("");
  const [targetJob, setTargetJob] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [analyzedCandidate, setAnalyzedCandidate] = useState<Candidate | null>(null);
  const [progressStep, setProgressStep] = useState(0);

  // Drag and drop states
  const [dragActive, setDragActive] = useState(false);

  const triggerProgress = () => {
    setProgressStep(1); // "语义标记提取..."
    const timers = [
      setTimeout(() => setProgressStep(2), 1200), // "进行五特征深度画像映射..."
      setTimeout(() => setProgressStep(3), 2400), // "正在运行AI招聘HR匹配研判..."
      setTimeout(() => setProgressStep(4), 3600), // "已完成"
    ];
    return timers;
  };

  const handleAnalyze = async (textToAnalyze: string) => {
    if (!textToAnalyze.trim()) {
      setError("请输入或贴入求职者的简历文本以供解析！");
      return;
    }

    setLoading(true);
    setError(null);
    setAnalyzedCandidate(null);

    const timers = triggerProgress();

    try {
      const response = await fetch("/api/resume/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          resumeText: textToAnalyze,
          targetJob: targetJob || undefined
        })
      });

      if (!response.ok) {
        throw new Error("简历神经网解析请求异常，已启动 fallback 引擎。");
      }

      const data = await response.json();
      
      // Map server response to Full Candidate structure
      const newCand: Candidate = {
        id: "cand_" + Date.now(),
        name: data.name || "求职者",
        role: data.role || targetJob || "资深工程师",
        experienceYears: Number(data.experienceYears) || 3,
        education: data.education || "名牌大学本科",
        status: CandidateStatus.WAITING_INTERVIEW,
        avatar: data.name === "邓思源"
          ? "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&h=150&fit=crop&crop=face"
          : "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&h=150&fit=crop&crop=face",
        matchScore: Number(data.matchScore) || 85,
        email: data.email || "candidate@example.com",
        phone: data.phone || "138-0000-0000",
        resumeText: textToAnalyze,
        competencies: {
          technical: Number(data.competencies?.technical) || 8,
          communication: Number(data.competencies?.communication) || 7,
          problemSolving: Number(data.competencies?.problemSolving) || 8,
          teamFit: Number(data.competencies?.teamFit) || 8,
          drive: Number(data.competencies?.drive) || 8
        },
        strengths: data.strengths || [],
        weaknesses: data.weaknesses || [],
        highlights: data.highlights || [],
        aiSummary: data.aiSummary || "",
        analyzedAt: new Date().toISOString().replace("T", " ").substring(0, 16)
      };

      // Add to main global talent list automatically
      setTimeout(() => {
        setAnalyzedCandidate(newCand);
        onAddCandidate(newCand);
        setLoading(false);
        timers.forEach(t => clearTimeout(t));
      }, 4000); // give enough time for progress animation to look professional and reassuring

    } catch (err: any) {
      console.warn(err);
      setError("AI分析服务暂调繁忙，建议稍后重试或使用推荐Demo尝试。");
      setLoading(false);
      timers.forEach(t => clearTimeout(t));
    }
  };

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

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      const reader = new FileReader();
      reader.onload = (event) => {
        const text = event.target?.result as string;
        if (text) {
          setResumeText(text);
          handleAnalyze(text);
        }
      };
      reader.readAsText(file);
    }
  };

  const handleDemoSelect = (key: "frontend" | "aimodel") => {
    const text = DEMO_RESUMES[key];
    setResumeText(text);
    const mockRole = key === "frontend" ? "资深前端专家" : "大语言模型研发算法科学家";
    setTargetJob(mockRole);
    handleAnalyze(text);
  };

  // Render customizable visual SVG metrics
  const renderCompetencyChart = (comp: any) => {
    // Math to draw a crisp, dynamic pentagon chart for candidate's 5 key competencies
    const center = 100;
    const r = 70; // radius
    const labels = ["技术深度", "沟通表达", "解决问题", "团队契合", "自驱动力"];
    const keys = ["technical", "communication", "problemSolving", "teamFit", "drive"];
    
    // SVG points for background concentric rings
    const angles = [
      -Math.PI / 2,                  // top
      -Math.PI / 2 + (2 * Math.PI) / 5,  // top right
      -Math.PI / 2 + (4 * Math.PI) / 5,  // bottom right
      -Math.PI / 2 + (6 * Math.PI) / 5,  // bottom left
      -Math.PI / 2 + (8 * Math.PI) / 5,  // top left
    ];

    const getPoints = (scale: number) => {
      return angles.map((angle, i) => {
        const x = center + r * scale * Math.cos(angle);
        const y = center + r * scale * Math.sin(angle);
        return `${x},${y}`;
      }).join(" ");
    };

    // Calculate score points (scores are from 1 to 10 scale)
    const scorePoints = angles.map((angle, i) => {
      const score = comp[keys[i]] || 8;
      const scale = score / 10;
      const x = center + r * scale * Math.cos(angle);
      const y = center + r * scale * Math.sin(angle);
      return `${x},${y}`;
    }).join(" ");

    return (
      <div className="flex flex-col items-center justify-center p-4">
        <svg width="240" height="230" className="drop-shadow-sm overflow-visible">
          {/* Conic background lines */}
          {angles.map((angle, i) => {
            const x = center + r * Math.cos(angle);
            const y = center + r * Math.sin(angle);
            return (
              <line key={i} x1={center} y1={center} x2={x} y2={y} strokeWidth="1" stroke="#c2c6d6" strokeDasharray="3,3" />
            );
          })}

          {/* Background Pentagons (grid lines) */}
          {[0.2, 0.4, 0.6, 0.8, 1.0].map((scale, i) => (
            <polygon
              key={i}
              points={getPoints(scale)}
              fill="none"
              stroke="#e2e7ff"
              strokeWidth="1.5"
            />
          ))}

          {/* Active Capacity Radar Shape */}
          <polygon
            points={scorePoints}
            fill="rgba(0, 88, 190, 0.15)"
            stroke="#0058be"
            strokeWidth="2.5"
            className="transition-all duration-1000 ease-in-out"
          />

          {/* Core score circle dots */}
          {angles.map((angle, i) => {
            const score = comp[keys[i]] || 8;
            const scale = score / 10;
            const x = center + r * scale * Math.cos(angle);
            const y = center + r * scale * Math.sin(angle);
            return (
              <g key={i} className="group cursor-help">
                <circle cx={x} cy={y} r="5" fill="#0058be" stroke="#ffffff" strokeWidth="2" />
                <rect x={x - 15} y={y - 24} width="30" height="16" rx="3" fill="#283044" className="opacity-0 group-hover:opacity-100 transition-opacity duration-200" />
                <text x={x} y={y - 12} fill="#ffffff" fontSize="10" fontWeight="bold" textAnchor="middle" className="pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity duration-200">{score}分</text>
              </g>
            );
          })}

          {/* Typography label positions around pentagon */}
          {angles.map((angle, i) => {
            // Push labels outward slightly
            const labelDist = r + 18;
            const x = center + labelDist * Math.cos(angle);
            // vertical tilt adjustments
            const y = center + labelDist * Math.sin(angle) + 4;
            const textAnchor = Math.cos(angle) > 0.1 ? "start" : Math.cos(angle) < -0.1 ? "end" : "middle";
            return (
              <text
                key={i}
                x={x}
                y={y}
                fill="#424754"
                fontSize="12"
                fontWeight="500"
                textAnchor={textAnchor}
                className="font-sans"
              >
                {labels[i]}
              </text>
            );
          })}
        </svg>
      </div>
    );
  };

  return (
    <div className="space-y-6" id="resume-analysis-container">
      {/* Search Header Banner */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-white/40 shadow-sm">
        <div>
          <h1 className="text-2xl font-bold font-sans text-slate-900 tracking-tight flex items-center gap-2">
            <Sparkles className="w-6 height-6 text-primary" /> 简历智能神经分析
          </h1>
          <p className="text-sm text-slate-500 mt-1 font-sans">
            输入候选人简历文本、上传文档，通过 Gemini 实时神经内核对求职者进行多维度精准打分，快速研判岗位契合度。
          </p>
        </div>

        {/* Demo Fast Selector */}
        <div className="flex items-center gap-2 bg-slate-100/80 p-1.5 rounded-xl border border-slate-200">
          <span className="text-xs font-semibold text-slate-500 font-sans px-2.5">快捷导入 Demo 简历:</span>
          <button
            onClick={() => handleDemoSelect("frontend")}
            disabled={loading}
            className="text-xs bg-white hover:bg-slate-50 text-slate-700 font-medium py-1.5 px-3 rounded-lg border border-slate-200 transition shadow-sm cursor-pointer disabled:opacity-50"
          >
            资深前端叶志豪
          </button>
          <button
            onClick={() => handleDemoSelect("aimodel")}
            disabled={loading}
            className="text-xs bg-white hover:bg-slate-50 text-slate-700 font-medium py-1.5 px-3 rounded-lg border border-slate-200 transition shadow-sm cursor-pointer disabled:opacity-50"
          >
            北大AI博士邓思源
          </button>
        </div>
      </div>

      {!analyzedCandidate && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Form Area (Input) */}
          <div className="lg:col-span-7 bg-white/70 backdrop-blur-md p-6 rounded-2xl border border-white/40 shadow-sm space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-semibold text-slate-700 font-sans block">
                意向/招聘目标岗位 (选填)
              </label>
              <input
                type="text"
                value={targetJob}
                onChange={(e) => setTargetJob(e.target.value)}
                placeholder="例如：高级前端开发专家、深度学习算法专家"
                className="w-full text-sm py-2.5 px-4 bg-slate-100/50 hover:bg-slate-100 focus:bg-white rounded-xl border border-slate-200 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none transition font-sans"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-semibold text-slate-700 font-sans block">
                求职者简历详文
              </label>
              <textarea
                value={resumeText}
                onChange={(e) => setResumeText(e.target.value)}
                placeholder="在此直接粘贴求职者的完整简历内容（支持姓名、电话邮箱、教育、完整工作成绩要素）..."
                className="w-full h-80 text-sm py-3 px-4 bg-slate-100/50 hover:bg-slate-100 focus:bg-white rounded-xl border border-slate-200 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none transition font-sans resize-none"
              />
            </div>

            {/* Drag Drop Area */}
            <div
              onDragEnter={handleDrag}
              onDragOver={handleDrag}
              onDragLeave={handleDrag}
              onDrop={handleDrop}
              className={`border-2 border-dashed rounded-2xl p-6 text-center transition flex flex-col justify-center items-center gap-2 cursor-pointer ${
                dragActive ? "border-primary bg-primary/5 scale-[1.01]" : "border-slate-200 bg-slate-50/50 hover:bg-slate-50 hover:border-slate-300"
              }`}
            >
              <Upload className="w-8 h-8 text-slate-400" />
              <p className="text-xs font-semibold text-slate-600 font-sans">
                支持拖放 📄 简历 .txt / .md 文件到此
              </p>
              <p className="text-[10px] text-slate-400 font-sans">
                或直接使用上方的 Demo 简历一件体验神析效果
              </p>
            </div>

            {error && (
              <div className="flex items-center gap-2.5 p-3.5 bg-red-50 rounded-xl border border-red-100 text-red-600 text-xs font-sans">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              onClick={() => handleAnalyze(resumeText)}
              disabled={loading || !resumeText.trim()}
              className="w-full font-sans text-sm font-semibold text-white bg-gradient-to-r from-primary to-primary-container hover:shadow-lg disabled:from-slate-400 disabled:to-slate-400 disabled:shadow-none hover:shadow-primary/20 py-3.5 px-6 rounded-xl transition flex items-center justify-center gap-2 cursor-pointer"
            >
              <Sparkles className="w-4.5 h-4.5 animate-pulse" />
              {loading ? "正在深度提取语义中..." : "选择并智慧神析此简历"}
            </button>
          </div>

          {/* Right Banner Introduction */}
          <div className="lg:col-span-5 bg-gradient-to-b from-primary/5 to-transparent p-8 rounded-2xl border border-primary/10 flex flex-col justify-between space-y-6">
            <div className="space-y-4">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center">
                <FileText className="w-6 h-6 text-primary" />
              </div>
              <h2 className="text-lg font-bold font-sans text-slate-800">
                AI 简历智能多维诊断科技
              </h2>
              <p className="text-xs text-slate-500 leading-relaxed font-sans">
                系统利用 **Google Gemini 1.5/3.5-Flash** 超级大语言模型架构，在毫秒级内解析求职者简历中的文字信息。不再停留在单一关键词匹配，系统可穿透工作成果、职责分量及项目高可用架构深度，完成客观人才能力解构。
              </p>

              <div className="space-y-3 pt-2">
                <div className="flex items-start gap-2.5">
                  <div className="w-1.5 h-1.5 bg-primary rounded-full mt-1.5" />
                  <p className="text-xs text-slate-600 font-sans">
                    **5大维度建模**：技术积淀、团队协同、硬软自驱、业务突破与逻辑修辞评分。
                  </p>
                </div>
                <div className="flex items-start gap-2.5">
                  <div className="w-1.5 h-1.5 bg-primary rounded-full mt-1.5" />
                  <p className="text-xs text-slate-600 font-sans">
                    **核心高频闪光点提取**：透视专利成果、名企资历、开源仓库、顶会论文等亮点。
                  </p>
                </div>
                <div className="flex items-start gap-2.5">
                  <div className="w-1.5 h-1.5 bg-primary rounded-full mt-1.5" />
                  <p className="text-xs text-slate-600 font-sans">
                    **真伪探测追问**：提取劣势细节，在后续“模拟面试”或“面试中心”一键生成针刺特问。
                  </p>
                </div>
              </div>
            </div>

            {/* Simulated Live Overlay Loader */}
            {loading && (
              <div className="bg-white/80 backdrop-blur-xl p-5 rounded-xl border border-slate-200 animate-pulse space-y-3.5 shadow-md">
                <div className="flex items-center gap-3">
                  <div className="w-4 h-4 rounded-full border-2 border-primary border-t-transparent animate-spin" />
                  <span className="text-xs font-semibold text-slate-700 font-sans">
                    {progressStep === 1 && "1. 抽取词槽及姓名学术档案..."}
                    {progressStep === 2 && "2. 评估五大职业素养维度..."}
                    {progressStep === 3 && "3. 输出HR建议及弱点清单..."}
                    {progressStep === 4 && "4. 解析完成！渲染看板中..."}
                  </span>
                </div>
                <div className="w-full bg-slate-200 h-1.5 rounded-full overflow-hidden">
                  <div
                    className="bg-primary h-full rounded-full transition-all duration-1000"
                    style={{ width: `${progressStep * 25}%` }}
                  />
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Render Results Dashboard and detailed Insights */}
      {analyzedCandidate && (
        <div className="space-y-6 animate-fade-in">
          {/* Card 1: Head of Candidate & Primary Overall Score */}
          <div className="bg-white/80 backdrop-blur-md p-6 rounded-2xl border border-white/50 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div className="flex items-center gap-4">
              <img
                src={analyzedCandidate.avatar}
                alt={analyzedCandidate.name}
                referrerPolicy="no-referrer"
                className="w-16 h-16 rounded-full object-cover border-2 border-primary/20 shadow-sm"
              />
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-bold font-sans text-slate-800">{analyzedCandidate.name}</h2>
                  <span className="text-xs bg-slate-100 text-slate-600 font-medium py-0.5 px-2.5 rounded-full border border-slate-200">
                    {analyzedCandidate.education}
                  </span>
                  <span className="text-xs bg-primary/10 text-primary font-semibold py-0.5 px-2.5 rounded-full border border-primary/10">
                    {analyzedCandidate.experienceYears}年工作资历
                  </span>
                </div>
                <p className="text-sm text-slate-500 font-sans">推荐定岗：{analyzedCandidate.role}</p>
                <div className="flex items-center gap-4 text-xs text-slate-400 mt-1 font-sans">
                  <span>✉️ {analyzedCandidate.email}</span>
                  <span>📞 {analyzedCandidate.phone}</span>
                </div>
              </div>
            </div>

            {/* AI Match circle indicator */}
            <div className="flex items-center gap-4 bg-slate-50/80 p-3 rounded-xl border border-slate-100">
              <div className="relative w-16 h-16 flex items-center justify-center">
                {/* Circular progress bar matching matchScore */}
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                  <path
                    className="text-slate-200"
                    strokeWidth="3.5"
                    stroke="currentColor"
                    fill="none"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                  <path
                    className="text-primary transition-all duration-1000"
                    strokeWidth="3.5"
                    strokeDasharray={`${analyzedCandidate.matchScore}, 100`}
                    strokeLinecap="round"
                    stroke="currentColor"
                    fill="none"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
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
                  {analyzedCandidate.matchScore >= 90 ? "极高纯度核心人选" : analyzedCandidate.matchScore >= 80 ? "高适配资深候选" : "基本适配人选"}
                </span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Visual Radars and Highlights (Col-5) */}
            <div className="lg:col-span-5 bg-white/80 backdrop-blur-md p-6 rounded-2xl border border-white/50 shadow-sm flex flex-col justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-800 font-sans border-b border-slate-100 pb-3 block">
                  五大维度能力神经映射
                </h3>
                {renderCompetencyChart(analyzedCandidate.competencies)}
              </div>

              {/* Highlights section with tiny spark chips */}
              <div className="space-y-2.5 mt-4">
                <span className="text-xs font-bold text-slate-400 block font-sans uppercase">
                  ⭐ 求职者核心亮点 (Highlights)
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

            {/* Strengths & Weaknesses Grids + AI summary (Col-7) */}
            <div className="lg:col-span-7 space-y-6">
              {/* Paragraph Summary Block */}
              <div className="bg-white/80 backdrop-blur-md p-6 rounded-2xl border border-white/50 shadow-sm space-y-3">
                <h3 className="text-sm font-bold text-slate-800 font-sans flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-primary" /> AI HR评估决策书
                </h3>
                <p className="text-xs leading-relaxed text-slate-600 font-sans italic bg-slate-50 p-4 rounded-xl border border-slate-100 border-l-4 border-l-primary shadow-sm">
                  “ {analyzedCandidate.aiSummary} ”
                </p>
                <div className="text-[10px] text-slate-400 font-sans text-right">
                  神析于： {analyzedCandidate.analyzedAt} · RecruitAI 智能招聘内核
                </div>
              </div>

              {/* Strengths and Weaknesses Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Strengths Card */}
                <div className="bg-emerald-50/40 p-5 rounded-2xl border border-emerald-100 shadow-sm space-y-3">
                  <h4 className="text-xs font-extrabold text-emerald-800 font-sans uppercase flex items-center gap-1.5">
                    <CheckCircle2 className="w-4.5 h-4.5" /> 3大核心技术与软技能优势
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

                {/* Weaknesses Card */}
                <div className="bg-red-50/40 p-5 rounded-2xl border border-red-100 shadow-sm space-y-3">
                  <h4 className="text-xs font-extrabold text-red-800 font-sans uppercase flex items-center gap-1.5">
                    <AlertCircle className="w-4.5 h-4.5" /> 2项需防范弱项/改善建议
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

              {/* Dynamic Bottom Links & Shortcuts */}
              <div className="bg-white/80 p-5 rounded-2xl border border-white/50 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-4">
                <button
                  onClick={() => {
                    setAnalyzedCandidate(null);
                    setResumeText("");
                    setTargetJob("");
                  }}
                  className="w-full sm:w-auto font-sans text-xs bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 px-5 rounded-xl transition cursor-pointer"
                >
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
                    className="flex-1 sm:flex-initial font-sans text-xs text-white bg-primary hover:bg-primary-container font-semibold py-2.5 px-5 rounded-xl transition shadow-md shadow-primary/10 flex items-center justify-center gap-1 cursor-pointer"
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
