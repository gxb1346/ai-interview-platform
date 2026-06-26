package com.interview.modules.knowledge.service;

import java.util.List;

/**
 * RAG 问答结果
 * 包含 AI 回答和引用来源
 */
public record QAResult(
        String answer,
        List<SourceInfo> sources
) {
    /**
     * 引用来源信息
     */
    public record SourceInfo(
            String documentTitle,
            String fileName,
            String chunkContent,
            double score,
            int chunkIndex
    ) {}
}