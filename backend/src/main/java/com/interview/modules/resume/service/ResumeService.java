package com.interview.modules.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.modules.resume.model.AnalysisResult;
import com.interview.modules.resume.model.Resume;
import com.interview.modules.resume.model.ResumeVO;
import com.interview.modules.resume.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 简历服务 — 编排上传→解析→AI分析→存储的完整流程
 */
@Service
public class ResumeService {

    private final TikaService tikaService;
    private final ResumeAnalysisService analysisService;
    private final ResumeRepository resumeRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.bucket}")
    private String bucket;

    public ResumeService(TikaService tikaService,
                         ResumeAnalysisService analysisService,
                         ResumeRepository resumeRepository,
                         S3Client s3Client,
                         ObjectMapper objectMapper) {
        this.tikaService = tikaService;
        this.analysisService = analysisService;
        this.resumeRepository = resumeRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    /**
     * 完整处理链路：上传 → Tika解析 → AI分析 → 存储到 DB + S3
     *
     * @param file      上传的简历文件 (PDF/DOCX/TXT)
     * @param targetJob 目标岗位 (可选)
     * @return 分析结果 VO
     */
    @Transactional
    public ResumeVO processResume(MultipartFile file, String targetJob) {
        // 1. 提取文件信息
        String fileName = file.getOriginalFilename();
        if (fileName == null) fileName = "unknown";
        String fileType = TikaService.getFileType(fileName);

        // 校验文件类型
        validateFileType(fileType);

        // 2. Tika 解析 → 提取纯文本
        String rawText = tikaService.extractText(file);

        // 3. AI 分析
        AnalysisResult result = analysisService.analyze(rawText, targetJob);

        // 4. 上传原始文件到 MinIO/S3
        String s3Key = uploadToS3(file, fileType);

        // 5. 保存到数据库
        Resume resume = new Resume();
        resume.setFileName(fileName);
        resume.setFileType(fileType);
        resume.setS3Key(s3Key);
        resume.setFileSize(file.getSize());
        resume.setRawText(rawText);

        resume.setCandidateName(result.getName());
        resume.setCandidateRole(result.getRole());
        resume.setExperienceYears(result.getExperienceYears());
        resume.setEducation(result.getEducation());
        resume.setEmail(result.getEmail());
        resume.setPhone(result.getPhone());
        resume.setMatchScore(result.getMatchScore());
        resume.setAiSummary(result.getAiSummary());

        // 序列化 JSONB 字段
        try {
            resume.setCompetenciesJson(objectMapper.writeValueAsString(result.getCompetencies()));
            resume.setStrengthsJson(objectMapper.writeValueAsString(result.getStrengths()));
            resume.setWeaknessesJson(objectMapper.writeValueAsString(result.getWeaknesses()));
            resume.setHighlightsJson(objectMapper.writeValueAsString(result.getHighlights()));
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }

        resume.setAnalyzedAt(LocalDateTime.now());

        Resume saved = resumeRepository.save(resume);

        return ResumeVO.fromEntity(saved);
    }

    /**
     * 获取所有简历分析记录
     */
    public List<ResumeVO> getAllResumes() {
        return resumeRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ResumeVO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取简历详情
     */
    public ResumeVO getResumeById(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        return ResumeVO.fromEntity(resume);
    }

    /**
     * 上传文件到 MinIO/S3
     */
    private String uploadToS3(MultipartFile file, String fileType) {
        String key = "resumes/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(getContentType(fileType))
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (Exception e) {
            throw new RuntimeException("文件上传到存储服务失败: " + e.getMessage(), e);
        }
        return key;
    }

    private String getContentType(String fileType) {
        return switch (fileType) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            default -> "text/plain";
        };
    }

    private void validateFileType(String fileType) {
        if (!List.of("pdf", "docx", "doc", "txt").contains(fileType)) {
            throw new IllegalArgumentException("不支持的文件格式: " + fileType + "，仅支持 PDF、DOCX、TXT");
        }
    }
}
