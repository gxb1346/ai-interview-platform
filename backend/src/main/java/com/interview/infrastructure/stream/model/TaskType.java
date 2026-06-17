package com.interview.infrastructure.stream.model;

/**
 * Redis Stream 任务类型枚举
 * 每种任务类型对应一个独立的处理逻辑
 */
public enum TaskType {

    /** 简历 AI 分析 */
    RESUME_ANALYSIS,

    /** 知识库文档向量化索引 */
    DOCUMENT_INDEX
}
