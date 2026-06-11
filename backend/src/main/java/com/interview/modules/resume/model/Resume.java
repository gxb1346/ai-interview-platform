package com.interview.modules.resume.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件名 */
    @Column(nullable = false)
    private String fileName;

    /** 文件类型: pdf, docx, txt */
    @Column(nullable = false)
    private String fileType;

    /** MinIO/S3 存储Key */
    private String s3Key;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** Tika 提取的纯文本内容 */
    @Column(columnDefinition = "TEXT")
    private String rawText;

    // ========== AI 提取的结构化字段 ==========

    /** 候选人姓名 */
    private String candidateName;

    /** 匹配岗位 */
    private String candidateRole;

    /** 工作年限 */
    private Integer experienceYears;

    /** 最高学历 */
    private String education;

    /** 邮箱 */
    private String email;

    /** 电话 */
    private String phone;

    /** AI 匹配度 (0-100) */
    private Integer matchScore;

    /** AI 综合评价 */
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    /** 五维能力评分 JSON: {"technical":8,"communication":7,...} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String competenciesJson;

    /** 核心优势 JSON 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String strengthsJson;

    /** 弱项/改善建议 JSON 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String weaknessesJson;

    /** 闪光亮点 JSON 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String highlightsJson;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** AI 分析完成时间 */
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
