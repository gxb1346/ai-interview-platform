package com.interview.modules.interview.skill;

import com.interview.modules.interview.model.InterviewQuestion;
import com.interview.modules.interview.model.InterviewLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 默认面试 Skill 实现
 * 基于 Spring AI ChatClient 动态生成题目
 */
public class DefaultInterviewSkill implements InterviewSkill {

    private final String directionName;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /** 各方向的基础考察范围 */
    private static final Map<String, List<String>> DIRECTION_SCOPE = Map.ofEntries(
            Map.entry("Java后端开发", List.of("Java核心（JVM、并发、集合）", "Spring Boot/Cloud 生态",
                    "微服务架构（服务治理、熔断、限流）", "数据库（MySQL、PostgreSQL）", "Redis 与缓存设计",
                    "消息队列（Kafka、RocketMQ）", "分布式理论（CAP、BASE、一致性算法）",
                    "高可用架构设计", "性能调优与监控", "CI/CD与DevOps")),
            Map.entry("阿里后端", List.of("Java 基础与并发编程", "Spring 全家桶深度实践",
                    "Dubbo/HSF 服务框架", "Nacos/Config 配置中心", "Sentinel 流量治理",
                    "RocketMQ 消息中间件", "PolarDB/MySQL 数据库", "TDDL 分库分表",
                    "阿里云原生体系（K8s、Serverless）", "高并发秒杀/大促系统架构")),
            Map.entry("字节后端", List.of("Go/Java 双语言开发", "微服务框架（Kitex/CloudWeGo）",
                    "Redis 深度实践", "MySQL 与 ByteNDB", "Kafka 消息引擎",
                    "RPC 框架与序列化", "高并发推送/推荐系统", "分布式存储（HDFS/ByteStore）",
                    "容器化与 K8s（TCE）", "AB 测试与数据驱动开发")),
            Map.entry("腾讯后端", List.of("C++/Go/Java 后端开发", "腾讯微服务框架（TARS）",
                    "分布式存储（Ceph、HDFS）", "MySQL/TDSQL 数据库", "Redis/Memcached 缓存",
                    "消息队列（CMQ、CKafka）", "游戏后端/社交后端架构", "即时通讯（IM）系统设计",
                    "海量服务架构（QQ/微信级）", "CDN 与边缘计算")),
            Map.entry("前端工程", List.of("JavaScript/TypeScript 核心", "React/Vue 框架深度",
                    "前端工程化（Webpack/Vite）", "浏览器渲染原理与性能优化",
                    "微前端架构（qiankun、Module Federation）", "Node.js 与服务端渲染",
                    "前端监控与埋点系统", "CSS 原子化与响应式布局",
                    "跨平台开发（React Native、Flutter）", "前端安全与防御")),
            Map.entry("Python后端开发", List.of("Python 语言核心特性", "Django/FastAPI/Flask 框架",
                    "异步编程（asyncio、协程）", "ORM 与数据库交互", "Celery 任务队列",
                    "RESTful/GraphQL API 设计", "数据爬虫与清洗", "微服务与容器化",
                    "科学计算与数据分析", "部署与运维（Gunicorn、Nginx）")),
            Map.entry("算法与数据结构", List.of("数组、链表、栈、队列", "树与图（BST、Trie、并查集）",
                    "动态规划与状态压缩", "贪心算法与回溯", "排序与搜索算法",
                    "字符串匹配（KMP、AC自动机）", "设计数据结构（LRU、LFU）",
                    "海量数据处理（Bitmap、Bloom Filter）", "并发算法与无锁数据结构",
                    "算法复杂度分析与优化")),
            Map.entry("系统设计", List.of("分布式系统基础理论", "微服务拆分与治理",
                    "高可用架构（冗余、故障转移）", "高并发设计（读写分离、CQRS）",
                    "数据存储设计（SQL vs NoSQL）", "缓存架构（多级缓存、缓存策略）",
                    "消息队列与事件驱动", "分布式 ID 生成与一致性",
                    "大型系统案例（设计微博/微信/电商）", "可观测性（监控、日志、链路追踪）")),
            Map.entry("测试开发", List.of("软件测试理论（黑盒/白盒/灰盒）", "自动化测试框架（Selenium、Playwright）",
                    "接口测试（Postman、RestAssured）", "性能测试（JMeter、Locust）",
                    "单元测试与 Mock（JUnit、Mockito）", "持续集成与测试流水线",
                    "质量度量与缺陷分析", "安全测试基础", "AI 在测试中的应用",
                    "测试工具开发（自定义平台）")),
            Map.entry("AI Agent开发", List.of("LLM 基础（Transformer、Prompt Engineering）",
                    "Agent 架构（ReAct、Plan-Execute）", "工具调用（Function Calling、MCP）",
                    "RAG 增强检索生成", "多 Agent 协作框架", "Memory 与上下文管理",
                    "Agent 评估与安全", "LangChain/LlamaIndex 框架",
                    "模型微调（SFT、RLHF）", "AI 应用部署与推理优化"))
    );

    /** 各方向的基础知识库 */
    private static final Map<String, List<String>> DIRECTION_KNOWLEDGE = Map.ofEntries(
            Map.entry("Java后端开发", List.of(
                    "JVM 内存模型与垃圾回收调优", "ConcurrentHashMap 实现原理",
                    "Spring 事务传播机制", "MySQL 索引优化与慢查询分析",
                    "Redis 持久化与主从复制", "Kafka 消息可靠性保证",
                    "分布式一致性（Raft、Paxos）", "系统性能指标（QPS、TP99、RT）")),
            Map.entry("AI Agent开发", List.of(
                    "ReAct 模式原理与实现", "Function Calling 协议",
                    "向量数据库与 Embedding", "RAG 评估方法论",
                    "Prompt 设计模式（CoT、ToT）", "Agent Tool 注册与发现",
                    "多 Agent 通信协议", "AI 安全（Prompt Injection、Guardrails）"))
    );

    public DefaultInterviewSkill(String directionName, ChatClient chatClient) {
        this.directionName = directionName;
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getDirectionName() {
        return directionName;
    }

    @Override
    public String getDescription() {
        return switch (directionName) {
            case "Java后端开发" -> "Java 后端全栈面试方向，涵盖 Java 核心、Spring 生态、微服务架构与分布式系统";
            case "阿里后端" -> "阿里后端专项面试方向，侧重阿里技术栈（Dubbo、Nacos、Sentinel、RocketMQ）与高并发架构";
            case "字节后端" -> "字节后端专项面试方向，侧重字节技术栈（Kitex、CloudWeGo）与推荐/推送场景";
            case "腾讯后端" -> "腾讯后端专项面试方向，侧重 TARS、IM 系统、海量服务架构";
            case "前端工程" -> "前端工程化面试方向，涵盖 React/Vue、微前端、性能优化与工程化体系";
            case "Python后端开发" -> "Python 后端开发面试方向，涵盖 FastAPI/Django、异步编程与数据科学";
            case "算法与数据结构" -> "算法面试方向，涵盖常见算法、数据结构与海量数据处理";
            case "系统设计" -> "系统架构设计面试方向，涵盖分布式系统、高可用高并发架构设计";
            case "测试开发" -> "测试开发面试方向，涵盖自动化测试、性能测试与质量保障体系";
            case "AI Agent开发" -> "AI Agent 面试方向，涵盖 LLM、Agent 架构、RAG、多 Agent 协作";
            default -> directionName + "面试方向";
        };
    }

    @Override
    public List<String> getScopeAreas() {
        return DIRECTION_SCOPE.getOrDefault(directionName,
                List.of(directionName + "基础知识", directionName + "进阶知识", directionName + "项目实践"));
    }

    @Override
    public Map<String, Double> getDifficultyDistribution() {
        return Map.of("校招", 0.40, "中级", 0.35, "高级", 0.25);
    }

    @Override
    public List<String> getKnowledgeBase() {
        return DIRECTION_KNOWLEDGE.getOrDefault(directionName, List.of());
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getPromptTemplate() {
        return """
            你是一个专业的面试出题专家，方向为：【%s】。
            
            考察范围：
            %s
            
            参考知识库：
            %s
            
            请生成 %d 道 %s 难度的面试题，要求：
            1. 题目考察实际工作中的真实场景，而非纯理论
            2. 难度分布合理，由浅入深
            3. 每道题包含场景描述和具体问题
            4. 答案预期：考察候选人的深度理解和实际经验
            5. 避免与已有题目重复
            
            请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
            """;
    }

    @Override
    public List<InterviewQuestion> generateQuestions(int count, String level,
                                                      String stage, List<String> excludeIds) {
        try {
            String scopeStr = String.join("、", getScopeAreas());
            String knowledgeStr = getKnowledgeBase().isEmpty()
                    ? "暂无预设知识库"
                    : String.join("\n- ", getKnowledgeBase());

            String prompt = String.format(getPromptTemplate(),
                    directionName, scopeStr, knowledgeStr, count, level);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析 LLM 返回的 JSON 并包装为 InterviewQuestion
            return parseQuestions(response, count, level, stage);

        } catch (Exception e) {
            // 降级：返回预设模板问题
            return generateFallbackQuestions(count, level, stage);
        }
    }

    @SuppressWarnings("unchecked")
    private List<InterviewQuestion> parseQuestions(String response, int count,
                                                    String level, String stage) {
        // 清理 Markdown 代码块
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline).trim();
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }

        List<InterviewQuestion> questions = new ArrayList<>();
        try {
            // 尝试解析为 List<Map>
            List<Map<String, Object>> items = objectMapper.readValue(cleaned, List.class);
            for (Map<String, Object> item : items) {
                InterviewQuestion q = new InterviewQuestion();
                q.setId(UUID.randomUUID().toString());
                q.setText((String) item.getOrDefault("text", ""));
                q.setSource("SKILL");
                q.setDirection(directionName);
                q.setLevel(level);
                q.setStage(stage);
                q.setCategory((String) item.getOrDefault("category", "通用"));
                Object diffScore = item.get("difficultyScore");
                q.setDifficultyScore(diffScore instanceof Integer ? (Integer) diffScore : 5);
                questions.add(q);
            }
        } catch (Exception e) {
            System.err.println("解析 AI 出题 JSON 失败: " + e.getMessage());
            return generateFallbackQuestions(count, level, stage);
        }

        return questions;
    }

    private List<InterviewQuestion> generateFallbackQuestions(int count, String level, String stage) {
        List<InterviewQuestion> fallback = new ArrayList<>();
        String[] templates = getFallbackTemplates(level);
        for (int i = 0; i < Math.min(count, templates.length); i++) {
            InterviewQuestion q = new InterviewQuestion();
            q.setId(UUID.randomUUID().toString());
            q.setText(templates[i].formatted(directionName));
            q.setSource("SKILL");
            q.setDirection(directionName);
            q.setLevel(level);
            q.setStage(stage);
            q.setCategory("通用");
            q.setDifficultyScore(level.equals("高级") ? 8 : level.equals("中级") ? 5 : 3);
            fallback.add(q);
        }
        return fallback;
    }

    private String[] getFallbackTemplates(String level) {
        return switch (level) {
            case "校招" -> new String[]{
                    "请介绍一下 %s 方向你最熟悉的技术栈和核心原理？",
                    "在 %s 方向中，你如何理解面向对象设计的基本原则？",
                    "请举例说明你在 %s 方向做过的一个有挑战性的项目。",
                    "%s 方向中，你常用的调试和排查问题的方法有哪些？",
                    "谈谈你对 %s 方向未来技术趋势的理解。"
            };
            case "中级" -> new String[]{
                    "在 %s 方向中，请详细描述你解决过的最复杂的一个线上问题及排查过程。",
                    "基于 %s 方向，你如何设计一个高可用的系统架构？需要考虑哪些关键点？",
                    "%s 方向中，谈谈你对性能优化的理解，从方法论到具体实践。",
                    "在 %s 方向的团队协作中，你如何推动技术方案的落地和执行？",
                    "请比较 %s 方向中两种主流技术方案的优劣和适用场景。"
            };
            case "高级" -> new String[]{
                    "在 %s 方向中，你如何从 0 到 1 设计一个支撑百万级并发的系统架构？",
                    "%s 方向中，请分享一次你主导的重大技术重构或架构升级决策的过程。",
                    "基于 %s 方向，你如何建立团队的技术规范和质量保障体系？",
                    "在 %s 方向的海量数据场景下，你如何做技术选型和架构决策？",
                    "谈谈你对 %s 方向未来 3-5 年技术演进路线的判断和你的准备。"
            };
            default -> new String[]{"在 %s 方向中，请分享你的核心经验和见解。"};
        };
    }
}
