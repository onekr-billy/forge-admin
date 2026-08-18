package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledge;
import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeChunk;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeChunkMapper;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeMapper;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 最终化处理器。
 * 执行 Lost-in-Middle 重排 + Nearby 上下文扩展。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeHandler implements RagSearchHandler {

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeChunkMapper chunkMapper;

    @Override
    public String getName() {
        return "finalize";
    }

    @Override
    public void handle(RagSearchContext context) {
        List<KnowledgeSearchResult> results = context.getFinalResults();
        if (results == null) {
            // 如果 rerank 未执行，从 fusedResults 或 vectorResults 取
            results = context.getFusedResults() != null ? context.getFusedResults() : context.getVectorResults();
        }

        if (results == null || results.isEmpty()) {
            context.setFinalResults(List.of());
            return;
        }

        KnowledgeSearchRequest request = context.getRequest();

        // 请求字段优先，缺失时回退知识库检索配置（对齐独立入口 KnowledgeSearchService）
        Boolean lostInMiddle = request.getLostInMiddle();
        Integer nearbyCount = request.getNearbyCount();
        if ((lostInMiddle == null || nearbyCount == null) && request.getKnowledgeId() != null) {
            AiKnowledge knowledge = knowledgeMapper.selectById(request.getKnowledgeId());
            if (knowledge != null) {
                JSONObject cfg = parseSearchConfig(knowledge.getSearchConfigJson());
                if (lostInMiddle == null && cfg.containsKey("lost_in_middle")) {
                    lostInMiddle = cfg.getBoolean("lost_in_middle");
                }
                if (nearbyCount == null && cfg.containsKey("nearby_count")) {
                    nearbyCount = cfg.getInteger("nearby_count");
                }
            }
        }

        // 1. Lost-in-Middle 重排（可选）
        if (Boolean.TRUE.equals(lostInMiddle)) {
            results = lostInMiddleRerank(results);
            log.debug("[FinalizeHandler] Lost-in-Middle重排完成");
        }

        // 2. 先截断到 topK，再扩展 Nearby（避免"先扩展后被截断丢弃"的白干）
        Integer topK = request.getTopK();
        if (topK != null && topK > 0 && results.size() > topK) {
            results = results.subList(0, topK);
        }

        // 3. Nearby 上下文扩展（请求字段优先，否则用知识库配置，默认不扩展）
        if (nearbyCount != null && nearbyCount > 0) {
            results = expandNearby(results, nearbyCount);
        }

        // 4. 更新检索计数（管线内单一计数点，避免与 KnowledgeSearchService 重复计数）
        for (KnowledgeSearchResult r : results) {
            try {
                chunkMapper.incrementRetrievalCount(Long.parseLong(r.getChunkId()));
            } catch (Exception ignored) {
            }
        }

        context.setFinalResults(results);
    }

    /**
     * Lost-in-Middle 重排：将最相关和最不相关的放在开头和结尾，中等相关的放在中间。
     */
    private List<KnowledgeSearchResult> lostInMiddleRerank(List<KnowledgeSearchResult> results) {
        if (results.size() <= 2) return results;

        List<KnowledgeSearchResult> reranked = new ArrayList<>();
        int left = 0;
        int right = results.size() - 1;
        boolean takeLeft = true;

        while (left <= right) {
            if (takeLeft) {
                reranked.add(results.get(left++));
            } else {
                reranked.add(results.get(right--));
            }
            takeLeft = !takeLeft;
        }

        return reranked;
    }

    /**
     * Nearby 上下文扩展：为每个命中的分块追加前后相邻分块（按文档缓存，避免重复查库）
     */
    private List<KnowledgeSearchResult> expandNearby(List<KnowledgeSearchResult> results, int nearbyCount) {
        Set<String> seen = new HashSet<>();
        List<KnowledgeSearchResult> expanded = new ArrayList<>();
        Map<Long, List<AiKnowledgeChunk>> chunksByDoc = new HashMap<>();

        for (KnowledgeSearchResult result : results) {
            if (seen.add(result.getChunkId())) {
                expanded.add(result);
            }

            Long documentId = result.getDocumentId();
            if (documentId == null) continue;
            List<AiKnowledgeChunk> nearbyChunks = chunksByDoc.computeIfAbsent(documentId, id -> {
                List<AiKnowledgeChunk> list = chunkMapper.selectByDocumentId(id);
                return list != null ? list : List.of();
            });
            int chunkIndex = result.getChunkIndex();

            for (int offset = -nearbyCount; offset <= nearbyCount; offset++) {
                if (offset == 0) continue;
                int targetIndex = chunkIndex + offset;
                if (targetIndex < 0) continue;

                for (AiKnowledgeChunk chunk : nearbyChunks) {
                    if (chunk.getChunkIndex() == targetIndex) {
                        String key = String.valueOf(chunk.getId());
                        if (seen.add(key)) {
                            KnowledgeSearchResult nearby = new KnowledgeSearchResult();
                            nearby.setChunkId(key);
                            nearby.setDocumentId(chunk.getDocumentId());
                            nearby.setChunkIndex(chunk.getChunkIndex());
                            nearby.setContent(chunk.getContent());
                            nearby.setTitle(chunk.getTitle());
                            nearby.setScore(result.getScore() * 0.8);
                            expanded.add(nearby);
                        }
                    }
                }
            }
        }

        return expanded;
    }

    /**
     * 解析知识库检索配置 JSON（空/解析失败返回空配置）
     */
    private JSONObject parseSearchConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(configJson);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
