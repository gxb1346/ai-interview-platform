package com.interview.modules.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.modules.resume.model.AnalysisResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AI 简历分析服务
 * 使用 Spring AI (通义千问 qwen3.5-flash) 对简历文本进行智能分析
 */
@Service
public class ResumeAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 分析简历文本，返回结构化评估结果
     *
     * @param rawText   简历纯文本
     * @param targetJob 目标岗位 (可选)
     * @return AI 分析结果
     */
    public AnalysisResult analyze(String rawText, String targetJob) {
        try {
            String prompt = buildPrompt(rawText, targetJob);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 清理可能的 Markdown 代码块包装
            String json = cleanJsonResponse(response);

            return objectMapper.readValue(json, AnalysisResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 简历分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 Prompt
     */
    private String buildPrompt(String resumeText, String targetJob) {
        String jobInfo = (targetJob != null && !targetJob.isBlank())
                ? "目标岗位：\"" + targetJob + "\""
                : "智能适配最佳岗位";

        return "你是一个资深的AI招聘专家与HR。请根据以下求职者的简历文本进行深度解析。\n"
                + "\n"
                + "要求：\n"
                + "1. 提取求职者姓名、适配岗位、工作年限、最高学历、邮箱、电话\n"
                + "2. 计算AI竞争力匹配度（0-100分）\n"
                + "3. 对五大能力指标分别打分（1-10分）：技术深度(technical)、沟通表达(communication)、解决问题(problemSolving)、团队契合(teamFit)、自驱动力(drive)\n"
                + "4. 列出3个核心优势\n"
                + "5. 列出2个弱项或改善建议\n"
                + "6. 列出3个闪光亮点\n"
                + "7. 撰写一段150-250字的AI综合评估报告\n"
                + "\n"
                + "请直接返回以下 JSON 格式（不要包含任何 Markdown 标记）：\n"
                + "\n"
                + "{\n"
                + "  \"name\": \"姓名\",\n"
                + "  \"role\": \"适配岗位\",\n"
                + "  \"experienceYears\": 5,\n"
                + "  \"education\": \"硕士\",\n"
                + "  \"matchScore\": 88,\n"
                + "  \"email\": \"xxx@example.com\",\n"
                + "  \"phone\": \"138-xxxx-xxxx\",\n"
                + "  \"competencies\": {\n"
                + "    \"technical\": 8,\n"
                + "    \"communication\": 7,\n"
                + "    \"problemSolving\": 8,\n"
                + "    \"teamFit\": 8,\n"
                + "    \"drive\": 9\n"
                + "  },\n"
                + "  \"strengths\": [\"优势1\", \"优势2\", \"优势3\"],\n"
                + "  \"weaknesses\": [\"弱项1\", \"弱项2\"],\n"
                + "  \"highlights\": [\"亮点1\", \"亮点2\", \"亮点3\"],\n"
                + "  \"aiSummary\": \"综合评估报告...\"\n"
                + "}\n"
                + "\n"
                + jobInfo + "\n\n简历内容：\n---\n" + resumeText + "\n---";
    }

    /**
     * 清理 AI 返回内容，去除可能的 Markdown ```json 包裹
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            throw new RuntimeException("AI 返回为空");
        }
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            // 移除 ```json 或 ``` 开头
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline).trim();
            }
            // 移除结尾的 ```
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        return cleaned;
    }
}
