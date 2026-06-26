package com.interview.modules.knowledge.controller;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.knowledge.service.DocumentProcessService;
import com.interview.modules.knowledge.service.DocumentProcessService.DuplicateDocumentException;
import com.interview.modules.knowledge.service.KnowledgeQAService;
import com.interview.modules.knowledge.service.KnowledgeQAService.ChatMessage;
import com.interview.modules.knowledge.service.QAResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final DocumentProcessService documentProcessService;
    private final KnowledgeQAService knowledgeQAService;
    private final KnowledgeDocumentRepository documentRepository;
    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public KnowledgeController(DocumentProcessService documentProcessService,
                               KnowledgeQAService knowledgeQAService,
                               KnowledgeDocumentRepository documentRepository) {
        this.documentProcessService = documentProcessService;
        this.knowledgeQAService = knowledgeQAService;
        this.documentRepository = documentRepository;
    }

    // ========== 文档管理 ==========

    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocument>> listDocuments() {
        return ResponseEntity.ok(documentRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocument> getDocument(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/documents/upload")
    public ResponseEntity<KnowledgeDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            KnowledgeDocument doc = documentProcessService.uploadDocument(file, title, description);
            return ResponseEntity.ok(doc);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (DuplicateDocumentException e) {
            return ResponseEntity.status(409).body(null);
        }
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentProcessService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{id}/reindex")
    public ResponseEntity<Map<String, Object>> reindexDocument(@PathVariable Long id) {
        try {
            documentProcessService.reindexDocument(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "重新索引任务已提交"));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocument> updateDocument(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        return documentRepository.findById(id)
                .map(doc -> {
                    if (body.containsKey("title")) doc.setTitle(body.get("title"));
                    if (body.containsKey("description")) doc.setDescription(body.get("description"));
                    documentRepository.save(doc);
                    return ResponseEntity.ok(doc);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== 知识库统计 ==========

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(documentProcessService.getStatistics());
    }

    // ========== RAG 问答（支持多轮对话 + 引用来源） ==========

    /**
     * RAG 问答（非流式）
     *
     * 请求体：
     * {
     *   "question": "用户问题",
     *   "documentIds": [1, 2, 3],        // 可选，不传则检索全部
     *   "history": [                      // 可选，多轮对话历史
     *     {"role": "user", "content": "..."},
     *     {"role": "assistant", "content": "..."}
     *   ]
     * }
     *
     * 响应：
     * {
     *   "answer": "AI回答",
     *   "sources": [
     *     {"documentTitle": "...", "fileName": "...", "chunkContent": "...", "score": 0.95, "chunkIndex": 0}
     *   ]
     * }
     */
    @PostMapping("/qa")
    public ResponseEntity<Map<String, Object>> askQuestion(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        @SuppressWarnings("unchecked")
        List<Long> documentIds = body.get("documentIds") != null
                ? ((List<Integer>) body.get("documentIds")).stream().map(Long::valueOf).toList()
                : List.of();

        List<ChatMessage> history = parseHistory(body.get("history"));

        QAResult result = knowledgeQAService.answer(question, documentIds, history);

        return ResponseEntity.ok(Map.of(
                "answer", result.answer(),
                "sources", result.sources()
        ));
    }

    /**
     * RAG 问答（SSE 流式，打字机效果 + 末尾引用来源）
     * 使用 POST 避免历史对话过长导致 URL 超长
     *
     * SSE 事件类型：
     * - event:token  data:文本片段          → 逐字输出
     * - event:sources  data:[{...}]        → 末尾输出引用来源 JSON
     * - event:done  data:[DONE]           → 流结束
     * - event:error  data:错误信息         → 异常
     */
    @PostMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }

        @SuppressWarnings("unchecked")
        List<Long> ids = body.get("documentIds") != null
                ? ((List<Integer>) body.get("documentIds")).stream().map(Long::valueOf).toList()
                : List.of();

        List<ChatMessage> history = parseHistory(body.get("history"));

        SseEmitter emitter = new SseEmitter(0L);

        sseExecutor.execute(() -> {
            try {
                knowledgeQAService.streamAnswer(question, ids, history)
                        .subscribe(
                                token -> {
                                    try {
                                        // 检查是否是 sources 标记
                                        if (token.contains("<!--SOURCES:")) {
                                            String sourcesJson = token
                                                    .replace("<!--SOURCES:", "")
                                                    .replace("-->", "")
                                                    .trim();
                                            emitter.send(SseEmitter.event()
                                                    .name("sources")
                                                    .data(sourcesJson));
                                        } else {
                                            emitter.send(SseEmitter.event()
                                                    .name("token")
                                                    .data(token));
                                        }
                                    } catch (Exception e) {
                                        emitter.complete();
                                    }
                                },
                                error -> {
                                    log.error("SSE 流式回答出错: {}", error.getMessage(), error);
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("error")
                                                .data(error.getMessage() != null ? error.getMessage() : "AI回答出错"));
                                    } catch (Exception ignored) {}
                                    emitter.complete();
                                },
                                () -> {
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("done")
                                                .data("[DONE]"));
                                    } catch (Exception ignored) {}
                                    emitter.complete();
                                }
                        );
            } catch (Exception e) {
                log.error("SSE 流式回答初始化失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage() != null ? e.getMessage() : "AI回答初始化失败"));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }

    // ========== 工具方法 ==========

    @SuppressWarnings("unchecked")
    private List<ChatMessage> parseHistory(Object historyObj) {
        if (historyObj == null) return List.of();
        List<ChatMessage> history = new ArrayList<>();
        if (historyObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String role = (String) map.get("role");
                    String content = (String) map.get("content");
                    if (role != null && content != null) {
                        history.add(new ChatMessage(role, content));
                    }
                }
            }
        }
        return history;
    }
}