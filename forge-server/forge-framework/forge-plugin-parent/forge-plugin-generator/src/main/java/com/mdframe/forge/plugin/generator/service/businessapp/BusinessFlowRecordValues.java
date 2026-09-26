package com.mdframe.forge.plugin.generator.service.businessapp;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务记录字段访问器。
 * <p>
 * 兼容历史记录的 camelCase、snake_case，以及主子表载荷中的 {@code main} 包装。
 */
final class BusinessFlowRecordValues {

    private BusinessFlowRecordValues() {
    }

    static Object read(Map<String, Object> recordData, String field) {
        if (recordData == null || StringUtils.isBlank(field)) {
            return null;
        }
        Object value = readFlat(recordData, field);
        if (value != null || contains(recordData, field)) {
            return value;
        }
        Object main = recordData.get("main");
        if (main instanceof Map<?, ?> mainMap) {
            Map<String, Object> mainRecord = new LinkedHashMap<>();
            mainMap.forEach((key, item) -> {
                if (key != null) {
                    mainRecord.put(String.valueOf(key), item);
                }
            });
            return readFlat(mainRecord, field);
        }
        return null;
    }

    static boolean contains(Map<String, Object> recordData, String field) {
        if (recordData == null || StringUtils.isBlank(field)) {
            return false;
        }
        return recordData.containsKey(field)
                || recordData.containsKey(snakeToCamel(field))
                || recordData.containsKey(camelToSnake(field));
    }

    static boolean sameField(String left, String right) {
        return StringUtils.equalsIgnoreCase(snakeToCamel(left), snakeToCamel(right));
    }

    static String snakeToCamel(String value) {
        if (StringUtils.isBlank(value) || !value.contains("_")) {
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

    static String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                result.append('_').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static Object readFlat(Map<String, Object> recordData, String field) {
        if (recordData.containsKey(field)) {
            return recordData.get(field);
        }
        String camelField = snakeToCamel(field);
        if (recordData.containsKey(camelField)) {
            return recordData.get(camelField);
        }
        String snakeField = camelToSnake(field);
        if (recordData.containsKey(snakeField)) {
            return recordData.get(snakeField);
        }
        return null;
    }
}
