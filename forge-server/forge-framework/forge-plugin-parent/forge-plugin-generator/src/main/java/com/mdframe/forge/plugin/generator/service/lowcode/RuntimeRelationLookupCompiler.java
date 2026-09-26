package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.findRelationFromPrimary;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolvePrimaryRef;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveRefSourceField;

/** Resolves reference display fields shared by joins, translation, search and edit controls. */
final class RuntimeRelationLookupCompiler {

    private RuntimeRelationLookupCompiler() {
    }

    record RelationLookupMeta(String modelCode,
                              String modelName,
                              String configKey,
                              String sourceField,
                              String targetField,
                              String displayField) {
    }

    static void appendDisplayTranslations(Map<String, Object> transConfig,
                                          LowcodeModelSchema modelSchema,
                                          LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getModelRefs() == null || pageSchema.getModelRefs().size() <= 1) {
            return;
        }
        LowcodePageModelRef primaryRef = resolvePrimaryRef(modelSchema, pageSchema);
        if (primaryRef == null) {
            return;
        }
        List<LowcodeRelationSchema> primaryRelations = primaryRef.getRelations() != null && !primaryRef.getRelations().isEmpty()
                ? primaryRef.getRelations()
                : modelSchema.getRelations();
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || StringUtils.isBlank(ref.getModelCode())) {
                continue;
            }
            LowcodeRelationSchema relation = findRelationFromPrimary(primaryRelations, ref.getModelCode());
            if (relation == null || !isReferenceRelation(relation)) {
                continue;
            }
            String sourceField = normalizePrimaryFieldName(modelSchema, relation.getSourceField());
            if (StringUtils.isBlank(sourceField) || findField(modelSchema, sourceField) == null
                    || transConfig.containsKey(sourceField)) {
                continue;
            }
            String displayField = resolveDisplayField(ref, relation);
            if (StringUtils.isBlank(displayField)) {
                continue;
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "relationName");
            rule.put("targetField", displayAlias(sourceField));
            rule.put("relationModelCode", ref.getModelCode());
            rule.put("displayField", displayField);
            transConfig.put(sourceField, rule);
        }
    }

    static String resolveDisplayField(LowcodePageModelRef ref, LowcodeRelationSchema relation) {
        if (ref == null || ref.getFields() == null || ref.getFields().isEmpty()) {
            return null;
        }
        String configured = resolveRefSourceField(ref, relation == null ? null : relation.getDisplayField());
        if (hasRefSourceField(ref, configured)) {
            return configured;
        }
        Set<String> excluded = new LinkedHashSet<>();
        excluded.add(resolveRefSourceField(ref, relation == null ? null : relation.getTargetField()));
        excluded.add(resolveRefSourceField(ref, relation == null ? null : relation.getSourceField()));

        String matched = pickRefDisplayField(ref, excluded, Set.of("name", "title", "label", "orgName", "deptName"));
        if (StringUtils.isNotBlank(matched)) {
            return matched;
        }
        return pickRefDisplayField(ref, excluded, Set.of());
    }

    static String normalizePrimaryFieldName(LowcodeModelSchema modelSchema, String value) {
        if (StringUtils.isBlank(value) || modelSchema == null || modelSchema.getFields() == null) {
            return value;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (value.equals(field.getField()) || value.equals(field.getColumnName())) {
                return field.getField();
            }
        }
        return value;
    }

    static String displayAlias(String sourceField) {
        return StringUtils.isBlank(sourceField) ? null : sourceField + "Name";
    }

    static RelationLookupMeta resolve(LowcodeModelSchema modelSchema,
                                      LowcodePageSchema pageSchema,
                                      String fieldName) {
        if (StringUtils.isBlank(fieldName) || modelSchema == null || pageSchema == null
                || pageSchema.getModelRefs() == null || pageSchema.getModelRefs().size() <= 1) {
            return null;
        }
        LowcodePageModelRef primaryRef = resolvePrimaryRef(modelSchema, pageSchema);
        if (primaryRef == null) {
            return null;
        }
        List<LowcodeRelationSchema> primaryRelations = primaryRef.getRelations() != null && !primaryRef.getRelations().isEmpty()
                ? primaryRef.getRelations()
                : modelSchema.getRelations();
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || StringUtils.isBlank(ref.getModelCode())) {
                continue;
            }
            LowcodeRelationSchema relation = findRelationFromPrimary(primaryRelations, ref.getModelCode());
            if (relation == null || !isReferenceRelation(relation)) {
                continue;
            }
            String sourceField = normalizePrimaryFieldName(modelSchema, relation.getSourceField());
            if (!fieldName.equals(sourceField)) {
                continue;
            }
            String displayField = resolveDisplayField(ref, relation);
            Map<String, Object> props = ref.getProps() == null ? Map.of() : ref.getProps();
            return new RelationLookupMeta(
                    ref.getModelCode(),
                    ref.getModelName(),
                    text(props.get("targetConfigKey")),
                    sourceField,
                    StringUtils.defaultIfBlank(relation.getTargetField(), text(props.get("targetField"))),
                    displayField
            );
        }
        return null;
    }

    static Map<String, Object> buildConfig(RelationLookupMeta lookupMeta) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (lookupMeta == null) {
            return config;
        }
        putIfNotBlank(config, "modelCode", lookupMeta.modelCode());
        putIfNotBlank(config, "modelName", lookupMeta.modelName());
        putIfNotBlank(config, "configKey", lookupMeta.configKey());
        putIfNotBlank(config, "sourceField", lookupMeta.sourceField());
        putIfNotBlank(config, "targetField", lookupMeta.targetField());
        putIfNotBlank(config, "displayField", lookupMeta.displayField());
        putIfNotBlank(config, "targetFieldAlias", displayAlias(lookupMeta.sourceField()));
        return config;
    }

    @SuppressWarnings("unchecked")
    static void applyProps(Map<String, Object> item, RelationLookupMeta lookupMeta, String label) {
        if (item == null || lookupMeta == null) {
            return;
        }
        Map<String, Object> props = item.get("props") instanceof Map<?, ?> propsMap
                ? new LinkedHashMap<>((Map<String, Object>) propsMap)
                : new LinkedHashMap<>();
        props.putIfAbsent("clearable", true);
        props.putIfAbsent("filterable", true);
        props.putIfAbsent("placeholder", "请选择" + StringUtils.defaultIfBlank(label, "关联数据"));
        putIfNotBlank(props, "labelValueField", displayAlias(lookupMeta.sourceField()));
        Map<String, Object> optionSource = buildOptionSource(lookupMeta);
        if (!optionSource.isEmpty()) {
            item.put("optionSource", optionSource);
            props.put("optionSource", optionSource);
        }
        item.put("props", props);
    }

    private static Map<String, Object> buildOptionSource(RelationLookupMeta lookupMeta) {
        if (lookupMeta == null || StringUtils.isBlank(lookupMeta.configKey())) {
            return Map.of();
        }
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "list");
        source.put("api", "get@/ai/crud/" + lookupMeta.configKey() + "/page");
        source.put("recordsField", "records");
        source.put("valueField", StringUtils.defaultIfBlank(lookupMeta.targetField(), "id"));
        source.put("keyField", StringUtils.defaultIfBlank(lookupMeta.targetField(), "id"));
        source.put("labelField", StringUtils.defaultIfBlank(lookupMeta.displayField(), "name"));
        source.put("fallbackLabelFields", List.of(
                StringUtils.defaultIfBlank(lookupMeta.displayField(), "name"),
                "name", "title", "label", "customerName", "contactName", "objectName"
        ));
        source.put("params", Map.of("pageNum", 1, "pageSize", 50));
        return source;
    }

    private static String pickRefDisplayField(LowcodePageModelRef ref, Set<String> excluded, Set<String> preferredNames) {
        if (ref == null || ref.getFields() == null) {
            return null;
        }
        for (Map<String, Object> field : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = text(field.get("columnName"));
            if (StringUtils.isBlank(sourceField)
                    || excluded.contains(sourceField)
                    || excluded.contains(columnName)
                    || RuntimeSystemFields.FIELD_NAMES.contains(sourceField)
                    || RuntimeSystemFields.COLUMN_NAMES.contains(columnName)) {
                continue;
            }
            if (!preferredNames.isEmpty() && !preferredNames.contains(sourceField)) {
                continue;
            }
            return sourceField;
        }
        return null;
    }

    private static boolean hasRefSourceField(LowcodePageModelRef ref, String sourceField) {
        if (ref == null || ref.getFields() == null || StringUtils.isBlank(sourceField)) {
            return false;
        }
        for (Map<String, Object> field : ref.getFields()) {
            String candidate = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = text(field.get("columnName"));
            if (sourceField.equals(candidate) || sourceField.equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReferenceRelation(LowcodeRelationSchema relation) {
        return relation != null && "REFERENCE".equalsIgnoreCase(StringUtils.defaultString(relation.getRelationType()));
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
