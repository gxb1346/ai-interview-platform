package com.interview.infrastructure.stream.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.stream.config.RedisStreamConfig;
import com.interview.infrastructure.stream.model.StreamMessage;
import com.interview.infrastructure.stream.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 通用任务生产者
 *
 * 将异步任务以 StreamMessage 格式发送到统一的任务流中。
 * 消费者根据 taskType 分发到不同的处理器。
 */
@Component
public class TaskProducer {

    private static final Logger log = LoggerFactory.getLogger(TaskProducer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TaskProducer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送异步任务到 Redis Stream
     *
     * @param taskType 任务类型
     * @param payload  业务参数对象（会被序列化为 JSON）
     * @return 生成的 taskId
     */
    public String sendTask(TaskType taskType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            StreamMessage message = new StreamMessage(taskType, payloadJson);
            String messageJson = objectMapper.writeValueAsString(message);

            ObjectRecord<String, String> record = StreamRecords
                    .newRecord()
                    .ofObject(messageJson)
                    .withStreamKey(RedisStreamConfig.STREAM_KEY);

            redisTemplate.opsForStream().add(record);

            log.info("[RedisStream] 任务已发送: taskId={}, type={}", message.getTaskId(), taskType);
            return message.getTaskId();
        } catch (JsonProcessingException e) {
            log.error("[RedisStream] 消息序列化失败: type={}", taskType, e);
            throw new BusinessException(ErrorCode.TASK_CREATE_FAILED, "Redis Stream 消息序列化失败: " + e.getMessage());
        }
    }

    /**
     * 发送异步任务（直接传入已序列化的 JSON payload）
     *
     * @param taskType   任务类型
     * @param payloadJson 已序列化的业务参数 JSON
     * @return 生成的 taskId
     */
    public String sendTaskRaw(TaskType taskType, String payloadJson) {
        try {
            StreamMessage message = new StreamMessage(taskType, payloadJson);
            String messageJson = objectMapper.writeValueAsString(message);

            ObjectRecord<String, String> record = StreamRecords
                    .newRecord()
                    .ofObject(messageJson)
                    .withStreamKey(RedisStreamConfig.STREAM_KEY);

            redisTemplate.opsForStream().add(record);

            log.info("[RedisStream] 任务已发送: taskId={}, type={}", message.getTaskId(), taskType);
            return message.getTaskId();
        } catch (JsonProcessingException e) {
            log.error("[RedisStream] 消息序列化失败: type={}", taskType, e);
            throw new BusinessException(ErrorCode.TASK_CREATE_FAILED, "Redis Stream 消息序列化失败: " + e.getMessage());
        }
    }
}