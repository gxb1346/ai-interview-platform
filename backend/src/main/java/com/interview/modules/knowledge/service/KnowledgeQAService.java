package com.interview.modules.knowledge.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库问答服务（RAG 检索增强生成）
 * 支持按文档 ID 过滤（单选/多选），SSE 流式输出
 */
@Service
public class KnowledgeQAService {

    /** 每次检索返回的最相关文档块数量 */
    private static final int TOP_K = 5;

    /** 相似度阈值（低于此值不纳入上下文） */
    private static final double SIMILARITY_THRESHOLD = 0.65;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public KnowledgeQAService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个专业的知识库问答助手。请基于提供的参考知识内容，准确、严谨地回答用户问题。"
                        + "如果参考知识中不包含答案，请如实告知，不要编造信息。"
                        + "回答时引用相关知识点，但不要提及\"根据提供的知识\"等措辞。")
                .build();
    }

    /**
     * RAG 问答（非流式，返回完整答案）
     *
     * @param question      用户问题
     * @param documentIds   文档 ID 列表（为空则检索全部知识库）
     * @return 答案
     */
    public String answer(String question, List<Long> documentIds) {
        // 1. 检索相关知识
        List<Document> relevantDocs = retrieve(question, documentIds);

        // 2. 构建增强 Prompt
        String prompt = buildRAGPrompt(question, relevantDocs);

        // 3. 调用 LLM
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * RAG 问答（SSE 流式，逐 token 返回）
     *
     * @param question      用户问题
     * @param documentIds   文档 ID 列表（为空则检索全部知识库）
     * @return 流式 Flux
     */
    public Flux<String> streamAnswer(String question, List<Long> documentIds) {
        // 1. 检索相关知识
        List<Document> relevantDocs = retrieve(question, documentIds);

        // 2. 构建增强 Prompt
        String prompt = buildRAGPrompt(question, relevantDocs);

        // 3. 流式调用 LLM
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * 从 pgvector 检索相关文档块
     * 支持按 documentId 过滤（单选/多选）
     */
    private List<Document> retrieve(String question, List<Long> documentIds) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD);

        // 如果指定了文档 ID，添加过滤条件: documentId in ['1', '2', '3']
        if (documentIds != null && !documentIds.isEmpty()) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            // documentId 在 metadata 中以字符串存储，需转为字符串值
            List<String> idStrs = documentIds.stream()
                    .map(String::valueOf)
                    .toList();
            Expression filter;
            if (idStrs.size() == 1) {
                // 单个值用 eq 避免生成数组语法 ["9"]
                filter = fb.eq("documentId", idStrs.get(0)).build();
            } else {
                filter = fb.in("documentId", idStrs).build();
            }
            builder.filterExpression(filter);
        }

        return vectorStore.similaritySearch(builder.build());
    }

    /**
     * 构建 RAG Prompt：将检索到的知识上下文注入到用户问题中
     */
    private String buildRAGPrompt(String question, List<Document> relevantDocs) {
        // 如果没有检索到相关知识，直接回答
        if (relevantDocs == null || relevantDocs.isEmpty()) {
            return "用户问题：" + question + "\n\n（未在知识库中找到相关信息）";
        }

        // 拼接检索到的知识上下文
        String context = relevantDocs.stream()
                .map(doc -> {
                    String source = "未知来源";
                    if (doc.getMetadata() != null) {
                        Object title = doc.getMetadata().get("title");
                        if (title != null) source = title.toString();
                    }
                    return "【来源：" + source + "】\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        return """
                请基于以下参考知识回答用户的问题。
                
                === 参考知识 ===
                %s
                === 参考知识结束 ===
                
                用户问题：%s
                
                回答要求：
                1. 基于参考知识回答，不要编造
                2. 如果参考知识不足以回答问题，请如实告知
                3. 回答要简洁、准确、有条理
                """.formatted(context, question);
    }
}
