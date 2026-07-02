package com.interview.modules.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.modules.interview.model.InterviewMessage;
import com.interview.modules.interview.model.InterviewQuestion;
import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.service.AudioService;
import com.interview.modules.interview.service.MockInterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MockInterviewControllerTest {

    @Mock
    private MockInterviewService interviewService;

    @Mock
    private AudioService audioService;

    @InjectMocks
    private MockInterviewController controller;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    private InterviewSession session;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .build();

        session = new InterviewSession();
        session.setSessionId("test-session-001");
        session.setCandidateId("cand-001");
        session.setCandidateName("张三");
        session.setDirection("AI Agent开发");
        session.setLevel("校招");
        session.setStatus("PREPARING");
        session.setTotalDuration(60);
        session.setFollowUpCount(1);
        session.setCurrentRound(0);
        session.setCurrentStage("selfIntro");
        session.setQuestions(new ArrayList<>());
        session.setMessages(new ArrayList<>());
    }

    // ==================== 创建会话 ====================

    @Test
    void shouldCreateSession() throws Exception {
        when(interviewService.createSession(any())).thenReturn(session);

        mockMvc.perform(post("/api/mock-interview/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "candidateId": "cand-001",
                                "candidateName": "张三",
                                "direction": "AI Agent开发",
                                "level": "校招",
                                "totalDuration": 60,
                                "followUpCount": 1,
                                "mode": "text"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session-001"))
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    // ==================== 开始面试 ====================

    @Test
    void shouldStartInterview() throws Exception {
        InterviewQuestion question = new InterviewQuestion("Q1", "自我介绍", "SKILL", "AI Agent开发", "校招");
        question.setCategory("自我介绍");
        question.setDifficultyScore(1);
        session.setQuestions(List.of(question));
        session.setStatus("IN_PROGRESS");

        InterviewMessage msg = new InterviewMessage("M1", "interviewer", "欢迎参加面试");
        msg.setStage("selfIntro");
        msg.setRoundNumber(0);
        session.setMessages(List.of(msg));

        when(interviewService.startInterview("test-session-001")).thenReturn(session);

        mockMvc.perform(post("/api/mock-interview/sessions/test-session-001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session-001"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.questions").isArray())
                .andExpect(jsonPath("$.questions[0].id").value("Q1"))
                .andExpect(jsonPath("$.currentStage").value("selfIntro"))
                .andExpect(jsonPath("$.messages").isArray());
    }

    // ==================== 提交回答 ====================

    @Test
    void shouldChat() throws Exception {
        InterviewMessage reply = new InterviewMessage("M2", "interviewer", "很好，请继续说说你的项目经验");
        reply.setStage("selfIntro");
        reply.setRoundNumber(1);
        session.setMessages(List.of(reply));
        session.setStatus("IN_PROGRESS");
        session.setCurrentRound(1);
        session.setCurrentQuestionIndex(0);
        session.setQuestions(List.of(new InterviewQuestion("Q1", "xxx", "SKILL", "AI Agent开发", "校招")));

        when(interviewService.processAnswer(eq("test-session-001"), anyString())).thenReturn(session);
        when(audioService.textToSpeechBase64(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/mock-interview/sessions/test-session-001/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": \"我做过很多项目\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("很好，请继续说说你的项目经验"))
                .andExpect(jsonPath("$.currentRound").value(1))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // ==================== 暂停/恢复 ====================

    @Test
    void shouldPauseInterview() throws Exception {
        session.setStatus("PAUSED");
        when(interviewService.pauseSession("test-session-001")).thenReturn(session);

        mockMvc.perform(post("/api/mock-interview/sessions/test-session-001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session-001"))
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void shouldUnpauseInterview() throws Exception {
        session.setStatus("IN_PROGRESS");
        when(interviewService.unpauseSession("test-session-001")).thenReturn(session);

        mockMvc.perform(post("/api/mock-interview/sessions/test-session-001/unpause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // ==================== 结束面试 ====================

    @Test
    void shouldEndInterview() throws Exception {
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        when(interviewService.endInterview("test-session-001")).thenReturn(session);

        mockMvc.perform(post("/api/mock-interview/sessions/test-session-001/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ==================== 获取会话 ====================

    @Test
    void shouldGetSession() throws Exception {
        when(interviewService.getSession("test-session-001")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/mock-interview/sessions/test-session-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session-001"));
    }

    @Test
    void shouldReturn404WhenSessionNotFound() throws Exception {
        when(interviewService.getSession("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/mock-interview/sessions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ==================== 恢复会话 ====================

    @Test
    void shouldResumeSession() throws Exception {
        session.setStatus("IN_PROGRESS");
        when(interviewService.resumeSession("test-session-001")).thenReturn(session);

        mockMvc.perform(post("/api/mock-interview/sessions/test-session-001/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // ==================== 批量删除 ====================

    @Test
    void shouldBatchDeleteSessions() throws Exception {
        when(interviewService.batchDeleteSessions(anyList())).thenReturn(3);

        mockMvc.perform(post("/api/mock-interview/sessions/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"s1\", \"s2\", \"s3\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(3));
    }

    @Test
    void shouldRejectEmptyBatchDelete() throws Exception {
        mockMvc.perform(post("/api/mock-interview/sessions/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    // ==================== 搜索 ====================

    @Test
    void shouldSearchSessions() throws Exception {
        when(interviewService.searchSessions(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/mock-interview/sessions/search")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}