package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeUniqueConstraintSchema;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates model and editor-declared unique constraints before dynamic writes. */
@Slf4j
final class DynamicCrudUniquenessValidator {

    private final DynamicCrudRepository repository;
    private final ObjectMapper objectMapper;

    DynamicCrudUniquenessValidator(DynamicCrudRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    void validate(AiCrudConfig config,
                  String tableName,
                  Map<String, Object> data,
                  Map<String, Object> beforeRecord,
                  Object excludeId,
                  String primaryKeyColumn) {
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        List<LowcodeUniqueConstraintSchema> constraints = resolveUniqueConstraints(modelSchema);
        appendEditSchemaUniqueConstraints(config, constraints);
        if (constraints.isEmpty()) {
            return;
        }
        Map<String, Object> uniqueData = extractMainPayload(data);
        Map<String, LowcodeFieldSchema> fieldMap = buildModelFieldAliasMap(modelSchema);
        appendEditSchemaFieldAliases(config, tableName, fieldMap, modelSchema);
        Set<String> tableColumns = repository.getTableColumns(tableName);
        for (LowcodeUniqueConstraintSchema constraint : constraints) {
            validateConstraint(constraint, tableName, uniqueData, beforeRecord, excludeId,
                    primaryKeyColumn, fieldMap, tableColumns);
        }
    }

    private void validateConstraint(LowcodeUniqueConstraintSchema constraint,
                                    String tableName,
                                    Map<String, Object> uniqueData,
                                    Map<String, Object> beforeRecord,
                                    Object excludeId,
                                    String primaryKeyColumn,
                                    Map<String, LowcodeFieldSchema> fieldMap,
                                    Set<String> tableColumns) {
        if (constraint == null || constraint.getFields() == null || constraint.getFields().isEmpty()) {
            return;
        }
        if (excludeId != null && constraint.getFields().stream()
                .map(fieldMap::get)
                .filter(Objects::nonNull)
                .noneMatch(field -> containsUniqueInputValue(uniqueData, field))) {
            return;
        }
        Map<String, Object> columnValues = new LinkedHashMap<>();
        for (String fieldName : constraint.getFields()) {
            LowcodeFieldSchema field = fieldMap.get(fieldName);
            if (field == null) {
                throw new BusinessException("唯一校验字段不存在: " + fieldName);
            }
            String columnName = StringUtils.defaultIfBlank(
                    field.getColumnName(), DynamicQueryGenerator.camelToSnake(field.getField()));
            repository.validateIdentifier(columnName);
            if (!tableColumns.contains(columnName)) {
                throw new BusinessException("唯一校验字段未同步到数据表: " + field.getField());
            }
            Object value = normalizeUniqueValue(
                    resolveUniqueFieldValue(field, uniqueData, beforeRecord), constraint);
            if (Boolean.TRUE.equals(constraint.getIgnoreBlank()) && isBlankUniqueValue(value)) {
                return;
            }
            columnValues.put(columnName, value);
        }
        if (repository.existsByColumns(tableName, columnValues, primaryKeyColumn, excludeId, null)) {
            throw new BusinessException(resolveUniqueMessage(constraint, fieldMap));
        }
    }

    private List<LowcodeUniqueConstraintSchema> resolveUniqueConstraints(LowcodeModelSchema modelSchema) {
        List<LowcodeUniqueConstraintSchema> result = new ArrayList<>();
        if (modelSchema == null) {
            return result;
        }
        if (modelSchema.getUniqueConstraints() != null) {
            result.addAll(modelSchema.getUniqueConstraints());
        }
        appendFieldUniqueConstraints(modelSchema, result);
        appendValidationRuleUniqueConstraints(modelSchema, result);
        return result;
    }

    private void appendFieldUniqueConstraints(
            LowcodeModelSchema modelSchema, List<LowcodeUniqueConstraintSchema> result) {
        if (modelSchema.getFields() == null) {
            return;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (!isFieldUniqueEnabled(field)) {
                continue;
            }
            LowcodeUniqueConstraintSchema constraint = new LowcodeUniqueConstraintSchema();
            constraint.setName("uk_" + StringUtils.defaultIfBlank(
                    field.getColumnName(), DynamicQueryGenerator.camelToSnake(field.getField())));
            constraint.setFields(List.of(field.getField()));
            constraint.setScope("TENANT");
            constraint.setNormalize(List.of("trim"));
            constraint.setIgnoreBlank(true);
            constraint.setMessage(StringUtils.defaultIfBlank(field.getLabel(), field.getField()) + "已存在");
            result.add(constraint);
        }
    }

    private void appendValidationRuleUniqueConstraints(
            LowcodeModelSchema modelSchema, List<LowcodeUniqueConstraintSchema> result) {
        if (modelSchema.getValidationRules() == null) {
            return;
        }
        for (Map<String, Object> rule : modelSchema.getValidationRules()) {
            if (!"UNIQUE".equalsIgnoreCase(text(rule.get("type")))) {
                continue;
            }
            LowcodeUniqueConstraintSchema constraint = new LowcodeUniqueConstraintSchema();
            constraint.setName(text(rule.get("name")));
            constraint.setFields(toStringList(firstNonNull(rule.get("fields"), rule.get("field"))));
            constraint.setScope(text(rule.get("scope")));
            constraint.setNormalize(toStringList(firstNonNull(rule.get("normalize"), rule.get("normalizers"))));
            constraint.setIgnoreBlank(
                    rule.get("ignoreBlank") == null || Boolean.parseBoolean(text(rule.get("ignoreBlank"))));
            constraint.setMessage(text(rule.get("message")));
            result.add(constraint);
        }
    }

    private void appendEditSchemaUniqueConstraints(
            AiCrudConfig config, List<LowcodeUniqueConstraintSchema> constraints) {
        List<Map<String, Object>> editFields = readEditSchemaFields(config);
        if (editFields.isEmpty()) {
            return;
        }
        Set<String> existingKeys = constraints.stream()
                .filter(Objects::nonNull)
                .map(constraint -> uniqueConstraintKey(constraint.getFields()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Map<String, Object> editField : editFields) {
            if (!isEditSchemaUniqueEnabled(editField)) {
                continue;
            }
            String fieldName = text(editField.get("field"));
            if (StringUtils.isBlank(fieldName) || !existingKeys.add(uniqueConstraintKey(List.of(fieldName)))) {
                continue;
            }
            LowcodeUniqueConstraintSchema constraint = new LowcodeUniqueConstraintSchema();
            constraint.setName("uk_" + DynamicQueryGenerator.camelToSnake(fieldName));
            constraint.setFields(List.of(fieldName));
            constraint.setScope("TENANT");
            constraint.setNormalize(List.of("trim"));
            constraint.setIgnoreBlank(true);
            constraint.setMessage(StringUtils.defaultIfBlank(text(editField.get("label")), fieldName) + "已存在");
            constraints.add(constraint);
        }
    }

    private void appendEditSchemaFieldAliases(
            AiCrudConfig config,
            String tableName,
            Map<String, LowcodeFieldSchema> fieldMap,
            LowcodeModelSchema modelSchema) {
        List<Map<String, Object>> editFields = readEditSchemaFields(config);
        if (editFields.isEmpty()) {
            return;
        }
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName, modelSchema);
        for (Map<String, Object> editField : editFields) {
            String fieldName = text(editField.get("field"));
            if (StringUtils.isBlank(fieldName) || fieldMap.containsKey(fieldName)) {
                continue;
            }
            LowcodeFieldSchema field = new LowcodeFieldSchema();
            field.setField(fieldName);
            field.setColumnName(columnMapping.getOrDefault(
                    fieldName, DynamicQueryGenerator.camelToSnake(fieldName)));
            field.setLabel(text(editField.get("label")));
            field.setAdvancedProps(new LinkedHashMap<>(mapFrom(editField.get("advancedProps"))));
            putFieldAlias(fieldMap, field.getField(), field);
            putFieldAlias(fieldMap, field.getColumnName(), field);
        }
    }

    private Map<String, String> buildRuntimeColumnMapping(
            AiCrudConfig config, String tableName, LowcodeModelSchema modelSchema) {
        Map<String, String> result = new LinkedHashMap<>(repository.getColumnMapping(tableName));
        if (config == null || !StringUtils.equals(tableName, config.getTableName())
                || modelSchema == null || modelSchema.getFields() == null) {
            return result;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null) {
                continue;
            }
            String columnName = StringUtils.trimToNull(StringUtils.defaultIfBlank(
                    field.getColumnName(), DynamicQueryGenerator.camelToSnake(field.getField())));
            if (columnName == null) {
                continue;
            }
            String normalizedColumn = columnName.toLowerCase(Locale.ROOT);
            putColumnAlias(result, field.getField(), normalizedColumn);
            putColumnAlias(result, field.getColumnName(), normalizedColumn);
        }
        return result;
    }

    private void putColumnAlias(Map<String, String> mapping, String alias, String columnName) {
        if (StringUtils.isBlank(alias) || StringUtils.isBlank(columnName)) {
            return;
        }
        mapping.put(alias, columnName);
        mapping.put(DynamicQueryGenerator.snakeToCamel(alias), columnName);
        mapping.put(DynamicQueryGenerator.camelToSnake(alias), columnName);
    }

    private Map<String, LowcodeFieldSchema> buildModelFieldAliasMap(LowcodeModelSchema modelSchema) {
        Map<String, LowcodeFieldSchema> result = new LinkedHashMap<>();
        if (modelSchema == null || modelSchema.getFields() == null) {
            return result;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field != null) {
                putFieldAlias(result, field.getField(), field);
                putFieldAlias(result, field.getColumnName(), field);
            }
        }
        return result;
    }

    private void putFieldAlias(Map<String, LowcodeFieldSchema> fields, String alias, LowcodeFieldSchema field) {
        if (StringUtils.isBlank(alias)) {
            return;
        }
        fields.putIfAbsent(alias, field);
        fields.putIfAbsent(DynamicQueryGenerator.snakeToCamel(alias), field);
        fields.putIfAbsent(DynamicQueryGenerator.camelToSnake(alias), field);
    }

    private boolean isEditSchemaUniqueEnabled(Map<String, Object> editField) {
        return editField != null && !editField.isEmpty()
                && (isTrue(editField.get("unique"))
                || isTrue(mapFrom(editField.get("advancedProps")).get("unique"))
                || isTrue(mapFrom(editField.get("advancedProps")).get("uniqueCheck"))
                || isTrue(mapFrom(editField.get("props")).get("unique"))
                || isTrue(mapFrom(editField.get("basicProps")).get("unique")));
    }

    private boolean isFieldUniqueEnabled(LowcodeFieldSchema field) {
        return field != null
                && (isTrue(field.getAdvancedProps() == null ? null : field.getAdvancedProps().get("unique"))
                || isTrue(field.getAdvancedProps() == null ? null : field.getAdvancedProps().get("uniqueCheck"))
                || isTrue(field.getBasicProps() == null ? null : field.getBasicProps().get("unique")));
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean bool ? bool
                : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean containsUniqueInputValue(Map<String, Object> data, LowcodeFieldSchema field) {
        return data != null && field != null
                && (data.containsKey(field.getField())
                || data.containsKey(field.getColumnName())
                || data.containsKey(DynamicQueryGenerator.camelToSnake(field.getField()))
                || data.containsKey(DynamicQueryGenerator.snakeToCamel(field.getColumnName())));
    }

    private Object resolveUniqueFieldValue(
            LowcodeFieldSchema field, Map<String, Object> data, Map<String, Object> beforeRecord) {
        Object value = firstPresentValue(data,
                field.getField(), field.getColumnName(),
                DynamicQueryGenerator.camelToSnake(field.getField()),
                DynamicQueryGenerator.snakeToCamel(field.getColumnName()));
        if (value != null) {
            return value;
        }
        value = firstPresentValue(beforeRecord,
                field.getColumnName(), field.getField(),
                DynamicQueryGenerator.camelToSnake(field.getField()),
                DynamicQueryGenerator.snakeToCamel(field.getColumnName()));
        return value != null ? value : field.getDefaultValue();
    }

    private Object normalizeUniqueValue(Object value, LowcodeUniqueConstraintSchema constraint) {
        Object result = value;
        List<String> normalizers = constraint.getNormalize() == null ? List.of() : constraint.getNormalize();
        for (String normalizer : normalizers) {
            if (result instanceof String textValue && "trim".equalsIgnoreCase(normalizer)) {
                result = textValue.trim();
            } else if (result instanceof String textValue
                    && ("lower".equalsIgnoreCase(normalizer) || "lowercase".equalsIgnoreCase(normalizer))) {
                result = textValue.toLowerCase(Locale.ROOT);
            }
        }
        return result;
    }

    private boolean isBlankUniqueValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String textValue) {
            return StringUtils.isBlank(textValue);
        }
        return value instanceof Collection<?> collection && collection.isEmpty();
    }

    private String resolveUniqueMessage(
            LowcodeUniqueConstraintSchema constraint, Map<String, LowcodeFieldSchema> fieldMap) {
        if (StringUtils.isNotBlank(constraint.getMessage())) {
            return constraint.getMessage();
        }
        if (constraint.getFields() != null && constraint.getFields().size() == 1) {
            LowcodeFieldSchema field = fieldMap.get(constraint.getFields().get(0));
            if (field != null && StringUtils.isNotBlank(field.getLabel())) {
                return field.getLabel() + "已存在";
            }
        }
        return "字段值已存在";
    }

    private List<Map<String, Object>> readEditSchemaFields(AiCrudConfig config) {
        if (config == null || StringUtils.isBlank(config.getEditSchema())) {
            return List.of();
        }
        try {
            Object schema = objectMapper.readValue(config.getEditSchema(), new TypeReference<Object>() { });
            List<Map<String, Object>> result = new ArrayList<>();
            collectEditSchemaFields(schema, result);
            return result;
        } catch (Exception e) {
            log.warn("[DynamicCrudUniquenessValidator] 解析 editSchema 唯一校验失败, configKey={}",
                    config.getConfigKey(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void collectEditSchemaFields(Object node, List<Map<String, Object>> result) {
        if (node instanceof List<?> list) {
            list.forEach(item -> collectEditSchemaFields(item, result));
            return;
        }
        if (!(node instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (StringUtils.isNotBlank(text(map.get("field")))) {
            result.add(map);
        }
        collectEditSchemaFields(map.get("children"), result);
        collectEditSchemaFields(map.get("items"), result);
        collectEditSchemaFields(map.get("components"), result);
    }

    private LowcodeModelSchema parseModelSchema(AiCrudConfig config) {
        if (config == null || StringUtils.isBlank(config.getModelSchema())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
        } catch (Exception e) {
            log.warn("[DynamicCrudUniquenessValidator] 解析 modelSchema 失败, configKey={}",
                    config.getConfigKey(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMainPayload(Map<String, Object> data) {
        return data != null && data.get("main") instanceof Map<?, ?> main
                ? (Map<String, Object>) main : data == null ? Map.of() : data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapFrom(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String uniqueConstraintKey(List<String> fields) {
        return fields == null || fields.isEmpty() ? "" : fields.stream()
                .filter(StringUtils::isNotBlank)
                .map(DynamicQueryGenerator::camelToSnake)
                .collect(Collectors.joining("|"));
    }

    private Object firstPresentValue(Map<String, Object> data, String... keys) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isNotBlank(key) && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::text).filter(StringUtils::isNotBlank).toList();
        }
        String textValue = text(value);
        return StringUtils.isBlank(textValue) ? List.of() : List.of(textValue);
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
