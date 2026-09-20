package com.mdframe.forge.plugin.generator.service.formula;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectRelation;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.formula.FormulaConfig;
import com.mdframe.forge.plugin.generator.domain.formula.FormulaRuntimeContext;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectRelationMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudRepository;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Recomputes STORED aggregate formulas on parent objects when detail rows change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoredAggregateRefreshService {

    private final BusinessObjectRelationMapper relationMapper;
    private final AiCrudConfigMapper crudConfigMapper;
    private final DynamicCrudRepository repository;
    private final StoredFormulaRuntime storedFormulaRuntime;
    private final ObjectMapper objectMapper;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;

    public void refreshAfterChildInsert(AiCrudConfig childConfig, Map<String, Object> childRecord) {
        refreshAfterChildChange(childConfig, null, childRecord);
    }

    public void refreshAfterChildUpdate(AiCrudConfig childConfig,
                                        Map<String, Object> beforeRecord,
                                        Map<String, Object> afterRecord) {
        refreshAfterChildChange(childConfig, beforeRecord, afterRecord);
    }

    public void refreshAfterChildDelete(AiCrudConfig childConfig, Map<String, Object> beforeRecord) {
        refreshAfterChildChange(childConfig, beforeRecord, null);
    }

    public void refreshRecord(AiCrudConfig config, Object recordId) {
        if (config == null || recordId == null || StringUtils.isBlank(config.getTableName())) {
            return;
        }
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(config)) {
            LowcodeModelSchema schema = parseModelSchema(config);
            if (schema == null) {
                return;
            }
            Set<String> formulaFields = new LinkedHashSet<>(storedFormulaRuntime.extractFormulas(schema).keySet());
            if (formulaFields.isEmpty()) {
                return;
            }
            Map<String, Object> rawRecord = repository.selectById(config.getTableName(), recordId);
            refreshParentRecord(config, schema, rawRecord, formulaFields);
        }
    }

    private void refreshAfterChildChange(AiCrudConfig childConfig,
                                         Map<String, Object> beforeRecord,
                                         Map<String, Object> afterRecord) {
        if (childConfig == null
                || StringUtils.isBlank(childConfig.getObjectCode())
                || StringUtils.isBlank(childConfig.getTableName())) {
            return;
        }
        List<AiBusinessObjectRelation> relations = relationMapper.selectRuntimeRelationsByTarget(
                childConfig.getTenantId(), extractSuiteCode(childConfig), childConfig.getObjectCode());
        if (relations == null || relations.isEmpty()) {
            return;
        }

        for (AiBusinessObjectRelation relation : relations) {
            try {
                refreshForRelation(childConfig, relation, beforeRecord, afterRecord);
            } catch (Exception e) {
                log.error("[公式聚合] 从表变更后刷新主表公式失败, childObject={}, relationId={}",
                        childConfig.getObjectCode(), relation == null ? null : relation.getId(), e);
            }
        }
    }

    private void refreshForRelation(AiCrudConfig childConfig,
                                    AiBusinessObjectRelation relation,
                                    Map<String, Object> beforeRecord,
                                    Map<String, Object> afterRecord) {
        if (relation == null || relation.getId() == null) {
            return;
        }
        AiCrudConfig parentConfig = crudConfigMapper.selectPublishedByObjectCode(
                childConfig.getTenantId(), relation.getSourceObjectCode());
        if (parentConfig == null || StringUtils.isBlank(parentConfig.getTableName())) {
            return;
        }
        try (LowcodeRuntimeDataSourceContextHolder.Scope ignored = useRuntimeContext(parentConfig)) {
            LowcodeModelSchema parentSchema = parseModelSchema(parentConfig);
            if (parentSchema == null) {
                return;
            }
            Set<String> affectedFields = resolveAffectedStoredFormulaFields(parentSchema, relation);
            if (affectedFields.isEmpty()) {
                return;
            }
            Set<Object> parentKeys = new LinkedHashSet<>();
            addIfNotNull(parentKeys, readFieldValue(beforeRecord, relation.getTargetFieldCode()));
            addIfNotNull(parentKeys, readFieldValue(afterRecord, relation.getTargetFieldCode()));
            if (parentKeys.isEmpty()) {
                return;
            }
            String parentJoinColumn = resolveColumnName(parentSchema, parentConfig.getTableName(), relation.getSourceFieldCode());
            if (StringUtils.isBlank(parentJoinColumn)) {
                return;
            }
            for (Object parentKey : parentKeys) {
                refreshParentByJoinValue(parentConfig, parentSchema, parentJoinColumn, parentKey, affectedFields);
            }
        }
    }

    private void refreshParentByJoinValue(AiCrudConfig parentConfig,
                                          LowcodeModelSchema parentSchema,
                                          String parentJoinColumn,
                                          Object parentKey,
                                          Set<String> affectedFields) {
        if (parentKey == null) {
            return;
        }
        List<Map<String, Object>> parentRows = repository.selectListByColumn(
                parentConfig.getTableName(), parentJoinColumn, parentKey);
        for (Map<String, Object> parentRow : parentRows) {
            refreshParentRecord(parentConfig, parentSchema, parentRow, affectedFields);
        }
    }

    private void refreshParentRecord(AiCrudConfig parentConfig,
                                     LowcodeModelSchema parentSchema,
                                     Map<String, Object> rawRecord,
                                     Set<String> formulaFields) {
        if (rawRecord == null || rawRecord.isEmpty() || formulaFields == null || formulaFields.isEmpty()) {
            return;
        }
        Object recordId = resolveRecordId(parentSchema, rawRecord);
        if (recordId == null) {
            return;
        }
        Map<String, Object> record = DynamicQueryGenerator.convertMapToCamelCase(rawRecord);
        LowcodeModelSchema calculationSchema = copySchemaWithFormulaFields(parentSchema, formulaFields);
        FormulaRuntimeContext context = new FormulaRuntimeContext(
                parentConfig.getTenantId(),
                extractSuiteCode(parentConfig),
                parentConfig.getObjectCode(),
                record);
        storedFormulaRuntime.calculate(List.of(record), calculationSchema, context);

        Map<String, Object> updateData = buildFormulaUpdateData(parentConfig, parentSchema, record, formulaFields);
        if (!updateData.isEmpty()) {
            try (AutoCloseable ignored = com.mdframe.forge.plugin.generator.service.audit.DataAuditTransactionHolder
                    .overrideFieldSource(com.mdframe.forge.plugin.generator.enums.DataAuditSourceType.FORMULA)) {
                repository.updateById(parentConfig.getTableName(), recordId, updateData);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private Set<String> resolveAffectedStoredFormulaFields(LowcodeModelSchema schema, AiBusinessObjectRelation relation) {
        Map<String, FormulaConfig> formulas = storedFormulaRuntime.extractFormulas(schema);
        if (formulas.isEmpty()) {
            return Set.of();
        }
        Set<String> affected = new LinkedHashSet<>();
        for (Map.Entry<String, FormulaConfig> entry : formulas.entrySet()) {
            FormulaConfig config = entry.getValue();
            if (config != null
                    && config.isAggregate()
                    && config.getAggregate() != null
                    && matchesAggregateRelation(config.getAggregate().getRelationCode(), relation)) {
                affected.add(entry.getKey());
            }
        }
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, FormulaConfig> entry : formulas.entrySet()) {
                if (affected.contains(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                List<String> dependsOn = entry.getValue().getDependsOn();
                if (dependsOn != null && dependsOn.stream().anyMatch(affected::contains)) {
                    affected.add(entry.getKey());
                    changed = true;
                }
            }
        } while (changed);
        return affected;
    }

    private boolean matchesAggregateRelation(String relationCode, AiBusinessObjectRelation relation) {
        if (StringUtils.isBlank(relationCode) || relation == null) {
            return false;
        }
        return relationCode.equals(String.valueOf(relation.getId()))
                || relationCode.equals(relation.getTargetObjectCode())
                || relationCode.equals(relation.getRelationName());
    }

    private LowcodeModelSchema copySchemaWithFormulaFields(LowcodeModelSchema source, Set<String> formulaFields) {
        LowcodeModelSchema target = new LowcodeModelSchema();
        target.setSchemaVersion(source.getSchemaVersion());
        target.setDomain(source.getDomain());
        target.setObject(source.getObject());
        target.setAppType(source.getAppType());
        target.setTableMode(source.getTableMode());
        target.setSourceTable(source.getSourceTable());
        target.setRuntimeDatasource(source.getRuntimeDatasource());
        target.setPrimaryKey(source.getPrimaryKey());
        target.setTenantStrategy(source.getTenantStrategy());
        target.setAuditStrategy(source.getAuditStrategy());
        target.setLogicDeleteStrategy(source.getLogicDeleteStrategy());
        target.setTableName(source.getTableName());
        target.setBusinessName(source.getBusinessName());
        target.setTreeConfig(source.getTreeConfig());
        target.setRelations(source.getRelations());
        target.setIndexes(source.getIndexes());
        target.setPolicies(source.getPolicies());
        target.setChildren(source.getChildren());
        target.setFields(source.getFields() == null
                ? List.of()
                : source.getFields().stream()
                .filter(field -> field != null && formulaFields.contains(field.getField()))
                .toList());
        return target;
    }

    private Map<String, Object> buildFormulaUpdateData(AiCrudConfig config,
                                                       LowcodeModelSchema schema,
                                                       Map<String, Object> record,
                                                       Set<String> formulaFields) {
        Map<String, Object> updateData = new LinkedHashMap<>();
        if (schema.getFields() == null) {
            return updateData;
        }
        for (LowcodeFieldSchema field : schema.getFields()) {
            if (field == null || !formulaFields.contains(field.getField())) {
                continue;
            }
            String column = resolveColumnName(schema, config.getTableName(), field.getField());
            if (StringUtils.isBlank(column)) {
                continue;
            }
            updateData.put(column, readFieldValue(record, field.getField()));
        }
        return updateData;
    }

    private LowcodeModelSchema parseModelSchema(AiCrudConfig config) {
        if (config == null || StringUtils.isBlank(config.getModelSchema())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
        } catch (Exception e) {
            log.warn("[公式聚合] 解析模型协议失败, configKey={}", config.getConfigKey(), e);
            return null;
        }
    }

    private String resolveColumnName(LowcodeModelSchema schema, String tableName, String fieldName) {
        if (StringUtils.isBlank(fieldName)) {
            return null;
        }
        Map<String, String> columnMapping = repository.getColumnMapping(tableName);
        Set<String> tableColumns = repository.getTableColumns(tableName);
        if (schema.getFields() != null) {
            for (LowcodeFieldSchema field : schema.getFields()) {
                if (field != null && matchesField(field, fieldName)) {
                    String column = firstColumn(tableColumns, columnMapping,
                            field.getColumnName(), field.getField(), fieldName);
                    if (StringUtils.isNotBlank(column)) {
                        return column;
                    }
                }
            }
        }
        return firstColumn(tableColumns, columnMapping, fieldName);
    }

    private boolean matchesField(LowcodeFieldSchema field, String value) {
        String snakeValue = DynamicQueryGenerator.camelToSnake(value);
        String camelValue = DynamicQueryGenerator.snakeToCamel(value);
        return Objects.equals(field.getField(), value)
                || Objects.equals(field.getField(), camelValue)
                || Objects.equals(field.getColumnName(), value)
                || Objects.equals(field.getColumnName(), snakeValue);
    }

    private String firstColumn(Set<String> tableColumns,
                               Map<String, String> columnMapping,
                               String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.isBlank(candidate)) {
                continue;
            }
            String mapped = columnMapping.get(candidate);
            if (StringUtils.isNotBlank(mapped) && tableColumns.contains(mapped)) {
                return mapped;
            }
            if (tableColumns.contains(candidate)) {
                return candidate;
            }
            String snake = DynamicQueryGenerator.camelToSnake(candidate);
            if (tableColumns.contains(snake)) {
                return snake;
            }
        }
        return null;
    }

    private Object resolveRecordId(LowcodeModelSchema schema, Map<String, Object> rawRecord) {
        if (rawRecord == null || rawRecord.isEmpty()) {
            return null;
        }
        if (schema != null && schema.getPrimaryKey() != null) {
            Object value = readFieldValue(rawRecord, schema.getPrimaryKey().getField());
            if (value != null) {
                return value;
            }
            value = readFieldValue(rawRecord, schema.getPrimaryKey().getColumnName());
            if (value != null) {
                return value;
            }
        }
        return readFieldValue(rawRecord, "id");
    }

    private Object readFieldValue(Map<String, Object> row, String fieldName) {
        if (row == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        if (row.containsKey(fieldName)) {
            return row.get(fieldName);
        }
        String snake = DynamicQueryGenerator.camelToSnake(fieldName);
        if (row.containsKey(snake)) {
            return row.get(snake);
        }
        String camel = DynamicQueryGenerator.snakeToCamel(fieldName);
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        return null;
    }

    private void addIfNotNull(Set<Object> values, Object value) {
        if (value != null) {
            values.add(value);
        }
    }

    private LowcodeRuntimeDataSourceContextHolder.Scope useRuntimeContext(AiCrudConfig config) {
        LowcodeRuntimeDataSourceContext context = runtimeDataSourceResolver.resolve(config);
        config.setTableName(StringUtils.defaultIfBlank(context.getTableName(), config.getTableName()));
        return LowcodeRuntimeDataSourceContextHolder.use(context);
    }

    private String extractSuiteCode(AiCrudConfig config) {
        String configKey = config.getConfigKey();
        if (StringUtils.isBlank(configKey)) {
            return "default";
        }
        int idx = configKey.indexOf("_");
        return idx > 0 ? configKey.substring(0, idx) : configKey;
    }
}
