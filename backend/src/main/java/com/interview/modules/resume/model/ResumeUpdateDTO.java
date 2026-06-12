package com.interview.modules.resume.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 手动修正 AI 解析结果的请求 DTO
 */
@Data
public class ResumeUpdateDTO {
    private String candidateName;
    private String candidateRole;
    private Integer experienceYears;
    private String education;
    private String email;
    private String phone;
    private Integer matchScore;
    private String aiSummary;
    private Map<String, Integer> competencies;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> highlights;
}
