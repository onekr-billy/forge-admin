package com.mdframe.forge.plugin.generator.service.businessapp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批标题只替换标量字段。主从表详情把字段放在 main 里，不能把整份记录当成一层键值替换。
 */
final class BusinessApprovalTitleRenderer {

    private BusinessApprovalTitleRenderer() {
    }

    static String render(String template,
                         Map<String, Object> recordData,
                         Map<String, String> extras,
                         String fallback) {
        if (template == null || template.isBlank()) {
            return fallback;
        }
        Map<String, Object> values = flatten(recordData);
        if (extras != null) {
            extras.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !values.containsKey(key)) {
                    values.put(key, value);
                }
            });
        }
        String resolved = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            resolved = replace(resolved, entry.getKey(), entry.getValue());
        }
        String trimmed = resolved == null ? "" : resolved.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static Map<String, Object> flatten(Map<String, Object> recordData) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (recordData == null || recordData.isEmpty()) {
            return values;
        }
        Object main = recordData.get("main");
        if (main instanceof Map<?, ?> mainRecord) {
            putScalars(values, mainRecord);
        }
        recordData.forEach((key, value) -> {
            if (key == null || "main".equals(key) || "children".equals(key) || !isScalar(value)) {
                return;
            }
            values.putIfAbsent(key, value);
        });
        return values;
    }

    private static void putScalars(Map<String, Object> values, Map<?, ?> source) {
        source.forEach((key, value) -> {
            if (key != null && isScalar(value)) {
                values.putIfAbsent(String.valueOf(key), value);
            }
        });
    }

    private static boolean isScalar(Object value) {
        return !(value instanceof Map<?, ?> || value instanceof Iterable<?>);
    }

    private static String replace(String template, String key, Object value) {
        if (template == null || key == null || key.isBlank()) {
            return template;
        }
        String text = value == null ? "" : String.valueOf(value);
        String result = replaceToken(template, key, text);
        result = replaceToken(result, snakeToCamel(key), text);
        return replaceToken(result, camelToSnake(key), text);
    }

    private static String replaceToken(String template, String key, String value) {
        if (key == null || key.isBlank()) {
            return template;
        }
        return template.replace("${" + key + "}", value).replace("{" + key + "}", value);
    }

    private static String snakeToCamel(String value) {
        if (value == null || !value.contains("_")) {
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

    private static String camelToSnake(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(ch));
        }
        return result.toString();
    }
}
