package com.interview.infrastructure.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.infrastructure.stream.config.RedisStreamConfig;
import com.interview.infrastructure.stream.model.StreamMessage;
import com.interview.infrastructure.stream.model.TaskType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis Stream 多线程消费者
 * 支持 N 个并行消费者（同一消费者组内），实现任务并行处理
 */
@Component
/**
 * @deprecated 已被 {@link com.interview.common.async.AbstractStreamConsumer} 替代，
 *             新版基于 Redisson Stream API，功能更完善。
 *             请逐步迁移到新版。
 */
@Deprecated
public class TaskConsumerRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumerRunner.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<TaskType, TaskHandler> handlerMap = new EnumMap<>(TaskType.class);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService consumerExecutor;

    /** 活跃消费者计数 */
    private final AtomicInteger activeConsumerCount = new AtomicInteger(0);

    private final long startupTimestamp = System.currentTimeMillis();

    public TaskConsumerRunner(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              List<TaskHandler> handlers) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        for (TaskHandler handler : handlers) {
            TaskType type = resolveTaskType(handler);
            if (type != null) {
                handlerMap.put(type, handler);
                log.info("[RedisStream] 注册处理器: {} -> {}", type, handler.getClass().getSimpleName());
            }
        }
    }

    @PostConstruct
    public void start() {
        createConsumerGroup();
        int consumerCount = RedisStreamConfig.CONSUMER_COUNT;
        consumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < consumerCount; i++) {
            String consumerName = "consumer-" + i + "-" + startupTimestamp + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            final String name = consumerName;
            consumerExecutor.submit(() -> consumeLoop(name));
            log.info("[RedisStream] 消费者已启动: consumerName={}, handlers={}",
                    name, handlerMap.keySet());
        }
        log.info("[RedisStream] 全部 {} 个消费者已启动", consumerCount);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            try {
                if (!consumerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    consumerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                consumerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("[RedisStream] 所有消费者已停止");
    }

    private void createConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                    RedisStreamConfig.STREAM_KEY,
                    RedisStreamConfig.CONSUMER_GROUP
            );
            log.info("[RedisStream] 消费者组创建成功: group={}", RedisStreamConfig.CONSUMER_GROUP);
        } catch (Exception e) {
            log.info("[RedisStream] 消费者组已存在: group={} ({}: {})",
                    RedisStreamConfig.CONSUMER_GROUP, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void consumeLoop(String consumerName) {
        activeConsumerCount.incrementAndGet();
        log.info("[RedisStream] 消费者开始运行: consumerName={}", consumerName);
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                        .read(
                                Consumer.from(RedisStreamConfig.CONSUMER_GROUP, consumerName),
                                StreamReadOptions.empty()
                                        .count(RedisStreamConfig.MAX_BATCH_SIZE)
                                        .block(Duration.ofMillis(RedisStreamConfig.BLOCK_TIMEOUT_MS)),
                                StreamOffset.create(RedisStreamConfig.STREAM_KEY, ReadOffset.lastConsumed())
                        );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : messages) {
                    String messageId = record.getId().getValue();
                    Map<Object, Object> valueMap = record.getValue();
                    Object payloadObj = valueMap != null ? valueMap.get("payload") : null;
                    String messageJson = payloadObj instanceof String ? (String) payloadObj : null;
                    if (messageJson == null) {
                        log.warn("[RedisStream] 消息格式异常，跳过: msgId={}, consumer={}", messageId, consumerName);
                        redisTemplate.opsForStream().acknowledge(
                                RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, record.getId());
                        continue;
                    }

                    try {
                        StreamMessage message = objectMapper.readValue(messageJson, StreamMessage.class);
                        boolean success = dispatch(message);

                        if (success) {
                            redisTemplate.opsForStream().acknowledge(
                                    RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, record.getId());
                            log.debug("[RedisStream] 消息处理成功: taskId={}, msgId={}, consumer={}",
                                    message.getTaskId(), messageId, consumerName);
                        } else {
                            log.warn("[RedisStream] 消息处理失败（稍后重试）: taskId={}, msgId={}, consumer={}",
                                    message.getTaskId(), messageId, consumerName);
                        }
                    } catch (Exception e) {
                        log.error("[RedisStream] 消息反序列化或分发失败: msgId={}, consumer={}, error={}",
                                messageId, consumerName, e.getMessage(), e);
                        redisTemplate.opsForStream().acknowledge(
                                RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, record.getId());
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("[RedisStream] 消费者循环异常: consumer={}, error={}", consumerName, e.getMessage(), e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        activeConsumerCount.decrementAndGet();
        log.info("[RedisStream] 消费者已退出: consumerName={}", consumerName);
    }

    private boolean dispatch(StreamMessage message) {
        TaskType taskType = message.getTaskType();
        TaskHandler handler = handlerMap.get(taskType);
        if (handler == null) {
            log.warn("[RedisStream] 未找到处理器: taskType={}, 可用处理器: {}", taskType, handlerMap.keySet());
            return true;
        }
        log.info("[RedisStream] 开始处理任务: taskId={}, type={}, handler={}",
                message.getTaskId(), taskType, handler.getClass().getSimpleName());
        long start = System.currentTimeMillis();
        boolean result = handler.handle(message);
        log.info("[RedisStream] 任务处理完成: taskId={}, type={}, result={}, 耗时={}ms",
                message.getTaskId(), taskType, result, System.currentTimeMillis() - start);
        return result;
    }

    private static TaskType resolveTaskType(TaskHandler handler) {
        String simpleName = handler.getClass().getSimpleName();
        for (TaskType type : TaskType.values()) {
            String normalized = type.name().replace("_", "");
            if (simpleName.toUpperCase().contains(normalized)) {
                return type;
            }
        }
        return null;
    }

    /** 获取当前活跃消费者数 */
    public int getActiveConsumerCount() {
        return activeConsumerCount.get();
    }
}
