package com.interview.modules.interview.controller;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.interview.modules.interview.controller.TechHotwords.HOTWORDS;

/**
 * 实时 ASR 语音识别 WebSocket 处理器
 * 前端通过 WebSocket 实时发送 PCM 音频块，后端转发给 DashScope Recognition SDK，
 * 识别结果通过 WebSocket 实时返回给前端
 */
@Component
public class AsrWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AsrWebSocketHandler.class);

    @Value("${AI_API_KEY}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    /** 每个 WebSocket 连接对应一个 ASR 识别会话 */
    private final ConcurrentHashMap<String, AsrSession> sessions = new ConcurrentHashMap<>();

    public AsrWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("===== ASR WebSocket Handler 初始化 =====");
        log.info("WebSocket ASR 端点: /ws/asr");
        log.info("DashScope WebSocket 端点: wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        log.info("ASR API Key 状态: {}", apiKey != null && !apiKey.isEmpty() ? "已配置" : "未配置");
        // 设置 DashScope WebSocket 服务端点
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String id = session.getId();
        log.info("[ASR WS] 连接已建立: id={}, remote={}", id, session.getRemoteAddress());

        if (apiKey == null || apiKey.isEmpty()) {
            sendJson(session, Map.of("type", "error", "message", "API Key 未配置"));
            safeClose(session);
            return;
        }

        try {
            AsrSession asrSession = new AsrSession(session, apiKey);
            asrSession.start();
            sessions.put(id, asrSession);
            log.info("[ASR WS] ASR 会话创建成功: id={}", id);
            sendJson(session, Map.of("type", "ready", "message", "ASR 连接就绪，请开始说话"));
        } catch (Exception e) {
            log.error("[ASR WS] ASR 会话创建失败: id={}, err={}", id, e.getMessage(), e);
            sendJson(session, Map.of("type", "error", "message", "ASR 启动失败: " + e.getMessage()));
            safeClose(session);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        AsrSession asrSession = sessions.get(session.getId());
        if (asrSession == null) return;

        ByteBuffer payload = message.getPayload();
        if (payload.remaining() == 0) return;

        asrSession.sendAudio(payload);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if ("EOS".equals(payload)) {
            // 前端发来结束信号
            log.debug("[ASR WS] 收到 EOS 信号: id={}", session.getId());
            AsrSession asrSession = sessions.remove(session.getId());
            if (asrSession != null) {
                asrSession.stop();
            }
        } else {
            // 尝试解析 JSON 命令
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cmd = objectMapper.readValue(payload, Map.class);
                String type = (String) cmd.getOrDefault("type", "");
                if ("stop".equals(type)) {
                    AsrSession asrSession = sessions.remove(session.getId());
                    if (asrSession != null) {
                        asrSession.stop();
                    }
                }
            } catch (Exception e) {
                log.warn("[ASR WS] 未知文本消息: {}", payload);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String id = session.getId();
        log.info("[ASR WS] 连接关闭: id={}, status={}", id, status);
        AsrSession asrSession = sessions.remove(id);
        if (asrSession != null) {
            asrSession.stop();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[ASR WS] 传输错误: id={}, err={}", session.getId(), exception.getMessage());
        String id = session.getId();
        AsrSession asrSession = sessions.remove(id);
        if (asrSession != null) {
            asrSession.stop();
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> data) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("[ASR WS] 发送消息失败: {}", e.getMessage());
        }
    }

    private void safeClose(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 单个 ASR 识别会话
     * 封装 DashScope Recognition SDK 的调用生命周期
     */
    private static class AsrSession {
        private final WebSocketSession session;
        private final Recognition recognizer;
        private final String apiKey;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final ObjectMapper mapper = new ObjectMapper();

        AsrSession(WebSocketSession session, String apiKey) {
            this.session = session;
            this.apiKey = apiKey;
            this.recognizer = new Recognition();
        }

        /** 启动 ASR 识别会话 */
        void start() {
            RecognitionParam param = RecognitionParam.builder()
                    .model("paraformer-realtime-v2")
                    .apiKey(apiKey)
                    .format("pcm")            // 前端发送原始 PCM 16-bit 16kHz mono
                    .sampleRate(16000)
                    .parameter("language_hints", new String[]{"zh", "en"})
                    .parameter("semantic_punctuation_enabled", true)
                    .parameter("hotwords", HOTWORDS)
                    .build();

            log.debug("[ASR Session] 开始回调识别...");

            recognizer.call(param, new ResultCallback<RecognitionResult>() {
                @Override
                public void onEvent(RecognitionResult result) {
                    try {
                        if (result.getSentence() == null) return;
                        String text = result.getSentence().getText();
                        boolean isEnd = result.isSentenceEnd();

                        if (text != null && !text.isBlank()) {
                            log.debug("[ASR Session] 识别结果: text=\"{}\", isEnd={}", text, isEnd);
                            String json = mapper.writeValueAsString(Map.of(
                                    "type", "transcript",
                                    "text", text,
                                    "isFinal", isEnd
                            ));
                            synchronized (this) {
                                if (session.isOpen()) {
                                    session.sendMessage(new TextMessage(json));
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.warn("[ASR Session] 发送识别结果失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onComplete() {
                    log.debug("[ASR Session] 识别完成");
                    sendJson(Map.of("type", "complete", "status", "done"));
                }

                @Override
                public void onError(Exception e) {
                    log.error("[ASR Session] 识别错误: {}", e.getMessage());
                    sendJson(Map.of("type", "error", "message", e.getMessage()));
                }

                private void sendJson(Map<String, Object> data) {
                    try {
                        String json = mapper.writeValueAsString(data);
                        synchronized (this) {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(json));
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            });
        }

        /** 发送音频帧到 DashScope */
        void sendAudio(ByteBuffer buffer) {
            if (!closed.get()) {
                try {
                    recognizer.sendAudioFrame(buffer);
                } catch (Exception e) {
                    log.warn("[ASR Session] sendAudioFrame 异常: {}", e.getMessage());
                }
            }
        }

        /** 停止识别并释放资源 */
        void stop() {
            if (!closed.compareAndSet(false, true)) return;
            log.debug("[ASR Session] 正在停止识别...");
            try {
                recognizer.stop();
            } catch (Exception e) {
                log.warn("[ASR Session] stop() 异常: {}", e.getMessage());
            }
            try {
                if (recognizer.getDuplexApi() != null) {
                    recognizer.getDuplexApi().close(1000, "bye");
                }
            } catch (Exception ignored) {
            }
            log.debug("[ASR Session] 识别已停止");
        }
    }
}
