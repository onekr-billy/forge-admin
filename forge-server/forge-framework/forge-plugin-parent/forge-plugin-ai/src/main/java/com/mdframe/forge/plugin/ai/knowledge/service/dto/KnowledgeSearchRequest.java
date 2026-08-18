package com.mdframe.forge.plugin.ai.knowledge.service.dto;

import lombok.Data;

/**
 * 知识库检索请求
 */
@Data
public class KnowledgeSearchRequest {

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 查询文本
     */
    private String query;

    /**
     * 返回数量（默认5）
     */
    private Integer topK;

    /**
     * 相似度阈值（默认0.5）
     */
    private Double threshold;

    /**
     * 是否启用 Rerank（统一入口，管线的 RerankHandler 与独立检索共用）
     */
    private Boolean rerankEnable;

    /**
     * 是否启用 Lost-in-Middle 重排
     */
    private Boolean lostInMiddle;

    /**
     * 融合策略（rrf/weighted_sum，默认 rrf）
     */
    private String fusionStrategy;

    /**
     * 搜索类型：vector / bm25 / hybrid（hybrid 走 Milvus 原生混合检索单次调用）；null 走"向量+BM25 两路+应用层融合"（默认）
     */
    private String searchType;

    /**
     * 过滤表达式（Milvus 表达式语法，可选，如 sourceId == "url1"）
     */
    private String filterExpr;

    /**
     * Nearby 上下文扩展数量（每命中分块前后各取 N 个相邻分块，默认不扩展）
     */
    private Integer nearbyCount;

    /**
     * 是否启用查询补全
     */
    private Boolean queryComplete;
}
