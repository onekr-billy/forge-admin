package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.resolveTaskFormControlType;

/**
 * 审批任务表单 schema 与布局装配器。
 * <p>
 * 统一设计器字段、发布态 editSchema、运行时布局和轻量表单资产的合并顺序，
 * 不处理节点权限、业务数据保存或流程状态。
 */
@Slf4j
final class BusinessFlowTaskFormSchemaAssembler {

    private final DynamicCrudService dynamicCrudService;
    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessFlowTaskChildPolicy childPolicy;
    private final Supplier<Long> tenantIdSupplier;

    BusinessFlowTaskFormSchemaAssembler(DynamicCrudService dynamicCrudService,
                                        BusinessRuntimeConfigResolver runtimeConfigResolver,
                                        BusinessFlowTaskChildPolicy childPolicy,
                                        Supplier<Long> tenantIdSupplier) {
        this.dynamicCrudService = dynamicCrudService;
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.childPolicy = childPolicy;
        this.tenantIdSupplier = tenantIdSupplier;
    }

    AiCrudConfig safeGetRuntimeConfig(String configKey) {
        if (StringUtils.isBlank(configKey)) {
            return null;
        }
        try {
            return dynamicCrudService.getRuntimeConfig(configKey);
        } catch (Exception e) {
            log.debug("读取运行配置失败: configKey={}, error={}", configKey, e.getMessage());
            return null;
        }
    }

    void applyPageFormIdentity(BusinessTaskFormContextVO vo, Map<String, Object> formRef) {
        if (vo == null || formRef == null || formRef.isEmpty()) {
            return;
        }
        vo.setApplicationId(StringUtils.trimToNull(textValue(formRef.get("applicationId"))));
        vo.setPageId(StringUtils.trimToNull(textValue(formRef.get("pageId"))));
        vo.setPageCode(StringUtils.trimToNull(textValue(formRef.get("pageCode"))));
        vo.setPageName(StringUtils.trimToNull(textValue(formRef.get("pageName"))));
    }

    void applyBusinessObjectFormLayout(BusinessTaskFormContextVO vo, JSONObject formSchema, String configKey) {
        applyBusinessObjectFormLayout(vo, formSchema, readRuntimeConfigOptions(configKey));
    }

    void applyBusinessObjectFormLayout(BusinessTaskFormContextVO vo,
                                               JSONObject formSchema,
                                               JSONObject runtimeOptions) {
        JSONObject settings = readNestedObject(formSchema == null ? null : formSchema.get("settings"));
        // 表单设计器布局在根 layout（画布与运行页都读这里），settings.layout 只是旧结构兜底
        JSONObject rootLayout = readNestedObject(formSchema == null ? null : formSchema.get("layout"));
        JSONObject layout = readNestedObject(settings.get("layout"));
        JSONObject options = runtimeOptions == null ? new JSONObject() : runtimeOptions;
        vo.setGridCols(Math.max(1, integerValue(
                firstNonNull(rootLayout.get("gridColumns"),
                        rootLayout.get("gridCols"),
                        layout.get("gridCols"),
                        layout.get("gridColumns"),
                        settings.get("gridCols"),
                        settings.get("gridColumns"),
                        options.get("editGridCols")),
                1)));
        vo.setLabelPlacement(StringUtils.defaultIfBlank(
                StringUtils.firstNonBlank(
                        textValue(rootLayout.get("labelPlacement")),
                        textValue(layout.get("labelPlacement")),
                        textValue(settings.get("labelPlacement")),
                        textValue(options.get("editLabelPlacement"))),
                "left"));
        vo.setLabelWidth(StringUtils.defaultIfBlank(
                StringUtils.firstNonBlank(
                        textValue(rootLayout.get("labelWidth")),
                        textValue(layout.get("labelWidth")),
                        textValue(settings.get("labelWidth")),
                        textValue(options.get("editLabelWidth"))),
                "100"));
        vo.setSize(StringUtils.defaultIfBlank(
                StringUtils.firstNonBlank(
                        textValue(rootLayout.get("size")),
                        textValue(layout.get("size")),
                        textValue(options.get("editSize"))),
                "medium"));
    }

    List<Map<String, Object>> resolveBusinessTaskCrudPageFields(String configKey,
                                                                        String formKey,
                                                                        JSONObject formSchema) {
        AiCrudConfig runtimeConfig = safeGetRuntimeConfig(configKey);
        JSONObject options = runtimeConfig == null ? new JSONObject() : readJsonObject(runtimeConfig.getOptions());
        return resolveBusinessTaskCrudPageFields(configKey, formKey, formSchema, runtimeConfig, options);
    }

    List<Map<String, Object>> resolveBusinessTaskCrudPageFields(String configKey,
                                                                        String formKey,
                                                                        JSONObject formSchema,
                                                                        AiCrudConfig runtimeConfig,
                                                                        JSONObject runtimeOptions) {
        // 审批渲染以当前表单设计器组件为准；对象字段资产里「未入当前表单」的字段不应出现在待办里
        List<Map<String, Object>> designerFields = new ArrayList<>(collectBusinessFormFieldCatalog(formSchema));
        JSONObject options = runtimeOptions == null ? new JSONObject() : runtimeOptions;
        if (!designerFields.isEmpty()) {
            List<Map<String, Object>> result = enrichTaskFormFieldsFromRuntime(
                    designerFields, runtimeConfig, formKey, formSchema, options);
            appendRuntimeChildFieldCatalog(options, result);
            if (runtimeConfig == null && StringUtils.isNotBlank(configKey)) {
                appendRuntimeChildFieldCatalog(configKey, result);
            }
            return result;
        }
        if (runtimeConfig == null || StringUtils.isBlank(configKey)) {
            List<Map<String, Object>> fallback = new ArrayList<>();
            appendRuntimeChildFieldCatalog(runtimeOptions, fallback);
            if (runtimeConfig == null && StringUtils.isNotBlank(configKey)) {
                appendRuntimeChildFieldCatalog(configKey, fallback);
            }
            return fallback;
        }
        try {
            if (!shouldUseCrudPageDefaultFormSchema(formKey, formSchema, options)) {
                return designerFields;
            }
            List<Map<String, Object>> runtimeFields = readMapList(readNestedArray(runtimeConfig.getEditSchema()));
            if (runtimeFields.isEmpty()) {
                return designerFields;
            }
            List<Map<String, Object>> layoutFields = applyRuntimeCrudFormLayout(
                    runtimeFields, readNestedArray(options.get("editFormLayout")));
            List<Map<String, Object>> result = new ArrayList<>(layoutFields.isEmpty() ? runtimeFields : layoutFields);
            appendRuntimeChildFieldCatalog(options, result);
            return result;
        } catch (Exception e) {
            log.debug("读取动态 CRUD 详情表单 schema 失败: configKey={}, error={}", configKey, e.getMessage());
            return designerFields;
        }
    }

    /**
     * 用发布态 editSchema 补齐设计器字段的类型/字典等元数据，但不引入「未入表单」的额外字段。
     * 组件类型与 props（optionSource / 人员组织选择器等）以设计器为准，禁止被 editSchema 的 input 覆盖。
     */
    private List<Map<String, Object>> enrichTaskFormFieldsFromRuntime(List<Map<String, Object>> designerFields,
                                                                      AiCrudConfig runtimeConfig,
                                                                      String formKey,
                                                                      JSONObject formSchema,
                                                                      JSONObject options) {
        if (designerFields == null || designerFields.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> field : designerFields) {
            if (field != null) {
                result.add(new LinkedHashMap<>(field));
            }
        }
        if (runtimeConfig == null || !shouldUseCrudPageDefaultFormSchema(formKey, formSchema, options)) {
            result.forEach(this::normalizeTaskFormControlIdentity);
            return result;
        }
        Map<String, Map<String, Object>> runtimeByField = new LinkedHashMap<>();
        for (Map<String, Object> field : readMapList(readNestedArray(runtimeConfig.getEditSchema()))) {
            String code = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("field"))),
                    StringUtils.trimToNull(textValue(field.get("fieldCode"))));
            if (code != null) {
                runtimeByField.putIfAbsent(code, field);
            }
        }
        if (runtimeByField.isEmpty()) {
            result.forEach(this::normalizeTaskFormControlIdentity);
            return result;
        }
        for (Map<String, Object> field : result) {
            String code = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("field"))),
                    StringUtils.trimToNull(textValue(field.get("fieldCode"))));
            Map<String, Object> runtimeField = code == null ? null : runtimeByField.get(code);
            if (runtimeField != null) {
                enrichDesignerFieldFromRuntime(field, runtimeField);
                field.put("field", code);
                field.put("fieldCode", code);
            }
            normalizeTaskFormControlIdentity(field);
        }
        return result;
    }

    /**
     * 发布态只补缺：字典/数据类型等；控件身份（componentKey/type/props）保留设计器。
     */
    private void enrichDesignerFieldFromRuntime(Map<String, Object> designerField, Map<String, Object> runtimeField) {
        if (designerField == null || runtimeField == null) {
            return;
        }
        Set<String> protectedKeys = Set.of(
                "type", "componentType", "componentKey", "props", "basicProps",
                "optionSource", "fieldMappings", "mappings", "recordSelector",
                "label", "field", "fieldCode", "required");
        runtimeField.forEach((key, value) -> {
            if (value == null || protectedKeys.contains(key)) {
                return;
            }
            Object current = designerField.get(key);
            if (current == null || (current instanceof String text && StringUtils.isBlank(text))) {
                designerField.put(key, value);
            }
        });
        Map<String, Object> designerProps = new LinkedHashMap<>(readNestedObject(designerField.get("props")));
        Map<String, Object> runtimeProps = new LinkedHashMap<>(readNestedObject(runtimeField.get("props")));
        // 运行态补缺 props，设计器同名键覆盖
        Map<String, Object> mergedProps = new LinkedHashMap<>(runtimeProps);
        mergedProps.putAll(designerProps);
        if (!mergedProps.isEmpty()) {
            designerField.put("props", mergedProps);
        }
        if (designerField.get("dictType") == null && runtimeField.get("dictType") != null) {
            designerField.put("dictType", runtimeField.get("dictType"));
        }
        if (designerField.get("dataType") == null && runtimeField.get("dataType") != null) {
            designerField.put("dataType", runtimeField.get("dataType"));
        }
    }

    private Map<String, Object> normalizeTaskFormControlIdentity(Map<String, Object> field) {
        if (field == null) {
            return null;
        }
        String controlType = resolveTaskFormControlType(field);
        String normalized = normalizeTaskFormFieldType(controlType);
        field.put("type", normalized);
        field.put("componentType", StringUtils.defaultIfBlank(controlType, normalized));
        return field;
    }

    void appendRuntimeChildFieldCatalog(String configKey, List<Map<String, Object>> fields) {
        if (StringUtils.isBlank(configKey) || fields == null) {
            return;
        }
        try {
            AiCrudConfig runtimeConfig = runtimeConfigResolver.runtime(tenantIdSupplier.get(), configKey);
            if (runtimeConfig == null) {
                runtimeConfig = runtimeConfigResolver.published(tenantIdSupplier.get(), configKey);
            }
            appendRuntimeChildFieldCatalog(readJsonObject(runtimeConfig == null ? null : runtimeConfig.getOptions()), fields);
        } catch (Exception e) {
            log.debug("读取待办子表字段目录失败: configKey={}, error={}", configKey, e.getMessage());
        }
    }

    void appendRuntimeChildFieldCatalog(JSONObject options, List<Map<String, Object>> fields) {
        if (options == null || fields == null) {
            return;
        }
        Set<String> seen = fields.stream()
                .map(field -> StringUtils.firstNonBlank(textValue(field.get("field")), textValue(field.get("fieldCode"))))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        JSONObject masterDetailConfig = readNestedObject(options.get("masterDetailConfig"));
        for (Map<String, Object> child : readMapList(readNestedArray(masterDetailConfig.get("children")))) {
            String childKey = childPolicy.resolveChildKey(child);
            for (Map<String, Object> rawField : readMapList(readNestedArray(child.get("fields")))) {
                Map<String, Object> field = normalizeRuntimeCrudFormField(rawField);
                if (field == null) {
                    continue;
                }
                String childField = StringUtils.firstNonBlank(
                        textValue(field.get("field")), textValue(field.get("fieldCode")));
                String permissionField = childKey + "." + childField;
                if (StringUtils.isBlank(childKey) || StringUtils.isBlank(childField) || !seen.add(permissionField)) {
                    continue;
                }
                field.put("scope", "child");
                field.put("childKey", childKey);
                field.put("childField", childField);
                field.put("childLabel", StringUtils.firstNonBlank(
                        textValue(child.get("label")),
                        textValue(child.get("modelName")),
                        textValue(child.get("relationName")),
                        childKey));
                field.put("relationName", StringUtils.firstNonBlank(
                        textValue(child.get("relationName")),
                        textValue(child.get("modelName")),
                        childKey));
                field.put("field", childField);
                field.put("fieldCode", childField);
                field.put("label", StringUtils.defaultIfBlank(textValue(rawField.get("label")), childField));
                fields.add(field);
            }
        }
    }

    private boolean shouldUseCrudPageDefaultFormSchema(String formKey, JSONObject formSchema, JSONObject options) {
        String requestedKey = StringUtils.trimToNull(formKey);
        if (requestedKey == null) {
            return true;
        }
        String currentFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formSchema == null ? null : formSchema.getString("formKey")),
                StringUtils.trimToNull(formSchema == null ? null : formSchema.getString("defaultFormKey")));
        JSONObject designerSchema = readNestedObject(options == null ? null : options.get("formDesignerSchema"));
        String defaultFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(designerSchema.getString("defaultFormKey")),
                StringUtils.trimToNull(designerSchema.getString("formKey")),
                currentFormKey);
        return StringUtils.isBlank(defaultFormKey) || StringUtils.equals(requestedKey, defaultFormKey);
    }

    List<Map<String, Object>> applyRuntimeCrudFormLayout(List<Map<String, Object>> fields, JSONArray layout) {
        if (fields == null || fields.isEmpty() || layout == null || layout.isEmpty()) {
            return fields == null ? List.of() : fields;
        }
        Map<String, Map<String, Object>> fieldMap = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            String fieldCode = StringUtils.trimToNull(textValue(field.get("field")));
            if (fieldCode != null) {
                fieldMap.put(fieldCode, field);
            }
        }
        Set<String> usedFields = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> layoutNodes = readMapList(layout);
        for (Map<String, Object> node : layoutNodes) {
            Map<String, Object> hydrated = hydrateRuntimeCrudLayoutNode(node, fieldMap, usedFields);
            if (hydrated != null) {
                result.add(hydrated);
            }
        }
        for (Map<String, Object> field : fields) {
            String fieldCode = StringUtils.trimToNull(textValue(field.get("field")));
            if (fieldCode != null && !usedFields.contains(fieldCode)) {
                result.add(field);
            }
        }
        return result;
    }

    private Map<String, Object> hydrateRuntimeCrudLayoutNode(Map<String, Object> node,
                                                             Map<String, Map<String, Object>> fieldMap,
                                                             Set<String> usedFields) {
        if (node == null || node.isEmpty()) {
            return null;
        }
        String fieldCode = StringUtils.trimToNull(textValue(node.get("field")));
        String nodeType = resolveRuntimeCrudLayoutNodeType(node);
        if (fieldCode != null && ("field".equals(nodeType) || fieldMap.containsKey(fieldCode))) {
            Map<String, Object> field = fieldMap.get(fieldCode);
            if (field == null) {
                return null;
            }
            usedFields.add(fieldCode);
            Map<String, Object> item = new LinkedHashMap<>(field);
            item.put("nodeType", "field");
            item.put("key", StringUtils.defaultIfBlank(textValue(node.get("key")), fieldCode));
            if (node.get("span") != null) {
                item.put("span", node.get("span"));
            }
            if (node.get("gridStyle") != null) {
                item.put("gridStyle", node.get("gridStyle"));
            }
            return item;
        }

        List<Map<String, Object>> children = new ArrayList<>();
        for (Map<String, Object> child : readMapList(readNestedArray(node.get("children")))) {
            Map<String, Object> hydrated = hydrateRuntimeCrudLayoutNode(child, fieldMap, usedFields);
            if (hydrated != null) {
                children.add(hydrated);
            }
        }
        if (children.isEmpty() && !isStandaloneRuntimeCrudLayoutNode(node)) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<>(node);
        item.put("nodeType", nodeType);
        item.put("children", children);
        return item;
    }

    private String resolveRuntimeCrudLayoutNodeType(Map<String, Object> node) {
        String key = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(node.get("componentKey"))),
                StringUtils.trimToNull(textValue(node.get("type"))),
                StringUtils.trimToNull(textValue(node.get("nodeType"))));
        if (Set.of("title", "fcTitle", "sectionTitle", "groupTitle", "groupHeader",
                "GroupHeader", "titleBlock", "section").contains(key)) {
            return "groupTitle";
        }
        if (Set.of("divider", "elDivider", "AiFormSectionTitle", "aiFormSectionTitle",
                "formSectionTitle", "FormSectionTitle").contains(key)) {
            return "divider";
        }
        return StringUtils.defaultIfBlank(key, "layout");
    }

    private boolean isStandaloneRuntimeCrudLayoutNode(Map<String, Object> node) {
        String key = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(node.get("componentKey"))),
                StringUtils.trimToNull(textValue(node.get("type"))),
                StringUtils.trimToNull(textValue(node.get("nodeType"))));
        return Set.of("title", "fcTitle", "sectionTitle", "groupTitle", "groupHeader", "GroupHeader",
                "titleBlock", "section", "divider", "elDivider", "AiFormSectionTitle", "aiFormSectionTitle",
                "formSectionTitle", "FormSectionTitle", "button", "table", "tableGrid", "AiCrudPage",
                "aiCrudPage", "crud", "crudBlock").contains(key);
    }

    List<Map<String, Object>> resolveBusinessTaskFormAssets(JSONObject formSchema,
                                                                    String configKey,
                                                                    String activeFormKey) {
        AiCrudConfig runtimeConfig = safeGetRuntimeConfig(configKey);
        JSONObject options = runtimeConfig == null ? new JSONObject() : readJsonObject(runtimeConfig.getOptions());
        return resolveBusinessTaskFormAssets(formSchema, configKey, activeFormKey, options);
    }

    List<Map<String, Object>> resolveBusinessTaskFormAssets(JSONObject formSchema,
                                                                    String configKey,
                                                                    String activeFormKey,
                                                                    JSONObject runtimeOptions) {
        if (StringUtils.isBlank(configKey)) {
            return List.of();
        }
        try {
            JSONObject options = runtimeOptions == null ? new JSONObject() : runtimeOptions;
            List<Map<String, Object>> configuredAssets = readMapList(readNestedArray(options.get("formAssets")));
            if (!configuredAssets.isEmpty()) {
                // 审批端主表单已走 uiDocument；弹窗资产只保留元数据，避免整份设计器 schema 撑爆响应
                return slimTaskFormAssets(configuredAssets);
            }
            JSONObject designerSchema = readNestedObject(options.get("formDesignerSchema"));
            JSONArray forms = readNestedArray(designerSchema.get("forms"));
            if (forms.isEmpty()) {
                return List.of();
            }
            String currentKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(activeFormKey),
                    StringUtils.trimToNull(formSchema == null ? null : formSchema.getString("formKey")),
                    StringUtils.trimToNull(designerSchema.getString("defaultFormKey")));
            List<Map<String, Object>> assets = new ArrayList<>();
            for (int i = 0; i < forms.size(); i++) {
                JSONObject form = forms.getJSONObject(i);
                if (form == null) {
                    continue;
                }
                String itemKey = StringUtils.trimToNull(form.getString("formKey"));
                if (itemKey == null || StringUtils.equals(itemKey, currentKey)) {
                    continue;
                }
                Map<String, Object> asset = new LinkedHashMap<>();
                asset.put("formKey", itemKey);
                asset.put("formName", StringUtils.defaultIfBlank(form.getString("formName"), itemKey));
                asset.put("usage", readNestedArray(form.get("usage")));
                assets.add(asset);
            }
            return assets;
        } catch (Exception e) {
            log.debug("读取业务表单资产失败: configKey={}, error={}", configKey, e.getMessage());
            return List.of();
        }
    }

    /**
     * 审批上下文里 formAssets 只保留轻量索引；完整 schema 由 uiDocument / 设计器按需加载。
     */
    private List<Map<String, Object>> slimTaskFormAssets(List<Map<String, Object>> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> slim = new ArrayList<>(assets.size());
        for (Map<String, Object> asset : assets) {
            if (asset == null || asset.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            String formKey = StringUtils.firstNonBlank(
                    textValue(asset.get("formKey")),
                    textValue(asset.get("key")));
            if (formKey != null) {
                item.put("formKey", formKey);
            }
            item.put("formName", StringUtils.defaultIfBlank(textValue(asset.get("formName")), formKey));
            if (asset.get("usage") != null) {
                item.put("usage", asset.get("usage"));
            }
            if (asset.get("formMode") != null) {
                item.put("formMode", asset.get("formMode"));
            }
            slim.add(item);
        }
        return slim;
    }

    JSONObject readRuntimeConfigOptions(String configKey) {
        if (StringUtils.isBlank(configKey)) {
            return new JSONObject();
        }
        try {
            AiCrudConfig runtimeConfig = dynamicCrudService.getRuntimeConfig(configKey);
            return runtimeConfig == null ? new JSONObject() : readJsonObject(runtimeConfig.getOptions());
        } catch (Exception e) {
            log.debug("读取业务表单运行态布局失败: configKey={}, error={}", configKey, e.getMessage());
            return new JSONObject();
        }
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integerValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = StringUtils.trimToNull(String.valueOf(value));
        if (text == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    private Map<String, Object> normalizeRuntimeCrudFormField(Map<String, Object> field) {
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

    private JSONObject readJsonObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new JSONObject();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null ? new JSONObject() : object;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
