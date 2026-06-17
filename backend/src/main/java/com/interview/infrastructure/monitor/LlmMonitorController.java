package com.interview.infrastructure.monitor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LLM 调用监控控制器
 * 提供 LLM 调用统计数据的 HTTP 查询接口
 */
@RestController
@RequestMapping("/api/monitor/llm-calls")
public class LlmMonitorController {

    private final LlmCallMonitor monitor;

    public LlmMonitorController(LlmCallMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * 获取 LLM 调用统计 JSON
     */
    @GetMapping
    public Map<String, Object> getStats() {
        return monitor.getStatsAsMap();
    }

    /**
     * 获取格式化的 LLM 调用报告文本
     */
    @GetMapping("/snapshot")
    public String getSnapshot() {
        return monitor.getSnapshot();
    }
}
