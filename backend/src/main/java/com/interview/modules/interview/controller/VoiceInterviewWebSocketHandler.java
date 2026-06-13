package com.interview.modules.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.service.MockInterviewService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音面试 WebSocket 处理器
 * 语音面试使用 WebSocket 实现实时双向通信
 */
@Component
public class VoiceInterviewWebSocketHandler extends TextWebSocketHandler {

    private final MockInterviewService interviewService;
    private final ObjectMapper objectMapper;

    /** 管理活跃 WebSocket 连接：sessionId -> WebSocketSession */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public VoiceInterviewWebSocketHandler(MockInterviewService interviewService,
                                          ObjectMapper objectMapper) {
        this.interviewService = interviewService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 连接建立时，session 的 URI 中应包含 sessionId
        // 格式: /ws/voice-interview/{sessionId}
        String path = session.getUri().getPath();
        String sessionId = extractSessionId(path);
        if (sessionId != null) {
            activeSessions.put(sessionId, session);
            System.out.println("WebSocket 连接已建立: sessionId=" + sessionId);
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
                case "START" -> handleStart(session, sessionId);
                case "ANSWER" -> {
                    String answer = (String) data.getOrDefault("answer", "");
                    handleAnswer(session, sessionId, answer);
                }
                case "END" -> handleEnd(session, sessionId);
                case "PING" -> sendMessage(session, Map.of("type", "PONG"));
                default -> sendMessage(session, Map.of("type", "ERROR", "message", "未知消息类型: " + type));
            }
        } catch (Exception e) {
            sendMessage(session, Map.of("type", "ERROR", "message", "处理消息失败: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.values().remove(session);
        System.out.println("WebSocket 连接已关闭: " + status);
    }

    private void handleStart(WebSocketSession session, String sessionId) throws IOException {
        InterviewSession interviewSession = interviewService.startInterview(sessionId);
        sendMessage(session, Map.of(
                "type", "STARTED",
                "sessionId", sessionId,
                "firstMessage", interviewSession.getMessages().isEmpty()
                        ? "" : interviewSession.getMessages().get(0).getText(),
                "questions", interviewSession.getQuestions().stream()
                        .map(q -> Map.of("id", q.getId(), "text", q.getText()))
                        .toList()
        ));
    }

    private void handleAnswer(WebSocketSession session, String sessionId, String answer) throws IOException {
        InterviewSession interviewSession = interviewService.processAnswer(sessionId, answer);

        var messages = interviewSession.getMessages();
        String reply = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getText();

        sendMessage(session, Map.of(
                "type", "REPLY",
                "sessionId", sessionId,
                "reply", reply,
                "currentRound", interviewSession.getCurrentRound(),
                "status", interviewSession.getStatus()
        ));
    }

    private void handleEnd(WebSocketSession session, String sessionId) throws IOException {
        interviewService.endInterview(sessionId);
        sendMessage(session, Map.of(
                "type", "ENDED",
                "sessionId", sessionId
        ));
        session.close(CloseStatus.NORMAL);
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
