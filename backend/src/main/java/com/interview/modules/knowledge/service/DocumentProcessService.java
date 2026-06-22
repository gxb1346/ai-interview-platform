package com.interview.modules.knowledge.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.stream.model.TaskType;
import com.interview.infrastructure.stream.producer.TaskProducer;
import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.resume.service.TikaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库文档处理服务
 * 文档上传 → Tika 解析 → Redis Stream 异步向量化
 */
@Service
public class DocumentProcessService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessService.class);

    private final TikaService tikaService;
    private final KnowledgeDocumentRepository documentRepository;
    private final TaskProducer taskProducer;

    public DocumentProcessService(TikaService tikaService,
                                  KnowledgeDocumentRepository documentRepository,
                                  TaskProducer taskProducer) {
        this.tikaService = tikaService;
        this.documentRepository = documentRepository;
        this.taskProducer = taskProducer;
    }

    /**
     * 上传并处理文档（同步方法，返回文档元数据）
     * Tika 解析同步完成，向量化通过 Redis Stream 异步执行
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
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "计算文件 MD5 失败: " + e.getMessage());
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

        // 1. Tika 解析文档为纯文本（同步完成，速度较快）
        String rawText = tikaService.extractText(file);
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "文档内容为空（文件可能为扫描件图片，无法提取文字）");
        }

        // 2. 通过 Redis Stream 异步执行向量化（分块 → Embedding → 写入 pgvector）
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", saved.getId());
        payload.put("rawText", rawText);
        payload.put("fileName", saved.getFileName());
        payload.put("title", saved.getTitle());
        taskProducer.sendTask(TaskType.DOCUMENT_INDEX, payload);

        log.info("文档上传完成，向量化任务已发送到 Redis Stream: documentId={}", saved.getId());
        return saved;
    }

    /**
     * 删除文档及其向量数据
     */
    @Transactional
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: id=" + docId));

        // 通过 metadata 中的 documentId 过滤删除 pgvector 中的向量数据
        try {
            String docIdStr = String.valueOf(docId);
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
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED, "MD5 算法不可用");
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