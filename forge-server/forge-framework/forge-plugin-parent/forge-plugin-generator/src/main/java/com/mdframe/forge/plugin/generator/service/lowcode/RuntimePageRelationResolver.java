package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves page model references and their runtime relation direction. */
final class RuntimePageRelationResolver {

    private RuntimePageRelationResolver() {
    }

    static LowcodePageModelRef resolvePrimaryRef(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getModelRefs() == null || pageSchema.getModelRefs().isEmpty()) {
            return null;
        }
        return pageSchema.getModelRefs().stream()
                .filter(ref -> Boolean.TRUE.equals(ref.getPrimary()))
                .findFirst()
                .orElseGet(() -> {
                    String modelCode = modelSchema.getObject() == null ? null : modelSchema.getObject().getCode();
                    return pageSchema.getModelRefs().stream()
                            .filter(ref -> StringUtils.equals(ref.getModelCode(), pageSchema.getPrimaryModelCode())
                                    || StringUtils.equals(ref.getModelCode(), modelCode))
                            .findFirst()
                            .orElse(pageSchema.getModelRefs().get(0));
                });
    }

    static String resolveChildRelationField(String primaryModelCode, LowcodeRelationSchema relation) {
        if (relation == null) {
            return null;
        }
        return primaryModelCode.equals(relation.getTargetObjectCode())
                ? relation.getSourceField()
                : relation.getTargetField();
    }

    static String resolveMainRelationField(String primaryModelCode, LowcodeRelationSchema relation) {
        if (relation == null) {
            return null;
        }
        return primaryModelCode.equals(relation.getTargetObjectCode())
                ? relation.getTargetField()
                : relation.getSourceField();
    }

    static LowcodeRelationSchema resolveRuntimeRelation(String primaryModelCode,
                                                         LowcodePageModelRef ref,
                                                         List<LowcodeRelationSchema> primaryRelations) {
        if (primaryRelations != null) {
            for (LowcodeRelationSchema relation : primaryRelations) {
                if (relation != null && ref.getModelCode().equals(relation.getTargetObjectCode())) {
                    return relation;
                }
            }
        }
        if (ref.getRelations() != null) {
            for (LowcodeRelationSchema relation : ref.getRelations()) {
                if (relation != null && primaryModelCode.equals(relation.getTargetObjectCode())) {
                    return relation;
                }
            }
        }
        return inferRuntimeRelation(primaryModelCode, ref);
    }

    static LowcodeRelationSchema findRelationFromPrimary(List<LowcodeRelationSchema> relations, String targetModelCode) {
        if (relations == null || StringUtils.isBlank(targetModelCode)) {
            return null;
        }
        return relations.stream()
                .filter(relation -> relation != null && targetModelCode.equals(relation.getTargetObjectCode()))
                .findFirst()
                .orElse(null);
    }

    private static LowcodeRelationSchema inferRuntimeRelation(String primaryModelCode, LowcodePageModelRef ref) {
        if (StringUtils.isBlank(primaryModelCode) || ref == null || ref.getFields() == null) {
            return null;
        }
        String expectedCamel = snakeToCamel(primaryModelCode) + "Id";
        String expectedSnake = camelToSnake(primaryModelCode) + "_id";
        for (Map<String, Object> field : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = StringUtils.defaultIfBlank(text(field.get("columnName")), sourceField);
            if (expectedCamel.equals(sourceField) || expectedSnake.equals(columnName)) {
                LowcodeRelationSchema relation = new LowcodeRelationSchema();
                relation.setRelationType("ONE_TO_MANY");
                relation.setSourceField(sourceField);
                relation.setTargetObjectCode(primaryModelCode);
                relation.setTargetField("id");
                return relation;
            }
        }
        return null;
    }

    static String resolveRefSourceField(LowcodePageModelRef ref, String value) {
        if (ref == null || ref.getFields() == null || StringUtils.isBlank(value)) {
            return value;
        }
        for (Map<String, Object> field : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String fieldRef = StringUtils.defaultIfBlank(text(field.get("fieldRef")), sourceField);
            String columnName = text(field.get("columnName"));
            if (value.equals(sourceField)
                    || value.equals(fieldRef)
                    || value.equals(columnName)
                    || value.equals(ref.getModelCode() + "__" + sourceField)
                    || value.equals(ref.getModelCode() + "." + sourceField)) {
                return sourceField;
            }
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String snakeToCamel(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }
}
