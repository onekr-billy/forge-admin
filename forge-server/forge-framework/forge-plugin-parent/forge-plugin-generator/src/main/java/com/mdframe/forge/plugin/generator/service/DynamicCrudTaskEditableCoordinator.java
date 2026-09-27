package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService.TaskChildPermission;
import com.mdframe.forge.plugin.generator.service.formula.StoredAggregateRefreshService;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 审批节点可编辑主子表数据的权限校验与持久化协调器。 */
final class DynamicCrudTaskEditableCoordinator {

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

    DynamicCrudTaskEditableCoordinator(
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

    Map<String, Object> update(
            AiCrudConfig config,
            Object id,
            Map<String, Object> data,
            Set<String> writableMainFields,
            Map<String, TaskChildPermission> childPermissions,
            RuntimeJoinContext joinContext,
            boolean masterDetailRuntime,
            DynamicCrudRepository.SqlCondition dataScopeCondition) {
        Set<String> allowedMain = writableMainFields == null ? Set.of() : Set.copyOf(writableMainFields);
        Map<String, TaskChildPermission> allowedChildren =
                childPermissions == null ? Map.of() : childPermissions;
        Map<String, Object> mainPayload = extractMainPayload(data);
        validateTaskMainPayload(mainPayload, allowedMain);
        Map<String, Object> childrenPayload = extractChildrenPayload(data);
        if (!childrenPayload.isEmpty() && (joinContext == null || !masterDetailRuntime)) {
            throw new BusinessException("当前业务对象不支持待办子表编辑");
        }
        if (joinContext != null && !childrenPayload.isEmpty()) {
            allowedChildren = normalizeTaskChildPermissions(config, joinContext, allowedChildren);
            childrenPayload = normalizeTaskChildrenPayload(config, joinContext, childrenPayload);
        }
        validateTaskChildrenPayload(childrenPayload, joinContext, allowedChildren);
        if (joinContext != null && masterDetailRuntime) {
            updateTaskMasterDetailData(
                    config, id, mainPayload, childrenPayload, allowedMain,
                    allowedChildren, joinContext, dataScopeCondition);
            return null;
        }
        Map<String, Object> updateData = new LinkedHashMap<>(mainPayload);
        updateData.put(primaryKeyField(currentPrimaryKey()), id);
        if (updateData.size() <= 1) {
            throw new BusinessException("未提交可编辑业务字段");
        }
        return updateData;
    }

    private Map<String, TaskChildPermission> normalizeTaskChildPermissions(
            AiCrudConfig config,
            RuntimeJoinContext joinContext,
            Map<String, TaskChildPermission> permissions) {
        if (joinContext == null || permissions == null || permissions.isEmpty()) {
            return permissions == null ? Map.of() : permissions;
        }
        Map<String, TaskChildPermission> result = new LinkedHashMap<>();
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            TaskChildPermission permission = permissions.get(relation.modelCode());
            if (permission == null) {
                for (String alias : runtimeChildKeyAliases(config, relation)) {
                    permission = permissions.get(alias);
                    if (permission != null) {
                        break;
                    }
                }
            }
            if (permission != null) {
                result.put(relation.modelCode(), permission);
            }
        }
        return result;
    }

    private Map<String, Object> normalizeTaskChildrenPayload(
            AiCrudConfig config,
            RuntimeJoinContext joinContext,
            Map<String, Object> payload) {
        if (payload == null || payload.isEmpty() || joinContext == null) {
            return payload == null ? Map.of() : payload;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            RuntimeChildRelation relation = resolveTaskChildRelation(config, joinContext, entry.getKey());
            String canonicalKey = relation == null ? entry.getKey() : relation.modelCode();
            if (result.containsKey(canonicalKey)) {
                throw new BusinessException("子表 payload 重复指定关系: " + entry.getKey());
            }
            result.put(canonicalKey, entry.getValue());
        }
        return result;
    }

    private RuntimeChildRelation resolveTaskChildRelation(AiCrudConfig config,
                                                           RuntimeJoinContext joinContext,
                                                           String childKey) {
        if (joinContext == null || StringUtils.isBlank(childKey)) {
            return null;
        }
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            if (runtimeChildKeyAliases(config, relation).stream()
                    .anyMatch(alias -> StringUtils.equalsIgnoreCase(alias, childKey))) {
                return relation;
            }
        }
        return null;
    }

    private Set<String> runtimeChildKeyAliases(AiCrudConfig config, RuntimeChildRelation relation) {
        Set<String> aliases = new LinkedHashSet<>();
        if (relation == null) {
            return aliases;
        }
        addNonBlankAlias(aliases, relation.modelCode());
        addNonBlankAlias(aliases, relation.tableName());
        JsonNode childNode = masterDetailEngine.findMasterDetailChildNode(config, relation);
        if (childNode != null && childNode.isObject()) {
            for (String fieldName : List.of("modelCode", "relationKey", "key", "tableName", "field")) {
                addNonBlankAlias(aliases, firstText(childNode, fieldName));
            }
        }
        return aliases;
    }

    private void addNonBlankAlias(Set<String> aliases, String value) {
        if (StringUtils.isNotBlank(value)) {
            aliases.add(value.trim());
        }
    }

    private void validateTaskMainPayload(Map<String, Object> payload, Set<String> writableFields) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        for (String field : payload.keySet()) {
            if (isImmutableWriteField(field)) {
                continue;
            }
            if (!containsFieldAlias(writableFields, field)) {
                throw new BusinessException("当前节点不允许编辑字段: " + field);
            }
        }
    }

    private void validateTaskChildrenPayload(Map<String, Object> payload,
                                             RuntimeJoinContext joinContext,
                                             Map<String, TaskChildPermission> permissions) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        if (joinContext == null) {
            throw new BusinessException("当前业务对象未配置可编辑子表");
        }
        Map<String, RuntimeChildRelation> relations = joinContext.childRelations().stream()
                .collect(Collectors.toMap(RuntimeChildRelation::modelCode, relation -> relation,
                        (left, right) -> left, LinkedHashMap::new));
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            RuntimeChildRelation relation = relations.get(entry.getKey());
            TaskChildPermission permission = permissions.get(entry.getKey());
            if (relation == null || permission == null || !permission.readable()) {
                throw new BusinessException("当前节点不允许编辑子表: " + entry.getKey());
            }
            for (Map<String, Object> row : normalizeChildRows(entry.getValue())) {
                validateTaskChildRowPayload(row, relation, permission);
            }
        }
    }

    private void validateTaskChildRowPayload(Map<String, Object> row,
                                             RuntimeChildRelation relation,
                                             TaskChildPermission permission) {
        if (row == null) {
            throw new BusinessException("子表行数据不能为空");
        }
        Object rowId = masterDetailEngine.resolveChildRowId(row);
        boolean deleted = masterDetailEngine.isDeletedChildRow(row);
        if (deleted && !permission.allowDelete()) {
            throw new BusinessException("当前节点不允许删除子表行");
        }
        if (!deleted && rowId == null && !permission.allowCreate()) {
            throw new BusinessException("当前节点不允许新增子表行");
        }
        if (!deleted && rowId != null && !permission.allowUpdate()) {
            throw new BusinessException("当前节点不允许修改子表行");
        }
        // 行内只读快照字段不在此拦截；落库前由 filterTaskChildWriteData 按 writableFields 过滤
    }

    private void updateTaskMasterDetailData(AiCrudConfig config,
                                             Object id,
                                             Map<String, Object> mainPayload,
                                             Map<String, Object> childrenPayload,
                                             Set<String> writableMainFields,
                                             Map<String, TaskChildPermission> permissions,
                                             RuntimeJoinContext joinContext,
                                             DynamicCrudRepository.SqlCondition dataScopeCondition) {
        Map<String, Object> authorizedMainRecord = repository.selectById(config.getTableName(), id, dataScopeCondition);
        if (authorizedMainRecord == null) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        writeFieldPolicy.applyStoredFormulasForUpdate(config, config.getTableName(), id, mainPayload, dataScopeCondition, authorizedMainRecord);
        writeFieldPolicy.validateFieldValues(config, mainPayload);
        validateUniqueConstraints(config, config.getTableName(), mainPayload, authorizedMainRecord, id);
        Map<String, Object> primaryData = masterDetailEngine.filterPrimaryWriteData(mainPayload, writableMainFields, joinContext);
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
            if (!childrenPayload.containsKey(relation.modelCode())) {
                continue;
            }
            TaskChildPermission permission = permissions.get(relation.modelCode());
            Object relationValue = resolveMainRelationValue(relation, primaryData, id, currentMainRecord);
            if (relationValue == null) {
                throw new BusinessException("无法解析子表归属字段: " + relation.modelCode());
            }
            for (Map<String, Object> row : normalizeChildRows(childrenPayload.get(relation.modelCode()))) {
                Object rowId = masterDetailEngine.resolveChildRowId(row);
                if (masterDetailEngine.isDeletedChildRow(row)) {
                    if (rowId == null) {
                        throw new BusinessException("删除子表行缺少id");
                    }
                    masterDetailEngine.assertChildRowBelongsToMain(relation, rowId, relationValue);
                    int affected = repository.deleteById(relation.tableName(), "id", rowId,
                            repository.hasDelFlag(relation.tableName()), null);
                    if (affected <= 0) {
                        throw new BusinessException("子表行删除失败或数据不存在");
                    }
                    childrenChanged = true;
                    continue;
                }
                Map<String, Object> childData = filterTaskChildWriteData(row, relation, permission.writableFields());
                masterDetailEngine.validateChildRow(relation, row, rowId == null);
                if (rowId == null) {
                    childData.put(relation.childFkColumn(), relationValue);
                    repository.insert(relation.tableName(), childData);
                } else {
                    masterDetailEngine.assertChildRowBelongsToMain(relation, rowId, relationValue);
                    if (childData.isEmpty()) {
                        continue;
                    }
                    int affected = repository.updateById(relation.tableName(), "id", rowId, childData, null);
                    if (affected <= 0) {
                        throw new BusinessException("子表行更新失败或数据不存在");
                    }
                }
                childrenChanged = true;
            }
        }
        if (primaryData.isEmpty() && !childrenChanged) {
            throw new BusinessException("未提交可编辑业务字段");
        }
        if (childrenChanged) {
            refreshRecordById(config, id);
        }
    }

    private Map<String, Object> filterTaskChildWriteData(Map<String, Object> data,
                                                         RuntimeChildRelation relation,
                                                         Set<String> writableFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (RuntimeFieldRef fieldRef : relation.fields().values()) {
            if (fieldRef.primary() || isImmutableWriteField(fieldRef.fieldName())
                    || isImmutableWriteField(fieldRef.sourceField())
                    || fieldRef.columnName().equals(relation.childFkColumn())) {
                continue;
            }
            String grantedField = resolveChildWritableField(relation, fieldRef.fieldName(), writableFields);
            if (grantedField == null) {
                continue;
            }
            Object value = firstPresent(data, fieldRef.sourceField(), fieldRef.fieldName(), fieldRef.columnName());
            if (value != null || masterDetailEngine.containsAnyKey(data, fieldRef.sourceField(), fieldRef.fieldName(), fieldRef.columnName())) {
                result.put(fieldRef.columnName(), value);
            }
        }
        return result;
    }

    private String resolveChildWritableField(RuntimeChildRelation relation,
                                             String inputField,
                                             Set<String> writableFields) {
        if (StringUtils.isBlank(inputField) || writableFields == null || writableFields.isEmpty()) {
            return null;
        }
        RuntimeFieldRef fieldRef = relation.fields().get(inputField);
        String sourceField = fieldRef == null ? inputField : fieldRef.sourceField();
        String fieldName = fieldRef == null ? inputField : fieldRef.fieldName();
        String columnName = fieldRef == null ? inputField : fieldRef.columnName();
        for (String allowed : writableFields) {
            if (StringUtils.equalsAnyIgnoreCase(allowed, inputField, sourceField, fieldName, columnName,
                    DynamicQueryGenerator.camelToSnake(inputField),
                    DynamicQueryGenerator.snakeToCamel(inputField))) {
                return allowed;
            }
        }
        return null;
    }

    private boolean containsFieldAlias(Set<String> fields, String field) {
        if (fields == null || fields.isEmpty() || StringUtils.isBlank(field)) {
            return false;
        }
        return fields.stream().anyMatch(item -> StringUtils.equalsAnyIgnoreCase(item, field,
                DynamicQueryGenerator.camelToSnake(field), DynamicQueryGenerator.snakeToCamel(field)));
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

    private Object resolveMainRelationValue(
            RuntimeChildRelation relation,
            Map<String, Object> primaryData,
            Object mainId,
            Map<String, Object> currentMainRecord) {
        if (relation == null
                || StringUtils.equalsAny(relation.mainColumn(), "id", primaryKeyColumn(currentPrimaryKey()))) {
            return mainId;
        }
        Object value = firstPresent(primaryData, relation.mainColumn());
        if (value == null) {
            value = firstPresent(currentMainRecord, relation.mainColumn(),
                    DynamicQueryGenerator.snakeToCamel(relation.mainColumn()));
        }
        return value;
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
}
