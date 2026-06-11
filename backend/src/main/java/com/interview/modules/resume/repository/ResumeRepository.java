package com.interview.modules.resume.repository;

import com.interview.modules.resume.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    /** 按创建时间倒序查询所有简历 */
    List<Resume> findAllByOrderByCreatedAtDesc();

    /** 按候选人姓名模糊搜索 */
    List<Resume> findByCandidateNameContainingIgnoreCase(String name);
}
