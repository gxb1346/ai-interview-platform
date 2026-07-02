package com.interview.modules.interview.skill;

import com.interview.modules.interview.model.InterviewQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于文件的面试 Skill 实现
 * 从 resources/skills/{dirName}/SKILL.md 和 skill.meta.yml 加载数据
 * 使用 Spring AI ChatClient 动态生成题目
 */
@Slf4j
public class FileBasedInterviewSkill implements InterviewSkill {

    private final SkillDefinition definition;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public FileBasedInterviewSkill(SkillDefinition definition, ChatClient chatClient) {
        this.definition = definition;
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    /** 获取 Skill 定义数据（供 SkillRegistry 获取显示元数据） */
    public SkillDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getDirectionName() {
        return definition.getDirectionName();
    }

    @Override
    public String getDescription() {
        return definition.getDescription() != null
                ? definition.getDescription()
                : definition.getDirectionName() + "面试方向";
    }

    @Override
    public List<String> getScopeAreas() {
        return definition.getScopeAreas() != null && !definition.getScopeAreas().isEmpty()
                ? definition.getScopeAreas()
                : List.of(definition.getDirectionName() + "基础知识");
    }

    @Override
    public Map<String, Double> getDifficultyDistribution() {
        return Map.of("校招", 0.40, "中级", 0.35, "高级", 0.25);
    }

    @Override
    public List<String> getKnowledgeBase() {
        if (definition.getKnowledgeBase() != null && !definition.getKnowledgeBase().isEmpty()) {
            return List.of(definition.getKnowledgeBase());
        }
        return List.of();
    }

    @Override
    public String getVersion() {
        return definition.getVersion() != null ? definition.getVersion() : "2.0.0";
    }

    @Override
    public String getPromptTemplate() {
        // 文件加载的 Skill 使用 SKILL.md 的系统提示词作为模板
        if (definition.getSystemPrompt() != null && !definition.getSystemPrompt().isEmpty()) {
            return definition.getSystemPrompt();
        }
        // 兜底模板
        return """
            你是一个专业的面试出题专家，方向为：【%s】。
            
            考察范围：
            %s
            
            参考知识库：
            %s
            
            请生成 %d 道 %s 难度的面试题，要求：
            1. 每道题的text字段必须只包含一个独立问题，严禁将多个子问题合并为一道题
            2. 题目考察实际工作中的真实场景，而非纯理论
            3. 难度分布合理，由浅入深
            4. 每道题包含场景描述和具体问题
            5. 答案预期：考察候选人的深度理解和实际经验
            6. 避免与已有题目重复
            
            请以 JSON 数组格式返回，数组中必须包含 %d 个元素，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
            """;
    }

    @Override
    public List<InterviewQuestion> generateQuestions(int count, String level,
                                                      String stage, List<String> excludeIds) {
        // 自我介绍环节：使用非技术题目
        if ("selfIntro".equals(stage)) {
            return generateSelfIntroQuestions(count, level, stage);
        }

        try {
            String scopeStr = String.join("、", getScopeAreas());
            String knowledgeStr = getKnowledgeBase().isEmpty()
                    ? "暂无预设知识库"
                    : String.join("\n", getKnowledgeBase());

            String prompt = buildStagePrompt(count, level, stage, scopeStr, knowledgeStr);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            List<InterviewQuestion> parsed = parseQuestions(response, count, level, stage);
            if (parsed.size() < Math.max(1, count / 2)) {
                log.warn("AI出题数量不足({}/{})，使用fallback", parsed.size(), count);
                return generateFallbackQuestions(count, level, stage);
            }
            return parsed;

        } catch (Exception e) {
            log.error("AI出题失败: {}", e.getMessage());
            return generateFallbackQuestions(count, level, stage);
        }
    }

    /**
     * 根据面试阶段构建出题 prompt
     */
    private String buildStagePrompt(int count, String level, String stage,
                                     String scopeStr, String knowledgeStr) {
        // 使用 SKILL.md 的系统提示词作为基础
        String systemPrompt = definition.getSystemPrompt() != null
                ? definition.getSystemPrompt() : "";

        if (stage == null) stage = "";

        return switch (stage) {
            case "selfIntro" -> String.format("""
                你是一个专业的面试官。请为【%s】方向的面试生成 %d 道自我介绍环节的引导问题。
                面试难度：%s
                
                要求：
                1. 题目应引导候选人介绍自己的技术背景、工作经历和职业规划
                2. 侧重了解候选人的项目经验概览、技术成长路径
                3. 考察候选人的表达能力和自我认知
                4. 不要涉及具体技术细节的深挖
                5. 问题应友好、开放
                
                请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
                """, definition.getDirectionName(), count, level);
            case "projectDeep" -> String.format("""
                %s
                
                你是一个资深技术面试官。请为【%s】方向的 %s 难度面试生成 %d 道项目深挖环节的题目。
                
                考察范围：
                %s
                
                参考知识库：
                %s
                
                要求：
                1. 每道题的text字段必须只包含一个独立问题，严禁将多个子问题合并为一道题
                2. 题目应深入挖掘候选人简历中的项目经历和架构决策
                3. 侧重考察候选人在实际项目中的技术深度和系统设计能力
                4. 关注候选人在项目中的角色、贡献和解决问题的思路
                5. 每道题应引导候选人描述具体的技术方案和权衡过程
                6. 不要出纯理论题，要结合项目实践
                
                请以 JSON 数组格式返回，数组中必须包含 %d 个元素，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
                CRITICAL: 每个元素必须是一道独立的题目，不能有任何两道题共用同一个场景描述。
                """, systemPrompt, definition.getDirectionName(), level, count, scopeStr, knowledgeStr, count);
            case "qaRound" -> String.format("""
                你是一个专业的面试官。请为【%s】方向的面试生成 %d 道反问环节的引导提示。
                面试难度：%s
                
                考察范围：
                %s
                
                要求：
                1. 每道题必须是一个独立的引导提示，严禁合并多个话题
                2. 题目应模拟候选人向面试官提问的场景
                3. 引导候选人主动提问，考察其对岗位的思考深度和主动性
                4. 问题方向包括：技术选型、团队协作方式、职业发展路径、业务方向等
                5. 不要出技术考察题，而是提供"候选人可以向面试官提问"的示例问题
                6. 每道题应以"你可以这样提问："开头
                
                请以 JSON 数组格式返回，数组中必须包含 %d 个元素，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
                """, definition.getDirectionName(), count, level, scopeStr, count);
            default -> String.format("""
                %s
                
                现在请为【%s】方向生成 %d 道 %s 难度的面试题。
                
                考察范围：
                %s
                
                参考知识库：
                %s
                
                请严格以上述面试官角色设定和考察范围为基础，生成题目。要求：
                1. 每道题的text字段必须只包含一个独立问题
                2. 题目考察实际工作中的真实场景
                3. 难度分布合理，由浅入深
                4. 每道题包含场景描述和具体问题
                5. 避免与已有题目重复
                
                请以 JSON 数组格式返回，数组中必须包含 %d 个元素，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
                """, systemPrompt, definition.getDirectionName(), count, level, scopeStr, knowledgeStr, count);
        };
    }

    private List<InterviewQuestion> generateSelfIntroQuestions(int count, String level, String stage) {
        List<InterviewQuestion> questions = new ArrayList<>();
        String[] templates = {
            "请简单介绍一下你自己，包括你的教育背景和所学专业。",
            "你在学校或工作中最让你有成就感的一件事情是什么？",
            "你对自己未来的职业发展有什么规划和期望？",
            "请谈谈你在团队合作中通常扮演什么角色，以及你是如何与团队协作的。",
            "你平时通过什么方式学习和提升自己的技术能力？",
            "请分享一下你为什么选择这个行业，是什么让你对这个方向感兴趣？",
            "在你过往的经历中，有没有遇到过印象深刻的困难？你是如何应对的？",
            "你希望从下一份工作中获得什么？"
        };
        int qCount = Math.min(count, templates.length);
        for (int i = 0; i < qCount; i++) {
            InterviewQuestion q = new InterviewQuestion();
            q.setId(UUID.randomUUID().toString());
            q.setText(templates[i]);
            q.setSource("SKILL");
            q.setDirection(definition.getDirectionName());
            q.setLevel(level);
            q.setStage(stage);
            q.setCategory("自我介绍");
            q.setDifficultyScore(1);
            questions.add(q);
        }
        return questions;
    }

    @SuppressWarnings("unchecked")
    private List<InterviewQuestion> parseQuestions(String response, int count,
                                                    String level, String stage) {
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
            List<Map<String, Object>> items = objectMapper.readValue(cleaned, List.class);
            for (Map<String, Object> item : items) {
                InterviewQuestion q = new InterviewQuestion();
                q.setId(UUID.randomUUID().toString());
                q.setText((String) item.getOrDefault("text", ""));
                q.setSource("SKILL");
                q.setDirection(definition.getDirectionName());
                q.setLevel(level);
                q.setStage(stage);
                q.setCategory((String) item.getOrDefault("category", "通用"));
                Object diffScore = item.get("difficultyScore");
                q.setDifficultyScore(diffScore instanceof Integer ? (Integer) diffScore : 5);
                questions.add(q);
            }
        } catch (Exception e) {
            log.warn("解析 AI 出题 JSON 失败: {}", e.getMessage());
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
            q.setText(templates[i].formatted(definition.getDirectionName()));
            q.setSource("SKILL");
            q.setDirection(definition.getDirectionName());
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