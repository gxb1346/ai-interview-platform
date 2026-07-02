package com.interview.modules.interview.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AudioControllerTest {

    @InjectMocks
    private AudioController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== 调试接口 ====================

    @Test
    void shouldReturnDebugInfo() throws Exception {
        ReflectionTestUtils.setField(controller, "apiKey", "sk-test12345678");

        mockMvc.perform(get("/api/audio/debug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.apiKeyPrefix").value("sk-test1..."))
                .andExpect(jsonPath("$.status").value("ASR 服务已就绪"));
    }

    @Test
    void shouldShowApiKeyNotConfigured() throws Exception {
        ReflectionTestUtils.setField(controller, "apiKey", null);

        mockMvc.perform(get("/api/audio/debug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyConfigured").value(false));
    }

    // ==================== 提供商信息 ====================

    @Test
    void shouldGetProvider() throws Exception {
        mockMvc.perform(get("/api/audio/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("dashscope"))
                .andExpect(jsonPath("$.name").isNotEmpty());
    }
}