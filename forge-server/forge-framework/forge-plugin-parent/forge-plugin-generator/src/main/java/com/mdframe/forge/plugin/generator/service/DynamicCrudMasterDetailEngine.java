package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.service.formula.StoredAggregateRefreshService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeFieldValueValidator;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 主子表详情读取、关联修复、校验与持久化引擎。 */
@Slf4j
final class DynamicCrudMasterDetailEngine {

    private static final Set<String> IMMUTABLE_WRITE_FIELDS = Set.of(
            "id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time",
            "delFlag", "del_flag"
    );

    private final DynamicCrudRepository repository;
    private final ObjectMapper objectMapper;
    private final StoredAggregateRefreshService storedAggregateRefreshService;
    private final DynamicDataScopeService dynamicDataScopeService;
    private final DynamicCrudWriteFieldPolicy writeFieldPolicy;
    private final DynamicCrudUniquenessValidator uniquenessValidator;
    private final DynamicCrudFieldValuePipeline fieldValuePipeline;
    private final DynamicCrudRuntimeRelationPlanner runtimeRelationPlanner;

    DynamicCrudMasterDetailEngine(
            DynamicCrudRepository repository,
            ObjectMapper objectMapper,
            StoredAggregateRefreshService storedAggregateRefreshService,
            DynamicDataScopeService dynamicDataScopeService,
            DynamicCrudWriteFieldPolicy writeFieldPolicy,
            DynamicCrudUniquenessValidator uniquenessValidator,
            DynamicCrudFieldValuePipeline fieldValuePipeline,
            DynamicCrudRuntimeRelationPlanner runtimeRelationPlanner) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.storedAggregateRefreshService = storedAggregateRefreshService;
        this.dynamicDataScopeService = dynamicDataScopeService;
        this.writeFieldPolicy = writeFieldPolicy;
        this.uniquenessValidator = uniquenessValidator;
        this.fieldValuePipeline = fieldValuePipeline;
        this.runtimeRelationPlanner = runtimeRelationPlanner;
    }

    Map<String, Object> selectById(AiCrudConfig config,
                                   Object id,
                                   RuntimeJoinContext joinContext,
                                   DynamicCrudRepository.SqlCondition dataScopeCondition) {
        Map<String, Object> record = repository.selectById(
                config.getTableName(),
                id,
                dataScopeCondition);
        if (record == null) {
            return null;
        }
        Map<String, Object> main = DynamicQueryGenerator.convertMapToCamelCase(record);
        applyReadPipeline(Collections.singletonList(main), config);

        Map<String, Object> children = new LinkedHashMap<>();
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            RuntimeChildRelation queryRelation = resolveMasterDetailQueryRelation(config, relation);
            Object relationValue = resolveMainRelationValue(queryRelation, record, id, record);
            if (relationValue == null) {
                log.info("[动态CRUD详情子表] configKey={}, mainId={}, childModel={}, table={}, childFkColumn={}, mainColumn={}, relationValue=null, rows=0",
                        config.getConfigKey(), id, queryRelation.modelCode(), queryRelation.tableName(),
                        queryRelation.childFkColumn(), queryRelation.mainColumn());
                children.put(queryRelation.modelCode(), List.of());
                continue;
            }
            List<Map<String, Object>> rows = selectMasterDetailChildRows(config, queryRelation, id, relationValue);
            log.info("[动态CRUD详情子表] configKey={}, mainId={}, childModel={}, table={}, childFkColumn={}, mainColumn={}, relationValue={}, rows={}, rowIds={}",
                    config.getConfigKey(), id, queryRelation.modelCode(), queryRelation.tableName(),
                    queryRelation.childFkColumn(), queryRelation.mainColumn(), relationValue, rows.size(),
                    summarizeRowIds(rows));
            children.put(queryRelation.modelCode(), DynamicQueryGenerator.convertListToCamelCase(rows));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("main", main);
        result.put("children", children);
        return result;
    }

    RuntimeChildRelation preferReadableChildRelation(AiCrudConfig config, RuntimeChildRelation relation) {
        if (relation == null) {
            return null;
        }
        RuntimeChildRelation resolved = resolveMasterDetailQueryRelation(config, relation);
        if (resolved == null) {
            resolved = relation;
        }
        if (!isSuspiciousMasterDetailChildFkColumn(resolved.childFkColumn())) {
            return resolved;
        }
        try {
            List<String> candidates = resolveMasterDetailChildFkCandidates(config, resolved);
            for (String column : candidates) {
                if (StringUtils.isNotBlank(column) && !isSuspiciousMasterDetailChildFkColumn(column)) {
                    return copyChildRelation(resolved, column, resolved.mainColumn());
                }
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudService] 解析子表外键失败，继续使用原关联, configKey={}, modelCode={}",
                    config == null ? null : config.getConfigKey(), resolved.modelCode(), e);
        }
        return resolved;
    }

    private RuntimeChildRelation copyChildRelation(RuntimeChildRelation relation, String childFkColumn, String mainColumn) {
        return new RuntimeChildRelation(
                relation.modelCode(),
                relation.tableName(),
                relation.tableAlias(),
                childFkColumn,
                mainColumn,
                relation.saveMode(),
                relation.fields(),
                relation.fieldRules()
        );
    }

    private RuntimeChildRelation resolveMasterDetailQueryRelation(AiCrudConfig config, RuntimeChildRelation relation) {
        JsonNode childNode = findMasterDetailChildNode(config, relation);
        if (childNode == null || !childNode.isObject()) {
            return relation;
        }
        Map<String, String> mainColumnMapping = writeFieldPolicy.buildRuntimeColumnMapping(config, config.getTableName());
        Map<String, String> childColumnMapping = repository.getColumnMapping(relation.tableName());
        String childField = firstText(childNode, "sourceField", "childField", "foreignKey", "foreignKeyField");
        String mainField = firstText(childNode, "targetField", "parentField", "mainField", "parentKey");
        String childColumn = runtimeRelationPlanner.resolveChildColumn(childField, childColumnMapping);
        String mainColumn = runtimeRelationPlanner.resolvePrimaryColumn(mainField, mainColumnMapping);
        if (StringUtils.isBlank(childColumn) || StringUtils.isBlank(mainColumn)) {
            return relation;
        }
        return new RuntimeChildRelation(
                relation.modelCode(),
                relation.tableName(),
                relation.tableAlias(),
                childColumn,
                mainColumn,
                relation.saveMode(),
                relation.fields(),
                relation.fieldRules()
        );
    }

    JsonNode findMasterDetailChildNode(AiCrudConfig config, RuntimeChildRelation relation) {
        if (config == null || StringUtils.isBlank(config.getOptions()) || relation == null) {
            return null;
        }
        try {
            JsonNode children = objectMapper.readTree(config.getOptions()).path("masterDetailConfig").path("children");
            if (!children.isArray()) {
                return null;
            }
            for (JsonNode child : children) {
                if (matchesMasterDetailChildNode(child, relation)) {
                    return child;
                }
            }
        } catch (Exception e) {
            log.debug("[DynamicCrudService] 解析主子表子表配置失败, configKey={}, modelCode={}, error={}",
                    config.getConfigKey(), relation.modelCode(), e.getMessage());
        }
        return null;
    }

    private boolean matchesMasterDetailChildNode(JsonNode child, RuntimeChildRelation relation) {
        if (child == null || relation == null) {
            return false;
        }
        return StringUtils.equalsAny(relation.modelCode(),
                firstText(child, "modelCode"),
                firstText(child, "relationKey"),
                firstText(child, "key"),
                firstText(child, "field"))
                || StringUtils.equals(relation.tableName(), firstText(child, "tableName"));
    }

    private List<Map<String, Object>> selectMasterDetailChildRows(AiCrudConfig config,
                                                                  RuntimeChildRelation relation,
                                                                  Object mainId,
                                                                  Object relationValue) {
        List<Map<String, Object>> rows = repository.selectListByColumn(
                relation.tableName(), relation.childFkColumn(), relationValue);
        if (mainId == null) {
            return rows;
        }
        List<String> fallbackColumns = resolveMasterDetailChildFkCandidates(config, relation);
        for (String column : fallbackColumns) {
            if (StringUtils.equals(column, relation.childFkColumn())) {
                continue;
            }
            List<Map<String, Object>> fallbackRows = repository.selectListByColumn(relation.tableName(), column, mainId);
            if (shouldUseFallbackChildRows(relation.childFkColumn(), rows, fallbackRows)) {
                log.warn("[DynamicCrudService] 主子表子表查询命中疑似错误外键，已切换候选外键: configKey={}, childModel={}, table={}, oldColumn={}, oldSize={}, newColumn={}, newSize={}",
                        config.getConfigKey(), relation.modelCode(), relation.tableName(), relation.childFkColumn(), rows.size(),
                        column, fallbackRows.size());
                log.info("[动态CRUD详情子表] fallback configKey={}, mainId={}, childModel={}, table={}, oldColumn={}, oldRows={}, newColumn={}, newRows={}, newRowIds={}",
                        config.getConfigKey(), mainId, relation.modelCode(), relation.tableName(), relation.childFkColumn(),
                        rows.size(), column, fallbackRows.size(), summarizeRowIds(fallbackRows));
                return fallbackRows;
            }
        }
        return rows;
    }

    private boolean shouldUseFallbackChildRows(String currentColumn,
                                               List<Map<String, Object>> currentRows,
                                               List<Map<String, Object>> fallbackRows) {
        if (fallbackRows == null || fallbackRows.isEmpty()) {
            return false;
        }
        int currentSize = currentRows == null ? 0 : currentRows.size();
        if (currentSize == 0) {
            return true;
        }
        if (isSuspiciousMasterDetailChildFkColumn(currentColumn)) {
            return true;
        }
        return currentSize > fallbackRows.size();
    }

    private boolean isSuspiciousMasterDetailChildFkColumn(String column) {
        if (StringUtils.isBlank(column)) {
            return true;
        }
        String normalized = column.trim().toLowerCase(Locale.ROOT);
        return Set.of("id", "tenant_id", "create_by", "create_dept", "update_by", "del_flag").contains(normalized)
                || normalized.endsWith("_name")
                || normalized.endsWith("_code");
    }

    private List<String> resolveMasterDetailChildFkCandidates(AiCrudConfig config, RuntimeChildRelation relation) {
        Set<String> candidates = new LinkedHashSet<>();
        Set<String> childColumns = repository.getTableColumns(relation.tableName());
        addMasterDetailChildFkCandidate(candidates, childColumns, relation.childFkColumn());

        JsonNode childNode = findMasterDetailChildNode(config, relation);
        if (childNode != null) {
            Map<String, String> childColumnMapping = repository.getColumnMapping(relation.tableName());
            addMasterDetailChildFkCandidate(candidates, childColumns,
                    runtimeRelationPlanner.resolveChildColumn(firstText(childNode, "sourceField", "childField", "foreignKey", "foreignKeyField"), childColumnMapping));
        }

        addMasterDetailChildFkNameCandidates(candidates, childColumns, config.getConfigKey());
        addMasterDetailChildFkNameCandidates(candidates, childColumns, config.getTableName());
        return new ArrayList<>(candidates);
    }

    private void addMasterDetailChildFkNameCandidates(Set<String> candidates, Set<String> childColumns, String baseName) {
        String normalized = normalizeIdentifierBase(baseName);
        if (StringUtils.isBlank(normalized)) {
            return;
        }
        addMasterDetailChildFkCandidate(candidates, childColumns, normalized + "_id");
        String withoutPrefix = removeShortBusinessPrefix(normalized);
        if (!StringUtils.equals(withoutPrefix, normalized)) {
            addMasterDetailChildFkCandidate(candidates, childColumns, withoutPrefix + "_id");
        }
    }

    private String normalizeIdentifierBase(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String normalized = DynamicQueryGenerator.camelToSnake(value).replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        return normalized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
    }

    private String removeShortBusinessPrefix(String value) {
        if (StringUtils.isBlank(value) || !value.contains("_")) {
            return value;
        }
        String prefix = StringUtils.substringBefore(value, "_");
        return prefix.length() <= 4 ? StringUtils.substringAfter(value, "_") : value;
    }

    private void addMasterDetailChildFkCandidate(Set<String> candidates, Set<String> childColumns, String column) {
        if (StringUtils.isBlank(column)) {
            return;
        }
        String normalized = column.trim();
        if (childColumns.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private List<Object> summarizeRowIds(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .limit(5)
                .map(row -> firstPresent(row, "id", "ID"))
                .toList();
    }

    void insert(AiCrudConfig config,
                                        Map<String, Object> data,
                                        Set<String> allowedFields,
                                        RuntimeJoinContext joinContext) {
        Map<String, Object> mainPayload = extractMainPayload(data);
        writeFieldPolicy.applyStoredFormulas(config, mainPayload);
        writeFieldPolicy.validateFieldValues(config, mainPayload);
        validateUniqueConstraints(config, config.getTableName(), mainPayload, null, null);
        Map<String, Object> primaryData = filterPrimaryWriteData(mainPayload, allowedFields, joinContext);
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
        putPrimaryKeyAlias(mainPayload, primaryKey, mainId);
        Map<String, Object> currentMainRecord = null;
        Map<String, Object> childrenPayload = extractChildrenPayload(data);
        boolean childrenChanged = false;
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            List<Map<String, Object>> childRows = normalizeChildRows(childrenPayload.get(relation.modelCode()));
            if (childRows.isEmpty()) {
                continue;
            }
            if (currentMainRecord == null && !"id".equals(relation.mainColumn()) && !primaryData.containsKey(relation.mainColumn())) {
                currentMainRecord = repository.selectById(config.getTableName(), mainId);
            }
            Object relationValue = resolveMainRelationValue(relation, primaryData, mainId, currentMainRecord);
            if (relationValue == null) {
                continue;
            }
            for (Map<String, Object> row : childRows) {
                Map<String, Object> childData = filterChildWriteData(row, relation);
                if (!hasWritableChildData(childData)) {
                    continue;
                }
                validateChildRow(relation, row, true);
                childData.put(relation.childFkColumn(), relationValue);
                repository.insert(relation.tableName(), childData);
                childrenChanged = true;
            }
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
        Map<String, Object> mainPayload = extractMainPayload(data);
        writeFieldPolicy.applyStoredFormulasForUpdate(config, config.getTableName(), id, mainPayload, dataScopeCondition, authorizedMainRecord);
        writeFieldPolicy.validateFieldValues(config, mainPayload);
        validateUniqueConstraints(config, config.getTableName(), mainPayload, authorizedMainRecord, id);
        Map<String, Object> primaryData = filterPrimaryWriteData(mainPayload, allowedFields, joinContext);
        removePrimaryKeyColumns(primaryData, currentPrimaryKey());
        fieldValuePipeline.removeMaskedDesensitizedWriteColumns(
                primaryData,
                config,
                writeFieldPolicy.buildRuntimeColumnMapping(config, config.getTableName()));
        if (!primaryData.isEmpty()) {
            fieldValuePipeline.applyMoneyStorageWrite(primaryData, config);
            fieldValuePipeline.applyStructuredFieldStorageWrite(primaryData, config);
            fieldValuePipeline.applyEncrypt(primaryData, config.getEncryptConfig());
            int affected = repository.updateById(config.getTableName(), id, primaryData, dataScopeCondition);
            if (affected <= 0) {
                throw new BusinessException("无权限更新该数据或数据不存在");
            }
        }

        Map<String, Object> childrenPayload = extractChildrenPayload(data);
        Map<String, Object> currentMainRecord = authorizedMainRecord;
        boolean childrenChanged = false;
        for (RuntimeChildRelation relation : joinContext.childRelations()) {
            if (!childrenPayload.containsKey(relation.modelCode())) {
                continue;
            }
            if (currentMainRecord == null && !"id".equals(relation.mainColumn()) && !primaryData.containsKey(relation.mainColumn())) {
                currentMainRecord = repository.selectById(config.getTableName(), id);
            }
            Object relationValue = resolveMainRelationValue(relation, primaryData, id, currentMainRecord);
            if (relationValue == null) {
                continue;
            }
            if (isMergeChildSaveMode(relation)) {
                childrenChanged = mergeMasterDetailChildRows(
                        relation,
                        relationValue,
                        normalizeChildRows(childrenPayload.get(relation.modelCode()))
                ) || childrenChanged;
                continue;
            }
            repository.deleteByColumn(
                    relation.tableName(),
                    relation.childFkColumn(),
                    relationValue,
                    repository.hasDelFlag(relation.tableName())
            );
            for (Map<String, Object> row : normalizeChildRows(childrenPayload.get(relation.modelCode()))) {
                Map<String, Object> childData = filterChildWriteData(row, relation);
                if (!hasWritableChildData(childData)) {
                    continue;
                }
                validateChildRow(relation, row, true);
                childData.put(relation.childFkColumn(), relationValue);
                repository.insert(relation.tableName(), childData);
                childrenChanged = true;
            }
        }

        if (primaryData.isEmpty() && !childrenChanged) {
            throw emptyMasterDetailUpdateException(config, data, allowedFields, joinContext);
        }
        if (childrenChanged) {
            refreshRecordById(config, id);
        }
    }

    Map<String, Object> filterPrimaryWriteData(Map<String, Object> data,
                                               Set<String> allowedFields,
                                               RuntimeJoinContext joinContext) {
        Map<String, Object> primaryData = new LinkedHashMap<>();
        if (data == null || data.isEmpty()) {
            return primaryData;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (isImmutableWriteField(key) || !allowedFields.contains(key)) {
                continue;
            }
            RuntimeFieldRef fieldRef = joinContext.fields().get(key);
            if (fieldRef == null || !fieldRef.primary()) {
                continue;
            }
            primaryData.put(fieldRef.columnName(), entry.getValue());
        }
        return primaryData;
    }

    private Map<String, Object> filterChildWriteData(Map<String, Object> data, RuntimeChildRelation relation) {
        Map<String, Object> childData = new LinkedHashMap<>();
        if (data == null || data.isEmpty()) {
            return childData;
        }
        for (RuntimeFieldRef fieldRef : relation.fields().values()) {
            if (fieldRef.primary() || isImmutableWriteField(fieldRef.fieldName()) || isImmutableWriteField(fieldRef.sourceField())) {
                continue;
            }
            if (fieldRef.columnName().equals(relation.childFkColumn())) {
                continue;
            }
            Object value = firstPresent(data, fieldRef.sourceField(), fieldRef.fieldName(), fieldRef.columnName());
            if (value != null) {
                childData.put(fieldRef.columnName(), value);
            }
        }
        return childData;
    }

    private boolean mergeMasterDetailChildRows(RuntimeChildRelation relation,
                                               Object relationValue,
                                               List<Map<String, Object>> childRows) {
        boolean changed = false;
        for (Map<String, Object> row : childRows) {
            Object rowId = resolveChildRowId(row);
            if (isDeletedChildRow(row)) {
                if (rowId != null) {
                    assertChildRowBelongsToMain(relation, rowId, relationValue);
                    int affected = repository.deleteById(relation.tableName(), "id", rowId,
                            repository.hasDelFlag(relation.tableName()), null);
                    if (affected <= 0) {
                        throw new BusinessException("子表行删除失败或数据不存在");
                    }
                    changed = true;
                }
                continue;
            }

            Map<String, Object> childData = filterChildWriteData(row, relation);
            if (rowId == null) {
                if (!hasWritableChildData(childData)) {
                    continue;
                }
                validateChildRow(relation, row, true);
                childData.put(relation.childFkColumn(), relationValue);
                repository.insert(relation.tableName(), childData);
                changed = true;
                continue;
            }

            assertChildRowBelongsToMain(relation, rowId, relationValue);
            if (!hasWritableChildData(childData)) {
                continue;
            }
            validateChildRow(relation, row, false);
            int affected = repository.updateById(relation.tableName(), "id", rowId, childData, null);
            if (affected <= 0) {
                throw new BusinessException("子表行更新失败或数据不存在");
            }
            changed = true;
        }
        return changed;
    }

    void assertChildRowBelongsToMain(RuntimeChildRelation relation, Object rowId, Object relationValue) {
        Map<String, Object> existing = repository.selectById(relation.tableName(), "id", rowId, null);
        if (existing == null || !sameValue(existing.get(relation.childFkColumn()), relationValue)) {
            throw new BusinessException("子表行不存在或不属于当前主记录");
        }
    }

    Object resolveChildRowId(Map<String, Object> row) {
        return firstPresent(row, "id");
    }

    boolean isDeletedChildRow(Map<String, Object> row) {
        Object deleted = firstPresent(row, "_deleted", "__deleted");
        if (deleted instanceof Boolean flag) {
            return flag;
        }
        return deleted != null && Set.of("true", "1", "yes", "y")
                .contains(String.valueOf(deleted).trim().toLowerCase(Locale.ROOT));
    }

    void validateChildRow(RuntimeChildRelation relation, Map<String, Object> row, boolean create) {
        if (relation == null || row == null || relation.fieldRules() == null || relation.fieldRules().isEmpty()) {
            return;
        }
        for (RuntimeChildFieldRule rule : relation.fieldRules().values()) {
            if (isSystemChildValidationRule(relation, rule)) {
                continue;
            }
            Object value = firstPresent(row, rule.sourceField(), rule.fieldName(), rule.columnName());
            boolean provided = containsAnyKey(row, rule.sourceField(), rule.fieldName(), rule.columnName());
            if (rule.required() && (create || provided) && isBlankChildValue(value)) {
                throw new BusinessException(StringUtils.defaultIfBlank(rule.label(), rule.sourceField()) + "不能为空");
            }
            if (!isBlankChildValue(value)) {
                validateChildStorageConstraint(rule, value);
                validateChildNumberRange(rule, value);
            }
        }
    }

    private void validateChildStorageConstraint(RuntimeChildFieldRule rule, Object value) {
        if (rule == null || StringUtils.isBlank(rule.dataType())) {
            return;
        }
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(rule.sourceField());
        field.setColumnName(rule.columnName());
        field.setLabel(StringUtils.defaultIfBlank(rule.label(), rule.sourceField()));
        field.setDataType(rule.dataType());
        field.setLength(rule.length());
        field.setPrecision(rule.precision());
        Map<String, Object> basicProps = new LinkedHashMap<>();
        if (rule.minValue() != null) {
            basicProps.put("min", rule.minValue());
        }
        if (rule.maxValue() != null) {
            basicProps.put("max", rule.maxValue());
        }
        field.setBasicProps(basicProps);
        LowcodeFieldValueValidator.validateValue(field, value, objectMapper);
    }

    private boolean isSystemChildValidationRule(RuntimeChildRelation relation, RuntimeChildFieldRule rule) {
        if (rule == null) {
            return true;
        }
        return isImmutableWriteField(rule.fieldName())
                || isImmutableWriteField(rule.sourceField())
                || isImmutableWriteField(rule.columnName())
                || StringUtils.equals(rule.columnName(), relation.childFkColumn());
    }

    private void validateChildNumberRange(RuntimeChildFieldRule rule, Object value) {
        if (rule.minValue() == null && rule.maxValue() == null) {
            return;
        }
        BigDecimal number = toBigDecimal(value);
        if (number == null) {
            throw new BusinessException(StringUtils.defaultIfBlank(rule.label(), rule.sourceField()) + "必须为有效数字");
        }
        if (rule.minValue() != null && number.compareTo(rule.minValue()) < 0) {
            throw new BusinessException(StringUtils.defaultIfBlank(rule.label(), rule.sourceField()) + "不能小于" + rule.minValue());
        }
        if (rule.maxValue() != null && number.compareTo(rule.maxValue()) > 0) {
            throw new BusinessException(StringUtils.defaultIfBlank(rule.label(), rule.sourceField()) + "不能大于" + rule.maxValue());
        }
    }

    boolean containsAnyKey(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (StringUtils.isNotBlank(key) && row.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlankChildValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return StringUtils.isBlank(text);
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean sameValue(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private boolean isMergeChildSaveMode(RuntimeChildRelation relation) {
        return relation != null && "merge".equalsIgnoreCase(StringUtils.defaultString(relation.saveMode()));
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


    private void applyReadPipeline(List<Map<String, Object>> rows, AiCrudConfig config) {
        fieldValuePipeline.applyRead(rows, config);
        dynamicDataScopeService.enrichRows(config, rows);
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

    private boolean hasWritableChildData(Map<String, Object> childData) {
        return childData != null && !childData.isEmpty();
    }

    private Object resolveMainRelationValue(
            RuntimeChildRelation relation,
            Map<String, Object> primaryData,
            Object mainId,
            Map<String, Object> currentMainRecord) {
        if (relation == null) {
            return mainId;
        }
        if (StringUtils.equals(relation.mainColumn(), primaryKeyColumn(currentPrimaryKey()))
                || StringUtils.equals(relation.mainColumn(), "id")) {
            return mainId;
        }
        Object value = firstPresent(primaryData, relation.mainColumn());
        if (value == null) {
            value = firstPresent(currentMainRecord, relation.mainColumn(),
                    DynamicQueryGenerator.snakeToCamel(relation.mainColumn()));
        }
        return value;
    }

    private boolean isImmutableWriteField(String key) {
        if (StringUtils.isBlank(key)) {
            return true;
        }
        return IMMUTABLE_WRITE_FIELDS.contains(key)
                || IMMUTABLE_WRITE_FIELDS.contains(DynamicQueryGenerator.snakeToCamel(key))
                || IMMUTABLE_WRITE_FIELDS.contains(DynamicQueryGenerator.camelToSnake(key));
    }

    private BusinessException emptyMasterDetailUpdateException(
            AiCrudConfig config,
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
        return new BusinessException("没有可更新的字段（对象 "
                + StringUtils.defaultString(config == null ? null : config.getConfigKey())
                + " / 表 " + StringUtils.defaultString(config == null ? null : config.getTableName())
                + "）。请求字段=" + (data == null ? Set.of() : data.keySet())
                + "，白名单字段数=" + (allowedFields == null ? 0 : allowedFields.size())
                + "。主子表已提交子表键=[" + submittedChildren + "]，配置期望子表=["
                + StringUtils.defaultIfBlank(expectedChildren, "无")
                + "]。若只改了子表，请确认 children 下的对象编码与主子表配置一致。");
    }
}
