package com.interview.modules.interview.repository;

import com.interview.modules.interview.model.InterviewSessionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewSessionRecordRepository extends JpaRepository<InterviewSessionRecord, String> {

    List<InterviewSessionRecord> findByCandidateIdOrderByCreatedAtDesc(String candidateId);

    Page<InterviewSessionRecord> findByCandidateIdOrderByCreatedAtDesc(String candidateId, Pageable pageable);

    List<InterviewSessionRecord> findByStatus(String status);

    void deleteBySessionIdIn(List<String> sessionIds);

    /**
     * 按条件分页查询面试历史（原生 SQL，时间参数在 Service 层已设默认值，避免 null 类型推断问题）
     */
    @Query(value = "SELECT * FROM interview_session_records s WHERE " +
           "(:candidateId IS NULL OR s.candidate_id = :candidateId) AND " +
           "(:direction IS NULL OR s.direction = :direction) AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "s.created_at >= :startTime AND " +
           "s.created_at <= :endTime " +
           "ORDER BY s.created_at DESC",
           countQuery = "SELECT count(*) FROM interview_session_records s WHERE " +
           "(:candidateId IS NULL OR s.candidate_id = :candidateId) AND " +
           "(:direction IS NULL OR s.direction = :direction) AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "s.created_at >= :startTime AND " +
           "s.created_at <= :endTime",
           nativeQuery = true)
    Page<InterviewSessionRecord> searchSessions(
            @Param("candidateId") String candidateId,
            @Param("direction") String direction,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 统计面试总数
     */
    long countByStatus(String status);

    /**
     * 统计候选人面试次数
     */
    long countByCandidateId(String candidateId);

    /**
     * 按方向统计面试数量
     */
    @Query("SELECT s.direction, COUNT(s) FROM InterviewSessionRecord s GROUP BY s.direction")
    List<Object[]> countByDirection();

    /**
     * 按状态统计面试数量
     */
    @Query("SELECT s.status, COUNT(s) FROM InterviewSessionRecord s GROUP BY s.status")
    List<Object[]> countByStatus();

    /**
     * 按日期统计面试数量（最近30天）
     */
    @Query("SELECT FUNCTION('DATE', s.createdAt), COUNT(s) FROM InterviewSessionRecord s " +
           "WHERE s.createdAt >= :since GROUP BY FUNCTION('DATE', s.createdAt) ORDER BY FUNCTION('DATE', s.createdAt)")
    List<Object[]> countByDate(@Param("since") LocalDateTime since);

    /**
     * 计算平均分
     */
    @Query("SELECT COALESCE(AVG(s.overallScore), 0) FROM InterviewSessionRecord s WHERE s.status = 'COMPLETED'")
    double getAverageScore();

    /**
     * 计算通过率
     */
    @Query("SELECT COUNT(s) FROM InterviewSessionRecord s WHERE s.status = 'COMPLETED' AND s.verdict = 'PASS'")
    long countPassed();
}