package com.interview.modules.resume.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 简历分析结果 — 前端展示用 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;

    private String candidateName;
    private String candidateRole;
    private Integer experienceYears;
    private String education;
    private String email;
    private String phone;
    private Integer matchScore;
    private String aiSummary;
    private String analyzedAt;

    /** 创建时间 */
    private String createdAt;

    /** 是否在人才库中 */
    private Boolean inTalentPool = false;

    /** 人才库状态 */
    private String talentStatus = "NEW";

    private Map<String, Integer> competencies;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> highlights;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 将 Resume 实体转为 VO */
    public static ResumeVO fromEntity(Resume entity) {
        ResumeVO vo = new ResumeVO();
        vo.setId(entity.getId());
        vo.setFileName(entity.getFileName());
        vo.setFileType(entity.getFileType());
        vo.setFileSize(entity.getFileSize());
        vo.setCandidateName(entity.getCandidateName());
        vo.setCandidateRole(entity.getCandidateRole());
        vo.setExperienceYears(entity.getExperienceYears());
        vo.setEducation(entity.getEducation());
        vo.setEmail(entity.getEmail());
        vo.setPhone(entity.getPhone());
        vo.setMatchScore(entity.getMatchScore());
        vo.setAiSummary(entity.getAiSummary());
        vo.setAnalyzedAt(entity.getAnalyzedAt() != null
                ? entity.getAnalyzedAt().toString().replace("T", " ").substring(0, 16)
                : "");
        vo.setCreatedAt(entity.getCreatedAt() != null
                ? entity.getCreatedAt().toString().replace("T", " ").substring(0, 16)
                : "");
        vo.setInTalentPool(entity.getInTalentPool());
        vo.setTalentStatus(entity.getTalentStatus() != null ? entity.getTalentStatus().name() : "NEW");

        // 解析 JSONB 字段
        try {
            vo.setCompetencies(entity.getCompetenciesJson() != null
                    ? MAPPER.readValue(entity.getCompetenciesJson(), new TypeReference<Map<String, Integer>>() {})
                    : Collections.emptyMap());
            vo.setStrengths(entity.getStrengthsJson() != null
                    ? MAPPER.readValue(entity.getStrengthsJson(), new TypeReference<List<String>>() {})
                    : Collections.emptyList());
            vo.setWeaknesses(entity.getWeaknessesJson() != null
                    ? MAPPER.readValue(entity.getWeaknessesJson(), new TypeReference<List<String>>() {})
                    : Collections.emptyList());
            vo.setHighlights(entity.getHighlightsJson() != null
                    ? MAPPER.readValue(entity.getHighlightsJson(), new TypeReference<List<String>>() {})
                    : Collections.emptyList());
        } catch (Exception e) {
            // 解析失败时使用空值
        }
        return vo;
    }
}
