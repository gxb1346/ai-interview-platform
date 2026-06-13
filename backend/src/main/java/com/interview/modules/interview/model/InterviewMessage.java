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

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
