package com.interview.common.async;

import com.interview.common.constant.AsyncTaskStreamConstants;
import com.interview.infrastructure.redis.RedisService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractStreamConsumer<T> implements InitializingBean {

    private final RedisService redisService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String consumerName;
    private Thread consumerThread;

    protected AbstractStreamConsumer(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void afterPropertiesSet() {
        if (running.get()) {
            log.info("{} consumer already running, skipping duplicate initialization", taskDisplayName());
            return;
        }
        log.info("=== {} InitializingBean.afterPropertiesSet() called ===", taskDisplayName());
        this.consumerName = consumerPrefix() + UUID.randomUUID().toString().substring(0, 8);

        running.set(true);

        this.consumerThread = new Thread(() -> {
            log.info("{} consumer thread starting, thread={}", taskDisplayName(), Thread.currentThread().getName());
            try {
                startConsumer();
            } catch (Exception e) {
                log.error("{} consumer thread fatal error", taskDisplayName(), e);
            }
        }, threadName());
        consumerThread.setDaemon(true);
        consumerThread.start();

        log.info("{} consumer started: consumerName={}", taskDisplayName(), consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("{} consumer stopped: consumerName={}", taskDisplayName(), consumerName);
    }

    private void startConsumer() {
        try {
            log.info("{} startConsumer: creating stream group, streamKey={}, groupName={}",
                taskDisplayName(), streamKey(), groupName());
            redisService.createStreamGroup(streamKey(), groupName());
            log.info("Redis Stream group is ready: {}", groupName());
        } catch (Exception e) {
            log.warn("Failed to prepare Redis Stream group: groupName={}, error={}", groupName(), e.getMessage());
        }

        log.info("{} entering consumeLoop", taskDisplayName());
        consumeLoop();
    }

    private void consumeLoop() {
        log.info("{} consumer loop started, thread={}", taskDisplayName(), Thread.currentThread().getName());
        while (running.get()) {
            try {
                boolean consumed = redisService.streamConsumeMessages(
                    streamKey(),
                    groupName(),
                    consumerName,
                    AsyncTaskStreamConstants.BATCH_SIZE,
                    AsyncTaskStreamConstants.POLL_INTERVAL_MS,
                    this::processMessage
                );
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Consumer thread interrupted");
                    break;
                }
                log.error("{} consumer loop error: {}", taskDisplayName(), e.getMessage(), e);
            }
        }
        log.info("{} consumer loop exited", taskDisplayName());
    }

    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        T payload = parsePayload(messageId, data);
        if (payload == null) {
            ackMessage(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        log.info("Processing {} task: payload={}, messageId={}, retryCount={}",
            taskDisplayName(), payloadIdentifier(payload), messageId, retryCount);

        try {
            markProcessing(payload);
            processBusiness(payload);
            markCompleted(payload);
            ackMessage(messageId);
            log.info("{} task completed: {}", taskDisplayName(), payloadIdentifier(payload));
        } catch (Exception e) {
            log.error("{} task failed: {}", taskDisplayName(), payloadIdentifier(payload), e);
            // 先 ACK 当前消息，再重试，防止同一消息被重复投递
            ackMessage(messageId);
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                retryMessage(payload, retryCount + 1);
            } else {
                markFailed(payload, truncateError(
                    taskDisplayName() + " failed after retry " + retryCount + ": " + e.getMessage()
                ));
            }
        }
    }

    protected int parseRetryCount(Map<String, String> data) {
        try {
            return Integer.parseInt(data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(streamKey(), groupName(), messageId);
        } catch (Exception e) {
            log.error("Failed to ack stream message: messageId={}", messageId, e);
        }
    }

    protected boolean isRunning() {
        return running.get();
    }

    protected RedisService redisService() {
        return redisService;
    }

    protected abstract String taskDisplayName();

    protected abstract String streamKey();

    protected abstract String groupName();

    protected abstract String consumerPrefix();

    protected abstract String threadName();

    protected abstract T parsePayload(StreamMessageId messageId, Map<String, String> data);

    protected abstract String payloadIdentifier(T payload);

    protected abstract void markProcessing(T payload);

    protected abstract void processBusiness(T payload);

    protected abstract void markCompleted(T payload);

    protected abstract void markFailed(T payload, String error);

    protected abstract void retryMessage(T payload, int retryCount);
}