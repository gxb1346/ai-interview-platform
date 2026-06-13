package com.interview.modules.evaluation.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评估报告（统一评估架构的输出）
 */
public class EvaluationReport {

    private String reportId;
    private String sessionId;
    private String candidateId;
    private String candidateName;
    private String direction;
    private String level;
    private int totalRounds;
    private int overallScore;
    private Map<String, Integer> dimensionScores;  // technical, communication, problemSolving, culturalFit
    private List<String> strengths;
    private List<String> improvements;
    private String summary;
    private String verdict;        // "建议录用" | "待定" | "不予录用"
    private String mode;           // "text" | "voice"
    private LocalDateTime evaluatedAt;
    private String pdfReportPath;  // PDF 导出后的文件路径

    public EvaluationReport() {
        this.evaluatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

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

    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }

    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

    public Map<String, Integer> getDimensionScores() { return dimensionScores; }
    public void setDimensionScores(Map<String, Integer> dimensionScores) { this.dimensionScores = dimensionScores; }

    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }

    public List<String> getImprovements() { return improvements; }
    public void setImprovements(List<String> improvements) { this.improvements = improvements; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public String getPdfReportPath() { return pdfReportPath; }
    public void setPdfReportPath(String pdfReportPath) { this.pdfReportPath = pdfReportPath; }
}
