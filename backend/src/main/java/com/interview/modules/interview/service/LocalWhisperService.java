package com.interview.modules.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * 本地 faster-whisper ASR 服务
 * <p>
 * 调用本地 Python faster-whisper HTTP 服务进行语音识别，
 * 替代付费的 DashScope ASR。
 * <p>
 * Python 服务启动方式:
 *   cd backend/src/main/python
 *   pip install -r requirements.txt
 *   python whisper_server.py --model base --device cpu --port 9090
 */
@Service
public class LocalWhisperService {

    private static final Logger log = LoggerFactory.getLogger(LocalWhisperService.class);

    @Value("${whisper.enabled:false}")
    private boolean whisperEnabled;

    @Value("${whisper.base-url:http://localhost:9090}")
    private String baseUrl;

    @Value("${whisper.model:base}")
    private String model;

    @Value("${whisper.device:cpu}")
    private String device;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("===== Local Whisper ASR 初始化 =====");
        log.info("  启用状态: {}", whisperEnabled);
        log.info("  服务地址: {}", baseUrl);
        log.info("  模型: {}", model);
        log.info("  设备: {}", device);

        if (whisperEnabled) {
            // 启动时检查连接
            try {
                ResponseEntity<Map> resp = restTemplate.getForEntity(
                        baseUrl + "/health", Map.class);
                log.info("  Whisper 服务连接成功: {}", resp.getBody());
            } catch (ResourceAccessException e) {
                log.warn("  Whisper 服务未就绪 (请启动 Python 服务): {}", e.getMessage());
            }
        }
    }

    /**
     * 是否启用了本地 Whisper
     */
    public boolean isEnabled() {
        return whisperEnabled;
    }

    /**
     * 将音频文件转写为文字
     *
     * @param audioFile 音频文件
     * @return 识别结果文本，失败返回空字符串
     */
    public String transcribe(MultipartFile audioFile) {
        if (!whisperEnabled) {
            log.warn("Whisper 未启用，跳过本地 ASR");
            return null;
        }

        long startTime = System.currentTimeMillis();
        log.info("===== Whisper ASR 请求开始 =====");

        try {
            // 构建 multipart 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename() != null
                            ? audioFile.getOriginalFilename()
                            : "audio.webm";
                }
            };
            body.add("audio", resource);
            body.add("model", model);
            body.add("device", device);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("调用 Whisper 服务: {}/asr (size={}KB)", baseUrl, audioFile.getSize() / 1024.0);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/asr",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            long elapsed = System.currentTimeMillis() - startTime;

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String text = (String) response.getBody().get("text");
                if (text != null && !text.isBlank()) {
                    log.info("Whisper ASR 成功: text=\"{}\" (耗时: {}ms)", text, elapsed);
                    return text;
                }
                log.warn("Whisper ASR 返回空文本 (耗时: {}ms)", elapsed);
                return "";
            }

            log.warn("Whisper 服务返回非正常状态: {} (耗时: {}ms)", response.getStatusCode(), elapsed);
            return "";

        } catch (ResourceAccessException e) {
            log.error("Whisper 服务连接失败 (请确认 Python 服务已启动): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Whisper ASR 请求异常: {}", e.getMessage(), e);
            return null;
        } finally {
            log.info("===== Whisper ASR 请求结束 =====");
        }
    }
}
