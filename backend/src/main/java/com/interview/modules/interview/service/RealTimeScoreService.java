package com.interview.modules.interview.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * 实时评分服务
 * 对候选人每条回答进行即时 AI 评分，存入 InterviewMessage
 */
@Slf4j
@Service
public class RealTimeScoreService {

    private static final int AI_MAX_RETRIES = 2;
    private static final long AI_RETRY_DELAY_MS = 500;

    private final ChatClient scoreClient;
    private final ObjectMapper objectMapper;

    public RealTimeScoreService(ChatClient.Builder chatClientBuilder) {
        this.scoreClient = chatClientBuilder
                .defaultSystem("你是一个严格的面试评分专家。只输出 JSON，不要输出其他内容。")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 对单条回答进行实时评分（带超时，同步返回）
     *
     * @param questionText 面试题目
     * @param answerText   候选人回答
     * @param stage        面试阶段
     * @param timeoutSeconds 超时秒数
     * @return 评分结果，超时或失败时返回 null
     */
    public ScoreResult scoreAnswerWithTimeout(String questionText, String answerText,
                                               String stage, int timeoutSeconds) {
        try {
            CompletableFuture<ScoreResult> future = CompletableFuture.supplyAsync(() ->
                    scoreAnswer(questionText, answerText, stage));
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("实时评分超时({}秒)，跳过", timeoutSeconds);
            return null;
        } catch (Exception e) {
            log.warn("实时评分异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 对单条回答进行实时评分（无超时）
     */
    public ScoreResult scoreAnswer(String questionText, String answerText, String stage) {
        try {
            String prompt = buildScorePrompt(questionText, answerText, stage);
            String response = callAiWithRetry(prompt);
            ScoreResult result = objectMapper.readValue(response, ScoreResult.class);

            if (result.score < 0) result.score = 0;
            if (result.score > 100) result.score = 100;

            log.debug("实时评分: score={}, stage={}", result.score, stage);
            return result;
        } catch (Exception e) {
            log.warn("实时评分失败，跳过评分: {}", e.getMessage());
            return null;
        }
    }

    private String callAiWithRetry(String prompt) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= AI_MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(AI_RETRY_DELAY_MS);
                }
                return scoreClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评分被中断");
            } catch (Exception e) {
                lastException = e;
                log.warn("实时评分第{}次调用失败: {}", attempt + 1, e.getMessage());
            }
        }
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "实时评分所有重试均失败");
    }

    private String buildScorePrompt(String question, String answer, String stage) {
        return String.format("""
                请对以下面试回答进行评分。

                面试阶段：%s
                题目：%s
                候选人回答：%s

                ### 评分规则（1-100分）：
                - 回答极其简短（如"不知道"、"好"、单个词）：10-25 分
                - 回答敷衍、内容空洞、与问题无关：20-40 分
                - 回答基本完整但缺少深度：50-65 分
                - 回答内容充实、有逻辑：70-85 分
                - 回答专业深入、有实例、表达清晰：86-100 分

                请以 JSON 格式返回：
                {
                    "score": 评分,
                    "feedback": "一句话简要评价"
                }

                只返回 JSON，不要输出其他内容。
                """, stage, question, answer);
    }

    /**
     * 评分结果
     */
    public static class ScoreResult {
        @JsonProperty("score")
        public int score;

        @JsonProperty("feedback")
        public String feedback;
    }
}