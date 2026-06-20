package com.interview.modules.interview.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试对话消息
 */
public class InterviewMessage implements Serializable {

    private String id;
    private String sender;      // "interviewer" | "candidate"
    private String text;
    private String stage;       // 所属面试阶段
    private int roundNumber;    // 第几轮对话
    private Integer score;      // 实时评分（仅候选人回答有值，0-100）
    private String scoreFeedback; // 评分简要反馈
    private LocalDateTime timestamp;

    public InterviewMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public InterviewMessage(String id, String sender, String text) {
        this();
        this.id = id;
        this.sender = sender;
        this.text = text;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getScoreFeedback() { return scoreFeedback; }
    public void setScoreFeedback(String scoreFeedback) { this.scoreFeedback = scoreFeedback; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}