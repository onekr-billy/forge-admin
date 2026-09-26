package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Compiles field metadata into the existing runtime dictionary and policy contracts. */
final class RuntimeFieldMetadataCompiler {

    List<Map<String, Object>> dictConfig(LowcodeModelSchema modelSchema) {
        Map<String, String> dictMap = new LinkedHashMap<>();
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (StringUtils.isNotBlank(field.getDictType())) {
                dictMap.putIfAbsent(field.getDictType(), StringUtils.defaultIfBlank(field.getLabel(), field.getDictType()));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : dictMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dictType", entry.getKey());
            item.put("dictName", entry.getValue());
            item.put("isNew", false);
            item.put("items", List.of());
            result.add(item);
        }
        return result;
    }

    Map<String, Object> desensitizeConfig(LowcodeModelSchema modelSchema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            String sensitiveType = StringUtils.defaultIfBlank(field.getSensitiveType(), "NONE").toUpperCase(Locale.ROOT);
            if ("NONE".equals(sensitiveType)) {
                continue;
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", sensitiveType);
            rule.put("label", StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
            result.put(field.getField(), rule);
        }
        return result;
    }

    Map<String, Object> encryptConfig(LowcodeModelSchema modelSchema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (StringUtils.isBlank(field.getEncryptAlgorithm())) {
                continue;
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("algorithm", field.getEncryptAlgorithm());
            result.put(field.getField(), rule);
        }
        return result;
    }

    Map<String, Object> translationConfig(LowcodeModelSchema modelSchema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            String componentType = StringUtils.defaultIfBlank(field.getComponentType(), "input");
            Map<String, Object> rule = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(field.getDictType())) {
                rule.put("dictType", field.getDictType());
            } else if ("orgTreeSelect".equals(componentType)) {
                rule.put("type", "orgName");
            } else if ("userSelect".equals(componentType)) {
                rule.put("type", "userName");
            } else if ("regionTreeSelect".equals(componentType)) {
                rule.put("type", "regionName");
            } else if ("fileUpload".equals(componentType) || "imageUpload".equals(componentType)) {
                rule.put("type", componentType);
            } else if (hasDynamicOptionSource(field, componentType)) {
                rule.put("type", "relationName");
            }
            if (!rule.isEmpty()) {
                rule.put("targetField", field.getField() + "Name");
                result.put(field.getField(), rule);
            }
        }
        return result;
    }

    boolean hasDynamicOptionSource(LowcodeFieldSchema field, String componentType) {
        if (field == null || !Set.of("select", "radio", "radioButton", "checkbox", "cascader", "treeSelect", "transfer")
                .contains(StringUtils.defaultString(componentType))) {
            return false;
        }
        Map<String, Object> props = field.getBasicProps();
        if (props == null || props.isEmpty()) {
            return false;
        }
        Object source = props.get("optionSource");
        if (!(source instanceof Map<?, ?> map)) {
            return false;
        }
        Object typeValue = map.get("type");
        String type = typeValue == null ? null : String.valueOf(typeValue);
        return StringUtils.isNotBlank(type) && !"STATIC".equalsIgnoreCase(type.replace('-', '_'));
    }
}
