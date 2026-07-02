package com.interview.modules.voiceinterview.repository;

import com.interview.modules.voiceinterview.model.VoiceInterviewEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 语音面试评估Repository
 */
@Repository
public interface VoiceInterviewEvaluationRepository extends JpaRepository<VoiceInterviewEvaluationEntity, Long> {

    /**
     * 根据会话ID查找评估结果（一对一关系）
     */
    Optional<VoiceInterviewEvaluationEntity> findBySessionId(Long sessionId);

    /**
     * 批量根据会话ID查找评估结果
     */
    List<VoiceInterviewEvaluationEntity> findBySessionIdIn(Collection<Long> sessionIds);

    /**
     * 平均分
     */
    @Query("SELECT AVG(e.overallScore) FROM VoiceInterviewEvaluationEntity e")
    Double findAverageScore();

    /**
     * 分数 >= 指定值的记录数
     */
    long countByOverallScoreGreaterThanEqual(Integer score);
}