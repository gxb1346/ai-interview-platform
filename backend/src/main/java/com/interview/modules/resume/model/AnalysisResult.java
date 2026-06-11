package com.interview.modules.resume.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 简历分析结果 — 与 Gemini/通义千问返回的 JSON Schema 对应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    /** 候选人姓名 */
    private String name;

    /** 适配岗位 */
    private String role;

    /** 工作年限 */
    private Integer experienceYears;

    /** 最高学历 */
    private String education;

    /** AI 匹配度 (0-100) */
    private Integer matchScore;

    /** 邮箱 */
    private String email;

    /** 电话 */
    private String phone;

    /** 五维能力评分: {technical, communication, problemSolving, teamFit, drive} */
    private Map<String, Integer> competencies;

    /** 3个核心优势 */
    private List<String> strengths;

    /** 2个弱项/改善建议 */
    private List<String> weaknesses;

    /** 3个闪光亮点 */
    private List<String> highlights;

    /** AI 综合评估 (150-250字) */
    private String aiSummary;
}
