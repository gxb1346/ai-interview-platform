package com.interview.modules.interview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealTimeScoreServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private RealTimeScoreService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        service = new RealTimeScoreService(chatClientBuilder);
    }

    // ==================== 基本评分 ====================

    @Test
    void shouldScoreAnswer() {
        String aiResponse = "{\"score\":85,\"feedback\":\"回答专业，有深度\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        RealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "请介绍你的项目经验", "我做过一个电商平台，用了Spring Boot和MyBatis...", "projectDeep");

        assertNotNull(result);
        assertEquals(85, result.score);
        assertEquals("回答专业，有深度", result.feedback);
    }

    @Test
    void shouldReturnNullOnAiFailure() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI service unavailable"));

        RealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "问题", "回答", "selfIntro");

        assertNull(result);
    }

    // ==================== 分数边界 ====================

    @Test
    void shouldClampNegativeScore() {
        String aiResponse = "{\"score\":-10,\"feedback\":\"无效\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        RealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "selfIntro");

        assertNotNull(result);
        assertEquals(0, result.score);
    }

    @Test
    void shouldClampScoreAbove100() {
        String aiResponse = "{\"score\":150,\"feedback\":\"超出范围\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        RealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "selfIntro");

        assertNotNull(result);
        assertEquals(100, result.score);
    }

    // ==================== 重试逻辑 ====================

    @Test
    void shouldRetryOnFailure() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        // 第一次失败，第二次成功
        when(responseSpec.content())
                .thenThrow(new RuntimeException("first attempt failed"))
                .thenReturn("{\"score\":75,\"feedback\":\"重试成功\"}");

        RealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "selfIntro");

        assertNotNull(result);
        assertEquals(75, result.score);
        // 验证调用了两次（一次失败 + 一次重试）
        verify(responseSpec, times(2)).content();
    }

    // ==================== 超时 ====================

    @Test
    void shouldReturnNullOnTimeout() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        // 模拟耗时操作（超过超时时间）
        when(responseSpec.content()).thenAnswer(invocation -> {
            Thread.sleep(2000);
            return "{\"score\":80}";
        });

        RealTimeScoreService.ScoreResult result = service.scoreAnswerWithTimeout(
                "Q", "A", "selfIntro", 1);

        assertNull(result);
    }

    // ==================== 无效 JSON ====================

    @Test
    void shouldReturnNullOnInvalidJson() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("这不是有效的 JSON 格式");

        RealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "selfIntro");

        assertNull(result);
    }
}