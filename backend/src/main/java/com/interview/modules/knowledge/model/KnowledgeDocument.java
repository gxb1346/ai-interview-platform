package com.interview.modules.knowledge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 知识库文档元数据
 * 每个文档对应一条记录，文档内容被分块后存入 pgvector
 */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件名 */
    @Column(nullable = false)
    private String fileName;

    /** 文件类型: pdf, docx, md, txt */
    @Column(nullable = false)
    private String fileType;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 文档标题（用户可自定义，默认为文件名） */
    private String title;

    /** 文档描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 分块数量 */
    @Column(nullable = false)
    private Integer chunkCount = 0;

    /** 向量化状态: PENDING, INDEXING, INDEXED, FAILED */
    @Column(nullable = false)
    private String indexStatus = "PENDING";

    /** 索引失败时的错误信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 文件内容的 MD5 哈希值（用于去重） */
    @Column(length = 32, unique = true)
    private String contentHash;

    /** 上传时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
