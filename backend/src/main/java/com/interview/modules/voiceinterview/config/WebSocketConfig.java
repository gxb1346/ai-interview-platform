package com.interview.modules.voiceinterview.config;

import com.interview.common.config.CorsProperties;
import com.interview.modules.interview.controller.AsrWebSocketHandler;
import com.interview.modules.voiceinterview.handler.VoiceInterviewWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration("voiceInterviewWebSocketConfig")
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceInterviewWebSocketHandler voiceInterviewWebSocketHandler;
    private final AsrWebSocketHandler asrWebSocketHandler;
    private final CorsProperties corsProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 语音面试 WebSocket 端点（新版）
        registry.addHandler(voiceInterviewWebSocketHandler, "/ws/voice-interview/{sessionId}")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins(corsProperties.getAllowedOrigins().split(","));

        // 实时 ASR 语音识别端点（供模拟面试 MockInterviewView 使用）
        registry.addHandler(asrWebSocketHandler, "/ws/asr")
                .setAllowedOrigins(corsProperties.getAllowedOrigins().split(","));
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        container.setMaxSessionIdleTimeout(1800000L); // 30分钟超时
        return container;
    }
}