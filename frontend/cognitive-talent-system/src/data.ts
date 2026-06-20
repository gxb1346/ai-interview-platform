import { Candidate, CandidateStatus, Interview, ScoreCard } from "./types";

export const PRESEEDED_CANDIDATES: Candidate[] = [];

export const PRESEEDED_INTERVIEWS: Interview[] = [
  {
    id: "int_demo_1",
    candidateId: "cand_demo_1",
    candidateName: "张三",
    role: "前端架构师",
    scheduledAt: "2026-06-19 10:00",
    status: "pending",
    suggestedQuestions: ["介绍一个你主导的前端项目", "如何处理跨域问题", "微前端架构的设计要点"],
    notes: "重点考察架构设计能力和团队协作经验"
  },
  {
    id: "int_demo_2",
    candidateId: "cand_demo_2",
    candidateName: "李四",
    role: "Java后端工程师",
    scheduledAt: "2026-06-19 14:30",
    status: "pending",
    suggestedQuestions: ["JVM调优经验", "分布式事务处理方案", "高并发系统设计思路"],
    notes: "要求5年以上Java开发经验"
  },
  {
    id: "int_demo_3",
    candidateId: "cand_demo_3",
    candidateName: "王五",
    role: "产品经理",
    scheduledAt: "2026-06-20 09:00",
    status: "completed",
    suggestedQuestions: ["从0到1的产品经验", "数据驱动决策案例", "跨团队协作经验"],
  },
  {
    id: "int_demo_4",
    candidateId: "cand_demo_4",
    candidateName: "赵六",
    role: "UI设计师",
    scheduledAt: "2026-06-20 15:00",
    status: "cancelled",
    suggestedQuestions: ["设计系统搭建经验", "用户研究方法", "设计稿到开发的协作流程"],
  },
  {
    id: "int_demo_5",
    candidateId: "cand_demo_5",
    candidateName: "陈七",
    role: "DevOps工程师",
    scheduledAt: "2026-06-21 11:00",
    status: "pending",
    suggestedQuestions: ["CI/CD流水线设计", "Kubernetes运维经验", "监控告警体系建设"],
  },
  {
    id: "int_demo_6",
    candidateId: "cand_demo_6",
    candidateName: "孙八",
    role: "数据分析师",
    scheduledAt: "2026-06-22 16:00",
    status: "completed",
    suggestedQuestions: ["AB测试设计方法", "数据仓库建模经验", "机器学习的业务落地案例"],
  },
];

export const PRESEEDED_SCORECARDS: ScoreCard[] = [];
