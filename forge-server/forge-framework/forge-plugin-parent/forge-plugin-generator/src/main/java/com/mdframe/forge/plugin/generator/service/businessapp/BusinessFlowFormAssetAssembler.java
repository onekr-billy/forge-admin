package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.buildFieldPreview;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNullableBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType;

/**
 * 业务表单资产组装器。
 * <p>
 * 使用 Assembler 统一对象设计器、运行配置和字段注册表三类资产的收集与回退，
 * 运行配置查询和审批权限仍由调用方负责。
 */
@Slf4j
final class BusinessFlowFormAssetAssembler {

    private final BusinessFieldDesignService businessFieldDesignService;
    private final RuntimeLayoutCompiler layoutCompiler;
    private final RuntimeChildFieldAppender childFieldAppender;

    BusinessFlowFormAssetAssembler(BusinessFieldDesignService businessFieldDesignService,
                                   RuntimeLayoutCompiler layoutCompiler,
                                   RuntimeChildFieldAppender childFieldAppender) {
        this.businessFieldDesignService = businessFieldDesignService;
        this.layoutCompiler = layoutCompiler;
        this.childFieldAppender = childFieldAppender;
    }

    List<Map<String, Object>> collectBusinessFormAssets(BusinessObjectVO object, JSONObject formSchema) {
        if (object == null || formSchema == null || formSchema.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendBusinessFormAsset(result, seen, object, formSchema, "default");

        JSONArray forms = readNestedArray(formSchema.get("forms"));
        for (int i = 0; i < forms.size(); i++) {
            JSONObject form = forms.getJSONObject(i);
            JSONObject schema = readNestedObject(form.get("schema"));
            appendBusinessFormAsset(result, seen, object, schema.isEmpty() ? form : schema, "form");
        }

        JSONObject settings = readNestedObject(formSchema.get("settings"));
        JSONArray formAssets = readNestedArray(settings.get("formAssets"));
        for (int i = 0; i < formAssets.size(); i++) {
            JSONObject asset = formAssets.getJSONObject(i);
            JSONObject schema = readNestedObject(asset.get("schema"));
            appendBusinessFormAsset(result, seen, object, schema.isEmpty() ? asset : schema, "asset");
        }
        return result;
    }

    List<Map<String, Object>> collectRuntimeCrudFormAssets(BusinessObjectVO object, AiCrudConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return List.of();
        }
        List<Map<String, Object>> designerAssets = collectRuntimeDesignerFormAssets(
                object, runtimeConfig, readRuntimeCrudFormDesignerSchema(runtimeConfig));
        if (!designerAssets.isEmpty()) {
            return designerAssets;
        }
        List<Map<String, Object>> fieldCatalog = collectRuntimeCrudFormFieldCatalog(runtimeConfig);
        if (fieldCatalog.isEmpty()) {
            return List.of();
        }
        String formKey = resolveRuntimeCrudFormKey(object, runtimeConfig);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "BUSINESS_OBJECT_FORM");
        item.put("formMode", "BUSINESS_OBJECT_FORM");
        item.put("objectCode", resolveRuntimeCrudObjectCode(object, runtimeConfig));
        item.put("objectName", resolveRuntimeCrudObjectName(object, runtimeConfig));
        item.put("configKey", runtimeConfig.getConfigKey());
        item.put("formKey", formKey);
        item.put("formName", resolveRuntimeCrudFormName(object, runtimeConfig));
        item.put("viewKey", "default");
        item.put("source", "runtimeCrud");
        item.put("sourceType", "businessObjectRuntime");
        item.put("fieldCatalog", fieldCatalog);
        item.put("fields", fieldCatalog);
        item.put("fieldCount", fieldCatalog.size());
        item.put("fieldPreview", buildFieldPreview(fieldCatalog));
        item.put("supportsSave", true);
        return List.of(item);
    }

    List<Map<String, Object>> collectObjectFieldRegistryFormAssets(BusinessObjectVO object) {
        if (object == null || object.getId() == null || StringUtils.isBlank(object.getObjectCode())) {
            return List.of();
        }
        List<Map<String, Object>> fieldCatalog;
        try {
            fieldCatalog = normalizeRuntimeCrudFormFields(readMapList(readNestedArray(
                    businessFieldDesignService.listFields(object.getId()))));
        } catch (Exception e) {
            log.warn("读取业务对象字段目录失败: objectId={}, objectCode={}, error={}",
                    object.getId(), object.getObjectCode(), e.getMessage());
            return List.of();
        }
        if (fieldCatalog.isEmpty()) {
            return List.of();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "BUSINESS_OBJECT_FORM");
        item.put("formMode", "BUSINESS_OBJECT_FORM");
        item.put("objectCode", object.getObjectCode());
        item.put("objectName", object.getObjectName());
        item.put("configKey", object.getConfigKey());
        item.put("formKey", object.getObjectCode());
        item.put("formName", StringUtils.defaultIfBlank(object.getObjectName(), object.getObjectCode()) + "表单");
        item.put("viewKey", "default");
        item.put("source", "objectFieldRegistry");
        item.put("sourceType", "businessObjectFieldRegistry");
        item.put("fieldCatalog", fieldCatalog);
        item.put("fields", fieldCatalog);
        item.put("fieldCount", fieldCatalog.size());
        item.put("fieldPreview", buildFieldPreview(fieldCatalog));
        item.put("supportsSave", true);
        return List.of(item);
    }

    void appendObjectFieldRegistryFallback(List<Map<String, Object>> assets, BusinessObjectVO object) {
        if (assets == null || assets.stream().anyMatch(this::hasFormAssetFields)) {
            return;
        }
        List<Map<String, Object>> fallback = collectObjectFieldRegistryFormAssets(object);
        if (fallback.isEmpty()) {
            return;
        }
        if (!assets.isEmpty()) {
            Map<String, Object> existing = assets.get(0);
            Map<String, Object> generated = fallback.get(0);
            String mode = StringUtils.defaultIfBlank(textValue(existing.get("formMode")), textValue(existing.get("type")));
            String formKey = StringUtils.trimToNull(textValue(existing.get("formKey")));
            if ("BUSINESS_OBJECT_FORM".equalsIgnoreCase(mode) && formKey != null) {
                generated.put("formKey", formKey);
                generated.put("formName", StringUtils.defaultIfBlank(textValue(existing.get("formName")),
                        textValue(generated.get("formName"))));
                generated.put("providerKey", existing.get("providerKey"));
            }
        }
        appendUniqueFormAssets(assets, fallback);
    }

    private boolean hasFormAssetFields(Map<String, Object> asset) {
        if (asset == null) {
            return false;
        }
        List<Map<String, Object>> fields = readMapList(readNestedArray(asset.get("fieldCatalog")));
        if (fields.isEmpty()) {
            fields = readMapList(readNestedArray(asset.get("fields")));
        }
        return !fields.isEmpty();
    }

    private List<Map<String, Object>> collectRuntimeDesignerFormAssets(BusinessObjectVO object,
                                                                       AiCrudConfig runtimeConfig,
                                                                       JSONObject formSchema) {
        if (runtimeConfig == null || formSchema == null || formSchema.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendRuntimeDesignerFormAsset(result, seen, object, runtimeConfig, formSchema, "runtimeDesignerDefault");

        JSONArray forms = readNestedArray(formSchema.get("forms"));
        for (int i = 0; i < forms.size(); i++) {
            JSONObject form = forms.getJSONObject(i);
            JSONObject schema = readNestedObject(form.get("schema"));
            appendRuntimeDesignerFormAsset(result, seen, object, runtimeConfig, schema.isEmpty() ? form : schema,
                    "runtimeDesignerForm");
        }

        JSONObject settings = readNestedObject(formSchema.get("settings"));
        JSONArray formAssets = readNestedArray(settings.get("formAssets"));
        for (int i = 0; i < formAssets.size(); i++) {
            JSONObject asset = formAssets.getJSONObject(i);
            JSONObject schema = readNestedObject(asset.get("schema"));
            appendRuntimeDesignerFormAsset(result, seen, object, runtimeConfig, schema.isEmpty() ? asset : schema,
                    "runtimeDesignerAsset");
        }
        return result;
    }

    private void appendRuntimeDesignerFormAsset(List<Map<String, Object>> result,
                                                Set<String> seen,
                                                BusinessObjectVO object,
                                                AiCrudConfig runtimeConfig,
                                                JSONObject schema,
                                                String source) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        String formKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(schema.getString("formKey")),
                StringUtils.trimToNull(schema.getString("defaultFormKey")),
                resolveRuntimeCrudFormKey(object, runtimeConfig));
        if (formKey == null || !seen.add(formKey)) {
            return;
        }
        List<Map<String, Object>> fieldCatalog = collectBusinessFormFieldCatalog(schema);
        if (fieldCatalog.isEmpty()) {
            fieldCatalog = collectRuntimeCrudFormFieldCatalog(runtimeConfig);
        } else if (runtimeConfig != null) {
            fieldCatalog = new ArrayList<>(fieldCatalog);
            childFieldAppender.append(readJsonObject(runtimeConfig.getOptions()), fieldCatalog);
        }
        if (fieldCatalog.isEmpty()) {
            return;
        }
        String objectName = resolveRuntimeCrudObjectName(object, runtimeConfig);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "BUSINESS_OBJECT_FORM");
        item.put("formMode", "BUSINESS_OBJECT_FORM");
        item.put("objectCode", resolveRuntimeCrudObjectCode(object, runtimeConfig));
        item.put("objectName", objectName);
        item.put("configKey", runtimeConfig.getConfigKey());
        item.put("formKey", formKey);
        item.put("formName", StringUtils.firstNonBlank(
                StringUtils.trimToNull(schema.getString("formName")),
                StringUtils.isBlank(objectName) ? null : objectName + "表单",
                formKey));
        item.put("viewKey", "default");
        item.put("source", source);
        item.put("sourceType", "businessObjectRuntime");
        item.put("fieldCatalog", fieldCatalog);
        item.put("fields", fieldCatalog);
        item.put("fieldCount", fieldCatalog.size());
        item.put("fieldPreview", buildFieldPreview(fieldCatalog));
        item.put("supportsSave", true);
        result.add(item);
    }

    void appendUniqueFormAssets(List<Map<String, Object>> target, List<Map<String, Object>> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int index = 0; index < target.size(); index++) {
            String key = formAssetIdentity(target.get(index));
            if (StringUtils.isNotBlank(key)) {
                positions.putIfAbsent(key, index);
            }
        }
        for (Map<String, Object> asset : source) {
            String key = formAssetIdentity(asset);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Integer existingIndex = positions.get(key);
            if (existingIndex == null) {
                positions.put(key, target.size());
                target.add(asset);
            } else {
                mergeFormAssetMetadata(target.get(existingIndex), asset);
            }
        }
    }

    private void mergeFormAssetMetadata(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) {
            return;
        }
        List<Map<String, Object>> targetFields = readMapList(readNestedArray(target.get("fieldCatalog")));
        if (targetFields.isEmpty()) {
            targetFields = readMapList(readNestedArray(target.get("fields")));
        }
        List<Map<String, Object>> sourceFields = readMapList(readNestedArray(source.get("fieldCatalog")));
        if (sourceFields.isEmpty()) {
            sourceFields = readMapList(readNestedArray(source.get("fields")));
        }
        if (sourceFields.size() > targetFields.size()) {
            List<Map<String, Object>> mergedFields = new ArrayList<>(sourceFields);
            target.put("fieldCatalog", mergedFields);
            target.put("fields", mergedFields);
            target.put("fieldCount", mergedFields.size());
            target.put("fieldPreview", buildFieldPreview(mergedFields));
        }
        for (String key : List.of("configKey", "objectCode", "objectName", "viewKey",
                "providerKey", "providerName", "formUrl")) {
            if (StringUtils.isBlank(textValue(target.get(key))) && StringUtils.isNotBlank(textValue(source.get(key)))) {
                target.put(key, source.get(key));
            }
        }
        if (!Boolean.TRUE.equals(readNullableBooleanValue(target.get("supportsSave")))
                && Boolean.TRUE.equals(readNullableBooleanValue(source.get("supportsSave")))) {
            target.put("supportsSave", true);
        }
    }

    private String formAssetIdentity(Map<String, Object> asset) {
        if (asset == null) {
            return "";
        }
        String formKey = StringUtils.trimToNull(textValue(asset.get("formKey")));
        if (formKey == null) {
            return "";
        }
        return StringUtils.defaultIfBlank(textValue(asset.get("formMode")), textValue(asset.get("type")))
                + "::" + StringUtils.defaultString(textValue(asset.get("providerKey")))
                + "::" + formKey;
    }

    JSONObject buildRuntimeCrudFormSchema(BusinessObjectVO object, AiCrudConfig runtimeConfig, String requestedFormKey) {
        if (runtimeConfig == null) {
            return new JSONObject();
        }
        JSONObject designerSchema = resolveRuntimeDesignerFormSchema(
                readRuntimeCrudFormDesignerSchema(runtimeConfig), requestedFormKey);
        if (!designerSchema.isEmpty()) {
            return designerSchema;
        }
        String formKey = resolveRuntimeCrudFormKey(object, runtimeConfig);
        if (!matchesRuntimeCrudFormKey(requestedFormKey, formKey, runtimeConfig)) {
            return new JSONObject();
        }
        List<Map<String, Object>> fieldCatalog = collectRuntimeCrudFormFieldCatalog(runtimeConfig);
        if (fieldCatalog.isEmpty()) {
            return new JSONObject();
        }
        JSONObject schema = new JSONObject();
        schema.put("schemaVersion", "runtime-crud");
        schema.put("formKey", formKey);
        schema.put("defaultFormKey", formKey);
        schema.put("formName", resolveRuntimeCrudFormName(object, runtimeConfig));
        schema.put("objectCode", resolveRuntimeCrudObjectCode(object, runtimeConfig));
        schema.put("objectName", resolveRuntimeCrudObjectName(object, runtimeConfig));

        JSONObject settings = new JSONObject();
        JSONObject layout = new JSONObject();
        JSONObject runtimeOptions = readJsonObject(runtimeConfig.getOptions());
        layout.put("gridColumns", Math.max(1, integerValue(runtimeOptions.get("editGridCols"), 2)));
        layout.put("labelPlacement", StringUtils.defaultIfBlank(textValue(runtimeOptions.get("editLabelPlacement")), "left"));
        layout.put("labelWidth", StringUtils.defaultIfBlank(textValue(runtimeOptions.get("editLabelWidth")), "100"));
        settings.put("layout", layout);
        schema.put("settings", settings);

        JSONArray components = new JSONArray();
        for (Map<String, Object> field : fieldCatalog) {
            components.add(toRuntimeCrudFormComponent(field));
        }
        schema.put("components", components);
        return schema;
    }

    private JSONObject resolveRuntimeDesignerFormSchema(JSONObject formSchema, String formKey) {
        if (formSchema == null || formSchema.isEmpty()) {
            return new JSONObject();
        }
        String targetFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formKey),
                StringUtils.trimToNull(formSchema.getString("defaultFormKey")),
                StringUtils.trimToNull(formSchema.getString("formKey")));

        JSONObject byForms = findFormSchemaInArray(readNestedArray(formSchema.get("forms")), targetFormKey);
        if (!byForms.isEmpty()) {
            return byForms;
        }
        JSONObject settings = readNestedObject(formSchema.get("settings"));
        JSONObject byAssets = findFormSchemaInArray(readNestedArray(settings.get("formAssets")), targetFormKey);
        if (!byAssets.isEmpty()) {
            return byAssets;
        }
        String rootFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formSchema.getString("formKey")),
                StringUtils.trimToNull(formSchema.getString("defaultFormKey")));
        if (StringUtils.isBlank(targetFormKey) || StringUtils.equals(targetFormKey, rootFormKey)) {
            return formSchema;
        }
        return new JSONObject();
    }

    private boolean matchesRuntimeCrudFormKey(String requestedFormKey, String runtimeFormKey, AiCrudConfig runtimeConfig) {
        String requested = StringUtils.trimToNull(requestedFormKey);
        if (requested == null) {
            return true;
        }
        return StringUtils.equals(requested, runtimeFormKey)
                || StringUtils.equals(requested, runtimeConfig.getConfigKey())
                || StringUtils.equals(requested, runtimeConfig.getObjectCode());
    }

    JSONObject readRuntimeCrudFormDesignerSchema(AiCrudConfig runtimeConfig) {
        JSONObject options = readJsonObject(runtimeConfig == null ? null : runtimeConfig.getOptions());
        return readNestedObject(options.get("formDesignerSchema"));
    }

    String resolveRuntimeCrudFormKey(BusinessObjectVO object, AiCrudConfig runtimeConfig) {
        JSONObject options = readJsonObject(runtimeConfig == null ? null : runtimeConfig.getOptions());
        JSONObject designerSchema = readNestedObject(options.get("formDesignerSchema"));
        String objectCode = resolveRuntimeCrudObjectCode(object, runtimeConfig);
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(designerSchema.getString("defaultFormKey")),
                StringUtils.trimToNull(designerSchema.getString("formKey")),
                StringUtils.trimToNull(textValue(options.get("defaultFormKey"))),
                StringUtils.trimToNull(textValue(options.get("formKey"))),
                StringUtils.isBlank(objectCode) ? null : objectCode + "_default_form",
                runtimeConfig == null ? null : runtimeConfig.getConfigKey());
    }

    String resolveRuntimeCrudFormName(BusinessObjectVO object, AiCrudConfig runtimeConfig) {
        JSONObject options = readJsonObject(runtimeConfig == null ? null : runtimeConfig.getOptions());
        JSONObject designerSchema = readNestedObject(options.get("formDesignerSchema"));
        String objectName = resolveRuntimeCrudObjectName(object, runtimeConfig);
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(designerSchema.getString("formName")),
                StringUtils.trimToNull(textValue(options.get("formName"))),
                StringUtils.isBlank(objectName) ? null : objectName + "表单",
                runtimeConfig == null ? null : runtimeConfig.getAppName(),
                resolveRuntimeCrudFormKey(object, runtimeConfig));
    }

    String resolveRuntimeCrudObjectCode(BusinessObjectVO object, AiCrudConfig runtimeConfig) {
        return StringUtils.firstNonBlank(
                object == null ? null : StringUtils.trimToNull(object.getObjectCode()),
                runtimeConfig == null ? null : StringUtils.trimToNull(runtimeConfig.getObjectCode()),
                runtimeConfig == null ? null : StringUtils.trimToNull(runtimeConfig.getConfigKey()));
    }

    String resolveRuntimeCrudObjectName(BusinessObjectVO object, AiCrudConfig runtimeConfig) {
        return StringUtils.firstNonBlank(
                object == null ? null : StringUtils.trimToNull(object.getObjectName()),
                runtimeConfig == null ? null : StringUtils.trimToNull(runtimeConfig.getObjectName()),
                runtimeConfig == null ? null : StringUtils.trimToNull(runtimeConfig.getAppName()),
                resolveRuntimeCrudObjectCode(object, runtimeConfig));
    }

    private List<Map<String, Object>> collectRuntimeCrudFormFieldCatalog(AiCrudConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return List.of();
        }
        JSONObject options = readJsonObject(runtimeConfig.getOptions());
        List<Map<String, Object>> fields = readMapList(readNestedArray(runtimeConfig.getEditSchema()));
        if (!fields.isEmpty()) {
            List<Map<String, Object>> layoutFields = layoutCompiler.apply(
                    fields, readNestedArray(options.get("editFormLayout")));
            List<Map<String, Object>> result = new ArrayList<>(normalizeRuntimeCrudFormFields(
                    layoutFields.isEmpty() ? fields : layoutFields));
            childFieldAppender.append(options, result);
            return result;
        }
        JSONObject modelSchema = readJsonObject(runtimeConfig.getModelSchema());
        List<Map<String, Object>> result = new ArrayList<>(normalizeRuntimeCrudFormFields(
                readMapList(readNestedArray(modelSchema.get("fields")))));
        childFieldAppender.append(options, result);
        return result;
    }

    List<Map<String, Object>> normalizeRuntimeCrudFormFields(List<Map<String, Object>> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> field : fields) {
            Map<String, Object> item = normalizeRuntimeCrudFormField(field);
            String fieldCode = item == null ? null : StringUtils.trimToNull(textValue(item.get("field")));
            if (fieldCode != null && seen.add(fieldCode)) {
                result.add(item);
            }
        }
        return result;
    }

    Map<String, Object> normalizeRuntimeCrudFormField(Map<String, Object> field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        String fieldCode = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("field"))),
                StringUtils.trimToNull(textValue(field.get("fieldCode"))),
                StringUtils.trimToNull(textValue(field.get("prop"))),
                StringUtils.trimToNull(textValue(field.get("name"))),
                StringUtils.trimToNull(textValue(field.get("model"))));
        if (fieldCode == null
                || readBooleanValue(field.get("systemField"), false)
                || readBooleanValue(field.get("internal"), false)
                || (field.containsKey("formVisible") && !readBooleanValue(field.get("formVisible"), true))) {
            return null;
        }
        JSONObject props = readNestedObject(field.get("props"));
        Map<String, Object> item = new LinkedHashMap<>(field);
        item.put("field", fieldCode);
        item.put("fieldCode", fieldCode);
        item.put("label", StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("label"))),
                StringUtils.trimToNull(textValue(field.get("title"))),
                StringUtils.trimToNull(textValue(field.get("fieldName"))),
                fieldCode));
        String componentType = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("componentType"))),
                StringUtils.trimToNull(textValue(field.get("type"))),
                StringUtils.trimToNull(textValue(field.get("componentKey"))),
                "input");
        item.put("type", normalizeTaskFormFieldType(componentType));
        item.put("componentType", componentType);
        if (field.get("dataType") != null) {
            item.put("dataType", textValue(field.get("dataType")));
        }
        String dictType = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("dictType"))),
                StringUtils.trimToNull(props.getString("dictType")));
        if (dictType != null) {
            item.put("dictType", dictType);
        }
        item.put("required", readBooleanValue(field.get("required"), false));
        item.putIfAbsent("readable", true);
        item.putIfAbsent("writable", !readBooleanValue(field.get("readonly"), false));
        return item;
    }

    JSONObject toRuntimeCrudFormComponent(Map<String, Object> field) {
        JSONObject component = new JSONObject();
        String fieldCode = StringUtils.trimToEmpty(textValue(field.get("field")));
        String componentType = StringUtils.defaultIfBlank(textValue(field.get("componentType")), "input");
        component.put("id", fieldCode);
        component.put("key", fieldCode);
        component.put("type", componentType);
        component.put("componentType", componentType);
        component.put("label", StringUtils.defaultIfBlank(textValue(field.get("label")), fieldCode));
        component.put("field", fieldCode);

        JSONObject binding = new JSONObject();
        binding.put("mode", "field");
        binding.put("fieldCode", fieldCode);
        binding.put("dataType", StringUtils.trimToEmpty(textValue(field.get("dataType"))));
        component.put("fieldBinding", binding);

        JSONObject props = readNestedObject(field.get("props"));
        props.put("field", fieldCode);
        props.put("label", component.getString("label"));
        if (field.get("dictType") != null) {
            props.put("dictType", field.get("dictType"));
        }
        component.put("props", props);

        JSONObject validation = new JSONObject();
        validation.put("required", readBooleanValue(field.get("required"), false));
        component.put("validation", validation);
        return component;
    }

    private void appendBusinessFormAsset(List<Map<String, Object>> result,
                                         Set<String> seen,
                                         BusinessObjectVO object,
                                         JSONObject schema,
                                         String source) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        String formKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(schema.getString("formKey")),
                StringUtils.trimToNull(schema.getString("defaultFormKey")));
        if (formKey == null || !seen.add(formKey)) {
            return;
        }
        String formName = StringUtils.defaultIfBlank(schema.getString("formName"), object.getObjectName() + "表单");
        List<Map<String, Object>> fieldCatalog = collectBusinessFormFieldCatalog(schema);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "BUSINESS_OBJECT_FORM");
        item.put("formMode", "BUSINESS_OBJECT_FORM");
        item.put("objectCode", object.getObjectCode());
        item.put("objectName", object.getObjectName());
        item.put("formKey", formKey);
        item.put("formName", formName);
        item.put("viewKey", "default");
        item.put("source", source);
        item.put("sourceType", "businessObject");
        item.put("fieldCatalog", fieldCatalog);
        item.put("fields", fieldCatalog);
        item.put("fieldCount", fieldCatalog.size());
        item.put("fieldPreview", buildFieldPreview(fieldCatalog));
        item.put("supportsSave", true);
        result.add(item);
    }


    JSONObject findFormSchemaInArray(JSONArray forms, String formKey) {
        if (forms == null || forms.isEmpty() || StringUtils.isBlank(formKey)) {
            return new JSONObject();
        }
        for (int i = 0; i < forms.size(); i++) {
            JSONObject form = forms.getJSONObject(i);
            if (form == null) {
                continue;
            }
            JSONObject schema = readNestedObject(form.get("schema"));
            JSONObject candidate = schema.isEmpty() ? form : schema;
            String candidateKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(form.getString("formKey")),
                    StringUtils.trimToNull(candidate.getString("formKey")),
                    StringUtils.trimToNull(candidate.getString("defaultFormKey")));
            if (StringUtils.equals(formKey, candidateKey)) {
                return candidate;
            }
        }
        return new JSONObject();
    }

    private JSONObject readJsonObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private Integer integerValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @FunctionalInterface
    interface RuntimeLayoutCompiler {
        List<Map<String, Object>> apply(List<Map<String, Object>> fields, JSONArray layout);
    }

    @FunctionalInterface
    interface RuntimeChildFieldAppender {
        void append(JSONObject options, List<Map<String, Object>> fields);
    }
}
