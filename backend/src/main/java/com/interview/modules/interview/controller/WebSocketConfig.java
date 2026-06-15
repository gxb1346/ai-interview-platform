package com.interview.modules.interview.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceInterviewWebSocketHandler voiceHandler;
    private final AsrWebSocketHandler asrHandler;

    public WebSocketConfig(VoiceInterviewWebSocketHandler voiceHandler,
                           AsrWebSocketHandler asrHandler) {
        this.voiceHandler = voiceHandler;
        this.asrHandler = asrHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 语音面试 WebSocket 端点
        registry.addHandler(voiceHandler, "/ws/voice-interview/{sessionId}")
                .setAllowedOrigins("*");

        // 实时 ASR 语音识别端点
        registry.addHandler(asrHandler, "/ws/asr")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(65536);
        container.setMaxBinaryMessageBufferSize(65536);
        container.setMaxSessionIdleTimeout(1800000L); // 30分钟超时
        return container;
    }
}
