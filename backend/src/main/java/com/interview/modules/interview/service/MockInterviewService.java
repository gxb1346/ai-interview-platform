package com.interview.modules.interview.service;

import com.interview.modules.interview.model.*;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
        int selfIntroCount = calculateQuestionCount(session, StageConfig.STAGE_SELF_INTRO);
        try {
            questions = questionGenerator.generateQuestions(
                    session.getResumeText(),
                    session.getDirection(),
                    session.getLevel(),
                    StageConfig.STAGE_SELF_INTRO,
                    session.getCandidateId(),
                    selfIntroCount
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
        // 每次回答递增轮次，确保 round 从 1 开始（0 是面试官开场白）
        currentRound++;
        session.setCurrentRound(currentRound);

        // 记录候选人回答
        InterviewMessage answerMsg = new InterviewMessage(
                UUID.randomUUID().toString(),
                "candidate",
                candidateAnswer
        );
        answerMsg.setStage(session.getCurrentStage());
        answerMsg.setRoundNumber(currentRound);
        session.addMessage(answerMsg);

        // 标记当前题目为已答（仅初次作答时标记，追问不重复标记）
        int qi = session.getCurrentQuestionIndex();
        int fi = session.getFollowUpIndex();
        if (qi >= 0 && qi < session.getQuestions().size() && fi == 0) {
            InterviewQuestion currentQ = session.getQuestions().get(qi);
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
     * 使用 currentQuestionIndex 独立跟踪题目进度，与 round 解耦
     */
    private String generateReply(InterviewSession session, String candidateAnswer, int round) {
        String currentStage = session.getCurrentStage();
        List<InterviewQuestion> questions = session.getQuestions();
        int questionIndex = session.getCurrentQuestionIndex();

        // 自我介绍和反问环节：不追问，每题回答后直接过渡到下一题
        boolean isDirectStage = StageConfig.STAGE_SELF_INTRO.equals(currentStage)
                || StageConfig.STAGE_QA_ROUND.equals(currentStage);

        // 当前阶段题目已全部用完，进入下一阶段
        if (questionIndex >= questions.size()) {
            return advanceStage(session);
        }

        InterviewQuestion currentQ = questions.get(questionIndex);

        if (isDirectStage) {
            if (StageConfig.STAGE_QA_ROUND.equals(currentStage)) {
                // 反问环节：检查候选人是否表示没有问题了
                if (isEndingMessage(candidateAnswer)) {
                    // 结束反问环节，进入COMPLETED
                    return advanceStage(session);
                }
                // 使用AI回答候选人的问题，不推进题目索引（保持开放状态）
                try {
                    String answer = followUpService.generateQaAnswer(candidateAnswer, currentQ);
                    return answer;
                } catch (Exception e) {
                    // AI失败时fallback
                    return "好的。如果你还有其他问题，可以继续问。";
                }
            } else {
                // selfIntro: 直接过渡到下一题
                int nextIdx = questionIndex + 1;
                session.setCurrentQuestionIndex(nextIdx);
                session.setFollowUpIndex(0);
                if (nextIdx < questions.size()) {
                    return getFallbackTransition(questions.get(nextIdx), currentStage, nextIdx + 1);
                }
                return advanceStage(session);
            }
        }

        // 技术考察 / 项目深挖：处理追问
        int followUpDepth = session.getFollowUpCount();
        int askedCount = session.getFollowUpIndex();

        // 如果还有剩余追问次数，生成追问
        if (followUpService.shouldContinueFollowUp(followUpDepth, askedCount)) {
            session.setFollowUpIndex(askedCount + 1);
            return followUpService.generateFollowUp(candidateAnswer, currentQ, askedCount + 1, currentStage);
        }

        // 追问已用完（或未配置追问），过渡到下一题
        int nextIdx = questionIndex + 1;
        session.setCurrentQuestionIndex(nextIdx);
        session.setFollowUpIndex(0);
        if (nextIdx < questions.size()) {
            InterviewQuestion nextQ = questions.get(nextIdx);
            int qNumber = nextIdx + 1;
            try {
                return followUpService.generateTransition(candidateAnswer, currentQ, nextQ, currentStage, qNumber);
            } catch (Exception e) {
                return getFallbackTransition(nextQ, currentStage, qNumber);
            }
        }

        // 当前阶段题目已全部完成，进入下一阶段
        return advanceStage(session);
    }

    /**
     * 检测候选人是否表示结束当前环节
     */
    private boolean isEndingMessage(String message) {
        if (message == null || message.isBlank()) return false;
        String t = message.trim();
        return t.equals("没有了") || t.equals("没有") || t.equals("暂时没有")
            || t.contains("没有问题了") || t.contains("没问题了")
            || t.equals("结束") || t.equals("不问了")
            || t.contains("就到这里") || t.contains("没有其他");
    }

    private String getFallbackTransition(InterviewQuestion nextQ, String stage, int questionNumber) {
        String prefix = "第" + questionNumber + "题：";
        if (StageConfig.STAGE_SELF_INTRO.equals(stage)) {
            return prefix + nextQ.getText();
        }
        if (StageConfig.STAGE_QA_ROUND.equals(stage)) {
            return "好的。如果你还有其他问题，可以继续问。\n\n" + prefix + nextQ.getText();
        }
        return prefix + "\n\n" + nextQ.getText();
    }

    private String advanceStage(InterviewSession session) {
        String currentStage = session.getCurrentStage();
        String nextStage = StageConfig.getNextStage(currentStage);

        if (nextStage == null) {
            session.setStatus("COMPLETED");
            session.setCompletedAt(java.time.LocalDateTime.now());
            sessionRepository.save(session);
            return "所有面试环节已结束。感谢你的参与，系统正在生成评估报告...";
        }

        session.setCurrentStage(nextStage);
        session.setCurrentRound(0);
        session.setCurrentQuestionIndex(0);
        session.setFollowUpIndex(0);

        List<InterviewQuestion> stageQuestions = generateStageQuestions(session, nextStage);
        // 替换为新阶段题目，避免旧阶段题目被重复问
        session.setQuestions(stageQuestions);

        String transitionMsg = getStageTransitionMessage(nextStage, session.getCandidateName());
        // 在过渡消息中追加第一道题并明确标注，避免用户先回复无意义的话
        if (!stageQuestions.isEmpty()) {
            transitionMsg += "\n\n第一题：" + stageQuestions.get(0).getText();
        }
        InterviewMessage msg = new InterviewMessage(
                UUID.randomUUID().toString(),
                "interviewer",
                transitionMsg
        );
        msg.setStage(nextStage);
        msg.setRoundNumber(0);
        session.addMessage(msg);

        sessionRepository.save(session);
        return transitionMsg;
    }

    private List<InterviewQuestion> generateStageQuestions(InterviewSession session, String stage) {
        int questionCount = calculateQuestionCount(session, stage);
        try {
            return questionGenerator.generateQuestions(
                    session.getResumeText(),
                    session.getDirection(),
                    session.getLevel(),
                    stage,
                    session.getCandidateId(),
                    questionCount
            );
        } catch (Exception e) {
            System.err.println("[" + stage + "] 出题失败，使用 fallback: " + e.getMessage());
            List<InterviewQuestion> fallbacks = new java.util.ArrayList<>();
            String[][] defaultQs = getStageDefaultQuestions(stage);
            for (String[] q : defaultQs) {
                InterviewQuestion question = new InterviewQuestion(
                        UUID.randomUUID().toString(), q[0], "SKILL", session.getDirection(), session.getLevel()
                );
                question.setCategory(q[1]);
                question.setDifficultyScore(Integer.parseInt(q[2]));
                question.setStage(stage);
                fallbacks.add(question);
            }
            return fallbacks;
        }
    }

    private String getStageTransitionMessage(String nextStage, String candidateName) {
        return switch (nextStage) {
            case StageConfig.STAGE_SELF_INTRO ->
                    String.format("%s，你好。首先请做一个简短的自我介绍，让我们了解一下你的背景和经历。", candidateName);
            case StageConfig.STAGE_TECH_EXAM ->
                    "好的，自我介绍环节先到这里。接下来我们进入技术考察环节，会涉及一些专业方向的问题。";
            case StageConfig.STAGE_PROJECT_DEEP ->
                    "技术考察环节先到这里。接下来我们聊聊项目经验，我会对你提到的项目做一些深入的了解。";
            case StageConfig.STAGE_QA_ROUND ->
                    "项目经验就先聊到这里。最后是自由提问环节，如果你有什么想了解的，无论是团队、技术还是公司方面，都可以随时提出来。";
            default -> "好的，我们进入下一环节。";
        };
    }

    private String[][] getStageDefaultQuestions(String stage) {
        return switch (stage) {
            case StageConfig.STAGE_SELF_INTRO -> new String[][]{
                {"请简单介绍一下你自己的技术背景和职业经历。", "自我介绍", "1"},
                {"你在过往的工作中最引以为豪的项目是什么？", "自我介绍", "2"},
                {"你对自己的职业发展有什么规划？", "自我介绍", "1"},
                {"请描述你在团队中通常扮演什么角色。", "自我介绍", "1"}
            };
            case StageConfig.STAGE_TECH_EXAM -> new String[][]{
                {"请谈谈你对微服务架构的理解和实践经验。", "基础", "3"},
                {"在分布式系统中，你是如何处理数据一致性问题？", "分布式", "6"},
                {"请描述一次你解决复杂线上问题的经历。", "经验", "5"},
                {"你对数据库性能优化有什么实践经验？", "数据库", "4"}
            };
            case StageConfig.STAGE_PROJECT_DEEP -> new String[][]{
                {"请详细介绍你简历中最有技术挑战的一个项目。", "项目挖深", "5"},
                {"在技术选型时你做过哪些关键的决策和权衡？", "项目挖深", "6"},
                {"请描述你的项目中一次重要的重构或优化过程。", "项目挖深", "5"},
                {"在项目推进过程中遇到最大困难是什么？如何解决的？", "项目挖深", "4"}
            };
            case StageConfig.STAGE_QA_ROUND -> new String[][]{
                {"你有什么问题想了解我们的团队或技术栈吗？", "反问", "1"},
                {"对于你的岗位，你最关注哪些方面？", "反问", "1"},
                {"你希望从未来的工作中获得什么？", "反问", "1"},
                {"你有什么职业发展的期望或目标想和我们分享？", "反问", "1"}
            };
            default -> new String[][]{
                {"请继续分享你的见解。", "综合", "1"},
                {"这个方向你有哪些深入的实践经验？", "综合", "3"},
                {"你如何看待这个领域的技术发展趋势？", "综合", "3"},
                {"请描述你解决复杂问题的方法论。", "综合", "4"}
            };
        };
    }

    public List<InterviewSession> getActiveSessions(String candidateId) {
        return sessionRepository.findActiveByCandidateId(candidateId);
    }

    public InterviewSession resumeSession(String sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("面试会话不存在: " + sessionId));

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new RuntimeException("只能恢复进行中的面试会话");
        }

        InterviewMessage resumeMsg = new InterviewMessage(
                UUID.randomUUID().toString(),
                "interviewer",
                String.format("欢迎回来！你的面试已恢复，当前阶段：%s。请继续你的回答。",
                        StageConfig.STAGE_LABELS.getOrDefault(session.getCurrentStage(), session.getCurrentStage()))
        );
        resumeMsg.setStage(session.getCurrentStage());
        resumeMsg.setRoundNumber(session.getCurrentRound());
        session.addMessage(resumeMsg);

        sessionRepository.save(session);
        return session;
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
     * 根据阶段时长和追问次数动态计算题目数量
     */
    private int calculateQuestionCount(InterviewSession session, String stage) {
        int stageMinutes = session.getStageConfig().getStageMinutes(stage);
        int followUpCount = session.getFollowUpCount();

        if (StageConfig.STAGE_SELF_INTRO.equals(stage)) {
            // 自我介绍：每题约1.5分钟，最少2题，最多6题
            int count = (int) Math.round(stageMinutes / 1.5);
            return Math.max(2, Math.min(6, count));
        } else if (StageConfig.STAGE_QA_ROUND.equals(stage)) {
            // 反问环节：每题约1分钟，最少2题，最多6题
            int count = (int) Math.round(stageMinutes / 1.0);
            return Math.max(2, Math.min(6, count));
        } else {
            // 技术考察/项目深挖：每题约2 + followUpCount*1.5 分钟
            double minutesPerQuestion = 2.0 + followUpCount * 1.5;
            int count = (int) Math.round(stageMinutes / minutesPerQuestion);
            return Math.max(2, Math.min(8, count));
        }
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
