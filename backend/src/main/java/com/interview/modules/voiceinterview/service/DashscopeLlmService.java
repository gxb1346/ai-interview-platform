package com.interview.modules.voiceinterview.service;

import com.interview.common.ai.LlmProviderRegistry;
import com.interview.common.ai.PromptSanitizer;
import com.interview.modules.resume.model.Resume;
import com.interview.modules.resume.repository.ResumeRepository;
import com.interview.modules.voiceinterview.config.VoiceInterviewProperties;
import com.interview.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import com.interview.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashscopeLlmService {

    private static final String TERMINAL_PUNCTUATION = "。！？；!?;.";

    private final LlmProviderRegistry llmProviderRegistry;
    private final VoiceInterviewPromptService promptService;
    private final ResumeRepository resumeRepository;
    private final VoiceInterviewMessageRepository messageRepository;
    private final VoiceInterviewProperties voiceInterviewProperties;
    private final PromptSanitizer promptSanitizer;

    public String chat(String userInput, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        try {
            PromptContext promptContext = buildPromptContext(userInput, session, conversationHistory);

            String provider = session.getLlmProvider();
            log.info("[VoiceInterview] Session {} using LLM provider: {}", session.getId(), provider);

            ChatClient chatClient = llmProviderRegistry.getVoiceChatClient(provider);

            ChatClient.CallResponseSpec response = chatClient.prompt()
                .system(promptContext.systemPrompt())
                .user(promptContext.userPrompt())
                .call();

            String content = response.chatResponse().getResult().getOutput().getText();
            String optimized = optimizeForVoice(content);

            log.info("LLM response generated for session {}: {}", session.getId(),
                     optimized.substring(0, Math.min(100, optimized.length())));

            return optimized;

        } catch (Exception e) {
            log.error("LLM chat error for session {}: {}", session.getId(), e.getMessage(), e);
            return mapLlmErrorToUserMessage(e);
        }
    }

    public String chatStream(String userInput, Consumer<String> onToken, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        return chatStreamSentences(userInput, onToken, null, session, conversationHistory);
    }

    /**
     * 流式调用 LLM，每检测到一个完整句子就回调 onSentence，同时推送实时文本给 onToken。
     * 返回完整优化后的文本。
     */
    public String chatStreamSentences(String userInput,
                                       Consumer<String> onToken,
                                       Consumer<String> onSentence,
                                       VoiceInterviewSessionEntity session,
                                       List<String> conversationHistory) {
        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("[VoiceInterview] Session {} LLM stream retry attempt {}/{}",
                        session.getId(), attempt, maxRetries);
                    long backoffMs = Math.min(1000L * (1L << (attempt - 1)), 4000L);
                    Thread.sleep(backoffMs);
                }
                return doChatStreamSentences(userInput, onToken, onSentence, session, conversationHistory);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries && isRetryableStreamError(e)) {
                    log.warn("[VoiceInterview] Session {} LLM stream retryable error (attempt {}): {}",
                        session.getId(), attempt + 1, e.getMessage());
                    continue;
                }
                log.error("LLM sentence stream error for session {}: {}", session.getId(), e.getMessage(), e);
                return mapLlmErrorToUserMessage(e);
            }
        }

        log.error("LLM sentence stream exhausted retries for session {}: {}",
            session.getId(), lastException != null ? lastException.getMessage() : "unknown");
        return mapLlmErrorToUserMessage(lastException);
    }

    private boolean isRetryableStreamError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("connection reset")
            || lower.contains("broken pipe")
            || lower.contains("socket")
            || lower.contains("eof")
            || lower.contains("timeout")
            || lower.contains("429")
            || lower.contains("rate limit")
            || lower.contains("too many requests");
    }

    private String doChatStreamSentences(String userInput,
                                          Consumer<String> onToken,
                                          Consumer<String> onSentence,
                                          VoiceInterviewSessionEntity session,
                                          List<String> conversationHistory) {
        PromptContext promptContext = buildPromptContext(userInput, session, conversationHistory);
        String provider = session.getLlmProvider();
        log.info("[VoiceInterview] Session {} using LLM provider (sentence stream): {}", session.getId(), provider);

        ChatClient chatClient = llmProviderRegistry.getVoiceChatClient(provider);
        StringBuilder raw = new StringBuilder();
        AtomicLong lastEmitNanos = new AtomicLong(System.nanoTime());
        AtomicInteger lastEmitLength = new AtomicInteger(0);
        AtomicInteger lastSentenceEnd = new AtomicInteger(0);
        int emitIntervalMs = Math.max(80, voiceInterviewProperties.getAiStreamPushIntervalMs());
        int minCharsDelta = Math.max(4, voiceInterviewProperties.getAiStreamMinCharsDelta());

        chatClient.prompt()
            .system(promptContext.systemPrompt())
            .user(promptContext.userPrompt())
            .stream()
            .content()
            .doOnNext(token -> {
                if (token == null || token.isEmpty()) {
                    return;
                }
                raw.append(token);

                if (onSentence != null && hasTerminalSince(token)) {
                    String normalized = normalizeRealtimeText(raw.toString());
                    int currentEnd = normalized.length();
                    if (currentEnd > lastSentenceEnd.get()) {
                        String sentence = normalized.substring(lastSentenceEnd.get()).trim();
                        if (!sentence.isEmpty()) {
                            onSentence.accept(sentence);
                        }
                        lastSentenceEnd.set(currentEnd);
                    }
                }

                if (onToken == null) {
                    return;
                }
                long now = System.nanoTime();
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(now - lastEmitNanos.get());
                int currentLength = raw.length();
                boolean shouldEmit = elapsedMs >= emitIntervalMs && currentLength - lastEmitLength.get() >= minCharsDelta;
                if (!shouldEmit) {
                    return;
                }
                String normalized = normalizeRealtimeText(raw.toString());
                if (normalized.isBlank()) {
                    return;
                }
                onToken.accept(normalized);
                lastEmitNanos.set(now);
                lastEmitLength.set(normalized.length());
            })
            .blockLast(Duration.ofSeconds(Math.max(30, voiceInterviewProperties.getLlmTimeoutSeconds())));

        if (onSentence != null) {
            String normalized = normalizeRealtimeText(raw.toString());
            if (normalized.length() > lastSentenceEnd.get()) {
                String remaining = normalized.substring(lastSentenceEnd.get()).trim();
                if (!remaining.isEmpty()) {
                    onSentence.accept(remaining);
                }
            }
        }

        String optimized = optimizeForVoice(raw.toString());
        if (onToken != null && !optimized.isBlank()) {
            onToken.accept(optimized);
        }

        log.info("LLM sentence stream response for session {}: {}", session.getId(),
            optimized.substring(0, Math.min(100, optimized.length())));
        return optimized;
    }

    private PromptContext buildPromptContext(String userInput, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        String resumeText = null;
        if (session.getResumeId() != null) {
            Resume resume = resumeRepository.findById(session.getResumeId()).orElse(null);
            if (resume != null) {
                resumeText = resume.getRawText();
            }
        }

        String systemPrompt = promptService.generateSystemPromptWithContext(
            session.getSkillId(), resumeText,
            session.getCurrentPhase() != null ? session.getCurrentPhase().name() : "INTRO",
            countAskedQuestions(session.getId()),
            getFollowUpIndex(session.getId()));

        StringBuilder promptBuilder = new StringBuilder();
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("【之前的对话】\n");
            for (String message : conversationHistory) {
                promptBuilder.append(promptSanitizer.sanitize(message)).append("\n");
            }
            promptBuilder.append("\n【当前对话】\n");
        }
        promptBuilder.append("用户：").append(
            promptSanitizer.wrapWithDelimiters("input", promptSanitizer.sanitize(userInput)));
        return new PromptContext(systemPrompt, promptBuilder.toString());
    }

    private String mapLlmErrorToUserMessage(Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage != null) {
            String lower = errorMessage.toLowerCase();
            if (lower.contains("403") || lower.contains("access_denied") ||
                lower.contains("authentication") || lower.contains("unauthorized")) {
                return "AI 服务认证失败，请检查 API Key 配置";
            } else if (lower.contains("timeout") || e instanceof TimeoutException) {
                return "AI 服务响应超时，请稍后重试";
            } else if (lower.contains("429") || lower.contains("rate limit") ||
                       lower.contains("quota") || lower.contains("too many requests")) {
                return "AI 服务调用频率超限，请稍后重试";
            } else if (lower.contains("connection reset") || lower.contains("broken pipe") ||
                       lower.contains("connection") || lower.contains("network") ||
                       lower.contains("socket") || lower.contains("eof")) {
                return "AI 服务网络连接失败，请检查网络";
            } else if (lower.contains("aggregation")) {
                return "AI 服务响应异常，请稍后重试";
            }
        }
        return "抱歉，AI 服务暂时不可用，请稍后重试";
    }

    private String optimizeForVoice(String content) {
        String normalized = normalizeRealtimeText(content);
        if (normalized.isBlank()) {
            return "请继续。";
        }

        int maxChars = Math.max(80, voiceInterviewProperties.getAiQuestionMaxChars());
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        String truncated = normalized.substring(0, maxChars);
        int lastTerminal = -1;
        for (int i = truncated.length() - 1; i >= 0; i--) {
            if (TERMINAL_PUNCTUATION.indexOf(truncated.charAt(i)) >= 0) {
                lastTerminal = i;
                break;
            }
        }
        if (lastTerminal >= maxChars / 2) {
            return truncated.substring(0, lastTerminal + 1);
        }

        return truncated + "…";
    }

    private String normalizeRealtimeText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content
            .replace("**", "")
            .replace("```", "")
            .replace("`", "")
            .replaceAll("(?m)^\\s*[-*+]\\s*", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private boolean hasTerminalSince(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (TERMINAL_PUNCTUATION.indexOf(token.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private int countAskedQuestions(Long sessionId) {
        try {
            return (int) messageRepository.countBySessionId(sessionId);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 获取当前追问次数（基于已提问数估算） */
    private int getFollowUpIndex(Long sessionId) {
        try {
            int asked = countAskedQuestions(sessionId);
            return Math.max(0, asked - 1);
        } catch (Exception e) {
            return 0;
        }
    }

    private record PromptContext(String systemPrompt, String userPrompt) {}
}