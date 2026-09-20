package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the fields used by data-audit diffing.
 *
 * <p>The published model schema remains the primary metadata source. Runtime edit schema and
 * physical row snapshots supplement it so a stale or incomplete schema cannot silently drop a
 * changed business column from the audit trail.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAuditFieldResolver {

    private final ObjectMapper objectMapper;
    private final DataAuditValueNormalizer valueNormalizer;

    public Map<String, Map<String, LowcodeFieldSchema>> load(AiCrudConfig config) {
        Map<String, Map<String, LowcodeFieldSchema>> result = new LinkedHashMap<>();
        if (config == null || StringUtils.isBlank(config.getTableName())) {
            return result;
        }
        Map<String, LowcodeFieldSchema> fields = new LinkedHashMap<>();
        loadModelFields(config, fields);
        loadEditFields(config, fields);
        if (!fields.isEmpty()) {
            result.put(config.getTableName(), fields);
        }
        return result;
    }

    public Map<String, LowcodeFieldSchema> resolveSnapshotFields(Map<String, LowcodeFieldSchema> configuredFields,
                                                                 Map<String, Object> before,
                                                                 Map<String, Object> after) {
        Map<String, LowcodeFieldSchema> resolved = uniqueFields(configuredFields);
        Set<String> snapshotColumns = new LinkedHashSet<>();
        if (before != null) {
            snapshotColumns.addAll(before.keySet());
        }
        if (after != null) {
            snapshotColumns.addAll(after.keySet());
        }
        for (String column : snapshotColumns) {
            if (StringUtils.isBlank(column) || valueNormalizer.isSystemColumn(column, snakeToCamel(column))) {
                continue;
            }
            LowcodeFieldSchema matched = findMatchingField(resolved.values(), column);
            if (matched == null) {
                LowcodeFieldSchema fallback = fallbackField(column, before, after);
                resolved.putIfAbsent(fallback.getField(), fallback);
                continue;
            }
            if (!column.equals(matched.getField()) && !column.equals(matched.getColumnName())) {
                LowcodeFieldSchema aligned = copy(matched);
                aligned.setColumnName(column);
                resolved.put(aligned.getField(), aligned);
            }
        }
        return resolved;
    }

    private void loadModelFields(AiCrudConfig config, Map<String, LowcodeFieldSchema> target) {
        if (StringUtils.isBlank(config.getModelSchema())) {
            return;
        }
        try {
            LowcodeModelSchema schema = objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
            mergeFields(target, schema.getFields());
        } catch (Exception ex) {
            log.debug("解析审计模型字段失败 configKey={}", config.getConfigKey());
        }
    }

    private void loadEditFields(AiCrudConfig config, Map<String, LowcodeFieldSchema> target) {
        if (StringUtils.isBlank(config.getEditSchema())) {
            return;
        }
        try {
            List<LowcodeFieldSchema> editFields = new ArrayList<>();
            collectEditFields(objectMapper.readTree(config.getEditSchema()), editFields);
            mergeFields(target, editFields);
        } catch (Exception ex) {
            log.debug("解析审计编辑字段失败 configKey={}", config.getConfigKey());
        }
    }

    private void collectEditFields(JsonNode node, List<LowcodeFieldSchema> target) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectEditFields(child, target));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String fieldCode = firstText(node, "field", "prop", "dataIndex");
        if (StringUtils.isNotBlank(fieldCode)) {
            LowcodeFieldSchema field = new LowcodeFieldSchema();
            field.setField(fieldCode);
            field.setColumnName(StringUtils.defaultIfBlank(firstText(node, "columnName"), camelToSnake(fieldCode)));
            field.setLabel(StringUtils.defaultIfBlank(firstText(node, "label", "title", "fieldName"), fieldCode));
            field.setComponentType(firstText(node, "componentType", "type"));
            field.setDataType(StringUtils.defaultIfBlank(firstText(node, "dataType"),
                    inferEditDataType(field.getComponentType())));
            field.setBusinessFieldType(firstText(node, "businessFieldType"));
            field.setDictType(firstText(node, "dictType"));
            target.add(field);
            return;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode child = entry.getValue();
            if (child != null && child.isArray()) {
                collectEditFields(child, target);
            }
        });
    }

    private void mergeFields(Map<String, LowcodeFieldSchema> target, List<LowcodeFieldSchema> fields) {
        if (fields == null) {
            return;
        }
        for (LowcodeFieldSchema field : fields) {
            if (field == null || StringUtils.isBlank(field.getField())) {
                continue;
            }
            LowcodeFieldSchema existing = findMatchingField(target.values(), field.getField());
            if (existing == null && StringUtils.isNotBlank(field.getColumnName())) {
                existing = findMatchingField(target.values(), field.getColumnName());
            }
            if (existing == null) {
                target.put(field.getField(), field);
            } else {
                supplement(existing, field);
            }
        }
    }

    private Map<String, LowcodeFieldSchema> uniqueFields(Map<String, LowcodeFieldSchema> fields) {
        Map<String, LowcodeFieldSchema> result = new LinkedHashMap<>();
        if (fields == null) {
            return result;
        }
        for (LowcodeFieldSchema field : fields.values()) {
            if (field != null && StringUtils.isNotBlank(field.getField())) {
                result.putIfAbsent(field.getField(), field);
            }
        }
        return result;
    }

    private LowcodeFieldSchema findMatchingField(Collection<LowcodeFieldSchema> fields, String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized.isEmpty()) {
            return null;
        }
        for (LowcodeFieldSchema field : fields) {
            if (field != null && (normalized.equals(normalizeIdentifier(field.getField()))
                    || normalized.equals(normalizeIdentifier(field.getColumnName())))) {
                return field;
            }
        }
        return null;
    }

    private void supplement(LowcodeFieldSchema target, LowcodeFieldSchema source) {
        if (StringUtils.isBlank(target.getColumnName())) {
            target.setColumnName(source.getColumnName());
        }
        if (StringUtils.isBlank(target.getLabel())) {
            target.setLabel(source.getLabel());
        }
        if (StringUtils.isBlank(target.getDataType())) {
            target.setDataType(source.getDataType());
        }
        if (StringUtils.isBlank(target.getComponentType())) {
            target.setComponentType(source.getComponentType());
        }
        if (StringUtils.isBlank(target.getBusinessFieldType())) {
            target.setBusinessFieldType(source.getBusinessFieldType());
        }
        if (StringUtils.isBlank(target.getDictType())) {
            target.setDictType(source.getDictType());
        }
    }

    private LowcodeFieldSchema fallbackField(String column,
                                             Map<String, Object> before,
                                             Map<String, Object> after) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(snakeToCamel(column));
        field.setColumnName(column);
        field.setLabel(column);
        Object sample = value(after, column);
        if (sample == null) {
            sample = value(before, column);
        }
        field.setDataType(inferSnapshotDataType(sample));
        return field;
    }

    private Object value(Map<String, Object> row, String column) {
        return row == null ? null : row.get(column);
    }

    private String inferSnapshotDataType(Object value) {
        if (value instanceof BigDecimal || value instanceof Float || value instanceof Double) {
            return "decimal";
        }
        if (value instanceof Number) {
            return "bigint";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof LocalDateTime) {
            return "datetime";
        }
        if (value instanceof LocalDate) {
            return "date";
        }
        if (value instanceof Collection<?> || value instanceof Map<?, ?> || value instanceof JsonNode) {
            return "json";
        }
        return "varchar";
    }

    private String inferEditDataType(String componentType) {
        if ("number".equals(componentType) || "input-number".equals(componentType) || "money".equals(componentType)) {
            return "decimal";
        }
        if ("switch".equals(componentType)) {
            return "boolean";
        }
        if ("date".equals(componentType) || "datetime".equals(componentType)) {
            return componentType;
        }
        return "varchar";
    }

    private LowcodeFieldSchema copy(LowcodeFieldSchema source) {
        LowcodeFieldSchema target = new LowcodeFieldSchema();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode()) {
                String text = StringUtils.trimToNull(value.asText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private String normalizeIdentifier(String value) {
        return StringUtils.defaultString(value)
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }

    private String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String snakeToCamel(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }
}
