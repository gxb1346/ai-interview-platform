package com.interview.modules.interview.service;

import com.interview.modules.interview.model.*;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 模拟面试主流程编排服务
 * 统筹出题、对话、追问、结束全流程
 */
@Service
public class MockInterviewService {

    private final QuestionGeneratorService questionGenerator;
    private final FollowUpService followUpService;
    private final JDParseService jdParseService;
    private final DirectionRecommendService directionRecommendService;
    private final InterviewSessionRepository sessionRepository;

    public MockInterviewService(QuestionGeneratorService questionGenerator,
                                FollowUpService followUpService,
                                JDParseService jdParseService,
                                DirectionRecommendService directionRecommendService,
                                InterviewSessionRepository sessionRepository) {
        this.questionGenerator = questionGenerator;
        this.followUpService = followUpService;
        this.jdParseService = jdParseService;
        this.directionRecommendService = directionRecommendService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 创建新的面试会话
     */
    public InterviewSession createSession(CreateSessionRequest request) {
        InterviewSession session = new InterviewSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setCandidateId(request.getCandidateId());
        session.setCandidateName(request.getCandidateName());
        session.setCandidateRole(request.getCandidateRole());
        session.setResumeText(request.getResumeText());
        session.setDirection(request.getDirection());
        session.setLevel(request.getLevel());
        session.setMode(request.getMode());
        session.setTotalDuration(request.getTotalDuration());
        session.setFollowUpCount(request.getFollowUpCount());
        session.setStatus("PREPARING");

        // 自定义 JD 处理
        if (request.getCustomJD() != null && !request.getCustomJD().isBlank()) {
            jdParseService.createSessionFromJD(session, request.getCustomJD());
        }

        sessionRepository.save(session);
        return session;
    }

    /**
     * 开始面试：生成题目
     */
    public InterviewSession startInterview(String sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("面试会话不存在: " + sessionId));

        // 确保 Redis 反序列化后的集合不为 null
        if (session.getQuestions() == null) {
            session.setQuestions(new java.util.ArrayList<>());
        }
        if (session.getMessages() == null) {
            session.setMessages(new java.util.ArrayList<>());
        }
        if (session.getAskedQuestionIds() == null) {
            session.setAskedQuestionIds(new java.util.ArrayList<>());
        }

        List<InterviewQuestion> questions;
        try {
            questions = questionGenerator.generateQuestions(
                    session.getResumeText(),
                    session.getDirection(),
                    session.getLevel(),
                    StageConfig.STAGE_TECH_EXAM,
                    session.getCandidateId(),
                    8  // 默认生成 8 道题
            );
        } catch (Exception e) {
            // AI 出题失败时使用内置 fallback 题目
            questions = getFallbackQuestions(session.getDirection(), session.getLevel());
            System.err.println("AI 出题失败，使用 fallback 题目: " + e.getMessage());
        }

        session.setQuestions(questions);
        session.setStatus("IN_PROGRESS");
        session.setCurrentRound(0);
        session.setCurrentStage(StageConfig.STAGE_SELF_INTRO);

        // 添加面试官开场白
        InterviewMessage welcomeMsg = new InterviewMessage(
                UUID.randomUUID().toString(),
                "interviewer",
                String.format(
                        "您好，%s。我是 RecruitAI 的 AI 面试官。今天我们将围绕「%s」方向进行一场 %s 难度的模拟面试。" +
                        "面试总时长约 %d 分钟，共分四个阶段：自我介绍、技术考察、项目深挖和反问环节。让我们先从自我介绍开始吧。",
                        session.getCandidateName(),
                        session.getDirection(),
                        session.getLevel(),
                        session.getTotalDuration()
                )
        );
        welcomeMsg.setStage(StageConfig.STAGE_SELF_INTRO);
        welcomeMsg.setRoundNumber(0);
        session.addMessage(welcomeMsg);

        sessionRepository.save(session);
        return session;
    }

    /**
     * AI 出题失败时的 fallback 题目
     */
    private List<InterviewQuestion> getFallbackQuestions(String direction, String level) {
        List<InterviewQuestion> fallbacks = new java.util.ArrayList<>();
        String[][] defaultQuestions = {
            {"请简单介绍一下你自己和技术背景。", "selfIntro", "基础", "1"},
            {"你在过去项目中遇到的最大技术挑战是什么？", "techExam", "综合", "2"},
            {"你如何保持技术知识的更新？", "techExam", "综合", "3"},
            {"请描述一个你主导设计的系统架构。", "projectDeep", "综合", "4"},
            {"你在团队协作中遇到过哪些困难？", "projectDeep", "综合", "5"},
            {"你对这个方向的未来发展趋势有什么看法？", "techExam", "综合", "6"},
            {"你有什么问题想问我们？", "qaRound", "综合", "7"},
            {"请评价一下自己的技术优势和提升空间。", "techExam", "综合", "8"}
        };
        for (String[] q : defaultQuestions) {
            InterviewQuestion question = new InterviewQuestion(
                    UUID.randomUUID().toString(), q[0], direction, level, q[1]
            );
            question.setCategory(q[2]);
            question.setDifficultyScore(Integer.parseInt(q[3]));
            fallbacks.add(question);
        }
        return fallbacks;
    }

    /**
     * 处理候选人回答并生成面试官回复
     */
    public InterviewSession processAnswer(String sessionId, String candidateAnswer) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("面试会话不存在: " + sessionId));

        // 确保 Redis 反序列化后的集合不为 null
        if (session.getQuestions() == null) {
            session.setQuestions(new java.util.ArrayList<>());
        }
        if (session.getMessages() == null) {
            session.setMessages(new java.util.ArrayList<>());
        }
        if (session.getAskedQuestionIds() == null) {
            session.setAskedQuestionIds(new java.util.ArrayList<>());
        }

        int currentRound = session.getCurrentRound();

        // 记录候选人回答
        InterviewMessage answerMsg = new InterviewMessage(
                UUID.randomUUID().toString(),
                "candidate",
                candidateAnswer
        );
        answerMsg.setStage(session.getCurrentStage());
        answerMsg.setRoundNumber(currentRound);
        session.addMessage(answerMsg);

        // 标记当前题目为已答
        if (currentRound - 1 < session.getQuestions().size()) {
            InterviewQuestion currentQ = session.getQuestions().get(currentRound - 1);
            session.markAnswered(currentQ.getId());
        }

        // 生成面试官回复（智能追问或下一题）
        String interviewerReply = generateReply(session, candidateAnswer, currentRound);
        InterviewMessage replyMsg = new InterviewMessage(
                UUID.randomUUID().toString(),
                "interviewer",
                interviewerReply
        );
        replyMsg.setStage(session.getCurrentStage());
        replyMsg.setRoundNumber(currentRound + 1);
        session.addMessage(replyMsg);

        sessionRepository.save(session);
        return session;
    }

    /**
     * 生成面试官回复
     */
    private String generateReply(InterviewSession session, String candidateAnswer, int round) {
        // 检查是否需要对当前题目进行追问
        if (round > 0 && session.getQuestions().size() >= round) {
            InterviewQuestion currentQ = session.getQuestions().get(round - 1);
            int followUpDepth = session.getFollowUpCount();

            // 获取该题目已经追问了几次
            int askedCount = countFollowUpsForQuestion(session, currentQ.getId());

            if (followUpService.shouldContinueFollowUp(followUpDepth, askedCount)) {
                // 生成追问
                return followUpService.generateFollowUp(candidateAnswer, currentQ, askedCount + 1);
            }
        }

        // 进入下一题
        int nextQuestionIndex = round - session.getFollowUpCount();
        if (nextQuestionIndex >= 0 && nextQuestionIndex < session.getQuestions().size()) {
            InterviewQuestion nextQ = session.getQuestions().get(nextQuestionIndex);
            InterviewQuestion currentQ = session.getQuestions().get(Math.min(round - 1, session.getQuestions().size() - 1));
            try {
                return followUpService.generateTransition(candidateAnswer, currentQ, nextQ);
            } catch (Exception e) {
                return "感谢你的回答。接下来我们进入下一题：\n\n" + nextQ.getText();
            }
        }

        // 所有题目问完，准备进入下一阶段或结束
        return advanceStage(session);
    }

    private int countFollowUpsForQuestion(InterviewSession session, String questionId) {
        return (int) session.getMessages().stream()
                .filter(m -> "interviewer".equals(m.getSender()))
                .filter(m -> m.getRoundNumber() > 0)
                .count();
    }

    private String advanceStage(InterviewSession session) {
        session.setStatus("COMPLETED");
        session.setCompletedAt(java.time.LocalDateTime.now());
        sessionRepository.save(session);

        return "所有面试环节已结束。感谢你的参与，系统正在生成评估报告...";
    }

    /**
     * 结束面试
     */
    public InterviewSession endInterview(String sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("面试会话不存在: " + sessionId));
        session.setStatus("COMPLETED");
        session.setCompletedAt(java.time.LocalDateTime.now());
        sessionRepository.save(session);
        return session;
    }

    /**
     * 获取面试会话
     */
    public Optional<InterviewSession> getSession(String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /**
     * 获取候选人历史会话
     */
    public List<InterviewSession> getCandidateSessions(String candidateId) {
        return sessionRepository.findByCandidateId(candidateId);
    }

    /**
     * 推荐面试方向
     */
    public List<DirectionRecommendService.DirectionMatch> recommendDirections(String resumeText) {
        return directionRecommendService.recommend(resumeText);
    }

    /**
     * 解析 JD
     */
    public JDParseService.JDParseResult parseJD(String jdText) {
        return jdParseService.parseJD(jdText);
    }

    /**
     * 创建会话请求 DTO
     */
    public static class CreateSessionRequest {
        private String candidateId;
        private String candidateName;
        private String candidateRole;
        private String resumeText;
        private String direction;
        private String level;
        private String mode;
        private int totalDuration;
        private int followUpCount;
        private String customJD;

        // Getters and Setters
        public String getCandidateId() { return candidateId; }
        public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

        public String getCandidateRole() { return candidateRole; }
        public void setCandidateRole(String candidateRole) { this.candidateRole = candidateRole; }

        public String getResumeText() { return resumeText; }
        public void setResumeText(String resumeText) { this.resumeText = resumeText; }

        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public int getTotalDuration() { return totalDuration; }
        public void setTotalDuration(int totalDuration) { this.totalDuration = totalDuration; }

        public int getFollowUpCount() { return followUpCount; }
        public void setFollowUpCount(int followUpCount) { this.followUpCount = followUpCount; }

        public String getCustomJD() { return customJD; }
        public void setCustomJD(String customJD) { this.customJD = customJD; }
    }
}
