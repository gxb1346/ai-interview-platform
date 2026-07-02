package com.interview.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private HealthController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").value("AI面试平台后端已启动"));
    }

    @Test
    void shouldReturnDebugConfig() throws Exception {
        // standaloneSetup 不会处理 @Value 注入，手动设置
        ReflectionTestUtils.setField(controller, "apiKey", "sk-test-api-key-12345");
        ReflectionTestUtils.setField(controller, "aiModel", "test-model");
        ReflectionTestUtils.setField(controller, "baseUrl", "http://localhost:9999");

        mockMvc.perform(get("/api/debug/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("sk-test-..."))
                .andExpect(jsonPath("$.model").value("test-model"));
    }
}