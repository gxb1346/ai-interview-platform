package com.interview.modules.interview.service;

import com.interview.modules.interview.skill.InterviewSkill;
import com.interview.modules.interview.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 简历方向推荐服务（Semantic Matching）
 * 上传简历后，LLM 自动推荐最匹配的面试方向
 */
@Service
public class DirectionRecommendService {

    private static final Logger log = LoggerFactory.getLogger(DirectionRecommendService.class);

    /** 方向推荐缓存前缀 */
    private static final String CACHE_PREFIX = "direction:recommend:";

    /** 缓存 TTL（小时） */
    private static final long CACHE_TTL_HOURS = 24;

    private final ChatClient chatClient;
    private final SkillRegistry skillRegistry;
    private final StringRedisTemplate redisTemplate;

    public DirectionRecommendService(ChatClient.Builder chatClientBuilder,
                                     SkillRegistry skillRegistry,
                                     StringRedisTemplate redisTemplate) {
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个 AI 招聘专家，擅长根据简历内容匹配最适合的面试方向。")
                .build();
        this.skillRegistry = skillRegistry;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 推荐面试方向
     *
     * @param resumeText 简历文本
     * @return 推荐的方向列表（按匹配度降序）
     */
    public List<DirectionMatch> recommend(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return getDefaultRecommendations();
        }

        // ---- 尝试从缓存获取 ----
        String cacheKey = CACHE_PREFIX + md5(resumeText);
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                log.info("[方向推荐] 缓存命中，跳过 LLM");
                return parseResponse(cachedJson);
            }
        } catch (Exception e) {
            log.warn("[方向推荐] 读缓存失败，降级: {}", e.getMessage());
        }

        try {
            List<String> allDirections = skillRegistry.getAllDirectionNames();
            String directionsStr = String.join("、", allDirections);

            String prompt = """
                    你是一个 AI 招聘专家。请根据以下简历内容，从 %s 中推荐最匹配的 3 个面试方向。
                    
                    要求：
                    1. 分析简历中的技术栈、项目经验和工作经历
                    2. 给出匹配度评分（0-100分）
                    3. 给出匹配理由
                    
                    请以 JSON 数组格式返回，不要包含 Markdown 标记。
                    [
                        {"direction": "方向名称", "matchScore": 95, "reason": "匹配理由"},
                        {"direction": "方向名称", "matchScore": 85, "reason": "匹配理由"},
                        {"direction": "方向名称", "matchScore": 75, "reason": "匹配理由"}
                    ]
                    
                    简历内容：
                    ---
                    %s
                    ---
                    """.formatted(directionsStr, resumeText);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // ---- 写入缓存 ----
            if (response != null) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_HOURS, TimeUnit.HOURS);
                } catch (Exception e) {
                    log.warn("[方向推荐] 写缓存失败: {}", e.getMessage());
                }
            }

            return parseResponse(response);
        } catch (Exception e) {
            System.err.println("方向推荐失败: " + e.getMessage());
            return getDefaultRecommendations();
        }
    }

    /**
     * 计算 MD5 哈希
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private List<DirectionMatch> parseResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) cleaned = cleaned.substring(firstNewline).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        List<DirectionMatch> results = new ArrayList<>();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> items = mapper.readValue(cleaned, List.class);
            for (Map<String, Object> item : items) {
                DirectionMatch match = new DirectionMatch();
                match.setDirection((String) item.get("direction"));
                match.setMatchScore(item.get("matchScore") instanceof Integer
                        ? (Integer) item.get("matchScore") : 70);
                match.setReason((String) item.get("reason"));
                results.add(match);
            }
        } catch (Exception e) {
            System.err.println("推荐方向 JSON 解析失败: " + e.getMessage());
            return getDefaultRecommendations();
        }

        return results;
    }

    private List<DirectionMatch> getDefaultRecommendations() {
        return List.of(
                new DirectionMatch("Java后端开发", 85, "默认推荐"),
                new DirectionMatch("系统设计", 75, "默认推荐"),
                new DirectionMatch("算法与数据结构", 70, "默认推荐")
        );
    }

    /**
     * 方向匹配结果
     */
    public static class DirectionMatch {
        private String direction;
        private int matchScore;
        private String reason;

        public DirectionMatch() {}

        public DirectionMatch(String direction, int matchScore, String reason) {
            this.direction = direction;
            this.matchScore = matchScore;
            this.reason = reason;
        }

        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }

        public int getMatchScore() { return matchScore; }
        public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
