package com.interview.modules.interview.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.model.InterviewSessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 面试会话存储（Redis 缓存 + PostgreSQL 持久化双写）
 * Redis 作为热数据缓存，PG 作为永久存储，支持断点续面和历史数据分析
 */
@Repository
public class InterviewSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(InterviewSessionRepository.class);

    private static final String SESSION_PREFIX = "interview:session:";
    private static final String CANDIDATE_SESSIONS_PREFIX = "interview:candidate:";
    private static final long SESSION_TTL_HOURS = 72; // 会话保留 72 小时

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final InterviewSessionRecordRepository pgRepository;

    public InterviewSessionRepository(StringRedisTemplate redisTemplate,
                                       ObjectMapper objectMapper,
                                       InterviewSessionRecordRepository pgRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.pgRepository = pgRepository;
    }

    /**
     * 保存面试会话（Redis + PostgreSQL 双写）
     */
    public void save(InterviewSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        try {
            String json = objectMapper.writeValueAsString(session);
            String sessionKey = sessionKey(session.getSessionId());
            redisTemplate.opsForValue().set(sessionKey, json, SESSION_TTL_HOURS, TimeUnit.HOURS);

            // 维护候选人会话索引
            if (session.getCandidateId() != null) {
                String indexKey = candidateIndexKey(session.getCandidateId());
                redisTemplate.opsForSet().add(indexKey, session.getSessionId());
                redisTemplate.expire(indexKey, SESSION_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("Redis 存储面试会话失败: sessionId={}", session.getSessionId(), e);
            throw new BusinessException(ErrorCode.SESSION_SAVE_FAILED, "Redis 存储面试会话失败: " + e.getMessage());
        }
    }

    /**
     * 保存面试会话到 PostgreSQL（异步双写，失败不影响 Redis 主流程）
     * 在关键状态变更时调用：PREPARING → IN_PROGRESS → COMPLETED
     */
    @Transactional
    public void saveToPg(InterviewSession session) {
        try {
            InterviewSessionRecord record = pgRepository.findById(session.getSessionId())
                    .orElseGet(InterviewSessionRecord::new);
            record.setSessionId(session.getSessionId());
            record.setCandidateId(session.getCandidateId());
            record.setCandidateName(session.getCandidateName());
            record.setDirection(session.getDirection());
            record.setLevel(session.getLevel());
            record.setMode(session.getMode());
            record.setStatus(session.getStatus());
            record.setTotalRounds(session.getCurrentRound());
            record.setSessionJson(objectMapper.writeValueAsString(session));
            record.setUpdatedAt(LocalDateTime.now());
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(session.getCreatedAt() != null ? session.getCreatedAt() : LocalDateTime.now());
            }
            if ("COMPLETED".equals(session.getStatus())) {
                record.setCompletedAt(session.getCompletedAt());
            }
            pgRepository.save(record);
        } catch (Exception e) {
            log.warn("PostgreSQL 保存面试会话失败（不影响 Redis 主流程）: sessionId={}", session.getSessionId(), e);
        }
    }

    /**
     * 根据 sessionId 查询会话
     */
    public Optional<InterviewSession> findById(String sessionId) {
        try {
            String sessionKey = sessionKey(sessionId);
            String json = redisTemplate.opsForValue().get(sessionKey);
            if (json == null) {
                log.debug("[Redis] 会话不存在: {}", sessionId);
                return Optional.empty();
            }
            // 访问时续期 TTL，避免活跃会话过期
            redisTemplate.expire(sessionKey, SESSION_TTL_HOURS, TimeUnit.HOURS);
            InterviewSession session = objectMapper.readValue(json, InterviewSession.class);
            return Optional.of(session);
        } catch (Exception e) {
            log.error("[Redis] 会话反序列化失败: {} - {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查询某候选人的所有会话
     */
    public List<InterviewSession> findByCandidateId(String candidateId) {
        try {
            String indexKey = candidateIndexKey(candidateId);
            Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey);
            if (sessionIds == null || sessionIds.isEmpty()) return Collections.emptyList();

            return sessionIds.stream()
                    .map(this::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 查询候选人所有进行中或暂停中的会话（用于续面）
     */
    public List<InterviewSession> findActiveByCandidateId(String candidateId) {
        return findByCandidateId(candidateId).stream()
                .filter(s -> "IN_PROGRESS".equals(s.getStatus()) || "PAUSED".equals(s.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 查询某候选人某方向的所有已完成会话（用于去重）
     */
    public List<InterviewSession> findCompletedByCandidateAndDirection(String candidateId, String direction) {
        return findByCandidateId(candidateId).stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()))
                .filter(s -> direction.equals(s.getDirection()))
                .collect(Collectors.toList());
    }

    /**
     * 获取某候选人某方向所有已问过的题目 ID
     */
    public List<String> getAskedQuestionIds(String candidateId, String direction) {
        List<InterviewSession> sessions = findCompletedByCandidateAndDirection(candidateId, direction);
        return sessions.stream()
                .map(InterviewSession::getAskedQuestionIds)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 删除会话
     */
    public void deleteById(String sessionId) {
        Optional<InterviewSession> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSession session = sessionOpt.get();
            redisTemplate.delete(sessionKey(sessionId));
            if (session.getCandidateId() != null) {
                String indexKey = candidateIndexKey(session.getCandidateId());
                redisTemplate.opsForSet().remove(indexKey, sessionId);
            }
        }
        try {
            pgRepository.deleteById(sessionId);
        } catch (Exception e) {
            log.warn("PostgreSQL 删除会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 批量删除会话
     */
    public void deleteByIds(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return;
        for (String sessionId : sessionIds) {
            deleteById(sessionId);
        }
    }

    /**
     * 列出所有活跃会话 ID（用于管理后台）
     */
    public List<String> findAllSessionIds() {
        Set<String> keys = redisTemplate.keys(SESSION_PREFIX + "*");
        if (keys == null) return Collections.emptyList();
        return keys.stream()
                .map(k -> k.substring(SESSION_PREFIX.length()))
                .collect(Collectors.toList());
    }

    private String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String candidateIndexKey(String candidateId) {
        return CANDIDATE_SESSIONS_PREFIX + candidateId;
    }
}