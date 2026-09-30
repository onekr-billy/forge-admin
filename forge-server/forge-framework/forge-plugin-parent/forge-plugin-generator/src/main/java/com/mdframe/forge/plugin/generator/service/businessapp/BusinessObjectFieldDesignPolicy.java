package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeModelSchemaNormalizer;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务对象字段设计策略。
 *
 * <p>重建设计字段时保留字典、引用、编码、级联、公式等运行时元数据，
 * 防止通用输入控件覆盖已经配置的业务组件。</p>
 */
final class BusinessObjectFieldDesignPolicy {

    private static final Map<String, ComponentFieldDefaults> COMPONENT_FIELD_DEFAULTS = Map.ofEntries(
            Map.entry("input", new ComponentFieldDefaults("TEXT", "varchar", 128, 2, "like")),
            Map.entry("barcodeScanner", new ComponentFieldDefaults("TEXT", "varchar", 2048, 2, "eq")),
            Map.entry("textarea", new ComponentFieldDefaults("MULTILINE", "text", null, 2, "like")),
            Map.entry("number", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("inputNumber", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("input-number", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("inputnumber", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("integer", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("money", new ComponentFieldDefaults("MONEY", "decimal", 18, 2, "eq")),
            Map.entry("slider", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("rate", new ComponentFieldDefaults("NUMBER", "decimal", 4, 1, "eq")),
            Map.entry("color", new ComponentFieldDefaults("TEXT", "varchar", 32, 2, "eq")),
            Map.entry("date", new ComponentFieldDefaults("DATE", "date", null, null, "eq")),
            Map.entry("datetime", new ComponentFieldDefaults("DATETIME", "datetime", null, null, "eq")),
            Map.entry("daterange", new ComponentFieldDefaults("TEXT", "text", null, null, "eq")),
            Map.entry("datetimerange", new ComponentFieldDefaults("TEXT", "text", null, null, "eq")),
            Map.entry("month", new ComponentFieldDefaults("TEXT", "varchar", 7, null, "eq")),
            Map.entry("year", new ComponentFieldDefaults("TEXT", "varchar", 4, null, "eq")),
            Map.entry("switch", new ComponentFieldDefaults("SWITCH", "tinyint", 1, 0, "eq")),
            Map.entry("select", new ComponentFieldDefaults("DICT", "varchar", 64, 2, "eq")),
            Map.entry("dictSelect", new ComponentFieldDefaults("DICT", "varchar", 64, 2, "eq")),
            Map.entry("radio", new ComponentFieldDefaults("RADIO", "varchar", 64, 2, "eq")),
            Map.entry("radioButton", new ComponentFieldDefaults("RADIO", "varchar", 64, 2, "eq")),
            Map.entry("checkbox", new ComponentFieldDefaults("CHECKBOX", "varchar", 255, 2, "in")),
            Map.entry("transfer", new ComponentFieldDefaults("MULTI_SELECT", "text", null, null, "in")),
            Map.entry("cascader", new ComponentFieldDefaults("DICT", "varchar", 128, 2, "eq")),
            Map.entry("treeSelect", new ComponentFieldDefaults("SELECT", "bigint", null, null, "eq")),
            Map.entry("customSelect", new ComponentFieldDefaults("SELECT", "varchar", 128, 2, "eq")),
            Map.entry("regionTreeSelect", new ComponentFieldDefaults("REGION", "varchar", 32, 2, "eq")),
            Map.entry("orgTreeSelect", new ComponentFieldDefaults("DEPT", "bigint", null, null, "eq")),
            Map.entry("userSelect", new ComponentFieldDefaults("USER", "bigint", null, null, "eq")),
            Map.entry("fileUpload", new ComponentFieldDefaults("FILE", "varchar", 512, 2, "eq")),
            Map.entry("imageUpload", new ComponentFieldDefaults("IMAGE", "varchar", 512, 2, "eq")),
            Map.entry("objectReference", new ComponentFieldDefaults("REFERENCE", "bigint", null, null, "eq")),
            Map.entry("recordSelector", new ComponentFieldDefaults("RECORD_SELECTOR", "bigint", null, null, "eq")),
            Map.entry("text", new ComponentFieldDefaults("TEXT", "varchar", 255, 2, "like")),
            Map.entry("timerange", new ComponentFieldDefaults("TEXT", "text", null, null, "eq")),
            Map.entry("time", new ComponentFieldDefaults("TEXT", "varchar", 32, null, "eq"))
    );
    private static final Set<String> BUSINESS_COMPONENT_TYPES = Set.of(
            "select", "dictSelect", "radio", "checkbox", "cascader",
            "regionTreeSelect", "orgTreeSelect", "userSelect",
            "fileUpload", "imageUpload", "objectReference", "recordSelector"
    );
    private static final Set<String> GENERIC_COMPONENT_TYPES = Set.of(
            "", "input", "textarea", "number", "inputNumber", "input-number", "inputnumber", "integer"
    );
    private static final Set<String> PRESERVED_BASIC_PROP_KEYS = Set.of(
            "dictType", "options", "optionSource", "labelValueField", "recordSelector", "generation",
            "cascade", "cascadeConfig",
            "referenceObjectCode", "referenceDisplayField", "referenceValueField",
            "targetObjectCode", "targetLabelField",
            "labelField", "valueField", "fieldMappings", "mappings", "searchParams", "keywordFields", "displayFields",
            "placeholder", "clearable", "filterable", "multiple", "validation",
            "checkedValue", "uncheckedValue", "runtimeRules"
    );
    private static final Set<String> PRESERVED_ADVANCED_PROP_KEYS = Set.of(
            "dictType", "recordSelector", "generation", "referenceObjectCode", "referenceDisplayField", "validation"
    );

    private final BusinessFieldSchemaService fieldSchemaService;
    private final LowcodeModelSchemaNormalizer schemaNormalizer;

    BusinessObjectFieldDesignPolicy(BusinessFieldSchemaService fieldSchemaService,
                                    LowcodeModelSchemaNormalizer schemaNormalizer) {
        this.fieldSchemaService = fieldSchemaService;
        this.schemaNormalizer = schemaNormalizer;
    }

    LowcodeModelSchema rebuildModelFields(LowcodeModelSchema modelSchema, List<BusinessFieldDTO> fields) {
        LowcodeModelSchema target = modelSchema == null ? new LowcodeModelSchema() : modelSchema;
        Map<String, LowcodeFieldSchema> existingFields = lowcodeFieldMap(target);
        List<LowcodeFieldSchema> newFields = new ArrayList<>();
        if (target.getFields() != null) {
            target.getFields().stream()
                    .filter(field -> field != null && Boolean.TRUE.equals(field.getSystemField()))
                    .forEach(newFields::add);
        }
        for (BusinessFieldDTO dto : fields) {
            if (dto != null) {
                mergePreservedFieldPayload(existingFields.get(StringUtils.defaultIfBlank(dto.getFieldCode(),
                        text(mapValue(dto.getFieldBinding()).get("fieldCode")))), dto);
                LowcodeFieldSchema next = fieldSchemaService.buildFieldSchema(dto);
                mergePreservedFieldMetadata(existingFields.get(next.getField()), next);
                next.applyMultipleSelectionStorage();
                newFields.add(next);
            }
        }
        target.setFields(newFields);
        return schemaNormalizer.normalizeModelFields(target, true);
    }

    void applyComponentDefaults(BusinessFieldDTO field, String componentType) {
        if (field == null) {
            return;
        }
        ComponentFieldDefaults defaults = COMPONENT_FIELD_DEFAULTS.get(componentType);
        if (defaults == null) {
            return;
        }
        if (StringUtils.isBlank(field.getFieldType())) {
            field.setFieldType(defaults.fieldType());
        }
        field.setQueryType(defaults.queryType());
        if (StringUtils.isBlank(field.getDataType())) {
            field.setDataType(defaults.dataType());
            if (field.getLength() == null) {
                field.setLength(defaults.length());
            }
            if (field.getPrecision() == null) {
                field.setPrecision(defaults.precision());
            }
        }
    }

    private void mergePreservedFieldPayload(LowcodeFieldSchema existing, BusinessFieldDTO dto) {
        if (existing == null || dto == null) {
            return;
        }
        Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(dto.getBasicProps()));
        mergeMissingProps(basicProps, mapValue(existing.getBasicProps()), PRESERVED_BASIC_PROP_KEYS);
        dto.setBasicProps(basicProps);
        Map<String, Object> advancedProps = new LinkedHashMap<>(mapValue(dto.getAdvancedProps()));
        mergeMissingProps(advancedProps, mapValue(existing.getAdvancedProps()), PRESERVED_ADVANCED_PROP_KEYS);
        dto.setAdvancedProps(advancedProps);
        if (StringUtils.isBlank(dto.getDictType()) && StringUtils.isNotBlank(existing.getDictType())) {
            dto.setDictType(existing.getDictType());
        }
        if (StringUtils.isBlank(dto.getReferenceObjectCode()) && StringUtils.isNotBlank(existing.getReferenceObjectCode())) {
            dto.setReferenceObjectCode(existing.getReferenceObjectCode());
        }
        if (StringUtils.isBlank(dto.getReferenceDisplayField()) && StringUtils.isNotBlank(existing.getReferenceDisplayField())) {
            dto.setReferenceDisplayField(existing.getReferenceDisplayField());
        }
        if ((dto.getFormulaConfig() == null || dto.getFormulaConfig().isEmpty())
                && existing.getFormulaConfig() != null && !existing.getFormulaConfig().isEmpty()) {
            dto.setFormulaConfig(new LinkedHashMap<>(existing.getFormulaConfig()));
        }
        if (shouldPreserveBusinessComponent(existing, dto.getComponentType())) {
            dto.setComponentType(existing.getComponentType());
            dto.setFieldType(existing.getBusinessFieldType());
            dto.setDataType(existing.getDataType());
            dto.setLength(existing.getLength());
            dto.setPrecision(existing.getPrecision());
            dto.setQueryType(existing.getQueryType());
        }
    }

    private void mergePreservedFieldMetadata(LowcodeFieldSchema existing, LowcodeFieldSchema next) {
        if (existing == null || next == null) {
            return;
        }
        if (StringUtils.isBlank(next.getDictType()) && StringUtils.isNotBlank(existing.getDictType())) {
            next.setDictType(existing.getDictType());
        }
        if (StringUtils.isBlank(next.getReferenceObjectCode()) && StringUtils.isNotBlank(existing.getReferenceObjectCode())) {
            next.setReferenceObjectCode(existing.getReferenceObjectCode());
        }
        if (StringUtils.isBlank(next.getReferenceDisplayField()) && StringUtils.isNotBlank(existing.getReferenceDisplayField())) {
            next.setReferenceDisplayField(existing.getReferenceDisplayField());
        }
        if ((next.getFormulaConfig() == null || next.getFormulaConfig().isEmpty())
                && existing.getFormulaConfig() != null && !existing.getFormulaConfig().isEmpty()) {
            next.setFormulaConfig(new LinkedHashMap<>(existing.getFormulaConfig()));
            next.setReadonly(true);
        }

        Map<String, Object> nextBasicProps = new LinkedHashMap<>(mapValue(next.getBasicProps()));
        Map<String, Object> existingBasicProps = mapValue(existing.getBasicProps());
        mergeMissingProps(nextBasicProps, existingBasicProps, PRESERVED_BASIC_PROP_KEYS);
        if (StringUtils.isBlank(next.getDictType()) && StringUtils.isNotBlank(text(nextBasicProps.get("dictType")))) {
            next.setDictType(text(nextBasicProps.get("dictType")));
        }
        next.setBasicProps(nextBasicProps);

        Map<String, Object> nextAdvancedProps = new LinkedHashMap<>(mapValue(next.getAdvancedProps()));
        mergeMissingProps(nextAdvancedProps, mapValue(existing.getAdvancedProps()), PRESERVED_ADVANCED_PROP_KEYS);
        next.setAdvancedProps(nextAdvancedProps);

        if (shouldPreserveBusinessComponent(existing, next)) {
            next.setComponentType(existing.getComponentType());
            next.setBusinessFieldType(existing.getBusinessFieldType());
            next.setDataType(existing.getDataType());
            next.setLength(existing.getLength());
            next.setPrecision(existing.getPrecision());
            next.setQueryType(existing.getQueryType());
        }
        if (StringUtils.isNotBlank(next.getDictType()) && isGenericComponent(next.getComponentType())) {
            next.setComponentType(StringUtils.defaultIfBlank(existing.getComponentType(), "select"));
            next.setBusinessFieldType(StringUtils.defaultIfBlank(existing.getBusinessFieldType(), "DICT"));
        }
    }

    private void mergeMissingProps(Map<String, Object> target, Map<String, Object> source, Set<String> keys) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String key : keys) {
            if (!source.containsKey(key)) {
                continue;
            }
            Object current = target.get(key);
            Object preserved = source.get(key);
            if (isBlankValue(current)) {
                target.put(key, preserved);
            } else if (current instanceof Map<?, ?> currentMap && preserved instanceof Map<?, ?> preservedMap) {
                Map<String, Object> merged = new LinkedHashMap<>(mapValue(preservedMap));
                merged.putAll(mapValue(currentMap));
                target.put(key, merged);
            }
        }
    }

    private boolean shouldPreserveBusinessComponent(LowcodeFieldSchema existing, LowcodeFieldSchema next) {
        return next != null && shouldPreserveBusinessComponent(existing, next.getComponentType());
    }

    private boolean shouldPreserveBusinessComponent(LowcodeFieldSchema existing, String nextComponentType) {
        if (existing == null || StringUtils.isBlank(existing.getComponentType()) || !isGenericComponent(nextComponentType)) {
            return false;
        }
        return BUSINESS_COMPONENT_TYPES.contains(existing.getComponentType())
                || StringUtils.isNotBlank(existing.getDictType())
                || !isBlankValue(mapValue(existing.getBasicProps()).get("recordSelector"))
                || !isBlankValue(mapValue(existing.getBasicProps()).get("generation"));
    }

    private boolean isGenericComponent(String componentType) {
        return GENERIC_COMPONENT_TYPES.contains(StringUtils.defaultString(componentType));
    }


    private Map<String, LowcodeFieldSchema> lowcodeFieldMap(LowcodeModelSchema modelSchema) {
        Map<String, LowcodeFieldSchema> result = new LinkedHashMap<>();
        if (modelSchema == null || modelSchema.getFields() == null) {
            return result;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field != null && StringUtils.isNotBlank(field.getField())) {
                result.put(field.getField(), field);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record ComponentFieldDefaults(String fieldType, String dataType, Integer length,
                                          Integer precision, String queryType) {
    }
}
