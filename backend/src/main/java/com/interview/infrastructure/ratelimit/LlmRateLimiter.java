package com.interview.infrastructure.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 调用限流器（令牌桶算法）
 * 限制每分钟内 LLM API 调用次数，防止突发流量耗尽 Token 配额
 */
@Component
public class LlmRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LlmRateLimiter.class);

    /** 每分钟最大调用次数 */
    private final int maxRequestsPerMinute;

    /** 当前时间窗口内的调用计数 */
    private final AtomicInteger requestCount = new AtomicInteger(0);

    /** 当前窗口开始时间戳（毫秒） */
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

    /** 限流触发次数统计 */
    private final AtomicInteger rateLimitTriggered = new AtomicInteger(0);

    public LlmRateLimiter(@Value("${llm.rate-limit.max-per-minute:60}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        log.info("[LlmRateLimiter] 初始化: maxRequestsPerMinute={}", maxRequestsPerMinute);
    }

    /**
     * 尝试获取调用许可
     *
     * @return true 允许调用，false 触发限流
     */
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long window = windowStart.get();

        // 如果已过窗口期（超过1分钟），重置窗口
        if (now - window > 60_000) {
            if (windowStart.compareAndSet(window, now)) {
                requestCount.set(0);
            }
        }

        int count = requestCount.incrementAndGet();
        if (count > maxRequestsPerMinute) {
            rateLimitTriggered.incrementAndGet();
            return false;
        }
        return true;
    }

    /** 获取当前窗口已用次数 */
    public int getCurrentCount() {
        return requestCount.get();
    }

    /** 获取限流触发总次数 */
    public int getRateLimitTriggeredCount() {
        return rateLimitTriggered.get();
    }

    /** 获取每分钟最大调用次数 */
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    /** 重置限流器状态 */
    public void reset() {
        windowStart.set(System.currentTimeMillis());
        requestCount.set(0);
        rateLimitTriggered.set(0);
    }
}
