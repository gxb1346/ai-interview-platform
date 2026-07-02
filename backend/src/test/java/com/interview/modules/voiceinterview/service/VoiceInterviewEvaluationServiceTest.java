package com.interview.modules.voiceinterview.service;

import com.interview.common.ai.LlmProviderRegistry;
import com.interview.common.evaluation.EvaluationReport;
import com.interview.common.evaluation.QaRecord;
import com.interview.common.evaluation.UnifiedEvaluationService;
import com.interview.modules.voiceinterview.model.VoiceInterviewMessageEntity;
import com.interview.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import com.interview.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import com.interview.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import com.interview.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceInterviewEvaluationServiceTest {

    @Mock
    private UnifiedEvaluationService unifiedEvaluationService;

    @Mock
    private LlmProviderRegistry llmProviderRegistry;

    @Mock
    private VoiceInterviewEvaluationRepository evaluationRepository;

    @Mock
    private VoiceInterviewMessageRepository messageRepository;

    @Mock
    private VoiceInterviewSessionRepository sessionRepository;

    @Mock
    private ChatClient chatClient;

    private VoiceInterviewEvaluationService service;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new VoiceInterviewEvaluationService(
                unifiedEvaluationService,
                llmProviderRegistry,
                evaluationRepository,
                messageRepository,
                sessionRepository,
                objectMapper
        );
    }

    // ==================== 空消息保护 ====================

    @Test
    void shouldGenerateEmptyEvaluationForNoMessages() {
        VoiceInterviewSessionEntity session = buildSession(1L, "dashscope");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
        when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.empty());

        // 空消息不应调用 unifiedEvaluationService
        service.generateEvaluation(1L);

        verify(unifiedEvaluationService, never()).evaluate(any(), any(), any(), any(), any());
    }

    // ==================== 正常评估 ====================

    @Test
    void shouldGenerateEvaluation() {
        VoiceInterviewSessionEntity session = buildSession(1L, "dashscope");
        List<VoiceInterviewMessageEntity> messages = buildTwoRoundMessages();

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(messages);
        when(llmProviderRegistry.getPlainChatClient("dashscope")).thenReturn(chatClient);
        when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.empty());

        EvaluationReport report = buildReport(85, "良好");
        when(unifiedEvaluationService.evaluate(any(), any(), any(), isNull(), any()))
                .thenReturn(report);

        service.generateEvaluation(1L);

        verify(unifiedEvaluationService).evaluate(eq(chatClient), eq("1"), any(), isNull(), any());
    }

    // ==================== 获取评估 DTO ====================

    @Test
    void shouldReturnEvaluationDetail() {
        var entity = com.interview.modules.voiceinterview.model.VoiceInterviewEvaluationEntity.builder()
                .sessionId(1L)
                .overallScore(85)
                .overallFeedback("表现良好")
                .questionEvaluationsJson("[]")
                .strengthsJson("[]")
                .improvementsJson("[]")
                .referenceAnswersJson("[]")
                .build();
        when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.of(entity));

        var detail = service.getEvaluation(1L);

        assertNotNull(detail);
        assertEquals(85, detail.getOverallScore());
        assertEquals("表现良好", detail.getOverallFeedback());
    }

    // ==================== QA 构建：单轮对话 ====================

    @Test
    void shouldBuildQaRecordsForSingleRound() {
        List<VoiceInterviewMessageEntity> messages = Arrays.asList(
                buildMessage("AI_SPEECH", "请做自我介绍", null),
                buildMessage("USER_SPEECH", null, "我是张三")
        );

        // 通过反射调用 private buildQaRecords 不方便，这里通过集成测试验证
        // 但我们可以通过 generateEvaluation 间接验证
        VoiceInterviewSessionEntity session = buildSession(1L, "dashscope");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(messages);
        when(llmProviderRegistry.getPlainChatClient("dashscope")).thenReturn(chatClient);
        when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.empty());

        EvaluationReport report = buildReport(80, "OK");
        when(unifiedEvaluationService.evaluate(any(), any(), any(), isNull(), any()))
                .thenReturn(report);

        service.generateEvaluation(1L);

        // 验证 evaluate 被调用，说明 QA 构建成功
        verify(unifiedEvaluationService).evaluate(eq(chatClient), eq("1"), any(), isNull(), any());
    }

    // ==================== 评估失败 ====================

    @Test
    void shouldHandleEvaluationFailure() {
        VoiceInterviewSessionEntity session = buildSession(1L, "dashscope");
        List<VoiceInterviewMessageEntity> messages = buildTwoRoundMessages();

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumAsc(1L)).thenReturn(messages);
        when(llmProviderRegistry.getPlainChatClient("dashscope")).thenReturn(chatClient);
        when(unifiedEvaluationService.evaluate(any(), any(), any(), isNull(), any()))
                .thenThrow(new RuntimeException("LLM timeout"));

        assertThrows(Exception.class, () -> service.generateEvaluation(1L));
    }

    // ==================== Helper ====================

    private VoiceInterviewSessionEntity buildSession(Long id, String llmProvider) {
        return VoiceInterviewSessionEntity.builder()
                .id(id)
                .roleType("java-backend")
                .llmProvider(llmProvider)
                .build();
    }

    private VoiceInterviewMessageEntity buildMessage(String type, String aiText, String userText) {
        return VoiceInterviewMessageEntity.builder()
                .sessionId(1L)
                .messageType(type)
                .aiGeneratedText(aiText)
                .userRecognizedText(userText)
                .phase(VoiceInterviewSessionEntity.InterviewPhase.INTRO)
                .sequenceNum(1)
                .build();
    }

    private List<VoiceInterviewMessageEntity> buildTwoRoundMessages() {
        return Arrays.asList(
                buildMessage("AI_SPEECH", "请做自我介绍", null),
                buildMessage("USER_SPEECH", null, "我是张三，3年Java开发经验"),
                buildMessage("AI_SPEECH", "请详细介绍你做过的一个项目", null),
                buildMessage("USER_SPEECH", null, "我做过一个电商平台")
        );
    }

    private EvaluationReport buildReport(int score, String feedback) {
        return new EvaluationReport(
                "1",
                4,
                score,
                List.of(),
                List.of(),
                feedback,
                List.of(),
                List.of(),
                List.of()
        );
    }
}