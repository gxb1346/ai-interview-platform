package com.interview.modules.evaluation.export;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.evaluation.engine.UnifiedEvaluationEngine;
import com.interview.modules.evaluation.model.EvaluationReport;
import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.service.MockInterviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 面试评估 API 控制器
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final UnifiedEvaluationEngine evaluationEngine;
    private final MockInterviewService interviewService;
    private final PdfExportService pdfExportService;

    public EvaluationController(UnifiedEvaluationEngine evaluationEngine,
                                MockInterviewService interviewService,
                                PdfExportService pdfExportService) {
        this.evaluationEngine = evaluationEngine;
        this.interviewService = interviewService;
        this.pdfExportService = pdfExportService;
    }

    /**
     * 评估面试会话
     */
    @PostMapping("/sessions/{sessionId}")
    public ResponseEntity<EvaluationReport> evaluate(@PathVariable String sessionId) {
        InterviewSession session = interviewService.getSession(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND, "面试会话不存在: " + sessionId));

        EvaluationReport report = evaluationEngine.evaluate(session);

        // 面试完成后自动异步生成 PDF
        pdfExportService.exportReport(report);

        // 评估后自动将会话标记为已完成（移出未完成面试列表）
        try {
            interviewService.endInterview(sessionId);
        } catch (Exception ignored) {
            // 已完成的会话不报错
        }

        return ResponseEntity.ok(report);
    }

    /**
     * 评估并立即下载 PDF
     */
    @PostMapping("/sessions/{sessionId}/export-pdf")
    public ResponseEntity<Map<String, Object>> evaluateAndExport(@PathVariable String sessionId) {
        InterviewSession session = interviewService.getSession(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND, "面试会话不存在: " + sessionId));

        EvaluationReport report = evaluationEngine.evaluate(session);

        // 异步导出 PDF
        CompletableFuture<String> pdfFuture = pdfExportService.exportReport(report);

        try {
            String pdfPath = pdfFuture.get();
            Map<String, Object> response = new HashMap<>();
            response.put("report", report);
            response.put("pdfPath", pdfPath);
            response.put("pdfUrl", "/api/evaluation/download?path=" + pdfPath);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("report", report);
            response.put("pdfPath", null);
            response.put("error", "PDF 导出失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}