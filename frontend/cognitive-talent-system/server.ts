import express from "express";
import path from "path";
import dotenv from "dotenv";
import { createServer as createViteServer } from "vite";

// AI calls using DashScope OpenAI-compatible API (通义千问 Qwen)

dotenv.config();

const app = express();
app.use(express.json({ limit: "10mb" }));

// Qwen API configuration (DashScope OpenAI-compatible)
const AI_API_KEY = process.env.AI_API_KEY || "";
const AI_MODEL = process.env.AI_MODEL || "qwen-turbo";
const AI_BASE_URL = process.env.AI_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1";

async function callQwen(messages: { role: string; content: string }[], options?: {
  temperature?: number;
  jsonMode?: boolean;
  maxTokens?: number;
  systemInstruction?: string;
}): Promise<string | null> {
  if (!AI_API_KEY) {
    console.warn("⚠️ Warning: AI_API_KEY environment variable is not defined.");
    return null;
  }

  const body: any = {
    model: AI_MODEL,
    messages: [...messages],
    temperature: options?.temperature ?? 0.7,
  };

  if (options?.systemInstruction) {
    body.messages.unshift({ role: "system", content: options.systemInstruction });
  }

  if (options?.jsonMode) {
    body.response_format = { type: "json_object" };
  }

  if (options?.maxTokens) {
    body.max_tokens = options.maxTokens;
  }

  try {
    const response = await fetch(`${AI_BASE_URL}/chat/completions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${AI_API_KEY}`,
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Qwen API error (${response.status}): ${errorText}`);
    }

    const data = await response.json();
    return data.choices?.[0]?.message?.content || null;
  } catch (err) {
    console.error("Qwen API call failed:", err);
    return null;
  }
}

// ----------------------------------------------------
// API ENDPOINTS
// ----------------------------------------------------

// 1. Resume Analysis API
app.post("/api/resume/analyze", async (req, res) => {
  const { resumeText, targetJob } = req.body;

  if (!resumeText) {
    return res.status(400).json({ error: "Missing resumeText in request body" });
  }

  const prompt = `你是一个资深的AI招聘专家与HR。请根据以下求职者的简历文本（以及可选的目标岗位名称："${targetJob || "智能适配最佳岗位"}"）进行深度解析。
请提取求职者姓名、适配岗、工作经历、最高学历、AI竞争力匹配度（0-100分，如果有目标岗位，匹配度应符合该岗位的要求；如果没有，匹配求职者自洽的岗位），并对其五大能力指标（技术深度、沟通表达、解决问题、团队契合、自驱动力，打分都在1至10分之间）进行科学测评。
请同时撰写一段极高水准的HR AI总结，列出3大核心优势、2条尚存弱点或改善建议，以及3个闪光点亮点（例如名企背景、技术开源等）。

求职者简历内容如下：
---
${resumeText}
---

请严格按照以下 JSON 格式返回，不要包含任何 Markdown 或其他说明文字：
{
  "name": "姓名",
  "role": "最适配岗位",
  "experienceYears": 年数,
  "education": "最高学历",
  "matchScore": 匹配度0-100,
  "email": "邮箱",
  "phone": "电话",
  "competencies": { "technical": 1-10, "communication": 1-10, "problemSolving": 1-10, "teamFit": 1-10, "drive": 1-10 },
  "strengths": ["优势1", "优势2", "优势3"],
  "weaknesses": ["弱点1", "弱点2"],
  "highlights": ["亮点1", "亮点2", "亮点3"],
  "aiSummary": "综合评价150-250字"
}`;

  try {
    const result = await callQwen(
      [{ role: "user", content: prompt }],
      { temperature: 0.2, jsonMode: true }
    );

    if (!result) {
      console.log("Using dynamic mock fallback for resume analysis due to missing API key");
      return res.json(generateFallbackAnalysis(resumeText, targetJob));
    }

    const parsedData = JSON.parse(result);
    res.json(parsedData);
  } catch (error: any) {
    console.error("Qwen API Error in resume analysis:", error);
    res.status(500).json({
      error: "AI Resume Analysis failed",
      detail: error.message,
      fallback: generateFallbackAnalysis(resumeText, targetJob),
    });
  }
});

// 2. AI Question Suggestions API
app.post("/api/interview/suggest-questions", async (req, res) => {
  const { candidateName, role, strengths, aiSummary } = req.body;

  const prompt = `你是一位高阶技术总监兼HR架构师。针对求职者 "${candidateName}" (应聘岗位: ${role})，
以下是该求职者的部分简历 summary 以及优势:
优势: ${JSON.stringify(strengths || [])}
HR AI总结: ${aiSummary || ""}

请针对应聘的"${role}"岗位特征，以及此求职者的个人画像，定制化地生成 5 道锋利、针对性极强的面试提问。
1. 第1题聚焦技术架构与技术瓶颈突破；
2. 第2题聚焦简历中所展现优势的真伪探测（深挖场景细节）；
3. 第3题考察复杂业务中沟通协同及技术冲突的解决思路；
4. 第4题根据其简历中潜在的短板或模糊地带进行侧面追问；
5. 第5题考察其自驱动学习和行业前沿技术敏感度。

请直接以 JSON 数组格式（["问题1", "问题2", "问题3", "问题4", "问题5"]）形式返回，不包含任何外部 Markdown 说明。`;

  try {
    const result = await callQwen(
      [{ role: "user", content: prompt }],
      { temperature: 0.7, jsonMode: true }
    );

    if (!result) {
      return res.json({
        questions: [
          `作为一名优秀的${role}，说说你过去最成功的产品或技术项目，你其中起到了什么关键作用？`,
          "你在简历提及的能力亮点中，团队协作或架构演进最具有挑战的是什么？",
          "针对你在有些领域的相对薄弱点，平时是如何通过自驱动进行系统学习和弥补的？",
          "在过往经历中，当技术选型与业务部门的进度诉求产生激烈冲突时，你通常如何妥协与说服？",
          "分享一个由于你前期考虑不足导致方案失败的段子，你后来是如何通过深度复盘挽救它的？"
        ]
      });
    }

    const parsedArray = JSON.parse(result.trim());
    res.json({ questions: Array.isArray(parsedArray) ? parsedArray : [] });
  } catch (error: any) {
    console.error("Qwen API Error in suggest-questions:", error);
    res.status(500).json({ error: error.message });
  }
});

// 3. AI Mock Interview Chat API
app.post("/api/mock-interview/chat", async (req, res) => {
  const { messages, candidateName, role } = req.body;

  if (!messages || !Array.isArray(messages)) {
    return res.status(400).json({ error: "Missing messages chat log list" });
  }

  try {
    const systemIns = `你是 RecruitAI 的AI顶级技术总监兼HR专家。目前你正在对求职者 "${candidateName}" 进行 "${role}" 岗位的模拟面试。
你需要保持极其专业、严谨但又不失亲和力的姿态。
你应该追问求职者深度技术和业务挑战、问题解决和沟通协同能力、职业规划与软实力。
请遵守以下面试守则：
1. 一次只问一个问题。绝对不要长篇大论或一次抛出两三个并列问题或连环追问。
2. 仔细阅读求职者的最近一次回答，对其进行技术性的点评、反思或简短赞许，随后根据他话语中的细节进行更深入的一轮追问。
3. 如果求职者回答非常闪烁或者过于笼统，你可以一针见血地探底，直言"你能举一个你实际做过的具体例子详细说说吗？"
4. 表现得像一个真实的高端面试官，带有技术大佬的谈吐细节，而不是机械化地念题目。`;

    const formattedMessages = messages.map((msg: any) => ({
      role: msg.sender === "candidate" ? "user" : "assistant",
      content: msg.text
    }));

    const result = await callQwen(formattedMessages, {
      temperature: 0.7,
      maxTokens: 500,
      systemInstruction: systemIns,
    });

    if (!result) {
      return res.json({
        reply: `[AI Fallback Mode] 您好 ${candidateName}，非常高兴与您面试。在应聘${role}岗位的过程中，能简单分享一下您近三年主要负责的核心业务链路与架构设计吗？`
      });
    }

    res.json({ reply: result.trim() });
  } catch (error: any) {
    console.error("Qwen API Error in mock interview chat:", error);
    res.status(500).json({ error: error.message });
  }
});

// 4. Evaluate & Score Mock Interview API
app.post("/api/mock-interview/evaluate", async (req, res) => {
  const { messages, candidateName, role } = req.body;

  if (!messages || !Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: "Missing messages transcript" });
  }

  try {
    const transcript = messages.map((m: any) => `${m.sender === "interviewer" ? "面试官" : "求职者"}: ${m.text}`).join("\n");

    const prompt = `您是高级招聘决策委员会专家。请深度评估求职者 "${candidateName}" 在岗位 "${role}" 模拟面试中的表现。
以下是完整的面试对话实录：
===
${transcript}
===

请深度评估该求职者的各项核心表现并返回严格的 JSON 格式：
- 技术深度 (technical): 满分 10 分
- 沟通表达 (communication): 满分 10 分
- 解决问题 (problemSolving): 满分 10 分
- 文化契合 (culturalFit): 满分 10 分
- 最终AI加权分 (overallScore): 满分 100 分

请客观剖析、输出：
1. 深入透彻的AI总体招聘画像分析 (summary)；
2. 3项在真实对话中得证的绝佳优势与亮点 (strengths)；
3. 2个尚待攻克提高的真实软肋或改善项 (improvements)；
4. 综合研判：建议录用、待定、不予录用 之一。

严格按照以下 JSON 格式返回，不要包含任何 Markdown 标识：
{
  "overallScore": 分数0-100,
  "scores": { "technical": 1-10, "communication": 1-10, "problemSolving": 1-10, "culturalFit": 1-10 },
  "summary": "评估全文150-300字",
  "strengths": ["优势1", "优势2", "优势3"],
  "improvements": ["改善1", "改善2"],
  "verdict": "建议录用|待定|不予录用"
}`;

    const result = await callQwen(
      [{ role: "user", content: prompt }],
      { temperature: 0.3, jsonMode: true }
    );

    if (!result) {
      return res.json(generateFallbackScoreCard(candidateName, role));
    }

    const parsedScoreCard = JSON.parse(result);
    res.json(parsedScoreCard);
  } catch (error: any) {
    console.error("Qwen scorecard evaluation fail:", error);
    res.status(500).json({
      error: "Scorecard evaluation failed",
      detail: error.message,
      fallback: generateFallbackScoreCard(candidateName, role)
    });
  }
});

// ----------------------------------------------------
// FALLBACK DYNAMIC GENERATION HELPERS
// (Used when API key is missing or system fails)
// ----------------------------------------------------

function generateFallbackAnalysis(text: string, targetJob?: string) {
  const normText = text.toLowerCase();
  let name = "张子涵";
  let role = targetJob || "前端架构师";
  let exp = 5;
  let edu = "硕士";
  let score = 88;

  if (normText.includes("李") || normText.includes("limin")) {
    name = "李明";
  } else if (normText.includes("王") || normText.includes("wang")) {
    name = "王小川";
  }

  if (normText.includes("科学") || normText.includes("ai") || normText.includes("python") || normText.includes("model")) {
    role = "AI 算法研究员";
    exp = 4;
    edu = "博士";
    score = 92;
  } else if (normText.includes("产品") || normText.includes("pm") || normText.includes("经理")) {
    role = "资深产品经理";
    exp = 7;
    edu = "本科";
    score = 85;
  }

  return {
    name,
    role,
    experienceYears: exp,
    education: edu,
    matchScore: score,
    email: `${name === "张子涵" ? "zihan.zhang" : name === "李明" ? "liming.tech" : "xiaochuan.wang"}@example.com`,
    phone: "138-1688-9902",
    competencies: {
      technical: score > 90 ? 9 : 8,
      communication: name === "李明" ? 9 : 7,
      problemSolving: 8,
      teamFit: 8,
      drive: 9,
    },
    strengths: [
      "精通高并发场景下的业务建模与底层引擎开发，代码交付质量行业领先",
      "具备突出的技术视野，积极跟踪开源动向并将最前沿大模型工程化引入自身业务之中",
      "思维框架严密清晰，表达精炼，具有强烈的跨端开发和全链路性能调优直觉"
    ],
    weaknesses: [
      "在某些极细分的深度硬件虚拟化底层协议上经验稍浅",
      "过往管理团队规模多局限于15人以内，需加强针对敏捷全栈大中型敏捷团队的组织管理张力"
    ],
    highlights: [
      "曾独立主导重构核心中台高可用模块，使核心链路耗时缩短 42%",
      "开源社区活跃贡献者，拥有超 5K+ Star 的微前端应用框架代表项目",
      "曾连续两年获得企业‘突出工程师贡献奖’及‘技术先锋导师’称号"
    ],
    aiSummary: `${name}具有极其扎实的计算机基础与大型项目交付积累。对技术充满热忱且基本功极度扎实。在面对深度分布式系统重构中展现了清晰的逻辑头脑。具备独立承载和打破跨部门资源壁垒、带领联合研发攻关的突出软硬件整合潜质，是极佳的${role}候选人，予以极力推荐。`
  };
}

function generateFallbackScoreCard(name: string, role: string) {
  return {
    overallScore: 86,
    scores: {
      technical: 8,
      communication: 9,
      problemSolving: 8,
      culturalFit: 9
    },
    summary: `在本次围绕${role}岗位的全栈模拟面试中，求职者${name}展现出了高超的综合素质。不仅沟通积极主动、逻辑主线清晰，更能在面对高阻尼场景下的真实痛点，提炼核心解题步骤。在遇到未知边界的架构追问时，态度诚恳、分析框架有章法，体现了极其宝贵的团队协作胸怀与韧性，文化价值观高度契合。`,
    strengths: [
      "沟通极其顺畅得体，答题条理清晰，善于通过‘先论点后阐述’机制组织结构，感染性强",
      "架构思考具备系统闭环模型，能够主动考虑到异常容错、限流和优雅降级等现实工程边界",
      "表现出了高昂的自驱动力，主动探索技术边界，对核心开源设计思想知其然并知其所以然"
    ],
    improvements: [
      "涉及核心中间件底层高可用的精细化参数调优策略，在实战微调经验方面还可以更丰富",
      "面对时间强压或资源极度匮乏的高频压力测试提问表现出了一丝紧绷，可以更加从容坦然"
    ],
    verdict: "建议录用"
  };
}

// ----------------------------------------------------
// MIDDLEWARE CONFIGURATION FOR VITE / PRODUCTION
// ----------------------------------------------------

async function startServer() {
  const PORT = 3000;

  if (process.env.NODE_ENV !== "production") {
    // Development mode with Vite Dev Server Middleware
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
    console.log("Vite development server middleware loaded.");
  } else {
    // Production mode - server statics
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
    console.log("Production static files serving configuration loaded.");
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`🚀 Cognitive Talent Server running on: http://0.0.0.0:${PORT}`);
  });
}

startServer();
