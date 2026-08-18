package com.mdframe.forge.plugin.ai.knowledge.vectorstore;

import com.alibaba.fastjson2.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mdframe.forge.starter.core.exception.BusinessException;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Milvus 向量存储服务实现。
 * 直接使用 Milvus SDK v2，不依赖 Spring AI MilvusVectorStore。
 */
@Slf4j
@Component
public class MilvusVectorStoreService implements VectorStoreService {

    /** 集合 schema 版本（存于集合 properties，用于迁移检测） */
    private static final String SCHEMA_VERSION = "2";
    private static final String SCHEMA_VERSION_KEY = "forge_schema_version";

    @Override
    public void createCollectionIfAbsent(CreateCollectionRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            String collectionName = request.getCollectionName();
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            Boolean exists = client.hasCollection(hasReq);
            if (Boolean.TRUE.equals(exists)) {
                // 迁移保护：旧 schema 集合缺少 BM25 字段，insert 字段会掉进 $meta、检索 outFields 报错，必须重建
                String version = readSchemaVersion(client, collectionName);
                if (SCHEMA_VERSION.equals(version)) {
                    // 维度校验：集合向量维度与当前 Embedding 模型输出不一致时（如旧集合按错误默认值 1536 建立，
                    // 而 text-embedding-v3 输出 1024），插入会因维度不符失败，须删除重建。
                    Integer existingDim = readVectorDimension(client, collectionName);
                    if (existingDim != null && !existingDim.equals(request.getDimension())) {
                        if (request.isForceRecreate()) {
                            log.warn("[Milvus] 集合维度不匹配(现有 {} vs 请求 {})，forceRecreate=true 删除重建: {}",
                                    existingDim, request.getDimension(), collectionName);
                            client.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
                        } else {
                            throw new BusinessException("集合 " + collectionName + " 的向量维度与当前 Embedding 模型输出不一致"
                                    + "(集合=" + existingDim + ", 模型=" + request.getDimension() + ")，"
                                    + "请删除该集合重建，或在创建请求中开启 forceRecreate。");
                        }
                    } else {
                        log.info("[Milvus] 集合已存在且 schema/维度最新: {}", collectionName);
                        return;
                    }
                } else if (request.isForceRecreate()) {
                    log.warn("[Milvus] 集合 schema 版本过旧({})，forceRecreate=true 删除重建: {}", version, collectionName);
                    client.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
                } else {
                    throw new BusinessException("集合 " + collectionName + " 的 schema 版本过旧（"
                            + version + "，当前需要 " + SCHEMA_VERSION + "），缺少 BM25 全文检索字段，检索/导入将失败。"
                            + "请删除该集合重建，或在创建请求中开启 forceRecreate。");
                }
            }

            // 显式 schema（BM25 全文检索需要 schema 级 Function，快速创建模式不支持）。
            // 字段设计对齐参考项目 54doctor_ai：
            //   id             VARCHAR 主键（字符串向量ID：doc_<documentId>_chunk_<index>）
            //   vector         稠密向量（向量检索）
            //   title          VARCHAR 标题（开 analyzer，BM25 标题检索 title_sparse）
            //   content        VARCHAR 正文（开 analyzer，BM25 正文检索 content_sparse）
            //   hideContent    VARCHAR 隐藏内容（不开 analyzer，不参与任何检索，仅存储命中后展示）
            //   source_id      VARCHAR 来源标识（URL/文档名/库表行标识，可被过滤表达式检索）
            //   content_sparse 稀疏向量（content → BM25 Function 自动生成，不手工写入）
            //   title_sparse   稀疏向量（title → BM25 Function 自动生成，不手工写入）
            //   $meta          隐藏动态字段，由 enableDynamicField(true) 自动创建（document_id/chunk_index 走它）
            // 注意：addField/addFunction 是 CollectionSchema 的实例方法，不能链在 builder() 上
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .enableDynamicField(true)
                    .build();
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .maxLength(256)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("vector")
                    .dataType(DataType.FloatVector)
                    .dimension(request.getDimension())
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("title")
                    .dataType(DataType.VarChar)
                    .maxLength(1024)
                    .enableAnalyzer(true)
                    .analyzerParams(Map.of("type", "standard"))
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("content")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .enableAnalyzer(true)
                    .analyzerParams(Map.of("type", "standard"))
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("hideContent")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("source_id")
                    .dataType(DataType.VarChar)
                    .maxLength(1000)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("content_sparse")
                    .dataType(DataType.SparseFloatVector)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("title_sparse")
                    .dataType(DataType.SparseFloatVector)
                    .build());
            schema.addFunction(CreateCollectionReq.Function.builder()
                    .name("content_bm25_emb")
                    .functionType(FunctionType.BM25)
                    .inputFieldNames(List.of("content"))
                    .outputFieldNames(List.of("content_sparse"))
                    .build());
            schema.addFunction(CreateCollectionReq.Function.builder()
                    .name("title_bm25_emb")
                    .functionType(FunctionType.BM25)
                    .inputFieldNames(List.of("title"))
                    .outputFieldNames(List.of("title_sparse"))
                    .build());

            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .property(SCHEMA_VERSION_KEY, SCHEMA_VERSION)
                    .indexParams(List.of(
                            // 稠密向量索引（向量检索用）
                            IndexParam.builder()
                                    .fieldName("vector")
                                    .indexType(IndexParam.IndexType.AUTOINDEX)
                                    .metricType(IndexParam.MetricType.COSINE)
                                    .build(),
                            // 正文 BM25 稀疏向量索引（全文检索用）
                            IndexParam.builder()
                                    .fieldName("content_sparse")
                                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                    .metricType(IndexParam.MetricType.BM25)
                                    .extraParams(Map.of("drop_ratio_search", 0.2))
                                    .build(),
                            // 标题 BM25 稀疏向量索引（标题命中加分用）
                            IndexParam.builder()
                                    .fieldName("title_sparse")
                                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                    .metricType(IndexParam.MetricType.BM25)
                                    .extraParams(Map.of("drop_ratio_search", 0.2))
                                    .build(),
                            // 标量字段索引（过滤表达式过滤 title/source_id 用，对齐参考项目）
                            IndexParam.builder()
                                    .fieldName("title")
                                    .indexType(IndexParam.IndexType.AUTOINDEX)
                                    .build(),
                            IndexParam.builder()
                                    .fieldName("source_id")
                                    .indexType(IndexParam.IndexType.AUTOINDEX)
                                    .build()
                    ))
                    .build();
            client.createCollection(createReq);
            // 显式加载到内存，否则检索报"collection not loaded"（对齐参考项目）
            client.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
            log.info("[Milvus] 集合创建成功: {}, dimension={}", collectionName, request.getDimension());
        } catch (Exception e) {
            log.error("[Milvus] 创建集合失败: {}", request.getCollectionName(), e);
            throw new BusinessException("Milvus创建集合失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public List<String> insert(InsertRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            List<JsonObject> rows = new ArrayList<>(request.getIds().size());
            for (int i = 0; i < request.getIds().size(); i++) {
                JsonObject row = new JsonObject();
                row.addProperty("id", request.getIds().get(i));
                JsonArray vectorArr = new JsonArray();
                for (Float v : request.getVectors().get(i)) {
                    vectorArr.add(v);
                }
                row.add("vector", vectorArr);
                row.addProperty("title", getOrEmpty(request.getTitles(), i));
                row.addProperty("content", request.getContents().get(i));
                row.addProperty("hideContent", getOrEmpty(request.getHideContents(), i));
                row.addProperty("document_id", request.getDocumentIds().get(i));
                row.addProperty("chunk_index", request.getChunkIndices().get(i));
                row.addProperty("source_id", getOrEmpty(request.getSourceIds(), i));
                rows.add(row);
            }

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(request.getCollectionName())
                    .data(rows)
                    .build();
            client.insert(insertReq);
            log.info("[Milvus] 插入成功: collection={}, count={}", request.getCollectionName(), rows.size());
            return request.getIds();
        } catch (Exception e) {
            log.error("[Milvus] 插入失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus插入失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public void delete(DeleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return;
        }
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            String filter = "id in [" + request.getIds().stream()
                    .map(id -> "\"" + id + "\"")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("") + "]";
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(request.getCollectionName())
                    .filter(filter)
                    .build();
            client.delete(deleteReq);
            log.info("[Milvus] 删除成功: collection={}, count={}", request.getCollectionName(), request.getIds().size());
        } catch (Exception e) {
            log.error("[Milvus] 删除失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus删除失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    /** 检索结果统一输出的字段 */
    private static final List<String> OUT_FIELDS =
            List.of("title", "content", "hideContent", "document_id", "chunk_index", "source_id");

    @Override
    public List<SearchResult> search(SearchRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            // 集合按知识库隔离（knowledge_<id>），无需 knowledge_id 过滤；
            // 集合同时含 vector(稠密) 与 content_sparse(稀疏) 两个向量字段，显式指定 annsField=vector
            SearchReq.SearchReqBuilder reqBuilder = SearchReq.builder()
                    .collectionName(request.getCollectionName())
                    .annsField("vector")
                    .data(List.of(new FloatVec(request.getVector())))
                    .topK(request.getTopK())
                    .outputFields(OUT_FIELDS);
            if (request.getFilterExpr() != null && !request.getFilterExpr().isBlank()) {
                reqBuilder.filter(request.getFilterExpr());
            }

            SearchResp searchResp = client.search(reqBuilder.build());
            return mapSearchResults(searchResp, request.getThreshold());
        } catch (Exception e) {
            log.error("[Milvus] 检索失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus检索失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public List<SearchResult> bm25Search(SearchRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            // BM25 全文检索：对 title_sparse（标题命中）与 content_sparse（正文命中）双路检索，RRF 融合。
            // 标题命中是强信号：同一文档标题含查询词时融合后显著靠前，等价于"隐藏内容参与提高相关性"。
            List<BaseVector> queryVectors = List.of(new EmbeddedText(request.getQuery()));
            List<AnnSearchReq> searchRequests = new ArrayList<>();
            searchRequests.add(buildAnnSearchReq("title_sparse", queryVectors, request));
            searchRequests.add(buildAnnSearchReq("content_sparse", queryVectors, request));

            int rrfK = request.getRrfK() != null && request.getRrfK() > 0 ? request.getRrfK() : 60;
            HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                    .collectionName(request.getCollectionName())
                    .searchRequests(searchRequests)
                    .ranker(new RRFRanker(rrfK))
                    .topK(request.getTopK())
                    .outFields(OUT_FIELDS)
                    .build();

            SearchResp searchResp = client.hybridSearch(hybridSearchReq);
            return mapSearchResults(searchResp, request.getThreshold());
        } catch (Exception e) {
            log.error("[Milvus] BM25检索失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus BM25检索失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    @Override
    public List<SearchResult> hybridSearch(SearchRequest request) {
        MilvusClientV2 client = null;
        try {
            client = createClient(request.getConfigJson());
            // 混合检索（对齐参考项目 54doctor_ai）：稠密向量 + 标题BM25 + 正文BM25 单次调用融合。
            // 融合类型：rerankType=weighted 用 WeightedRanker[vector, bm25/2, bm25/2]（BM25权重title/content平分）；
            // 否则用 RRFRanker。权重/阈值不参与 RRF/加权后的分数过滤（尺度与余弦相似度不同）。
            List<BaseVector> queryVectors = List.of(new EmbeddedText(request.getQuery()));
            List<AnnSearchReq> searchRequests = new ArrayList<>();
            AnnSearchReq.AnnSearchReqBuilder denseBuilder = AnnSearchReq.builder()
                    .vectorFieldName("vector")
                    .vectors(List.of(new FloatVec(request.getVector())))
                    .topK(request.getTopK());
            if (request.getFilterExpr() != null && !request.getFilterExpr().isBlank()) {
                denseBuilder.expr(request.getFilterExpr());
            }
            searchRequests.add(denseBuilder.build());
            searchRequests.add(buildAnnSearchReq("title_sparse", queryVectors, request));
            searchRequests.add(buildAnnSearchReq("content_sparse", queryVectors, request));

            HybridSearchReq.HybridSearchReqBuilder hybridBuilder = HybridSearchReq.builder()
                    .collectionName(request.getCollectionName())
                    .searchRequests(searchRequests)
                    .topK(request.getTopK())
                    .outFields(OUT_FIELDS);
            if (request.getRerankType() != null && "weighted".equalsIgnoreCase(request.getRerankType())) {
                // 权重对齐参考项目：weights[0]=向量权重, [1]=title BM25权重, [2]=content BM25权重（平分）
                float vw = request.getVectorWeight() != null ? request.getVectorWeight().floatValue() : 1.0f;
                float bw = request.getBm25Weight() != null ? request.getBm25Weight().floatValue() / 2 : 0.5f;
                hybridBuilder.ranker(new WeightedRanker(List.of(vw, bw, bw)));
            } else {
                int rrfK = request.getRrfK() != null && request.getRrfK() > 0 ? request.getRrfK() : 60;
                hybridBuilder.ranker(new RRFRanker(rrfK));
            }

            SearchResp searchResp = client.hybridSearch(hybridBuilder.build());
            return mapSearchResults(searchResp, 0.0);
        } catch (Exception e) {
            log.error("[Milvus] 混合检索失败: collection={}", request.getCollectionName(), e);
            throw new BusinessException("Milvus混合检索失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    /**
     * 构建 BM25 稀疏向量 AnnSearchReq（EmbeddedText 自动经 BM25 Function 转稀疏向量）。
     * 注：2.5.8 的 AnnSearchReq 过滤表达式方法是 expr()（filter() 是 2.6.x 才有）。
     */
    private AnnSearchReq buildAnnSearchReq(String fieldName, List<BaseVector> queryVectors, SearchRequest request) {
        AnnSearchReq.AnnSearchReqBuilder builder = AnnSearchReq.builder()
                .vectorFieldName(fieldName)
                .vectors(queryVectors)
                .topK(request.getTopK());
        if (request.getFilterExpr() != null && !request.getFilterExpr().isBlank()) {
            builder.expr(request.getFilterExpr());
        }
        return builder.build();
    }

    /**
     * 将 Milvus 检索结果映射为统一的 SearchResult 列表
     */
    private List<SearchResult> mapSearchResults(SearchResp searchResp, double threshold) {
        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        List<SearchResult> searchResults = new ArrayList<>();
        if (results != null && !results.isEmpty()) {
            for (SearchResp.SearchResult hit : results.get(0)) {
                double score = hit.getScore();
                if (score < threshold) {
                    continue;
                }
                SearchResult sr = new SearchResult();
                sr.setId(hit.getId().toString());
                sr.setScore(score);
                sr.setContent((String) hit.getEntity().get("content"));
                Object titleObj = hit.getEntity().get("title");
                sr.setTitle(titleObj != null ? titleObj.toString() : null);
                Object hideContentObj = hit.getEntity().get("hideContent");
                sr.setHideContent(hideContentObj != null ? hideContentObj.toString() : null);
                Object docIdObj = hit.getEntity().get("document_id");
                sr.setDocumentId(toLong(docIdObj));
                Object chunkIdxObj = hit.getEntity().get("chunk_index");
                sr.setChunkIndex(toInt(chunkIdxObj));
                Object sourceIdObj = hit.getEntity().get("source_id");
                sr.setSourceId(sourceIdObj != null ? sourceIdObj.toString() : null);
                searchResults.add(sr);
            }
        }
        return searchResults;
    }

    /**
     * 取列表第 index 个元素，null/越界一律返回空串（Milvus 非空字段不接受 JSON null）
     */
    private static String getOrEmpty(List<String> list, int index) {
        if (list == null || index >= list.size()) {
            return "";
        }
        String v = list.get(index);
        return v != null ? v : "";
    }

    /**
     * Milvus 返回的数值可能是 Long/Integer/Double，统一安全转为 Long
     */
    private static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.longValue();
        return Long.parseLong(obj.toString());
    }

    /**
     * Milvus 返回的数值可能是 Long/Integer/Double，统一安全转为 Integer
     */
    private static Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.intValue();
        return Integer.parseInt(obj.toString());
    }

    @Override
    public boolean testConnection(String configJson) {
        MilvusClientV2 client = null;
        try {
            client = createClient(configJson);
            client.listCollections();
            // 注：BM25 全文检索要求 Milvus >= 2.5.0（BM25 Function 是 2.5.0 新增），
            // 但连接测试不做版本校验——对齐参考项目 54doctor_ai；版本不满足时
            // createCollection 建 BM25 schema 会失败并抛 BusinessException，天然兜底。
            return true;
        } catch (Exception e) {
            log.warn("[Milvus] 连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            closeClient(client);
        }
    }

    @Override
    public void dropCollection(String collectionName, String configJson) {
        MilvusClientV2 client = null;
        try {
            client = createClient(configJson);
            DropCollectionReq req = DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            client.dropCollection(req);
            log.info("[Milvus] 集合已删除: {}", collectionName);
        } catch (Exception e) {
            log.error("[Milvus] 删除集合失败: {}", collectionName, e);
            throw new BusinessException("Milvus删除集合失败: " + e.getMessage());
        } finally {
            closeClient(client);
        }
    }

    // ===== 内部方法 =====

    private MilvusClientV2 createClient(String configJson) {
        MilvusConfig config = parseConfig(configJson);
        String host = config.getHost();
        // Milvus SDK 要求完整 URI（默认补 http:// 前缀）；兼容用户已填 http(s):// 前缀的情况，避免重复拼接
        if (host != null && !host.contains("://")) {
            host = "http://" + host;
        }
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(host + ":" + config.getPort());
        if (config.getToken() != null && !config.getToken().isEmpty()) {
            builder.token(config.getToken());
        }
        if (config.getUser() != null && !config.getUser().isEmpty()) {
            builder.username(config.getUser());
        }
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            builder.password(config.getPassword());
        }
        if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
            builder.dbName(config.getDatabase());
        }
        return new MilvusClientV2(builder.build());
    }

    private void closeClient(MilvusClientV2 client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 读取集合的 schema 版本（集合 properties 中存储），读取失败视为旧版本
     */
    private String readSchemaVersion(MilvusClientV2 client, String collectionName) {
        try {
            DescribeCollectionResp resp = client.describeCollection(
                    DescribeCollectionReq.builder().collectionName(collectionName).build());
            Map<String, String> props = resp.getProperties();
            String version = props != null ? props.get(SCHEMA_VERSION_KEY) : null;
            return version != null ? version : "legacy";
        } catch (Exception e) {
            log.warn("[Milvus] 读取集合 schema 版本失败, 视为旧版本: collection={}, err={}",
                    collectionName, e.getMessage());
            return "legacy";
        }
    }

    /**
     * 读取集合稠密向量字段的维度，读取失败返回 null
     */
    private Integer readVectorDimension(MilvusClientV2 client, String collectionName) {
        try {
            DescribeCollectionResp resp = client.describeCollection(
                    DescribeCollectionReq.builder().collectionName(collectionName).build());
            if (resp.getCollectionSchema() == null) {
                return null;
            }
            for (var field : resp.getCollectionSchema().getFieldSchemaList()) {
                if (field.getDataType() == DataType.FloatVector) {
                    return field.getDimension();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[Milvus] 读取集合向量维度失败: collection={}, err={}", collectionName, e.getMessage());
            return null;
        }
    }

    private MilvusConfig parseConfig(String configJson) {
        try {
            return JSON.parseObject(configJson, MilvusConfig.class);
        } catch (Exception e) {
            throw new BusinessException("Milvus配置JSON解析失败: " + e.getMessage());
        }
    }

    @Data
    static class MilvusConfig {
        private String host;
        private Integer port;
        private String user;
        private String password;
        private String token;
        private String database;
    }
}
