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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TaskConsumerRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumerRunner.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<TaskType, TaskHandler> handlerMap = new EnumMap<>(TaskType.class);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService consumerExecutor;

    private final String consumerName = "consumer-" + System.currentTimeMillis();

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
        consumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        consumerExecutor.submit(this::consumeLoop);
        log.info("[RedisStream] 消费者已启动: consumerName={}, handlers={}",
                consumerName, handlerMap.keySet());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            try {
                consumerExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("[RedisStream] 消费者已停止");
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

    private void consumeLoop() {
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
                        log.warn("[RedisStream] 消息格式异常，跳过: msgId={}", messageId);
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
                            log.debug("[RedisStream] 消息处理成功: taskId={}, msgId={}",
                                    message.getTaskId(), messageId);
                        } else {
                            log.warn("[RedisStream] 消息处理失败（稍后重试）: taskId={}, msgId={}",
                                    message.getTaskId(), messageId);
                        }
                    } catch (Exception e) {
                        log.error("[RedisStream] 消息反序列化或分发失败: msgId={}, error={}",
                                messageId, e.getMessage(), e);
                        redisTemplate.opsForStream().acknowledge(
                                RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, record.getId());
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("[RedisStream] 消费者循环异常: {}", e.getMessage(), e);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
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
}
