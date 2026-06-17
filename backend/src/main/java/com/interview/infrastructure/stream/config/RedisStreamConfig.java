package com.interview.infrastructure.stream.config;

/**
 * Redis Stream 配置常量
 *
 * Stream Key:      task:stream       — 统一任务流
 * Consumer Group:  task-consumer-group — 所有消费者属于同一组，实现任务分片
 */
public final class RedisStreamConfig {

    private RedisStreamConfig() {
    }

    /** Stream Key */
    public static final String STREAM_KEY = "task:stream";

    /** 消费者组名 */
    public static final String CONSUMER_GROUP = "task-consumer-group";

    /** 消费者批量读取最大条数 */
    public static final int MAX_BATCH_SIZE = 10;

    /** 消费者阻塞读取超时（毫秒） */
    public static final long BLOCK_TIMEOUT_MS = 5000;

    /** 消息处理失败最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 处理失败后延迟重新入队的时间（秒） */
    public static final int RETRY_DELAY_SECONDS = 30;
}
