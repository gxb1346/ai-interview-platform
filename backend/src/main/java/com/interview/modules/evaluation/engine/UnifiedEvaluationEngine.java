package com.interview.modules.evaluation.engine;

import com.interview.infrastructure.monitor.ChatResponseHelper;
import com.interview.infrastructure.monitor.LlmCallMonitor;
import com.interview.modules.evaluation.model.EvaluationReport;
import com.interview.modules.evaluation.model.EvaluationResult;
import com.interview.modules.interview.model.InterviewMessage;
import com.interview.modules.interview.model.InterviewSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 统一评估引擎
 * 文字面试和语音面试共用同一套评估逻辑
 * 架构：分批评估 -> 结构化输出 -> 二次汇总 -> 降级兜底
 */
@Slf4j
@Component
public class UnifiedEvaluationEngine {

    private static final int BATCH_SIZE = 5;  // 每批次评估 5 轮对话

    private final ChatClient evaluationClient;
    private final ObjectMapper objectMapper;
    private final ChatResponseHelper chatHelper;
    private final ExecutorService evaluationExecutor;

    public UnifiedEvaluationEngine(ChatClient.Builder chatClientBuilder,
                                    ChatResponseHelper chatHelper,
                                    @Qualifier("evaluationExecutor") ExecutorService evaluationExecutor) {
        this.evaluationClient = chatClientBuilder
                .defaultSystem("你是一个资深的 AI 面试评估专家，负责对面试对话进行多维度量化评估。")
                .build();
        this.objectMapper = new ObjectMapper();
        this.chatHelper = chatHelper;
        this.evaluationExecutor = evaluationExecutor;
    }

    /**
     * 对面试会话进行全面评估
     *
     * @param session 面试会话
     * @return 评估报告
     */
    public EvaluationReport evaluate(InterviewSession session) {
        List<InterviewMessage> messages = session.getMessages();

        // 1. 分批评估（对话太长则分块，每块独立评估，并行执行）
        List<EvaluationResult> batchResults = batchEvaluate(messages);

        // 2. 结构化输出（从批次结果中计算综合评分，不再额外调用 LLM，大幅提升速度）
        EvaluationReport report = structuredOutput(batchResults, session);

        // 3. 降级兜底（AI 调用失败时返回模板报告）
        if (report.getSummary() == null || report.getSummary().isEmpty()) {
            return fallbackReport(session);
        }

        return report;
    }

    /**
     * 分批评估
     */
    private List<EvaluationResult> batchEvaluate(List<InterviewMessage> messages) {
        List<EvaluationResult> results = new ArrayList<>();
        int totalRounds = messages.size();
        int batches = (int) Math.ceil((double) totalRounds / BATCH_SIZE);

        // 使用虚拟线程并行评估各个批次
        List<CompletableFuture<EvaluationResult>> futures = new ArrayList<>();

        for (int i = 0; i < batches; i++) {
            int finalStart = i * BATCH_SIZE;
            int finalEnd = Math.min(finalStart + BATCH_SIZE, totalRounds);
            int batchIdx = i;
            List<InterviewMessage> batchMessages = messages.subList(finalStart, finalEnd);

            CompletableFuture<EvaluationResult> future = CompletableFuture.supplyAsync(() ->
                    evaluateBatch(batchMessages, batchIdx, finalStart, finalEnd),
                    evaluationExecutor
            );
            futures.add(future);
        }

        // 收集所有批次结果
        for (CompletableFuture<EvaluationResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.warn("分批评估失败: {}", e.getMessage());
            }
        }

        return results;
    }

    /**
     * 评估单个批次
     */
    private EvaluationResult evaluateBatch(List<InterviewMessage> messages, int batchIndex,
                                            int roundStart, int roundEnd) {
        try {
            String conversationLog = messages.stream()
                    .map(m -> String.format("[%s]: %s", m.getSender(), m.getText()))
                    .collect(Collectors.joining("\n"));

            String prompt = String.format("""
                    你是一个严格的 AI 面试评估专家。请对以下面试对话（第 %d 批，共 %d 轮）进行严厉、客观的评估。

                    对话内容：
                    ---
                    %s
                    ---

                    ### 评分规则（必须严格执行）：
                    1. 如果候选人的回答极其简短（如只有"1"、"好"、"不知道"等），每出现一次扣 15 分
                    2. 如果候选人回答内容与问题无关、答非所问，每出现一次扣 10 分
                    3. 如果候选人所有回答都敷衍了事（如始终只回复一个词），综合评分不得超过 25 分
                    4. 维度评分（1-10分）中，敷衍回答的对应维度不得超过 2 分
                    5. 技术深度（technical）：根据回答的专业程度评分，无技术内容的回答不得超过 2 分
                    6. 沟通表达（communication）：回答简短敷衍则不得超过 2 分
                    7. 问题解决（problemSolving）：无实质内容则不得超过 2 分
                    8. 综合素质（culturalFit）：无实质内容则不得超过 2 分
                    9. 正常完整回答按实际水平客观评分

                    请从以下维度评分（1-10分）：
                    1. 技术深度（technical）
                    2. 沟通表达（communication）
                    3. 问题解决（problemSolving）
                    4. 综合素质（culturalFit）

                    同时给出：
                    - 综合评分（1-100分）
                    - 0-3 个该批次表现出的优势（仅当有实质性优势时填写，无则留空数组）
                    - 1-3 个待改进项（必须有内容）
                    - 一段简短评估（指出具体问题）

                    请以 JSON 格式返回：
                    {
                        "batchScore": 综合评分,
                        "dimensionScores": {"technical": 1, "communication": 1, "problemSolving": 1, "culturalFit": 1},
                        "strengths": ["优势1"],
                        "weaknesses": ["待改进1", "待改进2"],
                        "summary": "评估摘要..."
                    }
                    """, batchIndex, roundEnd - roundStart, conversationLog);

            String response = chatHelper.call(LlmCallMonitor.BATCH_EVALUATION, evaluationClient, prompt);

            return parseBatchResult(response, batchIndex, roundStart, roundEnd);

        } catch (Exception e) {
            log.warn("批次评估异常: {}", e.getMessage());
            EvaluationResult fallback = new EvaluationResult();
            fallback.setBatchId("batch_" + batchIndex);
            fallback.setBatchIndex(batchIndex);
            fallback.setRoundStart(roundStart);
            fallback.setRoundEnd(roundEnd);
            fallback.setBatchScore(50);
            fallback.setBatchStrengths(new ArrayList<>());
            fallback.setBatchWeaknesses(new ArrayList<>());
            fallback.setBatchSummary("该批次评估异常，使用默认评分。");
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private EvaluationResult parseBatchResult(String response, int batchIndex,
                                               int roundStart, int roundEnd) {
        String cleaned = cleanJson(response);
        EvaluationResult result = new EvaluationResult();
        result.setBatchId("batch_" + batchIndex);
        result.setBatchIndex(batchIndex);
        result.setRoundStart(roundStart);
        result.setRoundEnd(roundEnd);
        result.setBatchStrengths(new ArrayList<>());
        result.setBatchWeaknesses(new ArrayList<>());

        try {
            Map<String, Object> data = objectMapper.readValue(cleaned, Map.class);
            result.setBatchScore((Integer) data.getOrDefault("batchScore", 50));
            result.setBatchSummary((String) data.getOrDefault("summary", ""));

            if (data.get("strengths") instanceof List) {
                result.setBatchStrengths((List<String>) data.get("strengths"));
            }
            if (data.get("weaknesses") instanceof List) {
                result.setBatchWeaknesses((List<String>) data.get("weaknesses"));
            }
            if (data.get("dimensionScores") instanceof Map) {
                Map<String, Object> scores = (Map<String, Object>) data.get("dimensionScores");
                Map<String, Integer> parsed = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : scores.entrySet()) {
                    parsed.put(entry.getKey(), entry.getValue() instanceof Integer
                            ? (Integer) entry.getValue()
                            : Integer.parseInt(entry.getValue().toString()));
                }
                result.setDimensionScores(parsed);
            }
        } catch (Exception e) {
            result.setBatchScore(50);
            result.setBatchSummary("解析批次评估结果失败。");
        }

        return result;
    }

    /**
     * 结构化输出（无 LLM 调用，纯计算，快速）
     */
    private EvaluationReport structuredOutput(List<EvaluationResult> batchResults,
                                               InterviewSession session) {
        EvaluationReport report = new EvaluationReport();
        report.setReportId(UUID.randomUUID().toString());
        report.setSessionId(session.getSessionId());
        report.setCandidateId(session.getCandidateId());
        report.setCandidateName(session.getCandidateName());
        report.setDirection(session.getDirection());
        report.setLevel(session.getLevel());
        report.setMode(session.getMode());
        report.setTotalRounds(session.getCurrentRound());

        // 加权计算综合分
        double avgScore = batchResults.stream()
                .mapToInt(EvaluationResult::getBatchScore)
                .average()
                .orElse(50.0);
        report.setOverallScore((int) Math.round(avgScore));

        // 收集优势和改进项
        List<String> allStrengths = batchResults.stream()
                .flatMap(r -> r.getBatchStrengths().stream())
                .distinct()
                .collect(Collectors.toList());
        List<String> allWeaknesses = batchResults.stream()
                .flatMap(r -> r.getBatchWeaknesses().stream())
                .distinct()
                .collect(Collectors.toList());

        report.setStrengths(allStrengths.size() > 3 ? allStrengths.subList(0, 3) : allStrengths);
        report.setImprovements(allWeaknesses.size() > 2 ? allWeaknesses.subList(0, 2) : allWeaknesses);

        // 汇总各批次评估摘要，生成最终总结
        String combinedSummary = batchResults.stream()
                .map(r -> String.format("【第%d批(第%d-%d轮)】%s",
                        r.getBatchIndex() + 1, r.getRoundStart() + 1, r.getRoundEnd(),
                        r.getBatchSummary()))
                .collect(Collectors.joining("\n"));
        report.setSummary(combinedSummary.isEmpty() ? "评估完成，暂无详细总结。" : combinedSummary);

        // 根据综合评分判定结论
        int score = report.getOverallScore();
        if (score >= 80) {
            report.setVerdict("建议录用");
        } else if (score >= 60) {
            report.setVerdict("待定");
        } else {
            report.setVerdict("不予录用");
        }

        // 维度评分：从批次结果中取平均值
        Map<String, Integer> avgDimensions = new LinkedHashMap<>();
        List<Map<String, Integer>> allDimensionScores = batchResults.stream()
                .map(EvaluationResult::getDimensionScores)
                .filter(scores -> scores != null && !scores.isEmpty())
                .collect(Collectors.toList());

        if (!allDimensionScores.isEmpty()) {
            String[] dims = {"technical", "communication", "problemSolving", "culturalFit"};
            for (String dim : dims) {
                double avg = allDimensionScores.stream()
                        .filter(scores -> scores.containsKey(dim))
                        .mapToInt(scores -> scores.get(dim))
                        .average()
                        .orElse(3.0);
                avgDimensions.put(dim, (int) Math.round(avg));
            }
        } else {
            avgDimensions.put("technical", 3);
            avgDimensions.put("communication", 3);
            avgDimensions.put("problemSolving", 3);
            avgDimensions.put("culturalFit", 3);
        }
        report.setDimensionScores(avgDimensions);

        return report;
    }

    /**
     * 二次汇总
     */
    private EvaluationReport aggregate(EvaluationReport structuredReport,
                                        List<EvaluationResult> batchResults) {
        try {
            String batchSummaries = batchResults.stream()
                    .map(r -> String.format("第%d批(第%d-%d轮): 评分%d - %s",
                            r.getBatchIndex(), r.getRoundStart(), r.getRoundEnd(),
                            r.getBatchScore(), r.getBatchSummary()))
                    .collect(Collectors.joining("\n"));

            String prompt = String.format("""
                    你是一个严格的 AI 面试评估总专家。请根据以下各批次的评估结果，生成最终的面试评估报告。

                    各批次评估：
                    ---
                    %s
                    ---

                    ### 评分规则（必须严格执行）：
                    1. 如果批次评估显示候选人回答敷衍（简短、答非所问等），维度评分不得超过 2 分
                    2. 综合评分必须基于各批次评分的加权平均，不能随意调高
                    3. 四个维度评分（technical, communication, problemSolving, culturalFit，1-10分）：
                       - 敷衍回答 → 1-2分
                       - 一般回答 → 3-5分
                       - 良好回答 → 6-8分
                       - 优秀回答 → 9-10分
                    4. 最终建议严格遵循：综合评分≥80→"建议录用"，60-79→"待定"，<60→"不予录用"
                    5. 如果各批次评分均低于 30，综合评分应在 15-30 之间，最终建议为"不予录用"

                    请生成：
                    1. 一段 200-300 字的整体评估总结（如实反映候选人表现）
                    2. 四个维度的评分
                    3. 最终建议

                    请以 JSON 格式返回：
                    {
                        "summary": "最终评估总结...",
                        "dimensionScores": {"technical": 1, "communication": 1, "problemSolving": 1, "culturalFit": 1},
                        "verdict": "不予录用"
                    }
                    """, batchSummaries);

            String response = chatHelper.call(LlmCallMonitor.AGGREGATE_EVALUATION, evaluationClient, prompt);

            return parseAggregationResult(response, structuredReport);

        } catch (Exception e) {
            log.warn("二次汇总失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private EvaluationReport parseAggregationResult(String response, EvaluationReport report) {
        String cleaned = cleanJson(response);
        try {
            Map<String, Object> data = objectMapper.readValue(cleaned, Map.class);
            report.setSummary((String) data.getOrDefault("summary", "评估报告生成中..."));
            report.setVerdict((String) data.getOrDefault("verdict", "待定"));

            if (data.get("dimensionScores") instanceof Map) {
                Map<String, Object> scores = (Map<String, Object>) data.get("dimensionScores");
                Map<String, Integer> parsed = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : scores.entrySet()) {
                    parsed.put(entry.getKey(), entry.getValue() instanceof Integer ? (Integer) entry.getValue() : 1);
                }
                report.setDimensionScores(parsed);
            }
        } catch (Exception e) {
            report.setSummary("评估结果解析失败，无法生成完整报告。");
            report.setVerdict("待定");
            report.setDimensionScores(Map.of(
                    "technical", 3, "communication", 3,
                    "problemSolving", 3, "culturalFit", 3
            ));
        }
        return report;
    }

    /**
     * 降级兜底报告
     */
    private EvaluationReport fallbackReport(InterviewSession session) {
        EvaluationReport report = new EvaluationReport();
        report.setReportId(UUID.randomUUID().toString());
        report.setSessionId(session.getSessionId());
        report.setCandidateId(session.getCandidateId());
        report.setCandidateName(session.getCandidateName());
        report.setDirection(session.getDirection());
        report.setLevel(session.getLevel());
        report.setMode(session.getMode());
        report.setTotalRounds(session.getCurrentRound());
        report.setOverallScore(40);
        report.setDimensionScores(Map.of(
                "technical", 3, "communication", 3,
                "problemSolving", 3, "culturalFit", 3
        ));
        report.setStrengths(List.of());
        report.setImprovements(List.of("回答内容过于简短，缺乏实质技术内容", "建议深入思考问题后再作答"));
        report.setSummary("候选人完成了本次模拟面试，但由于评估引擎评分数据不足，此报告为系统自动生成的参考报告。最终评分仅供参考。");
        report.setVerdict("待定");
        return report;
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) cleaned = cleaned.substring(firstNewline).trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }
}