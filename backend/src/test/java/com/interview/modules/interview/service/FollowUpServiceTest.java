package com.interview.modules.interview.service;

import com.interview.modules.interview.model.InterviewQuestion;
import com.interview.modules.interview.model.InterviewSession;
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
class FollowUpServiceTest {

    private FollowUpService followUpService;

    @Mock
    private org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder;
    @Mock
    private org.springframework.ai.chat.client.ChatClient chatClient;
    @Mock
    private org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private org.springframework.ai.chat.client.ChatClient.CallResponseSpec responseSpec;

    private InterviewQuestion question;
    private InterviewQuestion nextQuestion;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        followUpService = new FollowUpService(chatClientBuilder);

        question = new InterviewQuestion(UUID.randomUUID().toString(),
                "请简单介绍一下你自己。", "SKILL", "AI Agent开发", "校招");
        question.setStage("selfIntro");
        question.setCategory("自我介绍");
        question.setDifficultyScore(1);

        nextQuestion = new InterviewQuestion(UUID.randomUUID().toString(),
                "你在学校中最有成就感的一件事是什么？", "SKILL", "AI Agent开发", "校招");
        nextQuestion.setStage("selfIntro");
        nextQuestion.setCategory("自我介绍");
        nextQuestion.setDifficultyScore(1);
    }

    @Test
    void shouldUseFallbackWhenAIFails() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI超时"));

        String transition = followUpService.generateTransition(
                "就读北京大学", question, nextQuestion, "selfIntro", 2);

        assertNotNull(transition);
    }

    @Test
    void shouldDetermineFollowUpContinuation() {
        assertTrue(followUpService.shouldContinueFollowUp(1, 0));
        assertFalse(followUpService.shouldContinueFollowUp(1, 1));
        assertFalse(followUpService.shouldContinueFollowUp(0, 0));
        assertTrue(followUpService.shouldContinueFollowUp(3, 2));
        assertFalse(followUpService.shouldContinueFollowUp(3, 3));
    }

    @Test
    void shouldGenerateFollowUpWhenAISuccessful() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("可以详细说说你的专业方向吗？");

        String followUp = followUpService.generateFollowUp(
                "我学计算机的", question, 1, "selfIntro");

        assertNotNull(followUp);
        assertEquals("可以详细说说你的专业方向吗？", followUp);
    }

    @Test
    void shouldUseFallbackWhenAIFollowUpFails() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI错误"));

        String followUp = followUpService.generateFollowUp(
                "好的", question, 1, "selfIntro");

        assertNotNull(followUp);
    }

    @Test
    void shouldGenerateFallbackTransitionWithQuestionNumber() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI失败"));

        String transition = followUpService.generateTransition(
                "回答完毕", question, nextQuestion, "techExam", 1);

        assertNotNull(transition);
    }

    @Test
    void shouldGenerateQaAnswerWhenCandidateAsksQuestion() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("我们团队使用Spring Boot和React技术栈。你还有其他想了解的吗？");

        String answer = followUpService.generateQaAnswer(
                "你们团队主要用什么技术栈？", question);

        assertNotNull(answer);
    }

    @Test
    void shouldUseFallbackWhenQaAnswerFails() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI错误"));

        String answer = followUpService.generateQaAnswer(
                "你们团队主要用什么技术栈？", question);

        assertEquals("好的。如果你还有其他问题，可以继续问。", answer);
    }

    @Test
    void shouldHandleEmptyCandidateAnswer() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("请继续分享一下你的想法。");

        String followUp = followUpService.generateFollowUp(
                "", question, 1, "techExam");

        assertNotNull(followUp);
    }

    @Test
    void shouldGenerateTransitionForDifferentStages() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("fallback"));

        String selfIntroTransition = followUpService.generateTransition(
                "我喜欢编程", question, nextQuestion, "selfIntro", 2);
        assertNotNull(selfIntroTransition);

        String techTransition = followUpService.generateTransition(
                "回答完毕", question, nextQuestion, "techExam", 3);
        assertNotNull(techTransition);

        String qaTransition = followUpService.generateTransition(
                "好的", question, nextQuestion, "qaRound", 4);
        assertNotNull(qaTransition);
    }
}
