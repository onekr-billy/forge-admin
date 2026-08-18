package com.mdframe.forge.plugin.ai.rag.search;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索管道。
 * 按 searchType 选择检索路径：
 *   null       默认：向量 + BM25 两路独立检索 + 应用层融合（向后兼容）
 *   vector     仅稠密向量检索
 *   bm25       仅 BM25 全文检索
 *   hybrid     Milvus 原生混合检索（稠密 + 标题BM25 + 正文BM25 单次调用融合，对齐参考项目 54doctor_ai）
 * 统一后处理：RerankHandler（对融合结果重排）+ FinalizeHandler（Lost-in-Middle/Nearby/截断/计数）。
 * 查询补全在请求副本上生效，不改动调用方入参。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchPipeline {

    private final VectorSearchHandler vectorSearchHandler;
    private final Bm25SearchHandler bm25SearchHandler;
    private final HybridSearchHandler hybridSearchHandler;
    private final HybridFusionHandler hybridFusionHandler;
    private final RerankHandler rerankHandler;
    private final FinalizeHandler finalizeHandler;
    private final QueryCompleter queryCompleter;

    /**
     * 执行检索管道
     *
     * @param request 检索请求
     * @return 检索结果
     */
    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        RagSearchContext context = run(request);
        return context.getFinalResults() != null ? context.getFinalResults() : new ArrayList<>();
    }

    /**
     * 执行检索管道并返回调试元信息（供检索调试 UI 使用，不改变 search() 的返回结构）。
     *
     * @param request 检索请求
     * @return 检索结果 + 元信息
     */
    public SearchDebugResponse searchWithDebug(KnowledgeSearchRequest request) {
        long start = System.nanoTime();
        RagSearchContext context = run(request);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        SearchDebugResponse.Meta meta = new SearchDebugResponse.Meta();
        meta.setSearchType(context.getRequest() != null ? context.getRequest().getSearchType() : null);
        meta.setElapsedMs(elapsedMs);
        meta.setVectorCount(sizeOf(context.getVectorResults()));
        meta.setBm25Count(sizeOf(context.getBm25Results()));
        meta.setHybridCount(sizeOf(context.getHybridResults()));
        meta.setFusedCount(sizeOf(context.getFusedResults()));
        meta.setFinalCount(sizeOf(context.getFinalResults()));
        meta.setExpandedQuery(context.getExpandedQuery());

        SearchDebugResponse response = new SearchDebugResponse();
        response.setMeta(meta);
        response.setList(context.getFinalResults() != null ? context.getFinalResults() : new ArrayList<>());
        return response;
    }

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * 管道核心：执行各 Handler 后返回上下文（search/searchWithDebug 共用）
     */
    private RagSearchContext run(KnowledgeSearchRequest request) {
        RagSearchContext context = new RagSearchContext(request);

        // 0. 查询补全（可选）：在请求副本上设置补全后的查询，不改动调用方入参
        if (Boolean.TRUE.equals(request.getQueryComplete())) {
            String expandedQuery = queryCompleter.expand(request.getQuery());
            if (expandedQuery != null && !expandedQuery.isBlank()) {
                KnowledgeSearchRequest effective = new KnowledgeSearchRequest();
                BeanUtils.copyProperties(request, effective);
                effective.setQuery(expandedQuery);
                context.setRequest(effective);
                context.setExpandedQuery(expandedQuery);
                log.debug("[RagSearchPipeline] 查询补全: {} -> {}", request.getQuery(), expandedQuery);
            }
        }

        // 1. 按搜索类型执行检索
        String searchType = context.getRequest().getSearchType();
        if (searchType != null && "vector".equalsIgnoreCase(searchType)) {
            vectorSearchHandler.handle(context);
            context.setFusedResults(context.getVectorResults());
        } else if (searchType != null && "bm25".equalsIgnoreCase(searchType)) {
            bm25SearchHandler.handle(context);
            context.setFusedResults(context.getBm25Results());
        } else if (searchType != null && "hybrid".equalsIgnoreCase(searchType)) {
            hybridSearchHandler.handle(context);
            context.setFusedResults(context.getHybridResults());
        } else {
            // 默认：向量 + BM25 两路独立检索 + 应用层融合
            vectorSearchHandler.handle(context);
            bm25SearchHandler.handle(context);
            if (context.getBm25Results() != null && !context.getBm25Results().isEmpty()) {
                hybridFusionHandler.handle(context);
            } else {
                context.setFusedResults(context.getVectorResults());
            }
        }

        // 2. Rerank（对融合结果重排一次）
        rerankHandler.handle(context);

        // 3. 最终化（Lost-in-Middle + Nearby + 截断 + 计数）
        finalizeHandler.handle(context);
        return context;
    }
}
