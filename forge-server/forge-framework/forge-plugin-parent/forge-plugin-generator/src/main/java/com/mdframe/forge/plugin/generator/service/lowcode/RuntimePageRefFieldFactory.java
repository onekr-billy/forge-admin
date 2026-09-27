package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts a page model-reference snapshot into the shared runtime field model. */
final class RuntimePageRefFieldFactory {

    private RuntimePageRefFieldFactory() {
    }

    static LowcodeFieldSchema build(LowcodePageModelRef ref, Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        String sourceField = StringUtils.defaultIfBlank(text(source.get("sourceField")), text(source.get("field")));
        if (StringUtils.isBlank(sourceField)) {
            return null;
        }
        boolean primary = Boolean.TRUE.equals(ref.getPrimary());
        String fieldRef = StringUtils.defaultIfBlank(text(source.get("fieldRef")),
                primary ? sourceField : safeKey(ref.getModelCode()) + "__" + sourceField);
        if (StringUtils.isBlank(fieldRef)) {
            return null;
        }

        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(fieldRef);
        field.setColumnName(StringUtils.defaultIfBlank(text(source.get("columnName")), sourceField));
        String rawLabel = StringUtils.defaultIfBlank(text(source.get("rawLabel")),
                StringUtils.defaultIfBlank(text(source.get("label")), sourceField));
        field.setLabel(primary ? rawLabel : stripChildModelNamePrefix(rawLabel, ref.getModelName()));
        field.setDataType(StringUtils.defaultIfBlank(text(source.get("dataType")), "varchar"));
        field.setLength(integerValue(source.get("length")));
        field.setPrecision(integerValue(source.get("precision")));
        field.setRequired(Boolean.TRUE.equals(booleanValue(source.get("required"))));
        field.setDefaultValue(source.get("defaultValue"));
        field.setSearchable(Boolean.TRUE.equals(booleanValue(source.get("searchable"))));
        field.setListVisible(booleanValue(source.get("listVisible")) == null
                || Boolean.TRUE.equals(booleanValue(source.get("listVisible"))));
        field.setFormVisible(booleanValue(source.get("formVisible")) == null
                || Boolean.TRUE.equals(booleanValue(source.get("formVisible"))));
        field.setComponentType(StringUtils.defaultIfBlank(text(source.get("componentType")), "input"));
        field.setQueryType(StringUtils.defaultIfBlank(text(source.get("queryType")), "eq"));
        field.setDictType(text(source.get("dictType")));
        field.setSensitiveType(StringUtils.defaultIfBlank(text(source.get("sensitiveType")), "NONE"));
        field.setEncryptAlgorithm(text(source.get("encryptAlgorithm")));
        field.setSortable(Boolean.TRUE.equals(booleanValue(source.get("sortable"))));
        field.setPrimaryKey(Boolean.TRUE.equals(booleanValue(source.get("primaryKey"))));
        field.setSystemField(Boolean.TRUE.equals(booleanValue(source.get("systemField"))));
        field.setReadonly(Boolean.TRUE.equals(booleanValue(source.get("readonly"))));
        field.setFieldStatus(StringUtils.defaultIfBlank(text(source.get("fieldStatus")), "ENABLED"));
        field.setAutoIncrement(Boolean.TRUE.equals(booleanValue(source.get("autoIncrement"))));
        field.setWidth(integerValue(source.get("width")));
        field.setRemark(text(source.get("remark")));
        field.setReferenceObjectCode(text(source.get("referenceObjectCode")));
        field.setReferenceDisplayField(text(source.get("referenceDisplayField")));
        Map<String, Object> props = new LinkedHashMap<>();
        if (source.get("basicProps") instanceof Map<?, ?> basicPropsMap) {
            basicPropsMap.forEach((key, value) -> {
                if (key != null) {
                    props.put(String.valueOf(key), value);
                }
            });
        }
        // Keep reference lookup props even when the older snapshot stored them only at field level.
        if (StringUtils.isNotBlank(field.getReferenceObjectCode())) {
            props.putIfAbsent("referenceObjectCode", field.getReferenceObjectCode());
        }
        if (StringUtils.isNotBlank(field.getReferenceDisplayField())) {
            props.putIfAbsent("referenceDisplayField", field.getReferenceDisplayField());
        }
        if (!props.isEmpty()) {
            field.setBasicProps(props);
        }
        if (source.get("advancedProps") instanceof Map<?, ?> advanced) {
            field.setAdvancedProps(castStringKeyMap(advanced));
        }
        if (source.get("formulaConfig") instanceof Map<?, ?> formula) {
            field.setFormulaConfig(castStringKeyMap(formula));
        }
        return field;
    }

    static String stripChildModelNamePrefix(String label, String modelName) {
        String normalizedLabel = StringUtils.trimToEmpty(label);
        String normalizedModelName = StringUtils.trimToEmpty(modelName);
        if (normalizedLabel.isEmpty() || normalizedModelName.isEmpty()) {
            return normalizedLabel;
        }
        List<String> prefixes = List.of(
                normalizedModelName + " · ",
                normalizedModelName + "·",
                normalizedModelName + ".",
                normalizedModelName + "。",
                normalizedModelName + ":",
                normalizedModelName + "："
        );
        for (String prefix : prefixes) {
            if (normalizedLabel.startsWith(prefix)) {
                return normalizedLabel.substring(prefix.length()).trim();
            }
        }
        return normalizedLabel;
    }

    static String safeKey(String value) {
        String key = StringUtils.defaultIfBlank(value, "model").replaceAll("[^A-Za-z0-9_]", "_");
        return StringUtils.defaultIfBlank(key, "model");
    }

    private static Map<String, Object> castStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private static Integer integerValue(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
