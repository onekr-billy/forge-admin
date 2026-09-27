package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType;

/** 代码应用 Provider 表单资产与绑定元数据的兼容合并，不负责读取绑定或执行字段权限。 */
final class BusinessCodeAppFormAssetMerger {

    private BusinessCodeAppFormAssetMerger() {
    }

    static Map<String, Object> sanitizeCodeAppMetadata(JSONObject metadata, boolean includeInternal) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(metadata);
        List<Map<String, Object>> fields = normalizeCodeAppMetadataFields(metadata.get("fields"), includeInternal);
        if (fields.isEmpty()) {
            fields = firstCodeAppAssetFields(metadata, includeInternal);
        }
        result.put("fields", fields);

        List<Map<String, Object>> formAssets = readMapList(readNestedArray(metadata.get("formAssets")));
        if (!formAssets.isEmpty()) {
            List<Map<String, Object>> normalizedAssets = new ArrayList<>();
            for (Map<String, Object> asset : formAssets) {
                Map<String, Object> item = new LinkedHashMap<>(asset);
                List<Map<String, Object>> assetFields = normalizeCodeAppMetadataFields(
                        item.get("fields") == null ? item.get("fieldCatalog") : item.get("fields"), includeInternal);
                if (assetFields.isEmpty()) {
                    assetFields = fields;
                }
                item.put("fields", assetFields);
                item.put("fieldCatalog", assetFields);
                item.put("fieldCount", assetFields.size());
                item.put("fieldPreview", buildCodeAppFieldPreview(assetFields));
                normalizedAssets.add(item);
            }
            result.put("formAssets", normalizedAssets);
        }
        return result;
    }

    static List<Map<String, Object>> mergeCodeAppAssets(String objectCode,
                                                         List<Map<String, Object>> providerAssets,
                                                         JSONObject metadata,
                                                         boolean includeInternal) {
        if (metadata == null || metadata.isEmpty()) {
            return providerAssets == null ? List.of() : providerAssets;
        }
        List<Map<String, Object>> configuredAssets = readMapList(readNestedArray(metadata.get("formAssets")));
        Set<String> removedAssetKeys = readStringSet(metadata.get("removedFormAssetKeys"));
        List<Map<String, Object>> globalFields = normalizeCodeAppMetadataFields(metadata.get("fields"), includeInternal);
        Set<String> globalHiddenFields = includeInternal ? Set.of() : collectNonPublicCodeAppFieldCodes(metadata.get("fields"));
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> usedConfiguredAssetKeys = new LinkedHashSet<>();
        for (Map<String, Object> providerAsset : providerAssets == null ? List.<Map<String, Object>>of() : providerAssets) {
            if (providerAsset == null) {
                continue;
            }
            String providerAssetKey = codeAppAssetKey(providerAsset);
            if (StringUtils.isNotBlank(providerAssetKey) && removedAssetKeys.contains(providerAssetKey)) {
                continue;
            }
            Map<String, Object> configuredAsset = findConfiguredCodeAppAsset(configuredAssets, providerAsset);
            Map<String, Object> item = new LinkedHashMap<>(providerAsset);
            if (configuredAsset != null) {
                mergeCodeAppAssetDisplay(item, configuredAsset);
                String configuredKey = codeAppAssetKey(configuredAsset);
                if (StringUtils.isNotBlank(configuredKey)) {
                    usedConfiguredAssetKeys.add(configuredKey);
                }
            }
            List<Map<String, Object>> providerFields = readMapList(readNestedArray(
                    providerAsset.get("fields") == null ? providerAsset.get("fieldCatalog") : providerAsset.get("fields")));
            List<Map<String, Object>> configuredFields = globalFields;
            if (configuredFields.isEmpty() && configuredAsset != null) {
                configuredFields = normalizeCodeAppMetadataFields(
                        configuredAsset.get("fields") == null ? configuredAsset.get("fieldCatalog") : configuredAsset.get("fields"),
                        includeInternal);
            }
            Set<String> hiddenFields = new LinkedHashSet<>(globalHiddenFields);
            if (!includeInternal && configuredAsset != null) {
                hiddenFields.addAll(collectNonPublicCodeAppFieldCodes(
                        configuredAsset.get("fields") == null ? configuredAsset.get("fieldCatalog") : configuredAsset.get("fields")));
            }
            List<Map<String, Object>> fields = mergeCodeAppFields(providerFields, configuredFields, hiddenFields);
            if (!fields.isEmpty()) {
                item.put("fields", fields);
                item.put("fieldCatalog", fields);
                item.put("fieldCount", fields.size());
                item.put("fieldPreview", buildCodeAppFieldPreview(fields));
                item.put("metadataConfigured", true);
            }
            item.put("objectCode", StringUtils.defaultIfBlank(textValue(item.get("objectCode")), objectCode));
            result.add(item);
        }
        for (Map<String, Object> configuredAsset : configuredAssets) {
            String configuredKey = codeAppAssetKey(configuredAsset);
            if (StringUtils.isNotBlank(configuredKey) && removedAssetKeys.contains(configuredKey)) {
                continue;
            }
            if (StringUtils.isNotBlank(configuredKey) && usedConfiguredAssetKeys.contains(configuredKey)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(configuredAsset);
            List<Map<String, Object>> fields = normalizeCodeAppMetadataFields(
                    item.get("fields") == null ? item.get("fieldCatalog") : item.get("fields"),
                    includeInternal);
            if (fields.isEmpty()) {
                fields = globalFields;
            }
            item.put("objectCode", StringUtils.defaultIfBlank(textValue(item.get("objectCode")), objectCode));
            item.put("fields", fields);
            item.put("fieldCatalog", fields);
            item.put("fieldCount", fields.size());
            item.put("fieldPreview", buildCodeAppFieldPreview(fields));
            item.put("metadataConfigured", true);
            result.add(item);
        }
        return result;
    }

    private static String codeAppAssetKey(Map<String, Object> asset) {
        if (asset == null) {
            return null;
        }
        String formKey = StringUtils.trimToNull(textValue(asset.get("formKey")));
        if (StringUtils.isNotBlank(formKey)) {
            return "form:" + formKey;
        }
        String providerKey = StringUtils.trimToNull(textValue(asset.get("providerKey")));
        return StringUtils.isBlank(providerKey) ? null : "provider:" + providerKey;
    }

    private static Set<String> readStringSet(Object source) {
        Set<String> result = new LinkedHashSet<>();
        if (source == null) {
            return result;
        }
        if (source instanceof String text && !StringUtils.trimToEmpty(text).startsWith("[")) {
            for (String item : text.split(",")) {
                String value = StringUtils.trimToNull(item);
                if (value != null) {
                    result.add(value);
                }
            }
            return result;
        }
        JSONArray array = readNestedArray(source);
        for (int i = 0; i < array.size(); i++) {
            String value = StringUtils.trimToNull(textValue(array.get(i)));
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> mergeCodeAppFields(List<Map<String, Object>> providerFields,
                                                         List<Map<String, Object>> configuredFields,
                                                         Set<String> hiddenFields) {
        Map<String, Map<String, Object>> configuredMap = new LinkedHashMap<>();
        for (Map<String, Object> configured : configuredFields == null ? List.<Map<String, Object>>of() : configuredFields) {
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(configured.get("field"))),
                    StringUtils.trimToNull(textValue(configured.get("fieldCode"))),
                    StringUtils.trimToNull(textValue(configured.get("code"))));
            if (fieldCode != null) {
                configuredMap.put(fieldCode, configured);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> providerField : providerFields == null ? List.<Map<String, Object>>of() : providerFields) {
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(providerField.get("field"))),
                    StringUtils.trimToNull(textValue(providerField.get("fieldCode"))),
                    StringUtils.trimToNull(textValue(providerField.get("code"))));
            if (fieldCode == null || hiddenFields.contains(fieldCode) || !seen.add(fieldCode)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(providerField);
            Map<String, Object> configured = configuredMap.get(fieldCode);
            if (configured != null) {
                mergeNonNull(item, configured);
            }
            item.put("field", fieldCode);
            item.put("fieldCode", fieldCode);
            result.add(item);
        }

        for (Map<String, Object> configured : configuredFields == null ? List.<Map<String, Object>>of() : configuredFields) {
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(configured.get("field"))),
                    StringUtils.trimToNull(textValue(configured.get("fieldCode"))),
                    StringUtils.trimToNull(textValue(configured.get("code"))));
            if (fieldCode == null || !seen.add(fieldCode)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(configured);
            item.put("field", fieldCode);
            item.put("fieldCode", fieldCode);
            result.add(item);
        }
        return result;
    }

    private static Set<String> collectNonPublicCodeAppFieldCodes(Object source) {
        JSONArray array = readNestedArray(source);
        if (array.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject field = array.getJSONObject(i);
            if (field == null) {
                continue;
            }
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(field.getString("field")),
                    StringUtils.trimToNull(field.getString("fieldCode")),
                    StringUtils.trimToNull(field.getString("code")));
            if (fieldCode != null && !isPublicCodeAppField(field)) {
                result.add(fieldCode);
            }
        }
        return result;
    }

    private static void mergeCodeAppAssetDisplay(Map<String, Object> target, Map<String, Object> configured) {
        for (String key : List.of("appName", "objectName", "businessName", "formKey", "formName",
                "formMode", "type", "providerKey", "providerName", "formUrl", "description")) {
            String value = StringUtils.trimToNull(textValue(configured.get(key)));
            if (value != null) {
                target.put(key, value);
            }
        }
        if (configured.containsKey("supportsSave")) {
            target.put("supportsSave", readBooleanValue(configured.get("supportsSave"), true));
        }
    }

    private static Map<String, Object> findConfiguredCodeAppAsset(List<Map<String, Object>> configuredAssets,
                                                           Map<String, Object> providerAsset) {
        if (configuredAssets == null || configuredAssets.isEmpty()) {
            return null;
        }
        String formKey = StringUtils.trimToNull(textValue(providerAsset.get("formKey")));
        String providerKey = StringUtils.trimToNull(textValue(providerAsset.get("providerKey")));
        for (Map<String, Object> asset : configuredAssets) {
            if (asset == null) {
                continue;
            }
            if (StringUtils.isNotBlank(formKey) && StringUtils.equals(formKey, StringUtils.trimToNull(textValue(asset.get("formKey"))))) {
                return asset;
            }
            if (StringUtils.isNotBlank(providerKey)
                    && StringUtils.equals(providerKey, StringUtils.trimToNull(textValue(asset.get("providerKey"))))) {
                return asset;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> firstCodeAppAssetFields(JSONObject metadata, boolean includeInternal) {
        List<Map<String, Object>> assets = readMapList(readNestedArray(metadata.get("formAssets")));
        for (Map<String, Object> asset : assets) {
            List<Map<String, Object>> fields = normalizeCodeAppMetadataFields(
                    asset.get("fields") == null ? asset.get("fieldCatalog") : asset.get("fields"), includeInternal);
            if (!fields.isEmpty()) {
                return fields;
            }
        }
        return List.of();
    }

    static List<Map<String, Object>> normalizeCodeAppMetadataFields(Object source, boolean includeInternal) {
        JSONArray array = readNestedArray(source);
        if (array.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject field = array.getJSONObject(i);
            if (field == null) {
                continue;
            }
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(field.getString("field")),
                    StringUtils.trimToNull(field.getString("fieldCode")),
                    StringUtils.trimToNull(field.getString("code")));
            if (fieldCode == null || !seen.add(fieldCode)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(field);
            item.put("field", fieldCode);
            item.put("fieldCode", fieldCode);
            item.putIfAbsent("label", fieldCode);
            item.putIfAbsent("componentType", StringUtils.defaultIfBlank(textValue(item.get("type")), "input"));
            item.putIfAbsent("type", normalizeTaskFormFieldType(textValue(item.get("componentType"))));
            item.putIfAbsent("visible", true);
            item.putIfAbsent("readonly", !readBooleanValue(item.get("writable"), true));
            if (!includeInternal && !isPublicCodeAppField(item)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private static boolean isPublicCodeAppField(Map<String, Object> field) {
        if (field == null) {
            return false;
        }
        return readBooleanValue(field.get("visible"), true)
                && !readBooleanValue(field.get("internal"), false)
                && !readBooleanValue(field.get("systemField"), false);
    }

    static boolean isPublicCodeAppFormField(Map<String, Object> field) {
        return isPublicCodeAppField(field) && readBooleanValue(field.get("formVisible"), true);
    }

    private static List<String> buildCodeAppFieldPreview(List<Map<String, Object>> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        List<String> preview = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String text = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("label"))),
                    StringUtils.trimToNull(textValue(field.get("field"))));
            if (text != null) {
                preview.add(text);
            }
            if (preview.size() >= 5) {
                break;
            }
        }
        return preview;
    }

    static void mergeNonNull(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) {
            return;
        }
        source.forEach((key, value) -> {
            if (value != null) {
                target.put(key, value);
            }
        });
    }
}
