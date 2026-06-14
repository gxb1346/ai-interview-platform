package com.interview.modules.knowledge.controller;

import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.knowledge.service.DocumentProcessService;
import com.interview.modules.knowledge.service.KnowledgeQAService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

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

    /**
     * 获取文档列表
     */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocument>> listDocuments() {
        return ResponseEntity.ok(documentRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * 获取单个文档
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocument> getDocument(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 上传文档（支持 PDF/DOCX/MD/TXT）
     */
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
        }
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentProcessService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新文档信息（标题、描述）
     */
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

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(documentProcessService.getStatistics());
    }

    // ========== RAG 问答 ==========

    /**
     * RAG 问答（非流式）
     *
     * @param body { question: string, documentIds: number[] }
     */
    @PostMapping("/qa")
    public ResponseEntity<Map<String, String>> askQuestion(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        @SuppressWarnings("unchecked")
        List<Long> documentIds = body.get("documentIds") != null
                ? ((List<Integer>) body.get("documentIds")).stream().map(Long::valueOf).toList()
                : List.of();

        String answer = knowledgeQAService.answer(question, documentIds);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    /**
     * RAG 问答（SSE 流式，打字机效果）
     * GET /api/knowledge/qa/stream?question=xxx&documentIds=1,2,3
     */
    @GetMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(
            @RequestParam String question,
            @RequestParam(required = false) String documentIds) {

        List<Long> ids = documentIds != null && !documentIds.isBlank()
                ? List.of(documentIds.split(",")).stream()
                .map(String::trim)
                .map(Long::parseLong)
                .toList()
                : List.of();

        SseEmitter emitter = new SseEmitter(0L); // 不超时

        sseExecutor.execute(() -> {
            try {
                knowledgeQAService.streamAnswer(question, ids)
                        .subscribe(
                                token -> {
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("token")
                                                .data(token));
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("error")
                                                .data(error.getMessage()));
                                    } catch (Exception ignored) {}
                                    emitter.completeWithError(error);
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
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
