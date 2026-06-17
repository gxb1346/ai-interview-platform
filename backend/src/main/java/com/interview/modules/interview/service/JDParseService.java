package com.interview.modules.interview.service;

import com.interview.modules.interview.model.*;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JD（职位描述）解析服务
 * LLM 动态提取面试分类并匹配共享题库
 */
@Service
public class JDParseService {

    private final ChatClient chatClient;
    private final SkillRegistryService skillRegistryService;
    private final InterviewSessionRepository sessionRepository;

    public JDParseService(ChatClient.Builder chatClientBuilder,
                          SkillRegistryService skillRegistryService,
                          InterviewSessionRepository sessionRepository) {
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个 JD 解析专家，负责分析职位描述并提取关键信息。")
                .build();
        this.skillRegistryService = skillRegistryService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 解析 JD 文本，返回结构化结果
     */
    public JDParseResult parseJD(String jdText) {
        try {
            String allDirections = String.join("、", skillRegistryService.getAllDirectionNames());

            String prompt = """
                    你是一个 JD 解析专家。请分析以下职位描述，提取：
                    1. 最匹配的面试方向（从以下列表中选择一个最接近的）：%s
                    2. 岗位所需的核心技能标签（最多5个）
                    3. 经验年限要求（年）
                    4. 关键技术栈关键词
                    
                    请以 JSON 格式返回，不要包含 Markdown 标记。
                    {
                        "matchedDirection": "方向名称",
                        "skills": ["技能1", "技能2", ...],
                        "experienceRequired": 3,
                        "techStack": ["技术1", "技术2", ...]
                    }
                    
                    JD内容：
                    ---
                    %s
                    ---
                    """.formatted(allDirections, jdText);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseResponse(response);
        } catch (Exception e) {
            System.err.println("JD 解析失败: " + e.getMessage());
            return new JDParseResult("Java后端开发", List.of("通用技能"), 3, List.of());
        }
    }

    private JDParseResult parseResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) cleaned = cleaned.substring(firstNewline).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(cleaned, JDParseResult.class);
        } catch (Exception e) {
            System.err.println("JD Parse JSON 解析失败: " + e.getMessage());
            return new JDParseResult("Java后端开发", List.of("通用技能"), 3, List.of());
        }
    }

    /**
     * 基于 JD 创建面试会话
     */
    public InterviewSession createSessionFromJD(InterviewSession session, String jdText) {
        JDParseResult result = parseJD(jdText);
        session.setDirection(result.getMatchedDirection());
        session.setCustomJD(jdText);
        session.setSessionId(UUID.randomUUID().toString());
        session.setStatus("PREPARING");
        sessionRepository.save(session);
        return session;
    }

    /**
     * JD 解析结果
     */
    public static class JDParseResult {
        private String matchedDirection;
        private List<String> skills;
        private int experienceRequired;
        private List<String> techStack;

        public JDParseResult() {}

        public JDParseResult(String matchedDirection, List<String> skills,
                             int experienceRequired, List<String> techStack) {
            this.matchedDirection = matchedDirection;
            this.skills = skills;
            this.experienceRequired = experienceRequired;
            this.techStack = techStack;
        }

        public String getMatchedDirection() { return matchedDirection; }
        public void setMatchedDirection(String matchedDirection) { this.matchedDirection = matchedDirection; }

        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }

        public int getExperienceRequired() { return experienceRequired; }
        public void setExperienceRequired(int experienceRequired) { this.experienceRequired = experienceRequired; }

        public List<String> getTechStack() { return techStack; }
        public void setTechStack(List<String> techStack) { this.techStack = techStack; }
    }
}
