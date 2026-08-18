package com.mdframe.forge.plugin.ai.rag.search.fusion;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;

import java.util.*;

/**
 * 加权求和融合。
 * score = α * vectorScore + (1 - α) * bm25Score
 */
public class WeightedSumFusion {

    private static final double DEFAULT_ALPHA = 0.7;

    /**
     * 对两路检索结果进行加权求和融合。
     *
     * @param vectorResults 向量检索结果
     * @param bm25Results   BM25 检索结果
     * @param alpha         向量检索权重（默认0.7）
     * @return 融合后的结果，按加权分数降序
     */
    public static List<KnowledgeSearchResult> fuse(
            List<KnowledgeSearchResult> vectorResults,
            List<KnowledgeSearchResult> bm25Results,
            double alpha) {

        if ((vectorResults == null || vectorResults.isEmpty())
                && (bm25Results == null || bm25Results.isEmpty())) {
            return List.of();
        }

        if (vectorResults == null || vectorResults.isEmpty()) {
            return bm25Results;
        }

        if (bm25Results == null || bm25Results.isEmpty()) {
            return vectorResults;
        }

        // 归一化分数到 [0, 1]
        Map<String, Double> normalizedVector = normalizeScores(vectorResults);
        Map<String, Double> normalizedBm25 = normalizeScores(bm25Results);

        // 合并所有 chunkId
        Set<String> allKeys = new LinkedHashSet<>();
        for (KnowledgeSearchResult r : vectorResults) allKeys.add(r.getChunkId());
        for (KnowledgeSearchResult r : bm25Results) allKeys.add(r.getChunkId());

        // 构建合并结果
        Map<String, KnowledgeSearchResult> merged = new LinkedHashMap<>();
        for (KnowledgeSearchResult r : vectorResults) {
            KnowledgeSearchResult copy = new KnowledgeSearchResult();
            copy.setChunkId(r.getChunkId());
            copy.setDocumentId(r.getDocumentId());
            copy.setDocName(r.getDocName());
            copy.setChunkIndex(r.getChunkIndex());
            copy.setContent(r.getContent());
            copy.setTitle(r.getTitle());
            copy.setHideContent(r.getHideContent());
            copy.setSourceId(r.getSourceId());
            merged.put(r.getChunkId(), copy);
        }
        for (KnowledgeSearchResult r : bm25Results) {
            if (!merged.containsKey(r.getChunkId())) {
                KnowledgeSearchResult copy = new KnowledgeSearchResult();
                copy.setChunkId(r.getChunkId());
                copy.setDocumentId(r.getDocumentId());
                copy.setDocName(r.getDocName());
                copy.setChunkIndex(r.getChunkIndex());
                copy.setContent(r.getContent());
                copy.setTitle(r.getTitle());
                copy.setHideContent(r.getHideContent());
                copy.setSourceId(r.getSourceId());
                merged.put(r.getChunkId(), copy);
            }
        }

        // 计算加权分数
        Map<String, Double> weightedScores = new HashMap<>();
        for (String key : allKeys) {
            double vs = normalizedVector.getOrDefault(key, 0.0);
            double bs = normalizedBm25.getOrDefault(key, 0.0);
            weightedScores.put(key, alpha * vs + (1 - alpha) * bs);
        }

        // 排序
        List<KnowledgeSearchResult> fused = new ArrayList<>(merged.values());
        fused.sort((a, b) -> Double.compare(
                weightedScores.getOrDefault(b.getChunkId(), 0.0),
                weightedScores.getOrDefault(a.getChunkId(), 0.0)));

        // 设置融合分数
        for (KnowledgeSearchResult r : fused) {
            r.setScore(weightedScores.getOrDefault(r.getChunkId(), 0.0));
        }

        return fused;
    }

    /**
     * 使用默认 alpha=0.7 的加权求和融合
     */
    public static List<KnowledgeSearchResult> fuse(
            List<KnowledgeSearchResult> vectorResults,
            List<KnowledgeSearchResult> bm25Results) {
        return fuse(vectorResults, bm25Results, DEFAULT_ALPHA);
    }

    /**
     * Min-Max 归一化分数到 [0, 1]
     */
    private static Map<String, Double> normalizeScores(List<KnowledgeSearchResult> results) {
        Map<String, Double> scores = new HashMap<>();
        if (results == null || results.isEmpty()) return scores;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (KnowledgeSearchResult r : results) {
            double s = r.getScore();
            min = Math.min(min, s);
            max = Math.max(max, s);
            scores.put(r.getChunkId(), s);
        }

        double range = max - min;
        if (range == 0) {
            // 所有分数相同
            for (String key : scores.keySet()) {
                scores.put(key, 1.0);
            }
        } else {
            for (Map.Entry<String, Double> entry : scores.entrySet()) {
                scores.put(entry.getKey(), (entry.getValue() - min) / range);
            }
        }

        return scores;
    }
}
