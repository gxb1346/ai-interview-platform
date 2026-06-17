package com.interview.infrastructure.stream.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Redis Stream 统一消息体
 *
 * 所有异步任务统一使用此消息结构，消费者根据 taskType 分发到不同的处理器。
 * payload 为 JSON 字符串，包含各任务类型特有的参数。
 *
 * @see TaskType
 */
public class StreamMessage {

    /** 全局唯一任务 ID */
    private String taskId;

    /** 任务类型 */
    private TaskType taskType;

    /** 业务参数 JSON（各任务类型不同） */
    private String payload;

    /** 消息创建时间戳（毫秒） */
    private long timestamp;

    public StreamMessage() {
    }

    public StreamMessage(TaskType taskType, String payload) {
        this.taskId = UUID.randomUUID().toString();
        this.taskType = taskType;
        this.payload = payload;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // ===== Getters / Setters =====

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
