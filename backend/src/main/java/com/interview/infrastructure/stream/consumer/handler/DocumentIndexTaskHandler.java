package com.interview.infrastructure.stream.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.stream.consumer.TaskHandler;
import com.interview.infrastructure.stream.model.StreamMessage;
import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.resume.service.TikaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库文档向量化任务处理器
 *
 * 消费 Redis Stream 中的 DOCUMENT_INDEX 消息，
 * 执行 Tika 解析 → 文本分块 → Embedding → 写入 pgvector
 */
@Component
public class DocumentIndexTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexTaskHandler.class);

    /** 每个分块的最大字符数 */
    private static final int CHUNK_MAX_LENGTH = 1500;

    /** 分块重叠字符数 */
    private static final int CHUNK_OVERLAP = 200;

    /** 单次写入 pgvector 的最大分块数 */
    private static final int BATCH_SIZE = 10;

    /** 文本最小长度 */
    private static final int MIN_CHUNK_LENGTH = 5;

    private final TikaService tikaService;
    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public DocumentIndexTaskHandler(TikaService tikaService,
                                    KnowledgeDocumentRepository documentRepository,
                                    VectorStore vectorStore,
                                    ObjectMapper objectMapper) {
        this.tikaService = tikaService;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean handle(StreamMessage message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

            Long documentId = ((Number) payload.get("documentId")).longValue();
            String rawText = (String) payload.get("rawText");
            String fileName = (String) payload.get("fileName");
            String title = (String) payload.get("title");

            Optional<KnowledgeDocument> optDoc = documentRepository.findById(documentId);
            if (optDoc.isEmpty()) {
                log.warn("[文档索引] 文档不存在: documentId={}", documentId);
                return true; // 已删除，视为成功
            }

            KnowledgeDocument doc = optDoc.get();
            doc.setIndexStatus("INDEXING");
            doc.setErrorMessage(null);
            documentRepository.save(doc);

            // 1. 文本分块
            List<String> chunks = splitText(rawText);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.DOCUMENT_INDEX_FAILED, "文档内容太少，无法分块");
            }

            // 2. 构建 Document 列表
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Document d = new Document(
                        chunks.get(i),
                        Map.of(
                                "documentId", String.valueOf(documentId),
                                "chunkIndex", i,
                                "fileName", fileName != null ? fileName : "",
                                "title", title != null ? title : ""
                        )
                );
                documents.add(d);
            }

            log.info("[文档索引] 文档 \"{}\" 解析完成：{} 个分块，开始向量化...", title, documents.size());

            // 3. 批量写入 pgvector
            int totalBatches = (documents.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            int batchIndex = 0;
            for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, documents.size());
                List<Document> batch = new ArrayList<>(documents.subList(i, end));
                batchIndex++;
                log.info("[文档索引]   写入批次 {}/{} (分块 {}~{}, 共 {} 个)",
                        batchIndex, totalBatches, i + 1, end, batch.size());
                try {
                    vectorStore.add(batch);
                } catch (Exception batchEx) {
                    log.error("[文档索引]   批次 {}/{} 写入失败: {}", batchIndex, totalBatches, batchEx.getMessage());
                    throw new BusinessException(ErrorCode.DOCUMENT_INDEX_FAILED, "向量化批次 " + batchIndex + "/" + totalBatches + " 失败: " + batchEx.getMessage());
                }
            }

            // 4. 更新文档状态
            doc.setChunkCount(documents.size());
            doc.setIndexStatus("INDEXED");
            documentRepository.save(doc);

            log.info("[文档索引] ✅ 知识文档索引完成: {} ({} 个分块)", title, documents.size());
            return true;

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[文档索引] ❌ 处理失败: taskId={}, error={}", message.getTaskId(), errorMsg, e);

            // 更新文档状态为 FAILED
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
                Long documentId = ((Number) payload.get("documentId")).longValue();
                documentRepository.findById(documentId).ifPresent(doc -> {
                    doc.setIndexStatus("FAILED");
                    doc.setErrorMessage(errorMsg);
                    documentRepository.save(doc);
                });
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    // ========== 文本分块（与 DocumentProcessService 保持一致） ==========

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");

        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (currentChunk.length() + trimmed.length() > CHUNK_MAX_LENGTH && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                String overlap = currentChunk.length() > CHUNK_OVERLAP
                        ? currentChunk.substring(currentChunk.length() - CHUNK_OVERLAP) + "\n"
                        : "";
                currentChunk = new StringBuilder(overlap);
            }

            currentChunk.append(trimmed).append("\n");

            while (currentChunk.length() > CHUNK_MAX_LENGTH) {
                String segment = currentChunk.substring(0, CHUNK_MAX_LENGTH);
                int lastPeriod = segment.lastIndexOf("。");
                int lastNewline = segment.lastIndexOf("\n");
                int splitPos = Math.max(lastPeriod, lastNewline);
                if (splitPos < CHUNK_MAX_LENGTH / 2) splitPos = CHUNK_MAX_LENGTH;

                chunks.add(currentChunk.substring(0, splitPos).trim());
                currentChunk = new StringBuilder(currentChunk.substring(Math.max(0, splitPos - CHUNK_OVERLAP)));
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}