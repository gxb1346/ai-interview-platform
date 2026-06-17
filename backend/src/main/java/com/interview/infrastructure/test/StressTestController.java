package com.interview.infrastructure.test;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 并发压测触发器
 */
@RestController
@RequestMapping("/api/test")
public class StressTestController {

    private final ConcurrencyStressTest stressTest;

    public StressTestController(ConcurrencyStressTest stressTest) {
        this.stressTest = stressTest;
    }

    /**
     * 触发并发压测
     *
     * @param concurrent  并发用户数
     * @param tasksPerUser 每个用户任务数
     * @return 测试结果
     */
    @PostMapping("/stress")
    public Map<String, Object> runStressTest(
            @RequestParam(defaultValue = "50") int concurrent,
            @RequestParam(defaultValue = "1") int tasksPerUser) {

        Map<String, Object> result = new HashMap<>();
        try {
            ConcurrencyStressTest.StressResult sr = stressTest.runStressTest(concurrent, tasksPerUser);

            result.put("code", 200);
            result.put("message", "压测完成");
            result.put("concurrentCount", sr.concurrentCount);
            result.put("totalTasks", sr.totalTasks);
            result.put("successSent", sr.successSent);
            result.put("failSent", sr.failSent);
            result.put("sendDurationMs", sr.sendDurationMs);
            result.put("sendP50Ms", sr.sendP50Ms);
            result.put("sendP95Ms", sr.sendP95Ms);
            result.put("sendP99Ms", sr.sendP99Ms);
            result.put("pendingBefore", sr.pendingBefore);
            result.put("pendingAfter", sr.pendingAfter);
            result.put("estimatedConsumed", sr.estimatedConsumed);
            result.put("sentTps", String.format("%.2f", sr.getSentTps()));
            result.put("consumeRatio", String.format("%.1f%%", sr.getConsumeRatio()));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "压测失败: " + e.getMessage());
        }
        return result;
    }
}
