package com.interview.infrastructure.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM API 调用令牌桶限流器
 *
 * 防止突发高并发导致 DashScope API 被限流(429)或 Token 费用激增。
 * 默认配置：每分钟最多 60 次调用（可根据模型调整）
 *
 * 使用方式：在调用 LLM API 之前调用 LlmRateLimiter.tryAcquire()，
 * 如果返回 false 则阻塞等待或降级。
 */
@Component
public class LlmRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LlmRateLimiter.class);

    /** 默认每分钟最大请求数 */
    private static final int DEFAULT_MAX_RPM = 60;

    /** 刷新窗口长度（毫秒） */
    private static final long WINDOW_MS = 60_000L;

    private final int maxRequestsPerWindow;
    private final AtomicInteger counter;
    private final AtomicLong windowStart;

    public LlmRateLimiter() {
        this(DEFAULT_MAX_RPM);
    }

    public LlmRateLimiter(int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.counter = new AtomicInteger(0);
        this.windowStart = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * 尝试获取一个调用许可
     *
     * @return true 允许调用，false 超过限流阈值
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试获取多个调用许可
     *
     * @param permits 需要的许可数量
     * @return true 允许调用，false 超过限流阈值
     */
    public boolean tryAcquire(int permits) {
        long now = System.currentTimeMillis();
        long window = windowStart.get();

        // 如果当前时间已超出窗口，重置计数器
        if (now - window > WINDOW_MS) {
            // 原子更新窗口起始时间（只允许一个线程重置）
            if (windowStart.compareAndSet(window, now)) {
                counter.set(0);
            }
        }

        int currentCount;
        do {
            currentCount = counter.get();
            if (currentCount + permits > maxRequestsPerWindow) {
                log.warn("[限流] LLM 调用限流触发: 当前窗口已用 {}/{}, 请求许可={}",
                        currentCount, maxRequestsPerWindow, permits);
                return false;
            }
        } while (!counter.compareAndSet(currentCount, currentCount + permits));

        if (currentCount == 0 || currentCount % 10 == 0) {
            log.info("[限流] LLM 调用量: {}/{}/分钟", currentCount + permits, maxRequestsPerWindow);
        }

        return true;
    }

    /**
     * 获取当前窗口已使用的请求数
     */
    public int getCurrentUsage() {
        return counter.get();
    }

    /**
     * 获取最大窗口请求数
     */
    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    /**
     * 获取当前窗口剩余许可数
     */
    public int getRemainingPermits() {
        return Math.max(0, maxRequestsPerWindow - counter.get());
    }
}
