package com.interview.infrastructure.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 调用监控器
 *
 * 跟踪每次 LLM API 调用，帮助评估 token 消耗情况。
 * 通过查看日志可以清楚区分：哪些请求走了缓存/去重，哪些真正调用了 LLM。
 *
 * 监控维度：
 * - 总调用次数
 * - 各调用点分布（简历分析、评估、TTS、方向推荐等）
 * - 缓存命中次数
 * - 去重拦截次数
 * - 限流触发次数
 */
@Component
public class LlmCallMonitor {

    private static final Logger log = LoggerFactory.getLogger(LlmCallMonitor.class);

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong dedupSkips = new AtomicLong(0);
    private final AtomicLong rateLimitBlocks = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);

    private final ConcurrentHashMap<String, AtomicInteger> callPoints = new ConcurrentHashMap<>();

    private final LocalDateTime startTime = LocalDateTime.now();

    // ======= 调用点枚举 =======

    public static final String RESUME_ANALYSIS = "简历分析";
    public static final String BATCH_EVALUATION = "分批评估";
    public static final String AGGREGATE_EVALUATION = "汇总评估";
    public static final String TTS_SYNTHESIS = "TTS语音合成";
    public static final String DIRECTION_RECOMMEND = "方向推荐";
    public static final String JD_PARSE = "岗位描述解析";
    public static final String FOLLOW_UP = "追问生成";
    public static final String QUESTION_GENERATE = "题目生成";

    /**
     * 记录一次真实的 LLM API 调用
     */
    public void recordCall(String callPoint) {
        totalCalls.incrementAndGet();
        callPoints.computeIfAbsent(callPoint, k -> new AtomicInteger(0)).incrementAndGet();
        log.info("[LLM监控] ✅ 真实调用: {} | 累计: {} 次", callPoint, totalCalls.get());
    }

    /**
     * 记录一次缓存命中（节省了 LLM 调用）
     */
    public void recordCacheHit(String cacheType) {
        cacheHits.incrementAndGet();
        log.info("[LLM监控] 🟢 缓存命中: {} | 累计节省: {} 次", cacheType, cacheHits.get());
    }

    /**
     * 记录一次去重拦截（节省了 LLM 调用）
     */
    public void recordDedupSkip(String dedupType) {
        dedupSkips.incrementAndGet();
        log.info("[LLM监控] 🔵 去重拦截: {} | 累计节省: {} 次", dedupType, dedupSkips.get());
    }

    /**
     * 记录一次限流触发（保护了 API）
     */
    public void recordRateLimit(String callPoint) {
        rateLimitBlocks.incrementAndGet();
        log.warn("[LLM监控] 🔴 限流触发: {} | 累计限流: {} 次", callPoint, rateLimitBlocks.get());
    }

    /**
     * 记录一次 LLM 调用失败
     */
    public void recordError(String callPoint, String error) {
        errors.incrementAndGet();
        log.error("[LLM监控] ❌ 调用失败: {} | 原因: {} | 累计失败: {} 次", callPoint, error, errors.get());
    }

    /**
     * 打印当前监控快照
     */
    public String getSnapshot() {
        StringBuilder sb = new StringBuilder();
        String separator = "=" .repeat(50);
        sb.append('\n').append(separator).append('\n');
        sb.append("  LLM 调用监控报告\n");
        sb.append("  启动时间: ").append(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        sb.append("  当前时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        sb.append(separator).append('\n');
        sb.append(String.format("  ✅ 真实 LLM 调用:  %d 次 (消费 Token)\n", totalCalls.get()));
        sb.append(String.format("  🟢 缓存命中:       %d 次 (节省 Token)\n", cacheHits.get()));
        sb.append(String.format("  🔵 去重拦截:       %d 次 (节省 Token)\n", dedupSkips.get()));
        sb.append(String.format("  🔴 限流触发:       %d 次 (保护 API)\n", rateLimitBlocks.get()));
        sb.append(String.format("  ❌ 调用失败:       %d 次\n", errors.get()));
        sb.append(separator).append('\n');
        sb.append("  各调用点分布:\n");
        callPoints.forEach((point, count) ->
                sb.append(String.format("    %-12s: %d 次\n", point, count.get()))
        );
        sb.append(separator).append('\n');

        long saved = cacheHits.get() + dedupSkips.get();
        long actual = totalCalls.get();

        if (totalCalls.get() > 0 || saved > 0) {
            double saveRate = (double) saved / (saved + actual) * 100;
            sb.append(String.format("  Token 节省率: %.1f%% (节省 %d 次 / 总共避免 %d 次调用)\n",
                    saveRate, saved, saved + actual));
        }

        if (rateLimitBlocks.get() > 0) {
            sb.append(String.format("  ⚠️  限流率: %.1f%%\n",
                    (double) rateLimitBlocks.get() / (totalCalls.get() + rateLimitBlocks.get()) * 100));
        }

        sb.append(separator).append('\n');
        return sb.toString();
    }

    public long getTotalCalls() { return totalCalls.get(); }
    public long getCacheHits() { return cacheHits.get(); }
    public long getDedupSkips() { return dedupSkips.get(); }
    public long getRateLimitBlocks() { return rateLimitBlocks.get(); }
    public long getSavedCalls() { return cacheHits.get() + dedupSkips.get(); }
}
