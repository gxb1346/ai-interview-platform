package com.interview.modules.interview.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.*;

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

    @Value("${AI_API_KEY}")
    private String apiKey;

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
     // 独立的 TTS 线程池：最多 2 个并发 TTS 请求，防止阻塞主请求线程
    private static final ExecutorService ttsExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "tts-worker");
        t.setDaemon(true);
        return t;
    });

    // TTS API 超时时间（秒）：超过此时间未返回音频则放弃，保障面试回复实时性
    private static final long TTS_TIMEOUT_SECONDS = 10;

    public String textToSpeechBase64(String text) {
        if (text == null || text.isBlank()) return null;
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("AI_API_KEY 未配置，TTS 功能不可用");
            return null;
        }

        Future<String> future = ttsExecutor.submit(() -> doTtsSynthesis(text));

        try {
            return future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("TTS 合成超时（{}秒），放弃音频合成，文本回复正常返回", TTS_TIMEOUT_SECONDS);
            future.cancel(true);
            return null;
        } catch (Exception e) {
            log.error("TTS 合成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String doTtsSynthesis(String text) throws Exception {
        // 截断过长文本（DashScope TTS 限制 20000 字符，截取前 500 字符保障面试实时性）
        String ttsText = text.length() > 500 ? text.substring(0, 500) : text;

        log.info("TTS 开始合成，文本长度: {} 字符", ttsText.length());
        log.debug("TTS 文本预览: \"{}\"", ttsText.substring(0, Math.min(50, ttsText.length())));

        // 使用 CosyVoice v3-flash 模型（最新稳定版）
        // 参考文档: https://help.aliyun.com/zh/model-studio/non-realtime-cosyvoice-api/
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .model("cosyvoice-v3-flash")          // 最新 CosyVoice 模型
                .voice("longanyang")                   // 系统音色：龙昂扬 - 浑厚男声，适合面试场景
                .apiKey(apiKey)
                .format(SpeechSynthesisAudioFormat.WAV_24000HZ_MONO_16BIT)  // 输出格式：WAV 24kHz 16bit
                .build();

        log.info("TTS 参数: model=cosyvoice-v3-flash, voice=longanyang, format=wav, sampleRate=24000");

        // 非流式调用，每次创建新实例（确保线程安全）
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        ByteBuffer audioBuffer = synthesizer.call(ttsText);

        if (audioBuffer == null || !audioBuffer.hasRemaining()) {
            log.warn("TTS 合成返回空音频");
            return null;
        }

        byte[] audioBytes = new byte[audioBuffer.remaining()];
        audioBuffer.get(audioBytes);

        log.info("TTS 合成成功，音频大小: {} 字节 ({} KB)", audioBytes.length, audioBytes.length / 1024.0);

        // Base64 编码返回，前端用 data:audio/wav;base64,... 播放
        return Base64.getEncoder().encodeToString(audioBytes);
    }
}
