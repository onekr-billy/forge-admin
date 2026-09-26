package com.mdframe.forge.plugin.generator.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.dto.CustomQueryExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditRemoveDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditWriteContextDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import com.mdframe.forge.plugin.generator.enums.DataAuditEventType;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditCaptureService;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditPayloadSupport;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditRecordIds;
import com.mdframe.forge.plugin.generator.service.formula.StoredAggregateRefreshService;
import com.mdframe.forge.plugin.generator.service.formula.StoredFormulaRuntime;
import com.mdframe.forge.plugin.generator.service.formula.VirtualFormulaRuntime;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessDocumentConfigService;
import com.mdframe.forge.plugin.generator.service.businessapp.CodeRuleService;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeFieldValueValidator;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeStrategyFactory;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import com.mdframe.forge.starter.trans.spi.DictValueProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 动态CRUD服务
 * 基于DynamicCrudRepository实现，支持配置驱动的通用CRUD操作
 * 
 * @author forge
 */
@Slf4j
@Service
public class DynamicCrudService {

    private static final int MAX_EXPORT_ROWS = 10000;
    private static final Set<String> IMMUTABLE_WRITE_FIELDS = Set.of(
            "id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time", "delFlag", "del_flag"
    );
    private static final Set<String> SUPPORTED_SEARCH_TYPES = Set.of(
            "eq", "ne", "like", "left_like", "right_like", "gt", "ge", "gte", "lt", "le", "lte",
            "in", "between", "is_null", "is_not_null"
    );

    /**
     * 当前流程节点对子表的最小写权限投影。权限来源由 BusinessFlowService
     * 从 BPMN 节点解析，动态 CRUD 服务只负责执行安全写入。
     */
    public record TaskChildPermission(boolean readable,
                                      boolean allowCreate,
                                      boolean allowUpdate,
                                      boolean allowDelete,
                                      Set<String> writableFields) {
        public TaskChildPermission {
            writableFields = writableFields == null ? Set.of() : Set.copyOf(writableFields);
        }
    }

    private record ExportQueryContext(AiCrudConfig config,
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
    private final StoredAggregateRefreshService storedAggregateRefreshService;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;
    private final DataAuditCaptureService dataAuditCaptureService;
    private final DynamicCrudUniquenessValidator uniquenessValidator;
    private final DynamicCrudGeneratedFieldPolicy generatedFieldPolicy;
    private final DynamicCrudTreeQueryEngine treeQueryEngine;
    private final DynamicCrudFieldValuePipeline fieldValuePipeline;
    private final DynamicCrudRuntimeRelationPlanner runtimeRelationPlanner;
    private final DynamicCrudWriteFieldPolicy writeFieldPolicy;
    private final DynamicCrudMasterDetailEngine masterDetailEngine;
    private final DynamicCrudTaskEditableCoordinator taskEditableCoordinator;

    public DynamicCrudService(
            DynamicCrudRepository repository,
            AiCrudConfigService configService,
            ObjectMapper objectMapper,
            DictValueProvider dictValueProvider,
            DesensitizeStrategyFactory desensitizeStrategyFactory,
            PersistentCryptoService persistentCryptoService,
            LowcodeEncryptConfigParser encryptConfigParser,
            DynamicDataScopeService dynamicDataScopeService,
            BusinessDocumentConfigService documentConfigService,
            CodeRuleService codeRuleService,
            StoredAggregateRefreshService storedAggregateRefreshService,
            StoredFormulaRuntime storedFormulaRuntime,
            VirtualFormulaRuntime virtualFormulaRuntime,
            LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver,
            DataAuditCaptureService dataAuditCaptureService) {
        this.repository = repository;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.dynamicDataScopeService = dynamicDataScopeService;
        this.storedAggregateRefreshService = storedAggregateRefreshService;
        this.runtimeDataSourceResolver = runtimeDataSourceResolver;
        this.dataAuditCaptureService = dataAuditCaptureService;
        this.uniquenessValidator = new DynamicCrudUniquenessValidator(repository, objectMapper);
        this.generatedFieldPolicy = new DynamicCrudGeneratedFieldPolicy(
                repository, objectMapper, documentConfigService, codeRuleService);
        this.treeQueryEngine = new DynamicCrudTreeQueryEngine(repository, configService, objectMapper);
        this.fieldValuePipeline = new DynamicCrudFieldValuePipeline(
                objectMapper,
                dictValueProvider,
                desensitizeStrategyFactory,
                persistentCryptoService,
                encryptConfigParser,
                virtualFormulaRuntime
        );
        this.runtimeRelationPlanner = new DynamicCrudRuntimeRelationPlanner(repository, objectMapper);
        this.writeFieldPolicy = new DynamicCrudWriteFieldPolicy(
                repository, objectMapper, fieldValuePipeline, storedFormulaRuntime);
        this.masterDetailEngine = new DynamicCrudMasterDetailEngine(
                repository,
                objectMapper,
                storedAggregateRefreshService,
                dynamicDataScopeService,
                writeFieldPolicy,
                uniquenessValidator,
                fieldValuePipeline,
                runtimeRelationPlanner);
        this.taskEditableCoordinator = new DynamicCrudTaskEditableCoordinator(
                repository,
                storedAggregateRefreshService,
                writeFieldPolicy,
                uniquenessValidator,
                fieldValuePipeline,
                masterDetailEngine);
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
    public record PrintRow(Map<String, Object> columns, Map<String, Object> values) { }

    public PrintRow selectPrintById(AiCrudConfig config, Object id) {
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            var raw = repository.selectById(config.getTableName(), primaryKeyColumn(currentPrimaryKey()), id,
                    buildDataScopeCondition(config, config.getTableName(), null));
            if (raw == null) {
                return null;
            }
            var values = DynamicQueryGenerator.convertMapToCamelCase(raw);
            applyPrintReadPipeline(Collections.singletonList(values), config);
            return new PrintRow(raw, values);
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

    // ==================== 新增操作 ====================

    /**
     * 新增
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> insert(String configKey, Map<String, Object> data) {
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            openDataAudit(config, null, DataAuditSourceType.FORM, DataAuditEventType.CREATE, data, false);
        String tableName = config.getTableName();
        
        // 获取字段映射
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        
        // 获取允许写入的字段。editSchema 是运行态表单白名单，modelSchema 兜底承接保存设计器后新增但尚未重建 editSchema 的字段。
        Set<String> allowedFields = buildAllowedWriteFields(config, tableName);
        generatedFieldPolicy.applyAutoGeneratedFields(config, data, allowedFields);
        generatedFieldPolicy.applyDocumentNoIfNeeded(config, data, allowedFields);
        SelectionIdentifierValidator.validate(config, data, objectMapper);

        RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
        if (isMasterDetailRuntime(config) && joinContext != null) {
            insertMasterDetailData(config, data, allowedFields, joinContext);
            return data;
        }
        if (joinContext != null) {
            insertJoinedData(config, data, allowedFields, joinContext);
            return data;
        }
        
        // 过滤前先计算 STORED 公式，确保公式结果参与写库。
        applyStoredFormulas(config, data);
        validateFieldValues(config, data);
        Map<String, Object> filteredData = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (isImmutableWriteField(entry.getKey())) {
                continue;
            }
            if (allowedFields.contains(entry.getKey())) {
                String columnName = columnMapping.getOrDefault(entry.getKey(), DynamicQueryGenerator.camelToSnake(entry.getKey()));
                filteredData.put(columnName, entry.getValue());
            }
        }
        
        if (filteredData.isEmpty()) {
            throw new BusinessException("没有可写入的字段");
        }

        validateUniqueConstraints(config, tableName, data, null, null);
        
        // 应用加密
        applyMoneyStorageWrite(filteredData, config);
        applyStructuredFieldStorageWrite(filteredData, config);
        applyEncrypt(filteredData, config.getEncryptConfig());
        
        // 执行插入
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        Object id = repository.insertReturningKey(
                tableName,
                filteredData,
                primaryKeyColumn(primaryKey),
                primaryKeyAutoIncrement(primaryKey));
        if (id == null) {
            storedAggregateRefreshService.refreshAfterChildInsert(config, data);
            return data;
        }
        Map<String, Object> result = selectById(configKey, id);
        if (result != null) {
            storedAggregateRefreshService.refreshAfterChildInsert(config, result);
            return result;
        }
        Map<String, Object> fallback = new LinkedHashMap<>(data);
        putPrimaryKeyAlias(fallback, primaryKey, id);
        storedAggregateRefreshService.refreshAfterChildInsert(config, fallback);
        return fallback;
        }
    }

    /**
     * 内部运行态创建记录。用于触发器创建关联记录，校验发布态配置和模型字段，
     * 不依赖前端编辑表单是否展示该字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> insertInternal(String configKey, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new BusinessException("没有可写入的字段");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        openDataAudit(config, null, DataAuditSourceType.AUTOMATION, DataAuditEventType.CREATE, data, false);
        String tableName = config.getTableName();
        Set<String> allowedFields = collectInternalWriteFields(config, tableName);
        generatedFieldPolicy.applyAutoGeneratedFields(config, data, allowedFields);
        generatedFieldPolicy.applyDocumentNoIfNeeded(config, data, allowedFields);
        applyStoredFormulas(config, data);
        validateFieldValues(config, data);
        Map<String, Object> filteredData = filterInternalWriteData(config, tableName, data);
        if (filteredData.isEmpty()) {
            throw new BusinessException("没有可写入的字段");
        }
        validateUniqueConstraints(config, tableName, data, null, null);
        applyStructuredFieldStorageWrite(filteredData, config);
        applyEncrypt(filteredData, config.getEncryptConfig());
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        Object id = repository.insertReturningKey(
                tableName,
                filteredData,
                primaryKeyColumn(primaryKey),
                primaryKeyAutoIncrement(primaryKey));
        Map<String, Object> result = id == null ? null : selectById(configKey, id);
        if (result != null) {
            storedAggregateRefreshService.refreshAfterChildInsert(config, result);
            return result;
        }
        Map<String, Object> fallback = new LinkedHashMap<>(data);
        putPrimaryKeyAlias(fallback, primaryKey, id);
        storedAggregateRefreshService.refreshAfterChildInsert(config, fallback);
        return fallback;
        }
    }

    /**
     * 事务型业务命令创建记录。字段只允许来自已发布模型/页面协议，不使用数据库全列兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> insertCommandRecord(String configKey, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new BusinessException("没有可写入的字段");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            openDataAudit(config, null, DataAuditSourceType.BUSINESS_ACTION, DataAuditEventType.CREATE, data, false);
            String tableName = config.getTableName();
            Set<String> allowedFields = collectCommandFields(config);
            generatedFieldPolicy.applyAutoGeneratedFields(config, data, allowedFields);
            generatedFieldPolicy.applyDocumentNoIfNeeded(config, data, allowedFields);
            applyStoredFormulas(config, data);
            validateFieldValues(config, data);
            Map<String, Object> filteredData = filterCommandWriteData(config, tableName, data);
            if (filteredData.isEmpty()) {
                throw new BusinessException("没有可写入的字段");
            }
            validateUniqueConstraints(config, tableName, data, null, null);
            applyStructuredFieldStorageWrite(filteredData, config);
            applyEncrypt(filteredData, config.getEncryptConfig());
            LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
            Object id = repository.insertReturningKey(
                    tableName, filteredData, primaryKeyColumn(primaryKey), primaryKeyAutoIncrement(primaryKey));
            Map<String, Object> result = id == null ? null : selectById(configKey, id);
            if (result != null) {
                storedAggregateRefreshService.refreshAfterChildInsert(config, result);
                return result;
            }
            Map<String, Object> fallback = new LinkedHashMap<>(data);
            putPrimaryKeyAlias(fallback, primaryKey, id);
            storedAggregateRefreshService.refreshAfterChildInsert(config, fallback);
            return fallback;
        }
    }

    // ==================== 更新操作 ====================

    /**
     * 更新
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateById(String configKey, Map<String, Object> data) {
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        String tableName = config.getTableName();
        
        // 获取ID
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        Object idValue = resolvePayloadId(data, primaryKey);
        if (idValue == null) {
            throw new BusinessException("更新操作缺少id");
        }
        Object id = idValue;
        openDataAudit(config, id, DataAuditSourceType.FORM, DataAuditEventType.UPDATE, data, true);
        
        // 获取字段映射
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        
        // 获取允许写入的字段。editSchema 是运行态表单白名单，modelSchema 兜底承接保存设计器后新增但尚未重建 editSchema 的字段。
        Set<String> allowedFields = buildAllowedWriteFields(config, tableName);
        SelectionIdentifierValidator.validate(config, data, objectMapper);

        RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
        if (isMasterDetailRuntime(config) && joinContext != null) {
            updateMasterDetailData(config, id, data, allowedFields, joinContext);
            return;
        }
        if (joinContext != null) {
            updateJoinedData(config, id, data, allowedFields, joinContext);
            return;
        }

        // 公式字段需要基于完整旧值上下文计算，确保部分更新不会把公式算空。
        DynamicCrudRepository.SqlCondition dataScopeCondition = buildWriteDataScopeCondition(config, tableName, null);
        Map<String, Object> beforeRecord = applyStoredFormulasForUpdate(config, tableName, id, data, dataScopeCondition);
        validateFieldValues(config, data);
        
        // 过滤并转换字段名
        Map<String, Object> filteredData = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (isImmutableWriteField(key)) {
                continue;
            }
            if (allowedFields.contains(key)) {
                String columnName = columnMapping.getOrDefault(key, DynamicQueryGenerator.camelToSnake(key));
                if (!isPrimaryKeyAlias(key, columnName, primaryKey)) {
                    filteredData.put(columnName, entry.getValue());
                }
            }
        }
        removeMaskedDesensitizedWriteColumns(filteredData, config, tableName);
        
        if (filteredData.isEmpty()) {
            throw emptyWriteFieldsException(config, "更新", data.keySet(), allowedFields);
        }

        validateUniqueConstraints(config, tableName, data, beforeRecord, id);
        
        // 应用加密
        applyMoneyStorageWrite(filteredData, config);
        applyStructuredFieldStorageWrite(filteredData, config);
        applyEncrypt(filteredData, config.getEncryptConfig());
        
        // 执行更新
        int affected = repository.updateById(tableName, primaryKeyColumn(primaryKey), id, filteredData, dataScopeCondition);
        if (affected <= 0) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        storedAggregateRefreshService.refreshAfterChildUpdate(config, beforeRecord,
                repository.selectById(tableName, primaryKeyColumn(primaryKey), id, null));
        }
    }

    /**
     * 保存流程待办允许编辑的主子表字段。
     *
     * <p>该入口与普通 CRUD 更新隔离：子表永远按 merge 语义处理，节点未授权的
     * 字段、行新增和行删除会在进入 repository 前拒绝，避免部分 payload 触发
     * replace 模式的数据丢失。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskEditableData(String configKey,
                                       Object id,
                                       Map<String, Object> data,
                                       Set<String> writableMainFields,
                                       Map<String, TaskChildPermission> childPermissions) {
        if (id == null) {
            throw new BusinessException("更新操作缺少id");
        }
        if (data == null || data.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            openDataAudit(config, id, DataAuditSourceType.FLOW_FORM, DataAuditEventType.UPDATE, data, false);
            RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
            boolean masterDetailRuntime = joinContext != null && isMasterDetailRuntime(config);
            DynamicCrudRepository.SqlCondition dataScopeCondition = masterDetailRuntime
                    ? buildWriteDataScopeCondition(config, config.getTableName(), null)
                    : null;
            Map<String, Object> fallbackUpdate = taskEditableCoordinator.update(
                    config,
                    id,
                    data,
                    writableMainFields,
                    childPermissions,
                    joinContext,
                    masterDetailRuntime,
                    dataScopeCondition);
            if (fallbackUpdate != null) {
                updateById(configKey, fallbackUpdate);
            }
        }
    }

    /**
     * 内部运行态字段更新。用于单据状态等系统驱动字段，不受编辑表单 schema 限制，
     * 但仍校验动态表真实列名、租户条件和数据权限。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateInternalFieldsById(String configKey, Object id, Map<String, Object> data) {
        updateInternalFieldsByConfig(getConfig(configKey), id, data);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateInternalFieldsByIdAllowDraft(String configKey, Object id, Map<String, Object> data) {
        AiCrudConfig config = configService.getByConfigKey(configKey);
        if (config == null || EnableStatus.ENABLED.matches(config.getStatus())) {
            throw new BusinessException("CRUD配置不存在或已停用: " + configKey);
        }
        if (!"CONFIG".equals(config.getMode())) {
            throw new BusinessException("该配置不是配置驱动模式: " + configKey);
        }
        updateInternalFieldsByConfig(config, id, data);
    }

    private void updateInternalFieldsByConfig(AiCrudConfig config, Object id, Map<String, Object> data) {
        if (id == null) {
            throw new BusinessException("更新操作缺少id");
        }
        if (data == null || data.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        openDataAudit(config, id, DataAuditSourceType.FLOW_CALLBACK, DataAuditEventType.UPDATE, data, false);
        String tableName = config.getTableName();
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        Set<String> tableColumns = repository.getTableColumns(tableName);
        // 流程回调等内部回写：无登录会话时跳过用户数据权限，仍由仓储施加租户条件。
        DynamicCrudRepository.SqlCondition dataScopeCondition = buildInternalWriteDataScopeCondition(config, tableName, null);
        Map<String, Object> beforeRecord = applyStoredFormulasForUpdate(config, tableName, id, data, dataScopeCondition);
        validateFieldValues(config, data);

        Map<String, Object> filteredData = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (isImmutableWriteField(key)) {
                continue;
            }
            String columnName = columnMapping.getOrDefault(key, DynamicQueryGenerator.camelToSnake(key));
            repository.validateIdentifier(columnName);
            if (!tableColumns.contains(columnName)) {
                throw missingRuntimeColumn(config, tableName, key, columnName);
            }
            if (isImmutableWriteField(columnName) || isPrimaryKeyAlias(key, columnName, primaryKey)) {
                continue;
            }
            filteredData.put(columnName, entry.getValue());
        }

        if (filteredData.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }

        applyStructuredFieldStorageWrite(filteredData, config);
        applyEncrypt(filteredData, config.getEncryptConfig());
        int affected = repository.updateById(tableName, primaryKeyColumn(primaryKey), id, filteredData, dataScopeCondition);
        if (affected <= 0) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        storedAggregateRefreshService.refreshAfterChildUpdate(config, beforeRecord,
                repository.selectById(tableName, primaryKeyColumn(primaryKey), id, null));
        }
    }

    /**
     * 内部运行态字段更新。用于触发器更新字段，字段必须存在于发布态模型或动态表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFieldsInternal(String configKey, Object id, Map<String, Object> fields) {
        if (id == null) {
            throw new BusinessException("更新操作缺少id");
        }
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
        openDataAudit(config, id, DataAuditSourceType.AUTOMATION, DataAuditEventType.UPDATE, fields, false);
        String tableName = config.getTableName();
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        DynamicCrudRepository.SqlCondition dataScopeCondition = buildInternalWriteDataScopeCondition(config, tableName, null);
        Map<String, Object> beforeRecord = applyStoredFormulasForUpdate(config, tableName, id, fields, dataScopeCondition);
        validateFieldValues(config, fields);
        Map<String, Object> filteredData = filterInternalWriteData(config, tableName, fields);
        if (filteredData.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }
        applyStructuredFieldStorageWrite(filteredData, config);
        applyEncrypt(filteredData, config.getEncryptConfig());
        removePrimaryKeyColumns(filteredData, primaryKey);
        int affected = repository.updateById(tableName, primaryKeyColumn(primaryKey), id, filteredData, dataScopeCondition);
        if (affected <= 0) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        storedAggregateRefreshService.refreshAfterChildUpdate(config, beforeRecord,
                repository.selectById(tableName, primaryKeyColumn(primaryKey), id, null));
        }
    }

    /**
     * 事务型命令条件更新。期望值、数据权限、租户和逻辑删除条件与写入处于同一 UPDATE。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCommandFields(String configKey,
                                    Object id,
                                    Map<String, Object> fields,
                                    Map<String, Object> expectedFields) {
        if (id == null) {
            throw new BusinessException("更新操作缺少id");
        }
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            openDataAudit(config, id, DataAuditSourceType.BUSINESS_ACTION, DataAuditEventType.UPDATE, fields, false);
            String tableName = config.getTableName();
            LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
            DynamicCrudRepository.SqlCondition dataScope = buildWriteDataScopeCondition(config, tableName, null);
            Map<String, Object> beforeRecord = applyStoredFormulasForUpdate(
                    config, tableName, id, fields, dataScope);
            validateFieldValues(config, fields);
            Map<String, Object> filteredData = filterCommandWriteData(config, tableName, fields);
            if (filteredData.isEmpty()) {
                throw new BusinessException("没有可更新的字段");
            }
            applyStructuredFieldStorageWrite(filteredData, config);
            applyEncrypt(filteredData, config.getEncryptConfig());
            removePrimaryKeyColumns(filteredData, primaryKey);
            DynamicCrudRepository.SqlCondition expected = buildCommandExpectedCondition(
                    config, tableName, expectedFields);
            int affected = repository.updateById(
                    tableName, primaryKeyColumn(primaryKey), id, filteredData,
                    combineConditions(dataScope, expected));
            if (affected <= 0) {
                throw new BusinessException("记录已变化、数值越界或无权限更新");
            }
            storedAggregateRefreshService.refreshAfterChildUpdate(config, beforeRecord,
                    repository.selectById(tableName, primaryKeyColumn(primaryKey), id, null));
        }
    }

    /**
     * 事务型命令多字段数值原子调整。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustCommandNumbers(String configKey,
                                     Object id,
                                     Map<String, BigDecimal> deltas,
                                     Map<String, BigDecimal> minimums,
                                     Map<String, BigDecimal> maximums,
                                     Map<String, Object> expectedFields) {
        if (id == null) {
            throw new BusinessException("数值调整缺少目标记录 ID");
        }
        if (deltas == null || deltas.isEmpty()) {
            throw new BusinessException("数值调整字段不能为空");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            openDataAudit(config, id, DataAuditSourceType.BUSINESS_ACTION, DataAuditEventType.UPDATE, Map.of(), false);
            String tableName = config.getTableName();
            LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
            Map<String, BigDecimal> mappedDeltas = mapCommandDecimalFields(config, tableName, deltas);
            Map<String, BigDecimal> mappedMinimums = mapCommandDecimalFields(config, tableName, minimums);
            Map<String, BigDecimal> mappedMaximums = mapCommandDecimalFields(config, tableName, maximums);
            DynamicCrudRepository.SqlCondition dataScope = buildWriteDataScopeCondition(config, tableName, null);
            DynamicCrudRepository.SqlCondition expected = buildCommandExpectedCondition(
                    config, tableName, expectedFields);
            Map<String, Object> before = repository.selectById(
                    tableName, primaryKeyColumn(primaryKey), id, dataScope);
            if (before == null) {
                throw new BusinessException("记录不存在或无权限调整");
            }
            int affected = repository.adjustNumbersById(
                    tableName, primaryKeyColumn(primaryKey), id,
                    mappedDeltas, mappedMinimums, mappedMaximums,
                    combineConditions(dataScope, expected));
            if (affected <= 0) {
                throw new BusinessException("记录已变化、数值越界或无权限调整");
            }
            storedAggregateRefreshService.refreshAfterChildUpdate(config, before,
                    repository.selectById(tableName, primaryKeyColumn(primaryKey), id, null));
        }
    }

    /**
     * 在当前本地事务中锁定目标记录并校验发布态 expected 字段。
     * 用于状态门禁，避免先查后改产生竞态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assertCommandRecord(String configKey, Object id, Map<String, Object> expectedFields) {
        assertCommandRecord(configKey, id, expectedFields, List.of());
    }

    /**
     * 在行锁门禁中增加受控的字段间数值比较，例如 pickedQuantity >= form.quantity。
     * 比较字段和值均已由动作协议校验并映射到发布模型字段，禁止传入 SQL 或任意列名。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assertCommandRecord(String configKey,
                                    Object id,
                                    Map<String, Object> expectedFields,
                                    List<Map<String, Object>> numericConstraints) {
        if (id == null || StringUtils.isBlank(String.valueOf(id))) {
            throw new BusinessException("记录门禁缺少目标记录 ID");
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            DynamicCrudRepository.SqlCondition dataScope = buildWriteDataScopeCondition(config, config.getTableName(), null);
            DynamicCrudRepository.SqlCondition expected = buildCommandExpectedCondition(
                    config, config.getTableName(), expectedFields);
            DynamicCrudRepository.SqlCondition numeric = buildCommandNumericCondition(
                    config, config.getTableName(), numericConstraints);
            Map<String, Object> record = repository.selectByIdForUpdate(
                    config.getTableName(), primaryKeyColumn(currentPrimaryKey()), id,
                    combineConditions(combineConditions(dataScope, expected), numeric));
            if (record == null) {
                throw new BusinessException("记录状态不满足门禁、无权限访问或记录不存在");
            }
        }
    }

    /** 本地事务命令只能使用默认 Forge 主数据源。 */
    public void assertLocalTransactionConfig(String configKey) {
        AiCrudConfig config = getConfig(configKey);
        if (config.getRuntimeDatasourceId() != null
                || StringUtils.isNotBlank(config.getRuntimeDatasourceCode())) {
            throw new BusinessException("本地事务动作不支持外接运行数据源: " + configKey);
        }
    }

    private Map<String, Object> selectMasterDetailById(
            AiCrudConfig config,
            Object id,
            RuntimeJoinContext joinContext) {
        Map<String, Object> result = masterDetailEngine.selectById(
                config,
                id,
                joinContext,
                buildDataScopeCondition(config, config.getTableName(), null));
        attachDataAuditMeta(config, result);
        return result;
    }

    private RuntimeChildRelation preferReadableChildRelation(
            AiCrudConfig config,
            RuntimeChildRelation relation) {
        return masterDetailEngine.preferReadableChildRelation(config, relation);
    }

    private void insertMasterDetailData(
            AiCrudConfig config,
            Map<String, Object> data,
            Set<String> allowedFields,
            RuntimeJoinContext joinContext) {
        masterDetailEngine.insert(config, data, allowedFields, joinContext);
    }

    private void updateMasterDetailData(
            AiCrudConfig config,
            Object id,
            Map<String, Object> data,
            Set<String> allowedFields,
            RuntimeJoinContext joinContext) {
        masterDetailEngine.update(
                config,
                id,
                data,
                allowedFields,
                joinContext,
                buildWriteDataScopeCondition(config, config.getTableName(), null));
    }

    private void validateChildRow(
            RuntimeChildRelation relation, Map<String, Object> row, boolean create) {
        masterDetailEngine.validateChildRow(relation, row, create);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMainPayload(Map<String, Object> data) {
        if (data != null && data.get("main") instanceof Map<?, ?> main) {
            return (Map<String, Object>) main;
        }
        return data == null ? Map.of() : data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractChildrenPayload(Map<String, Object> data) {
        if (data != null && data.get("children") instanceof Map<?, ?> children) {
            return (Map<String, Object>) children;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeChildRows(Object value) {
        if (value instanceof List<?> rows) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Object resolvePayloadId(Map<String, Object> data, LowcodePrimaryKeyStrategy primaryKey) {
        if (data == null) {
            return null;
        }
        Object idValue = firstPresent(data, primaryKeyField(primaryKey), primaryKeyColumn(primaryKey), "id");
        if (idValue != null) {
            return idValue;
        }
        Object main = data.get("main");
        if (main instanceof Map<?, ?> map) {
            Map<String, Object> mainData = (Map<String, Object>) map;
            return firstPresent(mainData, primaryKeyField(primaryKey), primaryKeyColumn(primaryKey), "id");
        }
        return null;
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

    private boolean primaryKeyAutoIncrement(LowcodePrimaryKeyStrategy primaryKey) {
        return primaryKey == null || primaryKey.getAutoIncrement() == null || primaryKey.getAutoIncrement();
    }

    private String primaryKeyOrderBy(String tableAlias, String direction) {
        String column = primaryKeyColumn(currentPrimaryKey());
        return StringUtils.isBlank(tableAlias)
                ? column + " " + direction
                : tableAlias + "." + column + " " + direction;
    }

    private boolean isPrimaryKeyAlias(String fieldName, String columnName, LowcodePrimaryKeyStrategy primaryKey) {
        String primaryField = primaryKeyField(primaryKey);
        String primaryColumn = primaryKeyColumn(primaryKey);
        return equalsFieldAlias(fieldName, primaryField)
                || equalsFieldAlias(fieldName, primaryColumn)
                || equalsFieldAlias(columnName, primaryColumn);
    }

    private boolean equalsFieldAlias(String actual, String expected) {
        if (StringUtils.isBlank(actual) || StringUtils.isBlank(expected)) {
            return false;
        }
        return actual.equals(expected)
                || actual.equals(DynamicQueryGenerator.snakeToCamel(expected))
                || actual.equals(DynamicQueryGenerator.camelToSnake(expected));
    }

    private void removePrimaryKeyColumns(Map<String, Object> data, LowcodePrimaryKeyStrategy primaryKey) {
        if (data == null || data.isEmpty()) {
            return;
        }
        data.remove(primaryKeyField(primaryKey));
        data.remove(primaryKeyColumn(primaryKey));
        data.remove(DynamicQueryGenerator.snakeToCamel(primaryKeyColumn(primaryKey)));
        data.remove(DynamicQueryGenerator.camelToSnake(primaryKeyField(primaryKey)));
        data.remove("id");
    }

    private void putPrimaryKeyAlias(Map<String, Object> data, LowcodePrimaryKeyStrategy primaryKey, Object id) {
        if (data == null || id == null) {
            return;
        }
        data.put(primaryKeyField(primaryKey), id);
        data.put(primaryKeyColumn(primaryKey), id);
        data.put("id", id);
    }

    private void refreshRecordById(AiCrudConfig config, Object id) {
        if (id != null && StringUtils.isNotBlank(String.valueOf(id))) {
            storedAggregateRefreshService.refreshRecord(config, id);
        }
    }

    private Object firstPresent(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (StringUtils.isNotBlank(key) && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private void insertJoinedData(AiCrudConfig config,
                                  Map<String, Object> data,
                                  Set<String> allowedFields,
                                  RuntimeJoinContext joinContext) {
        Map<String, Object> primaryData = new LinkedHashMap<>();
        Map<String, Map<String, Object>> childDataMap = new LinkedHashMap<>();
        applyStoredFormulas(config, data);
        validateFieldValues(config, data);
        validateUniqueConstraints(config, config.getTableName(), data, null, null);
        splitRuntimeWriteData(data, allowedFields, joinContext, primaryData, childDataMap);
        if (primaryData.isEmpty()) {
            throw new BusinessException("没有可写入的主表字段");
        }
        applyMoneyStorageWrite(primaryData, config);
        applyStructuredFieldStorageWrite(primaryData, config);
        applyEncrypt(primaryData, config.getEncryptConfig());
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        Object mainId = repository.insertReturningKey(
                config.getTableName(),
                primaryData,
                primaryKeyColumn(primaryKey),
                primaryKeyAutoIncrement(primaryKey));
        putPrimaryKeyAlias(data, primaryKey, mainId);
        boolean childrenChanged = false;
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            Map<String, Object> childData = childDataMap.get(relation.modelCode());
            if (!hasWritableChildData(childData)) {
                continue;
            }
            Object relationValue = resolveMainRelationValue(relation, primaryData, mainId, null);
            if (relationValue == null) {
                continue;
            }
            validateChildRow(relation, childData, true);
            childData.put(relation.childFkColumn(), relationValue);
            repository.insert(relation.tableName(), childData);
            childrenChanged = true;
        }
        if (childrenChanged) {
            refreshRecordById(config, mainId);
        }
    }

    private void updateJoinedData(AiCrudConfig config,
                                  Object id,
                                  Map<String, Object> data,
                                  Set<String> allowedFields,
                                  RuntimeJoinContext joinContext) {
        DynamicCrudRepository.SqlCondition dataScopeCondition = buildWriteDataScopeCondition(config, config.getTableName(), null);
        Map<String, Object> authorizedMainRecord = repository.selectById(config.getTableName(), id, dataScopeCondition);
        if (authorizedMainRecord == null) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        Map<String, Object> primaryData = new LinkedHashMap<>();
        Map<String, Map<String, Object>> childDataMap = new LinkedHashMap<>();
        applyStoredFormulasForUpdate(config, config.getTableName(), id, data, dataScopeCondition, authorizedMainRecord);
        validateFieldValues(config, data);
        validateUniqueConstraints(config, config.getTableName(), data, authorizedMainRecord, id);
        splitRuntimeWriteData(data, allowedFields, joinContext, primaryData, childDataMap);
        removePrimaryKeyColumns(primaryData, currentPrimaryKey());
        removeMaskedDesensitizedWriteColumns(primaryData, config, config.getTableName());

        if (!primaryData.isEmpty()) {
            applyMoneyStorageWrite(primaryData, config);
            applyStructuredFieldStorageWrite(primaryData, config);
            applyEncrypt(primaryData, config.getEncryptConfig());
            int affected = repository.updateById(config.getTableName(), id, primaryData, dataScopeCondition);
            if (affected <= 0) {
                throw new BusinessException("无权限更新该数据或数据不存在");
            }
        }

        Map<String, Object> currentMainRecord = authorizedMainRecord;
        boolean childrenChanged = false;
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            Map<String, Object> childData = childDataMap.get(relation.modelCode());
            if (!hasWritableChildData(childData)) {
                continue;
            }
            if (currentMainRecord == null && !"id".equals(relation.mainColumn()) && !primaryData.containsKey(relation.mainColumn())) {
                currentMainRecord = repository.selectById(config.getTableName(), id);
            }
            Object relationValue = resolveMainRelationValue(relation, primaryData, id, currentMainRecord);
            if (relationValue == null) {
                continue;
            }
            validateChildRow(relation, childData, false);
            childData.put(relation.childFkColumn(), relationValue);
            Long childId = repository.selectFirstIdByColumn(relation.tableName(), relation.childFkColumn(), relationValue);
            if (childId == null) {
                repository.insert(relation.tableName(), childData);
            } else {
                repository.updateById(relation.tableName(), "id", childId, childData, null);
            }
            childrenChanged = true;
        }

        if (primaryData.isEmpty() && childDataMap.values().stream().noneMatch(this::hasWritableChildData)) {
            throw new BusinessException("没有可更新的字段");
        }
        if (childrenChanged) {
            refreshRecordById(config, id);
        }
    }

    private void splitRuntimeWriteData(Map<String, Object> data,
                                       Set<String> allowedFields,
                                       RuntimeJoinContext joinContext,
                                       Map<String, Object> primaryData,
                                       Map<String, Map<String, Object>> childDataMap) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<String, RuntimeChildRelation> relationMap = joinContext.childRelations().stream()
                .collect(Collectors.toMap(RuntimeChildRelation::modelCode, relation -> relation, (left, right) -> left, LinkedHashMap::new));
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (isImmutableWriteField(key) || !allowedFields.contains(key)) {
                continue;
            }
            RuntimeFieldRef fieldRef = joinContext.fields().get(key);
            if (fieldRef == null) {
                continue;
            }
            if (fieldRef.primary()) {
                primaryData.put(fieldRef.columnName(), entry.getValue());
                continue;
            }
            RuntimeChildRelation relation = relationMap.get(fieldRef.modelCode());
            if (relation == null) {
                continue;
            }
            childDataMap.computeIfAbsent(fieldRef.modelCode(), ignored -> new LinkedHashMap<>())
                    .put(fieldRef.columnName(), entry.getValue());
        }
    }

    private boolean hasWritableChildData(Map<String, Object> childData) {
        if (childData == null || childData.isEmpty()) {
            return false;
        }
        return childData.values().stream().anyMatch(value -> value != null && !(value instanceof String text && StringUtils.isBlank(text)));
    }

    private Object resolveMainRelationValue(RuntimeChildRelation relation,
                                            Map<String, Object> primaryData,
                                            Object mainId,
                                            Map<String, Object> currentMainRecord) {
        if ("id".equals(relation.mainColumn())) {
            return mainId;
        }
        if (primaryData.containsKey(relation.mainColumn())) {
            return primaryData.get(relation.mainColumn());
        }
        if (currentMainRecord != null) {
            return currentMainRecord.get(relation.mainColumn());
        }
        return null;
    }

    private boolean isImmutableWriteField(String key) {
        return IMMUTABLE_WRITE_FIELDS.contains(key);
    }

    private BusinessException emptyWriteFieldsException(AiCrudConfig config,
                                                        String action,
                                                        Set<String> submittedKeys,
                                                        Set<String> allowedFields) {
        String configKey = config == null ? "" : StringUtils.defaultString(config.getConfigKey());
        String tableName = config == null ? "" : StringUtils.defaultString(config.getTableName());
        long submittedBusiness = submittedKeys == null ? 0
                : submittedKeys.stream().filter(key -> !isImmutableWriteField(key)
                        && !DataAuditPayloadSupport.PAYLOAD_KEY.equals(key)
                        && !"main".equals(key)
                        && !"children".equals(key)).count();
        return new BusinessException("没有可" + action + "的字段（对象 " + configKey + " / 表 " + tableName + "）。"
                + "请求里业务字段约 " + submittedBusiness + " 个，白名单可写字段 "
                + (allowedFields == null ? 0 : allowedFields.size()) + " 个，过滤后为空。"
                + "请检查：1) 字段已在表单设计中可见且已同步到物理表；"
                + "2) 字段未设为隐藏/禁用/只读；"
                + "3) 字段编码与模型一致；"
                + "4) 不要只提交 id 等系统字段。");
    }

    private BusinessException emptyMasterDetailUpdateException(AiCrudConfig config,
                                                               Map<String, Object> data,
                                                               Set<String> allowedFields,
                                                               RuntimeJoinContext joinContext) {
        Map<String, Object> childrenPayload = extractChildrenPayload(data);
        String expectedChildren = joinContext == null || joinContext.childRelations() == null
                ? ""
                : joinContext.childRelations().stream()
                        .map(RuntimeChildRelation::modelCode)
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .collect(java.util.stream.Collectors.joining("、"));
        String submittedChildren = childrenPayload.isEmpty()
                ? "无"
                : String.join("、", childrenPayload.keySet());
        BusinessException base = emptyWriteFieldsException(config, "更新",
                data == null ? Set.of() : data.keySet(), allowedFields);
        return new BusinessException(base.getMessage()
                + " 主子表额外检查：已提交子表键=[" + submittedChildren + "]，"
                + "配置期望子表=[" + StringUtils.defaultIfBlank(expectedChildren, "无") + "]。"
                + "若只改了子表，请确认 children 下的对象编码与主子表配置一致。");
    }

    private Set<String> buildAllowedWriteFields(AiCrudConfig config, String tableName) {
        return writeFieldPolicy.buildAllowedWriteFields(config, tableName);
    }

    private Map<String, Object> filterInternalWriteData(
            AiCrudConfig config, String tableName, Map<String, Object> data) {
        return writeFieldPolicy.filterInternalWriteData(config, tableName, data);
    }

    private Map<String, Object> filterCommandWriteData(
            AiCrudConfig config, String tableName, Map<String, ?> data) {
        return writeFieldPolicy.filterCommandWriteData(config, tableName, data);
    }

    private Set<String> collectCommandFields(AiCrudConfig config) {
        return writeFieldPolicy.collectCommandFields(config);
    }

    private Map<String, BigDecimal> mapCommandDecimalFields(
            AiCrudConfig config, String tableName, Map<String, BigDecimal> values) {
        return writeFieldPolicy.mapCommandDecimalFields(config, tableName, values);
    }

    private DynamicCrudRepository.SqlCondition buildCommandExpectedCondition(
            AiCrudConfig config, String tableName, Map<String, Object> expectedFields) {
        return writeFieldPolicy.buildCommandExpectedCondition(config, tableName, expectedFields);
    }

    private DynamicCrudRepository.SqlCondition buildCommandNumericCondition(
            AiCrudConfig config,
            String tableName,
            List<Map<String, Object>> numericConstraints) {
        return writeFieldPolicy.buildCommandNumericCondition(config, tableName, numericConstraints);
    }

    private DynamicCrudRepository.SqlCondition combineConditions(
            DynamicCrudRepository.SqlCondition first,
            DynamicCrudRepository.SqlCondition second) {
        return writeFieldPolicy.combineConditions(first, second);
    }

    private Map<String, String> buildRuntimeColumnMapping(AiCrudConfig config, String tableName) {
        return writeFieldPolicy.buildRuntimeColumnMapping(config, tableName);
    }

    private BusinessException missingRuntimeColumn(
            AiCrudConfig config, String tableName, String fieldName, String columnName) {
        return writeFieldPolicy.missingRuntimeColumn(config, tableName, fieldName, columnName);
    }

    private Set<String> collectInternalWriteFields(AiCrudConfig config, String tableName) {
        return writeFieldPolicy.collectInternalWriteFields(config, tableName);
    }

    private void validateUniqueConstraints(AiCrudConfig config,
                                           String tableName,
                                           Map<String, Object> data,
                                           Map<String, Object> beforeRecord,
                                           Object excludeId) {
        uniquenessValidator.validate(
                config, tableName, data, beforeRecord, excludeId,
                primaryKeyColumn(currentPrimaryKey()));
    }

    // ==================== 删除操作 ====================

    /**
     * 删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String configKey, Object id) {
        deleteById(configKey, id, new LinkedHashMap<>());
    }

    /**
     * 批量删除：一条 SQL 处理多条记录，避免逐条网络往返
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteByIds(String configKey, List<?> ids) {
        return batchDeleteByIds(configKey, ids, Map.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteByIds(String configKey, List<?> ids, Map<String, Object> auditPayload) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            // 同一事务内开一次采集会话；repository.deleteByIds 会对每个 id prepare/afterWrite
            openDataAudit(config, ids.get(0), DataAuditSourceType.FORM, DataAuditEventType.DELETE,
                    auditPayload == null ? Map.of() : auditPayload, false);
            String tableName = config.getTableName();
            LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
            String pkColumn = primaryKeyColumn(primaryKey);
            DynamicCrudRepository.SqlCondition dataScopeCondition = buildWriteDataScopeCondition(config, tableName, null);

            // 一次查询所有待删除记录，验证权限与数据存在性
            List<Map<String, Object>> beforeRecords = repository.selectByIds(tableName, pkColumn, ids, dataScopeCondition);
            if (beforeRecords.isEmpty()) {
                throw new BusinessException("无权限删除该数据或数据不存在");
            }

            // 提取实际查到的 ID，便于比对差异
            List<Object> foundIds = beforeRecords.stream()
                    .map(r -> r.get(pkColumn))
                    .filter(Objects::nonNull)
                    .toList();
            if (foundIds.size() < ids.size()) {
                throw new BusinessException("部分数据无权限删除或不存在");
            }

            boolean logicDelete = repository.hasDelFlag(tableName);
            Map<String, Map<String, Object>> beforeById = new LinkedHashMap<>();
            for (Map<String, Object> record : beforeRecords) {
                Object rawId = record.get(pkColumn);
                if (rawId == null) {
                    rawId = record.get("id");
                }
                String key = DataAuditRecordIds.normalize(rawId);
                if (key != null) {
                    beforeById.put(key, record);
                }
            }
            int affected = repository.deleteByIds(tableName, pkColumn, ids, logicDelete, dataScopeCondition, beforeById);

            // 逐条刷新聚合根缓存
            for (Map<String, Object> record : beforeRecords) {
                storedAggregateRefreshService.refreshAfterChildDelete(config, record);
            }
            return affected;
        }
    }

    /**
     * 暴露运行时配置给动态导入导出服务，仍统一走发布态校验。
     */
    public AiCrudConfig getRuntimeConfig(String configKey) {
        return getConfig(configKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public int removeWithAudit(String configKey, DataAuditRemoveDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException("请选择要删除的数据");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(dto.getReason())) {
            context.put("reason", dto.getReason());
        }
        // 删除不校验 expectedRevision；列表策略元数据 revision 常为 0
        payload.put(DataAuditPayloadSupport.PAYLOAD_KEY, context);
        return batchDeleteByIds(configKey, dto.getIds(), payload);
    }

    private AutoCloseable openDataAudit(AiCrudConfig config,
                                        Object recordId,
                                        DataAuditSourceType sourceType,
                                        DataAuditEventType eventType,
                                        Map<String, Object> payload,
                                        boolean requireRevision) {
        Map<String, Object> safePayload = payload == null ? new LinkedHashMap<>() : payload;
        DataAuditWriteContextDTO context = DataAuditPayloadSupport.extractAndStrip(safePayload);
        if (dataAuditCaptureService == null) {
            return () -> {
            };
        }
        return dataAuditCaptureService.open(config, recordId, sourceType, eventType, context, requireRevision);
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

    /** 列表挂策略级审计元数据，避免删除前再逐条拉详情。 */
    private void attachDataAuditPolicyMeta(AiCrudConfig config, List<Map<String, Object>> records) {
        if (dataAuditCaptureService == null || records == null || records.isEmpty()) {
            return;
        }
        dataAuditCaptureService.attachPolicyMeta(config, records);
    }

    private void deleteById(String configKey, Object id, Map<String, Object> auditPayload) {
        AiCrudConfig config = getConfig(configKey);
        assertRuntimeWritable(config);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            openDataAudit(config, id, DataAuditSourceType.FORM, DataAuditEventType.DELETE, auditPayload, true);
            String tableName = config.getTableName();
            LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
            DynamicCrudRepository.SqlCondition dataScopeCondition = buildWriteDataScopeCondition(config, tableName, null);
            Map<String, Object> beforeRecord = repository.selectById(
                    tableName, primaryKeyColumn(primaryKey), id, dataScopeCondition);
            if (beforeRecord == null) {
                throw new BusinessException("无权限删除该数据或数据不存在");
            }
            boolean logicDelete = repository.hasDelFlag(tableName);
            int affected = repository.deleteById(tableName, primaryKeyColumn(primaryKey), id, logicDelete, dataScopeCondition);
            if (affected <= 0) {
                throw new BusinessException("无权限删除该数据或数据不存在");
            }
            storedAggregateRefreshService.refreshAfterChildDelete(config, beforeRecord);
        }
    }

    public Object resolveRecordId(String configKey, Map<String, Object> data) {
        AiCrudConfig config = getConfig(configKey);
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            return resolvePayloadId(data, currentPrimaryKey());
        }
    }

    private LowcodeRuntimeDataSourceContextHolder.Scope useRuntimeContext(AiCrudConfig config) {
        LowcodeRuntimeDataSourceContext context = runtimeDataSourceResolver.resolve(config);
        config.setTableName(StringUtils.defaultIfBlank(context.getTableName(), config.getTableName()));
        return LowcodeRuntimeDataSourceContextHolder.use(context);
    }

    private void assertRuntimeWritable(AiCrudConfig config) {
        LowcodeRuntimeDataSourceContext context = runtimeDataSourceResolver.resolve(config);
        if (context.isReadonly() || !context.isAllowWrite()) {
            throw new BusinessException("当前运行数据源为只读或未开放写入");
        }
    }

    private RuntimeJoinContext buildRuntimeJoinContext(AiCrudConfig config) {
        return runtimeRelationPlanner.buildRuntimeJoinContext(
                config, this::buildRuntimeColumnMapping, this::preferReadableChildRelation);
    }

    private boolean isMasterDetailRuntime(AiCrudConfig config) {
        return runtimeRelationPlanner.isMasterDetailRuntime(config);
    }

    private String resolveChildColumn(String fieldName, Map<String, String> columnMapping) {
        return runtimeRelationPlanner.resolveChildColumn(fieldName, columnMapping);
    }

    private String resolvePrimaryColumn(String fieldName, Map<String, String> columnMapping) {
        return runtimeRelationPlanner.resolvePrimaryColumn(fieldName, columnMapping);
    }

    private String buildJoinOrderBy(String orderByColumn, String isAsc, RuntimeJoinContext context) {
        return runtimeRelationPlanner.buildJoinOrderBy(orderByColumn, isAsc, context);
    }

    private boolean requiresJoinedPageQuery(AiCrudConfig config,
                                            PageQuery pageQuery,
                                            Map<String, Object> searchParams,
                                            RuntimeJoinContext context) {
        return runtimeRelationPlanner.requiresJoinedPageQuery(config, pageQuery, searchParams, context);
    }

    private boolean requiresJoinedExportQuery(AiCrudConfig config,
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

    private void stampExpandedListRowKeys(List<Map<String, Object>> rows,
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


    private void applyMoneyStorageWrite(Map<String, Object> data, AiCrudConfig config) {
        fieldValuePipeline.applyMoneyStorageWrite(data, config);
    }

    private void applyStructuredFieldStorageWrite(Map<String, Object> data, AiCrudConfig config) {
        fieldValuePipeline.applyStructuredFieldStorageWrite(data, config);
    }

    private void applyEncrypt(Map<String, Object> data, String encryptConfigJson) {
        fieldValuePipeline.applyEncrypt(data, encryptConfigJson);
    }

    private void removeMaskedDesensitizedWriteColumns(Map<String, Object> data,
                                                       AiCrudConfig config,
                                                       String tableName) {
        fieldValuePipeline.removeMaskedDesensitizedWriteColumns(
                data, config, buildRuntimeColumnMapping(config, tableName));
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }


    // ==================== 配置加载 ====================

    /**
     * 获取配置
     */
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

    private Set<String> buildAllowedSearchFields(AiCrudConfig config) {
        Set<String> fields = new HashSet<>(buildAllowedCustomFields(config));
        if (treeQueryEngine.isTreeRuntime(config)) {
            LowcodeTreeConfig treeConfig = treeQueryEngine.resolveTreeConfig(config);
            if (StringUtils.isNotBlank(treeConfig.getFilterField())) {
                fields.add(treeConfig.getFilterField());
            }
        }
        return fields;
    }

    private Map<String, String> buildEffectiveSearchTypeMap(AiCrudConfig config,
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

    /**
     * 将显式传入的 searchParams 键扩展为允许搜索字段。
     * 解决选择器弹窗过滤字段不在 CRUD 配置 schemas 中时被静默跳过的问题。
     * 仅当字段名对应的列在表中真实存在时才放行，防止任意字段注入。
     */
    private void expandAllowedSearchFieldsFromParams(Map<String, Object> searchParams,
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
                // 未配置搜索类型时默认精确匹配，适合字典值、状态码等精确筛选场景
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
        RuntimeJoinContext joinContext = buildRuntimeJoinContext(config);
        return new ExportQueryContext(config, tableName, columnMapping, allowedSearchFields, searchTypeMap,
                searchParams, joinContext);
    }

    private DynamicCrudRepository.SqlCondition buildDataScopeCondition(AiCrudConfig config, String tableName, String tableAlias) {
        return dynamicDataScopeService.buildCondition(config, tableName, tableAlias);
    }

    private DynamicCrudRepository.SqlCondition buildWriteDataScopeCondition(AiCrudConfig config, String tableName, String tableAlias) {
        return dynamicDataScopeService.buildWriteCondition(config, tableName, tableAlias);
    }

    /**
     * 内部字段回写（流程回调 / 系统驱动）在无 Web 登录会话时无法构造用户数据权限。
     * 此时跳过用户范围条件，仅依赖租户隔离；有登录会话时仍走完整写权限。
     */
    private DynamicCrudRepository.SqlCondition buildInternalWriteDataScopeCondition(AiCrudConfig config,
                                                                                    String tableName,
                                                                                    String tableAlias) {
        if (!hasResolvableDataScopeUser()) {
            log.debug("[DynamicCrud] 内部字段回写无用户数据权限上下文，跳过写范围条件: configKey={}, table={}",
                    config == null ? null : config.getConfigKey(), tableName);
            return null;
        }
        return buildWriteDataScopeCondition(config, tableName, tableAlias);
    }

    private boolean hasResolvableDataScopeUser() {
        if (ExecutionIdentityContextHolder.current()
                .map(identity -> identity.loginUser())
                .filter(user -> user.getUserId() != null)
                .isPresent()) {
            return true;
        }
        try {
            LoginUser loginUser = SessionHelper.getLoginUser();
            return loginUser != null && loginUser.getUserId() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private DynamicCrudRepository.SqlCondition buildDataScopeCondition(AiCrudConfig config,
                                                                       String tableName,
                                                                       String tableAlias,
                                                                       DataScopeContext dataScopeContext) {
        return dynamicDataScopeService.buildCondition(config, tableName, tableAlias, dataScopeContext);
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


    // ==================== Formula Runtime Helpers ====================

    private Map<String, Object> applyStoredFormulasForUpdate(
            AiCrudConfig config,
            String tableName,
            Object id,
            Map<String, Object> data,
            DynamicCrudRepository.SqlCondition dataScopeCondition) {
        return writeFieldPolicy.applyStoredFormulasForUpdate(
                config, tableName, id, data, dataScopeCondition);
    }

    private Map<String, Object> applyStoredFormulasForUpdate(
            AiCrudConfig config,
            String tableName,
            Object id,
            Map<String, Object> data,
            DynamicCrudRepository.SqlCondition dataScopeCondition,
            Map<String, Object> existingRecord) {
        return writeFieldPolicy.applyStoredFormulasForUpdate(
                config, tableName, id, data, dataScopeCondition, existingRecord);
    }

    private void applyStoredFormulas(AiCrudConfig config, Map<String, Object> data) {
        writeFieldPolicy.applyStoredFormulas(config, data);
    }

    private LowcodeModelSchema parseModelSchema(AiCrudConfig config) {
        return writeFieldPolicy.parseModelSchema(config);
    }

    private void validateFieldValues(AiCrudConfig config, Map<String, Object> data) {
        writeFieldPolicy.validateFieldValues(config, data);
    }

    private int normalizeExportPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 1000;
        }
        return Math.min(pageSize, 5000);
    }
}
