package com.mdframe.forge.plugin.ai.rag.search.fusion;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF)。
 * score = Σ 1/(k + rank)，k=60
 */
public class RrfFusion {

    private static final int DEFAULT_K = 60;

    /**
     * 对多路检索结果进行 RRF 融合。
     *
     * @param resultLists 多路检索结果列表
     * @param k           RRF 参数（默认60）
     * @return 融合后的结果，按 RRF 分数降序
     */
    public static List<KnowledgeSearchResult> fuse(List<List<KnowledgeSearchResult>> resultLists, int k) {
        if (resultLists == null || resultLists.isEmpty()) {
            return List.of();
        }

        // chunkId -> 聚合结果
        Map<String, KnowledgeSearchResult> merged = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();

        for (List<KnowledgeSearchResult> results : resultLists) {
            for (int rank = 0; rank < results.size(); rank++) {
                KnowledgeSearchResult r = results.get(rank);
                String key = r.getChunkId();
                double rrfScore = 1.0 / (k + rank + 1); // rank从0开始，+1转为1-based

                scores.merge(key, rrfScore, Double::sum);

                if (!merged.containsKey(key)) {
                    KnowledgeSearchResult copy = new KnowledgeSearchResult();
                    copy.setChunkId(r.getChunkId());
                    copy.setDocumentId(r.getDocumentId());
                    copy.setDocName(r.getDocName());
                    copy.setChunkIndex(r.getChunkIndex());
                    copy.setContent(r.getContent());
                    copy.setTitle(r.getTitle());
                    copy.setHideContent(r.getHideContent());
                    copy.setSourceId(r.getSourceId());
                    merged.put(key, copy);
                }
            }
        }

        // 按 RRF 分数降序排序
        List<KnowledgeSearchResult> fused = new ArrayList<>(merged.values());
        fused.sort((a, b) -> Double.compare(
                scores.getOrDefault(b.getChunkId(), 0.0),
                scores.getOrDefault(a.getChunkId(), 0.0)));

        // 设置融合分数
        for (KnowledgeSearchResult r : fused) {
            r.setScore(scores.getOrDefault(r.getChunkId(), 0.0));
        }

        return fused;
    }

    /**
     * 使用默认 k=60 的 RRF 融合
     */
    public static List<KnowledgeSearchResult> fuse(List<List<KnowledgeSearchResult>> resultLists) {
        return fuse(resultLists, DEFAULT_K);
    }
}
