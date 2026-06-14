package com.interview.modules.knowledge.repository;

import com.interview.modules.knowledge.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    long countByIndexStatus(String indexStatus);

    Optional<KnowledgeDocument> findByContentHash(String contentHash);

    Optional<KnowledgeDocument> findByFileNameAndFileSizeAndContentHashIsNull(String fileName, Long fileSize);

    /**
     * 按 documentId 元数据删除 pgvector 中的向量数据
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM vector_store WHERE metadata->>'documentId' = ?1", nativeQuery = true)
    void deleteVectorByDocumentId(String documentId);
}
