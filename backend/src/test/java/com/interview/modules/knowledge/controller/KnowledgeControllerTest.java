package com.interview.modules.knowledge.controller;

import com.interview.modules.knowledge.model.KnowledgeDocument;
import com.interview.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.interview.modules.knowledge.service.DocumentProcessService;
import com.interview.modules.knowledge.service.KnowledgeQAService;
import com.interview.modules.knowledge.service.QAResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest {

    @Mock
    private DocumentProcessService documentProcessService;

    @Mock
    private KnowledgeQAService knowledgeQAService;

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @InjectMocks
    private KnowledgeController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== 文档列表 ====================

    @Test
    void shouldListDocuments() throws Exception {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("测试文档");
        doc.setCreatedAt(LocalDateTime.now());

        when(documentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("测试文档"));
    }

    // ==================== 获取单个文档 ====================

    @Test
    void shouldGetDocument() throws Exception {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("测试文档");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/knowledge/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturn404ForNonExistentDocument() throws Exception {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/knowledge/documents/999"))
                .andExpect(status().isNotFound());
    }

    // ==================== 删除文档 ====================

    @Test
    void shouldDeleteDocument() throws Exception {
        doNothing().when(documentProcessService).deleteDocument(1L);

        mockMvc.perform(delete("/api/knowledge/documents/1"))
                .andExpect(status().isNoContent());
    }

    // ==================== 统计信息 ====================

    @Test
    void shouldGetStatistics() throws Exception {
        when(documentProcessService.getStatistics()).thenReturn(
                java.util.Map.of("totalDocuments", 5, "totalChunks", 100));

        mockMvc.perform(get("/api/knowledge/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(5))
                .andExpect(jsonPath("$.totalChunks").value(100));
    }

    // ==================== RAG 问答 ====================

    @Test
    void shouldAnswerQuestion() throws Exception {
        QAResult result = new QAResult("这是回答内容", List.of());
        when(knowledgeQAService.answer(anyString(), anyList(), anyList())).thenReturn(result);

        mockMvc.perform(post("/api/knowledge/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"什么是Spring Boot?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("这是回答内容"))
                .andExpect(jsonPath("$.sources").isArray());
    }

    @Test
    void shouldRejectEmptyQuestion() throws Exception {
        mockMvc.perform(post("/api/knowledge/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/knowledge/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== 更新文档 ====================

    @Test
    void shouldUpdateDocument() throws Exception {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("旧标题");
        doc.setDescription("旧描述");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(KnowledgeDocument.class))).thenReturn(doc);

        mockMvc.perform(patch("/api/knowledge/documents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"新标题\", \"description\": \"新描述\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("新标题"))
                .andExpect(jsonPath("$.description").value("新描述"));
    }
}