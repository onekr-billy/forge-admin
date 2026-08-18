package com.mdframe.forge.plugin.ai.rag.search;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import lombok.Data;

import java.util.List;

/**
 * 检索调试响应（仅调试端点使用，不改变 /ai/rag/search 现有结构）。
 * list 为最终检索结果，meta 为检索元信息供调试 UI 展示。
 */
@Data
public class SearchDebugResponse {

    /**
     * 最终检索结果（与 /ai/rag/search 返回结构一致）
     */
    private List<KnowledgeSearchResult> list;

    /**
     * 检索元信息
     */
    private Meta meta;

    @Data
    public static class Meta {

        /**
         * 实际执行的检索类型（vector / bm25 / hybrid / null=默认两路融合）
         */
        private String searchType;

        /**
         * 总耗时（毫秒）
         */
        private long elapsedMs;

        /**
         * 向量检索命中数
         */
        private int vectorCount;

        /**
         * BM25 检索命中数
         */
        private int bm25Count;

        /**
         * Milvus 原生混合检索命中数（searchType=hybrid 时）
         */
        private int hybridCount;

        /**
         * 融合后命中数
         */
        private int fusedCount;

        /**
         * 最终返回条数
         */
        private int finalCount;

        /**
         * 查询补全后的文本（未启用补全为 null）
         */
        private String expandedQuery;
    }
}
