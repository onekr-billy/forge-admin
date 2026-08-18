package com.mdframe.forge.plugin.generator.service.businessapp;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 动作步骤配置解析工具。
 */
final class BusinessActionStepConfigHelper {

    private BusinessActionStepConfigHelper() {
    }

    static Map<String, Object> buildData(Map<String, Object> config, BusinessActionExecutionContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Object item : firstList(config, "fieldMapping", "fieldMappings", "fields", "params")) {
            Map<String, Object> mapping = asMap(item);
            if (mapping.isEmpty()) {
                continue;
            }
            String targetField = firstText(mapping, "targetField", "target", "field", "name");
            if (StringUtils.isBlank(targetField)) {
                continue;
            }
            data.put(targetField.trim(), resolveMappingValue(mapping, context));
        }
        data.putAll(asMap(firstValue(config, "staticValues", "values", "data")));
        return data;
    }

    static Map<String, Object> buildExpectedData(
            Map<String, Object> config,
            BusinessActionExecutionContext context) {
        Map<String, Object> expected = new LinkedHashMap<>();
        for (Object item : firstList(config, "expectedFieldMappings", "expectedMappings", "conditions")) {
            Map<String, Object> mapping = asMap(item);
            if (mapping.isEmpty()) {
                continue;
            }
            String targetField = firstText(mapping, "targetField", "target", "field", "name");
            if (StringUtils.isBlank(targetField)) {
                throw new IllegalArgumentException("条件更新字段不能为空");
            }
            if (expected.containsKey(targetField)) {
                throw new IllegalArgumentException("条件更新字段重复: " + targetField);
            }
            expected.put(targetField, resolveMappingValue(mapping, context));
        }
        Map<String, Object> staticExpected = asMap(firstValue(config, "expectedValues", "expected"));
        staticExpected.forEach((field, value) -> {
            if (expected.putIfAbsent(field, value) != null) {
                throw new IllegalArgumentException("条件更新字段重复: " + field);
            }
        });
        return expected;
    }

    /**
     * 解析 ASSERT_RECORD 的字段间数值比较。比较值可以来自当前动作表单、记录、父记录或可信系统上下文，
     * 但最终只保留字段、操作符和值，避免把客户端路径或任意表达式下沉到 SQL 层。
     */
    static List<Map<String, Object>> buildNumericConstraints(
            Map<String, Object> config,
            BusinessActionExecutionContext context) {
        List<Map<String, Object>> constraints = new ArrayList<>();
        for (Object item : firstList(config, "numericConstraints", "fieldComparisons")) {
            Map<String, Object> raw = asMap(item);
            if (raw.isEmpty()) {
                continue;
            }
            String field = firstText(raw, "field", "targetField", "target");
            String operator = firstText(raw, "operator", "op");
            if (StringUtils.isBlank(field) || StringUtils.isBlank(operator)) {
                throw new IllegalArgumentException("数值比较缺少 field 或 operator");
            }
            Map<String, Object> valueMapping = new LinkedHashMap<>(raw);
            valueMapping.remove("field");
            valueMapping.remove("targetField");
            valueMapping.remove("target");
            valueMapping.remove("operator");
            valueMapping.remove("op");
            Object value = resolveMappingValue(valueMapping, context);
            if (value == null) {
                throw new IllegalArgumentException("数值比较值不能为空: " + field);
            }
            constraints.add(new LinkedHashMap<>(Map.of(
                    "field", field,
                    "operator", operator,
                    "value", value)));
        }
        return constraints;
    }

    static Object resolveTargetRecordId(Map<String, Object> config, BusinessActionExecutionContext context) {
        Object explicit = firstValue(config, "targetRecordId", "recordId");
        if (explicit != null) {
            return explicit;
        }
        String sourceField = firstText(config, "targetRecordIdField", "recordIdField");
        if (StringUtils.isNotBlank(sourceField)) {
            return resolvePath(sourceField, context);
        }
        if (context.getRequest() == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(context.getRequest().getChildRecordId()),
                StringUtils.trimToNull(context.getRequest().getRecordId()));
    }

    static Object resolveMappingValue(Map<String, Object> mapping, BusinessActionExecutionContext context) {
        if (mapping.containsKey("value")) {
            return mapping.get("value");
        }
        if (mapping.containsKey("staticValue")) {
            return mapping.get("staticValue");
        }
        String sourceType = StringUtils.defaultIfBlank(firstText(mapping, "sourceType", "type"), "record")
                .trim()
                .toLowerCase(Locale.ROOT);
        String sourceField = firstText(mapping, "sourceField", "source", "formField", "field");
        if (StringUtils.isBlank(sourceField)) {
            return null;
        }
        return switch (sourceType) {
            case "parent", "parentrecord", "parent_record" -> readPath(context.getParentRecordData(), sourceField);
            case "form", "formdata", "form_data" -> readPath(context.getFormData(), sourceField);
            case "context" -> readPath(context.getExtraContext(), sourceField);
            case "system" -> resolveSystemValue(sourceField, context);
            case "static" -> mapping.get("value");
            default -> resolvePath(sourceField, context);
        };
    }

    static Object resolvePath(String sourceField, BusinessActionExecutionContext context) {
        String field = StringUtils.trimToEmpty(sourceField);
        if (context != null && context.getScopedVariables() != null) {
            String root = rootSegment(field);
            if (context.getScopedVariables().containsKey(root)) {
                Object scoped = context.getScopedVariables().get(root);
                String nestedPath = field.equals(root) ? "" : field.substring(root.length() + 1);
                return readPath(scoped, nestedPath);
            }
        }
        if (field.startsWith("formData.")) {
            return readPath(context.getFormData(), field.substring("formData.".length()));
        }
        if (field.startsWith("form.")) {
            return readPath(context.getFormData(), field.substring("form.".length()));
        }
        if (field.startsWith("record.")) {
            return readPath(context.getRecordData(), field.substring("record.".length()));
        }
        if (field.startsWith("row.")) {
            return readPath(context.getRecordData(), field.substring("row.".length()));
        }
        if (field.startsWith("parentRecord.")) {
            return readPath(context.getParentRecordData(), field.substring("parentRecord.".length()));
        }
        if (field.startsWith("parent.")) {
            return readPath(context.getParentRecordData(), field.substring("parent.".length()));
        }
        if (field.startsWith("context.")) {
            return readPath(context.getExtraContext(), field.substring("context.".length()));
        }
        Object formValue = readPath(context.getFormData(), field);
        if (formValue != null) {
            return formValue;
        }
        return readPath(context.getRecordData(), field);
    }

    static Object readPath(Object source, String path) {
        if (source == null) {
            return null;
        }
        if (StringUtils.isBlank(path)) {
            return source;
        }
        Object cursor = source;
        for (String part : path.split("\\.")) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            if (!(cursor instanceof Map<?, ?> map)) {
                return null;
            }
            cursor = map.get(part);
            if (cursor == null) {
                cursor = map.get(camelToSnake(part));
            }
            if (cursor == null) {
                cursor = map.get(snakeToCamel(part));
            }
        }
        return cursor;
    }

    static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                map.put(String.valueOf(key), item);
            }
        });
        return map;
    }

    static List<?> firstList(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().toList();
        }
        return List.of();
    }

    static Object firstValue(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    static String firstText(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    static Object readPath(Map<String, Object> source, String path) {
        return readPath((Object) source, path);
    }

    private static String rootSegment(String path) {
        String value = StringUtils.trimToEmpty(path);
        int dotIndex = value.indexOf('.');
        return dotIndex < 0 ? value : value.substring(0, dotIndex);
    }

    private static Object resolveSystemValue(String sourceField, BusinessActionExecutionContext context) {
        String field = StringUtils.defaultString(sourceField);
        Object trusted = readPath(context.getSystemContext(), field);
        if (trusted != null || context.getSystemContext().containsKey(field)) {
            return trusted;
        }
        return readPath(context.getSystemContext(), snakeToCamel(field));
    }

    private static String camelToSnake(String value) {
        return value == null ? null : value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String snakeToCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return builder.toString();
    }
}
