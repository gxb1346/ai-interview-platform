package com.interview.modules.interview.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试会话（Redis 持久化，支持断点续面）
 */
public class InterviewSession implements Serializable {

    private String sessionId;
    private String candidateId;
    private String candidateName;
    private String candidateRole;
    private String resumeText;              // 简历文本（用于简历深挖）
    private String direction;               // 面试方向
    private String level;                   // 难度等级
    private String mode;                    // "text" | "voice"
    private int totalDuration;              // 总时长（分钟）
    private StageConfig stageConfig;        // 阶段时长配置
    private int followUpCount;              // 智能追问次数（默认1）
    private String status;                  // "PREPARING" | "IN_PROGRESS" | "COMPLETED" | "TERMINATED"
    private String customJD;                // 自定义 JD 文本（可选）

    /** 面试题目列表 */
    private List<InterviewQuestion> questions;

    /** 对话历史 */
    private List<InterviewMessage> messages;

    /** 已问过的题目 ID（用于去重） */
    private List<String> askedQuestionIds;

    /** 当前阶段 */
    private String currentStage;

    /** 当前对话轮数 */
    private int currentRound;

    /** 当前题目索引（独立于 round，用于正确跟踪追问与切换题目） */
    private int currentQuestionIndex;

    /** 当前题目已追问次数（切换题目后重置为 0） */
    private int followUpIndex;

    /** 时间戳 */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public InterviewSession() {
        this.questions = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.askedQuestionIds = new ArrayList<>();
        this.currentStage = StageConfig.STAGE_SELF_INTRO;
        this.currentRound = 0;
        this.currentQuestionIndex = 0;
        this.followUpIndex = 0;
        this.status = "PREPARING";
        this.followUpCount = 1;
        this.totalDuration = 60;
        this.stageConfig = new StageConfig(60);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

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
    public void setTotalDuration(int totalDuration) {
        this.totalDuration = totalDuration;
        this.stageConfig = new StageConfig(totalDuration);
    }

    public StageConfig getStageConfig() { return stageConfig; }
    public void setStageConfig(StageConfig stageConfig) { this.stageConfig = stageConfig; }

    public int getFollowUpCount() { return followUpCount; }
    public void setFollowUpCount(int followUpCount) { this.followUpCount = followUpCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomJD() { return customJD; }
    public void setCustomJD(String customJD) { this.customJD = customJD; }

    public List<InterviewQuestion> getQuestions() { return questions; }
    public void setQuestions(List<InterviewQuestion> questions) { this.questions = questions; }

    public List<InterviewMessage> getMessages() { return messages; }
    public void setMessages(List<InterviewMessage> messages) { this.messages = messages; }

    public List<String> getAskedQuestionIds() { return askedQuestionIds; }
    public void setAskedQuestionIds(List<String> askedQuestionIds) { this.askedQuestionIds = askedQuestionIds; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public int getFollowUpIndex() { return followUpIndex; }
    public void setFollowUpIndex(int followUpIndex) { this.followUpIndex = followUpIndex; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    // Helper methods
    public void addMessage(InterviewMessage message) {
        this.messages.add(message);
        this.currentRound++;
        this.updatedAt = LocalDateTime.now();
    }

    public void addQuestion(InterviewQuestion question) {
        this.questions.add(question);
        this.updatedAt = LocalDateTime.now();
    }

    public void markAnswered(String questionId) {
        if (!this.askedQuestionIds.contains(questionId)) {
            this.askedQuestionIds.add(questionId);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isQuestionAsked(String questionId) {
        return this.askedQuestionIds.contains(questionId);
    }
}
