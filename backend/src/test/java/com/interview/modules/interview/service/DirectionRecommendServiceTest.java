package com.interview.modules.interview.service;

import com.interview.modules.interview.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectionRecommendServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    @Mock
    private SkillRegistry skillRegistry;

    private DirectionRecommendService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString()).build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        when(skillRegistry.getAllDirectionNames()).thenReturn(
                List.of("Java后端开发", "前端工程", "算法与数据结构", "系统设计", "AI Agent开发"));

        service = new DirectionRecommendService(chatClientBuilder, skillRegistry);
    }

    @Test
    void shouldReturnDefaultForNullText() {
        List<DirectionRecommendService.DirectionMatch> result = service.recommend(null);

        assertEquals(3, result.size());
        assertEquals("Java后端开发", result.get(0).getDirection());
        assertEquals(85, result.get(0).getMatchScore());
    }

    @Test
    void shouldReturnDefaultForBlankText() {
        List<DirectionRecommendService.DirectionMatch> result = service.recommend("   ");

        assertEquals(3, result.size());
    }

    @Test
    void shouldRecommendDirections() {
        String jsonResponse = "[{\"direction\":\"Java后端开发\",\"matchScore\":95,\"reason\":\"有丰富的Java项目经验\"},"
                + "{\"direction\":\"系统设计\",\"matchScore\":80,\"reason\":\"有分布式系统设计经验\"},"
                + "{\"direction\":\"AI Agent开发\",\"matchScore\":70,\"reason\":\"有AI项目背景\"}]";
        when(responseSpec.content()).thenReturn(jsonResponse);

        List<DirectionRecommendService.DirectionMatch> result = service.recommend("5年Java开发经验");

        assertEquals(3, result.size());
        assertEquals("Java后端开发", result.get(0).getDirection());
        assertEquals(95, result.get(0).getMatchScore());
    }

    @Test
    void shouldHandleMarkdownJsonBlock() {
        String jsonResponse = "```json\n[{\"direction\":\"AI Agent开发\",\"matchScore\":90,\"reason\":\"匹配\"}]\n```";
        when(responseSpec.content()).thenReturn(jsonResponse);

        List<DirectionRecommendService.DirectionMatch> result = service.recommend("AI Agent开发经验");

        assertEquals(1, result.size());
        assertEquals("AI Agent开发", result.get(0).getDirection());
    }

    @Test
    void shouldFallbackOnAiFailure() {
        when(responseSpec.content()).thenThrow(new RuntimeException("AI服务不可用"));

        List<DirectionRecommendService.DirectionMatch> result = service.recommend("简历内容");

        assertEquals(3, result.size());
        assertEquals("Java后端开发", result.get(0).getDirection());
    }
}