package com.interview.infrastructure.test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 压测控制器
 *
 * 用于触发和执行高并发压测，仅在开发/测试环境启用。
 *
 * API 端点：
 *   POST /api/test/stress?concurrent=50&tasksPerUser=2
 */
@RestController
@RequestMapping("/api/test")
public class StressTestController {

    private final ConcurrencyStressTest stressTest;

    /** 测试执行状态缓存 */
    private final ConcurrentHashMap<String, Object> testStatus = new ConcurrentHashMap<>();

    public StressTestController(ConcurrencyStressTest stressTest) {
        this.stressTest = stressTest;
    }

    /**
     * 执行并发压力测试
     *
     * @param concurrent  并发用户数（默认 10）
     * @param tasksPerUser 每人任务数（默认 1）
     */
    @PostMapping("/stress")
    public ResponseEntity<Map<String, Object>> runStressTest(
            @RequestParam(defaultValue = "10") int concurrent,
            @RequestParam(defaultValue = "1") int tasksPerUser) {

        if (testStatus.containsKey("running") && Boolean.TRUE.equals(testStatus.get("running"))) {
            return ResponseEntity.ok(Map.of(
                    "error", "测试正在进行中，请等待完成",
                    "status", "RUNNING"
            ));
        }

        testStatus.put("running", true);

        try {
            var result = stressTest.runStressTest(concurrent, tasksPerUser);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", "COMPLETED");
            response.put("concurrentCount", result.concurrentCount);
            response.put("totalTasks", result.totalTasks);
            response.put("successSent", result.successSent);
            response.put("failSent", result.failSent);
            response.put("sendDurationMs", result.sendDurationMs);
            response.put("sendTps", String.format("%.1f", result.getSentTps()));
            response.put("sendP50Ms", result.sendP50Ms);
            response.put("sendP95Ms", result.sendP95Ms);
            response.put("sendP99Ms", result.sendP99Ms);
            response.put("pendingBefore", result.pendingBefore);
            response.put("pendingAfter", result.pendingAfter);
            response.put("estimatedConsumed", result.estimatedConsumed);
            response.put("consumeRatio", String.format("%.1f%%", result.getConsumeRatio()));
            response.put("note", "注意：estimatedConsumed 为估算值，pendingAfter可能包含旧消息");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        } finally {
            testStatus.put("running", false);
        }
    }

    /**
     * 获取测试状态
     */
    @GetMapping("/stress/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "running", testStatus.getOrDefault("running", false),
                "available", true
        ));
    }
}
