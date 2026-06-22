package com.interview.modules.interview.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.concurrent.*;

/**
 * 语音合成服务
 * 基于本地 Edge-TTS 将 AI 回复文本转为语音（免费，无需 API Key）
 * 替代了付费的 DashScope CosyVoice TTS
 */
@Service
public class AudioService {
    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    @Value("${edge-tts.base-url:http://localhost:9091}")
    private String edgeTtsBaseUrl;

    private final RestTemplate restTemplate;
    private final Gson gson;

    // 独立的 TTS 线程池：最多 4 个并发 TTS 请求，防止阻塞主请求线程
    private static final ExecutorService ttsExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "tts-worker");
        t.setDaemon(true);
        return t;
    });

    // TTS API 超时时间（秒）：超过此时间未返回音频则放弃，保障面试回复实时性
    private static final long TTS_TIMEOUT_SECONDS = 15;

    // 最大合成文本长度（字符），防止 edge-tts 超时
    private static final int MAX_TTS_TEXT_LENGTH = 500;

    public AudioService() {
        this.restTemplate = new RestTemplate();
        this.gson = new Gson();
    }

    @PostConstruct
    public void init() {
        log.info("===== TTS AudioService 初始化 =====");
        log.info("TTS 提供商: 本地 Edge-TTS (免费)");
        log.info("Edge-TTS 服务地址: {}", edgeTtsBaseUrl);
        log.info("最大合成文本长度: {} 字符", MAX_TTS_TEXT_LENGTH);
        log.info("TTS 线程池: 4 线程");
        log.info("TTS 超时: {} 秒", TTS_TIMEOUT_SECONDS);
    }

    /**
     * 将文本转为语音，返回 Base64 编码的 MP3 音频数据
     * 通过调用本地 Edge-TTS Python 服务实现（免费，无需 API Key）
     *
     * @param text 待合成文本
     * @return Base64 编码的音频字节（MP3 格式），失败时返回 null
     */
    public String textToSpeechBase64(String text) {
        if (text == null || text.isBlank()) {
            log.warn("TTS 跳过：文本为空");
            return null;
        }

        // 截断过长文本以保障响应速度
        String ttsText = text.length() > MAX_TTS_TEXT_LENGTH
                ? text.substring(0, MAX_TTS_TEXT_LENGTH)
                : text;

        log.info("TTS 请求: text=\"{}\" ({}字符)", ttsText.substring(0, Math.min(30, ttsText.length())), ttsText.length());

        try {
            // 异步调用 Edge-TTS 服务，带超时控制
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return callEdgeTts(ttsText);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, ttsExecutor);

            // 等待结果，超时则放弃
            String audioBase64 = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (audioBase64 != null && !audioBase64.isEmpty()) {
                log.info("TTS 合成成功: base64长度={}, 文本=\"{}\"",
                        audioBase64.length(), ttsText.substring(0, Math.min(20, ttsText.length())));
                return audioBase64;
            }

            log.warn("TTS 合成返回空音频");
            return null;

        } catch (TimeoutException e) {
            log.warn("TTS 超时 ({}秒): {}", TTS_TIMEOUT_SECONDS, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("TTS 合成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // 最大重试次数（应对 Edge-TTS 间歇性网络波动）
    private static final int TTS_MAX_RETRIES = 2;

    /**
     * 调用 Edge-TTS HTTP 服务进行语音合成（带重试机制）
     */
    private String callEdgeTts(String text) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt <= TTS_MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Edge-TTS 重试 {}/{}", attempt, TTS_MAX_RETRIES);
                    Thread.sleep(500); // 重试前等待 500ms
                }
                return callEdgeTtsOnce(text);
            } catch (Exception e) {
                lastException = e;
                if (attempt < TTS_MAX_RETRIES) {
                    log.warn("Edge-TTS 第{}次调用失败，准备重试: {}", attempt + 1, e.getMessage());
                }
            }
        }

        throw lastException != null ? lastException : new BusinessException(ErrorCode.TTS_SERVICE_ERROR, "Edge-TTS 所有重试均失败");
    }

    /**
     * 单次调用 Edge-TTS HTTP 服务
     */
    private String callEdgeTtsOnce(String text) throws Exception {
        // 构建请求 JSON
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("text", text);
        requestBody.addProperty("voice", "zh-CN-YunxiNeural");  // 沉稳男声，适合面试
        requestBody.addProperty("rate", "+0%");
        requestBody.addProperty("volume", "+0%");
        requestBody.addProperty("pitch", "+0Hz");

        String jsonBody = requestBody.toString();

        log.debug("Edge-TTS 请求体: {}", jsonBody);

        // 使用 Spring RestTemplate 发送 POST 请求（比 Java HttpClient 更可靠）
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                edgeTtsBaseUrl + "/tts",
                requestEntity,
                String.class
        );

        int statusCode = responseEntity.getStatusCode().value();
        if (statusCode != 200) {
            log.warn("Edge-TTS 返回非 200 状态码: {}, body={}",
                    statusCode, responseEntity.getBody());
            throw new BusinessException(ErrorCode.TTS_SERVICE_ERROR, "Edge-TTS HTTP " + statusCode);
        }

        String responseBody = responseEntity.getBody();
        // 解析响应 JSON
        JsonObject respJson = gson.fromJson(responseBody, JsonObject.class);
        if (respJson == null || !respJson.has("audio")) {
            log.warn("Edge-TTS 响应缺少 audio 字段: {}", responseBody);
            throw new BusinessException(ErrorCode.TTS_SERVICE_ERROR, "Edge-TTS 响应缺少 audio 字段");
        }

        return respJson.get("audio").getAsString();
    }
}