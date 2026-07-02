package com.interview.modules.voiceinterview.listener;

import com.interview.common.async.AbstractStreamConsumer;
import com.interview.common.constant.AsyncTaskStreamConstants;
import com.interview.common.model.AsyncTaskStatus;
import com.interview.infrastructure.redis.RedisService;
import com.interview.modules.voiceinterview.service.VoiceInterviewEvaluationService;
import com.interview.modules.voiceinterview.service.VoiceInterviewService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 语音面试评估 Stream 消费者
 */
@Slf4j
@Component
public class VoiceEvaluateStreamConsumer extends AbstractStreamConsumer<VoiceEvaluateStreamConsumer.VoiceEvaluatePayload> {

    private final VoiceInterviewService voiceInterviewService;
    private final VoiceInterviewEvaluationService evaluationService;

    public VoiceEvaluateStreamConsumer(RedisService redisService,
                                   VoiceInterviewService voiceInterviewService,
                                   VoiceInterviewEvaluationService evaluationService) {
        super(redisService);
        this.voiceInterviewService = voiceInterviewService;
        this.evaluationService = evaluationService;
        log.info("VoiceEvaluateStreamConsumer constructed");
    }

    /**
     * 兜底启动：当 IDE 的 Spring 容器未调用父类 InitializingBean.afterPropertiesSet() 时，
     * 通过具体子类的 @PostConstruct 确保消费者线程被启动。
     * afterPropertiesSet() 内部已做幂等检查，不会重复启动。
     */
    @PostConstruct
    public void ensureConsumerStarted() {
        log.info("=== VoiceEvaluateStreamConsumer @PostConstruct fallback: isRunning={} ===", isRunning());
        afterPropertiesSet();
    }

    record VoiceEvaluatePayload(String sessionId) {}

    @Override
    protected String taskDisplayName() {
        return "语音面试评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "voice-evaluate-consumer";
    }

    @Override
    protected VoiceEvaluatePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(AsyncTaskStreamConstants.FIELD_VOICE_SESSION_ID);
        if (sessionId == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        return new VoiceEvaluatePayload(sessionId);
    }

    @Override
    protected String payloadIdentifier(VoiceEvaluatePayload payload) {
        return "voiceSessionId=" + payload.sessionId();
    }

    @Override
    protected void markProcessing(VoiceEvaluatePayload payload) {
        voiceInterviewService.updateEvaluateStatus(
                Long.parseLong(payload.sessionId()), AsyncTaskStatus.PROCESSING, null);
    }

    @Override
    protected void processBusiness(VoiceEvaluatePayload payload) {
        evaluationService.generateEvaluation(Long.parseLong(payload.sessionId()));
        log.info("语音面试评估完成: sessionId={}", payload.sessionId());
    }

    @Override
    protected void markCompleted(VoiceEvaluatePayload payload) {
        voiceInterviewService.updateEvaluateStatus(
                Long.parseLong(payload.sessionId()), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(VoiceEvaluatePayload payload, String error) {
        voiceInterviewService.updateEvaluateStatus(
                Long.parseLong(payload.sessionId()), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(VoiceEvaluatePayload payload, int retryCount) {
        String sessionId = payload.sessionId();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_VOICE_SESSION_ID, sessionId,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.VOICE_EVALUATE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("语音面试评估任务已重新入队: sessionId={}, retryCount={}", sessionId, retryCount);
        } catch (Exception e) {
            log.error("重试入队失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            voiceInterviewService.updateEvaluateStatus(
                    Long.parseLong(sessionId), AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }
}