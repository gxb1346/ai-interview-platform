package com.interview.modules.knowledge.service;

import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库问答服务（RAG 检索增强生成）
 * 支持多轮对话、按文档 ID 过滤、SSE 流式输出、引用来源追溯
 */
@Slf4j
@Service
public class KnowledgeQAService {

    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.65;
    private static final double FALLBACK_SIMILARITY_THRESHOLD = 0.55; // 兜底检索降低阈值
    private static final int MAX_HISTORY_TURNS = 6; // 最多携带最近6轮对话

    private static final String TABLE_NAME = "public.vector_store";

    private static final String BASE_SEARCH_SQL = "SELECT *, embedding <=> ? AS distance FROM "
            + TABLE_NAME + " WHERE embedding <=> ? < ? ";

    private static final String ORDER_LIMIT_SQL = " ORDER BY distance LIMIT ?";

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ChatClient queryRewriteClient;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final JsonMapper jsonMapper;

    public KnowledgeQAService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                               JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.jsonMapper = JsonMapper.builder().build();
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个专业的知识库问答助手。请基于提供的参考知识内容，准确、严谨地回答用户问题。"
                        + "如果参考知识中不包含答案，请如实告知，不要编造信息。"
                        + "回答时引用相关知识点，但不要提及\"根据提供的知识\"等措辞。")
                .build();
        this.queryRewriteClient = chatClientBuilder
                .defaultSystem("你是一个查询改写助手。根据对话历史，将用户的追问改写为独立完整的检索查询语句。只输出改写后的查询，不要输出任何其他内容。")
                .build();
    }

    /**
     * 对话历史消息
     */
    public record ChatMessage(String role, String content) {}

    /**
     * RAG 问答（非流式，返回完整答案 + 引用来源）
     *
     * @param question      用户问题
     * @param documentIds   文档 ID 列表（为空则检索全部知识库）
     * @param history       多轮对话历史（可为空）
     * @return 答案 + 引用来源
     */
    public QAResult answer(String question, List<Long> documentIds, List<ChatMessage> history) {
        // 1. 多轮对话：基于历史改写查询，提升检索精度
        String searchQuery = rewriteQuery(question, history);

        // 2. 检索相关知识
        List<Document> relevantDocs = retrieve(searchQuery, documentIds);

        // 3. 构建增强 Prompt（含对话历史）
        String prompt = buildRAGPrompt(question, relevantDocs, history);

        // 4. 调用 LLM
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 5. 构建引用来源
        List<QAResult.SourceInfo> sources = buildSources(relevantDocs);

        return new QAResult(answer, sources);
    }

    /**
     * RAG 问答（SSE 流式，逐 token 返回 + 末尾 sources 事件）
     *
     * @param question      用户问题
     * @param documentIds   文档 ID 列表（为空则检索全部知识库）
     * @param history       多轮对话历史（可为空）
     * @return 流式 Flux（token 逐字），末尾追加 sources JSON
     */
    public Flux<String> streamAnswer(String question, List<Long> documentIds, List<ChatMessage> history) {
        // 1. 多轮对话：基于历史改写查询
        String searchQuery = rewriteQuery(question, history);

        // 2. 检索相关知识
        List<Document> relevantDocs = retrieve(searchQuery, documentIds);

        // 3. 构建 sources JSON（提前构建好，流结束时追加）
        List<QAResult.SourceInfo> sources = buildSources(relevantDocs);
        String sourcesJson = toSourcesJson(sources);

        // 4. 构建增强 Prompt
        String prompt = buildRAGPrompt(question, relevantDocs, history);

        // 5. 流式调用 LLM + 末尾追加 sources
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .concatWith(Mono.just("\n\n<!--SOURCES:" + sourcesJson + "-->"));
    }

    // ========== 私有方法 ==========

    /**
     * 查询改写：将简短/模糊问题扩展为精准检索词
     * - 如果 LLM quota 充足：有历史补上下文，无历史做语义扩展
     * - 如果 quota 耗尽：只用规则做定义问题扩展 + 大小写变体，省额度
     */
    private String rewriteQuery(String question, List<ChatMessage> history) {
        String baseQuery;

        // === 先尝试规则扩展（不消耗 LLM quota）===
        baseQuery = ruleBasedExpand(question);

        // === 如果有历史且 LLM 可用，再补上下文 ===
        if (history != null && !history.isEmpty() && isLLMQuotaAvailable()) {
            List<ChatMessage> recentHistory = history.subList(
                    Math.max(0, history.size() - MAX_HISTORY_TURNS), history.size());

            String historyText = recentHistory.stream()
                    .map(m -> m.role() + ": " + m.content())
                    .collect(Collectors.joining("\n"));

            String rewritePrompt = """
                    对话历史：
                    %s
                    
                    用户最新问题：%s
                    
                    请将用户的最新问题改写为一个独立完整的检索查询语句。
                    如果用户是在追问，请将追问内容与历史中的关键信息合并。
                    只输出改写后的查询语句，不要输出任何其他内容。
                    """.formatted(historyText, question);

            String rewritten = callRewriteLLM(rewritePrompt, baseQuery);
            if (!rewritten.equals(baseQuery)) {
                baseQuery = rewritten;
            }
        }

        // 处理大小写变体：从含中文的词中提取英文词做扩展
        String[] words = baseQuery.split("[\\s,，.。]+");
        StringBuilder expanded = new StringBuilder(baseQuery);

        for (String word : words) {
            if (word.length() < 3) continue;
            String englishPart = word.replaceAll("[\\u4e00-\\u9fff]+", "").trim();
            if (englishPart.isEmpty()) continue;

            if (englishPart.equals(englishPart.toLowerCase()) && !englishPart.equals(englishPart.toUpperCase())) {
                String capitalized = Character.toUpperCase(englishPart.charAt(0)) + englishPart.substring(1);
                if (!expanded.toString().contains(capitalized)) {
                    expanded.append(" ").append(capitalized);
                }
            }
            if (Character.isUpperCase(englishPart.charAt(0))
                    && englishPart.substring(1).equals(englishPart.substring(1).toLowerCase())) {
                String lower = englishPart.toLowerCase();
                if (!expanded.toString().contains(lower)) {
                    expanded.append(" ").append(lower);
                }
            }
        }

        String result = expanded.toString().trim();
        return result.length() > 500 ? baseQuery : result;
    }

    /**
     * 规则扩展："XX是什么" → "XX 定义 概念 特点 概述"
     * 不消耗 LLM quota，免费额度耗尽也能工作
     */
    private String ruleBasedExpand(String question) {
        String base = question.trim();
        // 匹配"XX是什么"、"什么是XX"、"XX介绍"、"XX定义"
        if (base.matches(".*是什么$") || base.matches("什么是.*") || base.matches(".*介绍") || base.matches(".*定义")) {
            // 提取核心名词
            String keyword = base.replaceAll(".*什么是|是什么$|介绍|定义|什么", "").trim();
            if (!keyword.isEmpty()) {
                return keyword + " 定义 概念 特点 概述";
            }
        }
        // 匹配"XX有什么特点" → "XX 特点 特性 优势"
        if (base.matches(".*有什么特点$")) {
            String keyword = base.replaceAll("有什么特点$|什么", "").trim();
            if (!keyword.isEmpty()) {
                return keyword + " 特点 特性 优势";
            }
        }
        return base;
    }

    /**
     * 检查 LLM 是否还有配额：之前调用失败就不再尝试
     */
    private volatile boolean llmQuotaAvailable = true;

    private boolean isLLMQuotaAvailable() {
        return llmQuotaAvailable;
    }

    /**
     * 调用 LLM 改写查询，失败时返回原始问题
     */
    private String callRewriteLLM(String prompt, String fallback) {
        if (!llmQuotaAvailable) {
            return fallback;
        }
        try {
            String rewritten = queryRewriteClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (rewritten != null && !rewritten.isBlank() && rewritten.length() < 500) {
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.warn("查询改写失败: {} → 后续改用纯规则扩展", e.getMessage());
            // 如果是配额错误，永久禁用 LLM 改写
            if (e.getMessage() != null && e.getMessage().contains("quota") || e.getMessage().contains("403") || e.getMessage().contains("exhausted")) {
                llmQuotaAvailable = false;
            }
        }
        return fallback;
    }

    /**
     * 从 pgvector 检索相关文档块
     * 当向量检索无结果时，降级为关键词模糊匹配 + 降低阈值重试
     */
    private List<Document> retrieve(String question, List<Long> documentIds) {
        // 第1次：正常阈值检索
        List<Document> results = doVectorSearch(question, documentIds, SIMILARITY_THRESHOLD);

        // 第2次：无结果时降低阈值重试
        if (results.isEmpty()) {
            log.info("正常阈值(0.65)无结果，降低阈值(0.55)重试: {}", question);
            results = doVectorSearch(question, documentIds, FALLBACK_SIMILARITY_THRESHOLD);
        }

        // 第3次：仍无结果，关键词模糊匹配兜底
        if (results.isEmpty()) {
            log.info("降低阈值后仍无结果，启用关键词兜底搜索: {}", question);
            results = keywordFallbackSearch(question, documentIds);
        }

        return results;
    }

    /**
     * 向量检索
     */
    private List<Document> doVectorSearch(String question, List<Long> documentIds, double threshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(threshold);

        if (documentIds != null && !documentIds.isEmpty()) {
            float[] queryVector = embeddingModel.embed(question);
            PGvector queryPgVector = new PGvector(queryVector);
            double distance = 1 - threshold;

            String jsonPathFilter = documentIds.stream()
                    .map(id -> "$.documentId == \"" + id + "\"")
                    .collect(Collectors.joining(" || ", "'(", ")'::jsonpath"));

            String sql = BASE_SEARCH_SQL + " AND metadata::jsonb @@ " + jsonPathFilter + ORDER_LIMIT_SQL;

            return jdbcTemplate.query(sql, new DocumentRowMapper(jsonMapper),
                    queryPgVector, queryPgVector, distance, TOP_K);
        }

        return vectorStore.similaritySearch(builder.build());
    }

    /**
     * 关键词模糊匹配兜底：当向量检索无结果时，用 ILIKE 模糊匹配 content 和 metadata 中的 title
     */
    private List<Document> keywordFallbackSearch(String question, List<Long> documentIds) {
        // 提取中文关键词（2字以上）
        String[] words = question.split("[\\s,，.。？?！!]+");
        List<String> keywords = new ArrayList<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (trimmed.length() >= 2) {
                keywords.add(trimmed);
            }
        }

        if (keywords.isEmpty()) {
            return List.of();
        }

        // 提取核心关键词（最长的优先）
        String coreKeyword = keywords.stream()
                .max(Comparator.comparingInt(String::length))
                .orElse(keywords.get(0));

        // 用参数化查询防注入
        StringBuilder sql = new StringBuilder("SELECT *, 0.0 as distance FROM ").append(TABLE_NAME).append(" WHERE ");
        List<Object> params = new ArrayList<>();

        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            String likePattern = "%" + keywords.get(i) + "%";
            conditions.add("(content ILIKE ? OR metadata::jsonb->>'title' ILIKE ?)");
            params.add(likePattern);
            params.add(likePattern);
        }
        sql.append(String.join(" AND ", conditions));

        // 核心关键词优先排序
        sql.append(" ORDER BY (CASE WHEN content ILIKE ? THEN 0 ELSE 1 END) LIMIT ?");
        params.add("%" + coreKeyword + "%");
        params.add(TOP_K);

        if (documentIds != null && !documentIds.isEmpty()) {
            String jsonPathFilter = documentIds.stream()
                    .map(id -> {
                        params.add(String.valueOf(id));
                        return "metadata::jsonb->>'documentId' = ?";
                    })
                    .collect(Collectors.joining(" OR ", " AND (", ")"));
            sql.insert(sql.indexOf(" ORDER BY"), jsonPathFilter);
        }

        log.debug("关键词兜底SQL: {}", sql);

        return jdbcTemplate.query(sql.toString(), new DocumentRowMapper(jsonMapper), params.toArray());
    }

    /**
     * 构建引用来源列表
     */
    private List<QAResult.SourceInfo> buildSources(List<Document> relevantDocs) {
        if (relevantDocs == null || relevantDocs.isEmpty()) {
            return List.of();
        }

        return relevantDocs.stream()
                .map(doc -> {
                    String title = "未知文档";
                    String fileName = "";
                    int chunkIndex = 0;
                    if (doc.getMetadata() != null) {
                        if (doc.getMetadata().get("title") instanceof String t) title = t;
                        if (doc.getMetadata().get("fileName") instanceof String f) fileName = f;
                        if (doc.getMetadata().get("chunkIndex") instanceof Number n) chunkIndex = n.intValue();
                    }
                    return new QAResult.SourceInfo(
                            title,
                            fileName,
                            doc.getText() != null ? doc.getText() : "",
                            doc.getScore() != null ? doc.getScore() : 0.0,
                            chunkIndex
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 将 sources 序列化为 JSON（用于 SSE 末尾追加）
     */
    private String toSourcesJson(List<QAResult.SourceInfo> sources) {
        try {
            return jsonMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 构建 RAG Prompt：知识上下文 + 对话历史 + 用户问题
     */
    private String buildRAGPrompt(String question, List<Document> relevantDocs, List<ChatMessage> history) {
        StringBuilder prompt = new StringBuilder();

        // 对话历史
        if (history != null && !history.isEmpty()) {
            prompt.append("=== 对话历史 ===\n");
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (int i = start; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                String roleLabel = "user".equals(msg.role()) ? "用户" : "助手";
                prompt.append(roleLabel).append("：").append(msg.content()).append("\n");
            }
            prompt.append("=== 对话历史结束 ===\n\n");
        }

        // 知识上下文
        if (relevantDocs == null || relevantDocs.isEmpty()) {
            prompt.append("（未在知识库中找到相关信息）\n\n");
            prompt.append("用户问题：").append(question).append("\n\n");
            prompt.append("请注意：知识库中未检索到相关内容，但你可以结合自身知识回答。");
            prompt.append("回答时请在末尾注明「（该回答基于通用知识，未在知识库中确认）」。");
        } else {
            // 按文档去重合并：每个文档只取TOP2分块
            Map<String, List<Document>> grouped = relevantDocs.stream()
                    .collect(Collectors.groupingBy(
                            doc -> doc.getMetadata() != null && doc.getMetadata().get("title") != null
                                    ? doc.getMetadata().get("title").toString()
                                    : "未知来源",
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            prompt.append("=== 参考知识 ===\n");
            for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
                prompt.append("【来源：").append(entry.getKey()).append("】\n");
                List<Document> docs = entry.getValue().stream()
                        .limit(2) // 每个文档只取TOP2
                        .toList();
                for (int i = 0; i < docs.size(); i++) {
                    prompt.append("（片段").append(i + 1).append("）").append(docs.get(i).getText()).append("\n");
                }
                prompt.append("\n");
            }
            prompt.append("=== 参考知识结束 ===\n\n");

            prompt.append("用户问题：").append(question).append("\n\n");
            prompt.append("回答要求：\n");
            prompt.append("1. 基于参考知识回答，不要编造\n");
            prompt.append("2. 如果参考知识不足以回答问题，请如实告知\n");
            prompt.append("3. 回答要简洁、准确、有条理\n");
            prompt.append("4. 如果是追问，结合对话历史理解用户意图\n");
        }

        return prompt.toString();
    }

    // ========== 内部类 ==========

    private static class DocumentRowMapper implements RowMapper<Document> {

        private final JsonMapper jsonMapper;

        DocumentRowMapper(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = rs.getString("id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            float distance = rs.getFloat("distance");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = jsonMapper.readValue(metadataJson, Map.class);
            metadata.put(DocumentMetadata.DISTANCE.value(), distance);

            return Document.builder()
                    .id(id)
                    .text(content)
                    .metadata(metadata)
                    .score(1.0 - distance)
                    .build();
        }
    }
}