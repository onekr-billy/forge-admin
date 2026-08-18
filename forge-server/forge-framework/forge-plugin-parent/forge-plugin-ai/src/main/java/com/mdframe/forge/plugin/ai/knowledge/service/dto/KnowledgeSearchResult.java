package com.mdframe.forge.plugin.ai.knowledge.service.dto;

import lombok.Data;

/**
 * 知识库检索结果
 */
@Data
public class KnowledgeSearchResult {

    private String chunkId;

    private Long documentId;

    private String docName;

    private Integer chunkIndex;

    private String content;

    private String title;

    /** 隐藏内容（不参与检索，命中后展示给 RAG 的补充详情） */
    private String hideContent;

    /** 来源标识（来源URL/文档名/库表行标识），RAG回答引用来源用 */
    private String sourceId;

    private double score;

    private double rerankScore;
}
