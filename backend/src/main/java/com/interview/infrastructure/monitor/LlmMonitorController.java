package com.interview.infrastructure.monitor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LLM 调用监控端点
 *
 * GET /api/monitor/llm-calls  - 查看 LLM 调用统计
 * GET /api/monitor/llm-calls/snapshot - 查看详细报告
 */
@RestController
@RequestMapping("/api/monitor")
public class LlmMonitorController {

    private final LlmCallMonitor monitor;

    public LlmMonitorController(LlmCallMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/llm-calls")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalCalls", monitor.getTotalCalls(),
                "cacheHits", monitor.getCacheHits(),
                "dedupSkips", monitor.getDedupSkips(),
                "rateLimitBlocks", monitor.getRateLimitBlocks(),
                "savedCalls", monitor.getSavedCalls(),
                "netLlmCalls", monitor.getTotalCalls()  // 缓存/去重拦截的都不算入内
        ));
    }

    @GetMapping("/llm-calls/snapshot")
    public ResponseEntity<Map<String, String>> getSnapshot() {
        return ResponseEntity.ok(Map.of(
                "report", monitor.getSnapshot()
        ));
    }
}
