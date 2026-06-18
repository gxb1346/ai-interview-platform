package com.interview.modules.interview.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.modules.interview.model.InterviewSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 面试会话 Redis 存储
 * 支持断点续面，服务重启不丢数据
 */
@Repository
public class InterviewSessionRepository {

    private static final String SESSION_PREFIX = "interview:session:";
    private static final String CANDIDATE_SESSIONS_PREFIX = "interview:candidate:";
    private static final long SESSION_TTL_HOURS = 72; // 会话保留 72 小时

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public InterviewSessionRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存面试会话
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
            throw new RuntimeException("Redis 存储面试会话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 sessionId 查询会话
     */
    public Optional<InterviewSession> findById(String sessionId) {
        try {
            String sessionKey = sessionKey(sessionId);
            String json = redisTemplate.opsForValue().get(sessionKey);
            if (json == null) return Optional.empty();
            // 访问时续期 TTL，避免活跃会话过期
            redisTemplate.expire(sessionKey, SESSION_TTL_HOURS, TimeUnit.HOURS);
            InterviewSession session = objectMapper.readValue(json, InterviewSession.class);
            return Optional.of(session);
        } catch (Exception e) {
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
     * 查询候选人所有进行中的会话（用于续面）
     */
    public List<InterviewSession> findActiveByCandidateId(String candidateId) {
        return findByCandidateId(candidateId).stream()
                .filter(s -> "IN_PROGRESS".equals(s.getStatus()))
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
