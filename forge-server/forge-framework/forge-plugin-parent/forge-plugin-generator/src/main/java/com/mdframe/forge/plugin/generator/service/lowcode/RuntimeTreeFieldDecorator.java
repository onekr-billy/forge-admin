package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 为运行时树字段补充当前对象的 treeSelect 协议及选项源。 */
final class RuntimeTreeFieldDecorator {

    private RuntimeTreeFieldDecorator() {
    }

    @SuppressWarnings("unchecked")
    static void decorate(List<Map<String, Object>> fields, String configKey,
                         LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema,
                         boolean treeRuntime, boolean embeddedTreeTableRuntime) {
        if (!treeRuntime || StringUtils.isBlank(configKey)) {
            return;
        }
        boolean leftTree = RuntimeTreeConfigBuilder.isLeftTreeRightTableLayout(pageSchema);
        boolean modelEmbeddedTree = isModelEmbeddedTreeEnabled(modelSchema);
        if (leftTree && !modelEmbeddedTree) {
            decorateSelfTreeSelectFields(fields, configKey, modelSchema);
            decorateCurrentObjectParentTreeSelect(fields, configKey, modelSchema);
            return;
        }
        if (!leftTree && !embeddedTreeTableRuntime) {
            return;
        }
        Map<String, Object> treeConfig = RuntimeTreeConfigBuilder.buildTreeConfig(
                modelSchema, pageSchema, RuntimeTreeConfigBuilder.extractTreeConfigOverrides(pageSchema));
        String parentField = modelEmbeddedTree
                ? firstNonBlank(
                        modelSchema.getTreeConfig() != null ? modelSchema.getTreeConfig().getParentField() : null,
                        text(treeConfig.get("parentField")),
                        "parentId")
                : firstNonBlank(text(treeConfig.get("parentField")), text(treeConfig.get("filterField")));
        if (StringUtils.isBlank(parentField)) {
            return;
        }
        for (Map<String, Object> item : fields) {
            if (!parentField.equals(text(item.get("field")))) {
                continue;
            }
            String label = StringUtils.defaultIfBlank(text(item.get("label")), parentField);
            item.put("type", "treeSelect");
            item.put("queryType", "eq");
            Map<String, Object> props = new LinkedHashMap<>();
            Object sourceProps = item.get("props");
            if (sourceProps instanceof Map<?, ?> sourcePropsMap) {
                props.putAll((Map<String, Object>) sourcePropsMap);
            }
            props.putIfAbsent("placeholder", "请选择" + label);
            props.putIfAbsent("clearable", true);
            props.putIfAbsent("filterable", true);
            Map<String, Object> optionSource = RuntimeTreeConfigBuilder.buildTreeOptionSource(configKey, treeConfig);
            props.put("optionSource", optionSource);
            item.put("optionSource", optionSource);
            item.put("props", props);
        }
    }

    @SuppressWarnings("unchecked")
    private static void decorateSelfTreeSelectFields(List<Map<String, Object>> fields,
                                                     String configKey, LowcodeModelSchema modelSchema) {
        if (fields == null || StringUtils.isBlank(configKey) || !canUseSelfTreeOptionSource(modelSchema)) {
            return;
        }
        Map<String, Object> treeConfig = new LinkedHashMap<>();
        treeConfig.put("childrenField", "children");
        if (modelSchema != null && modelSchema.getTreeConfig() != null) {
            putIfNotBlank(treeConfig, "childrenField", modelSchema.getTreeConfig().getChildrenField());
        }
        for (Map<String, Object> item : fields) {
            if (!"treeSelect".equals(text(item.get("type")))) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            Object sourceProps = item.get("props");
            if (sourceProps instanceof Map<?, ?> sourcePropsMap) {
                props.putAll((Map<String, Object>) sourcePropsMap);
            }
            if (hasEffectiveOptionSource(item.get("optionSource"))
                    || hasEffectiveOptionSource(props.get("optionSource"))) {
                continue;
            }
            Map<String, Object> optionSource = RuntimeTreeConfigBuilder.buildTreeOptionSource(configKey, treeConfig);
            props.put("optionSource", optionSource);
            item.put("optionSource", optionSource);
            item.put("props", props);
        }
    }

    private static boolean canUseSelfTreeOptionSource(LowcodeModelSchema modelSchema) {
        if (modelSchema == null) {
            return false;
        }
        String parentField = firstNonBlank(
                modelSchema.getTreeConfig() != null ? modelSchema.getTreeConfig().getParentField() : null,
                null);
        if (StringUtils.isNotBlank(parentField)) {
            return findField(modelSchema, parentField) != null;
        }
        return findField(modelSchema, "parentId") != null
                || findField(modelSchema, "pid") != null
                || findField(modelSchema, "parentCode") != null;
    }

    @SuppressWarnings("unchecked")
    private static void decorateCurrentObjectParentTreeSelect(List<Map<String, Object>> fields,
                                                               String configKey, LowcodeModelSchema modelSchema) {
        if (fields == null || StringUtils.isBlank(configKey)) {
            return;
        }
        String parentField = firstNonBlank(
                modelSchema != null && modelSchema.getTreeConfig() != null
                        ? modelSchema.getTreeConfig().getParentField() : null,
                findField(modelSchema, "parentId") != null ? "parentId" : null,
                findField(modelSchema, "pid") != null ? "pid" : null,
                findField(modelSchema, "parentCode") != null ? "parentCode" : null);
        if (StringUtils.isBlank(parentField) || findField(modelSchema, parentField) == null) {
            return;
        }
        Map<String, Object> treeConfig = new LinkedHashMap<>();
        treeConfig.put("childrenField", "children");
        if (modelSchema != null && modelSchema.getTreeConfig() != null) {
            putIfNotBlank(treeConfig, "childrenField", modelSchema.getTreeConfig().getChildrenField());
        }
        for (Map<String, Object> item : fields) {
            if (!parentField.equals(text(item.get("field")))) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            Object sourceProps = item.get("props");
            if (sourceProps instanceof Map<?, ?> sourcePropsMap) {
                props.putAll((Map<String, Object>) sourcePropsMap);
            }
            if (hasEffectiveOptionSource(item.get("optionSource"))
                    || hasEffectiveOptionSource(props.get("optionSource"))) {
                if (!"treeSelect".equals(text(item.get("type")))) {
                    item.put("type", "treeSelect");
                }
                continue;
            }
            String label = StringUtils.defaultIfBlank(text(item.get("label")), parentField);
            item.put("type", "treeSelect");
            item.put("queryType", "eq");
            props.putIfAbsent("placeholder", "请选择" + label);
            props.putIfAbsent("clearable", true);
            props.putIfAbsent("filterable", true);
            Map<String, Object> optionSource = RuntimeTreeConfigBuilder.buildTreeOptionSource(configKey, treeConfig);
            props.put("optionSource", optionSource);
            item.put("optionSource", optionSource);
            item.put("props", props);
        }
    }

    static boolean hasEffectiveOptionSource(Object source) {
        if (!(source instanceof Map<?, ?> map) || map.isEmpty()) {
            return false;
        }
        return StringUtils.isNotBlank(text(map.get("type")))
                || StringUtils.isNotBlank(text(map.get("api")))
                || StringUtils.isNotBlank(text(map.get("querySourceCode")))
                || StringUtils.isNotBlank(text(map.get("sourceKey")))
                || StringUtils.isNotBlank(text(map.get("objectCode")))
                || StringUtils.isNotBlank(text(map.get("businessObjectCode")));
    }

    private static boolean isModelEmbeddedTreeEnabled(LowcodeModelSchema modelSchema) {
        if (modelSchema == null) {
            return false;
        }
        if ("TREE".equalsIgnoreCase(StringUtils.defaultIfBlank(modelSchema.getAppType(), ""))) {
            return true;
        }
        return modelSchema.getTreeConfig() != null && Boolean.TRUE.equals(modelSchema.getTreeConfig().getEnabled());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static LowcodeFieldSchema findField(LowcodeModelSchema modelSchema, String fieldName) {
        if (modelSchema == null || modelSchema.getFields() == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        return modelSchema.getFields().stream()
                .filter(field -> fieldName.equals(field.getField()))
                .findFirst()
                .orElse(null);
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
