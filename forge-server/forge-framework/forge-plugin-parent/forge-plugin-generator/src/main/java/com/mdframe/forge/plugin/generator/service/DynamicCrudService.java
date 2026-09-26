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

    private static final Set<String> IMMUTABLE_WRITE_FIELDS = Set.of(
            "id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time", "delFlag", "del_flag"
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
    private final DynamicCrudJoinedPersistenceEngine joinedPersistenceEngine;
    private final DynamicCrudReadCoordinator readCoordinator;

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
        this.joinedPersistenceEngine = new DynamicCrudJoinedPersistenceEngine(
                repository,
                storedAggregateRefreshService,
                writeFieldPolicy,
                uniquenessValidator,
                fieldValuePipeline,
                masterDetailEngine);
        this.readCoordinator = new DynamicCrudReadCoordinator(
                repository,
                configService,
                objectMapper,
                dynamicDataScopeService,
                runtimeDataSourceResolver,
                dataAuditCaptureService,
                treeQueryEngine,
                fieldValuePipeline,
                runtimeRelationPlanner,
                writeFieldPolicy,
                masterDetailEngine);
    }

    // ==================== 查询操作 ====================

    public Page<Map<String, Object>> selectPage(
            String configKey, PageQuery pageQuery, DynamicCrudQuery query) {
        return readCoordinator.selectPage(configKey, pageQuery, query);
    }

    public List<Map<String, Object>> selectExportRows(
            String configKey, DynamicCrudQuery query, Integer maxRows) {
        return readCoordinator.selectExportRows(configKey, query, maxRows);
    }

    public List<Map<String, Object>> selectScheduledCandidateRows(
            String configKey,
            String dueField,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            Integer batchSize) {
        return readCoordinator.selectScheduledCandidateRows(
                configKey, dueField, windowStart, windowEnd, batchSize);
    }

    public long countExportRows(
            String configKey, DynamicCrudQuery query, DataScopeContext dataScopeContext) {
        return readCoordinator.countExportRows(configKey, query, dataScopeContext);
    }

    public List<Map<String, Object>> selectExportPageRows(
            String configKey,
            DynamicCrudQuery query,
            Integer pageNum,
            Integer pageSize,
            DataScopeContext dataScopeContext) {
        return readCoordinator.selectExportPageRows(
                configKey, query, pageNum, pageSize, dataScopeContext);
    }

    public List<Map<String, Object>> selectTree(String configKey) {
        return readCoordinator.selectTree(configKey);
    }

    public List<Map<String, Object>> selectTree(
            String configKey, String parentValue, String parentId, String loadMode) {
        return readCoordinator.selectTree(configKey, parentValue, parentId, loadMode);
    }

    public List<Map<String, Object>> selectTree(
            String configKey,
            String parentValue,
            String parentId,
            String loadMode,
            String orderByColumn,
            String isAsc) {
        return readCoordinator.selectTree(
                configKey, parentValue, parentId, loadMode, orderByColumn, isAsc);
    }

    public Page<Map<String, Object>> selectCustomPage(
            String configKey, CustomQueryExecuteDTO request) {
        return readCoordinator.selectCustomPage(configKey, request);
    }

    public Map<String, Object> selectById(String configKey, Object id) {
        return readCoordinator.selectById(configKey, id);
    }

    public Map<String, Object> selectById(AiCrudConfig config, Object id) {
        return readCoordinator.selectById(config, id);
    }

    public Map<String, Object> selectByIdAllowDraft(String configKey, Object id) {
        return readCoordinator.selectByIdAllowDraft(configKey, id);
    }

    public record PrintRow(Map<String, Object> columns, Map<String, Object> values) {
    }

    public PrintRow selectPrintById(AiCrudConfig config, Object id) {
        return readCoordinator.selectPrintById(config, id);
    }

    public List<Map<String, Object>> selectPrintChildren(
            AiCrudConfig config, String foreignKey, Object parentValue) {
        return readCoordinator.selectPrintChildren(config, foreignKey, parentValue);
    }

    public Map<Object, Map<String, Object>> selectByIds(
            String configKey, Collection<?> ids) {
        return readCoordinator.selectByIds(configKey, ids);
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

    private Object firstPresent(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (StringUtils.isNotBlank(key) && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private void insertJoinedData(
            AiCrudConfig config,
            Map<String, Object> data,
            Set<String> allowedFields,
            RuntimeJoinContext joinContext) {
        joinedPersistenceEngine.insert(config, data, allowedFields, joinContext);
    }

    private void updateJoinedData(
            AiCrudConfig config,
            Object id,
            Map<String, Object> data,
            Set<String> allowedFields,
            RuntimeJoinContext joinContext) {
        joinedPersistenceEngine.update(
                config,
                id,
                data,
                allowedFields,
                joinContext,
                buildWriteDataScopeCondition(config, config.getTableName(), null));
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

    private void validateFieldValues(AiCrudConfig config, Map<String, Object> data) {
        writeFieldPolicy.validateFieldValues(config, data);
    }

}
