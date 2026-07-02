package com.interview.modules.resume.controller;

import com.interview.common.result.PageResult;
import com.interview.modules.resume.model.ResumeVO;
import com.interview.modules.resume.service.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ResumeControllerTest {

    @Mock
    private ResumeService resumeService;

    @InjectMocks
    private ResumeController controller;

    private MockMvc mockMvc;

    private ResumeVO resumeVO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        resumeVO = new ResumeVO();
        resumeVO.setId(1L);
        resumeVO.setCandidateName("张三");
        resumeVO.setEducation("本科");
        resumeVO.setMatchScore(85);
        resumeVO.setCreatedAt(LocalDateTime.now().toString());
    }

    // ==================== 简历列表 ====================

    @Test
    void shouldListAllResumes() throws Exception {
        when(resumeService.getAllResumes()).thenReturn(List.of(resumeVO));

        mockMvc.perform(get("/api/resume/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].candidateName").value("张三"));
    }

    // ==================== 分页查询 ====================

    @Test
    void shouldPageResumes() throws Exception {
        PageResult<ResumeVO> pageResult = new PageResult<>();
        pageResult.setList(List.of(resumeVO));
        pageResult.setTotal(10);
        pageResult.setTotalPages(1);

        when(resumeService.getResumePage(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/resume/page")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(10));
    }

    // ==================== 人才库 ====================

    @Test
    void shouldGetTalentPool() throws Exception {
        when(resumeService.getTalentPool()).thenReturn(List.of(resumeVO));

        mockMvc.perform(get("/api/resume/talent-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].candidateName").value("张三"));
    }

    // ==================== 移入人才库 ====================

    @Test
    void shouldMoveToTalentPool() throws Exception {
        when(resumeService.moveToTalentPool(1L)).thenReturn(resumeVO);

        mockMvc.perform(post("/api/resume/1/to-talent-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenMoveToTalentPoolFails() throws Exception {
        when(resumeService.moveToTalentPool(999L))
                .thenThrow(new RuntimeException("简历不存在"));

        mockMvc.perform(post("/api/resume/999/to-talent-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("简历不存在"));
    }
}