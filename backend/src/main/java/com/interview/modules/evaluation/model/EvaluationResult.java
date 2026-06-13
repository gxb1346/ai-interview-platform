package com.interview.modules.evaluation.model;

import java.util.List;

/**
 * 分批评估结果
 */
public class EvaluationResult {

    private String batchId;
    private int batchIndex;             // 第几批
    private int roundStart;
    private int roundEnd;
    private int batchScore;             // 该批次评分
    private List<String> batchStrengths;
    private List<String> batchWeaknesses;
    private String batchSummary;

    public EvaluationResult() {}

    // Getters and Setters
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public int getBatchIndex() { return batchIndex; }
    public void setBatchIndex(int batchIndex) { this.batchIndex = batchIndex; }

    public int getRoundStart() { return roundStart; }
    public void setRoundStart(int roundStart) { this.roundStart = roundStart; }

    public int getRoundEnd() { return roundEnd; }
    public void setRoundEnd(int roundEnd) { this.roundEnd = roundEnd; }

    public int getBatchScore() { return batchScore; }
    public void setBatchScore(int batchScore) { this.batchScore = batchScore; }

    public List<String> getBatchStrengths() { return batchStrengths; }
    public void setBatchStrengths(List<String> batchStrengths) { this.batchStrengths = batchStrengths; }

    public List<String> getBatchWeaknesses() { return batchWeaknesses; }
    public void setBatchWeaknesses(List<String> batchWeaknesses) { this.batchWeaknesses = batchWeaknesses; }

    public String getBatchSummary() { return batchSummary; }
    public void setBatchSummary(String batchSummary) { this.batchSummary = batchSummary; }
}
