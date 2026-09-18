package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Guards ID-backed user and organization selectors against display-name writes. */
final class SelectionIdentifierValidator {

    private static final Set<String> USER_COMPONENTS = Set.of(
            "userselect", "userpicker", "user", "username", "sysuserselect", "forgeuserselect");
    private static final Set<String> ORG_COMPONENTS = Set.of(
            "orgtreeselect", "orgselect", "organizationselect", "departmentselect",
            "departmenttreeselect", "deptselect", "depttreeselect", "eltreeselect",
            "orgname", "deptname", "forgeorgtreeselect");

    private SelectionIdentifierValidator() {
    }

    static void validate(AiCrudConfig config, Map<String, Object> data, ObjectMapper objectMapper) {
        if (config == null || data == null || data.isEmpty() || objectMapper == null
                || StringUtils.isBlank(config.getModelSchema())) {
            return;
        }
        try {
            LowcodeModelSchema modelSchema = objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
            Map<String, LowcodeFieldSchema> modelFields = modelFields(modelSchema);
            if (StringUtils.isNotBlank(config.getEditSchema())) {
                collectAndValidate(objectMapper.readTree(config.getEditSchema()), modelFields, data);
            } else {
                for (LowcodeFieldSchema field : modelFields.values()) {
                    validateField(field.getField(), field.getLabel(), field.getComponentType(), field, data);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // 损坏的 Schema 由既有发布校验处理，运行态不在这里扩大失败范围。
        }
    }

    private static Map<String, LowcodeFieldSchema> modelFields(LowcodeModelSchema schema) {
        Map<String, LowcodeFieldSchema> result = new LinkedHashMap<>();
        if (schema == null || schema.getFields() == null) {
            return result;
        }
        for (LowcodeFieldSchema field : schema.getFields()) {
            if (field == null || StringUtils.isBlank(field.getField())) {
                continue;
            }
            result.putIfAbsent(field.getField(), field);
            if (StringUtils.isNotBlank(field.getColumnName())) {
                result.putIfAbsent(field.getColumnName(), field);
            }
        }
        return result;
    }

    private static void collectAndValidate(JsonNode node,
                                           Map<String, LowcodeFieldSchema> modelFields,
                                           Map<String, Object> data) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectAndValidate(item, modelFields, data));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String fieldName = text(node, "field");
        if (StringUtils.isNotBlank(fieldName)) {
            LowcodeFieldSchema field = modelFields.get(fieldName);
            validateField(fieldName, text(node, "label"),
                    StringUtils.firstNonBlank(text(node, "type"), text(node, "componentType")), field, data);
        }
        collectAndValidate(node.get("children"), modelFields, data);
        collectAndValidate(node.get("items"), modelFields, data);
        collectAndValidate(node.get("components"), modelFields, data);
    }

    private static void validateField(String fieldName,
                                      String label,
                                      String componentType,
                                      LowcodeFieldSchema modelField,
                                      Map<String, Object> data) {
        if (modelField == null) {
            return;
        }
        String normalizedComponent = StringUtils.firstNonBlank(componentType, modelField.getComponentType(), "")
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
        boolean userSelection = USER_COMPONENTS.contains(normalizedComponent);
        boolean orgSelection = ORG_COMPONENTS.contains(normalizedComponent);
        if (!userSelection && !orgSelection) {
            return;
        }
        Object value = payloadValue(data, fieldName, modelField.getColumnName());
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return;
        }
        if (isIntegerIdentifier(value) || isCommaSeparatedIdentifiers(value)) {
            return;
        }
        boolean bigintField = "bigint".equalsIgnoreCase(modelField.getDataType());
        if (!bigintField && !modelField.isMultipleSelection()) {
            return;
        }
        String fieldLabel = StringUtils.firstNonBlank(label, modelField.getLabel(), fieldName);
        if (userSelection) {
            throw new BusinessException(fieldLabel + "必须保存人员ID，不能保存姓名");
        }
        throw new BusinessException(fieldLabel + "必须保存组织ID，不能保存名称");
    }

    @SuppressWarnings("unchecked")
    private static Object payloadValue(Map<String, Object> data, String fieldName, String columnName) {
        if (data.containsKey(fieldName)) {
            return data.get(fieldName);
        }
        if (StringUtils.isNotBlank(columnName) && data.containsKey(columnName)) {
            return data.get(columnName);
        }
        Object main = data.get("main");
        if (main instanceof Map<?, ?> map) {
            return payloadValue((Map<String, Object>) map, fieldName, columnName);
        }
        return null;
    }

    private static boolean isIntegerIdentifier(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger) {
            return ((Number) value).longValue() >= 0;
        }
        if (value instanceof Number number) {
            try {
                BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
                return decimal.signum() >= 0 && decimal.scale() <= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return String.valueOf(value).trim().matches("\\d+");
    }

    private static boolean isCommaSeparatedIdentifiers(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty() && collection.stream().allMatch(SelectionIdentifierValidator::isIntegerIdentifier);
        }
        String text = String.valueOf(value).trim();
        if (!text.contains(",")) {
            return false;
        }
        String[] parts = text.split(",");
        if (parts.length == 0) {
            return false;
        }
        for (String part : parts) {
            if (!isIntegerIdentifier(part.trim())) {
                return false;
            }
        }
        return true;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : StringUtils.trimToNull(value.asText());
    }
}
