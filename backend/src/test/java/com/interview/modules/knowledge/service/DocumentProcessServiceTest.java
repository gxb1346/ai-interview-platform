package com.interview.modules.knowledge.service;

import com.interview.common.exception.BusinessException;
import com.interview.infrastructure.stream.model.TaskType;
import com.interview.infrastructure.stream.producer.TaskProducer;
import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.resume.service.TikaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentProcessServiceTest {

    @Mock
    private TikaService tikaService;

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private TaskProducer taskProducer;

    @InjectMocks
    private DocumentProcessService service;

    // ==================== 删除文档 ====================

    @Test
    void shouldDeleteDocument() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("测试文档");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        doNothing().when(documentRepository).deleteVectorByDocumentId(anyString());
        doNothing().when(documentRepository).delete(doc);

        service.deleteDocument(1L);

        verify(documentRepository).deleteVectorByDocumentId("1");
        verify(documentRepository).delete(doc);
    }

    @Test
    void shouldThrowWhenDeleteNonExistentDocument() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.deleteDocument(999L));
    }

    // ==================== 重新索引 ====================

    @Test
    void shouldReindexDocument() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setRawText("这是文档内容");
        doc.setFileName("test.pdf");
        doc.setTitle("测试文档");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        doNothing().when(documentRepository).deleteVectorByDocumentId(anyString());
        when(documentRepository.save(any(KnowledgeDocument.class))).thenReturn(doc);
        when(taskProducer.sendTask(any(), anyMap())).thenReturn("task-123");

        service.reindexDocument(1L);

        verify(taskProducer).sendTask(eq(TaskType.DOCUMENT_INDEX), anyMap());
    }

    @Test
    void shouldThrowWhenReindexWithoutRawText() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setRawText(null);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

        assertThrows(BusinessException.class, () -> service.reindexDocument(1L));
    }

    // ==================== 统计信息 ====================

    @Test
    void shouldGetStatistics() {
        when(documentRepository.count()).thenReturn(10L);
        when(documentRepository.countByIndexStatus("INDEXED")).thenReturn(7L);
        when(documentRepository.countByIndexStatus("PENDING")).thenReturn(2L);
        when(documentRepository.countByIndexStatus("FAILED")).thenReturn(1L);

        Map<String, Object> stats = service.getStatistics();

        assertEquals(10L, stats.get("totalDocuments"));
        assertEquals(7L, stats.get("indexedDocuments"));
        assertEquals(2L, stats.get("pendingDocuments"));
        assertEquals(1L, stats.get("failedDocuments"));
    }
}