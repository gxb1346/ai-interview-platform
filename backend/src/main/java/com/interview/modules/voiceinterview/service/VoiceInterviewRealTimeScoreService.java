package com.interview.modules.voiceinterview.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * 语音面试实时逐题评分服务
 * 对候选人每条回答进行即时 AI 评分，结果存入 VoiceInterviewMessageEntity
 */
@Slf4j
@Service
public class VoiceInterviewRealTimeScoreService {

    private static final int AI_MAX_RETRIES = 2;
    private static final long AI_RETRY_DELAY_MS = 500;

    private final ChatClient scoreClient;
    private final ObjectMapper objectMapper;

    public VoiceInterviewRealTimeScoreService(ChatClient.Builder chatClientBuilder) {
        this.scoreClient = chatClientBuilder
                .defaultSystem("你是一个严格的面试评分专家。只输出 JSON，不要输出其他内容。")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 对单条回答进行实时评分（带超时，同步返回）
     */
    public ScoreResult scoreAnswerWithTimeout(String questionText, String answerText,
                                               String phase, int timeoutSeconds) {
        try {
            CompletableFuture<ScoreResult> future = CompletableFuture.supplyAsync(() ->
                    scoreAnswer(questionText, answerText, phase));
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
    public ScoreResult scoreAnswer(String questionText, String answerText, String phase) {
        try {
            String prompt = buildScorePrompt(questionText, answerText, phase);
            String response = callAiWithRetry(prompt);
            String cleaned = cleanJsonResponse(response);
            ScoreResult result = objectMapper.readValue(cleaned, ScoreResult.class);

            int clampedScore = result.score;
            if (clampedScore < 0) clampedScore = 0;
            if (clampedScore > 100) clampedScore = 100;

            result = new ScoreResult(clampedScore, result.feedback);
            log.debug("实时评分: score={}, phase={}", result.score, phase);
            return result;
        } catch (Exception e) {
            log.warn("实时评分失败，跳过评分: {}", e.getMessage());
            return null;
        }
    }

    /** 清洗 LLM 返回的 JSON：剥离 Markdown 代码块，提取第一个 JSON 对象 */
    private String cleanJsonResponse(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String trimmed = content.trim();

        // 剥离 Markdown 代码块 ```json ... ```
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                String inner = trimmed.substring(firstNewline + 1);
                if (inner.endsWith("```")) {
                    inner = inner.substring(0, inner.length() - 3).trim();
                }
                return extractJsonObject(inner);
            }
        }

        // 直接提取 JSON 对象
        return extractJsonObject(trimmed);
    }

    /** 从文本中提取第一个完整的 JSON 对象 */
    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim();
        }
        return text;
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

    private String buildScorePrompt(String question, String answer, String phase) {
        String phaseLabel = switch (phase != null ? phase : "") {
            case "INTRO" -> "自我介绍";
            case "TECH" -> "技术考察";
            case "PROJECT" -> "项目深挖";
            case "HR" -> "反问环节";
            default -> "综合面试";
        };
        return String.format("""
                请对以下语音面试回答进行评分。

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
                """, phaseLabel, question, answer);
    }

    /**
     * 评分结果
     */
    public record ScoreResult(
            @JsonProperty("score") int score,
            @JsonProperty("feedback") String feedback) {
    }
}