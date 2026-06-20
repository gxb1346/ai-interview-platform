package com.interview.modules.interview.controller;

import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.service.AudioService;
import com.interview.modules.interview.service.MockInterviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟面试 REST API 控制器
 * 文字面试使用 REST，语音面试使用 WebSocket
 */
@RestController
@RequestMapping("/api/mock-interview")
public class MockInterviewController {

    private final MockInterviewService interviewService;
    private final AudioService audioService;

    public MockInterviewController(MockInterviewService interviewService,
                                    AudioService audioService) {
        this.interviewService = interviewService;
        this.audioService = audioService;
    }

    /**
     * 创建面试会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody MockInterviewService.CreateSessionRequest request) {
        InterviewSession session = interviewService.createSession(request);
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "status", session.getStatus(),
                "stageConfig", session.getStageConfig()
        ));
    }

    /**
     * 开始面试（生成题目）
     */
    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<Map<String, Object>> startInterview(@PathVariable String sessionId) {
        InterviewSession session = interviewService.startInterview(sessionId);
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("status", session.getStatus());
        response.put("questions", session.getQuestions().stream().map(q -> Map.of(
                "id", q.getId(),
                "text", q.getText(),
                "category", q.getCategory(),
                "difficultyScore", q.getDifficultyScore(),
                "source", q.getSource()
        )).toList());
        response.put("currentStage", session.getCurrentStage());
        response.put("messages", session.getMessages());
        return ResponseEntity.ok(response);
    }

    /**
     * 提交回答并获取回复
     */
    @PostMapping("/sessions/{sessionId}/chat")
    public ResponseEntity<Map<String, Object>> chat(@PathVariable String sessionId,
                                                     @RequestBody Map<String, String> body) {
        String answer = body.get("answer");
        InterviewSession session = interviewService.processAnswer(sessionId, answer);

        var messages = session.getMessages();
        var lastMessage = messages.get(messages.size() - 1);

        String replyText = lastMessage.getText();

        Map<String, Object> response = new HashMap<>();
        response.put("reply", replyText);
        response.put("currentRound", session.getCurrentRound());
        response.put("currentQuestionIndex", session.getCurrentQuestionIndex());
        response.put("totalQuestions", session.getQuestions() == null ? 0 : session.getQuestions().size());
        response.put("currentStage", session.getCurrentStage());
        response.put("status", session.getStatus());

        // 只有语音模式才需要生成语音（文字模式不需要 TTS，避免产生额外费用）
        if ("voice".equals(session.getMode())) {
            String audioBase64 = audioService.textToSpeechBase64(replyText);
            if (audioBase64 != null) {
                response.put("audio", audioBase64);
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取候选人活跃面试会话（用于续面）
     */
    @GetMapping("/candidates/{candidateId}/active-sessions")
    public ResponseEntity<List<InterviewSession>> getActiveSessions(@PathVariable String candidateId) {
        return ResponseEntity.ok(interviewService.getActiveSessions(candidateId));
    }

    /**
     * 恢复面试会话（从中断处继续）
     */
    @PostMapping("/sessions/{sessionId}/resume")
    public ResponseEntity<Map<String, Object>> resumeSession(@PathVariable String sessionId) {
        try {
            InterviewSession session = interviewService.resumeSession(sessionId);
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", session.getSessionId());
            response.put("status", session.getStatus());
            response.put("questions", session.getQuestions().stream().map(q -> Map.of(
                    "id", q.getId(),
                    "text", q.getText(),
                    "category", q.getCategory(),
                    "difficultyScore", q.getDifficultyScore(),
                    "source", q.getSource()
            )).toList());
            response.put("currentStage", session.getCurrentStage());
            response.put("currentRound", session.getCurrentRound());
            response.put("mode", session.getMode());
            response.put("messages", session.getMessages());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 结束面试
     */
    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<Map<String, String>> endInterview(@PathVariable String sessionId) {
        InterviewSession session = interviewService.endInterview(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "status", session.getStatus()
        ));
    }

    /**
     * 暂停面试
     */
    @PostMapping("/sessions/{sessionId}/pause")
    public ResponseEntity<Map<String, String>> pauseInterview(@PathVariable String sessionId) {
        try {
            InterviewSession session = interviewService.pauseSession(sessionId);
            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getSessionId(),
                    "status", session.getStatus()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 恢复暂停的面试
     */
    @PostMapping("/sessions/{sessionId}/unpause")
    public ResponseEntity<Map<String, String>> unpauseInterview(@PathVariable String sessionId) {
        try {
            InterviewSession session = interviewService.unpauseSession(sessionId);
            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getSessionId(),
                    "status", session.getStatus()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取面试会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<InterviewSession> getSession(@PathVariable String sessionId) {
        return interviewService.getSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取候选人所有历史面试
     */
    @GetMapping("/candidates/{candidateId}/sessions")
    public ResponseEntity<List<InterviewSession>> getCandidateSessions(@PathVariable String candidateId) {
        return ResponseEntity.ok(interviewService.getCandidateSessions(candidateId));
    }

    /**
     * 获取所有面试方向
     */
    @GetMapping("/directions")
    public ResponseEntity<List<String>> getAllDirections() {
        return ResponseEntity.ok(com.interview.modules.interview.model.InterviewDirection.getDisplayNames());
    }

    /**
     * 推荐面试方向（基于简历）
     */
    @PostMapping("/directions/recommend")
    public ResponseEntity<List<Map<String, Object>>> recommendDirections(@RequestBody Map<String, String> body) {
        String resumeText = body.get("resumeText");
        var recommendations = interviewService.recommendDirections(resumeText);
        return ResponseEntity.ok(recommendations.stream().map(r -> Map.<String, Object>of(
                "direction", r.getDirection(),
                "matchScore", r.getMatchScore(),
                "reason", r.getReason()
        )).toList());
    }

    /**
     * 解析 JD 文本
     */
    @PostMapping("/jd/parse")
    public ResponseEntity<Map<String, Object>> parseJD(@RequestBody Map<String, String> body) {
        String jdText = body.get("jdText");
        var result = interviewService.parseJD(jdText);
        return ResponseEntity.ok(Map.of(
                "matchedDirection", result.getMatchedDirection(),
                "skills", result.getSkills(),
                "experienceRequired", result.getExperienceRequired(),
                "techStack", result.getTechStack()
        ));
    }
}
