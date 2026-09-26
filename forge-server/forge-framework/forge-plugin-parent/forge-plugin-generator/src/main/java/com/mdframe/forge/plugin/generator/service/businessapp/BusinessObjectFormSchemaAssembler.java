package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeComponentCatalog;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeFieldConstraintSupport;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 业务对象表单 Schema 组装器。
 *
 * <p>采用 Assembler + Adapter 模式，统一默认表单生成、旧 pageSchema 兼容和
 * form-create 规则迁移，避免这些协议转换继续堆积在设计器聚合服务。</p>
 */
final class BusinessObjectFormSchemaAssembler {

    private static final String FORM_DESIGNER_SCHEMA_OPTION_KEY = "formDesignerSchema";
    private static final Set<String> FORM_FIELD_COMPONENT_KEYS = LowcodeComponentCatalog.FIELD_COMPONENT_KEYS;

    private final ObjectMapper objectMapper;

    BusinessObjectFormSchemaAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    FormDesignerSchemaDTO resolveFormDesignerSchema(AiBusinessObject object, LowcodeModelSchema modelSchema,
                                                            LowcodePageSchema pageSchema,
                                                            Map<String, Object> designerOptions) {
        if (designerOptions != null && designerOptions.containsKey(FORM_DESIGNER_SCHEMA_OPTION_KEY)) {
            Object value = designerOptions.get(FORM_DESIGNER_SCHEMA_OPTION_KEY);
            try {
                if (value instanceof String text && StringUtils.isNotBlank(text)) {
                    return objectMapper.readValue(text, FormDesignerSchemaDTO.class);
                }
                if (value != null) {
                    return objectMapper.convertValue(value, FormDesignerSchemaDTO.class);
                }
            } catch (Exception ignored) {
                return buildDefaultFormDesignerSchema(object, modelSchema, pageSchema);
            }
        }
        FormDesignerSchemaDTO migrated = migrateFormDesignerSchemaFromPageSchema(object, modelSchema, pageSchema);
        if (migrated != null) {
            return migrated;
        }
        return buildDefaultFormDesignerSchema(object, modelSchema, pageSchema);
    }

    private FormDesignerSchemaDTO buildDefaultFormDesignerSchema(AiBusinessObject object, LowcodeModelSchema modelSchema,
                                                                 LowcodePageSchema pageSchema) {
        FormDesignerSchemaDTO schema = new FormDesignerSchemaDTO();
        String modelCode = resolveModelCode(object);
        schema.setFormKey(modelCode + "_default_form");
        schema.setFormName(StringUtils.defaultIfBlank(object.getObjectName(), modelCode) + "表单");
        Map<String, Object> layout = resolveFormDesignerLayout(pageSchema);
        schema.setLayout(layout);

        if (modelSchema == null || modelSchema.getFields() == null) {
            return schema;
        }
        List<Map<String, Object>> components = new ArrayList<>();
        int index = 0;
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null
                    || Boolean.TRUE.equals(field.getSystemField())
                    || Boolean.TRUE.equals(field.getReadonly())
                    || Boolean.FALSE.equals(field.getFormVisible())) {
                continue;
            }
            components.add(buildDefaultFormComponent(field, index++));
        }
        schema.setComponents(components);
        return schema;
    }

    private FormDesignerSchemaDTO migrateFormDesignerSchemaFromPageSchema(AiBusinessObject object,
                                                                          LowcodeModelSchema modelSchema,
                                                                          LowcodePageSchema pageSchema) {
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone == null || editZone.getProps() == null) {
            return null;
        }
        FormDesignerSchemaDTO embedded = readFormDesignerSchema(
                editZone.getProps().get(FORM_DESIGNER_SCHEMA_OPTION_KEY));
        if (embedded != null) {
            return embedded;
        }
        List<Map<String, Object>> formCreateRules = listOfMap(editZone.getProps().get("formCreateRule"));
        if (!formCreateRules.isEmpty()) {
            return migrateFormCreateRulesToFormDesignerSchema(object, modelSchema, pageSchema, formCreateRules);
        }
        if (editZone.getFieldRefs() == null || editZone.getFieldRefs().isEmpty()) {
            return null;
        }
        Map<String, LowcodeFieldSchema> fieldMap = lowcodeFieldMap(modelSchema);
        Map<String, Object> fieldSettings = mapValue(editZone.getProps().get("fieldSettings"));
        FormDesignerSchemaDTO schema = createBaseMigratedFormSchema(object, pageSchema, "pageSchema");
        List<Map<String, Object>> components = new ArrayList<>();
        int index = 0;
        for (String fieldRef : editZone.getFieldRefs()) {
            LowcodeFieldSchema field = fieldMap.get(fieldRef);
            if (field == null || Boolean.TRUE.equals(field.getSystemField())) {
                continue;
            }
            components.add(buildMigratedPageComponent(field, fieldSettings.get(fieldRef), index++));
        }
        if (components.isEmpty()) {
            return null;
        }
        schema.setComponents(components);
        return schema;
    }

    private FormDesignerSchemaDTO migrateFormCreateRulesToFormDesignerSchema(AiBusinessObject object,
                                                                             LowcodeModelSchema modelSchema,
                                                                             LowcodePageSchema pageSchema,
                                                                             List<Map<String, Object>> rules) {
        FormDesignerSchemaDTO schema = createBaseMigratedFormSchema(object, pageSchema, "formCreateRule");
        Map<String, LowcodeFieldSchema> fieldMap = lowcodeFieldMap(modelSchema);
        int gridColumns = integerValue(schema.getLayout().get("gridColumns"), 2);
        List<Map<String, Object>> components = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            Map<String, Object> component = migrateFormCreateRule(rules.get(index), fieldMap, gridColumns, index);
            if (component != null) {
                components.add(component);
            }
        }
        if (components.isEmpty()) {
            return null;
        }
        schema.setComponents(components);
        return schema;
    }

    private FormDesignerSchemaDTO createBaseMigratedFormSchema(AiBusinessObject object, LowcodePageSchema pageSchema,
                                                               String source) {
        FormDesignerSchemaDTO schema = new FormDesignerSchemaDTO();
        String modelCode = resolveModelCode(object);
        schema.setFormKey(modelCode + "_default_form");
        schema.setFormName(StringUtils.defaultIfBlank(object.getObjectName(), modelCode) + "表单");
        schema.setLayout(resolveFormDesignerLayout(pageSchema));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("migratedFrom", source);
        schema.setSettings(settings);
        return schema;
    }

    private Map<String, Object> buildMigratedPageComponent(LowcodeFieldSchema field, Object settingValue, int index) {
        Map<String, Object> component = buildDefaultFormComponent(field, index);
        Map<String, Object> setting = mapValue(settingValue);
        String componentType = text(setting.get("componentType"));
        if (StringUtils.isNotBlank(componentType)) {
            component.put("componentKey", normalizeFormComponentKey(componentType));
        }
        Map<String, Object> props = new LinkedHashMap<>(mapValue(component.get("props")));
        props.putAll(mapValue(setting.get("props")));
        putIfNotBlank(props, "dictType", text(setting.get("dictType")));
        if (setting.containsKey("defaultValue")) {
            props.put("defaultValue", setting.get("defaultValue"));
        }
        component.put("props", props);

        Map<String, Object> layout = new LinkedHashMap<>(mapValue(component.get("layout")));
        if (setting.containsKey("span")) {
            layout.put("span", integerValue(setting.get("span"), integerValue(layout.get("span"), 1)));
        }
        if (setting.containsKey("align")) {
            layout.put("align", normalizeAlign(text(setting.get("align"))));
        }
        if (setting.containsKey("labelWidth")) {
            layout.put("labelWidth", integerValue(setting.get("labelWidth"), 100));
        }
        component.put("layout", layout);

        Map<String, Object> validation = new LinkedHashMap<>(mapValue(component.get("validation")));
        if (setting.containsKey("required")) {
            validation.put("required", readBoolean(setting.get("required"), false));
        }
        putIfNotBlank(validation, "requiredMessage", text(setting.get("requiredMessage")));
        List<Map<String, Object>> rules = listOfMap(setting.get("rules"));
        if (!rules.isEmpty()) {
            validation.put("rules", rules);
        }
        component.put("validation", validation);

        Map<String, Object> visibility = new LinkedHashMap<>(mapValue(component.get("visibility")));
        if (setting.containsKey("readonly")) {
            visibility.put("readonly", readBoolean(setting.get("readonly"), false));
        }
        component.put("visibility", visibility);
        return component;
    }

    private Map<String, Object> migrateFormCreateRule(Map<String, Object> rule,
                                                      Map<String, LowcodeFieldSchema> fieldMap,
                                                      int gridColumns,
                                                      int index) {
        if (rule == null) {
            return null;
        }
        String fieldCode = resolveFormCreateFieldCode(rule);
        LowcodeFieldSchema field = fieldMap.get(fieldCode);
        String componentKey = resolveFormCreateComponentKey(rule, field);
        if (!FORM_FIELD_COMPONENT_KEYS.contains(componentKey) && StringUtils.isBlank(fieldCode)) {
            return null;
        }
        String label = StringUtils.firstNonBlank(text(rule.get("title")), text(rule.get("label")),
                field == null ? null : field.getLabel(), fieldCode, "字段");
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("id", StringUtils.firstNonBlank(text(getNestedValue(rule, "_forge.id")),
                text(rule.get("id")), "cmp_" + StringUtils.defaultIfBlank(fieldCode, String.valueOf(index + 1))));
        component.put("componentKey", componentKey);
        component.put("label", label);

        Map<String, Object> binding = new LinkedHashMap<>(mapValue(getNestedValue(rule, "_forge.fieldBinding")));
        binding.putIfAbsent("mode", StringUtils.isBlank(fieldCode) ? "virtual" : "field");
        binding.putIfAbsent("fieldCode", fieldCode);
        binding.putIfAbsent("createIfMissing", false);
        binding.putIfAbsent("source", "migration");
        binding.putIfAbsent("locked", field == null ? false : Boolean.TRUE.equals(field.getReadonly()));
        component.put("fieldBinding", binding);

        component.put("props", buildMigratedRuleProps(rule));
        component.put("layout", buildMigratedRuleLayout(rule, gridColumns));
        component.put("validation", buildMigratedRuleValidation(rule, componentKey, label));
        component.put("visibility", buildMigratedRuleVisibility(rule));
        List<Map<String, Object>> children = new ArrayList<>();
        List<Map<String, Object>> childRules = listOfMap(rule.get("children"));
        for (int childIndex = 0; childIndex < childRules.size(); childIndex++) {
            Map<String, Object> child = migrateFormCreateRule(childRules.get(childIndex), fieldMap, gridColumns, childIndex);
            if (child != null) {
                children.add(child);
            }
        }
        component.put("children", children);
        return component;
    }

    private Map<String, Object> buildMigratedRuleProps(Map<String, Object> rule) {
        Map<String, Object> props = new LinkedHashMap<>(mapValue(rule.get("props")));
        props.putAll(mapValue(getNestedValue(rule, "_forge.props")));
        Map<String, Object> formCreateMeta = new LinkedHashMap<>();
        putIfPresent(formCreateMeta, "style", rule.get("style"));
        putIfPresent(formCreateMeta, "class", rule.get("class"));
        putIfPresent(formCreateMeta, "className", rule.get("className"));
        putIfPresent(formCreateMeta, "native", rule.get("native"));
        putIfPresent(formCreateMeta, "wrap", rule.get("wrap"));
        putIfPresent(formCreateMeta, "slot", rule.get("slot"));
        putIfPresent(formCreateMeta, "effect", rule.get("effect"));
        if (!formCreateMeta.isEmpty()) {
            props.put("__fc", formCreateMeta);
        }
        if (rule.containsKey("value")) {
            props.put("defaultValue", rule.get("value"));
        }
        if (rule.get("options") instanceof List<?> options) {
            props.put("options", options);
        }
        return props;
    }

    private Map<String, Object> buildMigratedRuleLayout(Map<String, Object> rule, int gridColumns) {
        Map<String, Object> layout = new LinkedHashMap<>(mapValue(getNestedValue(rule, "_forge.layout")));
        int span = integerValue(layout.get("span"), 1);
        Map<String, Object> col = mapValue(rule.get("col"));
        if (col.containsKey("span")) {
            int colSpan = integerValue(col.get("span"), 24);
            span = Math.max(1, Math.min(gridColumns, (int) Math.ceil(gridColumns * Math.min(24, colSpan) / 24.0)));
        }
        layout.put("span", span);
        layout.put("align", normalizeAlign(text(layout.get("align"))));
        return layout;
    }

    private Map<String, Object> buildMigratedRuleValidation(Map<String, Object> rule, String componentKey, String label) {
        Map<String, Object> validation = new LinkedHashMap<>();
        List<Map<String, Object>> rules = listOfMap(rule.get("validate"));
        Object requiredSwitch = rule.get("$required");
        boolean requiredFromSwitch = isRequiredSwitchEnabled(requiredSwitch);
        boolean required = requiredFromSwitch || rules.stream().anyMatch(item -> readBoolean(item.get("required"), false));
        String requiredMessage = requiredFromSwitch && requiredSwitch instanceof String message && StringUtils.isNotBlank(message)
                ? message
                : rules.stream()
                .filter(item -> readBoolean(item.get("required"), false))
                .map(item -> text(item.get("message")))
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(buildFormPlaceholder(componentKey, label));
        if (requiredFromSwitch && rules.stream().noneMatch(item -> readBoolean(item.get("required"), false))) {
            Map<String, Object> requiredRule = new LinkedHashMap<>();
            requiredRule.put("required", true);
            requiredRule.put("message", requiredMessage);
            requiredRule.put("trigger", List.of("blur", "change"));
            rules = new ArrayList<>(rules);
            rules.add(0, requiredRule);
        }
        validation.put("required", required);
        validation.put("requiredMessage", required ? requiredMessage : "");
        validation.put("rules", rules);
        return validation;
    }

    private boolean isRequiredSwitchEnabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)
                && !"false".equalsIgnoreCase(text) && !"0".equals(text)) {
            return true;
        }
        return readBoolean(value, false);
    }

    private Map<String, Object> buildMigratedRuleVisibility(Map<String, Object> rule) {
        Map<String, Object> visibility = new LinkedHashMap<>(mapValue(getNestedValue(rule, "_forge.visibility")));
        visibility.put("hidden", readBoolean(rule.get("hidden"), readBoolean(visibility.get("hidden"), false)));
        visibility.put("readonly", readBoolean(getNestedValue(rule, "props.disabled"),
                readBoolean(visibility.get("readonly"), false)));
        return visibility;
    }

    private String resolveFormCreateFieldCode(Map<String, Object> rule) {
        String fieldCode = text(getNestedValue(rule, "_forge.fieldBinding.fieldCode"));
        return StringUtils.firstNonBlank(fieldCode, text(rule.get("field")), text(rule.get("name")));
    }

    private String resolveFormCreateComponentKey(Map<String, Object> rule, LowcodeFieldSchema field) {
        String componentKey = text(getNestedValue(rule, "_forge.componentKey"));
        if (StringUtils.isNotBlank(componentKey)) {
            return normalizeFormComponentKey(componentKey);
        }
        String dragTag = text(rule.get("_fc_drag_tag"));
        componentKey = switch (StringUtils.defaultString(dragTag)) {
            case "forgeDictSelect" -> "dictSelect";
            case "forgeRegionTreeSelect" -> "regionTreeSelect";
            case "forgeOrgTreeSelect" -> "orgTreeSelect";
            case "forgeUserSelect" -> "userSelect";
            case "forgeFileUpload" -> "fileUpload";
            case "forgeImageUpload" -> "imageUpload";
            case "forgeObjectReference" -> "objectReference";
            case "forgeRecordSelector" -> "recordSelector";
            default -> null;
        };
        if (StringUtils.isNotBlank(componentKey)) {
            return componentKey;
        }
        String type = text(rule.get("type"));
        Map<String, Object> props = mapValue(rule.get("props"));
        if ("input".equals(type) && "textarea".equals(text(props.get("type")))) {
            return "textarea";
        }
        if ("inputNumber".equals(type)) {
            return "number";
        }
        if ("datePicker".equals(type)) {
            return "datetime".equals(text(props.get("type"))) ? "datetime" : "date";
        }
        if ("timePicker".equals(type)) {
            return "time";
        }
        if ("upload".equals(type)) {
            return "picture-card".equals(text(props.get("listType"))) || "image/*".equals(text(props.get("accept")))
                    ? "imageUpload" : "fileUpload";
        }
        if ("select".equals(type) && StringUtils.isNotBlank(text(props.get("dictType")))) {
            return "dictSelect";
        }
        Map<String, String> typeMap = Map.ofEntries(
                Map.entry("input", "input"),
                Map.entry("select", "select"),
                Map.entry("radio", "radio"),
                Map.entry("checkbox", "checkbox"),
                Map.entry("switch", "switch"),
                Map.entry("cascader", "cascader"),
                Map.entry("tree", "orgTreeSelect"),
                Map.entry("elTreeSelect", "orgTreeSelect")
        );
        return StringUtils.defaultIfBlank(typeMap.get(type), field == null ? "input" : resolveFormComponentKey(field));
    }

    private Map<String, Object> resolveFormDesignerLayout(LowcodePageSchema pageSchema) {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("labelPlacement", "left");
        layout.put("labelAlign", "right");
        layout.put("labelWidth", 100);
        layout.put("size", "medium");
        layout.put("showFeedback", true);
        layout.put("gridColumns", 2);
        layout.put("rowGap", 16);
        layout.put("columnGap", 16);
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone == null || editZone.getProps() == null) {
            return layout;
        }
        Map<String, Object> props = editZone.getProps();
        layout.put("labelPlacement", StringUtils.defaultIfBlank(text(props.get("labelPlacement")), "left"));
        layout.put("labelAlign", normalizeLabelAlign(text(props.get("labelAlign"))));
        layout.put("labelWidth", integerValue(props.get("labelWidth"), 100));
        layout.put("size", normalizeRuntimeFormSize(text(props.get("size"))));
        layout.put("showFeedback", readBoolean(props.get("showFeedback"), true));
        layout.put("hideRequiredAsterisk", readBoolean(props.get("hideRequiredAsterisk"), false));
        layout.put("inlineFeedback", readBoolean(props.get("inlineFeedback"), false));
        putIfPresent(layout, "formStyle", props.get("editFormStyle"));
        putIfNotBlank(layout, "formClass", text(props.get("editFormClass")));
        layout.put("gridColumns", clamp(integerValue(props.get("editGridCols"), 2), 1, 3));
        layout.put("rowGap", integerValue(props.get("rowGap"), 16));
        layout.put("columnGap", integerValue(props.get("columnGap"), 16));
        Map<String, Object> options = mapValue(props.get("formCreateOptions"));
        Map<String, Object> form = mapValue(options.get("form"));
        Map<String, Object> forge = mapValue(options.get("_forge"));
        String labelPosition = text(form.get("labelPosition"));
        if (StringUtils.isNotBlank(labelPosition)) {
            layout.put("labelPlacement", "top".equals(labelPosition) ? "top" : "left");
            layout.put("labelAlign", "left".equals(labelPosition) ? "left" : "right");
        }
        if (form.containsKey("labelWidth")) {
            layout.put("labelWidth", integerValue(form.get("labelWidth"), integerValue(layout.get("labelWidth"), 100)));
        }
        if (form.containsKey("size")) {
            layout.put("size", normalizeRuntimeFormSize(text(form.get("size"))));
        }
        if (form.containsKey("showMessage")) {
            layout.put("showFeedback", readBoolean(form.get("showMessage"), true));
        }
        if (form.containsKey("hideRequiredAsterisk")) {
            layout.put("hideRequiredAsterisk", readBoolean(form.get("hideRequiredAsterisk"), false));
        }
        if (form.containsKey("inlineMessage")) {
            layout.put("inlineFeedback", readBoolean(form.get("inlineMessage"), false));
        }
        putIfPresent(layout, "formStyle", form.get("style"));
        putIfNotBlank(layout, "formClass", StringUtils.defaultIfBlank(text(form.get("className")), text(form.get("class"))));
        if (forge.containsKey("labelAlign")) {
            layout.put("labelAlign", normalizeLabelAlign(text(forge.get("labelAlign"))));
        }
        if (forge.containsKey("size")) {
            layout.put("size", normalizeRuntimeFormSize(text(forge.get("size"))));
        }
        if (forge.containsKey("showFeedback")) {
            layout.put("showFeedback", readBoolean(forge.get("showFeedback"), true));
        }
        if (forge.containsKey("gridColumns")) {
            layout.put("gridColumns", clamp(integerValue(forge.get("gridColumns"), integerValue(layout.get("gridColumns"), 2)), 1, 3));
        }
        if (forge.containsKey("rowGap")) {
            layout.put("rowGap", integerValue(forge.get("rowGap"), integerValue(layout.get("rowGap"), 16)));
        }
        if (forge.containsKey("columnGap")) {
            layout.put("columnGap", integerValue(forge.get("columnGap"), integerValue(layout.get("columnGap"), 16)));
        }
        return layout;
    }

    private Map<String, Object> buildDefaultFormComponent(LowcodeFieldSchema field, int index) {
        Map<String, Object> component = new LinkedHashMap<>();
        String fieldCode = StringUtils.defaultIfBlank(field.getField(), "field" + index);
        String label = StringUtils.defaultIfBlank(field.getLabel(), fieldCode);
        String componentKey = resolveFormComponentKey(field);
        component.put("id", "cmp_" + fieldCode);
        component.put("componentKey", componentKey);
        component.put("label", label);

        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("mode", "field");
        binding.put("fieldCode", fieldCode);
        binding.put("createIfMissing", false);
        binding.put("source", "field_asset");
        binding.put("locked", Boolean.TRUE.equals(field.getReadonly()));
        component.put("fieldBinding", binding);

        Map<String, Object> props = new LinkedHashMap<>();
        if (field.getBasicProps() != null) {
            props.putAll(field.getBasicProps());
            props.remove("fieldBinding");
        }
        props.putIfAbsent("placeholder", buildFormPlaceholder(componentKey, label));
        props.putIfAbsent("clearable", true);
        if (StringUtils.isNotBlank(field.getDictType())) {
            props.put("dictType", field.getDictType());
        }
        if (StringUtils.isNotBlank(field.getReferenceObjectCode())) {
            props.put("referenceObjectCode", field.getReferenceObjectCode());
        }
        if (StringUtils.isNotBlank(field.getReferenceDisplayField())) {
            props.put("referenceDisplayField", field.getReferenceDisplayField());
        }
        LowcodeFieldConstraintSupport.applyRuntimeConstraints(field, componentKey, props);
        component.put("props", props);

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("span", Set.of("textarea", "fileUpload", "imageUpload", "subTable").contains(componentKey) ? 2 : 1);
        layout.put("align", "left");
        component.put("layout", layout);

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("required", Boolean.TRUE.equals(field.getRequired()));
        validation.put("requiredMessage", Boolean.TRUE.equals(field.getRequired())
                ? buildFormPlaceholder(componentKey, label)
                : "");
        component.put("validation", validation);

        Map<String, Object> visibility = new LinkedHashMap<>();
        visibility.put("hidden", false);
        visibility.put("readonly", Boolean.TRUE.equals(field.getReadonly()));
        component.put("visibility", visibility);
        return component;
    }

    String resolveFormComponentKey(LowcodeFieldSchema field) {
        String componentType = StringUtils.defaultString(field.getComponentType());
        String businessType = StringUtils.defaultString(field.getBusinessFieldType()).toUpperCase(Locale.ROOT);
        if (Set.of("slider", "rate", "color", "radioButton", "transfer", "customSelect", "daterange",
                "datetimerange", "month", "year", "timerange", "treeSelect", "text").contains(componentType)) {
            return componentType;
        }
        if ("barcodeScanner".equals(componentType)) {
            return "barcodeScanner";
        }
        if ("textarea".equals(componentType) || "MULTILINE".equals(businessType)) {
            return "textarea";
        }
        if ("MONEY".equals(businessType)) {
            return "money";
        }
        if ("number".equals(componentType) || "NUMBER".equals(businessType)) {
            return "number";
        }
        if ("datetime".equals(componentType) || "DATETIME".equals(businessType)) {
            return "datetime";
        }
        if ("date".equals(componentType) || "DATE".equals(businessType)) {
            return "date";
        }
        if ("time".equals(componentType)) {
            return "time";
        }
        if ("switch".equals(componentType) || "SWITCH".equals(businessType)) {
            return "switch";
        }
        if ("radio".equals(componentType) || "RADIO".equals(businessType)) {
            return "radio";
        }
        if ("checkbox".equals(componentType) || Set.of("CHECKBOX", "MULTI_SELECT").contains(businessType)) {
            return "checkbox";
        }
        if (StringUtils.isNotBlank(field.getDictType()) || "dictSelect".equals(componentType)) {
            return "dictSelect";
        }
        if ("regionTreeSelect".equals(componentType) || "REGION".equals(businessType)) {
            return "regionTreeSelect";
        }
        if ("orgTreeSelect".equals(componentType) || "DEPT".equals(businessType)) {
            return "orgTreeSelect";
        }
        if ("userSelect".equals(componentType) || "USER".equals(businessType)) {
            return "userSelect";
        }
        if ("imageUpload".equals(componentType) || "IMAGE".equals(businessType)) {
            return "imageUpload";
        }
        if (Set.of("fileUpload", "upload").contains(componentType)
                || Set.of("FILE", "ATTACHMENT").contains(businessType)) {
            return "fileUpload";
        }
        if ("recordSelector".equals(componentType) || "RECORD_SELECTOR".equals(businessType)) {
            return "recordSelector";
        }
        if ("REFERENCE".equals(businessType)) {
            return "objectReference";
        }
        if ("select".equals(componentType) || Set.of("SELECT", "DICT").contains(businessType)) {
            return "select";
        }
        return "input";
    }

    private String buildFormPlaceholder(String componentKey, String label) {
        if (Set.of("select", "radio", "checkbox", "dictSelect", "date", "datetime", "time",
                "regionTreeSelect", "orgTreeSelect", "userSelect", "fileUpload", "imageUpload",
                "objectReference", "recordSelector").contains(componentKey)) {
            return "请选择" + label;
        }
        return "请输入" + label;
    }

    private String resolveModelCode(AiBusinessObject object) {
        return normalizeConfigKey(StringUtils.firstNonBlank(
                object.getModelCode(), object.getObjectCode(), "business_object"));
    }

    private String normalizeConfigKey(String value) {
        String normalized = StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[^a-z]+", "")
                .replaceAll("_+$", "");
        return StringUtils.left(StringUtils.defaultIfBlank(normalized, "business_object"), 64);
    }

    private LowcodePageZone findZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema == null || pageSchema.getZones() == null) {
            return null;
        }
        return pageSchema.getZones().stream()
                .filter(zone -> zone != null && zoneKey.equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
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

    private String normalizeFormComponentKey(String componentKey) {
        String normalized = StringUtils.defaultIfBlank(componentKey, "input");
        return Set.of("inputNumber", "input-number", "inputnumber").contains(normalized)
                ? "number" : normalized;
    }

    private String normalizeAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "center", "right").contains(align) ? align : "left";
    }

    private String normalizeLabelAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "right").contains(align) ? align : "right";
    }

    private String normalizeRuntimeFormSize(String value) {
        String size = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        if ("default".equals(size) || "medium".equals(size)) {
            return "medium";
        }
        return Set.of("small", "large").contains(size) ? size : "medium";
    }

    private int integerValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                String digits = text.trim().replaceAll("[^0-9-]", "");
                if (StringUtils.isBlank(digits) || "-".equals(digits)) {
                    return defaultValue;
                }
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignoredAgain) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private Object getNestedValue(Map<String, Object> source, String path) {
        if (source == null || StringUtils.isBlank(path)) {
            return null;
        }
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(this::mapValue)
                .toList();
    }

    private FormDesignerSchemaDTO readFormDesignerSchema(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                return objectMapper.readValue(text, FormDesignerSchemaDTO.class);
            }
            return objectMapper.convertValue(value, FormDesignerSchemaDTO.class);
        } catch (Exception ignored) {
            return null;
        }
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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

