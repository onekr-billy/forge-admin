package com.mdframe.forge.plugin.ai.knowledge.vectorstore;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.Data;

import java.util.List;

/**
 * 向量存储服务抽象接口。
 * 屏蔽不同向量数据库（Milvus/PgVector/ES）的底层差异。
 */
public interface VectorStoreService {

    /**
     * 创建集合（如不存在）
     *
     * @param request 创建请求
     */
    void createCollectionIfAbsent(CreateCollectionRequest request);

    /**
     * 插入向量
     *
     * @param request 插入请求
     * @return 每条记录的向量ID列表（与输入顺序一致）
     */
    List<String> insert(InsertRequest request);

    /**
     * 删除向量
     *
     * @param request 删除请求
     */
    void delete(DeleteRequest request);

    /**
     * 向量检索
     *
     * @param request 检索请求
     * @return 检索结果列表（按相似度降序）
     */
    List<SearchResult> search(SearchRequest request);

    /**
     * BM25 全文检索（稀疏向量）
     *
     * @param request 检索请求（query 为原始查询文本）
     * @return 检索结果列表（按相关度降序）
     */
    List<SearchResult> bm25Search(SearchRequest request);

    /**
     * 混合检索（稠密向量 + 标题BM25 + 正文BM25，Milvus 单次调用融合，对齐参考项目 54doctor_ai）
     *
     * @param request 检索请求（vector 为查询向量、query 为查询文本）
     * @return 检索结果列表
     */
    List<SearchResult> hybridSearch(SearchRequest request);

    /**
     * 搜索类型
     */
    enum SearchType {
        /** 稠密向量检索 */
        VECTOR,
        /** BM25 全文检索 */
        BM25,
        /** 混合检索（稠密 + 标题BM25 + 正文BM25） */
        HYBRID
    }

    /**
     * 测试连接
     *
     * @param configJson 连接配置JSON
     * @return 是否连接成功
     */
    boolean testConnection(String configJson);

    /**
     * 删除集合
     *
     * @param collectionName 集合名称
     * @param configJson     连接配置JSON
     */
    void dropCollection(String collectionName, String configJson);

    // ===== 请求/响应 DTO =====

    @Data
    class CreateCollectionRequest {
        private String collectionName;
        private int dimension;
        private String configJson;
        /** 集合已存在且 schema 版本过旧时，是否强制删除重建（默认 false：抛错提示手动处理） */
        private boolean forceRecreate;
    }

    @Data
    class InsertRequest {
        private String collectionName;
        private List<String> ids;
        private List<List<Float>> vectors;
        private List<String> contents;
        private List<Long> documentIds;
        private List<Integer> chunkIndices;
        /** 每行来源标识（与 ids 顺序一致）：URL导入存 source_url、文件导入存 doc_name、库表导入存表/行标识 */
        private List<String> sourceIds;
        /** 每行标题（参与 BM25 标题检索，可为 null） */
        private List<String> titles;
        /** 每行隐藏内容（不参与检索，仅存储供命中后展示，可为 null） */
        private List<String> hideContents;
        private String configJson;
    }

    @Data
    class DeleteRequest {
        private String collectionName;
        private List<String> ids;
        private String configJson;
    }

    @Data
    class SearchRequest {
        private String collectionName;
        /** 稠密查询向量（search/hybridSearch 用） */
        private List<Float> vector;
        /** BM25/混合检索的原始查询文本 */
        private String query;
        private int topK;
        private double threshold;
        private String configJson;
        /** 搜索类型（hybridSearch 走混合，其余方法可忽略） */
        private SearchType searchType;
        /** 过滤表达式（Milvus 表达式语法，可选，如 "sourceId == \"url1\""） */
        private String filterExpr;
        /** 向量检索权重（searchType=HYBRID 且 rerankType=weighted 时用） */
        private Double vectorWeight;
        /** BM25 检索权重（searchType=HYBRID 且 rerankType=weighted 时用，title/content 平分） */
        private Double bm25Weight;
        /** 融合类型（hybridSearch 用）：weighted=加权 / rrf=倒数排名融合，默认 rrf */
        private String rerankType;
        /** RRF 融合参数 k（rerankType=rrf 时用，默认60） */
        private Integer rrfK;
    }

    @Data
    class SearchResult {
        private String id;
        private double score;
        private String content;
        /** 标题（BM25 标题命中字段） */
        private String title;
        /** 隐藏内容（不参与检索，命中后展示给 RAG 的补充详情） */
        private String hideContent;
        private Long documentId;
        private Integer chunkIndex;
        /** 来源标识（来源URL/文档名/库表行标识），RAG回答引用来源用 */
        private String sourceId;
    }
}
