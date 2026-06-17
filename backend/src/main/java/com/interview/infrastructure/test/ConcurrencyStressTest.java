package com.interview.infrastructure.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.infrastructure.stream.config.RedisStreamConfig;
import com.interview.infrastructure.stream.model.StreamMessage;
import com.interview.infrastructure.stream.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高并发压测工具
 *
 * 模拟多个用户同时异步上传简历，测试 Redis Stream 消费者处理能力。
 *
 * 使用方式：
 * 1. 确保后端服务已启动（含 Redis、PostgreSQL、MinIO）
 * 2. 通过测试控制器暴露 HTTP 端点触发
 *
 * 测试指标：
 * - 消息投递耗时（生产者->Stream）
 * - 消息处理耗时（Stream->消费者完成Ack）
 * - 处理成功率
 * - Redis Stream Pending 消息数
 */
@Component
public class ConcurrencyStressTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyStressTest.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConcurrencyStressTest(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行高并发压测
     *
     * @param concurrentCount  并发用户数（如 50）
     * @param tasksPerUser     每个用户发送的任务数（如 2）
     * @return 测试结果摘要
     */
    public StressResult runStressTest(int concurrentCount, int tasksPerUser) throws InterruptedException {
        log.info("===== 并发压测开始 =====");
        log.info("并发用户数: {}, 每人任务数: {}, 总任务数: {}",
                concurrentCount, tasksPerUser, concurrentCount * tasksPerUser);

        // 1. 记录压测前 Stream 状态
        Long beforePending = getPendingCount();

        // 2. 并发发送 N 个简历分析任务
        int totalTasks = concurrentCount * tasksPerUser;
        CountDownLatch latch = new CountDownLatch(totalTasks);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        var executor = Executors.newVirtualThreadPerTaskExecutor();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTasks; i++) {
            final int taskIndex = i;
            executor.submit(() -> {
                try {
                    long sendStart = System.currentTimeMillis();
                    sendFakeResumeTask("并发测试简历_" + taskIndex);
                    long latency = System.currentTimeMillis() - sendStart;
                    synchronized (latencies) {
                        latencies.add(latency);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("任务 {} 发送失败: {}", taskIndex, e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();

        // 3. 等待所有消息投递完成
        boolean allSent = latch.await(60, TimeUnit.SECONDS);
        long sendDuration = System.currentTimeMillis() - startTime;
        log.info("消息投递完成: 成功={}, 失败={}, 耗时={}ms",
                successCount.get(), failCount.get(), sendDuration);

        // 4. 等待消费者处理（AI分析较慢，等待合理时间）
        log.info("等待消费者处理...（预计 30-120 秒，取决于 AI 响应速度）");
        TimeUnit.SECONDS.sleep(30);

        // 5. 统计处理结果
        Long afterPending = getPendingCount();
        long consumedCount = beforePending - afterPending > 0
                ? totalTasks - (afterPending - Math.max(0, beforePending))
                : totalTasks - afterPending;

        // 计算 P50/P95/P99 投递延迟
        latencies.sort(Long::compareTo);
        long p50 = latencies.get((int) (latencies.size() * 0.5));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        StressResult result = new StressResult();
        result.concurrentCount = concurrentCount;
        result.totalTasks = totalTasks;
        result.successSent = successCount.get();
        result.failSent = failCount.get();
        result.sendDurationMs = sendDuration;
        result.sendP50Ms = p50;
        result.sendP95Ms = p95;
        result.sendP99Ms = p99;
        result.pendingBefore = beforePending;
        result.pendingAfter = afterPending;
        result.estimatedConsumed = consumedCount;

        log.info("===== 压测结果 =====");
        log.info("投递 P50={}ms, P95={}ms, P99={}ms", p50, p95, p99);
        log.info("压测前 Pending: {}, 压测后 Pending: {}, 推测已消费: {}",
                beforePending, afterPending, consumedCount);
        log.info("===== 压测结束 =====");

        return result;
    }

    /**
     * 发送一个假的简历分析任务（不包含真实文件，仅用于测试吞吐量）
     */
    private void sendFakeResumeTask(String resumeText) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawText", resumeText);
        payload.put("targetJob", "Java开发工程师");
        payload.put("contentHash", "test_hash_" + resumeText.hashCode());
        payload.put("fileName", "stress_test_resume.txt");
        payload.put("fileType", "txt");
        payload.put("fileSize", 1024);
        payload.put("fileBytes", ""); // 空文件，消费者S3上传会跳过

        StreamMessage message = new StreamMessage(TaskType.RESUME_ANALYSIS,
                objectMapper.writeValueAsString(payload));
        String messageJson = objectMapper.writeValueAsString(message);

        ObjectRecord<String, String> record = StreamRecords
                .newRecord()
                .ofObject(messageJson)
                .withStreamKey(RedisStreamConfig.STREAM_KEY);

        redisTemplate.opsForStream().add(record);
    }

    /**
     * 获取 Redis Stream 中 Pending 消息数
     */
    private Long getPendingCount() {
        try {
            var info = redisTemplate.opsForStream().info(RedisStreamConfig.STREAM_KEY);
            if (info != null) {
                var groups = redisTemplate.opsForStream().groups(RedisStreamConfig.STREAM_KEY);
                if (groups != null && !groups.isEmpty()) {
                    return groups.get(0).pendingCount();
                }
            }
        } catch (Exception e) {
            log.warn("获取 Stream Pending 数失败: {}", e.getMessage());
        }
        return -1L;
    }

    /**
     * 压测结果
     */
    public static class StressResult {
        public int concurrentCount;
        public int totalTasks;
        public int successSent;
        public int failSent;
        public long sendDurationMs;
        public long sendP50Ms;
        public long sendP95Ms;
        public long sendP99Ms;
        public long pendingBefore;
        public long pendingAfter;
        public long estimatedConsumed;

        public double getSentTps() {
            return sendDurationMs > 0 ? (double) totalTasks / sendDurationMs * 1000 : 0;
        }

        public double getConsumeRatio() {
            return totalTasks > 0 ? (double) estimatedConsumed / totalTasks * 100 : 0;
        }
    }
}
