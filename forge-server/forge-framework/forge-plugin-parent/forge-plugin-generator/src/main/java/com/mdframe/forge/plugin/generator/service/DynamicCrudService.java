package com.mdframe.forge.plugin.generator.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.dto.CustomQueryExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditRemoveDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditWriteContextDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
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
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
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

/**
 * 动态CRUD服务
 * 基于DynamicCrudRepository实现，支持配置驱动的通用CRUD操作
 * 
 * @author forge
 */
@Slf4j
@Service
public class DynamicCrudService {

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
    private final DynamicCrudMutationCoordinator mutationCoordinator;

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
        this.mutationCoordinator = new DynamicCrudMutationCoordinator(
                repository,
                objectMapper,
                storedAggregateRefreshService,
                uniquenessValidator,
                generatedFieldPolicy,
                fieldValuePipeline,
                writeFieldPolicy,
                runtimeRelationPlanner,
                masterDetailEngine,
                joinedPersistenceEngine);
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
            return mutationCoordinator.insertForm(
                    config, data, id -> readCoordinator.selectById(config, id));
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
            return mutationCoordinator.insertInternal(
                    config, data, id -> readCoordinator.selectById(config, id));
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
            return mutationCoordinator.insertCommand(
                    config, data, id -> readCoordinator.selectById(config, id));
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
            Object id = mutationCoordinator.resolvePayloadId(data);
            if (id == null) {
                throw new BusinessException("更新操作缺少id");
            }
            openDataAudit(config, id, DataAuditSourceType.FORM, DataAuditEventType.UPDATE, data, true);
            mutationCoordinator.updateForm(
                    config, id, data,
                    buildWriteDataScopeCondition(config, config.getTableName(), null));
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
            mutationCoordinator.updateRuntimeFields(
                    config, id, data,
                    buildInternalWriteDataScopeCondition(config, config.getTableName(), null));
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
            mutationCoordinator.updateAutomationFields(
                    config, id, fields,
                    buildInternalWriteDataScopeCondition(config, config.getTableName(), null));
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
            mutationCoordinator.updateCommandFields(
                    config, id, fields, expectedFields,
                    buildWriteDataScopeCondition(config, config.getTableName(), null));
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
            mutationCoordinator.adjustCommandNumbers(
                    config, id, deltas, minimums, maximums, expectedFields,
                    buildWriteDataScopeCondition(config, config.getTableName(), null));
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
            mutationCoordinator.assertCommandRecord(
                    config, id, expectedFields, numericConstraints,
                    buildWriteDataScopeCondition(config, config.getTableName(), null));
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

    private String primaryKeyColumn(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getColumnName(), "id");
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
            return mutationCoordinator.resolvePayloadId(data);
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
                config,
                writeFieldPolicy::buildRuntimeColumnMapping,
                masterDetailEngine::preferReadableChildRelation);
    }

    private boolean isMasterDetailRuntime(AiCrudConfig config) {
        return runtimeRelationPlanner.isMasterDetailRuntime(config);
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

}
