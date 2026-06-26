package com.interview.modules.interview.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 面试会话 PostgreSQL 持久化记录
 * 与 Redis 双写，确保数据不丢失，支持历史数据分析和报表
 */
@Entity
@Table(name = "interview_session_records", indexes = {
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_candidate_id", columnList = "candidate_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_composite_search", columnList = "candidate_id, status, created_at"),
    @Index(name = "idx_composite_dashboard", columnList = "status, verdict, created_at")
})
public class InterviewSessionRecord {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "candidate_id", length = 64)
    private String candidateId;

    @Column(name = "candidate_name", length = 128)
    private String candidateName;

    @Column(name = "direction", length = 64)
    private String direction;

    @Column(name = "level", length = 32)
    private String level;

    @Column(name = "mode", length = 16)
    private String mode;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "total_rounds")
    private Integer totalRounds;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "verdict", length = 32)
    private String verdict;

    @Column(name = "session_json", columnDefinition = "TEXT")
    private String sessionJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public InterviewSessionRecord() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalRounds() { return totalRounds; }
    public void setTotalRounds(Integer totalRounds) { this.totalRounds = totalRounds; }

    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getSessionJson() { return sessionJson; }
    public void setSessionJson(String sessionJson) { this.sessionJson = sessionJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}