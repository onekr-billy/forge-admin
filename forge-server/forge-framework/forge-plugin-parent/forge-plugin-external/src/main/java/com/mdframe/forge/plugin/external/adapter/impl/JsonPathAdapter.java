package com.mdframe.forge.plugin.external.adapter.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.external.adapter.DataAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JsonPathAdapter implements DataAdapter {

    public static final String CONFIG_VERSION = "FIELD_MAP_V1";

    private static final int MAX_CONFIG_LENGTH = 16 * 1024;
    private static final int MAX_PATH_LENGTH = 256;
    private static final int MAX_PATH_DEPTH = 16;
    private static final int MAX_MAPPING_FIELDS = 128;
    private static final int MAX_COLLECTION_ITEMS = 10_000;
    private static final int MAX_MAPPING_OPERATIONS = 250_000;
    private static final int MAX_RESULT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONFIG_KEYS = Set.of(
            "version", "sourcePath", "fieldMapping", "targetPath");
    private static final Pattern FIELD_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,63}");
    private static final Pattern INDEX_SEGMENT = Pattern.compile("0|[1-9][0-9]{0,6}");

    @Override
    public String getAdapterType() {
        return "JsonPath";
    }

    @Override
    public Object transform(Object originalData, String adapterConfig) {
        TransformConfig config;
        try {
            config = parseConfig(adapterConfig);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("响应转换字段映射配置无效: " + exception.getMessage());
        }

        Object extractedData = extractByPath(originalData, config.sourcePath());
        Object transformedData = mapFields(extractedData, config.fieldMapping());
        Object result = buildTargetStructure(transformedData, config.targetPath());
        ensureResultSize(result);
        return result;
    }

    private Object extractByPath(Object source, String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            return source;
        }
        Object current = source;
        for (String part : normalizedPath.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && INDEX_SEGMENT.matcher(part).matches()) {
                int index = Integer.parseInt(part);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    private Object mapFields(Object data, Map<String, String> mapping) {
        if (data instanceof List<?> list) {
            if (list.size() > MAX_COLLECTION_ITEMS) {
                throw new BusinessException("响应转换记录数超过限制");
            }
            if (mapping.isEmpty()) {
                return data;
            }
            if ((long) list.size() * mapping.size() > MAX_MAPPING_OPERATIONS) {
                throw new BusinessException("响应转换记录数或字段映射操作数超过限制");
            }
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> source)) {
                    throw new BusinessException("字段映射仅支持 JSON 对象或对象数组");
                }
                result.add(mapSingleObject(source, mapping));
            }
            return result;
        }
        if (mapping.isEmpty()) {
            return data;
        }
        if (data instanceof Map<?, ?> source) {
            return mapSingleObject(source, mapping);
        }
        throw new BusinessException("字段映射仅支持 JSON 对象或对象数组");
    }

    private Map<String, Object> mapSingleObject(Map<?, ?> source, Map<String, String> mapping) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            result.put(entry.getKey(), extractByPath(source, entry.getValue()));
        }
        return result;
    }

    private Object buildTargetStructure(Object data, String targetPath) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        String normalizedTargetPath = normalizePath(targetPath);
        if (normalizedTargetPath.isEmpty()) {
            result.put("data", data);
            return result;
        }
        Map<String, Object> container = new LinkedHashMap<>();
        Map<String, Object> current = container;
        String[] parts = normalizedTargetPath.split("\\.");
        for (int index = 0; index < parts.length - 1; index++) {
            Map<String, Object> child = new LinkedHashMap<>();
            current.put(parts[index], child);
            current = child;
        }
        current.put(parts[parts.length - 1], data);
        result.put("data", container);
        return result;
    }

    @Override
    public boolean validateConfig(String adapterConfig) {
        try {
            parseConfig(adapterConfig);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private TransformConfig parseConfig(String adapterConfig) {
        if (adapterConfig == null || adapterConfig.isBlank()) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (adapterConfig.length() > MAX_CONFIG_LENGTH) {
            throw new IllegalArgumentException("配置长度超过限制");
        }
        JSONObject config;
        try {
            config = JSON.parseObject(adapterConfig);
        } catch (Exception exception) {
            throw new IllegalArgumentException("必须是合法 JSON 对象");
        }
        if (config == null || !ALLOWED_CONFIG_KEYS.containsAll(config.keySet())) {
            throw new IllegalArgumentException("包含未允许的配置项");
        }
        Object versionValue = config.get("version");
        if (!(versionValue instanceof String version) || !CONFIG_VERSION.equals(version)) {
            throw new IllegalArgumentException("version 必须为 " + CONFIG_VERSION);
        }
        String sourcePath = optionalString(config, "sourcePath");
        String targetPath = optionalString(config, "targetPath");
        validatePath(sourcePath, true);
        validateTargetPath(targetPath);

        Map<String, String> mapping = parseFieldMapping(config.get("fieldMapping"));
        if (normalizePath(sourcePath).isEmpty()
                && normalizePath(targetPath).isEmpty()
                && mapping.isEmpty()) {
            throw new IllegalArgumentException("至少配置 sourcePath、fieldMapping 或 targetPath 一项");
        }
        return new TransformConfig(sourcePath,
                Collections.unmodifiableMap(new LinkedHashMap<>(mapping)), targetPath);
    }

    private Map<String, String> parseFieldMapping(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof JSONObject mapping)) {
            throw new IllegalArgumentException("fieldMapping 必须是 JSON 对象");
        }
        if (mapping.size() > MAX_MAPPING_FIELDS) {
            throw new IllegalArgumentException("fieldMapping 字段数量超过限制");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            if (!FIELD_SEGMENT.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("fieldMapping 目标字段格式非法");
            }
            if (!(entry.getValue() instanceof String sourcePath) || sourcePath.isBlank()) {
                throw new IllegalArgumentException("fieldMapping 源字段必须是非空路径");
            }
            validatePath(sourcePath, false);
            result.put(entry.getKey(), sourcePath);
        }
        return result;
    }

    private String optionalString(JSONObject config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(key + " 必须是字符串");
        }
        return stringValue.trim();
    }

    private void validatePath(String path, boolean allowEmpty) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            if (allowEmpty) {
                return;
            }
            throw new IllegalArgumentException("路径不能为空");
        }
        if (normalizedPath.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("路径长度超过限制");
        }
        String[] parts = normalizedPath.split("\\.", -1);
        if (parts.length > MAX_PATH_DEPTH) {
            throw new IllegalArgumentException("路径层级超过限制");
        }
        for (String part : parts) {
            if (!FIELD_SEGMENT.matcher(part).matches() && !INDEX_SEGMENT.matcher(part).matches()) {
                throw new IllegalArgumentException("路径仅允许安全字段名和数字索引");
            }
        }
    }

    private void validateTargetPath(String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            return;
        }
        if (normalizedPath.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("路径长度超过限制");
        }
        String[] parts = normalizedPath.split("\\.", -1);
        if (parts.length > MAX_PATH_DEPTH) {
            throw new IllegalArgumentException("路径层级超过限制");
        }
        for (String part : parts) {
            if (!FIELD_SEGMENT.matcher(part).matches()) {
                throw new IllegalArgumentException("目标路径仅允许安全字段名");
            }
        }
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            return normalized.substring(2);
        }
        if ("$".equals(normalized)) {
            return "";
        }
        return normalized;
    }

    private void ensureResultSize(Object result) {
        int resultBytes = JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8).length;
        if (resultBytes > MAX_RESULT_BYTES) {
            throw new BusinessException("响应转换结果超过 2 MiB 限制");
        }
    }

    private record TransformConfig(
            String sourcePath,
            Map<String, String> fieldMapping,
            String targetPath) {
    }
}
