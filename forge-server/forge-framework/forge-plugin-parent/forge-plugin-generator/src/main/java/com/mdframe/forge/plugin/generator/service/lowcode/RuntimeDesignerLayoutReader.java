package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 只读取设计器保存的布局块、表单规则和画布元数据，不改写原始 Schema。 */
final class RuntimeDesignerLayoutReader {

    private RuntimeDesignerLayoutReader() {
    }

    static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    static String normalizeAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "center", "right").contains(align) ? align : null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveGridFieldSetting(LowcodePageSchema pageSchema, String zoneKey, String fieldName) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null || StringUtils.isBlank(fieldName)) {
            return Map.of();
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> list)) {
            return Map.of();
        }
        for (String blockType : runtimeSettingBlockTypes(zoneKey)) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> block)) {
                    continue;
                }
                if (!blockType.equals(text(block.get("blockType")))) {
                    continue;
                }
                Object propsValue = block.get("props");
                if (!(propsValue instanceof Map<?, ?> props)) {
                    continue;
                }
                String globalAlign = "table".equals(zoneKey) ? normalizeAlign(text(props.get("globalAlign"))) : null;
                // AiCrudPage 查询区配置写在 searchFieldSettings，不能误读表格 fieldSettings
                Object settingsValue = props.get("fieldSettings");
                if ("search".equals(zoneKey) && "AiCrudPage".equals(blockType) && props.containsKey("searchFieldSettings")) {
                    settingsValue = props.get("searchFieldSettings");
                }
                if (!(settingsValue instanceof Map<?, ?> settings)) {
                    if (StringUtils.isNotBlank(globalAlign)) {
                        return Map.of("align", globalAlign);
                    }
                    continue;
                }
                Object value = settings.get(fieldName);
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) map);
                    if (StringUtils.isNotBlank(globalAlign)
                            && StringUtils.isBlank(normalizeAlign(StringUtils.defaultIfBlank(
                            text(result.get("align")), text(result.get("textAlign")))))) {
                        result.put("align", globalAlign);
                    }
                    return result;
                }
                if (StringUtils.isNotBlank(globalAlign)) {
                    return Map.of("align", globalAlign);
                }
            }
        }
        return Map.of();
    }

    static List<String> runtimeSettingBlockTypes(String zoneKey) {
        if ("search".equals(zoneKey)) {
            return List.of("search-form", "AiCrudPage");
        }
        if ("table".equals(zoneKey)) {
            return List.of("data-table", "AiCrudPage", "AiTable");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveGridBlockProps(LowcodePageSchema pageSchema, List<String> blockTypes) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null || blockTypes == null || blockTypes.isEmpty()) {
            return Map.of();
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> list)) {
            return Map.of();
        }
        for (String blockType : blockTypes) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> block)) {
                    continue;
                }
                if (!blockType.equals(text(block.get("blockType")))) {
                    continue;
                }
                Object props = block.get("props");
                if (props instanceof Map<?, ?> map) {
                    return new LinkedHashMap<>((Map<String, Object>) map);
                }
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> extractFormRules(LowcodePageSchema pageSchema) {
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone == null || editZone.getProps() == null) {
            return List.of();
        }
        Object rules = editZone.getProps().get("formCreateRule");
        if (!(rules instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        collectFormRules(list, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> extractCanvasItems(LowcodePageZone zone) {
        if (zone == null || zone.getProps() == null) {
            return List.of();
        }
        Object canvas = zone.getProps().get("canvas");
        if (!(canvas instanceof Map<?, ?> canvasMap)) {
            return List.of();
        }
        Object items = canvasMap.get("items");
        if (!(items instanceof List<?> itemList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : itemList) {
            if (item instanceof Map<?, ?> itemMap) {
                result.add((Map<String, Object>) itemMap);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collectFormRules(List<?> rules, List<Map<String, Object>> result) {
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> rule)) {
                continue;
            }
            Map<String, Object> typedRule = (Map<String, Object>) rule;
            result.add(typedRule);
            Object children = typedRule.get("children");
            if (children instanceof List<?> childRules) {
                collectFormRules(childRules, result);
            }
        }
    }

    static LowcodePageZone findZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema == null || pageSchema.getZones() == null) {
            return null;
        }
        return pageSchema.getZones().stream()
                .filter(zone -> zoneKey.equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
    }

}
