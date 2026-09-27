package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolvePrimaryRef;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveRefSourceField;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveRuntimeRelation;

/** Compiles tree navigation configuration and option-source protocol for runtime pages. */
final class RuntimeTreeConfigBuilder {

    private RuntimeTreeConfigBuilder() {
    }

    static Map<String, Object> buildTreeConfig(LowcodeModelSchema modelSchema,
                                                LowcodePageSchema pageSchema,
                                                Object overrides) {
        Map<?, ?> overrideMap = asTreeOverrideMap(overrides);
        Map<String, Object> treeConfig = new LinkedHashMap<>();
        if (modelSchema != null && modelSchema.getTreeConfig() != null) {
            putIfNotBlank(treeConfig, "sourceModelCode", modelSchema.getTreeConfig().getSourceModelCode());
            putIfNotBlank(treeConfig, "sourceModelName", modelSchema.getTreeConfig().getSourceModelName());
            putIfNotBlank(treeConfig, "sourceTableName", modelSchema.getTreeConfig().getSourceTableName());
            putIfNotBlank(treeConfig, "sourceConfigKey", modelSchema.getTreeConfig().getSourceConfigKey());
            putIfNotBlank(treeConfig, "keyField", modelSchema.getTreeConfig().getKeyField());
            putIfNotBlank(treeConfig, "parentField", modelSchema.getTreeConfig().getParentField());
            putIfNotBlank(treeConfig, "labelField", modelSchema.getTreeConfig().getLabelField());
            putIfNotBlank(treeConfig, "filterField", modelSchema.getTreeConfig().getFilterField());
            putIfNotBlank(treeConfig, "targetField", modelSchema.getTreeConfig().getTargetField());
            putIfNotBlank(treeConfig, "childrenField", modelSchema.getTreeConfig().getChildrenField());
            putIfNotBlank(treeConfig, "treeTitle", modelSchema.getTreeConfig().getTreeTitle());
            putIfNotBlank(treeConfig, "loadMode", modelSchema.getTreeConfig().getLoadMode());
            if (modelSchema.getTreeConfig().getEnabled() != null) {
                treeConfig.put("enabled", modelSchema.getTreeConfig().getEnabled());
            }
        }
        if (overrideMap != null) {
            putIfNotBlank(treeConfig, "sourceModelCode", text(overrideMap.get("sourceModelCode")));
            putIfNotBlank(treeConfig, "sourceModelName", text(overrideMap.get("sourceModelName")));
            putIfNotBlank(treeConfig, "sourceTableName", text(overrideMap.get("sourceTableName")));
            putIfNotBlank(treeConfig, "sourceConfigKey", text(overrideMap.get("sourceConfigKey")));
            putIfNotBlank(treeConfig, "keyField", firstMapText(overrideMap, "keyField", "nodeKeyField"));
            putIfNotBlank(treeConfig, "parentField", firstMapText(overrideMap, "parentField", "parentIdField"));
            putIfNotBlank(treeConfig, "labelField", firstMapText(overrideMap, "labelField", "displayField", "nameField"));
            putIfNotBlank(treeConfig, "filterField", firstMapText(overrideMap, "filterField", "rightFilterField", "listFilterField"));
            putIfNotBlank(treeConfig, "targetField", firstMapText(overrideMap, "targetField", "nodeValueField", "valueField"));
            putIfNotBlank(treeConfig, "childrenField", text(overrideMap.get("childrenField")));
            putIfNotBlank(treeConfig, "treeTitle", firstMapText(overrideMap, "treeTitle", "title"));
            putIfNotBlank(treeConfig, "loadMode", text(overrideMap.get("loadMode")));
            if (StringUtils.isBlank(text(treeConfig.get("loadMode")))
                    && overrideMap.get("lazy") instanceof Boolean lazy
                    && lazy) {
                treeConfig.put("loadMode", "lazy");
            }
            Boolean overrideEnabled = readBooleanFlag(overrideMap.get("enabled"));
            if (overrideEnabled != null) {
                treeConfig.put("enabled", overrideEnabled);
            }
        }
        LowcodePageModelRef sourceRef = resolveTreeSourceRef(pageSchema, text(treeConfig.get("sourceModelCode")));
        if (sourceRef != null) {
            putIfNotBlank(treeConfig, "sourceModelCode", sourceRef.getModelCode());
            putIfNotBlank(treeConfig, "sourceModelName", sourceRef.getModelName());
            putIfNotBlank(treeConfig, "sourceTableName", sourceRef.getTableName());
            normalizeTreeSourceField(treeConfig, "keyField", sourceRef);
            normalizeTreeSourceField(treeConfig, "parentField", sourceRef);
            normalizeTreeSourceField(treeConfig, "labelField", sourceRef);
            normalizeTreeSourceField(treeConfig, "targetField", sourceRef);
        }
        treeConfig.putIfAbsent("keyField", "id");
        treeConfig.putIfAbsent("parentField", inferTreeParentField(modelSchema, sourceRef));
        treeConfig.putIfAbsent("labelField", inferTreeLabelField(modelSchema, sourceRef));
        LowcodeRelationSchema relation = sourceRef == null || Boolean.TRUE.equals(sourceRef.getPrimary())
                ? null
                : resolveRuntimeRelation(resolveTreePrimaryModelCode(modelSchema, pageSchema),
                sourceRef,
                resolvePrimaryTreeRelations(modelSchema, pageSchema));
        treeConfig.putIfAbsent("filterField", relation == null
                ? text(treeConfig.get("parentField"))
                : relation.getSourceField());
        treeConfig.putIfAbsent("targetField", relation == null
                ? text(treeConfig.get("keyField"))
                : relation.getTargetField());
        normalizePrimaryTreeField(treeConfig, "filterField", modelSchema);
        treeConfig.putIfAbsent("childrenField", "children");
        treeConfig.putIfAbsent("loadMode", "full");
        String defaultTreeTitle = StringUtils.defaultIfBlank(
                text(treeConfig.get("sourceModelName")),
                modelSchema == null ? null : modelSchema.getBusinessName());
        treeConfig.putIfAbsent("treeTitle", StringUtils.isBlank(defaultTreeTitle) ? "树形导航" : defaultTreeTitle + "树");
        // 左树右表 / 嵌入式树表都必须显式 enabled=true，否则前端会降级成普通平铺列表
        if (isLeftTreeRightTableLayout(pageSchema)) {
            treeConfig.put("enabled", Boolean.TRUE);
            // 默认点上级查询本级+全部下级
            treeConfig.putIfAbsent("includeChildren", Boolean.TRUE);
        } else if (isModelTreeEnabled(modelSchema) || Boolean.TRUE.equals(treeConfig.get("enabled"))) {
            treeConfig.put("enabled", Boolean.TRUE);
        }
        return treeConfig;
    }

    /**
     * 区域 props 里可能是 LinkedHashMap，也可能仍是设计态写入的 {@link LowcodeTreeConfig}。
     */
    private static Map<?, ?> asTreeOverrideMap(Object overrides) {
        if (overrides instanceof Map<?, ?> map) {
            return map;
        }
        if (overrides instanceof LowcodeTreeConfig config) {
            Map<String, Object> map = new LinkedHashMap<>();
            putIfNotBlank(map, "sourceModelCode", config.getSourceModelCode());
            putIfNotBlank(map, "sourceModelName", config.getSourceModelName());
            putIfNotBlank(map, "sourceTableName", config.getSourceTableName());
            putIfNotBlank(map, "sourceConfigKey", config.getSourceConfigKey());
            putIfNotBlank(map, "keyField", config.getKeyField());
            putIfNotBlank(map, "parentField", config.getParentField());
            putIfNotBlank(map, "labelField", config.getLabelField());
            putIfNotBlank(map, "filterField", config.getFilterField());
            putIfNotBlank(map, "targetField", config.getTargetField());
            putIfNotBlank(map, "childrenField", config.getChildrenField());
            putIfNotBlank(map, "treeTitle", config.getTreeTitle());
            putIfNotBlank(map, "loadMode", config.getLoadMode());
            if (config.getEnabled() != null) {
                map.put("enabled", config.getEnabled());
            }
            return map;
        }
        return null;
    }

    private static boolean isModelTreeEnabled(LowcodeModelSchema modelSchema) {
        if (modelSchema == null) {
            return false;
        }
        String appType = StringUtils.defaultIfBlank(modelSchema.getAppType(), "SINGLE").toUpperCase(Locale.ROOT);
        if ("TREE".equals(appType)) {
            return true;
        }
        return modelSchema.getTreeConfig() != null && Boolean.TRUE.equals(modelSchema.getTreeConfig().getEnabled());
    }

    private static Boolean readBooleanFlag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
                return Boolean.TRUE;
            }
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static String firstMapText(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            String value = text(values.get(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String inferTreeParentField(LowcodeModelSchema modelSchema, LowcodePageModelRef sourceRef) {
        return treeSourceFields(modelSchema, sourceRef).stream()
                .filter(field -> "parentId".equals(field) || "pid".equals(field) || "parentCode".equals(field))
                .findFirst()
                .orElse("parentId");
    }

    private static String inferTreeLabelField(LowcodeModelSchema modelSchema, LowcodePageModelRef sourceRef) {
        List<String> fields = treeSourceFields(modelSchema, sourceRef);
        return fields.stream()
                .filter(field -> "name".equals(field) || "title".equals(field) || "label".equals(field))
                .findFirst()
                .orElseGet(() -> fields.isEmpty() ? "name" : fields.get(0));
    }

    private static LowcodePageModelRef resolveTreeSourceRef(LowcodePageSchema pageSchema, String sourceModelCode) {
        if (pageSchema == null || pageSchema.getModelRefs() == null || pageSchema.getModelRefs().isEmpty()) {
            return null;
        }
        if (StringUtils.isNotBlank(sourceModelCode)) {
            for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
                if (ref != null && sourceModelCode.equals(ref.getModelCode())) {
                    return ref;
                }
            }
        }
        return pageSchema.getModelRefs().stream()
                .filter(ref -> ref != null && !Boolean.TRUE.equals(ref.getPrimary()))
                .findFirst()
                .orElseGet(() -> pageSchema.getModelRefs().stream()
                        .filter(ref -> ref != null && Boolean.TRUE.equals(ref.getPrimary()))
                        .findFirst()
                        .orElse(pageSchema.getModelRefs().get(0)));
    }

    private static List<String> treeSourceFields(LowcodeModelSchema modelSchema, LowcodePageModelRef sourceRef) {
        if (sourceRef != null && sourceRef.getFields() != null && !sourceRef.getFields().isEmpty()) {
            return sourceRef.getFields().stream()
                    .map(field -> StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field"))))
                    .filter(StringUtils::isNotBlank)
                    .toList();
        }
        if (modelSchema == null || modelSchema.getFields() == null) {
            return List.of();
        }
        return modelSchema.getFields().stream()
                .map(LowcodeFieldSchema::getField)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    private static void normalizeTreeSourceField(Map<String, Object> treeConfig, String key, LowcodePageModelRef sourceRef) {
        String value = text(treeConfig.get(key));
        if (StringUtils.isBlank(value) || sourceRef == null || sourceRef.getFields() == null) {
            return;
        }
        String normalized = resolveRefSourceField(sourceRef, value);
        if (StringUtils.isNotBlank(normalized)) {
            treeConfig.put(key, normalized);
        }
    }

    private static void normalizePrimaryTreeField(Map<String, Object> treeConfig, String key, LowcodeModelSchema modelSchema) {
        String value = text(treeConfig.get(key));
        if (StringUtils.isBlank(value) || modelSchema == null || modelSchema.getFields() == null) {
            return;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (value.equals(field.getField()) || value.equals(field.getColumnName())) {
                treeConfig.put(key, field.getField());
                return;
            }
        }
    }


    private static String resolveTreePrimaryModelCode(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        String code = pageSchema == null ? null : pageSchema.getPrimaryModelCode();
        if (StringUtils.isNotBlank(code)) {
            return code;
        }
        LowcodePageModelRef primaryRef = resolvePrimaryRef(modelSchema, pageSchema);
        if (primaryRef != null && StringUtils.isNotBlank(primaryRef.getModelCode())) {
            return primaryRef.getModelCode();
        }
        if (modelSchema == null || modelSchema.getObject() == null) {
            return null;
        }
        return modelSchema.getObject().getCode();
    }

    private static List<LowcodeRelationSchema> resolvePrimaryTreeRelations(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        LowcodePageModelRef primaryRef = resolvePrimaryRef(modelSchema, pageSchema);
        if (primaryRef != null && primaryRef.getRelations() != null && !primaryRef.getRelations().isEmpty()) {
            return primaryRef.getRelations();
        }
        return modelSchema == null || modelSchema.getRelations() == null ? List.of() : modelSchema.getRelations();
    }

    static boolean isLeftTreeRightTableLayout(LowcodePageSchema pageSchema) {
        if (pageSchema == null) {
            return false;
        }
        if ("tree-crud".equals(StringUtils.defaultIfBlank(pageSchema.getLayoutType(), ""))) {
            return true;
        }
        return hasTreePanelBlock(pageSchema);
    }

    static boolean hasTreePanelBlock(LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null) {
            return false;
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> itemList)) {
            return false;
        }
        for (Object item : itemList) {
            if (item instanceof Map<?, ?> block && "tree-panel".equals(String.valueOf(block.get("blockType")))) {
                return true;
            }
        }
        return false;
    }

    static Map<String, Object> buildTreeOptionSource(String configKey, Map<String, Object> treeConfig) {
        return buildTreeOptionSource(configKey, treeConfig, Map.of());
    }

    static Map<String, Object> buildTreeOptionSource(String configKey,
                                                      Map<String, Object> treeConfig,
                                                      Map<String, Object> sortParams) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "tree");
        source.put("api", "get@/ai/crud/" + configKey + "/tree");
        source.put("keyField", "key");
        source.put("valueField", "targetValue");
        source.put("labelField", "label");
        source.put("childrenField", StringUtils.defaultIfBlank(text(treeConfig.get("childrenField")), "children"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("loadMode", "full");
        if (sortParams != null) {
            putIfNotBlank(params, "orderByColumn", text(sortParams.get("orderByColumn")));
            putIfNotBlank(params, "isAsc", text(sortParams.get("isAsc")));
        }
        source.put("params", params);
        return source;
    }

    static String resolveTreeApiConfigKey(String fallbackConfigKey, LowcodePageSchema pageSchema) {
        Object overrides = extractTreeConfigOverrides(pageSchema);
        if (overrides instanceof Map<?, ?> map) {
            String sourceConfigKey = firstMapText(map, "sourceConfigKey");
            if (StringUtils.isNotBlank(sourceConfigKey)) {
                return sourceConfigKey;
            }
        }
        return fallbackConfigKey;
    }

    static Object extractTreeConfigOverrides(LowcodePageSchema pageSchema) {
        Object gridTree = extractGridTreeConfigOverrides(pageSchema);
        LowcodePageZone tableZone = findZone(pageSchema, "table");
        Object zoneTree = tableZone != null && tableZone.getProps() != null
                ? tableZone.getProps().get("treeConfig")
                : null;
        // 优先用 tree-panel（含外部 sourceConfigKey）；zone 上可能残留空来源的旧 treeConfig
        if (gridTree instanceof Map<?, ?> gridMap) {
            String sourceConfigKey = firstMapText(gridMap, "sourceConfigKey");
            String sourceModelCode = firstMapText(gridMap, "sourceModelCode");
            if (StringUtils.isNotBlank(sourceConfigKey) || StringUtils.isNotBlank(sourceModelCode) || zoneTree == null) {
                return gridTree;
            }
        }
        if (zoneTree != null) {
            return zoneTree;
        }
        return gridTree;
    }

    private static Object extractGridTreeConfigOverrides(LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null) {
            return null;
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> itemList)) {
            return null;
        }
        for (Object item : itemList) {
            if (!(item instanceof Map<?, ?> block)) {
                continue;
            }
            if (!"tree-panel".equals(String.valueOf(block.get("blockType")))) {
                continue;
            }
            Object props = block.get("props");
            if (props instanceof Map<?, ?>) {
                return props;
            }
        }
        return null;
    }


    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private static LowcodePageZone findZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema == null || pageSchema.getZones() == null) {
            return null;
        }
        return pageSchema.getZones().stream()
                .filter(zone -> zoneKey.equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
    }
}
