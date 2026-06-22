package com.interview.infrastructure.stream.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.result.Result;
import com.interview.infrastructure.stream.consumer.TaskHandler;
import com.interview.infrastructure.stream.model.StreamMessage;
import com.interview.modules.resume.model.AnalysisResult;
import com.interview.modules.resume.model.Resume;
import com.interview.modules.resume.repository.ResumeRepository;
import com.interview.modules.resume.service.ResumeAnalysisService;
import com.interview.modules.resume.service.TikaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 简历 AI 分析任务处理器
 *
 * 消费 Redis Stream 中的 RESUME_ANALYSIS 消息，
 * 执行 AI 分析 → S3 上传 → DB 持久化 → Redis 缓存
 */
@Component
public class ResumeAnalysisTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisTaskHandler.class);

    private static final String CACHE_PREFIX = "resume:analysis:";
    private static final long CACHE_TTL_HOURS = 168; // 7天

    private final ResumeAnalysisService analysisService;
    private final ResumeRepository resumeRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.storage.bucket}")
    private String bucket;

    public ResumeAnalysisTaskHandler(ResumeAnalysisService analysisService,
                                     ResumeRepository resumeRepository,
                                     S3Client s3Client,
                                     ObjectMapper objectMapper,
                                     StringRedisTemplate redisTemplate) {
        this.analysisService = analysisService;
        this.resumeRepository = resumeRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean handle(StreamMessage message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

            Long resumeId = payload.get("resumeId") != null
                    ? ((Number) payload.get("resumeId")).longValue() : null;
            String rawText = (String) payload.get("rawText");
            String targetJob = (String) payload.get("targetJob");
            String contentHash = (String) payload.get("contentHash");

            // ---- 二次去重：先查 Redis 缓存 ---
            String cacheKey = buildCacheKey(contentHash, targetJob);
            String cachedJson = null;
            try {
                cachedJson = redisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                log.warn("[简历分析] Redis 查询失败，降级: {}", e.getMessage());
            }
            if (cachedJson != null) {
                log.info("[简历分析] Redis 缓存命中，跳过 AI 分析: contentHash={}", contentHash);
                return true; // 无需重复分析
            }

            // ---- 二次去重：检查 DB 是否已存在相同内容的简历 ---
            boolean dbExists = resumeRepository.findByContentHashAndDeletedFalse(contentHash)
                    .stream().anyMatch(r -> r.getId() != null && !r.getId().equals(resumeId));
            if (dbExists) {
                log.info("[简历分析] DB 已存在相同内容简历，跳过: contentHash={}", contentHash);
                return true;
            }

            // 1. AI 分析
            log.info("[简历分析] 开始 AI 分析: resumeId={}, taskId={}", resumeId, message.getTaskId());
            AnalysisResult result = analysisService.analyze(rawText, targetJob);
            log.info("[简历分析] AI 分析完成: name={}, role={}, score={}",
                    result.getName(), result.getRole(), result.getMatchScore());

            // 2. 上传原始文件到 S3（文件名从 payload 获取）
            String fileName = (String) payload.get("fileName");
            String fileType = (String) payload.get("fileType");
            Long fileSize = payload.get("fileSize") != null
                    ? ((Number) payload.get("fileSize")).longValue() : null;
            String s3Key = uploadToS3(fileName, fileType, payload);

            // 3. 保存到数据库
            Resume saved = saveToDatabase(result, fileName, fileType, s3Key,
                    fileSize != null ? fileSize : 0L, rawText, contentHash);
            log.info("[简历分析] 已保存到数据库: resumeId={}", saved.getId());

            // 4. 写入 Redis 缓存
            cacheResult(cacheKey, result);

            return true;
        } catch (Exception e) {
            log.error("[简历分析] 处理失败: taskId={}, error={}", message.getTaskId(), e.getMessage(), e);
            return false;
        }
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败");
        }
        resume.setAnalyzedAt(java.time.LocalDateTime.now());
        return resumeRepository.save(resume);
    }

    @SuppressWarnings("unchecked")
    private String uploadToS3(String fileName, String fileType, Map<String, Object> payload) {
        // 从 payload 中获取已上传的 s3Key（如果同步阶段已上传）
        String existingKey = (String) payload.get("s3Key");
        if (existingKey != null && !existingKey.isBlank()) {
            return existingKey;
        }
        // 否则生成新 key（此时需有 fileBytes）
        String key = "resumes/" + UUID.randomUUID() + "_" + fileName;
        String fileBytesBase64 = (String) payload.get("fileBytes");
        if (fileBytesBase64 != null) {
            byte[] bytes = java.util.Base64.getDecoder().decode(fileBytesBase64);
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key)
                            .contentType(getContentType(fileType)).build(),
                    RequestBody.fromBytes(bytes)
            );
        }
        return key;
    }

    private String getContentType(String fileType) {
        return switch (fileType != null ? fileType : "txt") {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            default -> "text/plain";
        };
    }

    private String buildCacheKey(String contentHash, String targetJob) {
        String job = (targetJob != null && !targetJob.isBlank()) ? targetJob.trim() : "default";
        return CACHE_PREFIX + contentHash + ":" + job;
    }

    private void cacheResult(String cacheKey, AnalysisResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[简历分析] Redis 缓存写入失败: {}", e.getMessage());
        }
    }
}