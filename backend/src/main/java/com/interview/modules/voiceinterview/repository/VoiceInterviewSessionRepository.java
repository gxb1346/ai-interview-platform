package com.interview.modules.voiceinterview.repository;

import com.interview.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import com.interview.modules.voiceinterview.model.VoiceInterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 语音面试会话Repository
 */
@Repository
public interface VoiceInterviewSessionRepository extends JpaRepository<VoiceInterviewSessionEntity, Long> {

    /**
     * 根据用户ID查找所有会话，按开始时间倒序
     */
    List<VoiceInterviewSessionEntity> findByUserIdOrderByStartTimeDesc(String userId);

    /**
     * 查找指定状态且结束时间早于给定时间的会话
     * Note: Queries the AsyncTaskStatus field, not InterviewPhase
     */
    Optional<VoiceInterviewSessionEntity> findByStatusAndEndTimeBefore(
        com.interview.common.model.AsyncTaskStatus status,
        LocalDateTime time
    );

    /**
     * Find all sessions for a user, ordered by update time
     */
    List<VoiceInterviewSessionEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    /**
     * Find sessions by user and status, ordered by update time
     */
    List<VoiceInterviewSessionEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(
        String userId,
        VoiceInterviewSessionStatus status
    );

    List<VoiceInterviewSessionEntity> findByStatusAndStartTimeBefore(
        VoiceInterviewSessionStatus status,
        LocalDateTime time
    );

    List<VoiceInterviewSessionEntity> findByEvaluateStatusAndUpdatedAtBefore(
        com.interview.common.model.AsyncTaskStatus evaluateStatus,
        LocalDateTime time
    );

    // === Dashboard stats queries ===

    long countByStatus(VoiceInterviewSessionStatus status);

    List<VoiceInterviewSessionEntity> findByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT s.skillId, COUNT(s) FROM VoiceInterviewSessionEntity s WHERE s.createdAt >= :since GROUP BY s.skillId")
    List<Object[]> countBySkillIdSince(@Param("since") LocalDateTime since);

    /**
     * 查询所有会话，按更新时间倒序（不限用户）
     */
    List<VoiceInterviewSessionEntity> findAllByOrderByUpdatedAtDesc();

    /**
     * 按状态查询所有会话，按更新时间倒序（不限用户）
     */
    List<VoiceInterviewSessionEntity> findByStatusOrderByUpdatedAtDesc(VoiceInterviewSessionStatus status);
}