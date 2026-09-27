package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 历史运行时页面协议适配器。
 *
 * <p>把旧 search/edit/columns Schema 翻译为统一 Page Zone 协议，并负责区域别名归一，
 * 避免兼容逻辑继续渗入设计器聚合服务。</p>
 */
final class BusinessObjectLegacyPageSchemaAdapter {

    private static final Map<String, String> PAGE_ZONE_ALIASES = Map.ofEntries(
            Map.entry("search", "search"),
            Map.entry("search-form", "search"),
            Map.entry("query", "search"),
            Map.entry("filter", "search"),
            Map.entry("table", "table"),
            Map.entry("data-table", "table"),
            Map.entry("list", "table"),
            Map.entry("grid", "table"),
            Map.entry("edit", "edit"),
            Map.entry("edit-form", "edit"),
            Map.entry("form", "edit"),
            Map.entry("create", "edit"),
            Map.entry("update", "edit"),
            Map.entry("detail", "detail"),
            Map.entry("detail-view", "detail"),
            Map.entry("view", "detail"),
            Map.entry("toolbar", "toolbar"),
            Map.entry("table-toolbar", "toolbar"),
            Map.entry("actions", "toolbar")
    );
    private static final Map<String, String> PAGE_ZONE_COMPONENTS = Map.of(
            "search", "search-form",
            "table", "data-table",
            "edit", "edit-form",
            "detail", "detail-view",
            "toolbar", "table-toolbar"
    );
    private static final Set<String> GENERIC_COMPONENT_TYPES = Set.of(
            "", "input", "textarea", "number", "inputNumber", "input-number", "inputnumber", "integer"
    );

    private final ObjectMapper objectMapper;
    private final BusinessFieldSchemaService fieldSchemaService;

    BusinessObjectLegacyPageSchemaAdapter(ObjectMapper objectMapper,
                                          BusinessFieldSchemaService fieldSchemaService) {
        this.objectMapper = objectMapper;
        this.fieldSchemaService = fieldSchemaService;
    }

    LowcodePageSchema ensurePageSchema(LowcodePageSchema pageSchema, LowcodeModelSchema modelSchema) {
        LowcodePageSchema target = pageSchema == null ? fieldSchemaService.buildDefaultPageSchema(modelSchema) : pageSchema;
        if (StringUtils.isBlank(target.getLayoutType())) {
            target.setLayoutType("simple-crud");
        }
        // 画布已有 tree-panel 时强制左树右表，避免运行态仍按 simple-crud 渲染成普通列表
        if (hasTreePanelBlock(target) && !"tree-crud".equals(target.getLayoutType())) {
            target.setLayoutType("tree-crud");
        }
        if (target.getZones() == null) {
            target.setZones(new ArrayList<>());
        }
        Map<String, LowcodePageZone> normalizedZones = new LinkedHashMap<>();
        target.getZones().forEach(zone -> normalizePageZone(zone, normalizedZones));
        target.setZones(new ArrayList<>(normalizedZones.values()));
        Set<String> zoneKeys = new LinkedHashSet<>(normalizedZones.keySet());
        LowcodePageSchema defaults = fieldSchemaService.buildDefaultPageSchema(modelSchema);
        defaults.getZones().stream()
                .filter(zone -> !zoneKeys.contains(zone.getZoneKey()))
                .forEach(target.getZones()::add);
        return target;
    }

    private boolean hasTreePanelBlock(LowcodePageSchema pageSchema) {
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

    void applyLegacyRuntimeSchemas(AiCrudConfig config, LowcodePageSchema pageSchema, LowcodeModelSchema modelSchema) {
        if (config == null || pageSchema == null || modelSchema == null) {
            return;
        }
        Set<String> modelFields = lowcodeFieldMap(modelSchema).keySet();
        applyLegacyRuntimeSchemaToZone(config.getSearchSchema(), pageSchema, "search", "search-form", modelFields, "field");
        applyLegacyRuntimeSchemaToZone(config.getEditSchema(), pageSchema, "edit", "edit-form", modelFields, "field");
        applyLegacyRuntimeSchemaToZone(config.getColumnsSchema(), pageSchema, "table", "data-table", modelFields, "prop");
    }

    private void applyLegacyRuntimeSchemaToZone(String schemaJson, LowcodePageSchema pageSchema,
                                                String zoneKey, String componentKey,
                                                Set<String> modelFields, String primaryFieldKey) {
        List<Map<String, Object>> legacyItems = readLegacySchemaList(schemaJson);
        if (legacyItems.isEmpty() || modelFields == null || modelFields.isEmpty()) {
            return;
        }
        LowcodePageZone zone = findOrCreateZone(pageSchema, zoneKey, componentKey);
        List<String> fieldRefs = zone.getFieldRefs() == null ? new ArrayList<>() : new ArrayList<>(zone.getFieldRefs());
        LinkedHashSet<String> mergedRefs = new LinkedHashSet<>(fieldRefs);
        Map<String, Object> props = zone.getProps() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(zone.getProps());
        Map<String, Object> settings = new LinkedHashMap<>(mapValue(props.get("fieldSettings")));

        for (Map<String, Object> item : legacyItems) {
            String fieldCode = resolveLegacyFieldCode(item, primaryFieldKey);
            if (!modelFields.contains(fieldCode)) {
                continue;
            }
            mergedRefs.add(fieldCode);
            Map<String, Object> existing = new LinkedHashMap<>(mapValue(settings.get(fieldCode)));
            mergeLegacyRuntimeFieldSetting(existing, buildLegacyRuntimeFieldSetting(item, zoneKey));
            settings.put(fieldCode, existing);
        }
        zone.setFieldRefs(new ArrayList<>(mergedRefs));
        props.put("fieldSettings", settings);
        zone.setProps(props);
    }

    private List<Map<String, Object>> readLegacySchemaList(String schemaJson) {
        if (StringUtils.isBlank(schemaJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveLegacyFieldCode(Map<String, Object> item, String primaryFieldKey) {
        return StringUtils.firstNonBlank(
                text(item.get(primaryFieldKey)),
                text(item.get("field")),
                text(item.get("prop")),
                text(item.get("dataIndex")),
                text(item.get("key"))
        );
    }

    private Map<String, Object> buildLegacyRuntimeFieldSetting(Map<String, Object> item, String zoneKey) {
        Map<String, Object> setting = new LinkedHashMap<>();
        String componentType = normalizeRuntimeComponentType(StringUtils.firstNonBlank(
                text(item.get("componentType")), text(item.get("type"))));
        putIfNotBlank(setting, "componentType", componentType);
        putIfNotBlank(setting, "type", componentType);
        putIfNotBlank(setting, "label", text(item.get("label")));
        putIfNotBlank(setting, "queryType", text(item.get("queryType")));
        putIfNotBlank(setting, "align", normalizeAlign(StringUtils.firstNonBlank(text(item.get("align")), text(item.get("textAlign")))));
        putIfNotBlank(setting, "fixed", normalizeFixed(text(item.get("fixed"))));
        putIfPresent(setting, "width", item.get("width"));
        putIfPresent(setting, "minWidth", item.get("minWidth"));
        putIfPresent(setting, "span", item.get("span"));
        putIfPresent(setting, "required", item.get("required"));
        putIfPresent(setting, "readonly", item.get("readonly"));
        putIfPresent(setting, "disabled", item.get("disabled"));
        putIfPresent(setting, "defaultValue", item.get("defaultValue"));
        putIfPresent(setting, "rules", item.get("rules"));
        putIfNotBlank(setting, "requiredMessage", text(item.get("requiredMessage")));
        if (item.containsKey("sortable")) {
            setting.put("sortable", readBoolean(item.get("sortable"), false));
        }

        Map<String, Object> props = new LinkedHashMap<>(mapValue(item.get("props")));
        String dictType = StringUtils.firstNonBlank(text(item.get("dictType")), text(props.get("dictType")));
        putIfNotBlank(setting, "dictType", dictType);
        if (StringUtils.isNotBlank(dictType)) {
            props.putIfAbsent("dictType", dictType);
        }
        if (item.containsKey("generation")) {
            props.putIfAbsent("generation", item.get("generation"));
        }
        if (!props.isEmpty()) {
            setting.put("props", props);
        }

        if ("table".equals(zoneKey)) {
            Object render = item.get("render");
            Map<String, Object> renderMap = mapValue(render);
            String renderType = StringUtils.firstNonBlank(
                    text(item.get("renderType")),
                    text(renderMap.get("type")),
                    StringUtils.isNotBlank(dictType) ? "dictTag" : null
            );
            putIfNotBlank(setting, "renderType", renderType);
            if (StringUtils.isBlank(text(setting.get("dictType")))) {
                putIfNotBlank(setting, "dictType", text(renderMap.get("dictType")));
            }
        }
        return setting;
    }

    @SuppressWarnings("unchecked")
    private void mergeLegacyRuntimeFieldSetting(Map<String, Object> target, Map<String, Object> legacy) {
        if (target == null || legacy == null || legacy.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : legacy.entrySet()) {
            String key = entry.getKey();
            Object legacyValue = entry.getValue();
            Object currentValue = target.get(key);
            if ("props".equals(key) && legacyValue instanceof Map<?, ?> legacyProps) {
                Map<String, Object> merged = new LinkedHashMap<>(mapValue(legacyProps));
                merged.putAll(mapValue(currentValue));
                target.put("props", merged);
                continue;
            }
            if (("componentType".equals(key) || "type".equals(key)) && StringUtils.isNotBlank(text(legacyValue))) {
                String legacyComponent = text(legacyValue);
                if (isBlankValue(currentValue)
                        || (isGenericComponent(text(currentValue)) && !isGenericComponent(legacyComponent))) {
                    target.put(key, legacyComponent);
                }
                continue;
            }
            if (isBlankValue(currentValue)) {
                target.put(key, legacyValue);
            }
        }
    }

    private void normalizePageZone(LowcodePageZone zone, Map<String, LowcodePageZone> normalizedZones) {
        if (zone == null) {
            return;
        }
        String zoneKey = normalizePageZoneKey(zone.getZoneKey());
        if (StringUtils.isBlank(zoneKey)) {
            return;
        }
        zone.setZoneKey(zoneKey);
        if (StringUtils.isBlank(zone.getComponentKey())) {
            zone.setComponentKey(PAGE_ZONE_COMPONENTS.get(zoneKey));
        }
        if (zone.getFieldRefs() == null) {
            zone.setFieldRefs(new ArrayList<>());
        }
        if (zone.getProps() == null) {
            zone.setProps(new LinkedHashMap<>());
        }
        LowcodePageZone existing = normalizedZones.get(zoneKey);
        if (existing == null) {
            normalizedZones.put(zoneKey, zone);
            return;
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>(existing.getFieldRefs());
        refs.addAll(zone.getFieldRefs());
        existing.setFieldRefs(new ArrayList<>(refs));
        zone.getProps().forEach(existing.getProps()::putIfAbsent);
        if (existing.getEnabled() == null) {
            existing.setEnabled(zone.getEnabled());
        }
    }

    private String normalizePageZoneKey(String zoneKey) {
        String normalized = StringUtils.trimToEmpty(zoneKey).toLowerCase(Locale.ROOT);
        return PAGE_ZONE_ALIASES.get(normalized);
    }


    private LowcodePageZone findOrCreateZone(LowcodePageSchema pageSchema, String zoneKey, String componentKey) {
        if (pageSchema.getZones() == null) {
            pageSchema.setZones(new ArrayList<>());
        }
        LowcodePageZone zone = pageSchema.getZones().stream()
                .filter(item -> item != null && zoneKey.equals(item.getZoneKey()))
                .findFirst()
                .orElse(null);
        if (zone != null) {
            if (StringUtils.isBlank(zone.getComponentKey())) {
                zone.setComponentKey(componentKey);
            }
            if (zone.getFieldRefs() == null) {
                zone.setFieldRefs(new ArrayList<>());
            }
            if (zone.getProps() == null) {
                zone.setProps(new LinkedHashMap<>());
            }
            return zone;
        }
        zone = new LowcodePageZone();
        zone.setZoneKey(zoneKey);
        zone.setComponentKey(componentKey);
        zone.setEnabled(true);
        zone.setFieldRefs(new ArrayList<>());
        zone.setProps(new LinkedHashMap<>());
        pageSchema.getZones().add(zone);
        return zone;
    }

    private Map<String, LowcodeFieldSchema> lowcodeFieldMap(LowcodeModelSchema modelSchema) {
        Map<String, LowcodeFieldSchema> fields = new LinkedHashMap<>();
        if (modelSchema != null && modelSchema.getFields() != null) {
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field != null && StringUtils.isNotBlank(field.getField())) {
                    fields.put(field.getField(), field);
                }
            }
        }
        return fields;
    }

    private String normalizeRuntimeComponentType(String componentKey) {
        return switch (StringUtils.defaultString(componentKey)) {
            case "inputNumber", "input-number", "inputnumber", "integer", "money" -> "number";
            case "upload" -> "fileUpload";
            case "orgSelect", "departmentSelect", "departmentTreeSelect", "deptSelect", "deptTreeSelect",
                    "elTreeSelect", "orgName", "deptName" -> "orgTreeSelect";
            case "userPicker", "userName" -> "userSelect";
            default -> componentKey;
        };
    }

    private String normalizeAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "center", "right").contains(align) ? align : "left";
    }

    private String normalizeFixed(String value) {
        String fixed = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "right").contains(fixed) ? fixed : null;
    }

    private boolean isGenericComponent(String componentType) {
        return GENERIC_COMPONENT_TYPES.contains(StringUtils.defaultString(componentType));
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return StringUtils.isBlank(text);
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = StringUtils.trimToEmpty(String.valueOf(value));
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

