package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiStoreInstance;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeMapper;
import com.mdframe.forge.plugin.ai.knowledge.service.AiStoreInstanceService;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.knowledge.vectorstore.VectorStoreFactory;
import com.mdframe.forge.plugin.ai.knowledge.vectorstore.VectorStoreService;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BM25 检索处理器。
 * 调用 Milvus 稀疏向量全文检索（schema 中声明的 BM25 Function），结果喂给混合融合。
 * 未配置向量存储实例或检索失败时降级为空结果（由融合逻辑回退到纯向量检索）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Bm25SearchHandler implements RagSearchHandler {

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiStoreInstanceService storeInstanceService;
    private final VectorStoreFactory vectorStoreFactory;

    @Override
    public String getName() {
        return "bm25_search";
    }

    @Override
    public void handle(RagSearchContext context) {
        KnowledgeSearchRequest request = context.getRequest();
        AiKnowledge knowledge = knowledgeMapper.selectById(request.getKnowledgeId());
        if (knowledge == null) {
            log.warn("[Bm25SearchHandler] 知识库不存在，跳过BM25: knowledgeId={}", request.getKnowledgeId());
            context.setBm25Results(new ArrayList<>());
            return;
        }

        // 未配置向量存储实例时无法检索，返回空（由融合逻辑回退纯向量）
        if (knowledge.getVectorStoreInstanceId() == null) {
            log.debug("[Bm25SearchHandler] 知识库未配置向量存储实例，跳过BM25: knowledgeId={}", knowledge.getId());
            context.setBm25Results(new ArrayList<>());
            return;
        }
        AiStoreInstance storeInstance = storeInstanceService.getById(knowledge.getVectorStoreInstanceId());
        if (storeInstance == null) {
            log.warn("[Bm25SearchHandler] 向量存储实例不存在，跳过BM25: instanceId={}", knowledge.getVectorStoreInstanceId());
            context.setBm25Results(new ArrayList<>());
            return;
        }

        VectorStoreService.SearchRequest searchReq = new VectorStoreService.SearchRequest();
        searchReq.setCollectionName("knowledge_" + knowledge.getId());
        searchReq.setQuery(request.getQuery());
        searchReq.setTopK(request.getTopK() != null ? request.getTopK() : 5);
        // BM25 分数与向量相似度尺度不同，默认不过滤，避免把所有结果滤掉
        searchReq.setThreshold(request.getThreshold() != null ? request.getThreshold() : 0.0);
        searchReq.setFilterExpr(request.getFilterExpr());
        searchReq.setConfigJson(storeInstance.getConfigJson());

        try {
            List<VectorStoreService.SearchResult> vectorResults =
                    vectorStoreFactory.getService(storeInstance).bm25Search(searchReq);
            List<KnowledgeSearchResult> results = vectorResults.stream()
                    .map(sr -> {
                        KnowledgeSearchResult r = new KnowledgeSearchResult();
                        r.setChunkId(sr.getId());
                        r.setDocumentId(sr.getDocumentId());
                        r.setChunkIndex(sr.getChunkIndex());
                        r.setContent(sr.getContent());
                        r.setTitle(sr.getTitle());
                        r.setHideContent(sr.getHideContent());
                        r.setSourceId(sr.getSourceId());
                        r.setScore(sr.getScore());
                        return r;
                    })
                    .toList();
            log.debug("[Bm25SearchHandler] BM25检索完成, query={}, results={}", request.getQuery(), results.size());
            context.setBm25Results(results);
        } catch (Exception e) {
            log.warn("[Bm25SearchHandler] BM25检索失败，降级为纯向量检索: {}", e.getMessage());
            context.setBm25Results(new ArrayList<>());
        }
    }
}
