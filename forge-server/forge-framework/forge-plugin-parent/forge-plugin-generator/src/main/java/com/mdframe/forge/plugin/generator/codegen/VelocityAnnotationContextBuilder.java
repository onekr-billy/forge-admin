package com.mdframe.forge.plugin.generator.codegen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.GenTableColumn;
import com.mdframe.forge.plugin.generator.util.GenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Velocity 安全注解上下文构建器。
 */
@Slf4j
@RequiredArgsConstructor
final class VelocityAnnotationContextBuilder {

    private final ObjectMapper objectMapper;

    Map<String, Object> build(AiCrudConfig config, List<GenTableColumn> columns) {
        Map<String, Object> flags = new LinkedHashMap<>();
        applyDictionaryFlags(config, columns, flags);
        applyEncryptionFlags(config, flags);
        applyDesensitizeFlags(config, columns, flags);
        return flags;
    }

    private void applyDictionaryFlags(AiCrudConfig config,
                                      List<GenTableColumn> columns,
                                      Map<String, Object> flags) {
        boolean hasDictConfig = false;
        Set<String> dictFields = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(config.getDictConfig())
            && StringUtils.isNotBlank(config.getColumnsSchema())) {
            try {
                Map<String, String> dictTypeToField = new LinkedHashMap<>();
                for (String schemaJson : new String[]{config.getColumnsSchema(), config.getEditSchema()}) {
                    if (StringUtils.isBlank(schemaJson)) {
                        continue;
                    }
                    List<Map<String, Object>> schemaList = objectMapper.readValue(
                        schemaJson, new TypeReference<>() { });
                    for (Map<String, Object> column : schemaList) {
                        Object dictType = column.get("dictType");
                        Object field = column.get("field") != null ? column.get("field")
                            : column.get("dataIndex") != null ? column.get("dataIndex") : column.get("key");
                        if (dictType != null && field != null) {
                            dictTypeToField.putIfAbsent(String.valueOf(dictType), String.valueOf(field));
                        }
                    }
                }
                List<Map<String, Object>> dictionaries = objectMapper.readValue(
                    config.getDictConfig(), new TypeReference<>() { });
                for (Map<String, Object> dictionary : dictionaries) {
                    String dictType = (String) dictionary.get("dictType");
                    if (StringUtils.isBlank(dictType)) {
                        continue;
                    }
                    String field = dictTypeToField.get(dictType);
                    if (StringUtils.isNotBlank(field)) {
                        dictFields.add(field);
                        hasDictConfig = true;
                        columns.stream()
                            .filter(column -> field.equals(column.getJavaField()))
                            .findFirst()
                            .ifPresent(column -> column.setDictType(dictType));
                    }
                }
            } catch (Exception e) {
                log.warn("[VelocityAnnotationContextBuilder] 解析 dictConfig 失败", e);
            }
        }
        flags.put("hasDictConfig", hasDictConfig);
        flags.put("hasDictTrans", hasDictConfig || GenUtils.hasDictTrans(columns));
        flags.put("dictFields", dictFields);
    }

    private void applyEncryptionFlags(AiCrudConfig config, Map<String, Object> flags) {
        boolean enableDecrypt = false;
        boolean enableEncrypt = false;
        if (StringUtils.isNotBlank(config.getEncryptConfig())) {
            try {
                Map<String, Object> encryption = objectMapper.readValue(
                    config.getEncryptConfig(), new TypeReference<>() { });
                enableEncrypt = booleanValue(encryption.get("enableEncrypt"));
                enableDecrypt = booleanValue(encryption.get("enableDecrypt"));
            } catch (Exception e) {
                log.warn("[VelocityAnnotationContextBuilder] 解析 encryptConfig 失败", e);
            }
        }
        flags.put("hasEncrypt", enableEncrypt || enableDecrypt);
        flags.put("enableDecrypt", enableDecrypt);
        flags.put("enableEncrypt", enableEncrypt);
    }

    private void applyDesensitizeFlags(AiCrudConfig config,
                                       List<GenTableColumn> columns,
                                       Map<String, Object> flags) {
        columns.forEach(column -> column.setDesensitizeType(
            normalizeDesensitizeType(column.getDesensitizeType())));
        if (StringUtils.isNotBlank(config.getDesensitizeConfig())) {
            try {
                Map<String, Object> desensitize = objectMapper.readValue(
                    config.getDesensitizeConfig(), new TypeReference<>() { });
                for (Map.Entry<String, Object> entry : desensitize.entrySet()) {
                    String field = entry.getKey();
                    String strategy = resolveDesensitizeStrategy(entry.getValue());
                    if (StringUtils.isNotBlank(field)) {
                        columns.stream()
                            .filter(column -> field.equals(column.getJavaField()))
                            .findFirst()
                            .ifPresent(column -> column.setDesensitizeType(
                                normalizeDesensitizeType(strategy)));
                    }
                }
            } catch (Exception e) {
                log.warn("[VelocityAnnotationContextBuilder] 解析 desensitizeConfig 失败", e);
            }
        }
        flags.put("hasDesensitize", columns.stream()
            .anyMatch(column -> StringUtils.isNotBlank(column.getDesensitizeType())));
    }

    String normalizeDesensitizeType(String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return "NONE".equals(normalized) ? null : normalized;
    }

    private String resolveDesensitizeStrategy(Object value) {
        if (value instanceof Map<?, ?> map && map.get("type") != null) {
            return String.valueOf(map.get("type"));
        }
        return value instanceof String text ? text : null;
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
    }
}
