package com.interview.modules.interview.controller;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.interview.modules.interview.controller.TechHotwords.HOTWORDS;

/**
 * 语音识别（ASR）控制器
 * 使用 DashScope Fun-ASR / Paraformer 实时语音识别 SDK
 * 接收前端 MediaRecorder 录制的 WebM/Opus 音频，转为文字
 */
@RestController
@RequestMapping("/api/audio")
public class AudioController {
    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    @Value("${AI_API_KEY}")
    private String apiKey;

    @Value("${AI_MODEL:qwen-turbo}")
    private String aiModel;

    @PostConstruct
    public void init() {
        log.info("===== ASR Controller 初始化 =====");
        log.info("AI_API_KEY 状态: {}", apiKey != null && !apiKey.isEmpty() ? "已配置" : "未配置");
        if (apiKey != null && !apiKey.isEmpty()) {
            log.info("API Key 前8位: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
        }
        // 设置 DashScope WebSocket 服务端点（北京地域）
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        log.info("DashScope WebSocket 端点: {}", Constants.baseWebsocketApiUrl);
    }

    /**
     * ASR 调试/状态接口
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        return ResponseEntity.ok(Map.of(
                "apiKeyConfigured", apiKey != null && !apiKey.isEmpty(),
                "apiKeyPrefix", apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "(空)",
                "asrModel", "qwen3-asr-flash",
                "wsEndpoint", Constants.baseWebsocketApiUrl,
                "status", "ASR 服务已就绪"
        ));
    }

    /**
     * 获取当前 ASR 提供商信息（前端专用）
     */
    @GetMapping("/provider")
    public ResponseEntity<Map<String, Object>> getProvider() {
        return ResponseEntity.ok(Map.of(
                "provider", "dashscope",
                "name", "DashScope qwen3-asr-flash"
        ));
    }

    /**
     * 音频文件转文字（ASR）
     * 接收前端录制的音频，使用 DashScope Recognition SDK 进行语音识别
     */
    @PostMapping("/asr")
    public ResponseEntity<Map<String, Object>> transcribeAudio(@RequestParam("audio") MultipartFile audioFile) {
        long startTime = System.currentTimeMillis();
        log.info("===== ASR 请求开始 =====");

        if (audioFile.isEmpty()) {
            log.warn("ASR 失败: 音频文件为空");
            return ResponseEntity.badRequest().body(Map.of("error", "音频文件为空"));
        }

        // 打印请求详情
        log.info("音频文件信息:");
        log.info("  原始文件名: {}", audioFile.getOriginalFilename());
        log.info("  文件大小: {} bytes ({} KB)", audioFile.getSize(), audioFile.getSize() / 1024.0);
        log.info("  Content-Type: {}", audioFile.getContentType());

        try {
            byte[] audioBytes = audioFile.getBytes();
            log.info("  实际读取字节数: {}", audioBytes.length);

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("AI_API_KEY 未配置，ASR 不可用");
                return ResponseEntity.ok(Map.of(
                        "text", "",
                        "elapsed", System.currentTimeMillis() - startTime,
                        "warning", "ASR 不可用: 请配置 API_KEY"
                ));
            }

            log.info("使用 DashScope qwen3-asr-flash ASR...");
            String text = callQwenAsrFlash(audioBytes, audioFile.getContentType());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ASR 总耗时: {} ms", elapsed);

            if (text != null && !text.isBlank()) {
                log.info("ASR 识别成功! 文本: \"{}\"", text);
                return ResponseEntity.ok(Map.of(
                        "text", text,
                        "elapsed", elapsed,
                        "status", "success"
                ));
            }

            log.warn("ASR 识别完成但无有效文本返回 (耗时: {} ms)", elapsed);
            return ResponseEntity.ok(Map.of(
                    "text", "",
                    "elapsed", elapsed,
                    "warning", "ASR 未能识别出有效文本"
            ));
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("ASR 处理异常 (耗时: {} ms): {}", elapsed, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "text", "",
                    "elapsed", elapsed,
                    "error", "语音识别失败: " + e.getMessage()
            ));
        } finally {
            log.info("===== ASR 请求结束 =====");
        }
    }

    /**
     * 音频文件转文字（流式 ASR）- 使用 SSE 实时推送识别结果到前端
     * 使用回调式流式识别，分块发送音频，逐步产出中间结果
     */
    @PostMapping(value = "/asr/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTranscribeAudio(@RequestParam("audio") MultipartFile audioFile) {
        long startTime = System.currentTimeMillis();
        log.info("===== ASR 流式请求开始 =====");

        SseEmitter emitter = new SseEmitter(300000L); // 5 分钟超时

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("AI_API_KEY 未配置，ASR 流式跳过");
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "API Key 未配置")));
            } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }

        try {
            if (audioFile.isEmpty()) {
                log.warn("ASR 流式失败: 音频文件为空");
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "音频文件为空")));
                emitter.complete();
                return emitter;
            }

            byte[] audioBytes = audioFile.getBytes();
            String contentType = audioFile.getContentType();
            String format = detectFormat(contentType);
            String ext = formatToExtension(format);

            log.info("ASR 流式参数: size={}KB, format={}, contentType={}",
                    audioBytes.length / 1024.0, format, contentType);

            File tempFile = File.createTempFile("asr_stream_", "." + ext);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(audioBytes);
            }

            RecognitionParam param = RecognitionParam.builder()
                    .model("paraformer-realtime-v2")
                    .apiKey(apiKey)
                    .format(format)
                    .sampleRate(16000)
                    .parameter("language_hints", new String[]{"zh", "en"})
                    .parameter("hotwords", HOTWORDS)
                    .build();

            Recognition recognizer = new Recognition();

            CompletableFuture.runAsync(() -> {
                try {
                    log.info("ASR 流式: 开始回调识别...");

                    recognizer.call(param, new ResultCallback<RecognitionResult>() {
                        @Override
                        public void onEvent(RecognitionResult result) {
                            try {
                                String text = result.getSentence().getText();
                                boolean isEnd = result.isSentenceEnd();

                                if (text != null && !text.isBlank()) {
                                    log.debug("ASR 中间结果: text=\"{}\", isSentenceEnd={}", text, isEnd);
                                    emitter.send(SseEmitter.event()
                                            .name("transcript")
                                            .data(Map.of("text", text, "isFinal", isEnd)));
                                }
                            } catch (IOException e) {
                                log.error("SSE 发送失败: {}", e.getMessage());
                            }
                        }

                        @Override
                        public void onComplete() {
                            log.info("ASR 流式识别完成");
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("complete")
                                        .data(Map.of("status", "done",
                                                "elapsed", System.currentTimeMillis() - startTime)));
                            } catch (IOException ignored) {}
                            emitter.complete();
                        }

                        @Override
                        public void onError(Exception e) {
                            log.error("ASR 流式识别错误: {}", e.getMessage());
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(Map.of("error", e.getMessage())));
                            } catch (IOException ignored) {}
                            emitter.completeWithError(e);
                        }
                    });

                    // 分块发送音频（每块 ~100ms，3200 字节 @ 16kHz 16bit mono）
                    try (FileInputStream fis = new FileInputStream(tempFile)) {
                        byte[] buffer = new byte[3200];
                        int bytesRead;
                        int chunkIndex = 0;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            recognizer.sendAudioFrame(ByteBuffer.wrap(buffer, 0, bytesRead));
                            chunkIndex++;
                            Thread.sleep(50); // 控制发送速率
                        }
                        log.info("ASR 流式: 共发送 {} 个音频块", chunkIndex);
                    }

                    recognizer.stop();

                } catch (Exception e) {
                    log.error("ASR 流式处理异常: {}", e.getMessage(), e);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("error", e.getMessage())));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(e);
                } finally {
                    try {
                        if (recognizer.getDuplexApi() != null) {
                            recognizer.getDuplexApi().close(1000, "bye");
                        }
                    } catch (Exception ignored) {}
                    if (tempFile.exists() && !tempFile.delete()) {
                        log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                    }
                    log.info("ASR 流式请求结束, 总耗时: {}ms", System.currentTimeMillis() - startTime);
                }
            });

            return emitter;

        } catch (Exception e) {
            log.error("ASR 流式初始化失败: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage())));
            } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }
    }

    /**
     * 使用 DashScope qwen3-asr-flash 将音频转为文字（非流式）
     * 通过 MultiModalConversation API 传入 Base64 编码的音频
     */
    private String callQwenAsrFlash(byte[] audioBytes, String contentType) {
        long startTime = System.currentTimeMillis();
        log.info("qwen3-asr-flash ASR 开始...");

        try {
            // 将音频转为 Base64 Data URI
            String mimeType = detectMimeType(contentType);
            String base64 = Base64.getEncoder().encodeToString(audioBytes);
            String dataUri = "data:" + mimeType + ";base64," + base64;
            log.info("音频已编码: dataUri length={}, mimeType={}", dataUri.length(), mimeType);

            // 构建 MultiModalConversation 请求
            MultiModalConversation conv = new MultiModalConversation();

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Collections.singletonList(
                            Collections.singletonMap("audio", dataUri)))
                    .build();

            Map<String, Object> asrOptions = new HashMap<>();
            asrOptions.put("enable_itn", false);

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen3-asr-flash")
                    .message(userMessage)
                    .parameter("asr_options", asrOptions)
                    .build();

            log.info("调用 MultiModalConversation API...");
            MultiModalConversationResult result = conv.call(param);

            // 从结果中提取识别文本
            String text = extractAsrText(result);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("qwen3-asr-flash 识别完成: text=\"{}\", 耗时={}ms",
                    text != null ? text.substring(0, Math.min(50, text.length())) : "null", elapsed);
            return text;

        } catch (Exception e) {
            log.error("qwen3-asr-flash ASR 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 MultiModalConversationResult 中提取 ASR 识别文本
     * 响应格式: { output: { choices: [{ message: { content: [{ text: "..." }] } }] } }
     */
    private String extractAsrText(MultiModalConversationResult result) {
        try {
            String jsonStr = JsonUtils.toJson(result);
            JsonObject root = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();

            if (root.has("output")) {
                JsonObject output = root.getAsJsonObject("output");
                if (output.has("choices")) {
                    JsonArray choices = output.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonObject choice = choices.get(0).getAsJsonObject();
                        if (choice.has("message")) {
                            JsonObject message = choice.getAsJsonObject("message");
                            if (message.has("content")) {
                                JsonArray content = message.getAsJsonArray("content");
                                if (content != null && content.size() > 0) {
                                    JsonObject firstContent = content.get(0).getAsJsonObject();
                                    if (firstContent.has("text")) {
                                        return firstContent.get("text").getAsString();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 如果以上路径解析失败，记录完整响应用于调试
            log.warn("无法从 ASR 结果中提取文本，完整响应: {}", jsonStr);
            return null;
        } catch (Exception e) {
            log.warn("解析 ASR 结果异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据 Content-Type 获取对应的 MIME 类型（用于 Base64 Data URI）
     */
    private String detectMimeType(String contentType) {
        if (contentType == null) return "audio/wav";
        String ct = contentType.toLowerCase();
        if (ct.contains("webm")) return "audio/webm";
        if (ct.contains("opus")) return "audio/opus";
        if (ct.contains("wav")) return "audio/wav";
        if (ct.contains("mp3")) return "audio/mpeg";
        if (ct.contains("mp4") || ct.contains("aac")) return "audio/aac";
        if (ct.contains("amr")) return "audio/amr";
        if (ct.contains("ogg")) return "audio/ogg";
        return "audio/wav";
    }

    /**
     * 根据 Content-Type 确定 DashScope ASR 的 format 参数
     * 支持的格式: pcm, wav, mp3, opus, aac, amr, speex
     */
    private String detectFormat(String contentType) {
        if (contentType == null) {
            log.info("Content-Type 为 null，默认使用 pcm");
            return "pcm";
        }
        String ct = contentType.toLowerCase();
        log.debug("检测音频格式: Content-Type={}", ct);
        if (ct.contains("webm") || ct.contains("opus")) return "opus";
        if (ct.contains("wav")) return "wav";
        if (ct.contains("mp3")) return "mp3";
        if (ct.contains("mp4") || ct.contains("aac")) return "aac";
        if (ct.contains("amr")) return "amr";
        if (ct.contains("speex") || ct.contains("spx")) return "speex";
        if (ct.contains("pcm") || ct.contains("raw")) return "pcm";
        // 默认根据扩展名判断，回退到 pcm
        log.info("未知 Content-Type: {}，默认使用 pcm", ct);
        return "pcm";
    }

    private String formatToExtension(String format) {
        return switch (format) {
            case "opus" -> "opus";
            case "wav" -> "wav";
            case "mp3" -> "mp3";
            case "aac" -> "aac";
            case "amr" -> "amr";
            case "speex" -> "speex";
            default -> "pcm";
        };
    }
}



