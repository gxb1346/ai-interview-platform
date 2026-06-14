package com.interview.modules.knowledge.service;

import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.resume.service.TikaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 知识库文档处理服务
 * 文档上传 → Tika 解析 → 文本分块 → Embedding 向量化 → 存入 pgvector
 */
@Service
public class DocumentProcessService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessService.class);

    /** 每个分块的最大字符数 */
    private static final int CHUNK_MAX_LENGTH = 800;

    /** 分块重叠字符数 */
    private static final int CHUNK_OVERLAP = 100;

    /** 单次写入 pgvector 的最大分块数（DashScope embedding API 限制：不超过 25 个） */
    private static final int BATCH_SIZE = 10;

    /** 文本最小长度（低于此值跳过，避免空内容导致 embedding 失败） */
    private static final int MIN_CHUNK_LENGTH = 5;

    private final TikaService tikaService;
    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStore vectorStore;

    public DocumentProcessService(TikaService tikaService,
                                  KnowledgeDocumentRepository documentRepository,
                                  VectorStore vectorStore) {
        this.tikaService = tikaService;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    /**
     * 上传并处理文档（同步方法，返回文档元数据）
     * 实际的向量化在异步方法中执行
     *
     * @throws DuplicateDocumentException 如果文件内容已存在
     */
    @Transactional
    public KnowledgeDocument uploadDocument(MultipartFile file, String title, String description) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) fileName = "unknown";
        String fileType = getFileType(fileName);

        // 限制文件大小（最大 50MB）
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小超过限制（最大 50MB）");
        }

        // 计算文件 MD5 用于去重
        String contentHash;
        try {
            contentHash = computeMD5(file.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("计算文件 MD5 失败: " + e.getMessage());
        }

        // 检查是否已上传过相同内容的文件
        Optional<KnowledgeDocument> existing = documentRepository.findByContentHash(contentHash);
        if (existing.isPresent()) {
            KnowledgeDocument dup = existing.get();
            throw new DuplicateDocumentException("文件内容已存在，对应文档：「" + dup.getTitle() + "」，请勿重复上传", dup.getId());
        }

        // 创建文档记录
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setTitle(title != null && !title.isBlank() ? title : fileName);
        doc.setDescription(description);
        doc.setContentHash(contentHash);
        doc.setIndexStatus("PENDING");
        doc.setChunkCount(0);
        KnowledgeDocument saved = documentRepository.save(doc);

        // 异步执行向量化
        processDocumentAsync(file, saved);

        return saved;
    }

    /**
     * 异步处理文档：解析 → 分块 → 向量化
     */
    @Async
    public CompletableFuture<Void> processDocumentAsync(MultipartFile file, KnowledgeDocument doc) {
        Long docId = doc.getId();
        try {
            // 标记为索引中
            doc.setIndexStatus("INDEXING");
            doc.setErrorMessage(null);
            documentRepository.save(doc);

            // 1. Tika 解析文档为纯文本
            String rawText = tikaService.extractText(file);
            if (rawText == null || rawText.isBlank()) {
                throw new RuntimeException("文档内容为空（文件可能为扫描件图片，无法提取文字）");
            }

            // 2. 文本分块（基于字符数，保留段落边界）
            List<String> chunks = splitText(rawText);
            if (chunks.isEmpty()) {
                throw new RuntimeException("文档内容太少，无法分块");
            }

            // 3. 构建 Document 列表，每个 chunk 携带 documentId 元数据
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Document d = new Document(
                        chunks.get(i),
                        Map.of(
                                "documentId", String.valueOf(docId),
                                "chunkIndex", i,
                                "fileName", doc.getFileName(),
                                "title", doc.getTitle()
                        )
                );
                documents.add(d);
            }

            log.info("文档 \"{}\" 解析完成：{} 个分块，开始向量化...", doc.getTitle(), documents.size());

            // 4. 批量写入 pgvector（自动调用 embedding 模型）
            //    分块较多时分批写入，避免超出 DashScope 的 batch 限制
            int totalBatches = (documents.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            int batchIndex = 0;
            for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, documents.size());
                // 注意：必须创建 ArrayList 副本，subList 视图在某些 API 中无法正确序列化
                List<Document> batch = new ArrayList<>(documents.subList(i, end));
                batchIndex++;
                log.info("  写入批次 {}/{} (分块 {}~{}, 共 {} 个)",
                        batchIndex, totalBatches, i + 1, end, batch.size());
                try {
                    vectorStore.add(batch);
                } catch (Exception batchEx) {
                    // 单个批次失败，记录错误但继续尝试后续批次
                    log.error("  批次 {}/{} 写入失败: {}", batchIndex, totalBatches, batchEx.getMessage());
                    throw new RuntimeException("向量化批次 " + batchIndex + "/" + totalBatches + " 失败: " + batchEx.getMessage(), batchEx);
                }
            }

            // 5. 更新文档状态
            doc.setChunkCount(documents.size());
            doc.setIndexStatus("INDEXED");
            documentRepository.save(doc);

            log.info("✅ 知识文档索引完成: {} ({} 个分块)", doc.getTitle(), documents.size());
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("❌ 知识文档索引失败: {} - {}", doc.getTitle(), errorMsg, e);
            doc.setIndexStatus("FAILED");
            doc.setErrorMessage(errorMsg);
            documentRepository.save(doc);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 删除文档及其向量数据
     * 使用原生 JDBC 按 documentId 删除（PgVectorStore.delete() 仅支持 UUID 格式，不支持自定义元数据 ID）
     */
    @Transactional
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在: id=" + docId));

        // 通过 metadata 中的 documentId 过滤删除 pgvector 中的向量数据
        // 使用原生 SQL 因为 PgVectorStore.delete() 只接受 Document UUID，而非自定义 documentId
        try {
            String docIdStr = String.valueOf(docId);
            // 注意：metadata 是 JSONB 类型，使用 ->> 操作符提取 documentId 字段
            documentRepository.deleteVectorByDocumentId(docIdStr);
            log.info("已删除文档 {} 的向量数据", docId);
        } catch (Exception e) {
            log.error("删除向量数据失败: {}", e.getMessage());
        }

        documentRepository.delete(doc);
    }

    /**
     * 获取知识库统计信息
     */
    public Map<String, Object> getStatistics() {
        long totalDocs = documentRepository.count();
        long indexedDocs = documentRepository.countByIndexStatus("INDEXED");
        long pendingDocs = documentRepository.countByIndexStatus("PENDING");
        long failedDocs = documentRepository.countByIndexStatus("FAILED");

        return Map.of(
                "totalDocuments", totalDocs,
                "indexedDocuments", indexedDocs,
                "pendingDocuments", pendingDocs,
                "failedDocuments", failedDocs
        );
    }

    // ========== 文本分块工具 ==========

    /**
     * 基于字符数的文本分块，尽量保留段落完整性
     */
    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");

        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 如果当前块 + 新段落后超出限制，先保存当前块
            if (currentChunk.length() + trimmed.length() > CHUNK_MAX_LENGTH && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                // 保留末尾部分作为重叠
                String overlap = currentChunk.length() > CHUNK_OVERLAP
                        ? currentChunk.substring(currentChunk.length() - CHUNK_OVERLAP) + "\n"
                        : "";
                currentChunk = new StringBuilder(overlap);
            }

            currentChunk.append(trimmed).append("\n");

            // 如果当前块超过上限，强制截断
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

        // 最后一块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    // ========== 工具方法 ==========

    /**
     * 计算字节数组的 MD5 哈希值
     */
    private String computeMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }

    /**
     * 自定义异常：重复文档
     */
    public static class DuplicateDocumentException extends RuntimeException {
        private final Long existingDocId;

        public DuplicateDocumentException(String message, Long existingDocId) {
            super(message);
            this.existingDocId = existingDocId;
        }

        public Long getExistingDocId() {
            return existingDocId;
        }
    }

    private String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "txt";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
