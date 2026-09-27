package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignerDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 树形快捷页面的协议投影器。
 *
 * <p>统一把树形页面配置投影到设计态模型、页面区域和应用 Builder，避免三份协议分别演进。</p>
 */
final class BusinessApplicationTreePageProjector {

    void applyDesignerPreset(
            BusinessObjectDesignerDTO designer,
            LowcodeModelSchema currentModel,
            LowcodePageSchema currentPageSchema,
            String pageType,
            String objectName,
            Map<String, Object> builder,
            String pageId) {
        if (!isTreePage(pageType)) {
            return;
        }
        boolean treeTable = "tree-table".equals(pageType);
        LowcodeModelSchema model = currentModel == null ? new LowcodeModelSchema() : currentModel;
        LowcodeTreeConfig treeConfig = model.getTreeConfig() == null
                ? new LowcodeTreeConfig()
                : model.getTreeConfig();
        mergeTreeConfigDefaults(treeConfig, extractBuilderTreeConfig(builder, pageId), objectName);
        treeConfig.setEnabled(true);
        model.setAppType("TREE");
        model.setTreeConfig(treeConfig);
        designer.setModelSchema(model);

        LowcodePageSchema pageSchema = currentPageSchema == null
                ? new LowcodePageSchema()
                : currentPageSchema;
        if (treeTable) {
            pageSchema.setLayoutType("tree-crud");
        } else {
            // tree-list：纠正 appType 误写入的 layoutType（SINGLE/TREE 等）
            String currentLayout = StringUtils.defaultIfBlank(pageSchema.getLayoutType(), "");
            if (!"list-form".equals(currentLayout)
                    && !"simple-crud".equals(currentLayout)
                    && !"tree-crud".equals(currentLayout)) {
                pageSchema.setLayoutType("list-form");
            } else if (StringUtils.isBlank(currentLayout) || "simple-crud".equals(currentLayout)) {
                pageSchema.setLayoutType("list-form");
            }
        }
        LowcodePageZone tableZone = ensureTableZone(pageSchema);
        Map<String, Object> props = tableZone.getProps() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(tableZone.getProps());
        // zone props 统一落 Map，避免运行配置抽取时 instanceof Map 失败丢 enabled
        props.put("treeConfig", toTreeConfigMap(treeConfig));
        props.put("enableTreeAddChild", !treeTable);
        if (treeTable) {
            props.put("layoutType", "tree-crud");
        }
        tableZone.setProps(props);
        designer.setPageSchema(pageSchema);
    }

    void patchBuilder(Map<String, Object> builder, String pageId, String pageType) {
        if (!isTreePage(pageType)) {
            return;
        }
        boolean treeTable = "tree-table".equals(pageType);
        Map<String, Object> pages = mutableMap(builder.get("pages"));
        Map<String, Object> page = pages == null ? null : mutableMap(pages.get(pageId));
        Map<String, Object> layout = page == null ? null : mutableMap(page.get("layout"));
        Map<String, Object> grid = layout == null ? null : mutableMap(layout.get("gridLayout"));
        Object itemsValue = grid == null ? null : grid.get("items");
        if (!(itemsValue instanceof List<?> items)) {
            return;
        }
        for (Object item : items) {
            Map<String, Object> block = mutableMap(item);
            if (block == null || !"AiCrudPage".equals(String.valueOf(block.get("blockType")))) {
                continue;
            }
            Map<String, Object> props = mutableMap(block.get("props"));
            if (props == null) {
                props = new LinkedHashMap<>();
                block.put("props", props);
            }
            Map<String, Object> treeConfig = mutableMap(props.get("treeConfig"));
            if (treeConfig == null) {
                treeConfig = new LinkedHashMap<>();
            }
            treeConfig.put("enabled", Boolean.TRUE);
            treeConfig.putIfAbsent("keyField", "id");
            treeConfig.putIfAbsent("parentField", "parentId");
            treeConfig.putIfAbsent("labelField", "name");
            treeConfig.putIfAbsent("filterField", "parentId");
            treeConfig.putIfAbsent("targetField", "id");
            treeConfig.putIfAbsent("childrenField", "children");
            treeConfig.putIfAbsent("loadMode", "full");
            props.put("treeConfig", treeConfig);
            props.put("enableTreeAddChild", !treeTable);
            Map<String, Object> options = mutableMap(props.get("options"));
            if (options == null) {
                options = new LinkedHashMap<>();
            }
            options.put("treeConfig", new LinkedHashMap<>(treeConfig));
            options.put("enableTreeAddChild", !treeTable);
            if (treeTable) {
                options.put("layoutType", "tree-crud");
                props.put("layoutType", "tree-crud");
            }
            props.put("options", options);
        }
    }

    private boolean isTreePage(String pageType) {
        return "tree-list".equals(pageType) || "tree-table".equals(pageType);
    }

    private void mergeTreeConfigDefaults(
            LowcodeTreeConfig treeConfig, Map<String, Object> fromBuilder, String objectName) {
        if (fromBuilder != null) {
            if (StringUtils.isBlank(treeConfig.getKeyField())) {
                treeConfig.setKeyField(firstText(fromBuilder, "keyField", "id"));
            }
            if (StringUtils.isBlank(treeConfig.getParentField())) {
                treeConfig.setParentField(firstText(fromBuilder, "parentField", "parentId"));
            }
            if (StringUtils.isBlank(treeConfig.getLabelField())) {
                treeConfig.setLabelField(firstText(fromBuilder, "labelField", "name"));
            }
            if (StringUtils.isBlank(treeConfig.getFilterField())) {
                treeConfig.setFilterField(firstText(fromBuilder, "filterField",
                        StringUtils.defaultIfBlank(treeConfig.getParentField(), "parentId")));
            }
            if (StringUtils.isBlank(treeConfig.getTargetField())) {
                treeConfig.setTargetField(firstText(fromBuilder, "targetField",
                        StringUtils.defaultIfBlank(treeConfig.getKeyField(), "id")));
            }
            if (StringUtils.isBlank(treeConfig.getChildrenField())) {
                treeConfig.setChildrenField(firstText(fromBuilder, "childrenField", "children"));
            }
            if (StringUtils.isBlank(treeConfig.getTreeTitle())) {
                treeConfig.setTreeTitle(firstText(fromBuilder, "treeTitle", ""));
            }
            if (StringUtils.isBlank(treeConfig.getLoadMode())) {
                treeConfig.setLoadMode(firstText(fromBuilder, "loadMode", "full"));
            }
        }
        if (StringUtils.isBlank(treeConfig.getKeyField())) {
            treeConfig.setKeyField("id");
        }
        if (StringUtils.isBlank(treeConfig.getParentField())) {
            treeConfig.setParentField("parentId");
        }
        if (StringUtils.isBlank(treeConfig.getLabelField())) {
            treeConfig.setLabelField("name");
        }
        if (StringUtils.isBlank(treeConfig.getFilterField())) {
            treeConfig.setFilterField(treeConfig.getParentField());
        }
        if (StringUtils.isBlank(treeConfig.getTargetField())) {
            treeConfig.setTargetField(treeConfig.getKeyField());
        }
        if (StringUtils.isBlank(treeConfig.getChildrenField())) {
            treeConfig.setChildrenField("children");
        }
        if (StringUtils.isBlank(treeConfig.getLoadMode())) {
            treeConfig.setLoadMode("full");
        }
        if (StringUtils.isBlank(treeConfig.getTreeTitle())) {
            String title = StringUtils.trimToEmpty(objectName);
            treeConfig.setTreeTitle(title.isEmpty() ? "分类树" : title + "树");
        }
    }

    private Map<String, Object> toTreeConfigMap(LowcodeTreeConfig treeConfig) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (treeConfig == null) {
            map.put("enabled", Boolean.TRUE);
            return map;
        }
        map.put("enabled", Boolean.TRUE.equals(treeConfig.getEnabled()));
        putIfNotBlank(map, "sourceModelCode", treeConfig.getSourceModelCode());
        putIfNotBlank(map, "sourceModelName", treeConfig.getSourceModelName());
        putIfNotBlank(map, "sourceTableName", treeConfig.getSourceTableName());
        putIfNotBlank(map, "sourceConfigKey", treeConfig.getSourceConfigKey());
        putIfNotBlank(map, "keyField", treeConfig.getKeyField());
        putIfNotBlank(map, "parentField", treeConfig.getParentField());
        putIfNotBlank(map, "labelField", treeConfig.getLabelField());
        putIfNotBlank(map, "filterField", treeConfig.getFilterField());
        putIfNotBlank(map, "targetField", treeConfig.getTargetField());
        putIfNotBlank(map, "childrenField", treeConfig.getChildrenField());
        putIfNotBlank(map, "treeTitle", treeConfig.getTreeTitle());
        putIfNotBlank(map, "loadMode", treeConfig.getLoadMode());
        return map;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private LowcodePageZone ensureTableZone(LowcodePageSchema pageSchema) {
        if (pageSchema.getZones() == null) {
            pageSchema.setZones(new ArrayList<>());
        }
        for (LowcodePageZone zone : pageSchema.getZones()) {
            if (zone != null && "table".equals(zone.getZoneKey())) {
                return zone;
            }
        }
        LowcodePageZone created = new LowcodePageZone();
        created.setZoneKey("table");
        created.setEnabled(true);
        created.setProps(new LinkedHashMap<>());
        pageSchema.getZones().add(created);
        return created;
    }

    private Map<String, Object> extractBuilderTreeConfig(Map<String, Object> builder, String pageId) {
        if (builder == null || StringUtils.isBlank(pageId)) {
            return null;
        }
        Map<String, Object> pages = mutableMap(builder.get("pages"));
        Map<String, Object> page = pages == null ? null : mutableMap(pages.get(pageId));
        Map<String, Object> layout = page == null ? null : mutableMap(page.get("layout"));
        Map<String, Object> grid = layout == null ? null : mutableMap(layout.get("gridLayout"));
        Object itemsValue = grid == null ? null : grid.get("items");
        if (!(itemsValue instanceof List<?> items)) {
            return null;
        }
        for (Object item : items) {
            Map<String, Object> block = mutableMap(item);
            if (block == null || !"AiCrudPage".equals(String.valueOf(block.get("blockType")))) {
                continue;
            }
            Map<String, Object> props = mutableMap(block.get("props"));
            if (props == null) {
                return null;
            }
            Map<String, Object> treeConfig = mutableMap(props.get("treeConfig"));
            if (treeConfig == null) {
                Map<String, Object> options = mutableMap(props.get("options"));
                treeConfig = options == null ? null : mutableMap(options.get("treeConfig"));
            }
            return treeConfig;
        }
        return null;
    }

    private String firstText(Map<String, Object> source, String key, String fallback) {
        if (source == null) {
            return fallback;
        }
        String value = StringUtils.trimToNull(String.valueOf(source.getOrDefault(key, "")));
        return value == null || "null".equals(value) ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }
}
