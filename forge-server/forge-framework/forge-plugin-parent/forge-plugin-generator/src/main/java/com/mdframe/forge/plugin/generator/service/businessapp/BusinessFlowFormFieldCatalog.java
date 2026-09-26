package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.resolveTaskFormControlType;

/** 从应用页和业务对象设计器 schema 提取审批表单字段目录。 */
final class BusinessFlowFormFieldCatalog {

    private BusinessFlowFormFieldCatalog() {
    }

    static List<Map<String, Object>> collectBusinessFormFieldCatalog(JSONObject schema) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectBusinessFormFieldComponents(readNestedArray(schema.get("components")), result, seen);
        appendSchemaChildTableFields(schema, result);
        if (result.isEmpty()) {
            JSONArray catalog = readNestedArray(schema.get("fieldCatalog"));
            if (catalog.isEmpty()) {
                catalog = readNestedArray(schema.get("fields"));
            }
            for (int i = 0; i < catalog.size(); i++) {
                JSONObject field = catalog.getJSONObject(i);
                if (field == null) {
                    continue;
                }
                String code = StringUtils.firstNonBlank(
                        StringUtils.trimToNull(field.getString("field")),
                        StringUtils.trimToNull(field.getString("fieldCode")),
                        StringUtils.trimToNull(readNestedObject(field.get("fieldBinding")).getString("fieldCode")));
                if (code == null || !seen.add(code)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>(field);
                item.put("field", code);
                item.put("fieldCode", code);
                item.put("label", StringUtils.firstNonBlank(
                        StringUtils.trimToNull(field.getString("label")),
                        StringUtils.trimToNull(field.getString("fieldName")), code));
                item.put("type", normalizeTaskFormFieldType(StringUtils.firstNonBlank(
                        StringUtils.trimToNull(field.getString("type")),
                        StringUtils.trimToNull(field.getString("componentType")), "input")));
                item.putIfAbsent("componentType", item.get("type"));
                result.add(item);
            }
        }
        return result;
    }

    static List<String> buildFieldPreview(List<Map<String, Object>> fields) {
        List<String> preview = new ArrayList<>();
        if (fields == null) {
            return preview;
        }
        for (Map<String, Object> field : fields) {
            String label = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("label"))),
                    StringUtils.trimToNull(textValue(field.get("fieldName"))),
                    StringUtils.trimToNull(textValue(field.get("field"))),
                    StringUtils.trimToNull(textValue(field.get("fieldCode"))));
            if (label != null) {
                preview.add(label);
            }
            if (preview.size() >= 5) {
                break;
            }
        }
        return preview;
    }

    static void appendSchemaChildTableFields(JSONObject schema, List<Map<String, Object>> fields) {
        if (schema == null || fields == null) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> field : fields) {
            String childKey = StringUtils.trimToNull(textValue(field.get("childKey")));
            String childField = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("childField"))),
                    "child".equalsIgnoreCase(textValue(field.get("scope")))
                            ? StringUtils.trimToNull(textValue(field.get("field"))) : null);
            if (childKey != null && childField != null) {
                seen.add(childKey + ":" + childField);
            }
        }
        appendSchemaChildTableComponents(readNestedArray(schema.get("components")), fields, seen);
    }

    private static void appendSchemaChildTableComponents(JSONArray components,
                                                         List<Map<String, Object>> fields,
                                                         Set<String> seen) {
        if (components == null || fields == null) {
            return;
        }
        for (int i = 0; i < components.size(); i++) {
            JSONObject component = components.getJSONObject(i);
            if (component == null) {
                continue;
            }
            String componentKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(component.getString("componentKey")),
                    StringUtils.trimToNull(component.getString("type")));
            JSONObject props = readNestedObject(component.get("props"));
            if ("subTable".equalsIgnoreCase(componentKey) || "childTable".equalsIgnoreCase(componentKey)) {
                String childKey = StringUtils.firstNonBlank(
                        StringUtils.trimToNull(props.getString("modelCode")),
                        StringUtils.trimToNull(props.getString("relationKey")),
                        StringUtils.trimToNull(component.getString("modelCode")),
                        StringUtils.trimToNull(component.getString("relationKey")));
                String childLabel = StringUtils.firstNonBlank(
                        StringUtils.trimToNull(props.getString("header")),
                        StringUtils.trimToNull(props.getString("relationName")),
                        StringUtils.trimToNull(component.getString("label")),
                        childKey);
                JSONArray columns = readNestedArray(props.get("columns"));
                if (columns.isEmpty()) {
                    columns = readNestedArray(props.get("fields"));
                }
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object rawColumn = columns.get(columnIndex);
                    JSONObject column = rawColumn instanceof JSONObject jsonColumn
                            ? jsonColumn
                            : rawColumn instanceof Map<?, ?> ? readNestedObject(rawColumn) : null;
                    String childField = column == null
                            ? StringUtils.trimToNull(textValue(rawColumn))
                            : StringUtils.firstNonBlank(
                                    StringUtils.trimToNull(column.getString("fieldCode")),
                                    StringUtils.trimToNull(column.getString("field")),
                                    StringUtils.trimToNull(column.getString("sourceField")));
                    if (childKey == null || childField == null || !seen.add(childKey + ":" + childField)) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("field", childField);
                    item.put("fieldCode", childField);
                    item.put("label", column == null
                            ? childField
                            : StringUtils.firstNonBlank(
                                    StringUtils.trimToNull(column.getString("fieldLabel")),
                                    StringUtils.trimToNull(column.getString("label")),
                                    childField));
                    item.put("scope", "child");
                    item.put("childKey", childKey);
                    item.put("childField", childField);
                    item.put("childLabel", childLabel);
                    item.put("relationName", StringUtils.defaultIfBlank(
                            StringUtils.trimToNull(props.getString("relationName")), childLabel));
                    fields.add(item);
                }
            }
            appendSchemaChildTableComponents(readNestedArray(component.get("children")), fields, seen);
        }
    }

    private static void collectBusinessFormFieldComponents(JSONArray components,
                                                           List<Map<String, Object>> result,
                                                           Set<String> seen) {
        if (components == null) {
            return;
        }
        for (int i = 0; i < components.size(); i++) {
            JSONObject component = components.getJSONObject(i);
            if (component == null) {
                continue;
            }
            JSONObject binding = readNestedObject(component.get("fieldBinding"));
            JSONObject props = readNestedObject(component.get("props"));
            String field = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(binding.getString("fieldCode")),
                    StringUtils.trimToNull(component.getString("field")),
                    StringUtils.trimToNull(props.getString("field")));
            if (field != null && seen.add(field)) {
                JSONObject validation = readNestedObject(component.get("validation"));
                // 只抽取字段渲染需要的键，禁止整份拷贝组件（会把错误 type / 布局噪音带进审批 fields）
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", field);
                item.put("fieldCode", field);
                item.put("label", StringUtils.firstNonBlank(
                        StringUtils.trimToNull(component.getString("label")),
                        StringUtils.trimToNull(props.getString("label")),
                        StringUtils.trimToNull(props.getString("title")),
                        field));
                String componentKey = StringUtils.firstNonBlank(
                        StringUtils.trimToNull(component.getString("componentKey")),
                        StringUtils.trimToNull(component.getString("componentType")),
                        StringUtils.trimToNull(component.getString("type")),
                        StringUtils.trimToNull(component.getString("name")));
                Map<String, Object> propsMap = props.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(props);
                item.put("props", propsMap);
                item.put("componentKey", StringUtils.defaultIfBlank(componentKey, "input"));
                item.put("componentType", StringUtils.defaultIfBlank(componentKey, "input"));
                String resolvedType = resolveTaskFormControlType(item);
                item.put("type", normalizeTaskFormFieldType(resolvedType));
                item.put("componentType", StringUtils.defaultIfBlank(resolvedType, "input"));
                String dataType = StringUtils.firstNonBlank(
                        StringUtils.trimToNull(binding.getString("dataType")),
                        StringUtils.trimToNull(textValue(component.get("dataType"))));
                if (dataType != null) {
                    item.put("dataType", dataType);
                }
                String dictType = StringUtils.firstNonBlank(
                        StringUtils.trimToNull(textValue(component.get("dictType"))),
                        StringUtils.trimToNull(props.getString("dictType")));
                if (dictType != null) {
                    item.put("dictType", dictType);
                }
                boolean required = readBooleanValue(validation.get("required"), false)
                        || readBooleanValue(props.get("required"), false)
                        || readBooleanValue(component.get("required"), false);
                item.put("required", required);
                result.add(item);
            }
            collectBusinessFormFieldComponents(readNestedArray(component.get("children")), result, seen);
        }
    }
}
