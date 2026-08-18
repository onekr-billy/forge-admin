package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.mdframe.forge.plugin.ai.knowledge.service.KnowledgeSearchService;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Milvus 原生混合检索处理器（searchType=hybrid 时启用）。
 * 稠密向量 + 标题BM25 + 正文BM25 在 Milvus 单次调用内融合（WeightedRanker/RRF），
 * 权重与融合类型从知识库检索配置读取，对齐参考项目 54doctor_ai。
 */
@Component
@RequiredArgsConstructor
public class HybridSearchHandler implements RagSearchHandler {

    private final KnowledgeSearchService knowledgeSearchService;

    @Override
    public String getName() {
        return "hybrid_search";
    }

    @Override
    public void handle(RagSearchContext context) {
        List<KnowledgeSearchResult> results = knowledgeSearchService.hybridSearch(context.getRequest());
        context.setHybridResults(results);
    }
}
