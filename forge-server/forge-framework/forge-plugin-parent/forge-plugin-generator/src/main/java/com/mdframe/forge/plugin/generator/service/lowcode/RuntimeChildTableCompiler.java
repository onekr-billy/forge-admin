package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveChildRelationField;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveMainRelationField;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolvePrimaryRef;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveRuntimeRelation;

/** Compiles the master-detail runtime protocol; child field rendering remains a pluggable strategy. */
final class RuntimeChildTableCompiler {

    private static final Logger log = LoggerFactory.getLogger(RuntimeChildTableCompiler.class);

    private RuntimeChildTableCompiler() {
    }

    @FunctionalInterface
    interface ChildFieldsCompiler {
        List<Map<String, Object>> compile(LowcodePageModelRef ref, List<String> selectedEditRefs, String childFkField);
    }

    static Map<String, Object> buildMasterDetailConfig(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema,
                                                        ChildFieldsCompiler childFieldsCompiler) {
        log.info("[子表运行时] buildMasterDetailConfig ENTER layoutType={} modelRefs.size={} primaryModelCode={}",
                pageSchema == null ? null : pageSchema.getLayoutType(),
                pageSchema == null || pageSchema.getModelRefs() == null ? 0 : pageSchema.getModelRefs().size(),
                pageSchema == null ? null : pageSchema.getPrimaryModelCode());
        Map<String, Object> config = new LinkedHashMap<>();
        LowcodePageModelRef primaryRef = resolvePrimaryRef(modelSchema, pageSchema);
        String primaryModelCode = primaryRef == null
                ? modelSchema.getObject() == null ? null : modelSchema.getObject().getCode()
                : primaryRef.getModelCode();
        primaryModelCode = StringUtils.defaultIfBlank(pageSchema.getPrimaryModelCode(), primaryModelCode);

        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("modelCode", primaryModelCode);
        primary.put("modelName", primaryRef == null ? modelSchema.getBusinessName() : primaryRef.getModelName());
        primary.put("tableName", modelSchema.getTableName());
        primary.put("keyField", "id");
        config.put("primary", primary);

        List<Map<String, Object>> children = new ArrayList<>();
        if (StringUtils.isBlank(primaryModelCode) || pageSchema.getModelRefs() == null) {
            config.put("children", children);
            return config;
        }

        List<LowcodeRelationSchema> primaryRelations = primaryRef != null && primaryRef.getRelations() != null
                ? primaryRef.getRelations()
                : modelSchema.getRelations();
        List<String> selectedEditRefs = resolveSelectedEditRefs(pageSchema);
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || StringUtils.isBlank(ref.getModelCode())) {
                continue;
            }
            LowcodeRelationSchema relation = resolveRuntimeRelation(primaryModelCode, ref, primaryRelations);
            if (relation == null) {
                continue;
            }
            String childFkField = resolveChildRelationField(primaryModelCode, relation);
            List<Map<String, Object>> childFields = childFieldsCompiler.compile(ref, selectedEditRefs, childFkField);
            if (childFields.isEmpty()) {
                continue;
            }
            Map<String, Object> child = new LinkedHashMap<>();
            Map<String, Object> refProps = ref.getProps() == null ? Map.of() : ref.getProps();
            child.put("key", ref.getModelCode());
            child.put("modelCode", ref.getModelCode());
            child.put("modelName", ref.getModelName());
            child.put("tableName", ref.getTableName());
            child.put("relationType", StringUtils.defaultIfBlank(relation.getRelationType(), "ONE_TO_MANY"));
            child.put("relationKey", StringUtils.defaultIfBlank(text(refProps.get("relationKey")), ref.getModelCode()));
            child.put("sourceField", childFkField);
            child.put("targetField", resolveMainRelationField(primaryModelCode, relation));
            boolean inlineCreateEnabled = booleanWithDefault(refProps.get("inlineCreateEnabled"), true);
            child.put("showInCreate", inlineCreateEnabled);
            child.put("allowCreate", inlineCreateEnabled);
            child.put("inlineCreateEnabled", inlineCreateEnabled);
            child.put("showInEdit", booleanWithDefault(refProps.get("inlineEditEnabled"), true));
            child.put("showInDetail", booleanWithDefault(refProps.get("showInDetail"), true));
            child.put("saveMode", normalizeChildSaveMode(refProps.get("saveMode")));
            Object recordSelector = refProps.get("recordSelector");
            if (recordSelector instanceof Map<?, ?> selector && !selector.isEmpty()) {
                child.put("recordSelector", selector);
            }
            Object rowActions = refProps.get("rowActions");
            if (rowActions instanceof List<?> actions && !actions.isEmpty()) {
                child.put("rowActions", actions);
            }
            putIfNotBlank(child, "tabTitle", text(refProps.get("tabTitle")));
            putIfNotBlank(child, "relationName", text(refProps.get("relationName")));
            putIfNotBlank(child, "displayMode", text(refProps.get("displayMode")));
            child.put("fields", childFields);
            children.add(child);
            log.info("[子表运行时] ref.modelCode={} ref.fields.size={} childFields.size={} tabTitle={} relationName={} refProps.keys={}",
                    ref.getModelCode(),
                    ref.getFields() == null ? 0 : ref.getFields().size(),
                    childFields.size(),
                    text(refProps.get("tabTitle")),
                    text(refProps.get("relationName")),
                    refProps.keySet());
        }
        log.info("[子表运行时] configKey={} primaryModelCode={} masterDetailConfig.children.size={}",
                pageSchema == null ? "?" : (pageSchema.getPrimaryModelCode() + "-" + pageSchema.getLayoutType()),
                primaryModelCode, children.size());
        config.put("children", children);
        return config;
    }

    private static String normalizeChildSaveMode(Object value) {
        return "merge".equalsIgnoreCase(text(value)) ? "merge" : "replace";
    }

    private static List<String> resolveSelectedEditRefs(LowcodePageSchema pageSchema) {
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone == null || Boolean.FALSE.equals(editZone.getEnabled()) || editZone.getFieldRefs() == null) {
            return List.of();
        }
        return editZone.getFieldRefs().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
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

    private static boolean booleanWithDefault(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
