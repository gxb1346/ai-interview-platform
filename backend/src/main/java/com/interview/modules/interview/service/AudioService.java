package com.interview.modules.interview.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 语音合成服务
 * 基于阿里云 DashScope CosyVoice / Qwen-TTS 将 AI 回复文本转为语音
 *
 * API 文档:
 *   CosyVoice: https://help.aliyun.com/zh/model-studio/non-realtime-cosyvoice-api/
 *   Qwen-TTS:  https://help.aliyun.com/zh/model-studio/qwen-tts-api
 */
@Service
public class AudioService {
    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    /** TTS 音频缓存前缀 */
    private static final String TTS_CACHE_PREFIX = "tts:audio:";

    /** TTS 缓存 TTL（小时），面试回复常有重复文本（如欢迎语、过渡语） */
    private static final long CACHE_TTL_HOURS = 24;

    @Value("${AI_API_KEY}")
    private String apiKey;

    private final StringRedisTemplate redisTemplate;

    public AudioService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("===== TTS AudioService 初始化 =====");
        log.info("AI_API_KEY 状态: {}", apiKey != null && !apiKey.isEmpty() ? "已配置" : "未配置");
        // CosyVoice 仅在北京地域可用
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        log.info("DashScope HTTP 端点: {}", Constants.baseHttpApiUrl);
    }

    /**
     * 将文本转为语音，返回 Base64 编码的 MP3 音频数据
     *
     * @param text 待合成文本
     * @return Base64 编码的音频字节，失败时返回 null
     */
     public String textToSpeechBase64(String text) {
        if (text == null || text.isBlank()) return null;
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("AI_API_KEY 未配置，TTS 功能不可用");
            return null;
        }

        // ---- TTS 缓存：相同文本不再合成 ----
        String textHash = md5(text);
        String cacheKey = TTS_CACHE_PREFIX + textHash;
        try {
            String cachedAudio = redisTemplate.opsForValue().get(cacheKey);
            if (cachedAudio != null) {
                log.debug("TTS 缓存命中，跳过合成: hash={}", textHash);
                return cachedAudio;
            }
        } catch (Exception e) {
            log.warn("TTS 读缓存失败，降级: {}", e.getMessage());
        }

        try {
            // 截断过长文本（DashScope TTS 限制 20000 字符，截取前 500 字符保障面试实时性）
            String ttsText = text.length() > 500 ? text.substring(0, 500) : text;

            log.info("TTS 开始合成，文本长度: {} 字符", ttsText.length());
            log.debug("TTS 文本预览: \"{}\"", ttsText.substring(0, Math.min(50, ttsText.length())));

            // 使用 CosyVoice v3-flash 模型（最新稳定版）
            SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                    .model("cosyvoice-v3-flash")
                    .voice("longanyang")
                    .apiKey(apiKey)
                    .format(SpeechSynthesisAudioFormat.WAV_24000HZ_MONO_16BIT)
                    .build();

            log.info("TTS 参数: model=cosyvoice-v3-flash, voice=longanyang, format=wav, sampleRate=24000");

            SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
            ByteBuffer audioBuffer = synthesizer.call(ttsText);

            if (audioBuffer == null || !audioBuffer.hasRemaining()) {
                log.warn("TTS 合成返回空音频");
                return null;
            }

            byte[] audioBytes = new byte[audioBuffer.remaining()];
            audioBuffer.get(audioBytes);

            log.info("TTS 合成成功，音频大小: {} 字节 ({} KB)", audioBytes.length, audioBytes.length / 1024.0);

            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

            // ---- 写入 TTS 缓存 ----
            try {
                redisTemplate.opsForValue().set(cacheKey, audioBase64, CACHE_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("TTS 写缓存失败: {}", e.getMessage());
            }

            return audioBase64;
        } catch (Exception e) {
            log.error("TTS 合成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 计算 MD5 哈希
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
