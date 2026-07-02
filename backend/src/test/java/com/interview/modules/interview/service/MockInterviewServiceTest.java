package com.interview.modules.interview.service;

import com.interview.modules.evaluation.engine.UnifiedEvaluationEngine;
import com.interview.modules.interview.model.InterviewQuestion;
import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.repository.InterviewSessionRecordRepository;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockInterviewServiceTest {

    @Mock
    private QuestionGeneratorService questionGenerator;
    @Mock
    private FollowUpService followUpService;
    @Mock
    private JDParseService jdParseService;
    @Mock
    private DirectionRecommendService directionRecommendService;
    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private InterviewSessionRecordRepository interviewRecordRepository;
    @Mock
    private RealTimeScoreService realTimeScoreService;
    @Mock
    private UnifiedEvaluationEngine evaluationEngine;

    private MockInterviewService service;

    @BeforeEach
    void setUp() {
        service = new MockInterviewService(questionGenerator, followUpService,
                jdParseService, directionRecommendService, sessionRepository,
                interviewRecordRepository, realTimeScoreService, evaluationEngine);
    }

    private InterviewSession createFreshSession() {
        InterviewSession s = new InterviewSession();
        s.setSessionId(UUID.randomUUID().toString());
        s.setCandidateId("cand-001");
        s.setCandidateName("张三");
        s.setDirection("AI Agent开发");
        s.setLevel("校招");
        s.setResumeText("熟悉Java、Spring Boot、微服务架构");
        s.setTotalDuration(60);
        s.setFollowUpCount(1);
        return s;
    }

    @Test
    void shouldCreateSessionSuccessfully() {
        var request = new MockInterviewService.CreateSessionRequest();
        request.setCandidateId("cand-001");
        request.setCandidateName("张三");
        request.setDirection("AI Agent开发");
        request.setLevel("校招");
        request.setTotalDuration(60);
        request.setFollowUpCount(1);
        request.setMode("text");

        doNothing().when(sessionRepository).save(any(InterviewSession.class));

        InterviewSession created = service.createSession(request);

        assertNotNull(created);
        assertEquals("PREPARING", created.getStatus());
        assertNotNull(created.getSessionId());
        verify(sessionRepository).save(any(InterviewSession.class));
    }

    @Test
    void shouldStartSelfIntroStage() {
        InterviewSession session = createFreshSession();
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        List<InterviewQuestion> questions = List.of(
                new InterviewQuestion("Q1", "自我介绍", "SKILL", "AI Agent开发", "校招")
        );
        when(questionGenerator.generateQuestions(anyString(), anyString(), anyString(),
                eq("selfIntro"), anyString(), anyInt())).thenReturn(questions);
        doNothing().when(sessionRepository).save(any(InterviewSession.class));

        InterviewSession started = service.startInterview(session.getSessionId());

        assertEquals("IN_PROGRESS", started.getStatus());
        assertEquals("selfIntro", started.getCurrentStage());
        // addMessage 只对候选人回答增加轮次，面试官欢迎消息不增加轮次
        assertEquals(0, started.getCurrentRound());
        assertFalse(started.getQuestions().isEmpty());
        assertEquals(1, started.getMessages().size());
        assertEquals("interviewer", started.getMessages().get(0).getSender());
    }

    @Test
    void shouldPauseAndUnpauseSession() {
        InterviewSession session = createFreshSession();
        session.setStatus("IN_PROGRESS");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        doNothing().when(sessionRepository).save(any(InterviewSession.class));

        InterviewSession paused = service.pauseSession(session.getSessionId());
        assertEquals("PAUSED", paused.getStatus());

        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(paused));
        InterviewSession resumed = service.unpauseSession(session.getSessionId());
        assertEquals("IN_PROGRESS", resumed.getStatus());
    }

    @Test
    void shouldThrowWhenPausingNonInProgressSession() {
        InterviewSession session = createFreshSession();
        session.setStatus("COMPLETED");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(RuntimeException.class,
                () -> service.pauseSession(session.getSessionId()));
    }

    @Test
    void shouldEndInterview() {
        InterviewSession session = createFreshSession();
        session.setStatus("IN_PROGRESS");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        doNothing().when(sessionRepository).save(any(InterviewSession.class));

        InterviewSession ended = service.endInterview(session.getSessionId());

        assertEquals("COMPLETED", ended.getStatus());
        assertNotNull(ended.getCompletedAt());
    }

    @Test
    void shouldResumeInProgressSession() {
        InterviewSession session = createFreshSession();
        session.setStatus("IN_PROGRESS");
        session.setCurrentStage("techExam");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        doNothing().when(sessionRepository).save(any(InterviewSession.class));

        InterviewSession resumed = service.resumeSession(session.getSessionId());
        assertTrue(resumed.getMessages().stream()
                .anyMatch(m -> m.getText().contains("欢迎回来")));
    }

    @Test
    void shouldThrowWhenResumingNonActiveSession() {
        InterviewSession session = createFreshSession();
        session.setStatus("COMPLETED");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(RuntimeException.class,
                () -> service.resumeSession(session.getSessionId()));
    }

    @Test
    void shouldThrowWhenPausingAlreadyPausedSession() {
        InterviewSession session = createFreshSession();
        session.setStatus("PAUSED");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(RuntimeException.class,
                () -> service.pauseSession(session.getSessionId()));
    }

    @Test
    void shouldThrowWhenUnpausingNonPausedSession() {
        InterviewSession session = createFreshSession();
        session.setStatus("IN_PROGRESS");
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(RuntimeException.class,
                () -> service.unpauseSession(session.getSessionId()));
    }
}