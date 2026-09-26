package com.mdframe.forge.plugin.generator.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.dto.CustomQueryExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditCaptureService;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 动态 CRUD 的分页、导出、树、自定义、详情、打印与批量读取协调器。 */
@Slf4j
final class DynamicCrudReadCoordinator {

    private static final int MAX_EXPORT_ROWS = 10000;
    private static final Set<String> SUPPORTED_SEARCH_TYPES = Set.of(
            "eq", "ne", "like", "left_like", "right_like", "gt", "ge", "gte", "lt", "le", "lte",
            "in", "between", "is_null", "is_not_null"
    );

    private record ExportQueryContext(
            AiCrudConfig config,
            String tableName,
            Map<String, String> columnMapping,
            Set<String> allowedSearchFields,
            Map<String, String> searchTypeMap,
            Map<String, Object> searchParams,
            RuntimeJoinContext joinContext) {
    }

    private final DynamicCrudRepository repository;
    private final AiCrudConfigService configService;
    private final ObjectMapper objectMapper;
    private final DynamicDataScopeService dynamicDataScopeService;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;
    private final DataAuditCaptureService dataAuditCaptureService;
    private final DynamicCrudTreeQueryEngine treeQueryEngine;
    private final DynamicCrudFieldValuePipeline fieldValuePipeline;
    private final DynamicCrudRuntimeRelationPlanner runtimeRelationPlanner;
    private final DynamicCrudWriteFieldPolicy writeFieldPolicy;
    private final DynamicCrudMasterDetailEngine masterDetailEngine;

    DynamicCrudReadCoordinator(
            DynamicCrudRepository repository,
            AiCrudConfigService configService,
            ObjectMapper objectMapper,
            DynamicDataScopeService dynamicDataScopeService,
            LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver,
            DataAuditCaptureService dataAuditCaptureService,
            DynamicCrudTreeQueryEngine treeQueryEngine,
            DynamicCrudFieldValuePipeline fieldValuePipeline,
            DynamicCrudRuntimeRelationPlanner runtimeRelationPlanner,
            DynamicCrudWriteFieldPolicy writeFieldPolicy,
            DynamicCrudMasterDetailEngine masterDetailEngine) {
        this.repository = repository;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.dynamicDataScopeService = dynamicDataScopeService;
        this.runtimeDataSourceResolver = runtimeDataSourceResolver;
        this.dataAuditCaptureService = dataAuditCaptureService;
        this.treeQueryEngine = treeQueryEngine;
        this.fieldValuePipeline = fieldValuePipeline;
        this.runtimeRelationPlanner = runtimeRelationPlanner;
        this.writeFieldPolicy = writeFieldPolicy;
        this.masterDetailEngine = masterDetailEngine;
    }

    // ==================== 查询操作 ====================

    /**
     * 分页查询
     */
    public Page<Map<String, Object>> selectPage(String configKey, PageQuery pageQuery, DynamicCrudQuery query) {
        // 1. 加载配置
        AiCrudConfig config = getConfig(configKey);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        String tableName = config.getTableName();
        
        // 2. 获取字段映射
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        
        // 3. 解析搜索配置
        Set<String> allowedSearchFields = buildAllowedSearchFields(config);
        Map<String, String> searchTypeMap = buildEffectiveSearchTypeMap(config, query, allowedSearchFields);
        
        // 4. 构建搜索条件
        Map<String, Object> searchParams = (query != null) ? query.getSearchParams() : null;
        searchParams = treeQueryEngine.expandIncludeChildrenParams(
                searchParams, config, tableName, allowedSearchFields, searchTypeMap);
        treeQueryEngine.coerceMultiValueSearchTypes(searchParams, searchTypeMap);

        // 4.1 将显式传入的 searchParams 字段扩展为允许搜索字段（支持选择器弹窗过滤等场景）
        expandAllowedSearchFieldsFromParams(searchParams, allowedSearchFields, searchTypeMap, columnMapping);

        RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
        if (joinContext != null && requiresJoinedPageQuery(config, pageQuery, searchParams, joinContext)) {
            DynamicCrudRepository.SqlCondition dataScopeCondition = buildDataScopeCondition(config, tableName, "t0");
            boolean aggregateChildren = aggregateChildListRows(config);
            Page<Map<String, Object>> page = repository.selectJoinedPage(
                    tableName,
                    withExpandedChildKeys(buildRuntimeSelectFields(joinContext,
                            DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper), true),
                            joinContext, aggregateChildren),
                    joinContext.joins(),
                    pageQuery.getPageNum(),
                    pageQuery.getPageSize(),
                    searchParams,
                    allowedSearchFields,
                    searchTypeMap,
                    joinContext.fieldColumnMapping(),
                    buildJoinOrderBy(pageQuery.getOrderByColumn(), pageQuery.getIsAsc(), joinContext),
                    dataScopeCondition,
                    aggregateChildren
            );
            applyReadPipeline(page.getRecords(), config);
            stampExpandedListRowKeys(page.getRecords(), joinContext, aggregateChildren);
            attachDataAuditPolicyMeta(config, page.getRecords());
            return page;
        }
        
        // 5. 构建排序
        String orderBy = DynamicQueryGenerator.buildOrderByClause(
                pageQuery.getOrderByColumn(), pageQuery.getIsAsc(), columnMapping);
        
        // 6. 执行分页查询
        Page<Map<String, Object>> page = repository.selectPage(
                tableName,
                pageQuery.getPageNum(),
                pageQuery.getPageSize(),
                searchParams,
                allowedSearchFields,
                searchTypeMap,
                columnMapping,
                orderBy,
                buildDataScopeCondition(config, tableName, null)
        );
        
        // 7. 转换字段名为camelCase
        List<Map<String, Object>> camelCaseRecords = DynamicQueryGenerator.convertListToCamelCase(page.getRecords());
        
        // 8. 读取链路统一先解密，再计算 VIRTUAL 公式，最后翻译和脱敏。
        applyReadPipeline(camelCaseRecords, config);
        attachDataAuditPolicyMeta(config, camelCaseRecords);
        
        page.setRecords(camelCaseRecords);
        return page;
        }
    }

    /**
     * 查询动态导出数据，复用动态 CRUD 的字段白名单、解密、字典翻译和脱敏链路。
     */
    public List<Map<String, Object>> selectExportRows(String configKey,
                                                      DynamicCrudQuery query,
                                                      Integer maxRows) {
        int limit = normalizeExportLimit(maxRows);
        return selectExportPageRows(configKey, query, 1, limit, null);
    }

    /**
     * 定时触发器候选记录读取。只允许按运行配置字段白名单内的到期字段做区间查询，
     * 避免后台扫描器出现无条件全表读取。
     */
    public List<Map<String, Object>> selectScheduledCandidateRows(String configKey,
                                                                  String dueField,
                                                                  LocalDateTime windowStart,
                                                                  LocalDateTime windowEnd,
                                                                  Integer batchSize) {
        AiCrudConfig config = getConfig(configKey);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        if (StringUtils.isBlank(dueField)) {
            throw new BusinessException("定时触发缺少到期字段");
        }
        Set<String> allowedFields = buildAllowedCustomFields(config);
        if (!allowedFields.contains(dueField)) {
            throw new BusinessException("定时触发到期字段不在运行配置字段范围内: " + dueField);
        }

        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, config.getTableName());
        Map<String, Object> searchParams = new LinkedHashMap<>();
        searchParams.put(dueField, List.of(windowStart, windowEnd));
        Map<String, String> searchTypeMap = new LinkedHashMap<>();
        searchTypeMap.put(dueField, "between");

        List<Map<String, Object>> rows = repository.selectList(
                config.getTableName(),
                searchParams,
                allowedFields,
                searchTypeMap,
                columnMapping,
                primaryKeyColumn(currentPrimaryKey()) + " ASC",
                normalizeScheduledBatchSize(batchSize),
                null
        );
        List<Map<String, Object>> camelCaseRows = DynamicQueryGenerator.convertListToCamelCase(rows);
        applyReadPipeline(camelCaseRows, config);
        return camelCaseRows;
        }
    }

    /**
     * 统计动态导出数据量，供同步/异步导出决策使用。
     */
    public long countExportRows(String configKey,
                                DynamicCrudQuery query,
                                DataScopeContext dataScopeContext) {
        ExportQueryContext context = buildExportQueryContext(configKey, query);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(context.config())) {
        RuntimeJoinContext joinContext = context.joinContext();
        if (joinContext != null && requiresJoinedExportQuery(context.config(), context.searchParams(), joinContext)) {
            DynamicCrudRepository.SqlCondition dataScopeCondition = buildDataScopeCondition(
                    context.config(), context.tableName(), "t0", dataScopeContext);
            return repository.countJoined(
                    context.tableName(),
                    buildRuntimeSelectFields(joinContext,
                            DynamicQueryGenerator.extractFieldNames(context.config().getColumnsSchema(), objectMapper), true),
                    joinContext.joins(),
                    context.searchParams(),
                    context.allowedSearchFields(),
                    context.searchTypeMap(),
                    joinContext.fieldColumnMapping(),
                    dataScopeCondition,
                    aggregateChildListRows(context.config())
            );
        }

        return repository.countList(
                context.tableName(),
                context.searchParams(),
                context.allowedSearchFields(),
                context.searchTypeMap(),
                context.columnMapping(),
                buildDataScopeCondition(context.config(), context.tableName(), null, dataScopeContext)
        );
        }
    }

    /**
     * 分页查询动态导出数据，不重复统计 count，供异步导出分批写入使用。
     */
    public List<Map<String, Object>> selectExportPageRows(String configKey,
                                                          DynamicCrudQuery query,
                                                          Integer pageNum,
                                                          Integer pageSize,
                                                          DataScopeContext dataScopeContext) {
        ExportQueryContext context = buildExportQueryContext(configKey, query);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(context.config())) {
        int current = normalizePageNum(pageNum);
        int size = normalizeExportPageSize(pageSize);
        RuntimeJoinContext joinContext = context.joinContext();
        if (joinContext != null && requiresJoinedExportQuery(context.config(), context.searchParams(), joinContext)) {
            List<Map<String, Object>> rows = repository.selectJoinedPageRecords(
                    context.tableName(),
                    buildRuntimeSelectFields(joinContext,
                            DynamicQueryGenerator.extractFieldNames(context.config().getColumnsSchema(), objectMapper), true),
                    joinContext.joins(),
                    current,
                    size,
                    context.searchParams(),
                    context.allowedSearchFields(),
                    context.searchTypeMap(),
                    joinContext.fieldColumnMapping(),
                    primaryKeyOrderBy("t0", "DESC"),
                    buildDataScopeCondition(context.config(), context.tableName(), "t0", dataScopeContext),
                    aggregateChildListRows(context.config())
            );
            applyReadPipeline(rows, context.config());
            return rows;
        }

        List<Map<String, Object>> rows = repository.selectPageRecords(
                context.tableName(),
                current,
                size,
                context.searchParams(),
                context.allowedSearchFields(),
                context.searchTypeMap(),
                context.columnMapping(),
                primaryKeyColumn(currentPrimaryKey()) + " DESC",
                buildDataScopeCondition(context.config(), context.tableName(), null, dataScopeContext)
        );

        List<Map<String, Object>> camelCaseRows = DynamicQueryGenerator.convertListToCamelCase(rows);
        applyReadPipeline(camelCaseRows, context.config());
        return camelCaseRows;
        }
    }

    /**
     * 查询树形导航数据，供树形单表模板左侧树使用。
     */
    public List<Map<String, Object>> selectTree(String configKey) {
        return selectTree(configKey, null, null, null);
    }

    /**
     * 查询树形数据。loadMode=full 返回完整树，loadMode=lazy 按 parentValue 返回一层子节点。
     */
    public List<Map<String, Object>> selectTree(String configKey, String parentValue, String parentId, String loadMode) {
        return selectTree(configKey, parentValue, parentId, loadMode, null, null);
    }

    /**
     * 查询树形数据。loadMode=full 返回完整树，loadMode=lazy 按 parentValue 返回一层子节点。
     */
    public List<Map<String, Object>> selectTree(String configKey,
                                                String parentValue,
                                                String parentId,
                                                String loadMode,
                                                String orderByColumn,
                                                String isAsc) {
        AiCrudConfig config = getConfig(configKey);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        LowcodeTreeConfig treeConfig = treeQueryEngine.resolveTreeConfig(config);
        String tableName = StringUtils.defaultIfBlank(treeConfig.getSourceTableName(), config.getTableName());
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        DynamicCrudRepository.SqlCondition dataScopeCondition = buildDataScopeCondition(config, tableName, null);
        return treeQueryEngine.selectTree(
                config,
                treeConfig,
                parentValue,
                parentId,
                loadMode,
                orderByColumn,
                isAsc,
                columnMapping,
                dataScopeCondition,
                rows -> applyReadPipeline(rows, config)
        );
        }
    }

    /**
     * 自定义分页查询。
     */
    public Page<Map<String, Object>> selectCustomPage(String configKey, CustomQueryExecuteDTO request) {
        AiCrudConfig config = getConfig(configKey);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        String tableName = config.getTableName();
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        Set<String> allowedFields = buildAllowedCustomFields(config);

        RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
        if (joinContext != null) {
            allowedFields.addAll(joinContext.fields().keySet());
        }
        List<CustomQueryConditionDTO> customConditions = treeQueryEngine.expandCustomIncludeChildrenConditions(
                request.getConditions(), config, tableName, allowedFields);
        if (joinContext != null) {
            if (requiresJoinedCustomQuery(request, joinContext)) {
                DynamicCrudRepository.SqlCondition dataScopeCondition = buildDataScopeCondition(config, tableName, "t0");
                boolean aggregateChildren = aggregateChildListRows(config);
                Page<Map<String, Object>> page = repository.selectJoinedCustomPage(
                        tableName,
                        withExpandedChildKeys(buildRuntimeSelectFields(joinContext, request.getFields(), true),
                                joinContext, aggregateChildren),
                        joinContext.joins(),
                        normalizePageNum(request.getPageNum()),
                        normalizePageSize(request.getPageSize()),
                        customConditions,
                        allowedFields,
                        joinContext.fieldColumnMapping(),
                        buildJoinOrderBy(request.getOrderByColumn(), request.getIsAsc(), joinContext),
                        dataScopeCondition,
                        aggregateChildren
                );
                applyReadPipeline(page.getRecords(), config);
                stampExpandedListRowKeys(page.getRecords(), joinContext, aggregateChildren);
                return page;
            }
        }

        String orderBy = DynamicQueryGenerator.buildOrderByClause(
                request.getOrderByColumn(), request.getIsAsc(), columnMapping);

        Page<Map<String, Object>> page = repository.selectCustomPage(
                tableName,
                normalizePageNum(request.getPageNum()),
                normalizePageSize(request.getPageSize()),
                request.getFields(),
                customConditions,
                allowedFields,
                columnMapping,
                orderBy,
                buildDataScopeCondition(config, tableName, null)
        );

        List<Map<String, Object>> camelCaseRecords = DynamicQueryGenerator.convertListToCamelCase(page.getRecords());
        applyReadPipeline(camelCaseRecords, config);
        page.setRecords(camelCaseRecords);
        return page;
        }
    }

    /**
     * 根据ID查询
     */
    public Map<String, Object> selectById(String configKey, Object id) {
        return readRecordByConfig(getConfig(configKey), id);
    }

    /**
     * 复用已加载的运行配置读单据，避免 task-form-context 再查一次 ai_crud_config。
     */
    public Map<String, Object> selectById(AiCrudConfig config, Object id) {
        if (config == null) {
            throw new BusinessException("CRUD配置不能为空");
        }
        return readRecordByConfig(config, id);
    }

    /**
     * 业务流程审批节点读取业务记录：优先已发布配置，工作台草稿对象也允许按当前配置读取。
     */
    public Map<String, Object> selectByIdAllowDraft(String configKey, Object id) {
        AiCrudConfig config = configService.getByConfigKey(configKey);
        if (config == null || EnableStatus.ENABLED.matches(config.getStatus())) {
            throw new BusinessException("CRUD配置不存在或已停用: " + configKey);
        }
        if (!"CONFIG".equals(config.getMode())) {
            throw new BusinessException("该配置不是配置驱动模式: " + configKey);
        }
        return readRecordByConfig(config, id);
    }

    /** 仅供打印 Provider 使用的固定配置读取；不解析 configKey，也不读取草稿或猜测关联。 */
    public DynamicCrudService.PrintRow selectPrintById(AiCrudConfig config, Object id) {
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            var raw = repository.selectById(config.getTableName(), primaryKeyColumn(currentPrimaryKey()), id,
                    buildDataScopeCondition(config, config.getTableName(), null));
            if (raw == null) {
                return null;
            }
            var values = DynamicQueryGenerator.convertMapToCamelCase(raw);
            applyPrintReadPipeline(Collections.singletonList(values), config);
            return new DynamicCrudService.PrintRow(raw, values);
        }
    }

    public List<Map<String, Object>> selectPrintChildren(AiCrudConfig config, String foreignKey, Object parentValue) {
        if (parentValue == null || String.valueOf(parentValue).isBlank()) {
            return List.of();
        }
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            var rows = repository.selectTreeChildren(config.getTableName(), foreignKey, parentValue,
                    primaryKeyColumn(currentPrimaryKey()) + " ASC", 501,
                    buildDataScopeCondition(config, config.getTableName(), null));
            if (rows.size() > 500) {
                throw new BusinessException("打印明细超过 500 行，请缩小单据范围");
            }
            var values = DynamicQueryGenerator.convertListToCamelCase(rows);
            applyPrintReadPipeline(values, config);
            return values;
        }
    }

    private void applyPrintReadPipeline(List<Map<String, Object>> rows, AiCrudConfig config) {
        fieldValuePipeline.applyPrintRead(rows, config);
        dynamicDataScopeService.enrichRows(config, rows);
    }

    private Map<String, Object> readRecordByConfig(AiCrudConfig config, Object id) {
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        String tableName = config.getTableName();
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();

        RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
        if (isMasterDetailRuntime(config) && joinContext != null) {
            return selectMasterDetailById(config, id, joinContext);
        }
        if (joinContext != null) {
            Map<String, Object> record = repository.selectJoinedById(
                    tableName,
                    id,
                    joinContext.selectFields(),
                    joinContext.joins(),
                    buildDataScopeCondition(config, tableName, "t0"));
            if (record == null) {
                return null;
            }
            applyReadPipeline(Collections.singletonList(record), config);
            attachDataAuditMeta(config, record);
            return record;
        }
        
        Map<String, Object> record = repository.selectById(
                tableName,
                primaryKeyColumn(primaryKey),
                id,
                buildDataScopeCondition(config, tableName, null));
        if (record == null) {
            return null;
        }
        
        // 转换为camelCase
        Map<String, Object> camelCaseRecord = DynamicQueryGenerator.convertMapToCamelCase(record);
        
        // 单条读取同样遵循“解密 -> VIRTUAL 公式 -> 翻译 -> 脱敏”顺序。
        applyReadPipeline(Collections.singletonList(camelCaseRecord), config);
        attachDataAuditMeta(config, camelCaseRecord);
        return camelCaseRecord;
        }
    }

    /**
     * 按运行时主键批量读取记录，供运行态批量能力复用动态 CRUD 的数据源、数据权限和读取后处理链路。
     */
    public Map<Object, Map<String, Object>> selectByIds(String configKey, Collection<?> ids) {
        List<Object> normalizedIds = normalizeBatchIds(ids);
        Map<Object, Map<String, Object>> result = new LinkedHashMap<>();
        if (normalizedIds.isEmpty()) {
            return result;
        }
        AiCrudConfig config = getConfig(configKey);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            String tableName = config.getTableName();
            LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
            String primaryColumn = primaryKeyColumn(primaryKey);
            List<Map<String, Object>> rows = repository.selectListByColumnIn(
                    tableName,
                    primaryColumn,
                    normalizedIds,
                    buildDataScopeCondition(config, tableName, null)
            );
            List<Map<String, Object>> camelCaseRows = DynamicQueryGenerator.convertListToCamelCase(rows);
            applyReadPipeline(camelCaseRows, config);
            for (Map<String, Object> row : camelCaseRows) {
                Object key = resolveBatchRowKey(row, primaryKey);
                if (key != null) {
                    result.put(key, row);
                }
            }
            return result;
        }
    }

    private List<Object> normalizeBatchIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> StringUtils.isNotBlank(String.valueOf(id)))
                .distinct()
                .map(Object.class::cast)
                .toList();
    }

    private Object resolveBatchRowKey(Map<String, Object> row, LowcodePrimaryKeyStrategy primaryKey) {
        return firstPresent(
                row,
                primaryKeyField(primaryKey),
                DynamicQueryGenerator.snakeToCamel(primaryKeyColumn(primaryKey)),
                primaryKeyColumn(primaryKey),
                "id"
        );
    }


    private Map<String, Object> selectMasterDetailById(
            AiCrudConfig config, Object id, RuntimeJoinContext joinContext) {
        Map<String, Object> result = masterDetailEngine.selectById(
                config, id, joinContext,
                buildDataScopeCondition(config, config.getTableName(), null));
        attachDataAuditMeta(config, result);
        return result;
    }

    private Map<String, String> buildRuntimeColumnMapping(AiCrudConfig config, String tableName) {
        return writeFieldPolicy.buildRuntimeColumnMapping(config, tableName);
    }

    private RuntimeJoinContext buildRuntimeJoinContext(AiCrudConfig config) {
        return runtimeRelationPlanner.buildRuntimeJoinContext(
                config, this::buildRuntimeColumnMapping, masterDetailEngine::preferReadableChildRelation);
    }

    private boolean isMasterDetailRuntime(AiCrudConfig config) {
        return runtimeRelationPlanner.isMasterDetailRuntime(config);
    }

    private String buildJoinOrderBy(String orderByColumn, String isAsc, RuntimeJoinContext context) {
        return runtimeRelationPlanner.buildJoinOrderBy(orderByColumn, isAsc, context);
    }

    private boolean requiresJoinedPageQuery(
            AiCrudConfig config,
            PageQuery pageQuery,
            Map<String, Object> searchParams,
            RuntimeJoinContext context) {
        return runtimeRelationPlanner.requiresJoinedPageQuery(config, pageQuery, searchParams, context);
    }

    private boolean requiresJoinedExportQuery(
            AiCrudConfig config,
            Map<String, Object> searchParams,
            RuntimeJoinContext context) {
        return runtimeRelationPlanner.requiresJoinedExportQuery(config, searchParams, context);
    }

    private boolean requiresJoinedCustomQuery(CustomQueryExecuteDTO request, RuntimeJoinContext context) {
        return runtimeRelationPlanner.requiresJoinedCustomQuery(request, context);
    }

    private List<DynamicCrudRepository.JoinField> buildRuntimeSelectFields(
            RuntimeJoinContext context,
            Collection<String> requestedFields,
            boolean defaultPrimaryFields) {
        return runtimeRelationPlanner.buildRuntimeSelectFields(context, requestedFields, defaultPrimaryFields);
    }

    private boolean aggregateChildListRows(AiCrudConfig config) {
        return runtimeRelationPlanner.aggregateChildListRows(config);
    }

    private List<DynamicCrudRepository.JoinField> withExpandedChildKeys(
            List<DynamicCrudRepository.JoinField> selectFields,
            RuntimeJoinContext context,
            boolean aggregateChildren) {
        return runtimeRelationPlanner.withExpandedChildKeys(selectFields, context, aggregateChildren);
    }

    private void stampExpandedListRowKeys(
            List<Map<String, Object>> rows,
            RuntimeJoinContext context,
            boolean aggregateChildren) {
        runtimeRelationPlanner.stampExpandedListRowKeys(rows, context, aggregateChildren);
    }

    private LowcodeModelSchema readModelSchema(AiCrudConfig config) {
        return runtimeRelationPlanner.readModelSchema(config);
    }

    private void applyReadPipeline(List<Map<String, Object>> rows, AiCrudConfig config) {
        fieldValuePipeline.applyRead(rows, config);
        dynamicDataScopeService.enrichRows(config, rows);
    }

    private AiCrudConfig getConfig(String configKey) {
        AiCrudConfig config = configService.getByConfigKey(configKey);
        if (config == null || EnableStatus.ENABLED.matches(config.getStatus())) {
            throw new BusinessException("CRUD配置不存在或已停用: " + configKey);
        }
        if (!"CONFIG".equals(config.getMode())) {
            throw new BusinessException("该配置不是配置驱动模式: " + configKey);
        }
        boolean designPreview = isAuthorizedDesignPreviewRequest();
        if ("LOWCODE".equals(config.getBuildMode())
                && !"PUBLISHED".equals(config.getPublishStatus())
                && !designPreview) {
            throw new BusinessException("低代码应用尚未发布: " + configKey);
        }
        return designPreview
                ? configService.resolveDraftRuntimeConfig(config)
                : configService.resolvePublishedRuntimeConfig(config);
    }

    private boolean isAuthorizedDesignPreviewRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                || !"1".equals(attributes.getRequest().getParameter("designPreview"))) {
            return false;
        }
        return configService.hasDesignPreviewPermission();
    }

    private Set<String> buildAllowedCustomFields(AiCrudConfig config) {
        Set<String> fields = new HashSet<>();
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getSearchSchema(), objectMapper));
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper));
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getEditSchema(), objectMapper));
        fields.add("id");
        return fields;
    }

    Set<String> buildAllowedSearchFields(AiCrudConfig config) {
        Set<String> fields = new HashSet<>(buildAllowedCustomFields(config));
        if (treeQueryEngine.isTreeRuntime(config)) {
            LowcodeTreeConfig treeConfig = treeQueryEngine.resolveTreeConfig(config);
            if (StringUtils.isNotBlank(treeConfig.getFilterField())) {
                fields.add(treeConfig.getFilterField());
            }
        }
        return fields;
    }

    Map<String, String> buildEffectiveSearchTypeMap(
            AiCrudConfig config,
            DynamicCrudQuery query,
            Set<String> allowedSearchFields) {
        Map<String, String> result = new LinkedHashMap<>(
                DynamicQueryGenerator.extractSearchTypeMap(config.getSearchSchema(), objectMapper));
        Map<String, String> requested = query == null ? null : query.getSearchTypeMap();
        if (requested == null || requested.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String field = StringUtils.trimToNull(entry.getKey());
            String searchType = StringUtils.lowerCase(StringUtils.trimToNull(entry.getValue()), Locale.ROOT);
            if (field != null
                    && allowedSearchFields.contains(field)
                    && searchType != null
                    && SUPPORTED_SEARCH_TYPES.contains(searchType)) {
                result.put(field, searchType);
            }
        }
        return result;
    }

    private void expandAllowedSearchFieldsFromParams(
            Map<String, Object> searchParams,
            Set<String> allowedSearchFields,
            Map<String, String> searchTypeMap,
            Map<String, String> columnMapping) {
        if (searchParams == null || searchParams.isEmpty()) {
            return;
        }
        for (String key : searchParams.keySet()) {
            if ("__orLike".equals(key) || key.endsWith("_includeChildren")) {
                continue;
            }
            if (allowedSearchFields.contains(key)) {
                continue;
            }
            String column = columnMapping.getOrDefault(key, DynamicQueryGenerator.camelToSnake(key));
            if (column != null && columnMapping.containsValue(column)) {
                allowedSearchFields.add(key);
                searchTypeMap.putIfAbsent(key, "eq");
            }
        }
    }

    private ExportQueryContext buildExportQueryContext(String configKey, DynamicCrudQuery query) {
        AiCrudConfig config = getConfig(configKey);
        String tableName = config.getTableName();
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        Set<String> allowedSearchFields = buildAllowedSearchFields(config);
        Map<String, String> searchTypeMap = buildEffectiveSearchTypeMap(config, query, allowedSearchFields);
        Map<String, Object> searchParams = query != null ? query.getSearchParams() : null;
        searchParams = treeQueryEngine.expandIncludeChildrenParams(
                searchParams, config, tableName, allowedSearchFields, searchTypeMap);
        treeQueryEngine.coerceMultiValueSearchTypes(searchParams, searchTypeMap);
        return new ExportQueryContext(
                config, tableName, columnMapping, allowedSearchFields, searchTypeMap,
                searchParams, buildRuntimeJoinContext(config));
    }

    private DynamicCrudRepository.SqlCondition buildDataScopeCondition(
            AiCrudConfig config, String tableName, String tableAlias) {
        return dynamicDataScopeService.buildCondition(config, tableName, tableAlias);
    }

    private DynamicCrudRepository.SqlCondition buildDataScopeCondition(
            AiCrudConfig config,
            String tableName,
            String tableAlias,
            DataScopeContext dataScopeContext) {
        return dynamicDataScopeService.buildCondition(config, tableName, tableAlias, dataScopeContext);
    }

    private LowcodeRuntimeDataSourceContextHolder.Scope useRuntimeContext(AiCrudConfig config) {
        LowcodeRuntimeDataSourceContext context = runtimeDataSourceResolver.resolve(config);
        config.setTableName(StringUtils.defaultIfBlank(context.getTableName(), config.getTableName()));
        return LowcodeRuntimeDataSourceContextHolder.use(context);
    }

    private LowcodePrimaryKeyStrategy currentPrimaryKey() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        LowcodePrimaryKeyStrategy primaryKey = context == null ? null : context.getPrimaryKey();
        if (primaryKey == null) {
            primaryKey = new LowcodePrimaryKeyStrategy();
            primaryKey.setField("id");
            primaryKey.setColumnName("id");
            primaryKey.setDataType("bigint");
            primaryKey.setAutoIncrement(true);
        }
        return primaryKey;
    }

    private String primaryKeyField(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getField(), "id");
    }

    private String primaryKeyColumn(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getColumnName(), "id");
    }

    private String primaryKeyOrderBy(String tableAlias, String direction) {
        String column = primaryKeyColumn(currentPrimaryKey());
        return StringUtils.isBlank(tableAlias)
                ? column + " " + direction
                : tableAlias + "." + column + " " + direction;
    }

    private Object firstPresent(Map<String, Object> data, String... keys) {
        if (data == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isNotBlank(key) && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private void attachDataAuditMeta(AiCrudConfig config, Map<String, Object> record) {
        if (dataAuditCaptureService == null || record == null) {
            return;
        }
        dataAuditCaptureService.attachReadMeta(config, record);
        Object main = record.get("main");
        if (main instanceof Map<?, ?> mainMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) mainMap;
            dataAuditCaptureService.attachReadMeta(config, typed);
        }
    }

    private void attachDataAuditPolicyMeta(AiCrudConfig config, List<Map<String, Object>> records) {
        if (dataAuditCaptureService != null && records != null && !records.isEmpty()) {
            dataAuditCaptureService.attachPolicyMeta(config, records);
        }
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private int normalizeExportLimit(Integer maxRows) {
        if (maxRows == null || maxRows < 1) {
            return MAX_EXPORT_ROWS;
        }
        return Math.min(maxRows, MAX_EXPORT_ROWS);
    }

    private int normalizeScheduledBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize < 1) {
            return 50;
        }
        return Math.min(batchSize, 200);
    }

    private int normalizeExportPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 1000;
        }
        return Math.min(pageSize, 5000);
    }
}
