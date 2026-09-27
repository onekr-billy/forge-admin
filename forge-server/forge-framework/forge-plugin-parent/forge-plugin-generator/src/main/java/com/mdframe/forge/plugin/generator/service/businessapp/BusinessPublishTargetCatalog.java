package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 页面与表单发布目标目录。 */
final class BusinessPublishTargetCatalog {

    private static final String FORM_DESIGNER_SCHEMA_OPTION_KEY = "formDesignerSchema";

    private BusinessPublishTargetCatalog() {
    }

    static Set<String> pageKeys(LowcodePageSchema pageSchema) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("list");
        keys.add("detail");
        if (pageSchema != null && pageSchema.getPages() != null) {
            pageSchema.getPages().forEach(page -> addIfNotBlank(keys, text(page.get("pageKey"))));
        }
        return keys;
    }

    static Set<String> formKeys(Map<String, Object> designerOptions) {
        Set<String> keys = new LinkedHashSet<>();
        Map<String, Object> formSchema = mapValue(designerOptions.get(FORM_DESIGNER_SCHEMA_OPTION_KEY));
        addIfNotBlank(keys, text(formSchema.get("formKey")));
        addIfNotBlank(keys, text(formSchema.get("defaultFormKey")));
        listOfMap(formSchema.get("forms")).forEach(form -> {
            addIfNotBlank(keys, text(form.get("formKey")));
            addIfNotBlank(keys, text(mapValue(form.get("schema")).get("formKey")));
        });
        listOfMap(mapValue(formSchema.get("settings")).get("formAssets")).forEach(asset -> {
            addIfNotBlank(keys, text(asset.get("formKey")));
            addIfNotBlank(keys, text(mapValue(asset.get("schema")).get("formKey")));
        });
        return keys;
    }

    private static void addIfNotBlank(Set<String> keys, String value) {
        if (StringUtils.isNotBlank(value)) {
            keys.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private static List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(BusinessPublishTargetCatalog::mapValue).toList();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
