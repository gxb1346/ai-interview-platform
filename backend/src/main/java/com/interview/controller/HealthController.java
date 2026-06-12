package com.interview.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class HealthController {

    private final StringRedisTemplate redisTemplate;

    public HealthController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Value("${AI_API_KEY:}")
    private String apiKey;

    @Value("${AI_MODEL:}")
    private String aiModel;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "OK", "message", "AI面试平台后端已启动");
    }

    @GetMapping("/debug/config")
    public Map<String, String> debugConfig() {
        String maskedKey = apiKey.length() > 8
                ? apiKey.substring(0, 8) + "..."
                : "(空)";
        return Map.of(
                "apiKey", maskedKey,
                "model", aiModel,
                "baseUrl", baseUrl
        );
    }

    @GetMapping("/debug/redis")
    public Map<String, Object> debugRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return Map.of(
                    "status", "OK",
                    "message", "Redis连接正常",
                    "pong", pong
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "ERROR",
                    "message", "Redis连接失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }
}