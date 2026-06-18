package com.interview.modules.interview.service;

import com.interview.modules.interview.model.InterviewQuestion;
import com.interview.modules.interview.model.InterviewLevel;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.skill.InterviewSkill;
import com.interview.modules.interview.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 并行双路出题引擎
 * 60% 简历深挖题 + 40% 方向基础题，使用虚拟线程并行生成后合并
 */
@Service
public class QuestionGeneratorService {

    private final SkillRegistry skillRegistry;
    private final InterviewSessionRepository sessionRepository;
    private final ChatClient resumeChatClient;

    /**
     * 简历深挖使用独立的 ChatClient，与 Skill 出题物理隔离
     */
    public QuestionGeneratorService(SkillRegistry skillRegistry,
                                    InterviewSessionRepository sessionRepository,
                                    ChatClient.Builder chatClientBuilder) {
        this.skillRegistry = skillRegistry;
        this.sessionRepository = sessionRepository;
        this.resumeChatClient = chatClientBuilder
                .defaultSystem("你是一个资深技术面试官，擅长深入挖掘候选人简历中的项目经历和技术细节。")
                .build();
    }

    /**
     * 并行生成面试题目
     *
     * @param resumeText    简历文本（可为空）
     * @param directionName 面试方向
     * @param level         难度等级
     * @param stage         面试阶段
     * @param candidateId   候选人 ID（用于历史去重）
     * @param totalCount    需要的总题目数
     * @return 合并后的题目列表
     */
    public List<InterviewQuestion> generateQuestions(String resumeText,
                                                      String directionName,
                                                      String level,
                                                      String stage,
                                                      String candidateId,
                                                      int totalCount) {
        // 获取历史去重 ID 列表
        List<String> excludeIds = sessionRepository.getAskedQuestionIds(candidateId, directionName);

        boolean hasResume = resumeText != null && !resumeText.isBlank();

        // 自我介绍和反问环节：不需要简历深挖，100% Skill 出题
        if (!hasResume || "selfIntro".equals(stage) || "qaRound".equals(stage)) {
            return generateSkillQuestions(directionName, level, stage, totalCount, excludeIds);
        }

        // 有简历时（techExam / projectDeep）：60% 简历深挖 + 40% 方向基础
        int skillCount = (int) Math.ceil(totalCount * 0.4);
        int resumeCount = totalCount - skillCount;

        // 使用虚拟线程并行执行两条出题路径（物理隔离 ChatClient）
        CompletableFuture<List<InterviewQuestion>> skillFuture =
                CompletableFuture.supplyAsync(() ->
                        generateSkillQuestions(directionName, level, stage, skillCount, excludeIds),
                        Executors.newVirtualThreadPerTaskExecutor());

        CompletableFuture<List<InterviewQuestion>> resumeFuture =
                CompletableFuture.supplyAsync(() ->
                        generateResumeDeepDiveQuestions(resumeText, directionName, level, stage, resumeCount),
                        Executors.newVirtualThreadPerTaskExecutor());

        // 等全部完成后合并（interleave 交错排列）
        return CompletableFuture
                .allOf(skillFuture, resumeFuture)
                .thenApply(v -> {
                    List<InterviewQuestion> skillQuestions = skillFuture.join();
                    List<InterviewQuestion> resumeQuestions = resumeFuture.join();
                    return interleaveMerge(resumeQuestions, skillQuestions);
                })
                .join();
    }

    /**
     * Skill 驱动出题
     */
    private List<InterviewQuestion> generateSkillQuestions(String directionName,
                                                            String level,
                                                            String stage,
                                                            int count,
                                                            List<String> excludeIds) {
        InterviewSkill skill = skillRegistry.getSkill(directionName);
        return skill.generateQuestions(count, level, stage, excludeIds);
    }

    /**
     * 简历深挖出题（独立 Prompt，独立 ChatClient）
     */
    private List<InterviewQuestion> generateResumeDeepDiveQuestions(String resumeText,
                                                                     String directionName,
                                                                     String level,
                                                                     String stage,
                                                                     int count) {
        try {
            String prompt = buildResumeDeepDivePrompt(resumeText, directionName, level, count, stage);
            String response = resumeChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseResumeQuestions(response, directionName, level, stage);
        } catch (Exception e) {
            System.err.println("简历深挖出题失败: " + e.getMessage());
            return generateResumeFallbackQuestions(count, level, stage);
        }
    }

    /**
     * 构建简历深挖 Prompt
     */
    private String buildResumeDeepDivePrompt(String resumeText, String direction, String level, int count, String stage) {
        String stageIntro = switch (stage) {
            case "techExam" -> "%d 道技术考察题，基于候选人的简历项目经验进行提问";
            case "projectDeep" -> "%d 道项目深挖题，深入挖掘简历中的项目细节";
            default -> "%d 道面试题";
        };
        return String.format("""
            你是一个资深技术面试官。请根据以下候选人的简历，生成 %s。
            
            面试方向：%s
            面试难度：%s
            
            要求：
            1. 题目必须紧贴简历中提到的项目经历、技术栈和具体成果
            2. 深挖候选人简历中可能的技术深度、决策过程和系统设计思路
            3. 考察候选人是否真正深入理解了自身项目的架构和技术选型
            4. 每道题应包含具体的场景引用："你在简历中提到..."
            5. 不要问通用问题，所有问题必须能从简历中找到对应的切入点
            6. 每道题的text字段必须只包含一个独立问题，严禁将多个子问题合并为一道题
            
            简历内容：
            ---
            %s
            ---
            
            请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
            """, stageIntro.formatted(count), direction, level, resumeText);
    }

    @SuppressWarnings("unchecked")
    private List<InterviewQuestion> parseResumeQuestions(String response, String direction,
                                                          String level, String stage) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) cleaned = cleaned.substring(firstNewline).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        List<InterviewQuestion> questions = new ArrayList<>();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> items = mapper.readValue(cleaned, List.class);
            for (Map<String, Object> item : items) {
                InterviewQuestion q = new InterviewQuestion();
                q.setId(UUID.randomUUID().toString());
                q.setText((String) item.getOrDefault("text", ""));
                q.setSource("RESUME_DEEP_DIVE");
                q.setDirection(direction);
                q.setLevel(level);
                q.setStage(stage);
                q.setCategory((String) item.getOrDefault("category", "项目深挖"));
                Object diff = item.get("difficultyScore");
                q.setDifficultyScore(diff instanceof Integer ? (Integer) diff : 5);
                questions.add(q);
            }
        } catch (Exception e) {
            System.err.println("解析简历深挖题 JSON 失败: " + e.getMessage());
            return generateResumeFallbackQuestions(questions.size() > 0 ? questions.size() : 3, level, stage);
        }
        return questions;
    }

    private List<InterviewQuestion> generateResumeFallbackQuestions(int count, String level, String stage) {
        List<InterviewQuestion> fallback = new ArrayList<>();
        String[] templates = {
                "在你的简历中提到了相关项目经验，请详细描述你在该项目中最有挑战性的技术难点和解决思路。",
                "请结合你简历中的项目经历，谈谈你在系统架构设计方面做过的关键决策和权衡。",
                "根据你的经验，你如何评估和选择技术方案？请举一个具体的项目案例。",
                "请描述你在过去项目中进行过的一次重大重构或优化，从决策到实施的全过程。",
                "在团队协作和项目管理方面，你有哪些实践经验？请结合具体项目说明。"
        };
        for (int i = 0; i < Math.min(count, templates.length); i++) {
            InterviewQuestion q = new InterviewQuestion();
            q.setId(UUID.randomUUID().toString());
            q.setText(templates[i]);
            q.setSource("RESUME_DEEP_DIVE");
            q.setDirection("通用");
            q.setLevel(level);
            q.setStage(stage);
            q.setCategory("项目深挖");
            q.setDifficultyScore(level.equals("高级") ? 8 : level.equals("中级") ? 5 : 3);
            fallback.add(q);
        }
        return fallback;
    }

    /**
     * 交错合并简历题和 Skill 题
     * 前几题交替出现两种类型，让面试体验更自然
     */
    private List<InterviewQuestion> interleaveMerge(List<InterviewQuestion> resumeQs,
                                                     List<InterviewQuestion> skillQs) {
        List<InterviewQuestion> merged = new ArrayList<>();
        int maxLen = Math.max(resumeQs.size(), skillQs.size());

        for (int i = 0; i < maxLen; i++) {
            if (i < resumeQs.size()) merged.add(resumeQs.get(i));
            if (i < skillQs.size()) merged.add(skillQs.get(i));
        }

        return merged;
    }
}
