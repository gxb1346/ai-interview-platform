package com.interview.modules.interview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AudioService audioService;

    @BeforeEach
    void setUp() {
        // AudioService 构造函数中 new RestTemplate()，需要替换为 mock
        ReflectionTestUtils.setField(audioService, "restTemplate", restTemplate);
        // 注入 @Value 字段
        ReflectionTestUtils.setField(audioService, "edgeTtsBaseUrl", "http://localhost:9091");
    }

    // ==================== 空文本 ====================

    @Test
    void shouldReturnNullForNullText() {
        assertNull(audioService.textToSpeechBase64(null));
    }

    @Test
    void shouldReturnNullForBlankText() {
        assertNull(audioService.textToSpeechBase64("   "));
    }

    @Test
    void shouldReturnNullForEmptyText() {
        assertNull(audioService.textToSpeechBase64(""));
    }

    // ==================== 文本截断 ====================

    @Test
    void shouldTruncateLongText() {
        // MAX_TTS_TEXT_LENGTH = 500
        String longText = "A".repeat(600);
        String audioBase64 = Base64.getEncoder().encodeToString("fake-audio".getBytes());
        String responseJson = "{\"audio\":\"" + audioBase64 + "\"}";

        when(restTemplate.postForEntity(
                eq("http://localhost:9091/tts"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        String result = audioService.textToSpeechBase64(longText);

        assertNotNull(result);
        assertEquals(audioBase64, result);
    }

    // ==================== 缓存 ====================

    @Test
    void shouldCacheTtsResult() {
        String text = "你好，欢迎参加面试";
        String audioBase64 = Base64.getEncoder().encodeToString("cached-audio".getBytes());
        String responseJson = "{\"audio\":\"" + audioBase64 + "\"}";

        when(restTemplate.postForEntity(
                eq("http://localhost:9091/tts"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        // 第一次调用
        String result1 = audioService.textToSpeechBase64(text);
        assertNotNull(result1);

        // 第二次调用（缓存命中，不应再调用 RestTemplate）
        String result2 = audioService.textToSpeechBase64(text);
        assertEquals(result1, result2);

        // 验证只调用了一次 HTTP
        verify(restTemplate, times(1)).postForEntity(
                eq("http://localhost:9091/tts"),
                any(HttpEntity.class),
                eq(String.class));
    }

    // ==================== 失败处理 ====================

    @Test
    void shouldReturnNullOnHttpFailure() {
        when(restTemplate.postForEntity(
                eq("http://localhost:9091/tts"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = audioService.textToSpeechBase64("测试文本");

        assertNull(result);
    }

    // ==================== 响应缺少 audio 字段 ====================

    @Test
    void shouldReturnNullOnMissingAudioField() {
        String responseJson = "{\"error\":\"something wrong\"}";

        when(restTemplate.postForEntity(
                eq("http://localhost:9091/tts"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        String result = audioService.textToSpeechBase64("测试文本");

        assertNull(result);
    }
}