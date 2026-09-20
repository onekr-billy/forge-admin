package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * 动态 CRUD 写库前的字段容量校验，避免把数据库截断异常直接暴露给用户。
 */
public final class LowcodeFieldValueValidator {

    private LowcodeFieldValueValidator() {
    }

    public static void validate(LowcodeModelSchema schema,
                                Map<String, Object> values,
                                ObjectMapper objectMapper) {
        if (schema == null || schema.getFields() == null || values == null || values.isEmpty()) {
            return;
        }
        for (LowcodeFieldSchema field : schema.getFields()) {
            ValueRef valueRef = findValue(values, field);
            if (!valueRef.present() || valueRef.value() == null) {
                continue;
            }
            validateValue(field, valueRef.value(), objectMapper);
        }
    }

    public static void validateValue(LowcodeFieldSchema field, Object value, ObjectMapper objectMapper) {
        String dataType = LowcodeFieldConstraintSupport.dataType(field);
        if ("varchar".equals(dataType) || "char".equals(dataType)) {
            validateTextLength(field, value, objectMapper);
            return;
        }
        if (!LowcodeFieldConstraintSupport.isNumeric(field) || value instanceof String text && text.isBlank()) {
            return;
        }
        validateNumber(field, value);
    }

    private static void validateTextLength(LowcodeFieldSchema field, Object value, ObjectMapper objectMapper) {
        Integer maximumLength = field.getLength();
        if (maximumLength == null || maximumLength <= 0) {
            return;
        }
        String storedValue = storageText(value, objectMapper, label(field));
        int actualLength = storedValue.codePointCount(0, storedValue.length());
        if (actualLength > maximumLength) {
            throw new BusinessException("“" + label(field) + "”最多允许 " + maximumLength
                    + " 个字符，当前为 " + actualLength + " 个字符");
        }
    }

    private static void validateNumber(LowcodeFieldSchema field, Object rawValue) {
        BigDecimal number;
        if (rawValue instanceof Boolean flag && "tinyint".equals(LowcodeFieldConstraintSupport.dataType(field))) {
            number = flag ? BigDecimal.ONE : BigDecimal.ZERO;
        } else {
            number = LowcodeFieldConstraintSupport.decimalValue(rawValue);
        }
        if (number == null) {
            throw new BusinessException("“" + label(field) + "”必须为有效数字");
        }

        int allowedScale = LowcodeFieldConstraintSupport.displayScale(field);
        BigDecimal normalized = number.stripTrailingZeros();
        int actualScale = Math.max(normalized.scale(), 0);
        if (LowcodeFieldConstraintSupport.isIntegral(field)
                && !LowcodeFieldConstraintSupport.isMinorUnitMoney(field)
                && actualScale > 0) {
            throw new BusinessException("“" + label(field) + "”只允许填写整数");
        }
        if (("decimal".equals(LowcodeFieldConstraintSupport.dataType(field))
                || LowcodeFieldConstraintSupport.isMinorUnitMoney(field)) && actualScale > allowedScale) {
            throw new BusinessException("“" + label(field) + "”最多允许 " + allowedScale + " 位小数");
        }

        BigDecimal minimum = LowcodeFieldConstraintSupport.effectiveMinimum(field);
        BigDecimal maximum = LowcodeFieldConstraintSupport.effectiveMaximum(field);
        if (minimum != null && number.compareTo(minimum) < 0) {
            throw new BusinessException("“" + label(field) + "”不能小于 " + plain(minimum));
        }
        if (maximum != null && number.compareTo(maximum) > 0) {
            throw new BusinessException("“" + label(field) + "”不能大于 " + plain(maximum));
        }
    }

    private static String storageText(Object value, ObjectMapper objectMapper, String label) {
        if (value instanceof String text) {
            return text;
        }
        if (!(value instanceof Collection<?>) && !(value instanceof Map<?, ?>) && !(value instanceof JsonNode)) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("“" + label + "”无法转换为可保存的内容");
        }
    }

    private static ValueRef findValue(Map<String, Object> values, LowcodeFieldSchema field) {
        String fieldName = field == null ? null : field.getField();
        String columnName = field == null ? null : field.getColumnName();
        String snakeName = camelToSnake(fieldName);
        String camelName = snakeToCamel(columnName);
        for (String key : new String[]{fieldName, columnName, snakeName, camelName}) {
            if (StringUtils.isNotBlank(key) && values.containsKey(key)) {
                return new ValueRef(true, values.get(key));
            }
        }
        return new ValueRef(false, null);
    }

    private static String label(LowcodeFieldSchema field) {
        return StringUtils.defaultIfBlank(field == null ? null : field.getLabel(),
                StringUtils.defaultIfBlank(field == null ? null : field.getField(), "字段"));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String snakeToCamel(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private record ValueRef(boolean present, Object value) {
    }
}
