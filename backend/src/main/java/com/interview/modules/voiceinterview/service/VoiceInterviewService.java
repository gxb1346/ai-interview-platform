package com.interview.modules.voiceinterview.service;

import com.interview.common.ai.LlmProviderRegistry;
import com.interview.common.constant.CommonConstants.InterviewDefaults;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.modules.voiceinterview.config.VoiceInterviewProperties;
import com.interview.modules.voiceinterview.dto.CreateSessionRequest;
import com.interview.modules.voiceinterview.dto.SessionMetaDTO;
import com.interview.modules.voiceinterview.dto.SessionResponseDTO;
import com.interview.modules.voiceinterview.dto.VoiceInterviewMessageDTO;
import com.interview.modules.voiceinterview.listener.VoiceEvaluateStreamProducer;
import com.interview.modules.voiceinterview.model.VoiceInterviewEvaluationEntity;
import com.interview.modules.voiceinterview.model.VoiceInterviewMessageEntity;
import com.interview.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import com.interview.modules.voiceinterview.model.VoiceInterviewSessionStatus;
import com.interview.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import com.interview.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import com.interview.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import com.interview.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Voice Interview Service
 * 语音面试服务
 * <p>
 * Provides business logic for voice interview session management including:
 * - Session lifecycle management (create, end, retrieve)
 * - Phase transitions and state tracking
 * - Message persistence and conversation history
 * - Redis caching for active sessions
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceInterviewService {

    private final VoiceInterviewSessionRepository sessionRepository;
    private final VoiceInterviewMessageRepository messageRepository;
    private final VoiceInterviewEvaluationRepository evaluationRepository;
    private final ResumeRepository resumeRepository;
    private final RedissonClient redissonClient;
    private final VoiceInterviewProperties properties;
    private final VoiceEvaluateStreamProducer voiceEvaluateStreamProducer;
    private final LlmProviderRegistry llmProviderRegistry;

    private static final String SESSION_CACHE_KEY_PREFIX = "voice:interview:session:";
    private static final int CACHE_TTL_HOURS = 1;
    private static final String DEFAULT_USER_ID = "default";

    /**
     * Create a new voice interview session
     * 创建新的语音面试会话
     *
     * @param request Session creation request with role type and phase configuration
     * @return SessionResponseDTO with session details and WebSocket URL
     */
    @Transactional
    public SessionResponseDTO createSession(CreateSessionRequest request) {
        String effectiveSkillId = request.getSkillId() != null ? request.getSkillId() : InterviewDefaults.SKILL_ID;
        String effectiveLlmProvider = (request.getLlmProvider() != null && !request.getLlmProvider().isBlank())
            ? request.getLlmProvider()
            : null;

        String effectiveUserId = (request.getUserId() != null && !request.getUserId().isBlank())
            ? request.getUserId()
            : DEFAULT_USER_ID;

        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
                .userId(effectiveUserId)
                .candidateName(request.getCandidateName())
                .roleType(request.getRoleType() != null ? request.getRoleType() : effectiveSkillId)
                .skillId(effectiveSkillId)
                .difficulty(request.getDifficulty() != null ? request.getDifficulty() : InterviewDefaults.DIFFICULTY)
                .customJdText(request.getCustomJdText())
                .resumeId(request.getResumeId())
                .introEnabled(request.getIntroEnabled() != null ? request.getIntroEnabled() : false)
                .techEnabled(request.getTechEnabled() != null ? request.getTechEnabled() : true)
                .projectEnabled(request.getProjectEnabled() != null ? request.getProjectEnabled() : true)
                .hrEnabled(request.getHrEnabled() != null ? request.getHrEnabled() : true)
                .llmProvider(effectiveLlmProvider)
                .plannedDuration(request.getPlannedDuration())
                .currentPhase(determineFirstPhase(request))
                .build();

        VoiceInterviewSessionEntity saved = sessionRepository.save(session);
        cacheSession(saved);

        log.info("Created voice interview session: {} with template: {}, phase: {}",
                saved.getId(), effectiveSkillId, saved.getCurrentPhase());

        return buildSessionResponse(saved);
    }

    /**
     * 仅当会话处于 IN_PROGRESS 状态时结束，用于 WebSocket 异常断开的兜底。
     * 正常结束的 endSession 已设为 COMPLETED，此方法不会重复操作。
     */
    @Transactional
    public void endSessionIfInProgress(String sessionId) {
        Long sessionIdLong = parseSessionId(sessionId);
        VoiceInterviewSessionEntity session = sessionRepository.findById(sessionIdLong).orElse(null);
        if (session == null || session.getStatus() != VoiceInterviewSessionStatus.IN_PROGRESS) {
            return;
        }
        log.info("Auto-ending IN_PROGRESS session {} after WebSocket disconnect", sessionId);
        endSession(session);
    }

    /**
     * End interview session and update status
     * 结束面试会话并更新状态
     *
     * @param sessionId Session ID (String format, will be converted to Long)
     */
    @Transactional
    public void endSession(String sessionId) {
        Long sessionIdLong = parseSessionId(sessionId);
        VoiceInterviewSessionEntity session = getSession(sessionIdLong);

        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }

        endSession(session);
        voiceEvaluateStreamProducer.sendEvaluateTask(sessionId);
    }

    private void endSession(VoiceInterviewSessionEntity session) {
        session.setEndTime(LocalDateTime.now());
        session.setCurrentPhase(VoiceInterviewSessionEntity.InterviewPhase.COMPLETED);
        session.setStatus(VoiceInterviewSessionStatus.COMPLETED);
        session.setActualDuration((int) Duration.between(session.getStartTime(), LocalDateTime.now()).toSeconds());
        session.setEvaluateStatus(AsyncTaskStatus.PENDING);

        sessionRepository.save(session);
        invalidateSessionCache(session.getId());

        log.info("Ended voice interview session: {}, duration: {} seconds, evaluation triggered",
                session.getId(), session.getActualDuration());
    }

    /**
     * Get session by ID with Redis cache fallback
     * 通过ID获取会话，支持Redis缓存
     *
     * @param sessionId Session ID (String format, will be converted to Long)
     * @return VoiceInterviewSessionEntity or null if not found
     */
    public VoiceInterviewSessionEntity getSession(String sessionId) {
        return getSession(parseSessionId(sessionId));
    }

    /**
     * Get session by ID with Redis cache fallback
     * 通过ID获取会话，支持Redis缓存
     *
     * @param sessionId Session ID as Long
     * @return VoiceInterviewSessionEntity or null if not found
     */
    public VoiceInterviewSessionEntity getSession(Long sessionId) {
        if (sessionId == null) {
            return null;
        }

        // Try cache first
        String cacheKey = getSessionCacheKey(sessionId);
        RBucket<VoiceInterviewSessionEntity> bucket = redissonClient.getBucket(cacheKey);
        VoiceInterviewSessionEntity cached = bucket.get();

        if (cached != null) {
            log.debug("Session {} found in cache", sessionId);
            return cached;
        }

        // Fallback to database
        return sessionRepository.findById(sessionId).orElse(null);
    }

    /**
     * Start a new interview phase
     * 开始新的面试阶段
     *
     * @param sessionId Session ID (String format)
     * @param phaseStr  Phase as string (INTRO, TECH, PROJECT, HR)
     */
    @Transactional
    public void startPhase(String sessionId, String phaseStr) {
        Long sessionIdLong = parseSessionId(sessionId);
        VoiceInterviewSessionEntity session = getSession(sessionIdLong);

        if (session == null) {
            log.warn("Cannot start phase - session not found: {}", sessionId);
            return;
        }

        try {
            VoiceInterviewSessionEntity.InterviewPhase newPhase =
                    VoiceInterviewSessionEntity.InterviewPhase.valueOf(phaseStr.toUpperCase());

            VoiceInterviewSessionEntity.InterviewPhase oldPhase = session.getCurrentPhase();
            session.setCurrentPhase(newPhase);
            sessionRepository.save(session);
            cacheSession(session); // Update cache

            log.info("Session {} transitioned from phase {} to {}", sessionId, oldPhase, newPhase);

        } catch (IllegalArgumentException e) {
            log.error("Invalid phase string: {}", phaseStr, e);
        }
    }

    /**
     * Get current phase for session
     * 获取会话当前阶段
     *
     * @param sessionId Session ID (String format)
     * @return Current InterviewPhase or null if session not found
     */
    public VoiceInterviewSessionEntity.InterviewPhase getCurrentPhase(String sessionId) {
        VoiceInterviewSessionEntity session = getSession(sessionId);
        return session != null ? session.getCurrentPhase() : null;
    }

    /**
     * Save dialogue message (user and AI text) to database
     * 保存对话消息（用户和AI文本）到数据库
     *
     * @param sessionId Session ID (String format)
     * @param userText  User's recognized speech text
     * @param aiText    AI's generated response text
     */
    @Transactional
    public void saveMessage(String sessionId, String userText, String aiText) {
        Long sessionIdLong = parseSessionId(sessionId);
        VoiceInterviewSessionEntity session = getSession(sessionIdLong);

        if (session == null) {
            log.warn("Cannot save message - session not found: {}", sessionId);
            return;
        }

        String normalizedUserText = VoiceInterviewMessageEntity.trimToNull(userText);
        String normalizedAiText = VoiceInterviewMessageEntity.trimToNull(aiText);

        boolean answerAttached = normalizedUserText != null
            && fillLatestUnansweredQuestion(sessionIdLong, normalizedUserText);
        if (normalizedAiText == null) {
            return;
        }

        VoiceInterviewMessageEntity message = VoiceInterviewMessageEntity.builder()
                .sessionId(sessionIdLong)
                .messageType("DIALOGUE")
                .phase(session.getCurrentPhase())
                .userRecognizedText(normalizedUserText != null && !answerAttached
                    ? normalizedUserText
                    : null)
                .aiGeneratedText(normalizedAiText)
                .sequenceNum(getNextSequenceNum(sessionIdLong))
                .build();

        messageRepository.save(message);
        log.debug("Saved message for session: {}, phase: {}, sequence: {}",
                sessionId, session.getCurrentPhase(), message.getSequenceNum());
    }

    private boolean fillLatestUnansweredQuestion(Long sessionId, String userText) {
        return messageRepository
            .findFirstBySessionIdAndUserRecognizedTextIsNullAndAiGeneratedTextIsNotNullOrderBySequenceNumDesc(
                sessionId)
            .map(message -> {
                message.setUserRecognizedText(userText);
                messageRepository.save(message);
                log.debug("Filled answer for voice message: sessionId={}, sequence={}",
                    sessionId, message.getSequenceNum());
                return true;
            })
            .orElse(false);
    }

    /**
     * 更新最后一条用户回答的评分（实时评分结果）
     */
    public void updateMessageScore(String sessionId, int score, String feedback) {
        Long sessionIdLong = parseSessionId(sessionId);
        messageRepository
            .findFirstBySessionIdAndUserRecognizedTextIsNotNullOrderBySequenceNumDesc(sessionIdLong)
            .ifPresent(message -> {
                message.setScore(score);
                message.setScoreFeedback(feedback);
                messageRepository.save(message);
                log.debug("Updated real-time score: sessionId={}, score={}, feedback={}",
                    sessionId, score, feedback);
            });
    }

    /**
     * Get conversation history for a session
     * 获取会话的对话历史记录
     *
     * @param sessionId Session ID (String format)
     * @return List of messages ordered by sequence number
     */
    public List<VoiceInterviewMessageEntity> getConversationHistory(String sessionId) {
        Long sessionIdLong = parseSessionId(sessionId);
        return messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionIdLong);
    }

    /**
     * Get conversation history as DTOs (for frontend)
     */
    public List<VoiceInterviewMessageDTO> getConversationHistoryDTO(String sessionId) {
        return getConversationHistory(sessionId).stream()
            .map(msg -> VoiceInterviewMessageDTO.builder()
                .id(msg.getId())
                .sessionId(msg.getSessionId())
                .messageType(msg.getMessageType())
                .phase(msg.getPhase() != null ? msg.getPhase().name() : null)
                .userRecognizedText(msg.getUserRecognizedText())
                .aiGeneratedText(msg.getAiGeneratedText())
                .timestamp(msg.getTimestamp())
                .sequenceNum(msg.getSequenceNum())
                .score(msg.getScore())
                .scoreFeedback(msg.getScoreFeedback())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Pause interview session
     * 暂停面试会话
     *
     * @param sessionId Session ID
     * @param reason Pause reason (user_initiated or timeout)
     */
    @Transactional
    public void pauseSession(String sessionId, String reason) {
        Long sessionIdLong = parseSessionId(sessionId);

        VoiceInterviewSessionEntity session = sessionRepository.findById(sessionIdLong)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId));

        if (session.getStatus() != VoiceInterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "会话状态为 " + session.getStatus() + "，无法暂停"
            );
        }

        session.setStatus(VoiceInterviewSessionStatus.PAUSED);
        session.setPausedAt(LocalDateTime.now());

        sessionRepository.save(session);
        invalidateSessionCache(sessionIdLong);

        log.info("Session {} paused, reason: {}", sessionId, reason);
    }

    /**
     * Resume interview session
     * 恢复面试会话
     *
     * @param sessionId Session ID
     * @return SessionResponseDTO with WebSocket URL
     */
    @Transactional
    public SessionResponseDTO resumeSession(String sessionId) {
        Long sessionIdLong = parseSessionId(sessionId);

        VoiceInterviewSessionEntity session = sessionRepository.findById(sessionIdLong)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId));

        if (session.getStatus() != VoiceInterviewSessionStatus.PAUSED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "会话状态为 " + session.getStatus() + "，无法恢复"
            );
        }

        session.setStatus(VoiceInterviewSessionStatus.IN_PROGRESS);
        session.setResumedAt(LocalDateTime.now());

        VoiceInterviewSessionEntity saved = sessionRepository.save(session);
        cacheSession(saved);

        log.info("Session {} resumed with {} messages in conversation history",
            sessionId, messageRepository.countBySessionId(sessionIdLong));

        return buildSessionResponse(saved);
    }

    /**
     * Get all sessions for a user
     * 获取用户所有会话
     *
     * @param userId User ID (optional, defaults to DEFAULT_USER_ID)
     * @param status Filter by status (optional)
     * @return List of session metadata
     */
    public List<SessionMetaDTO> getAllSessions(String userId, String status) {
        boolean hasUserId = userId != null && !userId.isBlank();
        String effectiveUserId = hasUserId ? userId : DEFAULT_USER_ID;

        List<VoiceInterviewSessionEntity> sessions;
        if (status != null && !status.isEmpty()) {
            try {
                VoiceInterviewSessionStatus statusEnum =
                    VoiceInterviewSessionStatus.valueOf(status.toUpperCase());
                sessions = hasUserId
                    ? sessionRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(effectiveUserId, statusEnum)
                    : sessionRepository.findByStatusOrderByUpdatedAtDesc(statusEnum);
            } catch (IllegalArgumentException e) {
                log.warn("无效的会话状态参数: {}", status);
                sessions = hasUserId
                    ? sessionRepository.findByUserIdOrderByUpdatedAtDesc(effectiveUserId)
                    : sessionRepository.findAllByOrderByUpdatedAtDesc();
            }
        } else {
            sessions = hasUserId
                ? sessionRepository.findByUserIdOrderByUpdatedAtDesc(effectiveUserId)
                : sessionRepository.findAllByOrderByUpdatedAtDesc();
        }

        // 批量查询评估分数，避免 N+1
        List<Long> sessionIds = sessions.stream().map(VoiceInterviewSessionEntity::getId).toList();
        Map<Long, Integer> scoreMap = evaluationRepository.findBySessionIdIn(sessionIds).stream()
            .collect(Collectors.toMap(
                VoiceInterviewEvaluationEntity::getSessionId,
                VoiceInterviewEvaluationEntity::getOverallScore,
                (a, b) -> a));

        // 批量解析候选人名称：当 candidateName 为 null 时，尝试从 Resume 表按 userId 查询
        Map<String, String> resolvedNameMap = resolveCandidateNames(sessions);

        return sessions.stream()
            .map(session -> SessionMetaDTO.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .candidateName(
                    session.getCandidateName() != null
                        ? session.getCandidateName()
                        : resolvedNameMap.getOrDefault(session.getUserId(), "未命名候选人")
                )
                .skillId(session.getSkillId())
                .roleType(session.getRoleType())
                .status(session.getStatus().name())
                .currentPhase(session.getCurrentPhase().name())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .actualDuration(session.getActualDuration())
                .messageCount(messageRepository.countBySessionId(session.getId()))
                .evaluateStatus(session.getEvaluateStatus() != null ? session.getEvaluateStatus().name() : null)
                .evaluateError(session.getEvaluateError())
                .overallScore(scoreMap.get(session.getId()))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 批量解析候选人名称：从 Resume 表按 userId 查询
     * 当会话中 candidateName 为 null 时，尝试通过 userId（即 Resume ID）查找简历中的候选人姓名
     */
    private Map<String, String> resolveCandidateNames(List<VoiceInterviewSessionEntity> sessions) {
        Map<String, String> result = new HashMap<>();
        List<Long> resumeIds = sessions.stream()
            .filter(s -> s.getCandidateName() == null)
            .map(VoiceInterviewSessionEntity::getUserId)
            .filter(uid -> uid != null && !uid.isBlank() && !DEFAULT_USER_ID.equals(uid))
            .map(uid -> {
                try {
                    return Long.parseLong(uid);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());

        if (!resumeIds.isEmpty()) {
            resumeRepository.findAllById(resumeIds).forEach(resume -> {
                if (resume.getCandidateName() != null) {
                    result.put(resume.getId().toString(), resume.getCandidateName());
                }
            });
        }
        return result;
    }

    /**
     * Get session DTO by ID
     * 通过ID获取会话DTO
     *
     * @param sessionId Session ID as Long
     * @return SessionResponseDTO with session details or null if not found
     */
    public SessionResponseDTO getSessionDTO(Long sessionId) {
        VoiceInterviewSessionEntity session = getSession(sessionId);

        if (session == null) {
            return null;
        }

        return buildSessionResponse(session);
    }

    /**
     * Check if session should transition to next phase based on duration and question count
     * 检查是否应该转换到下一个阶段（基于时长和问题数量）
     *
     * @param session        Current session
     * @param phaseStartTime Time when current phase started
     * @param questionCount  Number of questions asked in current phase
     * @return true if should transition, false otherwise
     */
    public boolean shouldTransitionToNextPhase(VoiceInterviewSessionEntity session,
                                                LocalDateTime phaseStartTime,
                                                int questionCount) {
        VoiceInterviewSessionEntity.InterviewPhase currentPhase = session.getCurrentPhase();
        if (currentPhase == null || currentPhase == VoiceInterviewSessionEntity.InterviewPhase.COMPLETED) {
            return false;
        }

        Duration phaseDuration = Duration.between(phaseStartTime, LocalDateTime.now());
        VoiceInterviewProperties.DurationConfig config = getPhaseConfig(currentPhase);

        // Rule 1: Max duration reached (forced transition)
        if (phaseDuration.toMinutes() >= config.getMaxDuration()) {
            log.info("Phase {} reached max duration {} minutes, forcing transition",
                    currentPhase, config.getMaxDuration());
            return true;
        }

        // Rule 2: Min questions reached and sufficient information gathered (AI judgment)
        // For MVP, we use a simple heuristic based on question count
        if (questionCount >= config.getMaxQuestions()) {
            log.info("Phase {} reached max questions {}, suggesting transition",
                    currentPhase, config.getMaxQuestions());
            return true;
        }

        // Rule 3: Suggested duration reached with min questions
        if (phaseDuration.toMinutes() >= config.getSuggestedDuration()
                && questionCount >= config.getMinQuestions()) {
            log.info("Phase {} reached suggested duration {} with {} questions, suggesting transition",
                    currentPhase, config.getSuggestedDuration(), questionCount);
            return true;
        }

        return false;
    }

    /**
     * Get the next enabled phase after current phase
     * 获取当前阶段之后的下一个启用的阶段
     *
     * @param session Current session
     * @return Next InterviewPhase or COMPLETED if no more phases
     */
    public VoiceInterviewSessionEntity.InterviewPhase getNextPhase(VoiceInterviewSessionEntity session) {
        VoiceInterviewSessionEntity.InterviewPhase current = session.getCurrentPhase();
        if (current == null) {
            return getFirstEnabledPhase(session);
        }

        return switch (current) {
            case INTRO -> session.getTechEnabled() ? VoiceInterviewSessionEntity.InterviewPhase.TECH :
                    session.getProjectEnabled() ? VoiceInterviewSessionEntity.InterviewPhase.PROJECT :
                            session.getHrEnabled() ? VoiceInterviewSessionEntity.InterviewPhase.HR :
                                    VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
            case TECH -> session.getProjectEnabled() ? VoiceInterviewSessionEntity.InterviewPhase.PROJECT :
                    session.getHrEnabled() ? VoiceInterviewSessionEntity.InterviewPhase.HR :
                            VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
            case PROJECT -> session.getHrEnabled() ? VoiceInterviewSessionEntity.InterviewPhase.HR :
                    VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
            case HR, COMPLETED -> VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
        };
    }

    // ==================== Private Helper Methods ====================

    /**
     * Determine the first phase based on enabled phases
     * 根据启用的阶段确定第一个阶段
     */
    private VoiceInterviewSessionEntity.InterviewPhase determineFirstPhase(CreateSessionRequest request) {
        if (Boolean.TRUE.equals(request.getIntroEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.INTRO;
        if (Boolean.TRUE.equals(request.getTechEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.TECH;
        if (Boolean.TRUE.equals(request.getProjectEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.PROJECT;
        if (Boolean.TRUE.equals(request.getHrEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.HR;
        return VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
    }

    /**
     * Get first enabled phase from session
     */
    private VoiceInterviewSessionEntity.InterviewPhase getFirstEnabledPhase(VoiceInterviewSessionEntity session) {
        if (Boolean.TRUE.equals(session.getIntroEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.INTRO;
        if (Boolean.TRUE.equals(session.getTechEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.TECH;
        if (Boolean.TRUE.equals(session.getProjectEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.PROJECT;
        if (Boolean.TRUE.equals(session.getHrEnabled())) return VoiceInterviewSessionEntity.InterviewPhase.HR;
        return VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
    }

    private SessionResponseDTO buildSessionResponse(VoiceInterviewSessionEntity session) {
        return SessionResponseDTO.builder()
                .sessionId(session.getId())
                .roleType(session.getRoleType())
                .currentPhase(session.getCurrentPhase().name())
                .status(session.getStatus().name())
                .startTime(session.getStartTime())
                .plannedDuration(session.getPlannedDuration())
                .webSocketUrl(String.format("ws://localhost:8080/ws/voice-interview/%d", session.getId()))
                .build();
    }

    /**
     * Get phase configuration from properties
     */
    private VoiceInterviewProperties.DurationConfig getPhaseConfig(VoiceInterviewSessionEntity.InterviewPhase phase) {
        return switch (phase) {
            case INTRO -> properties.getPhase().getIntro();
            case TECH -> properties.getPhase().getTech();
            case PROJECT -> properties.getPhase().getProject();
            case HR -> properties.getPhase().getHr();
            default -> new VoiceInterviewProperties.DurationConfig(0, 0, 0, 0, 0);
        };
    }

    /**
     * Get next sequence number for messages in a session
     */
    private int getNextSequenceNum(Long sessionId) {
        return (int) messageRepository.countBySessionId(sessionId) + 1;
    }

    /**
     * Update evaluation status on session entity (shared by Producer/Consumer/Controller)
     * 同时更新 Redis 缓存，确保前端轮询能拿到最新状态
     */
    public void updateEvaluateStatus(Long sessionId, AsyncTaskStatus status, String error) {
        try {
            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setEvaluateStatus(status);
                session.setEvaluateError(error);
                VoiceInterviewSessionEntity saved = sessionRepository.save(session);
                // 同步更新 Redis 缓存，防止前端轮询读到旧数据
                cacheSession(saved);
                log.info("Evaluation status updated: sessionId={}, status={}", sessionId, status);
            });
        } catch (Exception e) {
            log.error("Failed to update evaluation status: sessionId={}, status={}, error={}",
                    sessionId, status, e.getMessage(), e);
        }
    }

    /**
     * Trigger async evaluation for a session (called by Controller)
     * 通过 Redis Stream 发送评估任务，由 VoiceEvaluateStreamConsumer 异步消费
     */
    @Transactional
    public void triggerEvaluation(Long sessionId) {
        log.info("Triggering evaluation for session: {}", sessionId);
        updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
        voiceEvaluateStreamProducer.sendEvaluateTask(sessionId.toString());
    }

    /**
     * 获取语音面试仪表盘统计数据
     */
    public Map<String, Object> getVoiceStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        long totalSessions = sessionRepository.count();
        long completedSessions = sessionRepository.countByStatus(VoiceInterviewSessionStatus.COMPLETED);
        long inProgressSessions = sessionRepository.countByStatus(VoiceInterviewSessionStatus.IN_PROGRESS);

        // 平均分
        Double avgScore = evaluationRepository.findAverageScore();
        double averageScore = avgScore != null ? avgScore : 0.0;

        // 通过率（分数 >= 60 为通过）
        Long passedCount = evaluationRepository.countByOverallScoreGreaterThanEqual(60);
        double passRate = completedSessions > 0 ? (double) passedCount / completedSessions * 100 : 0;

        stats.put("voiceTotalSessions", totalSessions);
        stats.put("voiceCompletedSessions", completedSessions);
        stats.put("voiceInProgressSessions", inProgressSessions);
        stats.put("voiceAverageScore", Math.round(averageScore * 10.0) / 10.0);
        stats.put("voicePassRate", Math.round(passRate * 10.0) / 10.0);

        // 方向统计
        List<Object[]> directionRows = sessionRepository.countBySkillIdSince(thirtyDaysAgo);
        List<Map<String, Object>> directionStats = directionRows.stream()
            .map(row -> {
                Map<String, Object> m = new HashMap<>();
                m.put("direction", row[0]);
                m.put("count", row[1]);
                return m;
            })
            .collect(Collectors.toList());
        stats.put("voiceDirectionStats", directionStats);

        // 每日统计：查出近30天的会话，在Java中按日期分组
        List<VoiceInterviewSessionEntity> recentSessions = sessionRepository.findByCreatedAtAfter(thirtyDaysAgo);
        Map<String, Long> dailyCountMap = recentSessions.stream()
            .collect(Collectors.groupingBy(
                s -> s.getCreatedAt().toLocalDate().toString(),
                TreeMap::new,
                Collectors.counting()
            ));
        List<Map<String, Object>> dailyStats = dailyCountMap.entrySet().stream()
            .map(entry -> {
                Map<String, Object> m = new HashMap<>();
                m.put("date", entry.getKey());
                m.put("count", entry.getValue());
                return m;
            })
            .collect(Collectors.toList());
        stats.put("voiceDailyStats", dailyStats);

        return stats;
    }

    /**
     * 删除语音面试会话及其关联的消息和评估记录
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND, "会话不存在: " + sessionId);
        }
        evaluationRepository.findBySessionId(sessionId).ifPresent(evaluationRepository::delete);
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        log.info("Deleted voice interview session: {}", sessionId);
    }

    /**
     * Cache session in Redis
     */
    private void cacheSession(VoiceInterviewSessionEntity session) {
        String cacheKey = getSessionCacheKey(session.getId());
        RBucket<VoiceInterviewSessionEntity> bucket = redissonClient.getBucket(cacheKey);
        bucket.set(session, Duration.ofHours(CACHE_TTL_HOURS));
        log.debug("Cached session: {}", session.getId());
    }

    /**
     * Invalidate session cache in Redis
     */
    private void invalidateSessionCache(Long sessionId) {
        String cacheKey = getSessionCacheKey(sessionId);
        RBucket<VoiceInterviewSessionEntity> bucket = redissonClient.getBucket(cacheKey);
        bucket.delete();
        log.debug("Invalidated cache for session: {}", sessionId);
    }

    /**
     * Generate Redis cache key for session
     */
    private String getSessionCacheKey(Long sessionId) {
        return SESSION_CACHE_KEY_PREFIX + sessionId;
    }

    /**
     * Parse session ID from String to Long with error handling
     */
    private Long parseSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        try {
            return Long.parseLong(sessionId);
        } catch (NumberFormatException e) {
            log.error("Invalid session ID format: {}", sessionId, e);
            return null;
        }
    }

    /**
     * 清理超时的 IN_PROGRESS 会话和卡住的 PROCESSING 评估。
     * 由 @Scheduled 在 WebSocketHandler 中定时触发。
     */
    @Transactional
    public int cleanupStaleSessions() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusHours(2);

        List<VoiceInterviewSessionEntity> staleSessions = sessionRepository
            .findByStatusAndStartTimeBefore(VoiceInterviewSessionStatus.IN_PROGRESS, staleThreshold);

        int cleaned = 0;
        for (VoiceInterviewSessionEntity session : staleSessions) {
            log.info("Cleaning up stale IN_PROGRESS session {}, started at {}",
                session.getId(), session.getStartTime());
            endSession(session);
            cleaned++;
        }

        LocalDateTime evalStaleThreshold = LocalDateTime.now().minusMinutes(30);
        List<VoiceInterviewSessionEntity> stuckEvals = sessionRepository
            .findByEvaluateStatusAndUpdatedAtBefore(AsyncTaskStatus.PROCESSING, evalStaleThreshold);

        for (VoiceInterviewSessionEntity session : stuckEvals) {
            log.info("Resetting stuck PROCESSING evaluation for session {}", session.getId());
            session.setEvaluateStatus(AsyncTaskStatus.FAILED);
            session.setEvaluateError("评估超时，请重新触发");
            sessionRepository.save(session);
            cleaned++;
        }

        return cleaned;
    }
}