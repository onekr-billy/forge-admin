package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeDesignerLayoutReader.findZone;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeDesignerLayoutReader.runtimeSettingBlockTypes;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeDesignerLayoutReader.text;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeFieldPresentationSupport.isSystemField;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRefFieldFactory.safeKey;

/** 运行时字段目录与设计器显式选列的唯一解析入口。 */
final class RuntimeFieldCatalogResolver {

    private RuntimeFieldCatalogResolver() {
    }

    static Set<String> buildChildFieldRefs(LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getModelRefs() == null) {
            return Set.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || ref.getFields() == null) {
                continue;
            }
            for (Map<String, Object> field : ref.getFields()) {
                String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
                String fieldRef = StringUtils.defaultIfBlank(text(field.get("fieldRef")),
                        safeKey(ref.getModelCode()) + "__" + sourceField);
                if (StringUtils.isNotBlank(fieldRef)) {
                    refs.add(fieldRef);
                }
            }
        }
        return refs;
    }

    static List<LowcodeFieldSchema> resolveFields(LowcodeModelSchema modelSchema,
                                                   LowcodePageSchema pageSchema,
                                                   String zoneKey,
                                                   Predicate<LowcodeFieldSchema> fallbackPredicate,
                                                   BiPredicate<LowcodePageSchema, String> isTableFieldHidden) {
        Map<String, LowcodeFieldSchema> fieldMap = buildRuntimeFieldMap(modelSchema, pageSchema);
        LowcodePageZone zone = findZone(pageSchema, zoneKey);
        List<String> gridFieldRefs = resolveListGridFieldRefs(pageSchema, zoneKey);
        List<String> selectedRefs = !gridFieldRefs.isEmpty()
                ? gridFieldRefs
                : zone == null || zone.getFieldRefs() == null ? List.of() : zone.getFieldRefs();
        if (selectedRefs.isEmpty()
                || (gridFieldRefs.isEmpty() && (zone == null || Boolean.FALSE.equals(zone.getEnabled())))) {
            return fieldMap.values().stream()
                    .filter(RuntimeFieldCatalogResolver::isActiveField)
                    .filter(fallbackPredicate)
                    .toList();
        }

        Set<String> refs = new LinkedHashSet<>(selectedRefs);
        Set<String> childFieldRefs = buildChildFieldRefs(pageSchema);
        List<LowcodeFieldSchema> selectedFields = refs.stream()
                .map(ref -> resolveRuntimeField(fieldMap, ref))
                .filter(field -> field != null)
                .filter(field -> isZoneFieldAllowed(field, zoneKey, fallbackPredicate)
                        || ("table".equals(zoneKey)
                            && isActiveField(field)
                            && childFieldRefs.contains(field.getField())))
                .collect(Collectors.toCollection(ArrayList::new));
        if (selectedFields.isEmpty()) {
            return fieldMap.values().stream()
                    .filter(RuntimeFieldCatalogResolver::isActiveField)
                    .filter(fallbackPredicate)
                    .toList();
        }
        if ("table".equals(zoneKey)) {
            appendManagedBusinessFlowStatusFields(selectedFields, fieldMap, pageSchema, isTableFieldHidden);
        }
        return selectedFields;
    }

    /**
     * 平台托管的 flowStatus 在列表自由布局旧快照里常被漏掉。
     * 发布运行配置时强制补列，除非用户在列表设计里显式隐藏。
     */
    private static void appendManagedBusinessFlowStatusFields(List<LowcodeFieldSchema> selectedFields,
                                                       Map<String, LowcodeFieldSchema> fieldMap,
                                                       LowcodePageSchema pageSchema,
                                                       BiPredicate<LowcodePageSchema, String> isTableFieldHidden) {
        if (selectedFields == null || fieldMap == null || fieldMap.isEmpty()) {
            return;
        }
        Set<String> present = selectedFields.stream()
                .map(LowcodeFieldSchema::getField)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (LowcodeFieldSchema field : fieldMap.values()) {
            if (!isManagedBusinessFlowStatusField(field) || !isActiveField(field)) {
                continue;
            }
            if (field.getListVisible() != null && !Boolean.TRUE.equals(field.getListVisible())) {
                continue;
            }
            if (isTableFieldHidden.test(pageSchema, field.getField())) {
                continue;
            }
            if (present.add(field.getField())) {
                selectedFields.add(field);
            }
        }
    }

    static boolean isManagedBusinessFlowStatusField(LowcodeFieldSchema field) {
        if (field == null) {
            return false;
        }
        if ("flowStatus".equalsIgnoreCase(field.getField())
                || "flow_status".equalsIgnoreCase(field.getColumnName())) {
            return true;
        }
        Map<String, Object> advancedProps = field.getAdvancedProps();
        if (advancedProps != null
                && "BUSINESS_FLOW".equalsIgnoreCase(String.valueOf(advancedProps.get("managedBy")))) {
            return true;
        }
        return "business_flow_status".equalsIgnoreCase(field.getDictType());
    }

    /**
     * 自由列表设计器的 gridLayout 是列表字段的直接事实来源。历史草稿可能因 viewSchema
     * 只识别主表字段而留下过期的 table zone，发布时必须优先读取网格区块的显式选列。
     */
    private static List<String> resolveListGridFieldRefs(LowcodePageSchema pageSchema, String zoneKey) {
        List<String> fromListGrid = extractGridFieldRefs(pageSchema == null ? null : pageSchema.getListGridLayout(), zoneKey);
        if (!fromListGrid.isEmpty()) {
            return fromListGrid;
        }
        // 兼容仅写在 pages[list].gridLayout 的草稿
        if (pageSchema == null || pageSchema.getPages() == null) {
            return List.of();
        }
        for (Map<String, Object> page : pageSchema.getPages()) {
            if (page == null || !"list".equals(text(page.get("pageKey")))) {
                continue;
            }
            Object grid = page.get("gridLayout");
            if (!(grid instanceof Map<?, ?> gridMap)) {
                continue;
            }
            Map<String, Object> layout = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : gridMap.entrySet()) {
                if (entry.getKey() != null) {
                    layout.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            List<String> fromPage = extractGridFieldRefs(layout, zoneKey);
            if (!fromPage.isEmpty()) {
                return fromPage;
            }
        }
        return List.of();
    }

    private static List<String> extractGridFieldRefs(Map<String, Object> gridLayout, String zoneKey) {
        if (gridLayout == null || gridLayout.isEmpty()) {
            return List.of();
        }
        Object itemsValue = gridLayout.get("items");
        if (!(itemsValue instanceof List<?> items)) {
            return List.of();
        }
        for (String blockType : runtimeSettingBlockTypes(zoneKey)) {
            for (Object itemValue : items) {
                if (!(itemValue instanceof Map<?, ?> item)
                        || !blockType.equals(text(item.get("blockType")))) {
                    continue;
                }
                Object refsValue = item.get("fieldRefs");
                if ("search".equals(zoneKey) && "AiCrudPage".equals(blockType)) {
                    Object propsValue = item.get("props");
                    if (propsValue instanceof Map<?, ?> props && props.containsKey("searchFieldRefs")) {
                        refsValue = props.get("searchFieldRefs");
                    }
                }
                if (!(refsValue instanceof List<?> refs)) {
                    continue;
                }
                return refs.stream()
                        .map(RuntimeDesignerLayoutReader::text)
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .toList();
            }
        }
        return List.of();
    }

    static Map<String, LowcodeFieldSchema> buildRuntimeFieldMap(LowcodeModelSchema modelSchema,
                                                                  LowcodePageSchema pageSchema) {
        Map<String, LowcodeFieldSchema> fieldMap = modelSchema.getFields().stream()
                .collect(Collectors.toMap(
                        LowcodeFieldSchema::getField,
                        field -> field,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (pageSchema == null || pageSchema.getModelRefs() == null) {
            return fieldMap;
        }
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || ref.getFields() == null) {
                continue;
            }
            for (Map<String, Object> source : ref.getFields()) {
                LowcodeFieldSchema field = RuntimePageRefFieldFactory.build(ref, source);
                if (field == null) {
                    continue;
                }
                fieldMap.putIfAbsent(field.getField(), field);
            }
        }
        return fieldMap;
    }

    private static LowcodeFieldSchema resolveRuntimeField(Map<String, LowcodeFieldSchema> fieldMap, String ref) {
        if (StringUtils.isBlank(ref) || fieldMap == null || fieldMap.isEmpty()) {
            return null;
        }
        LowcodeFieldSchema field = fieldMap.get(ref);
        if (field != null) {
            return field;
        }
        for (Map.Entry<String, LowcodeFieldSchema> entry : fieldMap.entrySet()) {
            if (ref.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isZoneFieldAllowed(LowcodeFieldSchema field,
                                       String zoneKey,
                                       Predicate<LowcodeFieldSchema> fallbackPredicate) {
        if (!isActiveField(field)) {
            return false;
        }
        if ("search".equals(zoneKey)) {
            return !isSystemField(field);
        }
        if ("edit".equals(zoneKey)) {
            return !isSystemField(field)
                    && !Boolean.TRUE.equals(field.getReadonly())
                    && fallbackPredicate.test(field);
        }
        return fallbackPredicate.test(field);
    }

    static boolean isActiveField(LowcodeFieldSchema field) {
        String status = StringUtils.defaultString(field == null ? null : field.getFieldStatus());
        return !"DISABLED".equalsIgnoreCase(status) && !"HIDDEN".equalsIgnoreCase(status);
    }

}
