package com.interview.infrastructure.monitor;

import com.interview.infrastructure.ratelimit.LlmRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 通用 ChatClient 调用助手
 * 统一封装：限流检查、Token 计数、并发跟踪
 */
@Component
public class ChatResponseHelper {

    private static final Logger log = LoggerFactory.getLogger(ChatResponseHelper.class);

    private final LlmRateLimiter rateLimiter;
    private final LlmCallMonitor monitor;

    public ChatResponseHelper(LlmRateLimiter rateLimiter, LlmCallMonitor monitor) {
        this.rateLimiter = rateLimiter;
        this.monitor = monitor;
    }

    /**
     * 调用 LLM 并自动记录 Token 消耗、耗时
     *
     * @param callPoint 调用点名称（如 LlmCallMonitor.RESUME_ANALYSIS）
     * @param client    ChatClient 实例
     * @param prompt    用户提示词
     * @return LLM 返回文本
     */
    public String call(String callPoint, ChatClient client, String prompt) {
        if (!rateLimiter.tryAcquire()) {
            monitor.recordRateLimit(callPoint);
            throw new RuntimeException("LLM 调用限流，请稍后重试");
        }

        long startNanos = System.nanoTime();
        monitor.incrementConcurrent();
        try {
            return callWithTokenTracking(callPoint, client, prompt, startNanos);
        } finally {
            monitor.decrementConcurrent();
        }
    }

    private String callWithTokenTracking(String callPoint, ChatClient client, String prompt, long startNanos) {
        long startMs = System.currentTimeMillis();

        try {
            // 尝试带 Token 统计的 chatResponse API
            var chatResponse = client.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String content = null;
            int promptTokens = 0;
            int completionTokens = 0;

            if (chatResponse != null) {
                // 通过反射提取 content
                try {
                    Object result = chatResponse.getResult();
                    if (result != null) {
                        Object output = result.getClass().getMethod("getOutput").invoke(result);
                        if (output != null) {
                            Object text = output.getClass().getMethod("getContent").invoke(output);
                            if (text instanceof String) {
                                content = (String) text;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("反射提取 content 失败: {}", e.getMessage());
                }

                // 提取 Token 使用量
                try {
                    Object metadata = chatResponse.getMetadata();
                    if (metadata != null) {
                        Object usage = metadata.getClass().getMethod("getUsage").invoke(metadata);
                        if (usage != null) {
                            Object pt = usage.getClass().getMethod("getPromptTokens").invoke(usage);
                            Object ct = usage.getClass().getMethod("getCompletionTokens").invoke(usage);
                            if (pt instanceof Integer) promptTokens = (Integer) pt;
                            if (ct instanceof Integer) completionTokens = (Integer) ct;
                        }
                    }
                } catch (Exception e) {
                    log.debug("反射提取 Token 计数失败: {}", e.getMessage());
                }
            }

            if (content == null) {
                content = client.prompt().user(prompt).call().content();
            }

            long durationMs = System.currentTimeMillis() - startMs;
            monitor.recordCall(callPoint, promptTokens, completionTokens, durationMs);

            log.debug("[ChatResponseHelper] {} 耗时={}ms, promptTokens={}, completionTokens={}",
                    callPoint, durationMs, promptTokens, completionTokens);

            return content;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            monitor.recordCall(callPoint);
            log.error("[ChatResponseHelper] {} 调用失败: {}, 耗时={}ms",
                    callPoint, e.getMessage(), durationMs);
            throw e;
        }
    }
}
