package com.interview.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.result.PageResult;
import com.interview.modules.resume.model.AnalysisResult;
import com.interview.modules.resume.model.Resume;
import com.interview.modules.resume.model.ResumeUpdateDTO;
import com.interview.modules.resume.model.ResumeVO;
import com.interview.modules.resume.model.TalentStatus;
import com.interview.modules.resume.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 简历服务 — 编排上传→解析→AI分析→存储的完整流程
 * 使用 Redis 缓存相同内容的简历分析结果，避免重复调用 AI
 */
@Service
public class ResumeService {

    private static final String RESUME_CACHE_PREFIX = "resume:analysis:";
    private static final long CACHE_TTL_HOURS = 168; // 7天

    private final TikaService tikaService;
    private final ResumeAnalysisService analysisService;
    private final ResumeRepository resumeRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.storage.bucket}")
    private String bucket;

    public ResumeService(TikaService tikaService,
                         ResumeAnalysisService analysisService,
                         ResumeRepository resumeRepository,
                         S3Client s3Client,
                         ObjectMapper objectMapper,
                         StringRedisTemplate redisTemplate) {
        this.tikaService = tikaService;
        this.analysisService = analysisService;
        this.resumeRepository = resumeRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 完整处理链路：上传 → Tika解析 → 查Redis缓存 → AI分析 → 存储到 DB + S3 + Redis
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

        // 3. 计算内容哈希，尝试查 Redis 缓存（Redis不可用时降级）
        String cachedJson = null;
        String cacheKey = null;
        try {
            String contentHash = md5(rawText);
            cacheKey = buildCacheKey(contentHash, targetJob);
            cachedJson = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            System.err.println("Redis 查询失败，降级到直接AI分析: " + e.getMessage());
        }

        if (cachedJson != null) {
            // 缓存命中，反序列化后直接存储到 DB（跳过 AI 调用）
            try {
                AnalysisResult result = objectMapper.readValue(cachedJson, AnalysisResult.class);
                String s3Key = uploadToS3(file, fileType);
                Resume saved = saveToDatabase(result, fileName, fileType, s3Key, file.getSize(), rawText);
                return ResumeVO.fromEntity(saved);
            } catch (Exception e) {
                // 缓存数据异常，忽略并重新分析
                if (cacheKey != null) {
                    try { redisTemplate.delete(cacheKey); } catch (Exception ignored) {}
                }
            }
        }

        // 4. 缓存未命中 → AI 分析
        AnalysisResult result = analysisService.analyze(rawText, targetJob);

        // 5. 上传原始文件到 MinIO/S3
        String s3Key = uploadToS3(file, fileType);

        // 6. 保存到数据库
        Resume saved = saveToDatabase(result, fileName, fileType, s3Key, file.getSize(), rawText);

        // 7. 尝试写入 Redis 缓存（失败不影响主流程）
        if (cacheKey != null) {
            cacheResult(cacheKey, result);
        }

        return ResumeVO.fromEntity(saved);
    }

    private ResumeVO buildResumeVO(AnalysisResult result, String fileName, String fileType,
                                    Long fileSize, String rawText) {
        // 缓存命中时，构造一个含分析结果的 VO（不存 DB，仅返回给前端）
        Resume resume = new Resume();
        resume.setFileName(fileName);
        resume.setFileType(fileType);
        resume.setFileSize(fileSize);
        resume.setRawText(rawText);
        resume.setCandidateName(result.getName());
        resume.setCandidateRole(result.getRole());
        resume.setExperienceYears(result.getExperienceYears());
        resume.setEducation(result.getEducation());
        resume.setEmail(result.getEmail());
        resume.setPhone(result.getPhone());
        resume.setMatchScore(result.getMatchScore());
        resume.setAiSummary(result.getAiSummary());
        try {
            resume.setCompetenciesJson(objectMapper.writeValueAsString(result.getCompetencies()));
            resume.setStrengthsJson(objectMapper.writeValueAsString(result.getStrengths()));
            resume.setWeaknessesJson(objectMapper.writeValueAsString(result.getWeaknesses()));
            resume.setHighlightsJson(objectMapper.writeValueAsString(result.getHighlights()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
        resume.setAnalyzedAt(LocalDateTime.now());
        return ResumeVO.fromEntity(resume);
    }

    private Resume saveToDatabase(AnalysisResult result, String fileName, String fileType,
                                   String s3Key, Long fileSize, String rawText) {
        Resume resume = new Resume();
        resume.setFileName(fileName);
        resume.setFileType(fileType);
        resume.setS3Key(s3Key);
        resume.setFileSize(fileSize);
        resume.setRawText(rawText);

        resume.setCandidateName(result.getName());
        resume.setCandidateRole(result.getRole());
        resume.setExperienceYears(result.getExperienceYears());
        resume.setEducation(result.getEducation());
        resume.setEmail(result.getEmail());
        resume.setPhone(result.getPhone());
        resume.setMatchScore(result.getMatchScore());
        resume.setAiSummary(result.getAiSummary());

        try {
            resume.setCompetenciesJson(objectMapper.writeValueAsString(result.getCompetencies()));
            resume.setStrengthsJson(objectMapper.writeValueAsString(result.getStrengths()));
            resume.setWeaknessesJson(objectMapper.writeValueAsString(result.getWeaknesses()));
            resume.setHighlightsJson(objectMapper.writeValueAsString(result.getHighlights()));
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }

        resume.setAnalyzedAt(LocalDateTime.now());
        return resumeRepository.save(resume);
    }

    private void cacheResult(String cacheKey, AnalysisResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
            System.err.println("Redis 缓存写入失败: " + e.getMessage());
        }
    }

    private String buildCacheKey(String contentHash, String targetJob) {
        String job = (targetJob != null && !targetJob.isBlank()) ? targetJob.trim() : "default";
        return RESUME_CACHE_PREFIX + contentHash + ":" + job;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 不可用", e);
        }
    }

    public List<ResumeVO> getAllResumes() {
        return resumeRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(ResumeVO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询简历列表（支持搜索 + 筛选）
     */
    public PageResult<ResumeVO> getResumePage(String keyword, String education,
                                                Integer minScore, Integer maxScore,
                                                int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Resume> resumePage;

        boolean hasSearch = (keyword != null && !keyword.isBlank())
                || (education != null && !education.isBlank())
                || minScore != null || maxScore != null;

        if (hasSearch) {
            resumePage = resumeRepository.searchByFilters(keyword, education, minScore, maxScore, pageable);
        } else {
            resumePage = resumeRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable);
        }

        List<ResumeVO> voList = resumePage.getContent().stream()
                .map(ResumeVO::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(voList, resumePage.getTotalElements(), page, pageSize);
    }

    /**
     * 手动修正 AI 解析结果
     */
    @Transactional
    public ResumeVO updateResume(Long id, ResumeUpdateDTO dto) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));

        if (dto.getCandidateName() != null) resume.setCandidateName(dto.getCandidateName());
        if (dto.getCandidateRole() != null) resume.setCandidateRole(dto.getCandidateRole());
        if (dto.getExperienceYears() != null) resume.setExperienceYears(dto.getExperienceYears());
        if (dto.getEducation() != null) resume.setEducation(dto.getEducation());
        if (dto.getEmail() != null) resume.setEmail(dto.getEmail());
        if (dto.getPhone() != null) resume.setPhone(dto.getPhone());
        if (dto.getMatchScore() != null) resume.setMatchScore(dto.getMatchScore());
        if (dto.getAiSummary() != null) resume.setAiSummary(dto.getAiSummary());

        try {
            if (dto.getCompetencies() != null)
                resume.setCompetenciesJson(objectMapper.writeValueAsString(dto.getCompetencies()));
            if (dto.getStrengths() != null)
                resume.setStrengthsJson(objectMapper.writeValueAsString(dto.getStrengths()));
            if (dto.getWeaknesses() != null)
                resume.setWeaknessesJson(objectMapper.writeValueAsString(dto.getWeaknesses()));
            if (dto.getHighlights() != null)
                resume.setHighlightsJson(objectMapper.writeValueAsString(dto.getHighlights()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }

        Resume saved = resumeRepository.save(resume);
        return ResumeVO.fromEntity(saved);
    }

    /**
     * 软删除简历
     */
    @Transactional
    public void softDelete(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        resume.setDeleted(true);
        resumeRepository.save(resume);
    }

    /**
     * 硬删除简历
     */
    @Transactional
    public void hardDelete(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        resumeRepository.delete(resume);
    }

    public ResumeVO getResumeById(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        return ResumeVO.fromEntity(resume);
    }

    /**
     * 获取人才库候选人列表
     */
    public List<ResumeVO> getTalentPool() {
        return resumeRepository.findByInTalentPoolTrueOrderByCreatedAtDesc()
                .stream()
                .map(ResumeVO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 移入人才库
     */
    @Transactional
    public ResumeVO moveToTalentPool(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        resume.setInTalentPool(true);
        resume.setTalentStatus(TalentStatus.NEW);
        Resume saved = resumeRepository.save(resume);
        return ResumeVO.fromEntity(saved);
    }

    /**
     * 更新人才库状态
     */
    @Transactional
    public ResumeVO updateTalentStatus(Long id, String status) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        try {
            TalentStatus ts = TalentStatus.valueOf(status);
            resume.setTalentStatus(ts);
            Resume saved = resumeRepository.save(resume);
            return ResumeVO.fromEntity(saved);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的人才库状态: " + status);
        }
    }

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
