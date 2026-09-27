package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.service.formula.StoredAggregateRefreshService;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 普通 Join 运行模型的跨表新增与更新引擎。 */
final class DynamicCrudJoinedPersistenceEngine {

    private static final Set<String> IMMUTABLE_WRITE_FIELDS = Set.of(
            "id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time",
            "delFlag", "del_flag"
    );

    private final DynamicCrudRepository repository;
    private final StoredAggregateRefreshService storedAggregateRefreshService;
    private final DynamicCrudWriteFieldPolicy writeFieldPolicy;
    private final DynamicCrudUniquenessValidator uniquenessValidator;
    private final DynamicCrudFieldValuePipeline fieldValuePipeline;
    private final DynamicCrudMasterDetailEngine masterDetailEngine;

    DynamicCrudJoinedPersistenceEngine(
            DynamicCrudRepository repository,
            StoredAggregateRefreshService storedAggregateRefreshService,
            DynamicCrudWriteFieldPolicy writeFieldPolicy,
            DynamicCrudUniquenessValidator uniquenessValidator,
            DynamicCrudFieldValuePipeline fieldValuePipeline,
            DynamicCrudMasterDetailEngine masterDetailEngine) {
        this.repository = repository;
        this.storedAggregateRefreshService = storedAggregateRefreshService;
        this.writeFieldPolicy = writeFieldPolicy;
        this.uniquenessValidator = uniquenessValidator;
        this.fieldValuePipeline = fieldValuePipeline;
        this.masterDetailEngine = masterDetailEngine;
    }

    void insert(AiCrudConfig config,
                                  Map<String, Object> data,
                                  Set<String> allowedFields,
                                  RuntimeJoinContext joinContext) {
        Map<String, Object> primaryData = new LinkedHashMap<>();
        Map<String, Map<String, Object>> childDataMap = new LinkedHashMap<>();
        writeFieldPolicy.applyStoredFormulas(config, data);
        writeFieldPolicy.validateFieldValues(config, data);
        validateUniqueConstraints(config, config.getTableName(), data, null, null);
        splitRuntimeWriteData(data, allowedFields, joinContext, primaryData, childDataMap);
        if (primaryData.isEmpty()) {
            throw new BusinessException("没有可写入的主表字段");
        }
        fieldValuePipeline.applyMoneyStorageWrite(primaryData, config);
        fieldValuePipeline.applyStructuredFieldStorageWrite(primaryData, config);
        fieldValuePipeline.applyEncrypt(primaryData, config.getEncryptConfig());
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
            masterDetailEngine.validateChildRow(relation, childData, true);
            childData.put(relation.childFkColumn(), relationValue);
            repository.insert(relation.tableName(), childData);
            childrenChanged = true;
        }
        if (childrenChanged) {
            refreshRecordById(config, mainId);
        }
    }

    void update(AiCrudConfig config,
                Object id,
                Map<String, Object> data,
                Set<String> allowedFields,
                RuntimeJoinContext joinContext,
                DynamicCrudRepository.SqlCondition dataScopeCondition) {
        Map<String, Object> authorizedMainRecord = repository.selectById(config.getTableName(), id, dataScopeCondition);
        if (authorizedMainRecord == null) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        Map<String, Object> primaryData = new LinkedHashMap<>();
        Map<String, Map<String, Object>> childDataMap = new LinkedHashMap<>();
        writeFieldPolicy.applyStoredFormulasForUpdate(config, config.getTableName(), id, data, dataScopeCondition, authorizedMainRecord);
        writeFieldPolicy.validateFieldValues(config, data);
        validateUniqueConstraints(config, config.getTableName(), data, authorizedMainRecord, id);
        splitRuntimeWriteData(data, allowedFields, joinContext, primaryData, childDataMap);
        removePrimaryKeyColumns(primaryData, currentPrimaryKey());
        removeMaskedWriteColumns(primaryData, config, config.getTableName());

        if (!primaryData.isEmpty()) {
            fieldValuePipeline.applyMoneyStorageWrite(primaryData, config);
            fieldValuePipeline.applyStructuredFieldStorageWrite(primaryData, config);
            fieldValuePipeline.applyEncrypt(primaryData, config.getEncryptConfig());
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
            masterDetailEngine.validateChildRow(relation, childData, false);
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

    private void removeMaskedWriteColumns(Map<String, Object> data, AiCrudConfig config, String tableName) {
        fieldValuePipeline.removeMaskedDesensitizedWriteColumns(
                data, config, writeFieldPolicy.buildRuntimeColumnMapping(config, tableName));
    }

    private void validateUniqueConstraints(
            AiCrudConfig config,
            String tableName,
            Map<String, Object> data,
            Map<String, Object> beforeRecord,
            Object excludeId) {
        uniquenessValidator.validate(
                config, tableName, data, beforeRecord, excludeId,
                primaryKeyColumn(currentPrimaryKey()));
    }

    private boolean isImmutableWriteField(String key) {
        if (StringUtils.isBlank(key)) {
            return true;
        }
        return IMMUTABLE_WRITE_FIELDS.contains(key)
                || IMMUTABLE_WRITE_FIELDS.contains(DynamicQueryGenerator.snakeToCamel(key))
                || IMMUTABLE_WRITE_FIELDS.contains(DynamicQueryGenerator.camelToSnake(key));
    }

    private void refreshRecordById(AiCrudConfig config, Object id) {
        if (id != null && StringUtils.isNotBlank(String.valueOf(id))) {
            storedAggregateRefreshService.refreshRecord(config, id);
        }
    }
}
