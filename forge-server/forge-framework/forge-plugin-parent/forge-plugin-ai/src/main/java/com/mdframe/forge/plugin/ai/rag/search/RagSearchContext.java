package com.mdframe.forge.plugin.ai.rag.search;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import lombok.Data;

import java.util.List;

/**
 * RAG 检索管道上下文。
 * 在各 Handler 之间传递中间结果。
 */
@Data
public class RagSearchContext {

    /**
     * 原始检索请求
     */
    private KnowledgeSearchRequest request;

    /**
     * 向量检索结果
     */
    private List<KnowledgeSearchResult> vectorResults;

    /**
     * BM25 检索结果
     */
    private List<KnowledgeSearchResult> bm25Results;

    /**
     * Milvus 原生混合检索结果（searchType=hybrid 时使用）
     */
    private List<KnowledgeSearchResult> hybridResults;

    /**
     * 融合后的结果
     */
    private List<KnowledgeSearchResult> fusedResults;

    /**
     * 最终结果（经过 rerank + finalize）
     */
    private List<KnowledgeSearchResult> finalResults;

    /**
     * 补全后的查询文本
     */
    private String expandedQuery;

    public RagSearchContext(KnowledgeSearchRequest request) {
        this.request = request;
    }
}
