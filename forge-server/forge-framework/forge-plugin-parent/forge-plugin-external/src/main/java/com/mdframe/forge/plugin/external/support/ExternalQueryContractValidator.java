package com.mdframe.forge.plugin.external.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ExternalQueryContractValidator {

    private static final Set<String> READ_QUERY_METHODS = Set.of("GET", "HEAD", "POST");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "string", "integer", "number", "boolean", "object", "array");
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final int MAX_SCHEMA_ITEMS = 100;
    private static final int MAX_STRING_LENGTH = 10000;

    public void validateConfiguration(Boolean enabled, String method, Boolean permissionCheckEnabled,
                                      String requiredPermission, String inputSchemaJson,
                                      String outputSchemaJson) {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!READ_QUERY_METHODS.contains(normalizedMethod)) {
            throw new BusinessException("低代码只读查询源仅支持GET、HEAD或已确认只读语义的POST接口");
        }
        if (!Boolean.TRUE.equals(permissionCheckEnabled) || requiredPermission == null
                || requiredPermission.isBlank()) {
            throw new BusinessException("低代码只读查询源必须启用权限校验并配置权限码");
        }
        validateSchema(inputSchemaJson, true, false);
        validateSchema(outputSchemaJson, false, true);
    }

    public Map<String, Object> validateAndFilter(String inputSchemaJson, Map<String, Object> params) {
        JSONArray schema = validateSchema(inputSchemaJson, true, false);
        Map<String, Object> source = params == null ? Map.of() : params;
        Set<String> allowedNames = new LinkedHashSet<>();
        for (Object item : schema) {
            allowedNames.add(((JSONObject) item).getString("name"));
        }
        if (source.keySet().stream().anyMatch(name -> !allowedNames.contains(name))) {
            throw new BusinessException("查询参数包含输入契约未声明的字段");
        }

        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Object item : schema) {
            JSONObject definition = (JSONObject) item;
            String name = definition.getString("name");
            String label = label(definition);
            Object value = source.get(name);
            if (isEmpty(value)) {
                if (definition.getBooleanValue("required")) {
                    throw new BusinessException("查询参数[" + label + "]不能为空");
                }
                continue;
            }
            String type = normalizeType(definition.getString("type"));
            Object normalizedValue = normalizeValue(value, type);
            if (normalizedValue == null) {
                throw new BusinessException("查询参数[" + label + "]类型不正确");
            }
            Integer maxLength = readMaxLength(definition);
            if (normalizedValue instanceof String text && maxLength != null && text.length() > maxLength) {
                throw new BusinessException("查询参数[" + label + "]长度超过限制");
            }
            filtered.put(name, normalizedValue);
        }
        return filtered;
    }

    JSONArray validateSchema(String schemaJson, boolean input, boolean requireItems) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new BusinessException(input ? "低代码查询输入Schema不能为空，无参数时请配置[]"
                    : "低代码查询输出Schema不能为空");
        }
        JSONArray schema;
        try {
            Object parsed = JSON.parse(schemaJson);
            if (!(parsed instanceof JSONArray array)) {
                throw new BusinessException("低代码查询Schema必须是JSON数组");
            }
            schema = array;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("低代码查询Schema必须是合法JSON数组");
        }
        if (schema.size() > MAX_SCHEMA_ITEMS) {
            throw new BusinessException("低代码查询Schema字段数量不能超过100个");
        }
        if (requireItems && schema.isEmpty()) {
            throw new BusinessException("低代码查询输出Schema至少配置一个字段");
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : schema) {
            if (!(item instanceof JSONObject definition)) {
                throw new BusinessException("低代码查询Schema数组项必须是JSON对象");
            }
            String name = definition.getString("name");
            if (name == null || !SAFE_NAME.matcher(name).matches()) {
                throw new BusinessException("低代码查询Schema字段名格式不正确");
            }
            if (!names.add(name)) {
                throw new BusinessException("低代码查询Schema存在重复字段名");
            }
            String type = normalizeType(definition.getString("type"));
            if (!SUPPORTED_TYPES.contains(type)) {
                throw new BusinessException("低代码查询Schema字段类型不支持");
            }
            if (!input) {
                String path = definition.getString("path");
                if (path == null || path.isBlank() || path.length() > 255) {
                    throw new BusinessException("低代码查询输出Schema字段路径不能为空且长度不能超过255");
                }
            }
            Integer maxLength = readMaxLength(definition);
            if (maxLength != null && (maxLength < 1 || maxLength > MAX_STRING_LENGTH)) {
                throw new BusinessException("低代码查询Schema字符串长度必须在1到10000之间");
            }
        }
        return schema;
    }

    private boolean matchesType(Object value, String type) {
        return switch (type) {
            case "string" -> isScalarStringValue(value);
            case "integer" -> isInteger(value);
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof Collection<?>;
            default -> false;
        };
    }

    private Object normalizeValue(Object value, String type) {
        if ("string".equals(type)) {
            return isScalarStringValue(value) ? String.valueOf(value) : null;
        }
        return matchesType(value, type) ? value : null;
    }

    private boolean isScalarStringValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private boolean isInteger(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private Integer readMaxLength(JSONObject definition) {
        Object value = definition.get("maxLength");
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new BusinessException("低代码查询Schema字符串长度必须是整数");
        }
        try {
            return new BigDecimal(number.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new BusinessException("低代码查询Schema字符串长度必须是整数");
        }
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "string" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String label(JSONObject definition) {
        String label = definition.getString("label");
        return label == null || label.isBlank() ? definition.getString("name") : label;
    }

    private boolean isEmpty(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }
}
