package com.interview.modules.interview.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试题目
 */
public class InterviewQuestion implements Serializable {

    private String id;
    private String text;
    private String source;          // "SKILL" | "RESUME_DEEP_DIVE" | "JD_PARSE"
    private String direction;       // 面试方向
    private String level;           // 难度等级
    private String stage;           // 所属面试阶段
    private String category;        // 知识点分类
    private int difficultyScore;    // 难度系数 1-10
    private LocalDateTime createdAt;

    public InterviewQuestion() {
        this.createdAt = LocalDateTime.now();
    }

    public InterviewQuestion(String id, String text, String source, String direction, String level) {
        this();
        this.id = id;
        this.text = text;
        this.source = source;
        this.direction = direction;
        this.level = level;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getDifficultyScore() { return difficultyScore; }
    public void setDifficultyScore(int difficultyScore) { this.difficultyScore = difficultyScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
