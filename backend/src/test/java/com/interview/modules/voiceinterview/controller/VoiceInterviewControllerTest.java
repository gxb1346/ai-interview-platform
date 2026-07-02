package com.interview.modules.voiceinterview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.modules.voiceinterview.dto.*;
import com.interview.modules.voiceinterview.listener.VoiceEvaluateStreamProducer;
import com.interview.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import com.interview.modules.voiceinterview.service.VoiceInterviewEvaluationService;
import com.interview.modules.voiceinterview.service.VoiceInterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VoiceInterviewControllerTest {

    @Mock
    private VoiceInterviewService voiceInterviewService;

    @Mock
    private VoiceInterviewEvaluationService evaluationService;

    @Mock
    private VoiceEvaluateStreamProducer voiceEvaluateStreamProducer;

    @InjectMocks
    private VoiceInterviewController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== 创建会话 ====================

    @Test
    void shouldCreateSession() throws Exception {
        SessionResponseDTO response = buildSessionResponse(1L, "java-backend");
        when(voiceInterviewService.createSession(any())).thenReturn(response);

        mockMvc.perform(post("/api/voice-interview/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "roleType": "java-backend",
                                "candidateName": "张三",
                                "difficulty": "mid"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(1))
                .andExpect(jsonPath("$.data.roleType").value("java-backend"));
    }

    @Test
    void shouldCreateSessionWithEmptyBody() throws Exception {
        SessionResponseDTO response = buildSessionResponse(1L, "");
        when(voiceInterviewService.createSession(any())).thenReturn(response);

        mockMvc.perform(post("/api/voice-interview/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ==================== 获取会话 ====================

    @Test
    void shouldGetSession() throws Exception {
        SessionResponseDTO response = buildSessionResponse(1L, "java-backend");
        when(voiceInterviewService.getSessionDTO(1L)).thenReturn(response);

        mockMvc.perform(get("/api/voice-interview/sessions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(1));
    }

    @Test
    void shouldReturnErrorWhenSessionNotFound() throws Exception {
        when(voiceInterviewService.getSessionDTO(99L)).thenReturn(null);

        mockMvc.perform(get("/api/voice-interview/sessions/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Session not found: 99"));
    }

    // ==================== 结束会话 ====================

    @Test
    void shouldEndSession() throws Exception {
        doNothing().when(voiceInterviewService).endSession("1");

        mockMvc.perform(post("/api/voice-interview/sessions/1/end"))
                .andExpect(status().isOk());
    }

    // ==================== 获取消息列表 ====================

    @Test
    void shouldGetMessages() throws Exception {
        List<VoiceInterviewMessageDTO> messages = Arrays.asList(
                createMessageDTO(1L, "AI_SPEECH", "你好，请做自我介绍"),
                createMessageDTO(2L, "USER_SPEECH", "我是张三，有3年Java开发经验")
        );
        when(voiceInterviewService.getConversationHistoryDTO("1")).thenReturn(messages);

        mockMvc.perform(get("/api/voice-interview/sessions/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void shouldReturnEmptyMessagesForNewSession() throws Exception {
        when(voiceInterviewService.getConversationHistoryDTO("1")).thenReturn(List.of());

        mockMvc.perform(get("/api/voice-interview/sessions/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ==================== 获取评估状态 ====================

    @Test
    void shouldReturnEvaluationStatusPending() throws Exception {
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
                .id(1L)
                .evaluateStatus(AsyncTaskStatus.PENDING)
                .build();
        when(voiceInterviewService.getSession(1L)).thenReturn(session);

        mockMvc.perform(get("/api/voice-interview/sessions/1/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluateStatus").value("PENDING"));
    }

    @Test
    void shouldReturnEvaluationCompleted() throws Exception {
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
                .id(1L)
                .evaluateStatus(AsyncTaskStatus.COMPLETED)
                .build();
        VoiceEvaluationDetailDTO evaluation = VoiceEvaluationDetailDTO.builder()
                .overallScore(85)
                .overallFeedback("表现良好")
                .build();
        when(voiceInterviewService.getSession(1L)).thenReturn(session);
        when(evaluationService.getEvaluation(1L)).thenReturn(evaluation);

        mockMvc.perform(get("/api/voice-interview/sessions/1/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluateStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.evaluation.overallScore").value(85));
    }

    @Test
    void shouldThrowExceptionWhenSessionNotFoundForEvaluation() {
        when(voiceInterviewService.getSession(99L)).thenReturn(null);

        Assertions.assertThrows(Exception.class, () -> {
            mockMvc.perform(get("/api/voice-interview/sessions/99/evaluation"));
        });
    }

    // ==================== 触发评估 ====================

    @Test
    void shouldTriggerEvaluation() throws Exception {
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
                .id(1L)
                .evaluateStatus(AsyncTaskStatus.PENDING)
                .build();
        when(voiceInterviewService.getSession(1L)).thenReturn(session);
        doNothing().when(voiceInterviewService).triggerEvaluation(1L);

        mockMvc.perform(post("/api/voice-interview/sessions/1/evaluation"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotRetriggerWhenProcessing() throws Exception {
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
                .id(1L)
                .evaluateStatus(AsyncTaskStatus.PROCESSING)
                .build();
        when(voiceInterviewService.getSession(1L)).thenReturn(session);

        mockMvc.perform(post("/api/voice-interview/sessions/1/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluateStatus").value("PROCESSING"));
        verify(voiceInterviewService, never()).triggerEvaluation(anyLong());
    }

    // ==================== 获取所有会话 ====================

    @Test
    void shouldGetAllSessions() throws Exception {
        List<SessionMetaDTO> sessions = Arrays.asList(
                SessionMetaDTO.builder().sessionId(1L).candidateName("张三").roleType("java-backend").status("IN_PROGRESS").build(),
                SessionMetaDTO.builder().sessionId(2L).candidateName("李四").roleType("python").status("COMPLETED").build()
        );
        when(voiceInterviewService.getAllSessions(null, null)).thenReturn(sessions);

        mockMvc.perform(get("/api/voice-interview/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ==================== 删除会话 ====================

    @Test
    void shouldDeleteSession() throws Exception {
        doNothing().when(voiceInterviewService).deleteSession(1L);

        mockMvc.perform(delete("/api/voice-interview/sessions/1"))
                .andExpect(status().isOk());
    }

    // ==================== 暂停/恢复 ====================

    @Test
    void shouldPauseSession() throws Exception {
        doNothing().when(voiceInterviewService).pauseSession(eq("1"), anyString());

        mockMvc.perform(put("/api/voice-interview/sessions/1/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"user_initiated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldResumeSession() throws Exception {
        SessionResponseDTO response = buildSessionResponse(1L, "java-backend");
        when(voiceInterviewService.resumeSession("1")).thenReturn(response);

        mockMvc.perform(put("/api/voice-interview/sessions/1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    // ==================== Helper ====================

    private SessionResponseDTO buildSessionResponse(Long sessionId, String roleType) {
        return SessionResponseDTO.builder()
                .sessionId(sessionId)
                .roleType(roleType)
                .status("IN_PROGRESS")
                .currentPhase("INTRO")
                .webSocketUrl("ws://localhost:8082/ws/voice-interview/" + sessionId)
                .build();
    }

    private VoiceInterviewMessageDTO createMessageDTO(Long id, String type, String text) {
        VoiceInterviewMessageDTO dto = new VoiceInterviewMessageDTO();
        dto.setId(id);
        dto.setMessageType(type);
        if ("AI_SPEECH".equals(type)) {
            dto.setAiGeneratedText(text);
        } else {
            dto.setUserRecognizedText(text);
        }
        return dto;
    }
}