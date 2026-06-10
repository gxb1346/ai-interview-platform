import { Candidate, CandidateStatus, Interview, ScoreCard } from "./types";

export const PRESEEDED_CANDIDATES: Candidate[] = [
  {
    id: "cand_01",
    name: "李明",
    role: "资深全栈研发专家",
    experienceYears: 6,
    education: "上海交通大学 · 软件工程硕士",
    status: CandidateStatus.WAITING_INTERVIEW,
    avatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop&crop=face",
    matchScore: 94,
    email: "liming.tech@sjtu.edu.cn",
    phone: "139-1788-0051",
    competencies: {
      technical: 9,
      communication: 8,
      problemSolving: 9,
      teamFit: 8,
      drive: 9
    },
    strengths: [
      "精通高并发 Node.js 微服务架构与现代 Web 技术栈 (TS/React)，具备全栈级微前端底座落地经验",
      "精于冷启动首屏加载性能优化，曾主导企业中台构建，核心加载耗时缩短达 55%",
      "具备突出的系统架构观，有完备的自动化流控与容灾优雅降级方案架构实战经历"
    ],
    weaknesses: [
      "涉足原生大基建物理网络层协议优化以及硬虚拟化底层核心驱动经验略有欠缺",
      "过去多参与极客敏捷技术团队运作（15人内），对200人以上超大型多元化组织矩阵的高压协同经验可以更丰富"
    ],
    highlights: [
      "GitHub 知名开源微看板库主导发起人，累计收获超过 6.2K Star 关注",
      "曾任职于国内一线互联网大厂（字节跳动），历任“杰出核心工程师”及“跨界攻坚带路人”",
      "对大语言模型 (LLM) RAG & 智能体端侧轻量化推理在 B 端应用落地有深度理解和技术原型存盘"
    ],
    aiSummary: "李明具有极其出色的现代全栈工程驾驭水平和优秀的逻辑素养。在以往的大型项目及开源领域表现极其显眼：沟通条理性佳，能够深刻理解商业目标并将技术完美契合业务，是极为难得的前端及全栈核心架构师候选人。",
    analyzedAt: "2026-06-09 18:22"
  },
  {
    id: "cand_02",
    name: "王小川",
    role: "AI/深度学习算法专家",
    experienceYears: 4,
    education: "清华大学 · 计算机博士 (AI方向)",
    status: CandidateStatus.PASSED,
    avatar: "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=150&h=150&fit=crop&crop=face",
    matchScore: 96,
    email: "xiaochuan.wang@tsinghua.org.cn",
    phone: "138-1002-3939",
    competencies: {
      technical: 10,
      communication: 7,
      problemSolving: 10,
      teamFit: 7,
      drive: 9
    },
    strengths: [
      "极强的大模型多模态对齐微调 (SFT/RLHF) 算法功底，精通 PyTorch、TensorFlow 底层改动",
      "专注于分布式千万级参数大模型冷启动切片和训练加速，对 Megatron-LM、DeepSpeed 熟若掌纹",
      "算法数学理论极限推演能力突出，已在 CVPR / NeurIPS 发表 3 篇高质量一作学术成果"
    ],
    weaknesses: [
      "沟通风格极为硬核且理性，针对非技术/运营部门业务痛点的同理心及白话沟通还可以进一步打磨",
      "在纯客户端高可用移动级嵌入式 CPU 运行优化实操链路深浅尚可，此前多侧重云端 GPU 集群"
    ],
    highlights: [
      "曾在顶级国际AI挑战赛夺得全栈视觉理解模块金牌（Top 0.05%在全球）",
      "受邀作为顶级学术会议特约交叉学科匿名审稿专家，享有极佳社区知名度",
      "手把手参与从零构建国产自研千万量级参数法律垂直垂直细分大模型微调全周期"
    ],
    aiSummary: "王小川作为清华大学毕业的AI学术派加工程派代表，在深度学习、大语言模型预训练、多卡训练提效等方面呈现了极强的学术储备和工业落地即得性。虽沟通更贴合科研属性，但对于技术自驱极其纯正，乃难得的多模太算法硬核人才。",
    analyzedAt: "2026-06-08 14:10"
  },
  {
    id: "cand_03",
    name: "张子涵",
    role: "资深产品总监",
    experienceYears: 8,
    education: "北京大学 · 心理学/信息管理双学士",
    status: CandidateStatus.INVITED,
    avatar: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&h=150&fit=crop&crop=face",
    matchScore: 89,
    email: "zihan.zhang@pku.edu.cn",
    phone: "135-0211-9210",
    competencies: {
      technical: 6,
      communication: 10,
      problemSolving: 8,
      teamFit: 9,
      drive: 8
    },
    strengths: [
      "顶尖的用户画像挖掘及闭环增长逻辑，拥有 0 到 1 多款DAU破百万国民级电商/社交应用孵化操盘手经历",
      "强悍且富有感召力的跨部门统筹表达，善于激发多工种能动性并深度挖掘敏锐痛点，极佳商业敏锐度",
      "擅长精细化数据导向运营 (A/B Test、漏斗分析)，善于运用增长飞轮撬动冷启动商业闭环"
    ],
    weaknesses: [
      "涉及微服务中间件分布式高可用、低时延高频交易系统微调底层技术逻辑的编码驾驭偏弱",
      "面对无数据指引、需要绝对直觉决策的高波动陌生蓝海业务，有时容易陷入过度严密数据论证中"
    ],
    highlights: [
      "主导发起的AI社交产品曾荣获 App Store 官方中国区“年度最佳极光应用”大奖",
      "北大辩论队前队长，跨圈层政企及海量大厂合伙人沟通桥梁，拥有无死角的职业风范",
      "曾发表行业广受关注的千字商业模式分析报告，全网总曝光量突破 1200万+"
    ],
    aiSummary: "张子涵具有令人瞩目的产品艺术创造力、无解的沟通表达以及商业洞悉视角。她能用顶尖的心理学模型对产品矩阵做结构式提纯，对商业闭环、运营攻坚有着深刻体验。对大模型技术具有敏锐的应用型思考，属于综合管理面极强的领军人选。",
    analyzedAt: "2026-06-10 09:30"
  },
  {
    id: "cand_04",
    name: "陈婉莹",
    role: "资深客户端开发工程师",
    experienceYears: 5,
    education: "同济大学 · 电子信息工程本科",
    status: CandidateStatus.REJECTED,
    avatar: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&h=150&fit=crop&crop=face",
    matchScore: 78,
    email: "wanying.chen@tongji.edu.cn",
    phone: "131-0988-7554",
    competencies: {
      technical: 7,
      communication: 8,
      problemSolving: 7,
      teamFit: 8,
      drive: 7
    },
    strengths: [
      "精通 iOS Swift & Flutter 混合架构开发，深入研读过 Dart 语言底层内存常驻监控原理解析",
      "擅长进行复杂精美动画及过渡微效调优，对移动端 UI 硬件加速渲染流程有极其深入体验"
    ],
    weaknesses: [
      "服务端高并发运维经验、SQL级分布式事务建模能力在面试提问中表现较为薄弱",
      "缺乏统筹 30 人以上复合型超级跨国混合工种项目的深度管理积淀"
    ],
    highlights: [
      "曾是知名开源框架 Flutter-Smooth 滑动流畅度调优提案的第 12 位主干合并工程师",
      "荣获2024开源创新中国大赛移动跨端应用实践金牌团队代表"
    ],
    aiSummary: "陈婉莹在移动端尤其是混合架构和高级动效渲染上，业务非常过硬。但考虑到目标招聘岗位需要承载全栈高抗压云基建、后端复杂数据库重度操作，她目前的硬性储备存在技术跨度断层。考虑到岗位相符权重，本次评分略低，属于不适配该特定职位的优秀移动端人才。",
    analyzedAt: "2026-06-07 10:15"
  }
];

export const PRESEEDED_INTERVIEWS: Interview[] = [
  {
    id: "int_01",
    candidateId: "cand_01",
    candidateName: "李明",
    role: "资深全栈研发专家",
    scheduledAt: "2026-06-11 10:00",
    status: "pending",
    suggestedQuestions: [
      "作为资深全栈研发专家，怎么看待微前端在隔离沙箱、CSS 污染冲突、资源加载开销上的极限调优空间？",
      "结合你主持的核心中台重构经历，面临的首屏核心加载链路，你具体采用了哪些量化手段使耗时缩短了 55%？",
      "假设未来面临高承载、高并发冷启动流量突袭，如何针对关键的 API 节点设计无感知的优雅降级体系？",
      "当公司的长远技术架构规划与短期商业交付压力产生极其尖锐的冲突时，作为核心研发你会怎样推进平衡？"
    ],
    notes: "一轮技术面，由技术部架构师主持，重点深挖微前端开源框架与性能提升指标细节。"
  },
  {
    id: "int_02",
    candidateId: "cand_02",
    candidateName: "王小川",
    role: "AI/深度学习算法专家",
    scheduledAt: "2026-06-12 14:30",
    status: "pending",
    suggestedQuestions: [
      "你发表关于 Megatron-LM 分布式异构切片的原理中，是如何解决 GPU 间极高吞吐下的通信开销和负载倾斜问题的？",
      "SFT 监督微调与 RLHF/DPO 各自在对齐多模态中的痛点和上限是什么？如何在低标注资源下实现鲁棒对齐？",
      "如果需要你在团队内和市场运营等非算法同事共同落地模型应用，你打算采用什么“白话文式解释”来推进他们理解幻觉率与控制逻辑？",
      "请针对你之前独立调优垂直法律大模型的闭环链路，说说在评估指标选取、自建评测集和防过拟合上的实操绝招。"
    ],
    notes: "技术专家面，深度测验学术一作论文真实度和分布式模型微调工程落地痛点。"
  }
];

export const PRESEEDED_SCORECARDS: ScoreCard[] = [
  {
    id: "sc_01",
    candidateId: "cand_02",
    candidateName: "王小川",
    role: "AI/深度学习算法专家",
    overallScore: 94,
    scores: {
      technical: 10,
      communication: 7,
      problemSolving: 10,
      culturalFit: 8
    },
    summary: "求职者在AI大模型微调和集群吞吐量化架构领域的专业功底无可置疑，展现出了接近行业顶尖的技术洞察者风范。解答多卡通信、注意力机制等问题深刻透彻、一针见血，具有极强的技术解决本领。表达偏向逻辑硬核，是一位不可多得的科学加工程双栖大牛。",
    strengths: [
      "算法底层数学本功极其雄厚，能深入到多卡分布式底层梯度融合与梯度通信耗时层进行推演优化",
      "思路清晰极度客气，在抛出极其尖锐的架构故障压测提问时，仍能够迅速整理出多级别闭环解题长短板",
      "研究自驱纯正，随时随地掌握国际学术界开源最前沿大模型微调理论的优劣端，不盲从不依赖"
    ],
    improvements: [
      "技术自谦略显单薄，在与团队非算法背景同事交流和日常业务转化上，需要更多降维用语支持",
      "对中游或偏前端的应用级封装（如移动端快速转换、微信端工程化）关注力不够，需建立长跑全局业务触角"
    ],
    verdict: "建议录用",
    evaluatedAt: "2026-06-08 15:30"
  },
  {
    id: "sc_02",
    candidateId: "cand_04",
    candidateName: "陈婉莹",
    role: "资深客户端开发工程师",
    overallScore: 78,
    scores: {
      technical: 7,
      communication: 8,
      problemSolving: 7,
      culturalFit: 8
    },
    summary: "候选人针对 iOS Flutter 动效微调与端侧高性能流畅度渲染非常熟练，拥有极强美学感受力。但由于本次招纳核心岗位侧重在超大规模后端大数据分布式容灾与核心高并发分布式事务控制，因此她对这些不熟场景的研究略显薄弱，岗位契合度一般，暂不录用。",
    strengths: [
      "富有精益求精的极客设计工匠主义，对于 60FPS 到 120FPS 下微卡顿、内存泄露追溯非常精熟",
      "待人亲和，沟通温润有礼，极富有团队合作与自驱向心力，执行敏捷性极佳"
    ],
    improvements: [
      "服务端存储引擎底层（如 MySQL 索引细节，Redis 分布式锁踩坑实录）基本功在本次高压追问下呈现了极大的拼图缺失",
      "以往开发场景高度集中于端侧，对大集群微服务云原生（Docker / K8s）的高可用设计架构认知偏少"
    ],
    verdict: "不予录用",
    evaluatedAt: "2026-06-07 11:20"
  }
];
