package com.interview.modules.voiceinterview.service;

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
class VoiceInterviewRealTimeScoreServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private VoiceInterviewRealTimeScoreService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        service = new VoiceInterviewRealTimeScoreService(chatClientBuilder);
    }

    // ==================== 基本评分 ====================

    @Test
    void shouldScoreAnswer() {
        String aiResponse = "{\"score\":85,\"feedback\":\"回答专业，有深度\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "请介绍你的项目经验", "我做过一个电商平台，用了Spring Boot和MyBatis...", "PROJECT");

        assertNotNull(result);
        assertEquals(85, result.score());
        assertEquals("回答专业，有深度", result.feedback());
    }

    // ==================== AI 失败降级 ====================

    @Test
    void shouldReturnNullOnAiFailure() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI service unavailable"));

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "问题", "回答", "INTRO");

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

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "INTRO");

        assertNotNull(result);
        assertEquals(0, result.score());
    }

    @Test
    void shouldClampOverScore() {
        String aiResponse = "{\"score\":150,\"feedback\":\"超高分\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "INTRO");

        assertNotNull(result);
        assertEquals(100, result.score());
    }

    // ==================== JSON 清洗：Markdown 代码块 ====================

    @Test
    void shouldParseJsonWithMarkdownCodeBlock() {
        String aiResponse = "```json\n{\"score\":72,\"feedback\":\"回答基本完整\"}\n```";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "请自我介绍", "我是张三，有3年Java经验", "INTRO");

        assertNotNull(result);
        assertEquals(72, result.score());
        assertEquals("回答基本完整", result.feedback());
    }

    @Test
    void shouldParseJsonWithPlainCodeBlock() {
        String aiResponse = "```\n{\"score\":60,\"feedback\":\"回答一般\"}\n```";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "TECH");

        assertNotNull(result);
        assertEquals(60, result.score());
    }

    // ==================== JSON 清洗：前面有文字说明 ====================

    @Test
    void shouldParseJsonWithLeadingText() {
        String aiResponse = "好的，这是评分结果：\n{\"score\":88,\"feedback\":\"非常专业\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "PROJECT");

        assertNotNull(result);
        assertEquals(88, result.score());
    }

    // ==================== 无效 JSON 降级 ====================

    @Test
    void shouldReturnNullOnInvalidJson() {
        String aiResponse = "评分：85分，表现不错";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "INTRO");

        assertNull(result);
    }

    @Test
    void shouldReturnNullOnEmptyResponse() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("");

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer("Q", "A", "INTRO");

        assertNull(result);
    }

    // ==================== 不同阶段评分 ====================

    @Test
    void shouldScoreForIntroPhase() {
        String aiResponse = "{\"score\":45,\"feedback\":\"自我介绍过于简短\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "请自我介绍", "我叫张三", "INTRO");

        assertNotNull(result);
        assertEquals(45, result.score());
    }

    @Test
    void shouldScoreForTechPhase() {
        String aiResponse = "{\"score\":90,\"feedback\":\"技术理解深入\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "请解释Spring Boot自动配置原理", "Spring Boot通过@EnableAutoConfiguration注解...", "TECH");

        assertNotNull(result);
        assertEquals(90, result.score());
    }

    @Test
    void shouldScoreForProjectPhase() {
        String aiResponse = "{\"score\":78,\"feedback\":\"项目描述清晰但缺少技术细节\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "请描述你参与的项目", "我做了一个微服务架构的电商平台", "PROJECT");

        assertNotNull(result);
        assertEquals(78, result.score());
    }

    @Test
    void shouldScoreForHrPhase() {
        String aiResponse = "{\"score\":65,\"feedback\":\"职业规划不够明确\"}";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        VoiceInterviewRealTimeScoreService.ScoreResult result = service.scoreAnswer(
                "你的职业规划是什么", "我想做技术专家", "HR");

        assertNotNull(result);
        assertEquals(65, result.score());
    }
}