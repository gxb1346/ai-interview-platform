package com.interview.infrastructure.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * LLM 调用监控器
 * 统计各调用点的调用次数、Token 消耗、耗时等指标
 */
@Component
public class LlmCallMonitor {

    private static final Logger log = LoggerFactory.getLogger(LlmCallMonitor.class);

    // ========== 调用点名称常量 ==========
    public static final String RESUME_ANALYSIS = "resume_analysis";
    public static final String FOLLOW_UP = "follow_up";
    public static final String QUESTION_GENERATE = "question_generate";
    public static final String DIRECTION_RECOMMEND = "direction_recommend";
    public static final String JD_PARSE = "jd_parse";
    public static final String BATCH_EVALUATION = "batch_evaluation";
    public static final String AGGREGATE_EVALUATION = "aggregate_evaluation";

    // ========== 全局统计 ==========
    /** 真实 LLM 调用次数 */
    private final AtomicLong totalCalls = new AtomicLong(0);
    /** 缓存命中次数 */
    private final AtomicLong cacheHits = new AtomicLong(0);
    /** 限流拒绝次数 */
    private final AtomicLong rateLimitBlocks = new AtomicLong(0);

    // ========== 并发跟踪 ==========
    private final AtomicInteger currentConcurrent = new AtomicInteger(0);
    private final AtomicInteger peakConcurrent = new AtomicInteger(0);

    // ========== Token 统计 ==========
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    // ========== 按调用点统计 ==========
    private final Map<String, CallPointStats> callPointStats = new ConcurrentHashMap<>();

    // ========== 对外接口 ==========

    /** 记录一次真实 LLM 调用（含 Token 信息） */
    public void recordCall(String callPoint, int promptTokens, int completionTokens, long durationMs) {
        totalCalls.incrementAndGet();
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalDurationMs.addAndGet(durationMs);

        CallPointStats stats = callPointStats.computeIfAbsent(callPoint, k -> new CallPointStats());
        stats.record(promptTokens, completionTokens, durationMs);
    }

    /** 记录一次真实 LLM 调用（不含 Token 信息，做估算用） */
    public void recordCall(String callPoint) {
        totalCalls.incrementAndGet();
        CallPointStats stats = callPointStats.computeIfAbsent(callPoint, k -> new CallPointStats());
        stats.record(0, 0, 0);
    }

    /** 记录一次缓存命中 */
    public void recordCacheHit(String callPoint) {
        cacheHits.incrementAndGet();
    }

    /** 记录一次限流触发 */
    public void recordRateLimit(String callPoint) {
        rateLimitBlocks.incrementAndGet();
    }

    /** 增加并发计数 */
    public void incrementConcurrent() {
        int cur = currentConcurrent.incrementAndGet();
        int peak;
        while (cur > (peak = peakConcurrent.get())) {
            peakConcurrent.compareAndSet(peak, cur);
        }
    }

    /** 减少并发计数 */
    public void decrementConcurrent() {
        currentConcurrent.decrementAndGet();
    }

    // ========== 统计查询 ==========

    /** 获取快照统计（Map 格式，适合 JSON 返回） */
    public Map<String, Object> getStatsAsMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalCalls", totalCalls.get());
        result.put("cacheHits", cacheHits.get());
        result.put("rateLimitBlocks", rateLimitBlocks.get());
        result.put("peakConcurrent", peakConcurrent.get());
        result.put("totalPromptTokens", totalPromptTokens.get());
        result.put("totalCompletionTokens", totalCompletionTokens.get());
        result.put("totalTokens", totalPromptTokens.get() + totalCompletionTokens.get());
        result.put("totalDurationMs", totalDurationMs.get());

        double cacheRate = totalCalls.get() + cacheHits.get() > 0
                ? (double) cacheHits.get() / (totalCalls.get() + cacheHits.get()) * 100 : 0;
        result.put("cacheRate", String.format("%.1f%%", cacheRate));

        double avgDuration = totalCalls.get() > 0 ? (double) totalDurationMs.get() / totalCalls.get() : 0;
        result.put("avgDurationMs", String.format("%.1f", avgDuration));

        // 各调用点详情
        Map<String, Object> pointDetails = new HashMap<>();
        for (Map.Entry<String, CallPointStats> entry : callPointStats.entrySet()) {
            Map<String, Object> detail = new HashMap<>();
            CallPointStats s = entry.getValue();
            detail.put("count", s.count.get());
            detail.put("promptTokens", s.promptTokens.get());
            detail.put("completionTokens", s.completionTokens.get());
            detail.put("totalTokens", s.promptTokens.get() + s.completionTokens.get());
            detail.put("totalDurationMs", s.totalDurationMs.get());
            detail.put("avgDurationMs", s.count.get() > 0 ?
                    String.format("%.1f", (double) s.totalDurationMs.get() / s.count.get()) : "0");
            pointDetails.put(entry.getKey(), detail);
        }
        result.put("callPointDetails", pointDetails);

        return result;
    }

    /** 获取格式化的文本快照报告 */
    public String getSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================\n");
        sb.append("  LLM 调用监控报告\n");
        sb.append("========================================================\n\n");

        sb.append("  真实调用:     ").append(totalCalls.get()).append(" 次\n");
        sb.append("  缓存命中:     ").append(cacheHits.get()).append(" 次\n");
        sb.append("  限流拒绝:     ").append(rateLimitBlocks.get()).append(" 次\n");
        sb.append("  并行峰值:     ").append(peakConcurrent.get()).append(" 线程\n\n");

        sb.append("  --- Token 消耗 ---\n");
        sb.append("  Prompt Token:     ").append(totalPromptTokens.get()).append("\n");
        sb.append("  Completion Token: ").append(totalCompletionTokens.get()).append("\n");
        sb.append("  合计:             ").append(totalPromptTokens.get() + totalCompletionTokens.get()).append("\n\n");

        sb.append("  --- 性能指标 ---\n");
        long totalTime = totalDurationMs.get();
        sb.append("  总耗时:   ").append(totalTime).append(" ms")
                .append(" (").append(String.format("%.1f", totalTime / 1000.0)).append(" s)\n");
        double avgDuration = totalCalls.get() > 0 ? (double) totalDurationMs.get() / totalCalls.get() : 0;
        sb.append("  平均耗时: ").append(String.format("%.1f", avgDuration)).append(" ms/次\n");
        double tps = totalTime > 0 ? (double) totalCalls.get() / totalTime * 1000 : 0;
        sb.append("  TPS:      ").append(String.format("%.2f", tps)).append("\n\n");

        sb.append("  --- 各调用点详情 ---\n");
        if (callPointStats.isEmpty()) {
            sb.append("  (暂无数据)\n");
        } else {
            sb.append(String.format("  %-25s %6s %8s %8s %10s %10s\n",
                    "名称", "次数", "Prompt", "Completion", "合计Token", "平均耗时"));
            sb.append("  ").append("-".repeat(75)).append("\n");
            for (Map.Entry<String, CallPointStats> entry : callPointStats.entrySet()) {
                CallPointStats s = entry.getValue();
                long tok = s.promptTokens.get() + s.completionTokens.get();
                double avg = s.count.get() > 0 ? (double) s.totalDurationMs.get() / s.count.get() : 0;
                sb.append(String.format("  %-25s %6d %8d %8d %10d %10.1fms\n",
                        entry.getKey(), s.count.get(), s.promptTokens.get(),
                        s.completionTokens.get(), tok, avg));
            }
        }

        sb.append("\n========================================================");
        return sb.toString();
    }

    // ========== 内部统计类 ==========

    static class CallPointStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong promptTokens = new AtomicLong(0);
        private final AtomicLong completionTokens = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);

        void record(int promptTks, int completionTks, long durationMs) {
            count.incrementAndGet();
            promptTokens.addAndGet(promptTks);
            completionTokens.addAndGet(completionTks);
            totalDurationMs.addAndGet(durationMs);
        }
    }
}
