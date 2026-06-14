package com.interview.modules.resume.repository;

import com.interview.modules.resume.model.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    /** 分页查询未删除的简历，按创建时间倒序 */
    Page<Resume> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /** 统计未删除的简历总数 */
    long countByDeletedFalse();

    /** 按候选人姓名模糊搜索（未删除） */
    Page<Resume> findByDeletedFalseAndCandidateNameContainingIgnoreCase(String name, Pageable pageable);

    /** 按文件名模糊搜索（未删除） */
    Page<Resume> findByDeletedFalseAndFileNameContainingIgnoreCase(String name, Pageable pageable);

    /** 多条件组合搜索 */
    @Query("""
        SELECT r FROM Resume r
        WHERE r.deleted = false
        AND (:keyword IS NULL OR :keyword = ''
            OR LOWER(r.candidateName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(r.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(r.candidateRole) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:education IS NULL OR :education = '' OR LOWER(r.education) LIKE LOWER(CONCAT('%', :education, '%')))
        AND (:minScore IS NULL OR r.matchScore >= :minScore)
        AND (:maxScore IS NULL OR r.matchScore <= :maxScore)
        ORDER BY r.createdAt DESC
    """)
    Page<Resume> searchByFilters(
            @Param("keyword") String keyword,
            @Param("education") String education,
            @Param("minScore") Integer minScore,
            @Param("maxScore") Integer maxScore,
            Pageable pageable
    );

    /** 统计筛选结果总数 */
    @Query("""
        SELECT COUNT(r) FROM Resume r
        WHERE r.deleted = false
        AND (:keyword IS NULL OR :keyword = ''
            OR LOWER(r.candidateName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(r.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(r.candidateRole) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:education IS NULL OR :education = '' OR LOWER(r.education) LIKE LOWER(CONCAT('%', :education, '%')))
        AND (:minScore IS NULL OR r.matchScore >= :minScore)
        AND (:maxScore IS NULL OR r.matchScore <= :maxScore)
    """)
    long countByFilters(
            @Param("keyword") String keyword,
            @Param("education") String education,
            @Param("minScore") Integer minScore,
            @Param("maxScore") Integer maxScore
    );

    /** 按内容哈希查找未删除的简历（支持去重） */
    java.util.List<Resume> findByContentHashAndDeletedFalse(String contentHash);

    /** 查找所有未删除的简历（兼容旧版） */
    java.util.List<Resume> findByDeletedFalseOrderByCreatedAtDesc();

    /** 查找已移入人才库且未删除的候选人 */
    java.util.List<Resume> findByInTalentPoolTrueAndDeletedFalseOrderByCreatedAtDesc();

    /** 查找已移入人才库的候选人 */
    java.util.List<Resume> findByInTalentPoolTrueOrderByCreatedAtDesc();

    /** 按候选人姓名模糊搜索（兼容旧版） */
    java.util.List<Resume> findByDeletedFalseAndCandidateNameContainingIgnoreCase(String name);
}
