package com.interview.modules.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音面试 WebSocket 处理器
 * 统一转发模式：ASR 识别结果 → REST /chat 处理 → 返回结果给 WebSocket 客户端
 * 与路径A（前端 ASR → REST chat）共享同一套后端逻辑
 */
@Slf4j
@Component
public class VoiceInterviewWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Gson gson = new Gson();
    private final RestTemplate restTemplate = new RestTemplate();

    /** 管理活跃 WebSocket 连接：sessionId -> WebSocketSession */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /** 后端 REST API 地址 */
    private static final String BACKEND_CHAT_URL = "http://localhost:8082/api/mock-interview/sessions/%s/chat";

    public VoiceInterviewWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String path = session.getUri().getPath();
        String sessionId = extractSessionId(path);
        if (sessionId != null) {
            activeSessions.put(sessionId, session);
            log.info("WebSocket 语音面试连接建立: sessionId={}", sessionId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String type = (String) data.getOrDefault("type", "");
            String sessionId = (String) data.getOrDefault("sessionId", "");

            switch (type) {
                case "ASR_RESULT" -> handleAsrResult(session, sessionId, data);
                case "PING" -> sendMessage(session, Map.of("type", "PONG"));
                default -> sendMessage(session, Map.of("type", "ERROR", "message", "未知消息类型: " + type));
            }
        } catch (Exception e) {
            log.error("WebSocket 消息处理失败: {}", e.getMessage());
            sendMessage(session, Map.of("type", "ERROR", "message", "处理失败: " + e.getMessage()));
        }
    }

    /**
     * 处理 ASR 识别结果：转发到 REST /chat 端点
     */
    private void handleAsrResult(WebSocketSession session, String sessionId, Map<String, Object> data) throws IOException {
        try {
            String text = (String) data.getOrDefault("text", "");
            if (text.isBlank()) {
                sendMessage(session, Map.of("type", "ERROR", "message", "识别文本为空"));
                return;
            }

            // 转发到 REST /chat 端点
            String chatUrl = String.format(BACKEND_CHAT_URL, sessionId);
            JsonObject chatBody = new JsonObject();
            chatBody.addProperty("answer", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(chatBody.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(chatUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonObject replyJson = gson.fromJson(response.getBody(), JsonObject.class);

                // 将 REST 响应包装为 WebSocket 消息格式
                JsonObject wsMsg = new JsonObject();
                wsMsg.addProperty("type", "INTERVIEWER_REPLY");
                wsMsg.addProperty("sessionId", sessionId);
                wsMsg.addProperty("reply", replyJson.get("reply").getAsString());
                wsMsg.addProperty("currentStage", replyJson.get("currentStage").getAsString());
                wsMsg.addProperty("status", replyJson.get("status").getAsString());

                if (replyJson.has("audio")) {
                    wsMsg.addProperty("audio", replyJson.get("audio").getAsString());
                }
                if (replyJson.has("score")) {
                    wsMsg.addProperty("score", replyJson.get("score").getAsInt());
                    wsMsg.addProperty("scoreFeedback", replyJson.get("scoreFeedback").getAsString());
                }

                session.sendMessage(new TextMessage(wsMsg.toString()));
                log.info("语音面试转发成功: sessionId={}, textLen={}", sessionId, text.length());
            } else {
                log.warn("REST /chat 返回非成功状态: {}", response.getStatusCode());
                sendMessage(session, Map.of("type", "ERROR", "message", "面试处理失败"));
            }
        } catch (Exception e) {
            log.error("语音面试转发失败: sessionId={}, error={}", sessionId, e.getMessage());
            sendMessage(session, Map.of("type", "ERROR", "message", "处理失败: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.values().remove(session);
        log.info("WebSocket 语音面试连接关闭: {}", status);
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        session.sendMessage(new TextMessage(json));
    }

    private String extractSessionId(String path) {
        if (path == null) return null;
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }
}