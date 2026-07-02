package com.interview.modules.resume.service;

import com.interview.common.result.PageResult;
import com.interview.modules.resume.model.Resume;
import com.interview.modules.resume.model.ResumeVO;
import com.interview.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private TikaService tikaService;

    @Mock
    private ResumeAnalysisService analysisService;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private software.amazon.awssdk.services.s3.S3Client s3Client;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Mock
    private com.interview.infrastructure.stream.producer.TaskProducer taskProducer;

    @InjectMocks
    private ResumeService resumeService;

    private Resume resume;

    @BeforeEach
    void setUp() {
        resume = new Resume();
        resume.setId(1L);
        resume.setCandidateName("张三");
        resume.setCandidateRole("Java开发工程师");
        resume.setEducation("本科");
        resume.setMatchScore(85);
        resume.setContentHash("abc123");
        resume.setCreatedAt(LocalDateTime.now());
        resume.setDeleted(false);
    }

    // ==================== 获取所有简历 ====================

    @Test
    void shouldGetAllResumes() {
        when(resumeRepository.findByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(resume));

        List<ResumeVO> result = resumeService.getAllResumes();

        assertFalse(result.isEmpty());
        assertEquals("张三", result.get(0).getCandidateName());
    }

    // ==================== 分页查询 ====================

    @Test
    void shouldGetResumePage() {
        Page<Resume> page = new PageImpl<>(List.of(resume));
        when(resumeRepository.findByDeletedFalseOrderByCreatedAtDesc(any(PageRequest.class)))
                .thenReturn(page);

        PageResult<ResumeVO> result = resumeService.getResumePage(null, null, null, null, 0, 10);

        assertFalse(result.getList().isEmpty());
        assertEquals(1, result.getTotal());
        assertEquals("张三", result.getList().get(0).getCandidateName());
    }

    // ==================== 获取单个简历 ====================

    @Test
    void shouldGetResumeById() {
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(resume));

        ResumeVO result = resumeService.getResumeById(1L);

        assertEquals("张三", result.getCandidateName());
        assertEquals(85, result.getMatchScore());
    }

    @Test
    void shouldThrowWhenResumeNotFound() {
        when(resumeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> resumeService.getResumeById(999L));
    }

    // ==================== 软删除 ====================

    @Test
    void shouldSoftDelete() {
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(resume));

        resumeService.softDelete(1L);

        assertTrue(resume.getDeleted());
        verify(resumeRepository).save(resume);
    }

    // ==================== 批量软删除 ====================

    @Test
    void shouldBatchSoftDelete() {
        Resume resume2 = new Resume();
        resume2.setId(2L);
        resume2.setDeleted(false);

        when(resumeRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(resume, resume2));

        resumeService.batchSoftDelete(List.of(1L, 2L));

        assertTrue(resume.getDeleted());
        assertTrue(resume2.getDeleted());
        verify(resumeRepository).saveAll(anyList());
    }

    // ==================== 人才库 ====================

    @Test
    void shouldGetTalentPool() {
        when(resumeRepository.findByInTalentPoolTrueAndDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(resume));

        List<ResumeVO> result = resumeService.getTalentPool();

        assertFalse(result.isEmpty());
        assertEquals("张三", result.get(0).getCandidateName());
    }
}