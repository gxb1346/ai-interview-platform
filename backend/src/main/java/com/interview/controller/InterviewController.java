package com.interview.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private final ChatClient chatClient;

    public InterviewController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        try {
            String userMessage = request.get("message");
            String response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();
            return Map.of("reply", response);
        } catch (Exception e) {
            System.err.println("AI对话异常: " + e.getMessage());
            e.printStackTrace();
            return Map.of("reply", "AI对话失败: " + e.getMessage());
        }
    }
}
