package com.interview.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.result.PageResult;
import com.interview.infrastructure.stream.model.TaskType;
import com.interview.infrastructure.stream.producer.TaskProducer;
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

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
    private final TaskProducer taskProducer;

    @Value("${app.storage.bucket}")
    private String bucket;

    public ResumeService(TikaService tikaService,
                         ResumeAnalysisService analysisService,
                         ResumeRepository resumeRepository,
                         S3Client s3Client,
                         ObjectMapper objectMapper,
                         StringRedisTemplate redisTemplate,
                         TaskProducer taskProducer) {
        this.tikaService = tikaService;
        this.analysisService = analysisService;
        this.resumeRepository = resumeRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.taskProducer = taskProducer;
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

        // 3. 计算内容哈希，用于去重
        String contentHash = md5(rawText);

        // 4. 检查是否已经存在相同内容的简历（去重）
        List<Resume> existing = resumeRepository.findByContentHashAndDeletedFalse(contentHash);
        if (!existing.isEmpty()) {
            // 已存在相同内容的简历，直接返回最早的那份
            return ResumeVO.fromEntity(existing.get(0));
        }

        // 5. 查 Redis 缓存（Redis不可用时降级）
        String cachedJson = null;
        String cacheKey = null;
        try {
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
                Resume saved = saveToDatabase(result, fileName, fileType, s3Key, file.getSize(), rawText, contentHash);
                return ResumeVO.fromEntity(saved);
            } catch (Exception e) {
                // 缓存数据异常，忽略并重新分析
                if (cacheKey != null) {
                    try { redisTemplate.delete(cacheKey); } catch (Exception ignored) {}
                }
            }
        }

        // 6. 缓存未命中 → AI 分析
        AnalysisResult result = analysisService.analyze(rawText, targetJob);

        // 7. 上传原始文件到 MinIO/S3
        String s3Key = uploadToS3(file, fileType);

        // 8. 保存到数据库
        Resume saved = saveToDatabase(result, fileName, fileType, s3Key, file.getSize(), rawText, contentHash);

        // 9. 尝试写入 Redis 缓存（失败不影响主流程）
        if (cacheKey != null) {
            cacheResult(cacheKey, result);
        }

        return ResumeVO.fromEntity(saved);
    }

    /**
     * 异步上传并分析简历（通过 Redis Stream）
     * 同步阶段：校验、Tika 解析、去重
     * 异步阶段（消费者）：S3 上传 → AI 分析 → DB 持久化 → Redis 缓存
     *
     * @return taskId（可关联任务状态）
     */
    @Transactional
    public String uploadResumeAsync(MultipartFile file, String targetJob) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) fileName = "unknown";
        String fileType = TikaService.getFileType(fileName);

        // 校验文件类型
        validateFileType(fileType);

        // Tika 解析 → 提取纯文本
        String rawText = tikaService.extractText(file);

        // 计算内容哈希，用于去重
        String contentHash = md5(rawText);

        // 检查是否已存在相同内容的简历
        List<Resume> existing = resumeRepository.findByContentHashAndDeletedFalse(contentHash);
        if (!existing.isEmpty()) {
            return "duplicate";
        }

        // 发送到 Redis Stream（消费者执行完整的 S3 上传 + AI 分析 + DB 写入 + 缓存）
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawText", rawText);
        payload.put("targetJob", targetJob);
        payload.put("contentHash", contentHash);
        payload.put("fileName", fileName);
        payload.put("fileType", fileType);
        payload.put("fileSize", file.getSize());
        payload.put("fileBytes", encodeFileBytes(file));
        return taskProducer.sendTask(TaskType.RESUME_ANALYSIS, payload);
    }

    private String encodeFileBytes(MultipartFile file) {
        try {
            return java.util.Base64.getEncoder().encodeToString(file.getBytes());
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取文件字节失败: " + e.getMessage(), e);
        }
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
                                   String s3Key, Long fileSize, String rawText, String contentHash) {
        Resume resume = new Resume();
        resume.setFileName(fileName);
        resume.setFileType(fileType);
        resume.setS3Key(s3Key);
        resume.setFileSize(fileSize);
        resume.setRawText(rawText);
        resume.setContentHash(contentHash);

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

    /**
     * 启动时回填：为已有数据中 content_hash 为 NULL 的记录计算并填充
     */
    @PostConstruct
    @Transactional
    public void backfillContentHash() {
        List<Resume> all = resumeRepository.findByDeletedFalseOrderByCreatedAtDesc();
        int count = 0;
        for (Resume r : all) {
            if (r.getContentHash() == null || r.getContentHash().isBlank()) {
                if (r.getRawText() != null && !r.getRawText().isBlank()) {
                    r.setContentHash(md5(r.getRawText()));
                    resumeRepository.save(r);
                    count++;
                }
            }
        }
        if (count > 0) {
            System.out.println("✅ 已回填 " + count + " 条简历记录的 content_hash");
        }
    }

    /**
     * 按 contentHash 去重：相同内容只保留最早创建的一份
     * contentHash 为 null 的旧数据当作唯一记录保留 */
    private List<ResumeVO> deduplicateByContent(List<Resume> resumes) {
        // 分离有 hash 和没 hash 的记录
        List<Resume> withHash = new java.util.ArrayList<>();
        List<Resume> withoutHash = new java.util.ArrayList<>();
        for (Resume r : resumes) {
            if (r.getContentHash() != null && !r.getContentHash().isBlank()) {
                withHash.add(r);
            } else {
                withoutHash.add(r);
            }
        }
        // 有 hash 的按 contentHash 去重
        List<ResumeVO> deduped = withHash.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Resume::getContentHash,
                        r -> r,
                        (r1, r2) -> r1.getCreatedAt().isBefore(r2.getCreatedAt()) ? r1 : r2
                ))
                .values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ResumeVO::fromEntity)
                .collect(java.util.stream.Collectors.toList());
        // 没 hash 的当作唯一记录追加
        withoutHash.stream()
                .map(ResumeVO::fromEntity)
                .forEach(deduped::add);
        return deduped;
    }

    public List<ResumeVO> getAllResumes() {
        return deduplicateByContent(resumeRepository.findByDeletedFalseOrderByCreatedAtDesc());
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

        List<ResumeVO> voList = deduplicateByContent(resumePage.getContent());

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
     * 批量软删除简历
     */
    @Transactional
    public void batchSoftDelete(List<Long> ids) {
        List<Resume> resumes = resumeRepository.findAllById(ids);
        for (Resume resume : resumes) {
            resume.setDeleted(true);
        }
        resumeRepository.saveAll(resumes);
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
     * 获取人才库候选人列表（已去重）
     */
    public List<ResumeVO> getTalentPool() {
        return deduplicateByContent(resumeRepository.findByInTalentPoolTrueAndDeletedFalseOrderByCreatedAtDesc());
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

    /**
     * 从人才库移除
     */
    @Transactional
    public ResumeVO removeFromTalentPool(Long id) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("简历不存在: id=" + id));
        resume.setInTalentPool(false);
        resume.setTalentStatus(null);
        Resume saved = resumeRepository.save(resume);
        return ResumeVO.fromEntity(saved);
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

    /**
     * 批量处理多份简历
     * 对每份文件依次执行：类型校验 → Tika解析 → 去重检查 → Redis缓存 → AI分析 → S3上传 → DB持久化
     *
     * @param files    上传的文件列表
     * @param targetJob 目标岗位（可选）
     * @return 每份文件的处理结果列表
     */
    public List<Map<String, Object>> batchProcessResumes(List<MultipartFile> files, String targetJob) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> result = new HashMap<>();
            String fileName = file.getOriginalFilename();
            result.put("fileName", fileName != null ? fileName : "unknown");

            try {
                String fileType = TikaService.getFileType(fileName != null ? fileName : "unknown");
                validateFileType(fileType);

                String rawText = tikaService.extractText(file);
                String contentHash = md5(rawText);

                // 去重检查
                List<Resume> existing = resumeRepository.findByContentHashAndDeletedFalse(contentHash);
                if (!existing.isEmpty()) {
                    Resume dup = existing.get(0);
                    result.put("status", "duplicate");
                    result.put("candidateName", dup.getCandidateName() != null ? dup.getCandidateName() : "未知");
                    result.put("message", "该简历内容已在系统中存在");
                    results.add(result);
                    continue;
                }

                // Redis 缓存检查
                String cacheKey = buildCacheKey(contentHash, targetJob);
                AnalysisResult analysisResult = null;
                try {
                    String cachedJson = redisTemplate.opsForValue().get(cacheKey);
                    if (cachedJson != null) {
                        analysisResult = objectMapper.readValue(cachedJson, AnalysisResult.class);
                    }
                } catch (Exception ignored) {}

                if (analysisResult == null) {
                    analysisResult = analysisService.analyze(rawText, targetJob);
                    if (cacheKey != null) {
                        cacheResult(cacheKey, analysisResult);
                    }
                }

                // S3 上传
                String s3Key = uploadToS3(file, fileType);

                // DB 持久化
                Resume saved = saveToDatabase(analysisResult, fileName != null ? fileName : "unknown",
                        fileType, s3Key, file.getSize(), rawText, contentHash);

                result.put("status", "success");
                result.put("candidateName", saved.getCandidateName() != null ? saved.getCandidateName() : "未知");
                result.put("id", saved.getId());
                result.put("matchScore", saved.getMatchScore());

            } catch (Exception e) {
                result.put("status", "error");
                result.put("message", e.getMessage());
            }

            results.add(result);
        }

        return results;
    }

    private void validateFileType(String fileType) {
        if (!List.of("pdf", "docx", "doc", "txt").contains(fileType)) {
            throw new IllegalArgumentException("不支持的文件格式: " + fileType + "，仅支持 PDF、DOCX、TXT");
        }
    }
}
